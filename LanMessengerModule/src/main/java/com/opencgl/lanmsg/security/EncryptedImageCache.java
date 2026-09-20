package com.opencgl.lanmsg.security;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/** Bounded encrypted clipboard objects. Opaque references are not filesystem paths. */
public final class EncryptedImageCache {
    private static final String PREFIX="lan-cache:";
    private final Path directory;
    private final LocalVault vault;
    public EncryptedImageCache(Path directory,LocalVault vault) throws IOException {
        this.directory=PrivateFiles.directory(directory);this.vault=java.util.Objects.requireNonNull(vault);
    }
    public static boolean isReference(String value) { return value!=null&&value.startsWith(PREFIX); }
    private static String id(String reference) throws IOException {
        if(!isReference(reference))throw new IOException("Invalid encrypted image reference");
        String id=reference.substring(PREFIX.length());
        try {if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();}
        catch(IllegalArgumentException e){throw new IOException("Invalid encrypted image reference");}
        return id;
    }
    public synchronized String put(byte[] bytes) throws IOException {
        if(bytes==null||bytes.length==0||bytes.length>LocalCipher.MAX_PLAINTEXT_BYTES)throw new IOException("Pasted image must be between 1 byte and 16 MiB encoded");
        String id=UUID.randomUUID().toString();
        PrivateFiles.atomicWrite(directory.resolve(id+".sealed"),vault.sealRecord("clipboard-v1",id,bytes));
        return PREFIX+id;
    }
    /** Caller owns returned plaintext and should clear it after use. */
    public synchronized byte[] read(String reference) throws IOException {
        String id=id(reference);
        return vault.openRecord("clipboard-v1",id,PrivateFiles.read(directory.resolve(id+".sealed"),LocalCipher.MAX_ENVELOPE_BYTES));
    }
    public synchronized int clear() throws IOException {
        int count=0;
        try(var files=Files.newDirectoryStream(directory,"*.sealed")) {
            for(Path file:files) {
                String name=file.getFileName().toString();
                try {id(PREFIX+name.substring(0,name.length()-7));}catch(IOException e){continue;}
                if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)){Files.delete(file);count++;}
            }
        }
        return count;
    }
}
