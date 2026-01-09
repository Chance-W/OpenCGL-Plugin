package com.opencgl;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * AI Q&A 模块独立启动入口
 */
public class AiQaAppLauncherPlugin extends Application {

    public static void main(String[] args) {
        Application.launch(AiQaAppLauncherPlugin.class, args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(this.getClass().getClassLoader().getResource("AiQaView.fxml"));
        Parent root = loader.load();
        
        Scene scene = new Scene(root, 1000, 700);
        stage.setScene(scene);
        stage.setTitle("AI 知识问答");
        stage.show();
    }
}
