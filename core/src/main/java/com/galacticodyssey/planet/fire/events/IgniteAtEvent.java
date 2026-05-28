package com.galacticodyssey.planet.fire.events;

/** Request to ignite the fuel grid near a world (local-scene) position. */
public final class IgniteAtEvent {
    public final float worldX;
    public final float worldZ;
    public final float strength; // 0..1+ ignition progress contribution
    public IgniteAtEvent(float worldX, float worldZ, float strength) {
        this.worldX = worldX; this.worldZ = worldZ; this.strength = strength;
    }
}
