package com.opencgl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.palexdev.materialfx.css.themes.MFXThemeManager;
import io.github.palexdev.materialfx.css.themes.Themes;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;


public class NewMainPlugin extends Application {

    private static final Logger logger = LoggerFactory.getLogger(NewMainPlugin.class);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(this.getClass().getClassLoader().getResource("EncryptAndDecrypt.fxml"));
        stage.setTitle("OpenCGL");
        double width = 0;
        double height = 0;
        try {
            Rectangle2D bounds = Screen.getScreens().get(0).getBounds();
            width = bounds.getWidth() / 1.68;
            height = bounds.getHeight() / 1.35;
        }
        catch (Exception e) {
            logger.error("", e);
        }
        Scene scene = new Scene(loader.load(), width, height);
        MFXThemeManager.addOn(scene, Themes.DEFAULT, Themes.LEGACY);
        stage.setScene(scene);
        stage.show();
    }
}
