
package com.opencgl.utils.wsdl2soap;


import com.opencgl.model.SoapWidgetDto;
import com.opencgl.utils.wsdl2soap.builder.SoapBuilder;
import com.opencgl.utils.wsdl2soap.builder.SoapOperation;
import com.opencgl.utils.wsdl2soap.builder.core.Wsdl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Collator;
import java.util.*;

@SuppressWarnings("unused")
public class BuildSoap {
    Logger log = LoggerFactory.getLogger(BuildSoap.class);

    /*   public static void main(String[] args) throws Exception {
           BuildSoap buildSoap= new BuildSoap("http://www.webxml.com.cn/WebServices/WeatherWebService.asmx?wsdl","111");
       }*/
    public static void buildSoap(String wsdlUrl, String ProjectName) throws Exception {
        List<SoapWidgetDto> list = buildXmlMessage(wsdlUrl, ProjectName);
        SoapWidgetDto soap;
        for (SoapWidgetDto soapWidgetDto : list) {
            soap = new SoapWidgetDto();
            soap.setRequestUrl(soapWidgetDto.getRequestUrl());
            soap.setRequestWsdl(soapWidgetDto.getRequestWsdl());
            soap.setSoapType(soapWidgetDto.getSoapType());
            soap.setFirstLevel(soapWidgetDto.getFirstLevel());
            soap.setSecondLevel(soapWidgetDto.getSecondLevel());
            soap.setThirdLevel(soapWidgetDto.getThirdLevel());
            soap.setFourthLevel(soapWidgetDto.getFourthLevel());
            soap.setSoapRequestMessage(soapWidgetDto.getSoapRequestMessage());
            //copy样例
            Dom4jUtil.appendSoapDtoXml(soap);
            //同时copy request
            soap.setSoapType("request");
            Dom4jUtil.appendSoapDtoXml(soap);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<SoapWidgetDto> sortStringMethod(List<SoapWidgetDto> list) {
        //noinspection rawtypes
        list.sort((Comparator) (o1, o2) -> Collator
                .getInstance(Locale.ENGLISH)
                .compare(((SoapWidgetDto) o1).getSecondLevel(),
                        ((SoapWidgetDto) o2).getSecondLevel()));
        return list;
    }


    private static List<SoapWidgetDto> buildXmlMessage(String wsdlUrl, String ProjectName) {
        Objects.requireNonNull(wsdlUrl, "wsdl url不能为空");
        Wsdl parse = Wsdl.parse(wsdlUrl);
        List<SoapWidgetDto> messageList = new ArrayList<>();
        String nameSpace;
        String soapBinding;
        for (int i = 0; i < parse.getBindings().size(); i++) {
            nameSpace = parse.getBindings().get(i).getNamespaceURI();
            soapBinding = parse.getBindings().get(i).getLocalPart();
            if (!soapBinding.contains("Soap")) {
                continue;
            }
            SoapBuilder builder = parse.binding().name("{" + nameSpace + "}" + soapBinding).find();
            List<String> requestUrl = builder.getServiceUrls();
            SoapWidgetDto soap;
            for (SoapOperation operation : builder.getOperations()) {
                soap = new SoapWidgetDto();
                soap.setRequestUrl(requestUrl);
                soap.setRequestWsdl(wsdlUrl);
                soap.setSoapType("sample");
                soap.setFirstLevel(ProjectName);
                soap.setSecondLevel(soapBinding);
                soap.setThirdLevel(operation.getOperationName());
                soap.setFourthLevel("Request 0");
                soap.setSoapRequestMessage(builder.buildInputMessage(operation));
                messageList.add(soap);
            }
        }
        return sortStringMethod(messageList);
    }
}
