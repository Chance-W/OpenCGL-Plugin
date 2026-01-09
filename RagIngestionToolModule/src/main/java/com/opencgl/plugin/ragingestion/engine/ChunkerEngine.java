package com.opencgl.plugin.ragingestion.engine;

import com.opencgl.plugin.ragingestion.model.DocumentChunk;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 零丢失工业级智能切片与前置清洗引擎 (Dify / LangChain 标准)。
 * 1. 支持自动字符集探测 (UTF-8 / GB18030 / GBK)，彻底避免乱码和读取失败。
 * 2. 严格零丢失承诺：绝不凭空丢弃文字或非 FAQ 行；标点符号精准保留在句末。
 * 3. 智能递归语义切分与滑动窗口重叠对齐。
 */
public class ChunkerEngine {

    public enum Strategy {
        RECURSIVE_SEMANTIC("1. 智能递归语义切片 (推荐: 段落->句子)"),
        CUSTOM_DELIMITER("2. 自定义分隔符/空行/正则切片 (支持自定义字符)"),
        QA_FAQ_PAIR("3. FAQ 问答对一问一答专项 (仅提问Q向量化)"),
        HEADER_HIERARCHY("4. Markdown 标题层级上下文切片"),
        ATOMIC_SENTENCE("5. 按行 / 自然单句原子独立切片"),
        FIXED_SLIDING("6. 定长重叠滑动窗口切片");

        private final String label;

        Strategy(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 前置文本预处理配置
     */
     public static class PreProcessConfig {
        private boolean foldBlankLines = true;
        private boolean trimWhitespace = true;
        private String excludeRegex = "";

        public PreProcessConfig() {}

        public PreProcessConfig(boolean foldBlankLines, boolean trimWhitespace, String excludeRegex) {
            this.foldBlankLines = foldBlankLines;
            this.trimWhitespace = trimWhitespace;
            this.excludeRegex = excludeRegex;
        }

        public boolean isFoldBlankLines() { return foldBlankLines; }
        public boolean isTrimWhitespace() { return trimWhitespace; }
        public String getExcludeRegex() { return excludeRegex; }
    }

    /**
     * 智能读取文件内容，自动识别并支持 UTF-8、GBK/GB18030 等多字符集，过滤 BOM。
     */
    public static String readTextAutoDetect(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = fis.read(bytes, offset, bytes.length - offset);
                if (read == -1) break;
                offset += read;
            }
        }
        // 1. 检查 UTF-8 BOM (0xEF 0xBB 0xBF)
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        // 2. 尝试 UTF-8 解码
        try {
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (!text.contains("\uFFFD")) {
                return text;
            }
        } catch (Exception ignored) {}

        // 3. 尝试 GB18030 / GBK 解码（兼容中文 Windows TXT）
        try {
            Charset gbk = Charset.forName("GB18030");
            String text = new String(bytes, gbk);
            if (!text.contains("\uFFFD")) {
                return text;
            }
        } catch (Exception ignored) {}

        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static List<DocumentChunk> splitFile(File file, Strategy strategy, int chunkSize, int overlap,
                                                String customDelimiter, PreProcessConfig preProc) throws IOException {
        String fullText = readTextAutoDetect(file);
        return splitText(file.getAbsolutePath(), file.getName(), fullText, strategy, chunkSize, overlap, customDelimiter, preProc);
    }

    public static List<DocumentChunk> splitText(String fileId, String fileName, String fullText,
                                                Strategy strategy, int chunkSize, int overlap) {
        return splitText(fileId, fileName, fullText, strategy, chunkSize, overlap, "\\n\\n", new PreProcessConfig());
    }

    public static List<DocumentChunk> splitText(String fileId, String fileName, String fullText,
                                                Strategy strategy, int chunkSize, int overlap,
                                                String customDelimiter, PreProcessConfig preProc) {
        String cleansedText = applyPreProcessing(fullText, preProc);

        List<DocumentChunk> result;
        switch (strategy) {
            case QA_FAQ_PAIR:
                result = splitByQaPair(fileId, fileName, cleansedText);
                break;
            case CUSTOM_DELIMITER:
                result = splitByCustomDelimiter(fileId, fileName, cleansedText, customDelimiter, chunkSize);
                break;
            case HEADER_HIERARCHY:
                result = splitByHeaderHierarchy(fileId, fileName, cleansedText, chunkSize);
                break;
            case ATOMIC_SENTENCE:
                result = splitByAtomicSentence(fileId, fileName, cleansedText);
                break;
            case FIXED_SLIDING:
                result = splitByFixedSliding(fileId, fileName, cleansedText, chunkSize, overlap);
                break;
            case RECURSIVE_SEMANTIC:
            default:
                result = splitByRecursiveSemantic(fileId, fileName, cleansedText, chunkSize, overlap);
                break;
        }
        return result;
    }

