package com.opencgl.lanmsg.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    @TempDir Path directory;
    @Test void framedTextRoundTripsNewlinesUnicodeAndPipes() throws Exception {
        var frame = Wire.frame("TEXT"); frame.put("text", "你好|world\nsecond line");
        var out = new ByteArrayOutputStream(); Wire.write(out, frame);
        assertEquals("你好|world\nsecond line", Wire.read(new ByteArrayInputStream(out.toByteArray())).getString("text"));
    }
    @Test void rejectsBadVersionAndOversizedLengthBeforeReadingPayload() throws Exception {
        var out = new ByteArrayOutputStream(); new DataOutputStream(out).writeInt(Integer.MAX_VALUE);
        assertThrows(IOException.class, () -> Wire.read(new ByteArrayInputStream(out.toByteArray())));
        var frame = Wire.frame("HELLO"); frame.put("v", 1);
        assertThrows(IOException.class, () -> Wire.write(new ByteArrayOutputStream(), frame));
        var oversized = Wire.frame("TEXT"); oversized.put("text", "a".repeat(1024 * 1024 + 1));
        assertThrows(IOException.class, () -> Wire.write(new ByteArrayOutputStream(), oversized));
    }
    @Test void identityHistoryAndPeersSurviveReopenAndDuplicatesDoNotOverwrite() {
        var file = directory.resolve("nested/lan.db");
        var repository = new ChatRepository(file); String identity = repository.identity();
        repository.savePeer(new Peer("device-b", "B", "127.0.0.1", 2456, true));
        var first = new ChatEntry(); first.peerId = "device-b"; first.senderId = "device-b";
        first.text = "你好\n100%_done"; first.status = "DELIVERED";
        assertTrue(repository.insertIncoming(first));
        first.text = "duplicate should not replace";
        assertFalse(repository.insertIncoming(first));
        var restarted = new ChatRepository(file);
        assertEquals(identity, restarted.identity());
        assertEquals("你好\n100%_done", restarted.history("device-b", Long.MAX_VALUE, "%_", 50).get(0).text);
        assertEquals(1, restarted.peers().size());
        assertFalse(restarted.peers().get(0).online());
        restarted.clear("other"); assertEquals(1, restarted.history("device-b", Long.MAX_VALUE, "", 50).size());
        restarted.clear("device-b"); assertTrue(restarted.history("device-b", Long.MAX_VALUE, "", 50).isEmpty());
    }
    @Test void paginationHasNoGapsForEqualTimestampsAndInterruptedSendsCanRecover() {
        var repo = new ChatRepository(directory.resolve("lan.db"));
        for (int i=0;i<3;i++) { var e=new ChatEntry(); e.peerId="B"; e.senderId="A"; e.outgoing=true; e.time=1; e.text="m"+i; repo.save(e); }
        var last = repo.history("B", Long.MAX_VALUE, "", 2);
        assertEquals(java.util.List.of("m1", "m2"), last.stream().map(e -> e.text).toList());
        assertEquals("m0", repo.history("B", last.get(0).sequence, "", 2).get(0).text);
        repo.markInterrupted();
        assertEquals("INTERRUPTED", repo.history("B", Long.MAX_VALUE, "", 1).get(0).status);
    }
}
