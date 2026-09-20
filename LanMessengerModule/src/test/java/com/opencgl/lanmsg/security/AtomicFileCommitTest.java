package com.opencgl.lanmsg.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtomicFileCommitTest {
    @TempDir Path directory;
    @Test void syncsContainingDirectoryAfterReplacement() throws Exception {
        Path target = directory.resolve("record"); var steps = new ArrayList<String>();
        PrivateFiles.atomicWrite(target, new byte[]{7}, new PrivateFiles.CommitOperations() {
            public void move(Path source, Path destination) throws IOException {
                assertArrayEquals(new byte[]{7}, Files.readAllBytes(source));
                steps.add("move"); Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
            }
            public void sync(Path parent) throws IOException {
                assertEquals(directory, parent); assertArrayEquals(new byte[]{7}, Files.readAllBytes(target));
                steps.add("sync");
            }
        });
        assertEquals(List.of("move", "sync"), steps);
    }

    @Test void failedMovePreservesPreviousFileAndCleansOnlyItsTemporaryFile() throws Exception {
        Path target = directory.resolve("record"); Files.write(target, new byte[]{1});
        Path unrelated = directory.resolve("keep"); Files.write(unrelated, new byte[]{9});
        assertThrows(IOException.class, () -> PrivateFiles.atomicWrite(target, new byte[]{7}, new PrivateFiles.CommitOperations() {
            public void move(Path source, Path destination) throws IOException { throw new IOException("injected rename failure"); }
            public void sync(Path parent) { fail("Must not sync an uncommitted rename"); }
        }));
        assertArrayEquals(new byte[]{1}, Files.readAllBytes(target));
        assertArrayEquals(new byte[]{9}, Files.readAllBytes(unrelated));
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }

    @Test void failedDirectorySyncDoesNotFalselyReportSuccessOrDestroyCommittedBytes() throws Exception {
        Path target = directory.resolve("record"); Files.write(target, new byte[]{1});
        assertThrows(IOException.class, () -> PrivateFiles.atomicWrite(target, new byte[]{7}, new PrivateFiles.CommitOperations() {
            public void move(Path source, Path destination) throws IOException {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            public void sync(Path parent) throws IOException { throw new IOException("injected directory sync failure"); }
        }));
        assertArrayEquals(new byte[]{7}, Files.readAllBytes(target));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
