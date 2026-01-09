package com.opencgl.lanmsg.service;

import com.opencgl.lanmsg.model.User;
import com.opencgl.lanmsg.model.User.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * UDP 用户发现服务
 */
public class DiscoveryService {
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryService.class);
    
    private static final int DISCOVERY_PORT = 2425;
    private static final int BUFFER_SIZE = 1024;
    private static final long HEARTBEAT_INTERVAL = 30000; // 30秒
    private static final long OFFLINE_TIMEOUT = 90000;    // 90秒无心跳视为离线
    
    private DatagramSocket socket;
    private final Map<String, User> onlineUsers = new ConcurrentHashMap<>();
    private String myUsername;
    private String myHostname;
    private String myIp;
    private int myPort;
    private boolean running = false;
    
    private ScheduledExecutorService scheduler;
    private ExecutorService receiver;
    
    private Consumer<User> onUserOnline;
    private Consumer<User> onUserOffline;
    private Consumer<User> onUserUpdate;
    
    public DiscoveryService() {
        try {
            this.myHostname = InetAddress.getLocalHost().getHostName();
            this.myIp = getLocalIp();
            this.myUsername = System.getProperty("user.name");
            this.myPort = 2425; // 默认端口
        } catch (Exception e) {
            logger.error("初始化失败", e);
        }
    }
    
    public void setMyUsername(String username) {
        this.myUsername = username;
    }
    
    public void setMyPort(int port) {
        this.myPort = port;
    }
    
    public void setOnUserOnline(Consumer<User> callback) {
        this.onUserOnline = callback;
    }
    
    public void setOnUserOffline(Consumer<User> callback) {
        this.onUserOffline = callback;
    }
    
    public void setOnUserUpdate(Consumer<User> callback) {
        this.onUserUpdate = callback;
    }
    
    public void start() throws SocketException {
        if (running) return;
        
        // Recreate executors if they were shutdown
        if (receiver == null || receiver.isShutdown()) {
            receiver = Executors.newSingleThreadExecutor();
        }
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(2);
        }
        
        // UDP需要绑定到0.0.0.0才能接收广播，不能绑定指定IP
        socket = new DatagramSocket(myPort);
        socket.setBroadcast(true);
        running = true;
        
        // 接收线程
        receiver.submit(this::receiveLoop);
        
        // 心跳和清理线程
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupOfflineUsers, OFFLINE_TIMEOUT, OFFLINE_TIMEOUT / 2, TimeUnit.MILLISECONDS);
        
        // 广播上线
        broadcastOnline();
        
        logger.info("发现服务已启动: {}:{}", myIp, myPort);
    }
    
    public void stop() {
        if (!running) return;
        running = false;
        
        // 广播下线
        broadcastOffline();
        
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (receiver != null) {
            receiver.shutdownNow();
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        
        onlineUsers.clear();
        logger.info("发现服务已停止");
    }
    
    private void receiveLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                String senderIp = packet.getAddress().getHostAddress();
                
                // 忽略自己的消息
                if (senderIp.equals(myIp)) continue;
                
                processMessage(message, senderIp);
            } catch (Exception e) {
                if (running) {
                    logger.error("接收消息失败", e);
                }
            }
        }
    }
    
    private void processMessage(String message, String senderIp) {
        String[] parts = message.split("\\|");
        if (parts.length < 2) return;
        
        String cmd = parts[0];
        switch (cmd) {
            case "ONLINE" -> handleOnline(parts, senderIp);
            case "OFFLINE" -> handleOffline(parts, senderIp);
            case "HEARTBEAT" -> handleHeartbeat(parts, senderIp);
        }
    }
    
    private void handleOnline(String[] parts, String senderIp) {
        // ONLINE|username|hostname|ip|port|status
        if (parts.length < 5) return;
        
        String username = parts[1];
        String hostname = parts[2];
        int port = Integer.parseInt(parts[4]);
        UserStatus status = parts.length > 5 ? UserStatus.valueOf(parts[5]) : UserStatus.ONLINE;
        
        User user = new User(username, hostname, senderIp, port);
        user.setStatus(status);
        
        boolean isNew = !onlineUsers.containsKey(user.getKey());
        onlineUsers.put(user.getKey(), user);
        
        if (isNew && onUserOnline != null) {
            onUserOnline.accept(user);
        }
        
        // 回复自己的上线消息
        sendOnlineResponse(senderIp, port);
    }
    
    private void handleOffline(String[] parts, String senderIp) {
        // OFFLINE|username|ip
        if (parts.length < 2) return;
        
        String key = senderIp + ":" + myPort;
        User user = onlineUsers.remove(key);
        if (user != null && onUserOffline != null) {
            onUserOffline.accept(user);
        }
    }
    
    private void handleHeartbeat(String[] parts, String senderIp) {
        // HEARTBEAT|username|ip
        String key = senderIp + ":" + myPort;
        User user = onlineUsers.get(key);
        if (user != null) {
            user.setLastSeen(LocalDateTime.now());
        }
    }
    
    private void broadcastOnline() {
        String message = String.format("ONLINE|%s|%s|%s|%d|%s",
            myUsername, myHostname, myIp, myPort, UserStatus.ONLINE.name());
        broadcast(message);
    }
    
    private void broadcastOffline() {
        String message = String.format("OFFLINE|%s|%s", myUsername, myIp);
        broadcast(message);
    }
    
    private void sendHeartbeat() {
        String message = String.format("HEARTBEAT|%s|%s", myUsername, myIp);
        broadcast(message);
    }
    
    private void sendOnlineResponse(String targetIp, int targetPort) {
        String message = String.format("ONLINE|%s|%s|%s|%d|%s",
            myUsername, myHostname, myIp, myPort, UserStatus.ONLINE.name());
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length,
                InetAddress.getByName(targetIp), targetPort);
            socket.send(packet);
        } catch (Exception e) {
            logger.error("发送响应失败", e);
        }
    }
    
    private void broadcast(String message) {
        try {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            
            // 默认广播地址
            InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
            
            // 尝试获取基于当前IP的子网广播地址
            try {
                InetAddress localAddr = InetAddress.getByName(myIp);
                NetworkInterface ni = NetworkInterface.getByInetAddress(localAddr);
                if (ni != null) {
                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        if (ia.getAddress() instanceof Inet4Address && ia.getBroadcast() != null) {
                            // 简单的匹配：如果接口地址匹配当前IP，或者这是该接口唯一的IPv4地址
                            if (ia.getAddress().equals(localAddr)) {
                                broadcastAddr = ia.getBroadcast();
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("无法计算子网广播地址，回退到全局广播: {}", e.getMessage());
            }

            logger.debug("Broadcasting to {}: {}", broadcastAddr, message);
            DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddr, myPort);
            socket.send(packet);
        } catch (Exception e) {
            logger.error("广播失败", e);
        }
    }
    
    private void cleanupOfflineUsers() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusSeconds(OFFLINE_TIMEOUT / 1000);
        
        Iterator<Map.Entry<String, User>> it = onlineUsers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, User> entry = it.next();
            User user = entry.getValue();
            if (user.getLastSeen().isBefore(threshold)) {
                it.remove();
                if (onUserOffline != null) {
                    onUserOffline.accept(user);
                }
            }
        }
    }
    
    public Collection<User> getOnlineUsers() {
        return onlineUsers.values();
    }
    
    public void refreshUsers() {
        broadcastOnline();
    }
    
    public String getMyIp() { return myIp; }
    public String getMyUsername() { return myUsername; }
    public int getMyPort() { return myPort; }
    
    private String getLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip;
                        }
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
    
    /**
     * 获取所有可用的本地 IP 地址
     */
    public static List<String> getAllLocalIps() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("获取本地IP列表失败", e);
        }
        if (ips.isEmpty()) {
            ips.add("127.0.0.1");
        }
        return ips;
    }
    
    public void setMyIp(String ip) {
        this.myIp = ip;
    }
}
