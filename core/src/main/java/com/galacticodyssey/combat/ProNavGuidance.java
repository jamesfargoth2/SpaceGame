package com.galacticodyssey.combat;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;

public final class ProNavGuidance {

    private ProNavGuidance() {}

    /**
     * Proportional navigation: returns the desired acceleration vector to steer
     * the missile toward the target. Caller must clamp to maxAcceleration.
     *
     * @param navGain navigation constant N (typically 3-5)
     */
    public static Vector3 steer(Vector3 missilePos, Vector3 missileVel,
                                Vector3 targetPos, Vector3 targetVel,
                                float navGain) {
        Vector3 relPos = Pools.obtain(Vector3.class).set(targetPos).sub(missilePos);
        Vector3 relVel = Pools.obtain(Vector3.class).set(targetVel).sub(missileVel);

        float range = relPos.len();
        if (range < 1f) {
            Pools.free(relPos);
            Pools.free(relVel);
            return new Vector3();
        }

        Vector3 los = Pools.obtain(Vector3.class).set(relPos).nor();

        float relVelDotLos = relVel.dot(los);
        Vector3 losRate = Pools.obtain(Vector3.class)
            .set(relVel)
            .sub(los.x * relVelDotLos, los.y * relVelDotLos, los.z * relVelDotLos)
            .scl(1f / range);

        float closingSpeed = -relVelDotLos;
        Vector3 result = new Vector3(losRate).scl(navGain * closingSpeed);

        Pools.free(relPos);
        Pools.free(relVel);
        Pools.free(los);
        Pools.free(losRate);

        return result;
    }

    /**
     * Adds angular noise to a guidance acceleration vector, simulating ECM interference.
     *
     * @param ecmStrength 0-1 (0 = no effect, 1 = max spoofing)
     */
    public static void applyECMNoise(Vector3 accel, float ecmStrength) {
        if (ecmStrength <= 0f || accel.isZero()) return;
        float noiseAngle = ecmStrength * MathUtils.degreesToRadians * 15f;
        float angle = MathUtils.random(0f, MathUtils.PI2);
        float magnitude = MathUtils.random(0f, noiseAngle);

        Vector3 perp = Pools.obtain(Vector3.class);
        Vector3 nor = Pools.obtain(Vector3.class).set(accel).nor();

        if (Math.abs(nor.x) < 0.9f) {
            perp.set(1f, 0f, 0f).crs(nor).nor();
        } else {
            perp.set(0f, 1f, 0f).crs(nor).nor();
        }
        Vector3 perp2 = Pools.obtain(Vector3.class).set(nor).crs(perp).nor();

        accel.mulAdd(perp, MathUtils.cos(angle) * magnitude * accel.len());
        accel.mulAdd(perp2, MathUtils.sin(angle) * magnitude * accel.len());

        Pools.free(perp);
        Pools.free(nor);
        Pools.free(perp2);
    }
}
