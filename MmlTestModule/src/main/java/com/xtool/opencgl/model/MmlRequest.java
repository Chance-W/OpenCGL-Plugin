package com.xtool.opencgl.model;

import com.opencgl.base.model.BaseRequest;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MmlRequest extends BaseRequest {
    private String requestUsername;
    private String requestPassword;
    private String requestIp;
    private String requestPort;
    private String requestMessage;
}
