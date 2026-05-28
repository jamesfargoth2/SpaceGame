# Vehicle Bay & Deployment System — Design

**Date:** 2026-05-28
**Status:** Approved (pending spec review)
**Scope:** Full store → deploy → drive → retrieve loop at MVP depth, including data-driven
vehicle definitions, a player driving mode, and combat-ready vehicles.

---

## 1. Goal

Let the player store ground vehicles inside a ship's vehicle bay, deploy one onto a planet
surface via the ship ramp, walk up to it and drive it (with working weapons and damageable
armor), then drive it back and retrieve it into the bay.

This closes the `docs/TODO-systems.md` gap: *"No `VehicleBayComponent`, no bay slot UI, no
ramp open/deploy/retrieve flow, no `VehicleRegistry`."*

## 2. Existing foundations (reused, not rebuilt)

- **`GroundVehicleComponent` + `SurfaceVehicleSystem`** (`planet/terrain/`) — surface driving
  physics (traction, regolith sinkage, low-grav liftoff risk, thermal). Currently the throttle
  is hardcoded to `1.0f` and there is no steering; this system is extended (not replaced).
- **Player state machine** — `PlayerStateComponent.PlayerMode` (`ON_FOOT_EXTERIOR`,
  `ON_FOOT_INTERIOR`, `PILOTING`) driven by `InteractionSystem` (E-key), event-driven via
  `EventBus`. The ship-pilot transition is the template this feature mirrors.
- **Combat pipeline** — `CombatInputComponent` (`fireRequested`, `fireHeld`, `aimDirection`)
  + `RangedWeaponComponent` → `WeaponFireSystem` publishes `WeaponFiredEvent` →
  `HitscanSystem`/`ProjectileSystem` → `DamageSystem`. Targets need `HitboxComponent` +
  `HealthComponent`; `ArmorComponent` mitigates. A vehicle entity carrying these components is
  **both** a damageable target and a weapon platform with **no new combat code**.
- **Registry/data patterns** — `ShipWeaponRegistry`, `GrenadeDataRegistry` show the JSON-loaded,
  lookup-by-id registry convention.
- **Snapshot persistence** — `Snapshotable<T>` + the snapshot registry (e.g. `HealthComponent`,
  `RangedWeaponComponent`, `PlayerStateComponent` already implement it).
- **Scene2D UI** — the crew-recruitment screen is the precedent for the bay panel.

## 3. Architecture decision

**Mirror the `libgdx-ship-pilot-transition` architecture.** Vehicle entry/drive/exit parallels
ship entry/pilot/exit: a new `PlayerMode`, transition logic in `InteractionSystem`, a dedicated
control system gated on the mode, a dedicated camera system, and event-driven decoupling.

Rejected alternatives:
- **Generalize a "rideable" abstraction** unifying pilot seat + ground vehicle — large refactor
  of working ship code, regression risk, scope creep. Not for MVP.
- **Fold logic into `InteractionSystem`/`SurfaceVehicleSystem`** — bloats those classes, couples
  input into physics, hurts isolated testability.

## 4. Data model & registry

### `VehicleDefinition` (package `data/`, loaded from `resources/data/vehicles/*.json`)

| Field | Purpose |
|---|---|
| `id`, `displayName`, `modelPath`, `sizeClass` | Identity & rendering |
| `mass`, `wheelbase`, `trackWidth`, `groundClearance` | Physics → `GroundVehicleComponent` |
| `maxDriveForce`, `maxSteerAngle`, `anchorBreakForce`, `dynamicLift` | Physics → `GroundVehicleComponent` |
| `maxHP`, `armorValue` | Survivability → `HealthComponent` + `ArmorComponent` |
| `weapon` (nested object) | Inline weapon stats: `damage`, `fireRate`, `range`, `hitscan`, `projectileSpeed`, `damageType`, `magSize`, `reloadTime` → populate a `RangedWeaponComponent` |
| `baySlots` | How many bay slots this vehicle occupies |

**Weapon stats are inlined in the vehicle JSON** (decision: 2026-05-28). The codebase has no
lightweight `weaponId → stats` registry — FPS weapons use the modular `WeaponAssembly` +
`WeaponInventoryComponent` machinery and ship weapons use a separate `ShipWeaponRegistry`.
Pulling either onto vehicles is scope creep for an MVP. Inlining maps directly to
`RangedWeaponComponent` while still reusing the full damage/projectile/hitscan/armor pipeline
downstream (see §5).

