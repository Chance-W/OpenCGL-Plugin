package com.xtool.opencgl.utils;

import java.io.File;

import com.opencgl.base.model.Base;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class ConfigureUtil {
    private static final String BASE_PATH = Base.COMMON_CONF + "ftp_conf" + File.separator;

    public static String getConfigurePath() {
        return BASE_PATH;
    }

    public static String getConfigurePath(String fileName) {
        return BASE_PATH + fileName;
    }

    public static File getConfigureFile(String fileName) {
        return new File(getConfigurePath(fileName));
    }
}
