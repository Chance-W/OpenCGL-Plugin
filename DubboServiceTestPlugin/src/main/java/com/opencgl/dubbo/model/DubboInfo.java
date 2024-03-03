package com.opencgl.dubbo.model;

import lombok.Builder;
import lombok.Data;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/12/20 17:29
 * @since v9.0
 */
@Data
@Builder
public class DubboInfo {
    private String zk;
    private String group;
}
