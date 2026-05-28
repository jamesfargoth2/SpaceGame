# Ship Boarding Pipeline — Design

**Date:** 2026-05-28
**Status:** Approved design, ready for implementation planning
**Scope:** Full 5-phase boarding pipeline, bidirectional (player↔NPC)

## 1. Overview

Implements the full ship boarding flow from [DESIGN.md §4.3](../../DESIGN.md):

1. **Disable** the target ship's engines (targeted fire / EMP).
2. **Attach** via docking clamp *or* breaching pod.
3. **Transition** the player into the enemy ship's interior (FPS).
4. **Fight** through corridors with an away team against the enemy crew.
5. **Resolve**: hijack, scrap, ransom, or tow the captured ship.

The pipeline is **bidirectional**: hostile NPC ships can also disable, board, and attempt
to capture the player's ship.

### Decisions locked during brainstorming

| Question | Decision |
|---|---|
| Completeness | Full pipeline, all 5 phases (including tow + player garage) |
| Engine disable | Targetable subsystems + dedicated EMP weapon |
| Attach method | Both docking clamp **and** breaching pod (player chooses) |
| FPS combat | Player + crew away team vs. defenders from enemy crew roster |
| Direction | Bidirectional (NPCs can board the player) |
| Win condition | Capture the bridge **OR** eliminate all defenders (whichever first) |
| NPC aggressor attach | Breaching pod only (skips precise autopilot-docking AI) |

## 2. Architecture

**Approach C — Hybrid.** A single `BoardingOperationComponent` holds shared operation
state and persistence (mirroring the existing `DockingStateComponent` + `Snapshotable`
pattern). Each phase's *work* lives in its own small, independently-testable system that
reacts to events and writes phase transitions back to the component. A tiny
`BoardingOrchestratorSystem` owns only the phase-transition rules.

This satisfies the CLAUDE.md architectural rules: event-driven communication, modular and
isolated testability (no GL context), data-driven content, and server-authoritative-ready
(state lives in a component, not in rendering/UI).

The operation component lives on the **boarding target ship** (the ship being boarded), so
the same systems run symmetrically whether the player or an NPC is the aggressor;
`playerIsAggressor` routes HUD vs. AI behaviour.

### Existing infrastructure reused

| Area | Existing asset | Path |
|---|---|---|
| Docking physics | `DockingApproachSystem`, `DockingCaptureSystem`, `DockingCaptureEvent` | `ship/docking/` |
| Player mode machine | `PlayerStateComponent.PlayerMode` (`ON_FOOT_INTERIOR`), `InteractionSystem`, `PilotTransitionSystem` | `player/` |
| Interior worlds | `ShipInteriorComponent` (isolated `btDynamicsWorld`), `ShipInteriorPhysicsSystem`, `ShipInteriorGenerator`, `InteriorLayout` (rooms, corridors, airlock, pilot seat) | `ship/` |
| Structural / breach | `StructuralIntegrityComponent` (zones, pressure, breach), `DamageCascadeSystem` | `ship/structure/` |
| Combat AI | `CombatAISystem`, `CombatAIComponent`, `SquadComponent`, 20+ behaviour-tree tasks | `combat/ai/`, `combat/systems/` |
| Ownership / crew | `FleetComponent`/`FleetMemberComponent` (factionId), `CrewMemberComponent`, `CrewAssignmentComponent` | `combat/fleet/`, `npc/crew/` |
| Reputation | reputation manager (ransom rewards) | faction system |
| Crafting / materials | materials registry (scrap output) | `crafting/` |
| Event bus | `EventBus` (publish/subscribe, snapshot isolation) | `core/EventBus.java` |
| Persistence | `Snapshotable`, `SnapshotComponentRegistry`, `DockingStateSnapshot` pattern | `persistence/` |
| System registration | `GameWorld` (Ashley `Engine`, priorities, DI) | `core/GameWorld.java` |

## 3. Data model

All new types under `com.galacticodyssey.ship.boarding`.

### `BoardingOperationComponent implements Component, Snapshotable`
On the **target** ship; added lazily when the ship is first disabled.

```
enum BoardingPhase {
    NONE,
    DISABLING,        // aggressor is shooting out subsystems
    VULNERABLE,       // engines down — attach now allowed
    ATTACHING,        // clamp aligning OR breaching pod in flight
    BREACHED,         // hard connection established, entry point open
    INTERIOR_COMBAT,  // boarders inside, fighting
    RESOLVING,        // win condition met, resolution menu open
    RESOLVED          // outcome applied; operation complete
}
enum AttachMethod { CLAMP, BREACH_POD }

BoardingPhase phase = NONE;
Entity aggressorShip;       // who is boarding
Entity targetShip;          // self (convenience)
AttachMethod attachMethod;
Entity entryPoint;          // interior spawn: airlock (clamp) or breach hole (pod)
boolean playerIsAggressor;  // routes HUD vs AI defense
```

### `ShipSubsystemsComponent implements Component, Snapshotable`
Functional subsystem state, distinct from `StructuralIntegrityComponent` (which models
physical hull zones/pressure). Related but separate concerns.

