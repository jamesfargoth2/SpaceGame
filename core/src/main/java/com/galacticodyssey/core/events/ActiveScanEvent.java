package com.galacticodyssey.core.events;

public final class ActiveScanEvent {
    public final float x, y, z;
    public final float range;
    public final float pingMultiplier;

    public ActiveScanEvent(float x, float y, float z, float range, float pingMultiplier) {
        this.x = x; this.y = y; this.z = z;
        this.range = range;
        this.pingMultiplier = pingMultiplier;
    }
}
