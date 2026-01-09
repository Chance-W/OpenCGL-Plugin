import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]


class PluginDisposalContractTest(unittest.TestCase):
    def test_resource_owning_entries_retain_and_dispose_their_controllers(self):
        entries = {
            "GRPCTestModule/src/main/java/com/opencgl/grpc/GrpcTestPluginUI.java":
                "GrpcTestController",
            "KafkaToolModule/src/main/java/com/opencgl/kafka/KafkaToolPluginUI.java":
                "KafkaToolController",
            "LanMessengerModule/src/main/java/com/opencgl/lanmsg/LanMessengerPluginUI.java":
                "LanMessengerController",
            "RestMockModule/src/main/java/com/opencgl/RestMockToolPluginUI.java":
                "RestMockServerWidgetController",
            "TcpUdpToolModule/src/main/java/com/opencgl/tcpudp/TcpUdpToolPluginUI.java":
                "TcpUdpToolController",
        }

        for relative_path, controller_type in entries.items():
            source = (ROOT / relative_path).read_text(encoding="utf-8")
            with self.subTest(plugin=relative_path):
                self.assertIn(f"private {controller_type} controller;", source)
                self.assertIn("controller = loader.getController();", source)
                self.assertIn("controller.dispose();", source)

    def test_network_controllers_expose_resource_cleanup(self):
        controllers = [
            "LanMessengerModule/src/main/java/com/opencgl/lanmsg/controller/LanMessengerController.java",
            "RestMockModule/src/main/java/com/opencgl/controller/RestMockServerWidgetController.java",
            "TcpUdpToolModule/src/main/java/com/opencgl/tcpudp/controller/TcpUdpToolController.java",
        ]
        for relative_path in controllers:
            source = (ROOT / relative_path).read_text(encoding="utf-8")
            with self.subTest(controller=relative_path):
                self.assertIn("public void dispose()", source)


if __name__ == "__main__":
    unittest.main()
