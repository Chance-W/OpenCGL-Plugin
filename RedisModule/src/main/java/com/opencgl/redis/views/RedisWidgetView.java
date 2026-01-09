package com.opencgl.redis.views;

import com.opencgl.redis.model.RedisWidgetDto;
import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Redis 视图基类
 */
public class RedisWidgetView {
    
    @FXML protected AnchorPane mainAnchorPane;
    @FXML protected SplitPane mainSplitPane;
    
    // 工具栏按钮
    @FXML protected MFXButton addConnectionBtn;
    @FXML protected MFXButton editConnectionBtn;
    @FXML protected MFXButton deleteConnectionBtn;
    @FXML protected MFXButton refreshTreeBtn;
    
    // 连接树
    @FXML protected TreeView<RedisWidgetDto> connectionTree;
    
    // 测试空状态提示
    @FXML protected VBox noConnectionPane;
    @FXML protected Label noConnectionLabel;
    @FXML protected MFXButton createFirstConnectionBtn;
    
    // 多标签页容器
    @FXML protected TabPane sessionTabPane;
}
