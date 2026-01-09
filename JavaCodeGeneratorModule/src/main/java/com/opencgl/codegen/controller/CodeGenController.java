package com.opencgl.codegen.controller;

import com.opencgl.codegen.i18n.I18N;
import com.opencgl.codegen.service.CodeGenService;
import com.opencgl.codegen.service.CodeGenService.NamingStyle;
import com.opencgl.codegen.views.CodeGenView;
import javafx.fxml.Initializable;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * 代码生成器控制器
 */
public class CodeGenController extends CodeGenView implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(CodeGenController.class);
    private final CodeGenService codeGenService = new CodeGenService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initComboBoxes();
        bindEvents();
        initI18n();
    }

    private void initI18n() {
        // FXML already uses %key; dynamic generateButton text is set in bindEvents listener
    }

    private void initComboBoxes() {
        conversionModeCombo.getItems().addAll(I18N.get("mode.jsonXmlToJava"), I18N.get("mode.javaToJsonXml"));
        conversionModeCombo.getSelectionModel().selectFirst();
        
        sourceTypeCombo.getItems().addAll(I18N.get("source.json"), I18N.get("source.xml"), I18N.get("source.sql"));
        sourceTypeCombo.getSelectionModel().selectFirst();
        
        dbTypeCombo.getItems().addAll("MySQL", "Oracle", "PostgreSQL", "SQLite", "SQL Server");
        dbTypeCombo.getSelectionModel().selectFirst();
        
        classStyleCombo.getItems().addAll(I18N.get("style.pascal"), I18N.get("style.camel"), I18N.get("style.snake"), I18N.get("style.upperSnake"));
        classStyleCombo.selectFirst();
        
        fieldStyleCombo.getItems().addAll(I18N.get("style.camel"), I18N.get("style.pascal"), I18N.get("style.snake"), I18N.get("style.upperSnake"));
        fieldStyleCombo.selectFirst();
        
        methodStyleCombo.getItems().addAll(I18N.get("style.camel"), I18N.get("style.pascal"), I18N.get("style.snake"), I18N.get("style.upperSnake"));
        methodStyleCombo.selectFirst();
        
        annotationTypeCombo.getItems().addAll("无", "JPA", "MyBatis-Plus");
        annotationTypeCombo.getSelectionModel().selectFirst();
    }

    private void bindEvents() {
        formatButton.setOnAction(e -> formatInput());
        generateButton.setOnAction(e -> generateCode());
        copyButton.setOnAction(e -> copyToClipboard());
        clearButton.setOnAction(e -> clearAll());
        
        // 监听转换方向变化，更新按钮文字
        conversionModeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.contains("Java")) {
                String fmt = sourceTypeCombo.getValue();
                generateButton.setText(I18N.get("btn.generateFormat", fmt != null ? fmt : ""));
            } else {
                generateButton.setText(I18N.get("btn.generate"));
            }
        });
        
        // 监听格式变化，更新按钮文字
        sourceTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            String mode = conversionModeCombo.getValue();
            if (mode != null && mode.contains("Java")) {
                generateButton.setText(I18N.get("btn.generateFormat", newVal != null ? newVal : ""));
            }
            // 显示/隐藏数据库类型选择器和注解类型选择器
            boolean isSql = "SQL".equals(newVal);
            dbTypeBox.setVisible(isSql);
            dbTypeBox.setManaged(isSql);
            annotationTypeBox.setVisible(isSql);
            annotationTypeBox.setManaged(isSql);
        });
    }

    private NamingStyle parseStyle(String styleStr) {
        if (styleStr == null) return NamingStyle.CAMEL_CASE;
        if (styleStr.contains("PascalCase")) return NamingStyle.PASCAL_CASE;
        if (styleStr.contains("camelCase")) return NamingStyle.CAMEL_CASE;
        if (styleStr.contains("UPPER_SNAKE")) return NamingStyle.UPPER_SNAKE_CASE;
        if (styleStr.contains("snake_case")) return NamingStyle.SNAKE_CASE;
        return NamingStyle.CAMEL_CASE;
    }

    private void formatInput() {
        String input = inputArea.getText();
        if (input == null || input.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInput"));
            return;
        }
        
        String conversionMode = conversionModeCombo.getValue();
        boolean isJavaToData = conversionMode != null && conversionMode.contains("Java →");
        
        try {
            String formatted;
            if (isJavaToData) {
                // Java代码格式化
                formatted = formatJavaCode(input.trim());
            } else {
                // JSON/XML/SQL格式化
                String sourceType = sourceTypeCombo.getValue();
                if ("SQL".equals(sourceType)) {
                    formatted = formatSql(input.trim());
                } else if ("XML".equals(sourceType)) {
                    formatted = formatXml(input.trim());
                } else {
                    formatted = formatJson(input.trim());
                }
            }
            inputArea.setText(formatted);
            showToast(I18N.get("msg.formatSuccess"), "#3498db");
        } catch (Exception e) {
            showError(I18N.get("msg.formatFailed", e.getMessage()));
        }
    }
    
    /**
     * 简单的Java代码格式化
     */
    private String formatJavaCode(String code) {
        StringBuilder result = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        String[] lines = code.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                result.append("\n");
                continue;
            }
            
            // 调整缩进
            if (trimmed.startsWith("}") || trimmed.startsWith(")")) {
                indent = Math.max(0, indent - 1);
            }
            
            result.append(repeat("    ", indent)).append(trimmed).append("\n");
            
            // 更新缩进
            for (char c : trimmed.toCharArray()) {
                if (c == '"' && (result.length() < 2 || result.charAt(result.length() - 2) != '\\')) {
                    inString = !inString;
                }
                if (!inString) {
                    if (c == '{' || c == '(') indent++;
                    else if (c == '}' || c == ')') indent = Math.max(0, indent - 1);
                }
            }
        }
        return result.toString().trim();
    }
    private String formatJson(String json) {
        StringBuilder result = new StringBuilder();
        int indent = 0;
        boolean inQuotes = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
                result.append(c);
            } else if (!inQuotes) {
                switch (c) {
                    case '{':
                    case '[':
                        result.append(c).append("\n").append(repeat("    ", ++indent));
                        break;
                    case '}':
                    case ']':
                        result.append("\n").append(repeat("    ", --indent)).append(c);
                        break;
                    case ',':
                        result.append(c).append("\n").append(repeat("    ", indent));
                        break;
                    case ':':
                        result.append(": ");
                        break;
                    case ' ':
                    case '\n':
                    case '\r':
                    case '\t':
                        break;
                    default:
                        result.append(c);
                }
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String formatXml(String xml) {
        StringBuilder result = new StringBuilder();
        int indent = 0;
        String[] lines = xml.replaceAll(">\\s*<", ">\n<").split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.startsWith("</")) {
                indent--;
                result.append(repeat("    ", indent)).append(line).append("\n");
            } else if (line.endsWith("/>") || (line.contains("</") && !line.startsWith("</"))) {
                result.append(repeat("    ", indent)).append(line).append("\n");
            } else if (line.startsWith("<") && !line.startsWith("<?")) {
                result.append(repeat("    ", indent)).append(line).append("\n");
                if (!line.contains("</")) indent++;
            } else {
                result.append(repeat("    ", indent)).append(line).append("\n");
            }
        }
        return result.toString().trim();
    }

    private String formatSql(String sql) {
        // 1. 规范化空白（保留换行用于检测原始格式，但先压缩成单行处理）
        sql = sql.replaceAll("\\s+", " ").trim();
        
        // 2. 移除括号内的多余空格: ( 6 ) -> (6)
        sql = sql.replaceAll("\\(\\s+", "(");
        sql = sql.replaceAll("\\s+\\)", ")");
        
        // 3. SQL 关键字大写转换
        String[] keywords = {
            "CREATE TABLE", "PRIMARY KEY", "FOREIGN KEY", "NOT NULL", 
            "DEFAULT", "AUTO_INCREMENT", "UNIQUE", "INDEX", "REFERENCES",
            "ON DELETE", "ON UPDATE", "CASCADE", "SET NULL", "RESTRICT",
            "INT", "BIGINT", "VARCHAR", "CHAR", "TEXT", "DECIMAL", "NUMERIC",
            "FLOAT", "DOUBLE", "BOOLEAN", "DATE", "TIME", "TIMESTAMP", "DATETIME",
            "BLOB", "CLOB", "IF NOT EXISTS", "ENGINE", "CHARSET", "COLLATE"
        };
        for (String keyword : keywords) {
            sql = sql.replaceAll("(?i)\\b" + keyword.replace(" ", "\\s+") + "\\b", keyword);
        }
        
        // 4. 格式化 CREATE TABLE 结构
        StringBuilder result = new StringBuilder();
        int parenDepth = 0;
        boolean inTableDef = false;
        
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            
            // 检测是否进入 CREATE TABLE 定义
            if (!inTableDef && c == '(' && 
                result.toString().toUpperCase().contains("CREATE TABLE")) {
                inTableDef = true;
                result.append(" (\n    ");
                parenDepth = 1;
                continue;
            }
            
            if (inTableDef) {
                if (c == '(') {
                    parenDepth++;
                    result.append(c);
                } else if (c == ')') {
                    parenDepth--;
                    if (parenDepth == 0) {
                        result.append("\n)");
                        inTableDef = false;
                    } else {
                        result.append(c);
                    }
                } else if (c == ',' && parenDepth == 1) {
                    result.append(",\n    ");
                } else {
                    result.append(c);
                }
            } else {
                result.append(c);
            }
        }
        
        // 5. 只清理每行前后多余空格，保留换行
        String[] lines = result.toString().split("\n");
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (i == 0) {
                formatted.append(line);
            } else {
                formatted.append("\n    ").append(line);
            }
        }
        
        // 修正最后的括号缩进
        return formatted.toString().replace("\n    )", "\n)");
    }

    private String repeat(String str, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) sb.append(str);
        return sb.toString();
    }

    private void generateCode() {
        String input = inputArea.getText();
        if (input == null || input.trim().isEmpty()) {
            showWarning(I18N.get("msg.pleaseInputContent"));
            return;
        }
        
        String conversionMode = conversionModeCombo.getValue();
        boolean isJavaToData = conversionMode != null && conversionMode.contains("Java →");
        
        try {
            String result;
            if (isJavaToData) {
                // Java类 → JSON/XML
                String format = sourceTypeCombo.getValue();
                result = convertJavaToData(input, "XML".equals(format));
                // 自动格式化输出
                if ("XML".equals(format)) {
                    result = formatXml(result);
                } else {
                    result = formatJson(result);
                }
            } else {
                // JSON/XML → Java类
                String className = classNameField.getText();
                if (className == null || className.trim().isEmpty()) {
                    className = "GeneratedClass";
                }
                
                String packageName = packageNameField.getText();
                String sourceType = sourceTypeCombo.getValue();
                NamingStyle classStyle = parseStyle(classStyleCombo.getValue());
                NamingStyle fieldStyle = parseStyle(fieldStyleCombo.getValue());
                NamingStyle methodStyle = parseStyle(methodStyleCombo.getValue());
                boolean useLombok = lombokCheckbox != null && lombokCheckbox.isSelected();
                String annotationType = annotationTypeCombo != null ? annotationTypeCombo.getValue() : "无";
                
                if ("SQL".equals(sourceType)) {
                    String dbType = dbTypeCombo.getValue();
                    if (dbType == null) dbType = "MySQL";
                    result = codeGenService.generateFromSql(input, className, classStyle, fieldStyle, methodStyle, useLombok, annotationType, packageName, dbType);
                } else if ("XML".equals(sourceType)) {
                    result = codeGenService.generateFromXml(input, className, classStyle, fieldStyle, methodStyle, useLombok, packageName);
                } else {
                    result = codeGenService.generateFromJson(input, className, classStyle, fieldStyle, methodStyle, useLombok, packageName);
                }
            }
            outputArea.setText(result);
            showToast(I18N.get("msg.convertSuccess"), "#27ae60");
        } catch (Exception e) {
            logger.error("转换失败", e);
            showError(I18N.get("msg.convertFailed", e.getMessage()));
        }
    }
    
    // 存储解析出的所有类
    private java.util.Map<String, java.util.LinkedHashMap<String, String>> parsedClasses = new java.util.LinkedHashMap<>();
    
    /**
     * Java类转JSON/XML - 支持嵌套类
     */
    private String convertJavaToData(String javaCode, boolean toXml) {
        parsedClasses.clear();
        
        // 解析所有类
        java.util.regex.Pattern classPattern = java.util.regex.Pattern.compile(
            "public\\s+class\\s+(\\w+)[^{]*\\{([^}]+(?:\\{[^}]*\\}[^}]*)*)\\}");
        java.util.regex.Matcher classMatcher = classPattern.matcher(javaCode);
        
        String mainClassName = null;
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            String classBody = classMatcher.group(2);
            
            if (mainClassName == null) mainClassName = className;
            
            // 解析字段
            java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
            java.util.regex.Pattern fieldPattern = java.util.regex.Pattern.compile(
                "private\\s+(\\S+)\\s+(\\w+)\\s*;");
            java.util.regex.Matcher fieldMatcher = fieldPattern.matcher(classBody);
            
            while (fieldMatcher.find()) {
                String type = fieldMatcher.group(1);
                String name = fieldMatcher.group(2);
                if (!name.equals("serialVersionUID")) {
                    fields.put(name, type);
                }
            }
            
            if (!fields.isEmpty()) {
                parsedClasses.put(className, fields);
            }
        }
        
        if (parsedClasses.isEmpty()) {
            throw new RuntimeException(I18N.get("msg.noClassDef"));
        }
        
        // 从主类生成
        if (toXml) {
            return generateXmlRecursive(mainClassName, 0);
        } else {
            return generateJsonRecursive(mainClassName, 0);
        }
    }
    
    private String generateJsonRecursive(String className, int indent) {
        java.util.LinkedHashMap<String, String> fields = parsedClasses.get(className);
        if (fields == null) return "{}";
        
        String pad = repeat("    ", indent);
        String pad1 = repeat("    ", indent + 1);
        StringBuilder sb = new StringBuilder("{\n");
        
        int i = 0;
        for (var entry : fields.entrySet()) {
            String name = entry.getKey();
            String type = entry.getValue();
            
            sb.append(pad1).append("\"").append(name).append("\": ");
            
            if (type.startsWith("List<")) {
                // List类型
                String innerType = type.substring(5, type.length() - 1);
                if (parsedClasses.containsKey(innerType)) {
                    sb.append("[\n").append(pad1).append("    ");
                    sb.append(generateJsonRecursive(innerType, indent + 2).replaceAll("\n", "\n" + pad1 + "    ").trim());
                    sb.append("\n").append(pad1).append("]");
                } else {
                    sb.append("[]");
                }
            } else if (parsedClasses.containsKey(type)) {
                // 嵌套对象
                sb.append(generateJsonRecursive(type, indent + 1).replaceAll("\n", "\n" + pad1).trim());
            } else {
                // 基本类型
                sb.append(getJsonDefaultValue(type));
            }
            
            if (i++ < fields.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append(pad).append("}");
        return sb.toString();
    }
    
    private String generateXmlRecursive(String className, int indent) {
        java.util.LinkedHashMap<String, String> fields = parsedClasses.get(className);
        if (fields == null) return "";
        
        String pad = repeat("    ", indent);
        String rootTag = indent == 0 ? "root" : className.substring(0, 1).toLowerCase() + className.substring(1);
        StringBuilder sb = new StringBuilder();
        
        if (indent == 0) sb.append("<").append(rootTag).append(">\n");
        
        for (var entry : fields.entrySet()) {
            String name = entry.getKey();
            String type = entry.getValue();
            
            if (type.startsWith("List<")) {
                String innerType = type.substring(5, type.length() - 1);
                if (parsedClasses.containsKey(innerType)) {
                    sb.append(pad).append("    <").append(name).append(">\n");
                    sb.append(generateXmlRecursive(innerType, indent + 2));
                    sb.append(pad).append("    </").append(name).append(">\n");
                } else {
                    sb.append(pad).append("    <").append(name).append("></").append(name).append(">\n");
                }
            } else if (parsedClasses.containsKey(type)) {
                sb.append(pad).append("    <").append(name).append(">\n");
                sb.append(generateXmlRecursive(type, indent + 2));
                sb.append(pad).append("    </").append(name).append(">\n");
            } else {
                sb.append(pad).append("    <").append(name).append(">")
                  .append(getXmlDefaultValue(type))
                  .append("</").append(name).append(">\n");
            }
        }
        
        if (indent == 0) sb.append("</").append(rootTag).append(">");
        return sb.toString();
    }
    
    private String getJsonDefaultValue(String type) {
        if (type.equals("String")) return "\"\"";
        if (type.equals("Long") || type.equals("long")) return "0";
        if (type.equals("Integer") || type.equals("int")) return "0";
        if (type.equals("Double") || type.equals("double")) return "0.0";
        if (type.equals("Boolean") || type.equals("boolean")) return "false";
        return "null";
    }
    
    private String getXmlDefaultValue(String type) {
        if (type.equals("String")) return "";
        if (type.equals("Long") || type.equals("long")) return "0";
        if (type.equals("Integer") || type.equals("int")) return "0";
        if (type.equals("Double") || type.equals("double")) return "0.0";
        if (type.equals("Boolean") || type.equals("boolean")) return "false";
        return "";
    }

    private void copyToClipboard() {
        String content = outputArea.getText();
        if (content == null || content.isEmpty()) {
            showWarning(I18N.get("msg.noContent"));
            return;
        }
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent cc = new ClipboardContent();
        cc.putString(content);
        clipboard.setContent(cc);
        showToast(I18N.get("msg.codeCopied"), "#27ae60");
    }

    private void clearAll() {
        inputArea.clear();
        outputArea.clear();
        classNameField.clear();
        showToast(I18N.get("msg.cleared"), "#95a5a6");
    }

    private void showToast(String message, String color) {
        javafx.scene.control.Label label = new javafx.scene.control.Label("✅ " + message);
        label.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-padding: 12 24; -fx-background-radius: 8; -fx-font-weight: bold;");
        StackPane.setAlignment(label, javafx.geometry.Pos.TOP_CENTER);
        StackPane.setMargin(label, new javafx.geometry.Insets(20, 0, 0, 0));
        label.setOpacity(0);
        rootPane.getChildren().add(label);
        
        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), label);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), label);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> rootPane.getChildren().remove(label));
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        pause.setOnFinished(e -> fadeOut.play());
        fadeIn.setOnFinished(e -> pause.play());
        fadeIn.play();
    }

    private void showWarning(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(I18N.get("title.warning")); alert.setHeaderText(null); alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18N.get("title.error")); alert.setHeaderText(null); alert.setContentText(msg);
        alert.showAndWait();
    }
}
