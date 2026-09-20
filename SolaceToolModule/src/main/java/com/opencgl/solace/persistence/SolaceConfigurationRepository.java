package com.opencgl.solace.persistence;

import com.opencgl.solace.model.SolaceWorkspace;

import java.io.IOException;

public interface SolaceConfigurationRepository {
    SolaceWorkspace load() throws IOException;
    void save(SolaceWorkspace workspace) throws IOException;
    AutoCloseable addChangeListener(Runnable listener);
}
