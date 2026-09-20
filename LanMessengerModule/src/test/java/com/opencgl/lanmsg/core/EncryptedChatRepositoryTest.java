package com.opencgl.lanmsg.core;

import com.opencgl.lanmsg.security.LocalVault;
import com.opencgl.lanmsg.security.SecretStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class EncryptedChatRepositoryTest {
    @TempDir Path directory;
    private final SecretStore keys = new SecretStore() {
        byte[] key;
        public Optional<byte[]> read() { return Optional.ofNullable(key == null ? null : key.clone()); }
        public void create(byte[] value) { key = value.clone(); }
    };
    private LocalVault vault() throws Exception { return LocalVault.open(directory.resolve("vault"), keys); }
    private Path database() { return directory.resolve("history.db"); }
    private Connection sql() throws Exception { return DriverManager.getConnection("jdbc:sqlite:" + database()); }
    private ChatEntry entry(String text) {
        var e = new ChatEntry(); e.peerId="peer-b"; e.senderId="peer-b"; e.text=text;
        e.status="DELIVERED"; e.time=1; return e;
    }

    @Test void sensitiveValuesSurviveReopenButNeverAppearInDatabaseFiles() throws Exception {
        String identity, id;
        try (var vault=vault()) {
            var repo=new ChatRepository(database(),vault);
            identity=repo.identity(); repo.settingSave("name","本机秘密名称");
            repo.savePeer(new Peer("peer-b","对端秘密名称","192.0.2.87",2426,true));
            var e=entry("机密中文📎\n第二行"); e.fileName="机密附件名.txt";
            e.path="/私人路径/文件.txt"; e.error="机密错误详情"; id=e.id; repo.save(e);
        }
        try (var vault=vault()) {
            var repo=new ChatRepository(database(),vault);
            assertEquals(identity,repo.identity());
            assertEquals("本机秘密名称",repo.setting("name",""));
            assertEquals(new Peer("peer-b","对端秘密名称","192.0.2.87",2426,false),repo.peers().getFirst());
            var e=repo.find(id); assertEquals("机密中文📎\n第二行",e.text);
            assertEquals("机密附件名.txt",e.fileName); assertEquals("/私人路径/文件.txt",e.path);
            assertEquals("机密错误详情",e.error);
        }
        try (var files=Files.walk(directory)) {
            for (Path file:files.filter(Files::isRegularFile).toList()) {
                String bytes=new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
                for (String secret:List.of("本机秘密名称","对端秘密名称","192.0.2.87","机密中文","机密附件名","私人路径","机密错误详情"))
                    assertFalse(bytes.contains(secret),file+" exposed "+secret);
            }
        }
    }

    @Test void sparseSearchScansPastNonmatchingBatchesAndPagesWithoutGaps() throws Exception {
        try (var vault=vault()) {
            var repo=new ChatRepository(database(),vault);
            repo.save(entry("更早中文ABC%_")); repo.save(entry("较早中文abc%_"));
            for (int i=0;i<205;i++) repo.save(entry("unmatched "+i));
            repo.save(entry("最新中文AbC%_"));
            var last=repo.history("peer-b",Long.MAX_VALUE,"中文abc%_",2);
            assertEquals(List.of("较早中文abc%_","最新中文AbC%_"),last.stream().map(e->e.text).toList());
            assertEquals("更早中文ABC%_",repo.history("peer-b",last.getFirst().sequence,"中文abc%_",2).getFirst().text);
            assertTrue(repo.history("other",Long.MAX_VALUE,"",50).isEmpty());
            Thread.currentThread().interrupt();
            try { assertThrows(CancellationException.class,()->repo.history("peer-b",Long.MAX_VALUE,"",50)); }
            finally { assertTrue(Thread.interrupted()); }
        }
    }

    @Test void deduplicationSurvivesClearAndSenderCollisionCannotOverwrite() throws Exception {
        try (var vault=vault()) {
            var repo=new ChatRepository(database(),vault); var e=entry("原始消息");
            assertTrue(repo.insertIncoming(e)); e.text="恶意替换";
            assertFalse(repo.insertIncoming(e)); assertEquals("原始消息",repo.find(e.id).text);
            e.senderId="other"; assertThrows(IllegalStateException.class,()->repo.insertIncoming(e));
            repo.clear("peer-b"); assertNull(repo.find(e.id));
            e.senderId="peer-b"; assertFalse(repo.insertIncoming(e));
            assertTrue(repo.history("peer-b",Long.MAX_VALUE,"",50).isEmpty());
        }
    }

    @Test void tamperedCiphertextOrMetadataIsNotReturnedAsEmptyHistory() throws Exception {
        try (var vault=vault()) {
            var repo=new ChatRepository(database(),vault); var a=entry("消息一"); var b=entry("消息二");
            repo.save(a); repo.save(b);
            try(var c=sql();var s=c.prepareStatement("UPDATE messages SET body=(SELECT body FROM messages WHERE id=?) WHERE id=?")) {
                s.setString(1,a.id);s.setString(2,b.id);s.executeUpdate();
            }
            assertThrows(IllegalStateException.class,()->repo.find(b.id));
            assertEquals("消息一",repo.find(a.id).text);
            try(var c=sql();var s=c.prepareStatement("UPDATE messages SET peer='other' WHERE id=?")) {
                s.setString(1,a.id);s.executeUpdate();
            }
            assertThrows(IllegalStateException.class,()->repo.history("other",Long.MAX_VALUE,"",50));
        }
    }

    @Test void interruptionReencryptsAndRollsBackIfAnyRecordCannotBeAuthenticated() throws Exception {
        try(var vault=vault()) {
            var repo=new ChatRepository(database(),vault); var a=entry("待发送"); var b=entry("已送达");
            a.status="SENDING";repo.save(a);
            for(int i=0;i<99;i++) repo.save(entry("已完成 "+i));
            repo.save(b);repo.markInterrupted();
            assertEquals("INTERRUPTED",repo.find(a.id).status);assertEquals("DELIVERED",repo.find(b.id).status);
            a.status="QUEUED";repo.save(a);
            byte[] original;
            try(var c=sql();var s=c.prepareStatement("SELECT body FROM messages WHERE id=?")) {
                s.setString(1,a.id);try(var r=s.executeQuery()) {assertTrue(r.next());original=r.getBytes(1);}
            }
            try(var c=sql();var s=c.prepareStatement("UPDATE messages SET body=x'00' WHERE id=?")) {s.setString(1,b.id);s.executeUpdate();}
            assertThrows(IllegalStateException.class,repo::markInterrupted);
            assertEquals("QUEUED",repo.find(a.id).status);
            try(var c=sql();var s=c.prepareStatement("SELECT body FROM messages WHERE id=?")) {
                s.setString(1,a.id);try(var r=s.executeQuery()) {assertTrue(r.next());assertArrayEquals(original,r.getBytes(1));}
            }
        }
    }

    @Test void failedIncomingSaveRollsBackReceiptSoRetryCanSucceed() throws Exception {
        try(var vault=vault()) {
            var repo=new ChatRepository(database(),vault);var e=entry("x".repeat(16*1024*1024));
            assertThrows(IllegalStateException.class,()->repo.insertIncoming(e));
            assertNull(repo.find(e.id));e.text="重试成功";
            assertTrue(repo.insertIncoming(e));assertEquals("重试成功",repo.find(e.id).text);
        }
    }

    @Test void missingSchemaMarkerCannotSilentlyInitializeOrClearExistingHistory() throws Exception {
        try(var vault=vault()) {
            var repo=new ChatRepository(database(),vault);repo.save(entry("应保留"));
            try(var c=sql();var s=c.createStatement()) {s.executeUpdate("DELETE FROM meta");}
            assertThrows(IllegalStateException.class,()->repo.clear("peer-b"));
            assertThrows(IllegalStateException.class,()->new ChatRepository(database(),vault));
            try(var c=sql();var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM messages")) {
                assertTrue(r.next());assertEquals(1,r.getInt(1));
            }
        }
    }

    @Test void statusUpdatesPreserveOrderingAndRejectMessageIdentityChanges() throws Exception {
        try(var vault=vault()) {
            var repo=new ChatRepository(database(),vault);var a=entry("第一条");
            repo.save(a);repo.save(entry("第二条"));long sequence=repo.find(a.id).sequence;
            a.status="FAILED";a.error="连接失败";repo.save(a);
            assertEquals(sequence,repo.find(a.id).sequence);
            assertEquals(List.of("第一条","第二条"),repo.history("peer-b",Long.MAX_VALUE,"",10).stream().map(e->e.text).toList());
            a.peerId="other";assertThrows(IllegalStateException.class,()->repo.save(a));
            assertEquals("peer-b",repo.find(a.id).peerId);
        }
    }

    @Test void wrongVaultAndClosedVaultCannotReadOrOverwriteRecords() throws Exception {
        String id;
        try(var vault=vault()) {var repo=new ChatRepository(database(),vault);var e=entry("保留原文");id=e.id;repo.save(e);}
        SecretStore wrong=new SecretStore() {
            public Optional<byte[]> read(){return Optional.of(new byte[32]);}
            public void create(byte[] value){throw new AssertionError();}
        };
        try(var other=LocalVault.open(directory.resolve("other-vault"),wrong)) {
            assertThrows(IllegalStateException.class,()->new ChatRepository(database(),other));
        }
        var vault=vault();var repo=new ChatRepository(database(),vault);vault.close();
        assertThrows(IllegalStateException.class,()->repo.save(entry("不能明文写入")));
        try(var reopened=vault()) {assertEquals("保留原文",new ChatRepository(database(),reopened).find(id).text);}
    }
}
