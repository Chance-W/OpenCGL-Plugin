package com.opencgl.base.controls;

import java.util.function.Consumer;

import io.github.palexdev.materialfx.controls.MFXFilterComboBox;
import io.github.palexdev.materialfx.controls.cell.MFXComboBoxCell;
import io.github.palexdev.virtualizedfx.unused.simple.SimpleVirtualFlow;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.ListChangeListener;
import javafx.scene.control.Skin;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/07/08 14:47
 * @since v9.0
 */
public class CustomMFXFilterComboBox<T> extends MFXFilterComboBox<T> {


    public CustomMFXFilterComboBox() {
        super();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new CustomMFXFilterComboBoxSkin<>(this, boundField);
    }


}


