package com.opencgl.sshjedi.service;

import com.jcraft.jsch.*;
import com.opencgl.sshjedi.model.SshConnectionDto;
import com.techsenger.jeditermfx.core.TtyConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * SSH连接服务
 */
public class SshService implements ITerminalService {

    private static final Logger logger = LoggerFactory.getLogger(SshService.class);

    @Override
    public TtyConnector createTtyConnector(SshConnectionDto connectionData) throws Exception {
        String host = connectionData.getHost();
        String user = connectionData.getUsername();
        int port = connectionData.getPort() != null ? connectionData.getPort() : 22;
        String password = connectionData.getPassword();
        String privateKeyPath = connectionData.getPrivateKeyPath();

        JSch jsch = new JSch();

        // Key authentication if provided
        if (privateKeyPath != null && !privateKeyPath.trim().isEmpty()) {
            if (password != null && !password.isEmpty()) {
                jsch.addIdentity(privateKeyPath, password); // passphrase in 'password'
            } else {
                jsch.addIdentity(privateKeyPath);
            }
            logger.info("Connecting via key-based auth: {}@{}:{}", user, host, port);
        } else {
            logger.info("Connecting via password auth: {}@{}:{}", user, host, port);
        }

        Session session = jsch.getSession(user, host, port);
        if (privateKeyPath == null || privateKeyPath.trim().isEmpty()) {
            session.setPassword(password);
        }

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.setTimeout(30000);
        session.connect();

        ChannelShell channel = (ChannelShell) session.openChannel("shell");
        channel.setPtyType("xterm-256color");
        channel.setPtySize(80, 24, 640, 480);
        
        java.io.InputStream inputStream = channel.getInputStream();
        java.io.OutputStream outputStream = channel.getOutputStream();
        
        channel.connect();

        return new JschTtyConnector(session, channel, inputStream, outputStream);
    }
}
