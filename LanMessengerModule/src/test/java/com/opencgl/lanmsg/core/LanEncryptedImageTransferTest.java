package com.opencgl.lanmsg.core;

import com.opencgl.lanmsg.security.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class LanEncryptedImageTransferTest {
    @TempDir Path directory;
    private static class Endpoint implements AutoCloseable {
        final LocalVault vault;final ChatRepository repo;final EncryptedImageCache cache;final LanService service;
        Endpoint(Path root) throws Exception {
            vault=LocalVault.open(root.resolve("security"),new MemorySecretStore());
            repo=new ChatRepository(root.resolve("history.db"),vault);cache=new EncryptedImageCache(root.resolve("images"),vault);
            var secure=new SecureTransport(DeviceIdentity.loadOrCreate(vault,repo.identity()),new TrustRepository(vault),"中文测试端");
            secure.onPairing(p->p.decision().complete(true));service=new LanService(repo,"中文测试端",secure,cache);
            service.start("127.0.0.1",0,0,false);
        }
        public void close() throws Exception {service.close();vault.close();}
    }
    @Test void cachedImageTransfersAndRetriesWithoutDecryptedSourceFile() throws Exception {
        byte[] bytes="中文截图内容📎".repeat(1000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(var a=new Endpoint(directory.resolve("a"));var b=new Endpoint(directory.resolve("b"))) {
            var peer=a.service.connectPeer("127.0.0.1",b.service.port());String ref=a.cache.put(bytes);
            var offered=a.service.offerCached(peer,ref);assertEquals("IMAGE",offered.kind);assertEquals(ref,offered.path);
            var received=b.repo.find(offered.id);Path target=directory.resolve("接收截图.png");
            b.service.accept(received,target,false).get(10,TimeUnit.SECONDS);assertArrayEquals(bytes,Files.readAllBytes(target));
            var retry=a.service.retry(offered);assertNotEquals(offered.id,retry.id);
            b.service.accept(b.repo.find(retry.id),directory.resolve("再次接收.png"),false).get(10,TimeUnit.SECONDS);
            try(var files=Files.walk(directory.resolve("a"))) {for(var file:files.filter(Files::isRegularFile).toList()) {
                assertFalse(Arrays.equals(bytes,Files.readAllBytes(file)));assertFalse(file.toString().endsWith(".png"));
            }}
            a.cache.clear();assertThrows(java.io.IOException.class,()->a.service.retry(offered));
        }
    }
    @Test void corruptionAfterOfferCannotCommitReceivedFileOrLeaveTemporaryPlaintext() throws Exception {
        try(var a=new Endpoint(directory.resolve("a"));var b=new Endpoint(directory.resolve("b"))) {
            var peer=a.service.connectPeer("127.0.0.1",b.service.port());String ref=a.cache.put(new byte[]{1,2,3});
            var offered=a.service.offerCached(peer,ref);Path record;
            try(var files=Files.list(directory.resolve("a/images"))){record=files.findFirst().orElseThrow();}
            byte[] bytes=Files.readAllBytes(record);bytes[bytes.length-1]^=1;Files.write(record,bytes);
            Path target=directory.resolve("不应出现.png");
            assertThrows(java.util.concurrent.ExecutionException.class,()->b.service.accept(b.repo.find(offered.id),target,false).get(10,TimeUnit.SECONDS));
            assertFalse(Files.exists(target));
            try(var files=Files.list(directory)){assertFalse(files.anyMatch(p->p.getFileName().toString().endsWith(".part")));}
        }
    }
}
