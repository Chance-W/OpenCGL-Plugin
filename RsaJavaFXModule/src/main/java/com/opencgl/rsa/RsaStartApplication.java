package com.opencgl.rsa;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * RSA密钥生成器独立测试应用
 * 
 * @author Chance.W
 * @date 2025-12-31
 */
public class RsaStartApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/com/opencgl/rsa/views/RsaKeyGeneratorView.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1400, 950);
        primaryStage.setTitle("RSA密钥生成工具 - 支持加密/解密/签名/验签");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
