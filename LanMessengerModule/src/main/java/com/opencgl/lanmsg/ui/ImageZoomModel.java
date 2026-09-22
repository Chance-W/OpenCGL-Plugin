package com.opencgl.lanmsg.ui;

/** Bounded zoom state used by the image viewer. */
public final class ImageZoomModel {
    public static final double MIN_SCALE = .1;
    public static final double MAX_SCALE = 8.0;
    private double scale = 1.0;

    public double scale() { return scale; }

    public void setScale(double value) {
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    public void wheel(double deltaY) {
        if (deltaY != 0) setScale(scale + deltaY / 10.0);
    }
}
