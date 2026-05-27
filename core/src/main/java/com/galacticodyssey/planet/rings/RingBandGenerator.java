package com.galacticodyssey.planet.rings;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.planet.PlanetType;
import java.util.Random;

public final class RingBandGenerator {

    public Array<RingBand> generate(float innerKm, float outerKm, PlanetType type, Random rng) {
        Array<RingBand> bands = new Array<>();
        float span    = outerKm - innerKm;
        float current = innerKm;

        while (current < outerKm) {
            float bandWidth = span * RngUtil.range(rng, 0.03f, 0.15f);
            RingBand band   = new RingBand();
            band.innerRadius = current;
            band.outerRadius = Math.min(current + bandWidth, outerKm);

            band.isGap = rng.nextFloat() < 0.12f;
            if (band.isGap) {
                band.density = RngUtil.range(rng, 0f, 0.05f);
                band.colour  = new Color(0.1f, 0.1f, 0.1f, 0.3f);
                band.albedo  = 0.05f;
            } else {
                band.density = RngUtil.range(rng, 0.1f, 0.9f);
                band.colour  = ringColour(type, rng);
                band.albedo  = RngUtil.range(rng, 0.3f, 0.9f);
            }
            bands.add(band);

            // small random gap between bands
            current = band.outerRadius + span * RngUtil.range(rng, 0f, 0.02f);
        }
        return bands;
    }

    private Color ringColour(PlanetType type, Random rng) {
        float iceAmount = (type == PlanetType.ICE_GIANT || type == PlanetType.ICE_WORLD) ? 0.8f : 0.3f;
        if (rng.nextFloat() < iceAmount) {
            float t = rng.nextFloat();
            return new Color(
                MathUtils.lerp(0.8f,  0.6f,  t),
                MathUtils.lerp(0.85f, 0.7f,  t),
                MathUtils.lerp(0.9f,  0.8f,  t),
                1f);
        } else {
            float t = rng.nextFloat();
            return new Color(
                MathUtils.lerp(0.75f, 0.55f, t),
                MathUtils.lerp(0.65f, 0.40f, t),
                MathUtils.lerp(0.45f, 0.25f, t),
                1f);
        }
    }
}
