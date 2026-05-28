package com.galacticodyssey.common.protocol;

import java.util.UUID;

public class HandoffPrepare extends NetworkMessage {
    public int entityNetworkId;
    public UUID sourceZoneId;
    public UUID targetZoneId;
    public byte[] serializedEntityData;
    public String playerSessionToken;
}
