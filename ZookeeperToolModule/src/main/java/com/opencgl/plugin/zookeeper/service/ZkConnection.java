package com.opencgl.plugin.zookeeper.service;

import com.opencgl.base.model.BaseDataDto;

/** A local connection or folder, never a server znode. */
public class ZkConnection extends BaseDataDto {
    private String servers = "localhost:2181";
    private int timeoutMs = 5000;
    public String getServers() { return servers; }
    public void setServers(String servers) { this.servers = servers; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public ZkConnection snapshot() {
        var copy = new ZkConnection();
        copy.setId(getId()); copy.setParentId(getParentId()); copy.setName(getName());
        copy.setIsLeaf(getIsLeaf()); copy.setSortOrder(getSortOrder());
        copy.setServers(servers); copy.setTimeoutMs(timeoutMs);
        return copy;
    }
}
