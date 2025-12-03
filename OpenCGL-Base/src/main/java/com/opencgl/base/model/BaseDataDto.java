package com.opencgl.base.model;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author Chance.W
 * @version 9.0
 * @description
 * @date 2023/2/21 10:41
 */
@Data
@ToString
@EqualsAndHashCode
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BaseDataDto {
    private Long id;

    private Long parentId;

    private Boolean isLeaf;

    private String name;

}
