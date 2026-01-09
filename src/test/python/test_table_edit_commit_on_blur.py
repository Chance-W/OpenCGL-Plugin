from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[3]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


class TableEditCommitOnBlurContractTest(unittest.TestCase):
    def test_shared_cell_commits_cancel_caused_by_external_focus_change(self):
        code = read("OpenCGL-Base/src/main/java/com/opencgl/base/utils/CommitOnBlurTableCell.java")
        self.assertIn("public void cancelEdit()", code)
        self.assertIn("commitEditorValue()", code)
        self.assertIn("explicitCancel", code)
        self.assertIn("KeyCode.ESCAPE", code)

    def test_http_debugger_uses_shared_cell_for_all_editable_tables(self):
        controller = read("HttpDebuggerModule/src/main/java/com/opencgl/http/controller/HttpDebuggerController.java")
        dialog = read("HttpDebuggerModule/src/main/java/com/opencgl/http/controller/HttpDebuggerEnvConfigureDialog.java")
        self.assertGreaterEqual(controller.count("CommitOnBlurTableCell.forStringColumn"), 4)
        self.assertGreaterEqual(dialog.count("CommitOnBlurTableCell.forStringColumn"), 2)
        self.assertNotIn("CommitOnBlurTableCellFactory", controller)

    def test_ftp_uses_commit_on_blur_cells(self):
        code = read("FtpToolModule/src/main/java/com/opencgl/ftp/controller/FtpServerController.java")
        self.assertGreaterEqual(code.count("CommitOnBlurTableCell.forStringColumn"), 3)

    def test_document_media_directory_commits_when_table_cancels_edit(self):
        code = read("DocToMarkdownModule/src/main/java/com/opencgl/doctomd/controller/DocToMarkdownController.java")
        self.assertIn("CommitOnBlurTableCell.forStringColumn", code)

    def test_legacy_table_and_tree_cells_commit_before_implicit_cancel(self):
        table_cell = read("OpenCGL-Base/src/main/java/com/opencgl/base/utils/EditingCell.java")
        tree_cell = read("RestTestModule/src/main/java/com/opencgl/util/EditableTextFieldTreeTableCell.java")
        self.assertIn("if (isEditing() && !explicitCancel)", table_cell)
        self.assertIn("if (isEditing() && !explicitCancel)", tree_cell)

    def test_dubbo_tree_rename_distinguishes_blur_from_escape(self):
        code = read("DubboMockModule/src/main/java/com/opencgl/dubbo/mock/controller/DubboMockController.java")
        self.assertIn("if (isEditing() && !explicitCancel)", code)
        self.assertIn("if (!isFocused && !explicitCancel)", code)


if __name__ == "__main__":
    unittest.main()
