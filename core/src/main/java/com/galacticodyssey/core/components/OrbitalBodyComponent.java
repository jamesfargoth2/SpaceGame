package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.galaxy.KeplerOrbit;
import com.galacticodyssey.galaxy.OrbitalSlot;

public class OrbitalBodyComponent implements Component, Pool.Poolable {

    public OrbitalSlot orbitalSlot;
    public Entity parentBody;
    public float bodyRadius;
    public float soiRadius;
    public KeplerOrbit cachedOrbit;
    public CelestialBodyType bodyType = CelestialBodyType.PLANET;

    @Override
    public void reset() {
        orbitalSlot = null;
        parentBody = null;
        bodyRadius = 0f;
        soiRadius = 0f;
        cachedOrbit = null;
        bodyType = CelestialBodyType.PLANET;
    }
}
