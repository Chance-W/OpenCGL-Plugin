-- HTTP调试器树节点表
CREATE TABLE IF NOT EXISTS http_tree_item (
    -- 基础字段（BaseDataDto）
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    parent_id INTEGER,
    name VARCHAR(200) NOT NULL,
    is_leaf BOOLEAN DEFAULT 0,
    sort_order INTEGER DEFAULT 0,
    
    -- 节点类型
    node_type VARCHAR(50) NOT NULL,  -- FOLDER, REQUEST
    icon_name VARCHAR(50),
    
    description TEXT,
    
    -- 请求配置（仅REQUEST类型使用）
    method VARCHAR(20),           -- GET, POST, PUT, DELETE等
    url TEXT,
    headers TEXT,                 -- JSON格式
    params TEXT,                  -- JSON格式（query params）
    body TEXT,
    body_type VARCHAR(20),        -- JSON/FORM/RAW/XML
    auth_config TEXT,             -- 认证配置 (JSON)
    hook_script VARCHAR(200),     -- 关联的Hook脚本文件名
    timeout INTEGER DEFAULT 30000,
    
    -- 审计字段
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_http_parent ON http_tree_item(parent_id);
CREATE INDEX IF NOT EXISTS idx_http_type ON http_tree_item(node_type);
CREATE INDEX IF NOT EXISTS idx_http_sort ON http_tree_item(sort_order);
