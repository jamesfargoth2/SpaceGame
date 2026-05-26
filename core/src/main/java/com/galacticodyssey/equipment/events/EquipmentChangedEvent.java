package com.galacticodyssey.equipment.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.items.Item;

public final class EquipmentChangedEvent {
    public final Entity entity;
    public final EquipmentSlot slot;
    public final Item oldItem;
    public final Item newItem;

    public EquipmentChangedEvent(Entity entity, EquipmentSlot slot, Item oldItem, Item newItem) {
        this.entity = entity;
        this.slot = slot;
        this.oldItem = oldItem;
        this.newItem = newItem;
    }
}
