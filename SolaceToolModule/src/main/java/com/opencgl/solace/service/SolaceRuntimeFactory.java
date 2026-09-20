package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceConnectionConfig;

@FunctionalInterface
public interface SolaceRuntimeFactory {
    SolaceRuntimeConnection open(SolaceConnectionConfig config) throws Exception;
}
