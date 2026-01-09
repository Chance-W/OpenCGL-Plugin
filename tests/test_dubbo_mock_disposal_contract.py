from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/"DubboMockModule/src/main/java/com/opencgl/dubbo/mock"
def test_dubbo_mock_disposes_controller_processes_and_services():
    plugin=(ROOT/"DubboMockPlugin.java").read_text(); controller=(ROOT/"controller/DubboMockController.java").read_text(); manager=(ROOT/"engine/DubboMockProcessManager.java").read_text(); service=(ROOT/"service/DubboMockService.java").read_text()
    assert "DubboMockController controller" in plugin and "controllerToDispose.dispose()" in plugin
    assert "public void dispose()" in controller and "disposed" in controller
    assert "public synchronized void stopAll()" in manager and "loggerThread.join" in manager
    assert "public void close()" in service and "service.unexport()" in service and "DubboBootstrap.getInstance().destroy()" in service
