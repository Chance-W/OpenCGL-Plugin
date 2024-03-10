package com.opencgl.utils.wsdl2soap.builder.core;//package com.wsdl2soap.builder.core;
//
//import com.wsdl2soap.builder.SoapBuilder;
//import com.wsdl2soap.builder.SoapOperation;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.apache.log4j.Logger;
//import org.dom4j.Document;
//import org.dom4j.DocumentException;
//import org.dom4j.DocumentHelper;
//import org.dom4j.Element;
//
//import java.io.IOException;
//import java.util.List;
//import java.util.Objects;
//
//public class SoapMessageUtils {
//
//    private final static Logger log = Logger.getLogger(SoapMessageUtils.class);
//
//    /**
//     * 构建最终请求WebService的参数报文
//     * @param wsdlUrl
//     * @param namespace
//     * @param soapBinding
//     * @param method
//     * @param paramJson
//     * @return
//     * @throws DocumentException
//     * @throws IOException
//     */
//    public static String buildRealInputMessage(String wsdlUrl,
//                                                String namespace,
//                                                String soapBinding,
//                                                String method,
//                                                String paramJson)  {
//        checkHasLength(wsdlUrl, namespace, soapBinding, method);
//        String request = buildXmlMessage(wsdlUrl, namespace, soapBinding, method);
//        if (request.length() == 0 || isNullOrEmpty(paramJson)) {
//            return request;
//        }
//
//        try {
//            ObjectMapper objectMapper = new ObjectMapper();
//            JsonNode jsonNode = objectMapper.readTree(paramJson);
//            Document document = DocumentHelper.parseText(request);
//            Element paramNode = document.getRootElement().element("Body").element(method);
//            replaceParamXml(paramNode, jsonNode);
//            return document.asXML();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return "";
//    }
//
//    /**
//     * 真实参数去替换生成的请求报文
//     * @param paramNode
//     * @param jsonNode
//     */
//    private static void replaceParamXml(Element paramNode, JsonNode jsonNode) {
//        List<Element> elements = paramNode.elements();
//        for (Element element : elements) {
//            if (Objects.isNull(element.elements()) || element.elements().isEmpty()) {
//                // 说明没有子集直接设值
//                String jsonText = jsonNode.findPath(element.getName()).asText();
//                element.setText(jsonText);
//            } else {
//                replaceParamXml(element, jsonNode);
//            }
//        }
//    }
//
//    /**
//     * 构建无参数的XML请求报文模板
//     * @param wsdlUrl
//     * @param namespace
//     * @param soapBinding
//     * @param method
//     * @return
//     */
//    private static String buildXmlMessage(String wsdlUrl,
//                                          String namespace,
//                                          String soapBinding,
//                                          String method) {
//        Wsdl parse = Wsdl.parse(wsdlUrl);
//        SoapBuilder builder = parse.binding().name("{" + namespace + "}" + soapBinding).find();
//        for (SoapOperation operation : builder.getOperations()) {
//            if (operation.getOperationName().equals(method)) {
//                String inputMessage = builder.buildInputMessage(operation);
//                return inputMessage;
//            }
//        }
//        log.info("该Wsdl中找不到method:[" + method +"]方法");
//        return "";
//    }
//
//    /**
//     * 验证参数是否为空
//     * @param str
//     */
//    private static void checkHasLength(String... str) {
//        for (String s : str) {
//            if (Objects.isNull(s) || s.length() == 0) {
//                throw new IllegalArgumentException("调用WebService入参为空");
//            }
//        }
//    }
//    /**
//     * 验证参数是否为空
//     * @param str
//     */
//    private static boolean isNullOrEmpty(String str) {
//        if (Objects.isNull(str) || str.length() == 0) {
//            return true;
//        }
//        return false;
//    }
//}
