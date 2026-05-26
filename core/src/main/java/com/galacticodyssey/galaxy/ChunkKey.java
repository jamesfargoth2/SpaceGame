package com.galacticodyssey.galaxy;

import java.util.Objects;

public final class ChunkKey {

    public final int cx;
    public final int cy;

    public ChunkKey(int cx, int cy) {
        this.cx = cx;
        this.cy = cy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkKey)) return false;
        ChunkKey that = (ChunkKey) o;
        return cx == that.cx && cy == that.cy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cx, cy);
    }

    @Override
    public String toString() {
        return "ChunkKey(" + cx + ", " + cy + ")";
    }
}
