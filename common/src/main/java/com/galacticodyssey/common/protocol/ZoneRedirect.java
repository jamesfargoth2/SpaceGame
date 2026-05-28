package com.galacticodyssey.common.protocol;

import java.util.UUID;

public class ZoneRedirect extends NetworkMessage {
    public String newZoneAddress;
    public String handoffToken;
    public UUID targetZoneId;
}
