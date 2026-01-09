package com.opencgl.jsonxml.controller;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ResourceBundle;

import org.apache.commons.lang.StringUtils;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.opencgl.jsonxml.i18n.I18N;
import com.opencgl.jsonxml.util.Dom4jUtil;
import com.opencgl.jsonxml.util.SyntaxHighlighter;
import com.opencgl.jsonxml.views.JsonXmlFormatWidgetView;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import lombok.SneakyThrows;

/**
 * JSON/XML/SQL 格式化控制器
 *
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class JsonXmlFormatWidgetController extends JsonXmlFormatWidgetView implements Initializable {
    private final Logger logger = LoggerFactory.getLogger(JsonXmlFormatWidgetController.class);

    private CodeArea inputCodeArea;
    private CodeArea outputCodeArea;

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupCodeAreas();
        setControlAndStyle();
        bindEvents();
        initI18n();
    }

    private void initI18n() {
        titleLabel.textProperty().bind(I18N.getBinding("label.title"));
        inputLabel.textProperty().bind(I18N.getBinding("label.input"));
        outputLabel.textProperty().bind(I18N.getBinding("label.output"));
        formatButton.textProperty().bind(I18N.getBinding("button.format"));
        compressButton.textProperty().bind(I18N.getBinding("button.compress"));
        clearButton.textProperty().bind(I18N.getBinding("button.clear"));
        swapButton.textProperty().bind(I18N.getBinding("button.swap"));
        copyButton.textProperty().bind(I18N.getBinding("button.copy"));
        Tooltip fmtTip = new Tooltip();
        fmtTip.textProperty().bind(I18N.getBinding("tooltip.format"));
        formatButton.setTooltip(fmtTip);
        Tooltip compTip = new Tooltip();
        compTip.textProperty().bind(I18N.getBinding("tooltip.compress"));
        compressButton.setTooltip(compTip);
        Tooltip clearTip = new Tooltip();
        clearTip.textProperty().bind(I18N.getBinding("tooltip.clear"));
        clearButton.setTooltip(clearTip);
        Tooltip swapTip = new Tooltip();
        swapTip.textProperty().bind(I18N.getBinding("tooltip.swap"));
        swapButton.setTooltip(swapTip);
        Tooltip copyTip = new Tooltip();
        copyTip.textProperty().bind(I18N.getBinding("tooltip.copy"));
        copyButton.setTooltip(copyTip);
    }

    private void setupCodeAreas() {
        // 创建 CodeArea 实例
        inputCodeArea = new CodeArea();
        outputCodeArea = new CodeArea();

        // 设置 CodeArea 样式
        SyntaxHighlighter.setupCodeArea(inputCodeArea);
        SyntaxHighlighter.setupCodeArea(outputCodeArea);

        // 创建带滚动的容器
        VirtualizedScrollPane<CodeArea> inputScrollPane =
            new VirtualizedScrollPane<>(inputCodeArea);
        VirtualizedScrollPane<CodeArea> outputScrollPane =
            new VirtualizedScrollPane<>(outputCodeArea);

        // 设置 VBox.vgrow
        javafx.scene.layout.VBox.setVgrow(inputScrollPane, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.VBox.setVgrow(outputScrollPane, javafx.scene.layout.Priority.ALWAYS);

        // 添加到 VBox
        inputVBox.getChildren().add(inputScrollPane);
        outputVBox.getChildren().add(outputScrollPane);

        // 加载语法高亮样式表
        try {
            String cssPath = getClass().getResource("/css/syntax-highlight.css").toExternalForm();
            mainStackPane.getStylesheets().add(cssPath);
        }
        catch (Exception e) {
            logger.warn("加载语法高亮样式失败", e);
        }

        // 输入区域内容变化时应用语法高亮
        inputCodeArea.textProperty().addListener((obs, oldText, newText) -> {
            applySyntaxHighlighting(inputCodeArea, newText);
        });
    }

    private void applySyntaxHighlighting(org.fxmisc.richtext.CodeArea codeArea, String text) {
        if (text == null || text.isEmpty()) return;

        String type = formatTypeComboBox.getValue();
        if (type == null) type = "JSON";

        try {
            switch (type) {
                case "JSON" -> codeArea.setStyleSpans(0, SyntaxHighlighter.highlightJson(text));
                case "XML" -> codeArea.setStyleSpans(0, SyntaxHighlighter.highlightXml(text));
                case "SQL" -> codeArea.setStyleSpans(0, SyntaxHighlighter.highlightSql(text));
            }
        }
        catch (Exception e) {
            // 忽略高亮错误
        }
    }

    private void bindEvents() {
        formatButton.setOnAction(e -> formatLabelAction());
        compressButton.setOnAction(e -> compressAction());
        clearButton.setOnAction(e -> clearAction());
        swapButton.setOnAction(e -> swapAction());
        copyButton.setOnAction(e -> copyAction());

        // 格式类型变化时重新应用高亮
        formatTypeComboBox.valueProperty().addListener((obs, old, newVal) -> {
            applySyntaxHighlighting(inputCodeArea, inputCodeArea.getText());
            applySyntaxHighlighting(outputCodeArea, outputCodeArea.getText());
        });
    }

    @FXML
    public void formatLabelAction() {
        if (StringUtils.isEmpty(inputCodeArea.getText())) {
            outputCodeArea.replaceText("格式化字符不能为空");
            setStatus(I18N.get("msg.input_empty"));
            return;
        }
        try {
            String type = formatTypeComboBox.getValue();
            String result;

            switch (type) {
                case "JSON" -> {
                    result = JSON.toJSONString(
                        JSONObject.parseObject(inputCodeArea.getText()),
                        SerializerFeature.PrettyFormat,
                        SerializerFeature.WriteDateUseDateFormat
                    );
                }
                case "XML" -> {
                    result = Dom4jUtil.formatXml(inputCodeArea.getText());
                }
                case "SQL" -> {
                    result = formatSql(inputCodeArea.getText());
                }
                default -> {
                    result = inputCodeArea.getText();
                }
            }

            outputCodeArea.replaceText(result);
            applySyntaxHighlighting(outputCodeArea, result);
            setStatus(I18N.get("msg.format_ok", type));
        }
        catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw, true));
            logger.error("格式化失败", e);
            outputCodeArea.replaceText(sw.toString());
            setStatus(I18N.get("msg.format_fail"));
        }
    }

    private void compressAction() {
        if (StringUtils.isEmpty(inputCodeArea.getText())) {
            setStatus(I18N.get("msg.input_empty"));
            return;
        }
        try {
            String type = formatTypeComboBox.getValue();
            String result;

            switch (type) {
                case "JSON" -> {
                    result = JSON.toJSONString(JSONObject.parseObject(inputCodeArea.getText()));
                }
                case "XML" -> {
                    result = inputCodeArea.getText().replaceAll(">\\s+<", "><").trim();
                }
                case "SQL" -> {
                    result = inputCodeArea.getText()
                        .replaceAll("\\s+", " ")
                        .replaceAll("\\s*,\\s*", ", ")
                        .trim();
                }
                default -> {
                    result = inputCodeArea.getText();
                }
            }

            outputCodeArea.replaceText(result);
            applySyntaxHighlighting(outputCodeArea, result);
            setStatus(I18N.get("msg.compress_ok"));
        }
        catch (Exception e) {
            logger.error("压缩失败", e);
            setStatus(I18N.get("msg.compress_fail", e.getMessage()));
        }
    }

    private void clearAction() {
        inputCodeArea.clear();
        outputCodeArea.clear();
        setStatus(I18N.get("msg.cleared"));
    }

    private void swapAction() {
        String input = inputCodeArea.getText();
        String output = outputCodeArea.getText();
        inputCodeArea.replaceText(output);
        outputCodeArea.replaceText(input);
        setStatus(I18N.get("msg.swapped"));
    }

    private void copyAction() {
        String output = outputCodeArea.getText();
        if (StringUtils.isNotEmpty(output)) {
            ClipboardContent content = new ClipboardContent();
            content.putString(output);
            Clipboard.getSystemClipboard().setContent(content);
            setStatus(I18N.get("msg.copied"));
        }
        else {
            setStatus(I18N.get("msg.output_empty"));
        }
    }

    /**
     * SQL 格式化
     */
    private String formatSql(String sql) {
        String nl = System.lineSeparator();

        // 规范化空白为单个空格
        sql = sql.replaceAll("\\s+", " ").trim();

        // SQL 关键字大写
        String[] keywords = {
            "SELECT", "FROM", "WHERE", "AND", "OR", "ORDER BY", "GROUP BY",
            "HAVING", "JOIN", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "OUTER JOIN",
            "ON", "AS", "IN", "NOT IN", "LIKE", "BETWEEN", "IS NULL", "IS NOT NULL",
            "INSERT INTO", "VALUES", "UPDATE", "SET", "DELETE FROM",
            "CREATE TABLE", "ALTER TABLE", "DROP TABLE", "PRIMARY KEY", "FOREIGN KEY",
            "NOT NULL", "DEFAULT", "AUTO_INCREMENT", "UNIQUE", "INDEX", "IF NOT EXISTS",
            "LIMIT", "OFFSET", "UNION", "UNION ALL", "DISTINCT", "COUNT", "SUM", "AVG", "MAX", "MIN",
            "INT", "BIGINT", "VARCHAR", "CHAR", "TEXT", "DECIMAL", "FLOAT", "DOUBLE",
            "BOOLEAN", "DATE", "TIME", "TIMESTAMP", "DATETIME"
        };

        for (String keyword : keywords) {
            sql = sql.replaceAll("(?i)\\b" + keyword.replace(" ", "\\s+") + "\\b", keyword);
        }

        String upperSql = sql.toUpperCase();

        // 根据语句类型分发到不同的格式化方法
        if (upperSql.startsWith("CREATE TABLE") || upperSql.contains(" CREATE TABLE")) {
            return formatCreateTable(sql, nl);
        }
        else if (upperSql.startsWith("INSERT INTO") || upperSql.contains(" INSERT INTO")) {
            return formatInsert(sql, nl);
        }
        else if (upperSql.startsWith("UPDATE") || upperSql.contains(" UPDATE")) {
            return formatUpdate(sql, nl);
        }
        else if (upperSql.startsWith("DELETE") || upperSql.contains(" DELETE")) {
            return formatDelete(sql, nl);
        }
        else {
            return formatSelect(sql, nl);
        }
    }

    /**
     * 格式化 SELECT 语句
     */
    private String formatSelect(String sql, String nl) {
        // 在主要关键字前添加换行
        sql = sql.replaceAll("\\bSELECT\\b", nl + "SELECT");
        sql = sql.replaceAll("\\bFROM\\b", nl + "FROM");
        sql = sql.replaceAll("\\bWHERE\\b", nl + "WHERE");
        sql = sql.replaceAll("\\bAND\\b", nl + "    AND");
        sql = sql.replaceAll("\\bOR\\b", nl + "    OR");
        sql = sql.replaceAll("\\bORDER BY\\b", nl + "ORDER BY");
        sql = sql.replaceAll("\\bGROUP BY\\b", nl + "GROUP BY");
        sql = sql.replaceAll("\\bHAVING\\b", nl + "HAVING");
        sql = sql.replaceAll("\\bLEFT JOIN\\b", nl + "LEFT JOIN");
        sql = sql.replaceAll("\\bRIGHT JOIN\\b", nl + "RIGHT JOIN");
        sql = sql.replaceAll("\\bINNER JOIN\\b", nl + "INNER JOIN");
        sql = sql.replaceAll("\\bLIMIT\\b", nl + "LIMIT");
        sql = sql.replaceAll("\\bUNION\\b", nl + "UNION");

        // SELECT 字段逗号换行
        StringBuilder result = new StringBuilder();
        String[] lines = sql.split(nl);

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("SELECT ")) {
                String selectPart = line.substring(7).trim();
                result.append("SELECT").append(nl);
                java.util.List<String> fields = splitByComma(selectPart);
                for (int j = 0; j < fields.size(); j++) {
                    result.append("    ").append(fields.get(j).trim());
                    if (j < fields.size() - 1) result.append(",");
                    result.append(nl);
                }
            }
            else {
                result.append(line).append(nl);
            }
        }

        return result.toString().trim();
    }

    /**
     * 格式化 INSERT 语句
     */
    private String formatInsert(String sql, String nl) {
        StringBuilder result = new StringBuilder();

        // 找 INSERT INTO table (columns) VALUES (values)
        int valuesIdx = sql.toUpperCase().indexOf("VALUES");

        if (valuesIdx == -1) {
            return sql; // 无法解析
        }

        String insertPart = sql.substring(0, valuesIdx).trim();
        String valuesPart = sql.substring(valuesIdx).trim();

        // 处理 INSERT INTO table (columns)
        int openParen = insertPart.indexOf('(');
        if (openParen > 0) {
            int closeParen = insertPart.lastIndexOf(')');
            String tablePart = insertPart.substring(0, openParen).trim();
            String columnsPart = insertPart.substring(openParen + 1, closeParen).trim();

            result.append(tablePart).append(" (").append(nl);
            java.util.List<String> cols = splitByComma(columnsPart);
            for (int i = 0; i < cols.size(); i++) {
                result.append("    ").append(cols.get(i).trim());
                if (i < cols.size() - 1) result.append(",");
                result.append(nl);
            }
            result.append(")").append(nl);
        }
        else {
            result.append(insertPart).append(nl);
        }

        // 处理 VALUES (...)
        int vOpenParen = valuesPart.indexOf('(');
        int vCloseParen = valuesPart.lastIndexOf(')');
        if (vOpenParen >= 0 && vCloseParen > vOpenParen) {
            String valuesContent = valuesPart.substring(vOpenParen + 1, vCloseParen).trim();
            result.append("VALUES (").append(nl);
            java.util.List<String> vals = splitByComma(valuesContent);
            for (int i = 0; i < vals.size(); i++) {
                result.append("    ").append(vals.get(i).trim());
                if (i < vals.size() - 1) result.append(",");
                result.append(nl);
            }
            result.append(")");

            // 处理末尾（如分号）
            String suffix = valuesPart.substring(vCloseParen + 1).trim();
            if (!suffix.isEmpty()) result.append(suffix);
        }
        else {
            result.append(valuesPart);
        }

        return result.toString();
    }

    /**
     * 格式化 UPDATE 语句
     */
    private String formatUpdate(String sql, String nl) {
        StringBuilder result = new StringBuilder();

        // UPDATE table SET col1=val1, col2=val2 WHERE ...
        int setIdx = sql.toUpperCase().indexOf(" SET ");
        int whereIdx = sql.toUpperCase().indexOf(" WHERE ");

        if (setIdx == -1) {
            return sql;
        }

        // UPDATE table
        String updatePart = sql.substring(0, setIdx).trim();
        result.append(updatePart).append(nl).append("SET").append(nl);

        // SET 部分
        String setPart;
        String wherePart = "";
        if (whereIdx > setIdx) {
            setPart = sql.substring(setIdx + 5, whereIdx).trim();
            wherePart = sql.substring(whereIdx).trim();
        }
        else {
            setPart = sql.substring(setIdx + 5).trim();
        }

        // 分割 SET 字段
        java.util.List<String> setItems = splitByComma(setPart);
        for (int i = 0; i < setItems.size(); i++) {
            result.append("    ").append(setItems.get(i).trim());
            if (i < setItems.size() - 1) result.append(",");
            result.append(nl);
        }

        // WHERE 部分
        if (!wherePart.isEmpty()) {
            wherePart = wherePart.replaceAll("\\bAND\\b", nl + "    AND");
            wherePart = wherePart.replaceAll("\\bOR\\b", nl + "    OR");
            result.append(wherePart);
        }

        return result.toString().trim();
    }

    /**
     * 格式化 DELETE 语句
     */
    private String formatDelete(String sql, String nl) {
        // DELETE FROM table WHERE ...
        sql = sql.replaceAll("\\bDELETE FROM\\b", "DELETE FROM");
        sql = sql.replaceAll("\\bWHERE\\b", nl + "WHERE");
        sql = sql.replaceAll("\\bAND\\b", nl + "    AND");
        sql = sql.replaceAll("\\bOR\\b", nl + "    OR");
        return sql.trim();
    }

    /**
     * 格式化 CREATE TABLE 语句
     */
    private String formatCreateTable(String sql, String nl) {
        StringBuilder result = new StringBuilder();

        // 找到第一个 ( 和最后一个 )
        int openParen = sql.indexOf('(');
        int closeParen = sql.lastIndexOf(')');

        if (openParen == -1 || closeParen == -1 || openParen >= closeParen) {
            return sql; // 无法解析，返回原始
        }

        // CREATE TABLE xxx (
        String tablePart = sql.substring(0, openParen).trim();
        result.append(tablePart).append(" (").append(nl);

        // 列定义部分
        String columnsPart = sql.substring(openParen + 1, closeParen).trim();

        // 按逗号分割，但要注意括号内的逗号不分割
        java.util.List<String> columns = splitByComma(columnsPart);

        for (int i = 0; i < columns.size(); i++) {
            String col = columns.get(i).trim();
            result.append("    ").append(col);
            if (i < columns.size() - 1) {
                result.append(",");
            }
            result.append(nl);
        }

        result.append(")");

        // 处理末尾可能有的内容，如 ENGINE=InnoDB
        String suffix = sql.substring(closeParen + 1).trim();
        if (!suffix.isEmpty()) {
            result.append(suffix);
        }

        return result.toString();
    }

    /**
     * 按逗号分割，但忽略括号内的逗号
     */
    private java.util.List<String> splitByComma(String str) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (char c : str.toCharArray()) {
            if (c == '(') {
                depth++;
                current.append(c);
            }
            else if (c == ')') {
                depth--;
                current.append(c);
            }
            else if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current = new StringBuilder();
            }
            else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            parts.add(current.toString());
        }

        return parts;
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    public void setControlAndStyle() {
        // Tooltips set in initI18n()
        formatTypeComboBox.selectFirst();
        formatTypeComboBox.setText("JSON");
    }
}
