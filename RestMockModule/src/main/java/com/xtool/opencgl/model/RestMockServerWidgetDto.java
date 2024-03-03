package com.xtool.opencgl.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Chance.W
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestMockServerWidgetDto {
    private Boolean isEnable;
    private String contentPath;
    private String responseHeader;
    private String responseContent;
    private String description;
}
