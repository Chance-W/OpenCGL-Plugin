package com.xtool.opencgl.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

/**
 * @author Chance.W
 */
public class BuildFourTreeDataUtil {

    public static void buildTree(List<FourTreeLevel> treeLevels, TreeItem<String> rootTreeItem) {
        Map<String, Map<String, Map<String, Map<String, FourTreeLevel>>>> cache = new LinkedHashMap<>(16);
        for (FourTreeLevel levels : treeLevels) {
            Map<String, Map<String, Map<String, FourTreeLevel>>> first =
                    cache.computeIfAbsent(levels.getFirstLevel(), a -> new LinkedHashMap<>());
            Map<String, Map<String, FourTreeLevel>> second =
                    first.computeIfAbsent(levels.getSecondLevel(), a -> new LinkedHashMap<>());
            Map<String, FourTreeLevel> third =
                    second.computeIfAbsent(levels.getThirdLevel(), a -> new LinkedHashMap<>());
            third.put(levels.getFourLevel(), levels);
        }

        ObservableList<TreeItem<String>> rootList = rootTreeItem.getChildren();
        cache.forEach((key, val) -> {
            TreeItem<String> node = new TreeItem<>(key);
            rootList.add(node);
            val.forEach((key2, val2) -> {
                TreeItem<String> node2 = new TreeItem<>(key2);
                node.getChildren().add(node2);
                val2.forEach((key3, val3) -> {
                    TreeItem<String> node3 = new TreeItem<>(key3);
                    node2.getChildren().add(node3);
                    val3.forEach((key4, val4) -> {
                        TreeItem<String> node4 = new TreeItem<>(key4);
                        node3.getChildren().add(node4);
                    });
                });
            });
        });
    }
}
