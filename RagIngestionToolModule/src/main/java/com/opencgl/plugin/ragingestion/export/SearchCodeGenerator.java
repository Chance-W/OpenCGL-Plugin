package com.opencgl.plugin.ragingestion.export;

/**
 * 相似度查询代码生成器。
 * 根据用户选择的数据源和语言，一键导出可立即执行的 Python、Java、C++ 与 cURL 检索代码示例。
 */
public class SearchCodeGenerator {

    public static String generatePythonCode(String dbType, String targetName, int dimension, String query) {
        if ("SQLITE".equalsIgnoreCase(dbType)) {
            return """
                # Python SQLite 向量相似度检索代码示例
                import sqlite3
                import json
                import math

                def cosine_sim(v1, v2):
                    dot = sum(a * b for a, b in zip(v1, v2))
                    n1 = math.sqrt(sum(a * a for a in v1))
                    n2 = math.sqrt(sum(b * b for b in v2))
                    return dot / (n1 * n2) if n1 > 0 and n2 > 0 else 0.0

                conn = sqlite3.connect("rag_kb.db")
                cur = conn.cursor()
                cur.execute("SELECT chunk_id, file_name, content, embedding_json FROM %s")
                # 此处将 query_vector 替换为对 "%s" 向量化后的 float 列表
                query_vector = [0.1] * %d
                
                results = []
                for row in cur.fetchall():
                    emb = json.loads(row[3])
                    score = cosine_sim(query_vector, emb)
                    if score >= 0.70:
                        results.append((score, row[0], row[1], row[2]))

                results.sort(key=lambda x: x[0], reverse=True)
                for rank, (score, cid, fname, content) in enumerate(results[:5], 1):
                    print(f"[{rank}] 得分:{score:.4f} 文件:{fname} 内容:{content[:50]}...")
                """.formatted(targetName, query, dimension);
        } else if ("PGVECTOR".equalsIgnoreCase(dbType)) {
            return """
                # Python PostgreSQL (PGVector) 相似度检索示例
                import psycopg2

                conn = psycopg2.connect("postgresql://postgres:pwd@localhost:5432/opencgl")
                cur = conn.cursor()
                # query_vector 为 %d 维向量浮点列表
                query_vector = [0.1] * %d
                vec_str = str(query_vector)

                sql = '''
                    SELECT chunk_id, file_name, content, 1 - (embedding <=> %%s::vector) AS score
                    FROM %s
                    WHERE 1 - (embedding <=> %%s::vector) >= 0.70
                    ORDER BY score DESC LIMIT 5
                '''
                cur.execute(sql, (vec_str, vec_str))
                for row in cur.fetchall():
                    print(f"得分:{row[3]:.4f} | 文件:{row[1]} | 内容:{row[2][:50]}...")
                """.formatted(dimension, dimension, targetName);
        } else {
            return """
                # Python Elasticsearch KNN 相似度检索示例
                import requests
                import json

                url = "http://localhost:9200/%s/_search"
                payload = {
                    "size": 5,
                    "query": {
                        "script_score": {
                            "query": {"match_all": {}},
                            "script": {
                                "source": "cosineSimilarity(params.qv, 'embedding') + 1.0",
                                "params": {"qv": [0.1] * %d}
                            }
                        }
                    }
                }
                resp = requests.post(url, json=payload, headers={"Content-Type": "application/json"})
                for hit in resp.json().get("hits", {}).get("hits", []):
                    print(f"得分:{hit['_score']-1.0:.4f} 内容:{hit['_source']['content'][:50]}...")
                """.formatted(targetName, dimension);
        }
    }

    public static String generateJavaCode(String dbType, String targetName, int dimension) {
        return """
            // Java JDBC PGVector / SQLite 相似度检索代码片段
            import java.sql.*;

            public class VectorSearchDemo {
                public static void main(String[] args) throws Exception {
                    String url = "jdbc:postgresql://localhost:5432/opencgl"; // 或 jdbc:sqlite:rag_kb.db
                    try (Connection conn = DriverManager.getConnection(url, "postgres", "pwd")) {
                        String sql = "SELECT chunk_id, content, 1 - (embedding <=> ?::vector) AS score "
                                   + "FROM %s ORDER BY score DESC LIMIT 5";
                        try (PreparedStatement ps = conn.prepareStatement(sql)) {
                            // 传入 JSON 格式 %d 维向量字符串
                            ps.setString(1, "[0.1, 0.2, ...]");
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    System.out.println("匹配度: " + rs.getDouble("score")
                                        + " 内容: " + rs.getString("content"));
                                }
                            }
                        }
                    }
                }
            }
            """.formatted(targetName, dimension);
    }

    public static String generateCurlCode(int port, String query, int topK) {
        return """
            # 通过本地微服务 HTTP REST API 调用相似度查找
            curl -X POST http://127.0.0.1:%d/api/rag/search \\
                 -H "Content-Type: application/json" \\
                 -d '{
                       "query": "%s",
                       "topK": %d,
                       "minScore": 0.70
                     }'
            """.formatted(port, query, topK);
    }
}
