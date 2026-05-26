# FPS Combat System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a first-person combat system with directional melee, modular ranged weapons, a unified damage pipeline, and behavior-tree-driven combat AI with squad tactics.

**Architecture:** Layered ECS pipeline — focused Ashley systems communicate via EventBus. Weapon stats are data-driven from JSON. Melee uses a lightweight FSM stored in a component. AI uses gdx-ai behavior trees. All simulation logic is GL-free and unit-testable.

**Tech Stack:** Java 21, libGDX, Ashley ECS, gdx-bullet (Bullet physics), gdx-ai (behavior trees), JUnit 5, Mockito

**Spec:** `docs/superpowers/specs/2026-05-25-fps-combat-system-design.md`

---

## File Structure

### New Files — Foundation (`combat/`)

```
core/src/main/java/com/galacticodyssey/combat/
  CombatEnums.java                    — DamageType, HitRegion, AttackDirection, FiringMode, WeaponCategory, MeleeCategory, WeightClass, ShieldType, QualityTier, StatusEffectType, MeleeState, WeaponSlot, SquadRole, CoverQuality enums
  events/
    WeaponFiredEvent.java
    HitscanHitEvent.java
    ProjectileHitEvent.java
    MeleeHitEvent.java
    MeleeBlockEvent.java
    DamageDealtEvent.java
    ShieldAbsorbEvent.java
    EntityKilledEvent.java
    StatusEffectAppliedEvent.java
    StatusEffectExpiredEvent.java
    WeaponSwitchedEvent.java
    ReloadStartedEvent.java
    RecoilEvent.java
    ThreatDetectedEvent.java
    FlankOrderEvent.java
    SuppressOrderEvent.java
    RetreatOrderEvent.java
    AdvanceOrderEvent.java
  data/
    WeaponFrameData.java              — POJO for ranged weapon frame JSON
    MeleeFrameData.java               — POJO for melee weapon frame JSON
    BarrelData.java                    — POJO for barrel JSON
    AmmoTypeData.java                  — POJO for ammo type JSON
    WeaponModData.java                 — POJO for weapon mod JSON
    QualityTierData.java               — POJO for quality tier JSON
    StatusEffectData.java              — POJO for status effect definition JSON
    DamageConfigData.java              — POJO for damage config JSON
    AIArchetypeData.java               — POJO for AI archetype JSON
    WeaponAssembly.java                — Assembled weapon reference (frameId + barrelId + ammoTypeId + modIds + quality)
    WeaponDataRegistry.java            — Loads/indexes all weapon part JSONs
    CombatDataRegistry.java            — Loads/indexes combat config JSONs
    WeaponStatsResolver.java           — Pure function: assembly + registry → resolved stats
  components/
    HealthComponent.java
    ShieldComponent.java
    ArmorComponent.java
    StatusEffectsComponent.java
    ActiveStatusEffect.java            — Data class for a single active effect instance
    WeaponInventoryComponent.java
    RangedWeaponComponent.java
    MeleeWeaponComponent.java
    MeleeStateComponent.java
    CombatInputComponent.java
    HitboxComponent.java
    HostileTagComponent.java
    CombatAIComponent.java
    SquadComponent.java
    CoverComponent.java
    CoverPoint.java                    — Plain data object (position, normal, quality, occupied)
    ProjectileComponent.java
  systems/
    CombatInputSystem.java
    WeaponSwitchSystem.java
    WeaponSystem.java
    MeleeSystem.java
    HitscanSystem.java
    ProjectileSystem.java
    DamageSystem.java
    StatusEffectSystem.java
    CombatAISystem.java
    SquadTacticsSystem.java
  ai/
    CombatBlackboard.java              — Shared data object for behavior tree tasks
    tasks/
      FindRetreatPointTask.java
      MoveToTask.java
      HealOrFleeTask.java
      HasTargetCondition.java
      InMeleeRangeCondition.java
      InWeaponRangeCondition.java
      HasLineOfSightCondition.java
      SelectMeleeDirectionTask.java
      MeleeAttackTask.java
      AimAndFireTask.java
      PeekAndShootTask.java
      FindCoverTask.java
      MoveToCoverTask.java
      InCoverCondition.java
      MoveToLastKnownTask.java
      SearchAreaTask.java
      PatrolTask.java
      ScanForThreatsTask.java
      ReportToSquadTask.java
      CriticalHealthCondition.java
      NoLineOfSightCondition.java
```

### New Files — Data

```
core/src/main/resources/data/
  weapons/
    frames.json
    barrels.json
    ammo_types.json
    mods.json
    melee_frames.json
    qualities.json
  combat/
    status_effects.json
    damage_config.json
    ai_archetypes.json
```

### New Test Files

```
core/src/test/java/com/galacticodyssey/combat/
  data/
    WeaponStatsResolverTest.java
    WeaponDataRegistryTest.java
    CombatDataRegistryTest.java
  systems/
    DamageSystemTest.java
    StatusEffectSystemTest.java
    WeaponSystemTest.java
    WeaponSwitchSystemTest.java
    MeleeSystemTest.java
    HitscanSystemTest.java
    ProjectileSystemTest.java
    CombatInputSystemTest.java
    CombatAISystemTest.java
    SquadTacticsSystemTest.java
  CombatIntegrationTest.java
```

### Modified Files

```
core/src/main/java/com/galacticodyssey/core/GameWorld.java
  — Add combat systems to engine, add combat entity creation methods
core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java
  — Subscribe to RecoilEvent, apply recoil impulse to pitch/yaw
core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java
  — Add combat input keybinds (fire, reload, melee attack/block, weapon switch, quick-melee)
core/src/main/java/com/galacticodyssey/player/components/PlayerInputComponent.java
  — Not modified; combat input goes through CombatInputComponent instead
```

---

### Task 1: Combat Enums and Shared Types

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/CombatEnums.java`

- [ ] **Step 1: Create the CombatEnums file with all enum types**

```java
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
        MELEE(2, 0.2f);

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
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/CombatEnums.java
git commit -m "feat(combat): add combat enum types"
```

---

### Task 2: Combat Events

**Files:**
- Create: 18 event classes in `core/src/main/java/com/galacticodyssey/combat/events/`

All events are immutable data carriers with `public final` fields and a constructor. They carry no logic. The EventBus uses class type for dispatch.

- [ ] **Step 1: Create all combat event classes**

Each event follows this pattern — a `public final` field carrier:

`WeaponFiredEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public final class WeaponFiredEvent {
    public final Entity shooter;
    public final Vector3 aimDirection;
    public final boolean hitscan;

    public WeaponFiredEvent(Entity shooter, Vector3 aimDirection, boolean hitscan) {
        this.shooter = shooter;
        this.aimDirection = new Vector3(aimDirection);
        this.hitscan = hitscan;
    }
}
```

`HitscanHitEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;

public final class HitscanHitEvent {
    public final Entity shooter;
    public final Entity target;
    public final Vector3 hitPoint;
    public final Vector3 hitNormal;
    public final HitRegion hitRegion;
    public final float damage;
    public final DamageType damageType;
    public final String ammoTypeId;

    public HitscanHitEvent(Entity shooter, Entity target, Vector3 hitPoint, Vector3 hitNormal,
                           HitRegion hitRegion, float damage, DamageType damageType, String ammoTypeId) {
        this.shooter = shooter;
        this.target = target;
        this.hitPoint = new Vector3(hitPoint);
        this.hitNormal = new Vector3(hitNormal);
        this.hitRegion = hitRegion;
        this.damage = damage;
        this.damageType = damageType;
        this.ammoTypeId = ammoTypeId;
    }
}
```

`ProjectileHitEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;

public final class ProjectileHitEvent {
    public final Entity shooter;
    public final Entity target;
    public final Vector3 hitPoint;
    public final float damage;
    public final DamageType damageType;
    public final float areaOfEffect;
    public final String ammoTypeId;

    public ProjectileHitEvent(Entity shooter, Entity target, Vector3 hitPoint,
                              float damage, DamageType damageType, float areaOfEffect, String ammoTypeId) {
        this.shooter = shooter;
        this.target = target;
        this.hitPoint = new Vector3(hitPoint);
        this.damage = damage;
        this.damageType = damageType;
        this.areaOfEffect = areaOfEffect;
        this.ammoTypeId = ammoTypeId;
    }
}
```

`MeleeHitEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;

public final class MeleeHitEvent {
    public final Entity attacker;
    public final Entity target;
    public final AttackDirection direction;
    public final HitRegion hitRegion;
    public final float damage;
    public final DamageType damageType;

    public MeleeHitEvent(Entity attacker, Entity target, AttackDirection direction,
                         HitRegion hitRegion, float damage, DamageType damageType) {
        this.attacker = attacker;
        this.target = target;
        this.direction = direction;
        this.hitRegion = hitRegion;
        this.damage = damage;
        this.damageType = damageType;
    }
}
```

`MeleeBlockEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;

public final class MeleeBlockEvent {
    public final Entity attacker;
    public final Entity blocker;
    public final AttackDirection attackDirection;
    public final AttackDirection blockDirection;
    public final boolean perfectBlock;

    public MeleeBlockEvent(Entity attacker, Entity blocker, AttackDirection attackDirection,
                           AttackDirection blockDirection, boolean perfectBlock) {
        this.attacker = attacker;
        this.blocker = blocker;
        this.attackDirection = attackDirection;
        this.blockDirection = blockDirection;
        this.perfectBlock = perfectBlock;
    }
}
```

`DamageDealtEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;

public final class DamageDealtEvent {
    public final Entity target;
    public final Entity attacker;
    public final float finalDamage;
    public final DamageType damageType;
    public final HitRegion hitRegion;

    public DamageDealtEvent(Entity target, Entity attacker, float finalDamage,
                            DamageType damageType, HitRegion hitRegion) {
        this.target = target;
        this.attacker = attacker;
        this.finalDamage = finalDamage;
        this.damageType = damageType;
        this.hitRegion = hitRegion;
    }
}
```

`ShieldAbsorbEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;

public final class ShieldAbsorbEvent {
    public final Entity target;
    public final float absorbed;
    public final float shieldRemaining;

    public ShieldAbsorbEvent(Entity target, float absorbed, float shieldRemaining) {
        this.target = target;
        this.absorbed = absorbed;
        this.shieldRemaining = shieldRemaining;
    }
}
```

`EntityKilledEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;

public final class EntityKilledEvent {
    public final Entity target;
    public final Entity killer;

    public EntityKilledEvent(Entity target, Entity killer) {
        this.target = target;
        this.killer = killer;
    }
}
```

`StatusEffectAppliedEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public final class StatusEffectAppliedEvent {
    public final Entity target;
    public final StatusEffectType effectType;
    public final Entity source;

    public StatusEffectAppliedEvent(Entity target, StatusEffectType effectType, Entity source) {
        this.target = target;
        this.effectType = effectType;
        this.source = source;
    }
}
```

`StatusEffectExpiredEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public final class StatusEffectExpiredEvent {
    public final Entity target;
    public final StatusEffectType effectType;

    public StatusEffectExpiredEvent(Entity target, StatusEffectType effectType) {
        this.target = target;
        this.effectType = effectType;
    }
}
```

`WeaponSwitchedEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.WeaponSlot;

public final class WeaponSwitchedEvent {
    public final Entity entity;
    public final WeaponSlot oldSlot;
    public final WeaponSlot newSlot;

    public WeaponSwitchedEvent(Entity entity, WeaponSlot oldSlot, WeaponSlot newSlot) {
        this.entity = entity;
        this.oldSlot = oldSlot;
        this.newSlot = newSlot;
    }
}
```

`ReloadStartedEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;

public final class ReloadStartedEvent {
    public final Entity entity;
    public final float reloadTime;

    public ReloadStartedEvent(Entity entity, float reloadTime) {
        this.entity = entity;
        this.reloadTime = reloadTime;
    }
}
```

`RecoilEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

public final class RecoilEvent {
    public final Entity entity;
    public final Vector2 recoilOffset;

    public RecoilEvent(Entity entity, Vector2 recoilOffset) {
        this.entity = entity;
        this.recoilOffset = new Vector2(recoilOffset);
    }
}
```

`ThreatDetectedEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public final class ThreatDetectedEvent {
    public final Entity detector;
    public final Entity threat;
    public final Vector3 position;
    public final int squadId;

    public ThreatDetectedEvent(Entity detector, Entity threat, Vector3 position, int squadId) {
        this.detector = detector;
        this.threat = threat;
        this.position = new Vector3(position);
        this.squadId = squadId;
    }
}
```

`FlankOrderEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.gdx.math.Vector3;

public final class FlankOrderEvent {
    public final int squadId;
    public final Vector3 targetPosition;
    public final Vector3 flankDirection;

    public FlankOrderEvent(int squadId, Vector3 targetPosition, Vector3 flankDirection) {
        this.squadId = squadId;
        this.targetPosition = new Vector3(targetPosition);
        this.flankDirection = new Vector3(flankDirection);
    }
}
```

`SuppressOrderEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.gdx.math.Vector3;

public final class SuppressOrderEvent {
    public final int squadId;
    public final Vector3 targetPosition;

    public SuppressOrderEvent(int squadId, Vector3 targetPosition) {
        this.squadId = squadId;
        this.targetPosition = new Vector3(targetPosition);
    }
}
```

`RetreatOrderEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.gdx.math.Vector3;

public final class RetreatOrderEvent {
    public final int squadId;
    public final Vector3 retreatPoint;

    public RetreatOrderEvent(int squadId, Vector3 retreatPoint) {
        this.squadId = squadId;
        this.retreatPoint = new Vector3(retreatPoint);
    }
}
```

`AdvanceOrderEvent.java`:
```java
package com.galacticodyssey.combat.events;

import com.badlogic.gdx.math.Vector3;

public final class AdvanceOrderEvent {
    public final int squadId;
    public final Vector3 targetPosition;

