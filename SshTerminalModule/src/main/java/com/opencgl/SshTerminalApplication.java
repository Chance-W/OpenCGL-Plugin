package com.opencgl;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * SSH终端模块启动类
 */
public class SshTerminalApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/opencgl/ssh/views/SshTerminalView.fxml"));
        Parent root = loader.load();
        
        Scene scene = new Scene(root, 1000, 700);
        primaryStage.setTitle(com.opencgl.ssh.i18n.I18N.getOrDefault("app.title", "🖥️ SSH终端"));
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
