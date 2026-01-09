package com.opencgl.utils.wsdl2soap;

import com.opencgl.base.model.Base;
import com.opencgl.model.FileTypeEnum;
import com.opencgl.model.SoapWidgetDto;

import org.apache.commons.lang.StringUtils;
import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class Dom4jUtil {
    private static final Logger log = LoggerFactory.getLogger(Dom4jUtil.class);
    private static final String BASE_DIRECTORY = Base.BASE_PATH + "conf" + File.separator + "webservice" + File.separator;
    private static final String PREFIX = "<![CDATA[";
    private static final String SUFFIX = "]]>";
    private static final SAXReader READER = new SAXReader();

    public static List<SoapWidgetDto> parseUtil(File[] files) throws Exception {
        List<SoapWidgetDto> soapWidgetDtos = new ArrayList<>();
        for (File file : files) {
            log.info("deal file {}", file.getName());
            InputStream inputStream = new FileInputStream(file);
            //将输入流对象当中所读取到的xml文件数据转换为一个document对象
            Document document = READER.read(inputStream);
            //进行根结点对象的获取操作
            Element root = document.getRootElement();
            log.info("root node is {}", root.getName());
            //建立一个迭代器对象用于实现对根结点对象当中的子结点进行遍历操作
            Iterator<?> listIterator = root.elementIterator();
            while (listIterator.hasNext()) {
                SoapWidgetDto soap = new SoapWidgetDto();
                Element soapNode = (Element) listIterator.next();
                //开始对soap子节点对象当中的数据进行获取
                // 对soap标签对象当中属性值进行获取操作，并以集合的形式进行返回
                @SuppressWarnings("unchecked")
                List<Attribute> attributes = soapNode.attributes();
                Iterator<?> soapInfoIterator = soapNode.elementIterator();

                while (soapInfoIterator.hasNext()) {
                    Element childNode = (Element) soapInfoIterator.next();
                    log.info("子结点为:" + childNode.getName());
                    soap.setFirstLevel(file.getName().replace(FileTypeEnum.XML, ""));
                    if ("soapType".equals(childNode.getName())) {
                        log.info(childNode.getTextTrim());
                        soap.setSoapType(childNode.getTextTrim());
                    }
                    if ("soapName".equals(childNode.getName())) {
                        log.info(childNode.getTextTrim());
                        soap.setSecondLevel(childNode.getTextTrim());
                    }
                    if ("soapBindingOperationName".equals(childNode.getName())) {
                        log.info(childNode.getTextTrim());
                        soap.setThirdLevel(childNode.getTextTrim());
                    }
                    if ("soapRequestName".equals(childNode.getName())) {
                        log.info(childNode.getTextTrim());
                        soap.setFourthLevel(childNode.getTextTrim());
                    }
                    if ("soapRequestMessage".equals(childNode.getName())) {
                        log.info(childNode.getTextTrim());
                        soap.setSoapRequestMessage(childNode.getTextTrim());
                    }
                }
                if (!"sample".equals(soap.getSoapType())) {
                    soapWidgetDtos.add(soap);
                }
            }
            inputStream.close();
        }
        return soapWidgetDtos;
    }


    public static Boolean judgeRequestNameIfRepeat(SoapWidgetDto soap, String requestName) throws Exception {
        // SAXReader reader = new SAXReader();
        Document document = READER.read(new File(BASE_DIRECTORY + soap.getFirstLevel() + FileTypeEnum.XML));
        Element root = document.getRootElement();
        @SuppressWarnings("unchecked")
        List<Element> list = root.elements("soapInfo");
        Element element;
        for (Element value : list) {
            element = value;
            Element soapType = element.element("soapType");
            if ("request".equals(soapType.getText())) {
                Element soapBindingOperationName = element.element("soapBindingOperationName");
                Element soapName = element.element("soapName");
                Element soapRequestName = element.element("soapRequestName");
                if (soapBindingOperationName.getText().equals(soap.getThirdLevel())
                    && soapName.getText().equals(soap.getSecondLevel())) {
                    if (requestName.equals(soapRequestName.getText())) {
                        //名称重复
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static void appendSoapDtoXml(SoapWidgetDto soap) throws Exception {
        File file = new File(BASE_DIRECTORY + soap.getFirstLevel() + FileTypeEnum.XML);
        Element root;
        Document document;
        if (file.exists()) {
            //  SAXReader reader = new SAXReader();
            document = READER.read(file);
            root = document.getRootElement();
        }
        else {
            boolean directoryRes = file.getParentFile().mkdirs();
            //boolean fileRes = file.mkdir();
            if (!directoryRes) {
                log.error(file.getParentFile().getName() + "-----------目录创建失败");
            }
            document = DocumentHelper.createDocument();
            root = document.addElement("soapProject");
            root.addAttribute("requestUrl", StringUtils.join(soap.getRequestUrl(), ","));
            root.addAttribute("requestWSDL", soap.getRequestWsdl());
        }
        Element soapInfo = root.addElement("soapInfo");
        Element soapType = soapInfo.addElement("soapType");
        Element soapName = soapInfo.addElement("soapName");
        Element soapBindingOperationName = soapInfo.addElement("soapBindingOperationName");
        Element soapRequestName = soapInfo.addElement("soapRequestName");
        Element soapRequestMessage = soapInfo.addElement("soapRequestMessage");
        //document.add(root);
        soapType.setText(soap.getSoapType());
        soapName.setText(soap.getSecondLevel());
        soapBindingOperationName.setText(soap.getThirdLevel());
        soapRequestName.setText(soap.getFourthLevel());
        soapRequestMessage.setText(PREFIX + soap.getSoapRequestMessage() + SUFFIX);

        // 格式化输出流，同时指定编码格式。也可以在FileOutputStream中指定。
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("utf-8");
        // format.setTrimText(false);
        XMLWriter writer = new XMLWriter(new FileOutputStream(BASE_DIRECTORY + soap.getFirstLevel() + FileTypeEnum.XML), format);
        try {
            writer.setEscapeText(false);
            writer.write(document);

        }
        catch (Exception e) {
            log.error("", e);
            throw new RuntimeException(e);
        }
        finally {
            writer.close();
        }

    }

    public static void modSoapDtoXmlValue(SoapWidgetDto soap, String newRequestMessage) throws Exception {
        // SAXReader reader = new SAXReader();
        Document document = READER.read(new File(BASE_DIRECTORY + soap.getFirstLevel() + FileTypeEnum.XML));
        Element root = document.getRootElement();
        @SuppressWarnings("unchecked")
        List<Element> list = root.elements("soapInfo");
        Element element;
        for (Element value : list) {
            element = value;
            Element soapType = element.element("soapType");
            if ("sample".equals(soapType.getText())) {
                continue;
            }
            Element soapName = element.element("soapName");
            Element soapBindingOperationName = element.element("soapBindingOperationName");
            Element soapRequestName = element.element("soapRequestName");

            if (soapName.getText().equals(soap.getSecondLevel())
                && soapBindingOperationName.getText().equals(soap.getThirdLevel())
                && soapRequestName.getText().equals(soap.getFourthLevel())) {
                Element soapRequestMessage = element.element("soapRequestMessage");
                soapRequestMessage.setText(PREFIX + newRequestMessage + SUFFIX);
            }
        }
        // 格式化输出流，同时指定编码格式。也可以在FileOutputStream中指定。
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("utf-8");

        XMLWriter writer = new XMLWriter(new FileOutputStream(BASE_DIRECTORY + soap.getFirstLevel() + FileTypeEnum.XML), format);
        try {
            writer.setEscapeText(false);
            writer.write(document);
            writer.close();
        }
        catch (Exception e) {
            log.error("", e);
        }
    }


    public static void modSoapDtoXmlName(SoapWidgetDto oldSoap, String newTreeName) throws Exception {
        Document document = READER.read(new File(BASE_DIRECTORY + oldSoap.getFirstLevel() + FileTypeEnum.XML));
        Element root = document.getRootElement();
        @SuppressWarnings("unchecked")
        List<Element> list = root.elements("soapInfo");
        Element element;
        for (Element value : list) {
            element = value;
            Element soapType = element.element("soapType");
            if ("sample".equals(soapType.getText())) {
                continue;
            }
            Element soapName = element.element("soapName");
            Element soapBindingOperationName = element.element("soapBindingOperationName");
            Element soapRequestName = element.element("soapRequestName");

            if (soapName.getText().equals(oldSoap.getSecondLevel())
                && soapBindingOperationName.getText().equals(oldSoap.getThirdLevel())
                && soapRequestName.getText().equals(oldSoap.getFourthLevel())) {
                soapRequestName.setText(newTreeName);
            }
        }
        // 格式化输出流，同时指定编码格式。也可以在FileOutputStream中指定。
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("utf-8");

        XMLWriter writer = new XMLWriter(new FileOutputStream(BASE_DIRECTORY + oldSoap.getFirstLevel() + FileTypeEnum.XML), format);
        try {
            writer.setEscapeText(false);
            writer.write(document);
            writer.close();
        }
        catch (Exception e) {
            log.error("", e);
        }
    }


    public static void removeSpecifiedNode(SoapWidgetDto soap) throws DocumentException, IOException {

        Document document = READER.read(new File(BASE_DIRECTORY + soap.getFirstLevel() + FileTypeEnum.XML));
        Element root = document.getRootElement();
        Element rootElement = document.getRootElement();
        @SuppressWarnings("unchecked")
        List<Element> list = root.elements("soapInfo");
        Element element;
        for (Element value : list) {
            element = value;
            Element soapType = element.element("soapType");
            if ("sample".equals(soapType.getText())) {
                continue;
            }
            Element soapBindingOperationName = element.element("soapBindingOperationName");
            Element soapRequestName = element.element("soapRequestName");

            if (soapBindingOperationName.getText().equals(soap.getThirdLevel())
                && soapRequestName.getText().equals(soap.getFourthLevel())) {
                rootElement.remove(value);
            }
        }
        // 格式化输出流，同时指定编码格式。也可以在FileOutputStream中指定。
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("utf-8");
        XMLWriter writer = new XMLWriter(new FileOutputStream(BASE_DIRECTORY + soap.getFirstLevel() + FileTypeEnum.XML), format);
        try {
            writer.setEscapeText(false);
            writer.write(document);
        }
        catch (Exception e) {
            log.error("", e);
            throw new RuntimeException(e);
        }
        finally {
            writer.flush();
            writer.close();
        }

    }


    public static void copySampleToRequest(SoapWidgetDto soap, String requestName) throws Exception {
        // SAXReader reader = new SAXReader();
        Document document = READER.read(new File(BASE_DIRECTORY + soap.getFirstLevel() + FileTypeEnum.XML));
        Element root = document.getRootElement();
        @SuppressWarnings("unchecked")
        List<Element> list = root.elements("soapInfo");
        Element element;
        for (Element value : list) {
            element = value;
            Element soapType = element.element("soapType");
            if ("sample".equals(soapType.getText())) {
                Element soapBindingOperationName = element.element("soapBindingOperationName");
                Element soapName = element.element("soapName");
                Element soapRequestMessage = element.element("soapRequestMessage");
                //  Element soapRequestName = element.element("soapRequestName");
                if (soapBindingOperationName.getText().equals(soap.getThirdLevel())
                    && soapName.getText().equals(soap.getSecondLevel())) {
                    soap.setSoapType("request");
                    soap.setFourthLevel(requestName);
                    soap.setSoapRequestMessage(soapRequestMessage.getText());
                    appendSoapDtoXml(soap);

                }
            }

        }
    }

    public static SoapWidgetDto getSoapRequestMessage(SoapWidgetDto soapWidgetDto) throws Exception {
        //  SAXReader reader = new SAXReader();
        Document document = READER.read(new File(BASE_DIRECTORY + soapWidgetDto.getFirstLevel() + FileTypeEnum.XML));
        Element root = document.getRootElement();
        @SuppressWarnings("unchecked")
        List<Element> list = root.elements("soapInfo");
        Element element;
        for (Element value : list) {
            element = value;
            Element soapType = element.element("soapType");
            if ("request".equals(soapType.getText())) {
                Element soapBindingOperationName = element.element("soapBindingOperationName");
                Element soapName = element.element("soapName");
                Element soapRequestName = element.element("soapRequestName");
                Element soapRequestMessage = element.element("soapRequestMessage");
                if (soapBindingOperationName.getText().equals(soapWidgetDto.getThirdLevel())
                    && soapName.getText().equals(soapWidgetDto.getSecondLevel())
                    && soapRequestName.getText().equals(soapWidgetDto.getFourthLevel())) {
                    soapWidgetDto.setSoapRequestMessage(soapRequestMessage.getText());
                }
            }
        }
        return soapWidgetDto;
    }

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

    public static String getRequestUrl(SoapWidgetDto soapWidgetDto) throws DocumentException {
        Document document = READER.read(new File(BASE_DIRECTORY + soapWidgetDto.getFirstLevel() + FileTypeEnum.XML));
        Element root = document.getRootElement();
        return root.attribute("requestUrl").getValue();
    }

    public static String getRequestWsdl(String file) throws DocumentException {
        Document document = READER.read(new File(BASE_DIRECTORY + file + FileTypeEnum.XML));
        Element root = document.getRootElement();
        return root.attribute("requestWSDL").getValue();
    }

}
