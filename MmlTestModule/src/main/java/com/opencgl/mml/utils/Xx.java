package com.opencgl.mml.utils;


import org.apache.mina.core.buffer.IoBuffer;

/**
 * @author Chance.W
 * @date 2020/2/7-9:53
 */
public final class Xx {
    public Xx() {
    }

    public static int getHexInt(IoBuffer buf, int size) {
        byte[] bs = new byte[size];
        buf.get(bs);
        return Integer.valueOf(new String(bs), 16);
    }

    public static byte[] intToHexByte4(int i) {
        return String.format("%04X", i).getBytes();
    }

    public static byte[] intToHexByte8(int i) {
        return String.format("%08X", i).getBytes();
    }

    public static void main(String[] args) {
        System.out.println(String.format("%04X", 1000));
    }
}
