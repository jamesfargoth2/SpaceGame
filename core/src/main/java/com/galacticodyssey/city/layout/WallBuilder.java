package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.ConvexHull;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.FloatArray;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.CityGate;
import com.galacticodyssey.city.layout.model.CityWall;

import java.util.ArrayList;
import java.util.List;

/** Builds a convex-hull wall around all block corners and cuts gates where the cardinal
 *  axes (the main avenue centrelines through the origin) pierce the hull. */
public final class WallBuilder {
    private WallBuilder() {}

    public static CityWall build(List<CityBlock> blocks) {
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

        float far = farthest(hull) * 2f + 10f;
        addGateAlongRay(wall, new Vector2(0, 0), new Vector2(far, 0));   // +X
        addGateAlongRay(wall, new Vector2(0, 0), new Vector2(-far, 0));  // -X
        addGateAlongRay(wall, new Vector2(0, 0), new Vector2(0, far));   // +Y
        addGateAlongRay(wall, new Vector2(0, 0), new Vector2(0, -far));  // -Y
        return wall;
    }

    private static float farthest(List<Vector2> hull) {
        float m = 0f;
        for (Vector2 v : hull) m = Math.max(m, v.len());
        return m;
    }

    private static void addGateAlongRay(CityWall wall, Vector2 from, Vector2 to) {
        List<Vector2> hull = wall.hull;
        Vector2 hit = new Vector2();
        for (int i = 0; i < hull.size(); i++) {
            Vector2 a = hull.get(i);
            Vector2 b = hull.get((i + 1) % hull.size());
            if (Intersector.intersectSegments(from, to, a, b, hit)) {
                wall.gates.add(new CityGate(new Vector2(hit)));
                return;
            }
        }
    }
}
