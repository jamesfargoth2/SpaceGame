# FPS Movement System Design

**Date:** 2026-05-25
**Scope:** Floating origin foundation + first-person character movement with full dynamic rigid body physics, procedural terrain test scene, and debug HUD.

---

## 1. Overview

Build the foundational layer (coordinate system, event bus, ECS bootstrap, Bullet physics) and a first-person movement controller on top of it. The player character is a Bullet dynamic rigid body with sim/tactical movement feel: momentum-based acceleration, head bob, stamina, and slope handling. A procedural terrain test scene provides a walking environment.

### Build Order

1. Core infrastructure: EventBus, CoordinateManager, GameWorld (ECS bootstrap)
2. Bullet physics integration: BulletPhysicsSystem, PhysicsBodyComponent, PhysicsBodySystem
3. FPS character: components, PlayerInputSystem, PlayerMovementSystem, CameraSystem
4. Procedural terrain test scene
5. Debug HUD

### Out of Scope

- Ship piloting / 6DOF controls
- Pilot seat transition state machine
- Networking / server-authoritative movement
- PBR rendering pipeline
- Gamepad/HOTAS input (gdx-controllers deferred)
- Weapons, combat, interactions

---

## 2. Floating Origin & Coordinate System

### CoordinateManager

A standalone service (not an ECS system) that all spatial systems query.

**State:**
- `originOffset`: `double x, y, z` — the galaxy-space position of local `(0,0,0)`

**Methods:**
- `toLocalSpace(double gx, double gy, double gz) -> Vector3` — converts galaxy doubles to local floats relative to current origin
- `toGalaxySpace(Vector3 local) -> double[3]` — converts local floats back to galaxy doubles
- `checkRebase(Vector3 playerLocalPos)` — if player distance from origin exceeds threshold (1000m), triggers rebase

**Rebase:**
- Computes delta = player's current local position
- Adds delta to `originOffset` (double precision)
- Publishes `OriginRebasedEvent(deltaX, deltaY, deltaZ)` on the EventBus
- All systems with cached world positions listen and shift by negative delta

### EventBus

Lightweight synchronous pub/sub.

**API:**
- `subscribe(Class<T> eventType, EventListener<T> listener)`
- `unsubscribe(Class<T> eventType, EventListener<T> listener)`
- `publish(T event)`

**Behavior:**
- Synchronous dispatch within the same frame
- Listeners called in subscription order
- Events are plain data objects (no logic)

**Events defined in this spec:**
- `OriginRebasedEvent` — `float deltaX, deltaY, deltaZ`

---

## 3. ECS Bootstrap — GameWorld

Central orchestrator that owns all shared infrastructure.

**Responsibilities:**
- Creates the Ashley `Engine`
- Creates and holds `CoordinateManager`, `EventBus`, `btDynamicsWorld`
- Registers systems in priority order:
  1. `PlayerInputSystem` (priority 0) — capture input
  2. `PlayerMovementSystem` (priority 1) — apply forces
  3. `BulletPhysicsSystem` (priority 2) — step physics
  4. `PhysicsBodySystem` (priority 3) — sync transforms
  5. `CameraSystem` (priority 4) — update camera
  6. `DebugHudSystem` (priority 10) — render HUD
- `update(float delta)` — calls `engine.update(delta)`
- `dispose()` — disposes Bullet world, all systems, all entities

---

## 4. Bullet Physics Integration

### BulletPhysicsSystem (EntitySystem)

Owns the Bullet simulation world.

**Setup:**
- `btDefaultCollisionConfiguration`
- `btCollisionDispatcher`
- `btDbvtBroadphase`
- `btSequentialImpulseConstraintSolver`
- `btDiscreteDynamicsWorld` with gravity `(0, -9.81, 0)`

**Per-frame:**
- Steps the world with fixed timestep `1/60s`, max substeps 3
- Listens for `OriginRebasedEvent`: translates all rigid body transforms by negative delta

