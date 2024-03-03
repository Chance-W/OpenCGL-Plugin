package com.opencgl.base.utils;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.model.Base;

/**
 * @author Chance.W
 */
public class OperationHisRecord {

    public static void record(String message) {
        Logger log = LoggerFactory.getLogger(OperationHisRecord.class);
        String fileDate = null;
        String logDate = null;
        try {
            fileDate = new DateFormatUtil().getDateyyyyMMdd();
            logDate = new DateFormatUtil().getFormat7();
        } catch (ParseException e) {
            e.printStackTrace();
            log.error("",e);
        }


        String hisF = Base.OPP_HIS_PATH + fileDate;
        File file = new File(hisF);
        //String str = "";

        if (!file.getParentFile().exists()) {
            log.info("logs目录不存在,开始创建操作文件夹");
            if (file.getParentFile().mkdirs()) {
                log.info(file.getName() + "创建成功");
            }
        }
        if (!file.exists()) {
            log.info("文件不存在,开始创建操作日志文件");
            try {
                if (file.createNewFile()) {
                    log.info(file.getName() + "创建成功");
                }
            } catch (IOException e) {
                e.printStackTrace();
                log.error("",e);
            }
        }


        FileWriter writer = null;
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
            writer = new FileWriter(hisF, true);
            writer.write("\n#######" + logDate + "#######" + "\t\n");
            writer.write(message + "\n");
        } catch (IOException e) {
            e.printStackTrace();
            log.error("",e);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                log.error("",e);
            }
        }
    }
}
