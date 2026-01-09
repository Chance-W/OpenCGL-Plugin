package com.opencgl.api;

/**
 * 主题信息类 - 传递给插件的主题数据
 * 
 * @author Chance.W
 */
public class ThemeInfo {
    private final String themeName;
    private final String primaryColor;
    private final String backgroundColor;
    private final String textColor;
    private final boolean isDark;

    public ThemeInfo(String themeName, String primaryColor, 
                     String backgroundColor, String textColor, boolean isDark) {
        this.themeName = themeName;
        this.primaryColor = primaryColor;
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
        this.isDark = isDark;
    }

    /**
     * 获取主题名称
     * @return 主题名称: default, dark, purple, blue
     */
    public String getThemeName() {
        return themeName;
    }

    /**
     * 获取主色调（侧边栏背景色）
     * @return 主色调十六进制颜色值
     */
    public String getPrimaryColor() {
        return primaryColor;
    }

    /**
     * 获取内容区背景色
     * @return 背景色十六进制颜色值
     */
    public String getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * 获取主文字颜色
     * @return 文字颜色
     */
    public String getTextColor() {
        return textColor;
    }

    /**
     * 是否为深色主题
     * @return 深色主题返回true
     */
    public boolean isDark() {
        return isDark;
    }

    @Override
    public String toString() {
        return "ThemeInfo{" +
                "themeName='" + themeName + '\'' +
                ", primaryColor='" + primaryColor + '\'' +
                ", backgroundColor='" + backgroundColor + '\'' +
                ", textColor='" + textColor + '\'' +
                ", isDark=" + isDark +
                '}';
    }
}
