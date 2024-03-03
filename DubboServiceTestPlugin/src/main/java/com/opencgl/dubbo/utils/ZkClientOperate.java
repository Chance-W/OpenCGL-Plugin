package com.opencgl.dubbo.utils;


import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.I0Itec.zkclient.ZkClient;
import org.apache.dubbo.common.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public class ZkClientOperate {
    private static final Logger log = LoggerFactory.getLogger(ZkClientOperate.class);

    public static void main(String[] args) {
        ZkClientOperate zct = new ZkClientOperate();

        try {
            List<URL> serverlist = zct.getProvider("172.16.25.100:26011", "/GLOBAL_REGISTRY", 12000);

            for (URL url : serverlist) {
                System.out.println("提供者" + url.toString());
                String interfaceName = url.getParameter("interface");
                String methodsName = url.getParameter("methods");
                System.out.println("获取到的接口名为" + interfaceName + " 方法名为" + methodsName);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            log.error("", e);
        }
    }

    //将接口和方法配置写入到client配置文件
    public void configInterAndMethod(String zookeeperServer, String rootPath, String fileName) {
        List<URL> serverlist = getProvider(zookeeperServer, rootPath, 12000);
        for (URL url : serverlist) {
            log.info("提供者" + url.toString());
            String interfaceName = url.getParameter("interface");
            String methodsName = url.getParameter("methods");
            log.info("获取到的接口名为" + interfaceName + " 方法名为" + methodsName);
        }
    }

    //获取所有注册到zk的提供者
    public List<URL> getProvider(String zookeeperServer, String rootPath, int maxMsToWaitUntilConnected) {
        long time = System.currentTimeMillis();
        List<URL> serverlist = getAllService(zookeeperServer, rootPath, maxMsToWaitUntilConnected);
        List<URL> serviceUrlList = new ArrayList<>();
        for (URL url : serverlist) {
            if (url.toString().contains("dubbo://")) {
                serviceUrlList.add(url);
                log.info("提供者" + url);
            }
        }
        log.info("遍历完成.耗时 " + (System.currentTimeMillis() - time) + "毫秒");
        return serviceUrlList;
    }

    public List<URL> getAllService(String zookeeperServer, String rootPath, int maxMsToWaitUntilConnected) {
        List<URL> serviceUrlList = new ArrayList<>();
        ZkClient client;
        client = new ZkClient(zookeeperServer, maxMsToWaitUntilConnected);
        List<String> list = client.getChildren(rootPath);
        Iterator<?> var9 = list.iterator();
        long start = System.currentTimeMillis();
        log.info("开始从zk拼接服务,开始时间为 " + start);
        while (var9.hasNext()) {
            String str = (String) var9.next();
            List<String> list1 = client.getChildren(rootPath + "/" + str);
            for (String str1 : list1) {
                List<String> listUrl = client.getChildren(rootPath + "/" + str + "/" + str1);
                if (listUrl != null && !listUrl.isEmpty()) {
                    URL url = URL.valueOf(URLDecoder.decode(listUrl.get(0), StandardCharsets.UTF_8));
                    serviceUrlList.add(url);
                    log.info(url.toFullString());
                }
            }
        }
        log.info("zk拼接服务,总计时间为 " + (System.currentTimeMillis() - start) + "毫秒");
        return serviceUrlList;

    }

    public List<URL> getNewProvider(String zookeeperServer, String registryGroup, List<String> zkJarPath, int maxMsToWaitUntilConnected) {
        List<URL> serverlist = getRegisty(zookeeperServer, registryGroup, zkJarPath, maxMsToWaitUntilConnected);
        List<URL> serviceUrlList = new ArrayList<>();
        for (URL url : serverlist) {
            if (url.toString().contains("dubbo://")) {
                serviceUrlList.add(url);
                log.info("提供者" + url);
            }
        }
        return serviceUrlList;
    }

    public List<URL> getRegisty(String zookeeperServer, String registrygroup, List<String> zkJarPath, int maxMsToWaitUntilConnected) {
        List<URL> serviceUrlList = new ArrayList<>();
        ZkClient client = new ZkClient(zookeeperServer, maxMsToWaitUntilConnected);
        Iterator<?> var9 = zkJarPath.iterator();
        List<String> list1;
        while (var9.hasNext()) {
            String str1 = (String) var9.next();
            list1 = client.getChildren("/" + registrygroup + "/" + str1.replace("interface ", ""));
            for (String str2 : list1) {
                List<String> listUrl = client.getChildren("/" + registrygroup + "/" + str1.replace("interface ", "") + "/" + str2);
                if (listUrl != null && !listUrl.isEmpty()) {
                    URL url = URL.valueOf(URLDecoder.decode(listUrl.get(0), StandardCharsets.UTF_8));
                    serviceUrlList.add(url);
                    log.info(url.toFullString());
                }
            }
        }
        return serviceUrlList;
    }
}
