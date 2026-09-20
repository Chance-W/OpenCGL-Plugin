package com.opencgl.solace.ui;

/** Prevents a view from rebuilding itself for the save notification it just emitted. */
final class WorkspaceRefreshGate {
    private boolean suppressNext;

    void suppressNext() { suppressNext = true; }

    boolean shouldRefresh() {
        if (!suppressNext) return true;
        suppressNext = false;
        return false;
    }

    void cancelSuppression() { suppressNext = false; }
}
