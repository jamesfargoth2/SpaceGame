# Orbital Mechanics System Design

**Date:** 2026-05-28
**Status:** Approved
**TODO ref:** "Orbital mechanics — No Keplerian orbit integration. Planets are stationary. No HUD trajectory prediction arc."

## Summary

Bring planets and moons to life as moving ECS entities on Keplerian orbits, add sphere-of-influence tracking for ships, apply gravitational forces to physics bodies via a hybrid gravity model, and render a real-time trajectory prediction arc on the HUD.

## Scope

- Planets and moons move along Keplerian orbits as ECS entities
- Moons orbit their parent planets with their own orbital elements
- Ships detect which gravitational sphere of influence they occupy
- Hybrid gravity: full force from dominant body, attenuated secondaries
- Trajectory prediction derives the ship's current orbit from state vectors
- 3D orbit line rendered in world space with periapsis/apoapsis markers
- SOI boundary transitions with reference-frame velocity conversion
- Time scale control for orbit advancement speed

## Non-Goals

- N-body simulation (gravity is patched conics with perturbation, not full N-body)
- Atmospheric drag on orbits (covered by atmospheric-flight skill)
- Autopilot orbit-matching burns (future work)
- System map / 2D orbital diagram view (UI feature, separate from this system)

---

## 1. Unit System

### Constants

| Name | Value | Purpose |
|------|-------|---------|
| `AU_TO_GAME_UNITS` | `1000f` | 1 AU = 1000 game-space units |
| `EARTH_MASS_KG` | `5.972e24f` | Convert Planet.mass (Earth-masses) to kg |
| `SOLAR_MASS_KG` | `1.989e30f` | Convert StarSystem.mass (solar-masses) to kg |
| `G` | `6.674e-11f` | Gravitational constant (already in OrbitalMechanics and GravitySystem) |

### Coordinate spaces

- **Orbital math:** AU for distances, years for periods (existing `OrbitalSlot` convention)
- **ECS positions:** game units (1 AU = 1000 units), local space relative to floating origin
- **Physics forces:** game units and kg, consistent with `GravitySystem.G`

### Time scale

`KeplerianOrbitSystem.timeScale` (float, default `1.0f`) multiplies `dt` before advancing mean anomalies. At `timeScale = 1.0f`, 1 real second = 1 game second. At `timeScale = 10000f`, orbits advance ~3 hours per real second, making inner-system planet motion visible.

The time scale applies only to celestial body orbit advancement — not to ship physics, thrust, or gameplay timers.

---

## 2. Components

### OrbitalBodyComponent

**Package:** `com.galacticodyssey.core.components`

```java
public class OrbitalBodyComponent implements Component, Poolable {
    public OrbitalSlot orbitalSlot;       // Keplerian orbital data (null for the star itself)
    public Entity parentBody;             // star entity (planets), planet entity (moons), null (star)
    public float bodyRadius;              // physical radius in game units
    public float soiRadius;              // sphere of influence in game units (0 for star = infinite)
    public KeplerOrbit cachedOrbit;       // full KeplerOrbit, rebuilt when elements change
    public CelestialBodyType bodyType;    // STAR, PLANET, MOON
}
```

`CelestialBodyType` is a simple enum: `STAR`, `PLANET`, `MOON`.

### SOITrackerComponent

**Package:** `com.galacticodyssey.core.components`

```java
public class SOITrackerComponent implements Component, Poolable {
    public Entity dominantBody;           // innermost SOI body (deepest nesting)
    public Entity secondaryBody;          // next body up in SOI hierarchy
    public float distanceToDominant;      // cached distance to dominant body center
}
```

Attached to ships, player, debris — anything that should track its gravitational context.

### TrajectoryComponent

**Package:** `com.galacticodyssey.core.components`

