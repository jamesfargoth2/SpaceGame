# First-Person Combat System — Design Spec

## Overview

A first-person combat system for Galactic Odyssey supporting both directional melee and modular ranged combat. Tactical and weighty feel — medium time-to-kill, armor matters, every hit is impactful. Built as a layered ECS pipeline with event-driven communication, gdx-ai behavior trees for combat AI, and a lightweight FSM for melee attack states.

## Architecture: Layered ECS + FSM/BT Hybrid

Core combat flows through focused Ashley systems communicating via the EventBus:

```
CombatInputSystem → WeaponSwitchSystem → WeaponSystem → AttackResolution → DamageSystem → StatusEffectSystem
                                                              ↑
                                                       CombatAISystem (BT-driven)
                                                              ↑
                                                    SquadTacticsSystem
```

Melee attack sequencing uses a lightweight FSM stored in an ECS component. AI decision-making uses gdx-ai behavior trees. Both integrate cleanly with the ECS — state lives in components, transitions are managed by the appropriate tool.

---

## 1. Component Architecture

### Health & Defense

- **HealthComponent** — `currentHP: float`, `maxHP: float`, `alive: boolean`
- **ShieldComponent** — `currentShield: float`, `maxShield: float`, `rechargeRate: float`, `rechargeDelay: float`, `timeSinceLastHit: float`, `shieldType: enum (energy/kinetic/composite)`
- **ArmorComponent** — `armorRating: float[]` per slot (head, torso, legs, arms), `resistances: Map<DamageType, Float>` per slot, `durability: float[]` per slot, `materialQuality: QualityTier`
- **StatusEffectsComponent** — `activeEffects: List<ActiveStatusEffect>`, where each entry has `type: StatusEffectType`, `remainingDuration: float`, `tickRate: float`, `magnitude: float`, `sourceEntity: Entity`

### Weapons

- **WeaponInventoryComponent** — `slots: WeaponAssembly[3]` (primary, secondary, melee), `activeSlotIndex: int`, `switchCooldown: float`. Each `WeaponAssembly` is a plain data object holding `frameId`, `barrelId`, `ammoTypeId`, `modSlotIds[]`, `materialQuality` — the parts that define a weapon. The inventory holds assemblies; the active slot's assembly is resolved into live stats below.
- **RangedWeaponComponent** — resolved stats for the currently active ranged weapon: `damage: float`, `fireRate: float`, `spread: float`, `range: float`, `recoilPattern: Vector2[]`, `currentAmmo: int`, `magSize: int`, `reloadTime: float`, `firingMode: enum (auto/semi/burst)`, `hitscan: boolean`. Rewritten by `WeaponSwitchSystem` when switching slots.
- **MeleeWeaponComponent** — resolved stats for the currently active melee weapon: `baseDamage: float`, `reach: float`, `swingSpeed: float`, `blockEfficiency: float[]` per direction, `staminaCosts: Map<AttackType, Float>`, `weightClass: enum (light/medium/heavy)`. Rewritten by `WeaponSwitchSystem` when switching to melee slot.

### Melee State

- **MeleeStateComponent** — `currentState: enum (idle/wind_up/active/recovery/blocking/staggered)`, `attackDirection: enum (left/right/overhead/thrust)`, `blockDirection: enum`, `stateTimer: float`, `comboCounter: int`, `canCombo: boolean`

### Combat AI

- **CombatAIComponent** — `behaviorTree: BehaviorTree<Entity>`, `currentTarget: Entity`, `aggroRange: float`, `engageRange: float`, `threatLevel: float`, `lastKnownTargetPosition: Vector3`
- **SquadComponent** — `squadId: int`, `role: enum (leader/flanker/suppressor/medic)`, `formationOffset: Vector3`
- **CoverComponent** — `currentCoverPoint: CoverPoint` (nullable), `inCover: boolean`, `coverQuality: enum (half/full)`, `peekDirection: Vector3`

### Combat Input

- **CombatInputComponent** — `fireRequested: boolean`, `aimDirection: Vector3`, `meleeAttackDirection: enum`, `blockRequested: boolean`, `blockDirection: enum`, `reloadRequested: boolean`, `switchSlotRequested: int`, `quickMeleeRequested: boolean`

### Tagging & Hitboxes

