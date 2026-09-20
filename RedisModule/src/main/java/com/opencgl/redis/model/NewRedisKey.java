package com.opencgl.redis.model;

import java.util.*;
import com.opencgl.redis.i18n.I18N;

/** Validated, immutable request. Values are never parsed as CLI command text. */
public record NewRedisKey(String key, List<String> arguments) {
    public record Row(String first, String second) {}

    public NewRedisKey { arguments = List.copyOf(arguments); }

    public static NewRedisKey of(String key, RedisKeyInfo.KeyType type, String ttlText,
                                 String text, List<Row> rows, String streamId) {
        if (key == null || key.isBlank()) throw invalid("newkey.error.key");
        long ttl;
        try {
            ttl = ttlText == null || ttlText.isBlank() ? 0 : Long.parseLong(ttlText.trim());
            if (ttl < 0 || ttl > Integer.MAX_VALUE) throw new NumberFormatException();
        } catch (NumberFormatException e) { throw invalid("newkey.error.ttl"); }
        if (type == null || type == RedisKeyInfo.KeyType.NONE) throw invalid("newkey.error.type");
        String command = switch (type) {
            case STRING -> "SET"; case HASH -> "HSET"; case LIST -> "RPUSH";
            case SET -> "SADD"; case ZSET -> "ZADD"; case STREAM -> "XADD";
            default -> throw invalid("newkey.error.type");
        };
        var args = new ArrayList<String>(List.of(command, Long.toString(ttl)));
        if (type == RedisKeyInfo.KeyType.STRING) {
            args.add(Objects.requireNonNullElse(text, ""));
        } else {
            if (rows == null || rows.isEmpty() || rows.size() > 1000) throw invalid("newkey.error.rows");
            if (type == RedisKeyInfo.KeyType.STREAM) {
                String id = streamId == null || streamId.isBlank() ? "*" : streamId.trim();
                if (!id.equals("*")) {
                    if (!id.matches("[0-9]+-[0-9]+")) throw invalid("newkey.error.id");
                    try {
                        String[] parts = id.split("-");
                        long ms = Long.parseUnsignedLong(parts[0]), seq = Long.parseUnsignedLong(parts[1]);
                        if (ms == 0 && seq == 0) throw invalid("newkey.error.id");
                    } catch (NumberFormatException e) { throw invalid("newkey.error.id"); }
                }
                args.add(id);
            }
            Set<String> names = new HashSet<>();
            for (Row row : rows) {
                if (row.first() == null || row.first().isEmpty()) throw invalid("newkey.error.member");
                if (type != RedisKeyInfo.KeyType.LIST && !names.add(row.first())) throw invalid("newkey.error.duplicate");
                if (type == RedisKeyInfo.KeyType.ZSET) {
                    try {
                        if (!Double.isFinite(Double.parseDouble(row.second()))) throw new NumberFormatException();
                    } catch (RuntimeException e) { throw invalid("newkey.error.score"); }
                    args.add(row.second().trim());
                }
                args.add(row.first());
                if (type == RedisKeyInfo.KeyType.HASH || type == RedisKeyInfo.KeyType.STREAM)
                    args.add(Objects.requireNonNullElse(row.second(), ""));
            }
        }
        return new NewRedisKey(key, args);
    }
    private static IllegalArgumentException invalid(String key) { return new IllegalArgumentException(I18N.get(key)); }
}
