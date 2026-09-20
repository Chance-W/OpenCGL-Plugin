package com.opencgl.solace.persistence;

import com.opencgl.solace.model.SolaceWorkspace;

import java.io.IOException;

/** One-time import from the former JSON workspace into the shared OpenCGL SQLite database. */
public final class SolaceRepositoryMigration {
    private SolaceRepositoryMigration() { }

    public static void importLegacyJsonWhenEmpty(SqliteSolaceConfigurationRepository target,
                                                  SolaceConfigurationRepository legacy) throws IOException {
        if (!target.isEmpty()) return;
        SolaceWorkspace oldWorkspace = legacy.load();
        if (!oldWorkspace.getNodes().isEmpty() || !oldWorkspace.getConnections().isEmpty()) {
            target.save(oldWorkspace);
        }
    }
}
