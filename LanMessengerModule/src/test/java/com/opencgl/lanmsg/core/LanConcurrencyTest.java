package com.opencgl.lanmsg.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import com.opencgl.lanmsg.security.SecureFixtures;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LanConcurrencyTest {
    @TempDir Path dir;
    @AfterEach void cleanup() throws Exception {SecureFixtures.closeAll();}
    LanService service(ChatRepository repo,String name) throws Exception {return SecureFixtures.service(repo,dir.resolve(name+"-vault"),name);}
    @Test void lateOfferAckCannotReplaceCompletedTransferState() throws Exception {
        Path file=dir.resolve("payload.txt");Files.writeString(file,"payload");
        var repo=new ChatRepository(dir.resolve("sender.db"));
        try(var service=service(repo,"sender");var remoteDevice=new SecureFixtures.Remote(dir.resolve("remote-vault"),UUID.randomUUID().toString());var server=new ServerSocket(0,10,InetAddress.getLoopbackAddress());var executor=Executors.newSingleThreadExecutor()) {
            service.start("127.0.0.1",0,0,false);
            SecureFixtures.trust(service,remoteDevice);
            String device=remoteDevice.identity.deviceId();var peer=new Peer(device,"receiver","127.0.0.1",server.getLocalPort(),true);repo.savePeer(peer);
            var remote=executor.submit(()->{
                try(var offerSocket=remoteDevice.accept(server)) {
                    offerSocket.setSoTimeout(5000);var offer=Wire.read(offerSocket.getInputStream());
                    try(var get=remoteDevice.connect(service)) {
                        get.setSoTimeout(5000);var request=Wire.frame("GET");request.put("device",device);request.put("name","receiver");request.put("port",server.getLocalPort());request.put("to",service.id());request.put("id",offer.getString("id"));request.put("token",offer.getString("token"));
                        Wire.write(get.getOutputStream(),request);assertEquals("READY",Wire.read(get.getInputStream()).getString("op"));
                        assertEquals("payload",new String(get.getInputStream().readNBytes(7),java.nio.charset.StandardCharsets.UTF_8));assertEquals("END",Wire.read(get.getInputStream()).getString("op"));
                        var ack=Wire.frame("FILE_ACK");ack.put("id",offer.getString("id"));Wire.write(get.getOutputStream(),ack);
                        assertEquals(-1,get.getInputStream().read()); // sender committed COMPLETED before closing
                    }
                    var ack=Wire.frame("ACK");ack.put("device",device);ack.put("id",offer.getString("id"));Wire.write(offerSocket.getOutputStream(),ack);return null;
                }
            });
            var sent=service.offer(peer,file);remote.get(10,TimeUnit.SECONDS);
            assertEquals("COMPLETED",sent.status);
        }
    }
    @Test void stoppingSettlesQueuedFuturesAndDoesNotCommitPartialFiles() throws Exception {
        try(var service=service(new ChatRepository(dir.resolve("receiver.db")),"receiver");var remoteDevice=new SecureFixtures.Remote(dir.resolve("remote-vault"),UUID.randomUUID().toString());var server=new ServerSocket(0,10,InetAddress.getLoopbackAddress());var executor=Executors.newSingleThreadExecutor()) {
            SecureFixtures.trust(service,remoteDevice);
            service.start("127.0.0.1",0,0,false);String sender=remoteDevice.identity.deviceId();service.repository().savePeer(new Peer(sender,"sender","127.0.0.1",server.getLocalPort(),true));
            var held=new CopyOnWriteArrayList<Socket>();var connected=new CountDownLatch(2);
            var accepted=executor.submit(()->{for(int i=0;i<2;i++){Socket socket=remoteDevice.accept(server);held.add(socket);Wire.read(socket.getInputStream());connected.countDown();}return null;});
            var futures=new ArrayList<CompletableFuture<Void>>();
            try {
                for(int i=0;i<3;i++) {
                    var e=new ChatEntry();e.peerId=sender;e.senderId=sender;e.kind="FILE";e.fileName="payload";e.token="token";e.sha256="0".repeat(64);e.size=100;e.status="OFFERED";e.expires=System.currentTimeMillis()+60000;service.repository().insertIncoming(e);
                    futures.add(service.accept(e,dir.resolve("target-"+i),false));
                }
                assertTrue(connected.await(5,TimeUnit.SECONDS));accepted.get(5,TimeUnit.SECONDS);service.stop();
                for(var future:futures){assertThrows(ExecutionException.class,()->future.get(2,TimeUnit.SECONDS));}
                for(int i=0;i<3;i++)assertFalse(Files.exists(dir.resolve("target-"+i)));
            }finally{service.stop();for(var s:held)s.close();}
        }
    }
    @Test void consumerExceptionCannotPreventSendingOrAcknowledging() throws Exception {
        try(var a=service(new ChatRepository(dir.resolve("a.db")),"a");var b=service(new ChatRepository(dir.resolve("b.db")),"b")) {
            a.start("127.0.0.1",0,0,false);b.start("127.0.0.1",0,0,false);
            a.onMessage(e->{throw new IllegalStateException("UI listener failure");});b.onMessage(e->{throw new IllegalStateException("UI listener failure");});
            var e=a.sendText(a.connectPeer("127.0.0.1",b.port()),"still deliver");
            assertEquals("DELIVERED",e.status);assertNotNull(b.repository().find(e.id));
        }
    }
    @Test void socketDeadlineReleasesAStalledTransferAndAllowsRetry() throws Exception {
        try(var service=SecureFixtures.service(new ChatRepository(dir.resolve("deadline.db")),dir.resolve("receiver-vault"),"receiver",java.time.Duration.ofMillis(400));var remoteDevice=new SecureFixtures.Remote(dir.resolve("remote-vault"),UUID.randomUUID().toString());var server=new ServerSocket(0,10,InetAddress.getLoopbackAddress());var executor=Executors.newSingleThreadExecutor()) {
            SecureFixtures.trust(service,remoteDevice);
            service.start("127.0.0.1",0,0,false);String sender=remoteDevice.identity.deviceId();service.repository().savePeer(new Peer(sender,"sender","127.0.0.1",server.getLocalPort(),true));
            var remote=executor.submit(()->{try(var socket=remoteDevice.accept(server)){socket.setSoTimeout(3000);Wire.read(socket.getInputStream());return socket.getInputStream().read();}});
            var e=new ChatEntry();e.peerId=sender;e.senderId=sender;e.kind="FILE";e.fileName="payload";e.token="token";e.sha256="0".repeat(64);e.size=100;e.status="OFFERED";e.expires=System.currentTimeMillis()+60000;service.repository().insertIncoming(e);
            assertThrows(ExecutionException.class,()->service.accept(e,dir.resolve("timed-out"),false).get(3,TimeUnit.SECONDS));
            assertEquals(-1,remote.get(3,TimeUnit.SECONDS));assertEquals("FAILED",service.repository().find(e.id).status);assertFalse(Files.exists(dir.resolve("timed-out")));
        }
    }
    @Test void cancellationFromFinalProgressPreservesExistingDestination() throws Exception {
        Path source=dir.resolve("source");Files.writeString(source,"small source");Path target=dir.resolve("target");Files.writeString(target,"keep original");
        try(var a=service(new ChatRepository(dir.resolve("cancel-a.db")),"a");var b=service(new ChatRepository(dir.resolve("cancel-b.db")),"b")) {
            a.start("127.0.0.1",0,0,false);b.start("127.0.0.1",0,0,false);
            var offer=a.offer(a.connectPeer("127.0.0.1",b.port()),source);
            b.onProgress((id,p)->{if(p.bytes()==p.total())try{b.cancel(id);}catch(Exception ex){throw new RuntimeException(ex);}});
            assertThrows(ExecutionException.class,()->b.accept(b.repository().find(offer.id),target,true).get(5,TimeUnit.SECONDS));
            assertEquals("keep original",Files.readString(target));assertEquals("CANCELLED",b.repository().find(offer.id).status);
        }
    }
    @Test void persistedSubmissionIsReportedEvenWhenAckIsLostAndRetryKeepsItsId() throws Exception {
        var receiver=new ChatRepository(dir.resolve("remote-receipts.db"));
        try(var service=service(new ChatRepository(dir.resolve("ack-loss.db")),"sender");var remoteDevice=new SecureFixtures.Remote(dir.resolve("remote-vault"),UUID.randomUUID().toString());var server=new ServerSocket(0,10,InetAddress.getLoopbackAddress());var executor=Executors.newSingleThreadExecutor()) {
            SecureFixtures.trust(service,remoteDevice);
            service.start("127.0.0.1",0,0,false);var peer=new Peer(remoteDevice.identity.deviceId(),"receiver","127.0.0.1",server.getLocalPort(),true);service.repository().savePeer(peer);
            var remote=executor.submit(()->{
                for(int i=0;i<2;i++)try(var socket=remoteDevice.accept(server)){
                    socket.setSoTimeout(3000);var f=Wire.read(socket.getInputStream());var incoming=new ChatEntry();incoming.id=f.getString("id");incoming.peerId=service.id();incoming.senderId=service.id();incoming.text=f.getString("text");incoming.status="DELIVERED";receiver.insertIncoming(incoming);
                    if(i==1){var ack=Wire.frame("ACK");ack.put("id",incoming.id);ack.put("device",peer.id());Wire.write(socket.getOutputStream(),ack);}
                    else remoteDevice.release(socket);
                }return null;
            });
            var persisted=new java.util.concurrent.atomic.AtomicReference<ChatEntry>();
            assertThrows(java.io.IOException.class,()->service.sendText(peer,"one submission",persisted::set));
            assertNotNull(persisted.get());assertNotNull(service.repository().find(persisted.get().id));
            assertEquals("DELIVERED",service.retry(persisted.get()).status);remote.get(5,TimeUnit.SECONDS);
            assertEquals(1,receiver.history(service.id(),Long.MAX_VALUE,"",50).size());
        }
    }
}
