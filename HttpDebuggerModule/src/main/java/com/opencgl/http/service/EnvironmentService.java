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
    private static final String EXPORT_FORMAT = "opencgl-http-environment";

    public String copyEnvironment(String name) {
        requireExisting(name);
        return copyEnvironment(name, getEnvironment(name));
    }

    public String copyEnvironment(String name, Map<String, String> variables) {
        requireExisting(name);
        return createUnique(name + " 副本", new LinkedHashMap<>(variables));
    }

    public String exportEnvironment(String name) {
        return exportEnvironment(name, getEnvironment(name));
    }

    public String exportEnvironment(String name, Map<String, String> variables) {
        requireExisting(name);
        var document = new LinkedHashMap<String, Object>();
        document.put("format", EXPORT_FORMAT);
        document.put("version", 1);
        document.put("name", name);
        document.put("variables", new TreeMap<>(variables));
        return com.alibaba.fastjson.JSON.toJSONString(document, true);
    }

    public String importEnvironment(String json) {
        if (json == null || json.length() > 5 * 1024 * 1024) throw new IllegalArgumentException("Invalid environment file");
        Object parsed;
        try {
            parsed = com.alibaba.fastjson.JSON.parse(json, com.alibaba.fastjson.parser.Feature.DisableSpecialKeyDetect);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid environment JSON", invalid);
        }
        if (!(parsed instanceof Map<?, ?> doc)
            || !EXPORT_FORMAT.equals(doc.get("format")) || !Integer.valueOf(1).equals(doc.get("version"))
            || !(doc.get("name") instanceof String name) || name.isBlank() || "None".equals(name.trim())
            || !(doc.get("variables") instanceof Map<?, ?> vars)) {
            throw new IllegalArgumentException("Unsupported environment document");
        }
        var values = new LinkedHashMap<String, String>();
        for (var entry : vars.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank() || !(entry.getValue() instanceof String value)) {
                throw new IllegalArgumentException("Variable names and values must be strings");
            }
            values.put(key, value);
        }
        return createUnique(name.trim(), values);
    }

    private void requireExisting(String name) {
        if (name == null || DEFAULT_ENV_NAME.equals(name) || !getEnvironmentNames().contains(name)) {
            throw new IllegalArgumentException("Select an existing environment");
        }
    }

    private String createUnique(String base, Map<String, String> variables) {
        var existing = new HashSet<>(getEnvironmentNames());
        String name = base;
        for (int number = 2; existing.contains(name); number++) name = base + " (" + number + ")";
        repository.createEnvironment(name, variables);
        return name;
    }
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
        if (name.equals(currentEnvName)) currentEnvName = DEFAULT_ENV_NAME;
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
