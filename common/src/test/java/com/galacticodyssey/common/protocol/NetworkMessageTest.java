package com.galacticodyssey.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkMessageTest {

    @Test
    void loginRequestHasPublicFields() {
        LoginRequest msg = new LoginRequest();
        msg.username = "player1";
        msg.clientVersion = "0.1.0";
        assertEquals("player1", msg.username);
        assertEquals("0.1.0", msg.clientVersion);
    }

    @Test
    void loginResponseHasPublicFields() {
        LoginResponse msg = new LoginResponse();
        msg.sessionToken = "abc-123";
        msg.zoneServerHost = "localhost";
        msg.zoneServerTcpPort = 7100;
        msg.zoneServerUdpPort = 7101;
        msg.playerId = java.util.UUID.randomUUID();
        assertNotNull(msg.playerId);
    }

    @Test
    void inputPacketBundlesThreeInputs() {
        InputPacket packet = new InputPacket();
        packet.inputs = new PlayerInput[3];
        packet.inputs[0] = new PlayerInput();
        packet.inputs[0].moveForward = 1.0f;
        packet.inputs[0].sequenceNumber = 42;
        assertEquals(3, packet.inputs.length);
        assertEquals(1.0f, packet.inputs[0].moveForward, 1e-5f);
        assertEquals(42, packet.inputs[0].sequenceNumber);
    }

    @Test
    void entityStateUpdateHasDirtyMask() {
        EntityStateUpdate update = new EntityStateUpdate();
        update.networkId = 7;
        update.serverTick = 100;
        update.dirtyMask = 0b0011;
        update.payload = new byte[]{1, 2, 3};
        assertEquals(7, update.networkId);
        assertEquals(0b0011, update.dirtyMask);
    }

    @Test
    void entityBatchUpdateWrapsMultipleUpdates() {
        EntityBatchUpdate batch = new EntityBatchUpdate();
        batch.serverTick = 200;
        batch.lastProcessedInputSequence = 50;
        batch.updates = new EntityStateUpdate[2];
        batch.updates[0] = new EntityStateUpdate();
        batch.updates[1] = new EntityStateUpdate();
        assertEquals(2, batch.updates.length);
        assertEquals(50, batch.lastProcessedInputSequence);
    }

    @Test
    void disconnectHasReasonEnum() {
        Disconnect msg = new Disconnect();
        msg.reason = Disconnect.Reason.TIMEOUT;
        assertEquals(Disconnect.Reason.TIMEOUT, msg.reason);
    }

    @Test
    void entitySpawnMessageHasFullPayload() {
        EntitySpawnMessage msg = new EntitySpawnMessage();
        msg.networkId = 1;
        msg.entityType = "ship";
        msg.persistenceId = java.util.UUID.randomUUID();
        msg.componentData = new byte[]{10, 20, 30};
        assertEquals("ship", msg.entityType);
        assertNotNull(msg.persistenceId);
    }
}
