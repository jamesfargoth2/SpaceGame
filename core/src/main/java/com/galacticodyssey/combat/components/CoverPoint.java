package com.galacticodyssey.combat.components;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.CoverQuality;

public class CoverPoint {
    public final Vector3 position = new Vector3();
    public final Vector3 normal = new Vector3();
    public CoverQuality quality = CoverQuality.HALF;
    public boolean occupied;

    public CoverPoint() {}

    public CoverPoint(Vector3 position, Vector3 normal, CoverQuality quality) {
        this.position.set(position);
        this.normal.set(normal);
        this.quality = quality;
    }
}
