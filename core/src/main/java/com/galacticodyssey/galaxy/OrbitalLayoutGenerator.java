package com.galacticodyssey.galaxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class OrbitalLayoutGenerator {

    public List<OrbitalSlot> generate(StarSystem system) {
        Random rng = new Random(system.seed + 1);
        int planetCount = RngUtil.range(rng,
            system.spectralClass.planetCountMin,
            system.spectralClass.planetCountMax + 1);

        // Binary companions truncate the inner orbit limit
        float innerLimit = 0f;
        if (system.companion != null) {
            innerLimit = system.companion.separationAU * 0.33f;
        }

        float baseAU = 0.2f + rng.nextFloat() * 0.2f;
        List<OrbitalSlot> slots = new ArrayList<>(planetCount);

        float prevRadius = 0f;
        for (int i = 0; i < planetCount; i++) {
            float step = (1.4f + rng.nextFloat() * 0.8f);
            float jitter = 1.0f + rng.nextFloat() * 0.3f;
            float radius;
            if (i == 0) {
                radius = baseAU * jitter;
            } else {
                radius = prevRadius * step * jitter;
            }
            radius = Math.max(radius, innerLimit);
            float eccentricity = rng.nextFloat() * 0.3f;
            OrbitalZone zone = classifyZone(radius, system);

            slots.add(new OrbitalSlot(i, radius, eccentricity, zone));
            prevRadius = radius;
        }
        return slots;
    }

    private OrbitalZone classifyZone(float radiusAU, StarSystem system) {
        if (radiusAU < system.habZoneInner) return OrbitalZone.INNER;
        if (radiusAU <= system.habZoneOuter) return OrbitalZone.HABITABLE;
        if (radiusAU <= system.frostLine * 3f) return OrbitalZone.OUTER;
        return OrbitalZone.DEEP;
    }
}
