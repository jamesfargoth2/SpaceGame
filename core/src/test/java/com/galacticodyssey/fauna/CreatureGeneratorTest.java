package com.galacticodyssey.fauna;

import com.galacticodyssey.data.FaunaDataRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureGeneratorTest {

    private FaunaDataRegistry reg;

    @BeforeEach
    void setUp() {
        reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso\",\"partType\":\"TORSO\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
          "  \"sockets\":[ {\"id\":\"lf\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,0.6],\"mirrorGroup\":\"front\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0]} ] }," +
          "{ \"id\":\"leg\",\"partType\":\"LIMB_LEG\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"quad\",\"bodyPlan\":\"QUADRUPED\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[" +
          "     {\"socketId\":\"lf\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
        reg.validate();
    }

    @Test
    void generatesSpecWithPositiveStats() {
        CreatureSpec spec = new CreatureGenerator(reg).generate("quad", 12345L);
        assertEquals("quad", spec.archetypeId);
        assertTrue(spec.mass > 0f);
        assertTrue(spec.maxHP >= 1f);
        assertTrue(spec.moveSpeed > 0f);
        assertTrue(spec.meleeDamage >= 1f);
    }

    @Test
    void sameSeedYieldsIdenticalStatsAndStructure() {
        CreatureGenerator g = new CreatureGenerator(reg);
        CreatureSpec a = g.generate("quad", 555L);
        CreatureSpec b = g.generate("quad", 555L);
        assertEquals(a.partCount(), b.partCount());
        assertEquals(a.mass, b.mass, 1e-4f);
        assertEquals(a.maxHP, b.maxHP, 1e-4f);
        assertEquals(a.colorSeed, b.colorSeed);
    }

    @Test
    void seededArchetypePickIsDeterministic() {
        CreatureGenerator g = new CreatureGenerator(reg);
        assertEquals(g.generate(900L).archetypeId, g.generate(900L).archetypeId);
    }

    @Test
    void gaitClassPropagatedToSpec() {
        CreatureSpec spec = new CreatureGenerator(reg).generate("quad", 42L);
        assertNotNull(spec.gaitClass);
        assertFalse(spec.gaitClass.isEmpty());
    }

    @Test
    void skinSpecIsPropagatedFromAssembly() {
        CreatureSpec spec = new CreatureGenerator(reg).generate("quad", 42L);
        assertNotNull(spec.skinSpec, "skinSpec should be populated during assembly");
        assertNotNull(spec.skinSpec.patternType);
        assertTrue(spec.skinSpec.primaryR >= 0f && spec.skinSpec.primaryR <= 1f);
    }
}
