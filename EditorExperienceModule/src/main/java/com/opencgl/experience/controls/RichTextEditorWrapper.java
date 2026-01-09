package com.opencgl.experience.controls;

import com.google.googlejavaformat.java.Formatter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class RichTextEditorWrapper extends StackPane {

    private final CodeArea codeArea;
    private String currentLang = "java";

    private static final String[] JAVA_KEYWORDS = new String[] {
            "abstract", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else",
            "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", JAVA_KEYWORDS) + ")\\b";
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = "\\;";
    private static final String STRING_PATTERN = "\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";

    private static final Pattern JAVA_PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<PAREN>" + PAREN_PATTERN + ")"
                    + "|(?<BRACE>" + BRACE_PATTERN + ")"
                    + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                    + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
    );

    public RichTextEditorWrapper() {
        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        
        // Load CSS
        codeArea.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/com/opencgl/editor/experience/css/rich-editor.css")).toExternalForm());
        
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            codeArea.setStyleSpans(0, computeHighlighting(newText));
            highlightError(newText);
        });

        getChildren().add(codeArea);
        setupCommentShortcuts();
    }
    
    public void setTheme(boolean isDark) {
        codeArea.getStylesheets().clear();
        if (isDark) {
             codeArea.getStylesheets().add(getClass().getResource("/com/opencgl/editor/experience/css/rich-editor-dark.css").toExternalForm());
        } else {
             codeArea.getStylesheets().add(getClass().getResource("/com/opencgl/editor/experience/css/rich-editor.css").toExternalForm());
        }
    }

    public void setText(String text) {
        codeArea.replaceText(0, codeArea.getLength(), text);
    }

    public String getText() {
        return codeArea.getText();
    }

    public void setLanguage(String lang) {
        this.currentLang = lang.toLowerCase();
        // Re-apply highlighting when language changes
        String text = codeArea.getText();
        codeArea.setStyleSpans(0, computeHighlighting(text));
        highlightError(text);
    }

    public void format() {
        String content = getText();
        if (content == null || content.isEmpty()) return;

        String formatted = content;
        try {
            switch(currentLang) {
                case "java":
                    formatted = new Formatter().formatSource(content);
                    break;
                case "json":
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    Object json = gson.fromJson(content, Object.class);
                    formatted = gson.toJson(json);
                    break;
                 case "xml":
                    formatted = formatXml(content);
                    break;
            }
        } catch (Throwable e) {
            System.err.println("Formatting failed: " + e.getMessage());
            if (e instanceof IllegalAccessError) {
                System.err.println("To fix this, add the following VM Options:\n" +
                        "--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED\n" +
                        "--add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED\n" +
                        "--add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED\n" +
                        "--add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED\n" +
                        "--add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
            } else {
                e.printStackTrace();
            }
            return;
        }
        
        int caret = codeArea.getCaretPosition();
        codeArea.replaceText(0, codeArea.getLength(), formatted);
        codeArea.moveTo(Math.min(caret, codeArea.getLength()));
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = JAVA_PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while(matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                    matcher.group("PAREN") != null ? "paren" :
                    matcher.group("BRACE") != null ? "brace" :
                    matcher.group("BRACKET") != null ? "bracket" :
                    matcher.group("SEMICOLON") != null ? "semicolon" :
                    matcher.group("STRING") != null ? "string" :
                    matcher.group("COMMENT") != null ? "comment" :
                    "";
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
    
    private void highlightError(String text) {
        if ("json".equals(currentLang)) {
            try {
                new Gson().fromJson(text, Object.class);
            } catch (com.google.gson.JsonSyntaxException e) {
                 int errorIdx = 0;
                 // Try to extract index from message (Gson usually provides "at line X column Y")
                 // But we have raw text, line/col mapping is manual.
                 // Simple regex for "line (\d+) column (\d+)"
                 try {
                     Matcher m = Pattern.compile("line (\\d+) column (\\d+)").matcher(e.getMessage());
                     if (m.find()) {
                         int line = Integer.parseInt(m.group(1)) - 1;
                         int col = Integer.parseInt(m.group(2)) - 1; 
                         // Map to absolute index? RichTextFX has getAbsolutePosition(paragraph, col)
                         if (line < codeArea.getParagraphs().size()) {
                             int lineLen = codeArea.getParagraph(line).length();
                             if (col > lineLen) col = lineLen; // clamp
                             
                             errorIdx = codeArea.getAbsolutePosition(line, col);
                             // Highlight just that char or a few chars
                             int end = Math.min(errorIdx + 1, codeArea.getLength());
                             if (errorIdx < end) {
                                 codeArea.setStyle(errorIdx, end, Collections.singleton("error"));
                             }
                         }
                     }
                 } catch(Exception ignore) {}
            }
        }
    }
    
    private String formatXml(String input) {
        try {
            Source xmlInput = new StreamSource(new StringReader(input));
            StringWriter stringWriter = new StringWriter();
            StreamResult xmlOutput = new StreamResult(stringWriter);
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer(); 
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(xmlInput, xmlOutput);
            return xmlOutput.getWriter().toString();
        } catch (Exception e) {
            return input;
        }
    }
    
    private void setupCommentShortcuts() {
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
             if (event.getCode() == KeyCode.ENTER) {
                 // Smart Indent (Basic) - could be expanded
             }
             
             // Auto-Completion Trigger (Ctrl+Space or Cmd+Space)
             if ((event.isControlDown() || event.isMetaDown()) && event.getCode() == KeyCode.SPACE) {
                 showAutoCompletion();
                 event.consume();
             }
        });

        codeArea.setOnKeyPressed(event -> {
            if ((event.isControlDown() || event.isMetaDown()) && !event.isShiftDown() &&
                (event.getCode() == KeyCode.SLASH || event.getText().equals("/"))) {
                event.consume();
                toggleLineComment();
            }
            else if ((event.isControlDown() || event.isMetaDown()) && event.isShiftDown() &&
                     (event.getCode() == KeyCode.SLASH || event.getText().equals("/"))) {
                event.consume();
                toggleBlockComment();
            }
             else if ((event.isControlDown() || event.isMetaDown()) && event.isAltDown() && event.getCode() == KeyCode.L) {
                 event.consume();
                 format();
             }
        });
        
        setupSmartClosing();
    }
    
    private void setupSmartClosing() {
        codeArea.setOnKeyTyped(event -> {
            String ch = event.getCharacter();
            if (ch.equals("{")) {
                codeArea.insertText(codeArea.getCaretPosition(), "}");
                codeArea.moveTo(codeArea.getCaretPosition() - 1);
            } else if (ch.equals("\"")) {
                 int caret = codeArea.getCaretPosition();
                 // Check if next char is already "
                 if (caret < codeArea.getLength()) {
                     String nextChar = codeArea.getText(caret, caret + 1);
                     if (nextChar.equals("\"")) {
                         // Overtype
                         event.consume();
                         codeArea.moveTo(caret + 1);
                         return;
                     }
                 }
                 codeArea.insertText(caret, "\"");
                 codeArea.moveTo(caret); // Move back inside
            } else if (ch.equals("(") ) {
                 codeArea.insertText(codeArea.getCaretPosition(), ")");
                 codeArea.moveTo(codeArea.getCaretPosition() - 1);
            } else if (ch.equals("[") ) {
                 codeArea.insertText(codeArea.getCaretPosition(), "]");
                 codeArea.moveTo(codeArea.getCaretPosition() - 1);
            }
            
            // Auto-trigger completion on typing (if valid char)
            if (!event.isControlDown() && !event.isMetaDown() && !event.isAltDown() && ch.matches("[a-zA-Z_.]")) {
                 Platform.runLater(this::showAutoCompletion);
            }
        });
    }

    private javafx.scene.control.ContextMenu autocompletePopup;
    private void showAutoCompletion() {
        if (autocompletePopup != null && autocompletePopup.isShowing()) return;

        int caret = codeArea.getCaretPosition();
        if (caret == 0) return;
        
        // Find word start
        int start = caret - 1;
        String text = codeArea.getText();
        while (start >= 0 && (Character.isJavaIdentifierPart(text.charAt(start)) || text.charAt(start) == '.')) {
            start--;
        }
        start++;
        
        if (start >= caret) return; // Nothing to complete
        
        String prefix = text.substring(start, caret);
        if (prefix.isEmpty()) return;
        
        java.util.List<String> suggestions = new java.util.ArrayList<>();
        for (String kw : JAVA_KEYWORDS) {
            if (kw.startsWith(prefix)) suggestions.add(kw);
        }
        // Basic System completion
        if ("System.out.println".startsWith(prefix)) suggestions.add("System.out.println");
        if ("String".startsWith(prefix)) suggestions.add("String");
        
        if (suggestions.isEmpty()) return;
        
        final int replacementStart = start;
        final int replacementEnd = caret;

        autocompletePopup = new ContextMenu();
        for (String suggestion : suggestions) {
            MenuItem item = new MenuItem(suggestion);
            item.setOnAction(e -> {
                 codeArea.replaceText(replacementStart, replacementEnd, suggestion);
            });
            autocompletePopup.getItems().add(item);
        }
        
        codeArea.getCharacterBoundsOnScreen(replacementStart, replacementEnd).ifPresent(bounds -> {
            autocompletePopup.show(codeArea, bounds.getMinX(), bounds.getMaxY());
        });
    }

    private void toggleLineComment() {
        // Get selection range
        int start = codeArea.getSelection().getStart();
        int end = codeArea.getSelection().getEnd();
        
        // Find paragraph indices for start and end
        int startPara = codeArea.offsetToPosition(start, org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
        int endPara = codeArea.offsetToPosition(end, org.fxmisc.richtext.model.TwoDimensional.Bias.Backward).getMajor();

        // Get the full text range for these paragraphs
        int blockStart = codeArea.getAbsolutePosition(startPara, 0);
        int blockEnd = codeArea.getAbsolutePosition(endPara, codeArea.getParagraph(endPara).length());
        
        String blockText = codeArea.getText(blockStart, blockEnd);
        // Split by newline but keep empty lines structure if needed? 
        // Actually split will eat empty trailing strings if limit is not negative.
        String[] lines = blockText.split("\n", -1);
        
        StringBuilder newBlock = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            
            if (trimmed.startsWith("//")) {
                // Uncomment
                int slashIdx = line.indexOf("//");
                if (slashIdx != -1) {
                    int offset = 2; 
                    if (slashIdx + 2 < line.length() && line.charAt(slashIdx+2) == ' ') {
                        offset = 3; 
                    }
                    newBlock.append(line.substring(0, slashIdx)).append(line.substring(slashIdx + offset));
                } else {
                    newBlock.append(line);
                }
            } else {
                // Comment
                int firstNonSplit = 0;
                while (firstNonSplit < line.length() && Character.isWhitespace(line.charAt(firstNonSplit))) {
                    firstNonSplit++;
                }
                newBlock.append(line.substring(0, firstNonSplit)).append("// ").append(line.substring(firstNonSplit));
            }
            
            if (i < lines.length - 1) {
                newBlock.append("\n");
            }
        }
        
        // Atomic replace - One Undo step!
        codeArea.replaceText(blockStart, blockEnd, newBlock.toString());
        
        // Restore selection coverage
        codeArea.selectRange(blockStart, blockStart + newBlock.length());
    }

    private void toggleBlockComment() {
        String text = codeArea.getText();
        int start = codeArea.getSelection().getStart();
        int end = codeArea.getSelection().getEnd();
        
        if (start == end) return; 
        
        String selected = text.substring(start, end);
        if (selected.trim().startsWith("/*") && selected.trim().endsWith("*/")) {
             String stripped = selected.trim();
             stripped = stripped.substring(2, stripped.length()-2).trim();
             codeArea.replaceText(start, end, stripped);
        } else {
             codeArea.replaceText(start, end, "/* " + selected + " */");
        }
    }
}
