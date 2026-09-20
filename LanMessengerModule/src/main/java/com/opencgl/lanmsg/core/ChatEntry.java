package com.opencgl.lanmsg.core;

import com.alibaba.fastjson2.JSON;
import java.util.UUID;

/** Persisted snapshot. Service updates never mutate a snapshot already handed to JavaFX. */
public final class ChatEntry {
    public String id = UUID.randomUUID().toString();
    public String peerId = "", senderId = "", senderName = "", text = "";
    public String kind = "TEXT", status = "SENDING", error = "";
    public String fileName = "", path = "", sha256 = "", token = "";
    public long time = System.currentTimeMillis(), size, sequence, expires;
    public boolean outgoing;
    public ChatEntry copy() { return JSON.parseObject(JSON.toJSONString(this), ChatEntry.class); }
    public boolean attachment() { return !"TEXT".equals(kind); }
}
