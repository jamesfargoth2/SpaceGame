package com.galacticodyssey.networking.interpolation;

public class SnapshotBuffer {
    public static final int CAPACITY = 4;
    private final EntitySnapshot[] buffer = new EntitySnapshot[CAPACITY];
    private int count;
    private int newestTick = -1;

    public void add(EntitySnapshot snapshot) {
        if (snapshot.tick <= newestTick) return;
        newestTick = snapshot.tick;

        if (count < CAPACITY) {
            buffer[count++] = snapshot;
        } else {
            System.arraycopy(buffer, 1, buffer, 0, CAPACITY - 1);
            buffer[CAPACITY - 1] = snapshot;
        }
    }

    public int size() {
        return count;
    }

    public int getNewestTick() {
        return newestTick;
    }

    public EntitySnapshot get(int index) {
        if (index < 0 || index >= count) return null;
        return buffer[index];
    }

    public EntitySnapshot[] findBracketing(int targetTick) {
        if (count < 2) return null;
        for (int i = 0; i < count - 1; i++) {
            if (buffer[i].tick <= targetTick && buffer[i + 1].tick >= targetTick) {
                return new EntitySnapshot[]{buffer[i], buffer[i + 1]};
            }
        }
        return null;
    }

    public EntitySnapshot getNewest() {
        if (count == 0) return null;
        return buffer[count - 1];
    }

    public void clear() {
        for (int i = 0; i < CAPACITY; i++) buffer[i] = null;
        count = 0;
        newestTick = -1;
    }
}
