import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]


class SshPluginLifecycleTest(unittest.TestCase):
    def test_plugin_entries_retain_and_dispose_their_controllers(self):
        entries = {
            "SshTerminalModule/src/main/java/com/opencgl/ssh/SshTerminalPluginUI.java":
                "SshTerminalController",
            "SshJediTermModule/src/main/java/com/opencgl/sshjedi/SshJediTermPluginUI.java":
                "SshJediTermController",
        }

        for relative_path, controller_type in entries.items():
            source = (ROOT / relative_path).read_text(encoding="utf-8")
            with self.subTest(plugin=relative_path):
                self.assertIn(f"private {controller_type} controller;", source)
                self.assertIn("controller = loader.getController();", source)
                self.assertIn("controller.dispose();", source)

    def test_controllers_and_sessions_expose_full_cleanup_chain(self):
        chains = [
            (
                "SshTerminalModule/src/main/java/com/opencgl/ssh/controller/SshTerminalController.java",
                "SshTerminalModule/src/main/java/com/opencgl/ssh/session/SshSessionTab.java",
                "sessionTab.dispose();",
            ),
            (
                "SshJediTermModule/src/main/java/com/opencgl/sshjedi/controller/SshJediTermController.java",
                "SshJediTermModule/src/main/java/com/opencgl/sshjedi/session/SshJediTermSessionTab.java",
                "sessionTab.dispose();",
            ),
        ]

        for controller_path, session_path, delegation in chains:
            controller = (ROOT / controller_path).read_text(encoding="utf-8")
            session = (ROOT / session_path).read_text(encoding="utf-8")
            with self.subTest(controller=controller_path):
                self.assertIn("public void dispose()", controller)
                self.assertIn(delegation, controller)
                self.assertIn("public void dispose()", session)
                self.assertIn("connectThread.interrupt();", session)

    def test_terminal_backends_stop_reader_threads_and_close_all_resources(self):
        ordinary_service = (ROOT / (
            "SshTerminalModule/src/main/java/com/opencgl/ssh/service/SshService.java"
        )).read_text(encoding="utf-8")
        local_service = (ROOT / (
            "SshTerminalModule/src/main/java/com/opencgl/ssh/service/LocalShellServiceImpl.java"
        )).read_text(encoding="utf-8")
        jedi_connector = (ROOT / (
            "SshJediTermModule/src/main/java/com/opencgl/sshjedi/service/JschTtyConnector.java"
        )).read_text(encoding="utf-8")

        self.assertIn("readerThread.interrupt();", ordinary_service)
        self.assertIn("readThread.interrupt();", local_service)
        self.assertIn("reader.close();", jedi_connector)
        self.assertIn("channel.disconnect();", jedi_connector)
        self.assertIn("session.disconnect();", jedi_connector)


if __name__ == "__main__":
    unittest.main()
