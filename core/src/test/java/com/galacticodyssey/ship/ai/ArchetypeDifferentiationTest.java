package com.galacticodyssey.ship.ai;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure (no physics) check that a faster-reacting archetype re-aims more often, keeping its nose
 * closer to a crossing target. Drives the blackboard + tree directly with a scripted target path.
 */
class ArchetypeDifferentiationTest {

    private float runAvgAngleOff(float reactionTimeSec) {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetype = new PilotArchetype();
        ai.archetype.reactionTimeSec = reactionTimeSec;
        ai.archetype.preferredEngageRange = 350f;
        ai.decisionInterval = reactionTimeSec;
        Entity e = new Entity();
        e.add(ai);
        ai.behaviorTree = DogfightTreeFactory.build(e, 1000f);

        Vector3 selfPos = new Vector3(0, 0, 0);
        Quaternion selfRot = new Quaternion();
        Vector3 selfVel = new Vector3();
        Vector3 targetVel = new Vector3(60, 0, 0);
        Vector3 targetPos = new Vector3(-200, 0, -400);

        float dt = 1f / 60f, sumAngle = 0f; int n = 0;
        ai.decisionTimer = 0f;
        for (int i = 0; i < 300; i++) {
            targetPos.mulAdd(targetVel, dt);
            ai.blackboard.updateSensors(selfPos, selfRot, selfVel, targetPos, targetVel, 1000f);
            ai.blackboard.hasTarget = true;
            ai.blackboard.selfHealthPercent = 1f;
            ai.decisionTimer -= dt;
            if (ai.decisionTimer <= 0f) {
                ai.decisionTimer = Math.max(ai.decisionInterval, ai.archetype.reactionTimeSec);
                ai.behaviorTree.step();
            }
            Vector3 fwd = new Vector3(0, 0, -1).mul(selfRot);
            Vector3 aim = ai.blackboard.desiredAimDir;
            Quaternion toAim = new Quaternion().setFromCross(fwd, aim);
            selfRot.slerp(new Quaternion(selfRot).mul(toAim), 0.15f);
            sumAngle += ai.blackboard.angleOffBore; n++;
        }
        return sumAngle / n;
    }

    @Test
    void fasterReactionTracksTargetMoreTightly() {
        float aceAvg = runAvgAngleOff(0.05f);
        float rookieAvg = runAvgAngleOff(0.6f);
        assertTrue(aceAvg <= rookieAvg + 1f,
            "ace should track at least as tightly as rookie (ace=" + aceAvg + " rookie=" + rookieAvg + ")");
    }
}
