package com.galacticodyssey.equipment;

import com.galacticodyssey.combat.CombatEnums.QualityTier;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.equipment.items.ComponentItem;
import com.galacticodyssey.equipment.items.ModItem;
import com.galacticodyssey.equipment.items.WeaponItem;
import com.galacticodyssey.equipment.systems.WeaponAssemblySystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeaponAssemblySystemTest {

    private WeaponAssemblySystem system;

    @BeforeEach
    void setUp() {
        system = new WeaponAssemblySystem();
    }

    @Test
    void assembleWeapon_validParts_createsWeaponItem() {
        ComponentItem frame = new ComponentItem("frame_pistol", "Pistol Frame", "A frame",
            "icon", QualityTier.COMMON, 2, 1, 0.5f, "pistol_standard", "frame");
        ComponentItem barrel = new ComponentItem("barrel_std", "Standard Barrel", "A barrel",
            "icon", QualityTier.COMMON, 1, 1, 0.3f, "standard_barrel", "barrel");
        ComponentItem ammo = new ComponentItem("ammo_std", "Standard Ammo Type", "Ammo",
            "icon", QualityTier.COMMON, 1, 1, 0.1f, "standard_round", "ammo_type");

        WeaponItem result = system.assemble(frame, barrel, ammo, List.of(), QualityTier.COMMON);

        assertNotNull(result);
        assertEquals("pistol_standard", result.assembly.frameId);
        assertEquals("standard_barrel", result.assembly.barrelId);
        assertEquals("standard_round", result.assembly.ammoTypeId);
        assertFalse(result.assembly.isMelee);
    }

    @Test
    void assembleWeapon_withMods_includesModIds() {
        ComponentItem frame = new ComponentItem("frame_rifle", "Rifle Frame", "A frame",
            "icon", QualityTier.COMMON, 3, 1, 1.0f, "rifle_standard", "frame");
        ComponentItem barrel = new ComponentItem("barrel_long", "Long Barrel", "A barrel",
            "icon", QualityTier.COMMON, 2, 1, 0.5f, "long_barrel", "barrel");
        ComponentItem ammo = new ComponentItem("ammo_std", "Standard Ammo Type", "Ammo",
            "icon", QualityTier.COMMON, 1, 1, 0.1f, "standard_round", "ammo_type");
        ModItem mod = new ModItem("mod_scope", "Basic Scope", "A scope",
            "icon", QualityTier.COMMON, 0.2f, "basic_scope");

        WeaponItem result = system.assemble(frame, barrel, ammo, List.of(mod), QualityTier.MILITARY);

        assertNotNull(result);
        assertEquals(QualityTier.MILITARY, result.qualityTier);
        assertEquals(1, result.assembly.modIds.length);
        assertEquals("basic_scope", result.assembly.modIds[0]);
    }

    @Test
    void assembleWeapon_missingFrame_returnsNull() {
        ComponentItem barrel = new ComponentItem("barrel_std", "Standard Barrel", "A barrel",
            "icon", QualityTier.COMMON, 1, 1, 0.3f, "standard_barrel", "barrel");
        ComponentItem ammo = new ComponentItem("ammo_std", "Standard Ammo Type", "Ammo",
            "icon", QualityTier.COMMON, 1, 1, 0.1f, "standard_round", "ammo_type");

        WeaponItem result = system.assemble(null, barrel, ammo, List.of(), QualityTier.COMMON);
        assertNull(result);
    }
}
