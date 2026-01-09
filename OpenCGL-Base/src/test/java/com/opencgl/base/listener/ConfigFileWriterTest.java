package com.opencgl.base.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigFileWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesByAtomicReplacementWithoutLeavingTemporaryFile() throws Exception {
        Path target = tempDir.resolve("config.json");
        Files.writeString(target, "old", StandardCharsets.UTF_8);

        ConfigFileWriter.writeAtomically(target, "new");

        assertEquals("new", Files.readString(target, StandardCharsets.UTF_8));
        assertFalse(Files.exists(tempDir.resolve("config.json.tmp")));
    }

    @Test
    void serializesRapidUpdatesAndWaitsForTheNewestValue() throws Exception {
        Path target = tempDir.resolve("config.json");
        try (ConfigFileWriter writer = new ConfigFileWriter()) {
            writer.submit(target, "first");
            writer.submit(target, "second");

            assertTrue(writer.awaitPendingWrites(Duration.ofSeconds(2)));
            assertEquals("second", Files.readString(target, StandardCharsets.UTF_8));
        }
    }
}
