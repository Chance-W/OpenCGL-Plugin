package com.opencgl.mml.impl;


import com.opencgl.base.service.SendMessageService;
import com.opencgl.mml.model.MmlRequest;
import com.opencgl.mml.model.MmlResponse;


import com.opencgl.mml.utils.MMLACK;
import com.opencgl.mml.utils.MMLClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;


/**
 * @author Chance.W
 */
public class MmlSender implements SendMessageService<MmlRequest, MmlResponse> {
    private final Logger logger = LoggerFactory.getLogger(MmlSender.class);
    private final Properties p = new Properties();
    private final MMLClient client = new MMLClient();

    @Override
    public MmlResponse send(MmlRequest request) {
        MmlResponse response = new MmlResponse();
        try {
            p.setProperty("username", request.getRequestUsername());
            p.setProperty("password", request.getRequestPassword());
            p.setProperty("hostname", request.getRequestIp());
            p.setProperty("port", request.getRequestPort());
            p.setProperty("timeOut", "10000");
            p.setProperty("idleTime", "120");
            p.setProperty("servicename", "OpenCGL");
            client.init(p);
            client.start();
            if (client.checkState()) {
                MMLACK mmlack = client.sendCmd(request.getRequestMessage());
                logger.info("response message is" + mmlack.getOrignal());
                response.setResultMsg(mmlack.getOrignal());
            } else {
                response.setResultMsg("connect mml server faild");
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw, true));
            logger.error("", e);
            response.setResultMsg(sw.toString());
        } finally {
            try {
                if (client.checkState()) {
                    logger.info("close mml conenct...");
                    client.stop();
                    client.destory();
                }
            } catch (Exception e) {
                logger.error("", e);
            }
        }
        return response;
    }
}
