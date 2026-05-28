package com.galacticodyssey.common.protocol;

import java.util.UUID;

public class HandoffTransferAck extends NetworkMessage {
    public int entityNetworkId;
    public UUID sourceZoneId;
    public UUID targetZoneId;
    public boolean success;
}
