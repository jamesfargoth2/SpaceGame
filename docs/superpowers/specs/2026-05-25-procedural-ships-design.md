# Procedural Spaceship System — Design Spec

## Overview

A procedural generation system that creates spaceship meshes at runtime, with full walkable interiors, player enter/exit mechanics, and 6DOF piloting. Ships are generated from a seed and size class — no hard 3D assets required.

## 1. Hull Generation

### 1.1 Spine and Cross-Sections

The hull is defined by a **cubic Bezier spine curve** running nose-to-tail, with **superellipse cross-sections** placed at intervals along it.

A superellipse is defined by `|x/a|^n + |y/b|^n = 1` where `a` and `b` are semi-axes and `n` controls the shape:
- `n = 2.0` → standard ellipse
- `n > 2.0` → rounded rectangle (squarish)
- `n < 2.0` → diamond/pinched

Each cross-section has: width (`a`), height (`b`), superellipse exponent (`n`), and a position along the spine (`t` from 0.0 at nose to 1.0 at tail). Values are randomized within per-size-class constraints.

### 1.2 Size Class Parameters

| Parameter | Small | Medium | Large |
|-----------|-------|--------|-------|
| Spine length | 8–12 m | 18–30 m | 40–70 m |
| Cross-section count | 5–7 | 8–12 | 12–18 |
| Max width | 3–5 m | 6–12 m | 15–25 m |
| Max height | 2–4 m | 4–8 m | 8–15 m |
| Wing pairs | 0–1 | 1–2 | 1–3 |
| Engine pods | 1–2 | 2–4 | 2–6 |

### 1.3 Mesh Generation Pipeline

1. **Generate spine** — cubic Bezier from nose to tail with random control points constrained per size class.
2. **Place cross-sections** — at evenly spaced `t` values along the spine, each with randomized width, height, and exponent.
3. **Smooth interpolation** — Catmull-Rom interpolation between adjacent cross-sections creates intermediate vertex rings.
4. **Loft into mesh** — connect vertex rings into triangle strips to form the hull surface.
5. **Compute normals** — smooth vertex normals for Phong lighting.
6. **Attach sub-shapes** — wings (swept tapered quads), engine pods (cylinders/cones), fins (flat tapered shapes) at parametric hardpoints along the spine.

### 1.4 Panel Lines

The hull surface is subdivided into panels by the cross-section rings (horizontal seams) and evenly spaced longitude lines (vertical seams). Alternating panels are inset by 0.01–0.02 m to create visible seams that catch light. Pattern varies per seed.

### 1.5 Sub-Shape Attachment

Wings, fins, and engine pods are attached at hardpoint positions defined as `(t, angle)` pairs on the hull surface — `t` is spine position, `angle` is rotation around the cross-section.

- **Wings:** swept quad geometry with taper. Root chord at hull surface, tip chord smaller. Sweep angle 15–45°.
- **Engine pods:** cylinder + cone combination. Attached at tail end of hull or on wing tips.
- **Fins:** flat tapered shapes. Dorsal, ventral, or lateral mounting.

## 2. Interior Generation

### 2.1 Phase 1: Voxelize Hull Volume

Discretize the hull interior into a 3D grid of ~1 m cells. Each cell is tagged as "inside hull" or "outside" by testing against the cross-section boundary at that spine position. The outermost layer of inside cells is reserved for hull walls (not packable).

### 2.2 Phase 2: Room Placement

Each size class has a room manifest:

| Room | Small | Medium | Large | Grid size (cells) |
|------|-------|--------|-------|--------------------|
| Cockpit | Required | Required | Required | 3×3×2 – 4×4×3 |
| Corridor | Required | Required | Required | 1×1×N (variable) |
| Engine Room | — | Required | Required | 3×3×2 – 5×5×3 |
| Cargo Bay | — | Optional | Required | 4×3×2 – 6×5×3 |
| Crew Quarters | — | Optional | Optional | 3×3×2 |
| Medbay | — | — | Optional | 3×2×2 |
| Armory | — | — | Optional | 2×2×2 |

