package com.opencgl.dubbo.mock.engine;

import com.alibaba.fastjson.JSON;
import com.opencgl.dubbo.mock.model.RegistryNodeModel;
import com.opencgl.dubbo.mock.service.DubboMockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 独立 JVM 容器化运行的 Dubbo Mock 引擎主入口。
 * 接收来自宿主的标准输入 (JSON 配置)，启动 Dubbo Provider 服务，
 * 并将日志和状态反馈输出到标准输出由宿主读取。
 */
public class DubboMockEngineMain {

    private static final Logger log = LoggerFactory.getLogger(DubboMockEngineMain.class);
    private static DubboMockService runningService = null;

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        // 诊断输出：确认运行的 JAR 和新代码版本
        System.out.println("[MOCK-ENGINE-DIAG] 子进程启动，JAR路径=" + DubboMockEngineMain.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("[MOCK-ENGINE-DIAG] preferIPv4Stack=" + System.getProperty("java.net.preferIPv4Stack"));
        log.info("[MOCK-ENGINE] 独立容器进程已启动，等待宿主下发配置...");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if ("SHUTDOWN".equalsIgnoreCase(line)) {
                    log.info("[MOCK-ENGINE] 收到关闭信号，正在停止服务...");
                    if (runningService != null) {
                        try {
                            runningService.stopProvider(runningService.getCurrentRegistryNode());
                        } catch (Exception e) {
                            log.error("停止服务时出错", e);
                        }
                    }
                    System.exit(0);
                }

                if (line.startsWith("{") && line.endsWith("}")) {
                    log.info("[MOCK-ENGINE] 收到完整 Registry JSON 配置，开始解析并启动服务");
                    try {
                        RegistryNodeModel registryNode = JSON.parseObject(line, RegistryNodeModel.class);
                        
                        // 先停止可能已在运行的服务
                        if (runningService != null) {
                            runningService.stopProvider(runningService.getCurrentRegistryNode());
                            runningService = null;
                        }

                        // 启动新服务
                        runningService = new DubboMockService();
                        // 覆盖 log listener，使得 Mock 的调用日志可以直接以特殊前缀打到标准输出，由宿主拦截
                        registryNode.getProviders().forEach(p -> {
                            p.setLogListener(msg -> System.out.println("[MOCK-CALL-LOG] " + msg));
                        });
                        
                        runningService.startProvider(registryNode, null); // Engine 模式下，直接在 startProvider 内部遍历所有 Provider
                        System.out.println("[MOCK-ENGINE-STATUS] SUCCESS");
                    } catch (Exception e) {
                        log.error("[MOCK-ENGINE] 启动配置解析或暴露失败", e);
                        String msg = e.getMessage();
                        Throwable cause = e.getCause();
                        while (cause != null) {
                            msg += " | Caused by: " + cause.toString();
                            cause = cause.getCause();
                        }
                        System.out.println("[MOCK-ENGINE-STATUS] ERROR: " + msg);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[MOCK-ENGINE] 读写异常", e);
            System.exit(-1);
        }
    }
}
