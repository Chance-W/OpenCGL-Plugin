package com.opencgl.base.ViewControllerUtil;

import java.util.Objects;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

public class commonPaneUsualTemplateController extends commonPaneUsualTemplateView {
    public static StackPane buildPane(Node node, Node treeView) throws Exception {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(Objects.requireNonNull(commonPaneUsualTemplateController.class.getClassLoader().getResource("com/opencgl/base/views/CommonPaneUsualTemplateView.fxml")));
        loader.setController(new commonPaneUsualTemplateController());
        loader.load();
        commonPaneUsualTemplateController commonPaneTemplateController = loader.getController();
        return commonPaneTemplateController.init(node, treeView);
    }

    public StackPane init(Node node, Node treeView) {
        sideLeftTreeView.getChildren().add(treeView);
        content.getChildren().add(node);
        return mainStackPane;
    }
}
