package com.galacticodyssey.economy.events;

import com.galacticodyssey.economy.service.TradeFailureReason;

public final class TradeFailedEvent {
    public final TradeFailureReason reason;
    public final String commodityId;
    public final int quantity;

    public TradeFailedEvent(TradeFailureReason reason, String commodityId, int quantity) {
        this.reason = reason;
        this.commodityId = commodityId;
        this.quantity = quantity;
    }
}
