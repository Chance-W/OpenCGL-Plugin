package com.opencgl.dubbo.mock.zookeeper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.zookeeper.curator.CuratorZookeeperClient;

/**
 * 继承 Dubbo 内置的 Curator4 客户端。
 * Curator4 的 createPersistent 使用 creatingParentsIfNeeded()，
 * 不会创建 CONTAINER 节点，天然兼容 ZooKeeper 3.4.x。
 * 此类主要目的是作为自定义 SPI mockcurator 的实现入口。
 */
public class MockCurator4ZookeeperClient extends CuratorZookeeperClient {

    public MockCurator4ZookeeperClient(URL url) {
        super(url);
    }
}
