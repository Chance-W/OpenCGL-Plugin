package com.opencgl.mml.utils;


import org.apache.mina.core.buffer.IoBuffer;

/**
 * @author Chance.W
 * @date 2020/2/7-9:53
 */
public class Msg implements MsgPart {
    public int msgLen;
    public int totalLen;
    public MsgHead msgHead;
    public MsgSessionHead msgSessionHead;
    public MsgTxHead msgTxHead;
    public MsgCmd cmd;
    public int crc;

    public Msg() {
        this.msgHead = MsgHead.INST;
        this.msgSessionHead = new MsgSessionHead();
        this.msgTxHead = new MsgTxHead();
        this.crc = 0;
    }

    public void calc() {
        this.msgLen = 56 + this.cmd.getLen();
        this.totalLen = 56 + this.cmd.getLen() + 16;
    }

    @Override
    public byte[] toByte() {
        this.calc();
        IoBuffer buf = IoBuffer.allocate(this.totalLen);
        buf.put("`SC`".getBytes());
        buf.put(Xx.intToHexByte4(this.msgLen));
        buf.put(this.msgHead.toByte());
        buf.put(this.msgSessionHead.toByte());
        buf.put(this.msgTxHead.toByte());
        buf.put(this.cmd.toByte());
        buf.put(Xx.intToHexByte8(this.crc));
        return buf.array();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Msg\n----------\n");
        sb.append("totalLen = ").append(this.totalLen).append('\n');
        sb.append("msgLen = ").append(this.msgLen).append('\n');
        sb.append("sessionId = ").append(this.msgSessionHead.id).append('\n');
        sb.append("sessionCtlWord = ").append(this.msgSessionHead.word).append('\n');
        sb.append("txId = ").append(this.msgTxHead.id).append('\n');
        sb.append("txCtlWord = ").append(this.msgTxHead.word).append('\n');
        sb.append("cmdLen = ").append(this.cmd.getLen()).append('\n');
        sb.append("cmd = ").append(this.cmd.getCmd()).append('\n');
        sb.append("----------\n");
        return sb.toString();
    }
}
