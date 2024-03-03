package com.xtool.opencgl.utils;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Chance.W
 */
public class ClientInfo {
    public ClientInfo() {
    }
    public AtomicInteger txId = new AtomicInteger(1);
    public String username;
    public String password;
    public Integer sessionId = 0;
    public static final String CLIENT_INFO_KEY = "CLIENT_INFO_KEY";


}