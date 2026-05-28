package com.galacticodyssey.common.protocol;

import java.util.UUID;

public class ZoneJoinRequest extends NetworkMessage {
    public String sessionToken;
    public UUID zoneId;
}
