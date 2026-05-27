---
name: libgdx-hydrodynamics
description: >
  Implements boat, ship, and submarine water physics in libGDX — buoyancy via
  multi-point hull displacement, Gerstner wave surfaces, hydrodynamic drag/lift,
  Kelvin wake generation, compartment flooding with free-surface stability effects,
  and submarine ballast/depth control. Use this skill whenever implementing water
  bodies on planetary surfaces, boat or ship movement, submarine mechanics, hull
  flooding, wave interactions, buoyancy calculations, or any vessel-on-water
  gameplay. Also applies to interior flooding scenarios in ships or stations and
  alien liquid environments (methane seas, lava lakes) by swapping fluid density
  and viscosity parameters. Trigger on: water physics, buoyancy, waves, boats,
  ships on water, submarines, ballast, flooding, capsizing, ocean surface,
  hydrofoil, hull drag, wake, watercraft, sailing, liquid bodies, splashdown.
---

# Hydrodynamics — Boats, Ships, and Submarines

## Core Architecture

Water physics splits into three layers that compose independently:

1. **Water surface** — Gerstner wave model providing height, normal, and flow velocity at any world point
2. **Vessel forces** — buoyancy, drag, lift, and wake computed per hull sample point against the surface
3. **Interior flooding** — compartment water levels, cross-flow, and free-surface stability effects

Each layer is an Ashley ECS system. Vessels interact with water through `BuoyancySamplePoint` arrays on their `HullComponent`, not through mesh intersection — this keeps the cost fixed per vessel regardless of mesh complexity.

### Coordinate Convention

Water surfaces exist in **local-space** (float) around the floating origin. Wave phase uses **galaxy-space** (double) so waves tile seamlessly across origin rebases. Convert at the boundary:

```java
// In WaveSystem.process()
double galaxyX = originManager.toGalaxyX(localPos.x);
double galaxyZ = originManager.toGalaxyZ(localPos.z);
float height = evaluateWaves(galaxyX, galaxyZ, time);
```

---

## Components

### WaterBodyComponent

Defines a body of water. Attached to an entity representing an ocean, lake, or flooded compartment.

```java
public class WaterBodyComponent implements Component {
    public float baseHeight;
    public float density = 1025f;               // kg/m³ (seawater)
    public float kinematicViscosity = 1.19e-6f;  // m²/s
    public float surfaceTension = 0.072f;        // N/m

    public final Array<WaveParams> waves = new Array<>(8);
    public final Vector3 currentVelocity = new Vector3(); // m/s in local space
    public BoundingBox bounds; // null = infinite ocean
}
```

### WaveParams

```java
public class WaveParams {
    public float amplitude;     // metres, peak-to-trough / 2
    public float wavelength;    // metres
    public float speed;         // m/s (deep water: sqrt(g·λ/2π))
    public float steepness;     // 0..1 (Gerstner Q, 0 = sine, 1 = breaking)
    public float directionDeg;  // wave propagation direction
}
```

### HullComponent

Attached to any entity that floats — boats, ships, submarines, debris.

```java
public class HullComponent implements Component {
    public final Array<BuoyancySamplePoint> samplePoints = new Array<>();
    public float dryMass;                        // kg — hull without ballast/cargo
    public float totalDisplacementVolume;         // m³ at full submersion
    public float dragCoefficientLinear = 0.05f;   // viscous (skin friction)
    public float dragCoefficientQuad = 0.8f;      // form drag Cd
    public float wettedArea;                      // m² below waterline
    public float beamWidth;                       // m — hull width at waterline
    public float hullLength;                      // m — waterline length

    public boolean isSubmersible = false;
    public float crushDepth = Float.MAX_VALUE;    // metres below surface
}
```

### BuoyancySamplePoint

Each point represents a hull surface patch. Place 8–32 points across the hull geometry, concentrated at bow, stern, and beam edges where the waterline varies most during pitch and roll.

```java
public class BuoyancySamplePoint {
    public final Vector3 localOffset = new Vector3(); // body-frame offset from entity centre
    public final Vector3 normal = new Vector3();       // outward hull normal in body frame
    public float area;       // m² of hull patch this point represents
    public float depth;      // current submersion depth (written by BuoyancySystem)
    public boolean submerged;
}
```

