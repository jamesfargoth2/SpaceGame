package com.galacticodyssey.server.replication;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReplicationStateTest {

    @Test
    void newStateIsUninitialized() {
        ReplicationState state = new ReplicationState(42);
        assertEquals(42, state.getNetworkId());
        assertFalse(state.hasBeenSent());
        assertEquals(InterestTier.NONE, state.getCurrentTier());
    }

    @Test
    void markSentUpdatesState() {
        ReplicationState state = new ReplicationState(1);
        state.setCurrentTier(InterestTier.NEAR);
        state.markSent(100);
        assertTrue(state.hasBeenSent());
        assertEquals(100, state.getLastSentTick());
    }

    @Test
    void tierChangeDetection() {
        ReplicationState state = new ReplicationState(1);
        state.setCurrentTier(InterestTier.NEAR);
        assertFalse(state.tierChangedFrom(InterestTier.NEAR));
        assertTrue(state.tierChangedFrom(InterestTier.MID));
    }

    @Test
    void lastSentSnapshotStorage() {
        ReplicationState state = new ReplicationState(1);
        byte[] snapshot = new byte[]{1, 2, 3};
        state.setLastSentSnapshot(snapshot);
        assertArrayEquals(new byte[]{1, 2, 3}, state.getLastSentSnapshot());
    }
}
