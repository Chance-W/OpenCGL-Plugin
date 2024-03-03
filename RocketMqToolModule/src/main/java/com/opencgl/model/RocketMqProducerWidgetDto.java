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
public class RocketMqProducerWidgetDto extends BaseDataDto {
    private String nameServerAddr;
    private String nameTopic;
    private String tags;
    private Integer count;
    private String inputText;
}
