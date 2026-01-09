package com.opencgl.dubbo.mock.zookeeper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.zookeeper.ZookeeperClient;
import org.apache.dubbo.remoting.zookeeper.ZookeeperTransporter;

public class MockZookeeperTransporter implements ZookeeperTransporter {
    @Override
    public ZookeeperClient connect(URL url) {
        System.out.println("[MOCK-CURATOR] MockZookeeperTransporter.connect() 被调用 " + url);
        return new MockCurator4ZookeeperClient(url);
    }

    @Override
    public void destroy() {
        // Dubbo 3.2.4 added destroy to ZookeeperTransporter
    }
}
