# Ship Piloting Systems Design

## Overview

A layered ECS pipeline that delivers the complete ship piloting experience: walking to a pilot seat, sitting down, flying with 6DOF in space or aerodynamic flight in atmosphere, firing weapons from the cockpit, and viewing the world through a full 3D cockpit interior with a comprehensive instrument HUD.

**Architecture:** Approach B — Layered System Pipeline. Discrete ECS systems form a processing pipeline, communicating via components and the event bus. No monolithic controller or hybrid state machine.

## 1. Input Mode System

### Problem

`PlayerInputSystem` currently writes FPS and flight inputs simultaneously with no awareness of `PlayerMode`. WASD, Space, Ctrl, and mouse all populate both FPS movement and flight thrust fields every frame.

### Solution

Branch `PlayerInputSystem.processEntity()` by `PlayerStateComponent.currentMode`:

- **ON_FOOT_EXTERIOR / ON_FOOT_INTERIOR**: Write to `PlayerInputComponent` (unchanged behavior). Route mouse clicks to `CombatInputSystem`.
- **PILOTING**: Write to `ShipFlightInputComponent` instead. Do not update `PlayerInputComponent` movement fields.

### New Component: `ShipFlightInputComponent`

```
package com.galacticodyssey.ship.components

Fields:
  float throttle          // -1 to 1 (W/S)
  float strafe            // -1 to 1 (A/D)
  float verticalThrust    // -1 to 1 (Space/Ctrl)
  float pitchInput        // mouse delta Y
  float yawInput          // mouse delta X
  float rollInput         // -1 to 1 (Z/C)
  boolean[] fireGroup     // [4] (mouse L = group 0, mouse R = group 1, num keys = explicit)
  boolean[] fireHeld      // [4] (sustained fire tracking)
  boolean targetLockPressed    // T key
  boolean nextTargetPressed    // Tab key
  boolean prevTargetPressed    // Shift+Tab
  boolean cameraTogglePressed  // V key
  float scrollDelta            // scroll wheel (weapon group cycle in some modes)
```

### Key Bindings (Piloting Mode)

| Key | Action |
|-----|--------|
| W/S | Throttle forward/back |
| A/D | Strafe left/right |
| Space | Thrust up |
| Ctrl | Thrust down |
| Z/C | Roll left/right |
| Mouse movement | Pitch/Yaw |
| Mouse L | Fire weapon group 1 |
| Mouse R | Fire weapon group 2 |
| 1-4 | Select weapon group |
| T | Target lock |
| Tab | Next target |
| Shift+Tab | Previous target |
| V | Toggle cockpit/chase camera |

## 2. Pilot Transition System

### New System: `PilotTransitionSystem` (priority 2)

Subscribes to transition events published by the existing `InteractionSystem`.

#### Enter Pilot Seat (on `PlayerStartPilotingEvent`)

1. Disable `PlayerMovementSystem` and `PlayerCameraSystem`
2. Enable `ShipFlightSystem`, `ShipCameraSystem`, `ShipWeaponPilotSystem`
3. Add `ShipFlightInputComponent` to the player entity
4. Start camera transition: lerp from current FPS camera position/rotation to cockpit seat position over 0.5 seconds
5. Publish `CockpitHUDShowEvent`
6. Hide FPS HUD elements (crosshair, health, ammo)

#### Exit Pilot Seat (on `PlayerStopPilotingEvent`)

1. Reverse all of the above
2. Camera lerps from cockpit back to FPS eye height at seat position over 0.5 seconds
3. Remove `ShipFlightInputComponent` from player entity
4. Re-enable FPS systems
5. Publish `CockpitHUDHideEvent`
6. Show FPS HUD elements

#### Camera Transition

The system tracks a `transitionTimer` and lerps between source and target camera transforms during transitions. Input is suppressed during the transition period. Uses `Vector3.lerp` for position and `Quaternion.slerp` for rotation.

No player animation during transition — player model visibility is toggled (hidden when piloting, shown on exit).

## 3. Dual Flight Model

### Space Flight (6DOF Thruster Mode)

The existing `ShipFlightSystem` handles space flight. Changes:

