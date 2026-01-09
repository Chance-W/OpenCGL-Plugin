package com.opencgl.encryptanddecrypt;

import java.io.IOException;
import java.net.URL;

import com.opencgl.api.PluginUI;
import com.opencgl.encryptanddecrypt.i18n.I18N;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class EncryptAndDecryptPluginUI implements PluginUI {

    private final ClassLoader pluginCl = this.getClass().getClassLoader();

    public EncryptAndDecryptPluginUI() {
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
        return this.getClass().getClassLoader().getResource("icon/decode.png");
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Object createView() {
        FXMLLoader loader = new FXMLLoader();
        loader.setClassLoader(pluginCl);
        loader.setResources(I18N.getBundle(I18N.getLocale()));
        loader.setLocation(this.getClass().getClassLoader().getResource("EncryptAndDecrypt.fxml"));
        try {
            return loader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dispose() {

    }
}
