package com.opencgl.solace.tree;

import com.alibaba.fastjson2.JSON;
import com.opencgl.base.service.TreeOperateService;
import com.opencgl.base.view.CustomizeTreeItem;
import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceNodeType;
import com.opencgl.solace.model.SolaceTreeNode;
import com.opencgl.solace.model.SolaceWorkspace;
import com.opencgl.solace.ui.SolaceToolView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Tree persistence adapter shared by the Dubbo-style collection tree and its menus. */
public final class SolaceTreeService implements TreeOperateService<SolaceTreeNode> {
    @FunctionalInterface
    public interface SaveAction { void save() throws IOException; }

    private final SolaceWorkspace workspace;
    private final SolaceToolView.Mode mode;
    private final SaveAction saveAction;
    private final Consumer<SolaceTreeNode> displayAction;

    public SolaceTreeService(SolaceWorkspace workspace, SolaceToolView.Mode mode,
                             SaveAction saveAction, Consumer<SolaceTreeNode> displayAction) {
        this.workspace = Objects.requireNonNull(workspace);
        this.mode = Objects.requireNonNull(mode);
        this.saveAction = Objects.requireNonNull(saveAction);
        this.displayAction = Objects.requireNonNull(displayAction);
    }

    public SolaceTreeNode create(SolaceTreeNode parent, SolaceNodeType type, String name) {
        validateParent(parent, type);
        SolaceTreeNode node = new SolaceTreeNode();
        node.setId(nextId());
        node.setParentId(parent == null ? 0L : parent.getId());
        node.setName(name == null || name.isBlank() ? defaultName(type) : name.trim());
        node.setNodeType(type);
        node.setIsLeaf(type == SolaceNodeType.SEND || type == SolaceNodeType.LISTEN);
        node.setSortOrder((int) workspace.getNodes().stream()
            .filter(item -> Objects.equals(item.getParentId(), node.getParentId())).count());

        if (type == SolaceNodeType.CONNECTION) {
            String connectionId = UUID.randomUUID().toString();
            node.setConnectionId(connectionId);
            SolaceConnectionConfig config = new SolaceConnectionConfig();
            config.setHost("tcp://localhost:55555");
            workspace.getConnections().put(connectionId, config);
        } else if (type == SolaceNodeType.SEND || type == SolaceNodeType.LISTEN) {
            node.setConnectionId(parent.getConnectionId());
            node.getSettings().put("destinationType", "TOPIC");
            node.getSettings().put("destination", "sample/topic");
        }
        workspace.getNodes().add(node);
        persist();
        return node;
    }

    private void validateParent(SolaceTreeNode parent, SolaceNodeType type) {
        if (type == SolaceNodeType.DIRECTORY && parent != null && parent.getNodeType() != SolaceNodeType.DIRECTORY) {
            throw new IllegalArgumentException("分组只能创建在根节点或其他分组下");
        }
        if (type == SolaceNodeType.CONNECTION && parent != null && parent.getNodeType() != SolaceNodeType.DIRECTORY) {
            throw new IllegalArgumentException("连接只能创建在根节点或分组下");
        }
        if (type == SolaceNodeType.SEND || type == SolaceNodeType.LISTEN) {
            SolaceNodeType required = mode == SolaceToolView.Mode.SEND ? SolaceNodeType.SEND : SolaceNodeType.LISTEN;
            if (type != required) {
                throw new IllegalArgumentException(mode == SolaceToolView.Mode.SEND
                    ? "发送插件只能创建发送配置" : "监听插件只能创建监听配置");
            }
            if (parent == null || parent.getNodeType() != SolaceNodeType.CONNECTION) {
                throw new IllegalArgumentException("发送/监听配置必须创建在连接节点下");
            }
        }
    }

    @Override public CustomizeTreeItem<SolaceTreeNode> add(SolaceTreeNode value) {
        SolaceTreeNode parent = find(value.getParentId());
        SolaceNodeType type;
        if (Boolean.TRUE.equals(value.getIsLeaf())) {
            type = mode == SolaceToolView.Mode.SEND ? SolaceNodeType.SEND : SolaceNodeType.LISTEN;
        } else {
            type = parent == null || parent.getNodeType() == SolaceNodeType.DIRECTORY
                ? SolaceNodeType.DIRECTORY : SolaceNodeType.CONNECTION;
        }
        return new CustomizeTreeItem<>(create(parent, type, value.getName()));
    }

