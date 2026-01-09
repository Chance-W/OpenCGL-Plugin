package com.opencgl.dbmatchexecute.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author Chance.W
 */
public class DbUtil {
    private static final Logger logger = LoggerFactory.getLogger(DbUtil.class);

    public static Connection getConnection(String url, String userName, String password) {
        try {
            if (url.contains("udal")) {
                Class.forName("com.mysql.jdbc.Driver");
            }
            else if (url.contains("mysql")) {
                Class.forName("com.mysql.jdbc.Driver");
            }
            else {
                Class.forName("oracle.jdbc.OracleDriver");
            }
            //通过DriverManager类的getConnection方法指定三个参数,连接数据库
            // 数据库连接对象
            Connection conn = DriverManager.getConnection(url, userName, password);
            logger.info("连接 {} 数据库成功!!!", url);
            //返回连接对象
            return conn;
        }
        catch (ClassNotFoundException | SQLException e) {
            logger.error("", e);
        }
        return null;
    }
}
