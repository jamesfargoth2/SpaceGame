package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.planet.BiomeType;
import java.util.Map;

public final class PopulationTickSystem {

    private PopulationTickSystem() {}

    private static final float STARVATION_RATE = 0.05f;
    private static final float ATTACK_RATE = 0.002f;

    public static void tickChunk(ChunkPopulationRecord chunk, Map<String, SpeciesDef> speciesMap, float dt) {
        for (SpeciesPopulation pop : chunk.populations) {
            SpeciesDef species = speciesMap.get(pop.speciesId);
            if (species == null || pop.count <= 0) continue;

            float fertility = biomeFertility(chunk.biome, species);
            float K = species.carryingCapacityBase * fertility;
            if (K < 1f) K = 1f;

            float growth = species.birthRate * pop.count * (1f - pop.count / K) * dt;
            pop.birthAccumulator += growth;

            while (pop.birthAccumulator >= 1f) {
                pop.count++;
                pop.birthAccumulator -= 1f;
            }

            if (pop.count > K * 1.2f) {
                float deaths = STARVATION_RATE * (pop.count - K) * dt;
                pop.count -= (int) deaths;
            }

            if (species.diet == com.galacticodyssey.fauna.behavior.Diet.CARNIVORE
                || species.diet == com.galacticodyssey.fauna.behavior.Diet.OMNIVORE) {
                for (String preyId : species.preySpecies) {
                    SpeciesPopulation preyPop = findPop(chunk, preyId);
                    if (preyPop != null && preyPop.count > 0) {
                        float consumed = ATTACK_RATE * pop.count * preyPop.count * dt;
                        int kills = Math.min((int) consumed, preyPop.count);
                        preyPop.count -= kills;
                    }
                }
            }

            pop.count = Math.max(0, pop.count);
        }
    }

    private static SpeciesPopulation findPop(ChunkPopulationRecord chunk, String speciesId) {
        for (SpeciesPopulation p : chunk.populations) {
            if (p.speciesId.equals(speciesId)) return p;
        }
        return null;
    }

    private static float biomeFertility(BiomeType biome, SpeciesDef species) {
        Float affinity = species.biomeAffinities.get(biome);
        return affinity != null ? affinity : 0f;
    }
}
