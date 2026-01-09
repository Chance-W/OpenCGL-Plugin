package com.opencgl.dubbossl.utils;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkInterruptedException;
import org.I0Itec.zkclient.exception.ZkTimeoutException;
import org.apache.dubbo.common.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZK 客户端工具类
 * 
 * @author Chance.W
 */
public class ZkClientTestUtil {
    private static final Logger log = LoggerFactory.getLogger(ZkClientTestUtil.class);

    public ZkClientTestUtil() {
    }

    /**
     * 获取所有注册到zk的提供者
     */
    public List<URL> getProvider(String zookeeperServer, String rootPath, int maxMsToWaitUntilConnected)
        throws Exception {
        List<URL> serverlist = getAllService(zookeeperServer, rootPath, maxMsToWaitUntilConnected);
        List<URL> serviceUrlList = new ArrayList<>();

        for (URL url : serverlist) {
            if (url.toString().contains("dubbo://")) {
                serviceUrlList.add(url);
                log.info("提供者" + url.toString());
            }
        }
        return serviceUrlList;
    }

    public List<URL> getAllService(String zookeeperServer, String rootPath, int maxMsToWaitUntilConnected)
        throws Exception {
        List<URL> serviceUrlList = new ArrayList<>();
        ZkClient client = null;
        try {
            client = new ZkClient(zookeeperServer, maxMsToWaitUntilConnected);
        } catch (ZkInterruptedException e) {
            throw new Exception("连接注册中心中断，请检查注册中心\n" + e.getMessage());
        } catch (ZkTimeoutException e) {
            throw new Exception("连接注册中心超时,超时时间" + maxMsToWaitUntilConnected + "ms");
        } catch (IllegalStateException e) {
            throw new Exception("注册中心出现异常状态，请检查注册中心\n" + e.getMessage());
        }

        try {
            List<String> list = client.getChildren(rootPath);
            for (String str : list) {
                List<String> list1 = client.getChildren(rootPath + "/" + str);
                for (String str1 : list1) {
                    List<String> listurl = client.getChildren(rootPath + "/" + str + "/" + str1);
                    if (listurl != null && !listurl.isEmpty()) {
                        URL url = URL.valueOf(URLDecoder.decode(listurl.get(0)));
                        serviceUrlList.add(url);
                        log.debug(url.toFullString());
                    }
                }
            }
        } finally {
            if (client != null) {
                client.close();
            }
        }
        return serviceUrlList;
    }

    public List<URL> getNewProvider(String zookeeperServer, String registrygroup, List<String> zkJarPath, int maxMsToWaitUntilConnected)
        throws Exception {
        List<URL> serverlist = getRegisty(zookeeperServer, registrygroup, zkJarPath, maxMsToWaitUntilConnected);
        List<URL> serviceUrlList = new ArrayList<>();
        for (URL url : serverlist) {
            if (url.toString().contains("dubbo://")) {
                serviceUrlList.add(url);
            }
        }
        return serviceUrlList;
    }

    public List<URL> getRegisty(String zookeeperServer, String registrygroup, List<String> zkJarPath, int maxMsToWaitUntilConnected)
        throws Exception {
        List<URL> serviceUrlList = new ArrayList<>();
        ZkClient client;
        try {
            client = new ZkClient(zookeeperServer, maxMsToWaitUntilConnected);
        } catch (ZkInterruptedException e) {
            throw new Exception("连接注册中心中断，请检查注册中心\n" + e.getMessage());
        } catch (ZkTimeoutException e) {
            throw new Exception("连接注册中心超时,超时时间" + maxMsToWaitUntilConnected + "ms");
        } catch (IllegalStateException e) {
            throw new Exception("注册中心出现异常状态，请检查注册中心\n" + e.getMessage());
        }

        try {
            for (String str1 : zkJarPath) {
                try {
                    List<String> list1 = client.getChildren("/" + registrygroup + "/" + str1.replace("interface ", ""));
                    for (String str2 : list1) {
                        try {
                            List<String> listurl = client.getChildren("/" + registrygroup + "/" + str1.replace("interface ", "") + "/" + str2);
                            if (listurl != null && !listurl.isEmpty()) {
                                URL url = URL.valueOf(URLDecoder.decode(listurl.get(0)));
                                serviceUrlList.add(url);
                            }
                        } catch (Exception e) {
                            log.debug(str1 + " is not registry");
                        }
                    }
                } catch (Exception e) {
                    log.error("", e);
                }
            }
        } finally {
            client.close();
        }
        return serviceUrlList;
    }
}
