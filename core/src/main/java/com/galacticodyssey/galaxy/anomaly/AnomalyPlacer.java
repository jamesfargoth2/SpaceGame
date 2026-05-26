package com.galacticodyssey.galaxy.anomaly;

import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Places space anomalies throughout the galaxy based on frequency tables and star count.
 */
public final class AnomalyPlacer {

    /** Expected count per 1000 star systems. */
    private static final Map<AnomalyType, Float> FREQUENCY_PER_1000;

    /** Types that should be placed in deep space rather than near stars. */
    private static final java.util.Set<AnomalyType> RARE_DEEP_SPACE_TYPES = java.util.EnumSet.of(
            AnomalyType.WORMHOLE,
            AnomalyType.ANCIENT_RUIN_SITE,
            AnomalyType.MEGASTRUCTURE_REMNANT
    );

    static {
        FREQUENCY_PER_1000 = new EnumMap<>(AnomalyType.class);
        FREQUENCY_PER_1000.put(AnomalyType.WORMHOLE, 0.5f);
        FREQUENCY_PER_1000.put(AnomalyType.JUMP_POINT, 3.0f);
        FREQUENCY_PER_1000.put(AnomalyType.GRAVITATIONAL_LENS, 2.0f);
        FREQUENCY_PER_1000.put(AnomalyType.PULSAR_BEAM, 0.3f);
        FREQUENCY_PER_1000.put(AnomalyType.MAGNETAR_FIELD, 0.2f);
        FREQUENCY_PER_1000.put(AnomalyType.DARK_MATTER_HALO, 1.0f);
        FREQUENCY_PER_1000.put(AnomalyType.QUANTUM_FLUX_ZONE, 0.4f);
        FREQUENCY_PER_1000.put(AnomalyType.ION_STORM, 1.5f);
        FREQUENCY_PER_1000.put(AnomalyType.ANCIENT_RUIN_SITE, 0.15f);
        FREQUENCY_PER_1000.put(AnomalyType.PROTOPLANETARY_DISK, 0.8f);
        FREQUENCY_PER_1000.put(AnomalyType.STELLAR_NURSERY, 0.3f);
        FREQUENCY_PER_1000.put(AnomalyType.DERELICT_FLEET, 0.1f);
        FREQUENCY_PER_1000.put(AnomalyType.MEGASTRUCTURE_REMNANT, 0.05f);
    }

    /**
     * Places anomalies based on star count, galaxy seed, and the frequency table.
     *
     * @param starCount total number of stars in the galaxy
     * @param galaxySeed root galaxy seed
     * @param rng random number generator
     * @return list of placed anomalies
     */
    public List<AnomalyData> place(int starCount, long galaxySeed, Random rng) {
        long anomalySeed = SeedDeriver.domain(galaxySeed, SeedDeriver.STAR_DOMAIN);
        float galaxyRadius = estimateGalaxyRadius(starCount);

        List<AnomalyData> anomalies = new ArrayList<>();
        long nextId = 1;

        // Place each type according to frequency table
        for (AnomalyType type : AnomalyType.values()) {
            Float freq = FREQUENCY_PER_1000.get(type);
            if (freq == null) continue;

            // Skip wormholes here; handle them specially below
            if (type == AnomalyType.WORMHOLE) continue;

            int expectedCount = Math.round(freq * starCount / 1000f);
            // Apply Poisson-like variation
            int count = poissonSample(rng, Math.max(0.1f, expectedCount));

            for (int i = 0; i < count; i++) {
                long id = nextId++;
                long seed = SeedDeriver.forId(anomalySeed, id);
                boolean isDeepSpace = RARE_DEEP_SPACE_TYPES.contains(type);

                double posX, posY, posZ;
                if (isDeepSpace) {
                    // Place in deep space between stars
                    posX = (rng.nextDouble() - 0.5) * 2.0 * galaxyRadius * 1.2;
                    posY = (rng.nextDouble() - 0.5) * 2.0 * galaxyRadius * 1.2;
                    posZ = (rng.nextDouble() - 0.5) * 2.0 * galaxyRadius * 0.1;
                } else {
                    // Place near star positions
                    posX = (rng.nextDouble() - 0.5) * 2.0 * galaxyRadius;
                    posY = (rng.nextDouble() - 0.5) * 2.0 * galaxyRadius;
                    posZ = (rng.nextDouble() - 0.5) * 2.0 * galaxyRadius * 0.05;
                }

                AnomalyEffects effects = effectsForType(type, rng);
                float coreRadius = coreRadiusForType(type, rng);
                float hazardRadius = coreRadius * RngUtil.range(rng, 2f, 5f);
                float difficulty = discoveryDifficultyForType(type, rng);
                boolean charted = rng.nextFloat() < 0.3f;

                anomalies.add(new AnomalyData(type, id, posX, posY, posZ,
                        coreRadius, hazardRadius, difficulty, charted, seed, effects, -1));
            }
        }

        // Place wormholes as paired partners
        placeWormholes(starCount, galaxyRadius, anomalySeed, rng, anomalies, nextId);

        return anomalies;
    }

