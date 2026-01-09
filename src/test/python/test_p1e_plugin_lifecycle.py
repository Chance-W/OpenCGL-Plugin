import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]


class P1EPluginLifecycleTest(unittest.TestCase):
    def source(self, relative_path):
        return (ROOT / relative_path).read_text(encoding="utf-8")

    def test_all_plugin_entries_retain_and_dispose_controllers(self):
        entries = {
            "ClipboardHistoryModule/src/main/java/com/opencgl/clipboard/ClipboardHistoryPluginUI.java": "ClipboardHistoryController",
            "PortScannerModule/src/main/java/com/opencgl/portscan/PortScannerPluginUI.java": "PortScannerController",
            "TimestampToolModule/src/main/java/com/opencgl/timestamp/TimestampToolLauncherPlugin.java": "TimestampToolController",
            "QRCodeGenerateModule/src/main/java/com/opencgl/QRCodeGeneratePluginUI.java": "QRCodeGenerateController",
            "PixelRulerModule/src/main/java/com/opencgl/ruler/PixelRulerPluginUI.java": "PixelRulerController",
            "DocConverterModule/src/main/java/com/opencgl/docconverter/DocConverterPluginUI.java": "DocConverterController",
            "DocToMarkdownModule/src/main/java/com/opencgl/doctomd/DocToMarkdownPluginUI.java": "DocToMarkdownController",
            "DbBatchExecModule/src/main/java/com/opencgl/dbbatch/DbBatchLauncherPlugin.java": "DbBatchController",
            "AiQaModule/src/main/java/com/opencgl/aiqa/AiQaPluginUI.java": "AiQaController",
        }
        for path, controller_type in entries.items():
            source = self.source(path)
            with self.subTest(plugin=path):
                self.assertIn(f"private {controller_type} controller;", source)
                self.assertIn("controller = loader.getController();", source)
                self.assertIn("controller.dispose();", source)

    def test_timers_and_background_executors_are_owned_and_stopped(self):
        expectations = {
            "ClipboardHistoryModule/src/main/java/com/opencgl/clipboard/controller/ClipboardHistoryController.java": ["public void dispose()", "clipboardService.stopMonitoring();", "disposed = true;"],
            "TimestampToolModule/src/main/java/com/opencgl/timestamp/controller/TimestampToolController.java": ["public void dispose()", "clockTimeline.stop();", "copyTimers", "disposed = true;"],
            "QRCodeGenerateModule/src/main/java/com/opencgl/qr/controller/QRCodeGenerateController.java": ["public void dispose()", "timer.cancel();", "disposed = true;"],
            "PixelRulerModule/src/main/java/com/opencgl/ruler/controller/PixelRulerController.java": ["public void dispose()", "monitorTimer.cancel();", "disposed = true;"],
            "DocConverterModule/src/main/java/com/opencgl/docconverter/controller/DocConverterController.java": ["public void dispose()", "convertExecutor.shutdownNow();", "webEngine.load(null);", "disposed = true;"],
            "DocToMarkdownModule/src/main/java/com/opencgl/doctomd/controller/DocToMarkdownController.java": ["public void dispose()", "executor.shutdownNow();", "conversionFuture", "disposed = true;"],
            "DbBatchExecModule/src/main/java/com/opencgl/dbbatch/controller/DbBatchController.java": ["public void dispose()", "backgroundExecutor.shutdownNow();", "disposed = true;"],
            "AiQaModule/src/main/java/com/opencgl/aiqa/controller/AiQaController.java": ["public void dispose()", "backgroundExecutor.shutdownNow();", "docService.close();", "disposed = true;"],
        }
        for path, snippets in expectations.items():
            source = self.source(path)
            with self.subTest(controller=path):
                for snippet in snippets:
                    self.assertIn(snippet, source)

    def test_port_scanner_uses_owned_executors_only(self):
        controller = self.source("PortScannerModule/src/main/java/com/opencgl/portscan/controller/PortScannerController.java")
        service = self.source("PortScannerModule/src/main/java/com/opencgl/portscan/service/PortScanService.java")
        local_ports = self.source("PortScannerModule/src/main/java/com/opencgl/portscan/service/LocalPortService.java")
        self.assertNotIn("CompletableFuture.supplyAsync(() ->", controller)
        self.assertNotIn("CompletableFuture.runAsync(() ->", service)
        self.assertIn("backgroundExecutor.shutdownNow();", controller)
        self.assertIn("public void dispose()", service)
        self.assertIn("localPortService.dispose();", controller)
        self.assertIn("process.destroyForcibly();", local_ports)

    def test_document_processes_and_ai_http_resources_are_cancellable(self):
        doc_service = self.source("DocToMarkdownModule/src/main/java/com/opencgl/doctomd/service/DocToMarkdownService.java")
        ai_interface = self.source("AiQaModule/src/main/java/com/opencgl/aiqa/provider/AiProvider.java")
        embedding = self.source("AiQaModule/src/main/java/com/opencgl/aiqa/service/EmbeddingService.java")
        documents = self.source("AiQaModule/src/main/java/com/opencgl/aiqa/service/DocumentLibraryService.java")
        for provider in ["OpenAiProvider.java", "OllamaProvider.java"]:
            source = self.source("AiQaModule/src/main/java/com/opencgl/aiqa/provider/" + provider)
            self.assertIn("public void close()", source)
            self.assertIn("client.dispatcher().executorService().shutdown()", source)
            self.assertIn("client.connectionPool().evictAll()", source)
        self.assertIn("currentProcess", doc_service)
        self.assertIn("public void cancel()", doc_service)
        self.assertIn("extends AutoCloseable", ai_interface)
        self.assertIn("public void close()", embedding)
        self.assertIn("public void close()", documents)

    def test_db_batch_owns_thread_and_interrupts_jdbc_work(self):
        controller = self.source("DbBatchExecModule/src/main/java/com/opencgl/dbbatch/controller/DbBatchController.java")
        service = self.source("DbBatchExecModule/src/main/java/com/opencgl/dbbatch/service/DbExecutorService.java")
        self.assertNotIn("new Thread(() ->", controller)
        self.assertIn("backgroundExecutor.submit", controller)
        self.assertIn("executionFuture.cancel(true);", controller)
        self.assertIn("statement.cancel();", service)
        self.assertIn("connection.close();", service)


if __name__ == "__main__":
    unittest.main()
