# NPC Dogfight AI — Design

**Date:** 2026-05-28
**Status:** Approved (pending implementation plan)

## Summary

NPC ships fly the existing 6DOF flight model and fight the player (and each
other) in close-range dogfights. Each combat ship gets a per-ship pilot AI built
on the project's existing `BehaviorTree<Entity>` pattern. The AI reuses the
fleet/squadron target-broadcast layer for target selection, the LOD tier
assigned by `FleetLODSystem` for cost control, and the existing flight, weapon,
ballistics, and missile-guidance systems for actuation. The AI itself only
**decides and commands** — it does not reimplement flight, weapons, lead
prediction, or missile guidance.

This work also closes a gap: today `FleetExpansionSystem` gives expanded fleet
members only a `TransformComponent`. To fly, they need the full flight stack, so
this work makes expanded combat ships flyable AI entities.

## Goals

- Convincing single-ship 6DOF dogfighting: pursuit, attack runs, evasion,
  energy-managed extend-and-reengage.
- Gun fire with lead prediction and missile launches against the current target.
- Data-driven pilot skill variation (rookie / veteran / ace).
- Cost control via LOD: full behavior at close range, a cheap approximation at
  mid range.
- Cohesion with existing systems (fleet/squadron, behavior trees, flight,
  weapons) — no parallel/duplicated subsystems.

## Non-goals (YAGNI)

- Capital-ship or fleet-level tactics (handled by `AdmiralBehaviorTree` /
  `FleetCommandSystem`).
- Multiplayer prediction/netcode for AI ships.
- Formation choreography beyond the existing squadron focus-fire.
- ABSTRACT-tier (>10km) statistical combat resolution — left as the current stub.

## Decisions (from brainstorming)

- **Fidelity:** Full 6DOF tactics (real flight model, pursuit curves, evasion,
  throttle/energy management).
- **Integration:** Reuse fleet infrastructure (behavior-tree pattern, squadron
  focus-fire target broadcast).
- **AI structure:** libGDX `BehaviorTree<Entity>` (LeafTask/BranchTask),
  consistent with the existing ground-combat AI.
- **Ship instantiation:** In scope — make expanded fleet ships flyable via
  `ShipFactory`.
- **Pilot variation:** Data-driven archetypes (JSON).
- **LOD scope:** FULL tier real behavior + SIMPLIFIED tier cheap approximation;
  ABSTRACT left as-is.

## Architecture

```
SquadronCoordinationSystem ──(assigns target)──▶ ShipPilotAIComponent
FleetLODSystem ──────────────(lodTier)─────────▶ ShipPilotAISystem
                                                      │  (reads ship + target state)
                                                      ▼
                                            DogfightBlackboard  ◀── update each tick
                                                      │
                                                      ▼
                                       BehaviorTree<Entity> (maneuver tasks)
                                                      │ desired aim dir / throttle / roll / fire intent
                                                      ▼
                                            ShipSteeringController (PD orientation → pitch/yaw/roll)
                                                      │
                                                      ▼
                       ShipFlightInputComponent ──────▶ ShipFlightSystem (forces)
                       ShipWeaponSystem.fireHardpoint(...) ◀── fire intent
```

The AI commands systems that already exist:

- **Flight:** writes `ShipFlightInputComponent` (`throttle`, `strafe`,
  `verticalThrust`, `pitchInput`, `yawInput`, `rollInput`); `ShipFlightSystem`
  integrates forces.
- **Guns:** `ShipWeaponSystem.fireHardpoint(shipEntity, hardpointId)` (built-in
  heat/ammo/cooldown checks).
- **Lead prediction:** `BallisticsUtil.computeLeadPoint` / `computeFullLead`.
- **Missiles:** set `GuidedProjectileComponent.targetEntity`; existing
  `GuidedProjectileSystem` (ProNav) steers them.

## Components & data

