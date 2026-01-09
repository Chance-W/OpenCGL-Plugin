from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "MongoDBModule/src/main/java/com/opencgl"


def test_plugin_dispose_reaches_controller_and_service_close_is_idempotent():
    plugin = (ROOT / "MongoDBPluginUI.java").read_text()
    controller = (ROOT / "mongodb/controller/MongoDBController.java").read_text()
    service = (ROOT / "mongodb/service/MongoDBService.java").read_text()
    assert "MongoDBController controller" in plugin
    assert "controller = loader.getController()" in plugin
    assert "controllerToDispose.dispose()" in plugin
    assert "mongoService.close()" in controller
    assert "MongoClient clientToClose = mongoClient" in service
    assert "mongoClient = null" in service
    assert "clientToClose.close()" in service
