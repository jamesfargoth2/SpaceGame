package com.galacticodyssey.fauna.animation;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.assembly.CreatureAssembler;
import com.galacticodyssey.fauna.rig.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WalkGaitControllerTest {

    private CreatureRig quadRig;

    @BeforeEach
    void setUp() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso\",\"partType\":\"TORSO\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
          "  \"sockets\":[ {\"id\":\"lf\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,0.6],\"mirrorGroup\":\"front\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"lr\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,-0.6],\"mirrorGroup\":\"rear\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0],\"jointHint\":\"neck\"} ] }," +
          "{ \"id\":\"leg\",\"partType\":\"LIMB_LEG\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"quad\",\"bodyPlan\":\"QUADRUPED\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"gaitClass\":\"walk\"," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[" +
          "     {\"socketId\":\"lf\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"lr\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
        reg.validate();
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("quad"), 42L);
        quadRig = new CreatureRigBuilder().build(spec);
    }

    @Test
    void updateModifiesHipBoneTransforms() {
        WalkGaitController ctrl = new WalkGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 1f / 60f;
        params.speed = 2f;
        params.maxSpeed = 5f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 0.5f;

        float[][] before = new float[quadRig.boneCount()][];
        for (int i = 0; i < quadRig.boneCount(); i++) {
            before[i] = quadRig.getBone(i).currentPose.val.clone();
        }

        ctrl.update(quadRig, params);

        boolean anyChanged = false;
        for (Bone b : quadRig.bonesWithRole(BoneRole.HIP)) {
            for (int j = 0; j < 16; j++) {
                if (Math.abs(b.currentPose.val[j] - before[b.index][j]) > 1e-6f) {
                    anyChanged = true;
                    break;
                }
            }
            if (anyChanged) break;
        }
        assertTrue(anyChanged, "walk gait must modify at least one hip bone");
    }

    @Test
    void idleDoesNotModifyLegsAggressively() {
        WalkGaitController ctrl = new WalkGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 1f / 60f;
        params.speed = 0f;
        params.maxSpeed = 5f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 0f;

        ctrl.update(quadRig, params);

        for (Bone b : quadRig.bonesWithRole(BoneRole.HIP)) {
            float delta = 0f;
            for (int j = 0; j < 16; j++) {
                delta += Math.abs(b.currentPose.val[j] - b.bindPose.val[j]);
            }
            assertTrue(delta < 2f, "idle should produce only subtle hip variation, got delta=" + delta);
        }
    }

    @Test
    void resetRestoresBindPoses() {
        WalkGaitController ctrl = new WalkGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 0.5f;
        params.speed = 3f;
        params.maxSpeed = 5f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 2f;
        ctrl.update(quadRig, params);

        ctrl.reset(quadRig);

        for (int i = 0; i < quadRig.boneCount(); i++) {
            Bone b = quadRig.getBone(i);
            assertArrayEquals(b.bindPose.val, b.currentPose.val, 1e-6f,
                "reset should restore bind pose for bone " + i);
        }
    }
}
