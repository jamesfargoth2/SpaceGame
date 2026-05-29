package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.OwnedShipSnapshot;

/** Marks who owns a ship. Added to captured/towed ships. */
public class OwnedShipComponent implements Component, Snapshotable<OwnedShipSnapshot> {

    public enum Owner { NONE, PLAYER, NPC }

    public Owner owner = Owner.NONE;
    public String factionId = "independent";

    @Override
    public OwnedShipSnapshot takeSnapshot() {
        OwnedShipSnapshot s = new OwnedShipSnapshot();
        s.owner = owner.name();
        s.factionId = factionId;
        return s;
    }

    @Override
    public void restoreFromSnapshot(OwnedShipSnapshot s) {
        owner = Owner.valueOf(s.owner);
        factionId = s.factionId;
    }
}
