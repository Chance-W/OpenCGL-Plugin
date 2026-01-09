package com.opencgl.ruler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ScreenCaptureService {
    private static final Logger logger = LoggerFactory.getLogger(ScreenCaptureService.class);
    
    private Robot robot;
    
    public ScreenCaptureService() {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            logger.error("Failed to create Robot", e);
        }
    }
    
    public Color getColorAtPoint(int x, int y) {
        if (robot == null) return Color.BLACK;
        return robot.getPixelColor(x, y);
    }
    
    public BufferedImage captureScreen(Rectangle bounds) {
        if (robot == null) return null;
        return robot.createScreenCapture(bounds);
    }
    
    public BufferedImage captureMagnifier(int centerX, int centerY, int size) {
        int half = size / 2;
        Rectangle bounds = new Rectangle(
            centerX - half,
            centerY - half,
            size,
            size
        );
        return captureScreen(bounds);
    }
}
