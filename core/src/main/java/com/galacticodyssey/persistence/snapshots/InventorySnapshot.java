package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class InventorySnapshot {
    public int gridWidth;
    public int gridHeight;
    public float maxWeight;
    public List<ItemSnapshot> items = new ArrayList<>();

    public InventorySnapshot() {}
}
