package com.opencgl.base.hook;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 脚本引擎管理器
 * 管理多个脚本引擎，根据文件扩展名选择合适的引擎加载脚本
 *
 * @author Chance.W
 */
public class ScriptEngineManager {

    private static final Logger logger = LoggerFactory.getLogger(ScriptEngineManager.class);

    private final Map<String, ScriptEngine> engines = new HashMap<>();
    private final Map<String, ScriptEngine> extensionMap = new HashMap<>();

    /**
     * 默认脚本目录
     */
    public static final Path DEFAULT_HOOKS_DIR = Paths.get(
        System.getProperty("user.home"), ".opencgl", "hooks"
    );

    public ScriptEngineManager() {
        // 确保 hooks 目录存在并复制默认脚本
        ensureHooksDirectory();
        // 尝试注册 Groovy 引擎
        tryRegisterEngine("com.opencgl.base.hook.script.GroovyScriptEngine");
        // 尝试注册 JavaScript 引擎
        tryRegisterEngine("com.opencgl.base.hook.script.JavaScriptEngine");
    }

    /**
     * 确保 hooks 目录存在，并复制默认脚本
     */
    private void ensureHooksDirectory() {
        try {
            if (!Files.exists(DEFAULT_HOOKS_DIR)) {
                Files.createDirectories(DEFAULT_HOOKS_DIR);
                logger.info("已创建 hooks 目录: {}", DEFAULT_HOOKS_DIR);
            }
            // 复制默认脚本
            copyDefaultScriptsIfNeeded();
        }
        catch (Exception e) {
            logger.warn("创建 hooks 目录失败", e);
        }
    }

    /**
     * 从 classpath 复制默认脚本到用户目录
     */
    private void copyDefaultScriptsIfNeeded() {
        String[] defaultScripts = {
            "add-timestamp.groovy",
            "json-format.js",
        };

        for (String scriptName : defaultScripts) {
            Path targetPath = DEFAULT_HOOKS_DIR.resolve(scriptName);
            if (!Files.exists(targetPath)) {
                try (var is = getClass().getResourceAsStream("/hooks/" + scriptName)) {
                    if (is != null) {
                        Files.copy(is, targetPath);
                        logger.info("已复制默认脚本: {}", scriptName);
                    }
                }
                catch (Exception e) {
                    logger.warn("复制默认脚本失败: {}", scriptName, e);
                }
            }
        }
    }

    private void tryRegisterEngine(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            ScriptEngine engine = (ScriptEngine) clazz.getDeclaredConstructor().newInstance();
            if (engine.isAvailable()) {
                registerEngine(engine);
                logger.info("已注册脚本引擎: {} ({})", engine.getType(), className);
            }
            else {
                logger.warn("脚本引擎不可用 (缺少依赖): {}", engine.getType());
            }
        }
        catch (ClassNotFoundException e) {
            logger.debug("脚本引擎类不存在: {}", className);
        }
        catch (Exception e) {
            logger.warn("注册脚本引擎失败: {}", className, e);
        }
    }

    /**
     * 注册脚本引擎
     */
    public void registerEngine(ScriptEngine engine) {
        engines.put(engine.getType(), engine);
        for (String ext : engine.getExtensions()) {
            extensionMap.put(ext.toLowerCase(), engine);
        }
    }

    /**
     * 从文件加载脚本
     */
    public RequestHook loadScript(Path path) throws Exception {
        String ext = getExtension(path);
        ScriptEngine engine = extensionMap.get(ext.toLowerCase());
        if (engine == null) {
            throw new IllegalArgumentException("不支持的脚本类型: " + ext);
        }
        return engine.loadFromFile(path);
    }

    /**
     * 列出可用脚本
     */
    public List<String> listAvailableScripts() {
        return listAvailableScripts(DEFAULT_HOOKS_DIR);
    }

    /**
     * 列出指定目录下的可用脚本
     */
    public List<String> listAvailableScripts(Path directory) {
        if (!Files.exists(directory)) {
            return new ArrayList<>();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files
                .filter(Files::isRegularFile)
                .filter(p -> isSupportedExtension(getExtension(p)))
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        }
        catch (Exception e) {
            logger.error("列出脚本失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取所有可用引擎
     */
    public List<ScriptEngine> getAvailableEngines() {
        return new ArrayList<>(engines.values());
    }

    private boolean isSupportedExtension(String ext) {
        return extensionMap.containsKey(ext.toLowerCase());
    }

    private String getExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot) : "";
    }
}
