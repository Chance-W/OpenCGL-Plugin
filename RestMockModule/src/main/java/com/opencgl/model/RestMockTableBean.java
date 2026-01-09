package com.opencgl.model;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class RestMockTableBean {
    public final SimpleBooleanProperty isEnable;
    public final SimpleStringProperty des;
    public final SimpleStringProperty contentTextPath;
    public final SimpleStringProperty responseHeader;
    public final SimpleStringProperty responseContent;
    /** HTTP 方法: GET / POST / PUT / DELETE 等 */
    public final SimpleStringProperty httpMethod;
    /** 响应状态码 */
    public final SimpleIntegerProperty statusCode;
    /** 模拟延迟（毫秒） */
    public final SimpleIntegerProperty delayMs;

    public boolean getIsEnable() {
        return isEnable.get();
    }

    public SimpleBooleanProperty isEnableProperty() {
        return isEnable;
    }

    public void setIsEnable(boolean isEnable) {
        this.isEnable.set(isEnable);
    }

    public void setDes(String des) {
        this.des.set(des);
    }

    public String getContentTextPath() {
        return contentTextPath.get();
    }

    public SimpleStringProperty contentTextPathProperty() {
        return contentTextPath;
    }

    public void setContentTextPath(String contentTextPath) {
        this.contentTextPath.set(contentTextPath);
    }

    public String getResponseHeader() {
        return responseHeader.get();
    }

    public SimpleStringProperty responseHeaderProperty() {
        return responseHeader;
    }

    public void setResponseHeader(String responseHeader) {
        this.responseHeader.set(responseHeader);
    }

    public String getResponseContent() {
        return responseContent.get();
    }

    public SimpleStringProperty responseContentProperty() {
        return responseContent;
    }

    public void setResponseContent(String responseContent) {
        this.responseContent.set(responseContent);
    }

    public String getDes() {
        return des.get();
    }

    public SimpleStringProperty desProperty() {
        return des;
    }

    public String getHttpMethod() {
        return httpMethod.get();
    }

    public SimpleStringProperty httpMethodProperty() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod.set(httpMethod);
    }

    public int getStatusCode() {
        return statusCode.get();
    }

    public SimpleIntegerProperty statusCodeProperty() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode.set(statusCode);
    }

    public int getDelayMs() {
        return delayMs.get();
    }

    public SimpleIntegerProperty delayMsProperty() {
        return delayMs;
    }

    public void setDelayMs(int delayMs) {
        this.delayMs.set(delayMs);
    }

    public RestMockTableBean(Boolean isEnable, String contentTextPath, String responseHeader,
            String responseContent, String des,
            String httpMethod, Integer statusCode, Integer delayMs) {
        this.isEnable = new SimpleBooleanProperty(isEnable);
        this.des = new SimpleStringProperty(des);
        this.contentTextPath = new SimpleStringProperty(contentTextPath);
        this.responseHeader = new SimpleStringProperty(responseHeader);
        this.responseContent = new SimpleStringProperty(responseContent);
        this.httpMethod = new SimpleStringProperty(httpMethod != null ? httpMethod : "GET");
        this.statusCode = new SimpleIntegerProperty(statusCode != null ? statusCode : 200);
        this.delayMs = new SimpleIntegerProperty(delayMs != null ? delayMs : 0);
    }
}
