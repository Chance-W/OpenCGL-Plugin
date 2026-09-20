package com.opencgl.decompiler.controller;

import com.opencgl.decompiler.service.SymbolNavigationService.Result;
import javafx.animation.PauseTransition;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.*;
import javafx.util.Duration;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import java.util.Collection;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Mouse hit-testing and feedback are independent of semantic resolution. */
final class EditorNavigation {
    @FunctionalInterface interface Resolver { void resolve(int offset, Consumer<Result> result); }
    private final CodeArea editor;
    private final Resolver resolver;
    private final Consumer<Integer> navigate;
    private final PauseTransition hoverDelay = new PauseTransition(Duration.millis(180));
    private final Tooltip tooltip = new Tooltip();
    private final ContextMenu menu = new ContextMenu();
    private long generation;
    private int start = -1, end;
    private StyleSpans<Collection<String>> savedStyle;

    EditorNavigation(CodeArea editor, Resolver resolver, Consumer<Integer> navigate) {
        this.editor = editor; this.resolver = resolver; this.navigate = navigate;
        editor.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.isShortcutDown()) {
                int offset = hit(e.getX(), e.getY());
                clear();
                if (offset >= 0) navigate.accept(offset);
                e.consume();
            }
        });
        editor.setOnMouseMoved(e -> {
            if (!e.isShortcutDown()) { clear(); return; }
            int offset = hit(e.getX(), e.getY());
            if (offset >= start && offset < end && start >= 0) return;
            clear();
            if (offset < 0) return;
            String text = editor.getText();
            if (!Character.isJavaIdentifierPart(text.charAt(offset))) return;
            start = offset; end = offset + 1;
            while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start-1))) start--;
            while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) end++;
            long request = generation;
            hoverDelay.setOnFinished(event -> resolver.resolve(offset, result -> {
                if (request != generation || result.targets().isEmpty() || start < 0) return;
                savedStyle = editor.getStyleSpans(start, end);
                editor.setStyleSpans(start, savedStyle.mapStyles(style -> {
                    var styles = new ArrayList<>(style); styles.add("symbol-link"); return styles;
                }));
                editor.setCursor(Cursor.HAND);
                tooltip.setText(result.targets().size() == 1 ? "跳转到定义\n" + result.targets().get(0).label()
                        : "存在 " + result.targets().size() + " 个定义，点击选择来源");
                Tooltip.install(editor, tooltip);
            }));
            hoverDelay.playFromStart();
        });
        editor.setOnMouseExited(e -> clear());
        editor.addEventFilter(KeyEvent.KEY_RELEASED, e -> { if (!e.isShortcutDown()) clear(); });
        editor.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isShortcutDown() && e.getCode() == KeyCode.B) {
                clear(); navigate.accept(caretOffset()); e.consume();
            }
        });
        editor.setOnContextMenuRequested(e -> {
            clear(); menu.hide();
            int offset = e.isKeyboardTrigger() ? caretOffset() : hit(e.getX(), e.getY());
            MenuItem jump = new MenuItem("跳转到定义 / 当前类引用");
            jump.setDisable(offset < 0);
            jump.setOnAction(event -> navigate.accept(offset));
            MenuItem copy = new MenuItem("复制");
            copy.setDisable(editor.getSelectedText().isEmpty());
            copy.setOnAction(event -> editor.copy());
            menu.getItems().setAll(jump, copy);
            menu.show(editor, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        editor.focusedProperty().addListener((obs, old, focused) -> { if (!focused) clear(); });
    }

    private int hit(double x, double y) {
        int offset = editor.hit(x, y).getCharacterIndex().orElse(-1);
        return offset >= 0 && offset < editor.getLength() ? offset : -1;
    }
    private int caretOffset() {
        int offset = editor.getCaretPosition();
        if (offset > 0 && (offset == editor.getLength() || !Character.isJavaIdentifierPart(editor.getText().charAt(offset)))) offset--;
        return offset;
    }
    void clear() {
        generation++; hoverDelay.stop();
        Tooltip.uninstall(editor, tooltip); tooltip.hide(); editor.setCursor(Cursor.TEXT);
        if (savedStyle != null && end <= editor.getLength()) editor.setStyleSpans(start, savedStyle);
        savedStyle = null; start = -1; end = 0;
    }
    void dispose() { clear(); menu.hide(); }
}
