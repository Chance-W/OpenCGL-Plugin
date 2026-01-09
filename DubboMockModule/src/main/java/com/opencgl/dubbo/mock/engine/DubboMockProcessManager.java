package com.opencgl.dubbo.mock.engine;

import com.alibaba.fastjson.JSON;
import com.opencgl.base.view.CustomInfoDialog;
import com.opencgl.dubbo.mock.model.RegistryNodeModel;
import com.opencgl.dubbo.mock.model.ProviderNodeModel;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 宿主端驱动：负责管理独立 JVM 子进程（DubboMockEngineMain）的启停及数据通信。
 */
public class DubboMockProcessManager {

    private static final Logger log = LoggerFactory.getLogger(DubboMockProcessManager.class);
    private static final DubboMockProcessManager INSTANCE = new DubboMockProcessManager();
    
    // 缓存每个 Registry 对应的专属独立进程
    private final Map<String, ProcessHandle> runningProcesses = new ConcurrentHashMap<>();
    private volatile boolean disposed;

    private DubboMockProcessManager() {}

    public static DubboMockProcessManager getInstance() {
        return INSTANCE;
    }

    private static class ProcessHandle {
        Process process;
        BufferedWriter writer;
        Thread loggerThread;
    }

    /**
     * 在独立子进程中拉起或更新 Mock 服务。
     * 自动提取当前 Java 运行环境路径，挂载相同 classpath 保证依赖透传。
     *
     * @param registry 完整的注册中心树数据
     * @param logListener 接受子进程反馈的 UI 回调
     */
    public synchronized void startOrUpdateMock(RegistryNodeModel registry, java.util.function.Consumer<String> logListener) {
        disposed = false;
        String registryId = registry.getId();
        ProcessHandle handle = runningProcesses.get(registryId);
        
        // 构建要发送给子进程的 JSON 配置
        String configJson = JSON.toJSONString(registry);

        if (handle != null && handle.process.isAlive()) {
            // 进程仍然活着，通过管道发送更新的新配置
            log.info("Mock 引擎子进程 {} 仍在线，发送热更新 JSON...", registryId);
            try {
                handle.writer.write(configJson);
                handle.writer.newLine();
                handle.writer.flush();
                return;
            } catch (Exception e) {
                log.error("管道通信中断，尝试强制重启子进程...", e);
                stopMock(registryId);
            }
        }

        // 需要启动新进程
        log.info("正在为 Registry [{}] 拉起隔离 Mock 引擎子进程...", registry.getName());
        try {
            String javaPath = System.getProperty("java.home") + "/bin/java";
            // 判断是否在 Windows 环境下
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                javaPath += ".exe";
            }
            
            // 彻底切断宿主环境（System.getProperty("java.class.path")）！！！
            // 宿主可能加载了老旧的 Dubbo、不同的 SPI 配置，传递给子进程会导致同名类和 SPI 服务严重冲突。
            // 真正的容器化隔离：仅将自身（即插件构建出的 Fat Jar 或当前 classes 目录）作为唯一类路径。
            String classPath;
            java.net.URL location = DubboMockEngineMain.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                classPath = new java.io.File(location.toURI()).getAbsolutePath();
            } else {
                throw new IllegalStateException("未能获取到独立 Mock 引擎物理执行路径，拉起失败");
            }
            
            ProcessBuilder pb = new ProcessBuilder(
                javaPath,
                "-Djava.net.preferIPv4Stack=true",
                "-cp", classPath,
                DubboMockEngineMain.class.getName()
            );

            // 让子进程把错误流合并到标准输出，方便统一抓取
            pb.redirectErrorStream(true);

            Process process = pb.start();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            // 发送首个配置包
            writer.write(configJson);
            writer.newLine();
            writer.flush();

            ProcessHandle newHandle = new ProcessHandle();
            newHandle.process = process;
            newHandle.writer = writer;
            
            // 启动异步线程不断读取子进程标准输出
            newHandle.loggerThread = new Thread(() -> {
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        final String outLog = line;
                        // 这里我们简单定义协议，如果返回的包含 MOCK-CALL-LOG 代表有实际请求到达
                        if (outLog.contains("[MOCK-CALL-LOG]")) {
                            if (logListener != null) {
                                Platform.runLater(() -> { if (!disposed) logListener.accept(outLog); });
                            }
                        } else if (outLog.contains("[MOCK-ENGINE-STATUS] ERROR")) {
                            Platform.runLater(() -> {
                                if (disposed) return;
                                CustomInfoDialog dialog = new CustomInfoDialog();
                                dialog.setLabelText("Mock引擎启动失败: " + outLog.replace("[MOCK-ENGINE-STATUS] ERROR:", ""));
                                dialog.showAndWait();
                            });
                        } else {
                            // 普通系统日志
                            log.debug("{}", outLog);
                        }
                    }
                } catch (Exception e) {
                    log.error("子进程输出流读取中断: {}", e.getMessage());
                } finally {
                    log.info("子进程 {} 日志监听线程安全退出", registryId);
                    runningProcesses.remove(registryId);
                }
            });
            newHandle.loggerThread.setDaemon(true);
            newHandle.loggerThread.start();

            runningProcesses.put(registryId, newHandle);
            
            // 标记相关 Provider 为运行中
            registry.getProviders().forEach(p -> p.setRunning(true));

        } catch (Exception e) {
            log.error("拉起子进程失败: {}", e.getMessage(), e);
            Platform.runLater(() -> {
                if (disposed) return;
                CustomInfoDialog dialog = new CustomInfoDialog();
                dialog.setLabelText("无法唤起隔离容器引擎: " + e.getMessage());
                dialog.showAndWait();
            });
        }
    }

    /**
     * 暴力销毁指定 Registry 绑定的 Mock 引擎进程
     */
    public synchronized void stopMock(String registryId) {
        ProcessHandle handle = runningProcesses.remove(registryId);
        if (handle != null) {
            log.info("下达 SHUTDOWN 停机信令并准备强制杀死子进程: {}", registryId);
            try {
                // 尝试优雅关闭
                if (handle.writer != null) {
                    handle.writer.write("SHUTDOWN");
                    handle.writer.newLine();
                    handle.writer.flush();
                }
            } catch (Exception ignore) {}
            
            // 强行休眠片刻后施加物理清理
            try { Thread.sleep(500); } catch (InterruptedException ignore) {}
            
            if (handle.process != null && handle.process.isAlive()) {
                handle.process.destroyForcibly();
                log.info("子进程已被 process.destroyForcibly() 彻底抹去。");
            }
            if (handle.loggerThread != null) {
                handle.loggerThread.interrupt();
                try { handle.loggerThread.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            try { if (handle.writer != null) handle.writer.close(); } catch (Exception e) { log.warn("关闭 Mock 进程输入流失败", e); }
        }
    }

    public synchronized void stopAll() {
        disposed = true;
        for (String registryId : new java.util.ArrayList<>(runningProcesses.keySet())) {
            try { stopMock(registryId); } catch (RuntimeException e) { log.error("停止 Mock 子进程失败: {}", registryId, e); }
        }
    }
}
