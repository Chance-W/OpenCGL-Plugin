package com.opencgl.plugin.ragingestion;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * RagIngestionToolModule 独立启动程序。
 * 允许在开发调试或无 OpenCGL 主程序启动时单独运行该模块全部功能。
 */
public class RagIngestionStartApplication extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(this.getClass().getResource("/com/opencgl/plugin/ragingestion/views/RagIngestionWidgetView.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1080, 720);
        stage.setTitle("OpenCGL - RAG 智能切片与多库向量化工作台 (RagIngestionToolModule)");
        stage.setScene(scene);
        stage.show();
    }
}
