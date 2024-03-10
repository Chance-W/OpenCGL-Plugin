package com.opencgl.utils;


/**
 * @author Chance.W
 * @date 2020/2/7-9:59
 */
public class LoginCmd extends MsgCmd {
    public LoginCmd(String username, String password) {
        super(String.format("LOGIN:USER=%s, PSWD=%s", username, password), 0);
    }
}