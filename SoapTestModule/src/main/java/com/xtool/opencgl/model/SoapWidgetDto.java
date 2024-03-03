package com.xtool.opencgl.model;


import lombok.*;

import java.util.List;

/**
 * @author Chance.W
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@SuppressWarnings("unused")
public class SoapWidgetDto extends LevelDto {
    private List<String> requestUrl;
    private String requestWsdl;
    private String soapType;
    private String soapRequestMessage;
}
