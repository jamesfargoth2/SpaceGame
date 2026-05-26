package com.galacticodyssey.galaxy.nebula;

import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates the volumetric interior data for a nebula region.
 */
public final class NebulaVolumeGenerator {

    public NebulaVolume generate(long seed, float radiusLY, String nebulaType) {
        long nebulaSeed = SeedDeriver.domain(seed, SeedDeriver.NEBULA_DOMAIN);
        Random rng = new Random(nebulaSeed);

        float peakDensity = peakDensityForType(nebulaType, rng);
        float dustFraction = dustFractionForType(nebulaType, rng);
        float glowIntensity = glowForType(nebulaType, rng);

        List<IonisationZone> ionZones = generateIonZones(nebulaType, radiusLY, rng);
        List<NebulaHazard> hazards = generateHazards(nebulaType, radiusLY, rng);
        List<EmbeddedObject> embeddedObjects = generateEmbeddedObjects(radiusLY, nebulaSeed, rng);

        float[] primaryColor = primaryColorForType(nebulaType, rng);
        float[] secondaryColor = secondaryColorForType(nebulaType, rng);

        return new NebulaVolume(seed, peakDensity, dustFraction, ionZones, hazards,
                embeddedObjects, glowIntensity, primaryColor, secondaryColor);
    }

    private float peakDensityForType(String type, Random rng) {
        switch (type) {
            case "EMISSION":  return RngUtil.range(rng, 5e-19f, 1e-18f);
            case "REFLECTION": return RngUtil.range(rng, 1e-19f, 5e-19f);
            case "DARK":       return RngUtil.range(rng, 1e-20f, 1e-19f);
            case "PLANETARY":  return RngUtil.range(rng, 3e-19f, 1e-18f);
            default:           return RngUtil.range(rng, 1e-20f, 1e-18f);
        }
    }

    private float dustFractionForType(String type, Random rng) {
        switch (type) {
            case "EMISSION":   return RngUtil.range(rng, 0.1f, 0.3f);
            case "REFLECTION": return RngUtil.range(rng, 0.2f, 0.5f);
            case "DARK":       return RngUtil.range(rng, 0.6f, 0.9f);
            case "PLANETARY":  return RngUtil.range(rng, 0.15f, 0.4f);
            default:           return RngUtil.range(rng, 0.1f, 0.9f);
        }
    }

    private float glowForType(String type, Random rng) {
        switch (type) {
            case "EMISSION":   return RngUtil.range(rng, 0.6f, 1.0f);
            case "REFLECTION": return RngUtil.range(rng, 0.3f, 0.6f);
            case "DARK":       return RngUtil.range(rng, 0.0f, 0.1f);
            case "PLANETARY":  return RngUtil.range(rng, 0.5f, 0.9f);
            default:           return RngUtil.range(rng, 0.1f, 0.5f);
        }
    }

    private List<IonisationZone> generateIonZones(String type, float radiusLY, Random rng) {
        List<IonisationZone> zones = new ArrayList<>();
        int count;
        switch (type) {
            case "EMISSION":
            case "PLANETARY":
                count = RngUtil.range(rng, 1, 6);
                break;
            case "DARK":
            case "REFLECTION":
            default:
                count = 0;
                break;
        }
        for (int i = 0; i < count; i++) {
            float cx = (rng.nextFloat() - 0.5f) * 2f * radiusLY * 0.7f;
            float cy = (rng.nextFloat() - 0.5f) * 2f * radiusLY * 0.7f;
            float cz = (rng.nextFloat() - 0.5f) * 2f * radiusLY * 0.7f;
            zones.add(IonisationZone.generate(cx, cy, cz, rng));
        }
        return zones;
    }

    private List<NebulaHazard> generateHazards(String type, float radiusLY, Random rng) {
        List<NebulaHazard> hazards = new ArrayList<>();
        int count = RngUtil.range(rng, 2, 9);

        NebulaHazardType[] hazardTypes = hazardDistributionForType(type);
        for (int i = 0; i < count; i++) {
            NebulaHazardType hType = hazardTypes[rng.nextInt(hazardTypes.length)];
            float cx = (rng.nextFloat() - 0.5f) * 2f * radiusLY * 0.8f;
            float cy = (rng.nextFloat() - 0.5f) * 2f * radiusLY * 0.8f;
            float cz = (rng.nextFloat() - 0.5f) * 2f * radiusLY * 0.8f;
            float radius = RngUtil.range(rng, 0.05f, 0.3f) * radiusLY;
            float intensity = RngUtil.range(rng, 0.2f, 1.0f);
            float period = RngUtil.range(rng, 5f, 120f);
            hazards.add(new NebulaHazard(hType, cx, cy, cz, radius, intensity, period));
        }
        return hazards;
    }

