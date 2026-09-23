package com.opencgl.http.ui;

import com.opencgl.base.model.Base;
import com.opencgl.http.model.HttpTreeItem;
import com.opencgl.http.service.*;
import javafx.scene.Scene;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpSaveAndEnvironmentTest {
    @BeforeAll static void start() throws Exception {
        assertTrue(System.getProperty("user.home").endsWith("test-home"), "Never open the user's DB in tests");
        Files.createDirectories(Path.of(Base.DB_PATH));
        HttpDebuggerLayoutTest.start();
    }

    @Test void saveButtonPersistsChineseBodyAcrossRepositoryReload() throws Exception {
        HttpDebuggerLayoutTest.fx(() -> {
            var c = HttpUiFixture.load();
            var service = new HttpTreeService();
            var item = service.createRequest("save regression", "GET", "http://localhost/old", 0L);
            var treeSnapshot = service.queryById(item.getId());
            var treeNode = new javafx.scene.control.TreeItem<>(treeSnapshot);
            var stage = new javafx.stage.Stage();
            try {
                stage.setScene(new Scene(c.mainStackPane)); stage.show();
                HttpUiFixture.set(c, "treeService", service);
                HttpUiFixture.set(c, "treeView", new javafx.scene.control.TreeView<>(treeNode));
                var env = new EnvironmentService();
                HttpUiFixture.set(c, "environmentService", env);
                HttpUiFixture.call(c, "setupEnvironmentUI");
                c.envComboBox.setValue("production");
                HttpUiFixture.set(c, "currentTreeItem", item);
                HttpUiFixture.call(c, "bindEvents");
                c.methodComboBox.setValue("POST");
                c.urlField.setText("http://localhost/new");
                c.bodyJsonRadio.setSelected(true);
                c.bodyEditor.replaceText("{\"内容\":\"保存中文\"}");
                c.configTimeoutField.setText("30");
                c.authTypeCombo.setValue("No Auth");
                var params = (javafx.collections.ObservableList<com.opencgl.http.model.KeyValueEntry>)HttpUiFixture.get(c, "paramsData");
                var headers = (javafx.collections.ObservableList<com.opencgl.http.model.KeyValueEntry>)HttpUiFixture.get(c, "headersData");
                params.add(new com.opencgl.http.model.KeyValueEntry("param", "参数", true));
                headers.add(new com.opencgl.http.model.KeyValueEntry("X-Test", "header", true));
                c.saveButton.fire();
                var restored = new HttpTreeService().queryById(item.getId());
                assertEquals("http://localhost/new", restored.getUrl());
                assertEquals("{\"内容\":\"保存中文\"}", restored.getBody());
                assertTrue(restored.getParams().contains("参数"));
                assertTrue(restored.getHeaders().contains("header"));
                treeNode.getValue().setName("renamed after save");
                service.update(treeNode.getValue());
                assertEquals("http://localhost/new", service.queryById(item.getId()).getUrl(), "Tree rename must not overwrite saved content");
                // Tree rebuilding / refreshing the editor can leave an older DTO in the tree.
                HttpUiFixture.call(c, "onTreeNodeSelected", new Class<?>[]{HttpTreeItem.class}, treeSnapshot);
                assertEquals("http://localhost/new", c.urlField.getText(), "Reselect must not restore stale tree data");
                assertEquals("{\"内容\":\"保存中文\"}", c.bodyEditor.getText());
                assertEquals("参数", params.getFirst().getValue());
                assertEquals("header", headers.getFirst().getValue());
                assertEquals("None", c.envComboBox.getValue());
                assertEquals("None", env.getCurrentEnvName());
            } finally { stage.close(); service.delete(item); c.dispose(); }
        });
    }

    @Test void selectingFolderWithoutRequestKeepsEmptyEnvironment() throws Exception {
        HttpDebuggerLayoutTest.fx(() -> {
            var c = HttpUiFixture.load();
            var env = new EnvironmentService();
            try {
                HttpUiFixture.set(c, "environmentService", env);
                HttpUiFixture.call(c, "setupEnvironmentUI");
                c.envComboBox.setValue("production");
                var item = new HttpTreeItem(); item.setId(42L); item.setIsLeaf(false); item.setNodeType("FOLDER");
                HttpUiFixture.call(c, "onTreeNodeSelected", new Class<?>[]{HttpTreeItem.class}, item);
                assertEquals("None", c.envComboBox.getValue());
                assertEquals("None", env.getCurrentEnvName());
            } finally { c.dispose(); }
        });
    }

    @Test void savingMissingRecordMustNotReportSuccess() {
        var service = new HttpTreeService();
        var item = new HttpTreeItem();
        item.setId(-999L); item.setName("missing"); item.setIsLeaf(true); item.setNodeType("REQUEST");
        assertThrows(IllegalStateException.class, () -> service.update(item));
    }
}
