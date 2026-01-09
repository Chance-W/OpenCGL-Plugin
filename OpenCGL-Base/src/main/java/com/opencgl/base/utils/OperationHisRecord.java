package com.opencgl.base.utils;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencgl.base.model.Base;

/**
 * @author Chance.W
 */
public class OperationHisRecord {

    private static final Logger log = LoggerFactory.getLogger(OperationHisRecord.class);

    public static void record(String... messages) {
        CompletableFuture.runAsync(() -> {
            String fileDate;
            String logDate;
            try {
                fileDate = new DateFormatUtil().getDateyyyyMMdd();
                logDate = new DateFormatUtil().getFormat7();
            }
            catch (ParseException e) {
                log.error("", e);
                return;
            }

            String hisF = Base.OPP_HIS_PATH + fileDate;
            File file = new File(hisF);

            if (!file.getParentFile().exists()) {
                if (file.getParentFile().mkdirs()) {
                    log.info("logs directory created");
                }
            }
            if (!file.exists()) {
                try {
                    if (file.createNewFile()) {
                        log.info("{} created successfully", file.getName());
                    }
                }
                catch (IOException e) {
                    log.error("", e);
                    return;
                }
            }

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(hisF, true), StandardCharsets.UTF_8)) {
                writer.write("\n#######" + logDate + "#######" + "\t\n");
                for (String message : messages) {
                    writer.write(message + "\n");
                }
                writer.write("\n");
            }
            catch (IOException e) {
                log.error("", e);
            }
        });
    }

    /**
     * Clear history for a specific date
     *
     * @param date Date string (filename)
     * @return true if successful
     */
    public static boolean clear(String date) {
        String hisF = Base.OPP_HIS_PATH + date;
        File file = new File(hisF);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public static List<String> getHistoryDates() {
        File prefixFile = new File(Base.OPP_HIS_PATH);
        File dir = prefixFile.getParentFile();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return new ArrayList<>();
        }

        String prefixName = prefixFile.getName();

        File[] files = dir.listFiles((d, name) -> name.startsWith(prefixName));
        if (files == null) {
            return new ArrayList<>();
        }

        return Arrays.stream(files)
            .map(File::getName)
            .map(name -> name.substring(prefixName.length()))
            .filter(date -> !date.isEmpty())
            .sorted(Collections.reverseOrder())
            .collect(Collectors.toList());
    }


    public static String read(String date) {
        String hisF = Base.OPP_HIS_PATH + date;
        File file = new File(hisF);
        if (!file.exists()) {
            return "";
        }

        try {
            return Files.readString(Paths.get(hisF));
        }
        catch (IOException e) {
            log.error("Failed to read history log: " + date, e);
            return "Error reading log: " + e.getMessage();
        }
    }
}
