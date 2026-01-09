package com.opencgl.dubbossl.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.I0Itec.zkclient.ZkClient;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Dubbo SSL 配置文件解析工具
 * 
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
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    fileList.add(f.getName());
                }
            }
        }
        return FXCollections.observableList(fileList);
    }





    public static List<String> getInformationFromJar(String[] jarFile) throws IOException {
        List<String> list = new ArrayList<>();
        for (String s : jarFile) {
            File f = new File(s);
            if (!f.exists()) continue;
            
            URL url1 = f.toURI().toURL();
            try (JarFile jar = new JarFile(s)) {
                Enumeration<JarEntry> enumFiles = jar.entries();
                JarEntry entry;
                while (enumFiles.hasMoreElements()) {
                    entry = enumFiles.nextElement();
                    if (!entry.getName().contains("META-INF")) {
                        String classFullName = entry.getName();
                        if (classFullName.endsWith(".class")) {
                            String className = classFullName.substring(0, classFullName.length() - 6).replace("/", ".");
                            list.add("ClassName=" + className);
                        }
                    }
                }
            }
        }
        return list;
    }

    public static List<String> listInterface(String[] jarFile, List<String> clazz) throws MalformedURLException, ClassNotFoundException {
        List<String> list = new ArrayList<>();
        URL[] urls = new URL[jarFile.length];
        for (int i = 0; i < jarFile.length; i++) {
            File file = new File(jarFile[i]);
            if (!file.exists()) continue;
            urls[i] = file.toURI().toURL();
        }
        URLClassLoader myClassLoader = new URLClassLoader(urls);
        for (String s : clazz) {
            try {
                Class<?> myclass = myClassLoader.loadClass(s.replace("ClassName=", ""));
                if (myclass.isInterface()) {
                    list.add(myclass.toString());
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return list;
    }

    public static List<String> getMethodAndParamer(String[] jarFile, String clazz) throws ClassNotFoundException, MalformedURLException {
        List<String> list = new ArrayList<>();
        StringBuilder info;
        URL[] urls = new URL[jarFile.length];
        for (int i = 0; i < jarFile.length; i++) {
            File file = new File(jarFile[i]);
            urls[i] = file.toURI().toURL();
        }
        URLClassLoader myClassLoader = new URLClassLoader(urls);
        Class<?> myclass = myClassLoader.loadClass(clazz);
        Method[] methods = myclass.getMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if (method.getParameterTypes().length == 0) {
                info = new StringBuilder(clazz + "," + methodName + ",{}");
            } else {
                Class<?>[] parameterTypes = method.getParameterTypes();
                info = new StringBuilder(methodName);
                for (Class<?> clas : parameterTypes) {
                    info.append(",").append(clas.getName());
                }
                info.insert(0, clazz + ",");
            }
            list.add(info.toString());
        }
        return list;
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
        try {
            List<String> providers = zkClient.getChildren(nodePath + "/providers");
            for (String provider : providers) {
                providerList.add(provider.substring(provider.indexOf("dubbo"), provider.lastIndexOf("%2F")).replace("%3A%2F%2F", "://").replace("%3A", ":"));
            }
        }
        catch (Exception e) {
            logger.error("", e);
        }
        finally {
            zkClient.close();
        }
        return providerList;
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
}
