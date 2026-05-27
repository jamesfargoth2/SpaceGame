package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public class StabilityWarningEvent {
    public final Entity entity;
    public final float freeSurfaceGzLoss;
    public final float currentRollDeg;
    public final int severity; // 0=caution, 1=warning, 2=critical

    public StabilityWarningEvent(Entity entity, float freeSurfaceGzLoss,
                                  float currentRollDeg, int severity) {
        this.entity = entity;
        this.freeSurfaceGzLoss = freeSurfaceGzLoss;
        this.currentRollDeg = currentRollDeg;
        this.severity = severity;
    }
}
