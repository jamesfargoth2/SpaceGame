package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.EngineSpecSnapshot;

public class EngineSpecComponent implements Component, Pool.Poolable, Snapshotable<EngineSpecSnapshot> {

    public String name;
    public float isp = 350f;
    public float maxThrust = 50000f;
    public float currentThrottle;
    public float minThrottle = 0.1f;
    public float throttleResponseRate = 2f;
    public float gimbalAngle = 0.1f;
    public boolean canThrottleOff = true;
    public float actualThrust;

    @Override
    public void reset() {
        name = null;
        isp = 350f;
        maxThrust = 50000f;
        currentThrottle = 0f;
        minThrottle = 0.1f;
        throttleResponseRate = 2f;
        gimbalAngle = 0.1f;
        canThrottleOff = true;
        actualThrust = 0f;
    }

    @Override
    public EngineSpecSnapshot takeSnapshot() {
        EngineSpecSnapshot s = new EngineSpecSnapshot();
        s.name = name;
        s.isp = isp;
        s.maxThrust = maxThrust;
        s.currentThrottle = currentThrottle;
        s.minThrottle = minThrottle;
        s.throttleResponseRate = throttleResponseRate;
        s.gimbalAngle = gimbalAngle;
        s.actualThrust = actualThrust;
        return s;
    }

    @Override
    public void restoreFromSnapshot(EngineSpecSnapshot s) {
        name = s.name;
        isp = s.isp;
        maxThrust = s.maxThrust;
        currentThrottle = s.currentThrottle;
        minThrottle = s.minThrottle;
        throttleResponseRate = s.throttleResponseRate;
        gimbalAngle = s.gimbalAngle;
        actualThrust = s.actualThrust;
    }
}
