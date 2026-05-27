package com.galacticodyssey.economy.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.PlayerWalletSnapshot;

public class PlayerWalletComponent implements Component, Snapshotable<PlayerWalletSnapshot> {
    public long credits;

    @Override
    public PlayerWalletSnapshot takeSnapshot() {
        PlayerWalletSnapshot s = new PlayerWalletSnapshot();
        s.credits = credits;
        return s;
    }

    @Override
    public void restoreFromSnapshot(PlayerWalletSnapshot s) {
        credits = s.credits;
    }
}
