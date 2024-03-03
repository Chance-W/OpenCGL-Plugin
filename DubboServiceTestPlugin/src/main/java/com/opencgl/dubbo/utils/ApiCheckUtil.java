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
import com.opencgl.dubbo.model.DubboConfigureDto;


/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class ApiCheckUtil {
    private static final Logger logger = LoggerFactory.getLogger(ApiCheckUtil.class);

    public static void check(String envName, String zkAdress, String dubboGroup, String jarPath) throws Exception {
        String clientFile = Base.BASE_PATH+"/conf/dubboConfigFile/" + envName;
        String[] jarFile = jarPath.split(",");
        List<String> list = null;
        try {
            list = DubboConfigFileParseUtil.getInformationFromJar(jarFile);
        } catch (IOException ex) {
            ex.printStackTrace();
            logger.error("", ex);
        }
        List<String> listInterface = DubboConfigFileParseUtil.listInterface(jarFile, list);
        ZkClientOperate zkClientTest = new ZkClientOperate();
        List<URL> list2 = zkClientTest.getNewProvider(zkAdress, dubboGroup, listInterface, 20000);

        StringBuilder message;
        message = new StringBuilder();
        DubboConfigFileParseUtil.initializeFile(clientFile);
        for (URL url : list2) {
            logger.info("注册成功的接口为" + url);
            for (int j = 0; j < listInterface.size(); j++) {
                if (url.toString().contains(listInterface.get(j).replace("interface ", ""))) {
                    message.append(listInterface.get(j)).append("\n");
                    String service = listInterface.get(j).replace("interface ", "");
                    List<String> serviceInformation = DubboConfigFileParseUtil.getMethodAndParamer(jarFile, service);
                    for (int k = 0; k < serviceInformation.size(); k++) {
                        DubboConfigFileParseUtil.operClientParmeterFile(clientFile, "service[" + j + "]" + "[" + k + "]", serviceInformation.get(k));
                    }

                }
            }
        }
    }

    public static List<DubboConfigureDto> readMethodAndType(String filePath) throws Exception {
        List<DubboConfigureDto> dubboConfigureDtos = new ArrayList<>();
        File file = new File(filePath);
        InputStreamReader read = new InputStreamReader(new FileInputStream(file));
        BufferedReader bufferedReader = new BufferedReader(read);
        String lineTxt;//读取一行
        String info;
        while ((lineTxt = bufferedReader.readLine()) != null) {
            try {
                info = lineTxt.split("=")[1];
                if (lineTxt.contains("service[")) {
                    DubboConfigureDto dubboConfigureDto = DubboConfigureDto.builder().envInfo(filePath)
                            .interfaceInfo(info.split(",")[0])
                            .methodInfo(info.split(",")[1])
                            .requestType(info.split(",")[2])
                            .build();
                    dubboConfigureDtos.add(dubboConfigureDto);
                }
            } catch (Exception e) {
                throw new Exception(e.getMessage());
            }
        }
        return dubboConfigureDtos;
    }
}