**Disposal:**
- Disposes world, solver, broadphase, dispatcher, config in reverse order

### PhysicsBodyComponent (Ashley Component)

**Fields:**
- `btRigidBody body`
- `btCollisionShape shape`
- `float mass`
- `float friction`
- `float restitution`
- `short collisionGroup`
- `short collisionMask`
- `boolean rebaseOnOriginShift` (default true)

### PhysicsBodySystem (IteratingSystem)

Iterates entities with `TransformComponent` + `PhysicsBodyComponent`.

**Per-entity per-frame (after physics step):**
- Reads the rigid body's world transform
- Writes position and rotation into `TransformComponent`

---

## 5. FPS Character — Components

### TransformComponent
- `Vector3 position` — local-space position
- `Quaternion rotation` — orientation

### PlayerTagComponent
- Empty marker. Identifies the local player entity.

### PlayerInputComponent
- `float moveForward` — -1 to +1 (W/S)
- `float moveStrafe` — -1 to +1 (A/D)
- `boolean sprint`
- `boolean jumpRequested`
- `boolean crouch`
- `float mouseDeltaX`
- `float mouseDeltaY`

### MovementStateComponent
- `boolean isGrounded`
- `boolean isSprinting`
- `boolean isCrouching`
- `float currentSpeed`
- `float currentStamina`
- `float maxStamina` (default 100)
- `float staminaDrainRate` (default 20 per second while sprinting)
- `float staminaRegenRate` (default 10 per second while not sprinting)
- `Vector3 groundNormal`
- `float fallVelocity` (for landing impact detection)

### FPSCameraComponent
- `float eyeHeight` (default 1.7m)
- `float crouchEyeHeight` (default 1.0m)
- `float currentEyeHeight` (interpolated)
- `float headBobAmplitude` (default 0.04m)
- `float headBobFrequency` (default 8.0 Hz)
- `float headBobPhase` (accumulated)
- `float pitchAngle` (clamped ±85°)
- `float yawAngle`
- `float mouseSensitivity` (default 0.15)
- `float landingDipAmount` (current dip offset, decays to 0)

---

## 6. FPS Character — Systems

### PlayerInputSystem

**Priority:** 0 (runs first)

Implements `InputProcessor`, registered via `Gdx.input.setInputProcessor`.

**Per-frame (`update`):**
- Polls `Gdx.input.isKeyPressed` for continuous movement keys (WASD, Shift, Ctrl)
- Writes normalized values to `PlayerInputComponent`
- Mouse delta accumulated from `mouseMoved` callback, written to component and reset each frame
- Jump is edge-triggered: set `jumpRequested = true` on `keyDown(SPACE)`, consumed by movement system

**Cursor:**
- Calls `Gdx.input.setCursorCatched(true)` on system initialization
- Escape key uncatches cursor (for future menu use)

### PlayerMovementSystem

**Priority:** 1 (runs after input, before physics step)

Iterates entities with `PlayerInputComponent`, `PhysicsBodyComponent`, `MovementStateComponent`, `TransformComponent`.

**Ground detection:**
- Short downward raycast from capsule bottom (ray length = capsule half-height + 0.15m)
- If hit: `isGrounded = true`, store `groundNormal`
- Slope check: if angle between ground normal and up vector > 45°, treat as not grounded (slide)

**Movement constants (tunable):**
- Walk speed: 3.5 m/s
- Sprint speed: 6.0 m/s
- Jump impulse: 5.0 m/s upward
- Ground force multiplier: 50.0
- Air force multiplier: 10.0 (20% of ground)
- Ground linear damping: 0.9
- Air linear damping: 0.1
- Max slope angle: 45°

