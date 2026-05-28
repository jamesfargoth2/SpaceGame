package com.galacticodyssey.combat.fleet.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.data.FleetRole;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.FleetMemberSnapshot;

public class FleetMemberComponent implements Component, Snapshotable<FleetMemberSnapshot> {

    /** Distance-based AI fidelity tier assigned by {@link com.galacticodyssey.combat.fleet.systems.FleetLODSystem}. */
    public enum LODTier { FULL, SIMPLIFIED, ABSTRACT }

    public Entity fleetEntity;
    public String fleetId;
    public int squadronIndex;
    public FleetRole role = FleetRole.ESCORT;
    public int formationSlotIndex;
    public final Vector3 localFormationOffset = new Vector3();
    public LODTier lodTier = LODTier.FULL;

    @Override
    public FleetMemberSnapshot takeSnapshot() {
        FleetMemberSnapshot s = new FleetMemberSnapshot();
        s.fleetId = fleetId;
        s.squadronIndex = squadronIndex;
        s.role = role.name();
        s.formationSlotIndex = formationSlotIndex;
        s.lodTier = lodTier.name();
        return s;
    }

    @Override
    public void restoreFromSnapshot(FleetMemberSnapshot s) {
        fleetId = s.fleetId;
        squadronIndex = s.squadronIndex;
        role = FleetRole.valueOf(s.role);
        formationSlotIndex = s.formationSlotIndex;
        lodTier = LODTier.valueOf(s.lodTier);
    }
}
