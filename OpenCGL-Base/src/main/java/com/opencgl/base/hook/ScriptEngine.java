package com.opencgl.base.hook;

import java.nio.file.Path;

/**
 * 脚本引擎接口
 * 支持从文件或字符串加载脚本并转换为 RequestHook
 *
 * @author Chance.W
 */
public interface ScriptEngine {

    /**
     * 支持的脚本类型
     * @return "groovy" 或 "javascript"
     */
    String getType();

    /**
     * 支持的文件扩展名
     * @return 如 [".groovy"] 或 [".js"]
     */
    String[] getExtensions();

    /**
     * 从文件加载脚本
     * @param scriptPath 脚本路径
     * @return RequestHook 实现
     * @throws Exception 加载失败
     */
    RequestHook loadFromFile(Path scriptPath) throws Exception;

    /**
     * 从字符串加载脚本
     * @param scriptContent 脚本内容
     * @return RequestHook 实现
     * @throws Exception 加载失败
     */
    RequestHook loadFromString(String scriptContent) throws Exception;

    /**
     * 检查引擎是否可用 (依赖是否存在)
     * @return true 如果依赖可用
     */
    boolean isAvailable();
}
