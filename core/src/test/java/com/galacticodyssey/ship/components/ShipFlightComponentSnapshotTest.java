package com.galacticodyssey.ship.components;

import com.galacticodyssey.persistence.snapshots.ShipFlightSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipFlightComponentSnapshotTest {

    @Test
    void roundTripPreservesNewFields() {
        ShipFlightComponent c = new ShipFlightComponent();
        c.reverseFraction = 0.4f;
        c.faLinearGain = 1.5f;
        c.faLateralBleed = 1.2f;
        c.blueZoneLow = 0.4f;
        c.blueZoneHigh = 0.8f;
        c.offBandTurnScale = 0.5f;
        c.rotStiffness = 4f;
        c.boostSpeedMultiplier = 1.6f;
        c.boostForce = 40000f;
        c.boostDuration = 5f;
        c.boostEnergyCost = 50f;
        c.boostMaxEnergy = 100f;
        c.boostRechargeRate = 12f;
        c.boostCooldown = 3f;
        c.flightAssistEnabled = false;
        c.boostEnergy = 73f;
        c.boostTimer = 1.5f;
        c.boostCooldownTimer = 2.5f;

        ShipFlightSnapshot s = c.takeSnapshot();
        ShipFlightComponent restored = new ShipFlightComponent();
        restored.restoreFromSnapshot(s);

        assertEquals(0.4f, restored.reverseFraction);
        assertEquals(1.5f, restored.faLinearGain);
        assertEquals(0.8f, restored.blueZoneHigh);
        assertEquals(4f, restored.rotStiffness);
        assertEquals(1.6f, restored.boostSpeedMultiplier);
        assertEquals(40000f, restored.boostForce);
        assertFalse(restored.flightAssistEnabled);
        assertEquals(73f, restored.boostEnergy);
        assertEquals(2.5f, restored.boostCooldownTimer);
    }

    @Test
    void defaultsAreSane() {
        ShipFlightComponent c = new ShipFlightComponent();
        assertTrue(c.flightAssistEnabled, "FA defaults on");
    }
}
