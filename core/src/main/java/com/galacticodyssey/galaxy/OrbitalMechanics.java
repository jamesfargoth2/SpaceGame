package com.galacticodyssey.galaxy;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

public final class OrbitalMechanics {

    public static final float G = 6.674e-11f;

    private OrbitalMechanics() {}

    // ------------------------------------------------------------------ basic formulae

    public static float visViva(float GM, float r, float a) {
        return (float) Math.sqrt(Math.max(0.0, GM * (2f / r - 1f / a)));
    }

    public static float circularOrbitSpeed(float GM, float r) {
        return (float) Math.sqrt(GM / r);
    }

    public static float escapeVelocity(float GM, float r) {
        return (float) Math.sqrt(2f * GM / r);
    }

    public static float orbitalPeriod(float GM, float a) {
        return (float) (MathUtils.PI2 * Math.sqrt((double)(a * a * a) / GM));
    }

    // ------------------------------------------------------------------ anomaly conversion

    /**
     * Converts mean anomaly M (radians) to true anomaly (radians) using Newton-Raphson
     * for the eccentric anomaly.  Eccentricity is clamped to [0, 0.999] to keep the
     * elliptical solver from diverging near the parabolic limit.
     */
    public static float trueAnomalyFromMean(float M, float e) {
        e = MathUtils.clamp(e, 0f, 0.999f);
        // Wrap M to [0, 2π]
        M = M % MathUtils.PI2;
        if (M < 0f) M += MathUtils.PI2;

        float E = M;
        for (int i = 0; i < 10; i++) {
            float denom = 1f - e * MathUtils.cos(E);
            if (Math.abs(denom) < 1e-10f) break;
            E = E - (E - e * MathUtils.sin(E) - M) / denom;
        }

        float sinV = (float)(Math.sqrt(1.0 - (double)e * e) * Math.sin(E)) / (1f - e * MathUtils.cos(E));
        float cosV = (MathUtils.cos(E) - e) / (1f - e * MathUtils.cos(E));
        return MathUtils.atan2(sinV, cosV);
    }

    // ------------------------------------------------------------------ position

    /**
     * Computes 3-D position (relative to primary) at the given true anomaly, using the
     * full perifocal-frame rotation through Ω (longitudeAscNode), i (inclination),
     * and ω (argumentPeriapsis).
     */
    public static Vector3 orbitPosition(KeplerOrbit o, float trueAnomaly, Vector3 out) {
        float e = o.eccentricity;
        float p = o.semiMajorAxis * (1f - e * e);
        float r = p / (1f + e * MathUtils.cos(trueAnomaly));

        float px = r * MathUtils.cos(trueAnomaly);
        float py = r * MathUtils.sin(trueAnomaly);

        float cosO = MathUtils.cos(o.longitudeAscNode);
        float sinO = MathUtils.sin(o.longitudeAscNode);
        float cosI = MathUtils.cos(o.inclination);
        float sinI = MathUtils.sin(o.inclination);
        float cosW = MathUtils.cos(o.argumentPeriapsis);
        float sinW = MathUtils.sin(o.argumentPeriapsis);

        out.x = (cosO * cosW - sinO * sinW * cosI) * px + (-cosO * sinW - sinO * cosW * cosI) * py;
        out.y = (sinI * sinW) * px + (sinI * cosW) * py;
        out.z = (sinO * cosW + cosO * sinW * cosI) * px + (-sinO * sinW + cosO * cosW * cosI) * py;
        return out;
    }

    /**
     * Fills {@code outPoints} with {@code segments} evenly-spaced orbit samples.
     * Callers must translate by the primary's world position before rendering.
     * Only call this when orbital elements change — do not invoke every frame.
     */
    public static void sampleOrbit(KeplerOrbit orbit, int segments, Array<Vector3> outPoints) {
        final float step = MathUtils.PI2 / segments;
        for (int i = 0; i < segments; i++) {
            outPoints.add(orbitPosition(orbit, i * step, new Vector3()));
        }
    }

    // ------------------------------------------------------------------ transfer

    public static final class HohmannTransfer {
        public final float deltaV1;
        public final float deltaV2;
        public final float transferTime;

        HohmannTransfer(float deltaV1, float deltaV2, float transferTime) {
            this.deltaV1      = deltaV1;
            this.deltaV2      = deltaV2;
            this.transferTime = transferTime;
        }
    }

