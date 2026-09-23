package com.opencgl.http.ui;

import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.http.model.HttpTreeItem;

/** Compatibility alias; leaf semantics now come from the shared is_leaf implementation. */
@Deprecated
public final class HttpTreeItemTreeItem extends CustomizeTreeItem<HttpTreeItem> {
    public HttpTreeItemTreeItem(HttpTreeItem value) {
        super(value);
    }

}
