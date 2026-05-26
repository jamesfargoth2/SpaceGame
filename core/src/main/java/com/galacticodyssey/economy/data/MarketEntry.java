package com.galacticodyssey.economy.data;

public class MarketEntry {
    public final String commodityId;
    public int stock;
    public int maxStock;
    public float demand;
    public float supplyRate;

    public MarketEntry(String commodityId, int stock, int maxStock, float demand, float supplyRate) {
        this.commodityId = commodityId;
        this.stock = stock;
        this.maxStock = maxStock;
        this.demand = demand;
        this.supplyRate = supplyRate;
    }
}
