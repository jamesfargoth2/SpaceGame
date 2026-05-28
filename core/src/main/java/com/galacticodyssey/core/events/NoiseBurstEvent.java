package com.galacticodyssey.core.events;

public final class NoiseBurstEvent {
    public final float x, y, z;
    public final float radius;
    public final float intensity; // 0–1

    public NoiseBurstEvent(float x, float y, float z, float radius, float intensity) {
        this.x = x; this.y = y; this.z = z;
        this.radius = radius;
        this.intensity = intensity;
    }
}
