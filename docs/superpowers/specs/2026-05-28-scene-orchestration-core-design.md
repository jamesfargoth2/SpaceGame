# Scene Orchestration Core — Design Spec

**Date:** 2026-05-28
**Status:** Approved (design); ready for implementation planning
**Author:** Brainstormed with James
**TODO reference:** [TODO-systems.md](../../TODO-systems.md) — "Scene / Sector streaming" (High Impact — Core Gameplay Loop Blockers)

---

## 1. Context & Scope

The TODO names the gap precisely:

> No `SceneManager` that additively loads/unloads zones (deep space → orbit → planet
> surface → station interior). `StreamingSystem` distance-enqueues assets but no zone
> transition state machine exists.

The supporting infrastructure **already exists** and is *not* re-implemented here:

| Existing piece | File | Role |
|---|---|---|
| `CoordinateManager` | `core/CoordinateManager.java` | Galaxy(double)↔local(float) conversion, floating-origin rebase, `OriginRebasedEvent` |
| `GalacticAssetManager` | `data/GalacticAssetManager.java` | Async GLTF/GLB load, ref-counted `AssetHandle`, priority queue, 3-loads/frame dispatch |
| `StreamingSystem` | `data/systems/StreamingSystem.java` | Per-entity distance enqueue/release of streamable assets |
| `ShipInteriorPhysicsSystem` | `ship/systems/ShipInteriorPhysicsSystem.java` | Per-ship interior `btDynamicsWorld` |
| `PlayerStateComponent` | `player/components/PlayerStateComponent.java` | `PlayerMode` FSM (ON_FOOT_EXTERIOR / ON_FOOT_INTERIOR / PILOTING) |
| `EventBus` | `core/EventBus.java` | Generic `subscribe(Class<T>)` / `publish(T)` |
| Procgen generators | `galaxy/`, `data/WorldPopulator`, station/`SpaceStationGenerator` | On-demand world instantiation |

The server module's `ZoneDefinition` / `ZoneHandoffManager` (`server/.../zone/`) is a
**separate concern** (server-side spatial partitioning for load distribution) and is
**out of scope** for this spec. Client-scene ↔ server-zone alignment is Sub-project C.

### Decomposition

This spec is **Sub-project A** of three. B and C each get their own spec → plan → build:

- **A — Scene Orchestration Core** (this spec): `SceneManager`, `Scene` abstraction,
  transition state machine. Wires the existing streaming/physics/coordinate systems.
- **B — LOD Streaming** (depends on A): geometry/physics/AI LOD bands with hysteresis
  for planets/ships/NPCs. Only fleet/debris LOD exist today (`FleetLODSystem`,
  `DebrisLODSystem`).
- **C — Multiplayer Zone Alignment** (depends on A): map client scenes onto the
  server's `ZoneDefinition` boundaries and `ZoneHandoffManager`.

### Decisions locked during brainstorming

- **Architecture:** Approach 1 — single Ashley `Engine` + per-entity scene-membership
  tag (not multiple engines, not spatial-query-only resource holders).
- **Triggering:** Hybrid — automatic distance-based for space transitions; explicit
  player action (reusing existing dock/land/interact flows) for surface & interior.
- **Presentation:** Async load + gameplay-disguised swap. No hard loading screen.

---

## 2. Taxonomy (two scales)

- **Sector** — a galaxy-space region anchored in 64-bit doubles (a star system or a
  deep-space cell). The context the `CoordinateManager` floating origin operates within.
  The `SceneManager` always operates *inside the current sector*.
- **Scene** — an active, *loaded* gameplay context whose entities live in 32-bit local
  space. `SceneType` is a data-driven enum, designed to be extended:
  `DEEP_SPACE`, `ORBITAL`, `PLANET_SURFACE`, `STATION_INTERIOR`, `SHIP_INTERIOR`,
  `ASTEROID_FIELD`.

