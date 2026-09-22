package com.opencgl.lanmsg.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LanInteractionPolicyTest {
    @Test
    void enterSendsOnlyAfterImeCompositionHasFinished() {
        assertFalse(ComposerInputPolicy.shouldSendOnEnter(true, false, "中文"));
        assertFalse(ComposerInputPolicy.shouldSendOnEnter(false, true, "中文"));
        assertFalse(ComposerInputPolicy.shouldSendOnEnter(false, false, "   "));
        assertTrue(ComposerInputPolicy.shouldSendOnEnter(false, false, "中文"));
    }

    @Test
    void zoomModelClampsAndSupportsWheelSteps() {
        var zoom = new ImageZoomModel();
        assertEquals(1.0, zoom.scale());
        zoom.wheel(4);
        assertEquals(1.4, zoom.scale(), 0.0001);
        zoom.wheel(-100);
        assertEquals(ImageZoomModel.MIN_SCALE, zoom.scale(), 0.0001);
        zoom.wheel(1000);
        assertEquals(ImageZoomModel.MAX_SCALE, zoom.scale(), 0.0001);
    }

    @Test
    void messageBubbleAlignmentFollowsDirection() {
        assertEquals(MessageBubbleAlignment.RIGHT, MessageBubbleAlignment.forMessage(true));
        assertEquals(MessageBubbleAlignment.LEFT, MessageBubbleAlignment.forMessage(false));
    }
}