- Read from `ShipFlightInputComponent` instead of `PlayerInputComponent`
- Add throttle management: `ShipFlightInputComponent.throttle` drives `EngineSpecComponent.currentThrottle` via `throttleResponseRate` (smooth response, not instant)
- Consume fuel from `FuelTankComponent` proportional to thrust output and ISP
- Pure Newtonian: no drag, no lift. Thrusters control all 6 axes.

### Atmospheric Flight (Aerodynamic Mode)

#### New System: `AtmosphericFlightSystem` (priority 4, after `ShipFlightSystem`)

Runs only when the ship entity is within an atmosphere zone (detected by comparing ship distance from planet center against `AtmosphereZoneComponent.atmosphereRadius`).

**Aerodynamic forces:**

- **Lift**: `F_lift = 0.5 * density * v^2 * Cl(AoA) * wingArea`. Lift coefficient is looked up from a curve based on angle of attack. Above `stallAngle`, Cl drops sharply — the ship stalls and noses down.
- **Drag**: `F_drag = 0.5 * density * v^2 * Cd * crossSectionArea`. Parasitic drag always present, plus induced drag from lift generation.
- **Control surface torques**: Pitch, yaw, roll torques scaled by `controlSurfaceAuthority * dynamicPressure`. At low speed, control surfaces are weak; at high speed, they're responsive.
- **Re-entry heating**: When `speed > machThreshold * localSpeedOfSound` at given density, heat accumulates on `ShipThermalComponent`. Visual effect triggered via `ReentryHeatingEvent`. Speed of sound is derived from atmosphere composition and temperature (simplified: stored as `speedOfSound` on `AtmosphereZoneComponent`). Default `machThreshold` is 3.0 (heating starts above Mach 3).

**Air density model:** `density = surfaceDensity * exp(-altitude / scaleHeight)`. Altitude = distance from planet center minus `surfaceRadius`.

**VTOL:** All ships are VTOL-capable. Vertical thrust is always sufficient to counter surface-level gravity. However, aerodynamic flight at speed is more fuel-efficient than hovering.

### Transition Zone (Altitude-Based Blending)

Between `transitionAltitude` and `atmosphereRadius`, the two flight models blend:

```
blendFactor = clamp((atmosphereRadius - altitude) / (atmosphereRadius - transitionAltitude), 0, 1)
effectiveForce = lerp(thrusterOnlyForce, thrusterForce + aeroForce, blendFactor)
```

At `blendFactor = 0` (above atmosphere): pure 6DOF.
At `blendFactor = 1` (deep in atmosphere): full aerodynamic forces applied on top of thrusters.

Smooth transition with no abrupt mode switch.

### New Components

**`AtmosphereZoneComponent`** (on planet entity):
```
float atmosphereRadius     // outer edge of atmosphere
float surfaceRadius        // planet surface
float surfaceDensity       // kg/m^3 at surface (Earth: 1.225)
float scaleHeight          // meters (Earth: 8500)
float transitionAltitude   // altitude where blending starts
float speedOfSound         // m/s at surface (Earth: 343)
float machThreshold        // Mach number above which re-entry heating begins (default: 3.0)
String composition         // e.g. "N2_O2", "CO2" (for visual effects)
```

**`ShipAerodynamicsComponent`** (on ship entity):
```
float wingArea
float dragCoefficient
float maxLiftCoefficient
float stallAngle           // degrees
float controlSurfaceAuthority
float vtolThrustFraction   // fraction of max thrust available for vertical hover
float crossSectionArea     // for drag calculation
float heatShieldRating     // re-entry heat resistance
float[] liftCurve          // Cl values at evenly spaced AoA from 0 to 90 degrees (index * 10 = AoA degrees)
```

**`ShipThermalComponent`** (on ship entity):
```
float currentHeat
float maxHeat
float dissipationRate      // heat lost per second
float heatShieldFactor     // multiplier on incoming heat (lower = better shield)
```

## 4. Weapon Piloting System

### New System: `ShipWeaponPilotSystem` (priority 7)

Bridges player input to the existing weapon systems (`ShipWeaponSystem`, `ShipHeatSystem`, `TurretTrackingSystem`).

### Weapon Groups

Ships have up to 4 weapon groups. Each group is a list of hardpoint IDs.

