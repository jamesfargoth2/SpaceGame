package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.Component;

/** Present while an entity is frozen solid. Other systems read these for penalties. */
public class FrozenComponent implements Component {
    public float frozenFraction = 1f;       // 0..1
    public float speedMultiplier = 1f;       // from material.frozenSpeedMultiplier
    public boolean brittle = false;          // from material.brittleWhenFrozen
}
