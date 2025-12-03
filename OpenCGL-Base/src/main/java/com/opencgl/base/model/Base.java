package com.opencgl.base.model;

import java.io.File;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class Base {
    public static final String BASE_PATH = System.getProperty("user.home") + File.separator + ".opencgl_new" + File.separator;

    public static final String PLUGIN_PATH = BASE_PATH + "ext-plugin" + File.separator;

    public static final String COMMON_CONF = BASE_PATH + "conf" + File.separator;

    public static final String OPP_HIS_PATH = BASE_PATH + "logs" + File.separator + "record" + File.separator + "message_record.log_";

    public static final String DB_PATH = COMMON_CONF + "dbs" + File.separator;

    public static final String DAY_TYPE = "DATE";
    public static final String MONTH_TYPE = "MONTH";
    public static final String YEAR_TYPE = "YEAR";
    public static final String YEAR_MONTH_TYPE = "YEAR_MONTH";

}
