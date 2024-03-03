package com.xtool.opencgl.utils;


import org.apache.mina.core.buffer.IoBuffer;

/**
 * @author Chance.W
 * @date 2020/2/7-10:10
 */
public class MsgSessionHead implements MsgPart {
    public static final int MSG_SESSION_HEAD_LEN = 18;
    public static final String DlgLgn = "DlgLgn";
    public static final String DlgCon = "DlgCon";
    public static final String DlgEnd = "DlgEnd";
    public Integer id;
    public String word;

    public MsgSessionHead() {
    }

    public MsgSessionHead(IoBuffer buf) {
        this.id = Xx.getHexInt(buf, 8);
        byte[] word = new byte[6];
        buf.get(word);
        this.word = new String(word);
    }

    @Override
    public byte[] toByte() {
        IoBuffer buffer = IoBuffer.allocate(18);
        buffer.put(Xx.intToHexByte8(this.id));
        buffer.put(this.word.getBytes());
        buffer.putInt(0);
        return buffer.array();
    }
}
