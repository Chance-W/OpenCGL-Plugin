package com.opencgl.http.ui;

import com.opencgl.http.model.HttpHistoryItem;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpHistoryPresentationTest {
    @BeforeAll static void start() throws Exception { HttpDebuggerLayoutTest.start(); }
    @Test void viewingKeepsDraftAndExplicitRestoreWorksWithoutCollectionSelection() throws Exception {
        HttpDebuggerLayoutTest.fx(() -> {
            var c = HttpUiFixture.load();
            try {
                HttpUiFixture.call(c, "bindEvents");
                HttpUiFixture.call(c, "setupBodyTypeSwitching");
                c.urlField.setText("http://draft");
                c.bodyJsonRadio.setSelected(true);
                c.bodyEditor.replaceText("draft");
                var history = new HttpHistoryItem();
                history.setRequestSnapshot("{\"method\":\"POST\",\"url\":\"http://history\",\"bodyType\":\"JSON\",\"body\":\"历史\"}");
                HttpUiFixture.call(c, "onHistorySelected", new Class<?>[]{HttpHistoryItem.class}, history);
                assertEquals("http://draft", c.urlField.getText());
                assertEquals("draft", c.bodyEditor.getText());
                assertFalse(c.historyFullTextArea.isEditable());
                c.restoreHistoryButton.fire();
                assertEquals("http://history", c.urlField.getText());
                assertEquals("历史", c.bodyEditor.getText());
                assertTrue(c.requestPanel.isVisible());
                assertFalse(c.historyDetailPane.isVisible());
            } finally { c.dispose(); }
        });
    }
}
