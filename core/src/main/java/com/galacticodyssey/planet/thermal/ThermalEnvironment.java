package com.galacticodyssey.planet.thermal;

import com.badlogic.gdx.math.Vector3;

/** Resolves the surrounding conditions an object exchanges heat with. */
public interface ThermalEnvironment {
    /** Ambient temperature (K) the object trends toward at the given local-scene position. */
    float ambientTemp(Vector3 localPos);

    /** Atmospheric O2 fraction (0..1) at the position; gates combustion. */
    float oxygenFraction(Vector3 localPos);

    /** Writes the local-space wind vector (m/s; x=east, z=north) into {@code out}. */
    void wind(Vector3 localPos, Vector3 out);
}
