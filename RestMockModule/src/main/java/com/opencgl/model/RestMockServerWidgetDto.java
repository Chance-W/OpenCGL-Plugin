package com.opencgl.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Chance.W
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestMockServerWidgetDto {
    private Boolean isEnable;
    private String contentPath;
    private String responseHeader;
    private String responseContent;
    private String description;
    /** HTTP 方法: GET / POST / PUT / DELETE 等 */
    private String httpMethod;
    /** 响应状态码，如 200、404、500 */
    private Integer statusCode;
    /** 模拟延迟（毫秒） */
    private Integer delayMs;
}
