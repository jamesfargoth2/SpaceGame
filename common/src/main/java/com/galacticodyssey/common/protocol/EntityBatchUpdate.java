package com.galacticodyssey.common.protocol;

public class EntityBatchUpdate extends NetworkMessage {
    public int serverTick;
    public int lastProcessedInputSequence;
    public EntityStateUpdate[] updates;

    public EntityBatchUpdate() {}
}
