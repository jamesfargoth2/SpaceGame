package com.galacticodyssey.economy.data;

import java.util.ArrayList;
import java.util.List;

public class CommodityDefinition {
    public String id;
    public String name;
    public CommodityCategory category;
    public CommodityTier tier;
    public int basePrice;
    public float mass;
    public float volume;
    public List<String> tags = new ArrayList<>();
}
