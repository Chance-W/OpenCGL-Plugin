package com.opencgl.factory;

import com.opencgl.base.factory.SendMessageFactory;
import com.opencgl.base.service.SendMessageService;
import com.opencgl.impl.RmqSender;
import com.opencgl.model.RmqRequest;
import com.opencgl.model.RmqResponse;

/**
 * @author Chance.W
 */
public class RmqSendMessageFactory extends SendMessageFactory {

    public static SendMessageService<RmqRequest, RmqResponse> sendRmqMessage(){
        return new RmqSender();
    }


}