### BallastComponent (submarines)

```java
public class BallastComponent implements Component {
    public final Array<BallastTank> tanks = new Array<>();
    public float targetDepth = 0f;   // metres below surface (0 = surface)
    public float depthKp = 2000f;    // PID proportional gain
    public float depthKd = 800f;     // PID derivative gain
    public float depthKi = 50f;      // PID integral gain
    public float depthIntegral;      // accumulated integral error
    public float prevError;          // for derivative term
}
```

### BallastTank

```java
public class BallastTank {
    public float capacity;    // m³
    public float currentFill; // m³ of water in tank
    public float fillRate;    // m³/s (pump speed)
    public float drainRate;   // m³/s (blow speed)
    public final Vector3 localPosition = new Vector3(); // affects trim
}
```

### FloodingComponent

Tracks water ingress in a vessel's compartments.

```java
public class FloodingComponent implements Component {
    public final Array<Compartment> compartments = new Array<>();
    public float totalFloodedMass;
    public final Vector3 floodedCoM = new Vector3(); // centre-of-mass shift
}
```

### Compartment

```java
public class Compartment {
    public String id;
    public float volume;       // m³ total
    public float waterVolume;  // m³ currently flooded
    public float breachArea;   // m² of hull breach (0 = intact)
    public float breachDepth;  // depth of breach below waterline
    public final Array<String> connectedTo = new Array<>(); // adjacent IDs
    public final Vector3 centroid = new Vector3(); // hull body frame
}
```

### WakeComponent

Rendering reads this to draw Kelvin wake geometry behind moving vessels.

```java
public class WakeComponent implements Component {
    public float kelvinAngleRad = 0.3398f; // ~19.47°
    public float wakeIntensity;            // 0..1 based on Froude number
    public float froudeNumber;             // v / sqrt(g·L)
    public final Array<Vector3> wakeTrail = new Array<>(64);
}
```

---

## Systems

### WaveSystem (Priority 1)

Evaluates the Gerstner wave surface. Every other water system queries it, so it runs first.

```java
public class WaveSystem extends EntitySystem {
    private final Array<WaveParams> activeWaves = new Array<>();
    private float baseHeight;
    private float time;

    @Override
    public void update(float dt) {
        time += dt;
    }

    /** Surface height at galaxy-space coordinates. */
    public float getHeight(double gx, double gz) {
        float h = baseHeight;
        for (int i = 0; i < activeWaves.size; i++) {
            WaveParams w = activeWaves.get(i);
            float k = MathUtils.PI2 / w.wavelength;
            float omega = k * w.speed;
            float dirRad = w.directionDeg * MathUtils.degreesToRadians;
            float dx = MathUtils.cos(dirRad);
            float dz = MathUtils.sin(dirRad);
            float phase = (float)(k * (dx * gx + dz * gz)) - omega * time;
            h += w.amplitude * MathUtils.sin(phase);
        }
        return h;
    }

    /** Analytical surface normal from Gerstner partial derivatives. */
    public Vector3 getNormal(double gx, double gz, Vector3 out) {
        float dhdx = 0f, dhdz = 0f;
        for (int i = 0; i < activeWaves.size; i++) {
            WaveParams w = activeWaves.get(i);
            float k = MathUtils.PI2 / w.wavelength;
            float dirRad = w.directionDeg * MathUtils.degreesToRadians;
            float dx = MathUtils.cos(dirRad);
            float dz = MathUtils.sin(dirRad);
            float phase = (float)(k * (dx * gx + dz * gz)) - k * w.speed * time;
            float dPhase = w.amplitude * k * MathUtils.cos(phase);
            dhdx += dPhase * dx;
            dhdz += dPhase * dz;
        }
        return out.set(-dhdx, 1f, -dhdz).nor();
    }

    /** Gerstner orbital velocity at a point, combined with ambient current. */
    public Vector3 getFlowVelocity(double gx, double gz, float depth,
                                    Vector3 current, Vector3 out) {
        out.set(current);
        for (int i = 0; i < activeWaves.size; i++) {
            WaveParams w = activeWaves.get(i);
            float k = MathUtils.PI2 / w.wavelength;
            float omega = k * w.speed;
            float dirRad = w.directionDeg * MathUtils.degreesToRadians;
            float phase = (float)(k * (MathUtils.cos(dirRad) * gx
                         + MathUtils.sin(dirRad) * gz)) - omega * time;
            float depthAtten = (float) Math.exp(-k * Math.max(depth, 0f));
            out.x += w.amplitude * omega * MathUtils.cos(dirRad)
                     * MathUtils.cos(phase) * depthAtten;
            out.z += w.amplitude * omega * MathUtils.sin(dirRad)
                     * MathUtils.cos(phase) * depthAtten;
        }
        return out;
    }
}
```

