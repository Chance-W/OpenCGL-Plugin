package com.opencgl.http.ui;

import com.opencgl.http.controller.HttpDebuggerController;
import com.opencgl.http.model.KeyValueEntry;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import java.net.URL;
import java.util.*;

final class HttpUiFixture {
    static HttpDebuggerController load() {
        try {
            var loader = new FXMLLoader(HttpUiFixture.class.getResource("/com/opencgl/http/views/HttpDebuggerView.fxml"),
                    ResourceBundle.getBundle("com.opencgl.http.i18n.HttpDebugger", Locale.ENGLISH));
            loader.setControllerFactory(t -> new HttpDebuggerController() {
                @Override public void initialize(URL location, ResourceBundle bundle) { }
            });
            loader.load();
            HttpDebuggerController c = loader.getController();
            for (String field : List.of("paramsData", "headersData", "bodyFormData", "responseHeadersData"))
                set(c, field, FXCollections.<KeyValueEntry>observableArrayList());
            c.emptyState.setVisible(false); c.emptyState.setManaged(false);
            c.requestPanel.setVisible(true); c.requestPanel.setManaged(true);
            return c;
        } catch (Exception e) { throw new AssertionError(e); }
    }
    static void set(Object c, String field, Object value) {
        try { var f = HttpDebuggerController.class.getDeclaredField(field); f.setAccessible(true); f.set(c, value); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    static Object get(Object c, String field) {
        try { var f = HttpDebuggerController.class.getDeclaredField(field); f.setAccessible(true); return f.get(c); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    static Object call(Object c, String name, Class<?>[] types, Object... args) {
        try { var m = HttpDebuggerController.class.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(c, args); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    static Object call(Object c, String name) { return call(c, name, new Class<?>[0]); }
}
