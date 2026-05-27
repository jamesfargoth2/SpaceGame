package com.galacticodyssey.equipment.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.items.Item;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.EquipmentSlotsSnapshot;
import com.galacticodyssey.persistence.snapshots.ItemSnapshot;
import java.util.EnumMap;
import java.util.Map;

public class EquipmentSlotsComponent implements Component, Snapshotable<EquipmentSlotsSnapshot> {
    private final Map<EquipmentSlot, Item> slots = new EnumMap<>(EquipmentSlot.class);

    public Item getSlot(EquipmentSlot slot) {
        return slots.get(slot);
    }

    public Item setSlot(EquipmentSlot slot, Item item) {
        Item previous = slots.put(slot, item);
        return previous;
    }

    public Item clearSlot(EquipmentSlot slot) {
        return slots.remove(slot);
    }

    public boolean isSlotEmpty(EquipmentSlot slot) {
        return !slots.containsKey(slot);
    }

    public Map<EquipmentSlot, Item> getAllEquipped() {
        return slots;
    }

    // -------------------------------------------------------------------------
    // Snapshotable
    // -------------------------------------------------------------------------

    @Override
    public EquipmentSlotsSnapshot takeSnapshot() {
        EquipmentSlotsSnapshot snap = new EquipmentSlotsSnapshot();
        for (Map.Entry<EquipmentSlot, Item> entry : slots.entrySet()) {
            if (entry.getValue() != null) {
                snap.slots.put(entry.getKey().name(), entry.getValue().toItemSnapshot());
            }
        }
        return snap;
    }

    @Override
    public void restoreFromSnapshot(EquipmentSlotsSnapshot snap) {
        slots.clear();
        for (Map.Entry<String, ItemSnapshot> entry : snap.slots.entrySet()) {
            try {
                EquipmentSlot slot = EquipmentSlot.valueOf(entry.getKey());
                Item item = Item.fromItemSnapshot(entry.getValue());
                slots.put(slot, item);
            } catch (IllegalArgumentException ignored) {
                // Unknown slot name from a future game version — skip gracefully.
            }
        }
    }
}
