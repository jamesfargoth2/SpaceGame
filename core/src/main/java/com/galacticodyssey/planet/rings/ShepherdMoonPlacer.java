package com.galacticodyssey.planet.rings;

import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.galaxy.RngUtil;
import java.util.Random;

public final class ShepherdMoonPlacer {

    public Array<ShepherdMoon> place(Array<RingBand> bands, float rocheLimit, Random rng) {
        Array<ShepherdMoon> moons = new Array<>();

        float outerEdge = bands.get(bands.size - 1).outerRadius;
        if (rng.nextFloat() < 0.6f) {
            ShepherdMoon outer = new ShepherdMoon();
            outer.orbitRadiusKm = outerEdge * RngUtil.range(rng, 1.02f, 1.08f);
            outer.radiusKm      = RngUtil.range(rng, 5f, 150f);
            outer.side          = "outer";
            if (outer.orbitRadiusKm > rocheLimit) moons.add(outer);
        }

        float innerEdge = bands.get(0).innerRadius;
        if (rng.nextFloat() < 0.4f) {
            ShepherdMoon inner = new ShepherdMoon();
            inner.orbitRadiusKm = innerEdge * RngUtil.range(rng, 0.92f, 0.98f);
            inner.radiusKm      = RngUtil.range(rng, 5f, 100f);
            inner.side          = "inner";
            if (inner.orbitRadiusKm > rocheLimit) moons.add(inner);
        }

        return moons;
    }
}
