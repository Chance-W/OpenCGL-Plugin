package com.opencgl.lanmsg.security;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.DWORD;

/** Files contain ciphertext only. User-selected exports do not use this storage. */
final class PrivateFiles {
    private PrivateFiles() { }
    interface CommitOperations {
        void move(Path source, Path destination) throws IOException;
        void sync(Path directory) throws IOException;
    }
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    private static final CommitOperations COMMIT = new CommitOperations() {
        public void move(Path source, Path destination) throws IOException {
            if (WINDOWS) {
                // Same-directory rename; no COPY_ALLOWED. Request write-through in addition to forcing data.
                try {
                    if (!Kernel32.INSTANCE.MoveFileEx(source.toString(), destination.toString(), new DWORD(0x1 | 0x8)))
                        throw new IOException("Windows vault rename failed (status " + Kernel32.INSTANCE.GetLastError() + ")");
                } catch (LinkageError e) { throw new IOException("Windows durable file operations are unavailable"); }
            } else {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        public void sync(Path directory) throws IOException { syncDirectory(directory); }
    };

    private static void syncDirectory(Path directory) throws IOException {
        if (WINDOWS) return; // Write-through MoveFileEx above; directory fsync is not portable to Windows.
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }

    static Path directory(Path directory) throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(absolute)) throw new IOException("Vault directory cannot be a symbolic link");
        var missing = new ArrayList<Path>();
        for (Path p = absolute; p != null && !Files.exists(p, LinkOption.NOFOLLOW_LINKS); p = p.getParent()) missing.add(p);
        try {
            Files.createDirectories(absolute, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } catch (UnsupportedOperationException e) { Files.createDirectories(absolute); }
        if (!Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid vault directory");
        restrict(absolute);
        for (Path created : missing) {
            syncDirectory(created.toRealPath());
            if (created.getParent() != null) syncDirectory(created.getParent().toRealPath());
        }
        return absolute.toRealPath();
    }

    static void restrict(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolic links are not allowed in vault storage");
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ? "rwx------" : "rw-------"));
            return;
        }
        var acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (acl == null) throw new IOException("Filesystem cannot protect private vault files");
        var owner = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                .setPermissions(EnumSet.allOf(AclEntryPermission.class));
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) owner.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
        acl.setAcl(List.of(owner.build()));
    }

    static byte[] read(Path file, int limit) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid vault file");
        try (var channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size < 0 || size > limit) throw new IOException("Vault file exceeds size limit");
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw new IOException("Truncated vault file");
            if (channel.read(ByteBuffer.allocate(1)) != -1) throw new IOException("Vault file changed during read");
            return buffer.array();
        }
    }

    static void atomicWrite(Path file, byte[] bytes) throws IOException {
        atomicWrite(file, bytes, COMMIT);
    }

    static void atomicWrite(Path file, byte[] bytes, CommitOperations commit) throws IOException {
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Invalid vault write target");
        Path temporary;
        try {
            temporary = Files.createTempFile(file.getParent(), ".vault-", ".tmp",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException e) { temporary = Files.createTempFile(file.getParent(), ".vault-", ".tmp"); }
        try {
            restrict(temporary);
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            commit.move(temporary, file);
            commit.sync(file.getParent());
        } finally { Files.deleteIfExists(temporary); }
    }

    static FileChannel lockChannel(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Invalid vault lock file");
        var channel = FileChannel.open(path, Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS));
        try { restrict(path); return channel; }
        catch (IOException e) { channel.close(); throw e; }
    }
}
