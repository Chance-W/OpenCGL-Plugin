from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/"DubboSslTestModule/src/main/java/com/opencgl/dubbossl"
def test_dubbo_ssl_has_owned_async_lifecycle():
    plugin=(ROOT/"DubboSslServiceTestPluginUI.java").read_text(); controller=(ROOT/"controller/DubboSslWidgetController.java").read_text(); dialog=(ROOT/"controller/DubboSslEnvConfigureDialog.java").read_text(); util=(ROOT/"utils/DubboSslUtil.java").read_text()
    assert "DubboSslWidgetController controller" in plugin and "controllerToDispose.dispose()" in plugin
    assert "ExecutorService executor" in controller and "Set<Future<?>> runningTasks" in controller
    assert "submitAsync(" in controller and "CompletableFuture.runAsync" not in controller
    assert "public void dispose()" in controller and "executor.shutdownNow()" in controller and "disposed" in controller
    assert "ExecutorService executor" in dialog and "stage.setOnHidden" in dialog and "public void dispose()" in dialog
    assert "Set<ReferenceConfig<GenericService>> activeReferences" in util and "reference.destroy()" in util
