package com.opencgl;

import io.github.palexdev.materialfx.theming.JavaFXThemes;
import io.github.palexdev.materialfx.theming.MaterialFXStylesheets;
import io.github.palexdev.materialfx.theming.UserAgentBuilder;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.ResourceBundle;

public class CertGeneratorStartApplication extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("com.opencgl.cert.i18n.CertGenerator");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/opencgl/cert/views/CertGeneratorView.fxml"), bundle);
        Parent root = loader.load();

        // Init MaterialFX Themes if desired, similar to TemplateLaunchApplication
        UserAgentBuilder.builder()
            .themes(JavaFXThemes.MODENA)
            .themes(MaterialFXStylesheets.forAssemble(true))
            .setDeploy(true)
            .setResolveAssets(true)
            .build()
            .setGlobal();

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("CertGenerator 调试器");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}

