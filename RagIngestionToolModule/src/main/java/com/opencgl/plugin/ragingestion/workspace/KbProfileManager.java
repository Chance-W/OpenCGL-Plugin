package com.opencgl.plugin.ragingestion.workspace;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库工作空间持久化中心管理器。
 * 负责保存与读取多套知识库项目节点配置。
 */
public class KbProfileManager {

    private final File configFile;
    private final List<KbWorkspaceProfile> profiles = new ArrayList<>();

    public KbProfileManager() {
        String homeDir = System.getProperty("user.home");
        File dir = new File(homeDir, ".gemini");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        configFile = new File(dir, "rag_kb_profiles.json");
        loadProfiles();
    }

    public synchronized List<KbWorkspaceProfile> getProfiles() {
        return new ArrayList<>(profiles);
    }

    public synchronized void saveOrUpdateProfile(KbWorkspaceProfile profile) {
        boolean found = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getProfileId().equals(profile.getProfileId())) {
                profiles.set(i, profile);
                found = true;
                break;
            }
        }
        if (!found) {
            profiles.add(profile);
        }
        saveToFile();
    }

    public synchronized void deleteProfile(String profileId) {
        profiles.removeIf(p -> p.getProfileId().equals(profileId));
        saveToFile();
    }

    private void loadProfiles() {
        profiles.clear();
        try {
            if (configFile.exists()) {
                String json = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
                List<KbWorkspaceProfile> list = JSON.parseArray(json, KbWorkspaceProfile.class);
                if (list != null && !list.isEmpty()) {
                    profiles.addAll(list);
                    boolean hasGeneral = profiles.stream().anyMatch(p -> "default-semantic-general".equals(p.getProfileId()));
                    if (!hasGeneral) {
                        profiles.add(0, new KbWorkspaceProfile(
                                "default-semantic-general",
                                "1. 通用智能递归语义切片库 (推荐默认)",
                                "",
                                true, true, "",
                                "1. 智能递归语义切片 (推荐: 段落->句子)",
                                "",
                                500, 60,
                                true, "text-embedding-3-small", 768,
                                "http://localhost:11434/v1/embeddings",
                                "SQLITE", "kb_general_chunks", "rag_kb.db", true
                        ));
                        saveToFile();
                    }
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("[KbProfileManager] 读取知识库配置异常，将使用系统推荐默认配置: " + e.getMessage());
        }

        // 默认推荐工作空间节点
        profiles.add(new KbWorkspaceProfile(
                "default-semantic-general",
                "1. 通用智能递归语义切片库 (推荐默认)",
                "",
                true, true, "",
                "1. 智能递归语义切片 (推荐: 段落->句子)",
                "",
                500, 60,
                true, "text-embedding-3-small", 768,
                "http://localhost:11434/v1/embeddings",
                "SQLITE", "kb_general_chunks", "rag_kb.db", true
        ));

        profiles.add(new KbWorkspaceProfile(
                "default-faq-sqlite",
                "2. 客服售前 FAQ 问答库 (专项Q&A)",
                "",
                true, true, "",
                "3. FAQ 问答对一问一答专项 (仅提问Q向量化)",
                "",
                500, 60,
                true, "text-embedding-3-small", 768,
                "http://localhost:11434/v1/embeddings",
                "SQLITE", "kb_faq_chunks", "rag_faq.db", true
        ));

        profiles.add(new KbWorkspaceProfile(
                "default-faiss-local",
                "3. 技术代码与 API 手册检索库 (FAISS极速)",
                "",
                true, true, "",
                "1. 智能递归语义切片 (推荐: 段落->句子)",
                "",
                600, 80,
                false, "offline-bge-small-zh", 512,
                "",
                "FAISS_LOCAL", "kb_tech_chunks", "rag_faiss.index", true
        ));
        saveToFile();
    }

    private void saveToFile() {
        try {
            String json = JSON.toJSONString(profiles, JSONWriter.Feature.PrettyFormat);
            Files.writeString(configFile.toPath(), json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[KbProfileManager] 写入知识库配置文件失败: " + e.getMessage());
        }
    }
}
