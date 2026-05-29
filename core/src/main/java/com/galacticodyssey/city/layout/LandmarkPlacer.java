package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.Landmark;
import com.galacticodyssey.city.layout.model.LandmarkType;
import com.galacticodyssey.galaxy.RngUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Places civic centre, spaceport, market plaza, faction landmark; merges authored ones first. */
public final class LandmarkPlacer {
    private LandmarkPlacer() {}

    public static List<Landmark> place(float radius, boolean hasSpaceport,
                                       List<AuthoredLandmark> authored, long citySeed) {
        Random rng = new Random(citySeed ^ 0x1A0D3A2BL);
        List<Landmark> out = new ArrayList<>();

        // 1. Authored landmarks first (preserved verbatim, flagged authored).
        for (AuthoredLandmark a : authored) {
            out.add(new Landmark(a.type, a.position.cpy(), true));
        }

        // 2. Civic centre near origin (unless one was authored).
        if (!hasType(out, LandmarkType.CIVIC_CENTRE)) {
            Vector2 jitter = polar(rng.nextFloat() * MathUtils.PI2,
                                   RngUtil.range(rng, 0f, 0.05f * radius));
            out.add(new Landmark(LandmarkType.CIVIC_CENTRE, jitter, false));
        }

        // 3. Spaceport in outer band.
        Vector2 spaceportPos = null;
        if (hasSpaceport && !hasType(out, LandmarkType.SPACEPORT)) {
            float angle = rng.nextFloat() * MathUtils.PI2;
            float dist = RngUtil.range(rng, 0.65f * radius, 0.85f * radius);
            spaceportPos = polar(angle, dist);
            out.add(new Landmark(LandmarkType.SPACEPORT, spaceportPos, false));
        }

        // 4. Market plaza between centre and spaceport (or its own outer-ish spot).
        if (!hasType(out, LandmarkType.MARKET_PLAZA)) {
            Vector2 centre = positionOf(out, LandmarkType.CIVIC_CENTRE, Vector2.Zero);
            Vector2 toward = spaceportPos != null ? spaceportPos
                    : polar(rng.nextFloat() * MathUtils.PI2, 0.5f * radius);
            float t = RngUtil.range(rng, 0.3f, 0.5f);
            Vector2 market = centre.cpy().lerp(toward, t)
                    .add(polar(rng.nextFloat() * MathUtils.PI2, 0.08f * radius));
            // Guarantee it isn't inside the central plaza void.
            if (market.len() < 0.16f * radius) market.setLength(0.2f * radius);
            out.add(new Landmark(LandmarkType.MARKET_PLAZA, market, false));
        }

        // 5. Faction landmark at mid radius (unless authored).
        if (!hasType(out, LandmarkType.FACTION_LANDMARK)) {
            Vector2 fl = polar(rng.nextFloat() * MathUtils.PI2, RngUtil.range(rng, 0.3f * radius, 0.55f * radius));
            out.add(new Landmark(LandmarkType.FACTION_LANDMARK, fl, false));
        }

        return out;
    }

    private static boolean hasType(List<Landmark> lm, LandmarkType t) {
        for (Landmark l : lm) if (l.type == t) return true;
        return false;
    }

    private static Vector2 positionOf(List<Landmark> lm, LandmarkType t, Vector2 fallback) {
        for (Landmark l : lm) if (l.type == t) return l.position.cpy();
        return fallback.cpy();
    }

    private static Vector2 polar(float angle, float dist) {
        return new Vector2(MathUtils.cos(angle) * dist, MathUtils.sin(angle) * dist);
    }
}
