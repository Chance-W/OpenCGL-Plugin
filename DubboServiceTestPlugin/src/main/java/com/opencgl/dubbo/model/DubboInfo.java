package com.opencgl.dubbo.model;

import lombok.Builder;
import lombok.Data;

/**
 * @author Chance.W
 * @version 1.0
 * @CreateDate 2023/12/20 17:29
 * @since v9.0
 */
public class DubboInfo {
    private String zk;
    private String group;

    public DubboInfo() {}

    public DubboInfo(String zk, String group) {
        this.zk = zk;
        this.group = group;
    }

    public String getZk() { return zk; }
    public void setZk(String zk) { this.zk = zk; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String zk;
        private String group;

        public Builder zk(String zk) { this.zk = zk; return this; }
        public Builder group(String group) { this.group = group; return this; }

        public DubboInfo build() {
            return new DubboInfo(zk, group);
        }
    }
}
