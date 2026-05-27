package com.galacticodyssey.planet.rings;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetType;
import java.util.Random;

public final class RingSystemGenerator {

    private final RingPresenceRoller  roller    = new RingPresenceRoller();
    private final RingBandGenerator   bandGen   = new RingBandGenerator();
    private final ResonanceGapCalculator gapCalc = new ResonanceGapCalculator();
    private final ShepherdMoonPlacer  moonPlacer = new ShepherdMoonPlacer();

    public RingSystemData generate(Planet planet, float planetAgeGyr, Random rng) {
        if (!roller.hasRings(planet.type, planetAgeGyr, rng)) return null;

        float density  = deriveApproxDensity(planet);
        // Roche limit: 2.44 * R * cbrt(ρ_planet / ρ_moon_material)
        // Use icy moon density (1000 kg/m³) as representative ring particle density
        float rocheKm  = 2.44f * planet.radius * (float) Math.cbrt(density / 1000.0);

        float innerKm  = rocheKm * RngUtil.range(rng, 0.3f, 0.5f);
        float outerKm  = rocheKm * RngUtil.range(rng, 0.85f, 0.99f);

        Array<RingBand>     bands = bandGen.generate(innerKm, outerKm, planet.type, rng);
        Array<ShepherdMoon> moons = moonPlacer.place(bands, rocheKm, rng);

        RingSystemData data  = new RingSystemData();
        data.innerRadiusKm   = innerKm;
        data.outerRadiusKm   = outerKm;
        data.tiltDeg         = RngUtil.range(rng, 0f, 30f);
        data.thicknessKm     = RngUtil.range(rng, 0.0001f, 0.1f);
        data.bands           = bands;
        data.shepherdMoons   = moons;
        data.rocheLimit      = rocheKm;
        data.seed            = rng.nextLong();
        return data;
    }

    private float deriveApproxDensity(Planet planet) {
        return switch (planet.type) {
            case ICE_WORLD, ICE_GIANT -> 1000f;
            case GAS_GIANT            -> 700f;
            default                   -> 3000f;
        };
    }
}
