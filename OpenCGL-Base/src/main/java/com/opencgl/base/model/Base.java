package com.opencgl.base.model;

import java.io.File;

/**
 * 插件基础路径常量 (Unified with BaseEnum from OpenCGL_New)
 * 
 * @author Chance.W
 */
public class Base {
    
    /** 应用根目录: ~/.opencgl/ */
    public static final String BASE_PATH = System.getProperty("user.home") + File.separator + ".opencgl" + File.separator;

    /** 插件目录: ~/.opencgl/ext-plugin/ */
    public static final String PLUGIN_PATH = BASE_PATH + "ext-plugin" + File.separator;

    /** 通用配置目录: ~/.opencgl/conf/ */
    public static final String COMMON_CONF = BASE_PATH + "conf" + File.separator;
    
    /** 基础配置目录: ~/.opencgl/conf/base/ */
    public static final String BASE_CONF_PATH = COMMON_CONF + "base" + File.separator;

    /** 配置文件: ~/.opencgl/conf/base/config.json */
    public static final String BASE_CONF_FILE = BASE_CONF_PATH + "config.json";

    /** 操作历史记录路径 */
    public static final String OPP_HIS_PATH = BASE_PATH + "logs" + File.separator + "record" + File.separator + "message_record.log_";

    /** 数据库目录: ~/.opencgl/conf/dbs/ */
    public static final String DB_PATH = COMMON_CONF + "dbs" + File.separator;

    /** 应用锁文件: ~/.opencgl/lock/opencgl.lock */
    public static final String OPEN_CGL_LOCK_FILE = BASE_PATH + "lock" + File.separator + "opencgl.lock";

    /** 默认图标路径 */
    public static final String DEFAULT_ICON_PATH = "com/opencgl/icon/logo.png";

    private Base() {
        // 工具类，禁止实例化
    }
}
