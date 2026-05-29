package com.galacticodyssey.fauna.animation;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.assembly.CreatureAssembler;
import com.galacticodyssey.fauna.rig.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkitterGaitControllerTest {

    private CreatureRig hexRig;

    @BeforeEach
    void setUp() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso_hex\",\"partType\":\"TORSO\",\"bodyPlans\":[\"HEXAPOD\"]," +
          "  \"geometry\":{\"shape\":\"CAPSULE\",\"length\":2.4,\"radius\":0.45}," +
          "  \"sockets\":[ {\"id\":\"la\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.4,-0.1,0.8],\"mirrorGroup\":\"a\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"lb\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.4,-0.1,0.0],\"mirrorGroup\":\"b\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"lc\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.4,-0.1,-0.8],\"mirrorGroup\":\"c\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.25],\"jointHint\":\"neck\"} ] }," +
          "{ \"id\":\"leg\",\"partType\":\"LIMB_LEG\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"hex\",\"bodyPlan\":\"HEXAPOD\",\"minSize\":1,\"maxSize\":1,\"density\":700," +
          "  \"gaitClass\":\"skitter\"," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[" +
          "     {\"socketId\":\"la\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"lb\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"lc\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
        reg.validate();
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("hex"), 42L);
        hexRig = new CreatureRigBuilder().build(spec);
    }

    @Test
    void hexapodHasSixHipBones() {
        assertEquals(6, hexRig.bonesWithRole(BoneRole.HIP).size(), "3 mirror pairs = 6 legs");
    }

    @Test
    void updateModifiesLegBones() {
        SkitterGaitController ctrl = new SkitterGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 1f / 60f;
        params.speed = 3f;
        params.maxSpeed = 5f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 0.5f;

        float[][] before = new float[hexRig.boneCount()][];
        for (int i = 0; i < hexRig.boneCount(); i++) {
            before[i] = hexRig.getBone(i).currentPose.val.clone();
        }

        ctrl.update(hexRig, params);

        boolean anyChanged = false;
        for (Bone b : hexRig.bonesWithRole(BoneRole.HIP)) {
            for (int j = 0; j < 16; j++) {
                if (Math.abs(b.currentPose.val[j] - before[b.index][j]) > 1e-6f) {
                    anyChanged = true;
                    break;
                }
            }
            if (anyChanged) break;
        }
        assertTrue(anyChanged, "skitter gait must modify leg bones");
    }

    @Test
    void alternatingTripodGroupsHaveOppositePhase() {
        SkitterGaitController ctrl = new SkitterGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 1f / 60f;
        params.speed = 3f;
        params.maxSpeed = 5f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 0.25f;

        ctrl.update(hexRig, params);

        java.util.List<Bone> hips = hexRig.bonesWithRole(BoneRole.HIP);
        assertTrue(hips.size() == 6);
        boolean groupsDiffer = false;
        for (int j = 0; j < 16; j++) {
            if (Math.abs(hips.get(0).currentPose.val[j] - hips.get(1).currentPose.val[j]) > 1e-6f) {
                groupsDiffer = true;
                break;
            }
        }
        assertTrue(groupsDiffer, "alternating tripod groups should have different phases");
    }
}
