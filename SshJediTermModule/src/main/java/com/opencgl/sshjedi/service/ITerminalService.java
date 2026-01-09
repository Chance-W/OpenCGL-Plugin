package com.opencgl.sshjedi.service;

import com.opencgl.sshjedi.model.SshConnectionDto;
import com.techsenger.jeditermfx.core.TtyConnector;

/**
 * 终端服务基类接口
 */
public interface ITerminalService {
    
    /**
     * 创建终端连接器
     */
    TtyConnector createTtyConnector(SshConnectionDto connectionData) throws Exception;
    
}