Placement algorithm:
1. **Cockpit** at the nose end (lowest `t` values with sufficient volume).
2. **Engine room** at the tail end.
3. **Remaining rooms** packed greedily front-to-back, largest first, checking that the room bounding box fits within "inside" voxels.
4. **Corridors** via A* pathfinding through remaining empty interior cells, connecting all room doorways into a connected graph.
5. **Airlock/entry point** placed on the hull underside near center of mass, with a ramp extending down.

### 2.3 Phase 3: Room Mesh Generation

Each room is built from flat floor/wall/ceiling panels — simple box geometry. Room types are visually distinguished by color:

| Room | Floor Color | Wall Accent |
|------|-------------|-------------|
| Cockpit | Dark grey | Blue instrument panels |
| Corridor | Medium grey | Strip lighting (bright vertex row) |
| Engine Room | Dark metal | Orange/red warning accents |
| Cargo Bay | Scuffed brown | Yellow hazard markings |
| Crew Quarters | Warm grey | Soft white accents |
| Medbay | White | Green/teal accents |
| Armory | Dark grey | Red accents |

Rooms also contain simple box-geometry furniture: pilot chair, cargo crates, beds, consoles (box with emissive face). All procedural.

The cockpit gets a windshield cutout in the hull mesh — a section of the hull at the nose is removed or made transparent, replaced by cockpit interior geometry that frames the exterior view.

### 2.4 Interior Physics

The interior runs in a **separate Bullet `btDynamicsWorld`** per the project's architectural rules. Interior wall/floor/ceiling geometry is used to create a `btBvhTriangleMeshShape` (static concave mesh). The player's physics capsule is moved between the exterior and interior physics worlds during enter/exit transitions.

## 3. ECS Integration

### 3.1 Components

```
Ship Entity
  ├── TransformComponent          (existing — position/rotation in world space)
  ├── PhysicsBodyComponent        (existing — Bullet rigid body for exterior)
  ├── ShipDataComponent           (blueprint ref, size class, seed, stats)
  ├── ShipMeshComponent           (generated hull Mesh + ModelInstance)
  ├── ShipInteriorComponent       (interior mesh, room layout, btDynamicsWorld)
  ├── ShipFlightComponent         (throttle, angular velocity, 6DOF state)
  ├── PilotSeatComponent          (trigger position in interior, occupied flag)
  └── ShipEntryPointComponent     (ramp position, trigger volume, open/closed)
```

**ShipDataComponent** holds ship stats loaded from JSON data files:

| Stat | Small | Medium | Large |
|------|-------|--------|-------|
| Mass (kg) | 5,000–15,000 | 30,000–80,000 | 150,000–500,000 |
| Max thrust (N) | 50,000 | 200,000 | 500,000 |
| Max turn rate (°/s) | 90 | 45 | 20 |
| Max speed (m/s) | 150 | 100 | 60 |
| Hull HP | 200 | 800 | 3,000 |

### 3.2 New Systems

| System | Priority | Responsibility |
|--------|----------|----------------|
| InteractionSystem | 0 | Proximity checks for entry points, pilot seats, interaction prompts |
| ShipFlightSystem | 1 | Reads input when piloting, applies Bullet forces/torques |
| ShipInteriorPhysicsSystem | 3 | Steps the interior btDynamicsWorld each frame |
| ShipCameraSystem | 4 | Manages cockpit/chase cam during piloting |

These integrate with existing systems (full priority map):
- 0: PlayerInputSystem (existing), InteractionSystem (new)
- 1: PlayerMovementSystem (existing), ShipFlightSystem (new — only one active at a time based on PlayerMode)
- 2: BulletPhysicsSystem (existing, exterior world)
- 3: PhysicsBodySystem (existing), ShipInteriorPhysicsSystem (new)
- 4: CameraSystem (existing), ShipCameraSystem (new — only one active at a time based on PlayerMode)
- 10: DebugHudSystem (existing)

### 3.3 Events

All state transitions communicate through the existing `EventBus`:

