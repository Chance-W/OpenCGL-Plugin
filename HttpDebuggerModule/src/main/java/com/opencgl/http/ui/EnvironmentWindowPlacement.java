package com.opencgl.http.ui;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.util.List;

/** Placement for HTTP environment windows only; no global dialog policy change. */
public final class EnvironmentWindowPlacement {
    private EnvironmentWindowPlacement() {}

    public static Point2D position(Rectangle2D owner, double width, double height, List<Rectangle2D> screens) {
        Rectangle2D screen = screens.stream().max(java.util.Comparator.comparingDouble(bounds ->
            Math.max(0, Math.min(owner.getMaxX(), bounds.getMaxX()) - Math.max(owner.getMinX(), bounds.getMinX())) *
            Math.max(0, Math.min(owner.getMaxY(), bounds.getMaxY()) - Math.max(owner.getMinY(), bounds.getMinY()))
        )).orElse(owner);
        double x = owner.getMinX() + (owner.getWidth() - width) / 2;
        double y = owner.getMinY() + (owner.getHeight() - height) / 2;
        return new Point2D(Math.max(screen.getMinX(), Math.min(x, screen.getMaxX() - width)),
            Math.max(screen.getMinY(), Math.min(y, screen.getMaxY() - height)));
    }

    public static Point2D position(Window owner, double width, double height) {
        return position(new Rectangle2D(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight()),
            width, height, Screen.getScreens().stream().map(Screen::getVisualBounds).toList());
    }

    public static void placeBeforeShow(Stage stage, Window owner) {
        if (owner == null || !owner.isShowing()) return;
        double width = stage.getWidth();
        double height = stage.getHeight();
        if (!Double.isFinite(width) || width <= 0) width = stage.getScene().getWidth();
        if (!Double.isFinite(height) || height <= 0) height = stage.getScene().getHeight();
        stage.setWidth(width);
        stage.setHeight(height);
        Point2D point = position(owner, width, height);
        stage.setX(point.getX());
        stage.setY(point.getY());
    }
}
