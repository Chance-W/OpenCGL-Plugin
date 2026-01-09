import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
MAVEN_NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def reactor_modules():
    root = ET.parse(ROOT / "pom.xml").getroot()
    return [node.text.strip() for node in root.findall("m:modules/m:module", MAVEN_NS)]


def dependency_coordinates():
    coordinates = set()
    for pom in ROOT.glob("*/pom.xml"):
        root = ET.parse(pom).getroot()
        for dependency in root.findall(".//m:dependency", MAVEN_NS):
            group = dependency.findtext("m:groupId", namespaces=MAVEN_NS)
            artifact = dependency.findtext("m:artifactId", namespaces=MAVEN_NS)
            if group and artifact:
                coordinates.add(f"{group}:{artifact}")
    return coordinates


class ReactorConfigurationTest(unittest.TestCase):

    def test_api_and_base_are_built_before_plugins(self):
        self.assertEqual(["PluginApiModule", "OpenCGL-Base"], reactor_modules()[:2])

    def test_supported_modules_use_case_sensitive_existing_paths(self):
        modules = reactor_modules()

        self.assertIn("GRPCTestModule", modules)
        self.assertNotIn("gRPCTestModule", modules)
        self.assertIn("SshJediTermModule", modules)
        self.assertTrue(
            all((ROOT / module / "pom.xml").is_file() for module in modules),
            "Every declared reactor module must resolve on a case-sensitive filesystem",
        )

    def test_oracle_driver_is_resolvable_from_the_configured_repositories(self):
        self.assertNotIn("cn.easyproject:ojdbc7", dependency_coordinates())


if __name__ == "__main__":
    unittest.main()
