package com.opencgl.lanmsg.core;

import com.alibaba.fastjson2.JSON;
import com.opencgl.lanmsg.security.LocalVault;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Ciphertext-only database. Callers serialize access and run off the JavaFX thread. */
final class EncryptedChatStore {
    private static final int BATCH = 100;
    private final Path database;
    private final String url, purpose;
    private final LocalVault vault;

    EncryptedChatStore(Path path, LocalVault vault) {
        this.vault=Objects.requireNonNull(vault);
        database=path.toAbsolutePath().normalize(); url="jdbc:sqlite:"+database;
        try {
            purpose="chat-db-v1/"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(database.toString().getBytes(StandardCharsets.UTF_8)));
            Files.createDirectories(database.getParent());
            // CREATE_NEW prevents another creator or a symlink from masquerading as a fresh store.
            boolean fresh;
            try { Files.createFile(database); fresh=true; }
            catch(FileAlreadyExistsException e) { fresh=false; }
            checkFile();
            try(var c=connect()) {
                if(fresh) {
                    transaction(c,()-> {
                        try(var s=c.createStatement()) {
                            s.executeUpdate("CREATE TABLE meta(id TEXT PRIMARY KEY,body BLOB NOT NULL)");
                            s.executeUpdate("CREATE TABLE settings(key TEXT PRIMARY KEY,body BLOB NOT NULL)");
                            s.executeUpdate("CREATE TABLE peers(id TEXT PRIMARY KEY,updated INTEGER NOT NULL,body BLOB NOT NULL)");
                            s.executeUpdate("CREATE TABLE messages(seq INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT UNIQUE NOT NULL,peer TEXT NOT NULL,body BLOB NOT NULL)");
                            s.executeUpdate("CREATE INDEX messages_peer_seq ON messages(peer,seq)");
                            s.executeUpdate("CREATE TABLE received(id TEXT PRIMARY KEY,body BLOB NOT NULL)");
                        }
                        put(c,"INSERT INTO meta VALUES('schema',?)",seal("schema","OpenCGL encrypted chat v1"));
                        return null;
                    });
                }
                verify(c);
            }
        } catch(Exception e) { throw failure(e); }
    }

    private void checkFile() throws IOException {
        if(!Files.isRegularFile(database,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Encrypted history database is missing or is not a regular file");
    }
    private Connection connect() throws SQLException {
        var c=DriverManager.getConnection(url);
        try(var s=c.createStatement()) {
            s.execute("PRAGMA busy_timeout=3000"); s.execute("PRAGMA temp_store=MEMORY");
        } catch(SQLException e) { c.close(); throw e; }
        return c;
    }
    private Connection open() throws Exception {
        checkFile(); var c=connect();
        try { verify(c); return c; } catch(Exception e) { c.close(); throw e; }
    }
    private void verify(Connection c) throws Exception {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT body FROM meta WHERE id='schema'")) {
            if(!r.next() || !"OpenCGL encrypted chat v1".equals(decode("schema",r.getBytes(1),String.class)))
                throw new IOException("Encrypted history schema authentication failed");
        }
    }
    private static String context(Object... parts) { return JSON.toJSONString(parts); }
    private byte[] seal(String id,Object value) throws IOException {
        byte[] plain=JSON.toJSONBytes(value);
        try { return vault.sealRecord(purpose,id,plain); } finally { Arrays.fill(plain,(byte)0); }
    }
    private <T> T decode(String id,byte[] body,Class<T> type) throws IOException {
        byte[] plain=vault.openRecord(purpose,id,body);
        try { return Objects.requireNonNull(JSON.parseObject(plain,type),"Invalid encrypted record"); }
        finally { Arrays.fill(plain,(byte)0); }
    }
    private static IllegalStateException failure(Exception e) {
        if(e instanceof CancellationException cancelled) throw cancelled;
        return new IllegalStateException("Encrypted LAN storage operation failed",e);
    }
    private static void cancelled() {
        if(Thread.currentThread().isInterrupted()) throw new CancellationException("History operation cancelled");
    }
    @FunctionalInterface private interface Operation<T> { T run() throws Exception; }
    private static <T> T transaction(Connection c,Operation<T> operation) throws Exception {
        c.setAutoCommit(false);
        try { T value=operation.run(); c.commit(); return value; }
        catch(Exception | Error e) { try { c.rollback(); } catch(SQLException rollback) { e.addSuppressed(rollback); } throw e; }
    }
    private static void put(Connection c,String sql,Object... args) throws SQLException {
        try(var s=c.prepareStatement(sql)) { bind(s,args); s.executeUpdate(); }
    }
    private static void bind(PreparedStatement s,Object... args) throws SQLException {
        for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
    }
    String setting(String key,String fallback) {
        try(var c=open();var s=c.prepareStatement("SELECT body FROM settings WHERE key=?")) {
            s.setString(1,key); try(var r=s.executeQuery()) { return r.next()?decode(context("setting",key),r.getBytes(1),String.class):fallback; }
        } catch(Exception e) { throw failure(e); }
    }
    void settingSave(String key,String value) {
        try(var c=open()) { put(c,"INSERT INTO settings VALUES(?,?) ON CONFLICT(key) DO UPDATE SET body=excluded.body",key,seal(context("setting",key),value)); }
        catch(Exception e) { throw failure(e); }
    }
    void savePeer(Peer peer) {
        long updated=System.currentTimeMillis();
        try(var c=open()) { put(c,"INSERT INTO peers VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET updated=excluded.updated,body=excluded.body",
                peer.id(),updated,seal(context("peer",peer.id(),updated),new Peer(peer.id(),peer.name(),peer.ip(),peer.port(),false))); }
        catch(Exception e) { throw failure(e); }
    }
    List<Peer> peers() {
        try(var c=open();var s=c.createStatement();var r=s.executeQuery("SELECT * FROM peers ORDER BY updated DESC")) {
            var result=new ArrayList<Peer>();
            while(r.next()) { cancelled(); result.add(decode(context("peer",r.getString("id"),r.getLong("updated")),r.getBytes("body"),Peer.class)); }
            return result;
        } catch(Exception e) { throw failure(e); }
    }
    private ChatEntry entry(ResultSet r) throws Exception {
        long seq=r.getLong("seq"); String id=r.getString("id"),peer=r.getString("peer");
        var e=decode(context("message",id,peer,seq),r.getBytes("body"),ChatEntry.class);
        if(!id.equals(e.id)||!peer.equals(e.peerId)) throw new IOException("Encrypted message identity mismatch");
        e.sequence=seq; return e;
    }
    private ChatEntry find(Connection c,String id) throws Exception {
        try(var s=c.prepareStatement("SELECT * FROM messages WHERE id=?")) {
            s.setString(1,id);try(var r=s.executeQuery()) { return r.next()?entry(r):null; }
        }
    }
    ChatEntry find(String id) {
        try(var c=open()) { return find(c,id); } catch(Exception e) { throw failure(e); }
    }
    private void save(Connection c,ChatEntry e) throws Exception {
        var old=find(c,e.id);
        long seq;
        if(old!=null) {
            if(!old.peerId.equals(e.peerId)||!old.senderId.equals(e.senderId)) throw new IOException("Message identity cannot change");
            seq=old.sequence;
        } else {
            // Placeholder is private to this transaction; no plaintext content ever reaches SQLite.
            put(c,"INSERT INTO messages(id,peer,body) VALUES(?,?,?)",e.id,e.peerId,new byte[0]);
            try(var s=c.createStatement();var r=s.executeQuery("SELECT last_insert_rowid()")) { r.next();seq=r.getLong(1); }
        }
        put(c,"UPDATE messages SET body=? WHERE id=?",seal(context("message",e.id,e.peerId,seq),e),e.id);
    }
    void save(ChatEntry e) {
        try(var c=open()) { transaction(c,()->{save(c,e);return null;}); } catch(Exception ex) { throw failure(ex); }
    }
    boolean insertIncoming(ChatEntry e) {
        try(var c=open()) {
            return transaction(c,()-> {
                try(var s=c.prepareStatement("SELECT body FROM received WHERE id=?")) {
                    s.setString(1,e.id);try(var r=s.executeQuery()) {
                        if(r.next()) {
                            if(!decode(context("receipt",e.id),r.getBytes(1),String.class).equals(e.senderId))
                                throw new IOException("Message ID belongs to another sender");
                            return false;
                        }
                    }
                }
                if(find(c,e.id)!=null) throw new IOException("Message ID collision");
                put(c,"INSERT INTO received VALUES(?,?)",e.id,seal(context("receipt",e.id),e.senderId));
                save(c,e); return true;
            });
        } catch(Exception ex) { throw failure(ex); }
    }
    List<ChatEntry> history(String peer,long before,String query,int limit) {
        cancelled(); int count=Math.max(1,Math.min(100,limit)); String needle=asciiLower(query);
        try(var c=open()) {
            var result=new ArrayList<ChatEntry>();long cursor=before;
            while(result.size()<count) {
                cancelled(); int scanned=0;
                try(var s=c.prepareStatement("SELECT * FROM messages WHERE peer=? AND seq<? ORDER BY seq DESC LIMIT ?")) {
                    bind(s,peer,cursor,BATCH);try(var r=s.executeQuery()) {
                        while(r.next()) {
                            cancelled();var e=entry(r);cursor=e.sequence;scanned++;
                            if(asciiLower(e.text+"\n"+e.fileName).contains(needle)) result.add(e);
                            if(result.size()==count) break;
                        }
                    }
                }
                if(scanned<BATCH) break;
            }
            Collections.reverse(result);return result;
        } catch(Exception e) { throw failure(e); }
    }
    // SQLite LIKE's default ASCII-only case folding, without turning %/_ into wildcards.
    private static String asciiLower(String value) {
        var chars=value.toCharArray();for(int i=0;i<chars.length;i++) if(chars[i]>='A'&&chars[i]<='Z') chars[i]+=32;
        return new String(chars);
    }
    void clear(String peer) {
        try(var c=open()) { put(c,"DELETE FROM messages WHERE peer=?",peer); } catch(Exception e) { throw failure(e); }
    }
    void markInterrupted() {
        try(var c=open()) { transaction(c,()-> {
            long cursor=0;
            while(true) {
                cancelled();var batch=new ArrayList<ChatEntry>();
                try(var s=c.prepareStatement("SELECT * FROM messages WHERE seq>? ORDER BY seq LIMIT ?")) {
                    bind(s,cursor,BATCH);try(var r=s.executeQuery()) { while(r.next()) {cancelled();batch.add(entry(r));} }
                }
                for(var e:batch) {
                    cursor=e.sequence;
                    if(Set.of("SENDING","QUEUED","TRANSFERRING","OFFERED").contains(e.status)) {
                        e.status="INTERRUPTED";e.error="Service stopped before completion";save(c,e);
                    }
                }
                if(batch.size()<BATCH) return null;
            }
        }); } catch(Exception e) { throw failure(e); }
    }
}
