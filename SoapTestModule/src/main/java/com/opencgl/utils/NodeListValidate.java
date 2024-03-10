package com.opencgl.utils;

import javafx.collections.ObservableList;
import javafx.scene.control.Tab;
import javafx.scene.control.TreeItem;

/**
 * @author Chance.W
 */
public class NodeListValidate {
    public static Boolean treeValidate(String value, ObservableList<TreeItem<String>> list) {
        for (TreeItem<String> treeItem : list) {
            if (treeItem.getValue().equals(value)) {
                return false;
            }
        }
        return true;
    }

    public static Long tabValidate(String value, ObservableList<Tab> list) {
        Long i = 0L;
        for (Tab tab : list) {
            i++;
            if (tab.getText().equals(value)) {
                return i;
            }
        }
        return 0L;
    }


}
