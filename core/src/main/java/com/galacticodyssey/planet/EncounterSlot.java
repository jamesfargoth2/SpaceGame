package com.galacticodyssey.planet;

public final class EncounterSlot {
    public final int roomId;
    public final int x;
    public final int y;
    public final int difficulty;

    public EncounterSlot(int roomId, int x, int y, int difficulty) {
        this.roomId = roomId;
        this.x = x;
        this.y = y;
        this.difficulty = difficulty;
    }
}
