package com.opencgl.plugin.ragingestion.engine;

import java.util.List;

/**
 * 向量化引擎接口，支持远程 API (模式 A) 和本地离线计算 (模式 B)。
 */
public interface EmbeddingEngine {

    /**
     * 批量为文本列表计算生成稠密向量
     *
     * @param texts 文本列表
     * @return 对应的 float[] 向量列表
     */
    List<float[]> embedBatch(List<String> texts) throws Exception;

    /**
     * 获取输出向量维度
     */
    int getDimension();

    /**
     * 获取当前引擎名称
     */
    String getEngineName();
}
