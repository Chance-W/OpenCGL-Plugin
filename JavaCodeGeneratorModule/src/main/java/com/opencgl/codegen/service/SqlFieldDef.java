package com.opencgl.codegen.service;

/**
 * SQL字段定义
 */
public class SqlFieldDef {
    public String columnName;
    public String dataType;
    public Integer length;
    public Integer precision;
    public Integer scale;
    public boolean notNull;
    public boolean primaryKey;
    public boolean autoIncrement;
    public String defaultValue;
    
    public SqlFieldDef(String columnName, String dataType) {
        this.columnName = columnName;
        this.dataType = dataType;
    }
}
