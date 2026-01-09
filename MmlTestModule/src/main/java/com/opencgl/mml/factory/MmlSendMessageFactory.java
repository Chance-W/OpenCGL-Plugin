package com.opencgl.mml.factory;

import com.opencgl.base.factory.SendMessageFactory;
import com.opencgl.base.service.SendMessageService;
import com.opencgl.mml.impl.MmlSender;

import com.opencgl.mml.model.MmlRequest;
import com.opencgl.mml.model.MmlResponse;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class MmlSendMessageFactory  extends SendMessageFactory {

    public static SendMessageService<MmlRequest, MmlResponse> sendMmlMessage(){
        return new MmlSender();
    }

}