**New Component: `WeaponGroupComponent`** (on ship entity):
```
List<String>[] groups      // [4] arrays of hardpoint IDs
int activeGroup            // 0-3, currently selected
```

- Player selects active group with number keys 1-4
- Left mouse fires group at index 0 (or active group)
- Right mouse fires group at index 1

### Hardpoint Behavior by Type

- **FIXED**: Fire along ship forward vector. Player aims the ship to aim these weapons.
- **TURRET**: Track the targeting reticle independently. Reticle is a world-space point from camera raycast through screen center (cockpit mode) or lead indicator position (when locked).
- **BROADSIDE**: Fire perpendicular to ship axis (port/starboard). Activated when ship orientation relative to target brings broadside arc into alignment.
- **MISSILE_BAY**: Lock-on required before firing. Uses existing `GuidedProjectileComponent` guidance.
- **POINT_DEFENSE**: Automated (existing `PointDefenseSystem`). Not player-controlled.

### Firing Flow

1. `ShipWeaponPilotSystem.update()` checks `ShipFlightInputComponent.fireHeld[0..3]`
2. For each active group with fire held, iterate its hardpoint IDs
3. For each hardpoint, call existing `ShipWeaponSystem.fireHardpoint(shipEntity, hardpointId)`
4. All existing firing logic (cooldown, heat, ammo, overheat) is reused unchanged
5. Gimballed turrets get `currentRotation` updated toward targeting reticle by `TurretTrackingSystem`

### Targeting System

**New System: `TargetingSystem`** (priority 6):

**New Component: `PlayerTargetComponent`** (on player entity):
```
Entity lockedTarget        // hard-locked target (T key)
Entity softTarget          // nearest entity in reticle cone
Vector3 leadIndicatorPos   // predicted firing solution position
float lockTimer            // time-to-lock countdown
```

**Targeting flow:**
- Each frame, find the closest targetable entity within a cone (configurable half-angle, default 5 degrees) around the camera forward direction. This is the `softTarget`.
- When player presses T: if `softTarget` exists, set `lockedTarget = softTarget`. If already locked, unlock.
- Tab cycles through all targetable entities sorted by distance.
- Lead indicator is calculated for locked targets: `leadPos = targetPos + targetVel * (distance / projectileSpeed)`. Uses the active weapon group's average projectile speed.
- `TurretTrackingSystem` uses `lockedTarget` (or `softTarget` if no lock) as its target for gimballed hardpoints.

## 5. Cockpit HUD

### New System: `CockpitHUDSystem` (priority 20)

Scene2D UI rendered over the game view using the existing `UiFactory` skin (Orbitron font, cyan/dark theme).

### HUD Layout

```
+-----------------------------------------------------+
|  [Speed/Alt]              [Target Info]   [Radar]    |
|                                            +---+     |
|                                            | O |     |
|              +-------------+               +---+     |
|              |   Reticle   |                         |
|              |   + Lead    |                         |
|              +-------------+                         |
|                                                      |
|  [Throttle]                          [Weapons]       |
|                                      Grp1: ----     |
|                                      Grp2: --..     |
|  [Shields]   [Fuel]    [Heat]        [Alerts]        |
+-----------------------------------------------------+
```

### HUD Panels

Each panel is a Scene2D `Table` or custom `Actor`.

1. **Speed/Altitude Panel** (top-left):
   - Current speed (m/s)
   - Altitude above surface (when in atmosphere)
   - Mach number (in atmosphere)
   - Heading (degrees)

2. **Throttle Gauge** (left edge, vertical):
   - Vertical bar showing current throttle percentage
   - Color gradient: cyan at low, yellow at mid, red at max
   - Numeric readout

3. **Attitude Indicator** (center, subtle overlay):
   - Pitch ladder lines showing degrees above/below horizon
   - Roll indicator arc
   - Custom `Actor` with `ShapeRenderer` line drawing
   - Only visible in atmosphere (irrelevant in space 6DOF)

4. **Targeting Reticle** (center):
   - Static crosshair when no target
   - Lead indicator diamond when locked (offset from crosshair showing where to aim)
   - Below reticle: target name, distance, closing speed
   - Reticle color: cyan (no target), green (friendly locked), red (hostile locked)

