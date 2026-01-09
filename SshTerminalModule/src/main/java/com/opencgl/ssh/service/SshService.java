package com.opencgl.ssh.service;

import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * SSH连接服务
 */
public class SshService implements ITerminalService {

    private static final Logger logger = LoggerFactory.getLogger(SshService.class);
    
    private Session session;
    private ChannelShell channel;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread readerThread;
    private volatile boolean running = false;

    /**
     * 连接SSH服务器
     */
    public void connect(String host, int port, String username, String password, 
                       Consumer<String> outputHandler) throws JSchException, IOException {
        JSch jsch = new JSch();
        
        session = jsch.getSession(username, host, port);
        session.setPassword(password);
        
        // 跳过主机密钥验证（生产环境应改为严格验证）
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        
        // 设置超时
        session.setTimeout(30000);
        session.connect();
        
        // 打开Shell通道
        channel = (ChannelShell) session.openChannel("shell");
        channel.setPtyType("xterm-256color");
        channel.setPtySize(80, 24, 640, 480); // 默认大小，后续会更新
        
        inputStream = channel.getInputStream();
        outputStream = channel.getOutputStream();
        
        channel.connect();
        
        // 启动读取线程
        running = true;
        readerThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            try {
                while (running && channel.isConnected()) {
                    int available = inputStream.available();
                    if (available > 0) {
                        int len = inputStream.read(buffer, 0, Math.min(available, buffer.length));
                        if (len > 0) {
                            String output = new String(buffer, 0, len, "UTF-8");
                            outputHandler.accept(output);
                        }
                    }
                    Thread.sleep(10);
                }
            } catch (Exception e) {
                if (running) {
                    logger.error("读取SSH输出失败", e);
                    outputHandler.accept("\r\n" + com.opencgl.ssh.i18n.I18N.getOrDefault("term.disconnected", "[已断开连接]") + "\r\n");
                }
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
        
        logger.info("SSH连接成功: {}@{}:{}", username, host, port);
    }
    
    /**
     * 设置终端大小（用于vi等全屏应用）
     */
    public void setPtySize(int cols, int rows) {
        if (channel != null && channel.isConnected()) {
            channel.setPtySize(cols, rows, cols * 8, rows * 16);
            logger.debug("设置PTY大小: {}x{}", cols, rows);
        }
    }

    /**
     * 使用密钥文件连接
     */
    public void connectWithKey(String host, int port, String username, String privateKeyPath,
                               String passphrase, Consumer<String> outputHandler) throws JSchException, IOException {
        JSch jsch = new JSch();
        
        if (passphrase != null && !passphrase.isEmpty()) {
            jsch.addIdentity(privateKeyPath, passphrase);
        } else {
            jsch.addIdentity(privateKeyPath);
        }
        
        session = jsch.getSession(username, host, port);
        
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        
        session.setTimeout(30000);
        session.connect();
        
        channel = (ChannelShell) session.openChannel("shell");
        channel.setPtyType("xterm-256color");
        channel.setPtySize(80, 24, 640, 480); // 提供和基础密码连接一致的默认起始回退大小
        
        inputStream = channel.getInputStream();
        outputStream = channel.getOutputStream();
        
        channel.connect();
        
        running = true;
        readerThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            try {
                while (running && channel.isConnected()) {
                    int len = inputStream.read(buffer);
                    if (len > 0) {
                        String output = new String(buffer, 0, len, "UTF-8");
                        outputHandler.accept(output);
                    }
                    Thread.sleep(50);
                }
            } catch (Exception e) {
                if (running) {
                    logger.error("读取SSH输出失败", e);
                    outputHandler.accept("\r\n" + com.opencgl.ssh.i18n.I18N.getOrDefault("term.disconnected", "[已断开连接]") + "\r\n");
                }
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
        
        logger.info("SSH密钥连接成功: {}@{}:{}", username, host, port);
    }

    /**
     * 发送命令
     */
    public void sendCommand(String command) throws IOException {
        if (outputStream != null && channel != null && channel.isConnected()) {
            outputStream.write((command + "\n").getBytes("UTF-8"));
            outputStream.flush();
        }
    }

    /**
     * 发送字符（用于交互式输入）
     */
    public void sendChar(char c) throws IOException {
        if (outputStream != null && channel != null && channel.isConnected()) {
            outputStream.write(c);
            outputStream.flush();
        }
    }

    /**
     * 发送特殊键
     */
    public void sendSpecialKey(String key) throws IOException {
        if (outputStream != null && channel != null && channel.isConnected()) {
            byte[] bytes = null;
            switch (key) {
                case "CTRL_C": bytes = new byte[]{3}; break;
                case "CTRL_D": bytes = new byte[]{4}; break;
                case "CTRL_Z": bytes = new byte[]{26}; break;
                case "TAB": bytes = new byte[]{9}; break;
                case "ENTER": bytes = new byte[]{'\r'}; break;
                case "UP": bytes = new byte[]{27, 91, 65}; break;
                case "DOWN": bytes = new byte[]{27, 91, 66}; break;
                case "RIGHT": bytes = new byte[]{27, 91, 67}; break;
                case "LEFT": bytes = new byte[]{27, 91, 68}; break;
                case "BACKSPACE": bytes = new byte[]{127}; break;
            }
            if (bytes != null) {
                outputStream.write(bytes);
                outputStream.flush();
            }
        }
    }

    /**
     * 发送原始字节序列（用于功能键、组合键等）
     */
    public void sendBytes(byte[] bytes) throws IOException {
        if (outputStream != null && channel != null && channel.isConnected() && bytes != null) {
            outputStream.write(bytes);
            outputStream.flush();
        }
    }

    /**
     * 断开连接
     */
    public synchronized void disconnect() {
        running = false;

        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }

        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (Exception e) {
            logger.warn("关闭输入流异常", e);
        } finally {
            inputStream = null;
        }

        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (Exception e) {
            logger.warn("关闭输出流异常", e);
        } finally {
            outputStream = null;
        }
        
        try {
            if (channel != null) {
                channel.disconnect();
            }
        } catch (Exception e) {
            logger.warn("关闭通道异常", e);
        } finally {
            channel = null;
        }
        
        try {
            if (session != null) {
                session.disconnect();
            }
        } catch (Exception e) {
            logger.warn("关闭会话异常", e);
        } finally {
            session = null;
        }
        
        logger.info("SSH连接已断开");
    }

    /**
     * 检查是否连接
     */
    public boolean isConnected() {
        return session != null && session.isConnected() && channel != null && channel.isConnected();
    }
}
