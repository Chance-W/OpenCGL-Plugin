package com.opencgl.impl;

import com.alibaba.rocketmq.client.producer.DefaultMQProducer;
import com.alibaba.rocketmq.client.producer.SendResult;
import com.alibaba.rocketmq.common.message.Message;

import com.opencgl.base.service.SendMessageService;
import com.opencgl.base.utils.FormatVariableUtil;
import com.opencgl.model.RmqRequest;
import com.opencgl.model.RmqResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;


/**
 * @author Chance.W
 */
public class RmqSender implements SendMessageService<RmqRequest, RmqResponse> {
    private final Logger logger = LoggerFactory.getLogger(RmqSender.class);

    @Override
    public RmqResponse send(RmqRequest request) {
        RmqResponse response = new RmqResponse();
        StringBuilder sb = new StringBuilder();
        DefaultMQProducer producer = new DefaultMQProducer("producer1");
        if (request.getNameTopic().contains("%")) {
            producer = new DefaultMQProducer(request.getNameTopic().split("%")[0] + "%producer1");
        }
        try {
            //设置NameServer地址,此处应改为实际NameServer地址，多个地址之间用；分隔
            //NameServer的地址必须有，但是也可以通过环境变量的方式设置，不一定非得写死在代码里
            //producer.setVipChannelEnabled(false);//3.2.6的版本没有该设置，在更新或者最新的版本中务必将其设置为false，否则会有问题
            //调用start()方法启动一个producer实例
            producer.setNamesrvAddr(request.getNameServerAddr().trim());
            producer.start();
            for (int i = 0; i < request.getCount(); i++) {
                Message msg = new Message(request.getNameTopic().trim(),
                    request.getTags().trim(),
                    (request.getRequestMessage()).getBytes(StandardCharsets.UTF_8));
                msg.setKeys(FormatVariableUtil.format("${YYYYMMDDHHMMSS}${Random10}"));
                SendResult sendResult = producer.send(msg);
                sb.append("第").append(i + 1).append("次发送结果 ");
                sb.append(sendResult.getSendStatus());
                sb.append("\n");
                sb.append(sendResult);
                sb.append("\n");
            }
            response.setResultMsg(sb.toString());

        }
        catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw, true));
            logger.error("", e);
            response.setResultMsg(sw.toString());
        }
        finally {
            producer.shutdown();
        }
        return response;
    }
}
