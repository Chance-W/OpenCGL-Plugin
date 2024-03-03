package com.xtool.opencgl.utils;


import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.demux.MessageDecoderAdapter;
import org.apache.mina.filter.codec.demux.MessageDecoderResult;

/**
 * @author Chance.W
 * @date 2020/2/7-10:11
 */
public class MsgDecoder extends MessageDecoderAdapter {
    public MsgDecoder() {
    }

    @Override
    public MessageDecoderResult decodable(IoSession session, IoBuffer buffer) {
        if (buffer.remaining() < 8) {
            return MessageDecoderResult.NEED_DATA;
        } else {
            buffer.getInt();
            int size = Xx.getHexInt(buffer, 4);
            return size > 4 ? MessageDecoderResult.OK : MessageDecoderResult.NOT_OK;
        }
    }

    @Override
    public MessageDecoderResult decode(IoSession session, IoBuffer buffer, ProtocolDecoderOutput output){
        if (buffer.remaining() < 8) {
            return MessageDecoderResult.NEED_DATA;
        } else {
            int limit = buffer.limit();
            int pos = buffer.position();
            buffer.getInt();
            int size = Xx.getHexInt(buffer, 4);
            if (buffer.remaining() < size + 8) {
                buffer.position(pos);
                buffer.limit(limit);
                return MessageDecoderResult.NEED_DATA;
            } else {
                Msg msg = new Msg();
                msg.msgLen = size;
                buffer.getInt();
                buffer.getInt();
                buffer.getInt();
                buffer.getInt();
                buffer.getInt();
                msg.msgHead = MsgHead.INST;
                msg.msgSessionHead = new MsgSessionHead(buffer.getSlice(18));
                msg.msgTxHead = new MsgTxHead(buffer.getSlice(18));
                int cmdLen = size - 20 - 18 - 18;
                msg.cmd = new MsgCmd(buffer.getSlice(cmdLen), cmdLen);
                byte[] crcBs = new byte[8];
                buffer.get(crcBs);
                msg.crc = 0;
                msg.totalLen = msg.msgLen + 20;
                output.write(msg);
                return MessageDecoderResult.OK;
            }
        }
    }
}

