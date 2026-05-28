package com.galacticodyssey.equipment;

public final class EquipmentEnums {

    public enum EquipmentSlot {
        PRIMARY_WEAPON, SECONDARY_WEAPON, MELEE_WEAPON,
        HELMET, CHEST, LEGS, BOOTS,
        UTILITY_1, UTILITY_2
    }

    public enum ItemType {
        WEAPON, MELEE_WEAPON, ARMOR, AMMO, MOD, COMPONENT, CONSUMABLE, JUNK, MATERIAL
    }

    private EquipmentEnums() {}
}
