package com.opencgl.solace.variable;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves OpenCGL dynamic variables plus Solace environment variables. */
public final class SolaceVariableResolver {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern RANDOM = Pattern.compile("Random(\\d*)", Pattern.CASE_INSENSITIVE);

    private final Function<String, String> environmentLookup;
    private final Function<String, String> systemPropertyLookup;
    private final Clock clock;

    public SolaceVariableResolver() {
        this(System::getenv, System::getProperty, Clock.systemDefaultZone());
    }

    public SolaceVariableResolver(Function<String, String> environmentLookup,
                                  Function<String, String> systemPropertyLookup,
                                  Clock clock) {
        this.environmentLookup = Objects.requireNonNull(environmentLookup, "environmentLookup");
        this.systemPropertyLookup = Objects.requireNonNull(systemPropertyLookup, "systemPropertyLookup");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Resolution resolve(String input, VariableScope scope) {
        if (input == null || input.isEmpty()) {
            return new Resolution(input, Set.of());
        }
        VariableScope effectiveScope = scope == null ? VariableScope.empty() : scope;
        Set<String> unresolved = new LinkedHashSet<>();
        Matcher matcher = VARIABLE.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = resolveOne(name, effectiveScope);
            if (value == null) {
                unresolved.add(name);
                value = matcher.group();
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(output);
        return new Resolution(output.toString(), Collections.unmodifiableSet(unresolved));
    }

    public String mask(String name, String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("secret") || normalized.contains("token")
            ? "******" : value;
    }

    private String resolveOne(String name, VariableScope scope) {
        if (scope.nodeVariables().containsKey(name)) return scope.nodeVariables().get(name);
        if (scope.connectionVariables().containsKey(name)) return scope.connectionVariables().get(name);
        if (scope.environmentVariables().containsKey(name)) return scope.environmentVariables().get(name);
        if (name.startsWith("env:")) return environmentLookup.apply(name.substring(4));
        if (name.startsWith("sys:")) return systemPropertyLookup.apply(name.substring(4));

        if ("UUID".equalsIgnoreCase(name)) return UUID.randomUUID().toString();
        if ("UUID_SIMPLE".equalsIgnoreCase(name)) return UUID.randomUUID().toString().replace("-", "");
        if ("UUID_UPPER".equalsIgnoreCase(name)) return UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        if ("TIMESTAMP".equalsIgnoreCase(name) || "DATE".equalsIgnoreCase(name)) {
            return Long.toString(clock.millis());
        }
        if ("TIMESTAMP_S".equalsIgnoreCase(name)) return Long.toString(clock.millis() / 1000);

        Matcher randomMatcher = RANDOM.matcher(name);
        if (randomMatcher.matches()) {
            int length = randomMatcher.group(1).isEmpty() ? 8 : Integer.parseInt(randomMatcher.group(1));
            return randomDigits(length);
        }
        try {
            return LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern(name));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String randomDigits(int requestedLength) {
        int length = Math.max(1, requestedLength);
        StringBuilder value = new StringBuilder(length);
        value.append(ThreadLocalRandom.current().nextInt(1, 10));
        for (int i = 1; i < length; i++) value.append(ThreadLocalRandom.current().nextInt(10));
        return value.toString();
    }

    public record VariableScope(Map<String, String> environmentVariables,
                                Map<String, String> connectionVariables,
                                Map<String, String> nodeVariables) {
        public VariableScope {
            environmentVariables = immutable(environmentVariables);
            connectionVariables = immutable(connectionVariables);
            nodeVariables = immutable(nodeVariables);
        }

        public static VariableScope empty() {
            return new VariableScope(Map.of(), Map.of(), Map.of());
        }

        private static Map<String, String> immutable(Map<String, String> values) {
            return values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public record Resolution(String value, Set<String> unresolvedVariables) {
        public Resolution {
            unresolvedVariables = unresolvedVariables == null ? Set.of() : Set.copyOf(unresolvedVariables);
        }

        public boolean resolved() {
            return unresolvedVariables.isEmpty();
        }
    }
}
