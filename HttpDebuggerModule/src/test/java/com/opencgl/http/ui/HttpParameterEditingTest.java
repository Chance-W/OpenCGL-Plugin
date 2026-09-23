package com.opencgl.http.ui;

import com.opencgl.http.controller.HttpDebuggerController;
import com.opencgl.http.service.*;
import com.opencgl.http.model.*;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpParameterEditingTest {
    @BeforeAll static void start() throws Exception { HttpDebuggerLayoutTest.start(); }
    @Test void activeCellFlushCommitsChineseWithoutChangingRowHeight() throws Exception {
        HttpDebuggerLayoutTest.fx(() -> {
            var c = HttpUiFixture.load();
            try {
                HttpUiFixture.call(c, "setupBodyFormTable");
                HttpUiFixture.call(c, "setupBodyTypeSwitching");
                c.requestTabPane.getSelectionModel().select(2);
                c.bodyFormRadio.setSelected(true);
                var entry = new KeyValueEntry("name", "old", true);
                c.bodyFormTable.getItems().setAll(entry);
                var scene = new javafx.scene.Scene(c.mainStackPane, 800, 650);
                scene.getStylesheets().add(getClass().getResource("/com/opencgl/base/css/themes/ThemeColors-dark.css").toExternalForm());
                c.mainStackPane.applyCss(); c.mainStackPane.layout();
                var cell = c.bodyFormTable.lookupAll(".table-cell").stream()
                    .filter(n -> n instanceof javafx.scene.control.TableCell<?, ?> tc
                        && tc.getIndex() == 0 && tc.getTableColumn() == c.formValueCol)
                    .map(n -> (javafx.scene.control.TableCell<?, ?>)n).findFirst().orElseThrow();
                double height = cell.getHeight();
                c.bodyFormTable.edit(0, c.formValueCol);
                c.mainStackPane.applyCss(); c.mainStackPane.layout();
                var editor = (javafx.scene.control.TextField)cell.getGraphic();
                assertNotNull(editor); editor.setText("最新中文");
                assertTrue(cell.isEditing());
                assertEquals(height, cell.getHeight(), 0.5);
                HttpUiFixture.call(c, "flushBulkEditors");
                assertEquals("最新中文", entry.getValue());
            } finally { c.dispose(); }
        });
    }
    @Test void formTableFitsAndToolbarRemovesOnlySelectedEditableRows() throws Exception {
        HttpDebuggerLayoutTest.fx(() -> {
            var c = HttpUiFixture.load();
            try {
                HttpUiFixture.call(c, "setupBodyFormTable");
                HttpUiFixture.call(c, "bindEvents");
                assertEquals(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN,
                        c.bodyFormTable.getColumnResizePolicy());
                var editable = new KeyValueEntry("name", "中文", true);
                var locked = new KeyValueEntry("locked", "value", true); locked.setReadOnly(true);
                c.bodyFormTable.getItems().setAll(editable, locked);
                c.bodyFormTable.getSelectionModel().selectAll();
                var remove = c.removeFormButton;
                assertNotNull(remove);
                remove.fire();
                assertEquals(java.util.List.of(locked), c.bodyFormTable.getItems());
            } finally { c.dispose(); }
        });
    }
    @Test void switchingBodyTypePreservesDraftAndCommitsBulkForm() throws Exception {
        HttpDebuggerLayoutTest.fx(() -> {
            var c = HttpUiFixture.load();
            try {
                HttpUiFixture.call(c, "setupBodyTypeSwitching");
                c.bodyJsonRadio.setSelected(true); c.bodyEditor.replaceText("{\"中文\":1}");
                c.bodyNoneRadio.setSelected(true); c.bodyJsonRadio.setSelected(true);
                assertEquals("{\"中文\":1}", c.bodyEditor.getText());
                c.bodyFormRadio.setSelected(true);
                c.formBulkEditor.setVisible(true); c.formBulkEditor.replaceText("name: 中文内容");
                c.bodyJsonRadio.setSelected(true);
                var entries = (ObservableList<KeyValueEntry>)HttpUiFixture.get(c, "bodyFormData");
                assertEquals("中文内容", entries.getFirst().getValue());
            } finally { c.dispose(); }
        });
    }
    @Test void sendUsesUnblurredBulkValuesAtRealLoopbackServer() throws Exception {
        var received = new ArrayBlockingQueue<String>(1);
        var recorded = new Semaphore(0);
        var receivedHeaders = new ArrayBlockingQueue<com.sun.net.httpserver.Headers>(8);
        var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            receivedHeaders.offer(exchange.getRequestHeaders());
            received.offer(exchange.getRequestURI().getRawQuery() + "|" +
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, 2); exchange.getResponseBody().write("OK".getBytes(StandardCharsets.UTF_8)); exchange.close();
        });
        server.start();
        var controller = new AtomicReference<HttpDebuggerController>();
        try {
            HttpDebuggerLayoutTest.fx(() -> {
                var c = HttpUiFixture.load(); controller.set(c);
                var env = mock(EnvironmentService.class);
                when(env.substitute(anyString())).thenAnswer(a -> a.getArgument(0));
                HttpUiFixture.set(c, "environmentService", env);
                var history = mock(HttpDebuggerHistoryService.class);
                doAnswer(a -> { recorded.release(); return null; }).when(history).add(any(), any());
                HttpUiFixture.set(c, "historyService", history);
                var client = new HttpClientService(); client.getProxyConfigService().getConfig().setEnabled(false);
                HttpUiFixture.set(c, "httpClientService", client);
                HttpUiFixture.set(c, "jsonFormatterService", new JsonFormatterService());
                c.methodComboBox.setValue("POST"); c.urlField.setText("http://127.0.0.1:" + server.getAddress().getPort() + "/echo");
                c.configTimeoutField.setText("5"); c.bodyFormRadio.setSelected(true);
                c.paramsBulkEditor.setVisible(true); c.paramsBulkEditor.replaceText("q: new");
                c.formBulkEditor.setVisible(true); c.formBulkEditor.replaceText("name: 中文");
                HttpUiFixture.call(c, "sendRequest");
            });
            assertEquals("q=new|name=%E4%B8%AD%E6%96%87", received.poll(10, TimeUnit.SECONDS));
            assertTrue(recorded.tryAcquire(10, TimeUnit.SECONDS));
            receivedHeaders.poll(10, TimeUnit.SECONDS);
            HttpDebuggerLayoutTest.fx(() -> {
                var c = controller.get();
                HttpUiFixture.call(c, "setupBodyTypeSwitching");
                c.bodyJsonRadio.setSelected(true);
                c.bodyEditor.replaceText("{\"草稿\":true}");
                c.bodyNoneRadio.setSelected(true);
                HttpUiFixture.call(c, "sendRequest");
            });
            assertEquals("q=new|", received.poll(10, TimeUnit.SECONDS), "NONE must not send retained draft");
            assertTrue(recorded.tryAcquire(10, TimeUnit.SECONDS));
            receivedHeaders.poll(10, TimeUnit.SECONDS);
            HttpDebuggerLayoutTest.fx(() -> {
                var c = controller.get(); c.bodyJsonRadio.setSelected(true);
                assertEquals("{\"草稿\":true}", c.bodyEditor.getText());
            });
            for (String type : java.util.List.of("JSON", "XML", "RAW")) {
                String body = type.equals("JSON") ? "{\"中文\":123}" : type.equals("XML") ? "<值>中文</值>" : "中文原文";
                HttpDebuggerLayoutTest.fx(() -> {
                    var c = controller.get();
                    if (type.equals("JSON")) c.bodyJsonRadio.setSelected(true);
                    else if (type.equals("XML")) c.bodyXmlRadio.setSelected(true);
                    else c.bodyRawRadio.setSelected(true);
                    c.bodyEditor.replaceText(body);
                    var headers = (ObservableList<KeyValueEntry>)HttpUiFixture.get(c, "headersData");
                    headers.setAll(new KeyValueEntry("X-Test", "edited-value", true));
                    HttpUiFixture.call(c, "sendRequest");
                });
                assertEquals("q=new|" + body, received.poll(10, TimeUnit.SECONDS));
                assertEquals("edited-value", receivedHeaders.poll(10, TimeUnit.SECONDS).getFirst("X-Test"));
                assertTrue(recorded.tryAcquire(10, TimeUnit.SECONDS));
            }
        } finally {
            HttpDebuggerLayoutTest.fx(() -> { if (controller.get() != null) controller.get().dispose(); });
            server.stop(0);
        }
    }
}
