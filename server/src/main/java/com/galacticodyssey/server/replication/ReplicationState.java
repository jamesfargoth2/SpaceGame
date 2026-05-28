package com.galacticodyssey.server.replication;

/**
 * Tracks what the server has already sent to a specific client about a specific entity.
 *
 * <p>The {@link com.galacticodyssey.server.replication.ServerReplicationSystem} maintains a
 * {@code Map<Integer, Map<Integer, ReplicationState>>} (connectionId → networkId → state)
 * to detect interest changes, drive delta updates, and support future snapshot compression.
 */
public class ReplicationState {

    private final int networkId;
    private InterestTier currentTier = InterestTier.NONE;
    private int lastSentTick = -1;
    private byte[] lastSentSnapshot;

    public ReplicationState(int networkId) {
        this.networkId = networkId;
    }

    public int getNetworkId() {
        return networkId;
    }

    public InterestTier getCurrentTier() {
        return currentTier;
    }

    public void setCurrentTier(InterestTier tier) {
        this.currentTier = tier;
    }

    /** Returns {@code true} once {@link #markSent(int)} has been called at least once. */
    public boolean hasBeenSent() {
        return lastSentTick >= 0;
    }

    public int getLastSentTick() {
        return lastSentTick;
    }

    public void markSent(int tick) {
        this.lastSentTick = tick;
    }

    /**
     * Returns {@code true} when the current tier differs from {@code previousTier},
     * indicating the entity has entered or left a replication band.
     */
    public boolean tierChangedFrom(InterestTier previousTier) {
        return currentTier != previousTier;
    }

    public byte[] getLastSentSnapshot() {
        return lastSentSnapshot;
    }

    public void setLastSentSnapshot(byte[] snapshot) {
        this.lastSentSnapshot = snapshot;
    }
}