    private NebulaHazardType[] hazardDistributionForType(String type) {
        switch (type) {
            case "EMISSION":
                return new NebulaHazardType[]{
                    NebulaHazardType.ION_DISCHARGE,
                    NebulaHazardType.PROTOSTELLAR_JET,
                    NebulaHazardType.STATIC_DISCHARGE
                };
            case "DARK":
                return new NebulaHazardType[]{
                    NebulaHazardType.DENSE_DUST_LANE,
                    NebulaHazardType.GRAVITATIONAL_EDDY
                };
            case "PLANETARY":
                return new NebulaHazardType[]{
                    NebulaHazardType.ION_DISCHARGE,
                    NebulaHazardType.STATIC_DISCHARGE,
                    NebulaHazardType.GRAVITATIONAL_EDDY
                };
            case "REFLECTION":
            default:
                return NebulaHazardType.values();
        }
    }

    private List<EmbeddedObject> generateEmbeddedObjects(float radiusLY, long nebulaSeed, Random rng) {
        List<EmbeddedObject> objects = new ArrayList<>();

        // 1-5 young stellar objects
        int ysoCount = RngUtil.range(rng, 1, 6);
        for (int i = 0; i < ysoCount; i++) {
            double px = (rng.nextDouble() - 0.5) * 2.0 * radiusLY * 0.6;
            double py = (rng.nextDouble() - 0.5) * 2.0 * radiusLY * 0.6;
            double pz = (rng.nextDouble() - 0.5) * 2.0 * radiusLY * 0.6;
            long objSeed = SeedDeriver.forId(nebulaSeed, i);
            objects.add(new EmbeddedObject(EmbeddedObjectType.YOUNG_STELLAR_OBJECT, px, py, pz, objSeed));
        }

        // 15% chance of hidden cache
        if (rng.nextFloat() < 0.15f) {
            double px = (rng.nextDouble() - 0.5) * 2.0 * radiusLY * 0.8;
            double py = (rng.nextDouble() - 0.5) * 2.0 * radiusLY * 0.8;
            double pz = (rng.nextDouble() - 0.5) * 2.0 * radiusLY * 0.8;
            long objSeed = SeedDeriver.forId(nebulaSeed, 1000);
            objects.add(new EmbeddedObject(EmbeddedObjectType.HIDDEN_CACHE, px, py, pz, objSeed));
        }

        // Protoplanetary disks near young stellar objects
        for (int i = 0; i < ysoCount; i++) {
            if (rng.nextFloat() < 0.5f) {
                EmbeddedObject yso = objects.get(i);
                double offset = radiusLY * 0.01;
                long objSeed = SeedDeriver.forId(nebulaSeed, 2000 + i);
                objects.add(new EmbeddedObject(EmbeddedObjectType.PROTOPLANETARY_DISK,
                        yso.posX + offset, yso.posY, yso.posZ, objSeed));
            }
        }

        return objects;
    }

    private float[] primaryColorForType(String type, Random rng) {
        switch (type) {
            case "EMISSION":   return new float[]{0.9f, 0.2f, 0.15f, 0.7f};
            case "REFLECTION": return new float[]{0.3f, 0.4f, 0.9f, 0.5f};
            case "DARK":       return new float[]{0.08f, 0.06f, 0.04f, 0.9f};
            case "PLANETARY":  return new float[]{0.2f, 0.9f, 0.5f, 0.6f};
            default:           return new float[]{0.5f, 0.5f, 0.5f, 0.4f};
        }
    }

    private float[] secondaryColorForType(String type, Random rng) {
        switch (type) {
            case "EMISSION":   return new float[]{0.4f, 0.1f, 0.6f, 0.4f};
            case "REFLECTION": return new float[]{0.6f, 0.7f, 1.0f, 0.3f};
            case "DARK":       return new float[]{0.02f, 0.02f, 0.02f, 0.7f};
            case "PLANETARY":  return new float[]{0.5f, 0.7f, 1.0f, 0.4f};
            default:           return new float[]{0.3f, 0.3f, 0.3f, 0.3f};
        }
    }
}
