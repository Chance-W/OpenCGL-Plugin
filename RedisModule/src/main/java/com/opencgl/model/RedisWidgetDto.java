package com.opencgl.model;


import com.opencgl.base.model.BaseDataDto;
import lombok.*;

/**
 * @author Chance.W
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedisWidgetDto extends BaseDataDto {
    private String redisIpAddress;
}
