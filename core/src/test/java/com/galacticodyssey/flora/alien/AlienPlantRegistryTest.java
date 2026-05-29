package com.galacticodyssey.flora.alien;

import com.galacticodyssey.flora.data.BiomePalette;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlienPlantRegistryTest {
    private static final String JSON = "{ \"species\": [\n" +
      "  { \"id\": \"glowcap\", \"archetype\": \"BIOLUMINESCENT\",\n" +
      "    \"stalk\": { \"height\": [1.5,3.0], \"baseRadius\": 0.12, \"taper\": 0.7, \"sides\": 6, \"color\": \"2a2f4a\" },\n" +
      "    \"canopy\": { \"clumps\": [3,6], \"radius\": [0.4,0.8], \"color\": \"2fd0c0\", \"emissive\": 3.0 },\n" +
      "    \"details\": { \"count\": [3,8], \"emissive\": 2.0 }, \"prototypeVariants\": 6 },\n" +
      "  { \"id\": \"maw\", \"archetype\": \"CARNIVOROUS\",\n" +
      "    \"stalk\": { \"height\": [0.8,1.6], \"baseRadius\": 0.15, \"taper\": 0.85, \"sides\": 6, \"color\": \"3a2a1f\" },\n" +
      "    \"canopy\": { \"mouthRadius\": [0.5,0.9], \"depth\": [0.6,1.1], \"color\": \"5a1f28\", \"lureEmissive\": 2.5 },\n" +
      "    \"details\": { \"teeth\": [5,9] }, \"prototypeVariants\": 5 },\n" +
      "  { \"id\": \"shard\", \"archetype\": \"CRYSTAL\",\n" +
      "    \"stalk\": { \"height\": [0.4,1.0], \"baseRadius\": 0.2, \"taper\": 0.9, \"sides\": 5, \"color\": \"404a6a\" },\n" +
      "    \"canopy\": { \"shards\": [4,8], \"length\": [0.6,1.6], \"color\": \"8ad0ff\", \"emissive\": 0.6 },\n" +
      "    \"details\": { \"subShards\": [2,5] }, \"prototypeVariants\": 6 }\n" +
      "] }";
    private static final String PALETTE = "{ \"palette\": [\n" +
      "  { \"biome\": \"SWAMP\", \"density\": 0.5, \"species\": [ {\"id\":\"glowcap\",\"weight\":0.7}, {\"id\":\"maw\",\"weight\":0.3} ] },\n" +
      "  { \"biome\": \"VOLCANIC\", \"density\": 0.3, \"species\": [ {\"id\":\"shard\",\"weight\":1.0} ] }\n" +
      "] }";

    @Test
    void loadsSpeciesAllArchetypes() {
        AlienPlantRegistry reg = new AlienPlantRegistry();
        reg.loadSpecies(JSON);
        AlienPlantSpecies g = reg.species("glowcap");
        assertEquals(AlienArchetype.BIOLUMINESCENT, g.archetype);
        assertEquals(1.5f, g.stalkHeightMin); assertEquals(3.0f, g.stalkHeightMax);
        assertEquals(6, g.stalkSides);
        assertEquals(3, g.clumpsMin); assertEquals(6, g.clumpsMax);
        assertEquals(3.0f, g.canopyEmissive);
        assertEquals(0x2f / 255f, g.canopyColor.r, 0.01f);
        assertEquals(6, g.prototypeVariants);

        assertEquals(AlienArchetype.CARNIVOROUS, reg.species("maw").archetype);
        assertEquals(2.5f, reg.species("maw").lureEmissive);
        assertEquals(5, reg.species("maw").teethMin);

        assertEquals(AlienArchetype.CRYSTAL, reg.species("shard").archetype);
        assertEquals(4, reg.species("shard").shardsMin);
        assertEquals(0.6f, reg.species("shard").canopyEmissive);

        assertNull(reg.species("nope"));
    }

    @Test
    void loadsPalette() {
        AlienPlantRegistry reg = new AlienPlantRegistry();
        reg.loadPalette(PALETTE);
        BiomePalette p = reg.palette(BiomeType.SWAMP);
        assertNotNull(p);
        assertEquals(0.5f, p.density);
        assertEquals(2, p.entries.size());
        assertNull(reg.palette(BiomeType.DESERT));
    }
}
