package com.opencgl;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * AES工具模块启动类
 */
public class AesToolApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/opencgl/aestool/views/AesToolView.fxml"));
        Parent root = loader.load();
        
        Scene scene = new Scene(root, 900, 600);
        primaryStage.setTitle("🔐 AES加解密工具");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
