package com.opencgl.lanmsg.service;

import com.opencgl.lanmsg.model.FileTransfer;
import com.opencgl.lanmsg.model.FileTransfer.TransferDirection;
import com.opencgl.lanmsg.model.FileTransfer.TransferStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 文件传输服务
 */
public class FileTransferService {
    private static final Logger logger = LoggerFactory.getLogger(FileTransferService.class);
    
    private static final int BUFFER_SIZE = 8192;
    
    private ExecutorService executor;
    private final ConcurrentHashMap<String, FileTransfer> transfers = new ConcurrentHashMap<>();
    
    private Consumer<FileTransfer> onTransferRequest;
    private BiConsumer<FileTransfer, Double> onProgressUpdate;
    private Consumer<FileTransfer> onTransferComplete;
    
    public FileTransferService() {
        this.executor = Executors.newCachedThreadPool();
    }
    
    public void setOnTransferRequest(Consumer<FileTransfer> callback) {
        this.onTransferRequest = callback;
    }
    
    public void setOnProgressUpdate(BiConsumer<FileTransfer, Double> callback) {
        this.onProgressUpdate = callback;
    }
    
    public void setOnTransferComplete(Consumer<FileTransfer> callback) {
        this.onTransferComplete = callback;
    }
    
    public void start() {
        // Recreate executor if it was shutdown
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newCachedThreadPool();
        }
        logger.info("文件传输服务已启动");
    }
    
    public void stop() {
        executor.shutdownNow();
        transfers.clear();
        logger.info("文件传输服务已停止");
    }
    
    /**
     * 发送文件
     */
    public FileTransfer sendFile(File file, String toUser, String toIp, String fromUser, String fromIp) {
        FileTransfer transfer = new FileTransfer(file, fromUser, fromIp, toUser, toIp);
        transfer.setDirection(TransferDirection.SEND);
        transfers.put(transfer.getId(), transfer);
        
        executor.submit(() -> doSendFile(transfer, toIp));
        
        return transfer;
    }
    
    private MessageService messageService;

    public void setMessageService(MessageService messageService) {
        this.messageService = messageService;
    }

    private void doSendFile(FileTransfer transfer, String targetIp) {
        try (ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getByName(transfer.getFromIp()))) {
            transfer.setPort(serverSocket.getLocalPort());
            transfer.setStatus(TransferStatus.PENDING);
            
            // 发送传输请求: FILE_REQ|filename|size|sender_port|transferId
            if (messageService != null) {
                String req = String.format("FILE_REQ|%s|%d|%d|%s", 
                    transfer.getFileName(), transfer.getFileSize(), transfer.getPort(), transfer.getId());
                messageService.sendRawMessage(targetIp, req);
            } else {
                logger.warn("MessageService not set, cannot send file request.");
            }
            
            serverSocket.setSoTimeout(60000); // 60秒超时等待对方连接
            Socket socket = serverSocket.accept();
            
            transfer.setStatus(TransferStatus.TRANSFERRING);
            
            try (InputStream fis = new FileInputStream(transfer.getFile());
                 OutputStream os = socket.getOutputStream()) {
                
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalSent = 0;
                
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                    totalSent += bytesRead;
                    transfer.setTransferredSize(totalSent);
                    
                    if (onProgressUpdate != null) {
                        onProgressUpdate.accept(transfer, transfer.getProgress());
                    }
                }
                
                transfer.setStatus(TransferStatus.COMPLETED);
                if (onTransferComplete != null) {
                    onTransferComplete.accept(transfer);
                }
            }
        } catch (Exception e) {
            logger.error("发送文件失败", e);
            transfer.setStatus(TransferStatus.FAILED);
        }
    }
    
    /**
     * 接收文件
     */
    public void receiveFile(FileTransfer transfer, File saveLocation) {
        transfer.setFile(saveLocation);
        transfer.setDirection(TransferDirection.RECEIVE);
        transfers.put(transfer.getId(), transfer);
        
        executor.submit(() -> doReceiveFile(transfer));
    }
    
    private void doReceiveFile(FileTransfer transfer) {
        Socket socket = null;
        try {
            socket = new Socket();
            // 如果配置了本地IP，绑定到该IP的随机端口
            String myIp = transfer.getToIp();
            if (myIp != null && !myIp.isEmpty()) {
                socket.bind(new InetSocketAddress(InetAddress.getByName(myIp), 0));
            }
            socket.connect(new InetSocketAddress(transfer.getFromIp(), transfer.getPort()), 5000); // 5秒连接超时
            
            try (InputStream is = socket.getInputStream();
                 OutputStream fos = new FileOutputStream(transfer.getFile())) {
                
                transfer.setStatus(TransferStatus.TRANSFERRING);
                
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalReceived = 0;
                
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalReceived += bytesRead;
                    transfer.setTransferredSize(totalReceived);
                    
                    if (onProgressUpdate != null) {
                        onProgressUpdate.accept(transfer, transfer.getProgress());
                    }
                }
                
                transfer.setStatus(TransferStatus.COMPLETED);
                if (onTransferComplete != null) {
                    onTransferComplete.accept(transfer);
                }
            }
        } catch (Exception e) {
            logger.error("接收文件失败", e);
            transfer.setStatus(TransferStatus.FAILED);
            if (onProgressUpdate != null) {
                // 通知失败状态
                onProgressUpdate.accept(transfer, 0.0);
            }
        } finally {
            if (socket != null && !socket.isClosed()) {
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }
    
    public void cancelTransfer(String transferId) {
        FileTransfer transfer = transfers.get(transferId);
        if (transfer != null) {
            transfer.setStatus(TransferStatus.CANCELLED);
        }
    }
    
    public FileTransfer getTransfer(String id) {
        return transfers.get(id);
    }
}
