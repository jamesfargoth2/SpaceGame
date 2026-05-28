package com.galacticodyssey.combat.fleet.events;

import com.badlogic.ashley.core.Entity;

public final class EscapePodLaunchedEvent {
    public final Entity pod;
    public final Entity sourceShip;

    public EscapePodLaunchedEvent(Entity pod, Entity sourceShip) {
        this.pod = pod;
        this.sourceShip = sourceShip;
    }
}
