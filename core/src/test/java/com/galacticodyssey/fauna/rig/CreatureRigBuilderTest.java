package com.galacticodyssey.fauna.rig;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.assembly.CreatureAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureRigBuilderTest {

    private FaunaDataRegistry reg;

    @BeforeEach
    void setUp() {
        reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso\",\"partType\":\"TORSO\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
          "  \"sockets\":[ {\"id\":\"lf\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,0.6],\"mirrorGroup\":\"front\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"lr\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,-0.6],\"mirrorGroup\":\"rear\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0],\"jointHint\":\"neck\"} ] }," +
          "{ \"id\":\"leg\",\"partType\":\"LIMB_LEG\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }," +
          "{ \"id\":\"seg\",\"partType\":\"TORSO\",\"bodyPlans\":[\"SERPENTINE\"]," +
          "  \"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.6,\"radius\":0.2}," +
          "  \"sockets\":[ {\"id\":\"next\",\"acceptedType\":\"TORSO\",\"pos\":[0,0,0.6],\"jointHint\":\"spine\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0,0.6],\"jointHint\":\"neck\"} ] }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"quad\",\"bodyPlan\":\"QUADRUPED\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"gaitClass\":\"walk\"," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[" +
          "     {\"socketId\":\"lf\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"lr\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }," +
          "{ \"id\":\"snake\",\"bodyPlan\":\"SERPENTINE\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"gaitClass\":\"slither\"," +
          "  \"root\":{\"partType\":\"TORSO\",\"repeat\":4,\"continuationSocketId\":\"next\"," +
          "    \"children\":[ {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
        reg.validate();
    }

    @Test
    void quadrupedRigHasCorrectBoneCountAndRoles() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 42L);
        CreatureRig rig = new CreatureRigBuilder().build(spec);
        assertEquals(6, rig.boneCount(), "1 torso + 4 legs + 1 head");
        assertEquals(0, rig.root().index);
        assertTrue(rig.root().isRoot());
        assertEquals(4, rig.bonesWithRole(BoneRole.HIP).size(), "4 legs have HIP role");
        assertEquals(1, rig.bonesWithRole(BoneRole.NECK).size(), "1 head has NECK role");
    }

    @Test
    void serpentineRigHasSpineBones() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("snake"), 7L);
        CreatureRig rig = new CreatureRigBuilder().build(spec);
        assertEquals(5, rig.boneCount(), "4 spine + 1 head");
        // First segment is root (STRUCTURAL), continuation segments get SPINE from jointHint
        assertEquals(3, rig.bonesWithRole(BoneRole.SPINE).size(),
            "3 spine continuation sockets (first segment is root/STRUCTURAL)");
        assertEquals(1, rig.bonesWithRole(BoneRole.NECK).size());
    }

    @Test
    void boneHierarchyMatchesAssembledNodeTree() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 42L);
        CreatureRig rig = new CreatureRigBuilder().build(spec);
        assertEquals(-1, rig.root().parentIndex);
        for (int i = 1; i < rig.boneCount(); i++) {
            int parent = rig.getBone(i).parentIndex;
            assertTrue(parent >= 0 && parent < rig.boneCount(),
                "bone " + i + " parent index " + parent + " out of range");
        }
    }

    @Test
    void bindPosesMatchAssembledNodeLocalTransforms() {
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 42L);
        CreatureRig rig = new CreatureRigBuilder().build(spec);
        for (int i = 0; i < rig.boneCount(); i++) {
            assertArrayEquals(
                spec.allNodes.get(i).localTransform.val,
                rig.getBone(i).bindPose.val, 1e-5f,
                "bone " + i + " bind pose must match assembled node local transform");
        }
    }
}