```
enum SubsystemType { ENGINES, SHIELDS, WEAPONS, LIFE_SUPPORT }
class Subsystem {
    float health, maxHealth;
    float empDisableTimer;   // > 0 means temporarily disabled
    boolean destroyed;
}
EnumMap<SubsystemType, Subsystem> subsystems;

boolean enginesOperational();  // ENGINES health > 0 && empDisableTimer <= 0
```

### `BreachingPodComponent implements Component`
On a launched breaching-pod entity: origin, target ship, velocity, impact point.

### `BridgeComponent implements Component`
Tags the interior room used as the capture objective (the existing cockpit/high-Z room).
Set by the interior generator.

### `OwnedShipComponent implements Component, Snapshotable`
Marks a ship's owner (`PLAYER` for captured/owned ships). Minimal per-ship ownership layer
(none exists today).

### `PlayerGarage` (registry, persisted)
Lightweight list of stored ship records (hull + loadout snapshot). Hijack and Tow both
register captured ships here. Reused by any future shipyard/fleet UI.

## 4. Phase detail

### Phase 1 — Disable subsystems (`ShipSubsystemSystem`)
- Subscribes to ship-weapon hit events. Maps hit location → subsystem via ship hardpoint/zone
  geometry (engines aft, etc.); routes damage to the matching `Subsystem`.
- **Damage typing:** weapon defs gain a `damageType` field (`KINETIC`, `ENERGY`, `EMP`).
  KINETIC/ENERGY reduce `health` (permanent until repaired). **EMP** sets `empDisableTimer`
  — non-lethal soft-disable, the "capture intact" path.
- Ticks `empDisableTimer` down each frame. When engines become non-operational, publishes
  `SubsystemDisabledEvent(ship, ENGINES)`.
- **EMP weapon** is added purely as a data-driven weapon definition (`damageType: EMP`, high
  subsystem damage, ~0 hull damage) plus a small handler branch for the `EMP` type.
- **Flight feedback:** `ShipFlightSystem` zeroes thrust/turn inputs when
  `!subsystems.enginesOperational()` (ship coasts).

### Phase 2 — Attach (`BoardingAttachSystem`)
Two paths converging on `BREACHED` + an `entryPoint`, so phase 3+ is method-agnostic.

- **Clamp (reuses docking):** when `DockingCaptureEvent(chaser, target)` fires AND target is
  `VULNERABLE` AND the ships are hostile, set `attachMethod = CLAMP`, `entryPoint = enemy
  airlock`, advance `ATTACHING → BREACHED`. No new docking code — just the hostile bridge.
- **Breaching pod:** a launched `BreachingPodComponent` entity flies ballistically to a
  raycast impact point on the target hull. On contact it marks a hull breach on the target's
  `StructuralIntegrityComponent` at the impact zone (reusing breach/venting cascade — interior
  becomes a vacuum hazard), creates the `entryPoint` at the breach, sets `attachMethod =
  BREACH_POD`, advances to `BREACHED`.
- On `BREACHED`, publishes `ShipBreachedEvent(aggressor, target, entryPoint, method)`.

### Phase 3 — Transition to interior (`BoardingEntrySystem`)
Reacts to `ShipBreachedEvent`:
1. Lazily build (if needed) and activate the target's `ShipInteriorComponent`
   (`active = true`) so `ShipInteriorPhysicsSystem` steps its isolated world.
2. Move the player's rigid body out of their current Bullet world into the **target ship's**
   `interiorWorld`; teleport to `entryPoint`. Set ship-local gravity (per pilot-transition
   skill rules).
3. Set `playerState.currentMode = ON_FOOT_INTERIOR`, `playerState.currentShip = targetShip`.
4. Hand camera to FPS `CameraSystem` (mutually exclusive with `ShipCameraSystem`; init lerp
   state to avoid snap).
5. Publish `PlayerEnteredHostileInteriorEvent(player, targetShip)`.
- **Abort/exit:** at the `entryPoint`, pressing E returns the player to their own ship
  (`PILOTING`), leaving the operation in `INTERIOR_COMBAT` (re-enterable). Prevents soft-locks.
- The interior generator tags one room with `BridgeComponent` (the cockpit/high-Z room).

### Phase 4 — FPS combat (`BoardingCombatSystem`)
- **Defenders:** spawned from the target ship's crew roster as entities with existing
  `CombatAIComponent` + behaviour tree + `SquadComponent`, positioned at interior choke points
  (corridor junctions, bridge entrance) using the layout's corridor grid. Count/skill scale
  with crew size and faction.
- **Away team:** the player's selected crew spawn at the `entryPoint` as friendly squad
  members following the player (existing follow/escort tasks).
- **Win conditions** (checked each frame in `INTERIOR_COMBAT`):
  - **Bridge captured** — player/away-team in `BridgeComponent` room, no live defenders in
    that room, console interact; OR
  - **All defenders eliminated** — defender count reaches zero (listens to `EntityKilledEvent`).
  - First to fire → publish `BoardingClearedEvent(aggressor, target)`, advance to `RESOLVING`.

