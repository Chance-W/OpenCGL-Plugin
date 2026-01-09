# 插件国际化硬编码文件梳理计划

本文档梳理 OpenCGL-Plugin-New 下除 TemplatePlugin3 外，**仍包含硬编码展示文案**的 FXML 与 Java 文件，便于按模块逐项替换为 I18N key 并实现 `initI18n()` 绑定。

**实施标准：** 参考 [DubboServiceTestPlugin](DubboServiceTestPlugin)：
- FXML 中所有面向用户的 `text`、`promptText`、`title`、`Tooltip` 等改为 `%key`，或由 Controller 的 `initI18n()` 中 `xxx.textProperty().bind(I18N.getBinding("key"))` 绑定。
- Controller 中 `setText("...")`、`new Tooltip("...")`、`Alert` 文案、`setTitle(...)` 等改为 `I18N.get("key")` 或绑定。

---

## 一、属性文件仅含 label.name / label.category 的模块（需补全 key 并改 FXML/Controller）

以下模块的 `*_zh_CN.properties` / `*_en.properties` 当前**仅包含** `label.name` 与 `label.category`，界面其它文案均为硬编码，需在本计划中补全。

| 模块 | 属性文件路径 | 需补 key 后改动的 FXML / Controller |
|------|--------------|--------------------------------------|
| SqlClientModule | `com/opencgl/sqlclient/i18n/SqlClient_*.properties` | 见下表「SqlClientModule」 |
| EncryptAndDecryptModule | `com/opencgl/encryptanddecrypt/i18n/EncryptAndDecrypt_*.properties` | 见下表「EncryptAndDecryptModule」 |
| FtpToolModule | `com/opencgl/ftp/i18n/FtpTool_*.properties` | 见下表「FtpToolModule」 |
| BrowserToolModule | `com/opencgl/brower/i18n/BrowserTool_*.properties` | 见下表「BrowserToolModule」 |
| JsonXmlFormatModule | `com/opencgl/jsonxml/i18n/JsonXmlFormat_*.properties` | 见下表「JsonXmlFormatModule」 |
| EditorExperienceModule | `com/opencgl/experience/i18n/EditorExperience_*.properties` | 见下表「EditorExperienceModule」 |
| MmlTestModule | `com/opencgl/mml/i18n/MmlTest_*.properties` | 见下表「MmlTestModule」 |
| AiQaModule | `com/opencgl/aiqa/i18n/AiQa_*.properties` | 见下表「AiQaModule」 |
| 以及其余「仅两键」模块：YamlTool, RsaTool, AesTool, Nacos, JWTTool, HashTool, DubboSslTest, SshTerminal, TemplatePlugin, NginxConfig, CronExpBuilder, GRPCTest, Docker, ScriptDebug, SimpleStaticServer, RestMock, DataExtractor, ClipboardHistory, JavaDecompiler, ImageSvg, PathExtractor, RegexTool, VariableGenerator, TextEscape, DbMatchExecuteTool, DbBatchExec, BatchRenamer, JavaCodeGenerator, PixelRuler, PortScanner, LanMessenger, DubboMock, RestTest, SoapTest, RsaJavaFX, GraphQL, WebSocket, DiffTool, MarkdownEditor, CodeSnippet, Elasticsearch, MongoDB | 各模块 `i18n/*_zh_CN.properties` 仅 2 条 | 需扫描对应 FXML + Controller，见下「含硬编码的 FXML 清单」 |

---

## 二、含硬编码的 FXML 文件清单（按模块）

每个条目格式：**模块 | FXML 路径 | 硬编码内容类型与示例**。

- **SqlClientModule**  
  - `SqlClientModule/src/main/resources/com/opencgl/sqlclient/views/SqlClientView.fxml`  
  - `text=`：数据库连接、SQL编辑器、执行(F5)、格式化、清空、查询结果、就绪、导出CSV、导出JSON、连接配置、基本设置、名称:、类型:、主机:、端口:、数据库:、用户名:、密码:、高级设置(SSH/SSL)、高级连接参数 (暂未实现)、使用 SSL、使用 SSH 隧道、测试连接、保存、取消。  
  - 默认值 `text="localhost"` / `text="3306"` 可保留或改为 placeholder；需补充 `promptText` 若存在未列出的输入框。

- **EncryptAndDecryptModule**  
  - `EncryptAndDecryptModule/src/main/resources/EncryptAndDecrypt.fxml`  
  - `text=`：明文：、密文：、字符集：、密钥：、加密、解密。

