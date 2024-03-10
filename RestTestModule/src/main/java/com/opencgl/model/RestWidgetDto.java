package com.opencgl.model;

import com.opencgl.base.model.BaseDataDto;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestWidgetDto extends BaseDataDto {
    private String requestMethod;
    private String requestUrl;
    private String requestHeader;
    private String requestCookie;
    private String requestMediaType;
    private String inputText;
}