    public AdvanceOrderEvent(int squadId, Vector3 targetPosition) {
        this.squadId = squadId;
        this.targetPosition = new Vector3(targetPosition);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/events/
git commit -m "feat(combat): add combat event types"
```

---

### Task 3: Weapon Data Model, JSON Files, and Registries

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/data/WeaponFrameData.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/data/MeleeFrameData.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/data/BarrelData.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/data/AmmoTypeData.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/data/WeaponModData.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/data/QualityTierData.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/data/StatusEffectData.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/data/DamageConfigData.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/data/AIArchetypeData.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/data/WeaponAssembly.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/data/WeaponDataRegistry.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/data/CombatDataRegistry.java`
- Create: 9 JSON data files in `core/src/main/resources/data/`
- Test: `core/src/test/java/com/galacticodyssey/combat/data/WeaponDataRegistryTest.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/data/CombatDataRegistryTest.java`

- [ ] **Step 1: Create weapon part data POJOs**

All data POJOs use public fields matching JSON keys. libGDX's `Json` class deserializes by field name.

`WeaponFrameData.java`:
```java
package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.CombatEnums.WeaponCategory;

public class WeaponFrameData {
    public String id;
    public WeaponCategory category;
    public float baseDamage;
    public float baseFireRate;
    public float baseSpread;
    public float baseRecoil;
    public int magSize;
    public int modSlotCount;
    public float weight;
    public FiringMode firingMode;
    public boolean hitscan;
    public float range = 100f;
    public float reloadTime = 2.0f;
}
```

`MeleeFrameData.java`:
```java
package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.MeleeCategory;
import com.galacticodyssey.combat.CombatEnums.WeightClass;
import java.util.HashMap;
import java.util.Map;

public class MeleeFrameData {
    public String id;
    public MeleeCategory category;
    public float baseDamage;
    public float baseSpeed;
    public float baseReach;
    public float baseBlockEfficiency;
    public float weight;
    public DamageType damageType;
    public WeightClass weightClass = WeightClass.MEDIUM;
    public Map<String, Float> directionalModifiers = new HashMap<>();
}
```

`BarrelData.java`:
```java
package com.galacticodyssey.combat.data;

public class BarrelData {
    public String id;
    public float damageMultiplier = 1.0f;
    public float rangeMultiplier = 1.0f;
    public float spreadMultiplier = 1.0f;
    public float recoilMultiplier = 1.0f;
    public float weightAdd;
}
```

`AmmoTypeData.java`:
```java
package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public class AmmoTypeData {
    public String id;
    public DamageType damageType;
    public float damageMultiplier = 1.0f;
    public StatusEffectType statusEffect;
    public float statusEffectChance;
    public Float projectileSpeed;
}
```

`WeaponModData.java`:
```java
package com.galacticodyssey.combat.data;

import java.util.HashMap;
import java.util.Map;

public class WeaponModData {
    public String id;
    public String slot;
    public Map<String, Float> statModifiers = new HashMap<>();
}
```

`QualityTierData.java`:
```java
package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.QualityTier;

public class QualityTierData {
    public QualityTier tier;
    public float globalMultiplier = 1.0f;
    public float durabilityMultiplier = 1.0f;
}
```

`StatusEffectData.java`:
```java
package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public class StatusEffectData {
    public StatusEffectType type;
    public float duration;
    public float tickRate;
    public float magnitude;
    public int maxStacks = 1;
    public boolean disablesShields;
    public float armorReduction;
    public float moveSpeedMultiplier = 1.0f;
    public boolean preventsActions;
}
```

`DamageConfigData.java`:
```java
package com.galacticodyssey.combat.data;

import java.util.HashMap;
import java.util.Map;

public class DamageConfigData {
    public Map<String, Float> hitRegionMultipliers = new HashMap<>();
    public float maxArmorResistance = 0.85f;
    public float defaultShieldRechargeDelay = 4.0f;
    public float wrongBlockMitigation = 0.3f;
    public float exhaustionAttackPenalty = 0.4f;
    public float comboDamageBonus = 0.1f;
    public float comboStaminaPenalty = 0.2f;
    public int maxComboHits = 3;
    public float quickMeleeDamage = 15f;
    public float quickMeleeCooldown = 0.8f;
    public float quickMeleeStaggerDuration = 0.3f;
}
```

`AIArchetypeData.java`:
```java
package com.galacticodyssey.combat.data;

public class AIArchetypeData {
    public String id;
    public float aggroRange;
    public float engageRange;
    public float preferredRangeMin;
    public float preferredRangeMax;
    public float aggression;
    public float maxHP = 100f;
    public float armorRating;
    public String preferredWeaponFrame;
    public String behaviorTreePath;
}
```

`WeaponAssembly.java`:
```java
package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.QualityTier;

public class WeaponAssembly {
    public final String frameId;
    public final String barrelId;
    public final String ammoTypeId;
    public final String[] modIds;
    public final QualityTier quality;
    public final boolean isMelee;

    public WeaponAssembly(String frameId, String barrelId, String ammoTypeId,
                          String[] modIds, QualityTier quality, boolean isMelee) {
        this.frameId = frameId;
        this.barrelId = barrelId;
        this.ammoTypeId = ammoTypeId;
        this.modIds = modIds != null ? modIds : new String[0];
        this.quality = quality;
        this.isMelee = isMelee;
    }

    public static WeaponAssembly melee(String frameId, QualityTier quality) {
        return new WeaponAssembly(frameId, null, null, null, quality, true);
    }

    public static WeaponAssembly ranged(String frameId, String barrelId, String ammoTypeId,
                                         String[] modIds, QualityTier quality) {
        return new WeaponAssembly(frameId, barrelId, ammoTypeId, modIds, quality, false);
    }
}
```

- [ ] **Step 2: Create the WeaponDataRegistry**

`WeaponDataRegistry.java`:
```java
package com.galacticodyssey.combat.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.Map;

public class WeaponDataRegistry {
    private final Map<String, WeaponFrameData> frames = new HashMap<>();
    private final Map<String, MeleeFrameData> meleeFrames = new HashMap<>();
    private final Map<String, BarrelData> barrels = new HashMap<>();
    private final Map<String, AmmoTypeData> ammoTypes = new HashMap<>();
    private final Map<String, WeaponModData> mods = new HashMap<>();
    private final Map<String, QualityTierData> qualities = new HashMap<>();

    public void loadFromFiles() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        JsonReader reader = new JsonReader();

        loadArray(json, reader, "data/weapons/frames.json", WeaponFrameData.class, frames, d -> d.id);
        loadArray(json, reader, "data/weapons/melee_frames.json", MeleeFrameData.class, meleeFrames, d -> d.id);
        loadArray(json, reader, "data/weapons/barrels.json", BarrelData.class, barrels, d -> d.id);
        loadArray(json, reader, "data/weapons/ammo_types.json", AmmoTypeData.class, ammoTypes, d -> d.id);
        loadArray(json, reader, "data/weapons/mods.json", WeaponModData.class, mods, d -> d.id);
        loadArray(json, reader, "data/weapons/qualities.json", QualityTierData.class, qualities, d -> d.tier.name());
    }

    private <T> void loadArray(Json json, JsonReader reader, String path, Class<T> type,
                               Map<String, T> target, java.util.function.Function<T, String> idExtractor) {
        JsonValue root = reader.parse(Gdx.files.internal(path));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            T data = json.readValue(type, entry);
            target.put(idExtractor.apply(data), data);
        }
    }

    public WeaponFrameData getFrame(String id) { return frames.get(id); }
    public MeleeFrameData getMeleeFrame(String id) { return meleeFrames.get(id); }
    public BarrelData getBarrel(String id) { return barrels.get(id); }
    public AmmoTypeData getAmmoType(String id) { return ammoTypes.get(id); }
    public WeaponModData getMod(String id) { return mods.get(id); }
    public QualityTierData getQuality(String tierName) { return qualities.get(tierName); }

    public void registerFrame(WeaponFrameData data) { frames.put(data.id, data); }
    public void registerMeleeFrame(MeleeFrameData data) { meleeFrames.put(data.id, data); }
    public void registerBarrel(BarrelData data) { barrels.put(data.id, data); }
    public void registerAmmoType(AmmoTypeData data) { ammoTypes.put(data.id, data); }
    public void registerMod(WeaponModData data) { mods.put(data.id, data); }
    public void registerQuality(QualityTierData data) { qualities.put(data.tier.name(), data); }
}
```

- [ ] **Step 3: Create the CombatDataRegistry**

`CombatDataRegistry.java`:
```java
package com.galacticodyssey.combat.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import java.util.HashMap;
import java.util.Map;

public class CombatDataRegistry {
    private final Map<StatusEffectType, StatusEffectData> statusEffects = new HashMap<>();
    private final Map<String, AIArchetypeData> aiArchetypes = new HashMap<>();
    private DamageConfigData damageConfig;

    public void loadFromFiles() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        JsonReader reader = new JsonReader();

        JsonValue effectsRoot = reader.parse(Gdx.files.internal("data/combat/status_effects.json"));
        for (JsonValue entry = effectsRoot.child; entry != null; entry = entry.next) {
            StatusEffectData data = json.readValue(StatusEffectData.class, entry);
            statusEffects.put(data.type, data);
        }

        JsonValue archetypesRoot = reader.parse(Gdx.files.internal("data/combat/ai_archetypes.json"));
        for (JsonValue entry = archetypesRoot.child; entry != null; entry = entry.next) {
            AIArchetypeData data = json.readValue(AIArchetypeData.class, entry);
            aiArchetypes.put(data.id, data);
        }

        damageConfig = json.fromJson(DamageConfigData.class, Gdx.files.internal("data/combat/damage_config.json"));
    }

    public StatusEffectData getStatusEffect(StatusEffectType type) { return statusEffects.get(type); }
    public AIArchetypeData getArchetype(String id) { return aiArchetypes.get(id); }
    public DamageConfigData getDamageConfig() { return damageConfig; }

    public void registerStatusEffect(StatusEffectData data) { statusEffects.put(data.type, data); }
    public void registerArchetype(AIArchetypeData data) { aiArchetypes.put(data.id, data); }
    public void setDamageConfig(DamageConfigData config) { this.damageConfig = config; }
}
```

- [ ] **Step 4: Create JSON data files**

`core/src/main/resources/data/weapons/frames.json`:
```json
[
  {
    "id": "pistol_standard",
    "category": "PISTOL",
    "baseDamage": 18.0,
    "baseFireRate": 5.0,
    "baseSpread": 1.5,
    "baseRecoil": 2.0,
    "magSize": 12,
    "modSlotCount": 1,
    "weight": 1.2,
    "firingMode": "SEMI",
    "hitscan": true,
    "range": 50.0,
    "reloadTime": 1.5
  },
  {
    "id": "rifle_standard",
    "category": "RIFLE",
    "baseDamage": 25.0,
    "baseFireRate": 8.0,
    "baseSpread": 2.0,
    "baseRecoil": 3.5,
    "magSize": 30,
    "modSlotCount": 2,
    "weight": 3.5,
    "firingMode": "AUTO",
    "hitscan": true,
    "range": 100.0,
    "reloadTime": 2.0
  },
  {
    "id": "shotgun_standard",
    "category": "SHOTGUN",
    "baseDamage": 12.0,
    "baseFireRate": 1.5,
    "baseSpread": 8.0,
    "baseRecoil": 6.0,
    "magSize": 8,
    "modSlotCount": 1,
    "weight": 4.0,
    "firingMode": "SEMI",
    "hitscan": true,
    "range": 25.0,
    "reloadTime": 3.0
  },
  {
    "id": "plasma_rifle",
    "category": "RIFLE",
    "baseDamage": 35.0,
    "baseFireRate": 3.0,
    "baseSpread": 1.0,
    "baseRecoil": 2.0,
    "magSize": 20,
    "modSlotCount": 2,
    "weight": 3.0,
    "firingMode": "SEMI",
    "hitscan": false,
    "range": 80.0,
    "reloadTime": 2.5
  }
]
```

`core/src/main/resources/data/weapons/melee_frames.json`:
```json
[
  {
    "id": "combat_blade",
    "category": "BLADE",
    "baseDamage": 40.0,
    "baseSpeed": 1.2,
    "baseReach": 1.5,
    "baseBlockEfficiency": 0.7,
    "weight": 1.0,
    "damageType": "MELEE",
    "weightClass": "LIGHT",
    "directionalModifiers": { "overhead": 1.3, "thrust": 1.1, "left": 1.0, "right": 1.0 }
  },
  {
    "id": "shock_staff",
    "category": "STAFF",
    "baseDamage": 30.0,
    "baseSpeed": 1.0,
    "baseReach": 2.0,
    "baseBlockEfficiency": 0.8,
    "weight": 2.0,
    "damageType": "ENERGY",
    "weightClass": "MEDIUM",
    "directionalModifiers": { "overhead": 1.2, "thrust": 1.0, "left": 1.1, "right": 1.1 }
  },
  {
    "id": "power_hammer",
    "category": "HAMMER",
    "baseDamage": 65.0,
    "baseSpeed": 0.7,
    "baseReach": 1.3,
    "baseBlockEfficiency": 0.5,
    "weight": 4.0,
    "damageType": "MELEE",
    "weightClass": "HEAVY",
    "directionalModifiers": { "overhead": 1.5, "thrust": 0.8, "left": 1.0, "right": 1.0 }
  }
]
```

`core/src/main/resources/data/weapons/barrels.json`:
```json
[
  {
    "id": "standard_barrel",
    "damageMultiplier": 1.0,
    "rangeMultiplier": 1.0,
    "spreadMultiplier": 1.0,
    "recoilMultiplier": 1.0,
    "weightAdd": 0.0
  },
  {
    "id": "long_barrel",
    "damageMultiplier": 1.15,
    "rangeMultiplier": 1.4,
    "spreadMultiplier": 0.7,
    "recoilMultiplier": 1.1,
    "weightAdd": 0.8
  },
  {
    "id": "short_barrel",
    "damageMultiplier": 0.9,
    "rangeMultiplier": 0.7,
    "spreadMultiplier": 1.3,
    "recoilMultiplier": 0.8,
    "weightAdd": -0.3
  }
]
```

`core/src/main/resources/data/weapons/ammo_types.json`:
```json
[
  {
    "id": "standard_round",
    "damageType": "BALLISTIC",
    "damageMultiplier": 1.0,
    "statusEffect": null,
    "statusEffectChance": 0.0,
    "projectileSpeed": null
  },
  {
    "id": "incendiary_round",
    "damageType": "INCENDIARY",
    "damageMultiplier": 0.9,
    "statusEffect": "BURNING",
    "statusEffectChance": 0.3,
    "projectileSpeed": null
  },
  {
    "id": "plasma_cell",
    "damageType": "PLASMA",
    "damageMultiplier": 1.1,
    "statusEffect": null,
    "statusEffectChance": 0.0,
    "projectileSpeed": 40.0
  },
  {
    "id": "emp_round",
    "damageType": "EMP",
    "damageMultiplier": 0.7,
    "statusEffect": "EMP_DISABLED",
    "statusEffectChance": 0.5,
    "projectileSpeed": null
  }
]
```

`core/src/main/resources/data/weapons/mods.json`:
```json
[
  {
    "id": "basic_scope",
    "slot": "scope",
    "statModifiers": { "spread": -0.3 }
  },
  {
    "id": "stabilizer_mk2",
    "slot": "stabilizer",
    "statModifiers": { "spread": -0.5, "recoil": -0.8 }
  },
  {
    "id": "extended_mag",
    "slot": "magazine",
    "statModifiers": { "magSize": 10 }
  },
  {
    "id": "suppressor",
    "slot": "muzzle",
    "statModifiers": { "damage": -3.0, "recoil": -0.5 }
  }
]
```

`core/src/main/resources/data/weapons/qualities.json`:
```json
[
  { "tier": "SALVAGED", "globalMultiplier": 0.7, "durabilityMultiplier": 0.5 },
  { "tier": "COMMON", "globalMultiplier": 1.0, "durabilityMultiplier": 1.0 },
  { "tier": "REFINED", "globalMultiplier": 1.1, "durabilityMultiplier": 1.2 },
  { "tier": "MILITARY", "globalMultiplier": 1.2, "durabilityMultiplier": 1.5 },
  { "tier": "EXPERIMENTAL", "globalMultiplier": 1.35, "durabilityMultiplier": 1.3 },
  { "tier": "ALIEN", "globalMultiplier": 1.5, "durabilityMultiplier": 1.8 },
  { "tier": "PRECURSOR", "globalMultiplier": 1.75, "durabilityMultiplier": 2.0 }
]
```

`core/src/main/resources/data/combat/status_effects.json`:
```json
[
  { "type": "BLEEDING", "duration": 5.0, "tickRate": 1.0, "magnitude": 3.0, "maxStacks": 3, "disablesShields": false, "armorReduction": 0.0, "moveSpeedMultiplier": 1.0, "preventsActions": false },
  { "type": "BURNING", "duration": 4.0, "tickRate": 0.5, "magnitude": 5.0, "maxStacks": 1, "disablesShields": false, "armorReduction": 0.2, "moveSpeedMultiplier": 1.0, "preventsActions": false },
  { "type": "EMP_DISABLED", "duration": 6.0, "tickRate": 0.0, "magnitude": 0.0, "maxStacks": 1, "disablesShields": true, "armorReduction": 0.0, "moveSpeedMultiplier": 1.0, "preventsActions": false },
  { "type": "STUNNED", "duration": 0.8, "tickRate": 0.0, "magnitude": 0.0, "maxStacks": 1, "disablesShields": false, "armorReduction": 0.0, "moveSpeedMultiplier": 0.0, "preventsActions": true },
  { "type": "SLOWED", "duration": 4.0, "tickRate": 0.0, "magnitude": 0.0, "maxStacks": 1, "disablesShields": false, "armorReduction": 0.0, "moveSpeedMultiplier": 0.6, "preventsActions": false }
]
```

`core/src/main/resources/data/combat/damage_config.json`:
```json
{
  "hitRegionMultipliers": { "HEAD": 2.0, "TORSO": 1.0, "ARMS": 0.75, "LEGS": 0.75 },
  "maxArmorResistance": 0.85,
  "defaultShieldRechargeDelay": 4.0,
  "wrongBlockMitigation": 0.3,
  "exhaustionAttackPenalty": 0.4,
  "comboDamageBonus": 0.1,
  "comboStaminaPenalty": 0.2,
  "maxComboHits": 3,
  "quickMeleeDamage": 15.0,
  "quickMeleeCooldown": 0.8,
  "quickMeleeStaggerDuration": 0.3
}
```

`core/src/main/resources/data/combat/ai_archetypes.json`:
```json
[
  { "id": "grunt", "aggroRange": 25.0, "engageRange": 20.0, "preferredRangeMin": 10.0, "preferredRangeMax": 20.0, "aggression": 0.5, "maxHP": 80.0, "armorRating": 0.15, "preferredWeaponFrame": "rifle_standard" },
  { "id": "rusher", "aggroRange": 15.0, "engageRange": 15.0, "preferredRangeMin": 0.0, "preferredRangeMax": 3.0, "aggression": 0.9, "maxHP": 60.0, "armorRating": 0.05, "preferredWeaponFrame": "combat_blade" },
  { "id": "sniper", "aggroRange": 50.0, "engageRange": 50.0, "preferredRangeMin": 30.0, "preferredRangeMax": 50.0, "aggression": 0.2, "maxHP": 50.0, "armorRating": 0.1, "preferredWeaponFrame": "rifle_standard" },
  { "id": "heavy", "aggroRange": 20.0, "engageRange": 15.0, "preferredRangeMin": 8.0, "preferredRangeMax": 15.0, "aggression": 0.5, "maxHP": 150.0, "armorRating": 0.4, "preferredWeaponFrame": "shotgun_standard" },
  { "id": "officer", "aggroRange": 25.0, "engageRange": 25.0, "preferredRangeMin": 15.0, "preferredRangeMax": 25.0, "aggression": 0.3, "maxHP": 70.0, "armorRating": 0.2, "preferredWeaponFrame": "pistol_standard" }
]
```

- [ ] **Step 5: Write WeaponDataRegistry test**

`core/src/test/java/com/galacticodyssey/combat/data/WeaponDataRegistryTest.java`:
```java
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
```

- [ ] **Step 6: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.data.WeaponDataRegistryTest"`
Expected: 4 tests PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/data/ core/src/main/resources/data/ core/src/test/java/com/galacticodyssey/combat/data/
git commit -m "feat(combat): add weapon data model, JSON files, and registries"
```

---

### Task 4: WeaponStatsResolver

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/data/WeaponStatsResolver.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/data/WeaponStatsResolverTest.java`

- [ ] **Step 1: Write the failing test**

`WeaponStatsResolverTest.java`:
```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.data.WeaponStatsResolverTest"`
Expected: FAIL — `WeaponStatsResolver` class not found

- [ ] **Step 3: Implement WeaponStatsResolver**

`WeaponStatsResolver.java`:
```java
package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.*;
import java.util.HashMap;
import java.util.Map;

public final class WeaponStatsResolver {
    private WeaponStatsResolver() {}

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

    public static RangedStats resolveRanged(WeaponAssembly assembly, WeaponDataRegistry registry) {
        WeaponFrameData frame = registry.getFrame(assembly.frameId);
        if (frame == null) throw new IllegalArgumentException("Unknown frame: " + assembly.frameId);

        float barrelDamageMult = 1f, barrelRangeMult = 1f, barrelSpreadMult = 1f, barrelRecoilMult = 1f;
        float barrelWeightAdd = 0f;
        if (assembly.barrelId != null) {
            BarrelData barrel = registry.getBarrel(assembly.barrelId);
            if (barrel != null) {
                barrelDamageMult = barrel.damageMultiplier;
                barrelRangeMult = barrel.rangeMultiplier;
                barrelSpreadMult = barrel.spreadMultiplier;
                barrelRecoilMult = barrel.recoilMultiplier;
                barrelWeightAdd = barrel.weightAdd;
            }
        }

        float ammoDamageMult = 1f;
        DamageType damageType = DamageType.BALLISTIC;
        StatusEffectType statusEffect = null;
        float statusEffectChance = 0f;
        Float projectileSpeed = null;
        String ammoTypeId = assembly.ammoTypeId;
        if (assembly.ammoTypeId != null) {
            AmmoTypeData ammo = registry.getAmmoType(assembly.ammoTypeId);
            if (ammo != null) {
                ammoDamageMult = ammo.damageMultiplier;
                damageType = ammo.damageType;
                statusEffect = ammo.statusEffect;
                statusEffectChance = ammo.statusEffectChance;
                projectileSpeed = ammo.projectileSpeed;
            }
        }

        float qualityMult = 1f;
        if (assembly.quality != null) {
            QualityTierData q = registry.getQuality(assembly.quality.name());
            if (q != null) qualityMult = q.globalMultiplier;
        }

        float modDamageFlat = 0f, modSpreadFlat = 0f, modRecoilFlat = 0f;
        int modMagFlat = 0;
        for (String modId : assembly.modIds) {
            WeaponModData mod = registry.getMod(modId);
            if (mod == null) continue;
            modDamageFlat += mod.statModifiers.getOrDefault("damage", 0f);
            modSpreadFlat += mod.statModifiers.getOrDefault("spread", 0f);
            modRecoilFlat += mod.statModifiers.getOrDefault("recoil", 0f);
            modMagFlat += mod.statModifiers.getOrDefault("magSize", 0f).intValue();
        }

        RangedStats stats = new RangedStats();
        stats.damage = Math.max(1f, frame.baseDamage * barrelDamageMult * ammoDamageMult * qualityMult + modDamageFlat);
        stats.fireRate = Math.max(0.5f, frame.baseFireRate * qualityMult);
        stats.spread = Math.max(0f, frame.baseSpread * barrelSpreadMult * qualityMult + modSpreadFlat);
        stats.range = Math.max(5f, frame.range * barrelRangeMult * qualityMult);
        stats.recoil = Math.max(0f, frame.baseRecoil * barrelRecoilMult * qualityMult + modRecoilFlat);
        stats.weight = Math.max(0.1f, frame.weight + barrelWeightAdd);
        stats.magSize = Math.max(1, frame.magSize + modMagFlat);
        stats.reloadTime = frame.reloadTime;
        stats.firingMode = frame.firingMode;
        stats.hitscan = frame.hitscan;
        stats.damageType = damageType;
        stats.statusEffect = statusEffect;
        stats.statusEffectChance = statusEffectChance;
        stats.projectileSpeed = projectileSpeed;
        stats.ammoTypeId = ammoTypeId;
        return stats;
    }

    public static MeleeStats resolveMelee(WeaponAssembly assembly, WeaponDataRegistry registry) {
        MeleeFrameData frame = registry.getMeleeFrame(assembly.frameId);
        if (frame == null) throw new IllegalArgumentException("Unknown melee frame: " + assembly.frameId);

        float qualityMult = 1f;
        if (assembly.quality != null) {
            QualityTierData q = registry.getQuality(assembly.quality.name());
            if (q != null) qualityMult = q.globalMultiplier;
        }

        MeleeStats stats = new MeleeStats();
        stats.baseDamage = Math.max(1f, frame.baseDamage * qualityMult);
        stats.speed = frame.baseSpeed;
        stats.reach = frame.baseReach;
        stats.blockEfficiency = frame.baseBlockEfficiency;
        stats.weight = frame.weight;
        stats.damageType = frame.damageType;
        stats.weightClass = frame.weightClass;
        stats.directionalModifiers = new HashMap<>(frame.directionalModifiers);
        return stats;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.data.WeaponStatsResolverTest"`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/data/WeaponStatsResolver.java core/src/test/java/com/galacticodyssey/combat/data/WeaponStatsResolverTest.java
git commit -m "feat(combat): add WeaponStatsResolver with stat resolution pipeline"
```

---

### Task 5: Combat ECS Components

**Files:**
- Create: 15 component files in `core/src/main/java/com/galacticodyssey/combat/components/`

- [ ] **Step 1: Create health and defense components**

`HealthComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;

public class HealthComponent implements Component {
    public float currentHP = 100f;
    public float maxHP = 100f;
    public boolean alive = true;
}
```

`ShieldComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.ShieldType;

public class ShieldComponent implements Component {
    public float currentShield = 40f;
    public float maxShield = 40f;
    public float rechargeRate = 8f;
    public float rechargeDelay = 4f;
    public float timeSinceLastHit = 0f;
    public ShieldType shieldType = ShieldType.ENERGY;
}
```

`ArmorComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import java.util.EnumMap;
import java.util.Map;

public class ArmorComponent implements Component {
    public final Map<HitRegion, Float> armorRating = new EnumMap<>(HitRegion.class);
    public final Map<HitRegion, Map<DamageType, Float>> resistances = new EnumMap<>(HitRegion.class);
    public final Map<HitRegion, Float> durability = new EnumMap<>(HitRegion.class);
    public final Map<HitRegion, Float> maxDurability = new EnumMap<>(HitRegion.class);

    public ArmorComponent() {
        for (HitRegion region : HitRegion.values()) {
            armorRating.put(region, 0f);
            resistances.put(region, new EnumMap<>(DamageType.class));
            durability.put(region, 100f);
            maxDurability.put(region, 100f);
        }
    }

    public float getResistance(DamageType type, HitRegion region) {
        float durabilityRatio = maxDurability.get(region) > 0
            ? durability.get(region) / maxDurability.get(region) : 0f;
        float baseResist = resistances.get(region).getOrDefault(type, 0f);
        return baseResist * durabilityRatio;
    }

    public void degradeSlot(HitRegion region, float amount) {
        float current = durability.get(region);
        durability.put(region, Math.max(0f, current - amount * 0.1f));
    }
}
```

`ActiveStatusEffect.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public class ActiveStatusEffect {
    public final StatusEffectType type;
    public float remainingDuration;
    public final float tickRate;
    public final float magnitude;
    public float tickAccumulator;
    public final Entity source;
    public int stacks;

    public ActiveStatusEffect(StatusEffectType type, float duration, float tickRate,
                              float magnitude, Entity source) {
        this.type = type;
        this.remainingDuration = duration;
        this.tickRate = tickRate;
        this.magnitude = magnitude;
        this.source = source;
        this.stacks = 1;
    }
}
```

`StatusEffectsComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import java.util.ArrayList;
import java.util.List;

public class StatusEffectsComponent implements Component {
    public final List<ActiveStatusEffect> activeEffects = new ArrayList<>();

    public ActiveStatusEffect getEffect(StatusEffectType type) {
        for (ActiveStatusEffect e : activeEffects) {
            if (e.type == type) return e;
        }
        return null;
    }

    public boolean hasEffect(StatusEffectType type) {
        return getEffect(type) != null;
    }
}
```

- [ ] **Step 2: Create weapon components**

`WeaponInventoryComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.WeaponSlot;
import com.galacticodyssey.combat.data.WeaponAssembly;

public class WeaponInventoryComponent implements Component {
    public final WeaponAssembly[] slots = new WeaponAssembly[3];
    public int activeSlotIndex = 0;
    public float switchTimer = 0f;
    public boolean switching = false;
    public boolean lowering = true;

    public WeaponAssembly getActiveAssembly() {
        return slots[activeSlotIndex];
    }

    public WeaponSlot getActiveSlot() {
        return WeaponSlot.values()[activeSlotIndex];
    }

    public boolean isActiveSlotMelee() {
        return activeSlotIndex == WeaponSlot.MELEE.index;
    }
}
```

`RangedWeaponComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public class RangedWeaponComponent implements Component {
    public float damage;
    public float fireRate;
    public float spread;
    public float range;
    public float recoil;
    public Vector2[] recoilPattern;
    public int recoilIndex;
    public int currentAmmo;
    public int magSize;
    public float reloadTime;
    public float reloadTimer;
    public boolean reloading;
    public FiringMode firingMode;
    public boolean hitscan;
    public DamageType damageType;
    public StatusEffectType statusEffect;
    public float statusEffectChance;
    public Float projectileSpeed;
    public String ammoTypeId;
    public float fireTimer;
    public int burstShotsRemaining;
    public float burstDelay = 0.05f;
    public float burstTimer;
}
```

`MeleeWeaponComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.WeightClass;
import java.util.EnumMap;
import java.util.Map;

public class MeleeWeaponComponent implements Component {
    public float baseDamage;
    public float reach;
    public float swingSpeed;
    public float blockEfficiency;
    public DamageType damageType;
    public WeightClass weightClass;
    public final Map<AttackDirection, Float> directionalModifiers = new EnumMap<>(AttackDirection.class);
    public final Map<AttackDirection, Float> staminaCosts = new EnumMap<>(AttackDirection.class);

    public MeleeWeaponComponent() {
        for (AttackDirection dir : AttackDirection.values()) {
            directionalModifiers.put(dir, 1.0f);
            staminaCosts.put(dir, 15f);
        }
        staminaCosts.put(AttackDirection.OVERHEAD, 20f);
    }
}
```

`MeleeStateComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.MeleeState;

public class MeleeStateComponent implements Component {
    public MeleeState currentState = MeleeState.IDLE;
    public AttackDirection attackDirection = AttackDirection.LEFT;
    public AttackDirection blockDirection = AttackDirection.LEFT;
    public float stateTimer;
    public int comboCounter;
    public boolean canCombo;
    public AttackDirection queuedDirection;
}
```

- [ ] **Step 3: Create combat input, hitbox, and AI components**

`CombatInputComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;

public class CombatInputComponent implements Component {
    public boolean fireRequested;
    public boolean fireHeld;
    public final Vector3 aimDirection = new Vector3(0, 0, -1);
    public AttackDirection meleeAttackDirection;
    public boolean meleeAttackRequested;
    public boolean blockRequested;
    public boolean blockHeld;
    public AttackDirection blockDirection;
    public boolean reloadRequested;
    public int switchSlotRequested = -1;
    public boolean quickMeleeRequested;
}
```

`HitboxComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import java.util.EnumMap;
import java.util.Map;

public class HitboxComponent implements Component {
    public final Map<HitRegion, Float> regionMultipliers = new EnumMap<>(HitRegion.class);
    public float bodyHeight = 1.8f;
    public float headHeightRatio = 0.85f;
    public float torsoHeightRatio = 0.5f;
    public float legsHeightRatio = 0.25f;

    public HitboxComponent() {
        regionMultipliers.put(HitRegion.HEAD, 2.0f);
        regionMultipliers.put(HitRegion.TORSO, 1.0f);
        regionMultipliers.put(HitRegion.ARMS, 0.75f);
        regionMultipliers.put(HitRegion.LEGS, 0.75f);
    }

    public HitRegion getRegionForHeight(float hitHeightRatio) {
        if (hitHeightRatio >= headHeightRatio) return HitRegion.HEAD;
        if (hitHeightRatio >= torsoHeightRatio) return HitRegion.TORSO;
        if (hitHeightRatio >= legsHeightRatio) return HitRegion.ARMS;
        return HitRegion.LEGS;
    }
}
```

`HostileTagComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;

public class HostileTagComponent implements Component {
    public String factionId = "hostile";
}
```

`CombatAIComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.math.Vector3;

public class CombatAIComponent implements Component {
    public BehaviorTree<Entity> behaviorTree;
    public Entity currentTarget;
    public float aggroRange = 25f;
    public float engageRange = 20f;
    public float preferredRangeMin = 10f;
    public float preferredRangeMax = 20f;
    public float aggression = 0.5f;
    public float threatLevel;
    public final Vector3 lastKnownTargetPosition = new Vector3();
    public boolean hasLastKnownPosition;
    public float searchTimer;
    public float searchDuration = 10f;
    public String archetypeId;
}
```

`SquadComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.SquadRole;

public class SquadComponent implements Component {
    public int squadId;
    public SquadRole role = SquadRole.FLANKER;
    public final Vector3 formationOffset = new Vector3();
}
```

`CoverPoint.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.CoverQuality;

public class CoverPoint {
    public final Vector3 position = new Vector3();
    public final Vector3 normal = new Vector3();
    public CoverQuality quality = CoverQuality.HALF;
    public boolean occupied;

    public CoverPoint() {}

    public CoverPoint(Vector3 position, Vector3 normal, CoverQuality quality) {
        this.position.set(position);
        this.normal.set(normal);
        this.quality = quality;
    }
}
```

`CoverComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

public class CoverComponent implements Component {
    public CoverPoint currentCoverPoint;
    public boolean inCover;
    public final Vector3 peekDirection = new Vector3();
}
```

`ProjectileComponent.java`:
```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.DamageType;

public class ProjectileComponent implements Component {
    public float speed;
    public float damage;
    public DamageType damageType;
    public Entity owner;
    public float lifetime;
    public float age;
    public float areaOfEffect;
    public String ammoTypeId;
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/components/
git commit -m "feat(combat): add all combat ECS components"
```

---

### Task 6: DamageSystem and StatusEffectSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/systems/DamageSystem.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/systems/StatusEffectSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/systems/DamageSystemTest.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/systems/StatusEffectSystemTest.java`

- [ ] **Step 1: Write DamageSystem test**

`DamageSystemTest.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.*;
import com.galacticodyssey.combat.events.*;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DamageSystemTest {
    private Engine engine;
    private EventBus eventBus;
    private DamageSystem damageSystem;
    private Entity target;
    private Entity attacker;
    private DamageConfigData config;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        config = new DamageConfigData();
        config.hitRegionMultipliers.put("HEAD", 2.0f);
        config.hitRegionMultipliers.put("TORSO", 1.0f);
        config.hitRegionMultipliers.put("ARMS", 0.75f);
        config.hitRegionMultipliers.put("LEGS", 0.75f);
        config.maxArmorResistance = 0.85f;

        CombatDataRegistry combatData = new CombatDataRegistry();
        combatData.setDamageConfig(config);

        StatusEffectData bleedData = new StatusEffectData();
        bleedData.type = StatusEffectType.BLEEDING;
        bleedData.duration = 5f;
        bleedData.tickRate = 1f;
        bleedData.magnitude = 3f;
        bleedData.maxStacks = 3;
        combatData.registerStatusEffect(bleedData);

        WeaponDataRegistry weaponData = new WeaponDataRegistry();
        damageSystem = new DamageSystem(eventBus, combatData, weaponData);
        engine = new Engine();
        engine.addSystem(damageSystem);

        target = new Entity();
        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        target.add(health);
        target.add(new HitboxComponent());
        target.add(new StatusEffectsComponent());
        engine.addEntity(target);

        attacker = new Entity();
        engine.addEntity(attacker);
    }

    @Test
    void headshotDealsTwoDamage() {
        float[] dealt = {0f};
        eventBus.subscribe(DamageDealtEvent.class, e -> dealt[0] = e.finalDamage);

        eventBus.publish(new HitscanHitEvent(attacker, target,
            new com.badlogic.gdx.math.Vector3(), new com.badlogic.gdx.math.Vector3(),
            HitRegion.HEAD, 20f, DamageType.BALLISTIC, "standard_round"));

        assertEquals(40f, dealt[0], 0.01f);
    }

    @Test
    void shieldAbsorbsDamageBeforeHealth() {
        ShieldComponent shield = new ShieldComponent();
        shield.currentShield = 30f;
        shield.maxShield = 30f;
        target.add(shield);

        eventBus.publish(new HitscanHitEvent(attacker, target,
            new com.badlogic.gdx.math.Vector3(), new com.badlogic.gdx.math.Vector3(),
            HitRegion.TORSO, 50f, DamageType.BALLISTIC, "standard_round"));

        assertEquals(0f, shield.currentShield, 0.01f);
        HealthComponent h = target.getComponent(HealthComponent.class);
        assertEquals(80f, h.currentHP, 0.01f);
    }

    @Test
    void armorReducesDamage() {
        ArmorComponent armor = new ArmorComponent();
        armor.resistances.get(HitRegion.TORSO).put(DamageType.BALLISTIC, 0.5f);
        target.add(armor);

        float[] dealt = {0f};
        eventBus.subscribe(DamageDealtEvent.class, e -> dealt[0] = e.finalDamage);

        eventBus.publish(new HitscanHitEvent(attacker, target,
            new com.badlogic.gdx.math.Vector3(), new com.badlogic.gdx.math.Vector3(),
            HitRegion.TORSO, 40f, DamageType.BALLISTIC, "standard_round"));

        assertEquals(20f, dealt[0], 0.01f);
    }

    @Test
    void entityKilledAtZeroHP() {
        boolean[] killed = {false};
        eventBus.subscribe(EntityKilledEvent.class, e -> killed[0] = true);

        target.getComponent(HealthComponent.class).currentHP = 10f;
        eventBus.publish(new HitscanHitEvent(attacker, target,
            new com.badlogic.gdx.math.Vector3(), new com.badlogic.gdx.math.Vector3(),
            HitRegion.TORSO, 50f, DamageType.BALLISTIC, "standard_round"));

        assertTrue(killed[0]);
        assertFalse(target.getComponent(HealthComponent.class).alive);
    }

    @Test
    void meleeHitProcessedThroughPipeline() {
        float[] dealt = {0f};
        eventBus.subscribe(DamageDealtEvent.class, e -> dealt[0] = e.finalDamage);

        eventBus.publish(new MeleeHitEvent(attacker, target,
            AttackDirection.OVERHEAD, HitRegion.HEAD, 40f, DamageType.MELEE));

        // 40 * 2.0 (head) = 80
        assertEquals(80f, dealt[0], 0.01f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.systems.DamageSystemTest"`
Expected: FAIL — `DamageSystem` class not found

- [ ] **Step 3: Implement DamageSystem**

`DamageSystem.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.AmmoTypeData;
import com.galacticodyssey.combat.data.CombatDataRegistry;
import com.galacticodyssey.combat.data.StatusEffectData;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.combat.events.*;
import com.galacticodyssey.core.EventBus;
import java.util.Random;

public class DamageSystem extends EntitySystem {
    private final EventBus eventBus;
    private final CombatDataRegistry combatData;
    private final WeaponDataRegistry weaponData;
    private final Random random = new Random();

    private final ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);
    private final ComponentMapper<ShieldComponent> shieldMapper = ComponentMapper.getFor(ShieldComponent.class);
    private final ComponentMapper<ArmorComponent> armorMapper = ComponentMapper.getFor(ArmorComponent.class);
    private final ComponentMapper<HitboxComponent> hitboxMapper = ComponentMapper.getFor(HitboxComponent.class);
    private final ComponentMapper<StatusEffectsComponent> statusMapper = ComponentMapper.getFor(StatusEffectsComponent.class);

    public DamageSystem(EventBus eventBus, CombatDataRegistry combatData, WeaponDataRegistry weaponData) {
        super(8);
        this.eventBus = eventBus;
        this.combatData = combatData;
        this.weaponData = weaponData;

        eventBus.subscribe(HitscanHitEvent.class, this::onHitscanHit);
        eventBus.subscribe(ProjectileHitEvent.class, this::onProjectileHit);
        eventBus.subscribe(MeleeHitEvent.class, this::onMeleeHit);
    }

    private void onHitscanHit(HitscanHitEvent event) {
        processDamage(event.shooter, event.target, event.damage, event.damageType,
            event.hitRegion, event.ammoTypeId);
    }

    private void onProjectileHit(ProjectileHitEvent event) {
        processDamage(event.shooter, event.target, event.damage, event.damageType,
            HitRegion.TORSO, event.ammoTypeId);
    }

    private void onMeleeHit(MeleeHitEvent event) {
        processDamage(event.attacker, event.target, event.damage, event.damageType,
            event.hitRegion, null);
    }

    private void processDamage(Entity attacker, Entity target, float rawDamage,
                               DamageType damageType, HitRegion hitRegion, String ammoTypeId) {
        HealthComponent health = healthMapper.get(target);
        if (health == null || !health.alive) return;

        float damage = rawDamage;

        // 1. Hitbox multiplier
        HitboxComponent hitbox = hitboxMapper.get(target);
        if (hitbox != null) {
            damage *= hitbox.regionMultipliers.getOrDefault(hitRegion, 1.0f);
        }

        // 2. Shield check
        ShieldComponent shield = shieldMapper.get(target);
        StatusEffectsComponent statusEffects = statusMapper.get(target);
        boolean shieldsDisabled = statusEffects != null && statusEffects.hasEffect(StatusEffectType.EMP_DISABLED);

        if (shield != null && shield.currentShield > 0 && !shieldsDisabled) {
            float absorbed = Math.min(damage, shield.currentShield);
            damage -= absorbed;
            shield.currentShield -= absorbed;
            shield.timeSinceLastHit = 0f;
            eventBus.publish(new ShieldAbsorbEvent(target, absorbed, shield.currentShield));
            if (damage <= 0) return;
        }

        // 3. Armor mitigation
        ArmorComponent armor = armorMapper.get(target);
        if (armor != null) {
            float resistance = armor.getResistance(damageType, hitRegion);
            float maxResist = combatData.getDamageConfig().maxArmorResistance;
            resistance = Math.min(resistance, maxResist);

            // Burning reduces armor effectiveness
            if (statusEffects != null && statusEffects.hasEffect(StatusEffectType.BURNING)) {
                StatusEffectData burnData = combatData.getStatusEffect(StatusEffectType.BURNING);
                if (burnData != null) resistance *= (1f - burnData.armorReduction);
            }

            damage *= (1f - resistance);
            armor.degradeSlot(hitRegion, rawDamage);
        }

        // 4. Health damage
        health.currentHP -= damage;
        eventBus.publish(new DamageDealtEvent(target, attacker, damage, damageType, hitRegion));

        if (health.currentHP <= 0) {
            health.currentHP = 0;
            health.alive = false;
            eventBus.publish(new EntityKilledEvent(target, attacker));
        }

        // 5. Status effect roll
        if (ammoTypeId != null && statusEffects != null) {
            applyStatusEffectFromAmmo(attacker, target, ammoTypeId, statusEffects);
        }
    }

    private void applyStatusEffectFromAmmo(Entity attacker, Entity target, String ammoTypeId,
                                           StatusEffectsComponent statusEffects) {
        if (weaponData == null) return;
        AmmoTypeData ammo = weaponData.getAmmoType(ammoTypeId);
        if (ammo == null || ammo.statusEffect == null) return;
        if (new Random().nextFloat() > ammo.statusEffectChance) return;
        applyStatusEffect(target, attacker, ammo.statusEffect);
    }

    public void applyStatusEffect(Entity target, Entity source, StatusEffectType type) {
        StatusEffectsComponent effects = statusMapper.get(target);
        if (effects == null) return;

        StatusEffectData data = combatData.getStatusEffect(type);
        if (data == null) return;

        ActiveStatusEffect existing = effects.getEffect(type);
        if (existing != null) {
            if (existing.stacks < data.maxStacks) {
                existing.stacks++;
            }
            existing.remainingDuration = data.duration;
        } else {
            effects.activeEffects.add(new ActiveStatusEffect(
                type, data.duration, data.tickRate, data.magnitude, source));
        }
        eventBus.publish(new StatusEffectAppliedEvent(target, type, source));
    }
}
```

- [ ] **Step 4: Run DamageSystem tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.systems.DamageSystemTest"`
Expected: 5 tests PASS

- [ ] **Step 5: Write StatusEffectSystem test**

`StatusEffectSystemTest.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.*;
import com.galacticodyssey.combat.events.StatusEffectExpiredEvent;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatusEffectSystemTest {
    private Engine engine;
    private EventBus eventBus;
    private Entity target;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        CombatDataRegistry combatData = new CombatDataRegistry();

        StatusEffectData bleed = new StatusEffectData();
        bleed.type = StatusEffectType.BLEEDING;
        bleed.duration = 5f;
        bleed.tickRate = 1f;
        bleed.magnitude = 3f;
        bleed.maxStacks = 3;
        combatData.registerStatusEffect(bleed);

        engine = new Engine();
        engine.addSystem(new StatusEffectSystem(eventBus, combatData));

        target = new Entity();
        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        target.add(health);
        StatusEffectsComponent effects = new StatusEffectsComponent();
        effects.activeEffects.add(new ActiveStatusEffect(
            StatusEffectType.BLEEDING, 2f, 1f, 3f, null));
        target.add(effects);
        engine.addEntity(target);
    }

    @Test
    void bleedingTicksDamageOverTime() {
        engine.update(1.0f);
        HealthComponent h = target.getComponent(HealthComponent.class);
        assertEquals(97f, h.currentHP, 0.01f);
    }

    @Test
    void expiredEffectRemoved() {
        boolean[] expired = {false};
        eventBus.subscribe(StatusEffectExpiredEvent.class, e -> expired[0] = true);

        engine.update(1.0f);
        engine.update(1.1f);
        assertTrue(expired[0]);

        StatusEffectsComponent effects = target.getComponent(StatusEffectsComponent.class);
        assertFalse(effects.hasEffect(StatusEffectType.BLEEDING));
    }

    @Test
    void stacksMultiplyDamage() {
        StatusEffectsComponent effects = target.getComponent(StatusEffectsComponent.class);
        effects.activeEffects.get(0).stacks = 3;

        engine.update(1.0f);
        HealthComponent h = target.getComponent(HealthComponent.class);
        // 3 damage * 3 stacks = 9 per tick
        assertEquals(91f, h.currentHP, 0.01f);
    }
}
```

- [ ] **Step 6: Implement StatusEffectSystem**

`StatusEffectSystem.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.*;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.CombatDataRegistry;
import com.galacticodyssey.combat.data.StatusEffectData;
import com.galacticodyssey.combat.events.StatusEffectExpiredEvent;
import com.galacticodyssey.core.EventBus;
import java.util.Iterator;

public class StatusEffectSystem extends IteratingSystem {
    private final EventBus eventBus;
    private final CombatDataRegistry combatData;
    private final ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);
    private final ComponentMapper<StatusEffectsComponent> statusMapper = ComponentMapper.getFor(StatusEffectsComponent.class);
    private final ComponentMapper<ShieldComponent> shieldMapper = ComponentMapper.getFor(ShieldComponent.class);

    public StatusEffectSystem(EventBus eventBus, CombatDataRegistry combatData) {
        super(Family.all(StatusEffectsComponent.class).get(), 9);
        this.eventBus = eventBus;
        this.combatData = combatData;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        StatusEffectsComponent effects = statusMapper.get(entity);
        HealthComponent health = healthMapper.get(entity);

        // Shield recharge
        ShieldComponent shield = shieldMapper.get(entity);
        if (shield != null && shield.currentShield < shield.maxShield) {
            boolean shieldsDisabled = effects.hasEffect(StatusEffectType.EMP_DISABLED);
            if (!shieldsDisabled) {
                shield.timeSinceLastHit += deltaTime;
                if (shield.timeSinceLastHit >= shield.rechargeDelay) {
                    shield.currentShield = Math.min(shield.maxShield,
                        shield.currentShield + shield.rechargeRate * deltaTime);
                }
            }
        }

        Iterator<ActiveStatusEffect> iter = effects.activeEffects.iterator();
        while (iter.hasNext()) {
            ActiveStatusEffect effect = iter.next();
            effect.remainingDuration -= deltaTime;

            if (effect.remainingDuration <= 0) {
                iter.remove();
                eventBus.publish(new StatusEffectExpiredEvent(entity, effect.type));
                continue;
            }

            if (effect.tickRate > 0 && health != null) {
                effect.tickAccumulator += deltaTime;
                while (effect.tickAccumulator >= effect.tickRate) {
                    effect.tickAccumulator -= effect.tickRate;
                    float tickDamage = effect.magnitude * effect.stacks;

                    // Burning + EMP combo: double damage
                    if (effect.type == StatusEffectType.BURNING && effects.hasEffect(StatusEffectType.EMP_DISABLED)) {
                        tickDamage *= 2f;
                    }

                    health.currentHP -= tickDamage;
                    if (health.currentHP <= 0) {
                        health.currentHP = 0;
                        health.alive = false;
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 7: Run all damage/status tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.systems.*"`
Expected: 8 tests PASS

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/systems/DamageSystem.java core/src/main/java/com/galacticodyssey/combat/systems/StatusEffectSystem.java core/src/test/java/com/galacticodyssey/combat/systems/
git commit -m "feat(combat): add DamageSystem and StatusEffectSystem with unified pipeline"
```

---

### Task 7: WeaponSystem and WeaponSwitchSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/systems/WeaponSystem.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/systems/WeaponSwitchSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/systems/WeaponSystemTest.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/systems/WeaponSwitchSystemTest.java`

- [ ] **Step 1: Write WeaponSystem test**

`WeaponSystemTest.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.events.ReloadStartedEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponSystemTest {
    private Engine engine;
    private EventBus eventBus;
    private Entity player;
    private RangedWeaponComponent weapon;
    private CombatInputComponent input;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new WeaponSystem(eventBus));

        player = new Entity();
        input = new CombatInputComponent();
        weapon = new RangedWeaponComponent();
        weapon.damage = 25f;
        weapon.fireRate = 10f;
        weapon.currentAmmo = 30;
        weapon.magSize = 30;
        weapon.reloadTime = 2f;
        weapon.firingMode = FiringMode.SEMI;
        weapon.hitscan = true;
        weapon.damageType = DamageType.BALLISTIC;

        WeaponInventoryComponent inv = new WeaponInventoryComponent();
        inv.activeSlotIndex = 0;

        player.add(input);
        player.add(weapon);
        player.add(inv);
        engine.addEntity(player);
    }

    @Test
    void semiAutoFiresOncePerClick() {
        boolean[] fired = {false};
        eventBus.subscribe(WeaponFiredEvent.class, e -> fired[0] = true);

        input.fireRequested = true;
        engine.update(0.016f);
        assertTrue(fired[0]);
        assertEquals(29, weapon.currentAmmo);
    }

    @Test
    void cannotFireWhileReloading() {
        weapon.reloading = true;
        weapon.reloadTimer = 1f;
        input.fireRequested = true;

        boolean[] fired = {false};
        eventBus.subscribe(WeaponFiredEvent.class, e -> fired[0] = true);

        engine.update(0.016f);
        assertFalse(fired[0]);
    }

    @Test
    void reloadRefillsAmmo() {
        weapon.currentAmmo = 0;
        input.reloadRequested = true;

        engine.update(0.016f);
        assertTrue(weapon.reloading);

        engine.update(2.1f);
        assertFalse(weapon.reloading);
        assertEquals(30, weapon.currentAmmo);
    }

    @Test
    void emptyMagCannotFire() {
        weapon.currentAmmo = 0;
        boolean[] fired = {false};
        eventBus.subscribe(WeaponFiredEvent.class, e -> fired[0] = true);

        input.fireRequested = true;
        engine.update(0.016f);
        assertFalse(fired[0]);
    }

    @Test
    void autoFiresContinuouslyWhileHeld() {
        weapon.firingMode = FiringMode.AUTO;
        input.fireHeld = true;

        int[] count = {0};
        eventBus.subscribe(WeaponFiredEvent.class, e -> count[0]++);

        // fireRate = 10 shots/sec = 0.1s interval
        // Update 0.25s = should fire ~2-3 times
        engine.update(0.1f);
        engine.update(0.1f);
        engine.update(0.05f);
        assertTrue(count[0] >= 2);
    }
}
```

- [ ] **Step 2: Implement WeaponSystem**

`WeaponSystem.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.*;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.events.ReloadStartedEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;

public class WeaponSystem extends IteratingSystem {
    private final EventBus eventBus;
    private final ComponentMapper<CombatInputComponent> inputMapper = ComponentMapper.getFor(CombatInputComponent.class);
    private final ComponentMapper<RangedWeaponComponent> weaponMapper = ComponentMapper.getFor(RangedWeaponComponent.class);
    private final ComponentMapper<WeaponInventoryComponent> invMapper = ComponentMapper.getFor(WeaponInventoryComponent.class);

    public WeaponSystem(EventBus eventBus) {
        super(Family.all(CombatInputComponent.class, RangedWeaponComponent.class,
            WeaponInventoryComponent.class).get(), 4);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CombatInputComponent input = inputMapper.get(entity);
        RangedWeaponComponent weapon = weaponMapper.get(entity);
        WeaponInventoryComponent inv = invMapper.get(entity);

        if (inv.switching || inv.isActiveSlotMelee()) return;

        // Update fire timer
        if (weapon.fireTimer > 0) weapon.fireTimer -= deltaTime;

        // Handle reload
        if (weapon.reloading) {
            weapon.reloadTimer -= deltaTime;
            if (weapon.reloadTimer <= 0) {
                weapon.reloading = false;
                weapon.currentAmmo = weapon.magSize;
            }
            return;
        }

        if (input.reloadRequested && weapon.currentAmmo < weapon.magSize) {
            weapon.reloading = true;
            weapon.reloadTimer = weapon.reloadTime;
            eventBus.publish(new ReloadStartedEvent(entity, weapon.reloadTime));
            input.reloadRequested = false;
            return;
        }

        // Handle firing
        boolean wantsFire = false;
        switch (weapon.firingMode) {
            case SEMI:
                wantsFire = input.fireRequested;
                break;
            case AUTO:
                wantsFire = input.fireHeld || input.fireRequested;
                break;
            case BURST:
                if (input.fireRequested && weapon.burstShotsRemaining <= 0) {
                    weapon.burstShotsRemaining = 3;
                }
                if (weapon.burstShotsRemaining > 0) {
                    weapon.burstTimer -= deltaTime;
                    if (weapon.burstTimer <= 0) {
                        wantsFire = true;
                        weapon.burstTimer = weapon.burstDelay;
                    }
                }
                break;
        }

        if (wantsFire && weapon.fireTimer <= 0 && weapon.currentAmmo > 0) {
            weapon.currentAmmo--;
            weapon.fireTimer = 1f / weapon.fireRate;
            weapon.recoilIndex = (weapon.recoilIndex + 1) % Math.max(1,
                weapon.recoilPattern != null ? weapon.recoilPattern.length : 1);

            eventBus.publish(new WeaponFiredEvent(entity, input.aimDirection, weapon.hitscan));

            if (weapon.firingMode == FiringMode.BURST) {
                weapon.burstShotsRemaining--;
            }
        }

        input.fireRequested = false;
        input.reloadRequested = false;
    }
}
```

- [ ] **Step 3: Write WeaponSwitchSystem test**

`WeaponSwitchSystemTest.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.*;
import com.galacticodyssey.combat.events.WeaponSwitchedEvent;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponSwitchSystemTest {
    private Engine engine;
    private EventBus eventBus;
    private Entity player;
    private WeaponInventoryComponent inv;
    private CombatInputComponent input;
    private WeaponDataRegistry weaponData;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        weaponData = new WeaponDataRegistry();

        WeaponFrameData rifle = new WeaponFrameData();
        rifle.id = "rifle";
        rifle.category = WeaponCategory.RIFLE;
        rifle.baseDamage = 25f;
        rifle.baseFireRate = 8f;
        rifle.magSize = 30;
        rifle.firingMode = FiringMode.AUTO;
        rifle.hitscan = true;
        rifle.range = 100f;
        rifle.reloadTime = 2f;
        weaponData.registerFrame(rifle);

        BarrelData barrel = new BarrelData();
        barrel.id = "standard";
        weaponData.registerBarrel(barrel);

        AmmoTypeData ammo = new AmmoTypeData();
        ammo.id = "standard";
        ammo.damageType = DamageType.BALLISTIC;
        ammo.damageMultiplier = 1f;
        weaponData.registerAmmoType(ammo);

        QualityTierData q = new QualityTierData();
        q.tier = QualityTier.COMMON;
        q.globalMultiplier = 1f;
        weaponData.registerQuality(q);

        MeleeFrameData blade = new MeleeFrameData();
        blade.id = "blade";
        blade.category = MeleeCategory.BLADE;
        blade.baseDamage = 40f;
        blade.baseSpeed = 1.2f;
        blade.baseReach = 1.5f;
        blade.baseBlockEfficiency = 0.7f;
        blade.damageType = DamageType.MELEE;
        blade.weightClass = WeightClass.LIGHT;
        blade.directionalModifiers.put("overhead", 1.3f);
        weaponData.registerMeleeFrame(blade);

        engine = new Engine();
        engine.addSystem(new WeaponSwitchSystem(eventBus, weaponData));

        player = new Entity();
        input = new CombatInputComponent();
        inv = new WeaponInventoryComponent();
        inv.slots[0] = WeaponAssembly.ranged("rifle", "standard", "standard", null, QualityTier.COMMON);
        inv.slots[2] = WeaponAssembly.melee("blade", QualityTier.COMMON);
        inv.activeSlotIndex = 0;

        player.add(input);
        player.add(inv);
        player.add(new RangedWeaponComponent());
        player.add(new MeleeWeaponComponent());
        engine.addEntity(player);
    }

    @Test
    void switchRequestStartsSwitching() {
        input.switchSlotRequested = 2;
        engine.update(0.016f);
        assertTrue(inv.switching);
    }

    @Test
    void switchCompletesAfterTimer() {
        boolean[] switched = {false};
        eventBus.subscribe(WeaponSwitchedEvent.class, e -> switched[0] = true);

        input.switchSlotRequested = 2;
        engine.update(0.016f);
        // Melee switch time = 0.2s
        engine.update(0.1f);
        engine.update(0.11f);
        assertTrue(switched[0]);
        assertEquals(2, inv.activeSlotIndex);
        assertFalse(inv.switching);
    }

    @Test
    void switchToSameSlotIgnored() {
        input.switchSlotRequested = 0;
        engine.update(0.016f);
        assertFalse(inv.switching);
    }

    @Test
    void switchCancelsReload() {
        RangedWeaponComponent weapon = player.getComponent(RangedWeaponComponent.class);
        weapon.reloading = true;
        weapon.reloadTimer = 1f;

        input.switchSlotRequested = 2;
        engine.update(0.016f);
        assertFalse(weapon.reloading);
    }
}
```

- [ ] **Step 4: Implement WeaponSwitchSystem**

`WeaponSwitchSystem.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.*;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.WeaponSlot;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.WeaponAssembly;
import com.galacticodyssey.combat.data.WeaponDataRegistry;
import com.galacticodyssey.combat.data.WeaponStatsResolver;
import com.galacticodyssey.combat.events.WeaponSwitchedEvent;
import com.galacticodyssey.core.EventBus;

public class WeaponSwitchSystem extends IteratingSystem {
    private final EventBus eventBus;
    private final WeaponDataRegistry weaponData;
    private final ComponentMapper<CombatInputComponent> inputMapper = ComponentMapper.getFor(CombatInputComponent.class);
    private final ComponentMapper<WeaponInventoryComponent> invMapper = ComponentMapper.getFor(WeaponInventoryComponent.class);
    private final ComponentMapper<RangedWeaponComponent> rangedMapper = ComponentMapper.getFor(RangedWeaponComponent.class);
    private final ComponentMapper<MeleeWeaponComponent> meleeMapper = ComponentMapper.getFor(MeleeWeaponComponent.class);

    public WeaponSwitchSystem(EventBus eventBus, WeaponDataRegistry weaponData) {
        super(Family.all(CombatInputComponent.class, WeaponInventoryComponent.class).get(), 3);
        this.eventBus = eventBus;
        this.weaponData = weaponData;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CombatInputComponent input = inputMapper.get(entity);
        WeaponInventoryComponent inv = invMapper.get(entity);

        if (inv.switching) {
            inv.switchTimer -= deltaTime;
            if (inv.switchTimer <= 0) {
                inv.switching = false;
                resolveActiveWeapon(entity, inv);
                WeaponSlot newSlot = inv.getActiveSlot();
                eventBus.publish(new WeaponSwitchedEvent(entity, null, newSlot));
            }
            return;
        }

        int requested = input.switchSlotRequested;
        if (requested >= 0 && requested < 3 && requested != inv.activeSlotIndex) {
            if (inv.slots[requested] == null) {
                input.switchSlotRequested = -1;
                return;
            }

            RangedWeaponComponent ranged = rangedMapper.get(entity);
            if (ranged != null) {
                ranged.reloading = false;
            }

            WeaponSlot targetSlot = WeaponSlot.values()[requested];
            inv.activeSlotIndex = requested;
            inv.switching = true;
            inv.switchTimer = targetSlot.switchTime;
        }
        input.switchSlotRequested = -1;
    }

    private void resolveActiveWeapon(Entity entity, WeaponInventoryComponent inv) {
        WeaponAssembly assembly = inv.getActiveAssembly();
        if (assembly == null) return;

        if (assembly.isMelee) {
            MeleeWeaponComponent melee = meleeMapper.get(entity);
            if (melee != null) {
                WeaponStatsResolver.MeleeStats stats = WeaponStatsResolver.resolveMelee(assembly, weaponData);
                melee.baseDamage = stats.baseDamage;
                melee.reach = stats.reach;
                melee.swingSpeed = stats.speed;
                melee.blockEfficiency = stats.blockEfficiency;
                melee.damageType = stats.damageType;
                melee.weightClass = stats.weightClass;
                melee.directionalModifiers.clear();
                for (var entry : stats.directionalModifiers.entrySet()) {
                    try {
                        AttackDirection dir = AttackDirection.valueOf(entry.getKey().toUpperCase());
                        melee.directionalModifiers.put(dir, entry.getValue());
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } else {
            RangedWeaponComponent ranged = rangedMapper.get(entity);
            if (ranged != null) {
                WeaponStatsResolver.RangedStats stats = WeaponStatsResolver.resolveRanged(assembly, weaponData);
                ranged.damage = stats.damage;
                ranged.fireRate = stats.fireRate;
                ranged.spread = stats.spread;
                ranged.range = stats.range;
                ranged.recoil = stats.recoil;
                ranged.magSize = stats.magSize;
                ranged.currentAmmo = stats.magSize;
                ranged.reloadTime = stats.reloadTime;
                ranged.firingMode = stats.firingMode;
                ranged.hitscan = stats.hitscan;
                ranged.damageType = stats.damageType;
                ranged.statusEffect = stats.statusEffect;
                ranged.statusEffectChance = stats.statusEffectChance;
                ranged.projectileSpeed = stats.projectileSpeed;
                ranged.ammoTypeId = stats.ammoTypeId;
                ranged.reloading = false;
                ranged.fireTimer = 0;
            }
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.systems.WeaponSystemTest" --tests "com.galacticodyssey.combat.systems.WeaponSwitchSystemTest"`
Expected: 9 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/systems/WeaponSystem.java core/src/main/java/com/galacticodyssey/combat/systems/WeaponSwitchSystem.java core/src/test/java/com/galacticodyssey/combat/systems/
git commit -m "feat(combat): add WeaponSystem and WeaponSwitchSystem"
```

---

### Task 8: CombatInputSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/systems/CombatInputSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/systems/CombatInputSystemTest.java`
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java` — expose fire/reload/melee/block keybinds

- [ ] **Step 1: Write CombatInputSystem test**

`CombatInputSystemTest.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatInputSystemTest {
    private Engine engine;
    private CombatInputSystem system;
    private Entity player;
    private CombatInputComponent combatInput;

    @BeforeEach
    void setUp() {
        system = new CombatInputSystem();
        engine = new Engine();
        engine.addSystem(system);

        player = new Entity();
        combatInput = new CombatInputComponent();
        player.add(combatInput);
        player.add(new FPSCameraComponent());
        player.add(new TransformComponent());
        player.add(new com.galacticodyssey.player.components.PlayerTagComponent());
        engine.addEntity(player);
    }

    @Test
    void aimDirectionDerivedFromCameraAngles() {
        FPSCameraComponent cam = player.getComponent(FPSCameraComponent.class);
        cam.yaw = 0f;
        cam.pitch = 0f;

        engine.update(0.016f);

        // Looking forward at yaw=0, pitch=0: aim should be approximately (0, 0, -1)
        assertEquals(0f, combatInput.aimDirection.x, 0.1f);
        assertEquals(0f, combatInput.aimDirection.y, 0.1f);
        assertTrue(combatInput.aimDirection.z < 0);
    }

    @Test
    void meleeDirectionFromMouseDelta() {
        system.setMouseDeltaForMelee(50f, 0f);
        system.setMeleeAttackInput(true);
        engine.update(0.016f);

        assertEquals(AttackDirection.RIGHT, combatInput.meleeAttackDirection);
        assertTrue(combatInput.meleeAttackRequested);
    }
}
```

- [ ] **Step 2: Implement CombatInputSystem**

`CombatInputSystem.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.PlayerTagComponent;

public class CombatInputSystem extends IteratingSystem {
    private final ComponentMapper<CombatInputComponent> combatMapper = ComponentMapper.getFor(CombatInputComponent.class);
    private final ComponentMapper<FPSCameraComponent> cameraMapper = ComponentMapper.getFor(FPSCameraComponent.class);

    private boolean fireInput;
    private boolean fireHeldInput;
    private boolean reloadInput;
    private boolean meleeAttackInput;
    private boolean blockInput;
    private boolean blockHeldInput;
    private boolean quickMeleeInput;
    private int switchSlotInput = -1;
    private float meleeMouseDeltaX;
    private float meleeMouseDeltaY;

    public CombatInputSystem() {
        super(Family.all(CombatInputComponent.class, FPSCameraComponent.class, PlayerTagComponent.class).get(), 1);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CombatInputComponent combat = combatMapper.get(entity);
        FPSCameraComponent camera = cameraMapper.get(entity);

        float yawRad = camera.yaw * MathUtils.degreesToRadians;
        float pitchRad = camera.pitch * MathUtils.degreesToRadians;
        combat.aimDirection.set(
            MathUtils.sin(yawRad) * MathUtils.cos(pitchRad),
            MathUtils.sin(pitchRad),
            -MathUtils.cos(yawRad) * MathUtils.cos(pitchRad)
        ).nor();

        combat.fireRequested = fireInput;
        combat.fireHeld = fireHeldInput;
        combat.reloadRequested = reloadInput;
        combat.quickMeleeRequested = quickMeleeInput;
        combat.switchSlotRequested = switchSlotInput;
        combat.blockRequested = blockInput;
        combat.blockHeld = blockHeldInput;

        if (meleeAttackInput) {
            combat.meleeAttackRequested = true;
            combat.meleeAttackDirection = resolveDirection(meleeMouseDeltaX, meleeMouseDeltaY);
        } else {
            combat.meleeAttackRequested = false;
        }

        if (blockHeldInput) {
            combat.blockDirection = resolveDirection(meleeMouseDeltaX, meleeMouseDeltaY);
        }

        fireInput = false;
        reloadInput = false;
        meleeAttackInput = false;
        blockInput = false;
        quickMeleeInput = false;
        switchSlotInput = -1;
    }

    private AttackDirection resolveDirection(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? AttackDirection.RIGHT : AttackDirection.LEFT;
        } else {
            return dy > 0 ? AttackDirection.OVERHEAD : AttackDirection.THRUST;
        }
    }

    public void setFireInput(boolean pressed) { this.fireInput = pressed; }
    public void setFireHeldInput(boolean held) { this.fireHeldInput = held; }
    public void setReloadInput() { this.reloadInput = true; }
    public void setMeleeAttackInput(boolean pressed) { this.meleeAttackInput = pressed; }
    public void setBlockInput(boolean pressed) { this.blockInput = pressed; }
    public void setBlockHeldInput(boolean held) { this.blockHeldInput = held; }
    public void setQuickMeleeInput() { this.quickMeleeInput = true; }
    public void setSwitchSlotInput(int slot) { this.switchSlotInput = slot; }
    public void setMouseDeltaForMelee(float dx, float dy) { this.meleeMouseDeltaX = dx; this.meleeMouseDeltaY = dy; }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.systems.CombatInputSystemTest"`
Expected: 2 tests PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/systems/CombatInputSystem.java core/src/test/java/com/galacticodyssey/combat/systems/CombatInputSystemTest.java
git commit -m "feat(combat): add CombatInputSystem with directional melee input"
```

---

### Task 9: HitscanSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/systems/HitscanSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/systems/HitscanSystemTest.java`

- [ ] **Step 1: Write HitscanSystem test**

`HitscanSystemTest.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.events.HitscanHitEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HitscanSystemTest {
    private EventBus eventBus;
    private Engine engine;
    private Entity shooter;
    private Entity target;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new HitscanSystem(eventBus));

        shooter = new Entity();
        TransformComponent shooterT = new TransformComponent();
        shooterT.position.set(0, 1, 0);
        shooter.add(shooterT);

        RangedWeaponComponent weapon = new RangedWeaponComponent();
        weapon.damage = 25f;
        weapon.range = 100f;
        weapon.spread = 0f;
        weapon.hitscan = true;
        weapon.damageType = DamageType.BALLISTIC;
        weapon.ammoTypeId = "standard";
        shooter.add(weapon);
        engine.addEntity(shooter);

        target = new Entity();
        TransformComponent targetT = new TransformComponent();
        targetT.position.set(0, 1, -10);
        target.add(targetT);
        target.add(new HitboxComponent());
        target.add(new HealthComponent());
        engine.addEntity(target);
    }

    @Test
    void hitscanHitPublishedForTargetInRange() {
        boolean[] hit = {false};
        eventBus.subscribe(HitscanHitEvent.class, e -> {
            hit[0] = true;
            assertEquals(25f, e.damage, 0.01f);
            assertEquals(DamageType.BALLISTIC, e.damageType);
        });

        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0, 0, -1), true));
        assertTrue(hit[0]);
    }

    @Test
    void noHitWhenTargetOutOfRange() {
        target.getComponent(TransformComponent.class).position.set(0, 1, -200);

        boolean[] hit = {false};
        eventBus.subscribe(HitscanHitEvent.class, e -> hit[0] = true);

        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0, 0, -1), true));
        assertFalse(hit[0]);
    }

    @Test
    void projectileWeaponIgnoredByHitscan() {
        boolean[] hit = {false};
        eventBus.subscribe(HitscanHitEvent.class, e -> hit[0] = true);

        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0, 0, -1), false));
        assertFalse(hit[0]);
    }
}
```

- [ ] **Step 2: Implement HitscanSystem**

`HitscanSystem.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.events.HitscanHitEvent;
import com.galacticodyssey.combat.events.RecoilEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

public class HitscanSystem extends EntitySystem {
    private final EventBus eventBus;
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<RangedWeaponComponent> weaponMapper = ComponentMapper.getFor(RangedWeaponComponent.class);
    private final ComponentMapper<HitboxComponent> hitboxMapper = ComponentMapper.getFor(HitboxComponent.class);
    private final ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);
    private Engine engineRef;

    private final Vector3 tmpRayStart = new Vector3();
    private final Vector3 tmpRayDir = new Vector3();
    private final Vector3 tmpDiff = new Vector3();

    public HitscanSystem(EventBus eventBus) {
        super(6);
        this.eventBus = eventBus;
        eventBus.subscribe(WeaponFiredEvent.class, this::onWeaponFired);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engineRef = engine;
    }

    private void onWeaponFired(WeaponFiredEvent event) {
        if (!event.hitscan) return;
        if (engineRef == null) return;

        TransformComponent shooterTransform = transformMapper.get(event.shooter);
        RangedWeaponComponent weapon = weaponMapper.get(event.shooter);
        if (shooterTransform == null || weapon == null) return;

        tmpRayStart.set(shooterTransform.position);
        tmpRayDir.set(event.aimDirection).nor();

        // Apply spread
        if (weapon.spread > 0) {
            float spreadRad = weapon.spread * com.badlogic.gdx.math.MathUtils.degreesToRadians * 0.01f;
            tmpRayDir.x += com.badlogic.gdx.math.MathUtils.random(-spreadRad, spreadRad);
            tmpRayDir.y += com.badlogic.gdx.math.MathUtils.random(-spreadRad, spreadRad);
            tmpRayDir.nor();
        }

        // Publish recoil
        if (weapon.recoilPattern != null && weapon.recoilPattern.length > 0) {
            int idx = weapon.recoilIndex % weapon.recoilPattern.length;
            eventBus.publish(new RecoilEvent(event.shooter, weapon.recoilPattern[idx]));
        } else if (weapon.recoil > 0) {
            eventBus.publish(new RecoilEvent(event.shooter,
                new com.badlogic.gdx.math.Vector2(
                    com.badlogic.gdx.math.MathUtils.random(-weapon.recoil * 0.3f, weapon.recoil * 0.3f),
                    weapon.recoil)));
        }

        // Simple entity-based raycast (no Bullet physics raycast needed for basic implementation)
        Entity closestHit = null;
        float closestDist = weapon.range;
        float hitHeightRatio = 0.5f;

        for (Entity entity : engineRef.getEntities()) {
            if (entity == event.shooter) continue;
            HitboxComponent hitbox = hitboxMapper.get(entity);
            HealthComponent health = healthMapper.get(entity);
            if (hitbox == null || health == null || !health.alive) continue;

            TransformComponent targetTransform = transformMapper.get(entity);
            if (targetTransform == null) continue;

            tmpDiff.set(targetTransform.position).sub(tmpRayStart);
            float dot = tmpDiff.dot(tmpRayDir);
            if (dot <= 0 || dot > closestDist) continue;

            Vector3 closest = new Vector3(tmpRayDir).scl(dot).add(tmpRayStart);
            float perpDist = closest.dst(targetTransform.position);

            float hitRadius = 0.5f;
            if (perpDist <= hitRadius) {
                closestDist = dot;
                closestHit = entity;
                hitHeightRatio = (closest.y - (targetTransform.position.y - hitbox.bodyHeight * 0.5f)) / hitbox.bodyHeight;
                hitHeightRatio = com.badlogic.gdx.math.MathUtils.clamp(hitHeightRatio, 0f, 1f);
            }
        }

        if (closestHit != null) {
            HitboxComponent hitbox = hitboxMapper.get(closestHit);
            com.galacticodyssey.combat.CombatEnums.HitRegion region = hitbox.getRegionForHeight(hitHeightRatio);
            Vector3 hitPoint = new Vector3(tmpRayDir).scl(closestDist).add(tmpRayStart);

            eventBus.publish(new HitscanHitEvent(
                event.shooter, closestHit, hitPoint, new Vector3(tmpRayDir).scl(-1),
                region, weapon.damage, weapon.damageType, weapon.ammoTypeId));
        }
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.systems.HitscanSystemTest"`
Expected: 3 tests PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/systems/HitscanSystem.java core/src/test/java/com/galacticodyssey/combat/systems/HitscanSystemTest.java
git commit -m "feat(combat): add HitscanSystem with entity raycast and recoil"
```

---

### Task 10: ProjectileSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/systems/ProjectileSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/systems/ProjectileSystemTest.java`

- [ ] **Step 1: Write ProjectileSystem test**

`ProjectileSystemTest.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.events.ProjectileHitEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileSystemTest {
    private EventBus eventBus;
    private Engine engine;
    private Entity shooter;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new ProjectileSystem(eventBus));

        shooter = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(0, 1, 0);
        shooter.add(t);

        RangedWeaponComponent weapon = new RangedWeaponComponent();
        weapon.damage = 35f;
        weapon.range = 80f;
        weapon.hitscan = false;
        weapon.damageType = DamageType.PLASMA;
        weapon.projectileSpeed = 40f;
        weapon.ammoTypeId = "plasma_cell";
        shooter.add(weapon);
        engine.addEntity(shooter);
    }

    @Test
    void projectileEntityCreatedOnFire() {
        int entitiesBefore = engine.getEntities().size();
        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0, 0, -1), false));
        assertEquals(entitiesBefore + 1, engine.getEntities().size());
    }

    @Test
    void projectileMovesForward() {
        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0, 0, -1), false));

        Entity projectile = null;
        for (Entity e : engine.getEntities()) {
            if (e.getComponent(ProjectileComponent.class) != null) {
                projectile = e;
                break;
            }
        }
        assertNotNull(projectile);

        TransformComponent pt = projectile.getComponent(TransformComponent.class);
        float initialZ = pt.position.z;
        engine.update(0.1f);
        assertTrue(pt.position.z < initialZ);
    }

    @Test
    void projectileExpiresAfterLifetime() {
        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0, 0, -1), false));

        // Lifetime = range / speed = 80 / 40 = 2 seconds
        engine.update(2.1f);

        boolean found = false;
        for (Entity e : engine.getEntities()) {
            if (e.getComponent(ProjectileComponent.class) != null) found = true;
        }
        assertFalse(found);
    }

    @Test
    void projectileHitsTarget() {
        Entity target = new Entity();
        TransformComponent tt = new TransformComponent();
        tt.position.set(0, 1, -5);
        target.add(tt);
        target.add(new HitboxComponent());
        HealthComponent h = new HealthComponent();
        h.currentHP = 100f;
        target.add(h);
        engine.addEntity(target);

        boolean[] hit = {false};
        eventBus.subscribe(ProjectileHitEvent.class, e -> hit[0] = true);

        eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0, 0, -1), false));

        // Advance enough for projectile to reach target (5 units at 40 units/s = 0.125s)
        for (int i = 0; i < 10; i++) engine.update(0.016f);
        assertTrue(hit[0]);
    }
}
```

- [ ] **Step 2: Implement ProjectileSystem**

`ProjectileSystem.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.events.ProjectileHitEvent;
import com.galacticodyssey.combat.events.WeaponFiredEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

public class ProjectileSystem extends IteratingSystem {
    private final EventBus eventBus;
    private Engine engineRef;
    private final Array<Entity> toRemove = new Array<>();
    private final Vector3 tmpVelocity = new Vector3();
    private final Vector3 tmpDiff = new Vector3();

    private final ComponentMapper<ProjectileComponent> projMapper = ComponentMapper.getFor(ProjectileComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<HitboxComponent> hitboxMapper = ComponentMapper.getFor(HitboxComponent.class);
    private final ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);

    public ProjectileSystem(EventBus eventBus) {
        super(Family.all(ProjectileComponent.class, TransformComponent.class).get(), 7);
        this.eventBus = eventBus;
        eventBus.subscribe(WeaponFiredEvent.class, this::onWeaponFired);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engineRef = engine;
    }

    private void onWeaponFired(WeaponFiredEvent event) {
        if (event.hitscan) return;
        if (engineRef == null) return;

        RangedWeaponComponent weapon = event.shooter.getComponent(RangedWeaponComponent.class);
        TransformComponent shooterT = event.shooter.getComponent(TransformComponent.class);
        if (weapon == null || shooterT == null) return;

        Entity projectile = new Entity();

        TransformComponent t = new TransformComponent();
        t.position.set(shooterT.position);
        projectile.add(t);

        ProjectileComponent p = new ProjectileComponent();
        p.speed = weapon.projectileSpeed != null ? weapon.projectileSpeed : 30f;
        p.damage = weapon.damage;
        p.damageType = weapon.damageType;
        p.owner = event.shooter;
        p.lifetime = weapon.range / p.speed;
        p.ammoTypeId = weapon.ammoTypeId;
        projectile.add(p);

        // Store direction in rotation (reuse quaternion w,x,y for direction)
        t.rotation.set(event.aimDirection.x, event.aimDirection.y, event.aimDirection.z, 0);

        engineRef.addEntity(projectile);
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        for (Entity e : toRemove) {
            engineRef.removeEntity(e);
        }
        toRemove.clear();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ProjectileComponent proj = projMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);

        proj.age += deltaTime;
        if (proj.age >= proj.lifetime) {
            toRemove.add(entity);
            return;
        }

        // Move projectile
        tmpVelocity.set(transform.rotation.x, transform.rotation.y, transform.rotation.z)
            .nor().scl(proj.speed * deltaTime);
        transform.position.add(tmpVelocity);

        // Check collision with entities
        for (Entity other : engineRef.getEntities()) {
            if (other == entity || other == proj.owner) continue;
            HitboxComponent hitbox = hitboxMapper.get(other);
            HealthComponent health = healthMapper.get(other);
            if (hitbox == null || health == null || !health.alive) continue;

            TransformComponent otherT = transformMapper.get(other);
            if (otherT == null) continue;

            float dist = transform.position.dst(otherT.position);
            if (dist <= 1.0f) {
                eventBus.publish(new ProjectileHitEvent(
                    proj.owner, other, new Vector3(transform.position),
                    proj.damage, proj.damageType, proj.areaOfEffect, proj.ammoTypeId));
                toRemove.add(entity);
                return;
            }
        }
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.systems.ProjectileSystemTest"`
Expected: 4 tests PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/systems/ProjectileSystem.java core/src/test/java/com/galacticodyssey/combat/systems/ProjectileSystemTest.java
git commit -m "feat(combat): add ProjectileSystem with pooled entities and collision"
```

---

### Task 11: MeleeSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/systems/MeleeSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/systems/MeleeSystemTest.java`

- [ ] **Step 1: Write MeleeSystem test**

`MeleeSystemTest.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.CombatDataRegistry;
import com.galacticodyssey.combat.data.DamageConfigData;
import com.galacticodyssey.combat.events.MeleeBlockEvent;
import com.galacticodyssey.combat.events.MeleeHitEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeSystemTest {
    private Engine engine;
    private EventBus eventBus;
    private Entity attacker;
    private Entity target;
    private CombatInputComponent attackerInput;
    private MeleeStateComponent attackerState;
    private MeleeWeaponComponent attackerWeapon;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        CombatDataRegistry combatData = new CombatDataRegistry();
        combatData.setDamageConfig(new DamageConfigData());

        engine = new Engine();
        engine.addSystem(new MeleeSystem(eventBus, combatData));

        attacker = new Entity();
        attackerInput = new CombatInputComponent();
        attackerState = new MeleeStateComponent();
        attackerWeapon = new MeleeWeaponComponent();
        attackerWeapon.baseDamage = 40f;
        attackerWeapon.reach = 2f;
        attackerWeapon.weightClass = WeightClass.LIGHT;
        attackerWeapon.damageType = DamageType.MELEE;
        attackerWeapon.directionalModifiers.put(AttackDirection.OVERHEAD, 1.3f);

        TransformComponent at = new TransformComponent();
        at.position.set(0, 0, 0);
        MovementStateComponent movement = new MovementStateComponent();

        WeaponInventoryComponent inv = new WeaponInventoryComponent();
        inv.activeSlotIndex = WeaponSlot.MELEE.index;

        attacker.add(attackerInput);
        attacker.add(attackerState);
        attacker.add(attackerWeapon);
        attacker.add(at);
        attacker.add(movement);
        attacker.add(inv);
        engine.addEntity(attacker);

        target = new Entity();
        TransformComponent tt = new TransformComponent();
        tt.position.set(0, 0, -1.5f);
        target.add(tt);
        target.add(new HitboxComponent());
        target.add(new HealthComponent());
        engine.addEntity(target);
    }

    @Test
    void attackTransitionsToWindUp() {
        attackerInput.meleeAttackRequested = true;
        attackerInput.meleeAttackDirection = AttackDirection.OVERHEAD;
        engine.update(0.016f);

        assertEquals(MeleeState.WIND_UP, attackerState.currentState);
        assertEquals(AttackDirection.OVERHEAD, attackerState.attackDirection);
    }

    @Test
    void windUpTransitionsToActive() {
        attackerState.currentState = MeleeState.WIND_UP;
        attackerState.stateTimer = 0.01f;
        engine.update(0.02f);

        assertEquals(MeleeState.ACTIVE, attackerState.currentState);
    }

    @Test
    void activePhasePublishesMeleeHit() {
        boolean[] hit = {false};
        eventBus.subscribe(MeleeHitEvent.class, e -> {
            hit[0] = true;
            assertEquals(DamageType.MELEE, e.damageType);
        });

        attackerState.currentState = MeleeState.ACTIVE;
        attackerState.stateTimer = WeightClass.LIGHT.activeTime;
        attackerState.attackDirection = AttackDirection.OVERHEAD;

        // Aim toward target
        attackerInput.aimDirection.set(0, 0, -1);
        engine.update(0.016f);
        assertTrue(hit[0]);
    }

    @Test
    void blockPublishesBlockEvent() {
        MeleeStateComponent targetState = new MeleeStateComponent();
        targetState.currentState = MeleeState.BLOCKING;
        targetState.blockDirection = AttackDirection.OVERHEAD;
        target.add(targetState);
        target.add(new MeleeWeaponComponent());
        target.add(new CombatInputComponent());
        target.add(new MovementStateComponent());
        WeaponInventoryComponent tinv = new WeaponInventoryComponent();
        tinv.activeSlotIndex = WeaponSlot.MELEE.index;
        target.add(tinv);

        boolean[] blocked = {false};
        eventBus.subscribe(MeleeBlockEvent.class, e -> {
            blocked[0] = true;
            assertTrue(e.perfectBlock);
        });

        attackerState.currentState = MeleeState.ACTIVE;
        attackerState.stateTimer = WeightClass.LIGHT.activeTime;
        attackerState.attackDirection = AttackDirection.OVERHEAD;
        attackerInput.aimDirection.set(0, 0, -1);
        engine.update(0.016f);
        assertTrue(blocked[0]);
    }

    @Test
    void exhaustionSlowsAttack() {
        MovementStateComponent movement = attacker.getComponent(MovementStateComponent.class);
        movement.stamina = 0f;
        movement.isExhausted = true;

        attackerInput.meleeAttackRequested = true;
        attackerInput.meleeAttackDirection = AttackDirection.LEFT;
        engine.update(0.016f);

        assertEquals(MeleeState.WIND_UP, attackerState.currentState);
        // With 40% penalty, wind-up for LIGHT should be 0.15 / 0.6 = 0.25
        assertTrue(attackerState.stateTimer > WeightClass.LIGHT.windUpTime);
    }

    @Test
    void comboAllowedOnDifferentDirection() {
        attackerState.currentState = MeleeState.RECOVERY;
        attackerState.stateTimer = 0.1f;
        attackerState.attackDirection = AttackDirection.LEFT;
        attackerState.comboCounter = 1;
        attackerState.canCombo = true;

        attackerInput.meleeAttackRequested = true;
        attackerInput.meleeAttackDirection = AttackDirection.RIGHT;
        engine.update(0.016f);

        assertEquals(AttackDirection.RIGHT, attackerState.queuedDirection);
    }

    @Test
    void sameDirectionComboRejected() {
        attackerState.currentState = MeleeState.RECOVERY;
        attackerState.stateTimer = 0.1f;
        attackerState.attackDirection = AttackDirection.LEFT;
        attackerState.canCombo = true;

        attackerInput.meleeAttackRequested = true;
        attackerInput.meleeAttackDirection = AttackDirection.LEFT;
        engine.update(0.016f);

        assertNull(attackerState.queuedDirection);
    }
}
```

- [ ] **Step 2: Implement MeleeSystem**

`MeleeSystem.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.CombatDataRegistry;
import com.galacticodyssey.combat.data.DamageConfigData;
import com.galacticodyssey.combat.events.MeleeBlockEvent;
import com.galacticodyssey.combat.events.MeleeHitEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.MovementStateComponent;

public class MeleeSystem extends IteratingSystem {
    private final EventBus eventBus;
    private final CombatDataRegistry combatData;
    private Engine engineRef;
    private final Vector3 tmpDir = new Vector3();

    private final ComponentMapper<CombatInputComponent> inputMapper = ComponentMapper.getFor(CombatInputComponent.class);
    private final ComponentMapper<MeleeStateComponent> stateMapper = ComponentMapper.getFor(MeleeStateComponent.class);
    private final ComponentMapper<MeleeWeaponComponent> weaponMapper = ComponentMapper.getFor(MeleeWeaponComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<WeaponInventoryComponent> invMapper = ComponentMapper.getFor(WeaponInventoryComponent.class);
    private final ComponentMapper<MovementStateComponent> movementMapper = ComponentMapper.getFor(MovementStateComponent.class);
    private final ComponentMapper<HitboxComponent> hitboxMapper = ComponentMapper.getFor(HitboxComponent.class);
    private final ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);

    public MeleeSystem(EventBus eventBus, CombatDataRegistry combatData) {
        super(Family.all(MeleeStateComponent.class, MeleeWeaponComponent.class,
            CombatInputComponent.class, TransformComponent.class, WeaponInventoryComponent.class).get(), 5);
        this.eventBus = eventBus;
        this.combatData = combatData;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engineRef = engine;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CombatInputComponent input = inputMapper.get(entity);
        MeleeStateComponent state = stateMapper.get(entity);
        MeleeWeaponComponent weapon = weaponMapper.get(entity);
        WeaponInventoryComponent inv = invMapper.get(entity);
        MovementStateComponent movement = movementMapper.get(entity);

        if (!inv.isActiveSlotMelee() && state.currentState == MeleeState.IDLE) return;

        DamageConfigData config = combatData.getDamageConfig();
        float exhaustionPenalty = (movement != null && movement.isExhausted) ? config.exhaustionAttackPenalty : 0f;
        float speedMult = 1f / (1f - exhaustionPenalty);

        switch (state.currentState) {
            case IDLE:
                if (input.blockHeld && inv.isActiveSlotMelee()) {
                    state.currentState = MeleeState.BLOCKING;
                    state.blockDirection = input.blockDirection != null ? input.blockDirection : AttackDirection.LEFT;
                } else if (input.meleeAttackRequested && inv.isActiveSlotMelee()) {
                    if (movement != null && movement.stamina <= 0 && movement.isExhausted) {
                        // Can still attack but slower
                    }
                    startAttack(state, input.meleeAttackDirection, weapon, speedMult);
                    if (movement != null) {
                        float cost = weapon.staminaCosts.getOrDefault(input.meleeAttackDirection, 15f);
                        movement.stamina = Math.max(0, movement.stamina - cost);
                    }
                    state.comboCounter = 0;
                }
                break;

            case WIND_UP:
                state.stateTimer -= deltaTime;
                if (state.stateTimer <= 0) {
                    state.currentState = MeleeState.ACTIVE;
                    state.stateTimer = weapon.weightClass.activeTime * speedMult;
                }
                break;

            case ACTIVE:
                performHitDetection(entity, state, weapon, input);
                state.stateTimer -= deltaTime;
                if (state.stateTimer <= 0) {
                    state.currentState = MeleeState.RECOVERY;
                    state.stateTimer = weapon.weightClass.recoveryTime;
                    state.canCombo = state.comboCounter < config.maxComboHits - 1;
                }
                break;

            case RECOVERY:
                if (state.canCombo && input.meleeAttackRequested) {
                    if (input.meleeAttackDirection != state.attackDirection) {
                        state.queuedDirection = input.meleeAttackDirection;
                    }
                }
                state.stateTimer -= deltaTime;
                if (state.stateTimer <= 0) {
                    if (state.queuedDirection != null) {
                        state.comboCounter++;
                        startAttack(state, state.queuedDirection, weapon, speedMult);
                        if (movement != null) {
                            float cost = weapon.staminaCosts.getOrDefault(state.queuedDirection, 15f);
                            cost *= (1f + config.comboStaminaPenalty * state.comboCounter);
                            movement.stamina = Math.max(0, movement.stamina - cost);
                        }
                        state.queuedDirection = null;
                    } else {
                        state.currentState = MeleeState.IDLE;
                        state.comboCounter = 0;
                    }
                }
                break;

            case BLOCKING:
                if (input.blockDirection != null) {
                    state.blockDirection = input.blockDirection;
                }
                if (!input.blockHeld) {
                    state.currentState = MeleeState.IDLE;
                }
                break;

            case STAGGERED:
                state.stateTimer -= deltaTime;
                if (state.stateTimer <= 0) {
                    state.currentState = MeleeState.IDLE;
                }
                break;
        }

        input.meleeAttackRequested = false;
    }

    private void startAttack(MeleeStateComponent state, AttackDirection direction,
                             MeleeWeaponComponent weapon, float speedMult) {
        state.currentState = MeleeState.WIND_UP;
        state.attackDirection = direction != null ? direction : AttackDirection.LEFT;
        state.stateTimer = weapon.weightClass.windUpTime * speedMult;
        state.queuedDirection = null;
    }

    private void performHitDetection(Entity attacker, MeleeStateComponent state,
                                     MeleeWeaponComponent weapon, CombatInputComponent input) {
        if (engineRef == null) return;
        TransformComponent attackerT = transformMapper.get(attacker);
        DamageConfigData config = combatData.getDamageConfig();

        for (Entity other : engineRef.getEntities()) {
            if (other == attacker) continue;
            TransformComponent otherT = transformMapper.get(other);
            HitboxComponent hitbox = hitboxMapper.get(other);
            HealthComponent health = healthMapper.get(other);
            if (otherT == null || hitbox == null || health == null || !health.alive) continue;

            tmpDir.set(otherT.position).sub(attackerT.position);
            float dist = tmpDir.len();
            if (dist > weapon.reach) continue;

            // Check facing direction
            tmpDir.nor();
            float dot = tmpDir.dot(input.aimDirection);
            if (dot < 0.5f) continue;

            float dirMod = weapon.directionalModifiers.getOrDefault(state.attackDirection, 1.0f);
            float comboDamageBonus = 1f + (config.comboDamageBonus * state.comboCounter);
            float damage = weapon.baseDamage * dirMod * comboDamageBonus;

            // Check if target is blocking
            MeleeStateComponent targetState = stateMapper.get(other);
            if (targetState != null && targetState.currentState == MeleeState.BLOCKING) {
                boolean perfectBlock = targetState.blockDirection == state.attackDirection;
                eventBus.publish(new MeleeBlockEvent(attacker, other,
                    state.attackDirection, targetState.blockDirection, perfectBlock));

                if (perfectBlock) {
                    state.currentState = MeleeState.STAGGERED;
                    state.stateTimer = weapon.weightClass.staggerTime;
                    return;
                } else {
                    damage *= config.wrongBlockMitigation;
                }
            }

            float hitHeight = (otherT.position.y + hitbox.bodyHeight * 0.5f - attackerT.position.y) / hitbox.bodyHeight;
            HitRegion region = hitbox.getRegionForHeight(com.badlogic.gdx.math.MathUtils.clamp(hitHeight, 0, 1));

            eventBus.publish(new MeleeHitEvent(attacker, other, state.attackDirection,
                region, damage, weapon.damageType));

            state.currentState = MeleeState.RECOVERY;
            state.stateTimer = weapon.weightClass.recoveryTime;
            state.canCombo = state.comboCounter < config.maxComboHits - 1;
            return;
        }
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.systems.MeleeSystemTest"`
Expected: 7 tests PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/systems/MeleeSystem.java core/src/test/java/com/galacticodyssey/combat/systems/MeleeSystemTest.java
git commit -m "feat(combat): add MeleeSystem with FSM, directional attacks, blocking, combos"
```

---

### Task 12: Combat AI — Behavior Tree Tasks and CombatAISystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/ai/CombatBlackboard.java`
- Create: 20 BT task files in `core/src/main/java/com/galacticodyssey/combat/ai/tasks/`
- Create: `core/src/main/java/com/galacticodyssey/combat/systems/CombatAISystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/systems/CombatAISystemTest.java`

- [ ] **Step 1: Create CombatBlackboard**

`CombatBlackboard.java`:
```java
package com.galacticodyssey.combat.ai;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CoverPoint;

public class CombatBlackboard {
    public Entity self;
    public Entity target;
    public final Vector3 targetPosition = new Vector3();
    public final Vector3 moveTarget = new Vector3();
    public CoverPoint selectedCover;
    public final Vector3 retreatPoint = new Vector3();
    public boolean hasLineOfSight;
    public float distanceToTarget;
    public float selfHealthPercent;
}
```

- [ ] **Step 2: Create core BT task classes**

All BT tasks extend `com.badlogic.gdx.ai.btree.LeafTask<Entity>`. Here are the key ones:

`HasTargetCondition.java`:
```java
package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.combat.components.CombatAIComponent;

public class HasTargetCondition extends LeafTask<Entity> {
    private final ComponentMapper<CombatAIComponent> aiMapper = ComponentMapper.getFor(CombatAIComponent.class);

    @Override
    public Status execute() {
        CombatAIComponent ai = aiMapper.get(getObject());
        return ai != null && ai.currentTarget != null ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
```

`CriticalHealthCondition.java`:
```java
package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.combat.components.HealthComponent;

public class CriticalHealthCondition extends LeafTask<Entity> {
    private static final float CRITICAL_THRESHOLD = 0.2f;
    private final ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);

    @Override
    public Status execute() {
        HealthComponent health = healthMapper.get(getObject());
        if (health == null) return Status.FAILED;
        return (health.currentHP / health.maxHP) < CRITICAL_THRESHOLD ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
```

`ScanForThreatsTask.java`:
```java
package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerTagComponent;

public class ScanForThreatsTask extends LeafTask<Entity> {
    private Engine engine;
    private final ComponentMapper<CombatAIComponent> aiMapper = ComponentMapper.getFor(CombatAIComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PlayerTagComponent> playerMapper = ComponentMapper.getFor(PlayerTagComponent.class);
    private final ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);
    private final Vector3 tmpDir = new Vector3();

    public void setEngine(Engine engine) { this.engine = engine; }

    @Override
    public Status execute() {
        if (engine == null) return Status.FAILED;
        Entity self = getObject();
        CombatAIComponent ai = aiMapper.get(self);
        TransformComponent myTransform = transformMapper.get(self);
        if (ai == null || myTransform == null) return Status.FAILED;

        for (Entity entity : engine.getEntities()) {
            if (entity == self) continue;
            if (playerMapper.get(entity) == null) continue;
            HealthComponent health = healthMapper.get(entity);
            if (health == null || !health.alive) continue;

            TransformComponent targetT = transformMapper.get(entity);
            if (targetT == null) continue;

            float dist = myTransform.position.dst(targetT.position);
            if (dist > ai.aggroRange) continue;

            ai.currentTarget = entity;
            ai.lastKnownTargetPosition.set(targetT.position);
            ai.hasLastKnownPosition = true;
            return Status.SUCCEEDED;
        }
        return Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        ((ScanForThreatsTask) task).engine = this.engine;
        return task;
    }
}
```

`AimAndFireTask.java`:
```java
package com.galacticodyssey.combat.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.core.components.TransformComponent;

public class AimAndFireTask extends LeafTask<Entity> {
    private final ComponentMapper<CombatAIComponent> aiMapper = ComponentMapper.getFor(CombatAIComponent.class);
    private final ComponentMapper<CombatInputComponent> inputMapper = ComponentMapper.getFor(CombatInputComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
    private final Vector3 tmpDir = new Vector3();

    @Override
    public Status execute() {
        Entity self = getObject();
        CombatAIComponent ai = aiMapper.get(self);
        CombatInputComponent input = inputMapper.get(self);
        TransformComponent transform = transformMapper.get(self);
        if (ai == null || input == null || transform == null || ai.currentTarget == null) return Status.FAILED;

        TransformComponent targetT = transformMapper.get(ai.currentTarget);
        if (targetT == null) return Status.FAILED;

        tmpDir.set(targetT.position).sub(transform.position).nor();
        input.aimDirection.set(tmpDir);
        input.fireRequested = true;
        input.fireHeld = true;

        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
```

Create additional BT tasks following the same pattern. Each task is a `LeafTask<Entity>` that reads/writes components:

- `InWeaponRangeCondition` — checks `distToTarget <= engageRange`
- `InMeleeRangeCondition` — checks `distToTarget <= meleeReach`
- `HasLineOfSightCondition` — checks `hasLineOfSight` on blackboard (simplified: always true for now, Bullet raycast in future)
- `NoLineOfSightCondition` — negation of above
- `MeleeAttackTask` — sets `meleeAttackRequested=true` with direction countering target's block
- `SelectMeleeDirectionTask` — picks direction opposing target's current block
- `FindCoverTask` — finds nearest unoccupied CoverPoint
- `MoveToCoverTask` — moves entity toward selected cover point
- `InCoverCondition` — checks `CoverComponent.inCover`
- `PeekAndShootTask` — temporarily leaves cover, fires, returns
- `FindRetreatPointTask` — picks position away from threat
- `MoveToTask` — moves entity toward moveTarget
- `MoveToLastKnownTask` — moves toward `lastKnownTargetPosition`
- `SearchAreaTask` — wanders near last known position
- `PatrolTask` — follows waypoints
- `HealOrFleeTask` — retreat behavior at low health
- `ReportToSquadTask` — publishes `ThreatDetectedEvent`

(Each file follows the same LeafTask pattern shown above — approximately 15-30 lines each.)

- [ ] **Step 3: Implement CombatAISystem**

`CombatAISystem.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

public class CombatAISystem extends IteratingSystem {
    private final EventBus eventBus;
    private final ComponentMapper<CombatAIComponent> aiMapper = ComponentMapper.getFor(CombatAIComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);
    private final Vector3 tmpDir = new Vector3();

    public CombatAISystem(EventBus eventBus) {
        super(Family.all(CombatAIComponent.class, TransformComponent.class,
            HealthComponent.class, CombatInputComponent.class).get(), 10);
        this.eventBus = eventBus;

        eventBus.subscribe(EntityKilledEvent.class, this::onEntityKilled);
    }

    private void onEntityKilled(EntityKilledEvent event) {
        for (Entity entity : getEntities()) {
            CombatAIComponent ai = aiMapper.get(entity);
            if (ai.currentTarget == event.target) {
                ai.currentTarget = null;
                ai.hasLastKnownPosition = false;
            }
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CombatAIComponent ai = aiMapper.get(entity);
        HealthComponent health = healthMapper.get(entity);
        if (!health.alive) return;

        // Update target tracking
        if (ai.currentTarget != null) {
            HealthComponent targetHealth = healthMapper.get(ai.currentTarget);
            if (targetHealth == null || !targetHealth.alive) {
                ai.currentTarget = null;
                ai.hasLastKnownPosition = false;
            } else {
                TransformComponent targetT = transformMapper.get(ai.currentTarget);
                if (targetT != null) {
                    ai.lastKnownTargetPosition.set(targetT.position);
                    ai.hasLastKnownPosition = true;
                }
            }
        }

        // Step behavior tree
        if (ai.behaviorTree != null) {
            ai.behaviorTree.step();
        }
    }
}
```

- [ ] **Step 4: Write CombatAISystem test**

`CombatAISystemTest.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatAISystemTest {
    private Engine engine;
    private EventBus eventBus;
    private Entity npc;
    private CombatAIComponent ai;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new CombatAISystem(eventBus));

        npc = new Entity();
        ai = new CombatAIComponent();
        ai.aggroRange = 25f;
        npc.add(ai);
        npc.add(new TransformComponent());
        HealthComponent h = new HealthComponent();
        h.currentHP = 80f;
        npc.add(h);
        npc.add(new CombatInputComponent());
        engine.addEntity(npc);
    }

    @Test
    void clearsTargetWhenKilled() {
        Entity target = new Entity();
        target.add(new HealthComponent());
        target.add(new TransformComponent());
        engine.addEntity(target);

        ai.currentTarget = target;
        eventBus.publish(new EntityKilledEvent(target, npc));

        engine.update(0.016f);
        assertNull(ai.currentTarget);
    }

    @Test
    void updatesLastKnownPosition() {
        Entity target = new Entity();
        HealthComponent th = new HealthComponent();
        th.alive = true;
        target.add(th);
        TransformComponent tt = new TransformComponent();
        tt.position.set(10, 0, -5);
        target.add(tt);
        engine.addEntity(target);

        ai.currentTarget = target;
        engine.update(0.016f);

        assertEquals(10f, ai.lastKnownTargetPosition.x, 0.01f);
        assertTrue(ai.hasLastKnownPosition);
    }

    @Test
    void deadNPCSkipped() {
        npc.getComponent(HealthComponent.class).alive = false;
        ai.currentTarget = npc;
        engine.update(0.016f);
        // Should not crash
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.systems.CombatAISystemTest"`
Expected: 3 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/ai/ core/src/main/java/com/galacticodyssey/combat/systems/CombatAISystem.java core/src/test/java/com/galacticodyssey/combat/systems/CombatAISystemTest.java
git commit -m "feat(combat): add CombatAISystem with behavior tree infrastructure"
```

---

### Task 13: SquadTacticsSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/systems/SquadTacticsSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/systems/SquadTacticsSystemTest.java`

- [ ] **Step 1: Write SquadTacticsSystem test**

`SquadTacticsSystemTest.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.SquadRole;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.events.RetreatOrderEvent;
import com.galacticodyssey.combat.events.ThreatDetectedEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SquadTacticsSystemTest {
    private Engine engine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new SquadTacticsSystem(eventBus));
    }

    private Entity createSquadMember(int squadId, SquadRole role, boolean alive) {
        Entity e = new Entity();
        SquadComponent squad = new SquadComponent();
        squad.squadId = squadId;
        squad.role = role;
        e.add(squad);
        HealthComponent h = new HealthComponent();
        h.alive = alive;
        h.currentHP = alive ? 80f : 0f;
        e.add(h);
        e.add(new TransformComponent());
        CombatAIComponent ai = new CombatAIComponent();
        e.add(ai);
        engine.addEntity(e);
        return e;
    }

    @Test
    void retreatOrderWhenHalfSquadDead() {
        createSquadMember(1, SquadRole.LEADER, true);
        createSquadMember(1, SquadRole.FLANKER, true);
        createSquadMember(1, SquadRole.FLANKER, false);
        createSquadMember(1, SquadRole.SUPPRESSOR, false);

        boolean[] retreated = {false};
        eventBus.subscribe(RetreatOrderEvent.class, e -> {
            retreated[0] = true;
            assertEquals(1, e.squadId);
        });

        engine.update(0.016f);
        assertTrue(retreated[0]);
    }

    @Test
    void threatSharedWithSquad() {
        Entity leader = createSquadMember(1, SquadRole.LEADER, true);
        Entity flanker = createSquadMember(1, SquadRole.FLANKER, true);

        Entity target = new Entity();
        target.add(new TransformComponent());
        engine.addEntity(target);

        eventBus.publish(new ThreatDetectedEvent(leader, target, new Vector3(5, 0, 0), 1));
        engine.update(0.016f);

        CombatAIComponent flankerAI = flanker.getComponent(CombatAIComponent.class);
        assertNotNull(flankerAI.currentTarget);
    }
}
```

- [ ] **Step 2: Implement SquadTacticsSystem**

`SquadTacticsSystem.java`:
```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.IntMap;
import com.galacticodyssey.combat.CombatEnums.SquadRole;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.events.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import java.util.ArrayList;
import java.util.List;

public class SquadTacticsSystem extends EntitySystem {
    private final EventBus eventBus;
    private Engine engineRef;
    private final IntMap<Boolean> retreatPublished = new IntMap<>();

    private final ComponentMapper<SquadComponent> squadMapper = ComponentMapper.getFor(SquadComponent.class);
    private final ComponentMapper<HealthComponent> healthMapper = ComponentMapper.getFor(HealthComponent.class);
    private final ComponentMapper<CombatAIComponent> aiMapper = ComponentMapper.getFor(CombatAIComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper = ComponentMapper.getFor(TransformComponent.class);

    public SquadTacticsSystem(EventBus eventBus) {
        super(11);
        this.eventBus = eventBus;

        eventBus.subscribe(ThreatDetectedEvent.class, this::onThreatDetected);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engineRef = engine;
    }

    private void onThreatDetected(ThreatDetectedEvent event) {
        if (engineRef == null) return;
        for (Entity entity : engineRef.getEntities()) {
            SquadComponent squad = squadMapper.get(entity);
            if (squad == null || squad.squadId != event.squadId) continue;
            if (entity == event.detector) continue;

            CombatAIComponent ai = aiMapper.get(entity);
            if (ai != null && ai.currentTarget == null) {
                ai.currentTarget = event.threat;
                ai.lastKnownTargetPosition.set(event.position);
                ai.hasLastKnownPosition = true;
            }
        }
    }

    @Override
    public void update(float deltaTime) {
        if (engineRef == null) return;

        IntMap<List<Entity>> squads = new IntMap<>();
        for (Entity entity : engineRef.getEntities()) {
            SquadComponent squad = squadMapper.get(entity);
            if (squad == null) continue;
            if (!squads.containsKey(squad.squadId)) {
                squads.put(squad.squadId, new ArrayList<>());
            }
            squads.get(squad.squadId).add(entity);
        }

        for (IntMap.Entry<List<Entity>> entry : squads) {
            int squadId = entry.key;
            List<Entity> members = entry.value;

            int alive = 0, total = members.size();
            Vector3 avgPosition = new Vector3();
            for (Entity member : members) {
                HealthComponent health = healthMapper.get(member);
                if (health != null && health.alive) {
                    alive++;
                    TransformComponent t = transformMapper.get(member);
                    if (t != null) avgPosition.add(t.position);
                }
            }
            if (alive > 0) avgPosition.scl(1f / alive);

            // Retreat if >50% casualties
            if (alive > 0 && alive <= total / 2 && !retreatPublished.containsKey(squadId)) {
                Vector3 retreatDir = new Vector3(avgPosition).nor().scl(-20f).add(avgPosition);
                eventBus.publish(new RetreatOrderEvent(squadId, retreatDir));
                retreatPublished.put(squadId, true);
            }
        }
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.systems.SquadTacticsSystemTest"`
Expected: 2 tests PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/systems/SquadTacticsSystem.java core/src/test/java/com/galacticodyssey/combat/systems/SquadTacticsSystemTest.java
git commit -m "feat(combat): add SquadTacticsSystem with retreat and threat sharing"
```

---

### Task 14: CameraSystem Recoil Extension and GameWorld Integration

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java`
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java`

- [ ] **Step 1: Add recoil handling to CameraSystem**

Add to `CameraSystem.java` — subscribe to `RecoilEvent`, apply pitch/yaw offset, decay over time:

In the constructor (or an init method), add:
```java
private float recoilPitch;
private float recoilYaw;
private static final float RECOIL_RECOVERY_SPEED = 8f;

// In constructor or addedToEngine:
eventBus.subscribe(RecoilEvent.class, this::onRecoil);

private void onRecoil(RecoilEvent event) {
    // event.recoilOffset.x = yaw offset, event.recoilOffset.y = pitch offset
    recoilYaw += event.recoilOffset.x;
    recoilPitch += event.recoilOffset.y;
}
```

In `processEntity`, after existing pitch/yaw application:
```java
// Apply recoil
camera.pitch += recoilPitch;
camera.yaw += recoilYaw;

// Decay recoil
recoilPitch = recoilPitch * (1f - RECOIL_RECOVERY_SPEED * deltaTime);
recoilYaw = recoilYaw * (1f - RECOIL_RECOVERY_SPEED * deltaTime);
if (Math.abs(recoilPitch) < 0.01f) recoilPitch = 0;
if (Math.abs(recoilYaw) < 0.01f) recoilYaw = 0;
```

- [ ] **Step 2: Add combat keybinds to PlayerInputSystem**

Add to `PlayerInputSystem.java` — the `InputAdapter` returned by `getInputAdapter()` needs additional key handlers:

```java
// Add a reference to CombatInputSystem
private CombatInputSystem combatInputSystem;

public void setCombatInputSystem(CombatInputSystem system) {
    this.combatInputSystem = system;
}

// In the InputAdapter:
@Override
public boolean touchDown(int screenX, int screenY, int pointer, int button) {
    if (button == Input.Buttons.LEFT && combatInputSystem != null) {
        combatInputSystem.setFireInput(true);
        combatInputSystem.setFireHeldInput(true);
    }
    if (button == Input.Buttons.RIGHT && combatInputSystem != null) {
        combatInputSystem.setBlockInput(true);
        combatInputSystem.setBlockHeldInput(true);
    }
    return true;
}

@Override
public boolean touchUp(int screenX, int screenY, int pointer, int button) {
    if (button == Input.Buttons.LEFT && combatInputSystem != null) {
        combatInputSystem.setFireHeldInput(false);
    }
    if (button == Input.Buttons.RIGHT && combatInputSystem != null) {
        combatInputSystem.setBlockHeldInput(false);
    }
    return true;
}

@Override
public boolean keyDown(int keycode) {
    // existing movement keys...
    if (combatInputSystem != null) {
        switch (keycode) {
            case Input.Keys.R: combatInputSystem.setReloadInput(); break;
            case Input.Keys.V: combatInputSystem.setQuickMeleeInput(); break;
            case Input.Keys.NUM_1: combatInputSystem.setSwitchSlotInput(0); break;
            case Input.Keys.NUM_2: combatInputSystem.setSwitchSlotInput(1); break;
            case Input.Keys.NUM_3: combatInputSystem.setSwitchSlotInput(2); break;
        }
    }
    return true;
}

@Override
public boolean scrolled(float amountX, float amountY) {
    if (combatInputSystem != null) {
        // Scroll up = next slot, scroll down = previous
        // simplified: just toggle through slots
    }
    return true;
}
```

- [ ] **Step 3: Wire combat systems into GameWorld**

Add to `GameWorld.java` constructor, after existing systems:

```java
// Combat systems
CombatInputSystem combatInputSystem = new CombatInputSystem();
WeaponDataRegistry weaponData = new WeaponDataRegistry();
CombatDataRegistry combatData = new CombatDataRegistry();
// weaponData.loadFromFiles(); // Uncomment when running with GL context and asset files
// combatData.loadFromFiles();

WeaponSwitchSystem weaponSwitchSystem = new WeaponSwitchSystem(eventBus, weaponData);
WeaponSystem weaponSystem = new WeaponSystem(eventBus);
MeleeSystem meleeSystem = new MeleeSystem(eventBus, combatData);
HitscanSystem hitscanSystem = new HitscanSystem(eventBus);
ProjectileSystem projectileSystem = new ProjectileSystem(eventBus);
DamageSystem damageSystem = new DamageSystem(eventBus, combatData, weaponData);
StatusEffectSystem statusEffectSystem = new StatusEffectSystem(eventBus, combatData);
CombatAISystem combatAISystem = new CombatAISystem(eventBus);
SquadTacticsSystem squadTacticsSystem = new SquadTacticsSystem(eventBus);

engine.addSystem(combatInputSystem);
engine.addSystem(weaponSwitchSystem);
engine.addSystem(weaponSystem);
engine.addSystem(meleeSystem);
engine.addSystem(hitscanSystem);
engine.addSystem(projectileSystem);
engine.addSystem(damageSystem);
engine.addSystem(statusEffectSystem);
engine.addSystem(combatAISystem);
engine.addSystem(squadTacticsSystem);

// Wire combat input to player input
playerInputSystem.setCombatInputSystem(combatInputSystem);
```

Add a `createCombatEntity` method to `GameWorld` for creating armed NPCs:

```java
public Entity createHostileNPC(Vector3 position, String archetypeId, int squadId,
                                WeaponDataRegistry weaponData, CombatDataRegistry combatData) {
    Entity entity = new Entity();

    TransformComponent transform = new TransformComponent();
    transform.position.set(position);
    entity.add(transform);

    AIArchetypeData archetype = combatData.getArchetype(archetypeId);

    HealthComponent health = new HealthComponent();
    health.maxHP = archetype != null ? archetype.maxHP : 80f;
    health.currentHP = health.maxHP;
    entity.add(health);

    entity.add(new HitboxComponent());
    entity.add(new StatusEffectsComponent());
    entity.add(new HostileTagComponent());
    entity.add(new CombatInputComponent());

    CombatAIComponent ai = new CombatAIComponent();
    if (archetype != null) {
        ai.aggroRange = archetype.aggroRange;
        ai.engageRange = archetype.engageRange;
        ai.preferredRangeMin = archetype.preferredRangeMin;
        ai.preferredRangeMax = archetype.preferredRangeMax;
        ai.aggression = archetype.aggression;
        ai.archetypeId = archetypeId;
    }
    entity.add(ai);

    SquadComponent squad = new SquadComponent();
    squad.squadId = squadId;
    entity.add(squad);

    entity.add(new CoverComponent());

    // Weapon setup
    WeaponInventoryComponent inv = new WeaponInventoryComponent();
    // Default loadout based on archetype
    entity.add(inv);
    entity.add(new RangedWeaponComponent());
    entity.add(new MeleeWeaponComponent());
    entity.add(new MeleeStateComponent());

    engine.addEntity(entity);
    return entity;
}
```

Also add combat components to the player entity creation method:

```java
// In createPlayerEntity or equivalent, add:
entity.add(new CombatInputComponent());
entity.add(new WeaponInventoryComponent());
entity.add(new RangedWeaponComponent());
entity.add(new MeleeWeaponComponent());
entity.add(new MeleeStateComponent());
entity.add(new HealthComponent());
entity.add(new ShieldComponent());
entity.add(new ArmorComponent());
entity.add(new StatusEffectsComponent());
entity.add(new HitboxComponent());
```

- [ ] **Step 4: Verify compilation of all combat code**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run all combat tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.*"`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(combat): integrate combat systems into GameWorld, add recoil and keybinds"
```

---

### Task 15: Combat Integration Test

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/combat/CombatIntegrationTest.java`

- [ ] **Step 1: Write end-to-end integration test**

`CombatIntegrationTest.java`:
```java
package com.galacticodyssey.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.*;
import com.galacticodyssey.combat.events.*;
import com.galacticodyssey.combat.systems.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatIntegrationTest {
    private Engine engine;
    private EventBus eventBus;
    private WeaponDataRegistry weaponData;
    private CombatDataRegistry combatData;
    private Entity player;
    private Entity enemy;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        weaponData = new WeaponDataRegistry();
        combatData = new CombatDataRegistry();

        // Register test weapon data
        WeaponFrameData rifle = new WeaponFrameData();
        rifle.id = "test_rifle";
        rifle.category = WeaponCategory.RIFLE;
        rifle.baseDamage = 25f;
        rifle.baseFireRate = 10f;
        rifle.magSize = 30;
        rifle.firingMode = FiringMode.SEMI;
        rifle.hitscan = true;
        rifle.range = 100f;
        rifle.reloadTime = 2f;
        weaponData.registerFrame(rifle);

        BarrelData barrel = new BarrelData();
        barrel.id = "std";
        weaponData.registerBarrel(barrel);

        AmmoTypeData ammo = new AmmoTypeData();
        ammo.id = "std";
        ammo.damageType = DamageType.BALLISTIC;
        ammo.damageMultiplier = 1f;
        weaponData.registerAmmoType(ammo);

        QualityTierData q = new QualityTierData();
        q.tier = QualityTier.COMMON;
        q.globalMultiplier = 1f;
        weaponData.registerQuality(q);

        MeleeFrameData blade = new MeleeFrameData();
        blade.id = "test_blade";
        blade.category = MeleeCategory.BLADE;
        blade.baseDamage = 40f;
        blade.baseSpeed = 1.2f;
        blade.baseReach = 2f;
        blade.baseBlockEfficiency = 0.7f;
        blade.damageType = DamageType.MELEE;
        blade.weightClass = WeightClass.LIGHT;
        blade.directionalModifiers.put("overhead", 1.3f);
        blade.directionalModifiers.put("left", 1.0f);
        blade.directionalModifiers.put("right", 1.0f);
        blade.directionalModifiers.put("thrust", 1.1f);
        weaponData.registerMeleeFrame(blade);

        DamageConfigData config = new DamageConfigData();
        combatData.setDamageConfig(config);

        StatusEffectData bleed = new StatusEffectData();
        bleed.type = StatusEffectType.BLEEDING;
        bleed.duration = 5f;
        bleed.tickRate = 1f;
        bleed.magnitude = 3f;
        bleed.maxStacks = 3;
        combatData.registerStatusEffect(bleed);

        // Build engine with all combat systems
        engine = new Engine();
        engine.addSystem(new WeaponSwitchSystem(eventBus, weaponData));
        engine.addSystem(new WeaponSystem(eventBus));
        engine.addSystem(new MeleeSystem(eventBus, combatData));
        engine.addSystem(new HitscanSystem(eventBus));
        engine.addSystem(new ProjectileSystem(eventBus));
        engine.addSystem(new DamageSystem(eventBus, combatData));
        engine.addSystem(new StatusEffectSystem(eventBus, combatData));

        // Create player
        player = new Entity();
        TransformComponent pt = new TransformComponent();
        pt.position.set(0, 1, 0);
        player.add(pt);
        player.add(new PlayerTagComponent());
        player.add(new FPSCameraComponent());
        player.add(new MovementStateComponent());

        CombatInputComponent playerInput = new CombatInputComponent();
        playerInput.aimDirection.set(0, 0, -1);
        player.add(playerInput);

        WeaponInventoryComponent inv = new WeaponInventoryComponent();
        inv.slots[0] = WeaponAssembly.ranged("test_rifle", "std", "std", null, QualityTier.COMMON);
        inv.slots[2] = WeaponAssembly.melee("test_blade", QualityTier.COMMON);
        inv.activeSlotIndex = 0;
        player.add(inv);

        RangedWeaponComponent ranged = new RangedWeaponComponent();
        ranged.damage = 25f;
        ranged.fireRate = 10f;
        ranged.currentAmmo = 30;
        ranged.magSize = 30;
        ranged.reloadTime = 2f;
        ranged.firingMode = FiringMode.SEMI;
        ranged.hitscan = true;
        ranged.range = 100f;
        ranged.damageType = DamageType.BALLISTIC;
        ranged.ammoTypeId = "std";
        player.add(ranged);

        player.add(new MeleeWeaponComponent());
        player.add(new MeleeStateComponent());
        player.add(new HealthComponent());
        player.add(new StatusEffectsComponent());
        player.add(new HitboxComponent());
        engine.addEntity(player);

        // Create enemy
        enemy = new Entity();
        TransformComponent et = new TransformComponent();
        et.position.set(0, 1, -10);
        enemy.add(et);
        HealthComponent eh = new HealthComponent();
        eh.currentHP = 100f;
        enemy.add(eh);
        enemy.add(new HitboxComponent());
        enemy.add(new StatusEffectsComponent());
        engine.addEntity(enemy);
    }

    @Test
    void fullRangedCombatPipeline_fireHitDamage() {
        CombatInputComponent input = player.getComponent(CombatInputComponent.class);
        input.fireRequested = true;

        float[] totalDamage = {0f};
        eventBus.subscribe(DamageDealtEvent.class, e -> totalDamage[0] += e.finalDamage);

        engine.update(0.016f);

        assertTrue(totalDamage[0] > 0, "Enemy should take damage from hitscan shot");
        HealthComponent eh = enemy.getComponent(HealthComponent.class);
        assertTrue(eh.currentHP < 100f);
    }

    @Test
    void fullMeleeCombatPipeline_switchAttackDamage() {
        CombatInputComponent input = player.getComponent(CombatInputComponent.class);

        // Switch to melee
        input.switchSlotRequested = 2;
        engine.update(0.016f);
        // Complete switch
        engine.update(0.3f);

        // Move enemy closer for melee range
        enemy.getComponent(TransformComponent.class).position.set(0, 1, -1.5f);

        // Attack
        input.meleeAttackRequested = true;
        input.meleeAttackDirection = AttackDirection.OVERHEAD;
        engine.update(0.016f); // -> WIND_UP

        MeleeStateComponent state = player.getComponent(MeleeStateComponent.class);
        assertEquals(MeleeState.WIND_UP, state.currentState);

        // Advance through wind-up
        engine.update(0.2f); // -> ACTIVE

        float[] totalDamage = {0f};
        eventBus.subscribe(DamageDealtEvent.class, e -> totalDamage[0] += e.finalDamage);

        // Active phase performs hit detection
        engine.update(0.016f);
        assertTrue(totalDamage[0] > 0, "Enemy should take melee damage");
    }

    @Test
    void playerCanDieFromDamage() {
        HealthComponent playerHealth = player.getComponent(HealthComponent.class);
        playerHealth.currentHP = 5f;

        boolean[] killed = {false};
        eventBus.subscribe(EntityKilledEvent.class, e -> {
            if (e.target == player) killed[0] = true;
        });

        // Simulate being hit
        eventBus.publish(new HitscanHitEvent(enemy, player,
            new Vector3(), new Vector3(), HitRegion.TORSO, 50f, DamageType.BALLISTIC, "std"));

        assertTrue(killed[0]);
        assertFalse(playerHealth.alive);
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `./gradlew core:test --tests "com.galacticodyssey.combat.CombatIntegrationTest"`
Expected: 3 tests PASS

- [ ] **Step 3: Run full test suite**

Run: `./gradlew core:test`
Expected: All existing + new tests PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/test/java/com/galacticodyssey/combat/CombatIntegrationTest.java
git commit -m "test(combat): add end-to-end combat integration tests"
```
