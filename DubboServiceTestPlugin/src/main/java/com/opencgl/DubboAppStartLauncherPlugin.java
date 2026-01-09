package com.opencgl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.theme.ThemeManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;


public class DubboAppStartLauncherPlugin extends Application {
    private static final Logger logger = LoggerFactory.getLogger(DubboAppStartLauncherPlugin.class);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        // 初始化主题，调试时这里可以改成需要调试的主题
        ThemeManager.getInstance().init("default");

        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(this.getClass().getClassLoader().getResource("DubboWidgetView.fxml"));
        stage.setTitle("OpenCGL - Dubbo Plugin");
        double width = 0;
        double height = 0;
        try {
            Rectangle2D bounds = Screen.getScreens().getFirst().getBounds();
            width = bounds.getWidth() / 1.68;
            height = bounds.getHeight() / 1.35;
        }
        catch (Exception e) {
            logger.error("", e);
        }
        Scene scene = new Scene(loader.load(), width, height);
        
        // Register scene with ThemeManager to apply themes
        ThemeManager.getInstance().registerScene(scene);

        stage.setScene(scene);
        stage.show();
    }
}
