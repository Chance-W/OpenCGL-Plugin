package com.opencgl.solace.model;

import com.opencgl.base.model.BaseDataDto;

import java.util.LinkedHashMap;
import java.util.Map;

public class SolaceTreeNode extends BaseDataDto {
    private SolaceNodeType nodeType = SolaceNodeType.DIRECTORY;
    private String connectionId;
    private Map<String, String> variables = new LinkedHashMap<>();
    private Map<String, String> settings = new LinkedHashMap<>();

    public SolaceNodeType getNodeType() { return nodeType; }
    public void setNodeType(SolaceNodeType nodeType) { this.nodeType = nodeType; }
    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public Map<String, String> getVariables() { return variables; }
    public void setVariables(Map<String, String> variables) {
        this.variables = variables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
    }
    public Map<String, String> getSettings() { return settings; }
    public void setSettings(Map<String, String> settings) {
        this.settings = settings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(settings);
    }
}
