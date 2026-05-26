package com.galacticodyssey.equipment;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.equipment.components.ArchetypeComponent;
import com.galacticodyssey.equipment.data.LootTable;
import com.galacticodyssey.equipment.data.LootTableRegistry;
import com.galacticodyssey.equipment.events.LootDroppedEvent;
import com.galacticodyssey.equipment.systems.LootGenerationSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LootGenerationSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private LootTableRegistry registry;
    private LootGenerationSystem system;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        registry = new LootTableRegistry();

        LootTable.Entry entry = new LootTable.Entry("ammo_std", "ammo", 1.0f, 10, 30);
        LootTable table = new LootTable("grunt", List.of(entry),
            new float[]{0.6f, 0.25f, 0.1f, 0.04f, 0.01f, 0f, 0f});
        registry.register(table);

        system = new LootGenerationSystem(eventBus, registry);
        engine.addSystem(system);
    }

    @Test
    void entityKilled_withLootTable_dropsLoot() {
        AtomicReference<LootDroppedEvent> received = new AtomicReference<>();
        eventBus.subscribe(LootDroppedEvent.class, received::set);

        Entity target = new Entity();
        TransformComponent tc = new TransformComponent();
        tc.position.set(5f, 0f, 10f);
        target.add(tc);
        target.add(new ArchetypeComponent("grunt"));

        Entity killer = new Entity();
        engine.addEntity(target);
        engine.addEntity(killer);

        eventBus.publish(new EntityKilledEvent(target, killer));
        engine.update(0.016f);

        assertNotNull(received.get());
        assertFalse(received.get().items.isEmpty());
        assertEquals(5f, received.get().position.x, 0.01f);
    }

    @Test
    void entityKilled_noLootTable_noEvent() {
        AtomicReference<LootDroppedEvent> received = new AtomicReference<>();
        eventBus.subscribe(LootDroppedEvent.class, received::set);

        Entity target = new Entity();
        target.add(new TransformComponent());
        Entity killer = new Entity();
        engine.addEntity(target);

        eventBus.publish(new EntityKilledEvent(target, killer));
        engine.update(0.016f);

        assertNull(received.get());
    }
}