**New package:** `com.galacticodyssey.core.scene` (alongside floating-origin / coordinate
/ event-bus code in `core/`, per the project folder layout).

---

## 3. Components (Ashley)

- `SceneComponent { int sceneId }` — tags every streamed entity with its owning scene.
  Makes load/unload deterministic (the core of Approach 1).
- `PersistentSceneMemberComponent {}` — marks the few entities that survive scene swaps
  (the player, the active ship). On transition they are **re-tagged** to the destination
  scene rather than unloaded. This is how the player straddles exterior↔interior cleanly.

---

## 4. Core Classes

All in `com.galacticodyssey.core.scene` unless noted.

- **`Scene`** — lifecycle data holder, no god-logic:
  `int id; SceneType type; SceneState state; double[] galaxyAnchor;
  Array<AssetHandle> assets; btDynamicsWorld interiorWorld /* nullable, interiors only */`.
- **`SceneState`** enum: `UNLOADED → LOADING → ACTIVE → UNLOADING`.
- **`SceneLoader`** (interface) + per-type impls: `DeepSpaceLoader`, `OrbitalLoader`,
  `PlanetSurfaceLoader`, `StationInteriorLoader`, `ShipInteriorLoader`. Each wraps the
  **existing** generators (`StarSystemGenerator`, `PlanetTerrainSystem`/`WorldPopulator`,
  `SpaceStationGenerator`, `ShipInteriorPhysicsSystem`) and asset acquisition behind one
  interface. Key method: `step(float budgetMs)` returns incremental progress so loading
  never blocks the render thread. This interface is the seam that makes scenes
  isolated-testable with fakes (no GL, no real procgen) — architectural rule #5.
- **`SceneManager`** — owns the active-scene set (max 2–3, per skill). Exposes
  `requestTransition(SceneTransitionRequest)`, `update(float dt)`, `getPrimaryScene()`,
  `getActiveScenes()`. Delegates choreography to the transition controller. A plain
  object (not an Ashley system), constructed with injected `GalacticAssetManager`,
  `CoordinateManager`, `EventBus`, and the `Engine`.
- **`SceneTransitionController`** — the state machine (see §5).
- **`SceneStreamingSystem`** (Ashley `EntitySystem`, registered in `GameWorld`) — feeds
  player/camera position to the `SceneManager` each frame, evaluates automatic distance
  triggers, and calls `SceneManager.update(dt)`. Owns the `SceneManager` instance.

---

## 5. Transition State Machine

`SceneTransitionController` drives **one** transition at a time:

```
IDLE
 → REQUESTED      Transition request accepted; target SceneType + galaxy anchor resolved.
 → PRELOADING     Target SceneLoader.step() runs incrementally (assets enqueued via
                  GalacticAssetManager + time-sliced procgen, ≤8ms/frame). Current
                  scene stays fully ACTIVE and interactive.
 → READY_OVERLAP  Target scene loaded + ACTIVE alongside source; both live.
                  SceneTransitionReadyEvent fires so gameplay disguise can play.
 → ACTIVATING     Re-tag PersistentSceneMember entities to target; hand off camera /
                  physics / primary-scene pointer; rebase floating origin via
                  CoordinateManager if the anchor jump is large.
 → UNLOADING_OLD  Source SceneLoader.unload(): release AssetHandles, remove its tagged
                  entities, dispose its btDynamicsWorld if interior.
 → IDLE           SceneTransitionCompletedEvent.
```

### Triggering (hybrid)

- **Automatic (distance):** `SceneStreamingSystem` evaluates distance thresholds **with
  hysteresis** (enter radius < exit radius) to prevent boundary thrash. Covers
  deep space ↔ orbital and approaching/leaving a star system.
- **Explicit (player action):** gameplay systems post a `SceneTransitionRequestEvent`
  on the `EventBus`. Landing, docking, and boarding reuse the **existing** docking flow
  and `InteractionSystem` / `PilotTransitionSystem` — no new input paths.
  `SHIP_INTERIOR` transitions (interior physics world already exists) are wrapped in the
  scene lifecycle.

