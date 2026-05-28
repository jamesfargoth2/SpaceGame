package com.galacticodyssey.crafting.data;

public class RefineryModuleDefinition {
    public final String moduleId;
    public final String name;
    public final int tier;
    public final int maxQueueSize;
    public final float speedMultiplier;
    public final float powerDraw;
    public final float weight;
    public final int cost;

    public RefineryModuleDefinition(String moduleId, String name, int tier,
                                    int maxQueueSize, float speedMultiplier,
                                    float powerDraw, float weight, int cost) {
        this.moduleId = moduleId;
        this.name = name;
        this.tier = tier;
        this.maxQueueSize = maxQueueSize;
        this.speedMultiplier = speedMultiplier;
        this.powerDraw = powerDraw;
        this.weight = weight;
        this.cost = cost;
    }

    public RefineryModuleDefinition() {
        this("", "", 1, 2, 1.0f, 10f, 500f, 5000);
    }
}
