package com.opencgl.lanmsg.model;

import java.io.File;
import java.util.UUID;

/**
 * 文件传输模型
 */
public class FileTransfer {
    private String id;
    private File file;
    private String fileName;
    private long fileSize;
    private long transferredSize;
    private String fromUser;
    private String fromIp;
    private String toUser;
    private String toIp;
    private int port;
    private TransferStatus status;
    private TransferDirection direction;
    
    public enum TransferStatus {
        PENDING("等待中"),
        TRANSFERRING("传输中"),
        COMPLETED("已完成"),
        FAILED("失败"),
        CANCELLED("已取消");
        
        private final String label;
        TransferStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    
    public enum TransferDirection {
        SEND, RECEIVE
    }
    
    public FileTransfer() {
        this.id = UUID.randomUUID().toString();
        this.status = TransferStatus.PENDING;
        this.transferredSize = 0;
    }
    
    public FileTransfer(File file, String fromUser, String fromIp, String toUser, String toIp) {
        this();
        this.file = file;
        this.fileName = file.getName();
        this.fileSize = file.length();
        this.fromUser = fromUser;
        this.fromIp = fromIp;
        this.toUser = toUser;
        this.toIp = toIp;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public File getFile() { return file; }
    public void setFile(File file) { this.file = file; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    
    public long getTransferredSize() { return transferredSize; }
    public void setTransferredSize(long transferredSize) { this.transferredSize = transferredSize; }
    
    public String getFromUser() { return fromUser; }
    public void setFromUser(String fromUser) { this.fromUser = fromUser; }
    
    public String getFromIp() { return fromIp; }
    public void setFromIp(String fromIp) { this.fromIp = fromIp; }
    
    public String getToUser() { return toUser; }
    public void setToUser(String toUser) { this.toUser = toUser; }
    
    public String getToIp() { return toIp; }
    public void setToIp(String toIp) { this.toIp = toIp; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public TransferStatus getStatus() { return status; }
    public void setStatus(TransferStatus status) { this.status = status; }
    
    public TransferDirection getDirection() { return direction; }
    public void setDirection(TransferDirection direction) { this.direction = direction; }
    
    public double getProgress() {
        if (fileSize == 0) return 0;
        return (double) transferredSize / fileSize;
    }
    
    public String getFormattedSize() {
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        if (fileSize < 1024 * 1024 * 1024) return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
    }
}
