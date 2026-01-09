package com.opencgl.sshjedi.model;

import com.opencgl.base.model.BaseDataDto;

/**
 * SSH连接信息DTO
 * 目录节点存储: NAME
 * 叶子节点存储: HOST, PORT, USERNAME, PASSWORD, PRIVATE_KEY_PATH
 * 
 * @author Chance.W
 */
public class SshConnectionDto extends BaseDataDto {

    /** 主机地址 */
    private String host;
    
    /** 端口 */
    private Integer port;
    
    /** 用户名 */
    private String username;
    
    /** 密码（加密存储） */
    private String password;
    
    /** 私钥路径 */
    private String privateKeyPath;

    // ========== Getters and Setters ==========

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    @Override
    public String toString() {
        return "SshConnectionDto{" +
            "name='" + getName() + '\'' +
            ", host='" + host + '\'' +
            ", port=" + port +
            ", username='" + username + '\'' +
            ", privateKeyPath='" + privateKeyPath + '\'' +
            '}';
    }
}
