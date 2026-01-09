#!/usr/bin/env python3
"""Collect and validate supported OpenCGL plugin artifacts."""

import argparse
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


MAVEN_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
NON_PLUGIN_MODULES = frozenset({"PluginApiModule", "OpenCGL-Base"})
IGNORED_SUFFIXES = ("-sources.jar", "-javadoc.jar", "-tests.jar")


def _pom_value(root, name):
    value = root.findtext(f"m:{name}", namespaces=MAVEN_NS)
    if value:
        return value.strip()
    return None


def _project_metadata(pom):
    root = ET.parse(pom).getroot()
    artifact = _pom_value(root, "artifactId")
    version = _pom_value(root, "version")
    if version is None:
        version = root.findtext("m:parent/m:version", namespaces=MAVEN_NS)
    final_name = root.findtext("m:build/m:finalName", namespaces=MAVEN_NS)
    if final_name:
        final_name = final_name.strip()
        final_name = final_name.replace("${project.artifactId}", artifact)
        final_name = final_name.replace("${project.version}", version)
    else:
        final_name = f"{artifact}-{version}"
    return artifact, version, final_name


def _reactor_modules(root):
    project = ET.parse(root / "pom.xml").getroot()
    return [node.text.strip() for node in project.findall("m:modules/m:module", MAVEN_NS)]


def _candidate_jars(module_dir, final_name):
    target = module_dir / "target"
    candidates = []
    for jar in target.glob("*.jar"):
        name = jar.name
        if name.startswith("original-") or name.endswith(IGNORED_SUFFIXES):
            continue
        if name == f"{final_name}.jar":
            candidates.append(jar)
    return candidates


def _sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _validate_jar(path):
    if path.stat().st_size == 0 or not zipfile.is_zipfile(path):
        raise ValueError(f"{path} is not a valid JAR")
    with zipfile.ZipFile(path) as jar:
        broken_member = jar.testzip()
    if broken_member is not None:
        raise ValueError(f"{path} is not a valid JAR: corrupt member {broken_member}")


def _reset_output(root, output):
    resolved_root = root.resolve()
    resolved_output = output.resolve()
    if resolved_output in (resolved_root, resolved_root.parent):
        raise ValueError(f"Refusing to clear unsafe output directory: {resolved_output}")
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)


def collect_plugins(root, output, manifest):
    root = Path(root)
    output = Path(output)
    manifest = Path(manifest)
    entries = []
    output_names = set()

    _reset_output(root, output)
    for module in _reactor_modules(root):
        if module in NON_PLUGIN_MODULES:
            continue
        module_dir = root / module
        artifact, version, final_name = _project_metadata(module_dir / "pom.xml")
        candidates = _candidate_jars(module_dir, final_name)
        if len(candidates) != 1:
            raise ValueError(
                f"{module} must produce exactly one runtime JAR named {final_name}.jar; "
                f"found {len(candidates)}"
            )
        source = candidates[0]
        _validate_jar(source)
        if source.name in output_names:
            raise ValueError(f"Duplicate plugin artifact: {source.name}")
        output_names.add(source.name)
        destination = output / source.name
        shutil.copy2(source, destination)
        entries.append(
            {
                "module": module,
                "artifact": artifact,
                "version": version,
                "file": source.name,
                "size": destination.stat().st_size,
                "sha256": _sha256(destination),
            }
        )

    manifest.parent.mkdir(parents=True, exist_ok=True)
    manifest.write_text(
        json.dumps({"plugins": entries}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    args = parser.parse_args()
    entries = collect_plugins(args.root, args.output, args.manifest)
    print(f"Collected and validated {len(entries)} plugin JARs in {args.output}")


if __name__ == "__main__":
    main()
