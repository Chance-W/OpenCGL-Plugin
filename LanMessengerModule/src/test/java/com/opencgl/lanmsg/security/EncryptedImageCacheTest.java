package com.opencgl.lanmsg.security;

import com.opencgl.lanmsg.ui.MediaSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class EncryptedImageCacheTest {
    @TempDir Path dir;
    @Test void encryptedPngSurvivesReopenAndPreviewUsesMemory() throws Exception {
        var keys=new LocalVaultTest.MemoryStore();byte[] png;
        var image=new BufferedImage(100,80,BufferedImage.TYPE_INT_RGB);image.setRGB(2,3,0xff112233);
        png=MediaSupport.encodePng(image);
        String ref;
        try(var vault=LocalVault.open(dir.resolve("vault"),keys)) {
            var cache=new EncryptedImageCache(dir.resolve("images"),vault);ref=cache.put(png);
        }
        try(var vault=LocalVault.open(dir.resolve("vault"),keys)) {
            var cache=new EncryptedImageCache(dir.resolve("images"),vault);assertArrayEquals(png,cache.read(ref));
            var preview=MediaSupport.read(cache.read(ref),20);assertEquals(20,preview.getWidth());assertEquals(16,preview.getHeight());
        }
        try(var files=Files.walk(dir)) {for(var file:files.filter(Files::isRegularFile).toList()) {
            assertFalse(java.util.Arrays.equals(png,Files.readAllBytes(file)));assertFalse(file.toString().endsWith(".png"));
        }}
    }
    @Test void corruptionInvalidReferenceAndOversizeFailWithoutPlaintextFallback() throws Exception {
        try(var vault=LocalVault.open(dir.resolve("vault"),new LocalVaultTest.MemoryStore())) {
            var cache=new EncryptedImageCache(dir.resolve("images"),vault);String ref=cache.put(new byte[]{1,2,3});
            Path record;try(var files=Files.list(dir.resolve("images"))){record=files.findFirst().orElseThrow();}
            byte[] bytes=Files.readAllBytes(record);bytes[bytes.length-1]^=1;Files.write(record,bytes);
            assertThrows(IOException.class,()->cache.read(ref));
            assertThrows(IOException.class,()->cache.read("lan-cache:../../secret"));
            assertThrows(IOException.class,()->cache.put(new byte[LocalCipher.MAX_PLAINTEXT_BYTES+1]));
        }
    }
    @Test void clearOnlyRemovesOwnedCiphertextNotUnrelatedFilesOrSymlinkTargets() throws Exception {
        try(var vault=LocalVault.open(dir.resolve("vault"),new LocalVaultTest.MemoryStore())) {
            var root=dir.resolve("images");var cache=new EncryptedImageCache(root,vault);String ref=cache.put(new byte[]{3});
            Files.write(root.resolve("keep.txt"),new byte[]{9});Path external=dir.resolve("external");Files.write(external,new byte[]{8});
            Files.createSymbolicLink(root.resolve(java.util.UUID.randomUUID()+".sealed"),external);
            assertEquals(1,cache.clear());assertArrayEquals(new byte[]{9},Files.readAllBytes(root.resolve("keep.txt")));
            assertArrayEquals(new byte[]{8},Files.readAllBytes(external));assertThrows(IOException.class,()->cache.read(ref));
        }
    }
}
