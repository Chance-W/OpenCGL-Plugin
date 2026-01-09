package com.opencgl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.palexdev.materialfx.theming.JavaFXThemes;
import io.github.palexdev.materialfx.theming.MaterialFXStylesheets;
import io.github.palexdev.materialfx.theming.UserAgentBuilder;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;


public class RocketMqConsumerNewMainPlugin extends Application {

    private static final Logger logger = LoggerFactory.getLogger(RocketMqConsumerNewMainPlugin.class);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(this.getClass().getClassLoader().getResource("RocketMqConsumerWidgetView.fxml"));
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
        UserAgentBuilder.builder()
            .themes(JavaFXThemes.MODENA)
            .themes(MaterialFXStylesheets.forAssemble(true))
            .setDeploy(true)
            .setResolveAssets(true)
            .build()
            .setGlobal();
        stage.setScene(scene);
        stage.show();
    }
}
