package com.opencgl.decompiler.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.CodeArea;
import com.opencgl.decompiler.model.ClassNode;

/**
 * 反编译器视图基类
 */
public class DecompilerView {
    @FXML public StackPane rootPane;
    
    // 工具栏
    @FXML public MFXButton openFileButton;
    @FXML public MFXButton openFolderButton;
    @FXML public MFXButton exportButton;
    @FXML public MFXButton exportAllButton;
    @FXML public TextField searchField;
    @FXML public Button navigationBackButton;
    @FXML public Button navigationForwardButton;
    
    // 文件树
    @FXML public TreeView<ClassNode> fileTreeView;
    
    // 代码显示区
    @FXML public Label classNameLabel;
    @FXML public Label lineCountLabel;
    @FXML public CodeArea codeArea;
    @FXML public TabPane codeTabPane;
    @FXML public TextField codeSearchField;
    @FXML public Button codeSearchPrevButton;
    @FXML public Button codeSearchNextButton;
    @FXML public javafx.scene.layout.HBox codeSearchBar;
    
    // 状态栏
    @FXML public Label statusLabel;
    @FXML public Label fileInfoLabel;
    @FXML public javafx.scene.control.ProgressBar symbolIndexProgress;
    @FXML public Label symbolIndexLabel;
}
