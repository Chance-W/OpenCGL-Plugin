from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[3]
FXML = ROOT / "TimestampToolModule/src/main/resources/TimestampToolView.fxml"
FX_ID = "{http://javafx.com/fxml/1}id"


class TimestampThemeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = ET.parse(FXML).getroot()
        cls.nodes = {
            node.attrib[FX_ID]: node
            for node in cls.root.iter()
            if FX_ID in node.attrib
        }

    def test_clock_card_uses_a_themed_surface_and_border(self):
        style = self.nodes["clockCard"].attrib.get("style", "")
        self.assertIn("-fx-background-color: -theme-bg-tertiary", style)
        self.assertIn("-fx-border-color: -theme-border", style)

    def test_clock_labels_use_readable_foreground_in_light_theme(self):
        label_ids = (
            "label_local_time",
            "currentLocalTimeLabel",
            "label_utc_time",
            "currentUtcTimeLabel",
            "label_unix_seconds",
            "unixSecondsLabel",
            "label_unix_millis",
            "unixMillisLabel",
        )
        for label_id in label_ids:
            with self.subTest(label_id=label_id):
                style = self.nodes[label_id].attrib.get("style", "")
                self.assertIn("-fx-text-fill: -theme-text-primary", style)
                self.assertNotIn("-theme-text-inverse", style)

    def test_pause_button_uses_the_shared_secondary_button_style(self):
        button = self.nodes["pauseBtn"]
        self.assertIn("secondary-button", button.attrib.get("styleClass", ""))
        self.assertIn(
            "-fx-text-fill: -theme-text-primary",
            button.attrib.get("style", ""),
        )


if __name__ == "__main__":
    unittest.main()
