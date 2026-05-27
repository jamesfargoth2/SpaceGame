package com.galacticodyssey.core;

import com.badlogic.gdx.math.MathUtils;

public final class RelativisticMath {

    private RelativisticMath() {}

    public static float lorentzFactor(float speed) {
        if (speed < RelativisticConstants.THRESHOLD) return 1f;
        float beta = speed / RelativisticConstants.C;
        float beta2 = beta * beta;
        if (beta2 >= 1f) beta2 = 0.9999f;
        return 1f / (float) Math.sqrt(1f - beta2);
    }

    public static float relativisticMass(float restMass, float speed) {
        return restMass * lorentzFactor(speed);
    }

    public static float relativisticKE(float restMass, float speed) {
        return (lorentzFactor(speed) - 1f) * restMass * RelativisticConstants.C2;
    }

    public static float addVelocities(float v1, float v2) {
        final float c = RelativisticConstants.C;
        return (v1 + v2) / (1f + v1 * v2 / (c * c));
    }

    public static float shipTimeFactor(float speed) {
        return 1f / lorentzFactor(speed);
    }

    public static float longitudinalAcceleration(float thrustForce, float restMass, float speed) {
        final float gamma = lorentzFactor(speed);
        return thrustForce / (gamma * gamma * gamma * restMass);
    }

    public static float transverseAcceleration(float thrustForce, float restMass, float speed) {
        return thrustForce / (lorentzFactor(speed) * restMass);
    }

    public static float dopplerFactor(float relativeSpeed) {
        float beta = relativeSpeed / RelativisticConstants.C;
        beta = MathUtils.clamp(beta, -0.9999f, 0.9999f);
        return (float) Math.sqrt((1f + beta) / (1f - beta));
    }

    public static float aberratedAngle(float thetaSource, float speed) {
        final float beta = speed / RelativisticConstants.C;
        final float cosT = MathUtils.cos(thetaSource);
        final float cosObs = (cosT + beta) / (1f + beta * cosT);
        return (float) Math.acos(MathUtils.clamp(cosObs, -1f, 1f));
    }
}
