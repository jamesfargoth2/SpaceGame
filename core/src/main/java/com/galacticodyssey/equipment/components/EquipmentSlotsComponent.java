package com.galacticodyssey.equipment.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.items.Item;
import java.util.EnumMap;
import java.util.Map;

public class EquipmentSlotsComponent implements Component {
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
}
