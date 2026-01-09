package com.opencgl.ssh.service;

import com.pty4j.PtyProcessBuilder;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 本机 Shell 终端服务实现类 (基于 Pty4J)
 */
public class LocalShellServiceImpl implements ITerminalService {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalShellServiceImpl.class);
    
    private PtyProcess ptyProcess;
    private Thread readThread;
    private volatile boolean running = false;
    
    /**
     * 连接到指定的本机 Shell
     *
     * @param shellCmd      执行的 Shell 命令（如 "cmd.exe", "/bin/bash", "wsl.exe"）
     * @param outputHandler 接收到终端输出后的回调
     */
    public void connect(String[] shellCmd, Consumer<String> outputHandler) throws Exception {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");
        
        if (logger.isDebugEnabled()) {
            logger.debug("正在启动本地终端进程: {}", String.join(" ", shellCmd));
        }
        
        ptyProcess = new PtyProcessBuilder()
                .setCommand(shellCmd)
                .setDirectory(System.getProperty("user.home"))
                .setEnvironment(env)
                .setRedirectErrorStream(true)
                .start();
                
        running = true;
        readThread = new Thread(() -> {
            try (InputStream in = ptyProcess.getInputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while (running && (len = in.read(buffer)) != -1) {
                    if (len > 0) {
                        outputHandler.accept(new String(buffer, 0, len, StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                if (running) {
                    logger.error("读取本地终端流失败", e);
                }
            } finally {
                disconnect();
            }
        }, "PtyReadThread");
        readThread.setDaemon(true);
        readThread.start();
    }

    @Override
    public void sendBytes(byte[] data) throws Exception {
        if (ptyProcess != null && isConnected()) {
            OutputStream out = ptyProcess.getOutputStream();
            out.write(data);
            out.flush();
        }
    }

    @Override
    public void setPtySize(int cols, int rows) {
        if (ptyProcess != null && isConnected()) {
            ptyProcess.setWinSize(new WinSize(cols, rows));
        }
    }

    @Override
    public synchronized void disconnect() {
        running = false;
        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }
        if (ptyProcess != null) {
            try {
                ptyProcess.destroyForcibly();
            } catch (Exception e) {
                logger.warn("销毁Pty进程失败", e);
            }
            ptyProcess = null;
        }
    }

    @Override
    public boolean isConnected() {
        return ptyProcess != null && ptyProcess.isAlive();
    }
}
