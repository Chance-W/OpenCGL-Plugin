package com.opencgl.formorhtmlwrite.views;

import com.opencgl.base.controls.CustomTextArea;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.StackPane;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2024/02/22 09:33
 * @since v9.0
 */
public abstract class FormOrHtmlWriteView implements Initializable {
    @FXML
    protected StackPane mainStackPane;
    @FXML
    protected MFXTextField originAndReferer;
    @FXML
    protected CustomTextArea contentTextArea;
    @FXML
    protected MFXButton openForm;

}
