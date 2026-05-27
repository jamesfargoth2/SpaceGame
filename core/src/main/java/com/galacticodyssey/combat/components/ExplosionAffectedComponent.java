package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;

public class ExplosionAffectedComponent implements Component, Pool.Poolable {
    public float projectedArea = 1f;
    public float armorFactor = 1f;
    public float thermalAbsorptivity = 0.5f;
    public float empHardeningFactor = 0f;

    @Override
    public void reset() {
        projectedArea = 1f;
        armorFactor = 1f;
        thermalAbsorptivity = 0.5f;
        empHardeningFactor = 0f;
    }
}
