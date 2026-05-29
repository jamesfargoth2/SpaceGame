package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.PlayerGarageSnapshot;

import java.util.ArrayList;
import java.util.List;

/** A player's stored ships (from hijack/tow). Lives on the player entity. */
public class PlayerGarageComponent implements Component, Snapshotable<PlayerGarageSnapshot> {

    /** One stored ship: hull identity (seed + size class) + how it was acquired. */
    public static final class GarageEntry {
        public String shipName;
        public long seed;
        public String sizeClass;
        public String acquiredVia; // "HIJACK" | "TOW"
        public GarageEntry() {}
    }

    public final List<GarageEntry> ships = new ArrayList<>();

    @Override
    public PlayerGarageSnapshot takeSnapshot() {
        PlayerGarageSnapshot s = new PlayerGarageSnapshot();
        for (GarageEntry e : ships) {
            PlayerGarageSnapshot.Entry se = new PlayerGarageSnapshot.Entry();
            se.shipName = e.shipName;
            se.seed = e.seed;
            se.sizeClass = e.sizeClass;
            se.acquiredVia = e.acquiredVia;
            s.entries.add(se);
        }
        return s;
    }

    @Override
    public void restoreFromSnapshot(PlayerGarageSnapshot s) {
        ships.clear();
        for (PlayerGarageSnapshot.Entry se : s.entries) {
            GarageEntry e = new GarageEntry();
            e.shipName = se.shipName;
            e.seed = se.seed;
            e.sizeClass = se.sizeClass;
            e.acquiredVia = se.acquiredVia;
            ships.add(e);
        }
    }
}
