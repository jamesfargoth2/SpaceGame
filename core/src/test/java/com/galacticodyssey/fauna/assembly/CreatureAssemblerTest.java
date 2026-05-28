package com.galacticodyssey.fauna.assembly;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.part.PartType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureAssemblerTest {

    private FaunaDataRegistry reg;

    @BeforeEach
    void setUp() {
        reg = new FaunaDataRegistry();
        // torso with 2 mirror leg sockets (-> 4 legs) and 1 head socket
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso\",\"partType\":\"TORSO\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
          "  \"sockets\":[ {\"id\":\"lf\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,0.6],\"mirrorGroup\":\"front\"}," +
          "               {\"id\":\"lr\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,-0.6],\"mirrorGroup\":\"rear\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0]} ] }," +
          "{ \"id\":\"leg\",\"partType\":\"LIMB_LEG\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }," +
          "{ \"id\":\"seg\",\"partType\":\"TORSO\",\"bodyPlans\":[\"SERPENTINE\"]," +
          "  \"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.6,\"radius\":0.2}," +
          "  \"sockets\":[ {\"id\":\"next\",\"acceptedType\":\"TORSO\",\"pos\":[0,0,0.6]}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0,0.6]} ] }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"quad\",\"bodyPlan\":\"QUADRUPED\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[" +
          "     {\"socketId\":\"lf\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"lr\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }," +
          "{ \"id\":\"snake\",\"bodyPlan\":\"SERPENTINE\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"root\":{\"partType\":\"TORSO\",\"repeat\":4,\"continuationSocketId\":\"next\"," +
          "    \"children\":[ {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
        reg.validate();
    }

    @Test
    void quadrupedHasFourLegsAndOneHead() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 42L);
        assertEquals(4, spec.countOfType(PartType.LIMB_LEG), "2 mirror sockets -> 4 legs");
        assertEquals(1, spec.countOfType(PartType.HEAD));
        assertEquals(1, spec.countOfType(PartType.TORSO));
        assertEquals(6, spec.partCount());
    }

    @Test
    void mirroredLegsAreOnOppositeSides() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 42L);
        float sumX = 0f;
        int count = 0;
        for (AssembledNode n : spec.allNodes) {
            if (n.part.partType == PartType.LIMB_LEG) {
                sumX += n.worldTransform.getTranslation(new com.badlogic.gdx.math.Vector3()).x;
                count++;
            }
        }
        assertEquals(4, count);
        assertEquals(0f, sumX, 1e-3f, "mirrored legs cancel in X");
    }

    @Test
    void serpentineChainsSegmentsThenHead() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("snake"), 7L);
        assertEquals(4, spec.countOfType(PartType.TORSO), "repeat=4 -> 4 spine segments");
        assertEquals(1, spec.countOfType(PartType.HEAD));
        assertEquals(0, spec.countOfType(PartType.LIMB_LEG));
    }

    @Test
    void assemblyIsDeterministic() {
        CreatureSpec a = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 99L);
        CreatureSpec b = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 99L);
        assertEquals(a.partCount(), b.partCount());
        for (int i = 0; i < a.allNodes.size(); i++) {
            assertEquals(a.allNodes.get(i).part.id, b.allNodes.get(i).part.id);
            assertArrayEquals(a.allNodes.get(i).worldTransform.val,
                              b.allNodes.get(i).worldTransform.val, 1e-6f);
        }
    }
}
