package com.opencgl.dubbo.model;

import com.opencgl.base.model.BaseDataDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/06/20 14:33
 * @since v9.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DubboTreeItem extends BaseDataDto {
    private String envName;
    private String interfaceInfo;
    private String methodInfo;
    private Boolean isSelected;
    private String requestType;
    private String inputText;
}
