package com.galacticodyssey.ui.actors;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import com.galacticodyssey.equipment.items.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentSlotsActorTest {

    @Test
    void getMatchingSlotForArmorItemReturnsCorrectSlot() {
        var helmet = new ArmorItem("h1", "Helmet", "", "", QualityTier.COMMON,
            1, 1, 1f, 10f, new java.util.EnumMap<>(com.galacticodyssey.combat.CombatEnums.DamageType.class),
            EquipmentSlot.HELMET, 50f);
        assertEquals(EquipmentSlot.HELMET, EquipmentSlotsActor.getMatchingSlot(helmet));

        var chest = new ArmorItem("c1", "Vest", "", "", QualityTier.COMMON,
            2, 2, 3f, 20f, new java.util.EnumMap<>(com.galacticodyssey.combat.CombatEnums.DamageType.class),
            EquipmentSlot.CHEST, 80f);
        assertEquals(EquipmentSlot.CHEST, EquipmentSlotsActor.getMatchingSlot(chest));
    }

    @Test
    void getMatchingSlotForWeaponReturnsPrimaryWeapon() {
        var weapon = new WeaponItem("w1", "Rifle", "", "", QualityTier.COMMON,
            3, 1, 4f,
            new com.galacticodyssey.combat.data.WeaponAssembly("frame1", null, null, new String[0], QualityTier.COMMON, false));
        assertEquals(EquipmentSlot.PRIMARY_WEAPON, EquipmentSlotsActor.getMatchingSlot(weapon));
    }

    @Test
    void getMatchingSlotForConsumableReturnsUtility1() {
        var item = new ConsumableItem("m1", "Medkit", "", "", QualityTier.COMMON,
            0.5f, 50f, "", 1f, 3);
        assertEquals(EquipmentSlot.UTILITY_1, EquipmentSlotsActor.getMatchingSlot(item));
    }

    @Test
    void getMatchingSlotForNonEquippableReturnsNull() {
        var junk = new JunkItem("j1", "Scrap", "", "", QualityTier.SALVAGED,
            1f, 5, new java.util.HashMap<>());
        assertNull(EquipmentSlotsActor.getMatchingSlot(junk));
    }

    @Test
    void slotLabelReturnsHumanReadableName() {
        assertEquals("Helmet", EquipmentSlotsActor.slotLabel(EquipmentSlot.HELMET));
        assertEquals("Primary", EquipmentSlotsActor.slotLabel(EquipmentSlot.PRIMARY_WEAPON));
        assertEquals("Utility 1", EquipmentSlotsActor.slotLabel(EquipmentSlot.UTILITY_1));
    }
}