- **HostileTagComponent** — `factionId: String`
- **HitboxComponent** — `regionMultipliers: Map<HitRegion, Float>` (head=2.0, torso=1.0, arms=0.75, legs=0.75)

---

## 2. Modular Weapon System

Weapons are assembled from parts defined in JSON data files. At equip time, parts are resolved into final stats by `WeaponStatsResolver`.

### Assembly Pipeline

```
WeaponAssembly (frameId + barrelId + ammoTypeId + modIds[] + quality)
    → WeaponStatsResolver.resolve(assembly, dataRegistry)
    → Final computed stats written to RangedWeaponComponent or MeleeWeaponComponent
```

### Part Definitions

**Frame:**
```json
{
  "id": "rifle_standard",
  "category": "rifle",
  "baseDamage": 25.0,
  "baseFireRate": 8.0,
  "baseSpread": 2.0,
  "baseRecoil": 3.5,
  "magSize": 30,
  "modSlotCount": 2,
  "weight": 3.5,
  "firingMode": "auto",
  "hitscan": true
}
```

**Barrel:**
```json
{
  "id": "long_barrel",
  "damageMultiplier": 1.15,
  "rangeMultiplier": 1.4,
  "spreadMultiplier": 0.7,
  "recoilMultiplier": 1.1,
  "weightAdd": 0.8
}
```

**Ammo Type:**
```json
{
  "id": "incendiary_round",
  "damageType": "incendiary",
  "damageMultiplier": 0.9,
  "statusEffect": "burning",
  "statusEffectChance": 0.3,
  "projectileSpeed": null
}
```

**Mod:**
```json
{
  "id": "stabilizer_mk2",
  "slot": "stabilizer",
  "statModifiers": {
    "spread": -0.5,
    "recoil": -0.8
  }
}
```

**Quality Tiers:**
```json
{
  "tier": "military",
  "globalMultiplier": 1.2,
  "durabilityMultiplier": 1.5
}
```

### Stat Resolution

Multiplicative stacking for multipliers, additive for flat bonuses:

```
finalStat = (baseStat * barrelMult * qualityMult) + sum(modFlatBonuses)
```

All stats clamped to defined min/max ranges.

### Melee Weapons

Melee weapons use frame + quality only (no barrel/ammo/mods):

```json
{
  "id": "plasma_blade",
  "category": "blade",
  "baseDamage": 40.0,
  "baseSpeed": 1.2,
  "baseReach": 1.5,
  "baseBlockEfficiency": 0.7,
  "weight": 1.0,
  "damageType": "energy",
  "directionalModifiers": {
    "overhead": 1.3,
    "thrust": 1.1,
    "left": 1.0,
    "right": 1.0
  }
}
```

`WeaponStatsResolver` is a pure utility class — no ECS or GL dependency, fully unit-testable.

---

## 3. Directional Melee Combat

### Attack Directions

| Direction | Damage Mult | Speed | Hitbox Shape | Notes |
|-----------|------------|-------|-------------|-------|
| Overhead | 1.3x | Slowest wind-up | Narrow vertical arc | Drains more block stamina |
| Left/Right Swing | 1.0x | Fastest | Wide horizontal arc | Best for groups |
| Thrust | 1.1x | Medium | Narrow, longest reach | Better armor penetration |

### Blocking

- Player holds block + mouse direction to set block stance (left/right/overhead/thrust)
- **Correct directional block:** negate most damage, attacker staggers briefly, small stamina cost
- **Wrong-direction block:** 30% mitigation, heavy stamina drain, no attacker stagger
- **No block:** full damage

### FSM States

```
Idle → (attack input) → Wind-Up → Active → Recovery → Idle
                                      ↓
                                 (hit lands) → can combo? → Wind-Up (next direction)

Idle → (block input) → Blocking → (release) → Idle

Any state → (hit while exposed) → Staggered → Idle
```

### Timing Windows (per weapon weight class)

| Phase | Light (blade) | Medium (staff) | Heavy (hammer) |
|-------|--------------|----------------|----------------|
| Wind-up | 0.15s | 0.25s | 0.4s |
| Active | 0.1s | 0.15s | 0.2s |
| Recovery | 0.2s | 0.35s | 0.5s |
| Stagger | 0.3s | 0.4s | 0.5s |

