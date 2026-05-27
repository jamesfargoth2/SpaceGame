package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.FuelTankSnapshot;

public class FuelTankComponent implements Component, Pool.Poolable, Snapshotable<FuelTankSnapshot> {

    public float maxMass = 1000f;
    public float currentMass = 1000f;
    public final Vector3 localPosition = new Vector3();
    public boolean isVenting;

    @Override
    public void reset() {
        maxMass = 1000f;
        currentMass = 1000f;
        localPosition.setZero();
        isVenting = false;
    }

    @Override
    public FuelTankSnapshot takeSnapshot() {
        FuelTankSnapshot s = new FuelTankSnapshot();
        s.maxMass = maxMass;
        s.currentMass = currentMass;
        s.localX = localPosition.x;
        s.localY = localPosition.y;
        s.localZ = localPosition.z;
        s.isVenting = isVenting;
        return s;
    }

    @Override
    public void restoreFromSnapshot(FuelTankSnapshot s) {
        maxMass = s.maxMass;
        currentMass = s.currentMass;
        localPosition.set(s.localX, s.localY, s.localZ);
        isVenting = s.isVenting;
    }
}
