from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/"RagIngestionToolModule/src/main/java/com/opencgl/plugin/ragingestion"
def test_rag_disposes_gateway_executor_and_tasks():
    plugin=(ROOT/"RagIngestionPluginUI.java").read_text(); controller=(ROOT/"controller/RagIngestionWidgetController.java").read_text(); gateway=(ROOT/"gateway/EmbeddedRestGateway.java").read_text()
    assert "RagIngestionWidgetController controller" in plugin and "controllerToDispose.dispose()" in plugin
    assert "Set<Task<?>> runningTasks" in controller and "task.cancel(true)" in controller and "public void dispose()" in controller
    assert "ExecutorService executor" in gateway and "executorToStop.shutdownNow()" in gateway
