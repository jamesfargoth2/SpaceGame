package com.galacticodyssey.equipment.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.equipment.items.Item;

public final class ItemAddedEvent {
    public final Entity entity;
    public final Item item;
    public final int gridX;
    public final int gridY;

    public ItemAddedEvent(Entity entity, Item item, int gridX, int gridY) {
        this.entity = entity;
        this.item = item;
        this.gridX = gridX;
        this.gridY = gridY;
    }
}
