package com.galacticodyssey.economy.events;

public final class SurplusEvent {
    public final String planetId;
    public final String commodityId;
    public final int excess;

    public SurplusEvent(String planetId, String commodityId, int excess) {
        this.planetId = planetId;
        this.commodityId = commodityId;
        this.excess = excess;
    }
}
