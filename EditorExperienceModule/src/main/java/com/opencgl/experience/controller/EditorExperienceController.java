package com.opencgl.experience.controller;

import com.opencgl.experience.controls.MonacoEditorWrapper;
import com.opencgl.experience.controls.MonacoWebViewManager;
import com.opencgl.experience.controls.RSyntaxEditorWrapper;
import com.opencgl.experience.controls.RichTextEditorWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;

public class EditorExperienceController {

    @FXML
    private RadioButton rbRichText;
    @FXML
    private RadioButton rbMonaco;
    @FXML
    private RadioButton rbRSyntax;
    @FXML
    private ToggleGroup groupEditor;
    @FXML
    private ComboBox<String> comboLanguage;
    @FXML
    private ComboBox<String> comboTheme;
    @FXML
    private Button btnFormat;
    @FXML
    private StackPane editorContainer;

    private RichTextEditorWrapper richTextEditor;
    private MonacoEditorWrapper monacoEditor;
    private RSyntaxEditorWrapper rSyntaxEditor;

    private Node activeEditor;
    private boolean disposed;

    private static final String DEFAULT_CODE = 
        "public class Example {\n" +
        "    public static void main(String[] args) {\n" +
        "        System.out.println(\"Hello World\");\n" +
        "    }\n" +
        "}";

    @FXML
    public void initialize() {
        // Initialize Editors
        richTextEditor = new RichTextEditorWrapper();
        monacoEditor = new MonacoEditorWrapper();
        rSyntaxEditor = new RSyntaxEditorWrapper();

        // Setup Language Combo
        comboLanguage.getItems().addAll("Java", "XML", "JSON", "JavaScript", "HTML", "Groovy");
        comboLanguage.setValue("Java");
        comboLanguage.setOnAction(e -> updateLanguage());
        
        // Setup Theme Combo
        comboTheme.getItems().addAll("Dark", "Light");
        comboTheme.setValue("Dark"); // Default
        comboTheme.setOnAction(e -> updateTheme());

        // Setup Editor Switching
        groupEditor.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == rbRichText) {
                switchEditor(richTextEditor);
            } else if (newVal == rbMonaco) {
                switchEditor(monacoEditor);
            } else if (newVal == rbRSyntax) {
                switchEditor(rSyntaxEditor);
            }
        });

        // Setup Format Button
        btnFormat.setOnAction(e -> formatCurrent());

        // Initial State
        richTextEditor.setText(DEFAULT_CODE);
        switchEditor(richTextEditor);
        // Apply initial theme
        updateTheme(); 
    }

    private void switchEditor(Node nextEditor) {
        String currentContent = null;
        
        // Get content from current editor
        if (activeEditor instanceof RichTextEditorWrapper) {
            currentContent = ((RichTextEditorWrapper) activeEditor).getText();
        } else if (activeEditor instanceof MonacoEditorWrapper) {
            currentContent = ((MonacoEditorWrapper) activeEditor).getValue();
        } else if (activeEditor instanceof RSyntaxEditorWrapper) {
            currentContent = ((RSyntaxEditorWrapper) activeEditor).getText();
        } else {
            currentContent = DEFAULT_CODE;
        }

        // Set content to next editor
        if (nextEditor instanceof RichTextEditorWrapper) {
            ((RichTextEditorWrapper) nextEditor).setText(currentContent);
        } else if (nextEditor instanceof MonacoEditorWrapper) {
             // Use safer set method if possible or handle sync
            ((MonacoEditorWrapper) nextEditor).setSafeValue(currentContent);
            ((MonacoEditorWrapper) nextEditor).onAttach(); // Attach Shared WebView
        } else if (nextEditor instanceof RSyntaxEditorWrapper) {
            ((RSyntaxEditorWrapper) nextEditor).setText(currentContent);
        }
        
        // Handle Detach for old editor if it was Monaco
        if (activeEditor instanceof MonacoEditorWrapper && activeEditor != nextEditor) {
            ((MonacoEditorWrapper) activeEditor).onDetach();
        }

        // Update UI
        editorContainer.getChildren().clear();
        editorContainer.getChildren().add(nextEditor);
        activeEditor = nextEditor;
        
        // Sync Language & Theme
        updateLanguage();
        updateTheme();
    }

    private void updateLanguage() {
        String lang = comboLanguage.getValue();
        if (activeEditor instanceof RichTextEditorWrapper) {
            ((RichTextEditorWrapper) activeEditor).setLanguage(lang);
        } else if (activeEditor instanceof MonacoEditorWrapper) {
            ((MonacoEditorWrapper) activeEditor).setLanguage(lang);
        } else if (activeEditor instanceof RSyntaxEditorWrapper) {
            ((RSyntaxEditorWrapper) activeEditor).setLanguage(lang);
        }
    }
    
    private void updateTheme() {
        String theme = comboTheme.getValue();
        boolean isDark = "Dark".equalsIgnoreCase(theme);
        
        if (activeEditor instanceof RichTextEditorWrapper) {
            ((RichTextEditorWrapper) activeEditor).setTheme(isDark);
        } else if (activeEditor instanceof MonacoEditorWrapper) {
            ((MonacoEditorWrapper) activeEditor).setTheme(isDark);
        } else if (activeEditor instanceof RSyntaxEditorWrapper) {
            ((RSyntaxEditorWrapper) activeEditor).setTheme(isDark);
        }
    }

    private void formatCurrent() {
         if (activeEditor instanceof RichTextEditorWrapper) {
            ((RichTextEditorWrapper) activeEditor).format();
        } else if (activeEditor instanceof MonacoEditorWrapper) {
            ((MonacoEditorWrapper) activeEditor).format();
        } else if (activeEditor instanceof RSyntaxEditorWrapper) {
            ((RSyntaxEditorWrapper) activeEditor).format();
        }
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        try {
            if (monacoEditor != null) monacoEditor.onDetach();
        } catch (Exception ignored) {
        }
        try {
            MonacoWebViewManager.disposeInstance();
        } catch (Exception ignored) {
        }
        if (comboLanguage != null) comboLanguage.setOnAction(null);
        if (comboTheme != null) comboTheme.setOnAction(null);
        if (btnFormat != null) btnFormat.setOnAction(null);
        if (editorContainer != null) editorContainer.getChildren().clear();
        activeEditor = null;
        richTextEditor = null;
        monacoEditor = null;
        rSyntaxEditor = null;
    }
}
