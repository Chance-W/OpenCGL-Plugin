package com.opencgl.experience.controls;

import com.google.googlejavaformat.java.Formatter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.embed.swing.SwingNode;
import javafx.scene.layout.StackPane;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class RSyntaxEditorWrapper extends StackPane {

    private final SwingNode swingNode;
    private RSyntaxTextArea textArea;
    private String currentLang = "java";

    public RSyntaxEditorWrapper() {
        swingNode = new SwingNode();
        createSwingContent(swingNode);
        getChildren().add(swingNode);
    }

    private void createSwingContent(final SwingNode swingNode) {
        SwingUtilities.invokeLater(() -> {
            textArea = new RSyntaxTextArea(20, 60);
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
            textArea.setCodeFoldingEnabled(true);
            textArea.setAntiAliasingEnabled(true);
            
            // Enable Auto-Closing Brackets/Tags
            textArea.setCloseCurlyBraces(true);
            textArea.setCloseMarkupTags(true);
            // Supported in RSTA 2.6.0+
            try { textArea.getClass().getMethod("setCloseQuote", boolean.class).invoke(textArea, true); } catch(Exception e) {}
            textArea.setClearWhitespaceLinesEnabled(false);
            
             // 1. Enable Code Completion
            CompletionProvider provider = createCompletionProvider();
            AutoCompletion ac = new AutoCompletion(provider);
            ac.setAutoActivationEnabled(true);
            ac.setAutoActivationDelay(300);
            ac.install(textArea);

            // 2. Enable Error Squiggles (Parsers managed by setLanguage)

            InputMap im = textArea.getInputMap();
            ActionMap am = textArea.getActionMap();
            int mod = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

            // [Fix] Add aggressive KeyListener to force-consume KEY_TYPED on Mac
            // This is required because SwingNode on Mac sometimes leaks the typed char even if Action is triggered
            textArea.addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                   // If Command is down and char is '/' or '?' (implied shift+/), consume it!
                   // On Mac, Meta is Command.
                   if (e.isMetaDown() && (e.getKeyChar() == '/' || e.getKeyChar() == '?')) {
                       e.consume(); 
                   }
                }
            });

            // Toggle Comment (Standard Cross-Platform binding)
            // We explicitly bind Cmd+/ (Mac) or Ctrl+/ (Win) to Custom Action.
            // Using a custom key ensures we don't rely on internal RSTA naming.
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, mod), "Custom.ToggleComment");
            am.put("Custom.ToggleComment", new RSyntaxTextAreaEditorKit.ToggleCommentAction());
            
            // Block Comment (Ctrl+Shift+/) - Custom Action
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, mod | InputEvent.SHIFT_DOWN_MASK), "Custom.BlockComment");
            am.put("Custom.BlockComment", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    toggleBlockComment();
                }
            });

            // Format (Ctrl+Alt+L on Windows/Linux, Cmd+Alt+L on Mac)
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, mod | InputEvent.ALT_DOWN_MASK), "Format");
            am.put("Format", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                     performFormatOnEDT();
                }
            });

            // Set Dark Theme if possible (mimic)
            // Deferred to ensure component handles are ready, or handle gracefully
            applyThemeSafe(true); // Default to Dark if preferred, or load default

            RTextScrollPane sp = new RTextScrollPane(textArea);
            sp.setFoldIndicatorEnabled(true);
            swingNode.setContent(sp);
        });
    }
    
    private void applyThemeSafe(boolean isDark) {
        SwingUtilities.invokeLater(() -> {
            if (textArea == null) return;
            
            // If not displayable, Theme.apply() might crash on getGraphics()
            if (!textArea.isDisplayable()) {
                 // Register listener to apply once displayable
                 textArea.addHierarchyListener(new HierarchyListener() {
                     @Override
                     public void hierarchyChanged(HierarchyEvent e) {
                         if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                                 && textArea.isDisplayable()) {
                             textArea.removeHierarchyListener(this);
                             applyThemeSafe(isDark);
                         }
                     }
                 });
                 return;
            }

            try {
                String themePath = isDark ? 
                    "/org/fife/ui/rsyntaxtextarea/themes/dark.xml" : 
                    "/org/fife/ui/rsyntaxtextarea/themes/default.xml";
                Theme theme = Theme.load(getClass().getResourceAsStream(themePath));
                theme.apply(textArea);
            } catch (Exception e) {
                // Fallback style if Theme fails
                textArea.setBackground(isDark ? Color.DARK_GRAY : Color.WHITE);
                textArea.setForeground(isDark ? Color.WHITE : Color.BLACK);
                textArea.setCaretColor(isDark ? Color.WHITE : Color.BLACK);
                textArea.setCurrentLineHighlightColor(isDark ? new Color(60, 60, 60) : new Color(255, 255, 224));
                System.err.println("Failed to apply RSyntaxTextArea theme: " + e.getMessage());
            }
        });
    }
    
    public void setTheme(boolean isDark) {
         applyThemeSafe(isDark);
    }
    
    // Improved block comment toggle logic
    private void toggleBlockComment() {
        try {
            int start = textArea.getSelectionStart();
            int end = textArea.getSelectionEnd();
            if (start == end) return; // Only work on selection for block comment
            
            String selected = textArea.getSelectedText();
            if (selected == null) return;
            
            String trimmed = selected.trim();
            // Check if already block commented (naive check)
            if (trimmed.startsWith("/*") && trimmed.endsWith("*/")) {
                // Uncomment: Remove first "/*" and last "*/"
                // We need to be careful about whitespace preservation if possible, 
                // but for now let's just strip the markers from the trimmed version 
                // and replace the original range.
                // Better approach: Find the actual indices of /* and */ in the raw selection
                int firstIdx = selected.indexOf("/*");
                int lastIdx = selected.lastIndexOf("*/");
                
                if (firstIdx != -1 && lastIdx != -1 && lastIdx > firstIdx) {
                    String core = selected.substring(firstIdx + 2, lastIdx);
                    // Reconstruct: (prefix before /*) + core + (suffix after */)
                    String prefix = selected.substring(0, firstIdx);
                    String suffix = selected.substring(lastIdx + 2);
                    textArea.replaceRange(prefix + core + suffix, start, end);
                }
            } else {
                // Comment: Wrap strictly
                textArea.replaceRange("/*" + selected + "*/", start, end);
            }
        } catch(Exception e) { e.printStackTrace(); }
    }
    
    private void performFormatOnEDT() {
        String content = textArea.getText();
        if (content == null || content.isEmpty()) return;
        
        String formatted = formatContent(content);
        if (!content.equals(formatted)) {
            textArea.setText(formatted);
        }
    }
    
    // Create a basic completion provider
    private CompletionProvider createCompletionProvider() {
        DefaultCompletionProvider provider = new DefaultCompletionProvider();
        
        // Java Keywords
        provider.addCompletion(new BasicCompletion(provider, "public"));
        provider.addCompletion(new BasicCompletion(provider, "private"));
        provider.addCompletion(new BasicCompletion(provider, "protected"));
        provider.addCompletion(new BasicCompletion(provider, "class"));
        provider.addCompletion(new BasicCompletion(provider, "interface"));
        provider.addCompletion(new BasicCompletion(provider, "void"));
        provider.addCompletion(new BasicCompletion(provider, "static"));
        provider.addCompletion(new BasicCompletion(provider, "final"));
        provider.addCompletion(new BasicCompletion(provider, "import"));
        provider.addCompletion(new BasicCompletion(provider, "return"));
        
        // Common System calls
        provider.addCompletion(new BasicCompletion(provider, "System.out.println", "Print to standard out"));
        provider.addCompletion(new BasicCompletion(provider, "String"));
        provider.addCompletion(new BasicCompletion(provider, "Integer"));
        
        return provider;
    }

    public void setText(String text) {
        SwingUtilities.invokeLater(() -> {
            if (textArea != null) textArea.setText(text);
        });
    }

    public String getText() {
        if (SwingUtilities.isEventDispatchThread()) {
            return textArea != null ? textArea.getText() : "";
        }
        final String[] result = {null};
        try {
            SwingUtilities.invokeAndWait(() -> {
                if(textArea != null) result[0] = textArea.getText();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result[0];
    }

    public void setLanguage(String lang) {
        this.currentLang = lang.toLowerCase();
        SwingUtilities.invokeLater(() -> {
            if (textArea == null) return;
            textArea.clearParsers(); // Clear old parsers
            
            switch(currentLang) {
                case "java": 
                    textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA); 
                    textArea.addParser(new DemoParser());
                    break;
                case "xml": textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML); break;
                case "json": 
                    textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON); 
                    textArea.addParser(new JsonParser());
                    break;
                case "javascript": 
                case "js": textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT); break;
                case "html": textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_HTML); break;
                case "groovy": textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_GROOVY); break;
                default: textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            }
        });
    }

    public void format() {
        // Called from JavaFX Thread usually
        String content = getText(); // Safe blocking call
        if (content == null || content.isEmpty()) return;
        
        String formatted = formatContent(content);
        setText(formatted);
    }
    
    private String formatContent(String content) {
        try {
            switch(currentLang) {
                case "java":
                    return new Formatter().formatSource(content);
                case "json":
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    Object json = gson.fromJson(content, Object.class);
                    return gson.toJson(json);
                case "xml":
                    return formatXml(content);
                default:
                    return content;
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
            return content;
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
    
    // Extended AbstractParser to implement Parser interface correctly
    private static class DemoParser extends AbstractParser {
        @Override
        public ParseResult parse(RSyntaxDocument doc, String style) {
            DefaultParseResult result = new DefaultParseResult(this);
            
             try {
                int lineCount = doc.getDefaultRootElement().getElementCount();
                 for (int i=0; i<lineCount; i++) {
                    Element elem = doc.getDefaultRootElement().getElement(i);
                    int start = elem.getStartOffset();
                    int end = elem.getEndOffset();
                    String line = doc.getText(start, end - start);
                    
                    if (line.contains("TODO")) {
                         int offset = start + line.indexOf("TODO");
                         result.addNotice(new DefaultParserNotice(this, "TODO found", i, offset, 4));
                    }
                    if (line.contains("error")) {
                        int offset = start + line.indexOf("error");
                        DefaultParserNotice notice = new DefaultParserNotice(this, "Example Error", i, offset, 5);
                        notice.setLevel(ParserNotice.Level.ERROR);
                        result.addNotice(notice);
                    }
                }
             } catch (Exception e) {}
            return result;
        }
    }
    // JSON Parser using Gson
    private static class JsonParser extends AbstractParser {
        private final Gson gson = new Gson();
        @Override
        public ParseResult parse(RSyntaxDocument doc, String style) {
            DefaultParseResult result = new DefaultParseResult(this);
            String text = "";
            try { 
                text = doc.getText(0, doc.getLength()); 
            } catch (Exception e) { 
                return result; 
            }
            
            try {
                gson.fromJson(text, Object.class);
            } catch (com.google.gson.JsonSyntaxException e) {
                 int line = 0;
                 try {
                     java.util.regex.Matcher m = java.util.regex.Pattern.compile("line (\\d+)").matcher(e.getMessage());
                     if (m.find()) {
                         line = Integer.parseInt(m.group(1)) - 1; 
                     }
                 } catch(Exception ignore){}
                 
                 DefaultParserNotice notice = new DefaultParserNotice(this, "JSON Syntax Error: " + e.getMessage(), line);
                 notice.setLevel(ParserNotice.Level.ERROR);
                 result.addNotice(notice);
            } catch (Exception e) {}
            return result;
        }
    }
}
