# Combat Systems

The `combat` package implements the unified damage model, all weapon types, combat AI, status effects, and the VFX/audio event bindings for combat feedback.

---

## Damage Pipeline

**`DamageSystem`**

All damage flows through a single ordered pipeline regardless of source (ranged, melee, explosive):

1. **Hitbox multiplier** — `HitboxComponent` stores per-region damage multipliers (headshot = 2×, torso = 1×, limbs = 0.75×).
2. **Shield absorption** — `ShieldComponent` absorbs incoming damage up to current shield HP; publishes `ShieldAbsorbEvent`.
3. **Armor reduction** — `ArmorComponent` reduces remaining damage by armor rating per damage type; armor degrades with use.
4. **Health deduction** — remaining damage applied to `HealthComponent`.
5. **Death check** — if HP ≤ 0, publishes `EntityKilledEvent`.

`DamageSystem` subscribes to `HitscanHitEvent`, `ProjectileHitEvent`, and `MeleeHitEvent`. It never pulls from weapons directly — all paths converge on those three event types.

---

## Weapon Systems

**`WeaponSystem`**

Drives firing, reload, and ammo management. Supports three firing modes:
- `SEMI` — one round per trigger pull
- `AUTO` — continuous fire while trigger held
- `BURST` — fixed round count per trigger pull

Reads `CombatInputComponent.firing` each frame, advances the fire-rate timer, decrements ammo in `RangedWeaponComponent`, and publishes `WeaponFiredEvent`. When ammo reaches zero, publishes `ReloadStartedEvent` and begins the reload timer.

**`HitscanSystem`**

Handles instant-hit (raycast) weapons. On `WeaponFiredEvent`, performs a Bullet ray test along the aim vector. If the ray hits a physics body that has `HitboxComponent`, determines the hit region and publishes `HitscanHitEvent` with shooter entity, hit entity, local hit point, body region, and damage value.

**`ProjectileSystem`**

Handles kinetic projectiles (bullets with travel time, rockets without guidance). Creates a projectile entity on `WeaponFiredEvent`, applies initial velocity, and resolves collision via Bullet contact callbacks, publishing `ProjectileHitEvent`.

**`MeleeSystem`**

Executes melee attacks from `MeleeWeaponComponent`. Drives the `MeleeStateComponent` state machine (idle → windup → active → recovery). During the active frames defined by `MeleeFrameData`, does an overlap test in front of the attacker; hits publish `MeleeHitEvent`.

**`WeaponSwitchSystem`**

Manages `WeaponInventoryComponent` slot changes. Enforces a holster-draw timing delay to prevent instant swapping.

**`BulletTracerSystem`**

Renders visual tracers for hitscan weapons. On `WeaponFiredEvent`, spawns a short-lived line segment from the muzzle toward the hit point, fading over ~0.1 s.

---

## Explosions & Area Effects

**`ExplosionSystem`**

On `WeaponFiredEvent` (for explosive projectiles) or direct calls, performs a sphere overlap test. All `ExplosionAffectedComponent` entities within radius receive damage scaled by inverse-square falloff, and knockback impulse radially outward.

---

## Status Effects

**`StatusEffectSystem`**

Applies, ticks, and expires status effects tracked in `StatusEffectsComponent`. Each active effect:
- Burns down its duration timer each frame.
- Applies periodic damage (burning, acid, EMP) via the damage pipeline.
- May reduce armor rating or movement speed while active.
- Publishes `StatusEffectExpiredEvent` when it ends.

Effect definitions are loaded from JSON by `CombatDataRegistry` into `StatusEffectData` objects (duration, damage-per-second, armor reduction, VFX key).

---

## Combat AI

**`CombatAISystem`**

Steps NPC behavior trees each frame. Reads from `CombatAIComponent` (which stores the current behavior tree root and target entity). Subscribes to `EntityKilledEvent` to clear targets when they die.

**`SquadTacticsSystem`**

Layer above individual AI. Coordinates groups sharing a `SquadComponent`. Distributes roles (suppressor, flanker, medic) and issues squad orders via `FlankOrderEvent`, `SuppressOrderEvent`, `AdvanceOrderEvent`, and `RetreatOrderEvent`.

**`CombatBlackboard`**

Shared data structure the behavior tree reads from: known threats, identified cover points, squad state, morale. Updated by perception tasks each tick.

### Behavior Tree Tasks

| Category | Tasks |
|---|---|
| Conditions | `HasTargetCondition`, `InWeaponRangeCondition`, `InCoverCondition`, `HasLineOfSightCondition`, `CriticalHealthCondition` |
| Movement | `MoveToTask`, `FindCoverTask`, `MoveToCoverTask`, `PatrolTask` |
| Combat | `AimAndFireTask`, `PeekAndShootTask`, `MeleeAttackTask` |
| Survival | `HealOrFleeTask`, `RetreatOrderEvent` |
| Coordination | `ReportToSquadTask` |

---

## Data Registries

| Registry | Content |
|---|---|
| `CombatDataRegistry` | Status effects, AI archetypes, global damage config (loaded from `data/combat/`) |
| `WeaponDataRegistry` | Weapon assemblies, barrel/ammo/mod definitions, quality tiers |
| `WeaponStatsResolver` | Combines assembly + mods + quality tier into final stats at runtime |
| `AIArchetypeData` | Per-archetype AI parameters: aggression, flank tendency, healing tendency, threat range |
| `DamageConfigData` | Global constants: max armor resistance cap, crit chance, hitbox multipliers |

---

## Components Reference

| Component | Key fields |
|---|---|
| `HealthComponent` | `currentHp`, `maxHp`, `alive` |
| `ShieldComponent` | `currentShield`, `maxShield`, `rechargeRate`, `rechargeDelay`, timer |
| `ArmorComponent` | armor values per region/damage-type, `durability`, degradation rate |
| `HitboxComponent` | hitbox AABB per body region, damage multipliers |
| `RangedWeaponComponent` | ammo, fire rate, firing mode, reload time, heat |
| `MeleeWeaponComponent` | damage, reach, attack speed, recovery time |
| `WeaponInventoryComponent` | weapon slot array, active slot index, switch state |
| `CombatInputComponent` | `firing`, `aiming`, `reloading`, `aimDirection` |
| `CombatAIComponent` | behavior tree root, current target entity, blackboard reference |
| `SquadComponent` | squad ID, leader entity, morale float |
| `StatusEffectsComponent` | list of active `StatusEffectInstance` with remaining duration |

---

## Events Reference

| Event | When published |
|---|---|
| `WeaponFiredEvent` | Weapon discharges (shooter entity, aim direction) |
| `HitscanHitEvent` | Raycast hits a valid target |
| `ProjectileHitEvent` | Projectile entity collides |
| `MeleeHitEvent` | Melee active-frame hits a target |
| `DamageDealtEvent` | Final damage number after all reductions |
| `ShieldAbsorbEvent` | Shield absorbs partial or full damage |
| `EntityKilledEvent` | Entity HP drops to 0 (includes killer entity reference) |
| `StatusEffectAppliedEvent` | Status effect begins |
| `StatusEffectExpiredEvent` | Status effect ends |
| Squad orders | `FlankOrderEvent`, `SuppressOrderEvent`, `RetreatOrderEvent`, `AdvanceOrderEvent` |
