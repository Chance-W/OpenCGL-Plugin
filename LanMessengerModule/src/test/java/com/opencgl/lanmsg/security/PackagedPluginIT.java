package com.opencgl.lanmsg.security;

import org.junit.jupiter.api.Test;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the shipped JAR, without Maven's dependency classpath or OS credential access. */
class PackagedPluginIT {
    @Test void packagedJarContainsWorkingNativeBridgeAndLocalCipher() throws Exception {
        Path jar = Path.of(System.getProperty("lan.packagedJar", "target/LanMessengerModule-1.0.0.jar"));
        try (var loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            // Initializing Native also exercises extraction/loading of the bundled platform binary.
            Class<?> nativeApi = assertDoesNotThrow(() -> Class.forName("com.sun.jna.Native", true, loader),
                    "Runtime JAR must bundle JNA, not rely on Maven's test classpath");
            assertTrue(nativeApi.getField("POINTER_SIZE").getInt(null) > 0);
            assertDoesNotThrow(() -> Class.forName("com.sun.jna.platform.win32.Crypt32Util", false, loader));
            assertNotNull(loader.getResource("com/sun/jna/darwin-aarch64/libjnidispatch.jnilib"));
            assertNotNull(loader.getResource("com/sun/jna/win32-x86-64/jnidispatch.dll"));
            assertNotNull(loader.getResource("com/sun/jna/linux-x86-64/libjnidispatch.so"));
            Class<?> cipherType = loader.loadClass("com.opencgl.lanmsg.security.LocalCipher");
            try (var archive = new java.util.jar.JarFile(jar.toFile())) {
                var metadata = new java.util.Properties();
                try (var stream = archive.getInputStream(archive.getJarEntry(
                        "META-INF/maven/com.opencgl/LanMessengerModule/pom.properties"))) {
                    metadata.load(stream);
                }
                assertEquals(metadata.getProperty("version"), cipherType.getPackage().getImplementationVersion(),
                        "Plugin version detection must retain the built artifact version");
            }
            byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
            byte[] plaintext = "打包验证：中文消息与图片 🖼️".getBytes(StandardCharsets.UTF_8);
            try (var cipher = (AutoCloseable) cipherType.getConstructor(byte[].class).newInstance((Object) key)) {
                var seal = cipherType.getMethod("seal", String.class, String.class, byte[].class);
                var open = cipherType.getMethod("open", String.class, String.class, byte[].class);
                byte[] encrypted = (byte[]) seal.invoke(cipher, "test", "record", plaintext);
                assertFalse(Arrays.equals(plaintext, encrypted));
                assertArrayEquals(plaintext, (byte[]) open.invoke(cipher, "test", "record", encrypted));
            } finally { Arrays.fill(key, (byte) 0); }
            assertNull(loader.getResource("org/junit/jupiter/api/Test.class"), "Do not ship test dependencies");
            assertNull(loader.getResource("com/opencgl/api/PluginUI.class"), "Host API is provided");
            assertNull(loader.getResource("com/opencgl/base/theme/ThemeManager.class"), "Host base is provided");
            assertNull(loader.getResource("javafx/scene/Node.class"), "Host JavaFX is provided");
            assertNotNull(loader.getResource("META-INF/services/com.opencgl.api.PluginUI"));
        }
    }
}
