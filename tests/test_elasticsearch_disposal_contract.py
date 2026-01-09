from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "ElasticsearchModule/src/main/java/com/opencgl/elasticsearch"


def test_plugin_dispose_reaches_controller_and_all_clients_are_isolated():
    plugin = (ROOT / "ElasticsearchPluginUI.java").read_text()
    controller = (ROOT / "controller/ElasticsearchController.java").read_text()
    service = (ROOT / "service/ElasticsearchService.java").read_text()
    assert "ElasticsearchController controller" in plugin
    assert "controller = loader.getController()" in plugin
    assert "controllerToDispose.dispose()" in plugin
    assert "esService.close()" in controller
    assert "ElasticsearchTransport transportToClose = transport" in service
    assert "RestClient restClientToClose = restClient" in service
    assert "transportToClose.close()" in service
    assert "restClientToClose.close()" in service
