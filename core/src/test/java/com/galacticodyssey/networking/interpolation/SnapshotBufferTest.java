package com.galacticodyssey.networking.interpolation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotBufferTest {

    private EntitySnapshot snap(int tick, float x) {
        return new EntitySnapshot(tick, x, 0, 0, 0, 0, 0, 1, 0, 0, 0);
    }

    @Test
    void addAndRetrieve() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(1, 10));
        buf.add(snap(2, 20));
        assertEquals(2, buf.size());
        assertEquals(1, buf.get(0).tick);
        assertEquals(2, buf.get(1).tick);
    }

    @Test
    void dropsOutOfOrderPackets() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(5, 50));
        buf.add(snap(3, 30));
        assertEquals(1, buf.size());
        assertEquals(5, buf.getNewestTick());
    }

    @Test
    void evictsOldestBeyondCapacity() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(1, 10));
        buf.add(snap(2, 20));
        buf.add(snap(3, 30));
        buf.add(snap(4, 40));
        assertEquals(4, buf.size());
        buf.add(snap(5, 50));
        assertEquals(4, buf.size());
        assertEquals(2, buf.get(0).tick);
        assertEquals(5, buf.get(3).tick);
    }

    @Test
    void findBracketingReturnsCorrectPair() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(0, 0));
        buf.add(snap(5, 50));
        buf.add(snap(10, 100));
        EntitySnapshot[] pair = buf.findBracketing(3);
        assertNotNull(pair);
        assertEquals(0, pair[0].tick);
        assertEquals(5, pair[1].tick);
    }

    @Test
    void findBracketingReturnsNullWithTooFewSnapshots() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(5, 50));
        assertNull(buf.findBracketing(3));
    }

    @Test
    void findBracketingReturnsNullForFutureTick() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(0, 0));
        buf.add(snap(5, 50));
        assertNull(buf.findBracketing(10));
    }

    @Test
    void clearResetsState() {
        SnapshotBuffer buf = new SnapshotBuffer();
        buf.add(snap(1, 10));
        buf.add(snap(2, 20));
        buf.clear();
        assertEquals(0, buf.size());
        assertEquals(-1, buf.getNewestTick());
    }
}
