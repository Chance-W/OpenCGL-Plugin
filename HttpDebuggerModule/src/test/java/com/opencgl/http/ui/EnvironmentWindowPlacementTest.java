package com.opencgl.http.ui;

import javafx.geometry.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentWindowPlacementTest {
    @Test void firstPlacementFollowsOwnerOnNegativeCoordinateSecondaryScreen() {
        var point = EnvironmentWindowPlacement.position(new Rectangle2D(-1800, 100, 1000, 800), 800, 500,
            List.of(new Rectangle2D(0, 0, 1920, 1080), new Rectangle2D(-1920, 0, 1920, 1080)));
        assertEquals(-1700, point.getX());
        assertEquals(250, point.getY());
    }
    @Test void straddlingOwnerUsesScreenWithLargestOverlapAndClampsDialog() {
        var point = EnvironmentWindowPlacement.position(new Rectangle2D(1800, 50, 1000, 800), 800, 500,
            List.of(new Rectangle2D(0, 0, 1920, 1080), new Rectangle2D(1920, 0, 1920, 1080)));
        assertEquals(1920, point.getX());
        assertEquals(200, point.getY());
    }
}
