package com.opencgl.base.hook;

/**
 * 通用请求/响应处理 Hook 接口
 * 可由脚本引擎或 Java 类实现
 *
 * @author Chance.W
 */
public interface RequestHook {

    /**
     * 请求预处理
     *
     * @param content 原始请求内容 (JSON/XML/Text)
     * @param context 上下文 (接口名、方法、环境、headers 等)
     * @return 处理后的请求内容
     */
    String preProcess(String content, HookContext context);

    /**
     * 响应后处理
     *
     * @param content 原始响应内容
     * @param context 上下文
     * @return 处理后的响应内容
     */
    String postProcess(String content, HookContext context);

    /**
     * 配置 TLS (可选，由脚本提供证书路径等)
     *
     * @param context 上下文
     * @return TLS 配置，返回 null 表示不使用 TLS
     */
    default TlsConfig configureTls(HookContext context) {
        return null;
    }

    /**
     * Hook 名称 (用于 UI 显示)
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Hook 描述
     */
    default String getDescription() {
        return "";
    }
}
