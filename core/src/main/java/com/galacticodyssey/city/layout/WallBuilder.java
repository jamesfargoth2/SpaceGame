package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.ConvexHull;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.FloatArray;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.CityGate;
import com.galacticodyssey.city.layout.model.CityWall;
import com.galacticodyssey.city.layout.model.Street;
import com.galacticodyssey.city.layout.model.StreetTier;

import java.util.ArrayList;
import java.util.List;

/** Builds a convex-hull wall around all block corners and cuts gates where AVENUE-tier
 *  streets pierce the hull perimeter. */
public final class WallBuilder {
    private WallBuilder() {}

    public static CityWall build(List<CityBlock> blocks, List<Street> streets) {
        FloatArray pts = new FloatArray();
        for (CityBlock b : blocks) {
            pts.add(b.footprint.x);                       pts.add(b.footprint.y);
            pts.add(b.footprint.x + b.footprint.width);   pts.add(b.footprint.y);
            pts.add(b.footprint.x);                       pts.add(b.footprint.y + b.footprint.height);
            pts.add(b.footprint.x + b.footprint.width);   pts.add(b.footprint.y + b.footprint.height);
        }
        // ConvexHull.computePolygon requires a sorted flag; false = it sorts internally.
        FloatArray hullPts = new ConvexHull().computePolygon(pts, false);
        List<Vector2> hull = new ArrayList<>();
        // computePolygon repeats the first point at the end; drop the duplicate.
        for (int i = 0; i < hullPts.size - 2; i += 2) {
            hull.add(new Vector2(hullPts.get(i), hullPts.get(i + 1)));
        }
        if (hull.size() < 3) {
            throw new IllegalArgumentException(
                "WallBuilder: convex hull degenerate (" + hull.size()
                + " vertices) - blocks must span at least two dimensions");
        }

        CityWall wall = new CityWall(hull);

        for (Street s : streets) {
            if (s.tier != StreetTier.AVENUE) continue;
            addGatesWhereSegmentCrossesHull(wall, s.start, s.end);
        }
        return wall;
    }

    private static void addGatesWhereSegmentCrossesHull(CityWall wall, Vector2 from, Vector2 to) {
        List<Vector2> hull = wall.hull;
        Vector2 hit = new Vector2();
        for (int i = 0; i < hull.size(); i++) {
            Vector2 a = hull.get(i);
            Vector2 b = hull.get((i + 1) % hull.size());
            if (Intersector.intersectSegments(from, to, a, b, hit)) {
                addGateDeduped(wall, hit);
            }
        }
    }

    private static void addGateDeduped(CityWall wall, Vector2 point) {
        for (CityGate g : wall.gates) {
            if (g.position.dst(point) <= 0.5f) return; // already have a gate here
        }
        wall.gates.add(new CityGate(new Vector2(point)));
    }
}
