package com.opencgl.timestamp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * 时间戳工具应用入口
 */
public class TimestampToolApplication extends Application {
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/TimestampToolView.fxml"));
        primaryStage.setTitle("时间戳工具");
        primaryStage.setScene(new Scene(root, 1000, 700));
        primaryStage.show();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
