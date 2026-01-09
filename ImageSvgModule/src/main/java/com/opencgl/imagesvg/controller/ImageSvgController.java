package com.opencgl.imagesvg.controller;

import com.opencgl.imagesvg.i18n.I18N;
import com.opencgl.imagesvg.service.ImageSvgService;
import com.opencgl.imagesvg.views.ImageSvgView;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.Initializable;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import javax.imageio.ImageIO;

/**
 * 图片转SVG控制器
 */
public class ImageSvgController extends ImageSvgView implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(ImageSvgController.class);
    private final ImageSvgService service = new ImageSvgService();
    private File currentImageFile;
    private String currentSvg;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        bindEvents();
        setupSvgCodeArea();
        initI18n();
        setupPreviewViews();
    }

    private void setupPreviewViews() {
        // 设置WebView背景透明，以适配主题切换
        svgPreviewView.setPageFill(javafx.scene.paint.Color.TRANSPARENT);
        // 初始加载一个空的透明HTML，避免WebView自带的白色默认背景显示
        svgPreviewView.getEngine().loadContent(getHtmlWrapper(""));
        
        // 修复WebView消费滚轮事件导致外层双指滑动无法滚动的问题
        svgPreviewView.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            javafx.scene.Node parent = svgPreviewView.getParent();
            while (parent != null && !(parent instanceof javafx.scene.control.ScrollPane)) {
                parent = parent.getParent();
            }
            if (parent instanceof javafx.scene.control.ScrollPane) {
                javafx.scene.control.ScrollPane scrollPane = (javafx.scene.control.ScrollPane) parent;
                double contentHeight = scrollPane.getContent().getBoundsInLocal().getHeight();
                double viewportHeight = scrollPane.getViewportBounds().getHeight();
                if (contentHeight > viewportHeight) {
                    double v = scrollPane.getVvalue() - event.getDeltaY() / (contentHeight - viewportHeight);
                    scrollPane.setVvalue(Math.max(0, Math.min(1, v)));
                }
                event.consume();
            }
        });

        // 绑定原图预览尺寸至其父级，防止大图撑开布局
        if (originalImageView.getParent() instanceof StackPane) {
            StackPane parentPane = (StackPane) originalImageView.getParent();
            originalImageView.fitWidthProperty().bind(parentPane.widthProperty().subtract(4));
            originalImageView.fitHeightProperty().bind(parentPane.heightProperty().subtract(4));
        }
    }

    private void initI18n() {
    }

    private String getHtmlWrapper(String svgContent) {
        return "<!DOCTYPE html>\n" +
               "<html>\n" +
               "<head>\n" +
               "<style>\n" +
               "body {\n" +
               "    margin: 0;\n" +
               "    display: flex;\n" +
               "    justify-content: center;\n" +
               "    align-items: center;\n" +
               "    min-height: 100vh;\n" +
               "    background: transparent;\n" +
               "    overflow: hidden;\n" +
               "}\n" +
               "svg {\n" +
               "    max-width: 100%;\n" +
               "    max-height: 100vh;\n" +
               "    width: auto;\n" +
               "    height: auto;\n" +
               "    object-fit: contain;\n" +
               "}\n" +
               "</style>\n" +
               "</head>\n" +
               "<body>\n" +
               svgContent + "\n" +
               "</body>\n" +
               "</html>";
    }


    private void setupSvgCodeArea() {
        // 监听文本变化，自动调整高度
        svgCodeArea.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                int lines = newVal.split("\n").length;
                double height = Math.max(150, Math.min(500, lines * 18));
                svgCodeArea.setPrefHeight(height);
            }
        });
    }

    private void bindEvents() {
        loadImageButton.setOnAction(e -> loadImage());
        convertEmbeddedButton.setOnAction(e -> convertToSvg(false));
        convertTracedButton.setOnAction(e -> convertToSvg(true));
        saveSvgButton.setOnAction(e -> saveSvg());
        saveImageButton.setOnAction(e -> saveAsImage());
        copySvgButton.setOnAction(e -> copySvg());
        renderSvgButton.setOnAction(e -> renderSvgFromCode());
    }

    /**
     * 从代码框渲染SVG
     */
    private void renderSvgFromCode() {
        String svgCode = svgCodeArea.getText();
        if (svgCode == null || svgCode.trim().isEmpty()) {
            showWarning(I18N.get("msg.pasteSvgFirst"));
            return;
        }

        try {
            currentSvg = svgCode.trim();
            currentImageFile = null;

            svgPreviewView.getEngine().loadContent(getHtmlWrapper(currentSvg));

            originalImageView.setImage(null);
            imageInfoLabel.setText(I18N.get("info.fromCode"));

            showToast(I18N.get("msg.svgRenderSuccess"), "#27ae60");
        }
        catch (Exception e) {
            logger.error("渲染SVG失败", e);
            showError(I18N.get("msg.renderFailed", e.getMessage()));
        }
    }

    private void loadImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("title.chooseFile"));
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(I18N.get("filter.allSupported"), "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp", "*.svg"),
            new FileChooser.ExtensionFilter(I18N.get("filter.images"), "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
            new FileChooser.ExtensionFilter(I18N.get("filter.svg"), "*.svg"),
            new FileChooser.ExtensionFilter(I18N.get("filter.all"), "*.*")
        );

        File file = chooser.showOpenDialog(rootPane.getScene().getWindow());
        if (file != null) {
            try {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".svg")) {
                    currentSvg = java.nio.file.Files.readString(file.toPath());
                    currentImageFile = null;
                    svgCodeArea.setText(currentSvg);
                    svgPreviewView.getEngine().loadContent(getHtmlWrapper(currentSvg));
                    originalImageView.setImage(null);
                    imageInfoLabel.setText(I18N.get("info.fileLoaded", file.getName()));
                    showToast(I18N.get("msg.svgRenderSuccess"), "#27ae60");
                }
                else {
                    currentImageFile = file;
                    currentSvg = null;
                    Image image = new Image(file.toURI().toString());
                    originalImageView.setImage(image);
                    originalImageView.setPreserveRatio(true);
                    imageInfoLabel.setText(I18N.get("info.imageLoaded", file.getName(), (int) image.getWidth(), (int) image.getHeight()));
                    showToast(I18N.get("msg.svgRenderSuccess"), "#27ae60");
                }
            }
            catch (Exception e) {
                logger.error("加载失败", e);
                showError(I18N.get("msg.renderFailed", e.getMessage()));
            }
        }
    }

    private void convertToSvg(boolean traced) {
        if (currentImageFile == null) {
            showWarning(I18N.get("msg.loadImageFirst"));
            return;
        }

        try {
            if (traced) {
                currentSvg = service.convertToTracedSvg(currentImageFile, 10);
            }
            else {
                currentSvg = service.convertToEmbeddedSvg(currentImageFile);
            }
            svgCodeArea.setText(currentSvg);
            svgPreviewView.getEngine().loadContent(getHtmlWrapper(currentSvg));
            showToast(I18N.get("msg.convertSuccess"), "#27ae60");
        }
        catch (Exception e) {
            logger.error("转换失败", e);
            showError(I18N.get("msg.convertFailed", e.getMessage()));
        }
    }

    private void saveSvg() {
        if (currentSvg == null || currentSvg.isEmpty()) {
            showWarning(I18N.get("msg.saveSvgFirst"));
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("title.saveSvg"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18N.get("filter.svgFile"), "*.svg"));
        if (currentImageFile != null) {
            String name = currentImageFile.getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            chooser.setInitialFileName(name + ".svg");
        }

        File file = chooser.showSaveDialog(rootPane.getScene().getWindow());
        if (file != null) {
            try {
                service.saveSvg(currentSvg, file);
                showToast(I18N.get("msg.saveSuccess"), "#27ae60");
            }
            catch (Exception e) {
                showError(I18N.get("msg.saveFailed", e.getMessage()));
            }
        }
    }

    private void copySvg() {
        if (currentSvg == null || currentSvg.isEmpty()) {
            showWarning(I18N.get("msg.noSvgToCopy"));
            return;
        }
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent cc = new ClipboardContent();
        cc.putString(currentSvg);
        clipboard.setContent(cc);
        showToast(I18N.get("msg.svgCopied"), "#27ae60");
    }

    private void saveAsImage() {
        if (currentSvg == null || currentSvg.isEmpty()) {
            showWarning(I18N.get("msg.loadSvgFirst"));
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18N.get("title.saveImage"));
        
        String defaultExt = "png";
        if (currentSvg.contains("data:image/gif")) {
            defaultExt = "gif";
        } else if (currentSvg.contains("data:image/jpeg") || currentSvg.contains("data:image/jpg")) {
            defaultExt = "jpg";
        } else if (currentImageFile != null) {
            String name = currentImageFile.getName().toLowerCase();
            if (name.endsWith(".gif")) defaultExt = "gif";
            else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) defaultExt = "jpg";
        }

        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(I18N.get("filter.png"), "*.png"),
            new FileChooser.ExtensionFilter(I18N.get("filter.jpeg"), "*.jpg", "*.jpeg"),
            new FileChooser.ExtensionFilter("GIF", "*.gif")
        );

        String baseName = "output";
        if (currentImageFile != null) {
            baseName = currentImageFile.getName();
            int dot = baseName.lastIndexOf('.');
            if (dot > 0) baseName = baseName.substring(0, dot);
        }
        chooser.setInitialFileName(baseName + "." + defaultExt);

        for (FileChooser.ExtensionFilter filter : chooser.getExtensionFilters()) {
            if (filter.getExtensions().contains("*." + defaultExt) || 
               (defaultExt.equals("jpg") && filter.getExtensions().contains("*.jpeg"))) {
                chooser.setSelectedExtensionFilter(filter);
                break;
            }
        }

        File file = chooser.showSaveDialog(rootPane.getScene().getWindow());
        if (file != null) {
            try {
                boolean savedFromBase64 = false;
                
                // 如果是内嵌Base64格式的SVG，我们直接提取原图数据覆盖写入，这样能无损保留GIF动图、JPEG画质和全尺寸最佳分辨率
                if (currentSvg.contains("href=\"data:image/")) {
                    int start = currentSvg.indexOf("href=\"data:image/");
                    if (start != -1) {
                        int quoteEnd = currentSvg.indexOf("\"", start + 6);
                        if (quoteEnd != -1) {
                            String dataUri = currentSvg.substring(start + 6, quoteEnd);
                            int comma = dataUri.indexOf(',');
                            if (comma != -1) {
                                String base64 = dataUri.substring(comma + 1);
                                byte[] imgBytes = java.util.Base64.getDecoder().decode(base64.trim());
                                java.nio.file.Files.write(file.toPath(), imgBytes);
                                savedFromBase64 = true;
                            }
                        }
                    }
                }
                
                // 如果未能提取 Base64（比如是矢量追踪生成的 SVG），则退回到 WebView 截图方案
                if (!savedFromBase64) {
                    WritableImage snapshot = svgPreviewView.snapshot(null, null);
                    BufferedImage bufferedImage = SwingFXUtils.fromFXImage(snapshot, null);
                    
                    String formatName = file.getName().toLowerCase();
                    String ext = "png";
                    if (formatName.endsWith(".jpg") || formatName.endsWith(".jpeg")) {
                        ext = "jpg";
                        // JPEG不支持透明度，为了防止透明背景发黑，用白色垫底
                        BufferedImage newImg = new BufferedImage(bufferedImage.getWidth(), bufferedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
                        java.awt.Graphics2D g = newImg.createGraphics();
                        g.drawImage(bufferedImage, 0, 0, java.awt.Color.WHITE, null);
                        g.dispose();
                        bufferedImage = newImg;
                    } else if (formatName.endsWith(".gif")) {
                        ext = "gif";
                    }
                    
                    ImageIO.write(bufferedImage, ext, file);
                }
                
                showToast(I18N.get("msg.imageSaveSuccess"), "#27ae60");
            }
            catch (Exception e) {
                logger.error("保存图片失败", e);
                showError(I18N.get("msg.saveFailed", e.getMessage()));
            }
        }
    }

    private void showToast(String message, String color) {
        javafx.scene.control.Label label = new javafx.scene.control.Label("✅ " + message);
        label.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-padding: 12 24; -fx-background-radius: 8; -fx-font-weight: bold;");
        StackPane.setAlignment(label, javafx.geometry.Pos.TOP_CENTER);
        StackPane.setMargin(label, new javafx.geometry.Insets(20, 0, 0, 0));
        label.setOpacity(0);
        rootPane.getChildren().add(label);

        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), label);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), label);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> rootPane.getChildren().remove(label));
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        pause.setOnFinished(e -> fadeOut.play());
        fadeIn.setOnFinished(e -> pause.play());
        fadeIn.play();
    }

    private void showWarning(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(I18N.get("title.warning"));
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18N.get("title.error"));
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
