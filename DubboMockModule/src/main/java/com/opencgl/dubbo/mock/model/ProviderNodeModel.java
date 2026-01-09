package com.opencgl.dubbo.mock.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Provider 节点模型（树的二级节点）
 * 代表一个被 Mock 的 Dubbo 服务接口，一个 Provider 可以 Mock 多个方法
 */
public class ProviderNodeModel {

    /** 唯一标识符，用于持久化和查询 */
    private String id;

    /** 接口全限定名，例如 "com.example.DemoService" */
    private String interfaceName;

    /** Dubbo 服务版本号，默认可为空 */
    private String version;

    /** Dubbo 服务分组，默认可为空 */
    private String group;

    /** 此提供者下记录的方法级 Mock 规则列表（树的三级节点） */
    private List<MethodMockConfig> methods;

    /**
     * 运行状态标记（非持久化字段）
     * true: 正在对外暴露服务
     * false: 未暴露服务
     */
    private transient boolean running = false;

    /** 运行时日志回调（非持久化），将请求记录输出至 UI */
    private transient java.util.function.Consumer<String> logListener;

    public ProviderNodeModel() {
        this.id = UUID.randomUUID().toString();
        this.version = "";
        this.group = "";
        this.methods = new ArrayList<>();
    }

    /** UI 树节点展示用的标题展示名称 */
    public String getDisplayName() {
        if (interfaceName == null || interfaceName.trim().isEmpty()) {
            return "Unconfigured Provider";
        }
        // 尝试只取短类名，如 com.example.DemoService -> DemoService
        int lastDotIndex = interfaceName.lastIndexOf(".");
        if (lastDotIndex != -1 && lastDotIndex < interfaceName.length() - 1) {
            return interfaceName.substring(lastDotIndex + 1);
        }
        return interfaceName;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInterfaceName() { return interfaceName; }
    public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public List<MethodMockConfig> getMethods() { return methods; }
    public void setMethods(List<MethodMockConfig> methods) { this.methods = methods; }
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
    public java.util.function.Consumer<String> getLogListener() { return logListener; }
    public void setLogListener(java.util.function.Consumer<String> logListener) { this.logListener = logListener; }
}
