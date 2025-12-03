package com.opencgl.model;

import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author Chance.W
 */
@Data
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DbBatchOperWidgetDto {
    private MFXCheckbox mfxCheckBox;
    private MFXTextField moduleDbInfoTemplate;
    private MFXTextField moduleDbTableNameTemplate;
    private MFXTextField moduleSuffixTemplate;
    private MFXCheckbox delOriginTableCheckBox;



    public DbBatchOperWidgetTextDto buildWidgetText() {
        return DbBatchOperWidgetTextDto.builder()
                .checked(mfxCheckBox.isSelected())
                .moduleDbInfo(moduleDbInfoTemplate.getText())
                .moduleDbTableName(moduleDbTableNameTemplate.getText())
                .moduleSuffix(moduleSuffixTemplate.getText())
                .delOriginTableCheckBox(delOriginTableCheckBox.isSelected())
                .build();
    }

}