### Combos

- During Recovery, inputting a *different* direction queues a follow-up attack
- Up to 3 hits in a combo chain
- Each successive combo hit: +10% damage, +20% stamina cost
- Same-direction repeated attacks cannot combo (prevents spam)

### Hit Detection

Swept sphere cast from weapon origin in attack direction during Active phase. Reach and arc width from `MeleeWeaponComponent`. Hits resolved against `HitboxComponent` for regional damage multipliers.

### Stamina Integration

Melee attacks and blocks drain stamina from `MovementStateComponent.stamina`. At 0 stamina: attacks 40% slower (exhaustion penalty), blocks auto-fail. Creates natural combat rhythm.

### MeleeSystem Responsibilities

- Reads `CombatInputComponent` for attack/block direction
- Advances `MeleeStateComponent` FSM
- During Active phase, performs swept sphere cast
- Publishes `MeleeHitEvent(attacker, target, direction, weaponData, hitRegion)` on contact
- Publishes `MeleeBlockEvent` when blocked

---

## 4. Ranged Combat

### Hitscan Weapons (ballistic, laser)

- On fire, `HitscanSystem` casts a ray from camera origin along aim direction
- Ray length = weapon range stat
- Spread applied by rotating ray within a cone (angle = weapon spread stat)
- First hit entity with `HitboxComponent` takes damage
- Publishes `HitscanHitEvent(shooter, target, hitPoint, hitNormal, hitRegion, weaponData)`
- Bullet tracers are visual-only, spawned by rendering subscriber

### Projectile Weapons (plasma, rockets, grenades)

- On fire, `ProjectileSystem` spawns a pooled projectile entity with: `TransformComponent`, `PhysicsBodyComponent` (small sphere collider), `ProjectileComponent` (speed, damage, damageType, ownerEntity, lifetime, areaOfEffect radius)
- Projectile velocity set on physics body — Bullet handles collision
- On collision, publishes `ProjectileHitEvent(shooter, target, hitPoint, projectileData)`
- AoE projectiles: sphere overlap query at impact, damage all entities within radius with falloff (100% center, 25% edge)
- Projectiles pooled via libGDX `Pool<Entity>` to avoid GC pressure

### Firing Mechanics

- **Semi-auto:** one shot per click
- **Automatic:** continuous fire while held, governed by fireRate (shots/second)
- **Burst:** 3 rounds per click with short inter-burst delay

### Recoil

- Each shot adds recoil impulse to camera pitch/yaw (per-weapon recoil pattern: sequence of Vector2 offsets)
- Camera recovers toward center between shots at a recovery rate
- Sustained fire walks the pattern upward; trigger discipline rewarded
- `RecoilEvent` published — `CameraSystem` subscribes and applies

### Reload

- Reload requested → weapon enters reload state for `reloadTime` seconds → mag refills
- Cannot fire during reload
- Reload cancelled by switching weapons or melee attacking

### WeaponSystem Responsibilities

- Reads `CombatInputComponent.fireRequested`
- Manages fire rate timing, ammo count, reload state
- On fire: publishes `WeaponFiredEvent(entity, weaponData, aimDirection)`
- `HitscanSystem` or `ProjectileSystem` subscribes based on weapon's hitscan flag

---

## 5. Damage Pipeline

`DamageSystem` subscribes to `HitscanHitEvent`, `ProjectileHitEvent`, and `MeleeHitEvent`. Normalizes into `DamageRequest` and runs the unified pipeline.

### Pipeline Stages

```
DamageRequest(rawDamage, damageType, hitRegion, attacker, target)
│
├─ 1. Hitbox Multiplier
│     damage *= regionMultiplier (head=2.0, torso=1.0, arms=0.75, legs=0.75)
│
├─ 2. Shield Check
│     if shield > 0:
│       shieldAbsorb = min(damage, currentShield)
│       damage -= shieldAbsorb
│       shield -= shieldAbsorb
│       reset rechargeTimer
│       if damage <= 0: publish ShieldAbsorbEvent, stop
│
├─ 3. Armor Mitigation
│     resistance = armor.getResistance(damageType, hitRegion)
│     damage *= (1.0 - resistance)     // resistance capped at 0.85
│     armor.degradeSlot(hitRegion, rawDamage)
│
├─ 4. Health Damage
│     health.currentHP -= damage
│     publish DamageDealtEvent(target, finalDamage, damageType, hitRegion)
│     if currentHP <= 0: publish EntityKilledEvent(target, attacker)
│
└─ 5. Status Effect Roll
      if ammoType has statusEffect:
        roll against statusEffectChance
        if success: add to StatusEffectsComponent
        publish StatusEffectAppliedEvent(target, effectType)
```