    private void placeWormholes(int starCount, float galaxyRadius, long anomalySeed,
                                Random rng, List<AnomalyData> anomalies, long nextId) {
        float freq = FREQUENCY_PER_1000.get(AnomalyType.WORMHOLE);
        int expectedCount = Math.round(freq * starCount / 1000f);
        int pairCount = Math.max(1, poissonSample(rng, Math.max(0.1f, expectedCount)) / 2);
        float minDistance = galaxyRadius * 0.2f;

        for (int p = 0; p < pairCount; p++) {
            long id1 = nextId++;
            long id2 = nextId++;
            long seed1 = SeedDeriver.forId(anomalySeed, id1);
            long seed2 = SeedDeriver.forId(anomalySeed, id2);

            // Place first wormhole
            double x1 = (rng.nextDouble() - 0.5) * 2.0 * galaxyRadius * 1.2;
            double y1 = (rng.nextDouble() - 0.5) * 2.0 * galaxyRadius * 1.2;
            double z1 = (rng.nextDouble() - 0.5) * 2.0 * galaxyRadius * 0.1;

            // Place second wormhole at least minDistance away
            double x2, y2, z2;
            int attempts = 0;
            do {
                x2 = (rng.nextDouble() - 0.5) * 2.0 * galaxyRadius * 1.2;
                y2 = (rng.nextDouble() - 0.5) * 2.0 * galaxyRadius * 1.2;
                z2 = (rng.nextDouble() - 0.5) * 2.0 * galaxyRadius * 0.1;
                attempts++;
            } while (distance(x1, y1, z1, x2, y2, z2) < minDistance && attempts < 100);

            AnomalyEffects effects = effectsForType(AnomalyType.WORMHOLE, rng);
            float coreRadius = coreRadiusForType(AnomalyType.WORMHOLE, rng);
            float hazardRadius1 = coreRadius * RngUtil.range(rng, 2f, 5f);
            float difficulty1 = discoveryDifficultyForType(AnomalyType.WORMHOLE, rng);
            float hazardRadius2 = coreRadius * RngUtil.range(rng, 2f, 5f);
            float difficulty2 = discoveryDifficultyForType(AnomalyType.WORMHOLE, rng);

            anomalies.add(new AnomalyData(AnomalyType.WORMHOLE, id1, x1, y1, z1,
                    coreRadius, hazardRadius1, difficulty1, false, seed1, effects, id2));
            anomalies.add(new AnomalyData(AnomalyType.WORMHOLE, id2, x2, y2, z2,
                    coreRadius, hazardRadius2, difficulty2, false, seed2, effects, id1));
        }
    }

    private AnomalyEffects effectsForType(AnomalyType type, Random rng) {
        switch (type) {
            case WORMHOLE:
                return new AnomalyEffects(3.0f, 0.5f, 0.1f, 0.05f, 0.2f, 0.3f);
            case JUMP_POINT:
                return new AnomalyEffects(1.0f, 1.0f, 0f, 0f, 1.5f, 0f);
            case GRAVITATIONAL_LENS:
                return new AnomalyEffects(2.5f, 1.5f, 0f, 0f, 0.7f, 0.1f);
            case PULSAR_BEAM:
                return new AnomalyEffects(1.0f, 0.3f, 0.2f, 0.8f, 0.5f, 0.05f);
            case MAGNETAR_FIELD:
                return new AnomalyEffects(1.2f, 0.2f, 0.5f, 0.6f, 0.3f, 0.2f);
            case DARK_MATTER_HALO:
                return new AnomalyEffects(1.8f, 0.8f, 0f, 0.02f, 0.9f, 0.15f);
            case QUANTUM_FLUX_ZONE:
                return new AnomalyEffects(1.0f, 0.7f, 0.05f, 0.1f, 0.4f, 0.5f);
            case ION_STORM:
                return new AnomalyEffects(1.0f, 0.4f, 0.3f, 0.15f, 0.6f, 0.25f);
            case ANCIENT_RUIN_SITE:
                return new AnomalyEffects(1.0f, 0.9f, 0f, 0.01f, 1.0f, 0f);
            case PROTOPLANETARY_DISK:
                return new AnomalyEffects(1.3f, 0.6f, 0f, 0.03f, 0.8f, 0.05f);
            case MEGASTRUCTURE_REMNANT:
                return new AnomalyEffects(1.5f, 1.2f, 0f, 0.02f, 0.9f, 0.1f);
            case DERELICT_FLEET:
                return new AnomalyEffects(1.0f, 0.8f, 0f, 0.05f, 1.0f, 0f);
            case STELLAR_NURSERY:
                return new AnomalyEffects(1.1f, 0.7f, 0f, 0.1f, 0.85f, 0.02f);
            default:
                return AnomalyEffects.defaults();
        }
    }