    @Override public CustomizeTreeItem<SolaceTreeNode> importData(SolaceTreeNode value) { return add(value); }

    @Override public CustomizeTreeItem<SolaceTreeNode> copy(SolaceTreeNode source) {
        if (source == null || !Boolean.TRUE.equals(source.getIsLeaf())) return null;
        SolaceTreeNode copy = JSON.parseObject(JSON.toJSONString(source), SolaceTreeNode.class);
        copy.setId(nextId());
        copy.setName(source.getName() + " Copy");
        copy.setSortOrder((int) workspace.getNodes().stream()
            .filter(item -> Objects.equals(item.getParentId(), source.getParentId())).count());
        workspace.getNodes().add(copy);
        persist();
        return new CustomizeTreeItem<>(copy);
    }

    @Override public CustomizeTreeItem<SolaceTreeNode> delete(SolaceTreeNode value) {
        if (value == null) return null;
        List<Long> ids = new ArrayList<>();
        collectIds(value.getId(), ids);
        workspace.getNodes().removeIf(item -> ids.contains(item.getId()));
        workspace.getConnections().entrySet().removeIf(entry -> workspace.getNodes().stream()
            .noneMatch(item -> entry.getKey().equals(item.getConnectionId())));
        persist();
        return new CustomizeTreeItem<>(value);
    }

    private void collectIds(Long parentId, List<Long> ids) {
        if (parentId == null || ids.contains(parentId)) return;
        ids.add(parentId);
        workspace.getNodes().stream().filter(item -> Objects.equals(parentId, item.getParentId()))
            .map(SolaceTreeNode::getId).toList().forEach(id -> collectIds(id, ids));
    }

    @Override public CustomizeTreeItem<SolaceTreeNode> update(SolaceTreeNode value) {
        persist();
        return new CustomizeTreeItem<>(value);
    }

    @Override public void changeToDisplay(SolaceTreeNode value) { displayAction.accept(value); }

    @Override public List<SolaceTreeNode> queryAll() {
        return workspace.getNodes().stream()
            .sorted(Comparator.comparing(item -> item.getSortOrder() == null ? Integer.MAX_VALUE : item.getSortOrder()))
            .toList();
    }

    public List<SolaceTreeNode> query(String keyword) {
        if (keyword == null || keyword.isBlank()) return queryAll();
        String normalized = keyword.trim().toLowerCase();
        Set<Long> visibleIds = new LinkedHashSet<>();
        for (SolaceTreeNode node : workspace.getNodes()) {
            if (node.getName() != null && node.getName().toLowerCase().contains(normalized)) {
                visibleIds.add(node.getId());
                addAncestors(node, visibleIds);
                addDescendants(node.getId(), visibleIds);
            }
        }
        return queryAll().stream().filter(item -> visibleIds.contains(item.getId())).toList();
    }

    private void addAncestors(SolaceTreeNode node, Set<Long> ids) {
        SolaceTreeNode parent = find(node.getParentId());
        while (parent != null && ids.add(parent.getId())) parent = find(parent.getParentId());
    }

    private void addDescendants(Long parentId, Set<Long> ids) {
        for (SolaceTreeNode child : workspace.getNodes()) {
            if (Objects.equals(parentId, child.getParentId()) && ids.add(child.getId())) {
                addDescendants(child.getId(), ids);
            }
        }
    }

    @Override public void updatePositionOnly(SolaceTreeNode value) { persist(); }
    @Override public void batchReorder(List<SolaceTreeNode> siblings) {
        for (int i = 0; i < siblings.size(); i++) siblings.get(i).setSortOrder(i);
        persist();
    }

    @Override public boolean supportImportAndExport() { return false; }

    private SolaceTreeNode find(Long id) {
        if (id == null || id == 0L) return null;
        return workspace.getNodes().stream().filter(item -> Objects.equals(id, item.getId())).findFirst().orElse(null);
    }

    private long nextId() {
        return workspace.getNodes().stream().map(SolaceTreeNode::getId).filter(Objects::nonNull)
            .max(Long::compareTo).orElse(0L) + 1;
    }

    private String defaultName(SolaceNodeType type) {
        return switch (type) {
            case DIRECTORY -> "新分组";
            case CONNECTION -> "新 Solace 连接";
            case SEND -> "新发送配置";
            case LISTEN -> "新监听配置";
        };
    }

    private void persist() {
        try { saveAction.save(); }
        catch (IOException error) { throw new IllegalStateException("保存 Solace 树失败", error); }
    }
}
