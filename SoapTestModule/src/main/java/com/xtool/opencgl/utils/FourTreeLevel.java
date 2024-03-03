package com.xtool.opencgl.utils;

import javafx.beans.property.SimpleStringProperty;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class FourTreeLevel {
    private final SimpleStringProperty fourthLevel;
    private final SimpleStringProperty thirdLevel;
    private final SimpleStringProperty secondLevel;
    private final SimpleStringProperty firstLevel;


    public String getFirstLevel() {
        return firstLevel.get();
    }


    public void setFirstLevel(String firstLevel) {
        this.firstLevel.set(firstLevel);
    }


    public FourTreeLevel(String fourLevel, String thirdLevel, String secondLevel, String firstLevel) {
        this.fourthLevel = new SimpleStringProperty(fourLevel);
        this.thirdLevel = new SimpleStringProperty(thirdLevel);
        this.secondLevel = new SimpleStringProperty(secondLevel);
        this.firstLevel = new SimpleStringProperty(firstLevel);
    }


    public String getSecondLevel() {
        return secondLevel.get();
    }

    public void setSecondLevel(String fName) {
        firstLevel.set(fName);
    }

    public String getFourLevel() {
        return fourthLevel.get();
    }

    public void setFourLevel(String fName) {
        fourthLevel.set(fName);
    }

    public String getThirdLevel() {
        return thirdLevel.get();
    }

    public void setThirdLevel(String fName) {
        thirdLevel.set(fName);
    }

}

