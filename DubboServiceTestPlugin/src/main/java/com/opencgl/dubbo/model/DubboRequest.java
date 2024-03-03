package com.opencgl.dubbo.model;

import com.opencgl.base.model.BaseRequest;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Chance.W
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
public class DubboRequest extends BaseRequest {
    private String dubboRegistryAddr;
    private String dubboRegistryGroup;
    private String dubboProvidersUrl;
    private Integer settingTimeout;
    private String interfaceName;
    private String method;
    private String reqType;
    private String reqJsonMessage;
}
