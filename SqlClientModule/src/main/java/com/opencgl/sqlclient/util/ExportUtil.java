package com.opencgl.sqlclient.util;

import com.opencgl.sqlclient.service.DatabaseService;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class ExportUtil {
    
    public static void exportToCSV(DatabaseService.QueryResult result, String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            // 写入列名
            writer.write(String.join(",", result.columns));
            writer.write("\n");
            
            // 写入数据
            for (List<Object> row : result.rows) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) line.append(",");
                    Object value = row.get(i);
                    String strValue = value == null ? "" : escapeCSV(value.toString());
                    line.append(strValue);
                }
                writer.write(line.toString());
                writer.write("\n");
            }
        }
    }
    
    public static void exportToJSON(DatabaseService.QueryResult result, String filePath) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("[\n");
        
        for (int i = 0; i < result.rows.size(); i++) {
            List<Object> row = result.rows.get(i);
            json.append("  {\n");
            
            for (int j = 0; j < result.columns.size(); j++) {
                json.append("    \"").append(result.columns.get(j)).append("\": ");
                Object value = row.get(j);
                if (value == null) {
                    json.append("null");
                } else if (value instanceof Number) {
                    json.append(value);
                } else {
                    json.append("\"").append(escapeJSON(value.toString())).append("\"");
                }
                if (j < result.columns.size() - 1) json.append(",");
                json.append("\n");
            }
            
            json.append("  }");
            if (i < result.rows.size() - 1) json.append(",");
            json.append("\n");
        }
        
        json.append("]");
        
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(json.toString());
        }
    }
    
    private static String escapeCSV(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    private static String escapeJSON(String value) {
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
