package com.opencgl.lanmsg.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import com.opencgl.lanmsg.security.SecureFixtures;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LanServiceTest {
    @TempDir Path directory;
    @AfterEach void cleanup() throws Exception {SecureFixtures.closeAll();}
    LanService service(String name) throws Exception { return SecureFixtures.service(new ChatRepository(directory.resolve(name+".db")),directory.resolve(name+"-vault"),name); }
    @Test void textDeliveryAndRetryAreAcknowledgedWithoutDuplicateHistory() throws Exception {
        try(var a=service("A"); var b=service("B")) {
            a.start("127.0.0.1",0,0,false); b.start("127.0.0.1",0,0,false);
            var peer=a.connectPeer("127.0.0.1",b.port());
            var sent=a.sendText(peer,"你好|text\nline two");
            assertEquals("DELIVERED",sent.status);
            assertEquals("你好|text\nline two",b.repository().history(a.id(),Long.MAX_VALUE,"",50).get(0).text);
            a.retry(sent);
            assertEquals(1,b.repository().history(a.id(),Long.MAX_VALUE,"",50).size());
            assertEquals(a.id(),b.repository().peers().get(0).id());
        }
    }
    @Test void occupiedTcpPortDoesNotLeaveDiscoverySocketBound() throws Exception {
        try(var occupied=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1")); var udp=new DatagramSocket(0); var a=service("A")) {
            int udpPort=udp.getLocalPort(); udp.close();
            assertThrows(Exception.class,()->a.start("127.0.0.1",udpPort,occupied.getLocalPort(),false));
            assertFalse(a.running());
            try(var available=new DatagramSocket(udpPort)) { assertEquals(udpPort,available.getLocalPort()); }
            a.start("127.0.0.1",udpPort,0,false); assertTrue(a.running());
        }
    }
    @Test void fileOfferDownloadsOverSamePortAndVerifiesBytesBeforeCompletion() throws Exception {
        Path source=directory.resolve("source.txt"); Files.writeString(source,"你好|\n".repeat(5000));
        try(var a=service("A");var b=service("B")) {
            a.start("127.0.0.1",0,0,false); b.start("127.0.0.1",0,0,false);
            var peer=a.connectPeer("127.0.0.1",b.port());
            var offer=a.offer(peer,source);
            var incoming=b.repository().find(offer.id);
            assertEquals("OFFERED",incoming.status);
            Path destination=directory.resolve("received.txt");
            b.accept(incoming,destination,false).get(10,TimeUnit.SECONDS);
            assertArrayEquals(Files.readAllBytes(source),Files.readAllBytes(destination));
            assertEquals("COMPLETED",b.repository().find(offer.id).status);
            assertEquals(destination.toString(),b.repository().find(offer.id).path);
        }
    }
    @Test void changedSourceFailsWithoutOverwritingExistingDestination() throws Exception {
        Path source=directory.resolve("source.bin");Files.writeString(source,"original");
        Path destination=directory.resolve("existing.bin");Files.writeString(destination,"keep me");
        try(var a=service("A");var b=service("B")) {
            a.start("127.0.0.1",0,0,false);b.start("127.0.0.1",0,0,false);
            var offer=a.offer(a.connectPeer("127.0.0.1",b.port()),source);
            Files.writeString(source,"modified");
            assertThrows(ExecutionException.class,()->b.accept(b.repository().find(offer.id),destination,true).get(10,TimeUnit.SECONDS));
            assertEquals("keep me",Files.readString(destination));
            assertEquals("FAILED",b.repository().find(offer.id).status);
        }
    }
    @Test void cancellingOfferInvalidatesDownloadToken() throws Exception {
        Path source=directory.resolve("source.txt");Files.writeString(source,"test");
        try(var a=service("A");var b=service("B")) {
            a.start("127.0.0.1",0,0,false);b.start("127.0.0.1",0,0,false);
            var offer=a.offer(a.connectPeer("127.0.0.1",b.port()),source);a.cancel(offer.id);
            assertThrows(java.io.IOException.class,()->b.accept(b.repository().find(offer.id),directory.resolve("no.txt"),false));
            assertFalse(Files.exists(directory.resolve("no.txt")));
        }
    }
    @Test void discoveryRepliesOnceAndAnnouncementsDoNotTriggerReplyLoops() throws Exception {
        try(var reserve=new DatagramSocket(0);var remote=new DatagramSocket(0);var a=service("discovery")) {
            int port=reserve.getLocalPort();reserve.close();a.start("127.0.0.1",port,0,false);
            var frame=Wire.frame("DISCOVER");frame.put("device",java.util.UUID.randomUUID().toString());frame.put("name","test");frame.put("port",12345);
            byte[] request=com.alibaba.fastjson2.JSON.toJSONBytes(frame);remote.send(new DatagramPacket(request,request.length,InetAddress.getByName("127.0.0.1"),port));
            remote.setSoTimeout(2000);var reply=new DatagramPacket(new byte[4096],4096);remote.receive(reply);
            var parsed=com.alibaba.fastjson2.JSON.parseObject(java.util.Arrays.copyOf(reply.getData(),reply.getLength()));assertEquals("ANNOUNCE",parsed.getString("op"));assertEquals(a.port(),parsed.getIntValue("port"));
            frame.put("op","ANNOUNCE");byte[] announcement=com.alibaba.fastjson2.JSON.toJSONBytes(frame);remote.send(new DatagramPacket(announcement,announcement.length,InetAddress.getByName("127.0.0.1"),port));
            remote.setSoTimeout(250);assertThrows(SocketTimeoutException.class,()->remote.receive(new DatagramPacket(new byte[4096],4096)));
        }
    }
    @Test void wrongTokenIsRejectedWithoutConsumingValidOffer() throws Exception {
        Path file=directory.resolve("token.txt");Files.writeString(file,"test");
        try(var a=service("token-a");var b=service("token-b")) {
            a.start("127.0.0.1",0,0,false);b.start("127.0.0.1",0,0,false);
            var offer=a.offer(a.connectPeer("127.0.0.1",b.port()),file);
            try(var socket=SecureFixtures.connect(b,a)) {
                socket.setSoTimeout(3000);var get=Wire.frame("GET");get.put("device",b.id());get.put("name","b");get.put("port",b.port());get.put("to",a.id());get.put("id",offer.id);get.put("token","wrong");Wire.write(socket.getOutputStream(),get);
                assertEquals("ERROR",Wire.read(socket.getInputStream()).getString("op"));
            }
            Path target=directory.resolve("correct-token.txt");b.accept(b.repository().find(offer.id),target,false).get(5,TimeUnit.SECONDS);assertEquals("test",Files.readString(target));
        }
    }
}
