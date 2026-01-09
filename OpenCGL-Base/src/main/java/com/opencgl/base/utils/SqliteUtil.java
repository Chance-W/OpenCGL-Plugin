package com.opencgl.base.utils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.opencgl.base.model.Base;

/**
 * @author Chance.W
 */
@SuppressWarnings({"unused"})
public class SqliteUtil {
    private static final Logger log = LoggerFactory.getLogger(SqliteUtil.class);

    private static final String SQLITE_PREFIX = "jdbc:sqlite";
    private static final String SQLITE_POSTFIX = "data.db";

    @SuppressWarnings("SqlNoDataSourceInspection")
    private static final String SQL = "select COUNT(*) from sqlite_master WHERE name= ?";

    static {
        try {
            init();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void init() throws SQLException {
        File dbFolder = new File(Base.DB_PATH);
        if (!dbFolder.exists()) {
            log.info("create dbs folder ：[{}]", dbFolder.mkdirs());
        }
    }


    private static <T> T apply(Function<Connection, T> function) {
        try (Connection conn = DriverManager.getConnection(SQLITE_PREFIX + ":" + Base.DB_PATH + SQLITE_POSTFIX)) {
            conn.setAutoCommit(false); // 设置手动提交
            return function.apply(conn);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void apply(Consumer<Connection> function) {
        try (Connection conn = DriverManager.getConnection(SQLITE_PREFIX + ":" + Base.DB_PATH + SQLITE_POSTFIX)) {
            conn.setAutoCommit(false); // 设置手动提交
            function.accept(conn);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * private static Connection getConnection() throws SQLException {
     * File dbFolder = new File(Base.DB_PATH);
     * if (!dbFolder.exists()) {
     * log.info("create dbs folder ：[{}]", dbFolder.mkdirs());
     * }
     * Connection conn = DriverManager.getConnection(SQLITE_PREFIX + ":" + Base.DB_PATH + SQLITE_POSTFIX);
     * conn.setAutoCommit(false);
     * conn.createStatement();
     * return conn;
     * }
     */


    public static boolean checkTableExist(String tableName) throws Exception {
        log.info("check table {}", tableName);
        return apply((conn) -> {
            try (PreparedStatement ps = conn.prepareStatement(SQL)) {
                ps.setString(1, tableName);
                try (ResultSet result = ps.executeQuery()) {
                    int i = result.getInt(1);
                    log.info("the table size is {}", i);
                    return i != 0;
                }
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

    }

    public static void update(String sql, Object... params) throws Exception {
        executeSql(sql, params);
    }

    public static void delete(String sql, Object... params) throws Exception {
        executeSql(sql, params);
    }

    public static void query(String sql) throws Exception {
        apply((connection -> {
            try {
                PreparedStatement ps = connection.prepareStatement(sql);
            }
            catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    private static synchronized void executeSql(String sql, Object[] params) {
        apply((connection -> {
            try (PreparedStatement ps = prepareStatementAndParams(sql, params, connection)) {
                ps.execute();
                connection.commit();
            }
            catch (Exception e) {
                log.error("", e);
                try {
                    connection.rollback();
                }
                catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
                throw new RuntimeException(e);
            }
        }));

    }

    public static synchronized Long insert(String sql, Object... params) throws Exception {
        return apply(connection -> {
            try (PreparedStatement ps = prepareStatementAndParams(sql, params, connection)) {
                int affectedRows = ps.executeUpdate();
                connection.commit();
                // 检查是否有插入成功
                if (affectedRows > 0) {
                    // 获取生成的主键值
                    ResultSet generatedKeys = ps.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        log.info("insert success,generate primary key is：{}", generatedKeys.getLong(1));
                        return generatedKeys.getLong(1);
                    }
                    else {
                        log.warn("insert success,but can not get the generated primary key");
                    }
                }
                else {
                    log.error("insert fail");
                }
                return null;
            }
            catch (Exception e) {
                log.error("", e);
                try {
                    connection.rollback();
                }
                catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
                throw new RuntimeException(e);
            }
        });

    }

    public static <T> List<T> queryForList(String sql, Class<T> clazz, Object... params) throws Exception {
        return apply(connection -> {
            List<Map<String, Object>> json = new ArrayList<>();
            try (PreparedStatement ps = prepareStatementAndParams(sql, params, connection);
                 ResultSet result = ps.executeQuery()) {
                while (result.next()) {
                    T object = clazz.getDeclaredConstructor().newInstance();
                    ResultSetMetaData metaData = result.getMetaData();
                    int count = metaData.getColumnCount();
                    Map<String, Object> cache = new HashMap<>(16);
                    json.add(cache);
                    for (int i = 1; i <= count; i++) {
                        String name = metaData.getColumnName(i);
                        cache.put(name, result.getObject(name));
                    }
                }
            }
            catch (Exception e) {
                log.error("", e);
                throw new RuntimeException(e);
            }
            log.info("query result {}", JSONObject.parseArray(JSON.toJSONString(json), clazz));
            return JSONObject.parseArray(JSON.toJSONString(json), clazz);
        });
    }

    private static PreparedStatement prepareStatementAndParams(String sql, Object[] params, Connection connection)
        throws SQLException, ParseException {
        log.info("execute sql is " + sql);
        log.info("params [{}]", JSON.toJSONString(params));
        PreparedStatement ps = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (ValidateUtil.isNumber(param)) {
                ps.setLong(i + 1, Long.parseLong(param.toString()));
            }
            else if (ValidateUtil.isTimeStap(param)) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                // ps.setTimestamp(i + 1, new Timestamp(new Date().getTime()));
                ps.setTimestamp(i + 1, new Timestamp(sdf.parse(param.toString()).getTime()));
            }
            else {
                if (null == param) {
                    ps.setString(i + 1, null);
                }
                else {
                    ps.setString(i + 1, param.toString());
                }
            }
        }
        return ps;
    }
}
