package com.galacticodyssey.core.events;

public final class OriginRebasedEvent {
    public final float deltaX;
    public final float deltaY;
    public final float deltaZ;

    public OriginRebasedEvent(float deltaX, float deltaY, float deltaZ) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.deltaZ = deltaZ;
    }
}
