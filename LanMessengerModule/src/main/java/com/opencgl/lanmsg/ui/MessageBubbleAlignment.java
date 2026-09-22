package com.opencgl.lanmsg.ui;

public enum MessageBubbleAlignment {
    LEFT, RIGHT;

    public static MessageBubbleAlignment forMessage(boolean outgoing) {
        return outgoing ? RIGHT : LEFT;
    }
}
