package com.opencgl.http.service;

import com.opencgl.http.repository.HttpEnvironmentRepository;
import com.opencgl.http.repository.impl.HttpEnvironmentRepositoryImpl;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 环境变量管理服务
 * 支持管理多个环境配置，并提供 {{variable}} 替换功能；持久化到 SQLite。
 */
public class EnvironmentService {
    /** 默认环境名称（下拉默认显示 None，不再使用 No Environment） */
    public static final String DEFAULT_ENV_NAME = "None";

    private final HttpEnvironmentRepository repository = new HttpEnvironmentRepositoryImpl();
    private String currentEnvName = DEFAULT_ENV_NAME;

    public List<String> getEnvironmentNames() {
        return repository.findAllEnvNames();
    }

    public Map<String, String> getEnvironment(String name) {
        return repository.getVariables(name != null ? name : DEFAULT_ENV_NAME);
    }

    public void updateEnvironment(String name, Map<String, String> vars) {
        if (name == null || name.trim().isEmpty()) return;
        repository.saveEnvironment(name.trim(), vars != null ? vars : new HashMap<>());
    }

    public void deleteEnvironment(String name) {
        if (name == null || DEFAULT_ENV_NAME.equals(name)) return;
        repository.deleteEnvironment(name);
    }

    public void setCurrentEnvName(String name) {
        this.currentEnvName = name != null ? name : DEFAULT_ENV_NAME;
    }

    public String getCurrentEnvName() {
        return currentEnvName != null ? currentEnvName : DEFAULT_ENV_NAME;
    }

    /**
     * 执行变量替换：将 {{key}} 或 ${key} 替换为当前环境中的 value
     */
    public String substitute(String content) {
        if (content == null || content.isEmpty()) return content;

        Map<String, String> vars = getEnvironment(currentEnvName);
        if (vars.isEmpty()) return content;

        Pattern pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}|\\$\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key;
            if (matcher.group(1) != null) {
                key = matcher.group(1).trim();
            } else {
                key = matcher.group(2).trim();
            }
            String value = vars.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
