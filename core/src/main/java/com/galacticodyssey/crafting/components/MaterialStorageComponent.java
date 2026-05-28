package com.galacticodyssey.crafting.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.crafting.data.MaterialDefinition;
import com.galacticodyssey.crafting.data.MaterialRegistry;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.MaterialStorageSnapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * ECS component for bulk material storage. Tracks quantities of materials by ID
 * and enforces weight and volume capacity limits using definitions from a
 * {@link MaterialRegistry}.
 */
public class MaterialStorageComponent implements Component, Snapshotable<MaterialStorageSnapshot> {

    private final float maxWeight;
    private final float maxVolume;
    private final MaterialRegistry registry;
    private final Map<String, Integer> quantities = new HashMap<>();

    public MaterialStorageComponent(float maxWeight, float maxVolume, MaterialRegistry registry) {
        this.maxWeight = maxWeight;
        this.maxVolume = maxVolume;
        this.registry = registry;
    }

    /**
     * Attempts to add the given amount of a material. Returns false if the material
     * is unknown or the addition would exceed weight or volume capacity. On failure,
     * no state is modified.
     */
    public boolean tryAdd(String materialId, int amount) {
        MaterialDefinition def = registry.get(materialId);
        if (def == null) {
            return false;
        }
        float addedWeight = amount * def.weight;
        float addedVolume = amount * def.volume;
        if (getCurrentWeight() + addedWeight > maxWeight) {
            return false;
        }
        if (getCurrentVolume() + addedVolume > maxVolume) {
            return false;
        }
        quantities.merge(materialId, amount, Integer::sum);
        return true;
    }

    /** Returns the stored quantity of the given material, or 0 if not present. */
    public int getQuantity(String materialId) {
        return quantities.getOrDefault(materialId, 0);
    }

    /** Returns true if the stored quantity of the material is at least {@code amount}. */
    public boolean hasEnough(String materialId, int amount) {
        return getQuantity(materialId) >= amount;
    }

    /**
     * Attempts to consume the given amount of a material. Returns false if insufficient
     * stock is available (no mutation on failure). Removes the map entry if the quantity
     * reaches zero.
     */
    public boolean tryConsume(String materialId, int amount) {
        int current = getQuantity(materialId);
        if (current < amount) {
            return false;
        }
        int remaining = current - amount;
        if (remaining == 0) {
            quantities.remove(materialId);
        } else {
            quantities.put(materialId, remaining);
        }
        return true;
    }

    /** Returns the total weight of all stored materials. */
    public float getCurrentWeight() {
        float total = 0f;
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            MaterialDefinition def = registry.get(entry.getKey());
            if (def != null) {
                total += entry.getValue() * def.weight;
            }
        }
        return total;
    }

    /** Returns the total volume of all stored materials. */
    public float getCurrentVolume() {
        float total = 0f;
        for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
            MaterialDefinition def = registry.get(entry.getKey());
            if (def != null) {
                total += entry.getValue() * def.volume;
            }
        }
        return total;
    }

    /** Returns an unmodifiable view of all stored material quantities. */
    public Map<String, Integer> getAllQuantities() {
        return Collections.unmodifiableMap(quantities);
    }

    public float getMaxWeight() {
        return maxWeight;
    }

    public float getMaxVolume() {
        return maxVolume;
    }

    // -------------------------------------------------------------------------
    // Snapshotable
    // -------------------------------------------------------------------------

    @Override
    public MaterialStorageSnapshot takeSnapshot() {
        MaterialStorageSnapshot snap = new MaterialStorageSnapshot();
        snap.maxWeight = maxWeight;
        snap.maxVolume = maxVolume;
        snap.quantities.putAll(quantities);
        return snap;
    }

    @Override
    public void restoreFromSnapshot(MaterialStorageSnapshot snapshot) {
        quantities.clear();
        quantities.putAll(snapshot.quantities);
    }
}
