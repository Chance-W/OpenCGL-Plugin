package com.opencgl.variable;

import com.opencgl.api.PluginUI;
import com.opencgl.variable.i18n.I18N;
import javafx.fxml.FXMLLoader;
import java.io.IOException;
import java.net.URL;

public class VariableGeneratorPlugin implements PluginUI {

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

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
        // 使用默认图标或加载特定图标
        return getClass().getResource("/com/opencgl/variable/icon.png");
    }

    @Override
    public Object createView() {
        try {
            URL resource = getClass().getResource("/VariableGeneratorView.fxml");
            if (resource == null) {
                throw new RuntimeException("Cannot find VariableGeneratorView.fxml");
            }
            FXMLLoader loader = new FXMLLoader(resource);
            loader.setClassLoader(this.getClass().getClassLoader());
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            return loader.load();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
