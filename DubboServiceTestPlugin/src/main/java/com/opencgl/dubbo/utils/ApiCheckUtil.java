package com.opencgl.dubbo.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.dubbo.common.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.model.Base;
import com.opencgl.dubbo.dao.DubboWidgetDao;
import com.opencgl.dubbo.model.DubboConfigureDto;
import com.opencgl.dubbo.model.DubboEnvConfig;


/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class ApiCheckUtil {
    private static final Logger logger = LoggerFactory.getLogger(ApiCheckUtil.class);

    public static void check(String envName, String zkAdress, String dubboGroup, String jarPath) throws Exception {
        String[] jarFile = jarPath.split(",");
        List<String> list = null;
        try {
            list = DubboConfigFileParseUtil.getInformationFromJar(jarFile);
        } catch (IOException ex) {
            ex.printStackTrace();
            logger.error("", ex);
        }
        List<String> listInterface = DubboConfigFileParseUtil.listInterface(jarFile, list);
        ZkClientTestUtil zkClientTest = new ZkClientTestUtil();
        List<URL> list2 = zkClientTest.getNewProvider(zkAdress, dubboGroup, listInterface, 20000);

        List<String> totalServiceInfo = new ArrayList<>();
        
        for (URL url : list2) {
            logger.info("注册成功的接口为" + url);
            for (int j = 0; j < listInterface.size(); j++) {
                if (url.toString().contains(listInterface.get(j).replace("interface ", ""))) {
                    String service = listInterface.get(j).replace("interface ", "");
                    List<String> serviceInformation = DubboConfigFileParseUtil.getMethodAndParamer(jarFile, service);
                    totalServiceInfo.addAll(serviceInformation);
                }
            }
        }
        
        // Update DB
        DubboWidgetDao dao = new DubboWidgetDao();
        DubboEnvConfig config = dao.queryEnvConfig(envName);
        if (config != null) {
            config.setServiceData(com.alibaba.fastjson.JSON.toJSONString(totalServiceInfo));
            dao.updateEnvConfig(config);
            logger.info("Environment {} service data updated in DB", envName);
        } else {
            logger.warn("Environment {} not found in DB, cannot update service data", envName);
        }
    }
}
