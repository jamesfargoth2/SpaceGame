# Core Systems

The `core` package contains the foundational systems that every other part of the game depends on: the floating-origin coordinate system, the central event bus, the ECS world bootstrap, and the Bullet physics integration.

---

## Floating-Origin Coordinate System

**`CoordinateManager`**

Galaxy-scale distances exceed the precision of 32-bit floats, so all authoritative positions are stored as 64-bit `double` values in galaxy-space. The active scene runs in local-space with 32-bit `float` coordinates, keeping the player near the world origin `(0, 0, 0)`.

When the player moves far enough from the origin (configurable threshold, default ~10 km), `CoordinateManager` triggers a **rebase**: all Bullet rigid bodies and local-space transforms are shifted so the player is back near `(0, 0, 0)`, and a `OriginRebasedEvent` is published to the event bus so every physics-aware system can adjust.

**Rules:**
- Never store galaxy-space distances in `float` variables. Always convert at the local-space boundary.
- `CoordinateManager.toLocal(double)` performs the galaxy → local conversion.
- `CoordinateManager.toGalaxy(float)` is the inverse, used when writing results back.

---

## Event Bus

**`EventBus`**

Central pub/sub system that decouples all cross-system communication. Systems publish strongly-typed event objects; other systems register typed listeners and react independently.

**Usage:**
```java
// Subscribe
eventBus.subscribe(EntityKilledEvent.class, this::onEntityKilled);

// Publish
eventBus.publish(new WeaponFiredEvent(shooter, direction));
```

All events are dispatched synchronously on the calling thread (the main game loop thread). There is no queuing or deferred dispatch — listeners are called immediately during `publish`.

**Key event families:**
- `OriginRebasedEvent` — floating origin shifted
- `PlayerEnterShipEvent / PlayerExitShipEvent` — ship entry/exit lifecycle
- `PlayerStartPilotingEvent / PlayerStopPilotingEvent` — pilot seat transitions
- `InteractionPromptEvent` — show/hide interaction UI
- `ShipEntryAvailableEvent / PilotSeatAvailableEvent` — proximity triggers

---

## ECS World Bootstrap

**`GameWorld`**

Creates the Ashley `Engine` and registers all entity systems in priority order. Acts as the single assembly point for the game's simulation: it wires physics, combat, player, ship, water, and UI systems together without creating direct cross-system dependencies.

Systems are added with explicit priorities so execution order is deterministic each frame. Higher-priority systems execute first.

Key registration order (representative):
1. Player input (priority 1)
2. Movement / flight (priority 3–5)
3. Physics step (mid)
4. Damage / combat (mid-high)
5. Rendering / HUD (low, after simulation)

---

## Bullet Physics Integration

**`BulletPhysicsSystem`**

Owns the Bullet `btDynamicsWorld`. Steps the simulation with a fixed timestep each frame. Subscribes to `OriginRebasedEvent` to shift all rigid body origins when the coordinate system rebases.

**`PhysicsBodySystem`**

After each Bullet step, copies rigid body transforms back into `TransformComponent` so the rest of the ECS reads correct positions.

**Separate interior worlds:** Ship interiors run in their own `btDynamicsWorld` (managed by `ShipInteriorPhysicsSystem`), not in the parent world. This prevents interior objects from interacting with the exterior simulation and avoids precision issues at large local-space offsets.

---

## Gravity

**`GravitySystem`** — each frame, sums all `GravitySourceComponent` entities and applies gravitational acceleration to entities that have `GravityAffectedComponent`.

**`RadialGravitySystem`** — spherical (inverse-square) gravity for planets and stars.

**`TumbleSystem`** — rotational damping on tumbling debris.

**`DebrisLODSystem`** — switches debris to low-fidelity simulation at distance.

---

## Black Hole Physics

Located in `core/blackhole/`.

| System | What it does |
|---|---|
| `EventHorizonSystem` | Applies spaghettification forces to entities near the Schwarzschild radius |
| `TidalForceSystem` | Computes differential tidal stress across the entity's extent |
| `TimeDilationSystem` | Scales local time for entities inside the ergosphere using the relativistic factor stored in `TimeDilationComponent` |

`BlackHoleComponent` marks an entity as a black hole and stores its Schwarzschild radius and accretion disk properties.

---

## Solar Physics

Located in `core/solar/`.

| System | What it does |
|---|---|
| `SolarWindSystem` | Applies stellar wind pressure forces to exposed surfaces |
| `RadiationBeltSystem` | Tracks entities passing through Van Allen-style radiation zones |
| `RadiationPressureSystem` | Provides solar pressure force for photon sail propulsion |
| `PhotonSailSystem` | Integrates radiation pressure into thrust for sail-equipped ships |
| `CMESystem` | Fires coronal mass ejection events that temporarily spike radiation and solar wind |

---

## Tether & Cable Physics

Located in `core/tether/`.

| Class | Role |
|---|---|
| `TetherSystem` | Manages cable constraint creation and teardown |
| `VerletRopeSystem` | Simulates flexible cable segments with Verlet integration |
| `WinchSystem` | Controls winch motor to reel cable in/out, adjusting tension |
| `VerletRopeComponent` | Per-segment rope state |
| `WinchComponent` | Winch motor speed and limit settings |
| `TetherConstraintComponent` | Physics constraint anchoring both ends |

---

## Core Components

| Component | Purpose |
|---|---|
| `TransformComponent` | Position, rotation, scale in local-space |
| `PhysicsBodyComponent` | Bullet rigid body, collision shape, mass, friction coefficients |
| `PlayerTagComponent` | Marks the player entity for fast family lookup |
| `GravitySourceComponent` | Marks a gravity well (planet, star, black hole) |
| `GravityAffectedComponent` | Opts an entity into gravity simulation |
| `GravityZoneComponent` | Defines a bounded gravity region |
| `AtmosphereZoneComponent` | Atmosphere properties for a region (density, composition) |
| `DebrisComponent` | Debris-specific physics properties |
