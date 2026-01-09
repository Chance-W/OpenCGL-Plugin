from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "NacosModule/src/main/java/com/opencgl"


def test_plugin_dispose_reaches_controller_and_both_services_are_isolated():
    plugin = (ROOT / "NacosPluginUI.java").read_text()
    controller = (ROOT / "nacos/controller/NacosController.java").read_text()
    service = (ROOT / "nacos/service/NacosService.java").read_text()
    assert "NacosController controller" in plugin
    assert "controller = loader.getController()" in plugin
    assert "controllerToDispose.dispose()" in plugin
    assert "nacosService.close()" in controller
    assert "ConfigService configToClose = configService" in service
    assert "NamingService namingToClose = namingService" in service
    assert "configToClose.shutDown()" in service
    assert "namingToClose.shutDown()" in service
