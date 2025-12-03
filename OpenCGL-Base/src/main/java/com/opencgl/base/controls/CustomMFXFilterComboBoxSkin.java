package com.opencgl.base.controls;

import java.util.Locale;

import org.apache.commons.lang.BooleanUtils;

import io.github.palexdev.materialfx.collections.TransformableList;
import io.github.palexdev.materialfx.controls.BoundTextField;
import io.github.palexdev.materialfx.controls.MFXFilterComboBox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import io.github.palexdev.materialfx.i18n.I18N;
import io.github.palexdev.materialfx.skins.MFXFilterComboBoxSkin;
import io.github.palexdev.virtualizedfx.unused.simple.SimpleVirtualFlow;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/07/09 22:44
 * @since v9.0
 */
public class CustomMFXFilterComboBoxSkin<T> extends MFXFilterComboBoxSkin<T> {

    public CustomMFXFilterComboBoxSkin(MFXFilterComboBox<T> comboBox, BoundTextField boundField) {
        super(comboBox, boundField);
    }

    @Override
    protected Node createPopupContent() {
        MFXFilterComboBox<T> comboBox = getComboBox();
        TransformableList<T> filterList = comboBox.getFilterList();

        MFXTextField searchField = new MFXTextField("", I18N.getOrDefault("filterCombo.search"));
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().bindBidirectional(comboBox.searchTextProperty());
        searchField.setMaxWidth(Double.MAX_VALUE);

        virtualFlow = new SimpleVirtualFlow<>(
            filterList,
            comboBox.getCellFactory(),
            Orientation.VERTICAL
        );
        virtualFlow.cellFactoryProperty().bind(comboBox.cellFactoryProperty());
        //virtualFlow.prefWidthProperty().bind(comboBox.widthProperty());


        virtualFlow.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (popup.isShowing()) {
                popup.hide();
            }
        });

        // 根据item动态调整virtualFlow宽度,observable展开时为true 收起时为false
        comboBox.showingProperty().addListener(observable -> {
            System.out.println(observable);
            if (BooleanUtils.isTrue(((ReadOnlyBooleanProperty) observable).get())) {
                double maxWidthValue = 0;
                for (int i = 0; i < comboBox.getItems().size(); i++) {
                    T t = comboBox.getItems().get(i);
                    double textWidth = t.toString().toLowerCase(Locale.ROOT).length() * 9;
                    maxWidthValue = Math.max(maxWidthValue, textWidth);
                }
                virtualFlow.setMinWidth(maxWidthValue);
            }
        });


        Runnable createBinding = () ->
            virtualFlow.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.min(comboBox.getRowsCount(), comboBox.getItems().size()) * virtualFlow.getCellHeight(),
                comboBox.rowsCountProperty(), comboBox.getItems(), virtualFlow.cellFactoryProperty(), vfInitialized
            ));

        virtualFlow.itemsProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                createBinding.run();
            }
            ;
        });
        createBinding.run();

        VBox container = new VBox(10, searchField, virtualFlow);
        container.getStyleClass().add("search-container");
        container.setAlignment(Pos.TOP_CENTER);
        return container;
    }
}