Deep-water dispersion relation: `speed = sqrt(g * wavelength / 2π)`. Enforce this when authoring `WaveParams` unless deliberately simulating shallow water (where `speed = sqrt(g * waterDepth)`).

### BuoyancySystem (Priority 2)

For each entity with `HullComponent` + `PhysicsBodyComponent`, samples every hull point against the wave surface and applies pressure forces.

```java
@Override
protected void processEntity(Entity entity, float dt) {
    HullComponent hull = hullMapper.get(entity);
    btRigidBody rb = physicsMapper.get(entity).rigidBody;
    Matrix4 worldTx = rb.getWorldTransform();

    Vector3 worldPt  = Pools.obtain(Vector3.class);
    Vector3 force    = Pools.obtain(Vector3.class);
    Vector3 lever    = Pools.obtain(Vector3.class);
    Vector3 torque   = Pools.obtain(Vector3.class);
    torque.setZero();
    Vector3 totalForce = Pools.obtain(Vector3.class).setZero();

    for (int i = 0; i < hull.samplePoints.size; i++) {
        BuoyancySamplePoint sp = hull.samplePoints.get(i);
        worldPt.set(sp.localOffset).mul(worldTx);

        double gx = originManager.toGalaxyX(worldPt.x);
        double gz = originManager.toGalaxyZ(worldPt.z);
        float surfaceY = waveSystem.getHeight(gx, gz);

        sp.depth = surfaceY - worldPt.y;
        sp.submerged = sp.depth > 0f;
        if (!sp.submerged) continue;

        // Hydrostatic pressure force on this hull patch
        float pressure = waterDensity * 9.81f * sp.depth;
        force.set(sp.normal).rot(worldTx).scl(pressure * sp.area);
        totalForce.add(force);

        // Torque about centre of mass
        lever.set(worldPt).sub(rb.getCenterOfMassPosition());
        torque.add(lever.crs(force));
    }

    if (!totalForce.isZero()) {
        rb.applyCentralForce(totalForce);
        rb.applyTorque(torque);
    }

    Pools.free(worldPt); Pools.free(force);
    Pools.free(lever);   Pools.free(torque);
    Pools.free(totalForce);
}
```

The force direction follows each sample point's **world-space hull normal**, not just world-up. This naturally produces restoring torques — a hull tilted so one side sits deeper receives more upward force on that side, rotating it back. This emergent righting behaviour replaces explicit metacentric height calculations for most gameplay purposes.

### HydrodynamicDragSystem (Priority 3)

Three drag components for submerged hulls:

1. **Viscous (skin friction):** `F = ½ρ Cf A v²` — linear in coefficient, quadratic in speed
2. **Form (pressure) drag:** `F = ½ρ Cd A_frontal v²` — bluff-body resistance
3. **Wave-making drag:** peaks at Froude ≈ 0.4–0.5 — the hull-speed barrier

