package com.galacticodyssey.ship.ai;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipSteeringControllerTest {

    private static float angleToTarget(Quaternion rot, Vector3 aim) {
        Vector3 fwd = new Vector3(0, 0, -1).mul(rot).nor();
        float dot = Math.max(-1f, Math.min(1f, fwd.dot(aim)));
        return (float) Math.toDegrees(Math.acos(dot));
    }

    @Test
    void convergesNoseTowardAimDirection() {
        ShipSteeringController ctrl = new ShipSteeringController();
        Quaternion rot = new Quaternion();                    // forward (0,0,-1)
        Vector3 aim = new Vector3(1, 0, -1).nor();            // 45deg to the right
        Vector3 angularVel = new Vector3();                   // local pitch/yaw/roll rates
        ShipFlightInputComponent out = new ShipFlightInputComponent();

        float startAngle = angleToTarget(rot, aim);
        float dt = 1f / 60f;
        for (int i = 0; i < 240; i++) {
            ctrl.computeInputs(rot, angularVel, aim, 0f, out);
            angularVel.add(out.pitchInput * 3f * dt, out.yawInput * 3f * dt, out.rollInput * 3f * dt);
            angularVel.scl(0.98f);
            Quaternion dq = new Quaternion().setEulerAnglesRad(
                angularVel.y * dt, angularVel.x * dt, angularVel.z * dt);
            rot.mul(dq).nor();
        }
        float endAngle = angleToTarget(rot, aim);
        assertTrue(endAngle < startAngle * 0.2f,
            "nose should converge toward aim (start=" + startAngle + " end=" + endAngle + ")");
    }

    @Test
    void alignedProducesNearZeroCommand() {
        ShipSteeringController ctrl = new ShipSteeringController();
        Quaternion rot = new Quaternion();
        Vector3 aim = new Vector3(0, 0, -1);                  // already aligned
        ShipFlightInputComponent out = new ShipFlightInputComponent();
        ctrl.computeInputs(rot, new Vector3(), aim, 0f, out);
        assertEquals(0f, out.pitchInput, 0.05f);
        assertEquals(0f, out.yawInput, 0.05f);
    }

    @Test
    void outputsClampedToUnitRange() {
        ShipSteeringController ctrl = new ShipSteeringController();
        Quaternion rot = new Quaternion();
        Vector3 aim = new Vector3(0, 0, 1);                   // 180deg away -> max error
        ShipFlightInputComponent out = new ShipFlightInputComponent();
        ctrl.computeInputs(rot, new Vector3(), aim, 1.5f, out);
        assertTrue(Math.abs(out.pitchInput) <= 1f);
        assertTrue(Math.abs(out.yawInput) <= 1f);
        assertTrue(Math.abs(out.rollInput) <= 1f);
        assertTrue(out.throttle <= 1f && out.throttle >= -1f);
    }
}
