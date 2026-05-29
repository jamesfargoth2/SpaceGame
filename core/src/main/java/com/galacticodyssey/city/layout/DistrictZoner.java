package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.DistrictType;
import com.galacticodyssey.city.layout.model.Landmark;
import com.galacticodyssey.city.layout.model.LandmarkType;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.List;
import java.util.Random;

/** Assigns a DistrictType to each block via centre-out gradient + landmark adjacency. */
public final class DistrictZoner {
    private static final float LANDMARK_ADJ_RADIUS = 30f;

    private DistrictZoner() {}

    public static void zone(List<CityBlock> blocks, List<Landmark> landmarks,
                            float radius, long citySeed) {
        Random rng = new Random(SeedDeriver.forId(citySeed, 0xD15741C7L));
        for (CityBlock block : blocks) {
            Vector2 c = block.centroid();

            Landmark adj = nearestLandmark(c, landmarks);
            if (adj != null) {
                block.district = landmarkZone(adj.type);
                continue;
            }
            float t = MathUtils.clamp(c.len() / radius, 0f, 1f);
            block.district = rollByDepth(t, rng);
        }
    }

    private static Landmark nearestLandmark(Vector2 c, List<Landmark> landmarks) {
        for (Landmark l : landmarks) {
            if (c.dst(l.position) <= LANDMARK_ADJ_RADIUS) return l;
        }
        return null;
    }

    private static DistrictType landmarkZone(LandmarkType t) {
        switch (t) {
            case SPACEPORT:        return DistrictType.SPACEPORT;
            case CIVIC_CENTRE:     return DistrictType.GOVERNMENT;
            case MARKET_PLAZA:     return DistrictType.COMMERCIAL;
            case FACTION_LANDMARK: return DistrictType.GOVERNMENT;
            default:               return DistrictType.GOVERNMENT;
        }
    }

    private static DistrictType rollByDepth(float t, Random rng) {
        if (t < 0.15f) return DistrictType.GOVERNMENT;
        if (t < 0.30f) {
            float r = rng.nextFloat();
            if (r < 0.40f) return DistrictType.COMMERCIAL;
            if (r < 0.70f) return DistrictType.RESIDENTIAL;
            return DistrictType.RELIGIOUS;
        }
        if (t < 0.55f) {
            float r = rng.nextFloat();
            if (r < 0.35f) return DistrictType.RESIDENTIAL;
            if (r < 0.60f) return DistrictType.COMMERCIAL;
            if (r < 0.75f) return DistrictType.INDUSTRIAL;
            return DistrictType.GARDEN;
        }
        if (t < 0.80f) {
            float r = rng.nextFloat();
            if (r < 0.40f) return DistrictType.INDUSTRIAL;
            if (r < 0.65f) return DistrictType.RESIDENTIAL;
            if (r < 0.80f) return DistrictType.SLUMS;
            return DistrictType.MILITARY;
        }
        float r = rng.nextFloat();
        if (r < 0.45f) return DistrictType.SLUMS;
        if (r < 0.70f) return DistrictType.INDUSTRIAL;
        return DistrictType.MILITARY;
    }
}
