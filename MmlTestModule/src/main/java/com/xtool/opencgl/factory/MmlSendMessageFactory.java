package com.xtool.opencgl.factory;

import com.opencgl.base.factory.SendMessageFactory;
import com.opencgl.base.service.SendMessageService;
import com.xtool.opencgl.impl.MmlSender;

import com.xtool.opencgl.model.MmlRequest;
import com.xtool.opencgl.model.MmlResponse;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class MmlSendMessageFactory  extends SendMessageFactory {

    public static SendMessageService<MmlRequest, MmlResponse> sendMmlMessage(){
        return new MmlSender();
    }

}
