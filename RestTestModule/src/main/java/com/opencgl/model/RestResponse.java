package com.opencgl.model;

import com.opencgl.base.model.BaseResponse;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Chance.W
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
public class RestResponse extends BaseResponse {
    private Long resultCode;
    private String resultMsg;
}
