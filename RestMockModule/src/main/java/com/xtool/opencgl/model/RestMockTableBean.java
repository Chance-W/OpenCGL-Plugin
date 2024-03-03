package com.xtool.opencgl.model;

import javafx.beans.property.SimpleBooleanProperty;
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

    public RestMockTableBean(Boolean isEnable, String contentTextPath, String responseHeader, String responseContent, String des) {
        this.isEnable = new SimpleBooleanProperty(isEnable);
        this.des = new SimpleStringProperty(des);
        this.contentTextPath = new SimpleStringProperty(contentTextPath);
        this.responseHeader = new SimpleStringProperty(responseHeader);
        this.responseContent = new SimpleStringProperty(responseContent);
    }
}

