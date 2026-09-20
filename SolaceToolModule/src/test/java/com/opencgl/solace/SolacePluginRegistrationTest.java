package com.opencgl.solace;

import com.opencgl.api.PluginUI;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SolacePluginRegistrationTest {
    @Test
    void serviceLoaderDiscoversIndependentSendAndListenEntries() {
        List<PluginUI> plugins = ServiceLoader.load(PluginUI.class).stream()
            .map(ServiceLoader.Provider::get)
            .filter(plugin -> plugin.pluginId().startsWith("com.opencgl.solace."))
            .toList();

        assertEquals(List.of("com.opencgl.solace.send", "com.opencgl.solace.listen"),
            plugins.stream().map(PluginUI::pluginId).toList());
        assertEquals(List.of("运维工具", "运维工具"), plugins.stream().map(PluginUI::category).toList());
        assertEquals(List.of("运维工具", "运维工具"), plugins.stream().map(PluginUI::directoryName).toList());
        plugins.forEach(plugin -> assertNotNull(plugin.iconPath()));
    }
}
