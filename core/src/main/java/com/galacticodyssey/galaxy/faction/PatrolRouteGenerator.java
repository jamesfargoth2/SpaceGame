package com.galacticodyssey.galaxy.faction;

import com.galacticodyssey.galaxy.RngUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates patrol routes for a faction's territory by sampling waypoints
 * from its owned star systems. Route count scales with territory size and
 * military strength.
 */
public final class PatrolRouteGenerator {

    /**
     * Generates patrol routes for the given faction.
     *
     * @param faction            the faction whose patrols to generate
     * @param territoryStarIds   IDs of stars this faction owns (used only for count)
     * @param starXs             x-coordinates indexed by territory star order
     * @param starYs             y-coordinates indexed by territory star order
     * @param starZs             z-coordinates indexed by territory star order
     * @param rng                seeded RNG
     * @return list of patrol routes
     */
    public List<PatrolRoute> generate(FactionData faction,
                                       long[] territoryStarIds,
                                       double[] starXs, double[] starYs, double[] starZs,
                                       Random rng) {
        if (territoryStarIds.length == 0) {
            return List.of();
        }

        int routeCount = Math.max(1,
                (int) (territoryStarIds.length * 0.2f * faction.militaryStrength));

        boolean isAggressive = faction.ethos == FactionEthos.MILITARIST;

        List<PatrolRoute> routes = new ArrayList<>(routeCount);
        for (int r = 0; r < routeCount; r++) {
            int waypointCount = RngUtil.range(rng, 3, 7); // 3-6 inclusive
            waypointCount = Math.min(waypointCount, territoryStarIds.length);

            double[] wxs = new double[waypointCount];
            double[] wys = new double[waypointCount];
            double[] wzs = new double[waypointCount];

            for (int w = 0; w < waypointCount; w++) {
                int idx = rng.nextInt(territoryStarIds.length);
                wxs[w] = starXs[idx];
                wys[w] = starYs[idx];
                wzs[w] = starZs[idx];
            }

            routes.add(new PatrolRoute(wxs, wys, wzs, isAggressive));
        }

        return routes;
    }
}
