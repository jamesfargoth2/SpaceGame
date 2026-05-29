package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.fauna.behavior.*;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class PopulationTickSystemTest {

    private SpeciesDef makeHerbivore(String id, int k) {
        SpeciesDef s = new SpeciesDef();
        s.id = id;
        s.diet = Diet.HERBIVORE;
        s.birthRate = 0.1f;
        s.carryingCapacityBase = k;
        s.trophicLevel = 1;
        s.biomeAffinities.put(BiomeType.GRASSLAND, 1.0f);
        return s;
    }

    private SpeciesDef makePredator(String id, int k, String... prey) {
        SpeciesDef s = new SpeciesDef();
        s.id = id;
        s.diet = Diet.CARNIVORE;
        s.birthRate = 0.05f;
        s.carryingCapacityBase = k;
        s.trophicLevel = 2;
        s.biomeAffinities.put(BiomeType.GRASSLAND, 1.0f);
        s.preySpecies.addAll(Arrays.asList(prey));
        return s;
    }

    @Test
    void logisticGrowthIncreasesPopulation() {
        SpeciesDef herb = makeHerbivore("herb", 50);
        Map<String, SpeciesDef> speciesMap = new HashMap<>();
        speciesMap.put("herb", herb);

        ChunkPopulationRecord chunk = new ChunkPopulationRecord();
        chunk.biome = BiomeType.GRASSLAND;
        chunk.populations.add(new SpeciesPopulation("herb", 10));

        PopulationTickSystem.tickChunk(chunk, speciesMap, 30f);
        assertTrue(chunk.populations.get(0).count >= 10);
    }

    @Test
    void populationDecaysAboveCarryingCapacity() {
        SpeciesDef herb = makeHerbivore("herb", 20);
        Map<String, SpeciesDef> speciesMap = new HashMap<>();
        speciesMap.put("herb", herb);

        ChunkPopulationRecord chunk = new ChunkPopulationRecord();
        chunk.biome = BiomeType.GRASSLAND;
        chunk.populations.add(new SpeciesPopulation("herb", 30));

        PopulationTickSystem.tickChunk(chunk, speciesMap, 60f);
        assertTrue(chunk.populations.get(0).count < 30);
    }

    @Test
    void predationReducesPreyPopulation() {
        SpeciesDef herb = makeHerbivore("herb", 50);
        SpeciesDef pred = makePredator("pred", 10, "herb");
        Map<String, SpeciesDef> speciesMap = new HashMap<>();
        speciesMap.put("herb", herb);
        speciesMap.put("pred", pred);

        ChunkPopulationRecord chunk = new ChunkPopulationRecord();
        chunk.biome = BiomeType.GRASSLAND;
        chunk.populations.add(new SpeciesPopulation("herb", 40));
        chunk.populations.add(new SpeciesPopulation("pred", 5));

        int preyBefore = chunk.populations.get(0).count;
        PopulationTickSystem.tickChunk(chunk, speciesMap, 60f);
        assertTrue(chunk.populations.get(0).count <= preyBefore);
    }

    @Test
    void populationNeverGoesNegative() {
        SpeciesDef herb = makeHerbivore("herb", 5);
        Map<String, SpeciesDef> speciesMap = new HashMap<>();
        speciesMap.put("herb", herb);

        ChunkPopulationRecord chunk = new ChunkPopulationRecord();
        chunk.biome = BiomeType.GRASSLAND;
        chunk.populations.add(new SpeciesPopulation("herb", 1));

        PopulationTickSystem.tickChunk(chunk, speciesMap, 1000f);
        assertTrue(chunk.populations.get(0).count >= 0);
    }
}
