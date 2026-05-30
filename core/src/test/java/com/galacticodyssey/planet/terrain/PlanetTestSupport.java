package com.galacticodyssey.planet.terrain;

import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetType;

/**
 * Shared test helper for terrain tests. Builds a deterministic Earth-like {@link Planet}
 * the same way {@code PlanetTerrainSystemScaleTest} does, so terrain tests can reuse a
 * single canonical construction.
 */
public final class PlanetTestSupport {
    private PlanetTestSupport() {}

    /** Earth-like planet: radius 1.0 (=> 6371 km), surface-bearing TERRAN type, fixed seed. */
    public static Planet earthLike() {
        return new Planet(123L, PlanetType.TERRAN, 1.0f, 1.0f, 5.5f, 24f, 23f, false);
    }
}
