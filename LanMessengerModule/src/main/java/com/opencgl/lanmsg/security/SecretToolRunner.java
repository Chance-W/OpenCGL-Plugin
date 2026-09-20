package com.opencgl.lanmsg.security;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/** Bounded subprocess I/O. Never include stdout/stderr or secret input in diagnostics. */
final class SecretToolRunner {
    @FunctionalInterface interface Starter { Process start(List<String> command) throws IOException; }
    record Result(int exitCode, byte[] output, boolean hasError) implements AutoCloseable {
        @Override public void close() { Arrays.fill(output, (byte) 0); }
    }
    private final Starter starter;
    private final Duration timeout;
    SecretToolRunner() { this(command -> new ProcessBuilder(command).start(), Duration.ofSeconds(45)); }
    SecretToolRunner(Starter starter, Duration timeout) { this.starter = starter; this.timeout = timeout; }

    Result run(List<String> arguments, byte[] input) throws IOException {
        if (input.length > 128) throw new IOException("Secret input exceeds limit");
        List<String> command = new ArrayList<>(); command.add("/usr/bin/secret-tool"); command.addAll(arguments);
        Process process;
        try { process = starter.start(List.copyOf(command)); }
        catch (IOException e) { throw new IOException("Cannot start /usr/bin/secret-tool; install libsecret tools and enable a session keyring"); }
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        Future<byte[]> output = executor.submit(() -> bounded(process.getInputStream(), process));
        Future<byte[]> error = executor.submit(() -> bounded(process.getErrorStream(), process));
        Future<?> writer = executor.submit(() -> { try (var out = process.getOutputStream()) { out.write(input); } return null; });
        byte[] stdout = null, stderr = null;
        boolean returned = false;
        try {
            long deadline = System.nanoTime() + timeout.toNanos();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new IOException("Linux Secret Service timed out");
            writer.get(remaining(deadline), TimeUnit.NANOSECONDS);
            stdout = output.get(remaining(deadline), TimeUnit.NANOSECONDS);
            stderr = error.get(remaining(deadline), TimeUnit.NANOSECONDS);
            returned = true;
            return new Result(process.exitValue(), stdout, stderr.length != 0);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("System key access interrupted"); }
        catch (ExecutionException | TimeoutException e) { throw new IOException("System key helper failed or exceeded output/time limit"); }
        finally {
            process.destroyForcibly();
            // Killing the helper closes its pipe ends and unblocks all readers/writers.
            try { process.waitFor(2, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            writer.cancel(true); output.cancel(true); error.cancel(true);
            executor.shutdownNow();
            if (!returned && stdout != null) Arrays.fill(stdout, (byte) 0);
            if (stderr != null) Arrays.fill(stderr, (byte) 0);
        }
    }

    private static long remaining(long deadline) throws TimeoutException {
        long nanos = deadline - System.nanoTime(); if (nanos <= 0) throw new TimeoutException(); return nanos;
    }
    private static byte[] bounded(InputStream input, Process process) throws IOException {
        byte[] buffer = new byte[4097];
        try (input) {
            int total = 0, count;
            while ((count = input.read(buffer, total, buffer.length - total)) != -1) {
                total += count;
                if (total == buffer.length) { process.destroyForcibly(); throw new IOException("System key helper output exceeds limit"); }
            }
            return Arrays.copyOf(buffer, total);
        } finally { Arrays.fill(buffer, (byte) 0); }
    }
}
