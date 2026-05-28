# Hacking System Design

**Date:** 2026-05-27  
**Status:** Approved

---

## Overview

A real-time, circuit-puzzle hacking system that lets the player interact with any electronic entity in the game world — terminals, doors, cameras, turrets, ship subsystems, and drones. Hacking skill gates access and modifies puzzle difficulty. Failure triggers alarms and a lockout cooldown. Everything hackable is data-driven; no targets are hardcoded.

---

## Architecture

Three layers, cleanly separated per the project's event-driven and testability rules.

### ECS Layer

**`HackableComponent`** — added to any entity that can be hacked:
- `String typeId` — key into `hackable_types.json`
- `int difficulty` (1–5)
- `HackEffect effect` — what succeeding does
- `float lockoutTimer` — counts down after failure; while > 0, target is locked out
- `float effectTimer` — counts down active timed effects (disable, subvert, etc.)
- `boolean requiresPhysicalAccess` — false allows remote hacking
- `float interactionRange` — default 2.5m for physical targets

**`HackingStateComponent`** — added to the player entity:
- `Entity currentTarget` — entity currently being hacked (null if idle)
- `HackingController controller` — active puzzle instance (null if idle)

**`HackingSystem`** (Ashley `IteratingSystem` over `HackableComponent`, runs each frame):
- Ticks `lockoutTimer` and `effectTimer` on all hackable entities
- Publishes `HackEffectExpiredEvent` when `effectTimer` reaches zero and restores prior state

**`PlayerHackingSystem`** (iterates player entities):
- Each frame, checks all `HackableComponent` entities for range against player position
- Physical range: `interactionRange` (default 2.5m); requires adjacency
- Remote range: base 10m, +2m per HACKING rank above 5; requires `HACKING ≥ 5`
- Publishes `InteractionPromptEvent("[F] Hack Terminal [Rank 3+]")` for the nearest in-range target
- Tab cycles between multiple in-range targets
- On F-press: instantiates `HackingController`, stores on `HackingStateComponent`, publishes `HackStartedEvent`
- Each frame while hacking: checks target still alive and (for physical hacks) still in range; cancels cleanly if not
- On `HackSucceededEvent`: applies the effect (see Effects table); on `HackFailedEvent`: starts lockout, publishes `SecurityAlarmEvent`
- If combat starts while hacking: cancels hack, counts as failure

### Controller Layer

**`HackingController`** — plain Java object, no libGDX or GL dependencies:
- Owns the `PuzzleGrid` (2D array of `GridTile`)
- Owns the countdown timer
- State machine: `IDLE → ACTIVE → SUCCESS | FAILED`
- `rotateTile(x, y)` — rotates tile clockwise 90°, triggers BFS power propagation
- `tick(float dt)` — advances timer; emits `HackFailedEvent` via injected `EventBus` on expiry
- `checkWin()` — BFS from source; if all target nodes powered, emits `HackSucceededEvent`
- Fully unit-testable without a GL context

**`PuzzleGrid`**:
- `GridTile[][]` — each tile has a `TileType` and a rotation (0–3)
- `TileType`: `STRAIGHT`, `ELBOW`, `TEE`, `CROSS`, `EMPTY`
- `propagatePower()` — BFS from source tile; marks each tile powered/unpowered based on connector alignment

### UI Layer

**`HackingOverlay`** — Scene2D actor:
- Subscribes to `HackStartedEvent` — appears and renders the grid
- Renders tiles as ASCII/icon grid; powered tiles glow, unpowered tiles are dim
- Click on a tile → calls `HackingController.rotateTile(x, y)`
- Displays countdown timer and current target name
- Subscribes to `HackSucceededEvent` / `HackFailedEvent` — plays animation then hides

---

## The Circuit Puzzle

A power-routing puzzle. A source tile must connect to one or more target nodes via a chain of rotated connector tiles.

### Tile Types

| Symbol | Name | Openings |
|---|---|---|
| │ / ─ | `STRAIGHT` | Two opposite sides |
| └ ┐ ┘ ┌ | `ELBOW` | Two adjacent sides |
| ├ ┤ ┬ ┴ | `TEE` | Three sides |
| ┼ | `CROSS` | All four sides |
| · | `EMPTY` | None (blocker) |

All types except `CROSS` and `EMPTY` are rotatable in 90° increments. Left-click rotates clockwise.

### Difficulty Scaling

| Difficulty | Grid size | Time limit |
|---|---|---|
| 1 | 3×3 | 30s |
| 2 | 3×3 | 20s |
| 3 | 4×4 | 30s |
| 4 | 4×4 | 20s |
| 5 | 5×5 | 25s |

### Skill Modifiers

- Each HACKING rank **above** the target's difficulty pre-rotates that many tiles correctly (shown lit up, saving the player from having to solve them).
- HACKING rank **equal** to difficulty: no assists.
- HACKING rank **below** difficulty: no assists and −5s on the time limit.
- **Remote hacking** (HACKING ≥ 5): +1 effective difficulty, −10s time limit.

### Win Condition

All target nodes simultaneously connected to the power source via a valid path. Power propagation recalculates via BFS after every tile rotation.

---

## Hackable Targets and Effects

On `HackSucceededEvent`, `HackingSystem` reads `HackEffect` and applies it:

