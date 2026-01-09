import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]


def source(path):
    return (ROOT / path).read_text(encoding="utf-8")


class UtilityLifecycleContractTest(unittest.TestCase):
    def assert_owned_executor_lifecycle(self, entry_path, controller_path):
        entry = source(entry_path)
        controller = source(controller_path)
        self.assertIn("controller", entry)
        self.assertIn("current.dispose()", entry)
        self.assertIn("ExecutorService", controller)
        self.assertIn("setDaemon(true)", controller)
        self.assertIn("shutdownNow()", controller)
        self.assertIn("boolean disposed", controller)

    def test_code_snippet(self):
        self.assert_owned_executor_lifecycle(
            "CodeSnippetModule/src/main/java/com/opencgl/snippet/CodeSnippetPluginUI.java",
            "CodeSnippetModule/src/main/java/com/opencgl/snippet/controller/CodeSnippetController.java")

    def test_data_extractor(self):
        self.assert_owned_executor_lifecycle(
            "DataExtractorModule/src/main/java/com/opencgl/extractor/DataExtractorPluginUI.java",
            "DataExtractorModule/src/main/java/com/opencgl/extractor/controller/DataExtractorController.java")

    def test_hash_tool(self):
        self.assert_owned_executor_lifecycle(
            "HashToolModule/src/main/java/com/opencgl/hash/HashToolPluginUI.java",
            "HashToolModule/src/main/java/com/opencgl/hash/controller/HashToolController.java")

    def test_decompiler(self):
        self.assert_owned_executor_lifecycle(
            "JavaDecompilerModule/src/main/java/com/opencgl/decompiler/JavaDecompilerPluginUI.java",
            "JavaDecompilerModule/src/main/java/com/opencgl/decompiler/controller/DecompilerController.java")

    def test_script_debugger(self):
        self.assert_owned_executor_lifecycle(
            "ScriptDebugModule/src/main/java/com/opencgl/scriptdebug/ScriptDebugAppLauncherPlugin.java",
            "ScriptDebugModule/src/main/java/com/opencgl/scriptdebug/controller/ScriptDebugWidgetController.java")

    def test_file_checksum_service(self):
        entry = source("FileChecksumModule/src/main/java/com/opencgl/checksum/FileChecksumPluginUI.java")
        service = source("FileChecksumModule/src/main/java/com/opencgl/checksum/service/ChecksumService.java")
        self.assertIn("current.dispose()", entry)
        self.assertIn("setDaemon(true)", service)
        self.assertIn("executor.shutdownNow()", service)

    def test_git_lite_kills_processes(self):
        entry = source("GitLiteModule/src/main/java/com/opencgl/gitlite/GitLitePluginUI.java")
        service = source("GitLiteModule/src/main/java/com/opencgl/gitlite/service/GitCommandService.java")
        self.assertIn("current.dispose()", entry)
        self.assertIn("destroyForcibly", service)

    def test_template_probe_threads(self):
        plugin = source("TemplatePlugin4/src/main/java/com/opencgl/template4/TemplatePlugin4Demo.java")
        self.assertIn("setDaemon(true)", plugin)
        self.assertIn("executor.shutdownNow()", plugin)
        self.assertNotIn("}).start();", plugin)


if __name__ == "__main__":
    unittest.main()
