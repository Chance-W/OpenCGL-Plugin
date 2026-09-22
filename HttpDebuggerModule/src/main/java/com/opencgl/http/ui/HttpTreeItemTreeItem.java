package com.opencgl.http.ui;

import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.http.model.HttpTreeItem;

/** HTTP tree item whose disclosure arrow reflects the materialized children. */
public final class HttpTreeItemTreeItem extends CustomizeTreeItem<HttpTreeItem> {
    public HttpTreeItemTreeItem(HttpTreeItem value) {
        super(value);
    }

    @Override
    public boolean isLeaf() {
        return getChildren().isEmpty();
    }
}
