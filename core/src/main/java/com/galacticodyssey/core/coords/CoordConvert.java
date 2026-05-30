package com.galacticodyssey.core.coords;

import com.badlogic.gdx.math.Vector3;

/** Conversions between PLANET_SPACE (km, double) and LOCAL_SCENE (m, float). */
public final class CoordConvert {
    public static final double KM_TO_M = 1000.0;
    public static final double M_TO_KM = 0.001;

    private CoordConvert() {}

    /** Planet-space km -> local-scene metres, given the floating origin's planet-space position.
     *  Subtracts in double BEFORE casting to float (catastrophic-cancellation safe). */
    public static LocalCoordsM planetToLocal(PlanetCoordsKM world, PlanetCoordsKM originKm) {
        return new LocalCoordsM(
            (float) ((world.x() - originKm.x()) * KM_TO_M),
            (float) ((world.y() - originKm.y()) * KM_TO_M),
            (float) ((world.z() - originKm.z()) * KM_TO_M));
    }

    /** Local-scene metres -> planet-space km, given the floating origin's planet-space position. */
    public static PlanetCoordsKM localToPlanet(LocalCoordsM local, PlanetCoordsKM originKm) {
        return new PlanetCoordsKM(
            originKm.x() + local.x() * M_TO_KM,
            originKm.y() + local.y() * M_TO_KM,
            originKm.z() + local.z() * M_TO_KM);
    }

    /** "Up" at a planet-space point = normalized radial direction (planet centre at origin). */
    public static Vector3 surfaceUpLocal(PlanetCoordsKM planetKm) {
        double r = planetKm.len();
        if (r == 0) return new Vector3(0, 1, 0);
        return new Vector3((float) (planetKm.x() / r), (float) (planetKm.y() / r), (float) (planetKm.z() / r));
    }
}
