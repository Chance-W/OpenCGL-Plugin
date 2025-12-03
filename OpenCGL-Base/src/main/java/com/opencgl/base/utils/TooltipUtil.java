package com.opencgl.base.utils;

import java.util.Timer;
import java.util.TimerTask;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

@SuppressWarnings("unused")
public class TooltipUtil {

    public static void showToast(String message) {
        showToast(null, message);
    }

    public static void showToast(Node node, String message) {
        Window window = getWindow(node);
        double x;
        double y;
        if (node != null) {
            x = getScreenX(node) + getWidth(node) / 2.0D;
            y = getScreenY(node) + getHeight(node);
        }
        else {
            x = window.getX() + window.getWidth() / 2.0D;
            y = window.getY() + window.getHeight();
        }
        showToast(window, message, 3000L, x, y, null);
    }

    public static void showToast(Node node, String message, Double size) {
        Window window = getWindow(node);
        double x;
        double y;
        if (node != null) {
            x = getScreenX(node) + getWidth(node) / 2.0D;
            y = getScreenY(node) + getHeight(node) / 2.0D;
        }
        else {
            x = window.getX() + window.getWidth() / 2.0D;
            y = window.getY() + window.getHeight() / 2.0D;
        }
        showToast(window, message, 3000L, x, y, size);
    }

    private static Window getWindow(Object node) {
        if (node != null) {
            if (node instanceof Window) {
                return (Window) node;
            }
            else if (node instanceof Node) {
                return ((Node) node).getScene().getWindow();
            }
            else {
                throw new IllegalArgumentException("Unknown owner: " + node.getClass());
            }
        }
        else {
            Window window = null;
            for (Window value : Window.getWindows()) {
                window = value;
                if (window.isFocused() && !(window instanceof PopupWindow)) {
                    break;
                }
            }
            return window;
        }
    }

    public static void showToast(Window window, String message, long time, double x, double y, Double size) {
        final Tooltip tooltip = new Tooltip(message);
        if (size != null) {
            Font font = new Font(size);
            tooltip.setFont(font);
        }
        tooltip.setAutoHide(true);
        tooltip.setOpacity(0.9D);
        tooltip.setWrapText(true);
        tooltip.show(window, x, y);
        tooltip.setAnchorX(tooltip.getAnchorX() - tooltip.getWidth() / 2.0D);
        tooltip.setAnchorY(tooltip.getAnchorY() - tooltip.getHeight());
        if (time > 0L) {
            (new Timer()).schedule(new TimerTask() {
                public void run() {
                    Platform.runLater(tooltip::hide);
                }
            }, time);
        }
    }

    public static double getScreenX(Node control) {
        return control.getScene().getWindow().getX() + control.getScene().getX() + control.localToScene(0.0D, 0.0D).getX();
    }

    public static double getScreenY(Node control) {
        return control.getScene().getWindow().getY() + control.getScene().getY() + control.localToScene(0.0D, 0.0D).getY();
    }

    public static double getWidth(Node control) {
        return control.getBoundsInParent().getWidth();
    }

    public static double getHeight(Node control) {
        return control.getBoundsInParent().getHeight();
    }
}
