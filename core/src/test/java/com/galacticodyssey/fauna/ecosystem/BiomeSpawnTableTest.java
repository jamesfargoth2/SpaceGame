package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.List;

class BiomeSpawnTableTest {

    private BiomeSpawnTable table;

    @BeforeEach
    void setUp() {
        SpeciesDef grazer = new SpeciesDef();
        grazer.id = "grazer";
        grazer.biomeAffinities.put(BiomeType.GRASSLAND, 1.0f);
        grazer.biomeAffinities.put(BiomeType.SAVANNA, 0.5f);

        SpeciesDef predator = new SpeciesDef();
        predator.id = "predator";
        predator.biomeAffinities.put(BiomeType.GRASSLAND, 0.8f);

        SpeciesDef aquatic = new SpeciesDef();
        aquatic.id = "aquatic";
        aquatic.biomeAffinities.put(BiomeType.OCEAN, 1.0f);

        table = new BiomeSpawnTable(Arrays.asList(grazer, predator, aquatic));
    }

    @Test
    void grasslandHasTwoEligibleSpecies() {
        assertEquals(2, table.speciesForBiome(BiomeType.GRASSLAND).size());
    }

    @Test
    void oceanHasOnlyAquatic() {
        List<BiomeSpawnTable.WeightedSpecies> eligible = table.speciesForBiome(BiomeType.OCEAN);
        assertEquals(1, eligible.size());
        assertEquals("aquatic", eligible.get(0).species.id);
    }

    @Test
    void desertHasNoSpecies() {
        assertTrue(table.speciesForBiome(BiomeType.DESERT).isEmpty());
    }

    @Test
    void weightsMatchAffinities() {
        for (BiomeSpawnTable.WeightedSpecies ws : table.speciesForBiome(BiomeType.GRASSLAND)) {
            if (ws.species.id.equals("grazer")) assertEquals(1.0f, ws.weight, 0.01f);
            if (ws.species.id.equals("predator")) assertEquals(0.8f, ws.weight, 0.01f);
        }
    }
}
