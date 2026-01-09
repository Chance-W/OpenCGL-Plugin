package com.opencgl.base.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WeakReferenceRegistryTest {

    @Test
    void registrationIsIdentityBasedAndCanBeRemoved() {
        WeakReferenceRegistry<Object> registry = new WeakReferenceRegistry<>();
        Object first = new Object();
        Object equalButDifferent = new String("value");

        registry.add(first);
        registry.add(first);
        registry.add(equalButDifferent);

        assertEquals(2, registry.liveValues().size());
        registry.remove(first);
        assertEquals(1, registry.liveValues().size());
    }
}
