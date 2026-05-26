package com.galacticodyssey.equipment.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.equipment.items.Item;

public final class ItemRemovedEvent {
    public final Entity entity;
    public final Item item;

    public ItemRemovedEvent(Entity entity, Item item) {
        this.entity = entity;
        this.item = item;
    }
}
