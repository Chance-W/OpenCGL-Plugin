package com.opencgl;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Java代码生成器模块启动类
 */
public class CodeGenApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/opencgl/codegen/views/CodeGenView.fxml"));
        Parent root = loader.load();
        
        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("☕ Java代码生成器");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
