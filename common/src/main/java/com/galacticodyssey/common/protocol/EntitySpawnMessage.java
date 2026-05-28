package com.galacticodyssey.common.protocol;

import java.util.UUID;

public class EntitySpawnMessage extends NetworkMessage {
    public int networkId;
    public String entityType;
    public UUID persistenceId;
    public byte[] componentData;

    public EntitySpawnMessage() {}
}
