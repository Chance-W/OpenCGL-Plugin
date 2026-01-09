package com.opencgl.base.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 分类图标信息类 (Migrated from OpenCGL_New)
 * 
 * @author Chance.W
 */
public class CategoryIconInfo {

    /**
     * FontAwesome 图标名称，如 "fas-wrench"
     */
    private String iconName;
    
    /**
     * 图标是否已固定
     */
    private boolean pinned;
    
    public CategoryIconInfo() {
    }
    
    public CategoryIconInfo(String iconName, boolean pinned) {
        this.iconName = iconName;
        this.pinned = pinned;
    }

    public String getIconName() {
        return iconName;
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    @Override
    public String toString() {
        return "CategoryIconInfo{" +
                "iconName='" + iconName + '\'' +
                ", pinned=" + pinned +
                '}';
    }
}
