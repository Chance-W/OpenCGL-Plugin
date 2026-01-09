package com.opencgl.sqlclient;

import com.opencgl.api.PluginUI;
import com.opencgl.sqlclient.controller.SqlClientController;
import com.opencgl.sqlclient.i18n.I18N;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

public class SqlClientPluginUI implements PluginUI {

    private static final Logger logger = LoggerFactory.getLogger(SqlClientPluginUI.class);
    private SqlClientController controller;

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
        return null;
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Node createView() {
        try {
            URL fxmlUrl = getClass().getResource("/com/opencgl/sqlclient/views/SqlClientView.fxml");
            if (fxmlUrl == null) {
                throw new RuntimeException("FXML not found");
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            loader.setClassLoader(getClass().getClassLoader());
            loader.setResources(I18N.getBundle(I18N.getLocale()));
            controller = new SqlClientController();
            loader.setController(controller);
            return loader.load();

        } catch (Exception e) {
            logger.error("Load SQL client UI failed", e);
            throw new RuntimeException("Load SQL client UI failed", e);
        }
    }

    @Override
    public void dispose() {
        SqlClientController controllerToDispose = controller;
        controller = null;
        if (controllerToDispose != null) {
            try {
                controllerToDispose.dispose();
            } catch (RuntimeException e) {
                logger.error("关闭 SQL 客户端资源失败", e);
            }
        }
        logger.info("SQL客户端插件已卸载");
    }
}
