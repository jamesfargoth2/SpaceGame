package com.galacticodyssey.common.protocol;

public class HeartbeatAck extends NetworkMessage {
    public long clientTimestamp;
    public long serverTimestamp;

    public HeartbeatAck() {}
}
