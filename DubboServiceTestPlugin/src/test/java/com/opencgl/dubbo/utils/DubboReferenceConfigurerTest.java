package com.opencgl.dubbo.utils;

import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.service.GenericService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DubboReferenceConfigurerTest {

    @Test
    void directProviderDoesNotBindRegistry() {
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
        RegistryConfig registry = new RegistryConfig("zookeeper://127.0.0.1:2181");

        DubboReferenceConfigurer.configure(
            reference,
            registry,
            "  dubbo://127.0.0.1:20889  "
        );

        assertEquals("dubbo://127.0.0.1:20889", reference.getUrl());
        assertNull(reference.getRegistry());
    }

    @Test
    void missingDirectProviderBindsRegistry() {
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
        RegistryConfig registry = new RegistryConfig("zookeeper://127.0.0.1:2181");

        DubboReferenceConfigurer.configure(reference, registry, "  ");

        assertNull(reference.getUrl());
        assertSame(registry, reference.getRegistry());
    }
}
