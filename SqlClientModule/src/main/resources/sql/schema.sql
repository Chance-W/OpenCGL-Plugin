-- SQL客户端树节点表
CREATE TABLE IF NOT EXISTS sql_tree_item (
    -- 基础字段（BaseDataDto）
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT,
    name VARCHAR(200) NOT NULL,
    is_leaf BOOLEAN DEFAULT FALSE,
    sort_order INT DEFAULT 0,
    
    -- 节点类型
    node_type VARCHAR(50) NOT NULL,  -- CONNECTION_GROUP, CONNECTION, TABLE等
    icon_name VARCHAR(50),
    
    -- 连接配置（仅CONNECTION类型使用）
    host VARCHAR(200),
    port INT,
    database_name VARCHAR(100),
    username VARCHAR(100),
    password VARCHAR(500),  -- AES加密
    db_type VARCHAR(20),    -- MySQL/PostgreSQL/Oracle/SQLite
    
    -- 数据库对象信息（TABLE/VIEW/FUNCTION等类型使用）
    schema_name VARCHAR(100),
    table_name VARCHAR(100),
    object_type VARCHAR(50),
    object_ddl TEXT,
    
    -- 审计字段
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_parent(parent_id),
    INDEX idx_type(node_type),
    INDEX idx_sort(sort_order)
);
