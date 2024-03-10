package com.opencgl.utils;

/**
 * @author Chance.W
 * @date 2020/2/7-10:05
 */
public class LogoutCmd extends MsgCmd {
    public LogoutCmd(String username) {
        super(String.format("LOGOUT:USER=%s", username), 2);
    }
}
