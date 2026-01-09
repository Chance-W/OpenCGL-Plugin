package com.opencgl.plugin.ragingestion.model;

import java.io.Serializable;

/**
 * 相似度在线检索返回结果实体。
 */
public class SearchResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private int rank;
    private double score;
    private String chunkId;
    private String fileName;
    private String content;

    public SearchResult() {
    }

    public SearchResult(int rank, double score, String chunkId, String fileName, String content) {
        this.rank = rank;
        this.score = score;
        this.chunkId = chunkId;
        this.fileName = fileName;
        this.content = content;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
