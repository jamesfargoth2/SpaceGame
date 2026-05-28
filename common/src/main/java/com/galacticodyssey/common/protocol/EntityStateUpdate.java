package com.galacticodyssey.common.protocol;

public class EntityStateUpdate {
    public int networkId;
    public int serverTick;
    public long dirtyMask;
    public byte[] payload;

    public EntityStateUpdate() {}
}
