package com.galacticodyssey.planet.rings;

import com.galacticodyssey.planet.PlanetType;
import java.util.Random;

public final class RingPresenceRoller {

    public boolean hasRings(PlanetType type, float planetAgeGyr, Random rng) {
        float chance = switch (type) {
            case GAS_GIANT  -> 0.75f;
            case ICE_GIANT  -> 0.50f;
            case BARREN     -> 0.05f;
            default         -> 0f;
        };
        if (chance == 0f) return false;
        // Young planets (< 2 Gyr) retain more ring material
        if (planetAgeGyr < 2f) chance = Math.min(1f, chance * 1.3f);
        return rng.nextFloat() < chance;
    }
}