    /**
     * 前置清洗预处理
     */
    private static String applyPreProcessing(String text, PreProcessConfig config) {
        if (text == null) return "";
        String processed = text;

        if (config != null && config.getExcludeRegex() != null && !config.getExcludeRegex().trim().isEmpty()) {
            try {
                Pattern pat = Pattern.compile(config.getExcludeRegex().trim(), Pattern.MULTILINE);
                StringBuilder sb = new StringBuilder();
                for (String line : processed.split("\n")) {
                    if (!pat.matcher(line).find()) {
                        sb.append(line).append("\n");
                    }
                }
                processed = sb.toString();
            } catch (Exception ignored) {}
        }

        if (config != null && config.isFoldBlankLines()) {
            processed = processed.replaceAll("\\n{3,}", "\n\n");
        }

        if (config != null && config.isTrimWhitespace()) {
            processed = processed.trim();
        }

        return processed;
    }

    /**
     * 策略3: FAQ 问答对一问一答专项分块 (零丢失保障：非 FAQ 段落也会保存为普通正文切片)
     */
    private static List<DocumentChunk> splitByQaPair(String fileId, String fileName, String text) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        StringBuilder nonQaBuffer = new StringBuilder();
        String currQ = null;
        StringBuilder currA = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (currQ != null) {
                    currA.append("\n");
                } else if (nonQaBuffer.length() > 0) {
                    nonQaBuffer.append("\n");
                }
                continue;
            }

