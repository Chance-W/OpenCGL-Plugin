package com.opencgl.qr.controller;

import com.opencgl.qr.i18n.I18N;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import javax.imageio.ImageIO;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.utils.DialogUtil;
import com.opencgl.base.utils.TooltipUtil;
import com.opencgl.qr.utils.ChoiceBoxHelper;
import com.opencgl.qr.utils.CorrectionLevel;
import com.opencgl.qr.utils.FileChooserUtil;
import com.opencgl.qr.utils.QRCodeUtil;
import com.opencgl.qr.view.littleTools.QRCodeGenerateView;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * 二维码生成工具
 *
 * @author xufeng
 * @since 2019/4/25 0025 23:26
 */

@SuppressWarnings("unused")
public class QRCodeGenerateController extends QRCodeGenerateView {
    private static final Logger logger = LoggerFactory.getLogger(QRCodeGenerateController.class);
    private int clickCount = 0;
    private final Timer timer = new Timer(true);
    private volatile boolean disposed;
    private EventHandler<MouseEvent> logoClickHandler;
    private static final int DOUBLE_CLICK_DELAY = 300; // 双击延迟时间，单位：毫秒

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initI18n();
        initView();
        initEvent();
    }

    private void initI18n() {
        contentTitledPane.textProperty().bind(I18N.getBinding("label.content"));
        basicTitledPane.textProperty().bind(I18N.getBinding("label.basic_elements"));
        logoTitledPane.textProperty().bind(I18N.getBinding("label.logo"));

        foregroundLabel.textProperty().bind(I18N.getBinding("label.foreground"));
        backgroundLabel.textProperty().bind(I18N.getBinding("label.background"));
        errorCorrectionLabel.textProperty().bind(I18N.getBinding("label.error_correction"));
        marginLabel.textProperty().bind(I18N.getBinding("label.margin"));
        encodingLabel.textProperty().bind(I18N.getBinding("label.encoding"));
        imageFormatLabel.textProperty().bind(I18N.getBinding("label.image_format"));
        logoOverlayLabel.textProperty().bind(I18N.getBinding("label.logo_overlay"));

        builderButton.textProperty().bind(I18N.getBinding("label.build"));
        saveButton.textProperty().bind(I18N.getBinding("label.save"));
        logoButton.textProperty().bind(I18N.getBinding("label.select_logo"));
    }

    private void initView() {
        initChoiceBox();
        codeFormatChoiceBox.getItems().addAll("utf-8", "gb2312", "ISO-8859-1", "US-ASCII", "utf-16");
        codeFormatChoiceBox.setValue("utf-8");
        onColorColorPicker.setValue(Color.BLACK);
        offColorColorPicker.setValue(Color.WHITE);
        marginChoiceBox.getItems().addAll(1, 2, 3, 4);
        marginChoiceBox.setValue(1);
        formatImageChoiceBox.getItems().addAll("png", "jpg", "gif", "jpeg", "bmp");
        formatImageChoiceBox.setValue("png");
    }

    private void initChoiceBox() {
        errorCorrectionLevelChoiceBox.getSelectionModel().selectedItemProperty()
                .addListener((_o, _v, newValue) -> logoSlider.setMax(newValue.getMaxOverlay()));
        ChoiceBoxHelper.setContentDisplay(
                errorCorrectionLevelChoiceBox, CorrectionLevel.class, level -> I18N.get("label.correction." + level.name()));
        errorCorrectionLevelChoiceBox.setValue(CorrectionLevel.H);
    }

    private void initEvent() {
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(I18N.getBinding("msg.clear_logo"));
        tooltip.setShowDelay(Duration.ZERO);
        Tooltip.install(codeImageView2, tooltip);

        codeImageView2.setImage(null);
        codeImageViewHBox.getChildren().remove(codeImageView2);
        codeImageView2.imageProperty().addListener((observableValue, image, t1) -> {
            if (t1 != null) {
                codeImageViewHBox.getChildren().add(codeImageView2);
            } else {
                codeImageView2.setImage(null);
                codeImageViewHBox.getChildren().remove(codeImageView2);

            }
        });
        logoClickHandler = mouseEvent -> {
            if (mouseEvent.getButton() == MouseButton.PRIMARY) {
                clickCount++;
                if (clickCount == 1) {
                    // 启动定时器，在双击延迟时间内检测第二次点击
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if (clickCount > 1) {
                                // 双击
                                Platform.runLater(() -> {
                                    if (disposed) return;
                                    codeImageView2.setImage(null);
                                    codeImageViewHBox.getChildren().remove(codeImageView2);
                                });
                            }
                            clickCount = 0;
                        }
                    }, DOUBLE_CLICK_DELAY);
                }
            }
        };
        codeImageView2.addEventFilter(MouseEvent.MOUSE_CLICKED, logoClickHandler);
        contentTextField.textProperty().addListener((_ob, _old, _new) -> Platform.runLater(() -> {
            if (!disposed) builderAction(null);
        }));
    }

    @FXML
    private void builderAction(ActionEvent event) {
        if (StringUtils.isEmpty(contentTextField.getText())) {
            return;
        }
        try {
            Image image = QRCodeUtil.toImage(contentTextField.getText(), (int) codeImageView1.getFitWidth(),
                    (int) codeImageView1.getFitHeight(), codeFormatChoiceBox.getValue(),
                    errorCorrectionLevelChoiceBox.getValue().getErrorCorrectionLevel(),
                    marginChoiceBox.getValue(), onColorColorPicker.getValue(),
                    offColorColorPicker.getValue(), formatImageChoiceBox.getValue());
            codeImageView1.setImage(image);
            if (codeImageView2.getImage() != null) {
                Image image1 = QRCodeUtil.encodeImgLogo(image, codeImageView2.getImage(), (int) logoSlider.getValue());
                codeImageView1.setImage(image1);
            }
        } catch (Throwable e) {
            TooltipUtil.showToast(I18N.get("msg.build_failed") + " " + e.getMessage());
            logger.error("", e);
        }
    }

    @FXML
    private void saveAction(ActionEvent event) throws Exception {
        String fileName = "x" + new SimpleDateFormat("yyyyMMddHHmm").format(new Date()) + "."
                + formatImageChoiceBox.getValue();
        File file = FileChooserUtil.chooseSaveFile(fileName,
                new FileChooser.ExtensionFilter(I18N.get("file.type_png"), "*.png"),
                new FileChooser.ExtensionFilter(I18N.get("file.type_jpg"), "*.jpg"),
                new FileChooser.ExtensionFilter(I18N.get("file.type_jpeg"), "*.jpeg"),
                new FileChooser.ExtensionFilter(I18N.get("file.type_bmp"), "*.bmp"));
        if (file != null) {
            String[] fileType = file.getPath().split("\\.");
            ImageIO.write(SwingFXUtils.fromFXImage(codeImageView1.getImage(), null), fileType[fileType.length - 1],
                    file);
            TooltipUtil.showToast(I18N.get("msg.save_success") + file.getPath());
        }
    }

    @FXML
    private void logoAction(ActionEvent event) throws Exception {
        File file = FileChooserUtil.chooseFile(
                new FileChooser.ExtensionFilter(I18N.get("file.type_all_images"), "*.*"),
                new FileChooser.ExtensionFilter(I18N.get("file.type_jpg"), "*.jpg"),
                new FileChooser.ExtensionFilter(I18N.get("file.type_png"), "*.png"),
                new FileChooser.ExtensionFilter(I18N.get("file.type_gif"), "*.gif"),
                new FileChooser.ExtensionFilter(I18N.get("file.type_jpeg"), "*.jpeg"),
                new FileChooser.ExtensionFilter(I18N.get("file.type_bmp"), "*.bmp"));
        if (file != null) {
            Image image = SwingFXUtils.toFXImage(ImageIO.read(file), null);
            codeImageView2.setImage(image);
        }
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        timer.cancel();
        if (logoClickHandler != null) {
            codeImageView2.removeEventFilter(MouseEvent.MOUSE_CLICKED, logoClickHandler);
            logoClickHandler = null;
        }
    }

}
