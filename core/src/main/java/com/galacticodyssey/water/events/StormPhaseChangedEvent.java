package com.galacticodyssey.water.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.water.WeatherPhase;

public final class StormPhaseChangedEvent {
    public final Entity stormEntity;
    public final WeatherPhase oldPhase;
    public final WeatherPhase newPhase;

    public StormPhaseChangedEvent(Entity stormEntity, WeatherPhase oldPhase,
                                   WeatherPhase newPhase) {
        this.stormEntity = stormEntity;
        this.oldPhase = oldPhase;
        this.newPhase = newPhase;
    }
}
