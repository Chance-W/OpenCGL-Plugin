package com.opencgl.base.utils.tree;

/**
 * 拖拽放置位置
 * @author Chance.W
 */
public enum DropPosition {
    /**
     * 放置在目标节点之前（同级排序）
     */
    BEFORE,
    
    /**
     * 放置到目标节点内部（作为子节点）
     */
    INTO,
    
    /**
     * 放置在目标节点之后（同级排序）
     */
    AFTER
}
