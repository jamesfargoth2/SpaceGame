# Elite-Dangerous-Style Ship Flight Model — Design

**Date:** 2026-05-28
**Status:** Approved (design); plan pending
**Scope:** Sub-project 1 of 2. SP2 (Power Pips / Distribution) is a separate spec.

## Goal

Make player and NPC ship flight feel like Elite Dangerous: a persistent
throttle set-point that maps to a target cruise speed, Flight Assist (FA) that
holds that speed and bleeds off drift, an FA-off Newtonian mode for advanced
maneuvers, a "blue zone" throttle band where the ship turns best, and an engine
boost surge gated by a dedicated energy gauge + cooldown.

This replaces the current *hold-to-thrust* model (W/S apply forward force only
while held, resetting to zero on release; an implicit speed cap arises only from
Bullet linear damping).

### Non-goals (deferred to SP2)
- SYS/ENG/WEP power pips and the discrete pip UI.
- Routing boost energy from the ENG capacitor (boost uses its own gauge here).

## Existing context (what we build on)

- `ShipFlightSystem` (`core/.../ship/systems/ShipFlightSystem.java`) integrates
  flight via gdx-bullet: `physics.body.applyCentralForce(...)`,
  `applyTorque(...)`, `setDamping(linearDrag, angularDrag)`. It already handles
  a relativistic force clamp (`RelativisticMath`) and reads
  `ShipFlightComponent` tuning. It drives both the player-piloted ship (input on
  the player entity) and NPC ships (input on the ship entity).
- `ShipFlightInputComponent` carries `throttle, strafe, verticalThrust,
  pitchInput, yawInput, rollInput` plus fire/target/camera flags.
- `ShipFlightComponent` carries tuning (`linearThrust, strafeThrustFraction,
  verticalThrustFraction, pitchYawTorque, rollTorque, linearDrag, angularDrag`)
  and runtime `currentThrottle`. It is `Snapshotable<ShipFlightSnapshot>`.
- `ShipDataComponent.maxSpeed` / `baseMaxSpeed` already exist (max speed is
  currently only implicit, via Bullet damping at terminal velocity).
- Flight tuning is data-driven: `ShipClassData` → `ShipClassRegistry` (reads
  JSON) → `ShipFactory` applies per-size-class values.
- `PlayerInputSystem.processFlightInput(...)` maps keys to the input component;
  it currently **zeroes `throttle` every frame** and adds `+1`/`-1` while W/S
  held.
- NPC ships are flown by `ShipPilotAISystem` / `ShipSteeringController`, which
  write `ShipFlightInputComponent` directly.
- A power system already exists (`PowerStateComponent` with reactor, battery,
  capacitor, per-subsystem allocations; `PowerPenaltySystem`) — relevant to SP2,
  not consumed here.

## Architecture decision

**Force-based velocity controller.** Keep the gdx-bullet rigid-body pipeline.
Flight Assist is implemented as a proportional controller that computes
*corrective forces* to drive the body's current velocity toward a *target
velocity*, and *corrective torque* to drive angular velocity toward an
input-commanded rate. This stays consistent with the relativistic clamp and
atmospheric drag, which already feed the same force accumulator, and avoids
fighting gravity/collision forces (which a direct `setLinearVelocity` override
would clobber).

`ShipFlightSystem` remains authoritative for Bullet damping and sets it to
`(0, 0)` so the controllers fully govern convergence (no double-damping). All
damping behavior is therefore gated on `flightAssistEnabled`, per the
flight-physics skill's rule.

## Components

### `ShipFlightInputComponent` (changed)
- `throttle` — now a **persistent set-point** in `[-reverseFraction, +1]`. The
  input system no longer zeroes it each frame; it ramps/sets it.
- Add edge-trigger flags (consumed once, like the existing `*Pressed` flags):
  - `boolean flightAssistTogglePressed`
  - `boolean boostPressed`
- `strafe`, `verticalThrust`, `pitchInput`, `yawInput`, `rollInput` remain
  momentary (zeroed each frame).

### `ShipFlightComponent` (extended)
New **tuning** fields (data-driven, see Config):
- `reverseFraction` — magnitude of max reverse throttle (e.g. `0.4`).
- `faLinearGain` — P-gain for forward-speed tracking.
- `faLateralBleed` — 0–1 fraction of strafe thrust used to cancel drift under FA.
- `maxTurnRate` — peak angular rate (rad/s) commandable at full stick.
- `blueZoneLow`, `blueZoneHigh` — throttle band (|throttle|) of best turning
  (e.g. `0.40`, `0.80`).
