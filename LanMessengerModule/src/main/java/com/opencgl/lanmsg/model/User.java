package com.opencgl.lanmsg.model;

import java.time.LocalDateTime;

/**
 * 用户模型
 */
public class User {
    private String username;
    private String hostname;
    private String ip;
    private int port;
    private UserStatus status;
    private LocalDateTime lastSeen;
    
    public enum UserStatus {
        ONLINE("在线", "🟢"),
        BUSY("忙碌", "🟡"),
        AWAY("离开", "🟠"),
        OFFLINE("离线", "⚪");
        
        private final String label;
        private final String icon;
        
        UserStatus(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }
        
        public String getLabel() { return label; }
        public String getIcon() { return icon; }
    }
    
    public User() {}
    
    public User(String username, String hostname, String ip, int port) {
        this.username = username;
        this.hostname = hostname;
        this.ip = ip;
        this.port = port;
        this.status = UserStatus.ONLINE;
        this.lastSeen = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    
    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }
    
    public String getDisplayName() {
        return status.getIcon() + " " + username + " (" + hostname + ")";
    }
    
    public String getKey() {
        return ip + ":" + port;
    }
    
    @Override
    public String toString() {
        return getDisplayName();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return ip.equals(user.ip) && port == user.port;
    }
    
    @Override
    public int hashCode() {
        return (ip + port).hashCode();
    }
}
