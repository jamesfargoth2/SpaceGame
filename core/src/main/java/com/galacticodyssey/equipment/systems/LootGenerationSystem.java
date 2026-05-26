package com.galacticodyssey.equipment.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.equipment.components.ArchetypeComponent;
import com.galacticodyssey.equipment.components.LootDropComponent;
import com.galacticodyssey.equipment.data.LootTable;
import com.galacticodyssey.equipment.data.LootTableRegistry;
import com.galacticodyssey.equipment.events.LootDroppedEvent;
import com.galacticodyssey.equipment.items.AmmoItem;
import com.galacticodyssey.equipment.items.Item;

import java.util.ArrayList;
import java.util.List;

public class LootGenerationSystem extends EntitySystem {
    private static final int PRIORITY = 10;
    private final EventBus eventBus;
    private final LootTableRegistry registry;
    private final List<EntityKilledEvent> pendingKills = new ArrayList<>();

    public LootGenerationSystem(EventBus eventBus, LootTableRegistry registry) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.registry = registry;
        eventBus.subscribe(EntityKilledEvent.class, pendingKills::add);
    }

    @Override
    public void update(float deltaTime) {
        for (EntityKilledEvent event : pendingKills) {
            processKill(event);
        }
        pendingKills.clear();
    }

    private void processKill(EntityKilledEvent event) {
        ArchetypeComponent archetype = event.target.getComponent(ArchetypeComponent.class);
        if (archetype == null) return;

        LootTable table = registry.getTable(archetype.archetypeId);
        if (table == null) return;

        TransformComponent tc = event.target.getComponent(TransformComponent.class);
        if (tc == null) return;

        List<Item> drops = new ArrayList<>();
        QualityTier quality = rollQuality(table.qualityWeights);

        for (LootTable.Entry entry : table.entries) {
            if (MathUtils.random() <= entry.dropChance) {
                Item item = createItem(entry, quality);
                if (item != null) drops.add(item);
            }
        }

        if (drops.isEmpty()) return;

        Entity lootEntity = new Entity();
        LootDropComponent ldc = new LootDropComponent();
        ldc.items.addAll(drops);
        ldc.position.set(tc.position);
        ldc.despawnTimer = 120f;
        lootEntity.add(ldc);

        TransformComponent lootTransform = new TransformComponent();
        lootTransform.position.set(tc.position);
        lootEntity.add(lootTransform);

        if (getEngine() != null) {
            getEngine().addEntity(lootEntity);
        }

        eventBus.publish(new LootDroppedEvent(lootEntity, new Vector3(tc.position), drops));
    }

    private QualityTier rollQuality(float[] weights) {
        QualityTier[] tiers = QualityTier.values();
        float roll = MathUtils.random();
        float cumulative = 0f;
        for (int i = 0; i < weights.length && i < tiers.length; i++) {
            cumulative += weights[i];
            if (roll <= cumulative) return tiers[i];
        }
        return QualityTier.COMMON;
    }

    private Item createItem(LootTable.Entry entry, QualityTier quality) {
        int quantity = MathUtils.random(entry.minQuantity, entry.maxQuantity);
        if ("ammo".equals(entry.itemType)) {
            AmmoItem ammo = new AmmoItem(entry.itemId, entry.itemId, "",
                "ammo_icon", quality, 0.1f, entry.itemId, 999);
            ammo.currentStack = quantity;
            return ammo;
        }
        return null;
    }
}
