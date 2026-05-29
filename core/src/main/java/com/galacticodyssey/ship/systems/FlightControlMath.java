package com.galacticodyssey.ship.systems;

import com.badlogic.gdx.math.MathUtils;

/** Pure, stateless math for ED-style flight control. No ECS/GL/Bullet dependencies. */
public final class FlightControlMath {

    private FlightControlMath() {}

    /**
     * Turn-rate multiplier for the "blue zone". Returns 1.0 when |throttle| is within
     * [low, high]; linearly falls to offScale at throttle magnitude 0 (below the band)
     * and 1.0 (above the band).
     */
    public static float blueZoneFactor(float throttle, float low, float high, float offScale) {
        float t = Math.abs(throttle);
        if (t >= low && t <= high) return 1f;
        if (t < low) {
            float frac = (low <= 0f) ? 1f : t / low;           // 0 at t=0 → offScale, 1 at t=low → 1.0
            return MathUtils.lerp(offScale, 1f, MathUtils.clamp(frac, 0f, 1f));
        }
        // t > high
        float denom = 1f - high;
        float frac = (denom <= 0f) ? 0f : (t - high) / denom;  // 0 at t=high → 1.0, 1 at t=1 → offScale
        return MathUtils.lerp(1f, offScale, MathUtils.clamp(frac, 0f, 1f));
    }

    /**
     * Steps a persistent throttle set-point. {@code up}/{@code down} ramp it by
     * {@code rampRate} per second; {@code zero} snaps to 0. Result is clamped to
     * [-reverseFraction, +1].
     */
    public static float stepThrottle(float current, boolean up, boolean down, boolean zero,
                                     float rampRate, float reverseFraction, float dt) {
        if (zero) return 0f;
        float t = current;
        if (up)   t += rampRate * dt;
        if (down) t -= rampRate * dt;
        return MathUtils.clamp(t, -reverseFraction, 1f);
    }
}
