package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.behavior.*;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeciesLoadingTest {

    private FaunaDataRegistry reg;

    @BeforeEach
    void setUp() {
        reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso\",\"partType\":\"TORSO\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
          "  \"sockets\":[ {\"id\":\"lf\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,0.6],\"mirrorGroup\":\"front\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0],\"jointHint\":\"neck\"} ] }," +
          "{ \"id\":\"leg\",\"partType\":\"LIMB_LEG\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"quad\",\"bodyPlan\":\"QUADRUPED\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[" +
          "     {\"socketId\":\"lf\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
    }

    @Test
    void loadsSpeciesFromJson() {
        reg.loadSpeciesFromJson("{ \"species\":[" +
          "{ \"id\":\"grazer\",\"archetypeId\":\"quad\",\"diet\":\"HERBIVORE\"," +
          "  \"temperament\":\"TIMID\",\"socialStructure\":\"HERD\"," +
          "  \"herdSizeMin\":4,\"herdSizeMax\":12," +
          "  \"biomes\":{\"GRASSLAND\":1.0,\"SAVANNA\":0.8}," +
          "  \"trophicLevel\":1,\"preySpecies\":[]," +
          "  \"activityCycle\":\"DIURNAL\"," +
          "  \"detectionRadius\":25,\"fleeRadius\":15,\"fleeSpeedMultiplier\":1.5,\"safeDistance\":40," +
          "  \"birthRate\":0.02,\"carryingCapacityBase\":30 }" +
          "] }");
        reg.validate();

        SpeciesDef s = reg.getSpecies("grazer");
        assertNotNull(s);
        assertEquals("quad", s.archetypeId);
        assertEquals(Diet.HERBIVORE, s.diet);
        assertEquals(Temperament.TIMID, s.temperament);
        assertEquals(SocialStructure.HERD, s.socialStructure);
        assertEquals(4, s.herdSizeMin);
        assertEquals(12, s.herdSizeMax);
        assertEquals(1.0f, s.biomeAffinities.get(BiomeType.GRASSLAND), 0.01f);
        assertEquals(0.8f, s.biomeAffinities.get(BiomeType.SAVANNA), 0.01f);
    }

    @Test
    void validationFailsForUnknownArchetype() {
        reg.loadSpeciesFromJson("{ \"species\":[" +
          "{ \"id\":\"bad\",\"archetypeId\":\"nonexistent\",\"diet\":\"HERBIVORE\"," +
          "  \"biomes\":{\"GRASSLAND\":1.0},\"trophicLevel\":1,\"preySpecies\":[] }" +
          "] }");
        assertThrows(IllegalStateException.class, () -> reg.validate());
    }

    @Test
    void allSpeciesReturnsList() {
        reg.loadSpeciesFromJson("{ \"species\":[" +
          "{ \"id\":\"a\",\"archetypeId\":\"quad\",\"diet\":\"HERBIVORE\"," +
          "  \"biomes\":{\"GRASSLAND\":1.0},\"trophicLevel\":1,\"preySpecies\":[] }," +
          "{ \"id\":\"b\",\"archetypeId\":\"quad\",\"diet\":\"CARNIVORE\"," +
          "  \"biomes\":{\"GRASSLAND\":1.0},\"trophicLevel\":2,\"preySpecies\":[\"a\"] }" +
          "] }");
        reg.validate();
        assertEquals(2, reg.allSpecies().size());
    }
}