5. **Holographic Radar Sphere** (top-right):
   - 3D wireframe sphere rendered to FBO (256x256)
   - Contacts shown as small colored spheres: green (friendly), red (hostile), yellow (neutral), white (unknown)
   - Sphere rotates to match ship orientation — contacts at relative positions
   - Range ring labels
   - Displayed as a `TextureRegion` from the FBO in the Scene2D HUD
   - Rendered using a dedicated `ModelBatch` with its own camera and viewport

6. **Target Info Panel** (top-right, below radar):
   - Locked target name/class
   - Hull percentage bar
   - Shield percentage bar
   - Distance (m or km)
   - Relative velocity (closing/opening speed)
   - Only visible when a target is locked

7. **Weapon Groups Panel** (bottom-right):
   - List of 4 groups, active group highlighted with cyan border
   - Each group shows: weapon name(s), ammo count/bar, heat bar
   - Overheated weapons flash red
   - Empty groups shown as "---"

8. **Shield Display** (bottom-left):
   - Four quadrant arcs (fore/aft/port/starboard) around a ship silhouette
   - Fill level indicates shield strength per quadrant
   - Color: cyan (healthy) -> yellow (damaged) -> red (critical) -> empty (down)
   - Depends on a quadrant-based `ShieldComponent` existing on the ship entity. If absent, this panel is hidden.

9. **Fuel Gauge** (bottom-center-left):
   - Remaining fuel as percentage
   - Estimated burn time at current throttle (seconds/minutes)
   - Color shifts to yellow < 25%, red < 10%

10. **Ship Heat Display** (bottom-center):
    - Current ship thermal state (from `ShipThermalComponent`)
    - Bar fill with color gradient (blue -> orange -> red)
    - Only relevant in atmosphere during re-entry

11. **Alert Panel** (bottom-right, above weapons):
    - Subscribes to: `MissileLockedEvent`, `ShipOverheatEvent`, `FuelDepletedEvent`, `StallWarningEvent`
    - Shows blinking/pulsing text alerts: "MISSILE LOCK", "OVERHEAT", "FUEL LOW", "STALL"
    - Alerts auto-dismiss after condition clears

### HUD Show/Hide

- Subscribes to `CockpitHUDShowEvent` / `CockpitHUDHideEvent`
- On show: create all Scene2D actors, add to stage
- On hide: remove all actors from stage, dispose textures
- FPS HUD elements (crosshair, health, ammo) are hidden when cockpit HUD is shown, restored on hide

## 6. 3D Cockpit Interior

### Procedural Cockpit Generation

**New Class: `CockpitGeometryBuilder`**

Generates a simple cockpit mesh per ship size class. No external 3D assets required — geometry is built from code.

**Size-specific layouts:**

- **SMALL** (Fighter cockpit): Tight canopy, single seat, wrap-around windshield. ~3m wide, 4m long, 2.5m tall. Minimal console in front of seat.
- **MEDIUM** (Bridge): Open bridge with forward viewport. ~8m wide, 6m long, 3m tall. Console arc with 2-3 station positions.
- **LARGE** (Command deck): Spacious bridge, wide panoramic viewport. ~15m wide, 10m long, 4m tall. Multiple console stations, captain's chair elevated.

**Materials:**
- Dark metallic hull surfaces
- Emissive cyan edge lighting (matching UI color scheme)
- Console surfaces with emissive accents
- Viewport is empty geometry (cutout) — exterior scene is visible through it

### Rendering Approach

**New Component: `CockpitRenderComponent`** (on ship entity):
```
Model cockpitModel
ModelInstance cockpitInstance
Environment cockpitEnvironment   // emissive-heavy lighting
boolean visible                  // only in cockpit camera mode
```

**New System: `CockpitModelSystem`** (render-time system, priority 12):

- When `PlayerMode == PILOTING` and camera mode is COCKPIT:
  - Clear depth buffer after exterior scene render
  - Render cockpit model at camera position using ship's local orientation
  - Cockpit environment uses stronger emissive/ambient lighting for the console glow
- When camera mode is CHASE: cockpit is not rendered
- Model lifecycle: created when entering pilot seat, disposed when exiting

The separate depth buffer clear prevents z-fighting between cockpit geometry and distant exterior objects.

## 7. Ship Data Files

### `data/ships/ship_classes.json`

