package com.galacticodyssey.fauna.animation;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.fauna.assembly.CreatureAssembler;
import com.galacticodyssey.fauna.rig.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlitherGaitControllerTest {

    private CreatureRig snakeRig;

    @BeforeEach
    void setUp() {
        FaunaDataRegistry reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"seg\",\"partType\":\"TORSO\",\"bodyPlans\":[\"SERPENTINE\"]," +
          "  \"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.6,\"radius\":0.2}," +
          "  \"sockets\":[ {\"id\":\"next\",\"acceptedType\":\"TORSO\",\"pos\":[0,0,0.6],\"jointHint\":\"spine\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0,0.6],\"jointHint\":\"neck\"} ] }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"snake\",\"bodyPlan\":\"SERPENTINE\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"gaitClass\":\"slither\"," +
          "  \"root\":{\"partType\":\"TORSO\",\"repeat\":4,\"continuationSocketId\":\"next\"," +
          "    \"children\":[ {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
        reg.validate();
        CreatureSpec spec = new CreatureAssembler(reg).assemble(reg.getArchetype("snake"), 7L);
        snakeRig = new CreatureRigBuilder().build(spec);
    }

    @Test
    void spineBonesPresentInRig() {
        assertTrue(snakeRig.bonesWithRole(BoneRole.SPINE).size() >= 2,
            "serpentine should have multiple spine bones");
    }

    @Test
    void updateModifiesSpineBones() {
        SlitherGaitController ctrl = new SlitherGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 1f / 60f;
        params.speed = 2f;
        params.maxSpeed = 4f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 1.0f;

        float[][] before = new float[snakeRig.boneCount()][];
        for (int i = 0; i < snakeRig.boneCount(); i++) {
            before[i] = snakeRig.getBone(i).currentPose.val.clone();
        }

        ctrl.update(snakeRig, params);

        boolean anySpineChanged = false;
        for (Bone b : snakeRig.bonesWithRole(BoneRole.SPINE)) {
            for (int j = 0; j < 16; j++) {
                if (Math.abs(b.currentPose.val[j] - before[b.index][j]) > 1e-6f) {
                    anySpineChanged = true;
                    break;
                }
            }
            if (anySpineChanged) break;
        }
        assertTrue(anySpineChanged, "slither must modify spine bones");
    }

    @Test
    void spineBonesHavePhaseOffsetRotations() {
        SlitherGaitController ctrl = new SlitherGaitController();
        GaitParams params = new GaitParams();
        params.deltaTime = 1f / 60f;
        params.speed = 2f;
        params.maxSpeed = 4f;
        params.sizeMultiplier = 1f;
        params.elapsedTime = 0.5f;

        ctrl.update(snakeRig, params);

        java.util.List<Bone> spines = snakeRig.bonesWithRole(BoneRole.SPINE);
        if (spines.size() >= 2) {
            boolean differ = false;
            for (int j = 0; j < 16; j++) {
                if (Math.abs(spines.get(0).currentPose.val[j] - spines.get(1).currentPose.val[j]) > 1e-6f) {
                    differ = true;
                    break;
                }
            }
            assertTrue(differ, "adjacent spine bones should have different phase-offset rotations");
        }
    }
}
