package com.opencgl.base.model;

import java.util.Date;

/**
 * Abstract class representing a history item.
 * Plugins should extend this class to add specific fields.
 */
public abstract class HistoryItem {
    private String id;
    private Date timestamp;
    private String status; // e.g., "SUCCESS", "ERROR"
    private String summary; // Short description

    public HistoryItem(String id, Date timestamp, String status, String summary) {
        this.id = id;
        this.timestamp = timestamp;
        this.status = status;
        this.summary = summary;
    }

    public HistoryItem() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
