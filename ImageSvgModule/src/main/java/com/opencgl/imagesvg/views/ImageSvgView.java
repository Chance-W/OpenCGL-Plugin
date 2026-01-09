package com.opencgl.imagesvg.views;

import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXSlider;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;

/**
 * 图片转SVG视图
 */
public class ImageSvgView {
    @FXML public StackPane rootPane;
    
    // 原图预览
    @FXML public ImageView originalImageView;
    @FXML public Label imageInfoLabel;
    
    // SVG预览
    @FXML public WebView svgPreviewView;
    
    // SVG代码
    @FXML public TextArea svgCodeArea;
    
    // 操作按钮
    @FXML public MFXButton loadImageButton;
    @FXML public MFXButton convertEmbeddedButton;
    @FXML public MFXButton convertTracedButton;
    @FXML public MFXButton saveSvgButton;
    @FXML public MFXButton saveImageButton;
    @FXML public MFXButton copySvgButton;
    @FXML public MFXButton renderSvgButton;
}
