package com.opencgl.utils.wsdl2soap.util;

import org.apache.xmlbeans.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;


public abstract class AbstractSoapVersion implements SoapVersion {
    private final static Logger log = LoggerFactory.getLogger(AbstractSoapVersion.class);

    @SuppressWarnings("unchecked")
    @Override
    public void validateSoapEnvelope(String soapMessage, List<XmlError> errors) {
        List<XmlError> errorList = new ArrayList<XmlError>();

        try {
            XmlOptions xmlOptions = new XmlOptions();
            xmlOptions.setLoadLineNumbers();
            xmlOptions.setValidateTreatLaxAsSkip();
            xmlOptions.setLoadLineNumbers(XmlOptions.LOAD_LINE_NUMBERS_END_ELEMENT);
            XmlObject xmlObject = getSoapEnvelopeSchemaLoader().parse(soapMessage, getEnvelopeType(), xmlOptions);
            xmlOptions.setErrorListener(errorList);
            xmlObject.validate(xmlOptions);
        }
        catch (XmlException e) {
            if (e.getErrors() != null) {
                errorList.addAll(e.getErrors());

            }
            errors.add(XmlError.forMessage(e.getMessage()));
        }
        catch (Exception e) {
            errors.add(XmlError.forMessage(e.getMessage()));
        }
        finally {
            for (XmlError error : errorList) {
                if (error instanceof XmlValidationError && shouldIgnore((XmlValidationError) error)) {
                    log.warn("Ignoring validation error: " + error.toString());
                    continue;
                }

                errors.add(error);
            }
        }
    }

    protected abstract SchemaTypeLoader getSoapEnvelopeSchemaLoader();

    @Override
    public boolean shouldIgnore(XmlValidationError error) {
        QName offendingQName = error.getOffendingQName();
        if (offendingQName != null) {
            if (offendingQName.equals(new QName(getEnvelopeNamespace(), "encodingStyle"))) {
                return true;
            }
            else if (offendingQName.equals(new QName(getEnvelopeNamespace(), "mustUnderstand"))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public abstract SchemaType getFaultType();

    @Override
    public abstract SchemaType getEnvelopeType();
}
