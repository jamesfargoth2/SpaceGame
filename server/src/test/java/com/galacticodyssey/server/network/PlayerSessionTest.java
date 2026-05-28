package com.galacticodyssey.server.network;

import com.galacticodyssey.common.protocol.PlayerInput;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSessionTest {

    @Test
    void sessionStoresPlayerInfo() {
        UUID playerId = UUID.randomUUID();
        PlayerSession session = new PlayerSession(7, playerId, "token-abc");

        assertEquals(7, session.getConnectionId());
        assertEquals(playerId, session.getPlayerId());
        assertEquals("token-abc", session.getSessionToken());
    }

    @Test
    void inputQueueDrainsInOrder() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");

        PlayerInput i1 = new PlayerInput();
        i1.sequenceNumber = 1;
        PlayerInput i2 = new PlayerInput();
        i2.sequenceNumber = 2;

        session.enqueueInput(i1);
        session.enqueueInput(i2);

        var drained = session.drainInputs();
        assertEquals(2, drained.size());
        assertEquals(1, drained.get(0).sequenceNumber);
        assertEquals(2, drained.get(1).sequenceNumber);
    }

    @Test
    void drainInputsClearsQueue() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        session.enqueueInput(new PlayerInput());
        session.drainInputs();

        var second = session.drainInputs();
        assertTrue(second.isEmpty());
    }

    @Test
    void lastProcessedInputSequenceTracking() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        assertEquals(-1, session.getLastProcessedInputSequence());

        session.setLastProcessedInputSequence(50);
        assertEquals(50, session.getLastProcessedInputSequence());
    }

    @Test
    void networkIdAssignment() {
        PlayerSession session = new PlayerSession(1, UUID.randomUUID(), "tok");
        assertEquals(-1, session.getPlayerNetworkId());

        session.setPlayerNetworkId(10);
        assertEquals(10, session.getPlayerNetworkId());
    }
}
