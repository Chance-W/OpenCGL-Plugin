package com.opencgl.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 环境变量解析器
 * 支持 {{variable}} 语法
 */
public class VariableResolver {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");
    
    private final Map<String, Map<String, String>> environments = new HashMap<>();
    private final Map<String, String> globalVariables = new HashMap<>();
    private String currentEnvironment = null;
    
    /**
     * 添加环境
     */
    public void addEnvironment(String name, Map<String, String> variables) {
        environments.put(name, new HashMap<>(variables));
    }
    
    /**
     * 设置当前环境
     */
    public void setCurrentEnvironment(String name) {
        this.currentEnvironment = name;
    }
    
    /**
     * 设置全局变量
     */
    public void setGlobalVariable(String key, String value) {
        globalVariables.put(key, value);
    }
    
    /**
     * 解析字符串中的变量
     */
    public String resolve(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            String value = getVariableValue(varName);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 获取变量值
     * 优先级：当前环境变量 > 全局变量
     */
    private String getVariableValue(String varName) {
        // 先查当前环境
        if (currentEnvironment != null && environments.containsKey(currentEnvironment)) {
            Map<String, String> envVars = environments.get(currentEnvironment);
            if (envVars.containsKey(varName)) {
                return envVars.get(varName);
            }
        }
        
        // 再查全局变量
        if (globalVariables.containsKey(varName)) {
            return globalVariables.get(varName);
        }
        
        return null;
    }
    
    /**
     * 检查字符串是否包含变量
     */
    public static boolean containsVariable(String input) {
        if (input == null) return false;
        return VARIABLE_PATTERN.matcher(input).find();
    }
    
    /**
     * 提取所有变量名
     */
    public static java.util.List<String> extractVariables(String input) {
        java.util.List<String> variables = new java.util.ArrayList<>();
        if (input == null) return variables;
        
        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        while (matcher.find()) {
            variables.add(matcher.group(1).trim());
        }
        return variables;
    }
    
    /**
     * 获取变量的Tooltip文本
     */
    public String getVariableTooltip(String varName) {
        String value = getVariableValue(varName);
        if (value != null) {
            String source = "global";
            if (currentEnvironment != null && environments.containsKey(currentEnvironment)) {
                if (environments.get(currentEnvironment).containsKey(varName)) {
                    source = currentEnvironment;
                }
            }
            return varName + " = \"" + value + "\" (" + source + ")";
        }
        return varName + " (undefined)";
    }
    
    /**
     * 获取当前环境名
     */
    public String getCurrentEnvironment() {
        return currentEnvironment;
    }
    
    /**
     * 获取所有环境名
     */
    public java.util.Set<String> getEnvironmentNames() {
        return environments.keySet();
    }
    
    /**
     * 获取环境变量
     */
    public Map<String, String> getEnvironmentVariables(String envName) {
        return environments.getOrDefault(envName, new HashMap<>());
    }
}