### `ShipPilotAIComponent` (`ship/ai/`)
Holds the per-ship AI state, mirroring `CombatAIComponent`:
- `BehaviorTree<Entity> behaviorTree`
- `Entity currentTarget`
- `DogfightBlackboard blackboard`
- `PilotArchetype archetype` (resolved from id)
- Timers: decision-cadence accumulator, reaction-delay accumulator.
- Implements the project's `Snapshotable<T>` for save/load (as
  `CombatAIComponent` does).

### `DogfightBlackboard` (`ship/ai/`)
6DOF analog of `CombatBlackboard`, updated each tick:
- Inputs (read from world): `self`, `target`, `rangeToTarget`, `closureRate`,
  `leadPoint`, `angleOffBore` (our nose vs. lead point), `targetAspectAngle`
  (are we on their six / are they on ours), `selfHealthPercent`.
- Outputs (written by tasks, read by the system): `desiredAimDir`,
  `desiredThrottle`, `desiredRoll`, `fireGuns`, `fireMissiles`.

### `PilotArchetype` + `resources/data/ai/pilot_archetypes.json`
Data-driven per architectural rule 2. Fields:
- `reactionTimeSec` — gates fire / target-switch decisions.
- `aimErrorDeg` — random cone added to `leadPoint`.
- `aggression` — bias toward attack vs. caution.
- `evadeHealthThreshold` — health fraction that triggers evasion.
- `preferredEngageRange` — throttle target distance.
- `overshootExtendDist` — when to extend-and-reengage.
- `throttleDiscipline` — energy-management aggressiveness.
- `usesMissiles` — whether the archetype launches missiles.

Ships reference an archetype id (e.g. `"rookie"`, `"veteran"`, `"ace"`).

## The flight brain — `ShipSteeringController` (`ship/ai/`)

A **pure logic class** — no Ashley, no GL — so it is directly unit-testable
(architectural rule 5). Given current orientation, current angular velocity, a
desired aim direction, and desired throttle/roll, it produces clamped
`pitch/yaw/roll/throttle` inputs using **PD control on the orientation error**:
the angular error toward the desired aim drives the proportional term, current
angular velocity drives the derivative (damping) term, preventing overshoot and
oscillation. Outputs are clamped to `[-1, 1]`. This is the reusable "point the
nose there and fly" primitive shared by every maneuver task.

## Behavior-tree maneuver tasks (`ship/ai/tasks/`)

`LeafTask<Entity>` / conditions following the existing `combat/ai/tasks`
pattern, loaded via the same tree-loading mechanism `CombatAIComponent` already
uses (exact `.tree`/JSON format to be confirmed in the plan against the existing
ground-combat loader).

**Conditions**
- `HasDogfightTargetCondition`
- `TargetInWeaponArcCondition`
- `LowHealthCondition`
- `IsBeingThreatenedCondition` (enemy on our six / missile lock)

**Maneuvers**
- `PursueTargetTask` — lead-pursuit toward `leadPoint`; throttle to close to
  `preferredEngageRange`.
- `AttackRunTask` — align nose on lead point; fire guns
  (`ShipWeaponSystem.fireHardpoint`) when `angleOffBore` < firing cone and in
  range; launch missiles when `usesMissiles` and locked.
- `EvadeTask` — break turn + roll + throttle jink when
  `selfHealthPercent < evadeHealthThreshold` or threatened.
- `ExtendAndReengageTask` — when overshooting (high closure, target slipping
  behind), extend away then turn back (energy management).
- `IdlePatrolTask` — slow patrol when no target.

**Tree shape:** `Selector( Evade?, AttackRun?, Pursue/Reposition, Patrol )`.

**Aim realism:** archetypes perturb `leadPoint` by a random cone scaled by
`aimErrorDeg`, and gate fire / target-switch decisions behind `reactionTimeSec`.
A rookie sprays; a veteran tracks.

## `ShipPilotAISystem` (`ship/ai/`)

`IteratingSystem`, priority ~10 (alongside `CombatAISystem`, **before**
`ShipFlightSystem` consumes the input each frame). Family:
`ShipPilotAIComponent + ShipFlightInputComponent + TransformComponent +
PhysicsBodyComponent`.

