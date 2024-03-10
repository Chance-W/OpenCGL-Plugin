package com.opencgl;

import com.jfoenix.controls.JFXDecorator;
import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NewMainPlugin extends Application {
    private static final Logger log = LoggerFactory.getLogger(NewMainPlugin.class);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
      /*  CompletableFuture.runAsync(() -> {
            try {
                Font.loadFont(NewMainPlugin.class.getResourceAsStream("css/font-awesome-4.7.0/fonts/fontawesome-webfont.ttf"), -1);
                SVGGlyphLoader.loadGlyphsFont(NewMainPlugin.class.getResourceAsStream("./fonts/icomoon.svg"),
                    "icomoon.svg");
            }
            catch (IOException ioExc) {
                log.error("", ioExc);
            }
        });*/
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(this.getClass().getClassLoader().getResource("SoapTestModule.fxml"));
        JFXDecorator decorator = new JFXDecorator(stage, loader.load());
        decorator.setCustomMaximize(true);
        stage.setTitle("OpenCGL");
        double width = 0;
        double height = 0;
        try {
            Rectangle2D bounds = Screen.getScreens().get(0).getBounds();
            width = bounds.getWidth() / 1.68;
            height = bounds.getHeight() / 1.35;
        }
        catch (Exception e) {
            log.error("", e);

        }
        Scene scene = new Scene(decorator, width, height);
        ObservableList<String> stylesheets = scene.getStylesheets();
        stylesheets.addAll(Objects.requireNonNull(getClass().getClassLoader().getResource("css/jfoenix-fonts.css")).toExternalForm(),
            Objects.requireNonNull(getClass().getClassLoader().getResource("css/jfoenix-design.css")).toExternalForm(),
            Objects.requireNonNull(getClass().getClassLoader().getResource("css/jfoenix-main-demo.css")).toExternalForm());
        stage.setScene(scene);
        stage.show();
    }
}
