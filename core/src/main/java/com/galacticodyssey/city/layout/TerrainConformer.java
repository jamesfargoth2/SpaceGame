package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Rectangle;
import com.galacticodyssey.city.layout.model.BuildingLot;
import com.galacticodyssey.city.layout.model.Street;

import java.util.Iterator;
import java.util.List;

/** Removes streets crossing water/steep slope and lots mostly on inaccessible ground. */
public final class TerrainConformer {
    private static final float MAX_SLOPE = 0.577f;      // tan(30 degrees)
    private static final float MAX_INACCESSIBLE = 0.40f;

    private TerrainConformer() {}

    public static void conform(List<Street> streets, List<BuildingLot> lots, TerrainSampler terrain) {
        Iterator<Street> sit = streets.iterator();
        while (sit.hasNext()) {
            Street s = sit.next();
            if (streetIsBad(s, terrain)) sit.remove();
        }
        Iterator<BuildingLot> lit = lots.iterator();
        while (lit.hasNext()) {
            BuildingLot lot = lit.next();
            if (lotFractionInaccessible(lot.footprint, terrain) > MAX_INACCESSIBLE) lit.remove();
        }
    }

    private static boolean streetIsBad(Street s, TerrainSampler t) {
        float[][] pts = {
            {s.start.x, s.start.y},
            {s.end.x, s.end.y},
            {(s.start.x + s.end.x) / 2f, (s.start.y + s.end.y) / 2f}
        };
        for (float[] p : pts) {
            if (t.isWater(p[0], p[1]) || t.slopeAt(p[0], p[1]) > MAX_SLOPE) return true;
        }
        return false;
    }

    private static float lotFractionInaccessible(Rectangle r, TerrainSampler t) {
        int bad = 0, total = 0;
        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) {
                float x = r.x + r.width * (i / 2f);
                float y = r.y + r.height * (j / 2f);
                total++;
                if (t.isWater(x, y) || t.slopeAt(x, y) > MAX_SLOPE) bad++;
            }
        }
        return (float) bad / total;
    }
}
