package com.galacticodyssey.mech.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.mech.components.MechPhysicsComponent.LocomotionMode;

/**
 * Published when a mech switches between walking and jetpack locomotion
 * (typically triggered by entering or leaving a gravity field).
 */
public final class MechLocomotionModeChangeEvent {

    public final Entity entity;
    public final LocomotionMode newMode;

    public MechLocomotionModeChangeEvent(Entity entity, LocomotionMode newMode) {
        this.entity = entity;
        this.newMode = newMode;
    }
}
