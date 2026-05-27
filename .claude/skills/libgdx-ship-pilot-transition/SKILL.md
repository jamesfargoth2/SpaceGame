---
name: libgdx-ship-pilot-transition
description: How the FPS-to-ship-pilot state machine works in Galactic Odyssey and how to extend it. Covers the PlayerMode state machine (ON_FOOT_EXTERIOR → ON_FOOT_INTERIOR → PILOTING), InteractionSystem E-key handling, system priority gating, camera handoff between CameraSystem and ShipCameraSystem, interior physics world activation, and event-driven decoupling via EventBus. Use this skill when adding new seat types (gunner, engineer), new player modes (EVA, autopilot, docking, turret), modifying the boarding flow, changing how the player enters/exits ships, fixing transition bugs (camera snap, physics body teleport, input stuck), adding transition animations, or extending the interaction system for new interactable objects. Also trigger for "pilot seat", "enter ship", "exit ship", "board ship", "player mode", "state machine transition", "cockpit transition", "seat interaction", "ship entry", "ramp deployment", or "mode switch".
---

# Ship Pilot Transition System

This skill documents how the player transitions between on-foot FPS mode and ship piloting, and how to extend the state machine for new modes and seat types.

## Architecture Overview

The transition system spans five cooperating systems that gate on `PlayerStateComponent.currentMode`:

```
PlayerInputSystem (priority 0)     ← always runs, fills PlayerInputComponent
    ↓
InteractionSystem (priority 0)     ← checks E-key, manages state transitions
    ↓
PlayerMovementSystem (priority 1)  ← runs only when NOT PILOTING
ShipFlightSystem (priority 1)      ← runs only when PILOTING
    ↓
CameraSystem (priority 4)          ← runs only when NOT PILOTING
ShipCameraSystem (priority 4)      ← runs only when PILOTING
```

The key insight: `PlayerInputComponent` is a shared input buffer. The same W/S/A/D keys drive either FPS movement or ship thrust depending on which system is active. Systems use early-return guards:

```java
// In PlayerMovementSystem:
if (playerState.currentMode == PlayerMode.PILOTING) return;

// In ShipFlightSystem:
if (playerState.currentMode != PlayerMode.PILOTING) return;
```

## State Machine

```
                    E near ramp                    E at pilot seat
ON_FOOT_EXTERIOR ──────────────► ON_FOOT_INTERIOR ─────────────────► PILOTING
       ▲                               ▲                                │
       │         E at entry point       │          E while seated       │
       └───────────────────────────────┘◄──────────────────────────────┘
```

### PlayerMode Enum

```java
public enum PlayerMode {
    ON_FOOT_EXTERIOR,  // Walking on planet/station surface
    ON_FOOT_INTERIOR,  // Walking inside a ship
    PILOTING           // Seated at pilot controls
}
```

### PlayerStateComponent Fields

```java
public PlayerMode currentMode = PlayerMode.ON_FOOT_EXTERIOR;
public Entity currentShip;          // the ship being boarded/piloted (null when exterior)
public Entity interactionTarget;    // object player is looking at (for UI prompts)
```

## InteractionSystem — The Transition Engine

`InteractionSystem` is a non-iterating system that queries all players and all ships each frame. It checks spatial proximity between the player and interaction points, then handles E-key presses.

### Transition: Enter Ship

**Trigger:** Player within `ShipEntryPointComponent.triggerRadius` of a ship with `rampDeployed == true`, presses E.

**Sequence:**
1. Publish `PlayerEnterShipEvent(player, ship)`
2. Set `playerState.currentMode = ON_FOOT_INTERIOR`
3. Set `playerState.currentShip = ship`
4. Activate `ShipInteriorComponent` (sets `active = true`, starts stepping interior physics)
5. Remove player's rigid body from exterior `btDynamicsWorld`
6. Add player's rigid body to ship's `interiorWorld` (the separate interior `btDynamicsWorld`)
7. Teleport player to `ShipEntryPointComponent.interiorAirlockPosition`

### Transition: Sit in Pilot Seat

**Trigger:** Player within `PilotSeatComponent.triggerRadius` of the pilot seat, presses E.

**Sequence:**
1. Set `PilotSeatComponent.occupied = true`, `occupant = player`
2. Publish `PlayerStartPilotingEvent(player, ship)`
3. Set `playerState.currentMode = PILOTING`
4. Disable player rigid body (freeze in place at seat position)
5. `ShipFlightSystem` and `ShipCameraSystem` start processing on next frame
6. `PlayerMovementSystem` and `CameraSystem` stop processing on next frame

### Transition: Leave Pilot Seat

**Trigger:** Player presses E while in PILOTING mode.

**Sequence:**
1. Set `PilotSeatComponent.occupied = false`, `occupant = null`
2. Publish `PlayerStopPilotingEvent(player, ship)`
3. Set `playerState.currentMode = ON_FOOT_INTERIOR`
4. Re-enable player rigid body at seat position
5. `ShipFlightSystem` / `ShipCameraSystem` stop; `PlayerMovementSystem` / `CameraSystem` resume

### Transition: Exit Ship

**Trigger:** Player near `ShipEntryPointComponent` interior position, presses E.

**Sequence:**
1. Remove player body from interior `btDynamicsWorld`
2. Add player body back to exterior `btDynamicsWorld`
3. Teleport player to `ShipEntryPointComponent.exteriorRampPosition`
4. Set `ShipInteriorComponent.active = false`
5. Publish `PlayerExitShipEvent(player, ship)`
6. Set `playerState.currentMode = ON_FOOT_EXTERIOR`
7. Set `playerState.currentShip = null`

