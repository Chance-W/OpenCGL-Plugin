package com.opencgl.model;

import com.opencgl.base.model.BaseRequest;
import lombok.*;

/**
 * @author Chance.W
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RmqRequest extends BaseRequest {
    private String nameServerAddr;
    private String nameTopic;
    private String tags;
    private Integer count;
    private String requestMessage;
}
