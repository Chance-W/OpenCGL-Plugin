package com.opencgl.template.util;


import java.io.*;
import com.aspose.words.Document;
import com.aspose.words.MarkdownSaveOptions;
import com.aspose.words.SaveFormat;

/**
 * Word (.docx) 转 Markdown 转换器
 * 支持文本、标题、列表和简单格式
 */
public class WordToMarkdownConverter {

    public static void main(String[] args) {
        try {
            // 输入 Word 文件路径
            String inputPath = "/Users/chancew./Documents/Chance/whalecloud/PayC/HLD/1.doc";
            // 输出 Markdown 文件路径
            String outputPath = "/Users/chancew./Downloads/output.md";

            // 加载 Word 文档
            Document doc = new Document(inputPath);

            // 保存为 Markdown 格式
            doc.save(outputPath, SaveFormat.MARKDOWN);

            System.out.println("文档已成功转换为 Markdown");
        }
        catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
