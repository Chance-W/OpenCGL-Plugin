package com.opencgl.base.theme;

import com.opencgl.base.listener.Config;
import com.opencgl.base.model.OpenCGLSelfProperties;
import com.opencgl.base.utils.CssFileWatcher;
import com.opencgl.base.utils.CssUtil;
import com.opencgl.api.ThemeAware;
import com.opencgl.api.ThemeInfo;
import io.github.palexdev.materialfx.theming.JavaFXThemes;
import io.github.palexdev.materialfx.theming.MaterialFXStylesheets;
import io.github.palexdev.materialfx.theming.UserAgentBuilder;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import lombok.Getter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 全局主题管理器 (OpenCGL-Base)
 * 负责主题的切换、多窗口同步应用和插件通知
 * 
 * @author Antigravity (Refactored)
 */
public class ThemeManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);
    
    @Getter
    public enum Theme {
        DEFAULT("默认主题", "default", "#079B8C", "#ffffff", "rgba(0,0,0,0.87)", false),
        DARK("深色主题", "dark", "#252526", "#1e1e1e", "#e0e0e0", true),
        PURPLE("紫色主题", "purple", "#7a0ed9", "#faf8ff", "#333333", false),
        BLUE("蓝色主题", "blue", "#1976d2", "#ffffff", "#333333", false),
        OCEAN("海蓝色主题", "ocean", "#0288d1", "#e8f4f8", "rgba(0,0,0,0.87)", false);
        
        private final String displayName;
        private final String cssName;
        private final String primaryColor;
        private final String backgroundColor;
        private final String textColor;
        private final boolean isDark;
        
        Theme(String displayName, String cssName, String primaryColor, 
              String backgroundColor, String textColor, boolean isDark) {
            this.displayName = displayName;
            this.cssName = cssName;
            this.primaryColor = primaryColor;
            this.backgroundColor = backgroundColor;
            this.textColor = textColor;
            this.isDark = isDark;
        }

        public String getDisplayName() { return displayName; }
        public String getCssName() { return cssName; }
        public String getPrimaryColor() { return primaryColor; }
        public String getBackgroundColor() { return backgroundColor; }
        public String getTextColor() { return textColor; }
        public boolean isDark() { return isDark; }

        public ThemeInfo toThemeInfo() {
            return new ThemeInfo(cssName, primaryColor, backgroundColor, textColor, isDark);
        }
        
        public static Theme fromCssName(String name) {
            if (name == null || name.isEmpty()) return DEFAULT;
            for (Theme t : values()) {
                if (t.cssName.equals(name)) return t;
            }
            return DEFAULT;
        }
        
        public static Theme fromDisplayName(String name) {
            if (name == null || name.isEmpty()) return DEFAULT;
            for (Theme t : values()) {
                if (t.displayName.equals(name)) return t;
            }
            return DEFAULT;
        }
    }

    private static final ThemeManager INSTANCE = new ThemeManager();
    private Theme currentTheme = Theme.DEFAULT;
    private ThemePreference currentPreference = new ThemePreference(ThemeMode.LIGHT, AccentColor.TEAL);
    
    // 使用线程安全的列表存储所有注册的 Scene，支持多窗口
    private final WeakReferenceRegistry<Scene> scenes = new WeakReferenceRegistry<>();
    private final List<ThemeAware> themeAwarePlugins = new CopyOnWriteArrayList<>();
    private final SystemThemeDetector systemThemeDetector = new SystemThemeDetector();

    // 存储全局需要加载的额外样式表路径 (例如组件特有的 CSS)
    private final List<String> globalStylesheetPaths = new CopyOnWriteArrayList<>();

    private ThemeManager() {}

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    /**
     * 注册全局样式表
     * 注册后的样式表会自动应用到所有受管 Scene，并支持 Dev Mode 热重载
     * 
     * @param resourcePath CSS 文件在 classpath 中的绝对路径 (例如 /com/xxx/style.css)
     */
    public void addGlobalStylesheet(String resourcePath) {
        if (resourcePath != null && !resourcePath.isEmpty() && !globalStylesheetPaths.contains(resourcePath)) {
            globalStylesheetPaths.add(resourcePath);
            // 如果已在运行中，立即刷新一次以应用新样式
            if (!scenes.isEmpty()) {
                reloadCurrentTheme();
            }
        }
    }

    /**
     * 初始化全局样式 (UserAgent)
     * 建议在主程序启动时调用
     */
    public void init() {
        try {
            UserAgentBuilder.builder()
                .themes(JavaFXThemes.MODENA)
                .themes(MaterialFXStylesheets.forAssemble(true))
                .setDeploy(true)
                .setResolveAssets(true)
                .build()
                .setGlobal();
            logger.info("Global UserAgent initialized with MaterialFX");

            loadSavedTheme();
            systemThemeDetector.listen(dark -> {
                if (currentPreference.mode() == ThemeMode.SYSTEM) reloadCurrentTheme();
            });
            
            // 启动 Dev Mode 文件监听器
            if (CssUtil.isDevMode()) {
                String devPath = System.getProperty(CssUtil.KEY_DEV_CSS_PATH);
                if (devPath != null && !devPath.isEmpty()) {
                    new CssFileWatcher(devPath).start();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to initialize ThemeManager", e);
        }
    }


    public void init(String themeName) {
        try {
            UserAgentBuilder.builder()
                .themes(JavaFXThemes.MODENA)
                .themes(MaterialFXStylesheets.forAssemble(true))
                .setDeploy(true)
                .setResolveAssets(true)
                .build()
                .setGlobal();
            logger.info("Global UserAgent initialized with MaterialFX");

            loadSavedTheme(themeName);

            // 启动 Dev Mode 文件监听器
            if (CssUtil.isDevMode()) {
                String devPath = System.getProperty(CssUtil.KEY_DEV_CSS_PATH);
                if (devPath != null && !devPath.isEmpty()) {
                    new CssFileWatcher(devPath).start();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to initialize ThemeManager", e);
        }
    }

    /**
     * 注册 Scene，使其参与全局主题切换
     */
    public void registerScene(Scene scene) {
        if (scene != null) {
            scenes.add(scene);
            applyThemeToScene(scene);
            logger.debug("Scene registered for global theming");
        }
    }

    public void unregisterScene(Scene scene) {
        scenes.remove(scene);
    }

    public void registerThemeAware(ThemeAware plugin) {
        if (plugin != null && !themeAwarePlugins.contains(plugin)) {
            themeAwarePlugins.add(plugin);
            plugin.onThemeChanged(currentTheme.toThemeInfo());
        }
    }

    public void unregisterThemeAware(ThemeAware plugin) {
        themeAwarePlugins.remove(plugin);
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public ThemePreference getCurrentPreference() {
        return currentPreference;
    }

    public void switchTheme(Theme theme) {
        if (theme == null) return;
        ThemePreference preference = ThemePreference.parse(theme.getCssName(), null);
        switchTheme(preference.mode(), preference.accent());
    }

    public void switchTheme(ThemeMode mode, AccentColor accent) {
        ThemePreference next = new ThemePreference(mode, accent);
        if (next.equals(currentPreference)) return;

        currentPreference = next;
        currentTheme = legacyThemeFor(next);
        logger.info("Switching global theme to mode={}, accent={}", mode, accent);

        // 1. 应用到所有已注册的 Scene
        for (Scene scene : scenes.liveValues()) {
            applyThemeToScene(scene);
        }

        // 2. 通知插件
        ThemeInfo info = currentTheme.toThemeInfo();
        for (ThemeAware plugin : themeAwarePlugins) {
            try {
                plugin.onThemeChanged(info);
            } catch (Exception e) {
                logger.warn("Failed to notify plugin: {}", e.getMessage());
            }
        }

        // 3. 持久化
        saveThemePreference(next);
    }

    private void applyThemeToScene(Scene scene) {
        if (scene == null) return;
        
        Runnable apply = () -> {
            ObservableList<String> sheets = scene.getStylesheets();
            
            // 移除旧的主题相关样式表 (包括基础颜色、主题颜色和所有注册的全局样式)
            sheets.removeIf(s -> {
                if (s.contains("/css/themes/ThemeColors-") || s.contains("/css/themes/ThemeTokens-")
                    || s.contains("/css/themes/Accent-") || s.contains("/css/MFXColors.css")
                    || s.contains("/css/GlobalComponents.css")) return true;
                for (String path : globalStylesheetPaths) {
                    if (s.contains(path)) return true;
                }
                return false;
            });
            
            try {
                // 基础颜色变量 (MFXColors.css)
                String baseCSS = getResourcePath("/com/opencgl/base/css/MFXColors.css");
                ThemeMode resolvedMode = resolveMode();
                String tokensCSS = getResourcePath("/com/opencgl/base/css/themes/ThemeTokens-" + resolvedMode.name().toLowerCase() + ".css");
                String themeCSS = getResourcePath("/com/opencgl/base/css/themes/ThemeColors-" + (resolvedMode == ThemeMode.DARK ? "dark" : "default") + ".css");
                String accentCSS = getResourcePath("/com/opencgl/base/css/themes/Accent-" + currentPreference.accent().cssName() + ".css");
                // 全局组件样式 (GlobalComponents.css)
                String globalComponentsCSS = getResourcePath("/com/opencgl/base/css/GlobalComponents.css");
                
                // 确保基础变量在前面
                if (baseCSS != null && !sheets.contains(baseCSS)) sheets.add(0, baseCSS);
                if (tokensCSS != null && !sheets.contains(tokensCSS)) sheets.add(1, tokensCSS);
                if (themeCSS != null && !sheets.contains(themeCSS)) sheets.add(2, themeCSS);
                if (accentCSS != null && !sheets.contains(accentCSS)) sheets.add(3, accentCSS);
                
                // 加载全局组件样式 (必须在变量之后)
                if (globalComponentsCSS != null && !sheets.contains(globalComponentsCSS)) {
                    // 插入到变量文件之后，但在其他动态样式之前
                    // 也可以直接 add，但在 globalStylesheetPaths 之前比较好，允许 globalStylesheetPaths 覆盖它
                    int insertIndex = 4;
                    if (sheets.size() >= insertIndex) {
                        sheets.add(insertIndex, globalComponentsCSS);
                    } else {
                        sheets.add(globalComponentsCSS);
                    }
                }

                // 动态加载所有注册的额外样式
                for (String path : globalStylesheetPaths) {
                    String url = getResourcePath(path);
                    if (url != null) {
                         // 因为上面已经 removeIf 清理过了，这里直接添加即可
                         // 如果是 Dev Mode，URL 带新时间戳，肯定不重复
                         // 如果是 Prod Mode，URL 不变，contains 检查作为双重保险 (虽然 removeIf 应该已经移除了)
                         if (!sheets.contains(url)) {
                             sheets.add(url);
                         }
                    }
                }
                
                logger.debug("Theme mode={}, accent={} applied to scene", resolvedMode, currentPreference.accent());
            } catch (Exception e) {
                logger.error("Failed to apply theme CSS", e);
            }
        };
        if (Platform.isFxApplicationThread()) {
            apply.run();
        } else {
            Platform.runLater(apply);
        }
    }

    private String getResourcePath(String path) {
        return CssUtil.getResourcePath(path);
    }

    /**
     * 强制重载当前主题
     * 在开发模式下修改 CSS 后，调用此方法即可立即看到效果
     */
    public void reloadCurrentTheme() {
        if (currentTheme == null) return;
        logger.info("Reloading current theme: {}", currentTheme.getDisplayName());
        
        // 重新应用到所有 Scene
        for (Scene scene : scenes.liveValues()) {
            applyThemeToScene(scene);
        }
        
        // 通知插件刷新
        ThemeInfo info = currentTheme.toThemeInfo();
        for (ThemeAware plugin : themeAwarePlugins) {
            try {
                plugin.onThemeChanged(info);
            } catch (Exception e) {
                logger.warn("Failed to notify plugin during reload: {}", e.getMessage());
            }
        }
    }

    public void loadSavedTheme() {
        String savedMode = Config.readExternalConfigure(OpenCGLSelfProperties.THEME_MODE_KEY);
        String savedAccent = Config.readExternalConfigure(OpenCGLSelfProperties.THEME_ACCENT_KEY);
        String legacy = Config.readExternalConfigure(OpenCGLSelfProperties.THEME_KEY);
        currentPreference = ThemePreference.parse(savedMode == null ? legacy : savedMode, savedAccent);
        currentTheme = legacyThemeFor(currentPreference);
        if (savedMode == null || savedAccent == null) saveThemePreference(currentPreference);
        // 注意：这里不直接调用 switchTheme 以免触发还没注册的 Scene
    }

    public void loadSavedTheme(String themeName) {
        currentPreference = ThemePreference.parse(themeName, null);
        currentTheme = legacyThemeFor(currentPreference);
        // 注意：这里不直接调用 switchTheme 以免触发还没注册的 Scene
    }

    private void saveThemePreference(ThemePreference preference) {
        Config.updateSingleConfig(OpenCGLSelfProperties.THEME_MODE_KEY, preference.mode().name());
        Config.updateSingleConfig(OpenCGLSelfProperties.THEME_ACCENT_KEY, preference.accent().name());
    }

    private ThemeMode resolveMode() {
        return currentPreference.resolvedMode(systemThemeDetector.isDark());
    }

    private Theme legacyThemeFor(ThemePreference preference) {
        if (preference.resolvedMode(false) == ThemeMode.DARK) return Theme.DARK;
        return switch (preference.accent()) {
            case BLUE -> Theme.BLUE;
            case PURPLE -> Theme.PURPLE;
            case OCEAN -> Theme.OCEAN;
            case TEAL -> Theme.DEFAULT;
        };
    }

    /**
     * 获取当前主题的样式表路径列表
     * 用于由 Popup 等无法自动继承 Scene 样式的组件手动应用
     */
    public List<String> getCurrentThemeStylesheets() {
        List<String> sheets = new ArrayList<>();
        String baseCSS = getResourcePath("/com/opencgl/base/css/MFXColors.css");
        ThemeMode resolvedMode = resolveMode();
        String tokensCSS = getResourcePath("/com/opencgl/base/css/themes/ThemeTokens-" + resolvedMode.name().toLowerCase() + ".css");
        String themeCSS = getResourcePath("/com/opencgl/base/css/themes/ThemeColors-" + (resolvedMode == ThemeMode.DARK ? "dark" : "default") + ".css");
        String accentCSS = getResourcePath("/com/opencgl/base/css/themes/Accent-" + currentPreference.accent().cssName() + ".css");
        String globalComponentsCSS = getResourcePath("/com/opencgl/base/css/GlobalComponents.css");
        
        if (baseCSS != null) sheets.add(baseCSS);
        if (tokensCSS != null) sheets.add(tokensCSS);
        if (themeCSS != null) sheets.add(themeCSS);
        if (accentCSS != null) sheets.add(accentCSS);
        if (globalComponentsCSS != null) sheets.add(globalComponentsCSS);
        
        for (String path : globalStylesheetPaths) {
            String url = getResourcePath(path);
            if (url != null) sheets.add(url);
        }
        
        return sheets;
    }

    /** Stops the operating-system appearance listener during application shutdown. */
    public void shutdown() {
        systemThemeDetector.close();
    }
}
