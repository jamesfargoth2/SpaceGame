package com.galacticodyssey.gateway;

import java.util.Map;
import java.util.UUID;

public class SessionManager {

    public record SessionData(UUID playerId, String zoneId) {}

    public interface RedisAdapter {
        void hset(String key, Map<String, String> fields, int ttlSeconds);
        Map<String, String> hgetAll(String key);
        boolean exists(String key);
        void expire(String key, int seconds);
        void del(String key);
    }

    private final RedisAdapter redis;
    private final int sessionTtlSeconds;

    public SessionManager(RedisAdapter redis, int sessionTtlSeconds) {
        this.redis = redis;
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    public String createSession(UUID playerId, String zoneId) {
        String token = UUID.randomUUID().toString();
        String key = "session:" + token;
        redis.hset(key, Map.of("playerId", playerId.toString(), "zoneId", zoneId), sessionTtlSeconds);
        return token;
    }

    public SessionData validateSession(String token) {
        String key = "session:" + token;
        Map<String, String> fields = redis.hgetAll(key);
        if (fields == null || fields.isEmpty() || !fields.containsKey("playerId")) return null;
        return new SessionData(UUID.fromString(fields.get("playerId")), fields.get("zoneId"));
    }

    public boolean refreshSession(String token) {
        String key = "session:" + token;
        if (!redis.exists(key)) return false;
        redis.expire(key, sessionTtlSeconds);
        return true;
    }

    public void destroySession(String token) {
        redis.del("session:" + token);
    }
}
