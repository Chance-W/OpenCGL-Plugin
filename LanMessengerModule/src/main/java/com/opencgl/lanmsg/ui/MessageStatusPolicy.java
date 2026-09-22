package com.opencgl.lanmsg.ui;

public final class MessageStatusPolicy {
    private MessageStatusPolicy() {}

    public static boolean showDeliveryReceipt(String status, boolean outgoing) {
        return outgoing && "DELIVERED".equals(status);
    }
}
