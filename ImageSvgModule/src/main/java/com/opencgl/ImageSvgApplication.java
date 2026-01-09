package com.opencgl;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 图片转SVG模块启动类
 */
public class ImageSvgApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/opencgl/imagesvg/views/ImageSvgView.fxml"));
        Parent root = loader.load();
        
        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("🖼️ 图片转SVG工具");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
