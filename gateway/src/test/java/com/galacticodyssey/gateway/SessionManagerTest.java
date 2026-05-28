package com.galacticodyssey.gateway;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @Test
    void createSessionGeneratesToken() {
        FakeRedisAdapter redis = new FakeRedisAdapter();
        SessionManager manager = new SessionManager(redis, 300);
        UUID playerId = UUID.randomUUID();
        String token = manager.createSession(playerId, "zone-1");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void validateSessionReturnsData() {
        FakeRedisAdapter redis = new FakeRedisAdapter();
        SessionManager manager = new SessionManager(redis, 300);
        UUID playerId = UUID.randomUUID();
        String token = manager.createSession(playerId, "zone-1");
        SessionManager.SessionData data = manager.validateSession(token);
        assertNotNull(data);
        assertEquals(playerId, data.playerId());
        assertEquals("zone-1", data.zoneId());
    }

    @Test
    void invalidTokenReturnsNull() {
        FakeRedisAdapter redis = new FakeRedisAdapter();
        SessionManager manager = new SessionManager(redis, 300);
        assertNull(manager.validateSession("bogus-token"));
    }

    @Test
    void refreshSessionUpdatesExpiry() {
        FakeRedisAdapter redis = new FakeRedisAdapter();
        SessionManager manager = new SessionManager(redis, 300);
        UUID playerId = UUID.randomUUID();
        String token = manager.createSession(playerId, "zone-1");
        assertTrue(manager.refreshSession(token));
        assertFalse(manager.refreshSession("bogus"));
    }

    @Test
    void destroySessionRemovesIt() {
        FakeRedisAdapter redis = new FakeRedisAdapter();
        SessionManager manager = new SessionManager(redis, 300);
        UUID playerId = UUID.randomUUID();
        String token = manager.createSession(playerId, "zone-1");
        manager.destroySession(token);
        assertNull(manager.validateSession(token));
    }

    /** In-memory Redis adapter for testing. Must be public for use by other test classes. */
    public static class FakeRedisAdapter implements SessionManager.RedisAdapter {
        private final Map<String, Map<String, String>> hashes = new HashMap<>();

        @Override public void hset(String key, Map<String, String> fields, int ttlSeconds) {
            hashes.put(key, new HashMap<>(fields));
        }
        @Override public Map<String, String> hgetAll(String key) {
            return hashes.getOrDefault(key, Map.of());
        }
        @Override public boolean exists(String key) { return hashes.containsKey(key); }
        @Override public void expire(String key, int seconds) {}
        @Override public void del(String key) { hashes.remove(key); }
    }
}
