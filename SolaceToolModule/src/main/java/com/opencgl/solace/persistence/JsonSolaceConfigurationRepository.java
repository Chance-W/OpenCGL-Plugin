package com.opencgl.solace.persistence;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.opencgl.solace.model.SolaceConnectionConfig;
import com.opencgl.solace.model.SolaceWorkspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** JSON repository with atomic replacement and explicit password protection. */
public final class JsonSolaceConfigurationRepository implements SolaceConfigurationRepository {
    private final Path file;
    private final SecretProtector secretProtector;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public JsonSolaceConfigurationRepository(Path file, SecretProtector secretProtector) {
        this.file = file.toAbsolutePath().normalize();
        this.secretProtector = secretProtector;
    }

    @Override
    public synchronized SolaceWorkspace load() throws IOException {
        if (!Files.exists(file) || Files.size(file) == 0) return new SolaceWorkspace();
        SolaceWorkspace workspace = JSON.parseObject(Files.readString(file, StandardCharsets.UTF_8), SolaceWorkspace.class);
        if (workspace == null) workspace = new SolaceWorkspace();
        workspace.getConnections().values().forEach(this::restoreSecret);
        return workspace;
    }

    @Override
    public synchronized void save(SolaceWorkspace workspace) throws IOException {
        SolaceWorkspace persisted = deepCopy(workspace == null ? new SolaceWorkspace() : workspace);
        persisted.getConnections().values().forEach(this::prepareSecretForStorage);
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.writeString(temporary,
                JSON.toJSONString(persisted, JSONWriter.Feature.PrettyFormat), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
        listeners.forEach(listener -> {
            try { listener.run(); } catch (RuntimeException ignored) { }
        });
    }

    @Override
    public AutoCloseable addChangeListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private SolaceWorkspace deepCopy(SolaceWorkspace workspace) {
        return JSON.parseObject(JSON.toJSONString(workspace), SolaceWorkspace.class);
    }

    private void prepareSecretForStorage(SolaceConnectionConfig connection) {
        String password = connection.getPassword();
        if (!connection.isRememberPassword() || password == null || password.isEmpty()) {
            connection.setPassword(null);
        } else {
            connection.setPassword(secretProtector.protect(password));
        }
    }

    private void restoreSecret(SolaceConnectionConfig connection) {
        String protectedPassword = connection.getPassword();
        if (connection.isRememberPassword() && protectedPassword != null && !protectedPassword.isEmpty()) {
            connection.setPassword(secretProtector.unprotect(protectedPassword));
        } else {
            connection.setPassword(null);
        }
    }
}
