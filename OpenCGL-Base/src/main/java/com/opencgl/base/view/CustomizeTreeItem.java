package com.opencgl.base.view;

import com.opencgl.base.model.BaseDataDto;
import javafx.scene.control.TreeItem;

public class CustomizeTreeItem<T extends BaseDataDto> extends TreeItem<T> {

    public CustomizeTreeItem(T value) {
        super(value);
    }

    public CustomizeTreeItem(){
        super();
    }

    @Override
    public boolean isLeaf() {
        return getValue().getIsLeaf();
    }

}
