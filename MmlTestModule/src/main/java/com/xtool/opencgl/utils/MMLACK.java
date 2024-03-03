package com.xtool.opencgl.utils;


import java.util.Map;

/**
 * @author Chance.W
 * @date 2020/2/7-9:49
 */
public class MMLACK {
    private String ack;
    private String cmd;
    private String code;
    private Map<String, Object> checkMap;
    private String orignal;

    public MMLACK() {
    }

    public String getAck() {
        return this.ack;
    }

    public void setAck(String ack) {
        this.ack = ack;
    }

    public String getCmd() {
        return this.cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public String getOrignal() {
        return this.orignal;
    }

    public void setOrignal(String orignal) {
        this.orignal = orignal;
    }

    public Map<String, Object> getCheckMap() {
        return this.checkMap;
    }

    public void setCheckMap(Map<String, Object> checkMap) {
        this.checkMap = checkMap;
    }

    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}

