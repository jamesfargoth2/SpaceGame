package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Random;

public final class AtmosphereGenerator {

    public Atmosphere generate(Planet planet, com.galacticodyssey.galaxy.StarSystem system) {
        if (planet.type == PlanetType.MOLTEN || planet.type == PlanetType.DWARF
            || planet.type == PlanetType.GAS_GIANT || planet.type == PlanetType.ICE_GIANT) {
            return null;
        }

        long atmoSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.ATMOSPHERE_DOMAIN), 0);
        Random rng = new Random(atmoSeed);

        if (planet.type == PlanetType.BARREN && rng.nextFloat() >= 0.1f) return null;
        if (planet.type == PlanetType.ICE_WORLD && rng.nextFloat() >= 0.3f) return null;

        Map<Gas, Float> composition = generateComposition(planet.type, rng);
        float pressure = generatePressure(planet.type, rng);
        float greenhouse = generateGreenhouse(planet.type, rng);

        float equilibriumTemp = 278f * (float) Math.pow(system.luminosity, 0.25)
            / (float) Math.sqrt(findOrbitalRadius(planet, system));
        float surfaceTemp = equilibriumTemp * greenhouse;

        boolean breathable = isBreathable(composition, pressure);
        EnumSet<AtmoHazard> hazards = deriveHazards(composition, pressure, surfaceTemp);

        return new Atmosphere(composition, pressure, greenhouse,
            equilibriumTemp, surfaceTemp, breathable, hazards);
    }

    private Map<Gas, Float> generateComposition(PlanetType type, Random rng) {
        Map<Gas, Float> comp = new EnumMap<>(Gas.class);
        switch (type) {
            case ARID -> {
                float co2 = RngUtil.range(rng, 0.75f, 0.85f);
                float n2 = RngUtil.range(rng, 0.10f, 0.20f);
                comp.put(Gas.CO2, co2);
                comp.put(Gas.N2, n2);
                comp.put(Gas.Ar, 1f - co2 - n2);
            }
            case TERRAN -> {
                float n2 = RngUtil.range(rng, 0.60f, 0.80f);
                float o2 = RngUtil.range(rng, 0.15f, 0.25f);
                comp.put(Gas.N2, n2);
                comp.put(Gas.O2, o2);
                comp.put(Gas.Ar, 1f - n2 - o2);
            }
            case OCEAN -> {
                float n2 = RngUtil.range(rng, 0.50f, 0.70f);
                float h2o = RngUtil.range(rng, 0.20f, 0.40f);
                comp.put(Gas.N2, n2);
                comp.put(Gas.H2O, h2o);
                comp.put(Gas.CO2, 1f - n2 - h2o);
            }
            case TOXIC -> {
                float so2 = RngUtil.range(rng, 0.35f, 0.45f);
                float co2 = RngUtil.range(rng, 0.30f, 0.40f);
                float hcl = RngUtil.range(rng, 0.02f, 0.08f);
                comp.put(Gas.SO2, so2);
                comp.put(Gas.CO2, co2);
                comp.put(Gas.HCl, hcl);
                comp.put(Gas.N2, 1f - so2 - co2 - hcl);
            }
            case BARREN -> {
                comp.put(Gas.CO2, 0.9f + rng.nextFloat() * 0.1f);
                comp.put(Gas.N2, 1f - comp.get(Gas.CO2));
            }
            case ICE_WORLD -> {
                comp.put(Gas.N2, 0.85f + rng.nextFloat() * 0.15f);
                comp.put(Gas.Ar, 1f - comp.get(Gas.N2));
            }
            default -> comp.put(Gas.N2, 1f);
        }
        normalizeComposition(comp);
        return comp;
    }

    private void normalizeComposition(Map<Gas, Float> comp) {
        float sum = 0f;
        for (float v : comp.values()) sum += v;
        if (sum <= 0f) return;
        for (Map.Entry<Gas, Float> e : comp.entrySet()) {
            e.setValue(e.getValue() / sum);
        }
    }

    private float generatePressure(PlanetType type, Random rng) {
        return switch (type) {
            case BARREN -> RngUtil.range(rng, 0.001f, 0.01f);
            case ARID -> RngUtil.range(rng, 0.01f, 0.5f);
            case TERRAN -> RngUtil.range(rng, 0.5f, 2.0f);
            case OCEAN -> RngUtil.range(rng, 1.0f, 4.0f);
            case TOXIC -> RngUtil.range(rng, 2.0f, 90.0f);
            case ICE_WORLD -> RngUtil.range(rng, 0.001f, 0.05f);
            default -> 0f;
        };
    }

    private float generateGreenhouse(PlanetType type, Random rng) {
        return switch (type) {
            case ARID -> RngUtil.range(rng, 1.1f, 1.4f);
            case TERRAN -> RngUtil.range(rng, 1.1f, 1.3f);
            case OCEAN -> RngUtil.range(rng, 1.3f, 1.8f);
            case TOXIC -> RngUtil.range(rng, 1.5f, 3.0f);
            default -> 1.0f;
        };
    }

    private float findOrbitalRadius(Planet planet, com.galacticodyssey.galaxy.StarSystem system) {
        for (var slot : system.orbits) {
            if (slot.planet == planet) return slot.orbitalRadius;
        }
        return 1.0f;
    }

    private boolean isBreathable(Map<Gas, Float> comp, float pressure) {
        float o2 = comp.getOrDefault(Gas.O2, 0f);
        if (o2 < 0.15f || o2 > 0.25f) return false;
        if (pressure < 0.5f || pressure > 2.0f) return false;
        if (comp.getOrDefault(Gas.SO2, 0f) > 0.01f) return false;
        if (comp.getOrDefault(Gas.HCl, 0f) > 0.01f) return false;
        if (comp.getOrDefault(Gas.NH3, 0f) > 0.01f) return false;
        return true;
    }

    private EnumSet<AtmoHazard> deriveHazards(Map<Gas, Float> comp, float pressure, float surfaceTemp) {
        EnumSet<AtmoHazard> hazards = EnumSet.noneOf(AtmoHazard.class);
        if (pressure < 0.01f) hazards.add(AtmoHazard.VACUUM);
        if (pressure > 10.0f) hazards.add(AtmoHazard.CRUSHING);
        float so2 = comp.getOrDefault(Gas.SO2, 0f);
        float hcl = comp.getOrDefault(Gas.HCl, 0f);
        float nh3 = comp.getOrDefault(Gas.NH3, 0f);
        if (so2 > 0.05f || hcl > 0.05f || nh3 > 0.05f) hazards.add(AtmoHazard.TOXIC);
        if (so2 > 0.20f && pressure > 5.0f) hazards.add(AtmoHazard.CORROSIVE);
        if (surfaceTemp > 400f) hazards.add(AtmoHazard.EXTREME_HEAT);
        if (surfaceTemp < 180f) hazards.add(AtmoHazard.EXTREME_COLD);
        if (hazards.isEmpty()) hazards.add(AtmoHazard.NONE);
        return hazards;
    }
}
