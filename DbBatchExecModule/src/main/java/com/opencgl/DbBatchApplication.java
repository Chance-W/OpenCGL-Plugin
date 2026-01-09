package com.opencgl;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class DbBatchApplication extends Application {
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/DbBatchExecView.fxml"));
        primaryStage.setTitle("数据库批量执行工具");
        primaryStage.setScene(new Scene(root, 1100, 750));
        primaryStage.show();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
