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

**`RealTimeSkillSystem`**

Tracks continuous skill use and awards XP proportional to performance:
- Accuracy improves with repeated successful shots.
- Sprint distance improves athleticism.
- Each skill is a `RealTimeSkill` with `SkillProgress` (current XP, threshold, level).

`PointSkill` handles discrete upgrade nodes purchased through a separate UI.

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
| `PlayerStatsComponent` | Skill map, total XP, level |
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
