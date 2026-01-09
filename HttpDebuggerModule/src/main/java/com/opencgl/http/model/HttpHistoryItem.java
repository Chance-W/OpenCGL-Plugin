package com.opencgl.http.model;

import com.opencgl.base.model.HistoryItem;

import java.util.Date;

/**
 * HTTP 请求历史记录实体
 */
public class HttpHistoryItem extends HistoryItem {
    private String method;
    private String url;
    private Integer statusCode;
    private Long duration; // ms
    private Long size; // bytes
    
    // Snapshots (JSON strings)
    private String requestSnapshot; // HttpRequestModel JSON
    private String responseSnapshot; // HttpResponseModel JSON (optional, maybe just body/headers)

    public HttpHistoryItem() {
        super();
    }

    public HttpHistoryItem(String id, Date timestamp, String status, String summary) {
        super(id, timestamp, status, summary);
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    // requestTime is now timestamp in parent
    public Date getRequestTime() {
        return getTimestamp();
    }

    public void setRequestTime(Date requestTime) {
        setTimestamp(requestTime);
    }

    public String getRequestSnapshot() {
        return requestSnapshot;
    }

    public void setRequestSnapshot(String requestSnapshot) {
        this.requestSnapshot = requestSnapshot;
    }

    public String getResponseSnapshot() {
        return responseSnapshot;
    }

    public void setResponseSnapshot(String responseSnapshot) {
        this.responseSnapshot = responseSnapshot;
    }
}
