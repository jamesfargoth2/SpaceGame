package com.galacticodyssey.equipment.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.equipment.items.Item;
import java.util.List;

public final class LootDroppedEvent {
    public final Entity lootEntity;
    public final Vector3 position;
    public final List<Item> items;

    public LootDroppedEvent(Entity lootEntity, Vector3 position, List<Item> items) {
        this.lootEntity = lootEntity;
        this.position = position;
        this.items = items;
    }
}
