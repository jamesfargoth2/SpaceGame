package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.ShipDataSnapshot;
import com.galacticodyssey.ship.HullGeometry;
import com.galacticodyssey.ship.ShipBlueprint;
import com.galacticodyssey.ship.ShipSizeClass;

public class ShipDataComponent implements Component, Snapshotable<ShipDataSnapshot> {
    public ShipBlueprint blueprint;
    public float mass;
    public float maxThrust;
    public float maxTurnRate;
    public float maxSpeed;
    public float hullHp;
    public float currentHullHp;
    public HullGeometry hullGeometry; // transient, used for deferred Mesh creation in GameScreen

    @Override
    public ShipDataSnapshot takeSnapshot() {
        ShipDataSnapshot s = new ShipDataSnapshot();
        if (blueprint != null) {
            s.blueprintSeed = blueprint.seed;
            s.sizeClass = blueprint.sizeClass.name();
            s.spineLength = blueprint.spineLength;
            s.crossSectionCount = blueprint.crossSectionCount;
            s.maxWidth = blueprint.maxWidth;
            s.maxHeight = blueprint.maxHeight;
            s.wingPairs = blueprint.wingPairs;
            s.enginePodCount = blueprint.enginePodCount;
        }
        s.mass = mass;
        s.maxThrust = maxThrust;
        s.maxTurnRate = maxTurnRate;
        s.maxSpeed = maxSpeed;
        s.hullHp = hullHp;
        s.currentHullHp = currentHullHp;
        return s;
    }

    @Override
    public void restoreFromSnapshot(ShipDataSnapshot s) {
        blueprint = new ShipBlueprint(s.blueprintSeed, ShipSizeClass.valueOf(s.sizeClass));
        mass = s.mass;
        maxThrust = s.maxThrust;
        maxTurnRate = s.maxTurnRate;
        maxSpeed = s.maxSpeed;
        hullHp = s.hullHp;
        currentHullHp = s.currentHullHp;
        // hullGeometry is intentionally not restored — regenerated from blueprint seed on load
    }
}
