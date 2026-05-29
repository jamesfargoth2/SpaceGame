package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.city.layout.model.Street;
import com.galacticodyssey.city.layout.model.StreetTier;

import java.util.Random;

/**
 * Builds an axis-aligned cell grid of blocks (within a disk of {@code radius}) plus the
 * streets between them. {@link CityForm} modulates the grid (see spec v1 simplification).
 */
public final class StreetNetworkBuilder {
    private static final float MAX_BLOCK = 60f;
    private static final float MIN_BLOCK = 20f;
    private static final float STREET_WIDTH = 8f;

    private StreetNetworkBuilder() {}

    public static StreetNetwork build(CityForm form, float radius, float density, long citySeed) {
        StreetNetwork net = new StreetNetwork();
        Random rng = new Random(citySeed ^ 0x57BEE7A1L);

        float baseBlock = MathUtils.lerp(MAX_BLOCK, MIN_BLOCK, density);
        float spacing = baseBlock + STREET_WIDTH;
        int half = (int) Math.ceil(radius / spacing) + 1;

        // LINEAR strip half-width (perpendicular to the long X axis).
        float stripHalf = radius * 0.32f;

        for (int i = -half; i <= half; i++) {
            for (int j = -half; j <= half; j++) {
                float cx = i * spacing;
                float cy = j * spacing;
                float dist = (float) Math.sqrt(cx * cx + cy * cy);
                if (dist > radius) continue;

                // RADIAL: leave a central plaza void for the civic landmark.
                if (form == CityForm.RADIAL && dist < radius * 0.1f) continue;
                // LINEAR: clip to an elongated strip along X.
                if (form == CityForm.LINEAR && Math.abs(cy) > stripHalf) continue;
                // SPRAWL: drop a fraction of peripheral cells for an irregular edge.
                if (form == CityForm.SPRAWL && dist > radius * 0.6f && rng.nextFloat() < 0.25f) continue;
                // ORGANIC: drop a small fraction of cells anywhere.
                if (form == CityForm.ORGANIC && rng.nextFloat() < 0.1f) continue;

                // Per-cell block size: RADIAL shrinks toward the centre.
                float blockSize = baseBlock;
                if (form == CityForm.RADIAL) {
                    float t = MathUtils.clamp(dist / radius, 0f, 1f);
                    blockSize = MathUtils.lerp(MIN_BLOCK, baseBlock, t);
                }

                Rectangle rect = new Rectangle(cx - blockSize / 2f, cy - blockSize / 2f,
                                               blockSize, blockSize);

                // ORGANIC: jitter the footprint slightly (kept inside the cell gap).
                if (form == CityForm.ORGANIC) {
                    float jx = (rng.nextFloat() - 0.5f) * STREET_WIDTH * 0.5f;
                    float jy = (rng.nextFloat() - 0.5f) * STREET_WIDTH * 0.5f;
                    rect.x += jx;
                    rect.y += jy;
                }
                net.blocks.add(new CityBlock(rect));
            }
        }

        // Streets: grid lines spanning the disk; every 3rd line is an AVENUE.
        for (int i = -half; i <= half; i++) {
            float p = i * spacing - spacing / 2f; // street runs in the gap before cell i
            float ext = chordHalfLength(radius, p);
            if (ext <= 0f) continue;
            StreetTier tier = (i % 3 == 0) ? StreetTier.AVENUE : StreetTier.STREET;
            net.streets.add(new Street(new Vector2(p, -ext), new Vector2(p, ext), tier));
            net.streets.add(new Street(new Vector2(-ext, p), new Vector2(ext, p), tier));
        }

        return net;
    }

    /** Half-length of the chord of a circle of radius r at perpendicular offset |p|. */
    private static float chordHalfLength(float r, float p) {
        float d = r * r - p * p;
        return d <= 0f ? 0f : (float) Math.sqrt(d);
    }
}
