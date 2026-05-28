package com.galacticodyssey.networking.prediction;

import java.util.ArrayList;
import java.util.List;

public class InputBuffer {
    public static final int CAPACITY = 128;
    private final TimestampedInput[] buffer = new TimestampedInput[CAPACITY];
    private int head;
    private int count;

    public void add(TimestampedInput entry) {
        buffer[head] = entry;
        head = (head + 1) % CAPACITY;
        if (count < CAPACITY) count++;
    }

    public int size() {
        return count;
    }

    public TimestampedInput get(int sequenceNumber) {
        for (int i = 0; i < count; i++) {
            int idx = (head - 1 - i + CAPACITY) % CAPACITY;
            if (buffer[idx] != null && buffer[idx].sequenceNumber == sequenceNumber) {
                return buffer[idx];
            }
        }
        return null;
    }

    public void discardUpTo(int acknowledgedSequence) {
        int removed = 0;
        for (int i = 0; i < count; i++) {
            int idx = (head - count + i + CAPACITY) % CAPACITY;
            if (buffer[idx] != null && buffer[idx].sequenceNumber <= acknowledgedSequence) {
                buffer[idx] = null;
                removed++;
            } else {
                break;
            }
        }
        count -= removed;
    }

    public List<TimestampedInput> getUnacknowledged(int afterSequence) {
        List<TimestampedInput> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = (head - count + i + CAPACITY) % CAPACITY;
            if (buffer[idx] != null && buffer[idx].sequenceNumber > afterSequence) {
                result.add(buffer[idx]);
            }
        }
        return result;
    }

    public void clear() {
        for (int i = 0; i < CAPACITY; i++) buffer[i] = null;
        head = 0;
        count = 0;
    }
}
