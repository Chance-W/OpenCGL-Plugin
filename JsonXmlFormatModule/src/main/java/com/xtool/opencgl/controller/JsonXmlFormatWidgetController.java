package com.xtool.opencgl.controller;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ResourceBundle;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.xtool.opencgl.util.Dom4jUtil;
import com.xtool.opencgl.views.JsonXmlFormatWidgetView;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tooltip;
import lombok.SneakyThrows;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class JsonXmlFormatWidgetController extends JsonXmlFormatWidgetView implements Initializable {
    private final Logger logger = LoggerFactory.getLogger(JsonXmlFormatWidgetController.class);

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        //设置控件
        setControlAndStyle();
    }


    @FXML
    public void formatLabelAction() {
        if (StringUtils.isEmpty(inputTextArea.getText())) {
            outputTextArea.setText("格式化字符不能为空");
            return;
        }
        try {
            if ("JSON".equals(formatTypeComboBox.getValue())) {
                outputTextArea.setText(JSON.toJSONString(JSONObject.parseObject(inputTextArea.getText()), SerializerFeature.PrettyFormat, SerializerFeature.WriteDateUseDateFormat));
            }
            else {
                outputTextArea.setText(Dom4jUtil.formatXml(inputTextArea.getText()));
            }
        }
        catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw, true));
            logger.error("", e);
            outputTextArea.setText(sw.toString());
        }
    }

    public void setControlAndStyle() {
        formatButton.setTooltip(new Tooltip("格式化"));
        formatTypeComboBox.selectFirst();
        formatTypeComboBox.setText("JSON");
    }
}


