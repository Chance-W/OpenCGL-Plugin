package com.opencgl.regex;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class RegexToolApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/RegexToolView.fxml"));
        primaryStage.setTitle("Regex Tool (Dev)");
        primaryStage.setScene(new Scene(root, 900, 600));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
