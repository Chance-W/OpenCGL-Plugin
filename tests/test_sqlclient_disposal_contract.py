from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "SqlClientModule/src/main/java/com/opencgl/sqlclient"


def test_plugin_dispose_reaches_controller_and_all_connections_are_closed():
    plugin = (ROOT / "SqlClientPluginUI.java").read_text()
    controller = (ROOT / "controller/SqlClientController.java").read_text()
    service = (ROOT / "service/DatabaseService.java").read_text()
    assert "SqlClientController controller" in plugin
    assert "controller = new SqlClientController()" in plugin
    assert "controllerToDispose.dispose()" in plugin
    assert "void dispose()" in controller
    assert "serviceToClose.disconnectAll()" in controller
    assert "connectionPool.clear()" in service
    assert "for (Connection connection : connections)" in service
    assert "connection.close()" in service
