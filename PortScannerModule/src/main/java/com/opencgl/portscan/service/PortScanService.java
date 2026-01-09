package com.opencgl.portscan.service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 端口扫描服务
 */
public class PortScanService {
    
    private static final int CONNECT_TIMEOUT = 1000; // 1秒
    private ExecutorService executor;
    private final ExecutorService coordinatorExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "port-scan-coordinator");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean scanning = false;
    
    /**
     * 测试单个端口连通性
     */
    public PortTestResult testPort(String host, int port) {
        long startTime = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
            long latency = System.currentTimeMillis() - startTime;
            return new PortTestResult(host, port, true, latency, "连接成功");
        } catch (IOException e) {
            long latency = System.currentTimeMillis() - startTime;
            return new PortTestResult(host, port, false, latency, e.getMessage());
        }
    }
    
    /**
     * Ping 主机
     */
    public PingResult ping(String host, int count) {
        List<Long> latencies = new ArrayList<>();
        int successCount = 0;
        
        for (int i = 0; i < count; i++) {
            try {
                long startTime = System.currentTimeMillis();
                InetAddress address = InetAddress.getByName(host);
                boolean reachable = address.isReachable(3000);
                long latency = System.currentTimeMillis() - startTime;
                
                if (reachable) {
                    successCount++;
                    latencies.add(latency);
                }
            } catch (IOException e) {
                // 忽略单次失败
            }
        }
        
        double avgLatency = latencies.isEmpty() ? 0 : 
            latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        double packetLoss = (count - successCount) * 100.0 / count;
        
        return new PingResult(host, count, successCount, avgLatency, packetLoss);
    }
    
    /**
     * 扫描端口范围
     */
    public void scanPorts(String host, int startPort, int endPort, 
                          Consumer<ScanProgress> progressCallback,
                          Consumer<PortTestResult> resultCallback,
                          Runnable onComplete) {
        scanning = true;
        executor = Executors.newFixedThreadPool(50);
        
        coordinatorExecutor.execute(() -> {
            int totalPorts = endPort - startPort + 1;
            int scannedPorts = 0;
            
            List<Future<PortTestResult>> futures = new ArrayList<>();
            
            for (int port = startPort; port <= endPort && scanning; port++) {
                final int p = port;
                futures.add(executor.submit(() -> testPort(host, p)));
            }
            
            for (Future<PortTestResult> future : futures) {
                if (!scanning) break;
                try {
                    PortTestResult result = future.get(CONNECT_TIMEOUT + 500, TimeUnit.MILLISECONDS);
                    if (result.isOpen()) {
                        resultCallback.accept(result);
                    }
                } catch (Exception e) {
                    // 忽略超时
                }
                scannedPorts++;
                progressCallback.accept(new ScanProgress(scannedPorts, totalPorts));
            }
            
            scanning = false;
            executor.shutdown();
            onComplete.run();
        });
    }
    
    /**
     * 停止扫描
     */
    public void stopScan() {
        scanning = false;
        if (executor != null) {
            executor.shutdownNow();
        }
    }
    
    public boolean isScanning() {
        return scanning;
    }

    public void dispose() {
        stopScan();
        coordinatorExecutor.shutdownNow();
    }
    
    // 结果类
    public record PortTestResult(String host, int port, boolean open, long latency, String message) {
        public boolean isOpen() { return open; }
    }
    
    public record PingResult(String host, int count, int success, double avgLatency, double packetLoss) {}
    
    public record ScanProgress(int scanned, int total) {
        public double getProgress() { return (double) scanned / total; }
    }
}
