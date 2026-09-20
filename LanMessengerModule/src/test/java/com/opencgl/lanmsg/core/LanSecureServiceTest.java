package com.opencgl.lanmsg.core;

import com.opencgl.lanmsg.security.SecureFixtures;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.Socket;
import java.net.*;
import java.util.concurrent.*;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LanSecureServiceTest {
    @TempDir Path root;
    @AfterEach void cleanup() throws Exception { SecureFixtures.closeAll(); }
    @Test void plaintextRequestCannotCreateReceivedHistory() throws Exception {
        var repo = new ChatRepository(root.resolve("history.db"));
        try (var service = SecureFixtures.service(repo,root.resolve("vault"),"receiver")) {
            service.start("127.0.0.1",0,0,false);
            String sender=UUID.randomUUID().toString(), message=UUID.randomUUID().toString();
            try (var raw=new Socket("127.0.0.1",service.port())) {
                raw.setSoTimeout(2000);
                var frame=Wire.frame("TEXT"); frame.put("device",sender);frame.put("name","冒充设备");frame.put("port",12345);
                frame.put("to",service.id());frame.put("id",message);frame.put("text","不能进入记录");
                Wire.write(raw.getOutputStream(),frame);
                assertThrows(java.io.IOException.class,()->Wire.read(raw.getInputStream()));
            }
            assertNull(repo.find(message)); assertTrue(repo.peers().isEmpty());
        }
    }
    @Test void authenticatedPeerCannotClaimAnotherDeviceIdInBusinessFrame() throws Exception {
        var repo=new ChatRepository(root.resolve("history.db"));
        try(var service=SecureFixtures.service(repo,root.resolve("vault"),"receiver");
            var remote=new SecureFixtures.Remote(root.resolve("remote"),UUID.randomUUID().toString())) {
            SecureFixtures.trust(service,remote);service.start("127.0.0.1",0,0,false);
            try(var socket=remote.connect(service)) {
                String message=UUID.randomUUID().toString();
                var frame=Wire.frame("TEXT");frame.put("device",UUID.randomUUID().toString());frame.put("name","伪造");frame.put("port",12345);
                frame.put("to",service.id());frame.put("id",message);frame.put("text","不能冒充");
                Wire.write(socket.getOutputStream(),frame);
                assertEquals("ERROR",Wire.read(socket.getInputStream()).getString("op"));
                assertNull(repo.find(message));assertTrue(repo.peers().isEmpty());
            }
        }
    }
    @Test void discoveryHintDoesNotPersistAsAuthenticatedContact() throws Exception {
        var repo=new ChatRepository(root.resolve("history.db"));
        try(var service=SecureFixtures.service(repo,root.resolve("vault"),"receiver");var reservation=new DatagramSocket(0);var udp=new DatagramSocket()) {
            int port=reservation.getLocalPort();reservation.close();service.start("127.0.0.1",port,0,false);
            var seen=new CompletableFuture<Peer>();service.onPeer(seen::complete);
            var frame=Wire.frame("ANNOUNCE");frame.put("device",UUID.randomUUID().toString());frame.put("name","不可信广播");frame.put("port",12345);
            byte[] bytes=com.alibaba.fastjson2.JSON.toJSONBytes(frame);
            udp.send(new DatagramPacket(bytes,bytes.length,InetAddress.getLoopbackAddress(),port));
            Peer hint=seen.get(2,TimeUnit.SECONDS);
            assertFalse(service.paired(hint.id()));assertTrue(repo.peers().isEmpty());
        }
    }
    @Test void revokingDuringPeerCallbackCannotInsertTheAlreadyReadBusinessFrame() throws Exception {
        var repo=new ChatRepository(root.resolve("history.db"));
        try(var service=SecureFixtures.service(repo,root.resolve("vault"),"receiver");var remote=new SecureFixtures.Remote(root.resolve("remote"),UUID.randomUUID().toString())) {
            SecureFixtures.trust(service,remote);service.start("127.0.0.1",0,0,false);
            var revoked=new CompletableFuture<Void>();
            service.onPeer(p->{try{service.revokeTrust(p.id());revoked.complete(null);}catch(Exception e){revoked.completeExceptionally(e);}});
            String message=UUID.randomUUID().toString();
            try(var socket=remote.connect(service)) {
                var frame=Wire.frame("TEXT");frame.put("device",remote.identity.deviceId());frame.put("name","remote");frame.put("port",12345);
                frame.put("to",service.id());frame.put("id",message);frame.put("text","撤销后不得保存");Wire.write(socket.getOutputStream(),frame);
                revoked.get(2,TimeUnit.SECONDS);
                try{socket.getInputStream().read();}catch(java.io.IOException ignored){}
                // A second local call takes the service monitor, after the current mutation if one occurred.
                service.revokeTrust(remote.identity.deviceId());
                assertNull(repo.find(message));assertFalse(service.paired(remote.identity.deviceId()));
            }
        }
    }
    @Test void knownPeerPresenceRecoversWithoutReplacingAuthenticatedMetadata() throws Exception {
        var repo=new ChatRepository(root.resolve("a.db"));
        try(var a=SecureFixtures.service(repo,root.resolve("a-vault"),"A");var b=SecureFixtures.service(new ChatRepository(root.resolve("b.db")),root.resolve("b-vault"),"B");var reserve=new DatagramSocket(0);var udp=new DatagramSocket()) {
            int port=reserve.getLocalPort();reserve.close();a.start("127.0.0.1",port,0,false);b.start("127.0.0.1",0,0,false);
            Peer verified=a.connectPeer("127.0.0.1",b.port());var events=new LinkedBlockingQueue<Peer>();a.onPeer(events::add);
            var sessionField=LanService.class.getDeclaredField("session");sessionField.setAccessible(true);Object session=sessionField.get(a);
            var seenField=session.getClass().getDeclaredField("seen");seenField.setAccessible(true);
            @SuppressWarnings("unchecked") var seen=(java.util.Map<String,Long>)seenField.get(session);
            seen.put(b.id(),System.nanoTime()-TimeUnit.SECONDS.toNanos(91));
            var expire=LanService.class.getDeclaredMethod("expire",session.getClass());expire.setAccessible(true);expire.invoke(a,session);
            assertFalse(events.poll(1,TimeUnit.SECONDS).online());
            var frame=Wire.frame("ANNOUNCE");frame.put("device",b.id());frame.put("name","不能覆盖名称");frame.put("port",b.port());
            byte[] bytes=com.alibaba.fastjson2.JSON.toJSONBytes(frame);udp.send(new DatagramPacket(bytes,bytes.length,InetAddress.getLoopbackAddress(),port));
            Peer present=events.poll(2,TimeUnit.SECONDS);assertNotNull(present);assertTrue(present.online());assertEquals(verified.name(),present.name());
            assertEquals(new Peer(verified.id(),verified.name(),verified.ip(),verified.port(),false),repo.peers().get(0));
        }
    }
}