- **FtpToolModule**  
  - `FtpToolModule/src/main/resources/FtpServer.fxml`  
  - `text=`：用户名：、密码：、共享目录：、选择、下载文件、上传文件、读写权限、添加、保存配置、配置另存为、加载配置、服务器端口：、允许匿名用户、最大连接数：、启动；`TableColumn text=`：是否启用、用户名、密码、共享目录、下载文件、上传文件、读写权限；`promptText=`：共享目录、匿名共享目录；`Tooltip text=`：在名称后加随机数防止文件重复。

- **BrowserToolModule**  
  - `BrowserToolModule/src/main/resources/browserTool.fxml`  
  - `text=`：网址：、跳转、返回首页、在默认浏览器中打开。

- **JsonXmlFormatModule**  
  - `JsonXmlFormatModule/src/main/resources/JsonXmlFormatWidgetView.fxml`  
  - `text=`：报文格式化工具、格式：、格式化、压缩、清空、交换、复制结果、输入、输出、就绪。

- **EditorExperienceModule**  
  - `EditorExperienceModule/src/main/resources/com/opencgl/editor/experience/views/EditorExperienceView.fxml`  
  - `text=`：Editor Implementation:、RichTextFX (Native)、Monaco Editor (Web)、RSyntaxTextArea (Swing)、Language:、Theme:、Format Code、Note: Switching editors...。

- **MmlTestModule**  
  - `MmlTestModule/src/main/resources/MmlWidgetView.fxml`  
  - `Tooltip text=`：刷新、拷贝、保存、发送；`promptText=`：用户名、密码、IP地址、端口。

- **AiQaModule**  
  - `AiQaModule/src/main/resources/AiQaView.fxml`  
  - `text=`：模型列表、模型配置、API 地址:、API Key:、模型:、温度:；`promptText=`：文档目录路径。

- **HttpDebuggerModule**  
  - `HttpDebuggerModule/src/main/resources/com/opencgl/http/views/HttpDebuggerView.fxml`  
  - `text=`：Tab "Auth"、"Settings"；Label 初始值 "Status: -"、"Time: - ms"、"Size: - KB"（若由 Controller 动态更新则保留绑定，仅需 key 化）。

- **ZookeeperToolModule**  
  - `ZookeeperToolModule/src/main/resources/ZookeeperTool.fxml`  
  - `text=`：默认连接串 "localhost:2181"（可为 placeholder 或 key）。

- **DubboServiceTestPlugin**  
  - `DubboServiceTestPlugin/src/main/resources/DubboWidgetView.fxml`  
  - 多处 `promptText=`：Direct Provider Address、com.xxx.requestParam、Version、Dubbo 服务分组、JSON Map、/path/to/client-cert.pem、选填、选择脚本文件... 等。  
  - `DubboServiceTestPlugin/src/main/resources/DubboEnvConfigureView.fxml`  
  - `promptText=`：例如: Dev, Prod、ip:port、ZK 命名空间分组、Jar包或目录路径。

- **DubboMockModule**  
  - `DubboMockModule/src/main/resources/com/opencgl/dubbo/mock/views/DubboMockView.fxml`  
  - 大量 `promptText=`：例如：研发本地ZK、127.0.0.1:2181、dubbo、20880、留空则自动检测...、dubbo-mock-provider、com.example.DemoService、默认留空、等待方法拦截调用...、例如 getUserInfo...、请输入合法的 JSON 报文...。

- **FileChecksumModule**  
  - `FileChecksumModule/src/main/resources/FileChecksumView.fxml`  
  - `promptText=`：输入第一个哈希值、输入第二个哈希值。

- **AesToolModule**  
  - `AesToolModule/src/main/resources/com/opencgl/aestool/views/AesToolView.fxml`  
  - `promptText=`：输入AES密钥...、输入IV向量（CBC模式需要）...、输入要加密的明文...、加密结果或输入要解密的密文...。

- **DbMatchExecuteToolModule**  
  - `DbMatchExecuteToolModule/src/main/resources/DbMatchExecuteWidgetView.fxml`  
  - `promptText=`：数据库信息,悬停获取格式、批次表查询语句(请勿用*)、匹配字段(可用;分隔,按顺序配置)。

- **SimpleStaticServerModule**  
  - `SimpleStaticServerModule/src/main/resources/com/opencgl/staticserver/views/StaticServerView.fxml`  
  - `promptText=`：选择IP地址、8080。

