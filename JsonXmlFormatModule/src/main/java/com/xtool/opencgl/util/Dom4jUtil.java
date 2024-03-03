package com.xtool.opencgl.util;


import java.io.StringWriter;

import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@SuppressWarnings("unused")
public class Dom4jUtil {
    private static final Logger logger = LoggerFactory.getLogger(Dom4jUtil.class);

    public static String formatXml(String str) throws Exception {
        Document document = DocumentHelper.parseText(str);
        // 格式化输出格式
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("gb2312");
        StringWriter writer = new StringWriter();
        // 格式化输出流
        XMLWriter xmlWriter = new XMLWriter(writer, format);
        // 将document写入到输出流
        xmlWriter.write(document);
        xmlWriter.close();
        return writer.toString().replace("<?xml version=\"1.0\" encoding=\"gb2312\"?>", "").trim();
    }

}