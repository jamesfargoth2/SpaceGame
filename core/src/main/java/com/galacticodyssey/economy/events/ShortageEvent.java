package com.galacticodyssey.economy.events;

public final class ShortageEvent {
    public final String planetId;
    public final String commodityId;
    public final int deficit;

    public ShortageEvent(String planetId, String commodityId, int deficit) {
        this.planetId = planetId;
        this.commodityId = commodityId;
        this.deficit = deficit;
    }
}
