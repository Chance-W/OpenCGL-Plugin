package com.opencgl.ruler.controller;

import com.opencgl.ruler.i18n.I18N;
import com.opencgl.ruler.model.ColorHistory;
import com.opencgl.ruler.service.ScreenCaptureService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.awt.MouseInfo;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

public class PixelRulerController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(PixelRulerController.class);
    
    public BorderPane rootPane;
    public CheckBox pickerCheckBox;
    public Button startButton;
    public Button stopButton;
    
    public Label positionLabel;
    public Label hexLabel;
    public Label rgbLabel;
    public Rectangle colorPreview;
    public Canvas magnifierCanvas;
    
    public ListView<ColorHistory> historyList;
    public Button clearHistoryButton;
    public Button copyHexButton;
    public Button copyRgbButton;
    public Label statusLabel;
    
    private ScreenCaptureService captureService;
    private ObservableList<ColorHistory> colorHistories;
    private Timer monitorTimer;
    private Color currentColor;
    private volatile boolean disposed;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        captureService = new ScreenCaptureService();
        colorHistories = FXCollections.observableArrayList();
        
        setupUI();
        bindEvents();
        initI18n();
        
        // 确保rootPane可以接收键盘事件
        Platform.runLater(() -> {
            if (!disposed) rootPane.requestFocus();
        });
    }

    private void initI18n() {
        statusLabel.textProperty().unbind();
        statusLabel.setText(I18N.get("status.ready"));
    }

    private void setupUI() {
        historyList.setItems(colorHistories);
        historyList.setCellFactory(lv -> new ColorHistoryCell());
        
        // 默认选中取色器
        pickerCheckBox.setSelected(true);
    }

    private void bindEvents() {
        startButton.setOnAction(e -> startMonitoring());
        stopButton.setOnAction(e -> stopMonitoring());
        
        clearHistoryButton.setOnAction(e -> colorHistories.clear());
        copyHexButton.setOnAction(e -> {
            if (currentColor != null) {
                copyToClipboard(hexLabel.getText());
            }
        });
        copyRgbButton.setOnAction(e -> {
            if (currentColor != null) {
                copyToClipboard(rgbLabel.getText());
            }
        });
        
        // 双击历史记录复制
        historyList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ColorHistory selected = historyList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    copyToClipboard(selected.getHex());
                }
            }
        });

        // 全局快捷键: 空格键直接复制当前颜色
        rootPane.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.SPACE) {
                if (currentColor != null) {
                    copyToClipboard(hexLabel.getText());
                    statusLabel.setText(I18N.get("status.copiedHex", hexLabel.getText()));
                    statusLabel.setStyle("-fx-text-fill: green;");
                    e.consume();
                }
            }
        });
        rootPane.setFocusTraversable(true);
        
        // 点击时重新获取焦点
        rootPane.setOnMouseClicked(e -> {
            rootPane.requestFocus();
            if (e.getClickCount() == 2) {
                if (currentColor != null) {
                    copyToClipboard(hexLabel.getText());
                    // 状态提示逻辑在copyToClipboard中已经有了，但这里可以额外保证一下或者不做额外处理
                }
            }
        });
    }

    private void startMonitoring() {
        if (monitorTimer != null) {
            return;
        }
        
        monitorTimer = new Timer(true);
        monitorTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateColorInfo();
            }
        }, 0, 100); // 每100ms更新一次
        
        startButton.setDisable(true);
        stopButton.setDisable(false);
        statusLabel.setText(I18N.get("status.monitoring"));
        statusLabel.setStyle("-fx-text-fill: green;");
        
        logger.info("开始监听鼠标");
    }

    private void stopMonitoring() {
        if (monitorTimer != null) {
            monitorTimer.cancel();
            monitorTimer = null;
        }
        
        startButton.setDisable(false);
        stopButton.setDisable(true);
        statusLabel.setText(I18N.get("status.stopped"));
        statusLabel.setStyle("-fx-text-fill: gray;");
        
        logger.info("停止监听");
    }

    private void updateColorInfo() {
        Platform.runLater(() -> {
            if (disposed) return;
            try {
                // 获取鼠标位置
                Point mousePos = MouseInfo.getPointerInfo().getLocation();
                positionLabel.setText(String.format("(%d, %d)", mousePos.x, mousePos.y));
                
                if (pickerCheckBox.isSelected()) {
                    // 取色
                    java.awt.Color awtColor = captureService.getColorAtPoint(mousePos.x, mousePos.y);
                    currentColor = Color.rgb(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());
                    
                    String hex = toHex(currentColor);
                    String rgb = toRGB(currentColor);
                    
                    hexLabel.setText(hex);
                    rgbLabel.setText(rgb);
                    colorPreview.setFill(currentColor);
                    
                    // 更新放大镜
                    updateMagnifier(mousePos.x, mousePos.y);
                }
                
            } catch (Exception e) {
                logger.error("更新颜色信息失败", e);
            }
        });
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (monitorTimer != null) {
            monitorTimer.cancel();
            monitorTimer = null;
        }
        if (rootPane != null) {
            rootPane.setOnKeyPressed(null);
            rootPane.setOnMouseClicked(null);
        }
        if (historyList != null) historyList.setOnMouseClicked(null);
    }

    private void updateMagnifier(int centerX, int centerY) {
        BufferedImage capture = captureService.captureMagnifier(centerX, centerY, 5);
        if (capture == null) return;
        
        GraphicsContext gc = magnifierCanvas.getGraphicsContext2D();
        double cellSize = magnifierCanvas.getWidth() / 5;
        
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                if (x < capture.getWidth() && y < capture.getHeight()) {
                    int rgb = capture.getRGB(x, y);
                    Color color = Color.rgb(
                        (rgb >> 16) & 0xFF,
                        (rgb >> 8) & 0xFF,
                        rgb & 0xFF
                    );
                    gc.setFill(color);
                    gc.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
                    
                    // 中心十字
                    if (x == 2 && y == 2) {
                        gc.setStroke(Color.RED);
                        gc.setLineWidth(2);
                        gc.strokeRect(x * cellSize, y * cellSize, cellSize, cellSize);
                    }
                }
            }
        }
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }

    private String toRGB(Color color) {
        return String.format("RGB(%d, %d, %d)",
            (int)(color.getRed() * 255),
            (int)(color.getGreen() * 255),
            (int)(color.getBlue() * 255));
    }

    private void copyToClipboard(String text) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
        
        // 添加到历史
        if (currentColor != null) {
            colorHistories.add(0, new ColorHistory(currentColor));
            if (colorHistories.size() > 20) {
                colorHistories.remove(20, colorHistories.size());
            }
        }
        
        statusLabel.setText(I18N.get("status.copied", text));
        statusLabel.setStyle("-fx-text-fill: green;");
    }

    // 自定义单元格
    static class ColorHistoryCell extends ListCell<ColorHistory> {
        @Override
        protected void updateItem(ColorHistory item, boolean empty) {
            super.updateItem(item, empty);
            
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(10);
                
                Rectangle rect = new Rectangle(30, 20);
                rect.setFill(item.getColor());
                rect.setStroke(Color.BLACK);
                rect.setStrokeWidth(1);
                
                Label hexLabel = new Label(item.getHex());
                Label rgbLabel = new Label(item.getRgb());
                rgbLabel.setStyle("-fx-text-fill: gray;");
                
                box.getChildren().addAll(rect, hexLabel, rgbLabel);
                setGraphic(box);
            }
        }
    }
}
