# OpenCGL-Plugin

## 说明

公共插件开发指导手册
模板 module 可见[TemplatePlugin](TemplatePlugin)
按照模板编写自己的工具，可选择是否引入 OpenCGL-Base，OpenCGL-Base提供了一些公共的数据，信息以及依赖，还有树视图的通用实现，如果需要的化，需要下载OpenCGL-Base源码，编译
install 到本地仓库即可，源码的地址附在了文章的下面

### 集成
#### 依赖PluginApiModule

```xml

<dependency>
    <groupId>OpenCGL-Plugin</groupId>
    <artifactId>PluginApiModule</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```
### 实现com.opencgl.api.PluginUI接口
```java
package com.opencgl;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class TemplatePluginUI implements PluginUI {

    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;

    public TemplatePluginUI() {
        this.pluginCl = this.getClass().getClassLoader();
    }


    @Override
    public String directoryName() {
        return "插件开发模版";
    }

    @Override
    public String name() {
        return "插件开发模板1";
    }



    @Override
    public URL iconPath() {
        return null;
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Object createView() {
        FXMLLoader loader = new FXMLLoader();
        loader.setClassLoader(pluginCl);
        loader.setLocation(this.getClass().getClassLoader().getResource("com/opencgl/template/views/TemplateWidgetView.fxml"));
        Parent root;
        try {
            root = loader.load();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    @Override
    public void dispose() {

    }
}
```

#### SPI配置
src/main/resources/META-INF/services目录下创建com.opencgl.api.PluginUI文件，内容为实现类，以插件模板为例，内容如下
```text
com.opencgl.TemplatePluginUI
```

### 如何新增一个插件模块

1. **复制模板**：从 [TemplatePlugin](TemplatePlugin) 复制一份为你的新模块目录（或复制 TemplatePlugin2 / TemplatePlugin4，按需选择 FXML 或纯 Java 构建）。
2. **必须修改**：
   - 实现类：实现 `PluginUI` 的类名、包名，以及 `name()`、`directoryName()`（建议来自 I18N：`I18N.get("label.name")` / `I18N.get("label.category")`）。
   - 资源：若使用 I18N，在模块内 `src/main/resources/com/opencgl/你的包/i18n/` 下增加 `模块名_zh_CN.properties`、`模块名_en.properties`，至少包含 `label.name`、`label.category`。
   - FXML（若有）：`createView()` 里在 `load()` 前调用 `loader.setResources(I18N.getBundle(I18N.getLocale()))`，界面文案用 `%key`，Controller 中 `initialize()` 末尾调用 `initI18n()`，用 `I18N.getBinding` / `I18N.get` 绑定或替换文案。
   - SPI 文件：`src/main/resources/META-INF/services/com.opencgl.api.PluginUI` 内容改为你的 **PluginUI 实现类全限定名**（一行一个，若一个 JAR 提供多个插件可多行）。
3. **单模块构建与加载**：在插件工程根目录执行 `mvn install`（或只构建该模块），将生成的 JAR 放入主程序「设置」里配置的插件目录，在主程序中刷新插件或重启即可加载。

### 插件统一打包与部署

工程根目录提供了统一打包脚本 `build/package_plugins.py`。

#### 打包全部插件

```bash
JAVA_HOME=/Users/chancew./Software/Java/zulu21.44.17_aarch64/zulu-21.jdk/Contents/Home \
python3 build/package_plugins.py
```

脚本会执行根工程的 `mvn clean package -DskipTests`，构建完成后把全部插件运行时 JAR 部署到根目录 `bin/`。同一插件的旧版本 JAR 会被替换，`bin/` 中不属于当前 Maven Reactor 的手工安装文件会保留。需要打包前同时运行测试时使用：

```bash
JAVA_HOME=/Users/chancew./Software/Java/zulu21.44.17_aarch64/zulu-21.jdk/Contents/Home \
python3 build/package_plugins.py --with-tests
```

全量打包时 API 和 Base 会作为同一个 Maven Reactor 的前置模块自动构建，不需要先 `install` 到本地仓库。

如果你需要把依赖显式安装到本地 Maven 仓库（例如在 IDE 中脱离根工程单独构建插件），可以手动执行：

```bash
mvn -pl PluginApiModule,OpenCGL-Base -am install -DskipTests
```

这不是统一打包脚本的必需步骤；脚本使用 Reactor 内部产物直接完成依赖解析。

#### 只打包一个或多个插件

```bash
JAVA_HOME=/Users/chancew./Software/Java/zulu21.44.17_aarch64/zulu-21.jdk/Contents/Home \
python3 build/package_plugins.py --module HttpDebuggerModule
```

`--module` 会自动转换为 Maven 的 `-pl HttpDebuggerModule -am`，因此会先构建 `PluginApiModule`、`OpenCGL-Base` 及其它必要依赖，但只把指定插件部署到 `bin/`。多个插件可以重复传入：

```bash
python3 build/package_plugins.py \
  --module HttpDebuggerModule \
  --module LanMessengerModule
```

也可以直接使用 Maven：

```bash
mvn -pl HttpDebuggerModule -am clean package -DskipTests
```

其中 `-am`（also-make）是关键，否则单独进入插件目录构建时可能找不到尚未安装到本地仓库的 API/Base 依赖。

### 开发规范建议

- **推荐**：FXML + Controller + 各模块 I18N 类（I18nResolver + get/getBinding/getLocale/getBundle）；PluginUI 的 `name()`、`directoryName()` 使用 I18N，便于主程序语言切换时同步。
- **createView**：若 load FXML 或创建视图失败，建议 try-catch 后打日志并返回占位面板或提示，避免单插件异常拖垮主程序。
- 不依赖 Base 的纯 I18n 示例见 [TemplatePlugin3](TemplatePlugin3)，其必选资源 key 见 TemplatePlugin3/README.md。

