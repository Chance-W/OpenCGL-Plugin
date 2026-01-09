from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/"RestTestModule/src/main/java/com/opencgl"
def test_rest_entry_disposes_controller_mock_server_and_webview():
    plugin=(ROOT/"RestServiceTestPluginUI.java").read_text(); controller=(ROOT/"controller/RestWidgetController.java").read_text(); mock=(ROOT/"mock/MockServer.java").read_text(); panel=(ROOT/"mock/MockServerPanel.java").read_text(); preview=(ROOT/"components/ResponsePreviewPane.java").read_text()
    assert "RestWidgetController controller" in plugin and "controllerToDispose.dispose()" in plugin
    assert "public void dispose()" in controller and "disposed" in controller
    assert "ExecutorService executor" in mock and "executorToStop.shutdownNow()" in mock
    assert "public void dispose()" in panel and "mockServer.stop()" in panel
    assert 'previewWebView.getEngine().load("about:blank")' in preview
