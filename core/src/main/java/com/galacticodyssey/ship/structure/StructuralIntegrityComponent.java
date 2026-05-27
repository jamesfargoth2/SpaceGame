package com.galacticodyssey.ship.structure;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.StructuralIntegritySnapshot;

/**
 * Ashley component that holds every {@link StructuralZone} of a ship.
 */
public class StructuralIntegrityComponent implements Component, Snapshotable<StructuralIntegritySnapshot> {

    public final Array<StructuralZone> zones = new Array<>();

    /**
     * Finds a zone by its id, or {@code null} if not present.
     */
    public StructuralZone getZone(ZoneId id) {
        for (int i = 0, n = zones.size; i < n; i++) {
            if (zones.get(i).id == id) {
                return zones.get(i);
            }
        }
        return null;
    }

    @Override
    public StructuralIntegritySnapshot takeSnapshot() {
        StructuralIntegritySnapshot s = new StructuralIntegritySnapshot();
        for (int i = 0, n = zones.size; i < n; i++) {
            StructuralZone zone = zones.get(i);
            StructuralIntegritySnapshot.ZoneData zd = new StructuralIntegritySnapshot.ZoneData();
            zd.zoneName = zone.id.name();
            zd.integrity = zone.integrity;
            zd.pressure = zone.pressure;
            zd.isBreached = zone.isBreached;
            zd.breachArea = zone.breachArea;
            s.zones.add(zd);
        }
        return s;
    }

    @Override
    public void restoreFromSnapshot(StructuralIntegritySnapshot s) {
        for (StructuralIntegritySnapshot.ZoneData zd : s.zones) {
            ZoneId id = ZoneId.valueOf(zd.zoneName);
            StructuralZone zone = getZone(id);
            if (zone != null) {
                zone.integrity = zd.integrity;
                zone.pressure = zd.pressure;
                zone.isBreached = zd.isBreached;
                zone.breachArea = zd.breachArea;
            }
        }
    }
}