### Status Effects

| Effect | Source | Behavior |
|--------|--------|----------|
| Bleeding | Ballistic/melee | 3 damage/sec for 5s, stacks up to 3x |
| Burning | Incendiary | 5 damage/sec for 4s, -20% armor effectiveness |
| EMP'd | EMP ammo | Disables shields for 6s, slows energy weapon recharge |
| Stunned | Heavy melee/explosive | Cannot move or attack for 0.8s |
| Slowed | Cryo (future) | -40% movement speed for 4s |

**Effect Combos:** Burning + EMP'd = Burning does double tick damage.

### Shield Recharge

- No recharge while taking damage
- After `rechargeDelay` seconds (default 4s) with no hits, regenerates at `rechargeRate/sec`
- Personal shields are a buffer (30-50% of max HP), not a wall

### Armor Degradation

- Each hit degrades durability on the struck slot
- At 0 durability, slot provides 0% resistance until repaired
- Sustained fire at one region breaks through armor (tactical depth)

---

## 6. Combat AI

### CombatAISystem — Behavior Tree (gdx-ai)

Each hostile NPC runs a `BehaviorTree<Entity>` assigned by archetype:

```
Root (Selector)
├─ [Sequence] Critical Health (<20%)
│   ├─ FindRetreatPoint
│   ├─ MoveTo(retreatPoint)
│   └─ HealOrFlee
│
├─ [Sequence] Has Target
│   ├─ [Selector] Engage
│   │   ├─ [Sequence] In Melee Range & Has Melee Weapon
│   │   │   ├─ SelectMeleeDirection (counter target's block stance)
│   │   │   └─ MeleeAttack
│   │   │
│   │   ├─ [Sequence] In Weapon Range & Has Line of Sight
│   │   │   ├─ [Selector] Position
│   │   │   │   ├─ [Sequence] InCover → PeekAndShoot
│   │   │   │   └─ [Sequence] FindCover → MoveToCover
│   │   │   └─ AimAndFire
│   │   │
│   │   └─ [Sequence] No Line of Sight
│   │       ├─ MoveToLastKnownPosition
│   │       └─ SearchArea
│   │
│   └─ [Decorator: Cooldown 2s] ReportTargetToSquad
│
└─ [Sequence] No Target
    ├─ Patrol(waypointList)
    └─ ScanForThreats(aggroRange)
```

### NPC Archetypes (data-driven JSON)

| Archetype | Behavior | Preferred Range | Aggression |
|-----------|----------|----------------|------------|
| Grunt | Advances toward cover, shoots from medium range | 10-20m | Medium |
| Rusher | Closes to melee quickly, light armor | 0-3m | High |
| Sniper | Seeks distant cover, retreats if approached | 30-50m | Low |
| Heavy | Slow advance, suppressive fire, high HP/armor | 8-15m | Medium |
| Officer | Behind squad, buffs allies, calls flanks | 15-25m | Low |

### SquadTacticsSystem — Group Coordination

- Squads: 3-6 NPCs sharing a `squadId`
- Squad leader makes tactical calls via events:
  - `FlankOrderEvent(squadId, targetPosition, flankDirection)` — 1-2 members reposition to flank
  - `SuppressOrderEvent(squadId, targetPosition)` — suppressor fires while others move
  - `RetreatOrderEvent(squadId, retreatPoint)` — fall back when >50% casualties
  - `AdvanceOrderEvent(squadId, targetPosition)` — push when target is vulnerable

### Cover System

- `CoverPoint`: position, normal, quality (half/full), occupied flag
- Cover points defined in level data or detected at runtime via raycasts against geometry
- NPC cover evaluation: distance to target, angle of protection, distance to self, occupancy
- Half cover: crouch, exposed head, 50% damage reduction
- Full cover: fully hidden, must peek to shoot (brief exposure window)

### Threat Detection

