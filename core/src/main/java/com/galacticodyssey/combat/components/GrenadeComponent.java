package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.galacticodyssey.combat.CombatEnums.FuseType;

public class GrenadeComponent implements Component {
    public static final ComponentMapper<GrenadeComponent> MAPPER =
            ComponentMapper.getFor(GrenadeComponent.class);

    public String grenadeTypeId;
    public FuseType fuseType = FuseType.TIMED;
    public float fuseTimer;
    public float fuseDuration;
    public float cookTime;
    public boolean cookable;
    public float proximityRadius;
    public boolean detonated;
    public float bounceRestitution = 0.3f;
    public int maxBounces = 5;
    public int bounceCount;

    public float damage;
    public float blastRadius;
    public float blastFraction = 0.5f;
    public float thermalFraction = 0.1f;
    public float fragmentFraction = 0.4f;
    public boolean isDirectional;
}
