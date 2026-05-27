package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.*;
import com.galacticodyssey.planet.rings.RingSystemGenerator;
import java.util.Random;

public final class PlanetGenerator {
    private final long galaxySeed;

    public PlanetGenerator(long galaxySeed) {
        this.galaxySeed = galaxySeed;
    }

    public Planet generate(OrbitalSlot slot, StarSystem system) {
        long planetSeed = SeedDeriver.forId(
            SeedDeriver.domain(system.seed, SeedDeriver.PLANET_DOMAIN), slot.index);
        Random rng = new Random(planetSeed);

        PlanetType type = rollPlanetType(rng, slot.zone);
        float radius = RngUtil.range(rng, type.radiusMin, type.radiusMax);
        float mass = radius * radius * RngUtil.range(rng, 0.7f, 1.3f);
        float dayLength = RngUtil.range(rng, 5f, 2000f);
        float axialTilt = rng.nextFloat() * 45f;

        boolean tidallyLocked = false;
        if (slot.zone == OrbitalZone.INNER && system.spectralClass == SpectralClass.M) {
            tidallyLocked = rng.nextFloat() < 0.7f;
        } else if (slot.zone == OrbitalZone.INNER) {
            tidallyLocked = rng.nextFloat() < 0.3f;
        }
        if (tidallyLocked) dayLength = slot.orbitalPeriod * 24f * 365.25f;

        Planet planet = new Planet(planetSeed, type, radius, mass, dayLength, axialTilt, tidallyLocked);

        int moonCount = (type.moonMax > type.moonMin)
            ? RngUtil.range(rng, type.moonMin, type.moonMax + 1)
            : type.moonMin;
        for (int m = 0; m < moonCount; m++) {
            long moonSeed = SeedDeriver.forId(
                SeedDeriver.domain(planetSeed, SeedDeriver.MOON_DOMAIN), m);
            Random moonRng = new Random(moonSeed);
            PlanetType moonType = moonRng.nextFloat() < 0.5f ? PlanetType.BARREN : PlanetType.ICE_WORLD;
            float moonRadius = RngUtil.range(moonRng, 0.05f, radius * 0.3f);
            float moonMass = moonRadius * moonRadius * RngUtil.range(moonRng, 0.5f, 1.0f);
            planet.moons.add(new Moon(moonSeed, moonType, moonRadius, moonMass));
        }

        RingSystemGenerator ringGen = new RingSystemGenerator();
        planet.rings = ringGen.generate(planet, estimatePlanetAge(system), rng);

        return planet;
    }

    private float estimatePlanetAge(StarSystem system) {
        // StarSystem.age is in Gyr; use it directly when available (> 0)
        if (system.age > 0f) return system.age;
        // Fallback: bright stars are typically younger; dim stars older
        // luminosity ~1 → solar analogue ~4.5 Gyr; scale inversely
        return Math.max(0.5f, Math.min(10f, 4.5f / (float) Math.sqrt(Math.max(0.01f, system.luminosity))));
    }

    private PlanetType rollPlanetType(Random rng, OrbitalZone zone) {
        PlanetType[] candidates = PlanetType.values();
        float totalWeight = 0f;
        float[] weights = new float[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            weights[i] = candidates[i].validZones.contains(zone) ? 1f : 0f;
            totalWeight += weights[i];
        }
        float roll = rng.nextFloat() * totalWeight;
        float cumulative = 0f;
        for (int i = 0; i < candidates.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return candidates[i];
        }
        return PlanetType.BARREN;
    }
}
