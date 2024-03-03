package com.xtool.opencgl.utils;


import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.demux.MessageDecoderAdapter;
import org.apache.mina.filter.codec.demux.MessageDecoderResult;

/**
 * @author Chance.W
 * @date 2020/2/7-9:55
 **/
public class HeartMsgDecoder extends MessageDecoderAdapter {
    public HeartMsgDecoder() {
    }

    @Override
    public MessageDecoderResult decodable(IoSession session, IoBuffer buffer) {
        if (buffer.remaining() < 8) {
            return MessageDecoderResult.NEED_DATA;
        } else {
            buffer.getInt();
            int size = Xx.getHexInt(buffer, 4);
            return size == 4 ? MessageDecoderResult.OK : MessageDecoderResult.NOT_OK;
        }
    }

    @Override
    public MessageDecoderResult decode(IoSession session, IoBuffer buffer, ProtocolDecoderOutput output) {
        if (buffer.remaining() < HeartMsg.HEART_MSG_TOTAL_LEN) {
            return MessageDecoderResult.NEED_DATA;
        } else {
            for(int i = 0; i < HeartMsg.HEART_MSG_TOTAL_LEN; ++i) {
                buffer.get();
            }

            output.write(HeartMsg.INST);
            return MessageDecoderResult.OK;
        }
    }
}
