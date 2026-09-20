package com.opencgl.lanmsg.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Length-prefixed UTF-8 JSON. No polymorphic or Java object deserialization. */
public final class Wire {
    public static final int CONTROL_LIMIT = 64 * 1024;
    public static final int TEXT_LIMIT = 1024 * 1024;
    private static final int FRAME_LIMIT = TEXT_LIMIT * 6 + CONTROL_LIMIT; // escaped JSON characters
    private Wire() {}
    public static JSONObject frame(String op) { var o = new JSONObject(); o.put("v", 2); o.put("op", op); return o; }
    public static void write(OutputStream output, JSONObject frame) throws IOException {
        byte[] bytes = JSON.toJSONBytes(frame); validate(frame, bytes.length);
        var out = new DataOutputStream(output); out.writeInt(bytes.length); out.write(bytes); out.flush();
    }
    public static JSONObject read(InputStream input) throws IOException {
        var in = new DataInputStream(input); int size = in.readInt();
        if (size <= 0 || size > FRAME_LIMIT) throw new IOException("Invalid frame length");
        byte[] bytes = new byte[size]; in.readFully(bytes);
        try { var frame = JSON.parseObject(bytes); validate(frame, size); return frame; }
        catch (RuntimeException e) { throw new IOException("Invalid protocol frame", e); }
    }
    private static void validate(JSONObject frame, int bytes) throws IOException {
        if (frame == null || frame.getIntValue("v") != 2 || frame.getString("op") == null)
            throw new IOException("Unsupported LAN protocol (v2 required)");
        if (bytes > FRAME_LIMIT || (!"TEXT".equals(frame.getString("op")) && bytes > CONTROL_LIMIT))
            throw new IOException("Frame too large");
        if ("TEXT".equals(frame.getString("op"))) {
            String text = frame.getString("text");
            if (text == null || text.getBytes(StandardCharsets.UTF_8).length > TEXT_LIMIT) throw new IOException("Text exceeds 1 MiB");
        }
    }
    public static String required(JSONObject frame, String key, int max) throws IOException {
        String value = frame.getString(key);
        if (value == null || value.isBlank() || value.length() > max) throw new IOException("Invalid " + key);
        return value;
    }
}
