package com.opencgl.lanmsg.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageStatusPolicyTest {
    @Test
    void deliveryReceiptIsShownOnlyOnOutgoingMessages() {
        assertTrue(MessageStatusPolicy.showDeliveryReceipt("DELIVERED", true));
        assertFalse(MessageStatusPolicy.showDeliveryReceipt("DELIVERED", false));
        assertFalse(MessageStatusPolicy.showDeliveryReceipt("FAILED", true));
    }
}