- `aggroRange` + 120-degree vision cone
- Line-of-sight via raycast (walls block)
- Sound propagation: gunshots alert NPCs within a radius (suppressor mod shrinks radius)
- `lastKnownTargetPosition` persists for search duration before NPC returns to patrol

---

## 7. Weapon Slots & Switching

### Slot Layout

| Slot | Index | Accepts |
|------|-------|---------|
| Primary | 0 | Rifles, shotguns, heavy weapons |
| Secondary | 1 | Pistols, SMGs |
| Melee | 2 | Blades, staves, hammers (fists as fallback) |

### Switching

- Number keys (1/2/3) or scroll wheel
- Switch time: 0.6s primary, 0.4s secondary, 0.2s melee
- State: `Equipped → Lowering (half switchTime) → Raising (half switchTime) → Equipped`
- Cannot fire during switch
- Switching cancels reload in progress
- Getting hit does NOT interrupt switch (avoids stun-lock frustration)

### Quick-Melee

- Dedicated key (V), available from any slot
- Does not change active slot
- Fixed low damage, no directional system, short stagger on hit
- Own cooldown: 0.8s, separate from weapon switching

### WeaponSwitchSystem

- Reads `CombatInputComponent.switchSlotRequested`
- Manages lower/raise timer
- On completion, resolves new slot's assembly via `WeaponStatsResolver`
- Publishes `WeaponSwitchedEvent(entity, oldSlot, newSlot)`

---

## 8. Event Architecture

### Combat Events

| Event | Publisher | Subscribers |
|-------|----------|-------------|
| `WeaponFiredEvent` | WeaponSystem | HitscanSystem, ProjectileSystem, UI, Audio, AI |
| `HitscanHitEvent` | HitscanSystem | DamageSystem, VFX |
| `ProjectileHitEvent` | ProjectileSystem | DamageSystem, VFX |
| `MeleeHitEvent` | MeleeSystem | DamageSystem, Audio, VFX |
| `MeleeBlockEvent` | MeleeSystem | DamageSystem, Audio, AI |
| `DamageDealtEvent` | DamageSystem | UI, Audio, AI |
| `ShieldAbsorbEvent` | DamageSystem | UI, Audio |
| `EntityKilledEvent` | DamageSystem | SquadTacticsSystem, UI, XP |
| `StatusEffectAppliedEvent` | DamageSystem | StatusEffectSystem, UI |
| `StatusEffectExpiredEvent` | StatusEffectSystem | UI, Audio |
| `WeaponSwitchedEvent` | WeaponSwitchSystem | UI, Animation |
| `ReloadStartedEvent` | WeaponSystem | UI, Animation, AI |
| `FlankOrderEvent` | SquadTacticsSystem | Squad members |
| `SuppressOrderEvent` | SquadTacticsSystem | Squad members |
| `RetreatOrderEvent` | SquadTacticsSystem | Squad members |
| `ThreatDetectedEvent` | CombatAISystem | SquadTacticsSystem, nearby AI |

### ECS System Priority Order

```
 0  PlayerInputSystem           (existing)
 1  CombatInputSystem           (NEW)
 2  PlayerMovementSystem        (existing)
 3  WeaponSwitchSystem          (NEW)
 4  WeaponSystem                (NEW)
 5  MeleeSystem                 (NEW)
 6  HitscanSystem               (NEW)
 7  ProjectileSystem            (NEW)
 8  DamageSystem                (NEW)
 9  StatusEffectSystem          (NEW)
10  CombatAISystem              (NEW)
11  SquadTacticsSystem          (NEW)
12  BulletPhysicsSystem         (existing)
13  PhysicsBodySystem           (existing)
14  CameraSystem                (existing, extended for recoil)
```

---

## 9. Data Files

All content loaded at runtime from JSON via `WeaponDataRegistry` and `CombatDataRegistry`. Both are plain Java objects with no ECS or GL dependency.

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
    squads.json
```

---

## 10. Summary

| Category | Count |
|----------|-------|
| New ECS Components | 13 |
| New ECS Systems | 9 |
| Event Types | 16 |
| Data File Categories | 10 |
| NPC Archetypes | 5 |
| Status Effects | 5 (extensible) |
| Weapon Part Types | 5 (frame, barrel, ammo, mod, quality) |
