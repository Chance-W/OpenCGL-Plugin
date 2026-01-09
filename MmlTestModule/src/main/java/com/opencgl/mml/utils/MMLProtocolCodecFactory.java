package com.opencgl.mml.utils;

import org.apache.mina.filter.codec.demux.DemuxingProtocolCodecFactory;

/**
 * @author Chance.W
 * @date 2020/2/7-9:54
 */
public class MMLProtocolCodecFactory extends DemuxingProtocolCodecFactory {
    public MMLProtocolCodecFactory() {
        this.addMessageDecoder(HeartMsgDecoder.class);
        this.addMessageDecoder(MsgDecoder.class);
        this.addMessageEncoder(HeartMsg.class, HeartMsgEncoder.class);
        this.addMessageEncoder(Msg.class,MsgEncoder.class);
    }
}