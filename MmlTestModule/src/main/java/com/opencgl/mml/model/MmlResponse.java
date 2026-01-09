package com.opencgl.mml.model;

import com.opencgl.base.model.BaseResponse;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MmlResponse extends BaseResponse {
    private Long resultCode;
    private String resultMsg;
}
