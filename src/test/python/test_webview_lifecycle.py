from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]

def read(path): return (ROOT / path).read_text()

class WebViewLifecycleTest(unittest.TestCase):
    def assert_chain(self, entry, controller):
        plugin = read(entry)
        self.assertIn("controller", plugin)
        self.assertIn("dispose()", plugin)
        self.assertIn("public void dispose()", read(controller))

    def test_browser_disposes_webview_and_http_client(self):
        self.assert_chain(
            "BrowserToolModule/src/main/java/com/opencgl/brower/BrowserToolPluginUI.java",
            "BrowserToolModule/src/main/java/com/opencgl/brower/controller/WebSourcesToolController.java")
        controller = read("BrowserToolModule/src/main/java/com/opencgl/brower/controller/WebSourcesToolController.java")
        self.assertIn('load("about:blank")', controller)

    def test_diff_disposes_webview(self):
        self.assert_chain(
            "DiffToolModule/src/main/java/com/opencgl/diff/DiffToolPluginUI.java",
            "DiffToolModule/src/main/java/com/opencgl/diff/controller/DiffToolController.java")
        self.assertIn('load("about:blank")', read("DiffToolModule/src/main/java/com/opencgl/diff/controller/DiffToolController.java"))

    def test_markdown_cancels_timer_and_webview(self):
        self.assert_chain(
            "MarkdownEditorModule/src/main/java/com/opencgl/markdown/MarkdownEditorPluginUI.java",
            "MarkdownEditorModule/src/main/java/com/opencgl/markdown/controller/MarkdownEditorController.java")
        controller = read("MarkdownEditorModule/src/main/java/com/opencgl/markdown/controller/MarkdownEditorController.java")
        self.assertIn("debounceTimer.cancel()", controller)
        self.assertIn('load("about:blank")', controller)
