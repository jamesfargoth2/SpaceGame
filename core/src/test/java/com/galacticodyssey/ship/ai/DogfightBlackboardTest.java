package com.galacticodyssey.ship.ai;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DogfightBlackboardTest {

    @Test
    void rangeAndClosureComputed() {
        DogfightBlackboard bb = new DogfightBlackboard();
        Vector3 selfPos = new Vector3(0, 0, 0);
        Quaternion selfRot = new Quaternion();                 // identity -> forward (0,0,-1)
        Vector3 selfVel = new Vector3(0, 0, -50);              // closing at 50 m/s
        Vector3 targetPos = new Vector3(0, 0, -100);
        Vector3 targetVel = new Vector3(0, 0, 0);

        bb.updateSensors(selfPos, selfRot, selfVel, targetPos, targetVel, 200f);

        assertEquals(100f, bb.rangeToTarget, 0.5f);
        assertTrue(bb.closureRate > 0f, "positive closure when approaching");
        assertTrue(bb.angleOffBore < 2f, "nose already on target");
    }

    @Test
    void angleOffBoreLargeWhenTargetBehind() {
        DogfightBlackboard bb = new DogfightBlackboard();
        Vector3 selfPos = new Vector3(0, 0, 0);
        Quaternion selfRot = new Quaternion();                 // forward (0,0,-1)
        Vector3 targetPos = new Vector3(0, 0, 100);            // directly behind
        bb.updateSensors(selfPos, selfRot, new Vector3(), targetPos, new Vector3(), 200f);
        assertTrue(bb.angleOffBore > 150f, "target behind -> large angle off bore");
    }

    @Test
    void leadPointAheadOfMovingTarget() {
        DogfightBlackboard bb = new DogfightBlackboard();
        Vector3 selfPos = new Vector3(0, 0, 0);
        Vector3 targetPos = new Vector3(0, 0, -100);
        Vector3 targetVel = new Vector3(20, 0, 0);             // crossing right
        bb.updateSensors(selfPos, new Quaternion(), new Vector3(), targetPos, targetVel, 200f);
        assertTrue(bb.leadPoint.x > 0f, "lead point leads a right-crossing target");
    }
}
