package com.xtool.opencgl.model;

import com.opencgl.base.model.BaseRequest;
import lombok.*;

import java.util.Map;

/**
 * @author Chance.W
 */
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class RestRequest extends BaseRequest {
    private String requestUrl;
    private String requestMethod;
    private String mediaType;
    private Map<String, String> requestHeaderMap;
    private  Map<String, String> requestCookieMap;
    private String requestMessage;
    private String requestBody;
}