```java
public class TrajectoryComponent implements Component, Poolable {
    public KeplerOrbit currentOrbit;      // derived from state vectors
    public Array<Vector3> predictedPath;  // sampled orbit points (local space)
    public boolean isStable;              // true if bound orbit (e < 1)
    public float periapsis;               // closest approach in game units
    public float apoapsis;                // furthest point in game units
    public boolean dirty;                 // true when orbit needs resampling
    public int sampleSegments = 96;       // number of sample points
}
```

---

## 3. Systems

### OrbitalPositionSystem (priority 3)

**Purpose:** Sync celestial body positions from orbital math to ECS `TransformComponent`.

**Runs after:** `KeplerianOrbitSystem` (priority 2) has advanced anomalies.

**Logic per entity with `OrbitalBodyComponent` + `TransformComponent`:**

1. If `orbitalSlot == null` (star): position stays at star's fixed local-space position (handled by CoordinateManager).
2. Otherwise:
   - `slot.getLocalPosition(out)` → position in AU relative to parent
   - Multiply by `AU_TO_GAME_UNITS`
   - Add `parentBody.TransformComponent.position` (star position for planets, planet position for moons)
   - Write to entity's `TransformComponent.position`

**Processing order:** Stars first, then planets, then moons (parent positions must be resolved before children). Use `CelestialBodyType` ordering.

**Origin rebase:** Subscribe to `OriginRebasedEvent`. On rebase, all celestial body positions shift by the rebase delta (same as other entities).

### SOITrackingSystem (priority 4)

**Purpose:** Determine which body's SOI each tracked entity occupies.

**Runs after:** `OrbitalPositionSystem` (positions are current).

**Logic per entity with `SOITrackerComponent` + `TransformComponent`:**

1. Start with the star as default dominant body.
2. Iterate all planet entities: compute distance from entity to planet.
   - If `distance < planet.soiRadius`, candidate = planet.
3. For the candidate planet, iterate its moon entities:
   - If `distance < moon.soiRadius`, candidate = moon.
4. If `dominantBody` changed from previous tick:
   - Perform reference-frame velocity conversion (see Section 5).
   - Publish `SOIChangedEvent`.
5. Update `dominantBody`, `secondaryBody`, `distanceToDominant`.

**Optimization:** Only check planets within a coarse distance threshold (e.g., `2 * maxPlanetSOI`). Skip distant planets entirely.

### GravityForceSystem (priority 5)

**Purpose:** Apply gravity acceleration to physics bodies.

**Logic per entity with `GravityAffectedComponent` + `PhysicsBodyComponent`:**

1. Read `lastAcceleration` from `GravityAffectedComponent` (computed by `GravitySystem` at priority 1).
2. Compute force: `F = mass * lastAcceleration`.
3. Apply via `btRigidBody.applyCentralForce(force)`.

This is a thin bridge system — `GravitySystem` handles all the computation; this system handles the Bullet integration.

### GravitySystem modification (hybrid model)

Modify `GravitySystem.computeNetAccelerationInternal()`:

When an entity has an `SOITrackerComponent`:
- **Dominant body:** Full gravitational acceleration (no attenuation).
- **Secondary body:** Full acceleration (allows multi-body effects near SOI boundaries).
- **All other sources:** Attenuated by a smooth falloff factor:
  `attenuation = max(0, 1 - (dist / dominantSOI)^2)`
  where `dist` is distance from the entity to the non-dominant source, and `dominantSOI` is the dominant body's SOI radius. This smoothly fades tertiary influences to zero as you move deeper into a dominant body's SOI.

When an entity does NOT have `SOITrackerComponent`: accumulate all sources equally (existing behavior).

### TrajectoryPredictionSystem (priority 6)

**Purpose:** Derive the ship's current orbit from physics state and sample the predicted path.

**Logic per entity with `TrajectoryComponent` + `PhysicsBodyComponent` + `SOITrackerComponent`:**

