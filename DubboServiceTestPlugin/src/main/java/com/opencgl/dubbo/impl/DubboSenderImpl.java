package com.opencgl.dubbo.impl;

import com.opencgl.base.service.SendMessageService;
import com.opencgl.dubbo.model.DubboRequest;
import com.opencgl.dubbo.model.DubboResponse;
import com.opencgl.dubbo.utils.DubboUtil;


/**
 * @author Chance.W
 */
public class DubboSenderImpl implements SendMessageService<DubboRequest, DubboResponse> {
    @Override
    public DubboResponse send(DubboRequest request) {
        return new DubboUtil(request).sendMessage();
    }
}
