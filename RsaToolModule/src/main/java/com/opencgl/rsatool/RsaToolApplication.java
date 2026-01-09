package com.opencgl.rsatool;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * RSA工具模块启动类
 * 包含：密钥生成、加密解密、签名验签
 */
public class RsaToolApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/opencgl/rsatool/views/RsaToolView.fxml"));
        Parent root = loader.load();
        
        Scene scene = new Scene(root, 1200, 850);
        primaryStage.setTitle("🔐 RSA工具箱");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
