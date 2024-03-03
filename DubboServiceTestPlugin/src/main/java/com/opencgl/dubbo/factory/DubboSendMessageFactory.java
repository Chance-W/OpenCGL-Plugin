package com.opencgl.dubbo.factory;


import com.opencgl.base.factory.SendMessageFactory;
import com.opencgl.base.service.SendMessageService;
import com.opencgl.dubbo.impl.DubboSenderImpl;
import com.opencgl.dubbo.model.DubboRequest;
import com.opencgl.dubbo.model.DubboResponse;

/**
 * @author Chance.W
 * @version 9.0
 * @className DubboSendMessageFactory
 * @date 2022/8/12 8:21
 */
@SuppressWarnings("unused")
public class DubboSendMessageFactory extends SendMessageFactory {
    public static SendMessageService<DubboRequest, DubboResponse> sendDubboMessage() {
        return new DubboSenderImpl();
    }
}
