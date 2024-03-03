package com.xtool.opencgl.utils.wsdl2soap.builder.core;

import com.xtool.opencgl.utils.wsdl2soap.builder.SoapBuilder;
import com.xtool.opencgl.utils.wsdl2soap.builder.SoapOperationBuilder;
import com.xtool.opencgl.utils.wsdl2soap.builder.SoapOperationFinder;
import com.xtool.opencgl.utils.wsdl2soap.ws.SoapBuilderException;
import com.xtool.opencgl.utils.wsdl2soap.ws.SoapContext;

import javax.wsdl.Binding;
import javax.wsdl.BindingOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SoapOperationFinderImpl implements SoapOperationFinder {
    private final Binding binding;

    private String operationName;
    private String operationInputName;
    private String operationOutputName;
    private String soapAction;
    private SoapBuilder builder;

    SoapOperationFinderImpl(SoapBuilder builder, Binding binding) {
        this.binding = binding;
        this.builder = builder;
    }

    @Override
    public SoapOperationFinder name(String operationName) {
        Objects.requireNonNull(operationName);
        this.operationName = operationName;
        return this;
    }

    @Override
    public SoapOperationFinder soapAction(String soapAction) {
        Objects.requireNonNull(soapAction);
        this.soapAction = soapAction;
        return this;
    }

    @Override
    public SoapOperationFinder inputName(String inputName) {
        Objects.requireNonNull(inputName);
        this.operationInputName = inputName;
        return this;
    }

    @Override
    public SoapOperationFinder outputName(String outputName) {
        Objects.requireNonNull(outputName);
        this.operationOutputName = outputName;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SoapOperationBuilder find() {
        validateInput();
        List<SoapOperationBuilder> found = new ArrayList<SoapOperationBuilder>();
        for (BindingOperation operation : (List<BindingOperation>) binding.getBindingOperations()) {
            boolean condition = true;
            condition &= checkOperationName(operation);
            condition &= checkSoapAction(operation);
            condition &= checkOperationInputName(operation);
            condition &= checkOperationOutputName(operation);
            if(condition) {
                found.add(SoapOperationImpl.create(builder, binding, operation));
                if(found.size() > 1) {
                    throw new SoapBuilderException("Operation not unique - found more than one operation");
                }
            }
        }
        if(found.isEmpty()) {
            throw new SoapBuilderException("Found no operations");
        }
        return found.iterator().next();
    }

    @Override
    public SoapOperationBuilder find(SoapContext context) {
        SoapOperationBuilder builder = find();
        builder.setContext(context);
        return builder;
    }

    private void validateInput() {
        boolean failed = true;
        failed &= this.operationName == null;
        failed &= this.soapAction == null;
        failed &= this.operationInputName == null;
        failed &= this.operationOutputName == null;
        if(failed) {
            throw new IllegalArgumentException("All finder properties cannot be null");
        }
    }

    private boolean checkOperationName(BindingOperation op) {
        if (this.operationName != null) {
            return this.operationName.equals(op.getOperation().getName());
        }
        return true;
    }

    private boolean checkSoapAction(BindingOperation op) {
        if (this.soapAction != null) {
            return this.soapAction.equals(SoapUtils.getSOAPActionUri(op));
        }
        return true;
    }

    private boolean checkOperationInputName(BindingOperation op) {
        if (this.operationInputName != null) {
            return this.operationInputName.equals(op.getOperation().getInput().getName());
        }
        return true;
    }

    private boolean checkOperationOutputName(BindingOperation op) {
        if (this.operationOutputName != null) {
            return this.operationOutputName.equals(op.getOperation().getOutput().getName());
        }
        return true;
    }

}
