package com.galacticodyssey.common.serialization;

public class DirtyTracker {
    private final int fieldCount;
    private long mask;

    public DirtyTracker(int fieldCount) {
        if (fieldCount < 1 || fieldCount > 64) {
            throw new IllegalArgumentException("fieldCount must be 1–64, got " + fieldCount);
        }
        this.fieldCount = fieldCount;
    }

    public void markDirty(int fieldIndex) {
        mask |= (1L << fieldIndex);
    }

    public void markAllDirty() {
        mask = fieldCount == 64 ? -1L : (1L << fieldCount) - 1;
    }

    public void clear() {
        mask = 0L;
    }

    public boolean isDirty() {
        return mask != 0L;
    }

    public boolean isBitDirty(int fieldIndex) {
        return (mask & (1L << fieldIndex)) != 0;
    }

    public long getDirtyMask() {
        return mask;
    }

    public int getFieldCount() {
        return fieldCount;
    }
}