- **SoapTestModule**  
  - `SoapTestModule/src/main/resources/SoapTestModule.fxml`  
  - `promptText=`：请选择请求地址。

- **JavaDecompilerModule**  
  - `JavaDecompilerModule/src/main/resources/com/opencgl/decompiler/views/DecompilerView.fxml`  
  - `promptText=`：搜索类...。

- **NacosModule**  
  - `NacosModule/src/main/resources/com/opencgl/nacos/views/NacosView.fxml`  
  - `promptText=`：可选。

- **GraphQLModule**  
  - `GraphQLModule/src/main/resources/com/opencgl/graphql/views/GraphQLView.fxml`  
  - `promptText=`：Key (如 Authorization)、Value。

- **JWTToolModule**  
  - `JWTToolModule/src/main/resources/com/opencgl/jwt/views/JWTToolView.fxml`  
  - `promptText=`：粘贴 JWT Token...、输入密钥验证签名（可选）、payload 示例、密钥示例。

- **DiffToolModule**  
  - `DiffToolModule/src/main/resources/com/opencgl/diff/views/DiffToolView.fxml`  
  - `promptText=`：粘贴或打开左侧文本...、粘贴或打开右侧文本...。

- **HashToolModule**  
  - `HashToolModule/src/main/resources/com/opencgl/hash/views/HashToolView.fxml`  
  - `promptText=`：请输入要计算哈希的文本...、选择文件...、输入预期哈希值进行对比...。

- **RestTestModule**  
  - `RestTestModule/src/main/resources/RestWidgetView.fxml`  
  - `promptText=`：Hook脚本。

- **RedisModule**  
  - `RedisModule/src/main/resources/RedisWidgetView.fxml`  
  - `text=`：serverInfoLabel 初始为空字符串，若由 Controller 设置文案则需在 Controller 中用 I18N。

其余未在以上逐条列出的 FXML（如 CronExpBuilder、YamlTool、NginxConfig、SshTerminal、Docker、MongoDB、Elasticsearch、WebSocket、CodeSnippet、BatchRenamer、RsaTool、RsaJavaFX、CertGenerator、Timestamp、QRCode、Template、Template2、Template4、PixelRuler、PortScanner、ScriptDebug、LanMessenger、PathExtractor、ImageSvg、MarkdownEditor、ClipboardHistory、JavaCodeGenerator、VariableGenerator、TextEscape、RegexTool、DbBatchExec、RestMock、DataExtractor、DubboSslTest、gRPC 等）需在实施时按同一规则扫描：凡 `text`/`promptText`/`title`/`Tooltip` 非 `%key` 且为展示用文案，均列入该模块的 key 表并改为 %key 或 Controller 绑定。

---

## 三、含硬编码的 Java 文件（Controller / PluginUI）

以下 Java 文件中有 `setText(...)`、`new Tooltip(...)`、`Alert`、`setTitle(...)`、或其它面向用户的中/英文字符串，需改为 `I18N.get("key")` 或在 `initI18n()` 中绑定。

- **RedisModule**  
  - `RedisModule/src/main/java/com/opencgl/redis/controller/RedisWidgetController.java`

- **GitLiteModule**  
  - `GitLiteModule/src/main/java/com/opencgl/gitlite/controller/GitLiteController.java`

- **MqTraceModule**  
  - `MqTraceModule/src/main/java/com/opencgl/mqtrace/controller/MqTraceController.java`

- **RocketMqToolModule**  
  - `RocketMqToolModule/src/main/java/com/opencgl/controller/RocketMqProducerWidgetController.java`  
  - `RocketMqToolModule/src/main/java/com/opencgl/controller/RocketMqConsumerWidgetController.java`

- **KafkaToolModule**  
  - `KafkaToolModule/src/main/java/com/opencgl/kafka/controller/KafkaToolController.java`

- **HttpDebuggerModule**  
  - `HttpDebuggerModule/src/main/java/com/opencgl/http/controller/HttpDebuggerController.java`

- **CertGeneratorModule**  
  - `CertGeneratorModule/src/main/java/com/opencgl/cert/controller/CertGeneratorController.java`

- **MmlTestModule**  
  - `MmlTestModule/src/main/java/com/opencgl/mml/controller/MmlWidgetController.java`

- **JsonXmlFormatModule**  
  - `JsonXmlFormatModule/src/main/java/com/opencgl/jsonxml/controller/JsonXmlFormatWidgetController.java`