| Target | Effect | Duration |
|---|---|---|
| Door / Airlock | `UNLOCK` — removes locked flag, door opens | Permanent |
| Security terminal | `ACCESS_DATA` — publishes `DataAccessedEvent` (loot/intel, mission progress) | Permanent |
| Camera | `DISABLE_CAMERA` — blinds detection cone | 60s |
| Turret | `DISABLE_TURRET` — zeroes AI target acquisition | 45s |
| Ship engines | `DISABLE_ENGINES` — cuts thruster force | 30s |
| Ship weapons | `DISABLE_WEAPONS` — prevents firing | 30s |
| Ship shields | `DISABLE_SHIELDS` — drops regen and active shield | 20s |
| Drone | `SUBVERT_DRONE` — flips faction allegiance to player | 90s |

Timed effects store remaining time in `HackableComponent.effectTimer`. `HackingSystem` ticks this each frame and publishes `HackEffectExpiredEvent` on expiry, restoring prior state.

---

## Failure and Alarms

**Failure triggers:**
- Timer reaches zero
- Player killed mid-hack
- Player moves out of physical range mid-hack
- Target destroyed mid-hack (clean cancel, no alarm)
- Combat begins while hacking (cancel + alarm)

**On failure:**
- `HackFailedEvent` published
- `SecurityAlarmEvent(location, radius)` published — NPC/security AI responds (guards investigate, reinforcements, camera sweep)
- `HackableComponent.lockoutTimer = lockoutDuration` (from JSON config, typically 30–60s)
- While locked out, interaction prompt shows "LOCKED OUT (Xs)" — no retry until timer expires

---

## Data-Driven Configuration

`core/src/main/resources/data/hacking/hackable_types.json`:

```json
{
  "standard_door":      { "difficulty": 1, "effect": "UNLOCK",           "lockoutDuration": 30, "requiresPhysicalAccess": true,  "interactionRange": 2.5 },
  "turret_mk1":         { "difficulty": 2, "effect": "DISABLE_TURRET",   "lockoutDuration": 45, "requiresPhysicalAccess": false, "interactionRange": 2.5 },
  "security_terminal":  { "difficulty": 3, "effect": "ACCESS_DATA",      "lockoutDuration": 45, "requiresPhysicalAccess": true,  "interactionRange": 2.5 },
  "combat_drone":       { "difficulty": 3, "effect": "SUBVERT_DRONE",    "lockoutDuration": 45, "requiresPhysicalAccess": false, "interactionRange": 2.5 },
  "ship_engines":       { "difficulty": 4, "effect": "DISABLE_ENGINES",  "lockoutDuration": 60, "requiresPhysicalAccess": false, "interactionRange": 2.5 },
  "ship_weapons":       { "difficulty": 4, "effect": "DISABLE_WEAPONS",  "lockoutDuration": 60, "requiresPhysicalAccess": false, "interactionRange": 2.5 },
  "ship_shields":       { "difficulty": 5, "effect": "DISABLE_SHIELDS",  "lockoutDuration": 60, "requiresPhysicalAccess": false, "interactionRange": 2.5 },
  "camera_mk1":         { "difficulty": 2, "effect": "DISABLE_CAMERA",   "lockoutDuration": 30, "requiresPhysicalAccess": false, "interactionRange": 2.5 }
}
```

A `HackableTypeRegistry` (loaded by the existing data-loading infrastructure) resolves `typeId → HackableComponent` at spawn time.

---

## Edge Cases

| Scenario | Behaviour |
|---|---|
| Player dies mid-hack | Controller cancelled, no event, overlay dismissed |
| Target destroyed mid-hack | Clean cancel, no alarm, no lockout |
| Player moves out of physical range | Clean cancel, no alarm |
| Multiple targets in range | Nearest gets prompt; Tab cycles |
| Remote hack while moving | Unaffected by player movement |
| Hack already locked out | Prompt shows timer; F-press does nothing |

---

## Testing

`HackingController` and `PuzzleGrid` have no libGDX dependencies — unit tests cover:

- BFS power propagation correctness after rotations
- Win condition detection (all targets powered)
- Timer expiry emits `HackFailedEvent`
- Skill rank → pre-placed tile count
- Remote difficulty penalty (+1 difficulty, −10s)
- Partial path does not trigger win

`HackingSystem` / `PlayerHackingSystem` integration tests use a headless Ashley engine:

- Lockout countdown and expiry
- Effect timer countdown and `HackEffectExpiredEvent`
- Range check cancels physical hack when player moves away
- `SecurityAlarmEvent` published on failure (not on clean cancel)

---

## New Package

`core/src/main/java/com/galacticodyssey/hacking/`

```
hacking/
  HackableComponent.java
  HackingStateComponent.java
  HackEffect.java              (enum)
  HackingController.java
  PuzzleGrid.java
  GridTile.java
  TileType.java
  systems/
    HackingSystem.java
    PlayerHackingSystem.java
  data/
    HackableTypeData.java
    HackableTypeRegistry.java
  events/
    HackStartedEvent.java
    HackSucceededEvent.java
    HackFailedEvent.java
    HackEffectExpiredEvent.java
  ui/
    HackingOverlay.java
```
