package com.galacticodyssey.ship.events;

import com.badlogic.ashley.core.Entity;

public final class EngineModeChangeEvent {

    public final Entity shipEntity;
    public final String modeName;

    public EngineModeChangeEvent(Entity shipEntity, String modeName) {
        this.shipEntity = shipEntity;
        this.modeName = modeName;
    }
}
