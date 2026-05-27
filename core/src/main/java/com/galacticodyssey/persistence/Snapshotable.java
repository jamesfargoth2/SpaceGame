package com.galacticodyssey.persistence;

public interface Snapshotable<S> {
    S takeSnapshot();
    void restoreFromSnapshot(S snapshot);
}
