package com.opencgl.api;

/**
 * 主题感知接口 - 插件可选实现
 * 
 * 【重要】此接口是可选的：
 * - 不实现此接口：插件UI完全不受应用主题影响
 * - 实现此接口：插件会在主题切换时收到通知，可自行更新样式
 * 
 * @author Chance.W
 */
public interface ThemeAware {
    
    /**
     * 当应用主题发生变化时调用
     * 
     * @param theme 新的主题信息，包含主题名称、颜色等
     */
    void onThemeChanged(ThemeInfo theme);
}
