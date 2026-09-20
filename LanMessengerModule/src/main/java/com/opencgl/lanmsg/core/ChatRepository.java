package com.opencgl.lanmsg.core;

import com.alibaba.fastjson2.JSON;
import com.opencgl.lanmsg.security.LocalVault;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Small per-operation connections, serialized access; all callers run off the FX thread. */
public final class ChatRepository {
    private final String url;
    private final EncryptedChatStore encrypted;
    /** New isolated store. Never pass the host's shared legacy database here. */
    public ChatRepository(Path database, LocalVault vault) {
        url = null;
        encrypted = new EncryptedChatStore(database, vault);
    }
    /** Legacy plaintext storage, retained for migration until live activation is wired. */
    public ChatRepository(Path database) {
        encrypted = null;
        url = "jdbc:sqlite:" + database.toAbsolutePath();
        try {
            Files.createDirectories(database.toAbsolutePath().getParent());
            try (var c=open(); var s=c.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS LAN_SETTINGS (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS LAN_PEERS (id TEXT PRIMARY KEY,name TEXT NOT NULL,ip TEXT NOT NULL,port INTEGER NOT NULL,updated INTEGER NOT NULL)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS LAN_MESSAGES (seq INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT UNIQUE NOT NULL,peer TEXT NOT NULL,body TEXT NOT NULL,status TEXT NOT NULL,error TEXT NOT NULL,search TEXT NOT NULL)");
                s.executeUpdate("CREATE INDEX IF NOT EXISTS LAN_MESSAGES_PEER_SEQ ON LAN_MESSAGES(peer,seq)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS LAN_RECEIVED (id TEXT PRIMARY KEY, sender TEXT NOT NULL)");
            }
        } catch(Exception e) { throw failure(e); }
    }
    private Connection open() throws SQLException {
        var c=DriverManager.getConnection(url);
        try(var s=c.createStatement()) { s.execute("PRAGMA busy_timeout=3000"); }
        return c;
    }
    private IllegalStateException failure(Exception e) { return new IllegalStateException("LAN storage: " + e.getMessage(),e); }
    public synchronized String identity() {
        String id=setting("deviceId", "");
        if(id.isEmpty()) { id=UUID.randomUUID().toString(); settingSave("deviceId",id); }
        return id;
    }
    public synchronized String setting(String key,String fallback) {
        if(encrypted!=null) return encrypted.setting(key,fallback);
        try(var c=open(); var s=c.prepareStatement("SELECT value FROM LAN_SETTINGS WHERE key=?")) {
            s.setString(1,key); try(var r=s.executeQuery()) { return r.next()?r.getString(1):fallback; }
        } catch(SQLException e) { throw failure(e); }
    }
    public synchronized void settingSave(String key,String value) {
        if(encrypted!=null) { encrypted.settingSave(key,value); return; }
        try(var c=open(); var s=c.prepareStatement("INSERT INTO LAN_SETTINGS VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            s.setString(1,key); s.setString(2,value); s.executeUpdate();
        } catch(SQLException e) { throw failure(e); }
    }
    public synchronized void savePeer(Peer p) {
        if(encrypted!=null) { encrypted.savePeer(p); return; }
        try(var c=open(); var s=c.prepareStatement("INSERT INTO LAN_PEERS VALUES(?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,ip=excluded.ip,port=excluded.port,updated=excluded.updated")) {
            s.setString(1,p.id()); s.setString(2,p.name()); s.setString(3,p.ip()); s.setInt(4,p.port()); s.setLong(5,System.currentTimeMillis()); s.executeUpdate();
        } catch(SQLException e) { throw failure(e); }
    }
    public synchronized List<Peer> peers() {
        if(encrypted!=null) return encrypted.peers();
        try(var c=open(); var s=c.createStatement(); var r=s.executeQuery("SELECT * FROM LAN_PEERS ORDER BY updated DESC")) {
            var result=new ArrayList<Peer>(); while(r.next()) result.add(new Peer(r.getString("id"),r.getString("name"),r.getString("ip"),r.getInt("port"),false)); return result;
        } catch(SQLException e) { throw failure(e); }
    }
    public synchronized void save(ChatEntry entry) {
        if(encrypted!=null) { encrypted.save(entry); return; }
        try(var c=open()) { save(c,entry); } catch(SQLException e) { throw failure(e); }
    }
    private void save(Connection c,ChatEntry e) throws SQLException {
        try(var s=c.prepareStatement("INSERT INTO LAN_MESSAGES(id,peer,body,status,error,search) VALUES(?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET body=excluded.body,status=excluded.status,error=excluded.error,search=excluded.search")) {
            s.setString(1,e.id); s.setString(2,e.peerId); s.setString(3,JSON.toJSONString(e));
            s.setString(4,e.status); s.setString(5,e.error); s.setString(6,e.text+"\n"+e.fileName); s.executeUpdate();
        }
    }
    public synchronized boolean insertIncoming(ChatEntry e) {
        if(encrypted!=null) return encrypted.insertIncoming(e);
        try(var c=open()) {
            c.setAutoCommit(false);
            try {
                try(var q=c.prepareStatement("SELECT sender FROM LAN_RECEIVED WHERE id=?")) {
                    q.setString(1,e.id); try(var r=q.executeQuery()) {
                        if(r.next()) { if(!r.getString(1).equals(e.senderId)) throw new SQLException("Message ID belongs to another sender"); c.rollback(); return false; }
                    }
                }
                try(var q=c.prepareStatement("SELECT id FROM LAN_MESSAGES WHERE id=?")) {
                    q.setString(1,e.id); try(var r=q.executeQuery()) { if(r.next()) throw new SQLException("Message ID collision"); }
                }
                try(var s=c.prepareStatement("INSERT INTO LAN_RECEIVED VALUES(?,?)")) { s.setString(1,e.id); s.setString(2,e.senderId); s.executeUpdate(); }
                save(c,e); c.commit(); return true;
            } catch(Exception failure) { c.rollback(); throw failure; }
        } catch(SQLException ex) { throw failure(ex); }
    }
    private ChatEntry entry(ResultSet r) throws SQLException {
        var e=JSON.parseObject(r.getString("body"),ChatEntry.class); e.sequence=r.getLong("seq"); e.status=r.getString("status"); e.error=r.getString("error"); return e;
    }
    public synchronized ChatEntry find(String id) {
        if(encrypted!=null) return encrypted.find(id);
        try(var c=open(); var s=c.prepareStatement("SELECT * FROM LAN_MESSAGES WHERE id=?")) {
            s.setString(1,id); try(var r=s.executeQuery()) { return r.next()?entry(r):null; }
        } catch(SQLException e) { throw failure(e); }
    }
    public synchronized List<ChatEntry> history(String peer,long before,String query,int limit) {
        if(encrypted!=null) return encrypted.history(peer,before,query,limit);
        try(var c=open(); var s=c.prepareStatement("SELECT * FROM LAN_MESSAGES WHERE peer=? AND seq<? AND search LIKE ? ESCAPE '\\' ORDER BY seq DESC LIMIT ?")) {
            s.setString(1,peer); s.setLong(2,before);
            s.setString(3,"%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"); s.setInt(4,Math.max(1,Math.min(100,limit)));
            var result=new ArrayList<ChatEntry>(); try(var r=s.executeQuery()) { while(r.next()) result.add(entry(r)); }
            Collections.reverse(result); return result;
        } catch(SQLException e) { throw failure(e); }
    }
    public synchronized void clear(String peer) {
        if(encrypted!=null) { encrypted.clear(peer); return; }
        try(var c=open(); var s=c.prepareStatement("DELETE FROM LAN_MESSAGES WHERE peer=?")) { s.setString(1,peer); s.executeUpdate(); }
        catch(SQLException e) { throw failure(e); }
        // Keep deduplication receipts: retries must not resurrect a cleared conversation.
    }
    public synchronized void markInterrupted() {
        if(encrypted!=null) { encrypted.markInterrupted(); return; }
        try(var c=open(); var s=c.createStatement()) {
            s.executeUpdate("UPDATE LAN_MESSAGES SET status='INTERRUPTED',error='Service stopped before completion' WHERE status IN ('SENDING','QUEUED','TRANSFERRING','OFFERED')");
        } catch(SQLException e) { throw failure(e); }
    }
}
