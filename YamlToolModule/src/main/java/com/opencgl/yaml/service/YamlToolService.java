package com.opencgl.yaml.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * YAML 工具服务
 * 支持格式化、校验、YAML/JSON 互转
 *
 * @author OpenCGL
 */
public class YamlToolService {

    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * 智能格式化 - 自动检测 JSON 或 YAML 并格式化
     */
    public String format(String content) throws Exception {
        String trimmed = content.trim();

        // 检测是否是 JSON (以 { 或 [ 开头)
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            // JSON 格式化
            Object data = jsonMapper.readValue(content, Object.class);
            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } else {
            // YAML 格式化
            return formatYaml(content);
        }
    }

    /**
     * 格式化 YAML
     */
    public String formatYaml(String yamlContent) {
        Yaml yaml = new Yaml();
        Object data = yaml.load(yamlContent);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(1);

        Yaml prettyYaml = new Yaml(options);
        return prettyYaml.dump(data);
    }

    /**
     * 校验 YAML
     */
    public ValidationResult validate(String yamlContent) {
        try {
            Yaml yaml = new Yaml();
            yaml.load(yamlContent);
            return new ValidationResult(true, "YAML 格式正确");
        } catch (Exception e) {
            return new ValidationResult(false, "YAML 格式错误: " + e.getMessage());
        }
    }

    /**
     * YAML 转 JSON
     */
    public String yamlToJson(String yamlContent) throws Exception {
        Yaml yaml = new Yaml();
        Object data = yaml.load(yamlContent);
        return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
    }

    /**
     * JSON 转 YAML
     */
    public String jsonToYaml(String jsonContent) throws Exception {
        Object data = jsonMapper.readValue(jsonContent, Object.class);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        Yaml yaml = new Yaml(options);
        return yaml.dump(data);
    }

    /**
     * 压缩 YAML (单行)
     */
    public String compress(String yamlContent) {
        Yaml yaml = new Yaml();
        Object data = yaml.load(yamlContent);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        options.setWidth(Integer.MAX_VALUE);

        Yaml flowYaml = new Yaml(options);
        return flowYaml.dump(data);
    }

    /**
     * 合并两个 YAML (后者覆盖前者)
     */
    @SuppressWarnings("unchecked")
    public String merge(String yaml1, String yaml2) {
        Yaml yaml = new Yaml();
        Map<String, Object> map1 = yaml.load(yaml1);
        Map<String, Object> map2 = yaml.load(yaml2);

        if (map1 == null)
            map1 = new java.util.HashMap<>();
        if (map2 != null) {
            deepMerge(map1, map2);
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);

        Yaml outputYaml = new Yaml(options);
        return outputYaml.dump(map1);
    }

    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object sourceValue = entry.getValue();
            Object targetValue = target.get(key);

            if (sourceValue instanceof Map && targetValue instanceof Map) {
                deepMerge((Map<String, Object>) targetValue, (Map<String, Object>) sourceValue);
            } else {
                target.put(key, sourceValue);
            }
        }
    }

    /**
     * YAML 转 Properties
     */
    @SuppressWarnings("unchecked")
    public String yamlToProperties(String yamlContent) {
        Yaml yaml = new Yaml();
        Object data = yaml.load(yamlContent);

        StringBuilder sb = new StringBuilder();
        if (data instanceof Map) {
            flattenMap("", (Map<String, Object>) data, sb);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void flattenMap(String prefix, Map<String, Object> map, StringBuilder sb) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                flattenMap(key, (Map<String, Object>) value, sb);
            } else if (value instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        flattenMap(key + "[" + i + "]", (Map<String, Object>) item, sb);
                    } else {
                        sb.append(key).append("[").append(i).append("]").append("=").append(item).append("\n");
                    }
                }
            } else {
                sb.append(key).append("=").append(value == null ? "" : value).append("\n");
            }
        }
    }

    /**
     * Properties 转 YAML
     */
    public String propertiesToYaml(String propertiesContent) throws Exception {
        Properties props = new Properties();
        props.load(new StringReader(propertiesContent));

        Map<String, Object> result = new LinkedHashMap<>();

        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            setNestedValue(result, key, value);
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        Yaml yaml = new Yaml(options);
        return yaml.dump(result);
    }

    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> map, String key, String value) {
        String[] parts = key.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            // 处理数组索引 key[0] 形式
            if (part.contains("[")) {
                part = part.substring(0, part.indexOf('['));
            }

            if (!current.containsKey(part)) {
                current.put(part, new java.util.LinkedHashMap<>());
            }
            Object next = current.get(part);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                Map<String, Object> newMap = new java.util.LinkedHashMap<>();
                current.put(part, newMap);
                current = newMap;
            }
        }

        String lastPart = parts[parts.length - 1];
        if (lastPart.contains("[")) {
            lastPart = lastPart.substring(0, lastPart.indexOf('['));
        }
        current.put(lastPart, value);
    }

    public record ValidationResult(boolean valid, String message) {
    }
}