| Event | Trigger | Subscribers |
|-------|---------|-------------|
| ShipEntryAvailableEvent | Player within 3 m of ramp | UI (show prompt) |
| PlayerEnterShipEvent | E pressed near ramp | PlayerMovementSystem, InteractionSystem |
| PilotSeatAvailableEvent | Player within 2 m of seat | UI (show prompt) |
| PlayerStartPilotingEvent | E pressed near seat | ShipFlightSystem, ShipCameraSystem, PlayerInputSystem |
| PlayerStopPilotingEvent | E pressed while piloting | Same subscribers, reverse |
| PlayerExitShipEvent | E pressed near airlock | PlayerMovementSystem, InteractionSystem |

## 4. Player State Machine

### 4.1 States

A new `PlayerStateComponent` on the player entity:

```
PlayerMode enum: ON_FOOT_EXTERIOR, ON_FOOT_INTERIOR, PILOTING

PlayerStateComponent:
  - currentMode: PlayerMode
  - currentShip: Entity (null when exterior)
  - interactionTarget: Entity (nearest interactable, null if none)
```

### 4.2 Transitions

**ON_FOOT_EXTERIOR → ON_FOOT_INTERIOR:**
1. Player presses E within 3 m of ship ramp
2. `PlayerEnterShipEvent` fired
3. Player exterior physics body deactivated
4. Player capsule created in ship's interior `btDynamicsWorld`
5. Player position set to interior airlock location
6. Camera transitions into interior

**ON_FOOT_INTERIOR → PILOTING:**
1. Player presses E within 2 m of pilot seat
2. `PlayerStartPilotingEvent` fired
3. `PlayerInputSystem` routes inputs to `ShipFlightComponent`
4. `ShipCameraSystem` activates (cockpit view by default)
5. `ShipFlightSystem` begins processing
6. Player movement system paused for this entity

**PILOTING → ON_FOOT_INTERIOR:**
1. Player presses E
2. `PlayerStopPilotingEvent` fired
3. Input routing returns to movement
4. Camera returns to FPS in cockpit
5. Ship retains current velocity (drifts)

**ON_FOOT_INTERIOR → ON_FOOT_EXTERIOR:**
1. Player presses E within 3 m of airlock
2. `PlayerExitShipEvent` fired
3. Interior physics capsule removed
4. Exterior physics body reactivated at ramp position
5. Interior physics world paused

### 4.3 InteractionSystem

Generic proximity-based interaction system (not ship-specific):
1. Queries all entities with interaction trigger components
2. Checks distance from player to each trigger
3. Updates `PlayerStateComponent.interactionTarget`
4. Fires UI events for prompt display
5. On E press, fires the appropriate transition event

Future interactables (terminals, NPCs, doors) plug in via `InteractableComponent`.

## 5. Ship Flight (6DOF)

### 5.1 Input Mapping

```
W/S       → Forward/reverse thrust (ship local Z)
A/D       → Strafe left/right (ship local X)
Space/Ctrl → Thrust up/down (ship local Y)
Mouse X   → Yaw torque
Mouse Y   → Pitch torque
Q/E       → Roll torque
```

### 5.2 Physics Model

Ship flight uses Bullet rigid body dynamics. No artificial velocity caps — drag forces provide natural speed limiting.

**Per-size-class tuning (from JSON data files):**

| Parameter | Small | Medium | Large |
|-----------|-------|--------|-------|
| Linear thrust (N) | 50,000 | 200,000 | 500,000 |
| Strafe thrust (% of main) | 60% | 40% | 25% |
| Vertical thrust (% of main) | 60% | 40% | 25% |
| Torque — pitch/yaw (Nm) | 20,000 | 50,000 | 100,000 |
| Torque — roll (Nm) | 15,000 | 30,000 | 60,000 |
| Linear drag | 0.3 | 0.5 | 0.7 |
| Angular drag | 2.0 | 3.0 | 5.0 |

### 5.3 ShipFlightSystem Per-Frame Logic

1. Read `PlayerInputComponent` directional and mouse input
2. Convert to local-space force/torque vectors using ship's current rotation
3. Apply forces via `btRigidBody.applyCentralForce()` and torques via `btRigidBody.applyTorque()`
4. Bullet integration handles drag (set via `btRigidBody.setDamping()`)

