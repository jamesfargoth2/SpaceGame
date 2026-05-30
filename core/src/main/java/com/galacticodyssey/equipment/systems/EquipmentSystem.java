package com.galacticodyssey.equipment.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.ArmorComponent;
import com.galacticodyssey.combat.components.WeaponInventoryComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.components.EquipmentSlotsComponent;
import com.galacticodyssey.equipment.components.InventoryComponent;
import com.galacticodyssey.equipment.events.EquipmentChangedEvent;
import com.galacticodyssey.equipment.items.*;

import java.util.Map;

public class EquipmentSystem extends EntitySystem {
    private static final int PRIORITY = 2;
    private final EventBus eventBus;

    private static final Map<EquipmentSlot, Integer> WEAPON_SLOT_MAP = Map.of(
        EquipmentSlot.PRIMARY_WEAPON, 0,
        EquipmentSlot.SECONDARY_WEAPON, 1,
        EquipmentSlot.MELEE_WEAPON, 3
    );

    private static final Map<EquipmentSlot, HitRegion> ARMOR_SLOT_MAP = Map.of(
        EquipmentSlot.HELMET, HitRegion.HEAD,
        EquipmentSlot.CHEST, HitRegion.TORSO,
        EquipmentSlot.LEGS, HitRegion.LEGS,
        EquipmentSlot.BOOTS, HitRegion.LEGS
    );

    public EquipmentSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    public boolean equip(Entity entity, EquipmentSlot slot, Item item) {
        if (!isValidForSlot(slot, item)) {
            return false;
        }
        EquipmentSlotsComponent equip = entity.getComponent(EquipmentSlotsComponent.class);
        if (equip == null) return false;

        Item old = equip.setSlot(slot, item);
        if (old != null) {
            InventoryComponent inv = entity.getComponent(InventoryComponent.class);
            if (inv != null) inv.tryAdd(old);
        }

        syncCombatComponents(entity, slot, item);
        eventBus.publish(new EquipmentChangedEvent(entity, slot, old, item));
        return true;
    }

    public Item unequip(Entity entity, EquipmentSlot slot) {
        EquipmentSlotsComponent equip = entity.getComponent(EquipmentSlotsComponent.class);
        if (equip == null) return null;

        Item old = equip.clearSlot(slot);
        if (old != null) {
            InventoryComponent inv = entity.getComponent(InventoryComponent.class);
            if (inv != null) inv.tryAdd(old);
            syncCombatComponents(entity, slot, null);
            eventBus.publish(new EquipmentChangedEvent(entity, slot, old, null));
        }
        return old;
    }

    private boolean isValidForSlot(EquipmentSlot slot, Item item) {
        switch (slot) {
            case PRIMARY_WEAPON:
            case SECONDARY_WEAPON:
                return item instanceof WeaponItem;
            case MELEE_WEAPON:
                return item instanceof MeleeWeaponItem;
            case HELMET:
            case CHEST:
            case LEGS:
            case BOOTS:
                if (!(item instanceof ArmorItem armor)) return false;
                return armor.slotType == slot;
            case UTILITY_1:
            case UTILITY_2:
                return item instanceof ConsumableItem;
            default:
                return false;
        }
    }

    private void syncCombatComponents(Entity entity, EquipmentSlot slot, Item item) {
        if (WEAPON_SLOT_MAP.containsKey(slot)) {
            syncWeaponSlot(entity, WEAPON_SLOT_MAP.get(slot), item);
        } else if (ARMOR_SLOT_MAP.containsKey(slot)) {
            syncArmorSlot(entity, slot);
        }
    }

    private void syncWeaponSlot(Entity entity, int slotIndex, Item item) {
        WeaponInventoryComponent wic = entity.getComponent(WeaponInventoryComponent.class);
        if (wic == null) return;

        if (item instanceof WeaponItem wi) {
            wic.slots[slotIndex] = wi.assembly;
        } else if (item instanceof MeleeWeaponItem mi) {
            wic.slots[slotIndex] = mi.assembly;
        } else {
            wic.slots[slotIndex] = null;
        }
    }

    private void syncArmorSlot(Entity entity, EquipmentSlot changedSlot) {
        ArmorComponent ac = entity.getComponent(ArmorComponent.class);
        EquipmentSlotsComponent equip = entity.getComponent(EquipmentSlotsComponent.class);
        if (ac == null || equip == null) return;

        for (HitRegion region : HitRegion.values()) {
            ac.armorRating.put(region, 0f);
            ac.resistances.put(region, new java.util.EnumMap<>(DamageType.class));
        }

        for (Map.Entry<EquipmentSlot, HitRegion> entry : ARMOR_SLOT_MAP.entrySet()) {
            Item equipped = equip.getSlot(entry.getKey());
            if (equipped instanceof ArmorItem armor) {
                HitRegion region = entry.getValue();
                float current = ac.armorRating.getOrDefault(region, 0f);
                ac.armorRating.put(region, current + armor.armorRating);
                for (Map.Entry<DamageType, Float> res : armor.resistances.entrySet()) {
                    Map<DamageType, Float> regionRes = ac.resistances.get(region);
                    float existing = regionRes.getOrDefault(res.getKey(), 0f);
                    regionRes.put(res.getKey(), Math.min(existing + res.getValue(), 0.85f));
                }
            }
        }
    }

    @Override
    public void update(float deltaTime) {
        // Equipment changes are command-driven, not per-frame
    }
}
