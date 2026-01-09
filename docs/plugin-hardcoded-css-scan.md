# 插件硬编码 CSS 扫描与主题变量适配

## 扫描范围
- 全量插件 FXML 内联 `style="..."` 中的颜色/背景
- 插件自有 `.css` 中的硬编码颜色（排除 OpenCGL-Base、第三方如 font-awesome）

## 主题变量参考 (OpenCGL-Base)
- **背景**: `-theme-bg-primary`, `-theme-bg-secondary`, `-theme-bg-tertiary`
- **文字**: `-theme-text-primary`, `-theme-text-secondary`, `-theme-text-disabled`, `-theme-text-inverse`
- **边框**: `-theme-border`
- **强调**: `-theme-accent`, `-theme-info`, `-theme-success`
- **控件内部背景**: 由主题在 `.root` 下定义 `-fx-control-inner-background`，插件无需写死

## 扫描结果：FXML 内联硬编码

| 模块 | 文件 | 硬编码内容 | 适配方案 |
|------|------|------------|----------|
| Base64ToolModule | Base64ToolView.fxml | #e0e0e0, #3c3f41, #1e1e1e, #90caf9 | 改为主题变量或移除（继承 .root） |
| UuidGeneratorModule | UuidGeneratorView.fxml | #e0e0e0, #1e1e1e, #90caf9 | 同上 |
| ColorPickerModule | ColorPickerView.fxml | #e0e0e0, #90caf9 | 同上 |
| CurlToRequestModule | CurlToRequestView.fxml | #2d3436, rgba(255,255,255,0.95), #1e272e, #dfe6e9, #55efc4, #74b9ff, #b2bec3 | 同上 |
| JsonYamlFormatModule | JsonYamlFormatView.fxml | #1a1a2e, rgba(255,255,255,0.08), #eaeaea, #a0a0a0, #16213e, #7fdbda, #b2bec3 | 同上 |
| CsvViewerModule | CsvViewerView.fxml | gray | -theme-text-secondary |
| SqlFormatterModule | SqlFormatterView.fxml | gray | -theme-text-secondary |
| LogViewerModule | LogViewerView.fxml | gray | -theme-text-secondary |
| PropertiesEditorModule | PropertiesEditorView.fxml | #e0e0e0, gray | 主题变量 |
| PingScanModule | PingScanView.fxml | #e0e0e0, #1e1e1e, #90caf9 | 同上 |
| ProcessPortModule | ProcessPortView.fxml | -fx-padding only | 保留 padding |
| EnvVarsModule | EnvVarsView.fxml | -fx-padding only | 保留 padding |
| TcpUdpToolModule | TcpUdpToolView.fxml | #e0e0e0, #1e1e1e, #90caf9 | 主题变量 |

## 适配原则
1. **颜色**：一律改为 `-theme-*` 或删除内联颜色，由主程序 Scene 的 `.root` 主题继承。
2. **-fx-control-inner-background / -fx-text-fill**：删除硬编码色值，让主题统一提供。
3. **保留**：`-fx-padding`、`-fx-font-family`、`-fx-font-size`、`-fx-font-weight` 等与主题无关的属性。

## 本次已适配（FXML 内联样式）
- Base64ToolModule, UuidGeneratorModule, ColorPickerModule, CurlToRequestModule, JsonYamlFormatModule  
- CsvViewerModule, SqlFormatterModule, LogViewerModule, PropertiesEditorModule  
- PingScanModule, ProcessPortModule, EnvVarsModule, TcpUdpToolModule  

上述模块中所有硬编码颜色已改为 `-theme-text-primary` / `-theme-text-secondary` / `-theme-bg-primary` / `-theme-bg-secondary` / `-theme-info` / `-theme-success` 等，并移除 `-fx-control-inner-background` 以继承主题。

## 第二批已适配（FXML 内联样式）
- PathExtractorModule, ElasticsearchModule, DataExtractorModule, LanMessengerModule  
- WebSocketModule, PortScannerModule, GRPCTestModule, DubboSslTestModule  
- MongoDBModule, NacosModule, DockerModule, GraphQLModule  
- NginxConfigModule, CodeSnippetModule, PixelRulerModule, HttpDebuggerModule  
- YamlToolModule, TimestampToolModule, SimpleStaticServerModule  

上述模块中硬编码颜色/背景已统一改为 `-theme-*` 变量；按钮语义色使用 `-theme-success` / `-theme-danger` / `-theme-info` / `-theme-accent` / `-theme-warning`，文字使用 `-theme-text-inverse`。

## 第三批已适配（并发执行剩余全部）
- **TimestampToolModule**：textFill/按钮色 → 主题变量  
- **DbBatchExecModule**、**JavaDecompilerModule**：按钮色 → -theme-success / -theme-info / -theme-danger / -theme-accent  
- **DubboServiceTestPlugin**、**DbMatchExecuteToolModule**：prompt 色、边框 → -theme-text-secondary / -theme-border  
- **GitLiteModule**、**DubboMockModule**、**RedisModule**：背景/文字/按钮/状态栏 → 主题变量  
- **HashToolModule**、**TextEscapeModule**：整页 + 多组按钮/标签 → 主题变量  
- **RestTestModule**：顶栏/输入区/按钮/标签/状态码色 → -theme-bg-secondary / -theme-border / -theme-success 等  
- **DiffToolModule**、**JWTToolModule**、**KafkaToolModule**、**MqTraceModule**：深色布局 + 控件/按钮/结果区 → 主题变量  
- **RegexToolModule**、**MarkdownEditorModule**、**ImageSvgModule**、**ScriptDebugModule**、**VariableGeneratorModule**、**JavaCodeGeneratorModule**、**FileChecksumModule**、**MmlTestModule**、**JsonXmlFormatModule**、**AesToolModule**、**EditorExperienceModule**、**RocketMqToolModule**（Consumer + Producer）：所有扫描到的硬编码色已改为 `-theme-*`。

## 插件自有 CSS 文件（未改）
以下为插件内 CSS，多为语法高亮或第三方，本次仅改 FXML 内联样式，CSS 若需随主题可后续替换变量：
- HttpDebuggerModule, CertGeneratorModule, RsaToolModule, RestTestModule, JavaCodeGeneratorModule, AesToolModule, RocketMqToolModule, DubboSslTestModule, RestMockModule, AiQaModule, EditorExperienceModule, SshTerminalModule, JavaDecompilerModule, CronExpBuilderModule 等（见 `**/*.css` 列表）。