            // 识别严格问答提问头部 (例如 Q: / 问: / 1. Q: / 问题1:)
            if (isQuestionHeader(trimmed)) {
                // 如果有上一对问答，先保存
                if (currQ != null) {
                    addQaChunk(chunks, fileId, fileName, currQ, currA.toString().trim());
                    currA.setLength(0);
                } else if (nonQaBuffer.length() > 0) {
                    // 保存问答对出现之前积累的普通前置文本
                    String nonQaText = nonQaBuffer.toString().trim();
                    if (!nonQaText.isEmpty()) {
                        addRegularChunk(chunks, fileId, fileName, nonQaText, "前导/补充正文说明");
                    }
                    nonQaBuffer.setLength(0);
                }
                currQ = extractQuestionContent(trimmed);
            } else if (isAnswerHeader(trimmed)) {
                currA.append(extractAnswerContent(trimmed)).append("\n");
            } else {
                if (currQ != null) {
                    currA.append(line).append("\n");
                } else {
                    nonQaBuffer.append(line).append("\n");
                }
            }
        }

        if (currQ != null) {
            addQaChunk(chunks, fileId, fileName, currQ, currA.toString().trim());
        } else if (nonQaBuffer.length() > 0) {
            String remaining = nonQaBuffer.toString().trim();
            if (!remaining.isEmpty()) {
                addRegularChunk(chunks, fileId, fileName, remaining, "非FAQ普通正文切片");
            }
        }
        return chunks;
    }

    private static boolean isQuestionHeader(String s) {
        String lower = s.toLowerCase();
        return lower.startsWith("q:") || lower.startsWith("q：") ||
               lower.startsWith("question:") || lower.startsWith("question：") ||
               s.startsWith("问:") || s.startsWith("问：") ||
               s.startsWith("问题:") || s.startsWith("问题：") ||
               s.startsWith("【问】") || s.startsWith("【问题】") ||
               s.startsWith("### q") || s.startsWith("### 问") ||
               s.matches("^\\d+[\\.\\、\\s]+(?:Q|问|问题)?[:：\\s].*") ||
               s.matches("^问题\\s*\\d*[:：\\s].*");
    }

    private static String extractQuestionContent(String s) {
        int idx = s.indexOf(':');
        if (idx < 0) idx = s.indexOf('：');
        if (idx > 0 && idx < 15) {
            return s.substring(idx + 1).trim();
        }
        return s;
    }

    private static boolean isAnswerHeader(String s) {
        String lower = s.toLowerCase();
        return lower.startsWith("a:") || lower.startsWith("a：") ||
               lower.startsWith("answer:") || lower.startsWith("answer：") ||
               s.startsWith("答:") || s.startsWith("答：") ||
               s.startsWith("解答:") || s.startsWith("解答：") ||
               s.startsWith("【答】") || s.startsWith("【解答】") ||
               s.startsWith("### a") || s.startsWith("### 答");
    }

    private static String extractAnswerContent(String s) {
        int idx = s.indexOf(':');
        if (idx < 0) idx = s.indexOf('：');
        if (idx > 0 && idx < 15) {
            return s.substring(idx + 1).trim();
        }
        return s;
    }

    private static void addQaChunk(List<DocumentChunk> chunks, String fileId, String fileName, String q, String a) {
        if (q == null || q.isEmpty()) return;
        String fullContent = "Q: " + q + "\nA: " + (a != null ? a : "");
        String hash = HashUtil.sha256(fullContent);
        String cid = HashUtil.sha256(fileId + "_QA_" + chunks.size() + "_" + hash);

        DocumentChunk chunk = new DocumentChunk(cid, fileId, fileName, chunks.size(), fullContent, hash);
        chunk.setQaQuestion(q);
        chunk.setQaAnswer(a != null ? a : "");
        chunk.setRuleMatched("FAQ问答对 (仅Q计算向量)");
        chunks.add(chunk);
    }

    /**
     * 策略1: 智能递归语义切分 (参考 Dify / LangChain 规范：零丢失、保留标点尾随、自然句子边界 Overlap)
     */
    private static List<DocumentChunk> splitByRecursiveSemantic(String fileId, String fileName, String text, int size, int overlap) {
        String[] separators = new String[]{"\n\n", "\n", "。", "！", "？", ". ", " "};
        List<String> snippets = recursiveSplitLossless(text, separators, 0, size, overlap);
        List<DocumentChunk> chunks = new ArrayList<>();
        for (String snippet : snippets) {
            String trimmed = snippet.trim();
            if (!trimmed.isEmpty()) {
                addRegularChunk(chunks, fileId, fileName, trimmed, "智能递归语义切片");
            }
        }
        return chunks;
    }

    private static List<String> recursiveSplitLossless(String text, String[] separators, int sepIndex, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text.length() <= size) {
            if (!text.trim().isEmpty()) {
                chunks.add(text);
            }
            return chunks;
        }
        if (sepIndex >= separators.length) {
            // 兜底长字符串定长切割
            int len = text.length();
            int step = Math.max(1, size - overlap);
            for (int i = 0; i < len; i += step) {
                int end = Math.min(len, i + size);
                chunks.add(text.substring(i, end));
                if (end >= len) break;
            }
            return chunks;
        }

        String sep = separators[sepIndex];
        List<String> splits = splitWithDelimiterAttached(text, sep);

        List<String> currentChunkPieces = new ArrayList<>();
        int currentLength = 0;

        for (String piece : splits) {
            if (piece.isEmpty()) continue;

            if (currentLength + piece.length() <= size) {
                currentChunkPieces.add(piece);
                currentLength += piece.length();
            } else {
                if (!currentChunkPieces.isEmpty()) {
                    String merged = joinPieces(currentChunkPieces);
                    if (!merged.trim().isEmpty()) {
                        chunks.add(merged);
                    }
                    // 精确提取末尾 overlap 字符保留至缓冲区作为下一切片的首部承接
                    if (overlap > 0 && merged.length() > overlap) {
                        String tail = merged.substring(merged.length() - overlap);
                        currentChunkPieces.clear();
                        currentChunkPieces.add(tail);
                        currentLength = tail.length();
                    } else {
                        currentChunkPieces.clear();
                        currentLength = 0;
                    }
                }
                if (piece.length() > size) {
                    List<String> subChunks = recursiveSplitLossless(piece, separators, sepIndex + 1, size, overlap);
                    chunks.addAll(subChunks);
                    currentChunkPieces.clear();
                    if (!subChunks.isEmpty() && overlap > 0) {
                        String lastSub = subChunks.get(subChunks.size() - 1);
                        String tail = lastSub.length() > overlap ? lastSub.substring(lastSub.length() - overlap) : lastSub;
                        currentChunkPieces.add(tail);
                        currentLength = tail.length();
                    } else {
                        currentLength = 0;
                    }
                } else {
                    currentChunkPieces.add(piece);
                    currentLength += piece.length();
                }
            }
        }

        if (!currentChunkPieces.isEmpty()) {
            String merged = joinPieces(currentChunkPieces);
            if (!merged.trim().isEmpty()) {
                if (chunks.isEmpty() || !chunks.get(chunks.size() - 1).endsWith(merged.trim())) {
                    chunks.add(merged);
                }
            }
        }

        return chunks;
    }

    /**
     * 将分隔符精准保留在每个子句尾部 (不丢标点、不断行)
     */
    private static List<String> splitWithDelimiterAttached(String text, String sep) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (true) {
            int idx = text.indexOf(sep, start);
            if (idx == -1) {
                String remainder = text.substring(start);
                if (!remainder.isEmpty()) {
                    result.add(remainder);
                }
                break;
            }
            int end = idx + sep.length();
            result.add(text.substring(start, end));
            start = end;
        }
        return result;
    }

    private static String joinPieces(List<String> pieces) {
        StringBuilder sb = new StringBuilder();
        for (String p : pieces) {
            sb.append(p);
        }
        return sb.toString();
    }

    /**
     * 策略2: 自定义分隔符
     */
    private static List<DocumentChunk> splitByCustomDelimiter(String fileId, String fileName, String text,
                                                              String delimiter, int size) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String actualDelim = (delimiter == null || delimiter.trim().isEmpty()) ? "\\n\\n" : delimiter.trim();
        if ("\\n\\n".equals(actualDelim)) actualDelim = "\\n{2,}";

        String[] parts;
        try {
            parts = text.split(actualDelim);
        } catch (Exception e) {
            parts = text.split(Pattern.quote(actualDelim));
        }

        for (String part : parts) {
            String sub = part.trim();
            if (!sub.isEmpty()) {
                addRegularChunk(chunks, fileId, fileName, sub, "自定义规则: " + actualDelim);
            }
        }
        return chunks;
    }

    /**
     * 策略4: Markdown 标题层级切分
     */
    private static List<DocumentChunk> splitByHeaderHierarchy(String fileId, String fileName, String text, int size) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String[] parts = text.split("(?m)^#{1,4}\\s+");

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            int firstLineEnd = trimmed.indexOf('\n');
            String headerTitle = firstLineEnd > 0 ? trimmed.substring(0, firstLineEnd).trim() : "章节小节";
            String bodyText = firstLineEnd > 0 ? trimmed.substring(firstLineEnd).trim() : trimmed;

            String enriched = "[文档: " + fileName + " | 章节层级: " + headerTitle + "]\n" + bodyText;
            addRegularChunk(chunks, fileId, fileName, enriched, "Markdown 结构化分章");
        }
        return chunks;
    }

    /**
     * 策略5: 原子句切分
     */
    private static List<DocumentChunk> splitByAtomicSentence(String fileId, String fileName, String text) {
        List<DocumentChunk> chunks = new ArrayList<>();
        List<String> splits = splitWithDelimiterAttached(text, "。");
        for (String s : splits) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                addRegularChunk(chunks, fileId, fileName, trimmed, "单句原子切分");
            }
        }
        return chunks;
    }

    /**
     * 策略6: 定长滑动窗口
     */
    private static List<DocumentChunk> splitByFixedSliding(String fileId, String fileName, String text, int size, int overlap) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int len = text.length();
        int step = Math.max(1, size - overlap);
        for (int i = 0; i < len; i += step) {
            int end = Math.min(len, i + size);
            String snippet = text.substring(i, end).trim();
            if (!snippet.isEmpty()) {
                addRegularChunk(chunks, fileId, fileName, snippet, "定长窗口(字数:" + snippet.length() + ")");
            }
            if (end >= len) break;
        }
        return chunks;
    }

    private static void addRegularChunk(List<DocumentChunk> chunks, String fileId, String fileName, String content, String rule) {
        String hash = HashUtil.sha256(content);
        String cid = HashUtil.sha256(fileId + "_" + chunks.size() + "_" + hash);
        DocumentChunk chunk = new DocumentChunk(cid, fileId, fileName, chunks.size(), content, hash);
        chunk.setRuleMatched(rule);
        chunks.add(chunk);
    }
}
