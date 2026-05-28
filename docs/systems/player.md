# Player Systems

The `player` package implements the player's FPS controller, input aggregation, camera, aiming mechanics, weapon feel systems, and the state machine that governs the transition between walking and piloting a ship.

---

## State Machine

The player operates in one of two top-level states stored in `PlayerStateComponent`:

| State | Description |
|---|---|
| `WALKING` | FPS first-person locomotion inside ships, stations, or on planetary surfaces |
| `PILOTING` | Seated in a ship cockpit; movement input is forwarded to `ShipFlightSystem` instead |

**`PilotTransitionSystem`**

Watches for `PlayerEnterShipEvent` and `PlayerExitShipEvent` from the event bus. When entering a ship, teleports the player to the pilot seat position from `PilotSeatComponent`, switches `PlayerStateComponent` to `PILOTING`, and publishes `PlayerStartPilotingEvent`. The reverse happens on exit.

---

## Input

**`PlayerInputSystem`**

Polls libGDX `Gdx.input` each frame and gamepad state via `gdx-controllers`. Aggregates into `PlayerInputComponent`:
- `forward / strafe / vertical` — movement axes (−1 to +1)
- `sprint / crouch / jump` — boolean flags
- `firing / aiming / reloading` — combat action flags
- `aimDirection` — normalised 3D vector from camera look direction

All other systems read from `PlayerInputComponent`; none call `Gdx.input` directly.

---

## Movement

**`PlayerMovementSystem`** (priority 1)

FPS capsule controller built on top of Bullet physics:

- **Ground detection:** casts a short ray downward from the capsule base each frame to determine slope angle and surface material.
- **Slope handling:** applies a gravity-compensation force perpendicular to the slope so the player doesn't slide on walkable angles; slopes steeper than the configured maximum cause sliding.
- **Jumping:** applies an upward impulse; air control is reduced while airborne.
- **Sprinting / crouching:** scales move speed from `MovementStateComponent`; crouching also adjusts the capsule half-height.
- **Stamina:** sprinting drains `MovementStateComponent.stamina`; exhaustion locks out sprinting until stamina partially recovers.

The system reads `PlayerInputComponent` and writes `MovementStateComponent` (stance, ground contact, stamina, slope angle). Applies forces to `PhysicsBodyComponent` (the capsule rigid body).

---

## Camera

**`CameraSystem`**

First-person camera positioned at `FPSCameraComponent.headPosition` relative to the capsule. Each frame:
1. Applies mouse/gamepad look delta to `FPSCameraComponent.pitch` and `.yaw` (clamped to ±90° pitch).
2. Updates the libGDX `PerspectiveCamera` position and direction.

**`ShipCameraSystem`** (in `ship/systems/`)

Third-person camera that tracks the ship when the player is in `PILOTING` state.

**`SwimCameraSystem`** (in `planet/terrain/` and `water/systems/`)

Underwater camera with refraction tint and buoyancy bob.

---

## Aiming & Weapon Feel

**`ADSSystem`** — Aim-Down-Sights. When `PlayerInputComponent.aiming` is true, lerps `ADSComponent.zoomLevel` toward the weapon's ADS FOV and reduces `ADSComponent.sensitivityMultiplier`. Affects both camera FOV and mouse sensitivity.

**`CrosshairSystem`** — Computes dynamic crosshair spread from movement speed, firing rate, and stance. Writes `CrosshairComponent.spread` which the HUD reads for reticle rendering.

**`RecoilSystem`** — On `WeaponFiredEvent` from the event bus, adds a random kick to `RecoilComponent.currentOffset` (within configurable cone). Each frame, lerps the offset back toward zero at the weapon's recovery rate. The camera pitch/yaw is nudged by this offset, creating viewkick.

**`WeaponSwaySystem`** — Reads movement velocity and applies slow sinusoidal sway to weapon position to simulate breathing and carry movement.

**`ScreenShakeSystem`** — Subscribes to explosion events. Adds trauma to `ScreenShakeComponent`; each frame, decays trauma and displaces the camera by a noise-sampled offset proportional to trauma².

---

## Interaction

**`InteractionSystem`**

Each frame, casts a short ray from the camera centre. If the ray hits an interactable entity (ship entry point, NPC, container) within range, publishes `InteractionPromptEvent` so the HUD can display the prompt. On interaction input, executes the action (triggers `PlayerEnterShipEvent`, opens a container, etc.).

