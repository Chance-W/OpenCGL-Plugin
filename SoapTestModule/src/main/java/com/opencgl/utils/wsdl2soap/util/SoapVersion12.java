package com.opencgl.utils.wsdl2soap.util;

import com.opencgl.utils.wsdl2soap.ws.common.ResourceUtils;
import com.opencgl.utils.wsdl2soap.ws.SoapBuilderException;

import org.apache.commons.lang.StringUtils;
import org.apache.xmlbeans.*;
import org.w3.x2003.x05.soapEnvelope.EnvelopeDocument;
import org.w3.x2003.x05.soapEnvelope.FaultDocument;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.net.URL;

public class SoapVersion12 extends AbstractSoapVersion {
    private final static QName envelopeQName = new QName(Constants.SOAP12_ENVELOPE_NS, "Envelope");
    private final static QName bodyQName = new QName(Constants.SOAP12_ENVELOPE_NS, "Body");
    private final static QName faultQName = new QName(Constants.SOAP11_ENVELOPE_NS, "Fault");
    private final static QName headerQName = new QName(Constants.SOAP12_ENVELOPE_NS, "Header");
    public final static SoapVersion12 instance = new SoapVersion12();

    private SchemaTypeLoader soapSchema;
    private XmlObject soapSchemaXml;
    private XmlObject soapEncodingXml;

    private SoapVersion12() {

        try {
            URL soapSchemaXmlResource = ResourceUtils.getResourceWithAbsolutePackagePath(getClass(),
                    "xsds/", "soapEnvelope12.xsd");
            soapSchemaXml = XmlUtils.createXmlObject(soapSchemaXmlResource);
            soapSchema = XmlBeans.loadXsd(new XmlObject[]{soapSchemaXml});

            URL soapEncodingXmlResource = ResourceUtils.getResourceWithAbsolutePackagePath(getClass(),
                    "xsds/", "soapEncoding12.xsd");
            soapEncodingXml = XmlUtils.createXmlObject(soapEncodingXmlResource);
        } catch (XmlException e) {
            throw new SoapBuilderException(e);
        }
    }

    @Override
    public String getEncodingNamespace() {
        return "http://www.w3.org/2003/05/soap-encoding";
    }
    @Override
    public XmlObject getSoapEncodingSchema() throws XmlException, IOException {
        return soapEncodingXml;
    }
    @Override
    public XmlObject getSoapEnvelopeSchema() throws XmlException, IOException {
        return soapSchemaXml;
    }
    @Override
    public String getEnvelopeNamespace() {
        return Constants.SOAP12_ENVELOPE_NS;
    }
    @Override
    public SchemaType getEnvelopeType() {
        return EnvelopeDocument.type;
    }
    @Override
    public String toString() {
        return "SOAP 1.2";
    }

    public static String quote(String str) {
        if (str == null) {
            return str;
        }

        if (str.length() < 2 || !str.startsWith("\"") || !str.endsWith("\"")) {
            str = "\"" + str + "\"";
        }

        return str;
    }
    @Override
    public String getContentTypeHttpHeader(String encoding, String soapAction) {
        String result = getContentType();

        if (encoding != null && encoding.trim().length() > 0) {
            result += ";charset=" + encoding;
        }

        if (StringUtils.isNotBlank(soapAction)) {
            result += ";action=" + quote(soapAction);
        }

        return result;
    }
    @Override
    public String getSoapActionHeader(String soapAction) {
        // SOAP 1.2 has this in the contenttype
        return null;
    }
    @Override
    public String getContentType() {
        return "application/soap+dom4j";
    }
    @Override
    public QName getBodyQName() {
        return bodyQName;
    }
    @Override
    public QName getEnvelopeQName() {
        return envelopeQName;
    }
    @Override
    public QName getHeaderQName() {
        return headerQName;
    }
    @Override
    protected SchemaTypeLoader getSoapEnvelopeSchemaLoader() {
        return soapSchema;
    }

    public static QName getFaultQName() {
        return faultQName;
    }
    @Override
    public SchemaType getFaultType() {
        return FaultDocument.type;
    }
    @Override
    public String getName() {
        return "SOAP 1.2";
    }
    @Override
    public String getFaultDetailNamespace() {
        return getEnvelopeNamespace();
    }
}