### Hook 脚本集成与高级特性

OpenCGL 支持在各类请求工具（如 Dubbo 请求、HTTP 请求调试器等）中运行 Groovy 或 JavaScript 形式的 Hook 脚本，以便在请求发送前或响应返回后动态修改数据。

在目前的集成中，`HookContext` 已自动注入了日志打印（`log`）以及操作历史记录（`history`）功能对象；同时，还支持调用 `context.print(...)` / `context.println(...)`，这些打印内容在点击 GUI 的“测试 Hook”时，会被自动收集并展示在界面的测试输出框中！

**Groovy 脚本示例：**
```groovy
def preProcess(String content, context) {
    // 1. 在测试输出框中打印调试信息（点击“测试 Hook”时直接在前端可见）
    context.println("正在预处理请求，原始数据长度: " + content.length())
    
    // 2. 获取日志对象并打印日志
    def log = context.get("log")
    log?.info("【Hook 拦截】执行了 preProcess, 请求参数为: {}", content)
    
    // 3. 获取历史记录工具类并写入操作历史
    def opHistory = context.get("history")
    opHistory?.record("【Hook执行】", "请求已拦截修改: " + content)
    
    // 返回修改后的内容
    return content
}
```

**JavaScript 脚本示例：**
```javascript
function preProcess(content, context) {
    // 1. 在测试输出框中打印调试信息
    context.println("JS正在预处理请求，原始数据: " + content);
    
    // 2. 获取日志对象并打印
    var log = context.get("log");
    if (log != null) {
        log.info("【Hook 拦截 JS】执行了 preProcess, 请求参数为: " + content);
    }
    
    // 3. 获取历史记录并写入操作历史
    var opHistory = context.get("history");
    if (opHistory != null) {
        // 由于 record 接收不定长参数 (String...)，在 Nashorn 中传递数组
        opHistory.record(["【Hook执行 JS】", "请求已拦截修改: " + content]);
    }
    
    // 返回修改后的内容
    return content;
}
```

### Dubbo 泛化调用参数填写与返回值说明

OpenCGL 支持对 Dubbo 接口进行全面泛化调用调试，不仅支持普通的 Java Bean 对象参数，更完全兼容基础数据类型（`String`、`Integer`、`Boolean` 等）、集合类型（`List`、`Map`）以及**多参数接口**和各种复杂返回值格式。

#### 1. 单入参接口调用样例
当目标方法只有一个参数时，根据其对应的数据类型输入报文：
* **入参类型为 `java.lang.String`**：
  直接填写普通 JSON 字符串或直接输入文本字符串：
  ```json
  "Hello OpenCGL Dubbo Test"
  ```
* **入参类型为 `java.util.List`**：
  填写标准的 JSON 数组：
  ```json
  ["item1", "item2", "item3"]
  ```
* **入参类型为普通 Java Bean（如 `com.example.UserDTO`）**：
  填写标准的 JSON 对象：
  ```json
  {
    "userId": 10001,
    "userName": "Chance"
  }
  ```

#### 2. 多入参接口调用样例
当接口方法定义有多个入参（如 `void sendMsg(String title, String content)` 对应参数类型 `java.lang.String,java.lang.String`，或 `void updateUser(String id, UserDTO user)`）时：
* **必须按顺序使用 JSON 数组包裹所有入参**：
  ```json
  [
    "系统通知标题",
    "这是正文信息内容"
  ]
  ```
* **多参数混合类型示例（字符串 + Bean 对象）**：
  ```json
  [
    "USER_001",
    {
      "userName": "Chance",
      "role": "ADMIN"
    }
  ]
  ```

#### 3. 返回值格式展示说明
* **`void` 返回值**：页面安全处理输出为 `void (无返回值 / null)`，不会引发前端渲染或 Hook 脚本空指针异常。
* **基础类型（如返回纯 `String`、`Integer`、`Boolean`）**：直接以普通文本或数值形式输出展现。
* **复杂对象 Bean 或 `Map`**：以美化格式化的 JSON 输出呈现，支持在 Hook 脚本中对返回值继续加工和处理。

## 联系方式

如有问题可直接通过以下方式联系

- 邮箱：chance.w@qq.com;chance_w@126.com
- 微信号：Chance_W-
- 钉钉号：xxx

## 特别感谢

感谢MaterialFX作者，编写了如此炫酷的展示和优化，让OpenCGL的桌面客户端也能焕发光彩

开源地址：https://github.com/palexdev/MaterialFX

感谢追风开源了xJavaFxTool，让我可以从中受益，学到到 javafx 的很多知识，同时插件工程也移植了部分追风的插件

开源地址：https://gitee.com/xwintop/xJavaFxTool

## 客户端仓库地址

https://gitee.com/chance_w/OpenCGL_New

## OpenCGL-Base仓库地址

https://gitee.com/chance_w/open-cgl-base

## 丢几张图

### DUBBO

![dubboRequest_1.png](markdown-assets%2FdubboRequest_1.png)
![dubboRequest_2.png](markdown-assets%2FdubboRequest_2.png)

### REST

![rest-request-1.png](markdown-assets%2Frest-request-1.png)

### Soap

![soap-request-1.png](markdown-assets%2Fsoap-request-1.png)

### Cron表达式

![cron-exp.png](markdown-assets%2Fcron-exp.png)
注：此插件是从https://gitee.com/xwintop/xJavaFxTool处移植

后续待补充。。。