- `offBandTurnScale` — turn-rate multiplier outside the blue zone (e.g. `0.5`).
- `boostSpeedMultiplier` (e.g. `1.6`), `boostForce` (extra forward N during
  boost), `boostDuration` (s), `boostEnergyCost`, `boostMaxEnergy`,
  `boostRechargeRate` (energy/s), `boostCooldown` (s).

New **runtime** fields (extend `ShipFlightSnapshot` so they persist):
- `boolean flightAssistEnabled` — default `true`.
- `float boostEnergy` — current gauge (starts full = `boostMaxEnergy`).
- `float boostTimer` — remaining boost time (>0 = boosting).
- `float boostCooldownTimer` — remaining cooldown before next boost.

Existing `linearDrag` / `angularDrag` tuning fields are retained in the
component/snapshot for compatibility but no longer drive Bullet damping (set to
0 by the system). They may be repurposed later; do not remove (avoids breaking
the snapshot schema unnecessarily).

`effectiveMaxSpeed` is computed per tick = `ShipDataComponent.maxSpeed ×
(boostTimer > 0 ? boostSpeedMultiplier : 1)`. If a ship has no
`ShipDataComponent`, fall back to a sane default so tests/AI without full data
still run.

## Behavior

### Throttle → target speed
`targetSpeed = throttle × effectiveMaxSpeed`, with `throttle ∈
[−reverseFraction, +1]`.

### Flight Assist — Linear (FA ON)
1. Read world velocity `v`; nose-forward unit vector `f`.
2. `forwardSpeed = v · f`.
3. `forwardError = targetSpeed − forwardSpeed`;
   `forwardForce = f × clamp(faLinearGain × forwardError, −linearThrust, +linearThrust)`.
   → smooth acceleration to the throttle speed, then holds it (the speed cap).
4. Lateral velocity `vLat = v − (forwardSpeed × f)`. Apply corrective force
   `−vLat`-direction, magnitude up to `strafeThrust × faLateralBleed`, to bleed
   drift → velocity realigns to the nose.