    public static HohmannTransfer hohmann(float GM, float r1, float r2) {
        float aTransfer   = (r1 + r2) / 2f;
        float v1Current   = circularOrbitSpeed(GM, r1);
        float v1Transfer  = visViva(GM, r1, aTransfer);
        float v2Transfer  = visViva(GM, r2, aTransfer);
        float v2Target    = circularOrbitSpeed(GM, r2);

        return new HohmannTransfer(
            v1Transfer - v1Current,
            v2Target   - v2Transfer,
            orbitalPeriod(GM, aTransfer) / 2f
        );
    }

    // ------------------------------------------------------------------ state vectors → elements

    public static float specificOrbitalEnergy(float GM, float r, float v) {
        return (v * v) / 2f - GM / r;
    }

    /**
     * Derives a {@link KeplerOrbit} from position and velocity vectors relative to the primary.
     * Inclination and node/argument angles are derived from the angular-momentum and
     * eccentricity vectors.
     */
    public static KeplerOrbit fromStateVectors(Vector3 pos, Vector3 vel, float primaryMass) {
        final float GM = G * primaryMass;
        final float r  = pos.len();
        final float v  = vel.len();

        // Specific angular momentum h = pos × vel
        Vector3 h = pos.cpy().crs(vel);
        float hLen = h.len();

        // Eccentricity vector e⃗ = (v × h) / GM − r̂
        Vector3 eVec = vel.cpy().crs(h).scl(1f / GM).sub(pos.cpy().nor());
        float e = eVec.len();

        float energy = specificOrbitalEnergy(GM, r, v);
        // Guard against (near-)zero or positive energy (hyperbolic/escape); use |a| safely.
        float a = (Math.abs(energy) > 1e-10f) ? (-GM / (2f * energy)) : Float.MAX_VALUE;

        // Inclination: angle between h and +Z axis (reference plane Z)
        float inc = (hLen > 1e-10f) ? (float) Math.acos(MathUtils.clamp(h.z / hLen, -1f, 1f)) : 0f;

        // Node vector N = Z × h  (ascending-node direction)
        Vector3 zAxis = new Vector3(0f, 0f, 1f);
        Vector3 nodeVec = zAxis.cpy().crs(h);
        float nLen = nodeVec.len();

        float lan = 0f;
        if (nLen > 1e-10f) {
            lan = (float) Math.acos(MathUtils.clamp(nodeVec.x / nLen, -1f, 1f));
            if (nodeVec.y < 0f) lan = MathUtils.PI2 - lan;
        }

        float argPeri = 0f;
        if (nLen > 1e-10f && e > 1e-10f) {
            argPeri = (float) Math.acos(MathUtils.clamp(nodeVec.dot(eVec) / (nLen * e), -1f, 1f));
            if (eVec.z < 0f) argPeri = MathUtils.PI2 - argPeri;
        }

        float trueAnom = 0f;
        if (e > 1e-10f) {
            trueAnom = (float) Math.acos(MathUtils.clamp(eVec.dot(pos) / (e * r), -1f, 1f));
            if (pos.dot(vel) < 0f) trueAnom = MathUtils.PI2 - trueAnom;
        }

        KeplerOrbit o = new KeplerOrbit();
        o.semiMajorAxis      = a;
        o.eccentricity       = e;
        o.inclination        = inc;
        o.longitudeAscNode   = lan;
        o.argumentPeriapsis  = argPeri;
        o.trueAnomalyAtEpoch = trueAnom;
        o.primaryMass        = primaryMass;
        o.GM                 = GM;
        o.period             = (a > 0f && a < Float.MAX_VALUE) ? orbitalPeriod(GM, a) : Float.MAX_VALUE;
        o.periapsis          = a * (1f - e);
        o.apoapsis           = a * (1f + e);
        return o;
    }

    // ------------------------------------------------------------------ SOI / stability

    public static float sphereOfInfluence(float orbitRadius, float bodyMass, float primaryMass) {
        return orbitRadius * (float) Math.pow(bodyMass / primaryMass, 0.4);
    }

    public static boolean isStableOrbit(Vector3 pos, Vector3 vel, float GM, float primaryRadius) {
        final float r = pos.len();
        final float v = vel.len();
        final float vEscape   = escapeVelocity(GM, r);
        final float vCircular = circularOrbitSpeed(GM, r);

        final boolean notEscaping    = v < vEscape;
        final boolean notImpacting   = r > primaryRadius * 1.1f;
        final boolean notDegenerate  = v > vCircular * 0.3f;
        return notEscaping && notImpacting && notDegenerate;
    }
}
