package com.galacticodyssey.equipment;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.ArmorComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.combat.data.WeaponStatsResolver;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.components.EquipmentSlotsComponent;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.events.EquipmentChangedEvent;
import com.galacticodyssey.equipment.items.ArmorItem;
import com.galacticodyssey.equipment.items.Item;
import com.galacticodyssey.equipment.items.WeaponItem;
import com.galacticodyssey.equipment.systems.EquipmentSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private EquipmentSystem system;
    private Entity player;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        system = new EquipmentSystem(eventBus);
        engine.addSystem(system);

        player = new Entity();
        player.add(new EquipmentSlotsComponent());
        player.add(new InventoryComponent(8, 6, 50f));
        player.add(new WeaponInventoryComponent());
        player.add(new RangedWeaponComponent());
        player.add(new ArmorComponent());
        engine.addEntity(player);
    }

    @Test
    void equipWeapon_publishesEquipmentChangedEvent() {
        AtomicReference<EquipmentChangedEvent> received = new AtomicReference<>();
        eventBus.subscribe(EquipmentChangedEvent.class, received::set);

        WeaponAssembly assembly = WeaponAssembly.ranged("pistol_standard", "standard_barrel",
            "standard_round", new String[]{}, QualityTier.COMMON);
        WeaponItem pistol = new WeaponItem("pistol_1", "Pistol", "A pistol",
            "pistol_icon", QualityTier.COMMON, 2, 1, 1.2f, assembly);

        system.equip(player, EquipmentSlot.PRIMARY_WEAPON, pistol);

        assertNotNull(received.get());
        assertEquals(EquipmentSlot.PRIMARY_WEAPON, received.get().slot);
        assertNull(received.get().oldItem);
        assertSame(pistol, received.get().newItem);
    }

    @Test
    void equipWeapon_syncsWeaponInventoryComponent() {
        WeaponAssembly assembly = WeaponAssembly.ranged("pistol_standard", "standard_barrel",
            "standard_round", new String[]{}, QualityTier.COMMON);
        WeaponItem pistol = new WeaponItem("pistol_1", "Pistol", "A pistol",
            "pistol_icon", QualityTier.COMMON, 2, 1, 1.2f, assembly);

        system.equip(player, EquipmentSlot.PRIMARY_WEAPON, pistol);

        WeaponInventoryComponent wic = player.getComponent(WeaponInventoryComponent.class);
        assertNotNull(wic.slots[WeaponSlot.PRIMARY.ordinal()]);
        assertEquals("pistol_standard", wic.slots[WeaponSlot.PRIMARY.ordinal()].frameId);
    }

    @Test
    void unequipWeapon_returnsToInventory() {
        WeaponAssembly assembly = WeaponAssembly.ranged("pistol_standard", "standard_barrel",
            "standard_round", new String[]{}, QualityTier.COMMON);
        WeaponItem pistol = new WeaponItem("pistol_1", "Pistol", "A pistol",
            "pistol_icon", QualityTier.COMMON, 2, 1, 1.2f, assembly);

        system.equip(player, EquipmentSlot.PRIMARY_WEAPON, pistol);
        Item unequipped = system.unequip(player, EquipmentSlot.PRIMARY_WEAPON);

        assertSame(pistol, unequipped);
        InventoryComponent inv = player.getComponent(InventoryComponent.class);
        assertTrue(inv.getAllItems().contains(pistol));
    }

    @Test
    void equipArmor_updatesArmorComponent() {
        Map<DamageType, Float> resistances = new EnumMap<>(DamageType.class);
        resistances.put(DamageType.BALLISTIC, 0.3f);
        ArmorItem chest = new ArmorItem("armor_chest_1", "Body Armor", "Chest plate",
            "chest_icon", QualityTier.MILITARY, 2, 2, 4.0f, 25.0f,
            resistances, EquipmentSlot.CHEST, 100f);

        system.equip(player, EquipmentSlot.CHEST, chest);

        ArmorComponent ac = player.getComponent(ArmorComponent.class);
        assertEquals(25.0f, ac.armorRating.get(HitRegion.TORSO), 0.01f);
    }

    @Test
    void equipWrongSlot_rejected() {
        Map<DamageType, Float> resistances = new EnumMap<>(DamageType.class);
        ArmorItem helmet = new ArmorItem("armor_helm_1", "Helmet", "Head protection",
            "helm_icon", QualityTier.COMMON, 2, 2, 2.0f, 15.0f,
            resistances, EquipmentSlot.HELMET, 80f);

        assertFalse(system.equip(player, EquipmentSlot.CHEST, helmet));
    }
}
