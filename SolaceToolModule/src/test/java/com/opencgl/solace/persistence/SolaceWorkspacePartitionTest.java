package com.opencgl.solace.persistence;

import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceNodeType;
import com.opencgl.solace.model.SolaceTreeNode;
import com.opencgl.solace.model.SolaceWorkspace;
import com.opencgl.solace.model.SolaceWorkspaceKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolaceWorkspacePartitionTest {
    @Test
    void keepsOnlyModeOperationsWithTheirAncestorsAndConnection() {
        SolaceWorkspace shared = new SolaceWorkspace();
        shared.setNodes(List.of(
            node(1, 0, SolaceNodeType.DIRECTORY, null),
            node(2, 1, SolaceNodeType.CONNECTION, "connection"),
            node(3, 2, SolaceNodeType.SEND, "connection"),
            node(4, 2, SolaceNodeType.LISTEN, "connection")));
        shared.setConnections(Map.of("connection", new SolaceConnectionConfig()));

        SolaceWorkspace sender = SolaceWorkspacePartition.forKind(shared, SolaceWorkspaceKind.SEND);
        SolaceWorkspace listener = SolaceWorkspacePartition.forKind(shared, SolaceWorkspaceKind.LISTEN);

        assertEquals(List.of(SolaceNodeType.DIRECTORY, SolaceNodeType.CONNECTION, SolaceNodeType.SEND),
            sender.getNodes().stream().map(SolaceTreeNode::getNodeType).toList());
        assertEquals(List.of(SolaceNodeType.DIRECTORY, SolaceNodeType.CONNECTION, SolaceNodeType.LISTEN),
            listener.getNodes().stream().map(SolaceTreeNode::getNodeType).toList());
        assertTrue(sender.getConnections().containsKey("connection"));
        assertTrue(listener.getConnections().containsKey("connection"));
    }

    private SolaceTreeNode node(long id, long parentId, SolaceNodeType type, String connectionId) {
        SolaceTreeNode node = new SolaceTreeNode();
        node.setId(id);
        node.setParentId(parentId);
        node.setName(type.name());
        node.setNodeType(type);
        node.setConnectionId(connectionId);
        node.setIsLeaf(type == SolaceNodeType.SEND || type == SolaceNodeType.LISTEN);
        node.setSortOrder((int) id);
        return node;
    }
}
