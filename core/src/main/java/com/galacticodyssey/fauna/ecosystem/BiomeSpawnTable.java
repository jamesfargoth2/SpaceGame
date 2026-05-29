package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.planet.BiomeType;
import java.util.*;

public final class BiomeSpawnTable {

    public static final class WeightedSpecies {
        public final SpeciesDef species;
        public final float weight;
        public WeightedSpecies(SpeciesDef species, float weight) {
            this.species = species;
            this.weight = weight;
        }
    }

    private final Map<BiomeType, List<WeightedSpecies>> table = new EnumMap<>(BiomeType.class);

    public BiomeSpawnTable(Collection<SpeciesDef> allSpecies) {
        for (BiomeType biome : BiomeType.values()) {
            List<WeightedSpecies> eligible = new ArrayList<>();
            for (SpeciesDef s : allSpecies) {
                Float affinity = s.biomeAffinities.get(biome);
                if (affinity != null && affinity > 0f) {
                    eligible.add(new WeightedSpecies(s, affinity));
                }
            }
            eligible.sort((a, b) -> a.species.id.compareTo(b.species.id));
            table.put(biome, Collections.unmodifiableList(eligible));
        }
    }

    public List<WeightedSpecies> speciesForBiome(BiomeType biome) {
        List<WeightedSpecies> result = table.get(biome);
        return result != null ? result : Collections.emptyList();
    }
}
