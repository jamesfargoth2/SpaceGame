package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;

import java.util.Random;

public class NebulaPlacer {

    public Array<NebulaRegion> place(GalaxyConfig cfg, long galaxySeed) {
        long nebulaSeed = SeedDeriver.domain(galaxySeed, SeedDeriver.NEBULA_DOMAIN);
        Random rng = new Random(nebulaSeed);
        GalaxyNoise noise = new GalaxyNoise(nebulaSeed);
        GalaxyDensityField density = new GalaxyDensityField();

        Array<NebulaRegion> nebulae = new Array<>();

        for (int i = 0; i < cfg.nebulaCount; i++) {
            float nx, ny;
            int attempts = 0;
            do {
                nx = rng.nextFloat() * 2f - 1f;
                ny = rng.nextFloat() * 2f - 1f;
                attempts++;
            } while (density.density(nx, ny, cfg, noise) < 0.4f && attempts < 100);

            NebulaRegion n = new NebulaRegion();
            n.centreX = nx * cfg.radiusLY;
            n.centreY = ny * cfg.radiusLY;
            n.radiusLY = cfg.radiusLY * RngUtil.range(rng, 0.02f, 0.08f);
            n.type = NebulaType.values()[rng.nextInt(NebulaType.values().length)];
            n.colour = nebulaColour(n.type, rng);
            nebulae.add(n);
        }
        return nebulae;
    }

    private Color nebulaColour(NebulaType type, Random rng) {
        switch (type) {
            case EMISSION:   return new Color(1f, 0.2f + rng.nextFloat() * 0.3f, 0.1f, 0.6f);
            case REFLECTION: return new Color(0.2f, 0.4f, 1f, 0.5f);
            case DARK:       return new Color(0.05f, 0.05f, 0.05f, 0.8f);
            case PLANETARY:  return new Color(0.3f, 1f, 0.5f, 0.4f);
            default:         return new Color(0.8f, 0.8f, 0.8f, 0.3f);
        }
    }
}