### Async + disguise

- `SceneLoader.step(budgetMs)` respects the 8ms/frame asset budget; the controller never
  blocks the render thread.
- Disguise is **event-driven, not baked in**: the controller emits
  `SceneTransitionReadyEvent` at READY_OVERLAP; VFX/camera/animation systems subscribe and
  play the atmospheric-entry / docking animation. The controller proceeds to ACTIVATING
  when the load is ready **and** the disguise signals done **or** a bounded timeout
  elapses. Keeps choreography decoupled (event-driven rule #3); no hard loading screen.

### Concurrency guard

Only one transition in flight. Automatic triggers during a transition are ignored; an
explicit request mid-transition is rejected with a logged `SceneTransitionRejectedEvent`
(no queue for v1 — YAGNI).

---

## 6. Integration Points (all via existing seams)

- **`GameWorld` bootstrap** — register `SceneStreamingSystem`. The `SceneManager` /
  `SceneTransitionController` are plain objects constructed with injected
  `GalacticAssetManager`, `CoordinateManager`, `EventBus`, `Engine`.
- **`StreamingSystem` (existing)** — responsibility unchanged (per-entity asset distance
  enqueue). Entities it streams now carry a `SceneComponent`, so scene unload releases
  their handles deterministically. **No rewrite.**
- **`CoordinateManager`** — scene anchors stored in galaxy-space doubles; ACTIVATING
  calls the existing rebase path on large anchor jumps. Existing `OriginRebasedEvent`
  listeners handle the shift — scenes need no special-casing.
- **`PlayerStateComponent`** — gains a derived read of the current primary scene (state
  is **not** duplicated). `PlayerMode` stays orthogonal (e.g. `PILOTING` in `DEEP_SPACE`
  or `ORBITAL`).
- **Events published** on `EventBus`: `SceneTransitionBeganEvent`,
  `SceneLoadProgressEvent`, `SceneTransitionReadyEvent`, `SceneActivatedEvent`,
  `SceneTransitionCompletedEvent`, `SceneTransitionRejectedEvent`, `SceneLoadFailedEvent`.
  HUD/audio/VFX subscribe independently.

---

## 7. Error Handling (never strand the player)

- **Load failure** (missing asset, generation throw) → abort, fully roll back: release
  any partially-acquired target handles, dispose a partially-built interior world, keep
  the source scene `ACTIVE`, post `SceneLoadFailedEvent`, log. The player never lands in
  a half-loaded scene.
- **Max-active-scenes exceeded** → reject the new transition (cannot evict the scene the
  player occupies); log.
- **Disguise timeout** → if the disguise never signals done, proceed to ACTIVATING after
  a bounded timeout so a missing VFX subscriber cannot soft-lock transitions.
- **Re-entrancy** → guarded by the single-in-flight rule (§5).

---

## 8. Testing (headless, no GL — rule #5)

- `SceneManagerTest` / `SceneTransitionControllerTest` with **fake `SceneLoader`s**
  (deterministic, no procgen/GL) and a fake `GalacticAssetManager` asserting
  acquire/release ref-count balance.
- Assertions:
  - Full phase sequence on a happy-path transition.
  - Both scenes `ACTIVE` during READY_OVERLAP.
  - `PersistentSceneMember` entities re-tagged exactly once.
  - Source entities removed + handles released on unload.
  - Failure path rolls back with source still `ACTIVE`.
  - Max-scene rejection.
  - Concurrency guard rejects overlapping requests.
  - Correct event sequence emitted.
- Hysteresis unit test on the distance-trigger evaluator (enter/exit radii) — no GL.

---

## 9. Out of Scope (deferred)

- LOD bands for geometry/physics/AI (Sub-project B).
- Client↔server zone alignment / handoff (Sub-project C).
- A queued transition backlog (YAGNI for v1 — single in-flight only).
- New input paths for landing/docking (reuse existing flows).
