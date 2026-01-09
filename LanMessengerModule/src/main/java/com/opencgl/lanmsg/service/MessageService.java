package com.opencgl.lanmsg.service;

import com.opencgl.lanmsg.model.Message;
import com.opencgl.lanmsg.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * TCP 消息服务
 */
public class MessageService {
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    
    private static final int MESSAGE_PORT = 2426;
    
    private ServerSocket serverSocket;
    private boolean running = false;
    private ExecutorService executor;
    
    private String myUsername;
    private String myIp;
    
    private Consumer<Message> onMessageReceived;
    
    public MessageService() {
        this.executor = Executors.newCachedThreadPool();
    }
    
    public void setMyInfo(String username, String ip) {
        this.myUsername = username;
        this.myIp = ip;
    }
    
    public void setOnMessageReceived(Consumer<Message> callback) {
        this.onMessageReceived = callback;
    }
    
    public void start() throws IOException {
        if (running) return;
        
        // Recreate executor if it was shutdown
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newCachedThreadPool();
        }
        
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        // 绑定到指定 IP
        if (myIp != null && !myIp.isEmpty()) {
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName(myIp), MESSAGE_PORT));
        } else {
            serverSocket.bind(new InetSocketAddress(MESSAGE_PORT));
        }
        running = true;
        
        executor.submit(this::acceptLoop);
        
        logger.info("消息服务已启动: {}", MESSAGE_PORT);
    }
    
    public void stop() {
        if (!running) return;
        running = false;
        
        // 先关闭 socket，阻止新连接
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.error("关闭服务器失败", e);
            }
        }
        
        // 再关闭线程池
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    logger.warn("线程池未能在规定时间内关闭");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("消息服务已停止");
    }
    
    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket));
            } catch (Exception e) {
                if (running) {
                    logger.error("接受连接失败", e);
                }
            }
        }
    }
    
    private void handleClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                processMessage(line, socket.getInetAddress().getHostAddress());
            }
        } catch (Exception e) {
            logger.error("处理客户端消息失败", e);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
    
    private void processMessage(String data, String senderIp) {
        // Simple protocol: TYPE|...args
        String[] parts = data.split("\\|", 5);
        if (parts.length < 2) return;
        
        String type = parts[0];
        
        if ("MSG".equals(type) && parts.length >= 5) {
            Message msg = new Message();
            msg.setFrom(parts[1]);
            msg.setFromIp(senderIp);
            msg.setTo(parts[2]);
            msg.setToIp(myIp);
            msg.setContent(parts[4]);
            
            if (onMessageReceived != null) {
                onMessageReceived.accept(msg);
            }
        } else if (type.startsWith("FILE_")) {
             // Let listeners handle raw message or specialized file service handle it
             // For now, reuse onMessageReceived but wrap as a system message or similar?
             // Or better, let's allow a separate listener for protocol messages, 
             // but to keep it simple, we treat it as a special Message or just handle it here if we had a reference to FileTransferService
             
             // Actually, simplest is to package it as a Message with special content prefix
             Message msg = new Message();
             msg.setFrom("System"); // Placeholder
             msg.setFromIp(senderIp);
             msg.setTo(myUsername);
             msg.setToIp(myIp);
             msg.setContent(data); // Pass full raw data
             
             if (onMessageReceived != null) {
                 onMessageReceived.accept(msg);
             }
        }
    }
    
    /**
     * 发送普通文本消息
     */
    public void sendMessage(User target, String content) {
        String data = String.format("MSG|%s|%s|%d|%s",
            myUsername, target.getUsername(), System.currentTimeMillis(), content);
        sendRawMessage(target.getIp(), data);
    }
    
    /**
     * 发送原始协议消息
     */
    /**
     * 发送原始协议消息。
     * 不绑定本地 IP，由系统自动选择出口网卡，避免 Windows 多网卡/虚拟网卡下绑定错误导致发送失败。
     */
    public void sendRawMessage(String targetIp, String rawData) {
        executor.submit(() -> {
            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(targetIp, MESSAGE_PORT), 5000);
                try (PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                    writer.println(rawData);
                }
            } catch (Exception e) {
                logger.error("发送消息失败: {} -> {}:{} - {}", myIp, targetIp, MESSAGE_PORT, e.getMessage(), e);
            } finally {
                if (socket != null && !socket.isClosed()) {
                    try { socket.close(); } catch (IOException e) {}
                }
            }
        });
    }
    
    public int getPort() {
        return MESSAGE_PORT;
    }
}
