package com.opencgl.lanmsg.core;

public record Peer(String id, String name, String ip, int port, boolean online) {
    @Override public String toString() { return name + " · " + ip + ":" + port; }
}
