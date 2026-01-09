package com.opencgl.experience.controls;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import javafx.scene.layout.StackPane;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class MonacoEditorWrapper extends StackPane {

    private boolean isDarkTheme = false;
    private String pendingText = "";
    private String currentLang = "java";

    public MonacoEditorWrapper() {
        // Initialize as empty container
        // WebView will be injected when attached
    }
    
    // Called when this editor becomes active/visible
    public void onAttach() {
        MonacoWebViewManager manager = MonacoWebViewManager.getInstance();
        if (!manager.isReady()) {
             // If not ready, we can still attach, the manager will process queue
             // But for safer UI, maybe show loader? For now, standard flow.
        }
        
        // 1. Detach from previous owner
        manager.detach();
        
        // 2. Add to self
        getChildren().add(manager.getWebView());
        
        // 3. Restore State
        String textToRestore = pendingText == null ? "" : pendingText;
        manager.setContent(textToRestore, currentLang, isDarkTheme);
    }
    
    // Called when this editor loses focus/visibility or tab closes
    public void onDetach() {
        // 1. Capture State
         if (getChildren().contains(MonacoWebViewManager.getInstance().getWebView())) {
             Object val = MonacoWebViewManager.getInstance().executeScriptReturns("window.javaEditor.getValue()");
             if (val instanceof String) {
                 this.pendingText = (String) val;
             }
         }
    }

    public void setValue(String text) {
        this.pendingText = text;
        // If we currently own the WebView, update it immediately
        if (getChildren().contains(MonacoWebViewManager.getInstance().getWebView())) {
             MonacoWebViewManager.getInstance().setContent(text, currentLang, isDarkTheme);
        }
    }
    
    public void setSafeValue(String text) {
        setValue(text);
    }
    
    public void insertText(String text) {
         if (getChildren().contains(MonacoWebViewManager.getInstance().getWebView())) {
             String safeText = text.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$");
             MonacoWebViewManager.getInstance().executeScript("editor.trigger('keyboard', 'type', {text: `" + safeText + "`})");
         }
    }


    public String getValue() {
        // If we own the WebView, get live value
        if (getChildren().contains(MonacoWebViewManager.getInstance().getWebView())) {
             Object val = MonacoWebViewManager.getInstance().executeScriptReturns("window.javaEditor.getValue()");
             if (val instanceof String) {
                 this.pendingText = (String) val;
             }
        }
        return this.pendingText;
    }

    public void setLanguage(String lang) {
        this.currentLang = lang.toLowerCase();
        if (getChildren().contains(MonacoWebViewManager.getInstance().getWebView())) {
             MonacoWebViewManager.getInstance().setContent(pendingText, currentLang, isDarkTheme);
        }
    }
    
    public void setTheme(boolean isDark) {
        this.isDarkTheme = isDark;
        if (getChildren().contains(MonacoWebViewManager.getInstance().getWebView())) {
             MonacoWebViewManager.getInstance().setContent(pendingText, currentLang, isDarkTheme);
        }
    }
    
    public void format() {
        String content = getValue();
        if (content == null || content.isEmpty()) return;
        
        // Clear any previous markers (via Manager)
         if (getChildren().contains(MonacoWebViewManager.getInstance().getWebView())) {
            MonacoWebViewManager.getInstance().executeScript("window.javaEditor.clearMarkers()");
         }

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
        } catch (FormatterException e) {
             for (var diag : e.diagnostics()) {
                 int line = diag.line();
                 String msg = diag.message();
                 addMarker(line, msg, true);
             }
             return;
        } catch (JsonSyntaxException e) {
             int line = 1;
             String msg = e.getMessage();
             Matcher m = Pattern.compile("line (\\d+)").matcher(msg);
             if (m.find()) {
                 try { line = Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
             }
             addMarker(line, "JSON Syntax Error: " + msg, true);
             return;
        } catch (Throwable e) {
             System.err.println("Formatting failed: " + e.getMessage());
             if (e instanceof IllegalAccessError) {
                 String msg = "Missing JVM Options for formatting. Check console.";
                 addMarker(1, msg, true);
                 System.err.println("To fix this, add the following VM Options:\n" +
                         "--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED\n" +
                         "--add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED\n" +
                         "--add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED\n" +
                         "--add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED\n" +
                         "--add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
             } else {
                 e.printStackTrace();
                 addMarker(1, "Formatting failed: " + e.getMessage(), true);
             }
             return; 
        }
        
        if (!content.equals(formatted)) {
            setValue(formatted);
        }
    }
    
    private void addMarker(int line, String message, boolean isError) {
         if (getChildren().contains(MonacoWebViewManager.getInstance().getWebView())) {
            String safeMsg = message.replace("'", "\\'").replace("\n", " ");
            MonacoWebViewManager.getInstance().executeScript("window.javaEditor.addMarker(" + line + ", '" + safeMsg + "', " + isError + ")");
         }
    }
    
    private String formatXml(String input) throws Exception {
        Source xmlInput = new StreamSource(new StringReader(input));
        StringWriter stringWriter = new StringWriter();
        StreamResult xmlOutput = new StreamResult(stringWriter);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer(); 
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(xmlInput, xmlOutput);
        return xmlOutput.getWriter().toString();
    }
}
