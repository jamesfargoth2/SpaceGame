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
        float density = densityForType(type, rng);
        float mass = density * radius * radius * radius;

        float basePeriod = 24f; // Earth hours baseline
        float dayLength = basePeriod / (float) Math.sqrt(mass) * RngUtil.range(rng, 0.5f, 2.0f);
        dayLength = Math.max(2f, Math.min(2000f, dayLength));

        float axialTilt = Math.abs((float) (rng.nextGaussian() * 12.0));
        axialTilt = Math.min(axialTilt, 90f);

        boolean tidallyLocked = false;
        if (slot.zone == OrbitalZone.INNER && system.spectralClass == SpectralClass.M) {
            tidallyLocked = rng.nextFloat() < 0.7f;
        } else if (slot.zone == OrbitalZone.INNER) {
            tidallyLocked = rng.nextFloat() < 0.3f;
        }
        if (tidallyLocked) dayLength = slot.orbitalPeriod * 24f * 365.25f;

        Planet planet = new Planet(planetSeed, type, radius, mass, density, dayLength, axialTilt, tidallyLocked);

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

            float moonOrbitalRadius = (0.002f + m * 0.002f) * RngUtil.range(moonRng, 0.8f, 1.2f);
            float moonEccentricity = moonRng.nextFloat() * 0.1f;
            float moonInclination = (float)(moonRng.nextGaussian() * 0.1);

            Moon moon = new Moon(moonSeed, moonType, moonRadius, moonMass,
                moonOrbitalRadius, moonEccentricity, moonInclination);
            moon.computeOrbitalPeriod(mass * OrbitalConstants.EARTH_MASS_KG);
            planet.moons.add(moon);
        }

        RingSystemGenerator ringGen = new RingSystemGenerator();
        planet.rings = ringGen.generate(planet, estimatePlanetAge(system), rng);

        return planet;
    }

    private float densityForType(PlanetType type, Random rng) {
        return switch (type) {
            case MOLTEN -> RngUtil.range(rng, 5.0f, 8.0f);
            case TERRAN -> RngUtil.range(rng, 4.5f, 6.0f);
            case OCEAN -> RngUtil.range(rng, 3.5f, 5.5f);
            case ARID -> RngUtil.range(rng, 4.0f, 5.5f);
            case TOXIC -> RngUtil.range(rng, 4.5f, 6.5f);
            case BARREN -> RngUtil.range(rng, 3.0f, 5.0f);
            case ICE_WORLD -> RngUtil.range(rng, 1.5f, 3.0f);
            case GAS_GIANT -> RngUtil.range(rng, 0.7f, 1.6f);
            case ICE_GIANT -> RngUtil.range(rng, 1.2f, 2.0f);
            case DWARF -> RngUtil.range(rng, 2.0f, 4.0f);
        };
    }

    private float estimatePlanetAge(StarSystem system) {
        // StarSystem.age is in Gyr; use it directly when available (> 0)
        if (system.age > 0f) return system.age;
        // Fallback: bright stars are typically younger; dim stars older
        // luminosity ~1 -> solar analogue ~4.5 Gyr; scale inversely
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
