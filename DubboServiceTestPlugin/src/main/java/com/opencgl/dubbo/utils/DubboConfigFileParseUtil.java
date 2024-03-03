package com.opencgl.dubbo.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.opencgl.dubbo.model.DubboInfo;
import com.opencgl.dubbo.model.DubboRequest;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class DubboConfigFileParseUtil {
    private static final Logger logger = LoggerFactory.getLogger(DubboConfigFileParseUtil.class);

    public static ObservableList<String> getFileList(String path) {
        List<String> fileList = new ArrayList<>();
        File file = new File(path);
        if (!file.exists()) {
            logger.info(path + "下相应文件不存在,开始创建目录,文件创建结果" + file.mkdirs());
        }
        for (int i = 0; i < Objects.requireNonNull(file.listFiles()).length; i++) {
            if (Objects.requireNonNull(file.listFiles())[i].isFile()) {
                fileList.add(Objects.requireNonNull(file.listFiles())[i].getName());
            }
        }
        return FXCollections.observableList(fileList);
    }

    public static void initializeFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            logger.info("文件不存在则认为是初次创建,直接结束进程");
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String tempString;
            int line = 1;
            // 一次读入一行，直到读入null为文件结束
            while ((tempString = reader.readLine()) != null) {
                // 显示行号
                if (tempString.startsWith("service")) {
                    logger.info("delete record {}:{}", line, tempString);
                    logger.info("删除结果 {}", deleteLine(line, filePath));
                }
                line++;
            }
        }
        catch (IOException e) {
            logger.error("", e);
        }
        finally {
            System.gc();
        }
    }

    /**
     * 删除service[][]操作,防止接口变少未替换配置导致异常
     */
    public static String deleteLine(int indexLine, String filePath) {
        int counter = 1;
        FileWriter writer = null;
        BufferedReader buffReader = null;
        StringBuilder tempTxt = new StringBuilder();
        try {
            File file = new File(filePath);
            FileReader freader = new FileReader(file);
            buffReader = new BufferedReader(freader);
            while (buffReader.ready()) {
                if (counter != indexLine) {
                    tempTxt.append(buffReader.readLine()).append("\n");
                }
                else {
                    buffReader.readLine();
                }
                counter++;
            }
            buffReader.close();
            writer = new FileWriter(file);
            writer.write(tempTxt.toString());
        }
        catch (Exception e) {
            return "fail :" + e.getCause();
        }
        finally {
            try {

                if (writer != null) {
                    buffReader.close();
                    writer.flush();
                    writer.close();
                }
            }
            catch (IOException e) {
                logger.error("", e);
            }
        }
        return "success!";
    }

    public static void operClientParmeterFile(String path, String key, String newValue) {
        String temp;
        Map<String, String> clientParmeter = new LinkedHashMap<>();
        try {
            File file = new File(path);
            if (!file.exists()) {
                boolean res = file.createNewFile();
            }
            BufferedReader br = new BufferedReader
                (new InputStreamReader(new FileInputStream(file)));
            StringBuilder buf = new StringBuilder();

            // 保存该行前面的内容
            while ((temp = br.readLine()) != null) {
                String[] parmeter = temp.split("=");
                if (parmeter.length == 1) {
                    clientParmeter.put(parmeter[0], "");
                    logger.info("{}", parmeter[0]);
                }
                else {
                    clientParmeter.put(parmeter[0], parmeter[1]);
                    logger.info(parmeter[0] + parmeter[1]);
                }


            }
            clientParmeter.put(key, newValue);
            //buf=buf.append(key+"="+newValue);
            for (Map.Entry<String, String> entry : clientParmeter.entrySet()) {
                logger.info("key:" + entry.getKey() + "," + "value:" + entry.getValue());
                buf.append(entry.getKey()).append("=").append(entry.getValue());
                buf.append(System.getProperty("line.separator"));
            }
            br.close();
            PrintWriter pw = new PrintWriter(new FileOutputStream(file));
            pw.write(buf.toString().toCharArray());
            pw.flush();
            pw.close();
        }
        catch (IOException e) {
            logger.error("", e);
        }
    }


    public static List<String> readMethodAndType(String filePath) throws Exception {
        List<String> serviceList = new ArrayList<>();
        File file = new File(filePath);
        InputStreamReader read = new InputStreamReader(new FileInputStream(file));
        BufferedReader bufferedReader = new BufferedReader(read);
        String lineTxt;//读取一行
        String info;
        while ((lineTxt = bufferedReader.readLine()) != null) {
            try {
                info = lineTxt.split("=")[1];
            }
            catch (Exception e) {
                throw new Exception(e.getMessage());
            }

            if (lineTxt.contains("service[")) {
                serviceList.add(info);
            }
        }
        return serviceList;
    }

    public static DubboInfo readClientParameter(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            logger.info(file + "不存在");
            return null;
        }
        else {
            Properties prop = new Properties();
            try {
                //装载配置文件
                prop.load(new FileInputStream(file));
            }
            catch (IOException e) {
                logger.error("", e);
            }
            //返回获取的值
            return DubboInfo.builder()
                .group(prop.getProperty("registryGroup"))
                .zk(prop.getProperty("registryAddress"))
                .build();
        }

    }

    public static String readClientParameter(String filePath, String name) {
        File file = new File(filePath);
        if (!file.exists()) {
            logger.info(file + "不存在");
            return null;
        }
        else {
            Properties prop = new Properties();
            try {
                //装载配置文件
                prop.load(new FileInputStream(file));
            }
            catch (IOException e) {
                logger.error("", e);
            }
            //返回获取的值
            return prop.getProperty(name);
        }

    }

    public static List<String> getInformationFromJar(String[] jarFile) throws IOException {
        List<String> list = new ArrayList<>();
        //通过将给定路径名字符串转换为抽象路径名来创建一个新File实例
        for (String s : jarFile) {
            File f = new File(s);
            URL url1 = f.toURI().toURL();
            URLClassLoader myClassLoader = new URLClassLoader(new URL[]{url1}, Thread.currentThread().getContextClassLoader());

            //通过jarFile和JarEntry得到所有的类
            JarFile jar = new JarFile(s);
            //返回zip文件条目的枚举
            Enumeration<JarEntry> enumFiles = jar.entries();
            JarEntry entry;

            //测试此枚举是否包含更多的元素
            while (enumFiles.hasMoreElements()) {
                entry = enumFiles.nextElement();
                if (!entry.getName().contains("META-INF")) {
                    String classFullName = entry.getName();
                    if (!classFullName.endsWith(".class")) {
                        classFullName = classFullName.substring(0, classFullName.length() - 1);
                    }
                    else {
                        //去掉后缀.class
                        String className = classFullName.substring(0, classFullName.length() - 6).replace("/", ".");
                        //打印类名
                        logger.info("*****************************");
                        logger.info("全类名:{}", className);
                        list.add("ClassName=" + className);
                    }
                    logger.info("==========================");
                }

            }
        }

        return list;
    }

    public static List<String> getMethodAndParamer(String[] jarFile, String clazz)
        throws ClassNotFoundException, MalformedURLException {
        List<String> list = new ArrayList<>();
        //通过将给定路径名字符串转换为抽象路径名来创建一个新File实例
        StringBuilder info;
        URLClassLoader myClassLoader;
        Class<?> myclass;
        URL[] urls = new URL[jarFile.length];


        for (int i = 0; i < jarFile.length; i++) {
            File file = new File(jarFile[i]);
            urls[i] = file.toURI().toURL();
        }
        myClassLoader = new URLClassLoader(urls);
        myclass = myClassLoader.loadClass(clazz);
        Method[] methods = myclass.getMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            logger.info("方法名称:" + methodName);
            if (method.getParameterTypes().length == 0) {
                info = new StringBuilder(clazz + "," + methodName + "," + "{}");
            }
            else {
                Class<?>[] parameterTypes = method.getParameterTypes();
                info = new StringBuilder(methodName);
                for (Class<?> clas : parameterTypes) {
                    String parameterName = clas.getSimpleName();
                    String fullparameterName = clas.getName();
                    logger.info("参数类型:" + parameterName);
                    logger.info("参数全路径为:" + fullparameterName);
                    info.append(",").append(fullparameterName);
                }
                info.insert(0, clazz + ",");
            }
            list.add(info.toString());
            logger.info("==========================");
        }
        return list;
    }

    public static String jsonMessage(String[] jarFile, String clazz)
        throws MalformedURLException, ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        logger.info("开始JSON转换");
        URLClassLoader myClassLoader;
        Class<?> myclass;
        URL[] urls = new URL[jarFile.length];

        for (int i = 0; i < jarFile.length; i++) {
            File file = new File(jarFile[i]);
            urls[i] = file.toURI().toURL();
        }
        myClassLoader = new URLClassLoader(urls);
        myclass = myClassLoader.loadClass(clazz);
        Object inst = createInstance(myclass);
        String json1 = JSON.toJSONString(inst, SerializerFeature.WriteMapNullValue, SerializerFeature.PrettyFormat);

        logger.info(json1.replace("null", "\"\""));
        return json1.replace("null", "\"\"");

    }


    public static Object createInstance(Class<?> myclass)
        throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        Field[] fieldList = myclass.getDeclaredFields();
        Object inst = myclass.getDeclaredConstructor().newInstance();
        for (Field field : fieldList) {
            Class<?> type = field.getType();
            if (!field.getName().contains("serialVersionUID")) {
                field.setAccessible(true);
                field.set(inst, createFieldInstance(type, field));
            }
        }
        return inst;
    }


    private static Object createFieldInstance(Class<?> type, Field field)
        throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String typeName = type.getName();
        if (typeName.startsWith("com")) {
            return createInstance(type);
        }
        else if (typeName.startsWith("java.util.List")) {
            List<Object> list = new ArrayList<>();
            Type paramType = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            list.add(createInstance((Class<?>) paramType));
            return list;
        }
        return null;
    }


    //list1为jar包中的接口,list2是从zk中遍历出来的提供者
    public List<String> getSame(List<String> list1, List<String> list2) {
        List<String> result = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        logger.info("开始遍历时间: {}", startTime);
        for (String interfaceName : list1) {
            for (String zkinterfaceName : list2) {
                if (zkinterfaceName.contains(interfaceName)) {
                    result.add(interfaceName);
                }
            }
        }
        logger.info("方法 耗时：" + (System.currentTimeMillis() - startTime) + " 毫秒");
        return result.stream().distinct().collect(Collectors.toList());

    }

    public static List<String> listInterface(String[] jarFile, List<String> clazz)
        throws MalformedURLException, ClassNotFoundException {
        List<String> list = new ArrayList<>();
        URLClassLoader myClassLoader;
        Class<?> myclass;
        URL[] urls = new URL[jarFile.length];
        for (int i = 0; i < jarFile.length; i++) {
            File file = new File(jarFile[i]);
            urls[i] = file.toURI().toURL();
        }
        myClassLoader = new URLClassLoader(urls);
        for (String s : clazz) {
            myclass = myClassLoader.loadClass(s.replace("ClassName=", ""));
            if (myclass.isInterface()) {
                list.add(myclass.toString());
            }
        }
        return list;
    }


    public String getInterfaceCharacter(String character, String type) {
        String value;
        if ("service".equals(type)) {
            value = character.substring(character.indexOf("ClassName=") + "ClassName=".length());
        }
        else {
            return "类型错误";
        }
        return value;
    }


    public static void deleteFile(String path) {
        logger.info("begin delete file {}", path);
        File file = new File(path);
        if (file.exists()) {
            boolean delete = file.delete();
            logger.info("delete file result:{}", delete);
        }
    }

    public static List<String> getProviders(String nodePath, String zkAdress) {
        List<String> providerList = new ArrayList<>();
        ZkClient zkClient = new ZkClient(zkAdress, 10000);
        List<String> providers = zkClient.getChildren(nodePath + "/providers");
        for (String provider : providers) {
            providerList.add(provider.substring(provider.indexOf("dubbo"), provider.lastIndexOf("%2F")).replace("%3A%2F%2F", "://").replace("%3A", ":"));
        }
        return providerList;
    }

}