```java
@Override
protected void processEntity(Entity entity, float dt) {
    HullComponent hull = hullMapper.get(entity);
    btRigidBody rb = physicsMapper.get(entity).rigidBody;

    Vector3 vel = Pools.obtain(Vector3.class);
    rb.getLinearVelocity(vel);
    vel.sub(waterBody.currentVelocity); // velocity relative to water
    float speed = vel.len();
    if (speed < 0.001f) { Pools.free(vel); return; }

    Vector3 dragDir = Pools.obtain(Vector3.class).set(vel).nor().scl(-1f);
    float rho = waterBody.density;

    // Skin friction
    float viscous = 0.5f * rho * hull.dragCoefficientLinear
                    * hull.wettedArea * speed * speed;

    // Form drag
    float frontalArea = hull.beamWidth * getSubmergedDraft(hull);
    float form = 0.5f * rho * hull.dragCoefficientQuad
                 * frontalArea * speed * speed;

    // Wave-making drag (surface vessels only)
    float wave = 0f;
    if (!isFullySubmerged(hull)) {
        float froude = speed / (float) Math.sqrt(9.81f * hull.hullLength);
        wave = computeWaveDrag(froude, rho, hull);

        if (wakeMapper.has(entity)) {
            WakeComponent wake = wakeMapper.get(entity);
            wake.froudeNumber = froude;
            wake.wakeIntensity = MathUtils.clamp(froude / 0.5f, 0f, 1f);
        }
    }

    rb.applyCentralForce(dragDir.scl(viscous + form + wave));

    // Angular damping — water resists rotation
    Vector3 angVel = Pools.obtain(Vector3.class);
    rb.getAngularVelocity(angVel);
    rb.applyTorque(angVel.scl(-0.5f * rho * hull.wettedArea * 0.1f));

    Pools.free(vel); Pools.free(dragDir); Pools.free(angVel);
}
```

**Wave-making drag** peaks near Froude 0.4–0.5, where a displacement hull climbs its own bow wave (hull speed barrier). Planing hulls punch through at higher Froude numbers. Model the hump:

```java
private float computeWaveDrag(float froude, float rho, HullComponent hull) {
    float peak = 0.45f, width = 0.15f;
    float envelope = (float) Math.exp(
        -((froude - peak) * (froude - peak)) / (2f * width * width));
    float Cw = 0.005f * envelope;
    return 0.5f * rho * Cw * hull.wettedArea * froude * froude
           * 9.81f * hull.hullLength;
}
```

### BallastSystem (Priority 4)

Submarine depth control via PID controller driving ballast tank fill/drain.

```java
@Override
protected void processEntity(Entity entity, float dt) {
    BallastComponent ballast = ballastMapper.get(entity);
    HullComponent hull = hullMapper.get(entity);
    PhysicsBodyComponent body = physicsMapper.get(entity);

    float currentDepth = getCurrentDepth(entity);
    float error = ballast.targetDepth - currentDepth;

    ballast.depthIntegral += error * dt;
    ballast.depthIntegral = MathUtils.clamp(ballast.depthIntegral, -10f, 10f);
    float dError = (error - ballast.prevError) / dt;
    ballast.prevError = error;

    float command = ballast.depthKp * error
                  + ballast.depthKi * ballast.depthIntegral
                  + ballast.depthKd * dError;

    // Positive = go deeper = flood tanks; negative = blow tanks
    for (int i = 0; i < ballast.tanks.size; i++) {
        BallastTank tank = ballast.tanks.get(i);
        if (command > 0) {
            tank.currentFill = Math.min(tank.capacity,
                tank.currentFill + tank.fillRate * dt);
        } else {
            tank.currentFill = Math.max(0f,
                tank.currentFill - tank.drainRate * dt);
        }
    }

    // Update rigid body mass from ballast water
    float ballastMass = getTotalBallastMass(ballast, waterBody.density);
    body.rigidBody.setMassProps(hull.dryMass + ballastMass, body.inertia);

    if (currentDepth > hull.crushDepth) {
        EventBus.post(new HullBreachEvent(entity, currentDepth, hull.crushDepth));
    }
}
```

### FloodingSystem (Priority 5)

Water ingress through hull breaches with orifice-flow cross-compartment equalization.

