package com.opencgl.template4;

import com.opencgl.template4.i18n.I18N;
import com.opencgl.api.PluginUI;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 【重型依赖隔绝方案演练模版】
 * 此卡片用于演示如何在不改变底层 ClassLoader （避免 ClassCastException 和 TCCL 泄漏）的情况下，
 * 采用 maven-shade-plugin 将包含 SPI 机制的霸道框架（以 Zookeeper 为例）完全阴影化封装。
 *
 * <p>
 * 使用该技术后，这里的代码哪怕直接引用 org.apache.zookeeper.ZooKeeper，
 * 等到打出 Jar 包的时候，编译产物中的字节码会自动被替换为:
 * com.opencgl.template4.shaded.apache.zookeeper.ZooKeeper
 * 从而实现了最完美彻底的物理级防弹隔离。
 * </p>
 */
public class TemplatePlugin4Demo implements PluginUI {

    private VBox rootPane;
    private TextArea logArea;
    private TextArea infoLabel;
    private Button btnOriginal;
    private Button btnShaded;
    private Button btnSlfOriginal;
    private Button btnSlfShaded;
    private Button btnClear;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "opencgl-template-probe");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean disposed;

    private void startTask(Runnable task) {
        if (!disposed) executor.submit(task);
    }

    @Override
    public String name() {
        return I18N.get("label.name");
    }

    @Override
    public String directoryName() {
        return I18N.get("label.category");
    }

    @Override
    public String description() {
        return I18N.get("label.description");
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public URL iconPath() {
        // 使用内置的图片，或者你可以自行放置 resources
        return null;
    }

    @Override
    public Object createView() {
        if (rootPane == null) {
            rootPane = new VBox(15);
            rootPane.setPadding(new Insets(20));
            rootPane.setAlignment(Pos.TOP_LEFT);
            rootPane.getStyleClass().add("plugin-root-pane");

            infoLabel = new TextArea();
            infoLabel.setWrapText(true);
            infoLabel.setEditable(false);
            infoLabel.setPrefHeight(120);

            HBox buttonBox1 = new HBox(10);
            btnOriginal = new Button();
            btnShaded = new Button();

            HBox buttonBox2 = new HBox(10);
            btnSlfOriginal = new Button();
            btnSlfShaded = new Button();
            btnClear = new Button();

            btnOriginal.setOnAction(e -> testOriginalClass());
            btnShaded.setOnAction(e -> testShadedClass());
            btnSlfOriginal.setOnAction(e -> testOriginalSlf4j());
            btnSlfShaded.setOnAction(e -> testShadedSlf4j());
            btnClear.setOnAction(e -> logArea.clear());

            buttonBox1.getChildren().addAll(btnOriginal, btnShaded);
            buttonBox2.getChildren().addAll(btnSlfOriginal, btnSlfShaded, btnClear);

            logArea = new TextArea();
            logArea.setEditable(false);
            logArea.setPrefHeight(300);

            rootPane.getChildren().addAll(infoLabel, buttonBox1, buttonBox2, logArea);
            initI18n();
        }
        return rootPane;
    }

    private void initI18n() {
        if (infoLabel != null) {
            infoLabel.textProperty().bind(
                    I18N.getBinding("label.info_header").concat("\n\n").concat(I18N.getBinding("label.info_body")));
        }
        if (btnOriginal != null) {
            btnOriginal.textProperty().bind(I18N.getBinding("btn.probe_zk_global"));
            Tooltip t = new Tooltip();
            t.textProperty().bind(I18N.getBinding("tooltip.probe_zk_global"));
            btnOriginal.setTooltip(t);
        }
        if (btnShaded != null) {
            btnShaded.textProperty().bind(I18N.getBinding("btn.probe_zk_shaded"));
            Tooltip t = new Tooltip();
            t.textProperty().bind(I18N.getBinding("tooltip.probe_zk_shaded"));
            btnShaded.setTooltip(t);
        }
        if (btnSlfOriginal != null) {
            btnSlfOriginal.textProperty().bind(I18N.getBinding("btn.probe_slf4j_global"));
            Tooltip t = new Tooltip();
            t.textProperty().bind(I18N.getBinding("tooltip.probe_slf4j_global"));
            btnSlfOriginal.setTooltip(t);
        }
        if (btnSlfShaded != null) {
            btnSlfShaded.textProperty().bind(I18N.getBinding("btn.probe_slf4j_shaded"));
            Tooltip t = new Tooltip();
            t.textProperty().bind(I18N.getBinding("tooltip.probe_slf4j_shaded"));
            btnSlfShaded.setTooltip(t);
        }
        if (btnClear != null) {
            btnClear.textProperty().bind(I18N.getBinding("btn.clear_logs"));
            Tooltip t = new Tooltip();
            t.textProperty().bind(I18N.getBinding("tooltip.clear_logs"));
            btnClear.setTooltip(t);
        }
    }

    private void testOriginalClass() {
        appendLog("\n==================================");
        String zkName = new String(new char[] { 'o', 'r', 'g', '.', 'a', 'p', 'a', 'c', 'h', 'e', '.', 'z', 'o', 'o',
                'k', 'e', 'e', 'p', 'e', 'r', '.', 'Z', 'o', 'o', 'K', 'e', 'e', 'p', 'e', 'r' });
        appendLog(I18N.get("log.probing_original", zkName));
        startTask(() -> {
            try {
                // 为了彻底防止 javac 编译期常量折叠以及 Maven Shade 打包期的字节码文本替换
                // 我们必须用非连续字符拼接或动态生成的手段来组合出这串名字
                ClassLoader cl = TemplatePlugin4Demo.class.getClassLoader();
                Class<?> zkClass = Class.forName(zkName, false, cl);

                // 如果能找到，说明是宿主(或IDE环境)里本身就存在 Zookeeper，由于双亲委派机制被透传过来了！
                String location = zkClass.getProtectionDomain().getCodeSource().getLocation().toString();
                appendLog(I18N.get("log.warn_found", "Zookeeper", location));
            } catch (ClassNotFoundException e) {
                appendLog(I18N.get("log.ok_intercepted", "org.apache.zookeeper"));
            } catch (Throwable t) {
                appendLog(I18N.get("log.error_probe", t.toString()));
            }
        });
    }

    private void testShadedClass() {
        appendLog("\n==================================");
        String shadedName = "com.opencgl.template4.shaded.apache.zookeeper.ZooKeeper";
        appendLog(I18N.get("log.probing_shaded", shadedName));
        startTask(() -> {
            try {
                ClassLoader cl = TemplatePlugin4Demo.class.getClassLoader();
                Class<?> shadedClass = Class.forName(shadedName, false, cl);

                String location = shadedClass.getProtectionDomain().getCodeSource().getLocation().toString();
                appendLog(I18N.get("log.success_verify", shadedClass.getName()));
                appendLog(I18N.get("log.jar_location", location));
                appendLog(I18N.get("log.security_line", "Zookeeper"));
            } catch (Throwable ex) {
                appendLog(I18N.get("log.fail_shaded", ex.toString()));
            }
        });
    }

    private void testOriginalSlf4j() {
        appendLog("\n==================================");
        String slfName = new String(new char[] { 'o', 'r', 'g', '.', 's', 'l', 'f', '4', 'j', '.', 'L', 'o', 'g', 'g',
                'e', 'r', 'F', 'a', 'c', 't', 'o', 'r', 'y' });
        appendLog(I18N.get("log.probing_original", slfName));
        startTask(() -> {
            try {
                ClassLoader cl = TemplatePlugin4Demo.class.getClassLoader();
                Class<?> slf4jClass = Class.forName(slfName, false, cl);

                String location = slf4jClass.getProtectionDomain().getCodeSource().getLocation().toString();
                appendLog(I18N.get("log.warn_found", "SLF4J", location));
            } catch (ClassNotFoundException e) {
                appendLog(I18N.get("log.ok_intercepted", "org.slf4j"));
            } catch (Throwable t) {
                appendLog(I18N.get("log.error_probe", t.toString()));
            }
        });
    }

    private void testShadedSlf4j() {
        appendLog("\n==================================");
        String shadedName = "com.opencgl.template4.shaded.org.slf4j.LoggerFactory";
        appendLog(I18N.get("log.probing_shaded", shadedName));
        startTask(() -> {
            try {
                ClassLoader cl = TemplatePlugin4Demo.class.getClassLoader();
                Class<?> shadedClass = Class.forName(shadedName, false, cl);

                String location = shadedClass.getProtectionDomain().getCodeSource().getLocation().toString();
                appendLog(I18N.get("log.slf4j_instance", shadedClass.getName()));
                appendLog(I18N.get("log.jar_location", location));
                appendLog(I18N.get("log.slf4j_exclusive"));
            } catch (Throwable ex) {
                appendLog(I18N.get("log.fail_shaded", ex.toString()));
            }
        });
    }

    private void appendLog(String message) {
        Platform.runLater(() -> {
            if (!disposed && logArea != null) logArea.appendText(message + "\n");
        });
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        executor.shutdownNow();
        if (btnOriginal != null) btnOriginal.setOnAction(null);
        if (btnShaded != null) btnShaded.setOnAction(null);
        if (btnSlfOriginal != null) btnSlfOriginal.setOnAction(null);
        if (btnSlfShaded != null) btnSlfShaded.setOnAction(null);
        if (btnClear != null) btnClear.setOnAction(null);
        rootPane = null;
        logArea = null;
    }
}
