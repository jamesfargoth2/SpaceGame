package com.galacticodyssey.ship.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;

/**
 * Pure PD attitude controller. Given current orientation, current angular velocity (local axes),
 * and a desired world-space aim direction, produces clamped pitch/yaw/roll/throttle stick inputs
 * that steer the ship's nose (local -Z) toward the aim direction without oscillating.
 */
public class ShipSteeringController {

    public float kp = 2.2f;
    public float kd = 0.8f;
    public float rollKd = 0.6f;

    private final Vector3 forward = new Vector3();
    private final Vector3 errorAxisWorld = new Vector3();
    private final Vector3 errorAxisLocal = new Vector3();
    private final Quaternion inv = new Quaternion();

    public void computeInputs(Quaternion shipRot, Vector3 angularVel,
                              Vector3 desiredAimDir, float desiredThrottle,
                              ShipFlightInputComponent out) {
        forward.set(0, 0, -1).mul(shipRot).nor();

        errorAxisWorld.set(forward).crs(desiredAimDir);
        float sin = errorAxisWorld.len();
        float cos = MathUtils.clamp(forward.dot(desiredAimDir), -1f, 1f);
        float angle = (float) Math.atan2(sin, cos);

        if (sin > 1e-5f) {
            errorAxisWorld.scl(1f / sin);
        } else if (angle > 1f) {
            errorAxisWorld.set(0, 1, 0);
        } else {
            errorAxisWorld.setZero();
        }
        errorAxisWorld.scl(angle);

        inv.set(shipRot).conjugate();
        errorAxisLocal.set(errorAxisWorld).mul(inv);

        float pitch = kp * errorAxisLocal.x - kd * angularVel.x;
        float yaw   = kp * errorAxisLocal.y - kd * angularVel.y;
        float roll  = -rollKd * angularVel.z;

        out.pitchInput = MathUtils.clamp(pitch, -1f, 1f);
        out.yawInput   = MathUtils.clamp(yaw, -1f, 1f);
        out.rollInput  = MathUtils.clamp(roll, -1f, 1f);
        out.throttle   = MathUtils.clamp(desiredThrottle, -1f, 1f);
    }
}
