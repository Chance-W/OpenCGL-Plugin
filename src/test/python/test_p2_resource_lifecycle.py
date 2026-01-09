import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]


class P2ResourceLifecycleTest(unittest.TestCase):
    def source(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_async_plugin_entries_retain_and_dispose_controllers(self):
        entries = {
            "CertGeneratorModule/src/main/java/com/opencgl/cert/CertGeneratorPluginUI.java": "CertGeneratorController",
            "RsaToolModule/src/main/java/com/opencgl/RsaToolPluginUI.java": "RsaToolController",
            "RsaJavaFXModule/src/main/java/com/opencgl/RsaJavaFXPluginUI.java": "RsaKeyGeneratorController",
            "GraphQLModule/src/main/java/com/opencgl/graphql/GraphQLPluginUI.java": "GraphQLController",
            "PingScanModule/src/main/java/com/opencgl/pingscan/PingScanPluginUI.java": "PingScanController",
            "SoapTestModule/src/main/java/com/opencgl/SoapServiceTestPluginUI.java": "SoapWidgetController",
        }
        for path, controller in entries.items():
            source = self.source(path)
            with self.subTest(plugin=path):
                self.assertIn(f"private {controller} controller;", source)
                self.assertIn("controller = loader.getController();", source)
                self.assertIn("controller.dispose();", source)

    def test_crypto_background_work_is_cancellable(self):
        controllers = [
            "CertGeneratorModule/src/main/java/com/opencgl/cert/controller/CertGeneratorController.java",
            "RsaToolModule/src/main/java/com/opencgl/rsatool/controller/RsaToolController.java",
            "RsaJavaFXModule/src/main/java/com/opencgl/rsa/controller/RsaKeyGeneratorController.java",
        ]
        for path in controllers:
            source = self.source(path)
            with self.subTest(controller=path):
                self.assertNotIn("new Thread(() ->", source)
                self.assertIn("private final ExecutorService backgroundExecutor", source)
                self.assertIn("backgroundExecutor.shutdownNow();", source)
                self.assertIn("public void dispose()", source)
                self.assertIn("disposed = true;", source)

    def test_graphql_owns_tasks_and_closes_http_resources(self):
        controller = self.source("GraphQLModule/src/main/java/com/opencgl/graphql/controller/GraphQLController.java")
        service = self.source("GraphQLModule/src/main/java/com/opencgl/graphql/service/GraphQLService.java")
        self.assertIn("backgroundExecutor", controller)
        self.assertIn("backgroundTasks", controller)
        self.assertIn("backgroundExecutor.shutdownNow();", controller)
        self.assertIn("graphqlService.close();", controller)
        self.assertIn("public void close()", service)
        self.assertIn("client.dispatcher().cancelAll()", service)
        self.assertIn("client.connectionPool().evictAll()", service)

    def test_ping_owns_tasks_and_running_process(self):
        controller = self.source("PingScanModule/src/main/java/com/opencgl/pingscan/controller/PingScanController.java")
        self.assertIn("private volatile Process currentProcess;", controller)
        self.assertIn("backgroundExecutor", controller)
        self.assertIn("process.destroyForcibly();", controller)
        self.assertIn("backgroundExecutor.shutdownNow();", controller)
        self.assertIn("public void dispose()", controller)

    def test_soap_unregisters_listener_and_closes_http(self):
        controller = self.source("SoapTestModule/src/main/java/com/opencgl/controller/SoapWidgetController.java")
        sender = self.source("SoapTestModule/src/main/java/com/opencgl/impl/RestSender.java")
        self.assertIn("backgroundExecutor", controller)
        self.assertIn("eventBus.unregister(this);", controller)
        self.assertIn("sendMessageService.close();", controller)
        self.assertIn("backgroundExecutor.shutdownNow();", controller)
        self.assertIn("public void close()", sender)
        self.assertIn("client.dispatcher().cancelAll()", sender)
        self.assertIn("client.connectionPool().evictAll()", sender)

    def test_curl_request_closes_one_shot_http_resources(self):
        controller = self.source("CurlToRequestModule/src/main/java/com/opencgl/curl/controller/CurlToRequestController.java")
        self.assertIn("try (Response response =", controller)
        self.assertIn("client.dispatcher().executorService().shutdown();", controller)
        self.assertIn("client.connectionPool().evictAll();", controller)


if __name__ == "__main__":
    unittest.main()