```java
@Override
protected void processEntity(Entity entity, float dt) {
    FloodingComponent flooding = floodingMapper.get(entity);

    for (int i = 0; i < flooding.compartments.size; i++) {
        Compartment comp = flooding.compartments.get(i);

        // External ingress: Torricelli's theorem  Q = Cd·A·√(2gh)
        if (comp.breachArea > 0f && comp.breachDepth > 0f) {
            float flow = 0.6f * comp.breachArea
                       * (float) Math.sqrt(2f * 9.81f * comp.breachDepth);
            comp.waterVolume = Math.min(comp.volume,
                comp.waterVolume + flow * dt);
        }

        // Cross-flow to connected compartments
        for (int j = 0; j < comp.connectedTo.size; j++) {
            Compartment neighbor = findById(flooding, comp.connectedTo.get(j));
            if (neighbor == null) continue;
            float headDiff = waterHead(comp) - waterHead(neighbor);
            if (Math.abs(headDiff) < 0.01f) continue;

            float crossFlow = 0.4f * 0.5f // Cd · passage area m²
                * (float) Math.sqrt(2f * 9.81f * Math.abs(headDiff))
                * Math.signum(headDiff) * dt;
            comp.waterVolume -= crossFlow;
            neighbor.waterVolume += crossFlow;
            comp.waterVolume = MathUtils.clamp(comp.waterVolume, 0f, comp.volume);
            neighbor.waterVolume = MathUtils.clamp(
                neighbor.waterVolume, 0f, neighbor.volume);
        }
    }

    updateFloodedMassAndCoM(flooding, waterBody.density);
    if (flooding.totalFloodedMass > 0f) {
        adjustRigidBodyMass(entity, flooding);
        applyFloodCoMShift(entity, flooding);
    }

    // Free surface effect — partially-filled compartments reduce stability
    float gzLoss = computeFreeSurfaceEffect(flooding);
    if (gzLoss > 0.1f) {
        EventBus.post(new StabilityWarningEvent(entity, gzLoss));
    }
}
```

**Applying the CoM shift** is critical — computing it isn't enough. The flooded water's off-centre mass creates a torque that makes the vessel list:

```java
private void applyFloodCoMShift(Entity entity, FloodingComponent flooding) {
    btRigidBody rb = physicsMapper.get(entity).rigidBody;
    Matrix4 worldTx = rb.getWorldTransform();

    // Flooded water weight acts at the flood CoM, not the hull CoM
    Vector3 floodWorldPos = Pools.obtain(Vector3.class);
    floodWorldPos.set(flooding.floodedCoM).mul(worldTx);

    Vector3 rbCoM = Pools.obtain(Vector3.class);
    rb.getCenterOfMassPosition(rbCoM);

    // Lever arm from hull CoM to flood CoM
    Vector3 lever = Pools.obtain(Vector3.class).set(floodWorldPos).sub(rbCoM);

    // Gravity force on flooded mass
    Vector3 floodWeight = Pools.obtain(Vector3.class)
        .set(0f, -flooding.totalFloodedMass * 9.81f, 0f);

    // Torque = lever × weight (this is what makes the ship list)
    Vector3 torque = Pools.obtain(Vector3.class).set(lever).crs(floodWeight);
    rb.applyTorque(torque);

    // Also apply the weight as a central force (adds to total mass effect)
    rb.applyCentralForce(floodWeight);

    Pools.free(floodWorldPos); Pools.free(rbCoM);
    Pools.free(lever); Pools.free(floodWeight); Pools.free(torque);
}
```

Don't try to use `setCenterOfMassTransform()` directly — Bullet recomputes CoM from collision shapes, so manual overrides get clobbered. Instead, apply the flood weight at its actual position via torque, which achieves the same listing effect through forces.

**Free surface effect**: A half-filled compartment lets water slosh to the low side during roll, amplifying it. The virtual reduction in metacentric height is `GZ_loss = ρ · I_free / Δ` where `I_free` is the second moment of area of the free surface and `Δ` is the vessel's displacement. This is the primary real-world cause of capsizing in damaged ships — partially flooded compartments are more dangerous than fully flooded ones.

---

## Fluid Property Presets

| Fluid        | Density (kg/m³) | Kinematic Viscosity (m²/s) | Notes                        |
|-------------|-----------------|---------------------------|-------------------------------|
| Seawater    | 1025            | 1.19e-6                   | Earth default                 |
| Fresh water | 998             | 1.00e-6                   | Lakes, rivers                 |
| Methane     | 450             | 2.2e-7                    | Titan-style hydrocarbon seas  |
| Ammonia     | 680             | 3.5e-7                    | Hypothetical alien oceans     |
| Lava        | 2700            | 100–1e5                   | Extreme drag, high buoyancy   |
| Heavy brine | 1200            | 1.5e-6                    | Hypersaline worlds            |

Swap `WaterBodyComponent.density` and `kinematicViscosity` to simulate alien environments — the same buoyancy and drag math works everywhere.

## Vessel Archetype Quick Reference

