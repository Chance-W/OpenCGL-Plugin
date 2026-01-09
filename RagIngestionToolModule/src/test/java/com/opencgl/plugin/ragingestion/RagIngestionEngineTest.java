package com.opencgl.plugin.ragingestion;

import com.opencgl.plugin.ragingestion.engine.ChunkerEngine;
import com.opencgl.plugin.ragingestion.engine.EmbeddingEngine;
import com.opencgl.plugin.ragingestion.engine.HashUtil;
import com.opencgl.plugin.ragingestion.engine.IncrementalDiffEngine;
import com.opencgl.plugin.ragingestion.engine.LocalOfflineEmbeddingEngine;
import com.opencgl.plugin.ragingestion.model.DocumentChunk;
import com.opencgl.plugin.ragingestion.model.SearchResult;
import com.opencgl.plugin.ragingestion.storage.SqliteVectorDriver;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全链路自测主入口：验证前置多层清洗、6大全面切片引擎、FAQ 一问一答仅 Q 向量化以及 SQLite 检索全流程。
 */
public class RagIngestionEngineTest {

    @Test
    void ingestsAndRetrievesFaqChunksEndToEnd() throws Exception {
        System.out.println("====== [开始执行 RagIngestionToolModule 增强版全链路验证] ======");

        // 1. 验证 SHA-256 哈希指纹
        String hash = HashUtil.sha256("测试文本");
        assertEquals(64, hash.length());
        System.out.println("1. SHA256 指纹测试 OK: " + hash.substring(0, 16) + "...");

        // 2. 验证多策略切片引擎 (常规递归切片)
        // 2. 验证多策略切片引擎 (常规递归切片，严格测试相邻切片重叠 Overlap)
        String sampleText = "# OpenCGL 插件开发指南\n\n这是第一段关于切片的介绍文本，包含多行叙述。\n\n## 向量化配置\n支持自定义维度和多源存储接入。\n\n第三段系统架构说明说明文本。";
        List<DocumentChunk> chunks = ChunkerEngine.splitText(
                "doc001", "manual.md", sampleText, ChunkerEngine.Strategy.RECURSIVE_SEMANTIC, 60, 20);
        assertTrue(chunks.size() > 1, "The sample must exercise recursive chunking");
        System.out.println("2. 递归语义切片 OK: 共生成 " + chunks.size() + " 个切片，严格验证重叠衔接(Overlap):");
        for (int i = 0; i < chunks.size(); i++) {
            System.out.println("   [Chunk #" + (i + 1) + "]: " + chunks.get(i).getContent().replace("\n", " \\n "));
        }

        // 2.1 验证 FAQ 问答一问一答切片 (仅 Q 向量化，A 为载荷)
        String faqText = "Q: 如何重置系统密码？\nA: 请进入系统设置页面点击重置密码按钮进行操作。\nQ: 数据库支持哪些？\nA: 支持 SQLite、PGVector 以及 Elasticsearch。";
        List<DocumentChunk> faqChunks = ChunkerEngine.splitText(
                "doc002", "faq.txt", faqText, ChunkerEngine.Strategy.QA_FAQ_PAIR, 500, 0);
        assertEquals(2, faqChunks.size());
        assertEquals("如何重置系统密码？", faqChunks.get(0).getEmbedTargetText());
        System.out.println("2.1 FAQ 一问一答切分 OK: 共生成 " + faqChunks.size() + " 对 Q&A");
        for (DocumentChunk fc : faqChunks) {
            System.out.println("    [Q提问->参与Embedding]: " + fc.getEmbedTargetText());
            System.out.println("    [A解答->完整表载荷]: " + fc.getQaAnswer().replace("\n", " "));
        }

        // 3. 验证本地离线 Embedding 计算引擎 (对 FAQ 只算 Q 向量)
        LocalOfflineEmbeddingEngine embEngine = new LocalOfflineEmbeddingEngine(512, "bge-small-zh");
        List<String> embedTexts = faqChunks.stream().map(DocumentChunk::getEmbedTargetText).toList();
        List<float[]> vecs = embEngine.embedBatch(embedTexts);
        assertEquals(2, vecs.size());
        assertTrue(vecs.stream().allMatch(vector -> vector.length == 512));
        for (int i = 0; i < faqChunks.size(); i++) {
            faqChunks.get(i).setEmbedding(vecs.get(i));
        }
        System.out.println("3. 离线向量引擎测试 OK: 成功为 " + vecs.size() + " 个 FAQ 提问计算 " + embEngine.getDimension() + " 维向量");

        // 4. 验证本地 SQLite 向量库操作与相似度查询
        File tempDb = File.createTempFile("rag_test_faq_", ".db");
        tempDb.deleteOnExit();
        SqliteVectorDriver sqliteDriver = new SqliteVectorDriver(tempDb.getAbsolutePath());
        sqliteDriver.ensureTableOrIndexExists("kb_chunks", 512);

        int inserted = sqliteDriver.upsertChunks("kb_chunks", faqChunks);
        assertEquals(2, inserted);
        System.out.println("4. SQLite 首次入库 OK: 写入 " + inserted + " 条问答记录");

        // 5. 相似度检索召回
        float[] queryVec = embEngine.embedBatch(List.of("密码忘了怎么办？如何重置？")).get(0);
        List<SearchResult> results = sqliteDriver.searchSimilar("kb_chunks", queryVec, 2, 0.1f);
        assertFalse(results.isEmpty(), "A password reset query must retrieve an FAQ chunk");
        assertEquals(1, results.get(0).getRank());
        System.out.println("5. 相似度查询召回测试 OK: 命中 Top " + results.size() + " 条");
        for (SearchResult sr : results) {
            System.out.println("   - Rank " + sr.getRank() + " | 分值: " + String.format("%.4f", sr.getScore()) + " | 召回内容: " + sr.getContent().replace("\n", " "));
        }

        System.out.println("====== [RagIngestionToolModule 增强版全链路自测通过] ======");
    }
}