**Per-entity per-frame:**
1. Compute movement direction from input + yaw rotation (forward/strafe in world XZ plane)
2. Project direction along ground normal if grounded and slope < max
3. Determine target speed (walk or sprint, reduced if crouching)
4. Apply horizontal force = direction × force multiplier
5. Set linear damping based on grounded state
6. If `jumpRequested` and `isGrounded`: apply upward impulse, clear flag
7. Update stamina: drain if sprinting, regen otherwise, clamp to [0, maxStamina]. If stamina reaches 0, force sprint off.
8. If crouch toggled: swap collision shape between standing capsule `btCapsuleShape(0.3, 1.2)` (total 1.8m) and crouched capsule `btCapsuleShape(0.3, 0.4)` (total 1.0m). Check headroom with an upward raycast before un-crouching.
9. Write derived state to `MovementStateComponent`

**Rotation:**
- Yaw applied by rotating the rigid body's transform around Y axis (no angular velocity — angular factor is locked to 0,0,0)
- Pitch stored in `FPSCameraComponent` only (body doesn't pitch)

### CameraSystem

**Priority:** 4 (runs after physics sync)

Reads `TransformComponent`, `FPSCameraComponent`, `MovementStateComponent`.

**Per-frame:**
- Position camera at entity position + `(0, currentEyeHeight, 0)`
- Interpolate `currentEyeHeight` toward target (standing or crouching) at ~10 units/s
- Set camera direction from yaw + pitch angles
- **Head bob:** If grounded and speed > 0.5 m/s:
  - Advance `headBobPhase += speed * headBobFrequency * delta`
  - Vertical offset = `sin(headBobPhase) * headBobAmplitude * (speed / walkSpeed)`
  - Horizontal offset = `cos(headBobPhase * 0.5) * headBobAmplitude * 0.5`
  - Add offsets to camera position
- **Landing dip:** On `isGrounded` transition false→true:
  - Set `landingDipAmount = clamp(fallVelocity * 0.02, 0, 0.15)`
  - Each frame, decay toward 0 at ~8 units/s
  - Subtract from camera Y position
- Call `camera.update()`

---

## 7. Procedural Terrain Test Scene

**Heightmap generation:**
- 257×257 vertices (256×256 quads), world size 500m × 500m
- Height values from 2 octaves of simplex noise: `height = noise(x * 0.005, z * 0.005) * 30 + noise(x * 0.02, z * 0.02) * 5`
- Produces gentle rolling hills (~30m amplitude) with smaller detail (~5m)

**Rendering:**
- Build a libGDX `Mesh` with `VertexAttributes`: position (3f), normal (3f), color (4f)
- Normals computed from cross product of neighbor height differences
- Color: green-brown gradient based on height and slope (steeper = more brown/rocky)
- Rendered with a basic `ShaderProgram` (simple diffuse + directional light) or libGDX `ModelBatch` with `DefaultShader`
- Single directional light angled at `(-0.4, -0.8, -0.3)`, ambient `(0.3, 0.3, 0.35)`

**Physics:**
- `btHeightfieldTerrainShape` from the same height array
- Added as a static (mass 0) rigid body to the Bullet world

**Scatter objects:**
- 10-20 box rigid bodies (1m-3m cubes) placed on the terrain surface at random positions
- Dynamic bodies (mass 50-200kg) so the player can push them
- Rendered as simple colored cubes via `ModelBuilder`

---

## 8. Debug HUD

**DebugHudSystem (EntitySystem, priority 10):**

Uses Scene2D `Stage` with `Viewport` (ScreenViewport for pixel-perfect text).

**Displayed fields (updated every frame):**
- **Galaxy Pos:** `x, y, z` (doubles from CoordinateManager, formatted to 2 decimal places)
- **Local Pos:** `x, y, z` (floats from TransformComponent)
- **Velocity:** magnitude in m/s
- **Ground:** `true/false`
- **State:** `walking | sprinting | crouching | airborne`
- **Stamina:** current / max
- **FPS:** `Gdx.graphics.getFramesPerSecond()`

**Rendering:**
- Uses libGDX's built-in `BitmapFont` (default font, white, 15px)
- `Label` widgets in a `Table` anchored to top-left
- Semi-transparent black background behind text for readability
- Toggle visibility with F3 key

**Lifecycle:**
- Creates its own `SpriteBatch` and `Stage`
- Renders during its `update()` call within the Ashley engine (priority 10 ensures it runs after all simulation systems)
- Disposes batch, stage, font on shutdown

---

## 9. Player Entity Construction

The player entity is assembled in `GameWorld` (or a factory method) with these components:
- `TransformComponent` — spawned at `(0, heightAt(0,0) + 2, 0)` (2m above terrain center)
- `PlayerTagComponent`
- `PlayerInputComponent`
- `MovementStateComponent` — stamina initialized to max
- `FPSCameraComponent` — default values
- `PhysicsBodyComponent`:
  - Shape: `btCapsuleShape(0.3, 1.2)` (total height ~1.8m with hemispheres)
  - Mass: 80kg
  - Friction: 1.0
  - Restitution: 0.0
  - Angular factor: `(0, 0, 0)` — no tumbling
  - Linear damping: set dynamically by movement system

---

## 10. GalacticOdyssey Integration

**`create()`:**
1. Initialize Bullet via `Bullet.init()`
2. Create `EventBus`
3. Create `CoordinateManager(eventBus)`
4. Generate terrain heightmap
5. Create `GameWorld(eventBus, coordinateManager)` — this creates the Ashley engine, Bullet world, and registers all systems
6. Create terrain entity (mesh + physics shape)
7. Create scatter box entities
8. Create player entity
9. Create `PerspectiveCamera(75, viewportWidth, viewportHeight)`, near=0.1, far=5000
10. Pass camera reference to `CameraSystem`

**`render()`:**
1. Clear screen (color + depth)
2. Call `gameWorld.update(Gdx.graphics.getDeltaTime())`
3. Render terrain mesh with camera
4. Render box entities
5. Debug HUD renders during its system update (priority 10, last in the engine tick)

**`resize()`:**
- Update camera viewport and HUD stage viewport

**`dispose()`:**
- Dispose `GameWorld` (which disposes all systems, entities, Bullet world)
- Dispose terrain mesh, models, shaders
- Dispose debug HUD resources

---

## 11. File Placement

Following the project's folder layout conventions:

```
core/src/main/java/com/galacticodyssey/
  core/
    GalacticOdyssey.java          (modify existing)
    GameWorld.java                 (new - ECS bootstrap)
    EventBus.java                 (new)
    CoordinateManager.java        (new)
    events/
      OriginRebasedEvent.java     (new)
    components/
      TransformComponent.java     (new)
      PhysicsBodyComponent.java   (new)
      PlayerTagComponent.java     (new)
    systems/
      BulletPhysicsSystem.java    (new)
      PhysicsBodySystem.java      (new)

  player/
    components/
      PlayerInputComponent.java   (new)
      MovementStateComponent.java (new)
      FPSCameraComponent.java     (new)
    systems/
      PlayerInputSystem.java      (new)
      PlayerMovementSystem.java   (new)
      CameraSystem.java           (new)

  ui/
    systems/
      DebugHudSystem.java         (new)

  data/
    TerrainGenerator.java         (new)
```

Total: 1 modified file, 16 new files.

---

## 12. Testing Strategy

All simulation systems are testable without a GL context:

- **CoordinateManager:** Test toLocalSpace/toGalaxySpace conversions, rebase threshold trigger, double precision preservation
- **EventBus:** Test subscribe/publish/unsubscribe, ordering
- **PlayerMovementSystem:** Create a Bullet world in test, spawn a capsule, apply input, step physics, assert velocity/position. Test: walking, sprinting, jumping, slope rejection, stamina drain/regen, crouch headroom check
- **PhysicsBodySystem:** Verify transform sync after physics step
- **BulletPhysicsSystem:** Test gravity, collision, rebase transform shift
- **TerrainGenerator:** Test heightmap dimensions, value ranges, normal computation

Camera and input systems require GL context and are tested manually via the test scene.
