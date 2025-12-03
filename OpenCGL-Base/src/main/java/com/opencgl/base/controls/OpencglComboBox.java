package com.opencgl.base.controls;

import javafx.animation.RotateTransition;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.util.Duration;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/07/08 14:47
 * @since v9.0
 */
public class OpencglComboBox<T> extends ComboBox<T> {

    private static final String OPENCGL_COMBOBOX_STYLE_CLASS = "opencgl-combo-box";

    public OpencglComboBox() {
        super();
        init();
    }

    public OpencglComboBox(ObservableList<T> items) {
        super(items);
        init();
    }

    private void init() {
        getStyleClass().add(OPENCGL_COMBOBOX_STYLE_CLASS);
        getStylesheets().setAll(this.getClass().getResource("/com/opencgl/base/css/OpencglComboBox.css").toExternalForm());

        setOnShowing(event -> {
            Node arrow = this.lookup(".arrow");
            if (arrow != null) {
                RotateTransition transition = new RotateTransition(Duration.millis(200), arrow);
                transition.setFromAngle(0);
                transition.setToAngle(180);
                transition.play();
            }
        });

        setOnHiding(event -> {
            Node arrow = this.lookup(".arrow");
            if (arrow != null) {
                RotateTransition transition = new RotateTransition(Duration.millis(200), arrow);
                transition.setFromAngle(180);
                transition.setToAngle(0);
                transition.play();
            }
        });
    }


}
