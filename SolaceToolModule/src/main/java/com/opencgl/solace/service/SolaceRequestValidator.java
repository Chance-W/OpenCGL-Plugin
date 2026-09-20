package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceOutboundMessage;

import java.util.ArrayList;
import java.util.List;

/** Required-field validation shared by sender and listener UI flows. */
public final class SolaceRequestValidator {
    private SolaceRequestValidator() { }

    public static List<String> validateConnection(SolaceConnectionConfig connection) {
        List<String> missing = new ArrayList<>();
        if (connection == null) {
            missing.add("连接配置");
            return missing;
        }
        required(missing, connection.getHost(), "连接地址（Host）");
        required(missing, connection.getUsername(), "用户名");
        required(missing, connection.getPassword(), "密码");
        if (connection.isTlsEnabled() && connection.isValidateCertificate()) {
            required(missing, connection.getTrustStorePath(), "Trust Store");
        }
        return missing;
    }

    public static List<String> validateSend(SolaceConnectionConfig connection, SolaceOutboundMessage message) {
        List<String> missing = new ArrayList<>(validateConnection(connection));
        if (message == null) {
            missing.add("发送报文");
            return missing;
        }
        required(missing, message.destinationName(), "Topic / Queue");
        required(missing, message.body(), "消息正文");
        return missing;
    }

    public static List<String> validateListener(SolaceConnectionConfig connection, SolaceListenerConfig listener) {
        List<String> missing = new ArrayList<>(validateConnection(connection));
        if (listener == null) missing.add("监听配置");
        else required(missing, listener.destinationName(), "Topic / Queue");
        return missing;
    }

    private static void required(List<String> missing, String value, String field) {
        if (value == null || value.isBlank()) missing.add(field);
    }
}
