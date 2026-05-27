package com.galacticodyssey.player.events;

import com.badlogic.ashley.core.Entity;

public final class PerkAvailableEvent {
    public final Entity player;

    public PerkAvailableEvent(Entity player) {
        this.player = player;
    }
}
