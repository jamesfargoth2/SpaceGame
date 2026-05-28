# Grenade System Design

## Overview

A data-driven grenade system supporting both hand-thrown and launcher-fired grenades. Launches with fragmentation grenades only, designed to expand to additional types (EMP, incendiary, stun) via JSON data. Shares the existing explosion, damage, and status effect pipelines — no new damage or VFX systems required.

## Architecture: Composition on ProjectileComponent

Grenades are ECS entities composed of `ProjectileComponent` (physics) + `GrenadeComponent` (fuse/cook state). This reuses all existing projectile physics (gravity, drag, collision) and the full explosion pipeline (`DetonationEvent` → `ExplosionSystem` → `BlastDamageEvent` → `DamageSystem`). Grenade-specific logic lives in two new systems: `GrenadeSystem` (fuse management) and `GrenadeThrowSystem` (input/cooking/spawning).

---

## Components

### GrenadeComponent

Attached alongside `ProjectileComponent` on every grenade entity. Pure data, no logic.

| Field | Type | Description |
|---|---|---|
| `grenadeTypeId` | `String` | Key into `grenades.json` data |
| `fuseType` | `FuseType` enum | `TIMED`, `IMPACT`, `PROXIMITY` |
| `fuseTimer` | `float` | Remaining time before detonation (timed fuse) |
| `fuseDuration` | `float` | Original fuse duration from data |
| `cookTime` | `float` | How long the player cooked before throwing |
| `cookable` | `boolean` | Whether this grenade type supports cooking |
| `proximityRadius` | `float` | Trigger radius for proximity fuse (future) |
| `detonated` | `boolean` | Prevents double-detonation |
| `bounceRestitution` | `float` | Energy retained per bounce (0.0–1.0) |
| `maxBounces` | `int` | Max bounces before resting |
| `bounceCount` | `int` | Current bounce count |

### GrenadeInventoryComponent

Attached to the player entity. Tracks grenade loadout.

| Field | Type | Description |
|---|---|---|
| `grenades` | `Map<String, Integer>` | Grenade type ID → count carried |
| `selectedGrenadeType` | `String` | Currently selected type ID |
| `maxPerType` | `int` | Carry limit per grenade type |
| `cookStartTime` | `float` | World time when cook began (0 if not cooking) |
| `throwState` | `ThrowState` enum | `IDLE`, `COOKING`, `THROWN` |

### FuseType Enum

```
TIMED      — detonates after fuseTimer reaches 0
IMPACT     — detonates on first collision
PROXIMITY  — detonates when an enemy enters proximityRadius
```

### ThrowState Enum

```
IDLE       — not throwing
COOKING    — holding the grenade, fuse counting down
THROWN     — grenade released (transient, resets to IDLE)
```

---

## Systems

### GrenadeSystem (Priority 8)

Processes entities with `GrenadeComponent` + `ProjectileComponent` + `TransformComponent`.

**Timed fuse logic:**
- Each tick: `fuseTimer -= deltaTime`
- When `fuseTimer <= 0`: publish `DetonationEvent` at entity position, set `detonated = true`, remove entity from engine

**Impact fuse logic:**
- Subscribes to `ProjectileHitEvent`
- If the hit entity has `GrenadeComponent` with `fuseType == IMPACT`: publish `DetonationEvent`, set `detonated = true`, remove entity

**Proximity fuse logic (future):**
- Each tick: check distance to all entities with `HealthComponent` + `HostileTagComponent`
- If any within `proximityRadius`: detonate

**Detonation:**
- Publishes `DetonationEvent(position, explosionData)` where `ExplosionData` is built from the grenade's JSON definition (blastRadius, blastEnergy, blastFraction, thermalFraction, fragmentFraction, isDirectional)
- The existing `ExplosionSystem` handles all blast physics from there

### GrenadeThrowSystem (Priority 3)

Processes the player entity's `GrenadeInventoryComponent` + `CombatInputComponent`.

**Input → cook → throw loop:**

1. **Throw button pressed** (`grenadeThrowRequested`):
   - Check inventory has grenades of `selectedGrenadeType`
   - Set `throwState = COOKING`, record `cookStartTime = worldTime`

2. **Each tick while COOKING:**
   - `cookTime = worldTime - cookStartTime`
   - If `cookTime >= fuseDuration` (for cookable grenades): detonate in hand — publish `DetonationEvent` at player position, decrement inventory, reset to `IDLE`. Self-damage via normal blast pipeline.

3. **Throw button released** (`grenadeThrowHeld` becomes false while COOKING):
   - Calculate remaining fuse: `fuseDuration - cookTime`
   - Spawn grenade entity with:
     - `TransformComponent` at player position + slight forward offset
     - `ProjectileComponent`: velocity = aim direction (with ~15° upward arc bias) × `throwForce / mass`, gravity enabled, drag from data
     - `GrenadeComponent`: fuseTimer = remaining fuse, all fields from data
   - Decrement inventory count
   - Publish `GrenadeThrowEvent`
   - Reset `throwState = IDLE`

**Non-cookable grenades:** Fuse timer starts at full `fuseDuration` regardless of hold time. Holding just delays the throw, doesn't shorten the fuse.

### ProjectileSystem Modifications

Two small changes to the existing system:

**1. Bounce handling for grenades:**
- In the collision handler, if the colliding entity has a `GrenadeComponent` AND `fuseType != IMPACT`:
  - Reflect velocity off surface normal
  - Multiply by `bounceRestitution`
  - Increment `bounceCount`
  - If `bounceCount >= maxBounces` or velocity magnitude < 0.5: zero velocity (grenade rests)
  - Publish `GrenadeBounceEvent`
  - Do NOT publish `ProjectileHitEvent`, do NOT destroy entity
