# TemplatePlugin3

示例插件 3：演示**不依赖 OpenCGL-Base** 的纯 I18n 实现（自有 ResourceBundle + PluginI18n 接口）。

## 与其它模板的区别

- 使用 `PluginI18n` 接口，在构造函数和 `onLanguageChange` 中通过 `ResourceBundle.getBundle("com.opencgl.Template", locale)` 加载文案。
- 不依赖 OpenCGL-Base 的 I18nResolver，资源文件为 `com/opencgl/Template.properties` 与 `Template_en.properties`。

## 必选资源 key（不可缺失）

若复制本模板或修改资源文件，以下 key 必须在 `Template.properties` / `Template_en.properties` 中存在，否则插件实例化会失败并导致主程序报错：

| Key | 用途 |
|-----|------|
| `plugin.name` | 插件显示名称（name()） |
| `plugin.category` | 插件分类（directoryName()） |
| `label.status` | 状态标签 |
| `label.input` | 输入框标签 |
| `prompt.text` | 输入框占位 |
| `btn.primary` | 主按钮文案 |
| `checkbox.sample` | 复选框文案 |
| `toggle.sample` | 切换按钮文案 |
| `label.progress` | 进度标签 |
| `label.slider` | 滑块标签 |

可选：`status.msg`、`label.category` 等按需保留。

## 资源文件路径

- `src/main/resources/com/opencgl/Template.properties`（默认/中文）
- `src/main/resources/com/opencgl/Template_en.properties`（英文）
