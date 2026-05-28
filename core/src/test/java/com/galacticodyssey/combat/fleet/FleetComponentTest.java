package com.galacticodyssey.combat.fleet;

import com.galacticodyssey.combat.fleet.components.FleetComponent;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.persistence.snapshots.FleetSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetComponentTest {

    @Test
    void computeAggregateFirepower() {
        FleetComponent fc = new FleetComponent();
        fc.composition.add(new FleetShipEntry(FleetShipClass.FIGHTER, 10, 1.0f));
        fc.composition.add(new FleetShipEntry(FleetShipClass.CRUISER, 3, 1.0f));
        fc.recomputeAggregates();

        float expected = FleetShipClass.FIGHTER.firepowerWeight * 10
                       + FleetShipClass.CRUISER.firepowerWeight * 3;
        assertEquals(expected, fc.aggregateFirepower, 0.01f);
    }

    @Test
    void aggregateSpeedLimitedBySlowest() {
        FleetComponent fc = new FleetComponent();
        fc.composition.add(new FleetShipEntry(FleetShipClass.FIGHTER, 5, 1.0f));
        fc.composition.add(new FleetShipEntry(FleetShipClass.BATTLESHIP, 1, 1.0f));
        fc.recomputeAggregates();

        assertEquals(FleetShipClass.BATTLESHIP.baseSpeed, fc.aggregateSpeed, 0.01f);
    }

    @Test
    void snapshotRoundTrip() {
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "fleet-001";
        fc.factionId = "militarist-1";
        fc.fleetName = "Iron Fist";
        fc.doctrine = FleetDoctrine.AGGRESSIVE;
        fc.state = FleetState.PATROL;
        fc.composition.add(new FleetShipEntry(FleetShipClass.CRUISER, 3, 0.8f));
        fc.recomputeAggregates();

        FleetSnapshot snap = fc.takeSnapshot();
        FleetComponent restored = new FleetComponent();
        restored.restoreFromSnapshot(snap);

        assertEquals("fleet-001", restored.fleetId);
        assertEquals("militarist-1", restored.factionId);
        assertEquals(FleetDoctrine.AGGRESSIVE, restored.doctrine);
        assertEquals(FleetState.PATROL, restored.state);
        assertEquals(1, restored.composition.size());
        assertEquals(FleetShipClass.CRUISER, restored.composition.get(0).shipClass);
        assertEquals(3, restored.composition.get(0).count);
        assertEquals(fc.aggregateFirepower, restored.aggregateFirepower, 0.01f);
    }
}
