import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]


class RedisZookeeperLifecycleTest(unittest.TestCase):
    def read(self, relative):
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_redis_dispose_reaches_sessions_and_clients(self):
        entry = self.read("RedisModule/src/main/java/com/opencgl/RedisToolPluginUI.java")
        controller = self.read("RedisModule/src/main/java/com/opencgl/redis/controller/RedisWidgetController.java")
        tab = self.read("RedisModule/src/main/java/com/opencgl/redis/components/RedisSessionTab.java")
        session = self.read("RedisModule/src/main/java/com/opencgl/redis/controller/RedisSessionController.java")
        browser = self.read("RedisModule/src/main/java/com/opencgl/redis/components/RedisKeyBrowser.java")
        connector = self.read("RedisModule/src/main/java/com/opencgl/redis/components/RedisTtyConnector.java")
        self.assertIn("controller.dispose()", entry)
        self.assertIn("sessionTab.dispose()", controller)
        self.assertIn("sessionController.disconnect()", tab)
        self.assertIn("connectionManager.disconnect()", session)
        self.assertIn("executor.shutdownNow()", session)
        self.assertIn("keyBrowser.dispose()", session)
        self.assertIn("executor.shutdownNow()", browser)
        self.assertIn("executor.shutdownNow()", connector)

    def test_zookeeper_dispose_closes_client_listeners_and_executor(self):
        entry = self.read("ZookeeperToolModule/src/main/java/com/opencgl/plugin/zookeeper/ZookeeperToolPluginUI.java")
        controller = self.read("ZookeeperToolModule/src/main/java/com/opencgl/plugin/zookeeper/controller/ZookeeperToolController.java")
        service = self.read("ZookeeperToolModule/src/main/java/com/opencgl/plugin/zookeeper/service/ZookeeperToolService.java")
        self.assertIn("controller.dispose()", entry)
        self.assertIn("zookeeperToolService.close()", controller)
        self.assertIn("zkClient.close()", service)
        self.assertIn("childListeners.clear()", service)
        self.assertIn("dataListeners.clear()", service)
        self.assertIn("executor.shutdownNow()", service)


if __name__ == "__main__":
    unittest.main()
