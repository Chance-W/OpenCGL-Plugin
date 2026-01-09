from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/"SimpleStaticServerModule/src/main/java"
def test_static_server_disposes_http_server_and_executor():
    plugin=(ROOT/"com/opencgl/SimpleStaticServerPluginUI.java").read_text(); controller=(ROOT/"com/opencgl/staticserver/controller/StaticServerController.java").read_text(); service=(ROOT/"com/opencgl/staticserver/service/HttpServerService.java").read_text()
    assert "StaticServerController controller" in plugin and "controllerToDispose.dispose()" in plugin
    assert "public void dispose()" in controller and "serverService.stop()" in controller
    assert "ExecutorService executor" in service and "executorToStop.shutdownNow()" in service
