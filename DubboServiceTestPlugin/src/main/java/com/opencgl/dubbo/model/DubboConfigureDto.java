package com.opencgl.dubbo.model;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@Builder
public class DubboConfigureDto {
    private String envInfo;
    private String interfaceInfo;
    private String methodInfo;
    private String requestType;
}
