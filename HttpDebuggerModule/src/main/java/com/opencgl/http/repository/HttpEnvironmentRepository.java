package com.opencgl.http.repository;

import java.util.List;
import java.util.Map;

/**
 * HTTP 环境与变量持久化接口（SQLite）
 */
public interface HttpEnvironmentRepository {

    void initializeDatabase();

    /** 确保默认环境 "None" 存在 */
    void ensureDefaultEnv();

    List<String> findAllEnvNames();

    Map<String, String> getVariables(String envName);

    void saveEnvironment(String envName, Map<String, String> variables);

    void deleteEnvironment(String envName);
}