1. Get ship position/velocity from `btRigidBody`.
2. Compute position relative to dominant body: `relPos = shipPos - dominantBodyPos`.
3. Compute velocity relative to dominant body: `relVel = shipVel - dominantBodyVel` (dominant body velocity derived from orbital slot or zero for star).
4. Get dominant body mass from `GravitySourceComponent`.
5. Call `OrbitalMechanics.fromStateVectors(relPos, relVel, dominantMass)` → `KeplerOrbit`.
6. Compare new orbit elements to cached orbit. If delta exceeds threshold (e.g., semi-major axis changed > 1%, eccentricity changed > 0.01): mark `dirty = true`.
7. When dirty:
   - For elliptical orbits (`e < 1`): sample full orbit with `OrbitalMechanics.sampleOrbit(orbit, segments, points)`.
   - For hyperbolic/parabolic (`e >= 1`): sample partial arc from current true anomaly ± some spread.
   - Translate all sample points by dominant body position.
   - Store in `predictedPath`.
   - Update `isStable`, `periapsis`, `apoapsis`.
   - Clear dirty flag.
8. Check `isStableOrbit()` and cache stability status.

**Throttle:** Only recompute when dirty. Sample points are cached and only rebuilt when orbit elements change significantly or SOI changes.

---

## 4. HUD Rendering

### OrbitLineRenderer

**Package:** `com.galacticodyssey.ui`

Reads `TrajectoryComponent.predictedPath` and renders a 3D line in world space.

**Rendering approach:**
- Use `ShapeRenderer` in `Line` mode for simplicity.
- Color: cyan for stable orbits, red-orange for escape trajectories, yellow for impact-risk orbits.
- Draw periapsis marker (small circle/dot) at the closest-approach point.
- Draw apoapsis marker at the furthest point (elliptical orbits only).
- Fade line opacity for the portion behind the ship's current position (already-traversed arc).
- **Performance:** The line is rebuilt only when `TrajectoryComponent.dirty` was set. Otherwise, the same vertex data is redrawn each frame with position offset for floating origin.

### OrbitHUDPanel updates

The existing `OrbitHUDPanel` already works — it derives orbit from state vectors on each `OrbitTickEvent`. Changes:
- Update `primaryMass` and `primaryRadius` when `SOIChangedEvent` fires (currently hardcoded to solar values).
- Add a line showing the current SOI body name if available.

---

## 5. SOI Transition & Frame Conversion

When `SOITrackingSystem` detects a body change:

1. **Velocity conversion:** The ship's velocity must be converted from the old body's reference frame to the new body's frame.
   ```
   v_ship_new = v_ship_old - v_oldBody + v_newBody
   ```
   Where `v_oldBody` and `v_newBody` are the orbital velocities of those bodies in the parent frame. This ensures the ship's trajectory is continuous across the SOI boundary.

2. **Publish `SOIChangedEvent(entity, oldBody, newBody)`** via EventBus.

3. **Subscribers react:**
   - `TrajectoryPredictionSystem`: force recompute (dirty = true)
   - `OrbitHUDPanel`: update `primaryMass`, `primaryRadius`
   - Audio/VFX: optional SOI-transition feedback

### SOIChangedEvent

```java
public final class SOIChangedEvent {
    public final Entity entity;
    public final Entity oldDominantBody;
    public final Entity newDominantBody;
}
```

---

## 6. Moon Orbital Data

Extend `Moon.java` with orbital fields:

```java
// New fields in Moon.java
public float orbitalRadius;       // in AU (consistent with OrbitalSlot; typical range 0.001-0.01 AU)
public float orbitalEccentricity; // [0, 0.3)
public float orbitalInclination;  // radians, relative to planet's equatorial plane
public float orbitalPeriod;       // derived from radius and planet mass
```

`PlanetGenerator` (or equivalent) populates these from the planet's mass and moon count. Each moon gets an `OrbitalSlot` analog for its orbit around the planet.

`KeplerianOrbitSystem` is extended to also advance moon anomalies. The system iterates all `OrbitalSlot` entries — both planet-around-star and moon-around-planet. Moon slots reference the planet's GM rather than the star's.

---

## 7. Entity Lifecycle

### Star system load (entering a system)