### Phase 4b — Bidirectional (enemy boards player)
Because the operation lives on the target ship and `playerIsAggressor` flips, the same systems
run inverted when an NPC boards the player.
- **`EnemyBoardingAISystem`:** a hostile NPC ship that has disabled the player's engines decides
  to board, fires a **breaching pod** (no precise docking AI), and spawns **attacker** AI inside
  the player's interior.
- Player becomes defender: combat in the player's own interior; win condition inverted (player
  clears attackers = repel; attackers reach the player's bridge = player ship captured).

### Phase 5 — Resolution (`BoardingResolutionSystem`)
On `RESOLVING`, the player gets a resolution menu (event-driven UI). Outcomes:
- **Hijack** — flip `FleetMemberComponent.fleetId`/`factionId` to the player; roll surviving
  enemy crew loyalty/morale (mutiny vs. desertion); restore enough engine operation to fly.
  Success chance gated on the `Tactics` skill (per DESIGN.md). Registers an `OwnedShipComponent`
  + garage record.
- **Scrap** — strip components (weapons, modules, materials via materials registry) into player
  inventory/cargo; mark hull destroyed/removed.
- **Ransom** — contact owning faction; apply credits reward + reputation delta via the
  reputation manager; release the ship.
- **Tow** — disable flight, attach for retrieval, store in `PlayerGarage` (+ `OwnedShipComponent`).
- Publishes `BoardingResolvedEvent(target, outcome)` → `RESOLVED`; deactivates target interior
  once the player has exited, detaches clamp/pod, clears `BoardingOperationComponent`.
- **Enemy-captures-player** auto-resolves (no menu): a fail/escape-pod scenario (lose the ship).

## 5. Events

All in `ship/boarding/events/`, via `EventBus`. UI/audio/VFX/quest subscribe independently.

| Event | Fired when |
|---|---|
| `SubsystemDisabledEvent(ship, type)` | a subsystem goes non-operational |
| `ShipBoardableEvent(ship)` | target enters `VULNERABLE` (engines down) |
| `ShipBreachedEvent(aggressor, target, entryPoint, method)` | attach completes |
| `PlayerEnteredHostileInteriorEvent(player, target)` | player transitions into enemy interior |
| `BoardingClearedEvent(aggressor, target)` | win condition met |
| `BoardingResolvedEvent(target, outcome)` | resolution applied |

## 6. Systems & registration

Registered in `GameWorld` with injected `EventBus`/registries (existing wiring pattern).

| System | Role | Priority band |
|---|---|---|
| `BoardingOrchestratorSystem` | phase-transition rules only | early (~1) |
| `BoardingEntrySystem` | interior-world handoff, camera, mode switch | transitions (~2) |
| `ShipSubsystemSystem` | route weapon damage → subsystems, tick EMP | ship-combat (~10) |
| `EnemyBoardingAISystem` | NPC aggressor drives phases 1–3 (breach-pod only) | combat AI (~10) |
| `BoardingCombatSystem` | spawn defenders/away-team, win-condition tracking | after combat AI (~11) |
| `BoardingResolutionSystem` | apply hijack/scrap/ransom/tow | low freq (~12) |
| `BoardingAttachSystem` | breach-pod flight/impact + hostile-dock bridge | after docking (~8) |

`ShipFlightSystem` gains the engines-operational guard.

## 7. Persistence

`BoardingOperationComponent`, `ShipSubsystemsComponent`, `OwnedShipComponent`, and
`PlayerGarage` get `Snapshotable` implementations + registration in
`SnapshotComponentRegistry` (mirroring `DockingStateSnapshot`), so an in-progress boarding
survives save/load.

## 8. Data-driven additions

- `EMP` damage type + EMP weapon definition in ship-weapon JSON.
- Subsystem layout (zone → subsystem mapping) in ship-class data.
- Defender-scaling parameters in faction/crew data.

## 9. Testing (isolated, no GL context)

- `ShipSubsystemSystem`: EMP sets timer & recovers; kinetic destroys; engines-down zeroes thrust.
- `BoardingOrchestratorSystem`: each event drives the correct transition; illegal transitions rejected.
- `BoardingAttachSystem`: pod impact creates breach + entryPoint; hostile-dock bridges only when VULNERABLE + hostile.
- `BoardingEntrySystem`: player body moves between Bullet worlds; mode/camera set correctly.
- `BoardingCombatSystem`: both win conditions fire `BoardingClearedEvent`; defender count derived from crew roster.
- `BoardingResolutionSystem`: each outcome mutates ownership/inventory/reputation/garage correctly; enemy-capture auto-resolves.
- Headless integration test: full pipeline `NONE → RESOLVED` with mocked combat.

## 10. Out of scope (this pass)

- NPC aggressor precise autopilot-docking (NPCs always breach-pod).
- Shipyard/fleet-management UI beyond the minimal `PlayerGarage` registry.
- Multiplayer replication of the boarding operation (design is replication-ready via the
  state component, but wiring is deferred).
