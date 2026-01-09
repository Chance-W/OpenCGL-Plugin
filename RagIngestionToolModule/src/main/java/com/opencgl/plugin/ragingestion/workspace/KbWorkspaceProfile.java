package com.opencgl.plugin.ragingestion.workspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库工作空间配置 Profile 实体。
 * 保存用户选择的数据源路径、切分策略、向量模型与目标数据库连接信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KbWorkspaceProfile {
    private String profileId;
    private String profileName;
    private String sourceFilePath;
    private boolean foldBlankLines;
    private boolean trimSpaces;
    private String excludeRegex;
    private String strategyName;
    private String customDelimiter;
    private int chunkSize;
    private int overlapSize;
    private boolean apiMode;
    private String modelName;
    private int dimension;
    private String apiUrl;
    private String storageType; // SQLITE, FAISS_LOCAL, MILVUS, PGVECTOR, ELASTICSEARCH
    private String targetTableName;
    private String connectionStringOrPath; // 本地路径或远程 IP:端口
    private boolean incrementalCheck;

    @Override
    public String toString() {
        return profileName + " [" + storageType + "]";
    }
}
