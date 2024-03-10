package com.opencgl.factory;

import com.opencgl.base.factory.SendMessageFactory;
import com.opencgl.base.service.SendMessageService;
import com.opencgl.impl.MmlSender;

import com.opencgl.model.MmlRequest;
import com.opencgl.model.MmlResponse;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class MmlSendMessageFactory  extends SendMessageFactory {

    public static SendMessageService<MmlRequest, MmlResponse> sendMmlMessage(){
        return new MmlSender();
    }

}
