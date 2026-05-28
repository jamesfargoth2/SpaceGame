package com.galacticodyssey.common.serialization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirtyTrackerTest {

    @Test
    void initiallyAllClean() {
        DirtyTracker tracker = new DirtyTracker(8);
        assertEquals(0L, tracker.getDirtyMask());
        assertFalse(tracker.isDirty());
    }

    @Test
    void markDirtySetsCorrectBit() {
        DirtyTracker tracker = new DirtyTracker(8);
        tracker.markDirty(0);
        tracker.markDirty(3);
        assertEquals(0b1001L, tracker.getDirtyMask());
        assertTrue(tracker.isDirty());
    }

    @Test
    void clearResetsAllBits() {
        DirtyTracker tracker = new DirtyTracker(8);
        tracker.markDirty(0);
        tracker.markDirty(7);
        tracker.clear();
        assertEquals(0L, tracker.getDirtyMask());
        assertFalse(tracker.isDirty());
    }

    @Test
    void isBitDirtyChecksSpecificBit() {
        DirtyTracker tracker = new DirtyTracker(8);
        tracker.markDirty(5);
        assertTrue(tracker.isBitDirty(5));
        assertFalse(tracker.isBitDirty(4));
    }

    @Test
    void markAllDirtySetsAllBitsUpToFieldCount() {
        DirtyTracker tracker = new DirtyTracker(4);
        tracker.markAllDirty();
        assertEquals(0b1111L, tracker.getDirtyMask());
    }

    @Test
    void supports64Fields() {
        DirtyTracker tracker = new DirtyTracker(64);
        tracker.markDirty(63);
        assertTrue(tracker.isBitDirty(63));
        assertFalse(tracker.isBitDirty(62));
    }
}
