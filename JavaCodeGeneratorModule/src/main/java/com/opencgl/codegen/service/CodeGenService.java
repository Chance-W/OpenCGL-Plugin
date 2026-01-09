package com.opencgl.codegen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java代码生成服务
 */
public class CodeGenService {

    private static final Logger logger = LoggerFactory.getLogger(CodeGenService.class);

    public enum NamingStyle {
        CAMEL_CASE,       // camelCase
        PASCAL_CASE,      // PascalCase  
        SNAKE_CASE,       // snake_case
        UPPER_SNAKE_CASE  // UPPER_SNAKE_CASE
    }

    /**
     * 从JSON生成Java类
     */
    public String generateFromJson(String json, String className, NamingStyle classStyle, 
                                   NamingStyle fieldStyle, NamingStyle methodStyle, 
                                   boolean useLombok, String packageName) {
        try {
            Map<String, Object> map = parseJson(json.trim());
            return generateClass(map, className, classStyle, fieldStyle, methodStyle, useLombok, packageName);
        } catch (Exception e) {
            logger.error("JSON解析失败", e);
            throw new RuntimeException("JSON解析失败: " + e.getMessage());
        }
    }

    /**
     * 从XML生成Java类
     */
    public String generateFromXml(String xml, String className, NamingStyle classStyle,
                                  NamingStyle fieldStyle, NamingStyle methodStyle,
                                  boolean useLombok, String packageName) {
        try {
            Map<String, Object> map = parseXml(xml.trim());
            return generateClass(map, className, classStyle, fieldStyle, methodStyle, useLombok, packageName);
        } catch (Exception e) {
            logger.error("XML解析失败", e);
            throw new RuntimeException("XML解析失败: " + e.getMessage());
        }
    }

    /**
     * JSON解析器 - 支持嵌套对象和数组中的对象
     */
    private Map<String, Object> parseJson(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        json = json.trim();
        
        // 移除末尾多余逗号
        json = json.replaceAll(",\\s*}", "}").replaceAll(",\\s*]", "]");
        
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new RuntimeException("无效的JSON格式");
        }
        
        json = json.substring(1, json.length() - 1).trim();
        
        int pos = 0;
        while (pos < json.length()) {
            // 跳过空白
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
            if (pos >= json.length()) break;
            
            // 找key
            if (json.charAt(pos) != '"') {
                pos++;
                continue;
            }
            int keyStart = pos + 1;
            int keyEnd = json.indexOf('"', keyStart);
            if (keyEnd < 0) break;
            String key = json.substring(keyStart, keyEnd);
            pos = keyEnd + 1;
            
            // 找冒号
            while (pos < json.length() && json.charAt(pos) != ':') pos++;
            pos++;
            
            // 跳过空白
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
            if (pos >= json.length()) break;
            
            char c = json.charAt(pos);
            if (c == '{') {
                // 嵌套对象
                int end = findMatchingBrace(json, pos, '{', '}');
                String nested = json.substring(pos, end + 1);
                result.put(key, parseJson(nested));
                pos = end + 1;
            } else if (c == '[') {
                // 数组
                int end = findMatchingBrace(json, pos, '[', ']');
                String arrayContent = json.substring(pos + 1, end).trim();
                
                // 检查数组是否包含对象
                if (arrayContent.contains("{")) {
                    // 找第一个对象来分析结构
                    int objStart = arrayContent.indexOf('{');
                    int objEnd = findMatchingBrace(arrayContent, objStart, '{', '}');
                    String firstObj = arrayContent.substring(objStart, objEnd + 1);
                    Map<String, Object> arrayItemType = parseJson(firstObj);
                    // 使用特殊包装来标记这是一个List<嵌套类>
                    result.put(key, new ArrayType(key, arrayItemType));
                } else {
                    result.put(key, "List<String>");
                }
                pos = end + 1;
            } else if (c == '"') {
                // 字符串
                int end = json.indexOf('"', pos + 1);
                result.put(key, "String");
                pos = end + 1;
            } else {
                // 其他值
                int end = pos;
                while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
                String value = json.substring(pos, end).trim();
                
                if (value.equals("true") || value.equals("false")) {
                    result.put(key, "Boolean");
                } else if (value.equals("null")) {
                    result.put(key, "Object");
                } else if (value.contains(".")) {
                    result.put(key, "Double");
                } else {
                    try {
                        Long.parseLong(value);
                        result.put(key, "Long");
                    } catch (NumberFormatException e) {
                        result.put(key, "String");
                    }
                }
                pos = end;
            }
            
            // 跳过逗号
            while (pos < json.length() && (json.charAt(pos) == ',' || Character.isWhitespace(json.charAt(pos)))) pos++;
        }
        
