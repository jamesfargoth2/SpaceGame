package com.galacticodyssey.equipment.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.equipment.items.Item;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.InventorySnapshot;
import com.galacticodyssey.persistence.snapshots.ItemSnapshot;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class InventoryComponent implements Component, Snapshotable<InventorySnapshot> {
    public int gridWidth;
    public int gridHeight;
    public float maxWeight;
    private Item[][] grid;
    private final List<Item> allItems = new ArrayList<>();

    /** No-arg constructor for deserialization / registry restore. */
    public InventoryComponent() {
        this(0, 0, 0f);
    }

    public InventoryComponent(int gridWidth, int gridHeight, float maxWeight) {
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.maxWeight = maxWeight;
        this.grid = new Item[gridWidth][gridHeight];
    }

    public boolean tryAdd(Item item) {
        if (getCurrentWeight() + item.getTotalWeight() > maxWeight) {
            return false;
        }
        if (item.stackable) {
            for (Item existing : allItems) {
                if (existing.id.equals(item.id) && existing.getSpaceRemaining() > 0) {
                    int transfer = Math.min(item.currentStack, existing.getSpaceRemaining());
                    existing.currentStack += transfer;
                    item.currentStack -= transfer;
                    if (item.currentStack <= 0) {
                        return true;
                    }
                }
            }
        }
        int[] pos = findFit(item.gridWidth, item.gridHeight);
        if (pos == null) {
            return false;
        }
        placeAt(item, pos[0], pos[1]);
        return true;
    }

    public boolean remove(Item item) {
        if (!allItems.remove(item)) {
            return false;
        }
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                if (grid[x][y] == item) {
                    grid[x][y] = null;
                }
            }
        }
        return true;
    }

    public Item getItemAt(int x, int y) {
        if (x < 0 || x >= gridWidth || y < 0 || y >= gridHeight) {
            return null;
        }
        return grid[x][y];
    }

    public float getCurrentWeight() {
        float total = 0;
        for (Item item : allItems) {
            total += item.getTotalWeight();
        }
        return total;
    }

    public List<Item> getAllItems() {
        return allItems;
    }

    public int getItemCount() {
        return allItems.size();
    }

    private int[] findFit(int w, int h) {
        for (int y = 0; y <= gridHeight - h; y++) {
            for (int x = 0; x <= gridWidth - w; x++) {
                if (canPlace(x, y, w, h)) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private boolean canPlace(int startX, int startY, int w, int h) {
        for (int x = startX; x < startX + w; x++) {
            for (int y = startY; y < startY + h; y++) {
                if (grid[x][y] != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private void placeAt(Item item, int startX, int startY) {
        for (int x = startX; x < startX + item.gridWidth; x++) {
            for (int y = startY; y < startY + item.gridHeight; y++) {
                grid[x][y] = item;
            }
        }
        allItems.add(item);
    }

    // -------------------------------------------------------------------------
    // Snapshotable
    // -------------------------------------------------------------------------

    @Override
    public InventorySnapshot takeSnapshot() {
        InventorySnapshot snap = new InventorySnapshot();
        snap.gridWidth  = gridWidth;
        snap.gridHeight = gridHeight;
        snap.maxWeight  = maxWeight;

        // Use an identity map to find each item's top-left anchor only once.
        Map<Item, int[]> anchors = new IdentityHashMap<>();
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                Item item = grid[x][y];
                if (item != null && !anchors.containsKey(item)) {
                    anchors.put(item, new int[]{x, y});
                }
            }
        }

        for (Item item : allItems) {
            ItemSnapshot s = item.toItemSnapshot();
            int[] anchor = anchors.get(item);
            if (anchor != null) {
                s.gridX = anchor[0];
                s.gridY = anchor[1];
            }
            snap.items.add(s);
        }
        return snap;
    }

    @Override
    public void restoreFromSnapshot(InventorySnapshot snap) {
        // Restore grid dimensions and weight limit from the snapshot.
        this.gridWidth  = snap.gridWidth;
        this.gridHeight = snap.gridHeight;
        this.maxWeight  = snap.maxWeight;
        this.grid = new Item[gridWidth][gridHeight];
        allItems.clear();

        for (ItemSnapshot s : snap.items) {
            Item item = Item.fromItemSnapshot(s);
            // Place directly at the recorded position, bypassing weight/fit checks
            // so restore is always exact regardless of order.
            placeAt(item, s.gridX, s.gridY);
        }
    }
}
