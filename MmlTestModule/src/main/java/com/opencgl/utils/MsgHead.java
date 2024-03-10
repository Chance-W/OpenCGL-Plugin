package com.opencgl.utils;


import org.apache.mina.core.buffer.IoBuffer;

/**
 * @author Chance.W
 * @date 2020/2/7-10:01
 */
public class MsgHead implements MsgPart {
    public static final int MSG_HEAD_LEN = 20;
    public static byte[] MSG_HEAD_VER = new byte[]{49, 49, 49, 49};
    public static byte[] MSG_HEAD_TERM_ID = new byte[]{90, 84, 80, 64, 49, 50, 51, 52};
    public static byte[] MSG_HEAD_SERVICE_NAME = new byte[]{80, 80, 83, 80, 80, 83, 32, 32};
    public static final MsgHead INST = new MsgHead();

    private MsgHead() {
    }

    @Override
    public byte[] toByte() {
        IoBuffer buffer = IoBuffer.allocate(20);
        buffer.put(MSG_HEAD_VER);
        buffer.put(MSG_HEAD_TERM_ID);
        buffer.put(MSG_HEAD_SERVICE_NAME);
        return buffer.array();
    }
}