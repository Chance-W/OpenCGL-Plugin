package com.opencgl.base.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 树形结构基础数据模型
 * @author Chance.W
 * @version 9.0
 * @date 2023/2/21 10:41
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class BaseDataDto {
    private Long id;
    private Long parentId;
    private Boolean isLeaf;
    private String name;
    private Integer sortOrder;

    // ============ Getter/Setter 方法 ============
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Boolean getIsLeaf() {
        return isLeaf;
    }

    public void setIsLeaf(Boolean isLeaf) {
        this.isLeaf = isLeaf;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
