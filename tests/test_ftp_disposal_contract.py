from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / "FtpToolModule/src/main/java/com/opencgl/ftp"
def test_ftp_spi_entry_stops_server():
    plugin=(ROOT/"FtpToolPluginUI.java").read_text(); controller=(ROOT/"controller/FtpServerController.java").read_text(); service=(ROOT/"utils/FtpServerService.java").read_text()
    assert "com.opencgl.ftp.FtpToolPluginUI" in (ROOT.parents[3]/"resources/META-INF/services/com.opencgl.api.PluginUI").read_text()
    assert "FtpServerController controller" in plugin and "controllerToDispose.dispose()" in plugin
    assert "ftpServerService.stopFtpServerAction()" in controller and "public void dispose()" in controller
    assert "FtpServer serverToStop = server" in service and "serverToStop.stop()" in service