| Type          | Sample Pts | Cd (form) | Beam (m) | Length (m) | Submersible |
|--------------|-----------|-----------|----------|------------|-------------|
| Rowboat       | 8         | 1.0       | 1.2      | 3          | No          |
| Speedboat     | 12        | 0.6       | 2.5      | 7          | No          |
| Fishing boat  | 16        | 0.9       | 4        | 12         | No          |
| Cargo ship    | 24        | 0.8       | 20       | 100        | No          |
| Submarine     | 20        | 0.3       | 8        | 60         | Yes         |
| Alien skiff   | 10        | 0.7       | 2        | 5          | No          |

## Events

| Event                  | Trigger                                     | Payload                       |
|-----------------------|---------------------------------------------|-------------------------------|
| VesselEnteredWater    | First sample point submerges                 | entity, waterBodyEntity       |
| VesselExitedWater     | Last sample point clears surface             | entity                        |
| HullBreachEvent       | Submarine exceeds crush depth                | entity, depth, crushDepth     |
| StabilityWarningEvent | Free surface GZ loss exceeds threshold       | entity, gzLoss                |
| FloodingStartedEvent  | Compartment begins taking water              | entity, compartmentId         |
| CapsizeEvent          | Roll exceeds 60° and not recovering          | entity                        |

---

## Integration Points

- **BulletPhysicsSystem** — all forces go through `btRigidBody.applyCentralForce()` / `applyTorque()`. Buoyancy, drag, and ballast don't bypass Bullet.
- **WaveSystem ↔ Rendering** — the renderer queries `WaveSystem.getHeight()` to displace the water mesh; physics and visuals share the same wave function so they never disagree.
- **Atmospheric flight transition** — when a vessel transitions from air to water (splashdown), switch from aerodynamic drag to hydrodynamic drag by detecting first sample point submersion. Publish `VesselEnteredWater` so other systems can react.
- **Ship interiors** — flooding compartments live in the ship's **interior** `btDynamicsWorld` (separate from the parent world, per CLAUDE.md rule 6). The flooding system updates mass/CoM on the parent hull's rigid body in the outer world.
- **Audio/VFX** — subscribe to water events for splash particles, flooding alarms, hull stress sounds, and wake foam rendering.
- **Existing zero-g fluid** — the `ship/fluid/` slosh/cryo systems handle propellant in microgravity; hydrodynamics handles vessels on liquid surfaces. They're complementary, not overlapping. A ship in atmosphere with fuel tanks uses both.

---

## Gotchas

| Mistake | Fix |
|---------|-----|
| Buoyancy force only in world-up direction | Use hull normal direction — this creates natural restoring torques for roll and pitch stability |
| Too few hull sample points | Minimum 8, concentrated at waterline edges. Too few causes oscillation and missed righting moments |
| Ignoring wave surface for buoyancy | Always query `WaveSystem.getHeight()`, not just `baseHeight`. Vessels must ride waves, not a flat plane |
| Drag computed against world velocity | Subtract `currentVelocity` first — drag is relative to the fluid, not the ground |
| Ballast mass not synced to rigid body | Call `setMassProps()` whenever ballast fill changes. Bullet caches mass and inertia |
| Free surface effect ignored | Partially flooded compartments are more dangerous than fully flooded ones. Always compute GZ loss |
| Wave phase in local coordinates | Use galaxy-space doubles for wave phase to avoid discontinuities on origin rebase |
| All buoyancy applied at CoM | Apply force at each sample point's world position via `applyForce(f, relPos)` or manual torque. `applyCentralForce` alone produces no roll or pitch |
| Submarine PID oscillation at surface | Add a dead-band (~0.5m) around target depth and increase derivative gain near surface to damp surface-piercing oscillation |
| Using render dt for physics | Hydrodynamic forces use the Bullet fixed timestep (1/60s), not the variable render frame delta |
| Flooding cross-flow violating conservation | Clamp both source and destination volumes after each transfer. Process each pair once per tick, not twice |
| Flood CoM computed but not applied | Computing `floodedCoM` isn't enough — you must apply the weight at that position via `applyTorque(lever.crs(weight))`. Don't use `setCenterOfMassTransform()` (Bullet overrides it) |
| Alien fluid with Earth constants | Always read density and viscosity from `WaterBodyComponent`, never hardcode 1025 or 9.81 for non-Earth gravity |