Defines ship archetypes with all components' initial values. Example entry:

```json
{
  "corvette_scout": {
    "name": "Scout Corvette",
    "sizeClass": "SMALL",
    "mass": 15000,
    "maxThrust": 120000,
    "maxTurnRate": 2.5,
    "maxSpeed": 350,
    "hullHp": 800,
    "engine": {
      "maxThrust": 120000,
      "isp": 3200,
      "throttleResponseRate": 2.0
    },
    "fuel": {
      "maxMass": 2000
    },
    "aerodynamics": {
      "wingArea": 25,
      "dragCoefficient": 0.35,
      "stallAngle": 18,
      "maxLiftCoefficient": 1.4,
      "controlSurfaceAuthority": 0.8,
      "vtolThrustFraction": 0.6,
      "crossSectionArea": 12,
      "heatShieldRating": 0.8,
      "liftCurve": [0.0, 0.2, 0.5, 0.9, 1.2, 1.4, 1.3, 0.8, 0.3]
    },
    "flight": {
      "linearThrust": 120000,
      "strafeThrustFraction": 0.3,
      "verticalThrustFraction": 0.4,
      "pitchYawTorque": 80000,
      "rollTorque": 60000,
      "linearDrag": 0.1,
      "angularDrag": 2.0
    },
    "shields": {
      "maxShield": 400,
      "rechargeRate": 15
    },
    "hardpoints": "corvette_scout",
    "defaultWeaponGroups": [
      ["hp_nose_fixed_1", "hp_nose_fixed_2"],
      ["hp_wing_turret_1"],
      [],
      []
    ]
  }
}
```

### `data/planets/atmosphere_profiles.json`

Reusable atmosphere definitions:

```json
{
  "earth_like": {
    "surfaceDensity": 1.225,
    "scaleHeight": 8500,
    "transitionAltitude": 100000,
    "speedOfSound": 343,
    "machThreshold": 3.0,
    "composition": "N2_O2"
  },
  "thin_mars": {
    "surfaceDensity": 0.02,
    "scaleHeight": 11100,
    "transitionAltitude": 50000,
    "speedOfSound": 240,
    "machThreshold": 3.0,
    "composition": "CO2"
  },
  "dense_venus": {
    "surfaceDensity": 65.0,
    "scaleHeight": 15900,
    "transitionAltitude": 200000,
    "speedOfSound": 410,
    "machThreshold": 3.0,
    "composition": "CO2_SO2"
  }
}
```

### Data Loading

**New Class: `ShipClassRegistry`** (singleton, same pattern as `ShipWeaponRegistry`):
- `loadShipClasses(String path)` — parses `ship_classes.json`
- `getShipClass(String id)` — returns ship class data
- `createShipEntity(Engine, String classId)` — constructs a fully configured ship entity with all components from registry data + hardpoint templates + weapon registry

## 8. System Wiring

### System Priority Order in GameWorld

| Priority | System | Status |
|----------|--------|--------|
| 0 | `PlayerInputSystem` | Modified |
| 1 | `InteractionSystem` | Existing |
| 2 | `PilotTransitionSystem` | **New** |
| 3 | `ShipFlightSystem` | Modified |
| 4 | `AtmosphericFlightSystem` | **New** |
| 5 | `TurretTrackingSystem` | Existing |
| 6 | `TargetingSystem` | **New** |
| 7 | `ShipWeaponPilotSystem` | **New** |
| 8 | `ShipWeaponSystem` | Existing |
| 9 | `ShipHeatSystem` | Existing |
| 10 | `ShipProjectileSystem` | Existing |
| 11 | `ShipCameraSystem` | Modified |
| 12 | `CockpitModelSystem` | **New** |
| 20 | `CockpitHUDSystem` | **New** |

### New Events

