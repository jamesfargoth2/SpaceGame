package com.galacticodyssey.combat.fleet.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.FleetSnapshot;
import java.util.ArrayList;
import java.util.List;

public class FleetComponent implements Component, Snapshotable<FleetSnapshot> {
    public String fleetId;
    public String factionId;
    public String fleetName;
    public Entity admiralEntity;
    public Entity flagshipEntity;
    public FleetDoctrine doctrine = FleetDoctrine.BALANCED;
    public FleetState state = FleetState.MUSTERING;
    public float aggregateFirepower;
    public float aggregateHP;
    public float aggregateSpeed;
    public final List<FleetShipEntry> composition = new ArrayList<>();
    public boolean expanded;

    public void recomputeAggregates() {
        aggregateFirepower = 0f;
        aggregateHP = 0f;
        aggregateSpeed = Float.MAX_VALUE;
        for (FleetShipEntry entry : composition) {
            aggregateFirepower += entry.totalFirepower();
            aggregateHP += entry.totalHp();
            aggregateSpeed = Math.min(aggregateSpeed, entry.shipClass.baseSpeed);
        }
        if (composition.isEmpty()) {
            aggregateSpeed = 0f;
        }
    }

    public float lossRatio() {
        float maxHP = 0f;
        for (FleetShipEntry entry : composition) {
            maxHP += entry.shipClass.baseHullHp * entry.count;
        }
        if (maxHP <= 0f) return 1f;
        return 1f - (aggregateHP / maxHP);
    }

    @Override
    public FleetSnapshot takeSnapshot() {
        FleetSnapshot s = new FleetSnapshot();
        s.fleetId = fleetId;
        s.factionId = factionId;
        s.fleetName = fleetName;
        s.doctrine = doctrine.name();
        s.state = state.name();
        s.aggregateFirepower = aggregateFirepower;
        s.aggregateHP = aggregateHP;
        s.aggregateSpeed = aggregateSpeed;
        s.expanded = expanded;
        for (FleetShipEntry entry : composition) {
            FleetSnapshot.FleetShipEntrySnapshot es = new FleetSnapshot.FleetShipEntrySnapshot();
            es.shipClass = entry.shipClass.name();
            es.count = entry.count;
            es.avgHpRatio = entry.avgHpRatio;
            s.composition.add(es);
        }
        return s;
    }

    @Override
    public void restoreFromSnapshot(FleetSnapshot s) {
        fleetId = s.fleetId;
        factionId = s.factionId;
        fleetName = s.fleetName;
        doctrine = FleetDoctrine.valueOf(s.doctrine);
        state = FleetState.valueOf(s.state);
        aggregateFirepower = s.aggregateFirepower;
        aggregateHP = s.aggregateHP;
        aggregateSpeed = s.aggregateSpeed;
        expanded = s.expanded;
        composition.clear();
        for (FleetSnapshot.FleetShipEntrySnapshot es : s.composition) {
            composition.add(new FleetShipEntry(
                FleetShipClass.valueOf(es.shipClass), es.count, es.avgHpRatio));
        }
    }
}
