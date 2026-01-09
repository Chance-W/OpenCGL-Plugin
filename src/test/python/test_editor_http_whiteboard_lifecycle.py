import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]


class EditorHttpWhiteboardLifecycleTest(unittest.TestCase):
    def source(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_editor_entry_disposes_controller_and_shared_webview(self):
        entry = self.source("EditorExperienceModule/src/main/java/com/opencgl/experience/EditorExperiencePlugin.java")
        controller = self.source("EditorExperienceModule/src/main/java/com/opencgl/experience/controller/EditorExperienceController.java")
        manager = self.source("EditorExperienceModule/src/main/java/com/opencgl/experience/controls/MonacoWebViewManager.java")
        self.assertIn("private EditorExperienceController controller;", entry)
        self.assertIn("controller = loader.getController();", entry)
        self.assertIn("controller.dispose();", entry)
        self.assertIn("MonacoWebViewManager.disposeInstance();", controller)
        self.assertIn("loadStateListener", manager)
        self.assertIn("removeListener(loadStateListener)", manager)
        self.assertIn('webEngine.load("about:blank")', manager)
        self.assertIn("pendingTasks.clear();", manager)

    def test_http_debugger_owns_async_work_and_disposes_services(self):
        entry = self.source("HttpDebuggerModule/src/main/java/com/opencgl/http/HttpDebuggerPluginUI.java")
        controller = self.source("HttpDebuggerModule/src/main/java/com/opencgl/http/controller/HttpDebuggerController.java")
        history = self.source("HttpDebuggerModule/src/main/java/com/opencgl/http/service/HttpDebuggerHistoryService.java")
        client = self.source("HttpDebuggerModule/src/main/java/com/opencgl/http/service/HttpClientService.java")
        self.assertIn("private HttpDebuggerController controller;", entry)
        self.assertIn("controller = loader.getController();", entry)
        self.assertIn("controller.dispose();", entry)
        self.assertNotIn("CompletableFuture.supplyAsync", controller)
        self.assertIn("private final ExecutorService backgroundExecutor", controller)
        self.assertIn("backgroundExecutor.shutdownNow();", controller)
        self.assertIn("historyService.close();", controller)
        self.assertIn("httpClientService.close();", controller)
        self.assertIn("Files.deleteIfExists", controller)
        self.assertNotIn("new Thread(() ->", history)
        self.assertIn("public void close()", history)
        self.assertIn("historyExecutor.shutdownNow();", history)
        self.assertIn(".executor(httpExecutor)", client)
        self.assertIn("httpExecutor.shutdownNow();", client)

    def test_whiteboard_removes_global_and_webview_listeners(self):
        plugin = self.source("WhiteboardModule/src/main/java/com/opencgl/whiteboard/view/WhiteboardPlugin.java")
        bridge = self.source("WhiteboardModule/src/main/java/com/opencgl/whiteboard/view/JavaIOBridge.java")
        self.assertIn("loadStateListener", plugin)
        self.assertIn("localeListener", plugin)
        self.assertIn("zoomHandler", plugin)
        self.assertIn("removeListener(loadStateListener)", plugin)
        self.assertIn("removeListener(localeListener)", plugin)
        self.assertIn("removeEventFilter(ZoomEvent.ZOOM, zoomHandler)", plugin)
        self.assertIn("ioBridge.dispose();", plugin)
        self.assertIn('webEngine.load("about:blank")', plugin)
        self.assertIn("rootPane.getChildren().clear();", plugin)
        self.assertIn("private volatile boolean disposed;", bridge)
        self.assertIn("public void dispose()", bridge)


if __name__ == "__main__":
    unittest.main()
