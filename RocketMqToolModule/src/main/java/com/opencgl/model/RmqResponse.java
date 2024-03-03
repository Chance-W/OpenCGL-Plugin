package com.opencgl.model;

import com.opencgl.base.model.BaseResponse;
import lombok.*;

/**
 * @author Chance.W
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RmqResponse extends BaseResponse {
    private Long resultCode;
    private String resultMsg;
}
