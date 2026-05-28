package com.galacticodyssey.common.protocol;

public class Disconnect extends NetworkMessage {
    public enum Reason {
        CLIENT_QUIT, TIMEOUT, KICKED, SERVER_SHUTDOWN, ZONE_REDIRECT
    }

    public Reason reason;

    public Disconnect() {}
}
