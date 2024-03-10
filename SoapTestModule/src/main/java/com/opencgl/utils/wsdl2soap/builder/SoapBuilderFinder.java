package com.opencgl.utils.wsdl2soap.builder;


import com.opencgl.utils.wsdl2soap.ws.SoapContext;

import javax.xml.namespace.QName;

@SuppressWarnings("unused")
public interface SoapBuilderFinder {

    SoapBuilderFinder name(String name);

    SoapBuilderFinder name(QName name);

    SoapBuilderFinder namespaceURI(String namespaceURI);

    SoapBuilderFinder localPart(String localPart);

    SoapBuilderFinder prefix(String prefix);

    SoapBuilder find();

    SoapBuilder find(SoapContext context);
}
