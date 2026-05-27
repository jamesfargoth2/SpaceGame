package com.galacticodyssey.combat;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;

public class ExplosionData {
    public final Vector3 origin = new Vector3();
    public float totalEnergy;
    public float blastFraction = 0.4f;
    public float thermalFraction = 0.3f;
    public float fragmentFraction = 0.3f;
    public float empRadius;
    public boolean isDirectional;
    public final Vector3 directionNormal = new Vector3();
    public float coneHalfAngle;
    public Entity owner;
    public DamageType damageType = DamageType.EXPLOSIVE;
}
