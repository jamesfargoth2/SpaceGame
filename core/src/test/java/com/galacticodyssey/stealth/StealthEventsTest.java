package com.galacticodyssey.stealth;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.events.ActiveScanEvent;
import com.galacticodyssey.core.events.NoiseBurstEvent;
import com.galacticodyssey.stealth.events.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StealthEventsTest {

    @Test
    void noiseBurstEvent_fieldsSet() {
        NoiseBurstEvent e = new NoiseBurstEvent(1, 2, 3, 10f, 0.8f);
        assertEquals(1f, e.x); assertEquals(2f, e.y); assertEquals(3f, e.z);
        assertEquals(10f, e.radius); assertEquals(0.8f, e.intensity);
    }

    @Test
    void activeScanEvent_fieldsSet() {
        ActiveScanEvent e = new ActiveScanEvent(0, 0, 0, 500f, 2.5f);
        assertEquals(500f, e.range); assertEquals(2.5f, e.pingMultiplier);
    }

    @Test
    void awarenessChangedEvent_copiesPosition() {
        Vector3 pos = new Vector3(5, 0, 3);
        AwarenessChangedEvent e = new AwarenessChangedEvent(
            "npc1", AwarenessState.UNAWARE, AwarenessState.CURIOUS, pos);
        pos.set(0, 0, 0); // mutate original
        assertEquals(5f, e.lastKnownPosition.x, 0.001f); // copy is unchanged
        assertEquals(AwarenessState.CURIOUS, e.newState);
    }

    @Test
    void darkModeToggledEvent_activeFlag() {
        assertTrue(new DarkModeToggledEvent(true).active);
        assertFalse(new DarkModeToggledEvent(false).active);
    }

    @Test
    void stealthHUDUpdateEvent_fieldsSet() {
        StealthHUDUpdateEvent e = new StealthHUDUpdateEvent(AwarenessState.ALERTED, 15.5f);
        assertEquals(AwarenessState.ALERTED, e.highestNearbyState);
        assertEquals(15.5f, e.nearestThreatDistance, 0.001f);
    }
}
