package com.galacticodyssey.combat;

public final class CombatEnums {
    private CombatEnums() {}

    public enum DamageType {
        BALLISTIC, ENERGY, PLASMA, EXPLOSIVE, INCENDIARY, EMP, MELEE, CRYO
    }

    public enum HitRegion {
        HEAD, TORSO, ARMS, LEGS
    }

    public enum AttackDirection {
        LEFT, RIGHT, OVERHEAD, THRUST
    }

    public enum FiringMode {
        SEMI, AUTO, BURST
    }

    public enum WeaponCategory {
        PISTOL, RIFLE, SHOTGUN, SMG, SNIPER, HEAVY
    }

    public enum MeleeCategory {
        BLADE, STAFF, HAMMER, FIST
    }

    public enum WeightClass {
        LIGHT(0.15f, 0.1f, 0.2f, 0.3f),
        MEDIUM(0.25f, 0.15f, 0.35f, 0.4f),
        HEAVY(0.4f, 0.2f, 0.5f, 0.5f);

        public final float windUpTime;
        public final float activeTime;
        public final float recoveryTime;
        public final float staggerTime;

        WeightClass(float windUp, float active, float recovery, float stagger) {
            this.windUpTime = windUp;
            this.activeTime = active;
            this.recoveryTime = recovery;
            this.staggerTime = stagger;
        }
    }

    public enum ShieldType {
        ENERGY, KINETIC, COMPOSITE
    }

    public enum QualityTier {
        SALVAGED, COMMON, REFINED, MILITARY, EXPERIMENTAL, ALIEN, PRECURSOR
    }

    public enum StatusEffectType {
        BLEEDING, BURNING, EMP_DISABLED, STUNNED, SLOWED
    }

    public enum MeleeState {
        IDLE, WIND_UP, ACTIVE, RECOVERY, BLOCKING, STAGGERED
    }

    public enum WeaponSlot {
        PRIMARY(0, 0.6f),
        SECONDARY(1, 0.4f),
        SIDEARM(2, 0.35f),
        MELEE(3, 0.2f);

        public final int index;
        public final float switchTime;

        WeaponSlot(int index, float switchTime) {
            this.index = index;
            this.switchTime = switchTime;
        }
    }

    public enum SquadRole {
        LEADER, FLANKER, SUPPRESSOR, MEDIC
    }

    public enum CoverQuality {
        HALF, FULL
    }

    public enum FuseType {
        TIMED,
        IMPACT,
        PROXIMITY
    }

    public enum ThrowState {
        IDLE,
        COOKING,
        THROWN
    }
}