### 5.4 Coordinate Integration

Ship position is in local float space near origin. The existing `CoordinateManager` handles floating origin rebasing. During flight, the ship becomes the anchor entity — when it moves far from origin, everything rebases around it.

## 6. Camera System

### 6.1 First-Person Cockpit

- Camera at pilot seat eye point inside the ship interior
- Looks through cockpit windshield cutout
- Small head-movement lag: camera rotation lerps behind ship rotation for momentum feel
- HUD overlays instrument readouts (speed, altitude, heading)

### 6.2 Third-Person Chase

- Camera orbits behind and above the ship
- Follow distance: small 15 m, medium 30 m, large 60 m
- Spring-damper follow: position lerps, rotation slerps
- Full ship model visible

### 6.3 Toggle

V key toggles between cockpit and chase cam. Both use the existing `PerspectiveCamera` — `ShipCameraSystem` takes ownership during piloting, yields back to `CameraSystem` when on foot.

## 7. Visual Detail

### 7.1 Color and Material

Procedural color scheme per seed:
1. **Base hull color** — from curated palette (greys, whites, dark blues, military greens)
2. **Accent color** — complementary, for stripes, wing tips, engine glow
3. **Trim color** — for panel line edges and detail highlights

Applied as vertex colors — no textures. Consistent with existing terrain rendering (vertex colors + Phong shading).

### 7.2 Engine Glow

Engine pods get emissive vertex colors (blue, orange, or white per seed). Emissive intensity modulated by throttle input via a per-vertex emissive attribute.

### 7.3 Shader

Extends existing Phong shader with:
- Vertex color support (already present on terrain)
- Emissive channel (float vertex attribute) for engine glow and interior lighting strips
- No new texture samplers

## 8. Ship Spawning and World Integration

### 8.1 ShipFactory

Single entry point for ship entity creation:

```
ShipBlueprint (JSON) → ShipFactory
  1. ShipHullGenerator → hull mesh + metadata
  2. ShipInteriorGenerator → interior mesh + room layout + btDynamicsWorld
  3. Collision shape creation (btConvexHullShape exterior, btBvhTriangleMeshShape interior)
  4. Entity assembly (all components)
  5. Add to Ashley Engine
```

### 8.2 Collision Shapes

- **Exterior:** `btConvexHullShape` from simplified hull vertices. For large ships, `btCompoundShape` of 3–5 convex sub-hulls.
- **Interior:** `btBvhTriangleMeshShape` (static concave) for room geometry.
- **Ramp trigger:** `btGhostObject` with box shape for overlap detection.

### 8.3 Landing and Placement

Ships are placed on terrain at spawn:
1. Select world position on sufficiently flat terrain
2. Raycast to get ground height
3. Place with landing gear offset
4. Ramp extends to ground

Landed ships are static bodies. On piloting start, body switches to dynamic. No landing/takeoff animation in this iteration.

### 8.4 Initial Test Setup

GameWorld spawns 3 ships on initialization:
- 1 small ship near player spawn (within walking distance)
- 1 medium ship further away
- 1 large ship at far end of terrain

Each with a different seed for visual variety.

### 8.5 Resource Lifecycle

All generated meshes, collision shapes, and interior physics worlds implement `Disposable`. Tracked by their respective components. Disposed when the ship entity is removed from the engine.

## 9. Data Files

Ship parameters are defined in JSON, loaded at runtime:

**`data/ships/ship-classes.json`** — size class definitions (parameter ranges, room manifests, stat ranges)

**`data/ships/flight-params.json`** — per-class flight tuning (thrust, drag, torque values)

**`data/ships/color-palettes.json`** — curated hull color palettes

## 10. Out of Scope (Future Work)

- Landing/takeoff animations
- Ship damage and destruction
- Weapon mounts and combat
- AI-piloted ships
- Multiplayer ship synchronization
- Ship customization UI
- Docking with stations
- Interior decoration placement
- Ship-to-ship boarding
- Atmospheric flight model differences
- LOD for distant ships
