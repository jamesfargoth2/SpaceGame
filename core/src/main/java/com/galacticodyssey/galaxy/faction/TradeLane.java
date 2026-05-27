package com.galacticodyssey.galaxy.faction;

/** A trade route connecting two star systems. */
public final class TradeLane {

    public final long fromSystemId;
    public final long toSystemId;
    public final boolean isMajor;
    /** Traffic volume, 0-1. */
    public final float traffic;

    public TradeLane(long fromSystemId, long toSystemId, boolean isMajor, float traffic) {
        this.fromSystemId = fromSystemId;
        this.toSystemId = toSystemId;
        this.isMajor = isMajor;
        this.traffic = traffic;
    }
}
