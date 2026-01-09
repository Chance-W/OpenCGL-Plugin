package com.opencgl.sshjedi.service;

import com.opencgl.sshjedi.model.SshConnectionDto;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.PtyProcess;
import com.techsenger.jeditermfx.core.TtyConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 本机 Shell 终端服务实现类 (基于 Pty4J)
 */
public class LocalShellServiceImpl implements ITerminalService {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalShellServiceImpl.class);
    
    @Override
    public TtyConnector createTtyConnector(SshConnectionDto connectionData) throws Exception {
        String shellName = connectionData.getHost() != null ? connectionData.getHost() : "cmd.exe";
        if (shellName.startsWith("LOCAL:")) {
            shellName = shellName.substring(6);
        }
        
        String[] cmd = new String[]{shellName};
        if (shellName.endsWith("bash") || shellName.endsWith("zsh")) {
            cmd = new String[]{shellName, "-l"};
        }
        
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");
        
        if (logger.isDebugEnabled()) {
            logger.debug("正在启动本地终端进程: {}", String.join(" ", cmd));
        }
        
        PtyProcess ptyProcess = new PtyProcessBuilder()
                .setCommand(cmd)
                .setDirectory(System.getProperty("user.home"))
                .setEnvironment(env)
                .setRedirectErrorStream(true)
                .start();
                
        return new LocalPtyTtyConnector(ptyProcess);
    }
}
