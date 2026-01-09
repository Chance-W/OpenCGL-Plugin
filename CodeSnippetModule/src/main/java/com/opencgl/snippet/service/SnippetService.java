package com.opencgl.snippet.service;

import com.opencgl.snippet.model.Snippet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 代码片段数据库服务
 */
public class SnippetService {
    private static final Logger logger = LoggerFactory.getLogger(SnippetService.class);
    private static final String DB_NAME = "code_snippets.db";
    private final String dbPath;
    
    public SnippetService() {
        // 数据库存储在用户目录
        String userHome = System.getProperty("user.home");
        File dataDir = new File(userHome, ".opencgl/data");
        dataDir.mkdirs();
        this.dbPath = new File(dataDir, DB_NAME).getAbsolutePath();
        initDatabase();
    }
    
    private void initDatabase() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS snippets (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                language TEXT NOT NULL,
                code TEXT NOT NULL,
                description TEXT,
                tags TEXT,
                favorite INTEGER DEFAULT 0,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """;
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            
            // 创建索引
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_language ON snippets(language)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_favorite ON snippets(favorite)");
        } catch (SQLException e) {
            logger.error("初始化数据库失败", e);
        }
    }
    
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }
    
    /**
     * 保存代码片段
     */
    public Long save(Snippet snippet) {
        String sql = """
            INSERT INTO snippets (title, language, code, description, tags, favorite, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, snippet.getTitle());
            pstmt.setString(2, snippet.getLanguage());
            pstmt.setString(3, snippet.getCode());
            pstmt.setString(4, snippet.getDescription());
            pstmt.setString(5, String.join(",", snippet.getTags()));
            pstmt.setInt(6, snippet.isFavorite() ? 1 : 0);
            pstmt.setString(7, snippet.getCreatedAt().toString());
            pstmt.setString(8, snippet.getUpdatedAt().toString());
            
            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.error("保存代码片段失败", e);
        }
        return null;
    }
    
    /**
     * 更新代码片段
     */
    public void update(Snippet snippet) {
        String sql = """
            UPDATE snippets SET title=?, language=?, code=?, description=?, 
            tags=?, favorite=?, updated_at=? WHERE id=?
            """;
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, snippet.getTitle());
            pstmt.setString(2, snippet.getLanguage());
            pstmt.setString(3, snippet.getCode());
            pstmt.setString(4, snippet.getDescription());
            pstmt.setString(5, String.join(",", snippet.getTags()));
            pstmt.setInt(6, snippet.isFavorite() ? 1 : 0);
            pstmt.setString(7, LocalDateTime.now().toString());
            pstmt.setLong(8, snippet.getId());
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("更新代码片段失败", e);
        }
    }
    
    /**
     * 删除代码片段
     */
    public void delete(Long id) {
        String sql = "DELETE FROM snippets WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("删除代码片段失败", e);
        }
    }
    
    /**
     * 查询所有代码片段
     */
    public List<Snippet> findAll() {
        String sql = "SELECT * FROM snippets ORDER BY updated_at DESC";
        List<Snippet> snippets = new ArrayList<>();
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                snippets.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("查询代码片段失败", e);
        }
        return snippets;
    }
    
    /**
     * 按语言查询
     */
    public List<Snippet> findByLanguage(String language) {
        String sql = "SELECT * FROM snippets WHERE language=? ORDER BY updated_at DESC";
        List<Snippet> snippets = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, language);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                snippets.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("查询代码片段失败", e);
        }
        return snippets;
    }
    
    /**
     * 搜索代码片段
     */
    public List<Snippet> search(String keyword) {
        String sql = "SELECT * FROM snippets WHERE title LIKE ? OR description LIKE ? OR code LIKE ? ORDER BY updated_at DESC";
        List<Snippet> snippets = new ArrayList<>();
        String pattern = "%" + keyword + "%";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, pattern);
            pstmt.setString(2, pattern);
            pstmt.setString(3, pattern);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                snippets.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("搜索代码片段失败", e);
        }
        return snippets;
    }
    
    /**
     * 获取所有语言列表
     */
    public List<String> getAllLanguages() {
        String sql = "SELECT DISTINCT language FROM snippets ORDER BY language";
        List<String> languages = new ArrayList<>();
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                languages.add(rs.getString("language"));
            }
        } catch (SQLException e) {
            logger.error("查询语言列表失败", e);
        }
        return languages;
    }
    
    private Snippet mapResultSet(ResultSet rs) throws SQLException {
        Snippet snippet = new Snippet();
        snippet.setId(rs.getLong("id"));
        snippet.setTitle(rs.getString("title"));
        snippet.setLanguage(rs.getString("language"));
        snippet.setCode(rs.getString("code"));
        snippet.setDescription(rs.getString("description"));
        
        String tags = rs.getString("tags");
        if (tags != null && !tags.isEmpty()) {
            snippet.setTags(Arrays.asList(tags.split(",")));
        }
        
        snippet.setFavorite(rs.getInt("favorite") == 1);
        snippet.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
        snippet.setUpdatedAt(LocalDateTime.parse(rs.getString("updated_at")));
        
        return snippet;
    }
}
