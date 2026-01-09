package com.opencgl.lanmsg.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 消息模型
 */
public class Message {
    private String from;
    private String fromIp;
    private String to;
    private String toIp;
    private String content;
    private LocalDateTime timestamp;
    private MessageType type;
    
    public enum MessageType {
        TEXT,       // 普通文本
        FILE,       // 文件传输请求
        FILE_ACK,   // 文件传输响应
        SYSTEM      // 系统消息
    }
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    
    public Message() {
        this.timestamp = LocalDateTime.now();
        this.type = MessageType.TEXT;
    }
    
    public Message(String from, String fromIp, String to, String toIp, String content) {
        this();
        this.from = from;
        this.fromIp = fromIp;
        this.to = to;
        this.toIp = toIp;
        this.content = content;
    }
    
    // Getters and Setters
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    
    public String getFromIp() { return fromIp; }
    public void setFromIp(String fromIp) { this.fromIp = fromIp; }
    
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    
    public String getToIp() { return toIp; }
    public void setToIp(String toIp) { this.toIp = toIp; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    
    public String getFormattedTime() {
        return timestamp.format(TIME_FORMAT);
    }
    
    public String getDisplayText(boolean isSelf) {
        String sender = isSelf ? "我" : from;
        return "[" + getFormattedTime() + "] " + sender + ": " + content;
    }
}
