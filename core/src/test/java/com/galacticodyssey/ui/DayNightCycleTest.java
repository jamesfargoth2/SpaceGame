package com.galacticodyssey.ui;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DayNightCycleTest {

    @Test
    void timeOfDayStartsAtNoon() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        assertEquals(0.5f, cycle.getTimeOfDay(), 1e-5f);
    }

    @Test
    void timeAdvancesOverDayLength() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        cycle.update(50f); // half a day from noon → midnight
        assertEquals(0.0f, cycle.getTimeOfDay(), 0.01f);
    }

    @Test
    void timeWrapsAroundAfterFullDay() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        cycle.update(100f); // full cycle back to noon
        assertEquals(0.5f, cycle.getTimeOfDay(), 0.01f);
    }

    @Test
    void sunDirectionChangesOverTime() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        Vector3 sunAtNoon = new Vector3(cycle.getSunDirection());
        cycle.update(25f); // quarter day
        Vector3 sunLater = new Vector3(cycle.getSunDirection());
        assertNotEquals(sunAtNoon.x, sunLater.x, 0.01f);
    }

    @Test
    void sunIsAboveHorizonAtNoon() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        assertTrue(cycle.getSunAltitude() > 0f, "Sun should be above horizon at noon");
        assertFalse(cycle.isNight());
    }

    @Test
    void sunIsBelowHorizonAtMidnight() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        cycle.update(50f); // noon → midnight
        assertTrue(cycle.getSunAltitude() < 0f, "Sun should be below horizon at midnight");
        assertTrue(cycle.isNight());
    }

    @Test
    void sunIntensityZeroAtNight() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        cycle.update(50f); // midnight
        assertEquals(0f, cycle.getSunIntensity(), 0.01f);
    }

    @Test
    void sunIntensityOneAtNoon() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        assertEquals(1f, cycle.getSunIntensity(), 0.1f);
    }

    @Test
    void ambientIntensityAlwaysPositive() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        assertTrue(cycle.getAmbientIntensity() > 0f);
        cycle.update(50f); // midnight
        assertTrue(cycle.getAmbientIntensity() > 0f, "Ambient should be > 0 even at night");
    }

    @Test
    void starVisibilityOneAtNightZeroAtDay() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, false);
        assertEquals(0f, cycle.getStarVisibility(), 0.1f);
        cycle.update(50f); // midnight
        assertEquals(1f, cycle.getStarVisibility(), 0.1f);
    }

    @Test
    void tidallyLockedSunDoesNotMove() {
        DayNightCycle cycle = new DayNightCycle(100f, 0f, true);
        Vector3 sunA = new Vector3(cycle.getSunDirection());
        cycle.update(50f);
        Vector3 sunB = new Vector3(cycle.getSunDirection());
        assertEquals(sunA.x, sunB.x, 1e-5f);
        assertEquals(sunA.y, sunB.y, 1e-5f);
        assertEquals(sunA.z, sunB.z, 1e-5f);
    }

    @Test
    void sunDirectionIsNormalized() {
        DayNightCycle cycle = new DayNightCycle(100f, 23.5f, false);
        for (float t = 0; t < 100f; t += 5f) {
            cycle.update(5f);
            Vector3 dir = cycle.getSunDirection();
            assertEquals(1f, dir.len(), 0.001f,
                "Sun direction not normalized at time " + t);
        }
    }
}
