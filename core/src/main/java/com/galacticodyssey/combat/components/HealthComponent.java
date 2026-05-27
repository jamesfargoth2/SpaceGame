package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.HealthSnapshot;

public class HealthComponent implements Component, Snapshotable<HealthSnapshot> {
    public float currentHP = 100f;
    public float maxHP = 100f;
    public boolean alive = true;

    @Override
    public HealthSnapshot takeSnapshot() {
        HealthSnapshot s = new HealthSnapshot();
        s.currentHP = currentHP;
        s.maxHP = maxHP;
        s.alive = alive;
        return s;
    }

    @Override
    public void restoreFromSnapshot(HealthSnapshot s) {
        currentHP = s.currentHP;
        maxHP = s.maxHP;
        alive = s.alive;
    }
}
