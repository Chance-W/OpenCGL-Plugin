package com.opencgl.dubbo.model;

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
public class DubboResponse extends BaseResponse {
    private Object object;
}
