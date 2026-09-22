package com.opencgl.http.views;

import com.opencgl.base.controls.CustomTextArea;
import com.opencgl.http.model.KeyValueEntry;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * 调试器视图基类 (FXML Injection)
 */
public class HttpDebuggerView {

    @FXML public StackPane mainStackPane;
    
    // Toolbar
    @FXML public Button proxyButton;
    @FXML public Button importButton;
    
    // Right Area
    @FXML public StackPane contentArea;
    @FXML public VBox emptyState;
    @FXML public SplitPane requestPanel;

    // 请求行
    @FXML public ComboBox<String> methodComboBox;
    @FXML public TextField urlField;
    @FXML public Button sendButton;
    @FXML public Button saveButton;
    public Button codeButton; // New Field (Manual)
    
    // Environment UI (Manual)
    public ComboBox<String> envComboBox;
    public Button manageEnvButton;

    // 请求配置Tab
    @FXML public TabPane requestTabPane;
    
    // Params表格
    @FXML public TableView<KeyValueEntry> paramsTable;
    @FXML public TableColumn<KeyValueEntry, Boolean> paramEnabledCol;
    @FXML public TableColumn<KeyValueEntry, String> paramKeyCol;
    @FXML public TableColumn<KeyValueEntry, String> paramValueCol;
    @FXML public Button paramsBulkEditBtn; // New
    @FXML public CustomTextArea paramsBulkEditor; // New
    @FXML public Button addParamButton;

    // Headers表格
    @FXML public TableView<KeyValueEntry> headersTable;
    @FXML public TableColumn<KeyValueEntry, Boolean> headerEnabledCol;
    @FXML public TableColumn<KeyValueEntry, String> headerKeyCol;
    @FXML public TableColumn<KeyValueEntry, String> headerValueCol;
    @FXML public Button addHeaderButton;

    // Body编辑器
    @FXML public RadioButton bodyNoneRadio;
    @FXML public RadioButton bodyJsonRadio;
    @FXML public RadioButton bodyFormRadio;
    @FXML public RadioButton bodyRawRadio;
    @FXML public RadioButton bodyXmlRadio;
    @FXML public ToggleGroup bodyTypeGroup;
    @FXML public HBox bodyFormatToolbar; // New
    @FXML public StackPane bodyRawContainer; // New
    @FXML public CustomTextArea bodyEditor;
    @FXML public Button compactBodyButton;
    @FXML public HBox requestFabBox; // New
    
    // Form body fields
    @FXML public StackPane bodyContentPane;
    @FXML public VBox bodyFormContainer;
    @FXML public Button formBulkEditBtn; // New
    @FXML public CustomTextArea formBulkEditor; // New
    @FXML public TableView<KeyValueEntry> bodyFormTable;
    @FXML public TableColumn<KeyValueEntry, String> formKeyCol;
    @FXML public TableColumn<KeyValueEntry, String> formValueCol;
    @FXML public Button addFormFieldButton;
    
    // Auth
    @FXML public ComboBox<String> authTypeCombo;
    @FXML public VBox authContentPane;
    
    // Hook（与 Dubbo 模块一致：路径、浏览、保存、编辑、测试、输出）
    @FXML public Tab hooksTab;
    @FXML public Label hookScriptLabel;
    @FXML public Label scriptPreviewLabel;
    @FXML public Label testOutputLabel;
    @FXML public TextField hookScriptPathField;
    @FXML public Button browseHookButton;
    @FXML public Button saveHookButton;
    @FXML public Button testHookButton;
    @FXML public CustomTextArea hookPreviewTextArea;
    @FXML public CustomTextArea hookOutputTextArea;

    // Settings
    @FXML public TextField configTimeoutField;
    @FXML public CheckBox configSslCheck;
    @FXML public CheckBox configRedirectCheck;
    
    // mTLS & Custom CA
    @FXML public TextField configCertPathField;
    @FXML public Button browseCertBtn;
    @FXML public PasswordField configCertPassField;
    
    @FXML public TextField configCaPathField;
    @FXML public Button browseCaBtn;
    @FXML public PasswordField configCaPassField;

    // 响应区域
    @FXML public HBox responseStatusBar;
    @FXML public Label statusLabel;
    @FXML public Label timeLabel;
    @FXML public Label sizeLabel;

    // 响应Tab
    @FXML public TabPane responseTabPane;
    @FXML public CustomTextArea responseBodyArea;
    @FXML public Button formatJsonButton;
    @FXML public Button previewInBrowserButton; // New
    @FXML public HBox responseFabBox; // New

    // 响应Headers表格
    @FXML public TableView<KeyValueEntry> responseHeadersTable;
    @FXML public TableColumn<KeyValueEntry, String> respHeaderKeyCol;
    @FXML public TableColumn<KeyValueEntry, String> respHeaderValueCol;

    // History 详情（单击历史记录，对齐 Dubbo 测试模块）
    @FXML public StackPane historyDetailPane;
    @FXML public CustomTextArea historyFullTextArea;
}
