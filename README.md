# OpenCGL 插件工程（OpenCGL-Plugin-New）

## 说明

本工程是 OpenCGL 桌面工具的 Maven 多模块插件工程，包含插件 API、公共组件和业务插件。本 README 同时说明插件开发、测试、打包和部署方式。

- `PluginApiModule/`：插件接口，Maven 坐标为 `com.opencgl:com.opencgl.api:1.0.0`。
- `OpenCGL-Base/`：公共组件、树视图、数据库和脚本等工具，坐标为 `com.opencgl:com.opencgl.base:1.0.0`。
- 各业务模块：例如 `HttpDebuggerModule/`、`DubboServiceTestPlugin/`、`RedisModule/`。
- `build/package_plugins.py`：构建并部署插件到 `bin/` 或指定目录。
- `build/collect_plugins.py`：收集、校验已有构建产物并生成清单，不执行编译。
- `bin/`：插件部署输出目录，不是主程序安装包目录。

API 和 Base 源码已包含在本工程中，正常从根目录构建时无需另行下载。插件模板见 [TemplatePlugin](TemplatePlugin)。主程序属于另一个工程，本工程不会生成主程序的 DMG、EXE 或 Linux 安装包。

快速跳转：[详细打包与部署](#插件统一打包与部署) · [新增插件](#如何新增一个插件模块) · [开发规范](#开发规范建议) · [Hook 脚本](#hook-脚本集成与高级特性)

### 集成
#### 依赖PluginApiModule

```xml

<dependency>
    <groupId>com.opencgl</groupId>
    <artifactId>com.opencgl.api</artifactId>
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
3. **注册模块**：修改模块 POM 的 `artifactId`，并将模块目录加入根 `pom.xml` 的 `<modules>`。全量脚本以此列表为准，不会自动发现未注册目录。
4. **构建与加载**：在工程根目录执行 `python3 build/package_plugins.py --module 你的模块目录名`。关闭主程序后部署到实际配置的插件目录，再重启主程序。详细步骤见下文。

### 插件统一打包与部署

#### 1. 先区分构建、安装依赖和部署

| 操作 | 实际作用 | 是否更新插件 `bin/` |
| --- | --- | --- |
| `mvn ... test` | 编译并运行 Maven 测试 | 否 |
| `mvn ... package` | 生成各模块 `target/` 下的 JAR | 不应据此判断已部署；请使用统一脚本 |
| `mvn ... install` | 在 package 基础上将 Maven 产物及 POM 安装到本地仓库 | 不等于部署到主程序 |
| `python3 build/package_plugins.py` | Maven 构建成功后，复制选中的业务插件 JAR | 是，默认工程根目录 `bin/` |
| `python3 build/collect_plugins.py ...` | 收集已有 target JAR、校验 ZIP 完整性并生成 SHA-256 清单 | 写入显式指定的收集目录 |

统一打包脚本会自动构建 API/Base，但**不会自动执行 `mvn install`**。同一次 Maven Reactor 构建可以直接解析上游模块产物，所以正常全量/单插件脚本构建不需要提前 install。

#### 2. 环境准备

需要 JDK 21（项目推荐 Azul Zulu）、Maven 和 Python 3。构建规则要求 Java 版本范围 `[21,22)`，不要用 JDK 17 或 JDK 22。Python 脚本使用标准库，无需 pip 安装依赖；建议使用 Python 3.10 或更新版本。

首次构建需要能够访问配置的 Maven 仓库。脚本直接调用 PATH 中的 `mvn`，不会自动寻找 Maven 安装目录，也不提供 `--maven` 参数。

macOS 本机示例（按实际安装位置调整）：

```bash
cd /Users/chancew./Documents/Chance/code/self_code/OpenCGL-Plugin-New
export JAVA_HOME=/Users/chancew./Software/Java/zulu21.44.17_aarch64/zulu-21.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:/Users/chancew./Software/apache-maven-3.6.3/bin:$PATH"
java -version
mvn -version
python3 --version
```

Linux 示例（替换 JDK 和工程路径）：

```bash
cd /path/to/OpenCGL-Plugin-New
export JAVA_HOME=/path/to/zulu-21
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
python3 --version
```

Windows PowerShell 示例（替换为本机路径）：

```powershell
Set-Location "D:\code\OpenCGL-Plugin-New"
$env:JAVA_HOME = "C:\Java\zulu-21"
$env:Path = "$env:JAVA_HOME\bin;C:\Tools\apache-maven\bin;$env:Path"
java -version
mvn -version
py -3 --version
py -3 build/package_plugins.py --help
```

Windows 后续命令使用 `py -3` 替换 `python3`；如果未安装 Python Launcher，也可使用已正确配置的 `python`。以 `mvn -version` 输出的 Java home 和 Java version 为准，避免终端 `java` 与 Maven 实际使用的 JDK 不一致。

#### 3. 打包前先退出主程序

先完整退出 OpenCGL，再更新它正在使用的插件目录。仅关闭插件页签不一定释放 ClassLoader 或 JAR 文件。Windows 可能直接阻止覆盖；macOS/Linux 即使覆盖成功，也不能保证正在运行的程序使用新代码。

脚本没有进程占用检测、自动备份或整体回滚功能。重要的旧包请先另行备份；不要把含有个人数据的目录当作构建输出目录。

#### 4. 全量打包（最常用）

在插件工程根目录执行：

```bash
python3 build/package_plugins.py
```

实际构建命令为：

```bash
mvn clean package -DskipTests
```

执行过程：

1. Maven 按根 POM 的模块列表和依赖顺序构建 API、Base 及业务插件。
2. `clean` 清理参与构建模块的旧 `target/`，随后重新编译和打包。
3. 默认 `-DskipTests` 跳过测试执行，仍可能编译测试源码；不是“测试通过”。
4. Maven 成功后，脚本选择各业务模块的最终运行时 JAR。
5. 将 JAR 部署到工程根目录 `bin/`，打印 `deployed N plugin JARs to ...` 及逐模块文件列表。

需要同时运行测试：

```bash
python3 build/package_plugins.py --with-tests
```

此时 Maven 执行 `clean package`，测试失败会中止，不会进入脚本的部署阶段。这里指 Maven 测试，不会自动执行仓库中的 Python 测试。涉及 JavaFX 的测试可能需要图形桌面；无显示环境的 Linux 执行完整测试时需另行准备显示环境。

#### 5. 单插件及多插件打包

只更新 HTTP 请求调试器：

```bash
python3 build/package_plugins.py --module HttpDebuggerModule
```

只更新 Dubbo 请求调试器（例如修复建表字段后）：

```bash
python3 build/package_plugins.py --module DubboServiceTestPlugin
```

单插件构建并运行测试：

```bash
python3 build/package_plugins.py --module DubboServiceTestPlugin --with-tests
```

同时更新多个插件，重复传 `--module`，不要将多个名称合并成一个逗号分隔的参数：

```bash
python3 build/package_plugins.py \
  --module HttpDebuggerModule \
  --module LanMessengerModule \
  --module RedisModule
```

单插件对应的 Maven 命令类似：

```bash
mvn -pl HttpDebuggerModule -am clean package -DskipTests
```

`-pl` 选择模块，`-am` 同时构建所需上游模块，因此 API/Base 也会参与构建；最终部署阶段只复制明确指定的业务插件。`--with-tests` 会运行本次 Reactor 内的测试，包括参与构建的上游依赖，并非只运行目标插件的测试。

常用模块目录名如下；完整列表以根 `pom.xml` 的 `<modules>` 为准：

| 工具 | `--module` 参数 |
| --- | --- |
| HTTP 请求调试器 | `HttpDebuggerModule` |
| Dubbo 请求调试器 | `DubboServiceTestPlugin` |
| Dubbo SSL 工具 | `DubboSslTestModule` |
| REST 工具（不是 HTTP 请求调试器） | `RestTestModule` |
| Redis | `RedisModule` |
| ZooKeeper | `ZookeeperToolModule` |
| Java 反编译器 | `JavaDecompilerModule` |
| 局域网通信 | `LanMessengerModule` |

参数使用模块目录名，区分大小写，不使用 UI 中的中文名称，也不使用 JAR 文件名。

#### 6. 指定输出目录和工程位置

输出到单独的暂存目录，确认后再部署：

```bash
python3 build/package_plugins.py --module HttpDebuggerModule --output ./target/plugin-staging
```

也可输出到主程序实际配置的插件目录，但执行前必须退出主程序。例如使用常见的用户插件目录：

```bash
python3 build/package_plugins.py --module HttpDebuggerModule --output "$HOME/.opencgl/ext-plugin"
```

此处仅是目录示例，实际加载位置以主程序设置为准。默认 `bin/` 不一定是正在运行的主程序所使用的目录，脚本不会同步所有安装位置。

从其它工作目录调用脚本：

```bash
python3 /Users/chancew./Documents/Chance/code/self_code/OpenCGL-Plugin-New/build/package_plugins.py \
  --module HttpDebuggerModule \
  --output /private/tmp/opencgl-plugin-staging
```

默认工程根目录根据脚本位置确定，不根据当前终端位置确定；显式传入的相对 `--output` 则相对于当前终端目录。需要使用另一份工程时，可传 `--root /path/to/OpenCGL-Plugin-New`。

脚本全部参数：

| 参数 | 默认值/含义 |
| --- | --- |
| `--root` | 脚本所在 `build/` 的上级目录 |
| `--output` | 工程根目录下的 `bin/` |
| `--module` | 不传表示全部业务插件；可重复传入 |
| `--with-tests` | 不传时跳过 Maven 测试执行 |
| `--help` | 显示帮助，不构建 |

#### 7. `bin/` 覆盖规则与产物选择

- 部署 API/Base 以外、且在根 POM 中注册的业务模块；API/Base JAR 不作为业务插件复制到 `bin/`。
- 从模块 `target/` 查找 `${finalName}.jar`；未指定 `finalName` 时使用 `${artifactId}-${version}.jar`。
- 忽略 `original-*.jar`、`*-sources.jar`、`*-javadoc.jar`、`*-tests.jar`，不要拿这些文件代替运行时包。
- 同名最终 JAR 会被覆盖；同一 artifact/finalName 前缀的其它匹配 JAR 会被删除。因此该前缀下手工保存的旧包也不能视为备份。
- 其它不匹配的手工 JAR 会保留；单插件打包不会清空整个 `bin/`，也不会统一清理已从 Reactor 移除的旧插件。
- 全量部署逐个文件执行，不是原子操作。若部署中途失败，可能已有部分包更新，需要解决原因后重新运行。

构建成功后应同时检查 Maven 成功信息和脚本的部署列表。仅有 `BUILD SUCCESS` 不足以证明后续文件复制也成功。

#### 8. 什么时候需要提前 install API/Base？

以下情形需要把最新依赖安装到本地 Maven 仓库：

- 不通过根 Reactor，直接进入单个插件目录执行 Maven。
- 另一个工程（例如主程序）需要解析本地最新 API/Base。
- 公共接口或实现改动后，IDE/其它构建仍解析到旧的 `1.0.0` 依赖。

在插件工程根目录先执行：

```bash
mvn -N install -DskipTests
mvn -pl PluginApiModule,OpenCGL-Base -am install -DskipTests
```

第一条安装根父 POM，方便独立工程解析插件的父配置；第二条构建并安装 API/Base 及必要依赖。默认本地仓库是 `~/.m2/repository`，若 Maven settings 自定义了仓库，以该配置为准。

之后可独立构建某个模块：

```bash
mvn -f DubboServiceTestPlugin/pom.xml clean package -DskipTests
```

此方式生成该模块 `target/` 产物，不替代统一部署脚本。日常开发仍推荐根目录 `package_plugins.py --module ...`，减少因忘记 install 而使用旧依赖的问题。

#### 9. 公共组件修改后，主程序是否也需要重打包？

默认 `plugin-mode` 将 API/Base 声明为 `provided`，运行时由主程序提供。插件打包成功，只说明编译时依赖满足，不保证旧主程序具备新方法。

- 只修改插件自身代码，未依赖新的 API/Base：通常更新对应插件即可。
- 修改 Base 的公共树组件、方法签名或实现，并要求运行时使用新版本：需 install 最新 API/Base，重新构建相关插件，并在主程序工程重新构建、部署匹配的主程序。
- 出现 `NoSuchMethodError`，例如 `TreeViewBuilder.treeItemFactory(...)`：优先检查插件编译时与主程序运行时 Base 是否一致。仅重新打包插件通常不能解决旧主程序缺方法的问题。

本脚本不会更新已安装主程序中的公共库，也不会生成主程序安装包。主程序的打包命令请在主程序工程执行并以其 README 为准，不能将本工程的 `package_plugins.py` 当作主程序打包脚本。

根 POM 另有 `stand-alone-mode`，会将 API/Base 纳入编译/运行依赖：

```bash
mvn -pl TemplatePlugin -am clean package -Pstand-alone-mode -DskipTests
```

仅用于确有独立运行需求的模块；是否能独立启动还取决于该模块入口和打包配置。正常安装到 OpenCGL 的插件使用默认模式，不要靠切换此 profile 解决公共库版本不匹配。统一 Python 打包脚本目前没有 profile 参数。

#### 10. 测试、构建脚本校验和发布收集

只运行目标模块及依赖测试，不打包部署：

```bash
mvn -pl DubboServiceTestPlugin -am test
```

运行指定回归测试，并允许上游模块没有同名测试：

```bash
mvn -pl DubboServiceTestPlugin -am \
  -Dtest=DubboWidgetDaoSchemaTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

查看失败详情：目标模块 `target/surefire-reports/`。`-DskipTests` 不能解决测试源码编译错误；不要把跳过测试的构建称为测试通过。

只校验构建/收集脚本的相关 Python 测试，不进行全量 Maven 构建：

```bash
python3 -m unittest discover -s src/test/python -p 'test_collect_plugins.py'
python3 -m unittest discover -s src/test/python -p 'test_reactor_configuration.py'
```

如果已有完整的全量 target 产物，需要收集到独立发布暂存目录并生成清单：

```bash
python3 build/collect_plugins.py \
  --root . \
  --output ./target/plugin-release \
  --manifest ./target/plugin-release-manifest.json
```

注意：`collect_plugins.py` 会先递归删除并重建指定输出目录，必须使用专门的暂存目录，绝不能指向家目录、工作区、业务数据目录或需要保留手工插件的 `bin/`。它要求所有业务模块均有产物，不适合仅构建一个模块后使用。清单记录模块名、artifact、版本、文件名、大小和 SHA-256；普通 `package_plugins.py` 不生成此清单，也不执行这套 JAR 完整性检查。

#### 11. 部署后验收

1. 确认脚本退出成功、目标模块出现在部署列表，目标目录中的 JAR 时间和文件名已更新。
2. 确认主程序实际使用该目录，而不是另一份安装目录或用户覆盖目录。
3. 重启 OpenCGL，打开对应插件，确认没有 SPI 加载或缺方法错误。
4. 测试本次修改对应的功能。例如 Dubbo 建表变更需分别验证新配置目录首次初始化和已有数据库升级；勿为测试删除真实数据库。
5. 若修改过 API/Base，确认主程序也已更新到对应版本。

macOS/Linux 可比较构建产物与部署产物（这里以当前 Dubbo 模块版本为例）：

```bash
cmp DubboServiceTestPlugin/target/DubboServiceTestPlugin-1.0.0.jar bin/DubboServiceTestPlugin-1.0.0.jar
```

没有输出且退出码为 0 表示两文件一致。模块版本或 `finalName` 改动后，使用部署列表中的实际文件名。

#### 12. 常见问题

| 现象 | 检查与处理 |
| --- | --- |
| 找不到 `mvn` | 将 Maven 的 `bin` 加入 PATH；脚本没有 Maven 路径参数 |
| Java 版本规则失败 | 检查 `mvn -version`，设置 JDK 21 的 JAVA_HOME |
| 找不到 API/Base 依赖 | 从根目录使用 `-am` 或统一脚本；独立构建先 install 依赖 |
| `Could not find the selected project` | 检查模块目录名及根 POM 注册情况 |
| 网络/依赖下载失败 | 查看 Maven 最早的下载错误，检查仓库、代理、网络；不要删除整个本地仓库 |
| `Unable to open DISPLAY` | JavaFX 测试缺少显示环境；准备图形环境，或仅打包时使用默认跳过测试模式 |
| 打包成功但界面仍旧 | 检查输出目录是否被主程序使用、是否有旧版本覆盖、是否已完整重启 |
| `NoSuchMethodError` | 检查主程序 API/Base 与插件编译版本是否匹配 |
| 覆盖 JAR 失败 | 退出 OpenCGL，确认目录权限和文件占用后重试 |
| 找不到预期运行时 JAR | 检查模块 assembly/shade 和 finalName 配置，不要随意改名后绕过检查 |

#### 13. 常用命令速查

以下均在已配置好 JDK/Maven 的工程根目录运行：

```bash
# 全量构建并更新 bin（默认不执行测试）
python3 build/package_plugins.py

# 全量测试、构建、更新 bin
python3 build/package_plugins.py --with-tests

# 只更新 HTTP 插件
python3 build/package_plugins.py --module HttpDebuggerModule

# 只测试、打包并更新 Dubbo 插件及其所需构建依赖
python3 build/package_plugins.py --module DubboServiceTestPlugin --with-tests

# 更新供其它工程使用的 API/Base 本地 Maven 依赖
mvn -pl PluginApiModule,OpenCGL-Base -am install -DskipTests
```

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
