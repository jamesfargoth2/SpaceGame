package com.galacticodyssey.ship.ai;

import com.galacticodyssey.persistence.snapshots.ShipPilotAISnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipPilotAIComponentTest {

    @Test
    void snapshotRoundTripsTuningFields() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetypeId = "ace";
        ai.decisionInterval = 0.2f;

        ShipPilotAISnapshot snap = ai.takeSnapshot();
        assertEquals("ace", snap.archetypeId);

        ShipPilotAIComponent restored = new ShipPilotAIComponent();
        restored.restoreFromSnapshot(snap);
        assertEquals("ace", restored.archetypeId);
        assertEquals(0.2f, restored.decisionInterval, 1e-4);
    }
}
