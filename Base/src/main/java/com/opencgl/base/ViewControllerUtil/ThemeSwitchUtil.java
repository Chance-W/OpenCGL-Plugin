package com.opencgl.base.ViewControllerUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.alibaba.fastjson.JSONObject;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Pane;
import lombok.SneakyThrows;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class ThemeSwitchUtil {
    private static final String THEME_NAME = System.getProperty("user.home") + "/.opencgl/conf/theme/theme.json";

    private static final File FILE = new File(THEME_NAME);


    @SneakyThrows
    public static void treeStyleSwitch(Pane mainStackPane, Pane contentBorderPane, TreeView<?> treeView) {
        mainStackPane.getChildren().addAll(CommonPaneUsualTemplateController.buildPane(contentBorderPane, treeView));
    }

    public static Object getTreeStyle() throws IOException {
        checkThemeFile();
        try (InputStream stream = new FileInputStream(FILE)) {
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject jsonObject = JSONObject.parseObject(content);
            return jsonObject.get("treeStyle");
        }
    }

    public static void checkThemeFile() throws IOException {
        if (!FILE.exists()) {
            boolean a = FILE.getParentFile().mkdirs();
            boolean b = FILE.createNewFile();
            FileOutputStream fos = new FileOutputStream(FILE);
            byte[] bytesArray = "{\"treeStyle\":\"leftTree\"}".getBytes();
            fos.write(bytesArray);
            fos.flush();
            fos.close();
        }
    }


}
