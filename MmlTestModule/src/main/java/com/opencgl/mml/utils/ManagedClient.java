package com.opencgl.mml.utils;

/**
 * @author Chance.W
 * @Description TODO
 * @date 2020/2/7-9:50
 */
import java.util.Properties;

public interface ManagedClient {
    void init(Properties var1) throws Exception;

    void start() throws Exception;

    void stop() throws Exception;

    void destory() throws Exception;

    String getType();
}

