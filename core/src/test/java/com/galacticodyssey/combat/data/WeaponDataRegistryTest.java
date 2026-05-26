package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponDataRegistryTest {
    private WeaponDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WeaponDataRegistry();
        WeaponFrameData frame = new WeaponFrameData();
        frame.id = "test_rifle";
        frame.category = WeaponCategory.RIFLE;
        frame.baseDamage = 25f;
        frame.baseFireRate = 8f;
        frame.magSize = 30;
        frame.firingMode = FiringMode.AUTO;
        frame.hitscan = true;
        registry.registerFrame(frame);

        BarrelData barrel = new BarrelData();
        barrel.id = "test_barrel";
        barrel.damageMultiplier = 1.15f;
        barrel.rangeMultiplier = 1.4f;
        registry.registerBarrel(barrel);

        AmmoTypeData ammo = new AmmoTypeData();
        ammo.id = "test_ammo";
        ammo.damageType = DamageType.BALLISTIC;
        ammo.damageMultiplier = 1.0f;
        registry.registerAmmoType(ammo);

        QualityTierData quality = new QualityTierData();
        quality.tier = QualityTier.MILITARY;
        quality.globalMultiplier = 1.2f;
        quality.durabilityMultiplier = 1.5f;
        registry.registerQuality(quality);
    }

    @Test
    void lookupRegisteredFrame() {
        WeaponFrameData frame = registry.getFrame("test_rifle");
        assertNotNull(frame);
        assertEquals(25f, frame.baseDamage);
        assertEquals(WeaponCategory.RIFLE, frame.category);
    }

    @Test
    void lookupMissingFrameReturnsNull() {
        assertNull(registry.getFrame("nonexistent"));
    }

    @Test
    void lookupBarrelAndAmmo() {
        assertNotNull(registry.getBarrel("test_barrel"));
        assertEquals(1.15f, registry.getBarrel("test_barrel").damageMultiplier);
        assertNotNull(registry.getAmmoType("test_ammo"));
        assertEquals(DamageType.BALLISTIC, registry.getAmmoType("test_ammo").damageType);
    }

    @Test
    void lookupQualityByTierName() {
        QualityTierData q = registry.getQuality("MILITARY");
        assertNotNull(q);
        assertEquals(1.2f, q.globalMultiplier);
    }
}