        return result;
    }
    
    private int findMatchingBrace(String s, int start, char open, char close) {
        int count = 0;
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == open) count++;
            else if (s.charAt(i) == close) {
                count--;
                if (count == 0) return i;
            }
        }
        return s.length() - 1;
    }
    
    /**
     * 内部类用于表示数组类型
     */
    public static class ArrayType {
        public String elementName;
        public Map<String, Object> elementFields;
        
        public ArrayType(String name, Map<String, Object> fields) {
            this.elementName = name;
            this.elementFields = fields;
        }
    }

    /**
     * XML解析器 - 支持嵌套元素
     */
    private Map<String, Object> parseXml(String xml) {
        // 移除XML声明
        xml = xml.replaceAll("<\\?xml[^>]*\\?>", "").trim();
        
        // 找到根元素
        Pattern rootPattern = Pattern.compile("<([a-zA-Z_][a-zA-Z0-9_-]*)(?:\\s[^>]*)?>([\\s\\S]*)</\\1>");
        Matcher rootMatcher = rootPattern.matcher(xml);
        
        if (rootMatcher.find()) {
            String rootContent = rootMatcher.group(2);
            return parseXmlContent(rootContent);
        }
        
        return parseXmlContent(xml);
    }
    
    /**
     * 递归解析XML内容 - 支持重复元素（数组）
     */
    private Map<String, Object> parseXmlContent(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> tagCount = new LinkedHashMap<>();
        Map<String, Object> firstOccurrence = new LinkedHashMap<>();
        
        // 匹配所有直接子元素
        Pattern pattern = Pattern.compile("<([a-zA-Z_][a-zA-Z0-9_-]*)(?:\\s[^>]*)?>([\\s\\S]*?)</\\1>");
        Matcher matcher = pattern.matcher(content);
        
        // 先统计每个标签出现的次数
        while (matcher.find()) {
            String tagName = matcher.group(1);
            String innerContent = matcher.group(2).trim();
            
            tagCount.put(tagName, tagCount.getOrDefault(tagName, 0) + 1);
            
            // 保存第一次出现的内容用于类型推断
            if (!firstOccurrence.containsKey(tagName)) {
                if (innerContent.contains("<") && innerContent.contains(">")) {
                    firstOccurrence.put(tagName, parseXmlContent(innerContent));
                } else {
                    firstOccurrence.put(tagName, inferType(innerContent));
                }
            }
        }
        
        // 根据出现次数决定是单个值还是数组
        for (Map.Entry<String, Integer> entry : tagCount.entrySet()) {
            String tagName = entry.getKey();
            int count = entry.getValue();
            Object value = firstOccurrence.get(tagName);
            
            if (count > 1) {
                // 多次出现 = 数组
                if (value instanceof Map) {
                    // 嵌套对象数组
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fields = (Map<String, Object>) value;
                    result.put(tagName, new ArrayType(tagName, fields));
                } else {
                    // 简单类型数组
                    result.put(tagName, "List<" + value + ">");
                }
            } else {
                // 单次出现 = 普通字段
                result.put(tagName, value);
            }
        }
        
        return result;
    }
    
    private String inferType(String content) {
        if (content.isEmpty()) {
            return "String";
        } else if (content.equals("true") || content.equals("false")) {
            return "Boolean";
        } else if (content.matches("-?\\d+\\.\\d+")) {
            return "Double";
        } else if (content.matches("-?\\d+")) {
            return "Long";
        } else {
            return "String";
        }
    }

    /**
     * 生成Java类
     */
    private String generateClass(Map<String, Object> fields, String className, 
                                 NamingStyle classStyle, NamingStyle fieldStyle, 
                                 NamingStyle methodStyle, boolean useLombok, String packageName) {
        StringBuilder sb = new StringBuilder();
        String formattedClassName = convertCase(className, classStyle);
        
        // 检查是否需要List import
        boolean needsList = false;
        for (Object value : fields.values()) {
            if (value instanceof ArrayType || (value instanceof String && value.toString().startsWith("List"))) {
                needsList = true;
                break;
            }
        }
        
        // Package
        if (packageName != null && !packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }
        
        // Imports
        sb.append("import java.io.Serializable;\n");
        if (needsList) {
            sb.append("import java.util.List;\n");
        }
        if (useLombok) {
            sb.append("import lombok.Data;\n");
            sb.append("import lombok.NoArgsConstructor;\n");
            sb.append("import lombok.AllArgsConstructor;\n");
        }
        sb.append("\n");
        
        // Class annotation
        if (useLombok) {
            sb.append("@Data\n");
            sb.append("@NoArgsConstructor\n");
            sb.append("@AllArgsConstructor\n");
        }
        
        // Class declaration
        sb.append("public class ").append(formattedClassName).append(" implements Serializable {\n\n");
        sb.append("    private static final long serialVersionUID = 1L;\n\n");
        
        // Fields
        List<String[]> fieldList = new ArrayList<>();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String fieldName = convertCase(entry.getKey(), fieldStyle);
            String fieldType;
            
            if (entry.getValue() instanceof Map) {
                // 嵌套对象
                fieldType = convertCase(entry.getKey(), classStyle);
            } else if (entry.getValue() instanceof ArrayType) {
                // 数组包含对象
                ArrayType arrayType = (ArrayType) entry.getValue();
                String elementClassName = convertCase(arrayType.elementName + "Item", classStyle);
                fieldType = "List<" + elementClassName + ">";
            } else {
                fieldType = entry.getValue().toString();
            }
            
            sb.append("    private ").append(fieldType).append(" ").append(fieldName).append(";\n");
            fieldList.add(new String[]{fieldType, fieldName, entry.getKey()});
        }
        sb.append("\n");
        
        // Getters and Setters (if not using Lombok)
        if (!useLombok) {
            for (String[] field : fieldList) {
                String type = field[0];
                String name = field[1];
                String originalName = field[2];
                String methodSuffix = convertCase(originalName, methodStyle);
                // 确保getter/setter方法名首字母大写
                if (!methodSuffix.isEmpty()) {
                    methodSuffix = Character.toUpperCase(methodSuffix.charAt(0)) + methodSuffix.substring(1);
                }
                
                // Getter
                sb.append("    public ").append(type).append(" get").append(methodSuffix).append("() {\n");
                sb.append("        return this.").append(name).append(";\n");
                sb.append("    }\n\n");
                
                // Setter
                sb.append("    public void set").append(methodSuffix).append("(").append(type).append(" ").append(name).append(") {\n");
                sb.append("        this.").append(name).append(" = ").append(name).append(";\n");
                sb.append("    }\n\n");
            }
        }
        
        sb.append("}\n");
        
        // 生成嵌套类 - Map类型
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getValue() instanceof Map) {
                sb.append("\n// ========== 嵌套类: ").append(convertCase(entry.getKey(), classStyle)).append(" ==========\n\n");
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedFields = (Map<String, Object>) entry.getValue();
                sb.append(generateClass(nestedFields, entry.getKey(), classStyle, fieldStyle, methodStyle, useLombok, packageName));
            }
        }
        
        // 生成嵌套类 - ArrayType类型
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getValue() instanceof ArrayType) {
                ArrayType arrayType = (ArrayType) entry.getValue();
                String nestedClassName = arrayType.elementName + "Item";
                sb.append("\n// ========== 数组元素类: ").append(convertCase(nestedClassName, classStyle)).append(" ==========\n\n");
                sb.append(generateClass(arrayType.elementFields, nestedClassName, classStyle, fieldStyle, methodStyle, useLombok, packageName));
            }
        }
        
        return sb.toString();
    }

    /**
     * 转换命名风格
     */
    public String convertCase(String input, NamingStyle style) {
        if (input == null || input.isEmpty()) return input;
        
        // 先将输入拆分成单词
        List<String> words = splitIntoWords(input);
        
        switch (style) {
            case CAMEL_CASE:
                return toCamelCase(words);
            case PASCAL_CASE:
                return toPascalCase(words);
            case SNAKE_CASE:
                return toSnakeCase(words, false);
            case UPPER_SNAKE_CASE:
                return toSnakeCase(words, true);
            default:
                return input;
        }
    }

    private List<String> splitIntoWords(String input) {
        List<String> words = new ArrayList<>();
        
        // 处理snake_case
        if (input.contains("_")) {
            for (String part : input.split("_")) {
                if (!part.isEmpty()) {
                    words.add(part.toLowerCase());
                }
            }
            return words;
        }
        
        // 如果全是大写字母，作为单个单词处理
        if (input.equals(input.toUpperCase()) && input.matches("[A-Z]+")) {
            words.add(input.toLowerCase());
            return words;
        }
        
        // 处理camelCase和PascalCase
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // 检测单词边界：当前为大写且前一个为小写，或当前为大写且下一个为小写
            boolean isWordBoundary = Character.isUpperCase(c) && current.length() > 0;
            
            if (isWordBoundary) {
                words.add(current.toString().toLowerCase());
                current = new StringBuilder();
            }
            current.append(c);
        }
        if (current.length() > 0) {
            words.add(current.toString().toLowerCase());
        }
        
        return words;
    }

    private String toCamelCase(List<String> words) {
        if (words.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(words.get(0).toLowerCase());
        for (int i = 1; i < words.size(); i++) {
            String word = words.get(i);
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1));
        }
        return sb.toString();
    }

    private String toPascalCase(List<String> words) {
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1));
        }
        return sb.toString();
    }

    private String toSnakeCase(List<String> words, boolean upper) {
        String result = String.join("_", words);
        return upper ? result.toUpperCase() : result.toLowerCase();
    }

    /**
     * 从SQL CREATE TABLE生成Java类
     */
    public String generateFromSql(String sql, String className, NamingStyle classStyle,
                                  NamingStyle fieldStyle, NamingStyle methodStyle,
                                  boolean useLombok, String annotationType, String packageName, String dbType) {
        try {
            SqlTableDef tableDef = parseSql(sql, dbType);
            return generateJavaFromSql(tableDef, className, classStyle, fieldStyle, methodStyle, useLombok, annotationType, packageName);
        } catch (Exception e) {
            logger.error("SQL解析失败", e);
            throw new RuntimeException("SQL解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析SQL CREATE TABLE语句
     */
    private SqlTableDef parseSql(String sql, String dbType) {
        sql = sql.trim();
        
        // 移除注释
        sql = sql.replaceAll("--.*", "").replaceAll("/\\*[\\s\\S]*?\\*/", "");
        
        // 提取表名
        Pattern tablePattern = Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`\"]?(\\w+)[`\"]?", Pattern.CASE_INSENSITIVE);
        Matcher tableMatcher = tablePattern.matcher(sql);
        String tableName = tableMatcher.find() ? tableMatcher.group(1) : "GeneratedEntity";
        
        // 提取字段定义部分
        int start = sql.indexOf('(');
        int end = sql.lastIndexOf(')');
        if (start < 0 || end < 0) {
            throw new RuntimeException("无效的SQL语法：找不到字段定义");
        }
        
        String fieldsBlock = sql.substring(start + 1, end);
        List<SqlFieldDef> fields = new ArrayList<>();
        
        // 分割字段（需要处理嵌套括号）
        String[] lines = fieldsBlock.split(",(?![^()]*\\))");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.toUpperCase().startsWith("PRIMARY KEY") 
                || line.toUpperCase().startsWith("FOREIGN KEY")
                || line.toUpperCase().startsWith("UNIQUE")
                || line.toUpperCase().startsWith("INDEX")
                || line.toUpperCase().startsWith("KEY")
                || line.toUpperCase().startsWith("CONSTRAINT")) {
                continue;
            }
            
            SqlFieldDef field = parseField(line, dbType);
            if (field != null) {
                fields.add(field);
            }
        }
        
        SqlTableDef tableDef = new SqlTableDef();
        tableDef.tableName = tableName;
        tableDef.fields = fields;
        return tableDef;
    }

    private SqlFieldDef parseField(String fieldDef, String dbType) {
        // 移除反引号和双引号
        fieldDef = fieldDef.replaceAll("[`\"]", "");
        
        String[] parts = fieldDef.split("\\s+", 2);
        if (parts.length < 2) return null;
        
        String columnName = parts[0];
        String rest = parts[1];
        
        SqlFieldDef field = new SqlFieldDef(columnName, "");
        
        // 提取数据类型
        Pattern typePattern = Pattern.compile("^(\\w+)(?:\\((\\d+)(?:,\\s*(\\d+))?\\))?", Pattern.CASE_INSENSITIVE);
        Matcher typeMatcher = typePattern.matcher(rest);
        
        if (typeMatcher.find()) {
            field.dataType = typeMatcher.group(1).toUpperCase();
            if (typeMatcher.group(2) != null) {
                field.length = Integer.parseInt(typeMatcher.group(2));
                if (typeMatcher.group(3) != null) {
                    field.precision = field.length;
                    field.scale = Integer.parseInt(typeMatcher.group(3));
                }
            }
        }
        
        // 检查约束
        String upperRest = rest.toUpperCase();
        field.notNull = upperRest.contains("NOT NULL");
        field.primaryKey = upperRest.contains("PRIMARY KEY");
        field.autoIncrement = upperRest.contains("AUTO_INCREMENT") || upperRest.contains("AUTOINCREMENT") 
                             || upperRest.contains("IDENTITY") || upperRest.contains("SERIAL");
        
        return field;
    }

    /**
     * 从SQL字段生成Java类
     */
    private String generateJavaFromSql(SqlTableDef tableDef, String className, 
                                       NamingStyle classStyle, NamingStyle fieldStyle,
                                       NamingStyle methodStyle, boolean useLombok, 
                                       String annotationType, String packageName) {
        StringBuilder sb = new StringBuilder();
        String formattedClassName = className != null && !className.isEmpty() ? 
                                   convertCase(className, classStyle) : 
                                   convertCase(tableDef.tableName, classStyle);
        
        boolean useJpa = "JPA".equals(annotationType);
        boolean useMybatisPlus = "MyBatis-Plus".equals(annotationType);
        
        // Package
        if (packageName != null && !packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }
        
        // Imports
        sb.append("import java.io.Serializable;\n");
        boolean needsBigDecimal = false;
        boolean needsLocalDateTime = false;
        boolean needsLocalDate = false;
        boolean needsLocalTime = false;
        
        for (SqlFieldDef field : tableDef.fields) {
            String javaType = mapSqlTypeToJava(field.dataType);
            if ("BigDecimal".equals(javaType)) needsBigDecimal = true;
            if ("LocalDateTime".equals(javaType)) needsLocalDateTime = true;
            if ("LocalDate".equals(javaType)) needsLocalDate = true;
            if ("LocalTime".equals(javaType)) needsLocalTime = true;
        }
        
        if (needsBigDecimal) sb.append("import java.math.BigDecimal;\n");
        if (needsLocalDateTime) sb.append("import java.time.LocalDateTime;\n");
        if (needsLocalDate) sb.append("import java.time.LocalDate;\n");
        if (needsLocalTime) sb.append("import java.time.LocalTime;\n");
        
        if (useLombok) {
            sb.append("import lombok.Data;\n");
            sb.append("import lombok.NoArgsConstructor;\n");
            sb.append("import lombok.AllArgsConstructor;\n");
        }
        
        if (useJpa) {
            sb.append("import javax.persistence.*;\n");
        }
        if (useMybatisPlus) {
            sb.append("import com.baomidou.mybatisplus.annotation.*;\n");
        }
        
        sb.append("\n");
        
        // Class annotations
        if (useLombok) {
            sb.append("@Data\n@NoArgsConstructor\n@AllArgsConstructor\n");
        }
        if (useJpa) {
            sb.append("@Entity\n");
            sb.append("@Table(name = \"").append(tableDef.tableName).append("\")\n");
        }
        if (useMybatisPlus) {
            sb.append("@TableName(\"").append(tableDef.tableName).append("\")\n");
        }
        
        // Class declaration
        sb.append("public class ").append(formattedClassName).append(" implements Serializable {\n\n");
        sb.append("    private static final long serialVersionUID = 1L;\n\n");
        
        // Fields
        List<String[]> fieldList = new ArrayList<>();
        for (SqlFieldDef field : tableDef.fields) {
            String fieldName = convertCase(field.columnName, fieldStyle);
            String javaType = mapSqlTypeToJava(field.dataType);
            
            if (useJpa) {
                if (field.primaryKey) {
                    sb.append("    @Id\n");
                    if (field.autoIncrement) {
                        sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
                    }
                }
                
                sb.append("    @Column(name = \"").append(field.columnName).append("\"");
                if (field.notNull && !field.primaryKey) sb.append(", nullable = false");
                if (field.length != null && field.precision == null) {
                    sb.append(", length = ").append(field.length);
                }
                if (field.precision != null && field.scale != null) {
                    sb.append(", precision = ").append(field.precision);
                    sb.append(", scale = ").append(field.scale);
                }
                sb.append(")\n");
            }
            
            if (useMybatisPlus) {
                if (field.primaryKey) {
                    sb.append("    @TableId(value = \"").append(field.columnName).append("\"");
                    if (field.autoIncrement) {
                        sb.append(", type = IdType.AUTO");
                    }
                    sb.append(")\n");
                } else {
                    sb.append("    @TableField(\"").append(field.columnName).append("\")\n");
                }
            }
            
            sb.append("    private ").append(javaType).append(" ").append(fieldName).append(";\n");
            if (useJpa || useMybatisPlus) sb.append("\n");
            
            fieldList.add(new String[]{javaType, fieldName, field.columnName});
        }
        
        // Getters/Setters (if not Lombok)
        if (!useLombok) {
            sb.append("\n");
            for (String[] field : fieldList) {
                String type = field[0];
                String name = field[1];
                String original = field[2];
                String methodSuffix = convertCase(original, methodStyle);
                if (!methodSuffix.isEmpty()) {
                    methodSuffix = Character.toUpperCase(methodSuffix.charAt(0)) + methodSuffix.substring(1);
                }
                
                sb.append("    public ").append(type).append(" get").append(methodSuffix).append("() {\n");
                sb.append("        return this.").append(name).append(";\n    }\n\n");
                
                sb.append("    public void set").append(methodSuffix).append("(").append(type).append(" ").append(name).append(") {\n");
                sb.append("        this.").append(name).append(" = ").append(name).append(";\n    }\n\n");
            }
        }
        
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * SQL类型到Java类型映射
     */
    private String mapSqlTypeToJava(String sqlType) {
        sqlType = sqlType.toUpperCase();
        
        // 数值类型
        if (sqlType.contains("BIGINT") || sqlType.equals("BIGSERIAL") || sqlType.equals("NUMBER") && sqlType.contains("19")) {
            return "Long";
        }
        if (sqlType.contains("INT") || sqlType.equals("SERIAL")) {
            return "Integer";
        }
        if (sqlType.contains("DECIMAL") || sqlType.contains("NUMERIC") || sqlType.equals("NUMBER")) {
            return "BigDecimal";
        }
        if (sqlType.contains("FLOAT") || sqlType.contains("DOUBLE") || sqlType.contains("REAL")) {
            return "Double";
        }
        if (sqlType.contains("BIT") && !sqlType.contains("BIT(1)")) {
            return "byte[]";
        }
        
        // 布尔类型
        if (sqlType.contains("BOOL")) {
            return "Boolean";
        }
        
        // 日期时间类型
        if (sqlType.contains("TIMESTAMP") || sqlType.contains("DATETIME")) {
            return "LocalDateTime";
        }
        if (sqlType.equals("DATE")) {
            return "LocalDate";
        }
        if (sqlType.equals("TIME")) {
            return "LocalTime";
        }
        
        // 字符串类型
        if (sqlType.contains("CHAR") || sqlType.contains("TEXT") || sqlType.contains("CLOB") 
            || sqlType.contains("VARCHAR2") || sqlType.contains("NVARCHAR")) {
            return "String";
        }
        
        // 二进制类型
        if (sqlType.contains("BLOB") || sqlType.contains("BINARY") || sqlType.contains("BYTEA")) {
            return "byte[]";
        }
        
        // JSON类型 (作为String处理)
        if (sqlType.contains("JSON")) {
            return "String";  // 添加注释建议使用JSON库
        }
        
        // 默认
        return "String";
    }

    /**
     * SQL表定义
     */
    public static class SqlTableDef {
        public String tableName;
        public List<SqlFieldDef> fields;
    }
}
