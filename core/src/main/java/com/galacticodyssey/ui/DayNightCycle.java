package com.galacticodyssey.ui;

import com.badlogic.gdx.math.Vector3;

public class DayNightCycle {

    private final float dayLengthSeconds;
    private float timeOfDayHours;
    private final boolean paused;

    private final Vector3 sunDirection = new Vector3();

    public DayNightCycle(float dayLengthSeconds, float axialTiltDegrees, boolean paused) {
        this.dayLengthSeconds = dayLengthSeconds;
        this.timeOfDayHours = 12f; // always start at solar noon
        this.paused = paused;
        updateSunDirection();
    }

    public void update(float deltaSeconds) {
        if (!paused) {
            float hoursPerSecond = 24f / dayLengthSeconds;
            timeOfDayHours += deltaSeconds * hoursPerSecond;
            if (timeOfDayHours >= 24f) timeOfDayHours -= 24f;
        }
        updateSunDirection();
    }

    private void updateSunDirection() {
        // Map time of day to sun angle. 6:00 = sunrise (east), 12:00 = noon (overhead), 18:00 = sunset (west)
        float angle = (timeOfDayHours / 24f) * 360f - 90f;
        float radians = (float) Math.toRadians(angle);
        float x = (float) Math.cos(radians);
        float y = (float) Math.sin(radians);
        // sun direction points FROM the sun TOWARD the scene
        sunDirection.set(-x, -y, -0.3f).nor();
    }

    private float computeDayFactor() {
        // Daylight between 6:00 and 18:00, peak at noon
        float t = (timeOfDayHours - 6f) / 12f;
        if (t < 0f || t > 1f) return 0f;
        return (float) Math.sin(t * Math.PI);
    }

    public float getSunIntensity() {
        return clamp01(computeDayFactor()) * 1.0f;
    }

    public float getAmbientIntensity() {
        return 0.3f;
    }

    public Vector3 getSunDirection() {
        return sunDirection;
    }

    public float getTimeOfDayHours() {
        return timeOfDayHours;
    }

    /** Normalized time in [0,1] where 0 = midnight and 0.5 = noon. */
    public float getTimeOfDay() {
        return timeOfDayHours / 24f;
    }

    /** Sun elevation: positive above horizon, negative below. */
    public float getSunAltitude() {
        return -sunDirection.y;
    }

    public boolean isNight() {
        return getSunAltitude() < 0f;
    }

    /** 0 at full day, 1 at full night. */
    public float getStarVisibility() {
        return 1f - clamp01(computeDayFactor());
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
