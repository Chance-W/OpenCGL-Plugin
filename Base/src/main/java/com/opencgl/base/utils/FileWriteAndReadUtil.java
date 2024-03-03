package com.opencgl.base.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * @author Chance.W
 */
public class FileWriteAndReadUtil {
    private final Logger log = Logger.getLogger(FileWriteAndReadUtil.class);
    private final File file;

    public FileWriteAndReadUtil(File file) {
        this.file = file;
    }

    public void writeFile(String content) throws IOException {

        if (!file.getParentFile().getParentFile().exists()) {
            boolean result = file.getParentFile().getParentFile().mkdir();
            if (!result) {
                log.error("创建 " + file + " 失败");
            }
        }

        if (!file.getParentFile().exists()) {
            boolean result = file.getParentFile().mkdir();
            if (!result) {
                log.error("创建 " + file + " 失败");
            }
        }
        if (!file.exists()) {

            boolean result = file.createNewFile();
            if (!result) {
                log.error("创建 " + file + " 失败");
            }
        }
        List<String> list = readFile();
        for (Object o : list) {
            if (o.toString().equals(content)) {
                return;
            }
        }
        FileWriter writer;
        if (file.length() == 0) writer = new FileWriter(file);
        else {
            writer = new FileWriter(file, true);
        }
        writer.write(content + "\n");
        writer.close();
    }


    public void writeFile(List<String> list) throws IOException {

        if (!file.getParentFile().getParentFile().exists()) {
            boolean result = file.getParentFile().getParentFile().mkdir();
            if (!result) {
                log.error("创建 " + file + " 失败");
            }
        }

        if (!file.getParentFile().exists()) {
            boolean result = file.getParentFile().mkdir();
            if (!result) {
                log.error("创建 " + file + " 失败");
            }
        }
        if (!file.exists()) {

            boolean result = file.createNewFile();
            if (!result) {
                log.error("创建 " + file + " 失败");
            }
        }
        FileWriter writer = new FileWriter(file);
        for (Object o : list) {
            writer.write(o.toString() + "\n");
        }
        writer.close();
    }

    public List<String> readFile() throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
        List<String> line = new ArrayList<>();
        String str;
        while ((str = bufferedReader.readLine()) != null) {
            line.add(str);
        }
        bufferedReader.close();
        return line;
    }

    public static void main(String[] args) throws IOException {
        File file = new File("/.opencgl/conf/data/soapProject/url_list/url_list");
        FileWriteAndReadUtil writeAndReadFileUtil = new FileWriteAndReadUtil(file);
        writeAndReadFileUtil.writeFile("aaaaaaaa");
        writeAndReadFileUtil.writeFile("vvvvvvvvvv");
        writeAndReadFileUtil.writeFile("bbb");
        writeAndReadFileUtil.writeFile("nnn");
        writeAndReadFileUtil.writeFile("jjj");
        writeAndReadFileUtil.writeFile("dddd");

        System.out.println(writeAndReadFileUtil.readFile().size());

    }
}
