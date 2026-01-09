package com.opencgl.base.view;

import com.opencgl.base.model.BaseDataDto;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.TreeItem;

public class CustomizeTreeItem<T extends BaseDataDto> extends TreeItem<T> {

    private StringProperty nameBindingProperty;

    public CustomizeTreeItem(T value) {
        super(value);
    }

    public CustomizeTreeItem() {
        super();
    }

    public StringProperty nameBindingProperty() {
        if (nameBindingProperty == null) {
            nameBindingProperty = new SimpleStringProperty(this, "nameBinding");
        }
        return nameBindingProperty;
    }

    @Override
    public boolean isLeaf() {
        return getValue() != null ? getValue().getIsLeaf() : super.isLeaf();
    }

}
