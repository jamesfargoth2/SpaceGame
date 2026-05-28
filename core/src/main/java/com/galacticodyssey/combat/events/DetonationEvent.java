package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;

public class DetonationEvent {
    public final Entity owner;
    public final Vector3 position;
    public final float damage;
    public final DamageType damageType;
    public final float areaOfEffect;
    public final float blastFraction;
    public final float thermalFraction;
    public final float fragmentFraction;
    public final boolean isDirectional;

    public DetonationEvent(Entity owner, Vector3 position, float damage,
                           DamageType damageType, float areaOfEffect) {
        this(owner, position, damage, damageType, areaOfEffect, 0.4f, 0.3f, 0.3f, false);
    }

    public DetonationEvent(Entity owner, Vector3 position, float damage,
                           DamageType damageType, float areaOfEffect,
                           float blastFraction, float thermalFraction,
                           float fragmentFraction, boolean isDirectional) {
        this.owner = owner;
        this.position = new Vector3(position);
        this.damage = damage;
        this.damageType = damageType;
        this.areaOfEffect = areaOfEffect;
        this.blastFraction = blastFraction;
        this.thermalFraction = thermalFraction;
        this.fragmentFraction = fragmentFraction;
        this.isDirectional = isDirectional;
    }
}