### `VehicleRegistry`

Loads and holds all `VehicleDefinition`s at bootstrap, lookup by `id`. Follows the
`ShipWeaponRegistry` / `GrenadeDataRegistry` pattern. Ships 2–3 starter vehicles as JSON
(e.g. light rover, armed scout, tank).

### `VehicleBayComponent` (on ship entities)

| Field | Purpose |
|---|---|
| `int capacity` | Total bay slots |
| `List<String> storedVehicleIds` | Definition ids currently stored |
| `Vector3 localRampSpawnPosition` | Ship-local spawn point beside the ramp |
| `float triggerRadius` | Retrieval proximity to the ramp |

`Snapshotable` (persists `capacity` + `storedVehicleIds`).

## 5. Vehicle entity assembly & combat integration

**`VehicleFactory`** — given a `VehicleDefinition` + spawn transform, builds an `Entity` with:

- `TransformComponent`
- `PhysicsBodyComponent` — Bullet rigid body sized from wheelbase/track/clearance, registered in
  the **exterior** dynamics world
- `GroundVehicleComponent` — populated from the definition's physics params
- `HealthComponent` (`maxHP`) + `ArmorComponent` (`armorValue`)
- `HitboxComponent` — makes the vehicle a valid hitscan/projectile target
- `RangedWeaponComponent` (populated directly from the definition's inline `weapon` stats) +
  `CombatInputComponent` (driver routes fire/aim here)
- `VehicleEntryPointComponent` — `triggerRadius` + local exit offset for walk-in/out
- `VehicleTagComponent` — holds the source `VehicleDefinition` id (retrieval/serialization)
- render/model component matching how ships/props render

**Firing trigger:** a thin `VehicleWeaponSystem` (family
`VehicleTagComponent + CombatInputComponent + RangedWeaponComponent + TransformComponent`)
advances the fire-rate timer, decrements ammo, and publishes `WeaponFiredEvent(vehicle,
aimDirection, hitscan, muzzlePos)` — the **same event the player's `WeaponSystem` publishes**.
This deliberately does *not* reuse `WeaponSystem` itself (which requires a
`WeaponInventoryComponent` and handles player melee/switching). MVP supports SEMI + AUTO fire
modes only.

Because the downstream combat systems key only on the event + component presence, the vehicle is
simultaneously a damageable target (`HitboxComponent` + `HealthComponent`, mitigated by
`ArmorComponent` via `DamageSystem`) and a firing platform — reusing the entire damage/projectile/
hitscan pipeline. Destruction reuses the existing `EntityKilledEvent` path.

## 6. Player mode, control & camera

### State machine

```
ON_FOOT_EXTERIOR ──[interact] near deployed vehicle──► DRIVING ──[interact] while driving──► ON_FOOT_EXTERIOR
```

The interact key is the existing `PlayerInputComponent.interactPressed` (prompts render as
`[F]`, matching the current `InteractionSystem`).

Add `DRIVING` to `PlayerMode`. `PlayerStateComponent` gains `Entity currentVehicle`
(+ `UUID currentVehicleId` for save/load, mirroring `currentShip`).

### Enter (walk-in)

Player on foot within the vehicle's `VehicleEntryPointComponent.triggerRadius` → prompt
`[F] Enter Vehicle`. On press: `freezePlayerBody(player)` (existing pattern),
`currentMode = DRIVING`, `state.currentVehicle = vehicle`, publish `PlayerEnterVehicleEvent`.

### Drive

**`VehicleControlSystem`** (runs only when `DRIVING`):
- Reads `PlayerInputComponent` (W/S → throttle, A/D → steer), writes new `throttleInput` and
  `steerInput` fields on `GroundVehicleComponent`.
- Routes fire input + camera aim direction into the vehicle's `CombatInputComponent`.

**`SurfaceVehicleSystem` change** — read `throttleInput` (replace the hardcoded `1.0f`) and apply
steering torque from `steerInput × maxSteerAngle`.

**`PlayerMovementSystem`** — early-return when `currentMode == DRIVING` (same guard as `PILOTING`).

### Camera

**`VehicleCameraSystem`** — chase cam behind/above the vehicle, lerped position/direction; reuses
the `ShipCameraSystem` CHASE approach. Mutually exclusive with `CameraSystem` (which early-returns
during `DRIVING`). Initialize lerp state from the current camera pose on the first `DRIVING` frame
to avoid a snap.

### Exit / Retrieve

- **Exit:** `[interact]` while driving → `unfreezePlayerBody` at a spot beside the vehicle,
  `currentMode = ON_FOOT_EXTERIOR`, `state.currentVehicle = null`, publish `PlayerExitVehicleEvent`.
- **Retrieve:** vehicle within the ship bay's `triggerRadius` of the ramp, `[interact]` press → vehicle id
  returns to `VehicleBayComponent.storedVehicleIds`, entity removed from engine, publish
  `VehicleRetrievedEvent`. If the player is driving it at the moment of retrieval, they are first
  dropped to `ON_FOOT_EXTERIOR`.

## 7. Deploy flow (bay UI)

`VehicleBayPanel` (Scene2D.UI), following the crew-recruitment-screen precedent:
- List of stored vehicles (name, class, HP/armor, weapon), a Deploy button per row, a capacity
  indicator.
- Opened via an interaction prompt when the player is in the vehicle bay room.
- Deploy: `VehicleFactory` spawns the entity at the ship's `localRampSpawnPosition` (world-space,
  beside the ramp), removes its id from `VehicleBayComponent.storedVehicleIds`, publishes
  `VehicleDeployedEvent(vehicle, ship)`.
