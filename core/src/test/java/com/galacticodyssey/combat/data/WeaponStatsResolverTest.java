package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponStatsResolverTest {
    private WeaponDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WeaponDataRegistry();

        WeaponFrameData frame = new WeaponFrameData();
        frame.id = "rifle";
        frame.category = WeaponCategory.RIFLE;
        frame.baseDamage = 25f;
        frame.baseFireRate = 8f;
        frame.baseSpread = 2f;
        frame.baseRecoil = 3.5f;
        frame.magSize = 30;
        frame.modSlotCount = 2;
        frame.weight = 3.5f;
        frame.firingMode = FiringMode.AUTO;
        frame.hitscan = true;
        frame.range = 100f;
        frame.reloadTime = 2f;
        registry.registerFrame(frame);

        BarrelData barrel = new BarrelData();
        barrel.id = "long";
        barrel.damageMultiplier = 1.2f;
        barrel.rangeMultiplier = 1.5f;
        barrel.spreadMultiplier = 0.7f;
        barrel.recoilMultiplier = 1.1f;
        barrel.weightAdd = 0.8f;
        registry.registerBarrel(barrel);

        AmmoTypeData ammo = new AmmoTypeData();
        ammo.id = "standard";
        ammo.damageType = DamageType.BALLISTIC;
        ammo.damageMultiplier = 1.0f;
        registry.registerAmmoType(ammo);

        WeaponModData mod = new WeaponModData();
        mod.id = "stabilizer";
        mod.slot = "stabilizer";
        mod.statModifiers.put("spread", -0.5f);
        mod.statModifiers.put("recoil", -0.8f);
        registry.registerMod(mod);

        QualityTierData quality = new QualityTierData();
        quality.tier = QualityTier.MILITARY;
        quality.globalMultiplier = 1.2f;
        quality.durabilityMultiplier = 1.5f;
        registry.registerQuality(quality);

        MeleeFrameData melee = new MeleeFrameData();
        melee.id = "blade";
        melee.category = MeleeCategory.BLADE;
        melee.baseDamage = 40f;
        melee.baseSpeed = 1.2f;
        melee.baseReach = 1.5f;
        melee.baseBlockEfficiency = 0.7f;
        melee.weight = 1f;
        melee.damageType = DamageType.MELEE;
        melee.weightClass = WeightClass.LIGHT;
        melee.directionalModifiers.put("overhead", 1.3f);
        melee.directionalModifiers.put("thrust", 1.1f);
        melee.directionalModifiers.put("left", 1.0f);
        melee.directionalModifiers.put("right", 1.0f);
        registry.registerMeleeFrame(melee);
    }

    @Test
    void resolveRangedWeaponAppliesMultipliers() {
        WeaponAssembly assembly = WeaponAssembly.ranged("rifle", "long", "standard",
            new String[]{"stabilizer"}, QualityTier.MILITARY);
        WeaponStatsResolver.RangedStats stats = WeaponStatsResolver.resolveRanged(assembly, registry);

        // damage = 25 * 1.2(barrel) * 1.0(ammo) * 1.2(quality) = 36.0
        assertEquals(36.0f, stats.damage, 0.01f);
        // range = 100 * 1.5(barrel) * 1.2(quality) = 180.0
        assertEquals(180.0f, stats.range, 0.01f);
        // spread = 2 * 0.7(barrel) * 1.2(quality) + (-0.5)(mod) = 1.68 - 0.5 = 1.18
        assertEquals(1.18f, stats.spread, 0.01f);
        assertEquals(FiringMode.AUTO, stats.firingMode);
        assertTrue(stats.hitscan);
        assertEquals(30, stats.magSize);
    }

    @Test
    void resolveRangedWeaponWithNoModsOrBarrel() {
        WeaponAssembly assembly = WeaponAssembly.ranged("rifle", null, "standard",
            null, QualityTier.COMMON);
        WeaponStatsResolver.RangedStats stats = WeaponStatsResolver.resolveRanged(assembly, registry);

        // No barrel, no mods, common quality (1.0x). Damage = 25 * 1.0 * 1.0 = 25
        assertEquals(25.0f, stats.damage, 0.01f);
    }

    @Test
    void resolveMeleeWeaponAppliesQuality() {
        WeaponAssembly assembly = WeaponAssembly.melee("blade", QualityTier.MILITARY);
        WeaponStatsResolver.MeleeStats stats = WeaponStatsResolver.resolveMelee(assembly, registry);

        // damage = 40 * 1.2(quality) = 48
        assertEquals(48.0f, stats.baseDamage, 0.01f);
        assertEquals(1.5f, stats.reach, 0.01f);
        assertEquals(WeightClass.LIGHT, stats.weightClass);
        assertEquals(1.3f, stats.directionalModifiers.get("overhead"), 0.01f);
    }

    @Test
    void damageClampedToMinimumOne() {
        QualityTierData terrible = new QualityTierData();
        terrible.tier = QualityTier.SALVAGED;
        terrible.globalMultiplier = 0.01f;
        terrible.durabilityMultiplier = 0.1f;
        registry.registerQuality(terrible);

        WeaponAssembly assembly = WeaponAssembly.ranged("rifle", null, "standard",
            null, QualityTier.SALVAGED);
        WeaponStatsResolver.RangedStats stats = WeaponStatsResolver.resolveRanged(assembly, registry);
        assertTrue(stats.damage >= 1.0f);
    }
}
