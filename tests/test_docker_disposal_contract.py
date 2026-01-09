from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "DockerModule/src/main/java/com/opencgl/docker"


def test_plugin_dispose_reaches_controller_and_service_close_is_idempotent():
    plugin = (ROOT / "DockerPluginUI.java").read_text()
    controller = (ROOT / "controller/DockerController.java").read_text()
    service = (ROOT / "service/DockerService.java").read_text()
    assert "DockerController controller" in plugin
    assert "controller = loader.getController()" in plugin
    assert "controllerToDispose.dispose()" in plugin
    assert "dockerService.close()" in controller
    assert "DockerClient clientToClose = dockerClient" in service
    assert "dockerClient = null" in service
    assert "clientToClose.close()" in service
