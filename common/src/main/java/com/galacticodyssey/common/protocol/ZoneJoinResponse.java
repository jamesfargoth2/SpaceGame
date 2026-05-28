package com.galacticodyssey.common.protocol;

public class ZoneJoinResponse extends NetworkMessage {
    public boolean success;
    public String failureReason;
    public byte[] worldSnapshotData;
}
