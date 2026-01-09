package com.opencgl.api;

import java.util.Locale;

/**
 * 插件国际化通知接口。
 * 对于没有集成 OpenCGL-Base 的独立插件，可以通过实现此接口来响应主程序的语言切换。
 */
public interface PluginI18n {

    /**
     * 当主程序语言切换时被调用。
     * @param newLocale 切换后的新语言区域
     */
    void onLanguageChange(Locale newLocale);
}
