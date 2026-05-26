package com.galacticodyssey.economy.events;

public final class TradeCompletedEvent {
    public final String stationId;
    public final String commodityId;
    public final int quantity;
    public final int unitPrice;
    public final int totalPrice;
    public final boolean isBuy;

    public TradeCompletedEvent(String stationId, String commodityId, int quantity,
                                int unitPrice, int totalPrice, boolean isBuy) {
        this.stationId = stationId;
        this.commodityId = commodityId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.isBuy = isBuy;
    }
}
