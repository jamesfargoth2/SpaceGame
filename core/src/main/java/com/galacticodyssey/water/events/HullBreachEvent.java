package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;

public class HullBreachEvent {
    public final Entity entity;
    public final String compartmentId;
    public final float breachArea;
    public final float breachDepth;

    public HullBreachEvent(Entity entity, String compartmentId,
                           float breachArea, float breachDepth) {
        this.entity = entity;
        this.compartmentId = compartmentId;
        this.breachArea = breachArea;
        this.breachDepth = breachDepth;
    }
}
