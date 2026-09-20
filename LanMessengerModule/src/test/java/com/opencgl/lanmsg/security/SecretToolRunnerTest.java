package com.opencgl.lanmsg.security;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SecretToolRunnerTest {
    private static Process helper(String mode) throws IOException {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), Helper.class.getName(), mode).start();
    }
    @Test void capturesStdinAsBytesAndKeepsStderrSeparate() throws Exception {
        var runner = new SecretToolRunner(args -> helper("echo"), Duration.ofSeconds(10));
        try (var result = runner.run(List.of("store"), new byte[]{1, 2, 3})) {
            assertEquals(0, result.exitCode()); assertArrayEquals(new byte[]{1, 2, 3}, result.output());
            assertTrue(result.hasError());
        }
    }
    @Test void excessiveOutputAndHungProcessesAreTerminated() throws Exception {
        var process = new AtomicReference<Process>();
        var noisy = new SecretToolRunner(args -> { var p = helper("flood"); process.set(p); return p; }, Duration.ofSeconds(10));
        assertThrows(IOException.class, () -> noisy.run(List.of("lookup"), new byte[0]));
        assertFalse(process.get().isAlive());
        var hung = new SecretToolRunner(args -> { var p = helper("hang"); process.set(p); return p; }, Duration.ofMillis(150));
        assertThrows(IOException.class, () -> hung.run(List.of("lookup"), new byte[0]));
        assertFalse(process.get().isAlive());
    }
    public static final class Helper {
        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "echo" -> { System.out.write(System.in.readAllBytes()); System.err.print("private error detail"); }
                case "flood" -> { for (int i = 0; i < 10000; i++) System.out.write(new byte[1024]); }
                case "hang" -> Thread.sleep(30000);
                default -> throw new IllegalArgumentException();
            }
        }
    }
}
