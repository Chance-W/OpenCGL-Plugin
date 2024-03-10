package com.opencgl.formorhtmlwrite.controller;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.utils.DialogUtil;
import com.opencgl.formorhtmlwrite.views.FormOrHtmlWriteView;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2024/02/22 09:32
 * @since v9.0
 */
public class FormOrHtmlWriteController extends FormOrHtmlWriteView {
    private static final Logger logger = LoggerFactory.getLogger(FormOrHtmlWriteController.class);

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        init();
    }

    private void init() {
        openForm.setOnAction(actionEvent -> {
            if (StringUtils.isNotEmpty(contentTextArea.getText())) {
                if (contentTextArea.getText().startsWith("<form")
                    || contentTextArea.getText().startsWith("\"<form")
                    || contentTextArea.getText().startsWith("<HTML")
                    || contentTextArea.getText().startsWith("\"<HTML")) {
                    String content = contentTextArea.getText()
                        .replace("\\n", "")
                        .replace("\"<form", "<form")
                        .replace("\"<HTML", "<HTML")
                        .replace("\\\"", "\"");
                    createHtmlFileAndOpen(content);
                }
                else {
                    DialogUtil.showErrorInfo("内容不合法，请检查！！！", mainStackPane);
                }
            }
            else {
                DialogUtil.showErrorInfo("表单内容不能为空！！！", mainStackPane);
            }
        });
    }

    private void createHtmlFileAndOpen(String content) {
        File htmlFile = null;
        try {

            htmlFile = createTempHTMLFileWithContent(content);
            // 使用 Desktop 类打开默认浏览器并显示临时HTML文件
            Desktop.getDesktop().browse(Optional.ofNullable(htmlFile)
                .map(File::toURI)
                .orElse(null));
        }
        catch (IOException e) {
            logger.error("", e);
        }
        finally {
            if (htmlFile != null) {
                htmlFile.deleteOnExit();
            }
        }
    }

    // 创建一个临时的HTML文件，写入指定的HTML内容，并返回该文件的引用
    private File createTempHTMLFileWithContent(String htmlContent) {
        try {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("""
                <!DOCTYPE html>
                <html>
                <head>
                <title>Auto Submit Form</title>""");
            if (StringUtils.isNotEmpty(originAndReferer.getText())) {
                stringBuilder.append("""
                                                
                        <script>
                        function submitForm() {    \s
                        var refererWindow = window.open(\"""")
                    .append(originAndReferer.getText())
                    .append("\");\n")
                    .append("refererWindow.onload = function() {\n")
                    .append("var form = document.forms[0];\n")
                    .append("form.submit();\n")
                    .append("}\n")
                    .append("</script>\n")
                    .append("</head>\n")
                    .append("<body>\n")
                    .append(htmlContent)
                    .append("</body>\n" + "</html>");
            }
            else {
                stringBuilder.append("""
                        <script>
                          function submitForm() {""")
                    .append("var form = document.forms[0];\n")
                    .append("form.submit();\n")
                    .append("}\n")
                    .append("</script>\n")
                    .append("</head>\n")
                    .append("<body>\n")
                    .append(htmlContent)
                    .append("</body>\n" + "</html>");
            }

            File tempFile = File.createTempFile("temp", ".html");
            FileWriter writer = new FileWriter(tempFile);
            writer.write(stringBuilder.toString());
            writer.close();
            return tempFile;
        }
        catch (IOException e) {
            logger.error("", e);
            return null;
        }
    }
}
