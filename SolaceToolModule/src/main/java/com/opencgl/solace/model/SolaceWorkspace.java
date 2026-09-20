package com.opencgl.solace.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared persisted state used by both Solace plugin entries. */
public class SolaceWorkspace {
    private List<SolaceTreeNode> nodes = new ArrayList<>();
    private Map<String, SolaceConnectionConfig> connections = new LinkedHashMap<>();

    public List<SolaceTreeNode> getNodes() { return nodes; }
    public void setNodes(List<SolaceTreeNode> nodes) {
        this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
    }
    public Map<String, SolaceConnectionConfig> getConnections() { return connections; }
    public void setConnections(Map<String, SolaceConnectionConfig> connections) {
        this.connections = connections == null ? new LinkedHashMap<>() : new LinkedHashMap<>(connections);
    }
}
