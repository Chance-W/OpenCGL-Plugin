package com.opencgl.diff.service;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.*;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;

import java.util.Arrays;
import java.util.List;

/**
 * 文本差异对比服务 - Beyond Compare 风格
 *
 * @author OpenCGL
 */
public class DiffToolService {

    /**
     * 对比两个文本，返回统一格式差异
     */
    public String unifiedDiff(String text1, String text2, String name1, String name2) {
        List<String> lines1 = Arrays.asList(text1.split("\n", -1));
        List<String> lines2 = Arrays.asList(text2.split("\n", -1));
        
        Patch<String> patch = DiffUtils.diff(lines1, lines2);
        List<String> unifiedDiff = com.github.difflib.UnifiedDiffUtils.generateUnifiedDiff(
            name1, name2, lines1, patch, 3);
        
        return String.join("\n", unifiedDiff);
    }

    /**
     * 对比两个文本，返回逐行对比结果
     */
    public List<DiffRow> sideBySideDiff(String text1, String text2) {
        List<String> lines1 = Arrays.asList(text1.split("\n", -1));
        List<String> lines2 = Arrays.asList(text2.split("\n", -1));
        
        DiffRowGenerator generator = DiffRowGenerator.create()
            .showInlineDiffs(true)
            .inlineDiffByWord(true)
            .oldTag(f -> f ? "<span class='del'>" : "</span>")
            .newTag(f -> f ? "<span class='add'>" : "</span>")
            .build();
        
        return generator.generateDiffRows(lines1, lines2);
    }

    /**
     * 生成 Beyond Compare 风格的 HTML 差异视图
     */
    public String generateBeyondCompareHtml(String text1, String text2) {
        List<DiffRow> rows = sideBySideDiff(text1, text2);
        
        StringBuilder html = new StringBuilder();
        html.append("""
            <!DOCTYPE html>
            <html>
            <head>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; background: #1e1e1e; color: #d4d4d4; overflow-x: hidden; }
                .container { display: flex; width: 100%; }
                .panel { flex: 1; overflow-x: hidden; border-right: 1px solid #3c3c3c; }
                .panel:last-child { border-right: none; }
                .header { background: #2d2d2d; padding: 8px 12px; font-weight: bold; color: #9cdcfe; border-bottom: 1px solid #3c3c3c; position: sticky; top: 0; z-index: 10; }
                .row { display: flex; border-bottom: 1px solid #2a2a2a; min-height: 22px; }
                .line-num { width: 50px; min-width: 50px; text-align: right; padding: 2px 8px; background: #252526; color: #858585; border-right: 1px solid #3c3c3c; user-select: none; flex-shrink: 0; }
                .line-content { flex: 1; padding: 2px 8px; white-space: pre-wrap; word-break: break-all; overflow-wrap: break-word; }
                .equal { background: transparent; }
                .change-left { background: rgba(255, 200, 100, 0.15); }
                .change-right { background: rgba(100, 200, 255, 0.15); }
                .delete { background: rgba(255, 100, 100, 0.2); }
                .insert { background: rgba(100, 255, 100, 0.2); }
                .del { background: #ff6b6b; color: #1e1e1e; padding: 0 2px; border-radius: 2px; }
                .add { background: #51cf66; color: #1e1e1e; padding: 0 2px; border-radius: 2px; }
                .diff-marker { width: 20px; min-width: 20px; text-align: center; font-weight: bold; flex-shrink: 0; }
                .diff-marker.del-marker { color: #ff6b6b; }
                .diff-marker.add-marker { color: #51cf66; }
                .diff-marker.change-marker { color: #ffd43b; }
            </style>
            </head>
            <body>
            <div class='container'>
                <div class='panel'>
                    <div class='header'>原文 (Original)</div>
            """);
        
        // 左侧面板
        int leftLineNum = 0;
        for (DiffRow row : rows) {
            String rowClass = switch (row.getTag()) {
                case CHANGE -> "change-left";
                case DELETE -> "delete";
                case INSERT -> ""; // 插入行在左侧为空
                default -> "equal";
            };
            
            String marker = switch (row.getTag()) {
                case CHANGE -> "<span class='diff-marker change-marker'>~</span>";
                case DELETE -> "<span class='diff-marker del-marker'>-</span>";
                default -> "<span class='diff-marker'> </span>";
            };
            
            if (row.getTag() != DiffRow.Tag.INSERT) {
                leftLineNum++;
                html.append("<div class='row ").append(rowClass).append("'>")
                    .append("<div class='line-num'>").append(leftLineNum).append("</div>")
                    .append(marker)
                    .append("<div class='line-content'>").append(escapeHtml(row.getOldLine())).append("</div>")
                    .append("</div>");
            } else {
                html.append("<div class='row insert'>")
                    .append("<div class='line-num'></div>")
                    .append("<span class='diff-marker'> </span>")
                    .append("<div class='line-content'></div>")
                    .append("</div>");
            }
        }
        
        html.append("""
                </div>
                <div class='panel'>
                    <div class='header'>新文 (Modified)</div>
            """);
        
        // 右侧面板
        int rightLineNum = 0;
        for (DiffRow row : rows) {
            String rowClass = switch (row.getTag()) {
                case CHANGE -> "change-right";
                case INSERT -> "insert";
                case DELETE -> ""; // 删除行在右侧为空
                default -> "equal";
            };
            
            String marker = switch (row.getTag()) {
                case CHANGE -> "<span class='diff-marker change-marker'>~</span>";
                case INSERT -> "<span class='diff-marker add-marker'>+</span>";
                default -> "<span class='diff-marker'> </span>";
            };
            
            if (row.getTag() != DiffRow.Tag.DELETE) {
                rightLineNum++;
                html.append("<div class='row ").append(rowClass).append("'>")
                    .append("<div class='line-num'>").append(rightLineNum).append("</div>")
                    .append(marker)
                    .append("<div class='line-content'>").append(escapeHtml(row.getNewLine())).append("</div>")
                    .append("</div>");
            } else {
                html.append("<div class='row delete'>")
                    .append("<div class='line-num'></div>")
                    .append("<span class='diff-marker'> </span>")
                    .append("<div class='line-content'></div>")
                    .append("</div>");
            }
        }
        
        html.append("""
                </div>
            </div>
            </body>
            </html>
            """);
        
        return html.toString();
    }

    /**
     * 获取差异统计
     */
    public DiffStats getStats(String text1, String text2) {
        List<DiffRow> rows = sideBySideDiff(text1, text2);
        
        int added = 0, deleted = 0, changed = 0, equal = 0;
        for (DiffRow row : rows) {
            switch (row.getTag()) {
                case INSERT -> added++;
                case DELETE -> deleted++;
                case CHANGE -> changed++;
                case EQUAL -> equal++;
            }
        }
        
        return new DiffStats(added, deleted, changed, equal, rows.size());
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    public record DiffStats(int added, int deleted, int changed, int equal, int total) {
        @Override
        public String toString() {
            return String.format("新增: %d  删除: %d  修改: %d  相同: %d  总计: %d 行",
                added, deleted, changed, equal, total);
        }
    }
}
