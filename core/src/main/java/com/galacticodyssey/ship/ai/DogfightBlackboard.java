package com.galacticodyssey.ship.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.BallisticsUtil;

/**
 * Per-ship scratch state for the dogfight behaviour tree. {@code updateSensors} computes
 * read-only situational values; tasks write the {@code desired*}/{@code fire*} intents which
 * the {@link ShipPilotAISystem} consumes after stepping the tree.
 */
public class DogfightBlackboard {

    // --- Sensors (written by updateSensors, read by tasks) ---
    public boolean hasTarget;
    public float rangeToTarget;
    /** Positive when closing on the target. */
    public float closureRate;
    /** Degrees between our nose and the lead point (0 = on target). */
    public float angleOffBore;
    public final Vector3 leadPoint = new Vector3();
    public float selfHealthPercent = 1f;

    // --- Intents (written by tasks, read by the system) ---
    public final Vector3 desiredAimDir = new Vector3(0, 0, -1);
    public float desiredThrottle;
    public float desiredRoll;
    public boolean fireGuns;
    public boolean fireMissiles;

    private final Vector3 forward = new Vector3();
    private final Vector3 toTarget = new Vector3();
    private final Vector3 relVel = new Vector3();
    private final Vector3 los = new Vector3();

    /** Recompute situational sensors. Pure; no ECS/GL. */
    public void updateSensors(Vector3 selfPos, Quaternion selfRot, Vector3 selfVel,
                              Vector3 targetPos, Vector3 targetVel, float muzzleSpeed) {
        hasTarget = true;
        fireGuns = false;
        fireMissiles = false;

        toTarget.set(targetPos).sub(selfPos);
        rangeToTarget = toTarget.len();

        los.set(toTarget).nor();
        relVel.set(selfVel).sub(targetVel);
        closureRate = relVel.dot(los);

        Vector3 lead = BallisticsUtil.computeLeadPoint(selfPos, muzzleSpeed, targetPos, targetVel);
        leadPoint.set(lead);
        desiredAimDir.set(leadPoint).sub(selfPos).nor();

        forward.set(0, 0, -1).mul(selfRot).nor();
        float dot = MathUtils.clamp(forward.dot(desiredAimDir), -1f, 1f);
        angleOffBore = (float) Math.toDegrees(Math.acos(dot));
    }

    public void clearTarget() {
        hasTarget = false;
        fireGuns = false;
        fireMissiles = false;
    }
}
