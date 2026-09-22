#!/usr/bin/env python3
"""Build every reactor plugin and deploy the resulting runtime JARs to bin/."""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

from collect_plugins import (
    NON_PLUGIN_MODULES,
    _candidate_jars,
    _project_metadata,
    _reactor_modules,
)


def _run_maven(root: Path, with_tests: bool, modules: list[str] | None) -> None:
    command = ["mvn"]
    if modules:
        command.extend(["-pl", ",".join(modules), "-am"])
    command.extend(["clean", "package"])
    if not with_tests:
        command.append("-DskipTests")
    print("[package-plugins] $ " + " ".join(command), flush=True)
    subprocess.run(command, cwd=root, check=True)


def _remove_old_versions(destination: Path, prefixes: tuple[str, ...], keep: str) -> None:
    # Only remove files belonging to this Maven artifact. Other manually
    # installed jars in bin/ (for example LicenseManager.jar) are untouched.
    pattern = re.compile(rf"^(?:{'|'.join(re.escape(prefix) for prefix in prefixes)})(?:-|\.)")
    for path in destination.iterdir():
        if path.is_file() and path.suffix == ".jar" and path.name != keep and pattern.match(path.name):
            path.unlink()


def deploy_plugins(root: Path, destination: Path,
                   modules: list[str] | None = None) -> list[tuple[str, Path]]:
    destination.mkdir(parents=True, exist_ok=True)
    deployed: list[tuple[str, Path]] = []
    selected = set(modules) if modules else None
    for module in _reactor_modules(root):
        if module in NON_PLUGIN_MODULES:
            continue
        if selected is not None and module not in selected:
            continue
        module_dir = root / module
        artifact, _version, final_name = _project_metadata(module_dir / "pom.xml")
        candidates = _candidate_jars(module_dir, final_name)
        if len(candidates) != 1:
            raise RuntimeError(
                f"{module}: expected target/{final_name}.jar after package, "
                f"found {len(candidates)}"
            )
        source = candidates[0]
        target = destination / source.name
        _remove_old_versions(destination, (artifact, final_name), source.name)
        shutil.copy2(source, target)
        deployed.append((module, target))
    return deployed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path, default=None,
                        help="插件部署目录，默认是工程根目录下的 bin")
    parser.add_argument("--module", action="append", dest="modules", metavar="MODULE",
                        help="只打包指定模块；可重复传入多个 --module，Maven 会自动构建 API/Base 依赖")
    parser.add_argument("--with-tests", action="store_true",
                        help="打包前运行测试；默认跳过测试，仅用于快速部署插件")
    args = parser.parse_args()
    root = args.root.resolve()
    output = (args.output or root / "bin").resolve()
    _run_maven(root, args.with_tests, args.modules)
    deployed = deploy_plugins(root, output, args.modules)
    print(f"[package-plugins] deployed {len(deployed)} plugin JARs to {output}")
    for module, target in deployed:
        print(f"  {module} -> {target.name}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as exc:
        print(f"[package-plugins] Maven failed with exit code {exc.returncode}", file=sys.stderr)
        raise
