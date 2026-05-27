package com.galacticodyssey.economy.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.CargoBaySnapshot;

import java.util.HashMap;
import java.util.Map;

public class CargoBayComponent implements Component, Snapshotable<CargoBaySnapshot> {
    public float capacity;
    public final Map<String, Integer> contents = new HashMap<>();
    public float usedVolume;

    @Override
    public CargoBaySnapshot takeSnapshot() {
        CargoBaySnapshot s = new CargoBaySnapshot();
        s.capacity = capacity;
        s.contents = new HashMap<>(contents);
        s.usedVolume = usedVolume;
        return s;
    }

    @Override
    public void restoreFromSnapshot(CargoBaySnapshot s) {
        capacity = s.capacity;
        contents.clear();
        if (s.contents != null) {
            contents.putAll(s.contents);
        }
        usedVolume = s.usedVolume;
    }
}
