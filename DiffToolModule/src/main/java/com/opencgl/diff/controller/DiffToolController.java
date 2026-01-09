package com.opencgl.diff.controller;

import com.opencgl.diff.i18n.I18N;
import com.opencgl.diff.service.DiffToolService;
import com.opencgl.diff.service.DiffToolService.DiffStats;
import com.github.difflib.text.DiffRow;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 文本差异对比控制器 - Beyond Compare 风格
 *
 * @author OpenCGL
 */
public class DiffToolController implements Initializable {

    @FXML private TextArea leftTextArea;
    @FXML private TextArea rightTextArea;
    @FXML private WebView diffWebView;
    @FXML private Label statsLabel;
    @FXML private Label leftFileLabel;
    @FXML private Label rightFileLabel;

    private final DiffToolService diffService = new DiffToolService();
    private List<DiffRow> currentDiffRows;
    private int currentDiffIndex = -1;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // 示例文本
        leftTextArea.setText("""
            function sayHello() {
                console.log("Hello");
                return true;
            }
            
            function add(a, b) {
                return a + b;
            }
            """);
        
        rightTextArea.setText("""
            function sayHello() {
                console.log("Hello World!");
                return true;
            }
            
            function add(a, b) {
                const result = a + b;
                return result;
            }
            
            function subtract(a, b) {
                return a - b;
            }
            """);
        
        // 初始化时自动对比
        onCompare();
    }

    @FXML
    private void onCompare() {
        String left = leftTextArea.getText();
        String right = rightTextArea.getText();
        
        if (left == null) left = "";
        if (right == null) right = "";
        
        // 获取统计信息
        DiffStats stats = diffService.getStats(left, right);
        statsLabel.setText(stats.toString());
        
        // 生成 Beyond Compare 风格 HTML
        String html = diffService.generateBeyondCompareHtml(left, right);
        diffWebView.getEngine().loadContent(html);
        
        // 保存差异行用于导航
        currentDiffRows = diffService.sideBySideDiff(left, right);
        currentDiffIndex = -1;
    }

    @FXML
    private void onPrevDiff() {
        if (currentDiffRows == null || currentDiffRows.isEmpty()) return;
        
        // 向前查找差异
        int start = currentDiffIndex > 0 ? currentDiffIndex - 1 : currentDiffRows.size() - 1;
        for (int i = start; i >= 0; i--) {
            if (currentDiffRows.get(i).getTag() != DiffRow.Tag.EQUAL) {
                currentDiffIndex = i;
                scrollToDiff(i);
                return;
            }
        }
        // 循环到末尾
        for (int i = currentDiffRows.size() - 1; i > currentDiffIndex; i--) {
            if (currentDiffRows.get(i).getTag() != DiffRow.Tag.EQUAL) {
                currentDiffIndex = i;
                scrollToDiff(i);
                return;
            }
        }
    }

    @FXML
    private void onNextDiff() {
        if (currentDiffRows == null || currentDiffRows.isEmpty()) return;
        
        // 向后查找差异
        int start = currentDiffIndex + 1;
        for (int i = start; i < currentDiffRows.size(); i++) {
            if (currentDiffRows.get(i).getTag() != DiffRow.Tag.EQUAL) {
                currentDiffIndex = i;
                scrollToDiff(i);
                return;
            }
        }
        // 循环到开头
        for (int i = 0; i < currentDiffIndex; i++) {
            if (currentDiffRows.get(i).getTag() != DiffRow.Tag.EQUAL) {
                currentDiffIndex = i;
                scrollToDiff(i);
                return;
            }
        }
    }

    private void scrollToDiff(int rowIndex) {
        // 使用 JavaScript 滚动到指定行
        String script = String.format(
            "var rows = document.querySelectorAll('.row'); " +
            "if (rows[%d]) { rows[%d].scrollIntoView({behavior: 'smooth', block: 'center'}); }",
            rowIndex, rowIndex);
        diffWebView.getEngine().executeScript(script);
    }

    @FXML
    private void onLoadLeftFile() {
        File file = showFileChooser(I18N.get("title.choose_left_file"));
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                leftTextArea.setText(content);
                leftFileLabel.setText(I18N.get("label.left_prefix") + file.getName());
            } catch (Exception e) {
                statsLabel.setText(I18N.get("msg.read_fail", e.getMessage()));
            }
        }
    }

    @FXML
    private void onLoadRightFile() {
        File file = showFileChooser(I18N.get("title.choose_right_file"));
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                rightTextArea.setText(content);
                rightFileLabel.setText(I18N.get("label.right_prefix") + file.getName());
            } catch (Exception e) {
                statsLabel.setText(I18N.get("msg.read_fail", e.getMessage()));
            }
        }
    }

    private File showFileChooser(String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        return fileChooser.showOpenDialog(null);
    }

    @FXML
    private void onSwap() {
        String left = leftTextArea.getText();
        String right = rightTextArea.getText();
        leftTextArea.setText(right);
        rightTextArea.setText(left);
        
        String leftLabel = leftFileLabel.getText();
        String rightLabel = rightFileLabel.getText();
        String leftPrefix = I18N.get("label.left_prefix");
        String rightPrefix = I18N.get("label.right_prefix");
        String rightPart = rightLabel.startsWith(rightPrefix) ? rightLabel.substring(rightPrefix.length()).trim() : rightLabel;
        String leftPart = leftLabel.startsWith(leftPrefix) ? leftLabel.substring(leftPrefix.length()).trim() : leftLabel;
        leftFileLabel.setText(leftPrefix + (rightPart.isEmpty() ? "" : " " + rightPart));
        rightFileLabel.setText(rightPrefix + (leftPart.isEmpty() ? "" : " " + leftPart));
        
        onCompare();
    }

    @FXML
    private void onClear() {
        leftTextArea.clear();
        rightTextArea.clear();
        diffWebView.getEngine().loadContent("<html><body style='background:#1e1e1e;color:#d4d4d4;font-family:Consolas;padding:20px;'>请输入文本或打开文件后点击「对比」按钮</body></html>");
        statsLabel.setText("");
        leftFileLabel.setText(I18N.get("label.left_file"));
        rightFileLabel.setText(I18N.get("label.right_file"));
        currentDiffRows = null;
        currentDiffIndex = -1;
    }

    public void dispose() {
        if (diffWebView != null) {
            diffWebView.getEngine().load("about:blank");
            diffWebView = null;
        }
        currentDiffRows = null;
    }
}
