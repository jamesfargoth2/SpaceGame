package com.galacticodyssey.networking.prediction;

import com.galacticodyssey.common.protocol.PlayerInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InputBufferTest {

    private TimestampedInput makeEntry(int seq) {
        PlayerInput input = new PlayerInput();
        input.sequenceNumber = seq;
        PredictedState state = new PredictedState(seq, 0, 0, 0, 0, 0, 1, 0, 0, 0);
        return new TimestampedInput(seq, input, state);
    }

    @Test
    void addAndRetrieveBySequence() {
        InputBuffer buf = new InputBuffer();
        buf.add(makeEntry(1));
        buf.add(makeEntry(2));
        buf.add(makeEntry(3));
        assertEquals(3, buf.size());
        assertNotNull(buf.get(2));
        assertEquals(2, buf.get(2).sequenceNumber);
    }

    @Test
    void getMissingReturnsNull() {
        InputBuffer buf = new InputBuffer();
        buf.add(makeEntry(1));
        assertNull(buf.get(99));
    }

    @Test
    void discardRemovesAcknowledgedEntries() {
        InputBuffer buf = new InputBuffer();
        buf.add(makeEntry(1));
        buf.add(makeEntry(2));
        buf.add(makeEntry(3));
        buf.discardUpTo(2);
        assertEquals(1, buf.size());
        assertNull(buf.get(1));
        assertNull(buf.get(2));
        assertNotNull(buf.get(3));
    }

    @Test
    void getUnacknowledgedReturnsOnlyNewer() {
        InputBuffer buf = new InputBuffer();
        buf.add(makeEntry(1));
        buf.add(makeEntry(2));
        buf.add(makeEntry(3));
        buf.add(makeEntry(4));
        var unacked = buf.getUnacknowledged(2);
        assertEquals(2, unacked.size());
        assertEquals(3, unacked.get(0).sequenceNumber);
        assertEquals(4, unacked.get(1).sequenceNumber);
    }

    @Test
    void wrapsAroundAtCapacity() {
        InputBuffer buf = new InputBuffer();
        for (int i = 0; i < InputBuffer.CAPACITY + 10; i++) {
            buf.add(makeEntry(i));
        }
        assertEquals(InputBuffer.CAPACITY, buf.size());
        assertNull(buf.get(0));
        assertNotNull(buf.get(InputBuffer.CAPACITY + 9));
    }

    @Test
    void clearResetsBuffer() {
        InputBuffer buf = new InputBuffer();
        buf.add(makeEntry(1));
        buf.add(makeEntry(2));
        buf.clear();
        assertEquals(0, buf.size());
        assertNull(buf.get(1));
    }
}
