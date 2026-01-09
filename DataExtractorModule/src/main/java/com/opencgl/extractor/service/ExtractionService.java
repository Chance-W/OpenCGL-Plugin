package com.opencgl.extractor.service;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据提取服务
 */
public class ExtractionService {
    private static final Logger logger = LoggerFactory.getLogger(ExtractionService.class);
    
    /**
     * 使用 JsonPath 提取 JSON 数据
     */
    public ExtractionResult extractJson(String json, String jsonPath) {
        try {
            Configuration conf = Configuration.builder()
                .options(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS)
                .build();
            
            Object result = JsonPath.using(conf).parse(json).read(jsonPath);
            
            return new ExtractionResult(true, formatResult(result), null);
        } catch (Exception e) {
            return new ExtractionResult(false, null, e.getMessage());
        }
    }
    
    /**
     * 使用 XPath 提取 XML 数据
     */
    public ExtractionResult extractXml(String xml, String xpathExpr) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new ByteArrayInputStream(xml.getBytes()));
            
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            
            NodeList nodeList = (NodeList) xpath.compile(xpathExpr).evaluate(doc, XPathConstants.NODESET);
            
            List<String> results = new ArrayList<>();
            for (int i = 0; i < nodeList.getLength(); i++) {
                results.add(nodeList.item(i).getTextContent());
            }
            
            String resultStr = results.isEmpty() ? "无匹配结果" : String.join("\n", results);
            return new ExtractionResult(true, resultStr, null);
        } catch (Exception e) {
            return new ExtractionResult(false, null, e.getMessage());
        }
    }
    
    /**
     * 格式化提取结果
     */
    private String formatResult(Object result) {
        if (result == null) {
            return "null";
        }
        
        if (result instanceof List) {
            List<?> list = (List<?>) result;
            if (list.isEmpty()) {
                return "[]";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                sb.append("[").append(i).append("] ").append(list.get(i)).append("\n");
            }
            return sb.toString().trim();
        }
        
        return result.toString();
    }
    
    /**
     * 常用 JsonPath 表达式
     */
    public static final List<PathTemplate> JSON_TEMPLATES = List.of(
        new PathTemplate("根对象", "$", "获取整个 JSON 对象"),
        new PathTemplate("单个字段", "$.fieldName", "获取根对象的字段"),
        new PathTemplate("嵌套字段", "$.parent.child", "获取嵌套对象的字段"),
        new PathTemplate("数组元素", "$.array[0]", "获取数组第一个元素"),
        new PathTemplate("所有数组元素", "$.array[*]", "获取数组所有元素"),
        new PathTemplate("数组切片", "$.array[0:5]", "获取数组前5个元素"),
        new PathTemplate("递归查找", "$..fieldName", "递归查找所有 fieldName"),
        new PathTemplate("条件过滤", "$.array[?(@.age > 18)]", "过滤数组元素"),
        new PathTemplate("多字段", "$.['name', 'age']", "获取多个字段"),
        new PathTemplate("数组长度", "$.array.length()", "获取数组长度")
    );
    
    /**
     * 常用 XPath 表达式
     */
    public static final List<PathTemplate> XPATH_TEMPLATES = List.of(
        new PathTemplate("根元素", "/root", "选择根元素"),
        new PathTemplate("子元素", "/root/child", "选择子元素"),
        new PathTemplate("所有子元素", "/root/*", "选择所有子元素"),
        new PathTemplate("递归查找", "//element", "递归查找所有 element"),
        new PathTemplate("属性选择", "/root/@attribute", "选择属性"),
        new PathTemplate("按属性过滤", "//element[@id='123']", "按属性过滤元素"),
        new PathTemplate("文本内容", "//element/text()", "获取元素文本"),
        new PathTemplate("第N个元素", "//element[1]", "选择第一个元素（1-based）"),
        new PathTemplate("条件过滤", "//item[price>100]", "按条件过滤"),
        new PathTemplate("父元素", "//child/parent::*", "选择父元素")
    );
    
    public record ExtractionResult(boolean success, String result, String error) {}
    public record PathTemplate(String name, String expression, String description) {}
}
