package com.opencgl.solace.tree;

import com.opencgl.solace.model.SolaceNodeType;
import com.opencgl.solace.model.SolaceTreeNode;
import com.opencgl.solace.model.SolaceWorkspace;
import com.opencgl.solace.ui.SolaceToolView;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SolaceTreeServiceTest {
    @Test
    void createsGroupConnectionAndModeSpecificOperationHierarchy() {
        SolaceWorkspace workspace = new SolaceWorkspace();
        AtomicInteger saves = new AtomicInteger();
        SolaceTreeService service = new SolaceTreeService(workspace, SolaceToolView.Mode.LISTEN,
            saves::incrementAndGet, ignored -> { });

        SolaceTreeNode group = service.create(null, SolaceNodeType.DIRECTORY, "测试环境");
        SolaceTreeNode connection = service.create(group, SolaceNodeType.CONNECTION, "开发 Solace");
        SolaceTreeNode listener = service.create(connection, SolaceNodeType.LISTEN, "订单监听");

        assertEquals(0L, group.getParentId());
        assertEquals(group.getId(), connection.getParentId());
        assertEquals(connection.getId(), listener.getParentId());
        assertEquals(connection.getConnectionId(), listener.getConnectionId());
        assertFalse(connection.getIsLeaf());
        assertTrue(listener.getIsLeaf());
        assertEquals(3, saves.get());
        assertTrue(workspace.getConnections().containsKey(connection.getConnectionId()));
    }

    @Test
    void rejectsOperationTypeFromTheOtherPluginMode() {
        SolaceTreeService service = new SolaceTreeService(new SolaceWorkspace(), SolaceToolView.Mode.SEND,
            () -> { }, ignored -> { });
        SolaceTreeNode group = service.create(null, SolaceNodeType.DIRECTORY, "环境");
        SolaceTreeNode connection = service.create(group, SolaceNodeType.CONNECTION, "连接");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.create(connection, SolaceNodeType.LISTEN, "监听"));

        assertTrue(error.getMessage().contains("发送"));
    }

    @Test
    void copyingOperationKeepsSettingsButUsesNewIdentity() {
        SolaceWorkspace workspace = new SolaceWorkspace();
        SolaceTreeService service = new SolaceTreeService(workspace, SolaceToolView.Mode.SEND,
            () -> { }, ignored -> { });
        SolaceTreeNode group = service.create(null, SolaceNodeType.DIRECTORY, "环境");
        SolaceTreeNode connection = service.create(group, SolaceNodeType.CONNECTION, "连接");
        SolaceTreeNode send = service.create(connection, SolaceNodeType.SEND, "发送订单");
        send.getSettings().put("destination", "orders/created");

        SolaceTreeNode copy = service.copy(send).getValue();

        assertNotEquals(send.getId(), copy.getId());
        assertEquals(send.getParentId(), copy.getParentId());
        assertEquals("orders/created", copy.getSettings().get("destination"));
        assertTrue(copy.getName().startsWith("发送订单 Copy"));
    }

    @Test
    void deletingConnectionRemovesDescendantsAndConnectionConfiguration() {
        SolaceWorkspace workspace = new SolaceWorkspace();
        SolaceTreeService service = new SolaceTreeService(workspace, SolaceToolView.Mode.SEND,
            () -> { }, ignored -> { });
        SolaceTreeNode group = service.create(null, SolaceNodeType.DIRECTORY, "环境");
        SolaceTreeNode connection = service.create(group, SolaceNodeType.CONNECTION, "连接");
        service.create(connection, SolaceNodeType.SEND, "发送");

        service.delete(connection);

        assertEquals(1, workspace.getNodes().size());
        assertSame(group, workspace.getNodes().getFirst());
        assertTrue(workspace.getConnections().isEmpty());
    }

    @Test
    void searchUsesCurrentWorkspaceAndKeepsTheMatchedNodePath() {
        SolaceWorkspace workspace = new SolaceWorkspace();
        SolaceTreeService service = new SolaceTreeService(workspace, SolaceToolView.Mode.SEND,
            () -> { }, ignored -> { });
        SolaceTreeNode group = service.create(null, SolaceNodeType.DIRECTORY, "开发环境");
        SolaceTreeNode connection = service.create(group, SolaceNodeType.CONNECTION, "公共 Solace");
        service.create(connection, SolaceNodeType.SEND, "订单创建消息");

        var result = service.query("订单");

        assertEquals(3, result.size());
        assertEquals("开发环境", result.get(0).getName());
        assertEquals("公共 Solace", result.get(1).getName());
        assertEquals("订单创建消息", result.get(2).getName());
    }
}
