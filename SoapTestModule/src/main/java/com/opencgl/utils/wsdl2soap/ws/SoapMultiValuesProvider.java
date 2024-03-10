package com.opencgl.utils.wsdl2soap.ws;
import javax.xml.namespace.QName;
import java.util.Set;


public interface SoapMultiValuesProvider {
    Set<String> getMultiValues(QName name);
}
