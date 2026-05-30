package com.galacticodyssey.planet;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that ScatteringParams expresses planet/atmosphere geometry in metres,
 * lockstep with the real-scale terrain local frame introduced in SP1.
 *
 * <p>Full sky/altitude retuning (density, scattering coefficients scaled to metre
 * optical paths) is deferred to SP3; this test guards only the geometry shell.
 */
class ScatteringParamsScaleTest {

    // Earth: radius == 1.0 Earth-radii → 6 371 000 m
    private static final float EARTH_RADIUS_M = 6_371_000f;

    @Test
    void planetRadiusIsMetresForEarthLikePlanet_fromPlanet() {
        // Minimal Earth-like N2/O2 atmosphere with surfacePressure >= 0.01 so
        // fromPlanet() takes the non-vacuum branch (planet.radius * 6371 * 1000).
        Map<Gas, Float> comp = new EnumMap<>(Gas.class);
        comp.put(Gas.N2, 0.78f);
        comp.put(Gas.O2, 0.21f);
        comp.put(Gas.H2O, 0.01f);
        Atmosphere atmo = new Atmosphere(
            comp,
            1.0f,            // surfacePressure (Earth atm)
            1.0f,            // greenhouseMultiplier
            255f,            // equilibriumTemp K
            288f,            // surfaceTemp K
            true,            // breathable
            EnumSet.of(AtmoHazard.NONE)
        );

        Planet earth = new Planet(1L, PlanetType.TERRAN, 1.0f, 1.0f, 5.5f, 24f, 23f, false);
        earth.atmosphere = atmo;

        ScatteringParams p = ScatteringParams.fromPlanet(earth);

        assertEquals(EARTH_RADIUS_M, p.planetRadius, 1f,
            "planetRadius should be ~6_371_000 m for an Earth-radius planet, was: " + p.planetRadius);
        assertTrue(p.atmosphereRadius > p.planetRadius,
            "atmosphereRadius (" + p.atmosphereRadius +
            ") must be above planetRadius (" + p.planetRadius + ")");
    }

    @Test
    void planetRadiusIsMetresForVacuumPlanet() {
        // Null atmosphere → vacuum() branch: radiusEarthRadii * 6371 * 1000
        Planet barren = new Planet(2L, PlanetType.BARREN, 1.0f, 1.0f, 5.5f, 24f, 0f, false);
        // atmosphere left null → vacuum path

        ScatteringParams p = ScatteringParams.fromPlanet(barren);

        assertEquals(EARTH_RADIUS_M, p.planetRadius, 1f,
            "vacuum path planetRadius should be ~6_371_000 m, was: " + p.planetRadius);
        assertTrue(p.atmosphereRadius > p.planetRadius,
            "atmosphereRadius must be above surface even for vacuum world");
    }

    @Test
    void earthLikeFactoryIsMetres() {
        ScatteringParams p = ScatteringParams.earthLike();

        assertEquals(EARTH_RADIUS_M, p.planetRadius, 1f,
            "earthLike() planetRadius should be ~6_371_000 m, was: " + p.planetRadius);
        assertTrue(p.atmosphereRadius > p.planetRadius,
            "atmosphereRadius (" + p.atmosphereRadius +
            ") must exceed planetRadius (" + p.planetRadius + ")");
    }

    @Test
    void atmosphereShellIsPlausiblySized() {
        // The atmosphere shell should be at least 1 km (1000 m) above surface and
        // no more than ~500 km above, regardless of the unit change.
        Map<Gas, Float> comp = new EnumMap<>(Gas.class);
        comp.put(Gas.N2, 0.78f);
        comp.put(Gas.O2, 0.22f);
        Atmosphere atmo = new Atmosphere(comp, 1.0f, 1.0f, 255f, 288f, true, EnumSet.of(AtmoHazard.NONE));
        Planet earth = new Planet(3L, PlanetType.TERRAN, 1.0f, 1.0f, 5.5f, 24f, 23f, false);
        earth.atmosphere = atmo;

        ScatteringParams p = ScatteringParams.fromPlanet(earth);

        float shellThickness = p.atmosphereRadius - p.planetRadius;
        assertTrue(shellThickness >= 1_000f,
            "Shell thickness should be at least 1 000 m, was: " + shellThickness);
        assertTrue(shellThickness <= 500_000f,
            "Shell thickness should not exceed 500 000 m, was: " + shellThickness);
    }
}
