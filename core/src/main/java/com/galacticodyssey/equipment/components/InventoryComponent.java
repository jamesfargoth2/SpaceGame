package com.galacticodyssey.equipment.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.equipment.items.Item;
import java.util.ArrayList;
import java.util.List;

public class InventoryComponent implements Component {
    public final int gridWidth;
    public final int gridHeight;
    public final float maxWeight;
    private final Item[][] grid;
    private final List<Item> allItems = new ArrayList<>();

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
}
