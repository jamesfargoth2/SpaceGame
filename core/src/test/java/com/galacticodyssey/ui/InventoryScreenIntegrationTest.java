package com.galacticodyssey.ui;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.components.EquipmentSlotsComponent;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.events.EquipmentChangedEvent;
import com.galacticodyssey.equipment.items.ArmorItem;
import com.galacticodyssey.equipment.items.ConsumableItem;
import com.galacticodyssey.equipment.systems.EquipmentSystem;
import com.galacticodyssey.ui.events.InventoryClosedEvent;
import com.galacticodyssey.ui.events.InventoryOpenedEvent;
import com.galacticodyssey.ui.systems.InventoryScreenSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InventoryScreenIntegrationTest {

    private EventBus eventBus;
    private Engine engine;
    private EquipmentSystem equipmentSystem;
    private InventoryScreenSystem inventorySystem;
    private Entity player;

    @BeforeEach
    void setup() {
        eventBus = new EventBus();
        engine = new Engine();
        equipmentSystem = new EquipmentSystem(eventBus);
        engine.addSystem(equipmentSystem);

        player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new InventoryComponent(10, 6, 100f));
        player.add(new EquipmentSlotsComponent());
        engine.addEntity(player);

        inventorySystem = new InventoryScreenSystem(eventBus, null);
    }

    @Test
    void toggleCyclePublishesCorrectEvents() {
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        eventBus.subscribe(InventoryOpenedEvent.class, e -> opened.incrementAndGet());
        eventBus.subscribe(InventoryClosedEvent.class, e -> closed.incrementAndGet());

        inventorySystem.toggle();
        assertTrue(inventorySystem.isOpen());
        assertEquals(1, opened.get());

        inventorySystem.toggle();
        assertFalse(inventorySystem.isOpen());
        assertEquals(1, closed.get());
    }

    @Test
    void equipViaSystemMovesItemFromInventoryToSlot() {
        InventoryComponent inv = player.getComponent(InventoryComponent.class);
        EquipmentSlotsComponent equip = player.getComponent(EquipmentSlotsComponent.class);

        ArmorItem helmet = new ArmorItem("helm1", "Iron Helmet", "", "", QualityTier.COMMON,
            1, 1, 2f, 15f, new EnumMap<>(com.galacticodyssey.combat.CombatEnums.DamageType.class),
            EquipmentSlot.HELMET, 50f);
        assertTrue(inv.tryAdd(helmet));
        assertEquals(1, inv.getItemCount());

        inv.remove(helmet);
        assertTrue(equipmentSystem.equip(player, EquipmentSlot.HELMET, helmet));

        assertEquals(0, inv.getItemCount());
        assertSame(helmet, equip.getSlot(EquipmentSlot.HELMET));
    }

    @Test
    void unequipReturnsItemToInventory() {
        ArmorItem chest = new ArmorItem("chest1", "Vest", "", "", QualityTier.REFINED,
            2, 2, 5f, 25f, new EnumMap<>(com.galacticodyssey.combat.CombatEnums.DamageType.class),
            EquipmentSlot.CHEST, 80f);
        equipmentSystem.equip(player, EquipmentSlot.CHEST, chest);

        EquipmentSlotsComponent equip = player.getComponent(EquipmentSlotsComponent.class);
        assertNotNull(equip.getSlot(EquipmentSlot.CHEST));

        equipmentSystem.unequip(player, EquipmentSlot.CHEST);
        assertNull(equip.getSlot(EquipmentSlot.CHEST));

        InventoryComponent inv = player.getComponent(InventoryComponent.class);
        assertEquals(1, inv.getItemCount());
    }

    @Test
    void equipmentChangedEventFiresOnEquip() {
        AtomicInteger count = new AtomicInteger();
        eventBus.subscribe(EquipmentChangedEvent.class, e -> count.incrementAndGet());

        ConsumableItem medkit = new ConsumableItem("med1", "Medkit", "", "",
            QualityTier.COMMON, 0.5f, 50f, "", 1f, 3);
        equipmentSystem.equip(player, EquipmentSlot.UTILITY_1, medkit);
        assertEquals(1, count.get());
    }
}
