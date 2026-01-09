package com.opencgl.base.hook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hook 执行上下文
 * 携带接口名、方法、环境等信息供脚本使用
 *
 * @author Chance.W
 */
public class HookContext {

    private String interfaceName;
    private String methodName;
    private String environment;
    private String protocol;  // dubbo / http / grpc
    private Map<String, String> headers = new HashMap<>();
    private Map<String, Object> extra = new HashMap<>();
    private final List<String> scriptOutput = new CopyOnWriteArrayList<>();
    private transient ScriptLog scriptLog;

    public HookContext() {
    }

    public HookContext(String interfaceName, String methodName, String environment, String protocol) {
        this.interfaceName = interfaceName;
        this.methodName = methodName;
        this.environment = environment;
        this.protocol = protocol;
    }

    public void put(String key, Object value) {
        extra.put(key, value);
    }

    public Object get(String key) {
        return extra.get(key);
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    // Getters and Setters

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public void print(Object msg) {
        scriptOutput.add(String.valueOf(msg));
    }

    public void println(Object msg) {
        scriptOutput.add(String.valueOf(msg));
    }

    public List<String> getScriptOutput() {
        return scriptOutput;
    }

    public void clearScriptOutput() {
        scriptOutput.clear();
    }

    /**
     * 获取脚本统一日志对象。对象复用同一个上下文，因此日志会同时出现在
     * Hook 测试输出和全局操作历史中。
     */
    public ScriptLog getScriptLog() {
        if (scriptLog == null) {
            scriptLog = new ScriptLog(this);
        }
        return scriptLog;
    }
}
