package com.opencgl.ssh.service;

import java.util.function.Consumer;

/**
 * 终端服务基类接口
 * 实现类可以是远程的 SshTerminalImpl 或本机的 LocalShellServiceImpl
 */
public interface ITerminalService {
    
    /**
     * 发送字节数据到终端流
     */
    void sendBytes(byte[] data) throws Exception;
    
    /**
     * 设置伪终端大小
     */
    void setPtySize(int cols, int rows);
    
    /**
     * 断开连接/销毁终端
     */
    void disconnect();
    
    /**
     * 是否处于连接状态
     */
    boolean isConnected();
}
