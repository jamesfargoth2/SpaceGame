package com.galacticodyssey.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropulsionUtilTest {

    @Test
    void deltaVMatchesTsiolkovsky() {
        // Isp=300s, wetMass=10000kg, dryMass=5000kg
        // Expected: 300 * 9.80665 * ln(10000/5000) = 300 * 9.80665 * ln(2) ~ 2038.6 m/s
        float dv = PropulsionUtil.deltaVBudget(300f, 10000f, 5000f);
        assertEquals(2038.6f, dv, 5f);
    }

    @Test
    void deltaVZeroWhenNoFuel() {
        float dv = PropulsionUtil.deltaVBudget(300f, 5000f, 5000f);
        assertEquals(0f, dv);
    }

    @Test
    void deltaVZeroWhenInvalidMass() {
        assertEquals(0f, PropulsionUtil.deltaVBudget(300f, 3000f, 5000f));
        assertEquals(0f, PropulsionUtil.deltaVBudget(300f, 5000f, 0f));
    }

    @Test
    void massFlowRateFormula() {
        // F = 50000 N, Isp = 350s
        // ṁ = F / (Isp * g0) = 50000 / (350 * 9.80665) ~ 14.57 kg/s
        float rate = PropulsionUtil.massFlowRate(50000f, 350f);
        float expected = 50000f / (350f * PropulsionUtil.G0);
        assertEquals(expected, rate, 0.01f);
    }

    @Test
    void burnTimeFormula() {
        float thrust = 50000f;
        float isp = 350f;
        float fuelMass = 1000f;
        float rate = PropulsionUtil.massFlowRate(thrust, isp);
        float expected = fuelMass / rate;
        float result = PropulsionUtil.burnTime(fuelMass, thrust, isp);
        assertEquals(expected, result, 0.01f);
    }

    @Test
    void remainingDeltaVDecreasesWithFuel() {
        float isp = 350f;
        float dryMass = 5000f;
        float dvFull = PropulsionUtil.remainingDeltaV(isp, 1000f, dryMass);
        float dvHalf = PropulsionUtil.remainingDeltaV(isp, 500f, dryMass);
        float dvEmpty = PropulsionUtil.remainingDeltaV(isp, 0f, dryMass);
        assertTrue(dvFull > dvHalf, "Full fuel should give more dV than half");
        assertTrue(dvHalf > dvEmpty, "Half fuel should give more dV than empty");
        assertEquals(0f, dvEmpty);
    }
}
