package com.galacticodyssey.combat;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProNavGuidanceTest {

    @Test
    void steersTowardClosingTarget() {
        Vector3 missilePos = new Vector3(0, 0, 0);
        Vector3 missileVel = new Vector3(100, 0, 0);
        Vector3 targetPos = new Vector3(500, 50, 0);
        Vector3 targetVel = new Vector3(-50, 0, 0);

        Vector3 accel = ProNavGuidance.steer(missilePos, missileVel,
            targetPos, targetVel, 3f);

        assertTrue(accel.y > 0f,
            "Guidance should steer upward toward target that is above the missile");
    }

    @Test
    void returnsZeroWhenOnTopOfTarget() {
        Vector3 pos = new Vector3(100, 100, 100);
        Vector3 vel = new Vector3(50, 0, 0);

        Vector3 accel = ProNavGuidance.steer(pos, vel, pos, vel, 3f);

        assertEquals(0f, accel.len(), 0.001f,
            "Should return zero acceleration when missile is at target");
    }

    @Test
    void ecmNoiseIncreasesDeviation() {
        Vector3 baseAccel = new Vector3(0, 10, 0);
        Vector3 noisyAccel = new Vector3(baseAccel);

        ProNavGuidance.applyECMNoise(noisyAccel, 1.0f);

        assertNotEquals(baseAccel.x, noisyAccel.x, 0.001f,
            "ECM at full strength should perturb the acceleration vector");
    }

    @Test
    void zeroEcmDoesNotAlterAccel() {
        Vector3 accel = new Vector3(0, 10, 0);
        Vector3 original = new Vector3(accel);

        ProNavGuidance.applyECMNoise(accel, 0f);

        assertEquals(original.x, accel.x, 0.0001f);
        assertEquals(original.y, accel.y, 0.0001f);
        assertEquals(original.z, accel.z, 0.0001f);
    }
}
