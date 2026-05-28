package com.galacticodyssey.server.replication;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterestManagerTest {

    @Test
    void entityAtOriginIsNear() {
        InterestManager manager = new InterestManager();
        InterestTier tier = manager.computeTier(0, 0, 0, 100, 0, 0);
        assertEquals(InterestTier.NEAR, tier);
    }

    @Test
    void entityAt1000mIsMid() {
        InterestManager manager = new InterestManager();
        InterestTier tier = manager.computeTier(0, 0, 0, 1000, 0, 0);
        assertEquals(InterestTier.MID, tier);
    }

    @Test
    void entityAt5000mIsFar() {
        InterestManager manager = new InterestManager();
        InterestTier tier = manager.computeTier(0, 0, 0, 5000, 0, 0);
        assertEquals(InterestTier.FAR, tier);
    }

    @Test
    void entityBeyond10kmIsNone() {
        InterestManager manager = new InterestManager();
        InterestTier tier = manager.computeTier(0, 0, 0, 15000, 0, 0);
        assertEquals(InterestTier.NONE, tier);
    }

    @Test
    void tierBoundariesAreInclusive() {
        InterestManager manager = new InterestManager();
        assertEquals(InterestTier.NEAR, manager.computeTier(0, 0, 0, 500, 0, 0));
        assertEquals(InterestTier.MID, manager.computeTier(0, 0, 0, 500.01, 0, 0));
        assertEquals(InterestTier.MID, manager.computeTier(0, 0, 0, 2000, 0, 0));
        assertEquals(InterestTier.FAR, manager.computeTier(0, 0, 0, 2000.01, 0, 0));
        assertEquals(InterestTier.FAR, manager.computeTier(0, 0, 0, 10000, 0, 0));
        assertEquals(InterestTier.NONE, manager.computeTier(0, 0, 0, 10000.01, 0, 0));
    }

    @Test
    void shouldSendThisTickNearEveryTick() {
        InterestManager manager = new InterestManager();
        for (int tick = 0; tick < 20; tick++) {
            assertTrue(manager.shouldSendThisTick(InterestTier.NEAR, tick));
        }
    }

    @Test
    void shouldSendThisTickMidEvery4th() {
        InterestManager manager = new InterestManager();
        assertTrue(manager.shouldSendThisTick(InterestTier.MID, 0));
        assertFalse(manager.shouldSendThisTick(InterestTier.MID, 1));
        assertFalse(manager.shouldSendThisTick(InterestTier.MID, 2));
        assertFalse(manager.shouldSendThisTick(InterestTier.MID, 3));
        assertTrue(manager.shouldSendThisTick(InterestTier.MID, 4));
    }

    @Test
    void shouldSendThisTickFarEvery10th() {
        InterestManager manager = new InterestManager();
        assertTrue(manager.shouldSendThisTick(InterestTier.FAR, 0));
        assertFalse(manager.shouldSendThisTick(InterestTier.FAR, 1));
        assertTrue(manager.shouldSendThisTick(InterestTier.FAR, 10));
    }

    @Test
    void shouldNeverSendForNone() {
        InterestManager manager = new InterestManager();
        for (int tick = 0; tick < 100; tick++) {
            assertFalse(manager.shouldSendThisTick(InterestTier.NONE, tick));
        }
    }
}
