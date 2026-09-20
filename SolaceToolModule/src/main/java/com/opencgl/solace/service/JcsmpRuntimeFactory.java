package com.opencgl.solace.service;

import com.opencgl.solace.model.SolaceConnectionConfig;
import com.solacesystems.jcsmp.JCSMPFactory;
import com.solacesystems.jcsmp.JCSMPProperties;
import com.solacesystems.jcsmp.JCSMPSession;

/** Production bridge to the Solace JCSMP client. */
public final class JcsmpRuntimeFactory implements SolaceRuntimeFactory {
    private final SolaceSessionFactory propertyFactory = new SolaceSessionFactory();
    private final SolaceMessageMapper messageMapper = new SolaceMessageMapper();

    @Override
    public SolaceRuntimeConnection open(SolaceConnectionConfig config) throws Exception {
        JCSMPProperties properties = propertyFactory.toProperties(config);
        JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
        session.connect();
        return new JcsmpRuntimeConnection(session, messageMapper);
    }
}
