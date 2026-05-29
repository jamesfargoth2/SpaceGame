package com.galacticodyssey.ship.systems;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlightControlMathTest {

    @Test
    void blueZoneFactorIsOneInsideBand() {
        assertEquals(1f, FlightControlMath.blueZoneFactor(0.6f, 0.4f, 0.8f, 0.5f), 1e-4);
        assertEquals(1f, FlightControlMath.blueZoneFactor(0.4f, 0.4f, 0.8f, 0.5f), 1e-4);
        assertEquals(1f, FlightControlMath.blueZoneFactor(0.8f, 0.4f, 0.8f, 0.5f), 1e-4);
    }

    @Test
    void blueZoneFactorFallsToOffScaleAtExtremes() {
        assertEquals(0.5f, FlightControlMath.blueZoneFactor(1.0f, 0.4f, 0.8f, 0.5f), 1e-4);
        assertEquals(0.5f, FlightControlMath.blueZoneFactor(0.0f, 0.4f, 0.8f, 0.5f), 1e-4);
    }

    @Test
    void blueZoneFactorInterpolatesAboveBand() {
        // halfway between high(0.8) and max(1.0) → halfway between 1.0 and offScale(0.5)
        float f = FlightControlMath.blueZoneFactor(0.9f, 0.4f, 0.8f, 0.5f);
        assertEquals(0.75f, f, 1e-4);
    }

    @Test
    void blueZoneFactorUsesThrottleMagnitude() {
        // reverse throttle still benefits from band by magnitude
        assertEquals(1f, FlightControlMath.blueZoneFactor(-0.6f, 0.4f, 0.8f, 0.5f), 1e-4);
    }

    @Test
    void throttleStepRampsUpAndClampsAtOne() {
        float t = FlightControlMath.stepThrottle(0.95f, true, false, false, 2f, 0.4f, 0.1f);
        assertEquals(1f, t, 1e-4); // 0.95 + 2*0.1 = 1.15 clamped to 1
    }

    @Test
    void throttleStepRampsDownToReverseLimit() {
        float t = FlightControlMath.stepThrottle(-0.35f, false, true, false, 2f, 0.4f, 0.1f);
        assertEquals(-0.4f, t, 1e-4); // -0.35 - 0.2 = -0.55 clamped to -reverseFraction
    }

    @Test
    void throttleZeroKeyWins() {
        float t = FlightControlMath.stepThrottle(0.8f, true, false, true, 2f, 0.4f, 0.1f);
        assertEquals(0f, t, 1e-4);
    }

    @Test
    void throttleHoldsWhenNoInput() {
        float t = FlightControlMath.stepThrottle(0.55f, false, false, false, 2f, 0.4f, 0.1f);
        assertEquals(0.55f, t, 1e-4);
    }
}