---

## Animation

**`PlayerAnimationSystem`**

Drives the player body model animation state machine from `MovementStateComponent` and `ADSComponent`:
- Idle / Walk / Sprint / Crouch-Walk / Jump / Fall
- ADS blend layer on the upper body
- Reload / Fire animation triggers on weapon events

---

## Skills & Progression

### RealTimeSkillSystem

Core runtime skill engine. The public entry point is:

```
awardSkillXP(player, skill, baseXP, difficulty)
```

XP is scaled by the difficulty modifier before being added to the skill's running total. Character level is derived from aggregate XP across all skills:

```
characterLevel = 1 + (int) sqrt(totalXP / 250)
```

On each level-up the system grants:
- **2 skill points** per level (3 on every 3rd level).
- **+1 `unspentPerkPicks`** every 5 character levels.

Published events:
- `SkillLevelUpEvent` — when an individual skill's level increases.
- `CharacterLevelUpEvent` — when the derived character level increases.
- `PerkAvailableEvent` — when `unspentPerkPicks` becomes non-zero (i.e. a pick was just granted).

---

### SkillXpAwardSystem

A centralised `EntitySystem` that subscribes to gameplay events and forwards player-sourced actions to `awardSkillXP`. This keeps every other system ignorant of XP mechanics.

| Skill | Source event | Award |
|---|---|---|
| Firearms | `DamageDealtEvent` (type BALLISTIC, attacker = player) | `finalDamage × 0.1` |
| Energy Weapons | `DamageDealtEvent` (type ENERGY or PLASMA, attacker = player) | `finalDamage × 0.1` |
| Firearms (kill bonus) | `EntityKilledEvent` (killer = player) | +15 |
| Melee | `MeleeHitEvent` (attacker = player) | `damage × 0.15` |
| Mining | `ResourceCollectedEvent` | `amount × 2` |
| Trading | `TradeCompletedEvent` | `totalPrice × 0.01` |
| Repair | `HullRepairEvent` (player) | +10 |
| Stealth | `AwarenessChangedEvent` (NPC transitions back to UNAWARE) | +20 |
| Athletics | Accrual in `update()`: player sprinting on ground | 1 XP per 10 units of distance |
| Piloting | Accrual in `update()`: `PlayerStateComponent.mode == PILOTING` | 2 XP per second |

> **TODO hook:** ship-module repair beyond hull (`HullRepairEvent`) has no source event yet; a `ModuleRepairEvent` is the intended trigger.

---

### PerkRegistry

Loads perk tree definitions from `data/player/perk_trees.json` at startup. There are **9 trees** — one per real-time skill — each with **3 tiers** of nodes.

Each `PerkNodeDef` carries:
- `id` — unique string identifier.
- `treeSkill` — which real-time skill this node belongs to.
- `tier` — 1, 2, or 3.
- `requiredSkillLevel` — minimum level for the parent skill.
- `prerequisitePerkIds` — list of perk ids that must already be owned.
- `modifiers` — list of data-driven `PerkModifier` records (`target`, `op`, `value`).
- `specialEffectId` *(optional)* — named hook consumed by systems that implement bespoke logic.

**Gate check — `canSelect(stats, id)`** returns true only when all of the following hold:
1. The perk is not already owned by the player.
2. The player's relevant skill meets `requiredSkillLevel`.
3. All `prerequisitePerkIds` are present in `stats.perks`.

**Effect application:** data-driven modifiers are folded into stat results by `PlayerStatQuery.applyModifiers`. Special-effect ids are checked at call-sites via `PerkRegistry.has(stats, id)`.

---

### PerkSystem

Handles perk selection at the player's request:

```
selectPerk(player, id)
```

- Requires `stats.unspentPerkPicks > 0`.
- Requires `PerkRegistry.canSelect(stats, id)` to pass.
- Perk selection is **permanent** — there is no respec mechanic.
- On success: adds `id` to `stats.perks`, decrements `unspentPerkPicks`, publishes `PerkSelectedEvent`.

---

### PlayerStatQuery (updated)

When a `PerkRegistry` is injected via `setPerkRegistry(registry)`, all 8 stat query methods fold in the owned-perk modifiers before returning. Added query:

```
getOutgoingDamageMultiplier(stats, DamageType)
```

