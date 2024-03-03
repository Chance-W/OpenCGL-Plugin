package com.xtool.opencgl.model;

import javafx.beans.property.SimpleStringProperty;

public class DataAttribute {
    public final SimpleStringProperty name;
    public final SimpleStringProperty value;
    public final SimpleStringProperty des;

    public DataAttribute(String name, String value, String des) {
        this.name = new SimpleStringProperty(name);
        this.value = new SimpleStringProperty(value);
        this.des = new SimpleStringProperty(des);
    }

    public String getDes() {
        return des.get();
    }

    public SimpleStringProperty desProperty() {
        return des;
    }

    public void setDes(String des) {
        this.name.set(des);
    }

    public String getName() {
        return name.get();
    }

    public SimpleStringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }


    public String getValue() {
        return value.get();
    }

    public SimpleStringProperty valueProperty() {
        return value;
    }

    public void setValue(String value) {
        this.value.set(value);
    }


}

