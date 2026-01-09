package com.opencgl.dubbo.mock.zookeeper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.zookeeper.curator5.Curator5ZookeeperClient;
import org.apache.zookeeper.CreateMode;
import org.apache.curator.framework.CuratorFramework;
import java.lang.reflect.Field;

public class MockCurator5ZookeeperClient extends Curator5ZookeeperClient {
    
    public MockCurator5ZookeeperClient(URL url) {
        super(url);
    }

    private CuratorFramework getCurator() {
        try {
            Field f = Curator5ZookeeperClient.class.getDeclaredField("client");
            f.setAccessible(true);
            return (CuratorFramework) f.get(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void create(String path, boolean ephemeral, boolean sequential) {
        if (!ephemeral && !sequential) {
            createPersistent(path, false);
            return;
        }
        super.create(path, ephemeral, sequential);
    }

    @Override
    public void createPersistent(String path, boolean ephemeral) {
        try {
            if (ephemeral) {
                createEphemeral(path, false);
            } else {
                getCurator().create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(path);
            }
        } catch (org.apache.zookeeper.KeeperException.NodeExistsException e) {
            // ignore
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public void createPersistent(String path, String data, boolean ephemeral) {
        try {
            if (ephemeral) {
                createEphemeral(path, data, false);
            } else {
                getCurator().create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(path, data.getBytes("UTF-8"));
            }
        } catch (org.apache.zookeeper.KeeperException.NodeExistsException e) {
            try {
                getCurator().setData().forPath(path, data.getBytes("UTF-8"));
            } catch (Exception ex) {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
