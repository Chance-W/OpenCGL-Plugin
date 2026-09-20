package com.opencgl.solace.persistence;

import com.alibaba.fastjson2.JSON;
import com.opencgl.solace.model.SolaceNodeType;
import com.opencgl.solace.model.SolaceTreeNode;
import com.opencgl.solace.model.SolaceWorkspace;
import com.opencgl.solace.model.SolaceWorkspaceKind;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Splits the former shared sender/listener tree while retaining each operation's ancestors. */
public final class SolaceWorkspacePartition {
    private SolaceWorkspacePartition() { }

    public static SolaceWorkspace forKind(SolaceWorkspace shared, SolaceWorkspaceKind kind) {
        SolaceWorkspace result = new SolaceWorkspace();
        if (shared == null) return result;
        SolaceNodeType operationType = kind == SolaceWorkspaceKind.SEND ? SolaceNodeType.SEND : SolaceNodeType.LISTEN;
        Set<Long> included = new LinkedHashSet<>();
        for (SolaceTreeNode node : shared.getNodes()) {
            if (node.getNodeType() == operationType) {
                includeWithAncestors(shared, node, included);
            }
        }
        result.setNodes(shared.getNodes().stream().filter(node -> included.contains(node.getId()))
            .map(SolaceWorkspacePartition::copyNode).toList());
        Set<String> connectionIds = result.getNodes().stream().map(SolaceTreeNode::getConnectionId)
            .filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        shared.getConnections().forEach((id, config) -> {
            if (connectionIds.contains(id)) {
                result.getConnections().put(id, JSON.parseObject(JSON.toJSONString(config), config.getClass()));
            }
        });
        return result;
    }

    private static void includeWithAncestors(SolaceWorkspace workspace, SolaceTreeNode node, Set<Long> included) {
        SolaceTreeNode current = node;
        while (current != null && current.getId() != null && included.add(current.getId())) {
            Long parentId = current.getParentId();
            current = parentId == null || parentId == 0L ? null : workspace.getNodes().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), parentId)).findFirst().orElse(null);
        }
    }

    private static SolaceTreeNode copyNode(SolaceTreeNode node) {
        return JSON.parseObject(JSON.toJSONString(node), SolaceTreeNode.class);
    }
}
