package com.opencgl.utils.wsdl2soap.util;

import com.opencgl.utils.wsdl2soap.ws.common.ResourceUtils;
import com.opencgl.utils.wsdl2soap.ws.SoapBuilderException;

import org.apache.xmlbeans.*;
import org.xmlsoap.schemas.soap.envelope.EnvelopeDocument;

import javax.xml.namespace.QName;
import java.net.URL;

@SuppressWarnings("unused")
public class SoapVersion11 extends AbstractSoapVersion {
    private final static QName envelopeQName = new QName(Constants.SOAP11_ENVELOPE_NS, "Envelope");
    private final static QName bodyQName = new QName(Constants.SOAP11_ENVELOPE_NS, "Body");
    private final static QName faultQName = new QName(Constants.SOAP11_ENVELOPE_NS, "Fault");
    private final static QName headerQName = new QName(Constants.SOAP11_ENVELOPE_NS, "Header");

    SchemaTypeLoader soapSchema;
    SchemaType soapEnvelopeType;
    private XmlObject soapSchemaXml;
    private XmlObject soapEncodingXml;
    private SchemaType soapFaultType;

    public final static SoapVersion11 instance = new SoapVersion11();

    private SoapVersion11() {
        try {
            XmlOptions options = new XmlOptions();
            options.setCompileNoValidation();
            options.setCompileNoPvrRule();
            options.setCompileDownloadUrls();
            options.setCompileNoUpaRule();
            options.setValidateTreatLaxAsSkip();

            URL soapSchemaXmlResource = ResourceUtils.getResourceWithAbsolutePackagePath(getClass(),
                    "xsds/", "soapEnvelope.xsd");
            soapSchemaXml = XmlUtils.createXmlObject(soapSchemaXmlResource, options);
            soapSchema = XmlBeans.loadXsd(new XmlObject[]{soapSchemaXml});

            soapEnvelopeType = soapSchema.findDocumentType(envelopeQName);
            soapFaultType = soapSchema.findDocumentType(faultQName);

            URL soapEncodingXmlResource = ResourceUtils.getResourceWithAbsolutePackagePath(getClass(),
                    "xsds/", "soapEncoding.xsd");
            soapEncodingXml = XmlUtils.createXmlObject(soapEncodingXmlResource, options);

        } catch (XmlException ex) {
            throw new SoapBuilderException(ex);
        }
    }

    @Override
    public SchemaType getEnvelopeType() {
        return EnvelopeDocument.type;
    }

    @Override
    public String getEnvelopeNamespace() {
        return Constants.SOAP11_ENVELOPE_NS;
    }

    @Override
    public String getEncodingNamespace() {
        return Constants.SOAP_ENCODING_NS;
    }

    @Override
    public XmlObject getSoapEncodingSchema(){
        return soapEncodingXml;
    }

    @Override
    public XmlObject getSoapEnvelopeSchema(){
        return soapSchemaXml;
    }

    @Override
    public String toString() {
        return "SOAP 1.1";
    }

    @Override
    public String getContentTypeHttpHeader(String encoding, String soapAction) {
        if (encoding == null || encoding.trim().length() == 0){
            return getContentType();
        }
        else {
            return getContentType() + ";charset=" + encoding;
        }
    }

    @Override
    public String getSoapActionHeader(String soapAction) {
        if (soapAction == null || soapAction.length() == 0) {
            soapAction = "\"\"";
        } else {
            soapAction = "\"" + soapAction + "\"";
        }

        return soapAction;
    }

    @Override
    public String getContentType() {
        return "text/dom4j";
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

    @Override
    public SchemaType getFaultType() {
        return soapFaultType;
    }

    @Override
    public String getName() {
        return "SOAP 1.1";
    }

    @Override
    public String getFaultDetailNamespace() {
        return "";
    }
}
