package com.galacticodyssey.persistence.snapshots;

import java.util.UUID;

public class BoardingOperationSnapshot {
    public String phase;
    public String attachMethod;
    public boolean playerIsAggressor;
    public UUID aggressorShipId;
    public UUID targetShipId;
    public UUID entryPointId;
    public BoardingOperationSnapshot() {}
}