Player outgoing damage is scaled in `DamageSystem.processDamage` — the single convergence point for both ranged and melee damage application. Melee perks therefore use the event's actual `DamageType` and are applied there; `MeleeSystem` itself is not modified.

---

### CharacterScreen (UI)

A new **"Character"** tab in the main UI, bound to the **C** key. It contains four sections:

1. **Character summary** — current character level and total XP with a progress bar to the next level.
2. **Real-time skills** — all 9 skills displayed with their current level and XP progress bar.
3. **Point skills** — all 8 point skills with permanent `+` / `−` allocation buttons; each press spends or refunds an `unspentPoints` token.
4. **Perk trees** — the 9 trees laid out tier by tier; nodes available for selection are highlighted when `unspentPerkPicks > 0` and `canSelect` passes; clicking an available node calls `PerkSystem.selectPerk`.

**`LevelUpToastOverlay`** — a transient HUD overlay that pops up on `CharacterLevelUpEvent`, `SkillLevelUpEvent`, and `PerkSelectedEvent`, showing a short message before fading out.

---

### Persistence

`PlayerStatsComponent` implements `Snapshotable<PlayerStatsSnapshot>` and is registered under the key `"PlayerStats"` with the save system. The player entity carries a `PersistenceIdComponent` so its stats are saved and restored across save/load cycles.

---

### Reserved special-effect ids

The following `specialEffectId` values are recorded in `perk_trees.json` and their `PerkRegistry.has()` calls are wired, but the systems that consume them do not exist yet. They are documented here so future implementers know the intended hook names:

| Id | Intended effect |
|---|---|
| `firearms_rapid_reload` | Reduced reload time for ballistic weapons |
| `energy_heat_sink` | Faster heat dissipation for energy weapons |
| `melee_executioner` | Bonus damage vs low-health targets |
| `piloting_evasive` | Dodge window on incoming missile lock |
| `piloting_ace` | Enhanced manoeuvring thrusters at high speed |
| `athletics_marathon` | Reduced stamina drain while sprinting |
| `athletics_free_runner` | Nullify fall damage below a threshold |
| `stealth_shadow` | Movement speed not reduced while crouching |
| `stealth_ghost` | NPC awareness raise rate reduced |
| `mining_rich_veins` | Bonus yield from asteroid mining |
| `mining_deep_core` | Unlock rare-tier minerals from standard deposits |
| `repair_field` | Allow hull repair while under fire |
| `repair_overhaul` | Periodic passive hull regeneration |

---

## Components Reference

| Component | Key fields |
|---|---|
| `PlayerInputComponent` | Movement axes, sprint/crouch/jump, fire/aim/reload, aimDirection |
| `PlayerStateComponent` | `mode` (`WALKING`/`PILOTING`), current ship entity reference |
| `MovementStateComponent` | Stance enum, `isGrounded`, `stamina`, `slopeAngle` |
| `FPSCameraComponent` | Head position offset, `pitch`, `yaw`, field of view |
| `ADSComponent` | `zoomLevel`, `sensitivityMultiplier` |
| `CrosshairComponent` | `spread`, `color`, `opacity` |
| `RecoilComponent` | `currentOffset`, `recoveryRate` |
| `ScreenShakeComponent` | `trauma`, `frequency`, `duration` |
| `PlayerTargetComponent` | Currently targeted entity, distance, name (for HUD) |
| `PlayerStatsComponent` | Skill map, total XP, character level, `unspentPoints`, `unspentPerkPicks`, owned `perks` set |
| `PlayerModelComponent` | Body model handle, current animation state |

---

## Events

| Event | When published |
|---|---|
| `PlayerStateEvent` | Player transitions between WALKING and PILOTING |
| `InteractionPromptEvent` | Interactable enters or leaves the player's crosshair |
| `PlayerEnterShipEvent` | Player steps into an airlock |
| `PlayerExitShipEvent` | Player leaves the ship |
| `PlayerStartPilotingEvent` | Player sits in the pilot seat |
| `PlayerStopPilotingEvent` | Player stands up from the pilot seat |
| `SkillLevelUpEvent` | An individual real-time skill's level increases |
| `CharacterLevelUpEvent` | Derived character level increases |
| `PerkAvailableEvent` | `unspentPerkPicks` becomes non-zero after a level-up grant |
| `PerkSelectedEvent` | Player confirms a perk node selection |
