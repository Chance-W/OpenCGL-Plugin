from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[3]


def source(path):
    return (ROOT / path).read_text()


class MessagingLifecycleTest(unittest.TestCase):
    def assert_entry_delegates(self, path, controller_type):
        text = source(path)
        self.assertIn(f"private {controller_type} controller", text)
        self.assertIn("controller = loader.getController()", text)
        self.assertIn("current.dispose()", text)

    def test_mq_trace_entry_disposes_controller(self):
        self.assert_entry_delegates(
            "MqTraceModule/src/main/java/com/opencgl/MqTracePluginUI.java",
            "MqTraceController",
        )
        controller = source(
            "MqTraceModule/src/main/java/com/opencgl/mqtrace/controller/MqTraceController.java"
        )
        self.assertNotIn("CompletableFuture.runAsync", controller)
        self.assertIn("executor.shutdownNow()", controller)
        self.assertIn("runOnUi", controller)

    def test_websocket_entry_disposes_controller(self):
        self.assert_entry_delegates(
            "WebSocketModule/src/main/java/com/opencgl/WebSocketPluginUI.java",
            "WebSocketController",
        )
        controller = source(
            "WebSocketModule/src/main/java/com/opencgl/websocket/controller/WebSocketController.java"
        )
        service = source(
            "WebSocketModule/src/main/java/com/opencgl/websocket/service/WebSocketService.java"
        )
        self.assertIn("wsService.dispose()", controller)
        self.assertIn("public void dispose()", service)
        self.assertIn("onMessageCallback = null", service)
        self.assertIn("onStatusCallback = null", service)

    def test_rocketmq_consumer_entry_and_controller_shutdown_consumer(self):
        self.assert_entry_delegates(
            "RocketMqToolModule/src/main/java/com/opencgl/RocketMqConsumerToolPluginUI.java",
            "RocketMqConsumerWidgetController",
        )
        controller = source(
            "RocketMqToolModule/src/main/java/com/opencgl/controller/RocketMqConsumerWidgetController.java"
        )
        self.assertIn("public void dispose()", controller)
        self.assertIn("consumer.shutdown()", controller)
        self.assertIn("executor.shutdownNow()", controller)
        self.assertNotIn("CompletableFuture.runAsync", controller)

    def test_rocketmq_producer_entry_cancels_owned_executor(self):
        self.assert_entry_delegates(
            "RocketMqToolModule/src/main/java/com/opencgl/RocketMqProducerToolPluginUI.java",
            "RocketMqProducerWidgetController",
        )
        controller = source(
            "RocketMqToolModule/src/main/java/com/opencgl/controller/RocketMqProducerWidgetController.java"
        )
        self.assertIn("executor.shutdownNow()", controller)
        self.assertNotIn("CompletableFuture.runAsync", controller)

    def test_mml_entry_cancels_owned_executor(self):
        self.assert_entry_delegates(
            "MmlTestModule/src/main/java/com/opencgl/mml/MmlServiceTestestPluginUI.java",
            "MmlWidgetController",
        )
        controller = source(
            "MmlTestModule/src/main/java/com/opencgl/mml/controller/MmlWidgetController.java"
        )
        self.assertIn("public void dispose()", controller)
        self.assertIn("executor.shutdownNow()", controller)
        self.assertNotIn("CompletableFuture.runAsync", controller)
