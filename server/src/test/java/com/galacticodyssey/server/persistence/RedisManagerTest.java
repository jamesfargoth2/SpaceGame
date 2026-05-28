package com.galacticodyssey.server.persistence;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RedisManagerTest {
    @Test
    void configHasDefaults() {
        RedisManager.Config config = new RedisManager.Config();
        assertEquals("localhost", config.host);
        assertEquals(6379, config.port);
        assertEquals(10, config.maxPoolSize);
    }

    @Test
    void createManagerDoesNotThrow() {
        RedisManager manager = new RedisManager(new RedisManager.Config());
        assertNotNull(manager);
        assertFalse(manager.isRunning());
    }

    @Test
    void shutdownWithoutInitIsNoOp() {
        RedisManager manager = new RedisManager(new RedisManager.Config());
        assertDoesNotThrow(manager::shutdown);
    }

    @Test
    void sessionKeyFormat() {
        assertEquals("session:tok-abc", RedisManager.sessionKey("tok-abc"));
    }

    @Test
    void zoneLoadKeyFormat() {
        assertEquals("zone:zone-1:load", RedisManager.zoneLoadKey("zone-1"));
    }

    @Test
    void zoneBorderChannelFormat() {
        assertEquals("zone.border.zone-1", RedisManager.zoneBorderChannel("zone-1"));
    }

    @Test
    void handoffPrepareChannelFormat() {
        assertEquals("zone.handoff.prepare.zone-1", RedisManager.handoffPrepareChannel("zone-1"));
    }

    @Test
    void handoffAckChannelFormat() {
        assertEquals("zone.handoff.ack.zone-1", RedisManager.handoffAckChannel("zone-1"));
    }
}
