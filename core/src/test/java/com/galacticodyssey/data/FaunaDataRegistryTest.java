package com.galacticodyssey.data;

import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.fauna.archetype.BodyPlanArchetypeDef;
import com.galacticodyssey.fauna.part.CreaturePartDef;
import com.galacticodyssey.fauna.part.PartType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FaunaDataRegistryTest {

    private static final String PARTS = "{ \"parts\": [" +
        "{ \"id\":\"torso_quad\", \"partType\":\"TORSO\", \"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
        "  \"sockets\":[ {\"id\":\"leg_f\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,0,0.6],\"mirrorGroup\":\"legs\"}," +
        "                {\"id\":\"head\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0]} ] }," +
        "{ \"id\":\"leg_a\", \"partType\":\"LIMB_LEG\", \"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
        "{ \"id\":\"head_a\", \"partType\":\"HEAD\", \"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
        "] }";

    private static final String ARCHES = "{ \"archetypes\": [" +
        "{ \"id\":\"quad_grazer\", \"bodyPlan\":\"QUADRUPED\", \"minSize\":0.5,\"maxSize\":3,\"density\":900," +
        "  \"root\":{ \"partType\":\"TORSO\", \"children\":[" +
        "     {\"socketId\":\"leg_f\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
        "     {\"socketId\":\"head\",\"partType\":\"HEAD\"} ] } }" +
        "] }";

    @Test
    void loadsPartsAndArchetypes() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson(PARTS);
        reg.loadArchetypesFromJson(ARCHES);

        CreaturePartDef torso = reg.getPart("torso_quad");
        assertNotNull(torso);
        assertEquals(PartType.TORSO, torso.partType);
        assertEquals(2, torso.sockets.size());
        assertEquals("legs", torso.findSocket("leg_f").mirrorGroup);
        assertEquals(0.6f, torso.findSocket("leg_f").localPosition.z, 1e-4);

        BodyPlanArchetypeDef arch = reg.getArchetype("quad_grazer");
        assertNotNull(arch);
        assertEquals(BodyPlan.QUADRUPED, arch.bodyPlan);
        assertEquals(PartType.TORSO, arch.root.partType);
        assertEquals(2, arch.root.children.size());
        assertTrue(arch.root.children.get(0).mirror);
    }

    @Test
    void validationRejectsMirrorOnSocketWithoutMirrorGroup() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson(PARTS);
        String bad = "{ \"archetypes\":[ {\"id\":\"m\",\"bodyPlan\":\"QUADRUPED\"," +
            "\"root\":{\"partType\":\"TORSO\",\"children\":[{\"socketId\":\"head\",\"partType\":\"HEAD\",\"mirror\":true}]}} ] }";
        reg.loadArchetypesFromJson(bad);
        IllegalStateException ex = assertThrows(IllegalStateException.class, reg::validate);
        assertTrue(ex.getMessage().toLowerCase().contains("mirror"));
    }

    @Test
    void validationRejectsUnknownSocketId() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson(PARTS);
        String bad = "{ \"archetypes\":[ {\"id\":\"u\",\"bodyPlan\":\"QUADRUPED\"," +
            "\"root\":{\"partType\":\"TORSO\",\"children\":[{\"socketId\":\"nope\",\"partType\":\"HEAD\"}]}} ] }";
        reg.loadArchetypesFromJson(bad);
        IllegalStateException ex = assertThrows(IllegalStateException.class, reg::validate);
        assertTrue(ex.getMessage().contains("nope"));
    }

    @Test
    void loadRejectsInvertedSizeRange() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson(PARTS);
        String bad = "{ \"archetypes\":[ {\"id\":\"r\",\"bodyPlan\":\"QUADRUPED\",\"minSize\":3,\"maxSize\":1," +
            "\"root\":{\"partType\":\"TORSO\",\"children\":[]}} ] }";
        assertThrows(IllegalStateException.class, () -> reg.loadArchetypesFromJson(bad));
    }

    @Test
    void validationRejectsArchetypeReferencingMissingPartType() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson(PARTS);
        // TAIL has no eligible part in the library
        String bad = "{ \"archetypes\":[ {\"id\":\"x\",\"bodyPlan\":\"QUADRUPED\"," +
            "\"root\":{\"partType\":\"TORSO\",\"children\":[{\"socketId\":\"head\",\"partType\":\"TAIL\"}]}} ] }";
        reg.loadArchetypesFromJson(bad);
        IllegalStateException ex = assertThrows(IllegalStateException.class, reg::validate);
        assertTrue(ex.getMessage().contains("TAIL"));
    }
}
