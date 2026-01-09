package com.opencgl.pathextractor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

public class XPathService {
    private static final Logger logger = LoggerFactory.getLogger(XPathService.class);
    
    public String extract(String xml, String path) {
        if (xml == null || xml.trim().isEmpty()) {
            return "错误: XML内容为空";
        }
        
        if (path == null || path.trim().isEmpty()) {
            return "错误: 路径表达式为空";
        }
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            
            XPath xpath = XPathFactory.newInstance().newXPath();
            
            // 尝试作为NodeList
            NodeList nodes = (NodeList) xpath.evaluate(path, doc, XPathConstants.NODESET);
            if (nodes != null && nodes.getLength() > 0) {
                StringBuilder result = new StringBuilder();
                result.append("找到 ").append(nodes.getLength()).append(" 个节点:\n\n");
                
                for (int i = 0; i < nodes.getLength(); i++) {
                    result.append("[").append(i + 1).append("] ");
                    result.append(nodes.item(i).getTextContent().trim());
                    result.append("\n");
                }
                return result.toString();
            }
            
            // 尝试作为String
            String textResult = xpath.evaluate(path, doc);
            if (textResult != null && !textResult.isEmpty()) {
                return textResult;
            }
            
            return "未找到匹配结果";
            
        } catch (Exception e) {
            logger.error("XPath提取失败", e);
            return "错误: " + e.getMessage();
        }
    }
    
    public boolean isValidXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new InputSource(new StringReader(xml)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
