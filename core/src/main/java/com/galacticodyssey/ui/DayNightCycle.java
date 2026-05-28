package com.galacticodyssey.ui;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public final class DayNightCycle {

    private final float dayLengthSeconds;
    private final float axialTiltRadians;
    private final boolean tidallyLocked;

    private float elapsed;
    private final Vector3 sunDirection = new Vector3();

    public DayNightCycle(float dayLengthSeconds, float axialTiltDegrees,
                         boolean tidallyLocked) {
        this.dayLengthSeconds = Math.max(1f, dayLengthSeconds);
        this.axialTiltRadians = axialTiltDegrees * MathUtils.degreesToRadians;
        this.tidallyLocked = tidallyLocked;
        this.elapsed = dayLengthSeconds * 0.5f;
        recalcSunDirection();
    }

    public void update(float delta) {
        if (tidallyLocked) return;
        elapsed += delta;
        if (elapsed >= dayLengthSeconds) {
            elapsed -= dayLengthSeconds * (int) (elapsed / dayLengthSeconds);
        }
        recalcSunDirection();
    }

    public Vector3 getSunDirection() {
        return sunDirection;
    }

    public float getSunAltitude() {
        return sunDirection.y;
    }

    public float getTimeOfDay() {
        return elapsed / dayLengthSeconds;
    }

    public boolean isNight() {
        return sunDirection.y < -0.05f;
    }

    public float getSunIntensity() {
        return smoothstep(-0.1f, 0.2f, sunDirection.y);
    }

    public float getAmbientIntensity() {
        float dayAmbient = 0.35f;
        float nightAmbient = 0.06f;
        float t = smoothstep(-0.1f, 0.2f, sunDirection.y);
        return nightAmbient + (dayAmbient - nightAmbient) * t;
    }

    public float getStarVisibility() {
        return 1f - smoothstep(-0.15f, 0.05f, sunDirection.y);
    }

    private void recalcSunDirection() {
        // Offset by half-cycle so angle=0 at elapsed=0.5*day (noon), y=+1 (overhead)
        float angle = ((elapsed / dayLengthSeconds) - 0.5f) * MathUtils.PI2;
        float cosA = MathUtils.cos(angle);
        float sinA = MathUtils.sin(angle);

        float tiltCos = MathUtils.cos(axialTiltRadians);
        float tiltSin = MathUtils.sin(axialTiltRadians);

        sunDirection.set(
            sinA,
            cosA * tiltCos,
            cosA * tiltSin
        ).nor();
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }
}