- **EditorExperienceModule**  
  - `EditorExperienceModule/src/main/java/com/opencgl/experience/controller/EditorExperienceController.java`

- **FtpToolModule**  
  - `FtpToolModule/src/main/java/com/opencgl/ftp/controller/FtpServerController.java`

- **EncryptAndDecryptModule**  
  - `EncryptAndDecryptModule/src/main/java/com/opencgl/encryptanddecrypt/controller/EncryptAndDecryptController.java`

- **DbMatchExecuteToolModule**  
  - `DbMatchExecuteToolModule/src/main/java/com/opencgl/dbmatchexecute/controller/DbBatchOperWidgetController.java`

- **BrowserToolModule**  
  - `BrowserToolModule/src/main/java/com/opencgl/brower/controller/WebSourcesToolController.java`

- **AiQaModule**  
  - `AiQaModule/src/main/java/com/opencgl/aiqa/controller/AiQaController.java`

- **YamlToolModule**  
  - `YamlToolModule/src/main/java/com/opencgl/yaml/controller/YamlToolController.java`

- **AesToolModule**  
  - `AesToolModule/src/main/java/com/opencgl/aestool/controller/AesToolController.java`

- **FileChecksumModule**  
  - `FileChecksumModule/src/main/java/com/opencgl/checksum/controller/FileChecksumController.java`

- **DubboServiceTestPlugin**  
  - `DubboServiceTestPlugin/src/main/java/com/opencgl/dubbo/controller/DubboWidgetController.java`

- **QRCodeGenerateModule**  
  - `QRCodeGenerateModule/src/main/java/com/opencgl/qr/controller/QRCodeGenerateController.java`

- **TimestampToolModule**  
  - `TimestampToolModule/src/main/java/com/opencgl/timestamp/controller/TimestampToolController.java`

- **DubboMockModule**  
  - `DubboMockModule/src/main/java/com/opencgl/dubbo/mock/controller/DubboMockController.java`

- **SshTerminalModule**  
  - `SshTerminalModule/src/main/java/com/opencgl/ssh/controller/SshTerminalController.java`

- **SimpleStaticServerModule**  
  - `SimpleStaticServerModule/src/main/java/com/opencgl/staticserver/controller/StaticServerController.java`

- **RestTestModule**  
  - `RestTestModule/src/main/java/com/opencgl/controller/RestWidgetController.java`

- **DockerModule**  
  - `DockerModule/src/main/java/com/opencgl/docker/controller/DockerController.java`

- **NacosModule**  
  - `NacosModule/src/main/java/com/opencgl/nacos/controller/NacosController.java`

- **GraphQLModule**  
  - `GraphQLModule/src/main/java/com/opencgl/graphql/controller/GraphQLController.java`

- **JWTToolModule**  
  - `JWTToolModule/src/main/java/com/opencgl/jwt/controller/JWTToolController.java`

- **MongoDBModule**  
  - `MongoDBModule/src/main/java/com/opencgl/mongodb/controller/MongoDBController.java`

- **DiffToolModule**  
  - `DiffToolModule/src/main/java/com/opencgl/diff/controller/DiffToolController.java`

- **GRPCTestModule**  
  - `GRPCTestModule/src/main/java/com/opencgl/grpc/controller/GrpcTestController.java`

- **HashToolModule**  
  - `HashToolModule/src/main/java/com/opencgl/hash/controller/HashToolController.java`

- **SoapTestModule**  
  - `SoapTestModule/src/main/java/com/opencgl/controller/SoapWidgetController.java`  
  - `SoapTestModule/src/main/java/com/opencgl/controller/DialogController.java`

- **SqlClientModule**  
  - `SqlClientModule/src/main/java/com/opencgl/sqlclient/controller/SqlClientController.java`

- **TemplatePlugin**  
  - `TemplatePlugin/src/main/java/com/opencgl/template/controller/TemplateWidgetController.java`

- **WebSocketModule**  
  - `WebSocketModule/src/main/java/com/opencgl/websocket/controller/WebSocketController.java`

- **PixelRulerModule**  
  - `PixelRulerModule/src/main/java/com/opencgl/ruler/controller/PixelRulerController.java`

- **CodeSnippetModule**  
  - `CodeSnippetModule/src/main/java/com/opencgl/snippet/controller/CodeSnippetController.java`

- **NginxConfigModule**  
  - `NginxConfigModule/src/main/java/com/opencgl/nginx/controller/NginxConfigController.java`

