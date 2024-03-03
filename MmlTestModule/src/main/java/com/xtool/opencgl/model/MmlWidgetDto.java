package com.xtool.opencgl.model;

import com.opencgl.base.model.BaseDataDto;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MmlWidgetDto extends BaseDataDto {
    private String requestUsername;
    private String requestPassword;
    private String requestIp;
    private String requestPort;
    private String requestMediaType;
    private String inputText;
}
