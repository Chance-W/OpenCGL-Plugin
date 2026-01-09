package com.opencgl.renamer.service;

import com.opencgl.renamer.model.FileRenameEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class FileRenameService {
    private static final Logger logger = LoggerFactory.getLogger(FileRenameService.class);
    
    private final List<RenameOperation> history = new ArrayList<>();
    
    public static class RenameOperation {
        public Path oldPath;
        public Path newPath;
        
        public RenameOperation(Path oldPath, Path newPath) {
            this.oldPath = oldPath;
            this.newPath = newPath;
        }
        
        public void undo() throws IOException {
            if (Files.exists(newPath)) {
                Files.move(newPath, oldPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
    
    public boolean executeRename(FileRenameEntry entry) {
        if (!entry.needsRename()) {
            entry.setStatus("跳过");
            return true;
        }
        
        try {
            File oldFile = entry.getOriginalFile();
            File newFile = new File(oldFile.getParent(), entry.getNewName());
            
            if (newFile.exists() && !newFile.equals(oldFile)) {
                entry.setStatus("冲突");
                entry.setHasConflict(true);
                return false;
            }
            
            Path oldPath = oldFile.toPath();
            Path newPath = newFile.toPath();
            
            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
            
            // 记录操作历史
            history.add(new RenameOperation(oldPath, newPath));
            
            entry.setStatus("✓ 成功");
            logger.info("重命名: {} -> {}", oldFile.getName(), newFile.getName());
            return true;
            
        } catch (IOException e) {
            entry.setStatus("✗ 失败");
            logger.error("重命名失败: " + entry.getOriginalName(), e);
            return false;
        }
    }
    
    public void undoLastBatch() {
        for (int i = history.size() - 1; i >= 0; i--) {
            try {
                history.get(i).undo();
                logger.info("撤销: {} -> {}", history.get(i).newPath, history.get(i).oldPath);
            } catch (IOException e) {
                logger.error("撤销失败", e);
            }
        }
        history.clear();
    }
    
    public void clearHistory() {
        history.clear();
    }
    
    public boolean hasHistory() {
        return !history.isEmpty();
    }
}
