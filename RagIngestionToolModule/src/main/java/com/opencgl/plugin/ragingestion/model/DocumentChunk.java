package com.opencgl.plugin.ragingestion.model;

import java.io.Serializable;
import java.util.Arrays;

/**
 * 文档切分片段实体，用于在界面预览、哈希对齐、向量计算与数据库读写之间传递。
 * 支持普通正文分块与 FAQ 问答对 (Q&A) 分块模式。
 */
public class DocumentChunk implements Serializable {
    private static final long serialVersionUID = 1L;

    private String chunkId;         // SHA-256 唯一主键指纹
    private String fileId;          // 所属文档标识/路径
    private String fileName;        // 文件名
    private int chunkIndex;         // 序号 (0, 1, 2...)
    private String content;         // 切断内容文字 (展示用全文或常规全文)
    private String contentHash;     // 内容 SHA-256 散列，判定是否已变更
    private int charLength;         // 字数
    private float[] embedding;      // 向量 float 数组
    private String diffStatus;      // UNCHANGED(已存在跳过), NEW(新建待算), MODIFIED(内容改动)
    private String processStatus;   // PENDING, EMBEDDING, SUCCESS, FAILED, SKIPPED

    // FAQ 问答对扩展属性与切片规则元数据
    private String qaQuestion;      // FAQ 问答提问 (作为实际 Embedding 索引的 Q 文本)
    private String qaAnswer;        // FAQ 问答权威解答 (随附保存的有效载荷 A 文本)
    private String ruleMatched;     // 命中的分切规则名称 (例如 "递归语义", "FAQ问答", "自定义: ---" 等)

    public DocumentChunk() {
    }

    public DocumentChunk(String chunkId, String fileId, String fileName, int chunkIndex,
                         String content, String contentHash) {
        this.chunkId = chunkId;
        this.fileId = fileId;
        this.fileName = fileName;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.contentHash = contentHash;
        this.charLength = content != null ? content.length() : 0;
        this.diffStatus = "NEW";
        this.processStatus = "PENDING";
        this.ruleMatched = "自动解析";
    }

    /**
     * 获取真正送去计算 Embedding 向量的目标文本。
     * 若为 FAQ 问答对且 Q 非空，仅对 Q 提问部分进行向量计算！
     */
    public String getEmbedTargetText() {
        if (qaQuestion != null && !qaQuestion.trim().isEmpty()) {
            return qaQuestion.trim();
        }
        return content;
    }

    public boolean isQaPair() {
        return qaQuestion != null && !qaQuestion.trim().isEmpty();
    }

    public void setQaPair(boolean qaPair) {
        // boolean flag helper computed by qaQuestion
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.charLength = content != null ? content.length() : 0;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public int getCharLength() {
        return charLength;
    }

    public void setCharLength(int charLength) {
        this.charLength = charLength;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public String getDiffStatus() {
        return diffStatus;
    }

    public void setDiffStatus(String diffStatus) {
        this.diffStatus = diffStatus;
    }

    public String getProcessStatus() {
        return processStatus;
    }

    public void setProcessStatus(String processStatus) {
        this.processStatus = processStatus;
    }

    public String getQaQuestion() {
        return qaQuestion;
    }

    public void setQaQuestion(String qaQuestion) {
        this.qaQuestion = qaQuestion;
    }

    public String getQaAnswer() {
        return qaAnswer;
    }

    public void setQaAnswer(String qaAnswer) {
        this.qaAnswer = qaAnswer;
    }

    public String getRuleMatched() {
        return ruleMatched;
    }

    public void setRuleMatched(String ruleMatched) {
        this.ruleMatched = ruleMatched;
    }

    @Override
    public String toString() {
        return "DocumentChunk{" +
                "chunkId='" + chunkId + '\'' +
                ", chunkIndex=" + chunkIndex +
                ", charLength=" + charLength +
                ", diffStatus='" + diffStatus + '\'' +
                ", ruleMatched='" + ruleMatched + '\'' +
                '}';
    }
}
