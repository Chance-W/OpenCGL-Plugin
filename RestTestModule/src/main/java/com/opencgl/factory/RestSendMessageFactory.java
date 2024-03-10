package com.opencgl.factory;


import com.opencgl.base.factory.SendMessageFactory;
import com.opencgl.base.service.SendMessageService;
import com.opencgl.impl.RestSender;
import com.opencgl.model.RestRequest;
import com.opencgl.model.RestResponse;

/**
 * @author Chance.W
 * @version 9.0
 * @className RestSendMessageFactory
 * @description TODO
 * @date 2022/8/13 18:18
 */
public class RestSendMessageFactory extends SendMessageFactory {
    public static SendMessageService<RestRequest, RestResponse> sendRestMessage() {
        return new RestSender();
    }

}