1. Create star entity: `TransformComponent` + `GravitySourceComponent(mass=star.massKg)` + `OrbitalBodyComponent(type=STAR, soiRadius=0)`
2. For each `OrbitalSlot` in `StarSystem.orbits`:
   - Compute SOI: `OrbitalMechanics.sphereOfInfluence(slot.orbitalRadius * AU_TO_GAME_UNITS, planet.massKg, star.massKg)`
   - Create planet entity: `TransformComponent` + `GravitySourceComponent(mass=planet.massKg)` + `OrbitalBodyComponent(type=PLANET, parent=starEntity, orbitalSlot=slot, soiRadius=computed)`
   - For each moon of the planet:
     - Create moon entity similarly, with `type=MOON`, `parent=planetEntity`
3. Set `KeplerianOrbitSystem.setActiveSystem(system)` (already exists).

### Star system unload (leaving a system)

Remove all celestial body entities from the engine. Reset `KeplerianOrbitSystem.setActiveSystem(null)`.

### Ship entity setup

Add `SOITrackerComponent` + `GravityAffectedComponent` + `TrajectoryComponent` to the player ship entity. `GravityAffectedComponent.mass` should match the ship's physics mass.

---

## 8. System Registration Order (GameWorld.java)

```
Priority 1:  GravitySystem              (existing, modified for hybrid)
Priority 2:  KeplerianOrbitSystem        (existing, modified for time scale + moons)
Priority 3:  OrbitalPositionSystem       (NEW)
Priority 4:  SOITrackingSystem           (NEW)
Priority 5:  GravityForceSystem          (NEW)
Priority 6:  TrajectoryPredictionSystem  (NEW)
             ... existing physics, flight, rendering systems ...
```

---

## 9. File Manifest

### New files

| File | Package | Purpose |
|------|---------|---------|
| `OrbitalBodyComponent.java` | `core.components` | Links entity to orbital data, parent body, SOI radius |
| `SOITrackerComponent.java` | `core.components` | Tracks dominant/secondary gravity body for an entity |
| `TrajectoryComponent.java` | `core.components` | Cached predicted orbit path for HUD rendering |
| `CelestialBodyType.java` | `core.components` | Enum: STAR, PLANET, MOON |
| `SOIChangedEvent.java` | `core` | Event published on SOI boundary crossing |
| `OrbitalPositionSystem.java` | `core.systems` | Syncs orbital positions to TransformComponent |
| `SOITrackingSystem.java` | `core.systems` | Determines SOI for tracked entities |
| `GravityForceSystem.java` | `core.systems` | Applies gravity acceleration to btRigidBody |
| `TrajectoryPredictionSystem.java` | `core.systems` | Derives orbit from state vectors, samples path |
| `OrbitLineRenderer.java` | `ui` | Renders 3D trajectory arc in world space |

### Modified files

| File | Change |
|------|--------|
| `KeplerianOrbitSystem.java` | Add `timeScale` field, advance moon anomalies |
| `Moon.java` | Add orbital element fields |
| `OrbitalSlot.java` | Add `AU_TO_GAME_UNITS` constant |
| `GravitySystem.java` | Hybrid gravity model with SOI-aware attenuation |
| `GameWorld.java` | Register new systems, celestial body entity creation |
| `OrbitHUDPanel.java` | React to SOIChangedEvent, update primary body context |
| `OrbitalLayoutGenerator.java` | Generate moon orbital elements |
| `PlanetGenerator.java` | Populate moon orbital data |

---

## 10. Testing Strategy

- **Unit tests:** `OrbitalMechanics` math (already exists) — add tests for SOI computation, frame conversion
- **Integration tests:** Create a minimal star system with one planet and one moon, advance 1000 ticks, verify positions match expected Keplerian paths
- **Trajectory accuracy:** Derive orbit from known state vectors, verify periapsis/apoapsis match within 0.1%
- **SOI transition:** Place ship at SOI boundary, verify velocity conversion preserves trajectory continuity
- **Performance:** Benchmark `GravitySystem` + `SOITrackingSystem` with 10 planets and 30 moons — must stay under 0.5ms per tick