| Event | Published by | Consumed by |
|-------|-------------|-------------|
| `CockpitHUDShowEvent` | `PilotTransitionSystem` | `CockpitHUDSystem` |
| `CockpitHUDHideEvent` | `PilotTransitionSystem` | `CockpitHUDSystem` |
| `TargetLockedEvent` | `TargetingSystem` | `CockpitHUDSystem`, audio |
| `TargetLostEvent` | `TargetingSystem` | `CockpitHUDSystem`, audio |
| `AtmosphereEnteredEvent` | `AtmosphericFlightSystem` | `CockpitHUDSystem`, audio/VFX |
| `AtmosphereExitedEvent` | `AtmosphericFlightSystem` | `CockpitHUDSystem`, audio/VFX |
| `ReentryHeatingEvent` | `AtmosphericFlightSystem` | `CockpitHUDSystem`, VFX |
| `StallWarningEvent` | `AtmosphericFlightSystem` | `CockpitHUDSystem`, audio |

### New Components Summary

| Component | Attached To | Purpose |
|-----------|------------|---------|
| `ShipFlightInputComponent` | Player entity (when piloting) | Flight-specific input state |
| `WeaponGroupComponent` | Ship entity | Weapon group assignments |
| `PlayerTargetComponent` | Player entity | Target lock and lead indicator |
| `AtmosphereZoneComponent` | Planet entity | Atmosphere physical properties |
| `ShipAerodynamicsComponent` | Ship entity | Aerodynamic properties |
| `ShipThermalComponent` | Ship entity | Ship heat state |
| `CockpitRenderComponent` | Ship entity | Cockpit 3D model reference |

### Data Flow Diagram

```
PlayerInputSystem
    |
    v (writes)
ShipFlightInputComponent
    |
    +---> ShipFlightSystem (reads throttle/strafe/thrust/pitch/yaw/roll)
    |         | (applies forces via PhysicsBodyComponent)
    |         v
    |     AtmosphericFlightSystem (modifies forces based on atmosphere)
    |
    +---> TargetingSystem (reads lock/cycle inputs)
    |         | (updates PlayerTargetComponent)
    |         v
    |     TurretTrackingSystem (reads target, updates hardpoint rotation)
    |
    +---> ShipWeaponPilotSystem (reads fire inputs)
              | (calls ShipWeaponSystem.fireHardpoint())
              v
          ShipWeaponSystem -> ShipProjectileSystem (spawns projectiles)
          ShipHeatSystem (manages heat)

ShipCameraSystem (reads seat position, camera mode)
    |
    v
CockpitModelSystem (renders 3D cockpit in cockpit view)
    |
    v
CockpitHUDSystem (reads all ship state, renders Scene2D UI)
```

## File Inventory

### New Files (21)

**Components (7):**
- `ship/components/ShipFlightInputComponent.java`
- `ship/components/ShipAerodynamicsComponent.java`
- `ship/components/ShipThermalComponent.java`
- `ship/components/CockpitRenderComponent.java`
- `ship/weapons/components/WeaponGroupComponent.java`
- `player/components/PlayerTargetComponent.java`
- `core/components/AtmosphereZoneComponent.java`

**Systems (6):**
- `player/systems/PilotTransitionSystem.java`
- `ship/systems/AtmosphericFlightSystem.java`
- `ship/systems/CockpitModelSystem.java`
- `ship/weapons/systems/ShipWeaponPilotSystem.java`
- `ship/weapons/systems/TargetingSystem.java`
- `ui/CockpitHUDSystem.java`

**Events (8):**
- `ui/events/CockpitHUDShowEvent.java`
- `ui/events/CockpitHUDHideEvent.java`
- `ship/weapons/events/TargetLockedEvent.java`
- `ship/weapons/events/TargetLostEvent.java`
- `ship/events/AtmosphereEnteredEvent.java`
- `ship/events/AtmosphereExitedEvent.java`
- `ship/events/ReentryHeatingEvent.java`
- `ship/events/StallWarningEvent.java`

**Data/Builders:**
- `ship/CockpitGeometryBuilder.java`
- `ship/data/ShipClassRegistry.java`

**Data Files (2):**
- `resources/data/ships/ship_classes.json`
- `resources/data/planets/atmosphere_profiles.json`

### Modified Files (5)

- `player/systems/PlayerInputSystem.java` — Add mode branch for PILOTING
- `ship/systems/ShipFlightSystem.java` — Read `ShipFlightInputComponent`, add throttle/fuel management
- `ship/systems/ShipCameraSystem.java` — Minor: cockpit model visibility coordination
- `core/GameWorld.java` — Register new systems
- `ui/GameScreen.java` — Add cockpit render pass, wire cockpit HUD