- If `fuseType == IMPACT`: proceed with normal `ProjectileHitEvent` flow (GrenadeSystem catches it)

**2. GrenadeComponent attachment for launcher weapons:**
- When spawning a projectile from `WeaponFiredEvent`, check if the weapon data includes a `grenadeTypeId` field
- If present, load grenade data from `GrenadeDataRegistry` and attach a `GrenadeComponent` to the projectile entity

### CombatInputSystem Modification

Add `grenadeThrowRequested` (one-shot) and `grenadeThrowHeld` (continuous) to `CombatInputComponent`, following the existing `fireRequested`/`fireHeld` pattern. Default keybind: `G`.

---

## Data

### grenades.json

Location: `core/src/main/resources/data/combat/grenades.json`

```json
{
  "frag": {
    "displayName": "M4 Fragmentation Grenade",
    "fuseType": "TIMED",
    "fuseDuration": 3.0,
    "cookable": true,
    "throwForce": 18.0,
    "mass": 0.4,
    "drag": 0.05,
    "gravity": true,
    "blastRadius": 8.0,
    "blastEnergy": 5000.0,
    "blastFraction": 0.5,
    "thermalFraction": 0.1,
    "fragmentFraction": 0.4,
    "isDirectional": false,
    "bounceRestitution": 0.3,
    "maxBounces": 5,
    "statusEffect": null,
    "statusEffectChance": 0.0,
    "maxCarry": 4
  }
}
```

All blast fields map 1:1 to `ExplosionData` consumed by the existing `ExplosionSystem`.

### GrenadeDataRegistry

Loads and caches `grenades.json` at startup. Follows the same pattern as `WeaponDataRegistry`. Provides lookup by type ID string.

### Weapon data modification

The Thumper GL entry in `weapons/frames.json` gains a `grenadeTypeId: "frag"` field. When `ProjectileSystem` spawns a projectile for this weapon, it checks for this field and attaches `GrenadeComponent`.

---

## Events

### New Events

| Event | Fields | Published by | Consumed by |
|---|---|---|---|
| `GrenadeThrowEvent` | `Entity thrower, Vector3 position, Vector3 direction, String grenadeTypeId` | `GrenadeThrowSystem` | VFX, Audio (pin-pull sound, throw animation) |
| `GrenadeBounceEvent` | `Entity grenade, Vector3 position, Vector3 surfaceNormal` | `ProjectileSystem` | Audio (metallic clank sound) |

### Existing Events Reused

| Event | Role in grenade flow |
|---|---|
| `DetonationEvent` | Published by `GrenadeSystem` on fuse trigger → consumed by `ExplosionSystem` |
| `BlastDamageEvent` | Published by `ExplosionSystem` for each target in blast → consumed by `DamageSystem` |
| `DamageDealtEvent` | Published by `DamageSystem` after damage applied → consumed by UI, audio |
| `EntityKilledEvent` | Published by `DamageSystem` if HP ≤ 0 |
| `StatusEffectAppliedEvent` | Published if grenade data includes a status effect |
| `ProjectileHitEvent` | Used only for `IMPACT` fuse grenades — triggers detonation in `GrenadeSystem` |

---

## VFX & Audio Integration

No new VFX or audio systems. New event bindings only.

### VFX event bindings (additions to vfx_event_bindings.json)

- `GrenadeThrowEvent` → hand-motion particle (low priority, optional)
- `GrenadeBounceEvent` → small spark/dust puff at bounce point
- `DetonationEvent` → already bound to `impact_explosion` (reused as-is)

### Audio event bindings

- `GrenadeThrowEvent` → pin-pull + throw whoosh
- `GrenadeBounceEvent` → metallic clank
- `DetonationEvent` → already bound to explosion sound

### Screen shake

Already triggered by `DetonationEvent` via `screen_shake_config.json`, scaled by blast energy and distance. No changes needed.

---

## Summary of Changes

| Category | Item | Type |
|---|---|---|
| New component | `GrenadeComponent` | New file |
| New component | `GrenadeInventoryComponent` | New file |
| New system | `GrenadeSystem` (Priority 8) | New file |
| New system | `GrenadeThrowSystem` (Priority 3) | New file |
| New data class | `GrenadeData` | New file |
| New data class | `GrenadeDataRegistry` | New file |
| New event | `GrenadeThrowEvent` | New file |
| New event | `GrenadeBounceEvent` | New file |
| New enums | `FuseType`, `ThrowState` | New file(s) or in CombatEnums |
| New data file | `combat/grenades.json` | New resource |
| Modified system | `ProjectileSystem` — bounce branch + GrenadeComponent attachment | Small edit |
| Modified system | `CombatInputSystem` — grenade throw input fields | Small edit |
| Modified component | `CombatInputComponent` — `grenadeThrowRequested`, `grenadeThrowHeld` | Small edit |
| Modified data | `weapons/frames.json` — `grenadeTypeId` on Thumper GL | Small edit |
| Modified data | `vfx/vfx_event_bindings.json` — new event bindings | Small edit |

---

## Future Expansion Path

Adding a new grenade type (e.g. EMP) requires only:
1. New entry in `grenades.json` with `fuseType`, blast params, and `statusEffect: "EMP_DISABLED"`
2. Optionally a new VFX binding for type-specific explosion visuals
3. No code changes — `GrenadeSystem`, `ExplosionSystem`, and `StatusEffectSystem` handle it generically
