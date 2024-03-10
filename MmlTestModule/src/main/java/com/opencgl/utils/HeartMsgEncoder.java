package com.opencgl.utils;



import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.filter.codec.demux.MessageEncoder;
/**
 * @author Chance.W
 * @date 2020/2/7-10:12
 */
public class HeartMsgEncoder implements MessageEncoder<HeartMsg> {
    public HeartMsgEncoder() {
    }

    @Override
    public void encode(IoSession session, HeartMsg msg, ProtocolEncoderOutput output) {
        output.write(IoBuffer.wrap(HeartMsg.HEART_MSG_BUF));
    }

}