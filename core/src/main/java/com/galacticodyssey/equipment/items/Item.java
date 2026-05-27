package com.galacticodyssey.equipment.items;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.EquipmentEnums.ItemType;
import com.galacticodyssey.persistence.snapshots.ItemSnapshot;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class Item {
    public final String id;
    public final String name;
    public final String description;
    public final String icon;
    public final QualityTier qualityTier;
    public final int gridWidth;
    public final int gridHeight;
    public final float weight;
    public final boolean stackable;
    public final int maxStack;
    public int currentStack;

    protected Item(String id, String name, String description, String icon,
                   QualityTier qualityTier, int gridWidth, int gridHeight,
                   float weight, boolean stackable, int maxStack) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.qualityTier = qualityTier;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.weight = weight;
        this.stackable = stackable;
        this.maxStack = maxStack;
        this.currentStack = 1;
    }

    public abstract ItemType getType();

    public float getTotalWeight() {
        return weight * currentStack;
    }

    public boolean canStackWith(Item other) {
        return stackable && other != null && id.equals(other.id)
            && currentStack + other.currentStack <= maxStack;
    }

    public int getSpaceRemaining() {
        return stackable ? maxStack - currentStack : 0;
    }

    /** Serialize this item's runtime state into an {@link ItemSnapshot}. Grid position
     *  (gridX/gridY) is filled in by the caller, which has access to the grid layout. */
    public ItemSnapshot toItemSnapshot() {
        ItemSnapshot s = new ItemSnapshot();
        s.itemId       = id;
        s.itemType     = getType().name();
        s.quality      = qualityTier.name();
        s.displayName  = name;
        s.weight       = weight;
        s.gridWidth    = gridWidth;
        s.gridHeight   = gridHeight;
        s.stackCount   = currentStack;
        s.maxStack     = maxStack;
        populateCustomData(s.customData);
        return s;
    }

    /** Subclasses override this to write type-specific fields into {@code customData}. */
    protected void populateCustomData(Map<String, Object> customData) {}

    /** Reconstruct the correct concrete {@link Item} subtype from a snapshot.
     *  Grid position fields (gridX/gridY) are ignored here — the caller places the
     *  item back into the grid at the recorded position. */
    @SuppressWarnings("unchecked")
    public static Item fromItemSnapshot(ItemSnapshot s) {
        QualityTier quality = QualityTier.valueOf(s.quality);
        Map<String, Object> cd = s.customData != null ? s.customData : new HashMap<>();

        switch (s.itemType) {
            case "WEAPON": {
                WeaponAssembly assembly = assemblyFromCustomData(cd, false);
                WeaponItem item = new WeaponItem(s.itemId, s.displayName, "", "",
                        quality, s.gridWidth, s.gridHeight, s.weight, assembly);
                item.currentStack = s.stackCount;
                return item;
            }
            case "MELEE_WEAPON": {
                WeaponAssembly assembly = assemblyFromCustomData(cd, true);
                MeleeWeaponItem item = new MeleeWeaponItem(s.itemId, s.displayName, "", "",
                        quality, s.gridWidth, s.gridHeight, s.weight, assembly);
                item.currentStack = s.stackCount;
                return item;
            }
            case "ARMOR": {
                float armorRating   = getFloat(cd, "armorRating", 0f);
                float maxDurability = s.maxDurability > 0 ? s.maxDurability : 100f;
                EquipmentSlot slot  = EquipmentSlot.valueOf((String) cd.getOrDefault("slotType", "CHEST"));
                Map<DamageType, Float> resistances = new HashMap<>();
                Object rawRes = cd.get("resistances");
                if (rawRes instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) rawRes).entrySet()) {
                        try {
                            resistances.put(DamageType.valueOf(e.getKey().toString()),
                                    ((Number) e.getValue()).floatValue());
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
                ArmorItem item = new ArmorItem(s.itemId, s.displayName, "", "",
                        quality, s.gridWidth, s.gridHeight, s.weight,
                        armorRating, resistances, slot, maxDurability);
                item.durability = s.durability > 0 ? s.durability : maxDurability;
                item.currentStack = s.stackCount;
                return item;
            }
            case "AMMO": {
                String ammoTypeId = (String) cd.getOrDefault("ammoTypeId", "");
                AmmoItem item = new AmmoItem(s.itemId, s.displayName, "", "",
                        quality, s.weight, ammoTypeId, s.maxStack);
                item.currentStack = s.stackCount;
                return item;
            }
            case "CONSUMABLE": {
                float healAmount = getFloat(cd, "healAmount", 0f);
                String buffEffect = (String) cd.getOrDefault("buffEffect", "");
                float useTime    = getFloat(cd, "useTime", 1f);
                ConsumableItem item = new ConsumableItem(s.itemId, s.displayName, "", "",
                        quality, s.weight, healAmount, buffEffect, useTime, s.maxStack);
                item.currentStack = s.stackCount;
                return item;
            }
            case "MOD": {
                String weaponModId = (String) cd.getOrDefault("weaponModId", "");
                ModItem item = new ModItem(s.itemId, s.displayName, "", "",
                        quality, s.weight, weaponModId);
                item.currentStack = s.stackCount;
                return item;
            }
            case "COMPONENT": {
                String componentId   = (String) cd.getOrDefault("componentId", "");
                String componentType = (String) cd.getOrDefault("componentType", "");
                ComponentItem item = new ComponentItem(s.itemId, s.displayName, "", "",
                        quality, s.gridWidth, s.gridHeight, s.weight, componentId, componentType);
                item.currentStack = s.stackCount;
                return item;
            }
            case "JUNK": {
                int sellValue = ((Number) cd.getOrDefault("sellValue", 0)).intValue();
                Map<String, Integer> salvageYields = new HashMap<>();
                Object rawSalvage = cd.get("salvageYields");
                if (rawSalvage instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) rawSalvage).entrySet()) {
                        salvageYields.put(e.getKey().toString(),
                                ((Number) e.getValue()).intValue());
                    }
                }
                JunkItem item = new JunkItem(s.itemId, s.displayName, "", "",
                        quality, s.weight, sellValue, salvageYields);
                item.currentStack = s.stackCount;
                return item;
            }
            default:
                throw new IllegalArgumentException("Unknown itemType in snapshot: " + s.itemType);
        }
    }

    private static float getFloat(Map<String, Object> map, String key, float fallback) {
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).floatValue() : fallback;
    }

    private static WeaponAssembly assemblyFromCustomData(Map<String, Object> cd, boolean isMelee) {
        String frameId    = (String) cd.getOrDefault("frameId", "");
        String barrelId   = (String) cd.get("barrelId");
        String ammoTypeId = (String) cd.get("ammoTypeId");
        Object rawMods    = cd.get("modIds");
        String[] modIds   = {};
        if (rawMods instanceof String[]) {
            modIds = (String[]) rawMods;
        } else if (rawMods instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) rawMods;
            modIds = list.stream().map(Object::toString).toArray(String[]::new);
        }
        QualityTier quality = QualityTier.valueOf(
                (String) cd.getOrDefault("assemblyQuality", "STANDARD"));
        return new WeaponAssembly(frameId, barrelId, ammoTypeId, modIds, quality, isMelee);
    }
}
