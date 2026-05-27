package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.SquadRole;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.SquadSnapshot;

public class SquadComponent implements Component, Snapshotable<SquadSnapshot> {
    public int squadId;
    public SquadRole role = SquadRole.FLANKER;
    public final Vector3 formationOffset = new Vector3();

    @Override
    public SquadSnapshot takeSnapshot() {
        SquadSnapshot s = new SquadSnapshot();
        s.squadId = squadId;
        return s;
    }

    @Override
    public void restoreFromSnapshot(SquadSnapshot s) {
        squadId = s.squadId;
    }
}
