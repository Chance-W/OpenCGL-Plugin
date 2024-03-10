package com.opencgl.utils;


import org.apache.mina.core.buffer.IoBuffer;

/**
 * @author Chance.W
 * @date 2020/2/7-10:04
 */
public class MsgCmd implements MsgPart {
    public static final int DlgLgn = 0;
    public static final int DlgCon = 1;
    public static final int DlgEnd = 2;
    private String cmd;
    private int len;
    private String fixedCmd;
    private int cmdType;

    public MsgCmd(String cmd) {
        this((String)cmd, 1);
    }

    public MsgCmd(String cmd, int type) {
        this.cmd = cmd;
        this.len = cmd.length();
        this.cmdType = type;
        this.fix();
    }

    public MsgCmd(IoBuffer buf, int len) {
        byte[] bs = new byte[len];
        buf.get(bs);
        this.cmd = new String(bs);
        this.len = len;
    }

    public String getCmd() {
        return this.cmd;
    }

    public int getLen() {
        return this.len;
    }

    public int getCmdType() {
        return this.cmdType;
    }

    protected void fix() {
        if (this.cmd.length() % 4 == 1) {
            this.fixedCmd = this.cmd + "   ";
            this.len += 3;
        } else if (this.cmd.length() % 4 == 2) {
            this.fixedCmd = this.cmd + "  ";
            this.len += 2;
        } else if (this.cmd.length() % 4 == 3) {
            this.fixedCmd = this.cmd + " ";
            ++this.len;
        } else {
            this.fixedCmd = this.cmd;
        }

    }

    @Override
    public byte[] toByte() {
        IoBuffer buf = IoBuffer.allocate(this.len);
        buf.put(this.fixedCmd.getBytes());
        return buf.array();
    }
}

