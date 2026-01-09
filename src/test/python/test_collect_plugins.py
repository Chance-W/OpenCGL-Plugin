import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
COLLECTOR_PATH = ROOT / "build" / "collect_plugins.py"


def load_collector():
    spec = importlib.util.spec_from_file_location("collect_plugins", COLLECTOR_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def write_pom(path, artifact, version="1.0.0", modules=()):
    module_xml = "".join(f"<module>{name}</module>" for name in modules)
    path.write_text(
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
        f"<modelVersion>4.0.0</modelVersion><artifactId>{artifact}</artifactId>"
        f"<version>{version}</version><modules>{module_xml}</modules></project>",
        encoding="utf-8",
    )


def write_jar(path, member="plugin.txt"):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as jar:
        jar.writestr(member, "plugin")


class CollectPluginsTest(unittest.TestCase):

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        write_pom(
            self.root / "pom.xml",
            "plugins",
            modules=("PluginApiModule", "OpenCGL-Base", "PluginOne", "PluginTwo"),
        )
        for module in ("PluginApiModule", "OpenCGL-Base", "PluginOne", "PluginTwo"):
            module_dir = self.root / module
            module_dir.mkdir()
            write_pom(module_dir / "pom.xml", module)

    def tearDown(self):
        self.temp.cleanup()

    def test_collects_one_valid_jar_per_supported_plugin(self):
        write_jar(self.root / "PluginOne/target/PluginOne-1.0.0.jar")
        write_jar(self.root / "PluginTwo/target/PluginTwo-1.0.0.jar")
        output = self.root / "dist"
        manifest = self.root / "manifest.json"

        entries = load_collector().collect_plugins(self.root, output, manifest)

        self.assertEqual(["PluginOne", "PluginTwo"], [entry["module"] for entry in entries])
        self.assertEqual(2, len(list(output.glob("*.jar"))))
        self.assertEqual(entries, json.loads(manifest.read_text(encoding="utf-8"))["plugins"])
        self.assertTrue(all(len(entry["sha256"]) == 64 for entry in entries))

    def test_rejects_a_missing_plugin_jar(self):
        write_jar(self.root / "PluginOne/target/PluginOne-1.0.0.jar")

        with self.assertRaisesRegex(ValueError, "PluginTwo.*exactly one"):
            load_collector().collect_plugins(self.root, self.root / "dist", self.root / "manifest.json")

    def test_ignores_non_runtime_jars(self):
        write_jar(self.root / "PluginOne/target/PluginOne-1.0.0.jar")
        write_jar(self.root / "PluginOne/target/PluginOne-1.0.0-sources.jar")
        write_jar(self.root / "PluginOne/target/original-PluginOne-1.0.0.jar")
        write_jar(self.root / "PluginTwo/target/PluginTwo-1.0.0.jar")

        entries = load_collector().collect_plugins(
            self.root, self.root / "dist", self.root / "manifest.json"
        )

        self.assertEqual(2, len(entries))

    def test_rejects_a_non_zip_artifact(self):
        bad_jar = self.root / "PluginOne/target/PluginOne-1.0.0.jar"
        bad_jar.parent.mkdir(parents=True)
        bad_jar.write_text("not a jar", encoding="utf-8")
        write_jar(self.root / "PluginTwo/target/PluginTwo-1.0.0.jar")

        with self.assertRaisesRegex(ValueError, "valid JAR"):
            load_collector().collect_plugins(self.root, self.root / "dist", self.root / "manifest.json")

    def test_rejects_duplicate_output_names(self):
        write_pom(self.root / "PluginOne/pom.xml", "same")
        write_pom(self.root / "PluginTwo/pom.xml", "same")
        write_jar(self.root / "PluginOne/target/same-1.0.0.jar")
        write_jar(self.root / "PluginTwo/target/same-1.0.0.jar")

        with self.assertRaisesRegex(ValueError, "Duplicate plugin artifact"):
            load_collector().collect_plugins(self.root, self.root / "dist", self.root / "manifest.json")


if __name__ == "__main__":
    unittest.main()