    private float coreRadiusForType(AnomalyType type, Random rng) {
        switch (type) {
            case WORMHOLE:             return RngUtil.range(rng, 0.001f, 0.01f);
            case JUMP_POINT:           return RngUtil.range(rng, 0.005f, 0.02f);
            case GRAVITATIONAL_LENS:   return RngUtil.range(rng, 0.01f, 0.05f);
            case PULSAR_BEAM:          return RngUtil.range(rng, 0.001f, 0.005f);
            case MAGNETAR_FIELD:       return RngUtil.range(rng, 0.005f, 0.03f);
            case DARK_MATTER_HALO:     return RngUtil.range(rng, 0.1f, 0.5f);
            case QUANTUM_FLUX_ZONE:    return RngUtil.range(rng, 0.01f, 0.05f);
            case ION_STORM:            return RngUtil.range(rng, 0.05f, 0.2f);
            case ANCIENT_RUIN_SITE:    return RngUtil.range(rng, 0.001f, 0.005f);
            case PROTOPLANETARY_DISK:  return RngUtil.range(rng, 0.01f, 0.1f);
            case MEGASTRUCTURE_REMNANT: return RngUtil.range(rng, 0.01f, 0.05f);
            case DERELICT_FLEET:       return RngUtil.range(rng, 0.001f, 0.01f);
            case STELLAR_NURSERY:      return RngUtil.range(rng, 0.05f, 0.3f);
            default:                   return RngUtil.range(rng, 0.01f, 0.1f);
        }
    }

    private float discoveryDifficultyForType(AnomalyType type, Random rng) {
        switch (type) {
            case WORMHOLE:             return RngUtil.range(rng, 0.6f, 0.9f);
            case JUMP_POINT:           return RngUtil.range(rng, 0.2f, 0.5f);
            case GRAVITATIONAL_LENS:   return RngUtil.range(rng, 0.3f, 0.6f);
            case PULSAR_BEAM:          return RngUtil.range(rng, 0.1f, 0.3f);
            case MAGNETAR_FIELD:       return RngUtil.range(rng, 0.2f, 0.4f);
            case DARK_MATTER_HALO:     return RngUtil.range(rng, 0.5f, 0.8f);
            case QUANTUM_FLUX_ZONE:    return RngUtil.range(rng, 0.4f, 0.7f);
            case ION_STORM:            return RngUtil.range(rng, 0.1f, 0.3f);
            case ANCIENT_RUIN_SITE:    return RngUtil.range(rng, 0.7f, 0.95f);
            case PROTOPLANETARY_DISK:  return RngUtil.range(rng, 0.2f, 0.4f);
            case MEGASTRUCTURE_REMNANT: return RngUtil.range(rng, 0.8f, 0.99f);
            case DERELICT_FLEET:       return RngUtil.range(rng, 0.5f, 0.8f);
            case STELLAR_NURSERY:      return RngUtil.range(rng, 0.2f, 0.5f);
            default:                   return RngUtil.range(rng, 0.3f, 0.7f);
        }
    }

    /**
     * Simple Poisson-like sampling using the Knuth method.
     */
    private int poissonSample(Random rng, float lambda) {
        if (lambda <= 0f) return 0;
        double l = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= rng.nextDouble();
        } while (p > l);
        return k - 1;
    }

    private float estimateGalaxyRadius(int starCount) {
        // Rough estimate: radius scales with cube root of star count
        return (float) (Math.cbrt(starCount) * 10.0);
    }

    private double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
