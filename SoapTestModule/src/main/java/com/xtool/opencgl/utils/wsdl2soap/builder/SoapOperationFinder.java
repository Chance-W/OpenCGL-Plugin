package com.xtool.opencgl.utils.wsdl2soap.builder;


import com.xtool.opencgl.utils.wsdl2soap.ws.SoapContext;

@SuppressWarnings("unused")
public interface SoapOperationFinder {

    SoapOperationFinder name(String operationName);

    SoapOperationFinder soapAction(String soapAction);

    SoapOperationFinder inputName(String inputName);

    SoapOperationFinder outputName(String inputName);

    SoapOperationBuilder find();

    SoapOperationBuilder find(SoapContext context);
}
