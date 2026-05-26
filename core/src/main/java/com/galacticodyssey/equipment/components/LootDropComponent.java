package com.galacticodyssey.equipment.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.equipment.items.Item;
import java.util.ArrayList;
import java.util.List;

public class LootDropComponent implements Component {
    public final List<Item> items = new ArrayList<>();
    public final Vector3 position = new Vector3();
    public float despawnTimer;

    public LootDropComponent() {
        this.despawnTimer = -1f;
    }
}
