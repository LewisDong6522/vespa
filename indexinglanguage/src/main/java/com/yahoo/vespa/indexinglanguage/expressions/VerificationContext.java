// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public class VerificationContext implements FieldTypeAdapter {

    private final Map<String, DataType> variables = new HashMap<>();
    private final FieldTypeAdapter fieldType;
    private DataType valueType;
    private String outputField;

    public VerificationContext() {
        this.fieldType = null;
    }

    public VerificationContext(FieldTypeAdapter field) {
        this.fieldType = field;
    }

    public VerificationContext execute(Expression exp) {
        if (exp != null) {
            exp.verify(this);
        }
        return this;
    }

    public DataType getFieldType(Expression exp) {
        return fieldType.getInputType(exp, getOutputField());
    }

    @Override
    public DataType getInputType(Expression exp, String fieldName) {
        return fieldType.getInputType(exp, fieldName);
    }

    @Override
    public void tryOutputType(Expression exp, String fieldName, DataType valueType) {
        fieldType.tryOutputType(exp, fieldName, valueType);
    }

    public DataType getVariable(String name) {
        return variables.get(name);
    }

    public VerificationContext setVariable(String name, DataType value) {
        variables.put(name, value);
        return this;
    }

    public DataType getValueType() {
        return valueType;
    }

    /** Sets the output value type */
    public VerificationContext setValueType(DataType value) {
        this.valueType = value;
        return this;
    }

    /** Sets the name of the (last) output field of the statement this is executed as a part of */
    public void setOutputField(String outputField) { this.outputField = outputField; }

    /**
     * Returns the name of the (last) output field of the statement this is executed as a part of,
     * or null if none or not yet verified
     */
    public String getOutputField() { return outputField; }

    public VerificationContext clear() {
        variables.clear();
        valueType = null;
        return this;
    }

}

