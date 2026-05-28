package com.galacticodyssey.fauna;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.fauna.part.PartType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

class DefaultFaunaContentTest {

    private FaunaDataRegistry loadDefaults() throws Exception {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        String parts = new String(Files.readAllBytes(
            Paths.get("src/main/resources/data/fauna/parts/default-parts.json")));
        String arches = new String(Files.readAllBytes(
            Paths.get("src/main/resources/data/fauna/archetypes/default-archetypes.json")));
        reg.loadPartsFromJson(parts);
        reg.loadArchetypesFromJson(arches);
        reg.validate();
        return reg;
    }

    @Test
    void defaultContentLoadsAndValidates() throws Exception {
        FaunaDataRegistry reg = loadDefaults();
        assertNotNull(reg.getArchetype("grazer_quad"));
        assertNotNull(reg.getArchetype("skitterer_hex"));
        assertNotNull(reg.getArchetype("strider_biped"));
        assertNotNull(reg.getArchetype("crawler_serpent"));
    }

    @Test
    void eachArchetypeGeneratesExpectedTopology() throws Exception {
        CreatureGenerator g = new CreatureGenerator(loadDefaults());
        assertEquals(4, g.generate("grazer_quad", 1L).countOfType(PartType.LIMB_LEG));
        assertEquals(6, g.generate("skitterer_hex", 1L).countOfType(PartType.LIMB_LEG));
        CreatureSpec biped = g.generate("strider_biped", 1L);
        assertEquals(2, biped.countOfType(PartType.LIMB_LEG));
        assertEquals(2, biped.countOfType(PartType.LIMB_ARM));
        CreatureSpec snake = g.generate("crawler_serpent", 1L);
        assertEquals(6, snake.countOfType(PartType.TORSO));
        assertEquals(BodyPlan.SERPENTINE, snake.bodyPlan);
    }
}
