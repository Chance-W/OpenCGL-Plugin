package com.opencgl.dbbatch.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.opencgl.dbbatch.model.DbTaskDto;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库执行服务
 */
public class DbExecutorService {
    private volatile Connection currentConnection;
    private volatile Statement currentStatement;

    /**
     * 生成可执行的 SQL 列表 (支持批量生成)
     */
    public List<String> generateSqls(DbTaskDto task) {
        if (task.getSqlContent() == null) return Collections.emptyList();
        
        List<String> results = new ArrayList<>();
        String replaceConfig = task.getReplaceConfig();
        
        // 如果没有配置或配置为空，直接返回原 SQL (如果是多条语句怎么处理？暂把 whole content 当作一次执行块)
        if (replaceConfig == null || replaceConfig.trim().isEmpty()) {
            results.add(task.getSqlContent());
            return results;
        }

        try {
            Map<String, Object> replacements = JSON.parseObject(replaceConfig, Map.class);
            if (replacements == null) {
                results.add(task.getSqlContent());
                return results;
            }

            // 1. 识别是否有批量生成的变量
            Map<String, List<String>> batchVars = new HashMap<>(); // key -> list of values
            Map<String, String> staticVars = new HashMap<>();     // key -> single value
            
            int maxBatchSize = 1;

            for (Map.Entry<String, Object> entry : replacements.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();

                if (val instanceof JSONObject || val instanceof Map) {
                    // 转为 Map 方便处理
                    Map<String, Object> conf = (Map<String, Object>) val;
                    if (conf.containsKey("mode") && conf.get("mode").toString().startsWith("BATCH_")) {
                        // 是批量配置
                        List<String> generatedValues = generateBatchValues(conf);
                        batchVars.put(key, generatedValues);
                        maxBatchSize = Math.max(maxBatchSize, generatedValues.size());
                    } else {
                        // 普通对象? 目前暂不支持复杂对象替换，当作 toString
                         staticVars.put(key, val.toString());
                    }
                } else {
                    // 普通值
                    staticVars.put(key, val.toString());
                }
            }

            // 2. 生成 SQL 列表
            // 策略：如果有多个批量变量，取最大长度，不足的循环引用 (Lock-step) 还是 笛卡尔积？
            // 用户需求通常是 "按月递增"，若是多个变量（如 month, id），通常也是对应的。
            // 采用 Lock-step (对齐索引)
            
            if (batchVars.isEmpty()) {
                // 只有静态变量
                results.add(applyReplacement(task.getSqlContent(), staticVars));
            } else {
                for (int i = 0; i < maxBatchSize; i++) {
                    Map<String, String> currentReplacements = new HashMap<>(staticVars);
                    // 填充当次循环的批量变量
                    for (Map.Entry<String, List<String>> batchEntry : batchVars.entrySet()) {
                        List<String> vals = batchEntry.getValue();
                        // 防止越界，取模循环或者取最后一个? 
                        // 通常应该长度一致。如果不一致，取模比较合理，或者取最后一个
                        String val = vals.isEmpty() ? "" : vals.get(i % vals.size());
                        currentReplacements.put(batchEntry.getKey(), val);
                    }
                    results.add(applyReplacement(task.getSqlContent(), currentReplacements));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            // 出错 fallback
            results.add(task.getSqlContent());
        }
        
        return results;
    }

    private List<String> generateBatchValues(Map<String, Object> conf) {
        List<String> values = new ArrayList<>();
        String mode = String.valueOf(conf.get("mode"));
        int count = conf.containsKey("count") ? Integer.parseInt(String.valueOf(conf.get("count"))) : 1;
        
        if ("BATCH_DATE".equals(mode)) {
            String startStr = resolveDynamicVariables(String.valueOf(conf.get("start")));
            String format = (String) conf.getOrDefault("format", "yyyyMM");
            String unit = (String) conf.getOrDefault("unit", "Months"); // Months, Days, Years, Weeks
            
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(format);
            LocalDateTime baseTime;
            try {
               if (format.length() <= 8) { 
                     if (format.contains("dd")) {
                         baseTime = LocalDate.parse(startStr, dtf).atStartOfDay();
                     } else if (format.contains("MM")) {
                         if (format.equals("yyyyMM")) {
                             int year = Integer.parseInt(startStr.substring(0, 4));
                             int month = Integer.parseInt(startStr.substring(4, 6));
                             baseTime = LocalDateTime.of(year, month, 1, 0, 0);
                         } else {
                             // Try best effort standard parsing, else fallback current
                             try { baseTime = LocalDateTime.parse(startStr); } 
                             catch(Exception e) { baseTime = LocalDateTime.now(); }
                         }
                     } else if (format.contains("yyyy")) {
                         // Year only?
                         int year = Integer.parseInt(startStr);
                         baseTime = LocalDateTime.of(year, 1, 1, 0, 0);
                     } else {
                         baseTime = LocalDateTime.now();
                     }
                } else {
                    baseTime = LocalDateTime.parse(startStr, dtf);
                }
            } catch (Exception e) {
                // Specialized parser for yyyy (Year loop)
                if ("yyyy".equals(format) && startStr.length() == 4) {
                     int y = Integer.parseInt(startStr);
                     baseTime = LocalDateTime.of(y, 1, 1, 0, 0);
                } else if ("yyyyMM".equals(format) && startStr.length() == 6) {
                     int y = Integer.parseInt(startStr.substring(0, 4));
                     int m = Integer.parseInt(startStr.substring(4, 6));
                     baseTime = LocalDateTime.of(y, m, 1, 0, 0);
                } else {
                    baseTime = LocalDateTime.now();
                }
            }
            
            for (int i = 0; i < count; i++) {
                LocalDateTime t = baseTime;
                if ("Months".equalsIgnoreCase(unit)) t = t.plusMonths(i);
                else if ("Days".equalsIgnoreCase(unit)) t = t.plusDays(i);
                else if ("Years".equalsIgnoreCase(unit)) t = t.plusYears(i);
                else if ("Weeks".equalsIgnoreCase(unit)) t = t.plusWeeks(i);
                else if ("Hours".equalsIgnoreCase(unit)) t = t.plusHours(i);
                
                values.add(t.format(dtf));
            }
            
        } else if ("BATCH_INT".equals(mode)) {
            int start = Integer.parseInt(String.valueOf(conf.get("start")));
            int step = Integer.parseInt(String.valueOf(conf.getOrDefault("step", "1")));
            for (int i = 0; i < count; i++) {
                values.add(String.valueOf(start + (i * step)));
            }
        } else if ("BATCH_ENUM".equals(mode)) {
            // Enumeration mode: cycle through a list of values
            Object valsObj = conf.get("values");
            if (valsObj instanceof List) {
                List<?> list = (List<?>) valsObj;
                for (int i = 0; i < count; i++) {
                    // Cyclic get
                    if (list.isEmpty()) values.add("");
                    else values.add(String.valueOf(list.get(i % list.size())));
                }
            }
        } else if ("BATCH_RANDOM_INT".equals(mode)) {
            // Random Integer within range
            int min = Integer.parseInt(String.valueOf(conf.getOrDefault("min", "0")));
            int max = Integer.parseInt(String.valueOf(conf.getOrDefault("max", "100")));
            Random rand = new Random();
            for (int i = 0; i < count; i++) {
                 values.add(String.valueOf(rand.nextInt((max - min) + 1) + min));
            }
        }
        
        return values;
    }

    private String applyReplacement(String sql, Map<String, String> vars) {
        String processed = sql;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            String val = entry.getValue();
            // 先解析 value 中的动态变量 (如果是静态引用的)
            val = resolveDynamicVariables(val);
            processed = processed.replace("${" + entry.getKey() + "}", val);
        }
        return processed;
    }

    /**
     * 执行任务 (修改为支持多条 SQL)
     */
    public void executeTask(DbTaskDto task, Consumer<String> logConsumer) throws Exception {
        if (task.isGroup()) return;
        
        List<String> sqls = generateSqls(task);
        if (sqls.isEmpty()) throw new IllegalArgumentException("SQL 为空");
        
        Connection conn = getConnection(task);
        currentConnection = conn;
        try (conn) {
             Statement stmt = conn.createStatement();
             currentStatement = stmt;
             try (stmt) {
                 for (String sql : sqls) {
                     if (sql.trim().isEmpty()) continue;
                     if (logConsumer != null) logConsumer.accept("Executing: " + (sql.length() > 50 ? sql.substring(0,50)+"..." : sql));
                     stmt.execute(sql);
                 }
             } finally {
                 currentStatement = null;
             }
        } finally {
            currentConnection = null;
        }
    }

    public void close() {
        Statement statement = currentStatement;
        currentStatement = null;
        if (statement != null) {
            try {
                statement.cancel();
            } catch (Exception ignored) {
                // Continue with closing the statement and connection.
            }
            try {
                statement.close();
            } catch (Exception ignored) {
                // Continue with closing the connection.
            }
        }
        Connection connection = currentConnection;
        currentConnection = null;
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
                // Disposal is best-effort.
            }
        }
    }

    
    private Connection getConnection(DbTaskDto task) throws Exception {
        String url;
        String driver;
        
        if ("MYSQL".equalsIgnoreCase(task.getDbType())) {
            driver = "com.mysql.cj.jdbc.Driver";
            try { Class.forName(driver); } catch (ClassNotFoundException e) { driver = "com.mysql.jdbc.Driver"; }
            url = String.format("jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=UTC", 
                task.getHost(), task.getPort(), task.getDatabase());
        } else if ("ORACLE".equalsIgnoreCase(task.getDbType())) {
            driver = "oracle.jdbc.driver.OracleDriver";
            url = String.format("jdbc:oracle:thin:@%s:%s:%s", task.getHost(), task.getPort(), task.getDatabase());
        } else if ("POSTGRESQL".equalsIgnoreCase(task.getDbType())) {
            driver = "org.postgresql.Driver";
            url = String.format("jdbc:postgresql://%s:%s/%s", task.getHost(), task.getPort(), task.getDatabase());
        } else if ("SQLSERVER".equalsIgnoreCase(task.getDbType())) {
            driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            url = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=false", task.getHost(), task.getPort(), task.getDatabase());
        } else if ("SQLITE".equalsIgnoreCase(task.getDbType())) {
            driver = "org.sqlite.JDBC";
            url = String.format("jdbc:sqlite:%s", task.getHost());
        } else {
            throw new UnsupportedOperationException("暂不支持的数据库类型: " + task.getDbType());
        }
        
        Class.forName(driver);
        return DriverManager.getConnection(url, task.getUsername(), task.getPassword());
    }
    

    
    /**
     * 解析字符串中的动态系统变量，格式 ${Var}
     * 支持:
     * 1. ${UUID} -> 随机UUID
     * 2. ${TIMESTAMP} -> 当前毫秒时间戳
     * 3. ${yyyy-MM-dd} 等 -> 当前时间格式化
     */
    private String resolveDynamicVariables(String input) {
        if (input == null || !input.contains("${")) return input;
        
        Matcher m = Pattern.compile("\\$\\{([^}]+)\\}").matcher(input);
        StringBuffer sb = new StringBuffer();
        try {
            while (m.find()) {
                String varName = m.group(1);
                String systemVal = getSystemVariableValue(varName);
                if (systemVal != null) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(systemVal));
                } else {
                    // 无法识别的变量，保留原样? 或者置空? 
                    // 保留原样以便后续可能处理或作为普通字符串
                    // m.appendReplacement(sb, "\\${" + varName + "}"); 
                    // 这里为了简单，如果没有匹配到系统变量，就不替换 (但是 Java Matcher 机制一旦 find 必须 appendReplacement 如果要推进)
                    // 所以如果找不到，我们用原文替换回去
                    m.appendReplacement(sb, Matcher.quoteReplacement("${" + varName + "}"));
                }
            }
            m.appendTail(sb);
        } catch (Exception e) {
            return input; // 出错则返回原值
        }
        return sb.toString();
    }
    
    private String getSystemVariableValue(String var) {
        if ("UUID".equalsIgnoreCase(var)) return UUID.randomUUID().toString();
        if ("TIMESTAMP".equalsIgnoreCase(var)) return String.valueOf(System.currentTimeMillis());
        
        try {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern(var));
        } catch (Exception e) {
            // Not a valid date pattern
            return null;
        }
    }
}
