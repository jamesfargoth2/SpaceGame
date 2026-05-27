package com.galacticodyssey.persistence.snapshots;

import java.util.HashMap;
import java.util.Map;

public class ItemSnapshot {
    public String itemId;
    public String itemType;
    public String quality;
    public String displayName;
    public float weight;
    public int gridX;
    public int gridY;
    public int gridWidth;
    public int gridHeight;
    public int stackCount;
    public int maxStack;
    public float durability;
    public float maxDurability;
    public Map<String, Object> customData = new HashMap<>();

    public ItemSnapshot() {}
}
