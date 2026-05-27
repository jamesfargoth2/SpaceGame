package com.galacticodyssey.persistence;

import java.util.UUID;

public class ManifestData {
    public static final int CURRENT_VERSION = 1;

    public String saveName;
    public long timestampMillis;
    public int saveVersion = CURRENT_VERSION;
    public long galaxySeed;
    public UUID playerEntityId;
    public UUID currentSystemId;

    public ManifestData() {}

    public ManifestData(String saveName, long galaxySeed, UUID playerEntityId, UUID currentSystemId) {
        this.saveName = saveName;
        this.timestampMillis = System.currentTimeMillis();
        this.galaxySeed = galaxySeed;
        this.playerEntityId = playerEntityId;
        this.currentSystemId = currentSystemId;
    }
}