## Events

All transitions publish events via `EventBus`. Subscribe to react without coupling:

| Event | When | Typical subscribers |
|---|---|---|
| `PlayerEnterShipEvent` | Boarding a ship | Audio (airlock sound), UI (hide exterior HUD) |
| `PlayerStartPilotingEvent` | Sitting at pilot seat | UI (show ship HUD), Audio (engine idle loop) |
| `PlayerStopPilotingEvent` | Leaving pilot seat | UI (hide ship HUD), Audio (stop engine) |
| `PlayerExitShipEvent` | Leaving the ship | Audio (ramp sound), UI (show exterior HUD) |

```java
eventBus.subscribe(PlayerStartPilotingEvent.class, event -> {
    shipHud.show(event.ship);
    audioManager.playLoop("engine_idle", event.ship);
});
```

## Camera Handoff

The camera transition between FPS and ship pilot is handled by the two camera systems being mutually exclusive:

- **CameraSystem** builds the view matrix from `FPSCameraComponent` (pitch/yaw angles, eye height, head bob). Inactive when PILOTING.
- **ShipCameraSystem** has two submodes toggled with V:
  - **COCKPIT** — camera at pilot seat world position, direction lerps toward ship forward (smooth lag ~8 deg/s), up = ship up
  - **CHASE** — camera 15–60m behind+above ship (size-dependent), lerps to target position (~4 deg/s), looks at ship center

To avoid camera snap on transition, the ShipCameraSystem should initialize its internal lerp state from the current camera position/direction on the first frame after `PILOTING` begins (read `camera.position` and `camera.direction` before overwriting).

## Interior Physics Isolation

Each ship has a separate `btDiscreteDynamicsWorld` in `ShipInteriorComponent`. This is the most important architectural constraint:

- Interior physics simulates gravity, player walking, and loose objects **relative to the ship**
- The interior world's gravity vector is set to ship-local down (so the player walks on the floor even if the ship is pitched 90 degrees)
- When the ship moves, the interior world moves with it — objects inside don't feel external acceleration (no inertial effects by default)
- `ShipInteriorPhysicsSystem` only steps worlds where `active == true`

To add inertial effects (objects sliding when ship accelerates hard), apply the inverse of the ship's acceleration as a force on interior bodies.

## How to Add a New Seat Type

Example: adding a gunner seat.

### 1. Create the component

```java
// ship/components/GunnerSeatComponent.java
public class GunnerSeatComponent implements Component {
    public Vector3 interiorPosition = new Vector3();
    public float triggerRadius = 1.5f;
    public boolean occupied;
    public Entity occupant;
    public int turretIndex;  // which turret this seat controls
}
```

### 2. Add a new PlayerMode (if behavior differs significantly)

```java
public enum PlayerMode {
    ON_FOOT_EXTERIOR,
    ON_FOOT_INTERIOR,
    PILOTING,
    GUNNING   // new mode
}
```

If the gunner uses the same input flow (mouse aim, click to fire) but routes it differently, a separate mode keeps systems cleanly gated.

### 3. Extend InteractionSystem

Add a `checkGunnerSeat()` method following the same pattern as `checkPilotSeat()`. When E is pressed near a gunner seat:

```java
gunnerSeat.occupied = true;
gunnerSeat.occupant = player;
eventBus.publish(new PlayerStartGunningEvent(player, ship, turretIndex));
playerState.currentMode = PlayerMode.GUNNING;
```

### 4. Create the gunner system

```java
public class ShipTurretSystem extends IteratingSystem {
    // Only runs when GUNNING
    // Reads mouseDeltaX/Y for turret aim
    // Reads mouse buttons for firing
    // Routes to the specific turret hardpoint
}
```

### 5. Create the gunner camera

Either reuse `ShipCameraSystem` with a turret-specific view, or create `TurretCameraSystem` that follows the turret's barrel direction.

### 6. Register in GameWorld

Add the new system with appropriate priority and gating.

## How to Add a New Player Mode

The pattern for any new mode (EVA, autopilot, docking, turret gunning):

1. Add enum value to `PlayerMode`
2. Add early-return guards to existing systems that shouldn't run in the new mode
3. Create new system(s) that only run in the new mode
4. Add transition logic in `InteractionSystem` (or a new trigger system)
5. Publish events for the transition
6. Handle camera in a new or existing camera system

## Common Mistakes

| Mistake | Fix |
|---|---|
| Forgetting to move the player rigid body between physics worlds on enter/exit | Player body must be removed from one world and added to the other — it can't be in both |
| Camera snaps on pilot/exit transition | Initialize lerp state from current camera pos/dir on first frame of new mode |
| Input "sticks" after transition (player keeps moving) | `PlayerInputComponent` is reset each frame by `PlayerInputSystem` — if you see stuck input, check that the input system runs before the movement system |
| Ship keeps drifting after player exits pilot seat | `ShipFlightSystem` applies zero force when not piloting, but doesn't brake. Add velocity damping in the system when no pilot is seated, or leave it as Newtonian behavior |
| Interior gravity wrong after ship rotates | Update interior world gravity to match ship-local down each frame: `interiorWorld.setGravity(shipDownVector.scl(-9.81f))` |
| Interaction prompt flickers | Check trigger radius overlap in `InteractionSystem` — if the player is right at the edge, they'll oscillate in/out. Add a small hysteresis buffer |