Per-tier behavior (tier read from `FleetMemberComponent.lodTier`):
- **FULL (<2km):** update blackboard → step behavior tree at the archetype's
  decision cadence → write `ShipFlightInputComponent` + fire weapons.
- **SIMPLIFIED (2–10km):** cheap path — proportional turn-toward-target +
  throttle-to-range + occasional fire, ticked at a low frequency; no full tree.
- **ABSTRACT (>10km):** untouched (existing stub).

Subscribes to `EntityKilledEvent` to clear dead targets (as `CombatAISystem`
does). Reads `currentTarget` assigned by `SquadronCoordinationSystem`, with a
nearest-hostile scan as fallback when no squadron target is set.

Registered in `GameWorld` alongside the other combat-AI systems.

## Making NPC ships flyable (fleet-expansion gap)

Today `FleetExpansionSystem` attaches only `FleetMemberComponent` +
`TransformComponent`. Add:

**`ShipFactory.createNpcCombatShip(shipClass, faction, archetypeId)`** —
attaches the full flight stack, reusing the existing player ship-build path
minus player input:
- `ShipDataComponent`, `ShipFlightComponent`, `ShipFlightInputComponent`
- a Bullet `PhysicsBodyComponent`
- `ShipHardpointComponent` + weapons
- `ShipThermalComponent`, `HealthComponent`
- `ShipPilotAIComponent` (with resolved archetype + loaded behavior tree)

`FleetExpansionSystem` calls this per member. On `FleetCollapsedEvent` / despawn,
Bullet bodies are disposed (architectural dispose rule — no leaks).

## Coordinate handling

All dogfight math runs in the active local scene in 32-bit floats (ships are
near the floating origin during combat), consistent with architectural rule 1.
No galaxy-space doubles are touched in this system.

## Testing (isolated, no GL — architectural rule 5)

- **`ShipSteeringController`** (pure unit): converges the nose to a target
  direction over simulated steps; damps without oscillation; never NaNs; clamps
  to `[-1, 1]`.
- **`DogfightBlackboard`** (pure unit): range / closure / `angleOffBore` /
  `targetAspectAngle` math correctness.
- **Each task** (fake blackboard + entity): `AttackRunTask` fires only in
  cone + range; `EvadeTask` triggers at/below `evadeHealthThreshold`;
  `PursueTask` sets throttle toward `preferredEngageRange`.
- **Integration (headless Bullet, no GL):** spawn an AI attacker + a moving
  target in an Ashley engine with `ShipFlightSystem` + physics; tick several
  seconds; assert the attacker closes range, aligns its nose, and fires ≥1 shot.
- **Archetype:** over a fixed sim, "ace" lands more hits than "rookie"
  (validates the aim-error / reaction wiring).

## File placement

```
core/src/main/java/com/galacticodyssey/ship/ai/
  ShipPilotAIComponent.java
  ShipPilotAISystem.java
  ShipSteeringController.java
  DogfightBlackboard.java
  PilotArchetype.java
  tasks/
    HasDogfightTargetCondition.java
    TargetInWeaponArcCondition.java
    LowHealthCondition.java
    IsBeingThreatenedCondition.java
    PursueTargetTask.java
    AttackRunTask.java
    EvadeTask.java
    ExtendAndReengageTask.java
    IdlePatrolTask.java

core/src/main/resources/data/ai/
  pilot_archetypes.json
  dogfight_tree.<ext>          (format per existing combat-AI tree loader)

core/src/main/java/com/galacticodyssey/ship/ShipFactory.java   (add createNpcCombatShip)
core/.../combat/fleet/systems/FleetExpansionSystem.java         (call factory; dispose on collapse)
core/.../core/GameWorld.java                                    (register ShipPilotAISystem)
```

## Open items for the plan

- Confirm the exact behavior-tree file format/loader used by the existing
  ground-combat AI and match it.
- Confirm headless Bullet test harness availability (used by existing physics
  integration tests).
- Confirm `ShipFactory`'s current player build path to factor out the shared
  flight-stack assembly cleanly.
