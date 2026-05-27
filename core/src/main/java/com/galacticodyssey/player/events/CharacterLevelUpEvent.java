package com.galacticodyssey.player.events;

import com.badlogic.ashley.core.Entity;

public final class CharacterLevelUpEvent {
    public final Entity player;
    public final int    newLevel;
    public final int    pointsAwarded;

    public CharacterLevelUpEvent(Entity player, int newLevel, int pointsAwarded) {
        this.player        = player;
        this.newLevel      = newLevel;
        this.pointsAwarded = pointsAwarded;
    }
}
