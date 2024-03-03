package com.xtool.opencgl.utils;



import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.filter.codec.demux.MessageEncoder;

/**
 * @author Chance.W
 * @date 2020/2/7-10:14
 */
public class MsgEncoder implements MessageEncoder<Msg> {
    public MsgEncoder() {
    }

    @Override
    public void encode(IoSession session, Msg msg, ProtocolEncoderOutput output) {
        ClientInfo info = (ClientInfo)session.getAttribute("CLIENT_INFO_KEY");
        msg.msgHead = MsgHead.INST;
        msg.msgSessionHead.id = info.sessionId;
        switch(msg.cmd.getCmdType()) {
            case 0:
                msg.msgSessionHead.word = "DlgLgn";
                break;
            case 1:
            default:
                msg.msgSessionHead.word = "DlgCon";
                break;
            case 2:
                msg.msgSessionHead.word = "DlgEnd";
        }

        msg.msgTxHead.id = info.txId.getAndIncrement();
        msg.msgTxHead.word = "TxCon ";
        msg.calc();
        output.write(IoBuffer.wrap(msg.toByte()));
    }
}
