package com.opencgl.base.utils;

import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LoadingMaskTest {

    @Test
    void detachMaskRemovesAttachedOverlayEvenWhenVisibilityStateIsStale() {
        StackPane parent = new StackPane();
        StackPane mask = new StackPane();
        parent.getChildren().add(mask);

        LoadingMask.detachMask(mask);

        assertFalse(parent.getChildren().contains(mask));
    }

    @Test
    void detachMaskIsIdempotent() {
        StackPane mask = new StackPane();

        LoadingMask.detachMask(mask);
        LoadingMask.detachMask(mask);
    }

    @Test
    void loadingUtilRemoveBeforeShowIsSafe() {
        LoadingUtil.remove(new StackPane());
    }
}
