package com.galacticodyssey.combat;

import com.badlogic.gdx.math.Vector3;

public final class BallisticsUtil {

    private BallisticsUtil() {}

    /**
     * Iteratively computes where to aim so a projectile at {@code muzzleSpeed}
     * intercepts a target moving at constant velocity. Converges in 3-4 iterations.
     */
    public static Vector3 computeLeadPoint(Vector3 shooterPos, float muzzleSpeed,
                                           Vector3 targetPos, Vector3 targetVel) {
        Vector3 estimated = new Vector3(targetPos);
        for (int i = 0; i < 4; i++) {
            float travelTime = estimated.dst(shooterPos) / muzzleSpeed;
            estimated.set(targetPos).mulAdd(targetVel, travelTime);
        }
        return estimated;
    }

    /**
     * Adjusts a lead point upward to compensate for gravitational drop over the
     * time of flight. For kinetic rounds in gravity wells.
     */
    public static Vector3 computeGravityLead(Vector3 shooterPos, float muzzleSpeed,
                                             Vector3 targetPos, Vector3 gravAccel) {
        float dist = shooterPos.dst(targetPos);
        float tof = dist / muzzleSpeed;
        Vector3 drop = new Vector3(gravAccel).scl(-0.5f * tof * tof);
        return new Vector3(targetPos).add(drop);
    }

    /**
     * Combined: lead a moving target AND compensate for gravity.
     */
    public static Vector3 computeFullLead(Vector3 shooterPos, float muzzleSpeed,
                                          Vector3 targetPos, Vector3 targetVel,
                                          Vector3 gravAccel) {
        Vector3 lead = computeLeadPoint(shooterPos, muzzleSpeed, targetPos, targetVel);
        float tof = lead.dst(shooterPos) / muzzleSpeed;
        lead.mulAdd(gravAccel, -0.5f * tof * tof);
        return lead;
    }
}
