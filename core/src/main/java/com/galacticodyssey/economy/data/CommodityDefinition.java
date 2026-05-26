package com.galacticodyssey.economy.data;

import java.util.HashSet;
import java.util.Set;

public class CommodityDefinition {
    public String id;
    public String name;
    public CommodityCategory category;
    public CommodityTier tier;
    public int basePrice;
    public float mass;
    public float volume;
    public Set<String> tags = new HashSet<>();
}
