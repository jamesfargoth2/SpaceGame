package com.galacticodyssey.water.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;

/**
 * Manages the submarine's ballast tanks.
 * Each tank has a capacity, current fill level, and flow rate.
 * Filling tanks with water increases weight (submarine sinks).
 * Emptying tanks (blowing ballast) decreases weight (submarine rises).
 */
public class BallastTankComponent implements Component {

    /** Individual ballast tank state. */
    public static class Tank {
        /** Unique identifier for this tank. */
        public final String id;

        /** Maximum water capacity in cubic meters. */
        public final float capacity;

        /** Current water volume in cubic meters. */
        public float currentVolume;

        /** Maximum fill/drain rate in cubic meters per second. */
        public final float flowRate;

        /** Offset from center of mass along the forward axis (positive = forward). */
        public final float forwardOffset;

        /** Offset from center of mass along the vertical axis (positive = up). */
        public final float verticalOffset;

        /** Whether this tank's valve is currently open for filling. */
        public boolean filling;

        /** Whether this tank is currently being blown (emptied). */
        public boolean blowing;

        public Tank(String id, float capacity, float flowRate, float forwardOffset, float verticalOffset) {
            this.id = id;
            this.capacity = capacity;
            this.flowRate = flowRate;
            this.forwardOffset = forwardOffset;
            this.verticalOffset = verticalOffset;
            this.currentVolume = 0f;
            this.filling = false;
            this.blowing = false;
        }

        /** Returns the fill fraction (0-1). */
        public float getFillFraction() {
            return capacity > 0f ? currentVolume / capacity : 0f;
        }

        /** Returns the mass of water currently in this tank (kg). */
        public float getWaterMass() {
            return currentVolume * WATER_DENSITY;
        }

        private static final float WATER_DENSITY = 1025f; // seawater kg/m^3
    }

    /** All ballast tanks on this submarine. */
    public final Array<Tank> tanks = new Array<>();

    /** Target fill fraction for autopilot ballast control (0-1). */
    public float targetFillFraction = 0f;

    /** Whether the ballast system is under automatic control. */
    public boolean autoControl = true;

    /** Emergency blow: rapidly empty all tanks. */
    public boolean emergencyBlow = false;

    /** Emergency blow flow rate multiplier. */
    public float emergencyBlowMultiplier = 5f;

    /** Returns total water mass across all tanks (kg). */
    public float getTotalWaterMass() {
        float total = 0f;
        for (int i = 0; i < tanks.size; i++) {
            total += tanks.get(i).getWaterMass();
        }
        return total;
    }

    /** Returns total capacity across all tanks (m^3). */
    public float getTotalCapacity() {
        float total = 0f;
        for (int i = 0; i < tanks.size; i++) {
            total += tanks.get(i).capacity;
        }
        return total;
    }

    /** Returns overall fill fraction across all tanks. */
    public float getOverallFillFraction() {
        float totalCap = getTotalCapacity();
        if (totalCap <= 0f) return 0f;
        float totalVol = 0f;
        for (int i = 0; i < tanks.size; i++) {
            totalVol += tanks.get(i).currentVolume;
        }
        return totalVol / totalCap;
    }
}
