package com.opencgl.base.ViewControllerUtil;

import java.util.Objects;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.TreeView;
import javafx.scene.layout.StackPane;

public class CommonPaneUsualTemplateController extends CommonPaneUsualTepmpateView {
    public static StackPane buildPane(Node node, TreeView<?> treeView) throws Exception {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(Objects.requireNonNull(CommonPaneUsualTemplateController.class.getClassLoader().getResource("com/opencgl/base/views/CommonPaneUsualTepmpateView.fxml")));
        loader.setController(new CommonPaneUsualTemplateController());
        loader.load();
        CommonPaneUsualTemplateController commonPaneTepmpateController = loader.getController();
        return commonPaneTepmpateController.init(node, treeView);
    }

    public StackPane init(Node node, TreeView<?> treeView) {
        treeView.setStyle("-fx-background-color:WHITE");
        sideLeftTreeView.getChildren().add(treeView);
        content.getChildren().add(node);
        return mainStackPane;
    }
}
