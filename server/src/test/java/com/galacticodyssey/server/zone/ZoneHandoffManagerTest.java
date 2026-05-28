package com.galacticodyssey.server.zone;

import com.galacticodyssey.common.protocol.HandoffPrepare;
import com.galacticodyssey.common.protocol.HandoffTransferAck;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneHandoffManagerTest {

    @Test
    void initiateHandoffCreatesPrepareMessage() {
        List<HandoffPrepare> published = new ArrayList<>();
        ZoneHandoffManager manager = new ZoneHandoffManager(published::add);

        UUID sourceZone = UUID.randomUUID();
        UUID targetZone = UUID.randomUUID();
        byte[] entityData = new byte[]{1, 2, 3};

        manager.initiateHandoff(42, sourceZone, targetZone, entityData, "tok-player");

        assertEquals(1, published.size());
        HandoffPrepare prep = published.get(0);
        assertEquals(42, prep.entityNetworkId);
        assertEquals(sourceZone, prep.sourceZoneId);
        assertEquals(targetZone, prep.targetZoneId);
        assertArrayEquals(entityData, prep.serializedEntityData);
        assertEquals("tok-player", prep.playerSessionToken);
    }

    @Test
    void tracksPendingHandoff() {
        ZoneHandoffManager manager = new ZoneHandoffManager(p -> {});
        UUID sourceZone = UUID.randomUUID();
        UUID targetZone = UUID.randomUUID();

        manager.initiateHandoff(42, sourceZone, targetZone, new byte[0], "tok");
        assertTrue(manager.isPending(42));
        assertFalse(manager.isPending(99));
    }

    @Test
    void acknowledgeHandoffCompletesIt() {
        ZoneHandoffManager manager = new ZoneHandoffManager(p -> {});
        UUID sourceZone = UUID.randomUUID();
        UUID targetZone = UUID.randomUUID();

        manager.initiateHandoff(42, sourceZone, targetZone, new byte[0], "tok");

        HandoffTransferAck ack = new HandoffTransferAck();
        ack.entityNetworkId = 42;
        ack.sourceZoneId = sourceZone;
        ack.targetZoneId = targetZone;
        ack.success = true;

        ZoneHandoffManager.HandoffResult result = manager.acknowledgeHandoff(ack);
        assertNotNull(result);
        assertTrue(result.success());
        assertEquals("tok", result.playerSessionToken());
        assertFalse(manager.isPending(42));
    }

    @Test
    void acknowledgeUnknownHandoffReturnsNull() {
        ZoneHandoffManager manager = new ZoneHandoffManager(p -> {});

        HandoffTransferAck ack = new HandoffTransferAck();
        ack.entityNetworkId = 99;
        ack.success = true;

        assertNull(manager.acknowledgeHandoff(ack));
    }
}
