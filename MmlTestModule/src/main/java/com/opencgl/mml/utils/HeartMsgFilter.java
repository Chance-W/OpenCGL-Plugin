package com.opencgl.mml.utils;


import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
/**
 * @author Chance.W
 * @date 2020/2/7-10:02
 */
public class HeartMsgFilter extends IoFilterAdapter {
    public HeartMsgFilter() {
    }

    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) {
        if (message != HeartMsg.INST) {
            nextFilter.messageReceived(session, message);
        }
    }
}
