package com.galacticodyssey.player.events;

import com.badlogic.ashley.core.Entity;

public final class PerkSelectedEvent {
    public final Entity player;
    public final String perkId;

    public PerkSelectedEvent(Entity player, String perkId) {
        this.player = player;
        this.perkId = perkId;
    }
}
