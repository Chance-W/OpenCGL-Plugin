package com.opencgl.scriptdebug.model;

import com.opencgl.base.model.BaseDataDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ScriptDebugTreeItem extends BaseDataDto {
    /**
     * 脚本类型: GROOVY / JS
     */
    private String language;
    
    /**
     * 脚本内容
     */
    private String scriptContent;
    
    /**
     * 上下文变量 (JSON format)
     */
    private String contextJson;
}
