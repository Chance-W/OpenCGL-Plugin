package com.opencgl.portscan.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地端口服务 - 获取本机监听端口信息
 */
public class LocalPortService {
    private volatile Process currentProcess;
    
    /**
     * 获取本机监听端口列表
     */
    public List<LocalPortInfo> getLocalPorts() {
        List<LocalPortInfo> ports = new ArrayList<>();
        
        String os = System.getProperty("os.name").toLowerCase();
        
        try {
            if (os.contains("mac") || os.contains("linux")) {
                ports.addAll(getPortsUnix());
            } else if (os.contains("windows")) {
                ports.addAll(getPortsWindows());
            }
        } catch (Exception e) {
            // 忽略错误
        }
        
        return ports;
    }
    
    private List<LocalPortInfo> getPortsUnix() throws Exception {
        List<LocalPortInfo> ports = new ArrayList<>();
        
        // 使用 lsof 命令
        ProcessBuilder pb = new ProcessBuilder("lsof", "-i", "-P", "-n");
        Process process = pb.start();
        currentProcess = process;
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            reader.readLine(); // 跳过标题行
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 9) {
                    String command = parts[0];
                    String pid = parts[1];
                    String user = parts[2];
                    String name = parts[parts.length - 1];
                    
                    // 解析端口
                    if (name.contains("LISTEN") || name.contains("(LISTEN)")) {
                        String portInfo = parts[parts.length - 2];
                        int port = extractPort(portInfo);
                        if (port > 0) {
                            String protocol = portInfo.contains("TCP") ? "TCP" : "UDP";
                            ports.add(new LocalPortInfo(port, protocol, command, pid, user, "LISTEN"));
                        }
                    }
                }
            }
        }
        
        process.waitFor();
        currentProcess = null;
        return ports;
    }
    
    private List<LocalPortInfo> getPortsWindows() throws Exception {
        List<LocalPortInfo> ports = new ArrayList<>();
        
        ProcessBuilder pb = new ProcessBuilder("netstat", "-ano");
        Process process = pb.start();
        currentProcess = process;
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("LISTENING")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 5) {
                        String protocol = parts[0];
                        String localAddr = parts[1];
                        String state = parts[3];
                        String pid = parts[4];
                        
                        int port = extractPort(localAddr);
                        if (port > 0) {
                            ports.add(new LocalPortInfo(port, protocol, "", pid, "", state));
                        }
                    }
                }
            }
        }
        
        process.waitFor();
        currentProcess = null;
        return ports;
    }
    
    private int extractPort(String address) {
        Pattern pattern = Pattern.compile(":(\\d+)$");
        Matcher matcher = pattern.matcher(address);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }

    public void dispose() {
        Process process = currentProcess;
        currentProcess = null;
        if (process != null) {
            process.destroy();
            if (process.isAlive()) process.destroyForcibly();
        }
    }
    
    public record LocalPortInfo(int port, String protocol, String process, String pid, String user, String state) {}
}