5. Intentional `strafe` / `verticalThrust` input adds a commanded lateral target
   (direct strafe force up to that axis's thrust); on release, step 4 bleeds it
   off.

### Flight Assist — Linear (FA OFF / Newtonian)
- Forward force = `throttle × linearThrust` along the nose.
- `strafe` / `verticalThrust` → direct forces on their axes.
- **No** lateral bleed, **no** target-speed cap (only the existing relativistic
  clamp bounds top speed). Momentum persists; the ship drifts and coasts.

### Rotation
- Desired angular rate per axis = `stickInput × maxTurnRate × blueZoneFactor`.
- **FA ON:** apply torque (P-controller) driving angular velocity toward the
  desired rate. Zero input → desired rate 0 → ship auto-stops rotating.
- **FA OFF:** apply torque from input directly (proportional to
  `pitchYawTorque`/`rollTorque`); **no** auto-stop — the ship keeps spinning
  until countered.

### Blue zone
```
b = |throttle|
if blueZoneLow ≤ b ≤ blueZoneHigh:      blueZoneFactor = 1.0
else linearly interpolate down to offBandTurnScale at the throttle extremes
     (0 below the band, 1.0 above the band).
```
Scales `maxTurnRate`, so easing off the throttle into the band wins turning
fights — ED's blue throttle segment.

### Boost
- On `boostPressed`, if `boostEnergy ≥ boostEnergyCost` **and**
  `boostCooldownTimer == 0`: deduct `boostEnergyCost`, set
  `boostTimer = boostDuration`, set `boostCooldownTimer = boostCooldown`.
- While `boostTimer > 0`: `effectiveMaxSpeed ×= boostSpeedMultiplier`, add a
  `boostForce` forward surge.
- Each tick: decrement `boostTimer` and `boostCooldownTimer` by `dt` (floored at
  0). When not boosting, recharge `boostEnergy` toward `boostMaxEnergy` at
  `boostRechargeRate`.
- Self-contained gauge in SP1; SP2 may later source it from the ENG capacitor.

### Engines disabled
The existing `canThrust(ship)` guard still applies first: if engines are
non-operational (destroyed / EMP), the ship ignores all thrust/turn/boost input
and coasts (Newtonian), regardless of FA state.

## Input mapping (`PlayerInputSystem.processFlightInput`)
- Stop zeroing `throttle`. Instead:
  - W held: `throttle += throttleRampRate × dt` (clamp ≤ +1).
  - S held: `throttle −= throttleRampRate × dt` (clamp ≥ −reverseFraction).
  - `X`: `throttle = 0`.
  - (A tap nudges; a hold ramps — covers the "≈±10% step" feel without discrete
    step detection.)
- `strafe`/`verticalThrust`/`roll` remain momentary (A/D, Space/Ctrl, Q/E).
- Pitch/yaw from mouse, as today.
- `Z`: set `flightAssistTogglePressed` (edge).
- `Tab`: set `boostPressed` (edge).
- **Binding audit:** confirm `Z`, `Tab`, `X` do not clash with existing piloting
  bindings (fire groups, target cycle, camera toggle, board/interact); adjust if
  needed and record the final binding table in the plan.

## AI compatibility
- NPC input is written by `ShipPilotAISystem` / `ShipSteeringController`. A
  set-point throttle is natural for AI (it sets a desired fraction).
- NPC ships default `flightAssistEnabled = true` so the FA controller flies them
  toward targets as before.
- **Audit** `ShipSteeringController` for any assumption that `throttle` is
  momentary or that rotation is raw-torque-per-input; adapt so the dogfight AI
  behavior and its existing tests (`ShipPilotAISystemTest`,
  `ShipSteeringControllerTest`, `ShipFlightSystemNpcTest`, dogfight task tests)
  stay green. Treat any required AI change as in-scope glue, not a redesign.

## Data-driven config
- Add the new tuning fields to `ShipClassData`, load them in
  `ShipClassRegistry` from the ship-class JSON (with safe defaults when a field
  is absent so existing data files keep loading), and apply them in
  `ShipFactory` (both the player and NPC construction paths).
- Per-size-class defaults: fighters get high `maxTurnRate`, agile blue zone, and
  strong boost; freighters get low turn rate and weak boost. Keep values in the
  data layer — never hardcode in the system.

## HUD (minimal, in SP1)
Surface flight state on the existing cockpit HUD so the feel is readable:
throttle %, current speed (and target speed), FA ON/OFF indicator, and the boost
gauge/cooldown. Read-only display bound to `ShipFlightComponent` /
`ShipDataComponent`; no new simulation logic. Kept intentionally light; richer
HUD work is out of scope.

## Testing
Headless JUnit (follow `ShipFlightSystemTest` patterns — no GL context):
1. **Throttle cap:** with FA on and full throttle, forward speed converges to
   `maxSpeed` and holds (does not overshoot indefinitely).
2. **Partial throttle:** 50% throttle converges to ~50% of `maxSpeed`.
3. **Reverse:** negative throttle drives negative forward speed up to
   `reverseFraction × maxSpeed`.
4. **FA lateral bleed:** with FA on and an initial lateral velocity, lateral
   component decays toward 0 over time.
5. **FA off momentum:** with FA off, an initial lateral velocity is preserved
   (no bleed) and no target-speed cap is enforced.
6. **FA toggle:** `flightAssistTogglePressed` flips `flightAssistEnabled` once
   per press (edge-triggered).
7. **Blue zone:** commanded/achieved turn rate is higher at a throttle inside
   `[blueZoneLow, blueZoneHigh]` than at full throttle.
8. **Boost:** boost raises achievable speed for `boostDuration`, depletes the
   gauge, respects cooldown (a second press during cooldown is ignored), and the
   gauge recharges when idle.
9. **Engines disabled:** with `enginesOperational()==false`, input is ignored
   and the ship coasts.
10. **NPC regression:** existing NPC/AI flight tests still pass.

## Verification
After tests pass, fly the game via the `run-galactic-odyssey` skill: enter
piloting mode, exercise throttle set-point, FA on/off, a hard turn at blue-zone
vs full throttle, and a boost; capture a screenshot of the cockpit HUD showing
throttle/speed/FA/boost state.

## Risks
- **AI regression** from changed throttle/rotation semantics — mitigated by the
  AI audit and keeping existing tests green.
- **Tuning feel** is iterative; defaults are starting points to be refined live
  (James iterates on game feel).
- **Snapshot schema** change (new persisted fields) — additive only; keep old
  fields to avoid breaking saves.
- **Binding clashes** for Z/Tab/X — resolved by the binding audit.
