package com.opencgl.redis.components;

import com.opencgl.redis.controller.RedisSessionController;
import com.opencgl.redis.model.RedisWidgetDto;
import com.opencgl.redis.i18n.I18N;
import com.opencgl.base.utils.DialogUtil;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Tab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JavaFX Tab that wraps the RedisSessionView.
 * It manages the lifecycle of a single Redis connection session.
 */
public class RedisSessionTab extends Tab {
    private static final Logger log = LoggerFactory.getLogger(RedisSessionTab.class);
    private RedisWidgetDto connectionData;
    private RedisSessionController sessionController;

    public RedisSessionTab(RedisWidgetDto dto) {
        super(buildTabTitle(dto));
        this.connectionData = dto;
        initContent();
    }

    private void initContent() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/RedisSessionView.fxml"));
            loader.setClassLoader(getClass().getClassLoader());
            // FXML 内 %key 语法需要 ResourceBundle，否则会抛 "No resources specified"
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            
            setContent(loader.load());
            sessionController = loader.getController();
            
            // Initialize session logic
            sessionController.initSession(connectionData, () -> {
                // Optional: What to do when the inner 'Disconnect' button is clicked?
                // Usually we might just leave the tab open but disconnected, or we can close it.
                // For now, we leave it open. The user can close the tab explicitly.
            });
            
            // Disconnect when tab is closed
            setOnClosed(e -> {
                dispose();
            });
            
        } catch (Exception e) {
            log.error("Failed to load RedisSessionView.fxml", e);
            DialogUtil.showErrorInfo("Failed to open connection tab: " + e.getMessage());
        }
    }

    private static String buildTabTitle(RedisWidgetDto dto) {
        if (dto == null) {
            return "New Connection";
        }
        String name = dto.getConnectionName();
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        return dto.getHost() + ":" + dto.getPort();
    }

    public RedisWidgetDto getConnectionData() {
        return connectionData;
    }

    public void dispose() {
        if (sessionController != null) {
            sessionController.disconnect();
            sessionController = null;
        }
    }
}
