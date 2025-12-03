package com.opencgl.model;


import lombok.*;

/**
 * @author Chance.W
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DbBatchOperWidgetTextDto {
    private Boolean checked;
    private Boolean delOriginTableCheckBox;
    private String moduleDbInfo;
    private String moduleDbTableName;
    private String moduleSuffix;
}
