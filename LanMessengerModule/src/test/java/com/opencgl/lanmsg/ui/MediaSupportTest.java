package com.opencgl.lanmsg.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class MediaSupportTest {
    @TempDir Path dir;
    @Test void decodesBoundedThumbnailAndRejectsNonImages() throws Exception {
        Path image=dir.resolve("sample.png");
        ImageIO.write(new BufferedImage(1600,800,BufferedImage.TYPE_INT_RGB),"png",image.toFile());
        var decoded=MediaSupport.read(image,320);
        assertTrue(decoded.getWidth()<=320);
        assertTrue(decoded.getHeight()<=320);
        Path invalid=dir.resolve("invalid.png");Files.writeString(invalid,"not an image");
        assertThrows(java.io.IOException.class,()->MediaSupport.read(invalid,320));
    }
    @Test void safeNamesCannotEscapeDestinationOrUseWindowsReservedNames() {
        assertEquals("report.txt",MediaSupport.safeName("report.txt"));
        assertFalse(MediaSupport.safeName("../foo\\bar:?.txt").contains("/"));
        assertFalse(MediaSupport.safeName("CON").equalsIgnoreCase("CON"));
    }
}
