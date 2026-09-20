package com.opencgl.solace;

import com.opencgl.solace.model.SolaceWorkspace;
import com.opencgl.solace.model.SolaceWorkspaceKind;
import com.opencgl.base.model.Base;
import com.opencgl.solace.persistence.AesGcmSecretProtector;
import com.opencgl.solace.persistence.JsonSolaceConfigurationRepository;
import com.opencgl.solace.persistence.SolaceConfigurationRepository;
import com.opencgl.solace.persistence.SolaceWorkspacePartition;
import com.opencgl.solace.persistence.SqliteSolaceConfigurationRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/** Shared persisted model for the independent sender and listener plugin tabs. */
public final class SolacePluginContext {
    private static final Map<SolaceWorkspaceKind, SolacePluginContext> INSTANCES = new EnumMap<>(SolaceWorkspaceKind.class);
    static {
        INSTANCES.put(SolaceWorkspaceKind.SEND, new SolacePluginContext(SolaceWorkspaceKind.SEND));
        INSTANCES.put(SolaceWorkspaceKind.LISTEN, new SolacePluginContext(SolaceWorkspaceKind.LISTEN));
    }
    private final SolaceConfigurationRepository repository;
    private SolaceWorkspace workspace;

    private SolacePluginContext(SolaceWorkspaceKind kind) {
        String home = System.getProperty("user.home", ".");
        String material = System.getProperty("user.name", "opencgl") + "@" + home + ":solace";
        AesGcmSecretProtector protector = new AesGcmSecretProtector(material);
        SolaceConfigurationRepository selectedRepository;
        try {
            Path database = Path.of(Base.DB_PATH, "data.db");
            SqliteSolaceConfigurationRepository sqlite = SqliteSolaceConfigurationRepository.forWorkspace(
                database, protector, kind);
            if (sqlite.isEmpty()) {
                SqliteSolaceConfigurationRepository shared = new SqliteSolaceConfigurationRepository(database, protector);
                SolaceWorkspace migrated = SolaceWorkspacePartition.forKind(shared.load(), kind);
                if (!migrated.getNodes().isEmpty() || !migrated.getConnections().isEmpty()) sqlite.save(migrated);
            }
            JsonSolaceConfigurationRepository legacy = new JsonSolaceConfigurationRepository(
                Path.of(home, ".opencgl", "solace", "workspace.json"), protector);
            if (sqlite.isEmpty()) {
                SolaceWorkspace migrated = SolaceWorkspacePartition.forKind(legacy.load(), kind);
                if (!migrated.getNodes().isEmpty() || !migrated.getConnections().isEmpty()) sqlite.save(migrated);
            }
            selectedRepository = sqlite;
        } catch (IOException error) {
            selectedRepository = new JsonSolaceConfigurationRepository(
                Path.of(home, ".opencgl", "solace", "workspace.json"), protector);
        }
        repository = selectedRepository;
        try { workspace = repository.load(); }
        catch (IOException error) { workspace = new SolaceWorkspace(); }
    }

    public static SolacePluginContext get(SolaceWorkspaceKind kind) { return INSTANCES.get(kind); }
    public synchronized SolaceWorkspace workspace() { return workspace; }
    public synchronized void save() throws IOException { repository.save(workspace); }
    public AutoCloseable addChangeListener(Runnable listener) { return repository.addChangeListener(listener); }
}
