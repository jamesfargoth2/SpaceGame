package com.galacticodyssey.galaxy.faction;

import com.galacticodyssey.galaxy.RngUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates a symmetric diplomatic-relation graph between factions based on
 * ethos similarity, geographic proximity, and random noise.
 */
public final class PoliticalRelationGraph {

    /**
     * Generates pairwise political relations for all faction pairs.
     * The result is symmetric: relations.get(a).get(b) == relations.get(b).get(a).
     */
    public Map<String, Map<String, PoliticalRelation>> generate(List<FactionData> factions, Random rng) {
        Map<String, Map<String, PoliticalRelation>> result = new HashMap<>();

        // Initialise maps
        for (FactionData f : factions) {
            result.put(f.id, new HashMap<>());
        }

        for (int i = 0; i < factions.size(); i++) {
            FactionData a = factions.get(i);
            for (int j = i + 1; j < factions.size(); j++) {
                FactionData b = factions.get(j);

                float affinity = computeAffinity(a, b, rng);
                PoliticalRelation relation = affinityToRelation(affinity);

                result.get(a.id).put(b.id, relation);
                result.get(b.id).put(a.id, relation);
            }
        }

        return result;
    }

    private float computeAffinity(FactionData a, FactionData b, Random rng) {
        // Ethos similarity: +0.4 if same, -0.1 otherwise
        float ethosFactor = (a.ethos == b.ethos) ? 0.4f : -0.1f;

        // Proximity penalty: closer factions have more friction
        double dx = a.capitalX - b.capitalX;
        double dy = a.capitalY - b.capitalY;
        double dz = a.capitalZ - b.capitalZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Normalise distance to [0, 1] using 2000 LY as the reference max distance
        float normDist = (float) Math.min(dist / 2000.0, 1.0);
        // Proximity penalty: -1 when right on top of each other, 0 when far apart
        float proximityPenalty = -(1.0f - normDist);

        // Random noise
        float noise = RngUtil.range(rng, -0.2f, 0.2f);

        return ethosFactor + proximityPenalty + noise;
    }

    private PoliticalRelation affinityToRelation(float affinity) {
        if (affinity > 0.7f) return PoliticalRelation.ALLIED;
        if (affinity > 0.4f) return PoliticalRelation.FRIENDLY;
        if (affinity > 0.1f) return PoliticalRelation.NEUTRAL;
        if (affinity > -0.2f) return PoliticalRelation.TENSE;
        if (affinity > -0.5f) return PoliticalRelation.HOSTILE;
        return PoliticalRelation.WAR;
    }
}
