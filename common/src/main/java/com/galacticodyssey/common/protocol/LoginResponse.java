package com.galacticodyssey.common.protocol;

import java.util.UUID;

public class LoginResponse extends NetworkMessage {
    public boolean success;
    public String sessionToken;
    public String zoneServerHost;
    public int zoneServerTcpPort;
    public int zoneServerUdpPort;
    public String zoneServerAddress;
    public UUID playerId;
    public String failureReason;

    public LoginResponse() {}
}
