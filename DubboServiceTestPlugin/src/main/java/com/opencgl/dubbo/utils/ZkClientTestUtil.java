package com.opencgl.dubbo.utils;


import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkInterruptedException;
import org.I0Itec.zkclient.exception.ZkTimeoutException;
import org.apache.dubbo.common.URL;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class ZkClientTestUtil {
    private static final Logger log = LogManager.getLogger(ZkClientTestUtil.class);

    public ZkClientTestUtil() {
    }

    public static void main(String[] args) {
        ZkClientTestUtil zct = new ZkClientTestUtil();

        try {
            List<URL> serverlist = zct.getProvider("172.16.25.100:26011", "/GLOBAL_REGISTRY", 12000);

            for (URL url : serverlist) {
                log.info("提供者" + url.toString());

                String interfaceName = url.getParameter("interface");
                String methodsName = url.getParameter("methods");
                log.info("获取到的接口名为" + interfaceName + " 方法名为" + methodsName);

            }


        }
        catch (Exception var3) {
            var3.printStackTrace();
        }

    }

    /**
     * 将接口和方法配置写入到client配置文件
     */
    public void configInterAndMethod(String zookeeperServer, String rootPath, String fileName) throws Exception {
        List<URL> serverlist = getProvider(zookeeperServer, rootPath, 12000);
        for (URL url : serverlist) {
            log.info("提供者" + url.toString());

            String interfaceName = url.getParameter("interface");
            String methodsName = url.getParameter("methods");
            log.info("获取到的接口名为" + interfaceName + " 方法名为" + methodsName);
        }
    }

    public List getJarInterface() {
        return null;
    }

    //获取所有注册到zk的提供者
    public List<URL> getProvider(String zookeeperServer, String rootPath, int maxMsToWaitUntilConnected)
        throws Exception {
        long time = System.currentTimeMillis();
        List<URL> serverlist = getAllService(zookeeperServer, rootPath, maxMsToWaitUntilConnected);
        List<URL> serviceUrlList = new ArrayList();


        for (URL url : serverlist) {
            if (url.toString().contains("dubbo://")) {
                serviceUrlList.add(url);
                log.info("提供者" + url.toString());
            }
        }
        log.info("遍历完成.耗时 " + (System.currentTimeMillis() - time) + "毫秒");
        return serviceUrlList;
    }

    public List<URL> getAllService(String zookeeperServer, String rootPath, int maxMsToWaitUntilConnected)
        throws Exception {
        List<URL> serviceUrlList = new ArrayList<>();
        ZkClient client = null;
        try {
            client = new ZkClient(zookeeperServer, maxMsToWaitUntilConnected);
        }
        catch (ZkInterruptedException var15) {
            throw new Exception("连接注册中心中断，请检查注册中心\n" + var15.getMessage());
        }
        catch (ZkTimeoutException var16) {
            throw new Exception("接注册中心超时,超时时间" + maxMsToWaitUntilConnected + "ms");
        }
        catch (IllegalStateException var17) {
            throw new Exception("注册中心出现异常状态，请检查注册中心\n" + var17.getMessage());
        }

        List<String> list = client.getChildren(rootPath);
        Iterator<?> var9 = list.iterator();
        long start = System.currentTimeMillis();
        log.info("开始从zk拼接服务,开始时间为 " + start);
        while (var9.hasNext()) {


            String str = (String) var9.next();
            List<String> list1 = client.getChildren(rootPath + "/" + str);

            for (String str1 : list1) {
                List<String> listurl = client.getChildren(rootPath + "/" + str + "/" + str1);

                if (listurl != null && !listurl.isEmpty()) {
                    URL url = URL.valueOf(URLDecoder.decode(listurl.get(0)));

                    serviceUrlList.add(url);
                    log.info(url.toFullString());
                }
            }
        }
        log.info("zk拼接服务,总计时间为 " + (System.currentTimeMillis() - start) + "毫秒");
        return serviceUrlList;

    }

    public List<URL> getNewProvider(String zookeeperServer, String registrygroup, List<String> zkJarPath, int maxMsToWaitUntilConnected)
        throws Exception {

        List<URL> serverlist = getRegisty(zookeeperServer, registrygroup, zkJarPath, maxMsToWaitUntilConnected);
        List<URL> serviceUrlList = new ArrayList<>();
        for (URL url : serverlist) {
            if (url.toString().contains("dubbo://")) {
                serviceUrlList.add(url);
                log.info("提供者" + url.toString());
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
        }
        catch (ZkInterruptedException var15) {
            throw new Exception("连接注册中心中断，请检查注册中心\n" + var15.getMessage());
        }
        catch (ZkTimeoutException var16) {
            throw new Exception("接注册中心超时,超时时间" + maxMsToWaitUntilConnected + "ms");
        }
        catch (IllegalStateException var17) {
            throw new Exception("注册中心出现异常状态，请检查注册中心\n" + var17.getMessage());
        }
        Iterator<String> var9 = zkJarPath.iterator();
        List<String> list1 = null;

        while (var9.hasNext()) {

            String str1 = var9.next();
            try {
                list1 = client.getChildren("/" + registrygroup + "/" + str1.replace("interface ", ""));
            }
            catch (Exception e) {
                e.printStackTrace();
                log.error("", e);
            }
            Iterator<?> var12 = list1.iterator();
            while (var12.hasNext()) {
                String str2 = (String) var12.next();
                try {
                    List<String> listurl = client.getChildren("/" + registrygroup + "/" + str1.replace("interface ", "") + "/" + str2);
                    if (listurl != null && !listurl.isEmpty()) {
                        URL url = URL.valueOf(URLDecoder.decode(listurl.get(0)));
                        serviceUrlList.add(url);
                        log.info(url.toFullString());
                    }
                }
                catch (Exception e) {
                    log.info(str1 + " is not registry");
                }
            }
        }
        return serviceUrlList;
    }

}
