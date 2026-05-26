package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import com.galacticodyssey.combat.CombatEnums.WeightClass;
import java.util.HashMap;
import java.util.Map;

/** Resolves a {@link WeaponAssembly} into final computed stats via multiplicative stacking. */
public final class WeaponStatsResolver {
    private WeaponStatsResolver() {}

    // -------------------------------------------------------------------------
    // Output value objects
    // -------------------------------------------------------------------------

    public static class RangedStats {
        public float damage;
        public float fireRate;
        public float spread;
        public float range;
        public float recoil;
        public float weight;
        public int magSize;
        public float reloadTime;
        public FiringMode firingMode;
        public boolean hitscan;
        public DamageType damageType;
        public StatusEffectType statusEffect;
        public float statusEffectChance;
        public Float projectileSpeed;
        public String ammoTypeId;
    }

    public static class MeleeStats {
        public float baseDamage;
        public float speed;
        public float reach;
        public float blockEfficiency;
        public float weight;
        public DamageType damageType;
        public WeightClass weightClass;
        public Map<String, Float> directionalModifiers;
    }

    // -------------------------------------------------------------------------
    // Resolution methods
    // -------------------------------------------------------------------------

    /**
     * Resolves a ranged {@link WeaponAssembly} into {@link RangedStats}.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Base frame values
     *   <li>Barrel multiplicative modifiers (damage, range, spread, recoil)
     *   <li>Ammo-type multiplicative modifier (damage only)
     *   <li>Quality-tier global multiplier (damage, fire-rate, spread, range, recoil)
     *   <li>Weapon-mod flat additive offsets (damage, spread, recoil, magSize)
     * </ol>
     * Damage is clamped to a minimum of 1. Spread, recoil and weight are clamped to 0.
     */
    public static RangedStats resolveRanged(WeaponAssembly assembly, WeaponDataRegistry registry) {
        WeaponFrameData frame = registry.getFrame(assembly.frameId);
        if (frame == null) {
            throw new IllegalArgumentException("Unknown frame: " + assembly.frameId);
        }

        // --- barrel multipliers ---
        float barrelDamageMult = 1f;
        float barrelRangeMult = 1f;
        float barrelSpreadMult = 1f;
        float barrelRecoilMult = 1f;
        float barrelWeightAdd = 0f;
        if (assembly.barrelId != null) {
            BarrelData barrel = registry.getBarrel(assembly.barrelId);
            if (barrel != null) {
                barrelDamageMult = barrel.damageMultiplier;
                barrelRangeMult  = barrel.rangeMultiplier;
                barrelSpreadMult = barrel.spreadMultiplier;
                barrelRecoilMult = barrel.recoilMultiplier;
                barrelWeightAdd  = barrel.weightAdd;
            }
        }

        // --- ammo-type multipliers ---
        float ammoDamageMult = 1f;
        DamageType damageType = DamageType.BALLISTIC;
        StatusEffectType statusEffect = null;
        float statusEffectChance = 0f;
        Float projectileSpeed = null;
        String ammoTypeId = assembly.ammoTypeId;
        if (assembly.ammoTypeId != null) {
            AmmoTypeData ammo = registry.getAmmoType(assembly.ammoTypeId);
            if (ammo != null) {
                ammoDamageMult      = ammo.damageMultiplier;
                damageType          = ammo.damageType;
                statusEffect        = ammo.statusEffect;
                statusEffectChance  = ammo.statusEffectChance;
                projectileSpeed     = ammo.projectileSpeed;
            }
        }

        // --- quality-tier global multiplier ---
        float qualityMult = 1f;
        if (assembly.quality != null) {
            QualityTierData q = registry.getQuality(assembly.quality.name());
            if (q != null) {
                qualityMult = q.globalMultiplier;
            }
        }

        // --- mod flat additive offsets ---
        float modDamageFlat = 0f;
        float modSpreadFlat = 0f;
        float modRecoilFlat = 0f;
        int   modMagFlat    = 0;
        for (String modId : assembly.modIds) {
            WeaponModData mod = registry.getMod(modId);
            if (mod == null) continue;
            modDamageFlat += mod.statModifiers.getOrDefault("damage",  0f);
            modSpreadFlat += mod.statModifiers.getOrDefault("spread",  0f);
            modRecoilFlat += mod.statModifiers.getOrDefault("recoil",  0f);
            modMagFlat    += mod.statModifiers.getOrDefault("magSize", 0f).intValue();
        }

        // --- assemble final stats ---
        RangedStats stats = new RangedStats();
        stats.damage   = Math.max(1f, frame.baseDamage  * barrelDamageMult * ammoDamageMult * qualityMult + modDamageFlat);
        stats.fireRate  = Math.max(0.5f, frame.baseFireRate * qualityMult);
        stats.spread    = Math.max(0f,   frame.baseSpread  * barrelSpreadMult * qualityMult + modSpreadFlat);
        stats.range     = Math.max(5f,   frame.range       * barrelRangeMult  * qualityMult);
        stats.recoil    = Math.max(0f,   frame.baseRecoil  * barrelRecoilMult * qualityMult + modRecoilFlat);
        stats.weight    = Math.max(0.1f, frame.weight + barrelWeightAdd);
        stats.magSize   = Math.max(1,    frame.magSize + modMagFlat);
        stats.reloadTime         = frame.reloadTime;
        stats.firingMode         = frame.firingMode;
        stats.hitscan            = frame.hitscan;
        stats.damageType         = damageType;
        stats.statusEffect       = statusEffect;
        stats.statusEffectChance = statusEffectChance;
        stats.projectileSpeed    = projectileSpeed;
        stats.ammoTypeId         = ammoTypeId;
        return stats;
    }

    /**
     * Resolves a melee {@link WeaponAssembly} into {@link MeleeStats}.
     *
     * <p>Quality-tier global multiplier is applied to base damage only.
     * Damage is clamped to a minimum of 1.
     */
    public static MeleeStats resolveMelee(WeaponAssembly assembly, WeaponDataRegistry registry) {
        MeleeFrameData frame = registry.getMeleeFrame(assembly.frameId);
        if (frame == null) {
            throw new IllegalArgumentException("Unknown melee frame: " + assembly.frameId);
        }

        float qualityMult = 1f;
        if (assembly.quality != null) {
            QualityTierData q = registry.getQuality(assembly.quality.name());
            if (q != null) {
                qualityMult = q.globalMultiplier;
            }
        }

        MeleeStats stats = new MeleeStats();
        stats.baseDamage          = Math.max(1f, frame.baseDamage * qualityMult);
        stats.speed               = frame.baseSpeed;
        stats.reach               = frame.baseReach;
        stats.blockEfficiency     = frame.baseBlockEfficiency;
        stats.weight              = frame.weight;
        stats.damageType          = frame.damageType;
        stats.weightClass         = frame.weightClass;
        stats.directionalModifiers = new HashMap<>(frame.directionalModifiers);
        return stats;
    }
}