- **PortScannerModule**  
  - `PortScannerModule/src/main/java/com/opencgl/portscan/controller/PortScannerController.java`

- **ScriptDebugModule**  
  - `ScriptDebugModule/src/main/java/com/opencgl/scriptdebug/controller/ScriptDebugWidgetController.java`

- **BatchRenamerModule**  
  - `BatchRenamerModule/src/main/java/com/opencgl/renamer/controller/BatchRenamerController.java`

- **DubboSslTestModule**  
  - `DubboSslTestModule/src/main/java/com/opencgl/dubbossl/controller/DubboSslWidgetController.java`

- **LanMessengerModule**  
  - `LanMessengerModule/src/main/java/com/opencgl/lanmsg/controller/LanMessengerController.java`

- **DataExtractorModule**  
  - `DataExtractorModule/src/main/java/com/opencgl/extractor/controller/DataExtractorController.java`

- **ElasticsearchModule**  
  - `ElasticsearchModule/src/main/java/com/opencgl/elasticsearch/controller/ElasticsearchController.java`

- **PathExtractorModule**  
  - `PathExtractorModule/src/main/java/com/opencgl/pathextractor/controller/PathExtractorController.java`

- **JavaDecompilerModule**  
  - `JavaDecompilerModule/src/main/java/com/opencgl/decompiler/controller/DecompilerController.java`

- **MarkdownEditorModule**  
  - `MarkdownEditorModule/src/main/java/com/opencgl/markdown/controller/MarkdownEditorController.java`

- **ClipboardHistoryModule**  
  - `ClipboardHistoryModule/src/main/java/com/opencgl/clipboard/controller/ClipboardHistoryController.java`

- **JavaCodeGeneratorModule**  
  - `JavaCodeGeneratorModule/src/main/java/com/opencgl/codegen/controller/CodeGenController.java`

- **VariableGeneratorModule**  
  - `VariableGeneratorModule/src/main/java/com/opencgl/variable/controller/VariableGeneratorWidgetController.java`

- **ImageSvgModule**  
  - `ImageSvgModule/src/main/java/com/opencgl/imagesvg/controller/ImageSvgController.java`

- **RsaToolModule**  
  - `RsaToolModule/src/main/java/com/opencgl/rsatool/controller/RsaToolController.java`

- **RsaJavaFXModule**  
  - `RsaJavaFXModule/src/main/java/com/opencgl/rsa/controller/RsaKeyGeneratorController.java`

- **RestMockModule**  
  - `RestMockModule/src/main/java/com/opencgl/controller/RestMockServerWidgetController.java`

- **TextEscapeModule**  
  - `TextEscapeModule/src/main/java/com/opencgl/textescape/controller/TextEscapeController.java`

- **RegexToolModule**  
  - `RegexToolModule/src/main/java/com/opencgl/regex/controller/RegexToolController.java`

- **DbBatchExecModule**  
  - `DbBatchExecModule/src/main/java/com/opencgl/dbbatch/controller/DbBatchController.java`

**说明：** 上述 Controller 均需在 `initialize()` 末尾调用 `initI18n()`，并在 `initI18n()` 中对 FXML 注入的 Label/Button/Tab/ComboBox 等控件，凡文案需随语言切换的，使用 `control.textProperty().bind(I18N.getBinding("key"))` 或 `promptTextProperty().bind(...)`；运行时设置的文案（如状态、错误提示）改为 `I18N.get("msg.xxx", ...)`。

---

## 四、实施顺序建议

1. **按模块**：选定一个模块（建议先 **SqlClientModule** 或 **EncryptAndDecryptModule**），完成「属性文件补 key → FXML 改为 %key → Controller initI18n() 绑定 + setText/Alert 改为 I18N.get」全流程，作为模板。
2. **批量**：按本文档第二节、第三节的清单，逐模块补 key、改 FXML、改 Controller。
3. **校验**：每模块完成后切换全局语言，检查界面与插件 name/directoryName 是否全部随语言更新。

---

## 五、排除范围

- **TemplatePlugin3**：不纳入任何 i18n 改动。
- **PluginApiModule**：仅 API 定义，`PluginUI` 默认实现返回 "其他" 为兜底，可保留或后续在 Base 中统一 key。

---

*文档生成后可根据实际扫描结果增删具体文件与行号，本计划以「模块 + 文件路径 + 硬编码类型」为主便于逐项落地。*
