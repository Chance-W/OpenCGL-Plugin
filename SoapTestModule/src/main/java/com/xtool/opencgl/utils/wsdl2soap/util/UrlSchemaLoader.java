package com.xtool.opencgl.utils.wsdl2soap.util;

import com.xtool.opencgl.utils.wsdl2soap.ws.SoapBuilderException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

import java.net.URL;


public class UrlSchemaLoader implements SchemaLoader, DefinitionLoader {
    private String baseURI;

    public UrlSchemaLoader(String baseURI) {
        this.baseURI = baseURI;
    }

    @Override
    public XmlObject loadXmlObject(String wsdlUrl, XmlOptions options) throws Exception {
        return XmlUtils.createXmlObject(new URL(wsdlUrl), options);
    }

    @Override
    public String getBaseURI() {
        return baseURI;
    }

    @Override
    public void setProgressInfo(String info) {
        throw new SoapBuilderException("Not Implemented");
    }

    @Override
    public boolean isAborted() {
        throw new SoapBuilderException("Not Implemented");
    }

    @Override
    public boolean abort() {
        throw new SoapBuilderException("Not Implemented");
    }

    @Override
    public void setNewBaseURI(String uri) {
        throw new SoapBuilderException("Not Implemented");
    }

    @Override
    public String getFirstNewURI() {
        throw new SoapBuilderException("Not Implemented");
    }
}
