package com.opencgl.sqlclient.model;

/**
 * SQL树节点类型枚举
 * 提取为顶级类以解决FastJSON反序列化时的NoClassDefFoundError
 */
public enum NodeType {
    CONNECTION_GROUP,   // 连接组（文件夹）
    CONNECTION,         // 数据库连接
    SCHEMA,            // Schema（PostgreSQL/Oracle）
    TABLE_GROUP,       // 表分组
    TABLE,             // 表
    VIEW_GROUP,        // 视图分组
    VIEW,              // 视图
    FUNCTION_GROUP,    // 函数分组
    FUNCTION,          // 函数
    PROCEDURE_GROUP,   // 存储过程分组
    PROCEDURE          // 存储过程
}