- The panel subscribes to `VehicleDeployedEvent`/`VehicleRetrievedEvent` to refresh.
- No simulation logic in the UI — it issues deploy/retrieve commands through the event bus / a
  small service only.

**Gating:** deploy requires the ship ramp to be deployed (`ShipEntryPointComponent.rampDeployed`).
No strict "landed on surface" check for MVP.

## 8. Events

| Event | When | Typical subscribers |
|---|---|---|
| `VehicleDeployedEvent(vehicle, ship)` | Vehicle spawned from bay | UI refresh, audio (ramp/engine) |
| `VehicleRetrievedEvent(vehicleId, ship)` | Vehicle returned to bay | UI refresh, audio |
| `PlayerEnterVehicleEvent(player, vehicle)` | Player enters driver mode | HUD (vehicle HUD), audio |
| `PlayerExitVehicleEvent(player, vehicle)` | Player leaves driver mode | HUD, audio |

## 9. Room type

Add a `VEHICLE_BAY` entry to `RoomType` (currently missing) with size bounds and colors so
interior generation can place bays.

## 10. Persistence & networking

- **Persistence:** `VehicleBayComponent` is `Snapshotable` (persists `capacity` +
  `storedVehicleIds`); bay contents survive save/load via the snapshot registry. **Out of MVP
  scope:** deployed-in-world vehicles are not persisted — on reload they are considered returned
  to/absent from the world (documented limitation).
- **Networking:** state changes are event-driven and component-based (server-authoritative
  friendly), but full KryoNet replication of vehicles is **out of MVP scope** (future work).

## 11. Integration points

- Register new systems in `GameWorld`:
  - `VehicleControlSystem` — priority alongside `InteractionSystem` (0).
  - `VehicleWeaponSystem` — priority 4 (alongside the player `WeaponSystem`, before
    `HitscanSystem` at 6).
  - `VehicleCameraSystem` — priority 4 (with the other camera systems).
  - `SurfaceVehicleSystem` already runs at priority 5 (physics).
- Load `VehicleRegistry` at bootstrap (where other registries load).
- Extend `InteractionSystem` for `DRIVING` transitions (enter/exit/retrieve), following the
  ship-entry method structure.

## 12. Testing (JUnit 5 + Mockito, no GL context)

- `VehicleRegistry` loads definitions from JSON correctly.
- `VehicleFactory` produces an entity with all required components populated from a definition.
- `SurfaceVehicleSystem` respects `throttleInput`/`steerInput` — zero throttle → no drive force;
  nonzero steer → steering torque applied.
- `VehicleWeaponSystem` fires on input (publishes `WeaponFiredEvent`, decrements ammo, respects
  fire-rate timer); does not fire on empty mag.
- Deploy removes from bay + spawns entity; retrieve returns id to bay + despawns; capacity enforced.
- State transitions: enter → `DRIVING`; exit → `ON_FOOT_EXTERIOR`; `PlayerMovementSystem` guarded
  off while `DRIVING`.

## 13. Out of scope (MVP)

- Persisting deployed-in-world vehicles across save/load.
- Networked replication of vehicles.
- Drop-pod / orbital deployment (ramp walk-in only).
- Vehicle interiors, multi-crew vehicles, mounted-turret passenger seats.
- "Landed on surface" gating (ramp-deployed gating only).
