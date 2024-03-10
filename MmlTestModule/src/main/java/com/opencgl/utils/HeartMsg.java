package com.opencgl.utils;

/**
 * @author Chance.W
 */
public class HeartMsg implements MsgPart {
    public static final HeartMsg INST = new HeartMsg();
    public static final byte[] HEART_MSG_BUF = new byte[]{96, 83, 67, 96, 48, 48, 48, 52, 72, 66, 72, 66, 66, 55, 66, 68, 66, 55, 66, 68};
    public static final int HEART_MSG_LEN = 4;
    public static final int HEART_MSG_TOTAL_LEN;

    static {
        HEART_MSG_TOTAL_LEN = HEART_MSG_BUF.length;
    }

    private HeartMsg() {
    }

    @Override
    public byte[] toByte() {
        return HEART_MSG_BUF;
    }
}
