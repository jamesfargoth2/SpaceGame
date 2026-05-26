package com.galacticodyssey.galaxy.faction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns stars to faction territories using weighted Voronoi partitioning.
 * Distance is divided by military strength, with a hard cutoff at each
 * faction's influence radius.
 */
public final class TerritoryAssigner {

    /**
     * Assigns each star to the faction whose weighted distance is lowest,
     * provided the star is within that faction's influence radius.
     *
     * @return map from starId to factionId; stars outside all influence radii are absent
     */
    public Map<Long, String> assign(List<FactionData> factions,
                                     long[] starIds,
                                     double[] starXs, double[] starYs, double[] starZs) {
        Map<Long, String> assignment = new HashMap<>();

        for (int s = 0; s < starIds.length; s++) {
            double sx = starXs[s];
            double sy = starYs[s];
            double sz = starZs[s];

            String bestFaction = null;
            double bestWeighted = Double.MAX_VALUE;

            for (FactionData faction : factions) {
                double dx = sx - faction.capitalX;
                double dy = sy - faction.capitalY;
                double dz = sz - faction.capitalZ;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                // Hard cutoff at influence radius
                if (dist > faction.influenceRadiusLY) {
                    continue;
                }

                // Weighted distance: lower military strength = effectively farther away
                double weighted = dist / faction.militaryStrength;
                if (weighted < bestWeighted) {
                    bestWeighted = weighted;
                    bestFaction = faction.id;
                }
            }

            if (bestFaction != null) {
                assignment.put(starIds[s], bestFaction);
            }
        }

        return assignment;
    }

    /**
     * Finds stars that are "contested" -- owned by one faction but within
     * {@code thresholdLY} light-years of a star owned by a different faction.
     */
    public List<Long> findContestedSystems(Map<Long, String> assignment,
                                            long[] starIds,
                                            double[] starXs, double[] starYs, double[] starZs,
                                            float thresholdLY) {
        List<Long> contested = new ArrayList<>();
        double thresholdSq = (double) thresholdLY * thresholdLY;

        for (int i = 0; i < starIds.length; i++) {
            String ownerI = assignment.get(starIds[i]);
            if (ownerI == null) continue;

            for (int j = 0; j < starIds.length; j++) {
                if (i == j) continue;
                String ownerJ = assignment.get(starIds[j]);
                if (ownerJ == null || ownerJ.equals(ownerI)) continue;

                double dx = starXs[i] - starXs[j];
                double dy = starYs[i] - starYs[j];
                double dz = starZs[i] - starZs[j];
                double distSq = dx * dx + dy * dy + dz * dz;

                if (distSq <= thresholdSq) {
                    contested.add(starIds[i]);
                    break; // this star is contested, no need to check more neighbours
                }
            }
        }

        return contested;
    }
}
