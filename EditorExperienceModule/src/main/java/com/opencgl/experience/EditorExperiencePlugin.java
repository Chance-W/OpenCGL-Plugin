package com.opencgl.experience;

import com.opencgl.api.PluginUI;
import com.opencgl.experience.i18n.I18N;
import com.opencgl.experience.controller.EditorExperienceController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.net.URL;

public class EditorExperiencePlugin implements PluginUI {

    private EditorExperienceController controller;

    @Override
    public String directoryName() {
        return I18N.get("label.category");
    }

    @Override
    public String name() {
        return I18N.get("label.name");
    }

    @Override
    public URL iconPath() {
        // Can be null or path to icon
        return null;
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Object createView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/opencgl/editor/experience/views/EditorExperienceView.fxml"));
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            Parent root = loader.load();
            controller = loader.getController();
            return root;
        } catch (IOException e) {
            e.printStackTrace();
            return null; // Or a label with error
        }
    }

    @Override
    public void dispose() {
        if (controller != null) {
            controller.dispose();
            controller = null;
        }
    }
}
