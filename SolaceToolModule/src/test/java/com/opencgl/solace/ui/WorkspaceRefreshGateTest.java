package com.opencgl.solace.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceRefreshGateTest {

    @Test
    void consumesExactlyOneSelfTriggeredRefresh() {
        WorkspaceRefreshGate gate = new WorkspaceRefreshGate();

        gate.suppressNext();

        assertFalse(gate.shouldRefresh());
        assertTrue(gate.shouldRefresh());
    }

    @Test
    void cancelRestoresNormalRefreshAfterFailedSave() {
        WorkspaceRefreshGate gate = new WorkspaceRefreshGate();
        gate.suppressNext();

        gate.cancelSuppression();

        assertTrue(gate.shouldRefresh());
    }
}
