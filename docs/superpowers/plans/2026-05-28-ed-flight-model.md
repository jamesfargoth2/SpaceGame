# Elite-Dangerous-Style Ship Flight Model — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current hold-to-thrust ship control with an Elite-Dangerous-style model: persistent throttle set-point + speed cap, Flight Assist (on/off), a blue-zone turn-rate band, and a dedicated-gauge boost.

**Architecture:** Keep the existing gdx-bullet rigid-body pipeline in `ShipFlightSystem` (forces/torque via `applyCentralForce`/`applyTorque`). Flight Assist is a proportional controller that drives the body's *current* velocity toward a *target* velocity (throttle→nose-forward speed + lateral bleed) and angular velocity toward an input-commanded rate. Tuning is data-driven through `ShipClassData`/`ShipClassRegistry`/`ShipFactory`. Pure math is factored into a testable helper class.

**Tech Stack:** Java, libGDX, gdx-bullet (Bullet physics), Ashley ECS, JUnit 5. Build/test with Gradle (`./gradlew`). Project root for all paths below is the worktree root.

**Spec:** [docs/superpowers/specs/2026-05-28-ed-flight-model-design.md](../specs/2026-05-28-ed-flight-model-design.md)

## Conventions for this plan

- Run a single test class headless with:
  `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.FlightControlMathTest"`
  (substitute the class). On Windows PowerShell use `.\gradlew` — the Bash tool also works.
- Bullet-backed tests must call `Bullet.init()` once (`@BeforeAll`) and build a `btDiscreteDynamicsWorld`; copy the harness from `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightSystemTest.java`.
- Commit after each task with the message shown in its final step.

## Deviations from the spec (locked decisions)

- **`maxTurnRate` and `maxSpeed` are read from `ShipDataComponent`, not duplicated onto `ShipFlightComponent`.** `OutfitterSystem` mutates `ShipDataComponent.maxSpeed`/`maxTurnRate` at runtime for module bonuses; reading them live keeps outfitting correct. `ShipFlightSystem` reads `ShipDataComponent` when present and falls back to constants `DEFAULT_MAX_SPEED = 100f`, `DEFAULT_MAX_TURN_RATE_DEG = 45f` when absent (so ad-hoc test ships still run). `ShipDataComponent.maxTurnRate` is in **degrees/second**; convert to rad/s in the system.
- **FA controllers are mass-relative.** Forward/lateral corrective forces are `clamp(gain * mass * velocityError, ±thrust)`, so feel is consistent across ship masses and the thrust cap produces the speed cap naturally.
- **Rotation uses a rate-target P-controller** (torque proportional to `desiredRate − currentRate`, capped at the ship's torque). This applies to player and AI alike (AI already outputs normalized [-1,1] stick values).

## File Structure

**Create:**
- `core/src/main/java/com/galacticodyssey/ship/systems/FlightControlMath.java` — pure, stateless helpers (blue-zone factor, throttle set-point stepping). No GL/ECS/Bullet deps → trivially unit-testable.
- `core/src/test/java/com/galacticodyssey/ship/systems/FlightControlMathTest.java`
- `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightAssistTest.java` — Bullet-backed tests for the linear/rotation/boost behavior.

**Modify:**
- `core/src/main/java/com/galacticodyssey/ship/components/ShipFlightComponent.java` — new tuning + runtime fields.
- `core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipFlightSnapshot.java` — persist new fields.
- `core/src/main/java/com/galacticodyssey/ship/components/ShipFlightInputComponent.java` — `flightAssistTogglePressed`, `boostPressed`.
- `core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java` — the FA/rotation/boost rewrite.
- `core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java` — persistent throttle, Z/Tab/X keys.
- `core/src/main/java/com/galacticodyssey/ship/data/ShipClassData.java` — new tuning fields.
- `core/src/main/java/com/galacticodyssey/ship/data/ShipClassRegistry.java` — parse new fields with defaults.
- `core/src/main/java/com/galacticodyssey/ship/ShipFactory.java` — per-size-class default arrays + apply (both build paths).
- `core/src/main/java/com/galacticodyssey/ui/CockpitHUDSystem.java` — FA + BOOST readouts.

---

### Task 1: Extend `ShipFlightComponent` + snapshot with new fields

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/components/ShipFlightComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipFlightSnapshot.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/components/ShipFlightComponentSnapshotTest.java` (create)

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/com/galacticodyssey/ship/components/ShipFlightComponentSnapshotTest.java`:

```java
package com.galacticodyssey.ship.components;

import com.galacticodyssey.persistence.snapshots.ShipFlightSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipFlightComponentSnapshotTest {

    @Test
    void roundTripPreservesNewFields() {
        ShipFlightComponent c = new ShipFlightComponent();
        c.reverseFraction = 0.4f;
        c.faLinearGain = 1.5f;
        c.faLateralBleed = 1.2f;
        c.blueZoneLow = 0.4f;
        c.blueZoneHigh = 0.8f;
        c.offBandTurnScale = 0.5f;
        c.rotStiffness = 4f;
        c.boostSpeedMultiplier = 1.6f;
        c.boostForce = 40000f;
        c.boostDuration = 5f;
        c.boostEnergyCost = 50f;
        c.boostMaxEnergy = 100f;
        c.boostRechargeRate = 12f;
        c.boostCooldown = 3f;
        c.flightAssistEnabled = false;
        c.boostEnergy = 73f;
        c.boostTimer = 1.5f;
        c.boostCooldownTimer = 2.5f;

        ShipFlightSnapshot s = c.takeSnapshot();
        ShipFlightComponent restored = new ShipFlightComponent();
        restored.restoreFromSnapshot(s);

        assertEquals(0.4f, restored.reverseFraction);
        assertEquals(1.5f, restored.faLinearGain);
        assertEquals(0.8f, restored.blueZoneHigh);
        assertEquals(4f, restored.rotStiffness);
        assertEquals(1.6f, restored.boostSpeedMultiplier);
        assertEquals(40000f, restored.boostForce);
        assertFalse(restored.flightAssistEnabled);
        assertEquals(73f, restored.boostEnergy);
        assertEquals(2.5f, restored.boostCooldownTimer);
    }

    @Test
    void defaultsAreSane() {
        ShipFlightComponent c = new ShipFlightComponent();
        assertTrue(c.flightAssistEnabled, "FA defaults on");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.components.ShipFlightComponentSnapshotTest"`
Expected: FAIL — compile error, fields like `reverseFraction` do not exist.

- [ ] **Step 3: Add fields to `ShipFlightComponent`**

In `ShipFlightComponent.java`, after the existing `gravitationalTimeDilation` field (around line 17), add:

```java
    // --- ED flight tuning (data-driven) ---
    public float reverseFraction = 0.4f;
    public float faLinearGain = 1.5f;      // 1/s, forward-speed P-gain (mass-relative)
    public float faLateralBleed = 1.2f;    // 1/s, drift-cancel gain (mass-relative)
    public float blueZoneLow = 0.4f;
    public float blueZoneHigh = 0.8f;
    public float offBandTurnScale = 0.5f;
    public float rotStiffness = 4f;        // rate-error → torque-fraction stiffness
    public float boostSpeedMultiplier = 1.6f;
    public float boostForce = 40000f;      // extra forward N during boost
    public float boostDuration = 5f;       // s
    public float boostEnergyCost = 50f;
    public float boostMaxEnergy = 100f;
    public float boostRechargeRate = 12f;  // energy/s when idle
    public float boostCooldown = 3f;       // s

    // --- ED flight runtime state (persisted) ---
    public boolean flightAssistEnabled = true;
    public float boostEnergy = 100f;
    public float boostTimer = 0f;
    public float boostCooldownTimer = 0f;
```

Then extend `takeSnapshot()` to copy each new field onto the snapshot, and `restoreFromSnapshot()` to copy each back. Add the assignments alongside the existing ones, e.g. in `takeSnapshot()`:

```java
        s.reverseFraction = reverseFraction;
        s.faLinearGain = faLinearGain;
        s.faLateralBleed = faLateralBleed;
        s.blueZoneLow = blueZoneLow;
        s.blueZoneHigh = blueZoneHigh;
        s.offBandTurnScale = offBandTurnScale;
        s.rotStiffness = rotStiffness;
        s.boostSpeedMultiplier = boostSpeedMultiplier;
        s.boostForce = boostForce;
        s.boostDuration = boostDuration;
        s.boostEnergyCost = boostEnergyCost;
        s.boostMaxEnergy = boostMaxEnergy;
        s.boostRechargeRate = boostRechargeRate;
        s.boostCooldown = boostCooldown;
        s.flightAssistEnabled = flightAssistEnabled;
        s.boostEnergy = boostEnergy;
        s.boostTimer = boostTimer;
        s.boostCooldownTimer = boostCooldownTimer;
```

and the mirror copies in `restoreFromSnapshot(ShipFlightSnapshot s)`:

```java
        reverseFraction = s.reverseFraction;
        faLinearGain = s.faLinearGain;
        faLateralBleed = s.faLateralBleed;
        blueZoneLow = s.blueZoneLow;
        blueZoneHigh = s.blueZoneHigh;
        offBandTurnScale = s.offBandTurnScale;
        rotStiffness = s.rotStiffness;
        boostSpeedMultiplier = s.boostSpeedMultiplier;
        boostForce = s.boostForce;
        boostDuration = s.boostDuration;
        boostEnergyCost = s.boostEnergyCost;
        boostMaxEnergy = s.boostMaxEnergy;
        boostRechargeRate = s.boostRechargeRate;
        boostCooldown = s.boostCooldown;
        flightAssistEnabled = s.flightAssistEnabled;
        boostEnergy = s.boostEnergy;
        boostTimer = s.boostTimer;
        boostCooldownTimer = s.boostCooldownTimer;
```

- [ ] **Step 4: Add matching fields to `ShipFlightSnapshot`**

In `ShipFlightSnapshot.java`, add before the constructor:

```java
    public float reverseFraction;
    public float faLinearGain;
    public float faLateralBleed;
    public float blueZoneLow;
    public float blueZoneHigh;
    public float offBandTurnScale;
    public float rotStiffness;
    public float boostSpeedMultiplier;
    public float boostForce;
    public float boostDuration;
    public float boostEnergyCost;
    public float boostMaxEnergy;
    public float boostRechargeRate;
    public float boostCooldown;
    public boolean flightAssistEnabled = true;
    public float boostEnergy;
    public float boostTimer;
    public float boostCooldownTimer;
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.components.ShipFlightComponentSnapshotTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/components/ShipFlightComponent.java \
        core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipFlightSnapshot.java \
        core/src/test/java/com/galacticodyssey/ship/components/ShipFlightComponentSnapshotTest.java
git commit -m "feat(flight): add ED flight tuning + runtime fields to ShipFlightComponent"
```

---

### Task 2: Add edge-trigger input flags

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/components/ShipFlightInputComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightInputComponentTest.java` (exists — add a case)

- [ ] **Step 1: Write the failing test**

Append to the existing `ShipFlightInputComponentTest` class a new test method:

```java
    @Test
    void newControlFlagsDefaultFalse() {
        com.galacticodyssey.ship.components.ShipFlightInputComponent in =
            new com.galacticodyssey.ship.components.ShipFlightInputComponent();
        assertFalse(in.flightAssistTogglePressed);
        assertFalse(in.boostPressed);
    }
```

(If `assertFalse` is not statically imported in that file, use `org.junit.jupiter.api.Assertions.assertFalse(...)`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightInputComponentTest"`
Expected: FAIL — `flightAssistTogglePressed` does not exist.

- [ ] **Step 3: Add the fields**

In `ShipFlightInputComponent.java`, after `public float scrollDelta;`:

```java
    public boolean flightAssistTogglePressed;
    public boolean boostPressed;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightInputComponentTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/components/ShipFlightInputComponent.java \
        core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightInputComponentTest.java
git commit -m "feat(flight): add flightAssistToggle/boost edge flags to flight input"
```

---

### Task 3: Pure flight-control math helpers (blue zone + throttle stepping)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/systems/FlightControlMath.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/systems/FlightControlMathTest.java`

- [ ] **Step 1: Write the failing test**

Create `FlightControlMathTest.java`:

```java
package com.galacticodyssey.ship.systems;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlightControlMathTest {

    @Test
    void blueZoneFactorIsOneInsideBand() {
        assertEquals(1f, FlightControlMath.blueZoneFactor(0.6f, 0.4f, 0.8f, 0.5f), 1e-4);
        assertEquals(1f, FlightControlMath.blueZoneFactor(0.4f, 0.4f, 0.8f, 0.5f), 1e-4);
        assertEquals(1f, FlightControlMath.blueZoneFactor(0.8f, 0.4f, 0.8f, 0.5f), 1e-4);
    }

    @Test
    void blueZoneFactorFallsToOffScaleAtExtremes() {
        assertEquals(0.5f, FlightControlMath.blueZoneFactor(1.0f, 0.4f, 0.8f, 0.5f), 1e-4);
        assertEquals(0.5f, FlightControlMath.blueZoneFactor(0.0f, 0.4f, 0.8f, 0.5f), 1e-4);
    }

    @Test
    void blueZoneFactorInterpolatesAboveBand() {
        // halfway between high(0.8) and max(1.0) → halfway between 1.0 and offScale(0.5)
        float f = FlightControlMath.blueZoneFactor(0.9f, 0.4f, 0.8f, 0.5f);
        assertEquals(0.75f, f, 1e-4);
    }

    @Test
    void blueZoneFactorUsesThrottleMagnitude() {
        // reverse throttle still benefits from band by magnitude
        assertEquals(1f, FlightControlMath.blueZoneFactor(-0.6f, 0.4f, 0.8f, 0.5f), 1e-4);
    }

    @Test
    void throttleStepRampsUpAndClampsAtOne() {
        float t = FlightControlMath.stepThrottle(0.95f, true, false, false, 2f, 0.4f, 0.1f);
        assertEquals(1f, t, 1e-4); // 0.95 + 2*0.1 = 1.15 clamped to 1
    }

    @Test
    void throttleStepRampsDownToReverseLimit() {
        float t = FlightControlMath.stepThrottle(-0.35f, false, true, false, 2f, 0.4f, 0.1f);
        assertEquals(-0.4f, t, 1e-4); // -0.35 - 0.2 = -0.55 clamped to -reverseFraction
    }

    @Test
    void throttleZeroKeyWins() {
        float t = FlightControlMath.stepThrottle(0.8f, true, false, true, 2f, 0.4f, 0.1f);
        assertEquals(0f, t, 1e-4);
    }

    @Test
    void throttleHoldsWhenNoInput() {
        float t = FlightControlMath.stepThrottle(0.55f, false, false, false, 2f, 0.4f, 0.1f);
        assertEquals(0.55f, t, 1e-4);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.FlightControlMathTest"`
Expected: FAIL — `FlightControlMath` does not exist.

- [ ] **Step 3: Write the implementation**

Create `FlightControlMath.java`:

```java
package com.galacticodyssey.ship.systems;

import com.badlogic.gdx.math.MathUtils;

/** Pure, stateless math for ED-style flight control. No ECS/GL/Bullet dependencies. */
public final class FlightControlMath {

    private FlightControlMath() {}

    /**
     * Turn-rate multiplier for the "blue zone". Returns 1.0 when |throttle| is within
     * [low, high]; linearly falls to offScale at throttle magnitude 0 (below the band)
     * and 1.0 (above the band).
     */
    public static float blueZoneFactor(float throttle, float low, float high, float offScale) {
        float t = Math.abs(throttle);
        if (t >= low && t <= high) return 1f;
        if (t < low) {
            float frac = (low <= 0f) ? 1f : t / low;           // 0 at t=0 → offScale, 1 at t=low → 1.0
            return MathUtils.lerp(offScale, 1f, MathUtils.clamp(frac, 0f, 1f));
        }
        // t > high
        float denom = 1f - high;
        float frac = (denom <= 0f) ? 0f : (t - high) / denom;  // 0 at t=high → 1.0, 1 at t=1 → offScale
        return MathUtils.lerp(1f, offScale, MathUtils.clamp(frac, 0f, 1f));
    }

    /**
     * Steps a persistent throttle set-point. {@code up}/{@code down} ramp it by
     * {@code rampRate} per second; {@code zero} snaps to 0. Result is clamped to
     * [-reverseFraction, +1].
     */
    public static float stepThrottle(float current, boolean up, boolean down, boolean zero,
                                     float rampRate, float reverseFraction, float dt) {
        if (zero) return 0f;
        float t = current;
        if (up)   t += rampRate * dt;
        if (down) t -= rampRate * dt;
        return MathUtils.clamp(t, -reverseFraction, 1f);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.FlightControlMathTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/systems/FlightControlMath.java \
        core/src/test/java/com/galacticodyssey/ship/systems/FlightControlMathTest.java
git commit -m "feat(flight): add pure blue-zone + throttle-step helpers"
```

---

### Task 4: Linear Flight Assist (throttle→target speed, cap, lateral bleed, FA-off momentum)

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightAssistTest.java` (create)

This task rewrites the *linear* half of `applyFlight`. Rotation is replaced in Task 5; until then keep the existing torque block intact.

- [ ] **Step 1: Write the failing test**

Create `ShipFlightAssistTest.java`. It uses the same Bullet harness as `ShipFlightSystemTest`; a private helper builds a ship entity with a `ShipDataComponent` so the system can read `maxSpeed`.

```java
package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipFlightAssistTest {

    @BeforeAll static void initBullet() { Bullet.init(); }

    private btCollisionConfiguration cc;
    private btCollisionDispatcher disp;
    private btBroadphaseInterface bp;
    private btConstraintSolver solver;
    private btDiscreteDynamicsWorld world;
    private Entity ship;

    @AfterEach
    void tearDown() {
        if (ship != null) {
            PhysicsBodyComponent p = ship.getComponent(PhysicsBodyComponent.class);
            if (p != null && p.body != null) {
                world.removeRigidBody(p.body);
                p.body.dispose();
                p.shape.dispose();
            }
        }
        if (world != null) world.dispose();
        if (solver != null) solver.dispose();
        if (bp != null) bp.dispose();
        if (disp != null) disp.dispose();
        if (cc != null) cc.dispose();
    }

    /** Builds a flyable ship entity registered with engine+world, returns the engine+system to step. */
    private ShipFlightSystem buildShip(Engine engine, float maxSpeed, boolean faOn,
                                       Vector3 initialVelocity) {
        cc = new btDefaultCollisionConfiguration();
        disp = new btCollisionDispatcher(cc);
        bp = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(disp, bp, solver, cc);
        world.setGravity(new Vector3(0, 0, 0));

        ShipFlightSystem system = new ShipFlightSystem();
        engine.addSystem(system);

        ship = new Entity();
        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btBoxShape(new Vector3(1, 1, 1));
        float mass = 10000f;
        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().idt());
        physics.mass = mass;
        info.dispose();
        if (initialVelocity != null) physics.body.setLinearVelocity(initialVelocity);
        ship.add(physics);

        ShipFlightComponent flight = new ShipFlightComponent();
        flight.linearThrust = 50000f;
        flight.strafeThrustFraction = 0.6f;
        flight.verticalThrustFraction = 0.6f;
        flight.pitchYawTorque = 20000f;
        flight.rollTorque = 15000f;
        flight.flightAssistEnabled = faOn;
        ship.add(flight);

        ShipDataComponent data = new ShipDataComponent();
        data.maxSpeed = maxSpeed;
        data.maxTurnRate = 90f;
        ship.add(data);

        ship.add(new ShipFlightInputComponent());

        engine.addEntity(ship);
        world.addRigidBody(physics.body);
        return system;
    }

    private void step(ShipFlightSystem system, float seconds) {
        float dt = 1f / 60f;
        int steps = Math.round(seconds / dt);
        for (int i = 0; i < steps; i++) {
            system.update(dt);
            world.stepSimulation(dt, 1, dt);
        }
    }

    @Test
    void fullThrottleConvergesToMaxSpeedAndHolds() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, true, null);
        ShipFlightInputComponent in = ship.getComponent(ShipFlightInputComponent.class);
        in.throttle = 1f;

        step(system, 10f);

        float speed = ship.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().len();
        assertEquals(100f, speed, 5f, "should hold near maxSpeed, got " + speed);
    }

    @Test
    void halfThrottleConvergesToHalfMaxSpeed() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, true, null);
        ship.getComponent(ShipFlightInputComponent.class).throttle = 0.5f;

        step(system, 10f);

        float speed = ship.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().len();
        assertEquals(50f, speed, 5f, "got " + speed);
    }

    @Test
    void faBleedsLateralVelocity() {
        Engine engine = new Engine();
        // Nose points -Z; give it sideways (+X) drift, zero throttle.
        ShipFlightSystem system = buildShip(engine, 100f, true, new Vector3(40f, 0f, 0f));
        ship.getComponent(ShipFlightInputComponent.class).throttle = 0f;

        step(system, 8f);

        float lateral = Math.abs(ship.getComponent(PhysicsBodyComponent.class)
            .body.getLinearVelocity().x);
        assertTrue(lateral < 5f, "FA should bleed lateral drift, |vx|=" + lateral);
    }

    @Test
    void flightAssistOffPreservesLateralMomentum() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, false, new Vector3(40f, 0f, 0f));
        ship.getComponent(ShipFlightInputComponent.class).throttle = 0f;

        step(system, 8f);

        float lateral = ship.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().x;
        assertEquals(40f, lateral, 2f, "FA-off must preserve drift, vx=" + lateral);
    }

    @Test
    void reverseThrottleProducesBackwardSpeed() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, true, null);
        ship.getComponent(ShipFlightInputComponent.class).throttle = -0.4f;

        step(system, 10f);

        // Nose is -Z; reverse means +Z world velocity.
        float vz = ship.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().z;
        assertTrue(vz > 20f, "reverse should drive +Z, vz=" + vz);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightAssistTest"`
Expected: FAIL — current system has no speed cap (full throttle keeps accelerating) and no lateral bleed.

- [ ] **Step 3: Rewrite the linear portion of `ShipFlightSystem`**

In `ShipFlightSystem.java`:

(a) Add a `ShipDataComponent` mapper and default constants near the other mappers:

```java
    private final ComponentMapper<com.galacticodyssey.ship.components.ShipDataComponent> shipDataMapper =
        ComponentMapper.getFor(com.galacticodyssey.ship.components.ShipDataComponent.class);

    private static final float DEFAULT_MAX_SPEED = 100f;
    private static final float DEFAULT_MAX_TURN_RATE_DEG = 45f;
```

(b) Add scratch vectors near the existing ones:

```java
    private final Vector3 lateralVel = new Vector3();
    private final Vector3 forwardVelComp = new Vector3();
```

(c) Replace the linear-force block. The current block computes `force` from `effectiveThrottle`/`strafe`/`verticalThrust` then applies it (lines ~131–156, ending with the relativistic branch + `applyCentralForce`). Replace from `force.setZero();` up to and including that relativistic `if/else` apply block with:

```java
        // --- Effective max speed (live from ShipData; boost handled in Task 7) ---
        com.galacticodyssey.ship.components.ShipDataComponent shipData = shipDataMapper.get(ship);
        float maxSpeed = (shipData != null && shipData.maxSpeed > 0f)
            ? shipData.maxSpeed : DEFAULT_MAX_SPEED;
        if (flight.boostTimer > 0f) maxSpeed *= flight.boostSpeedMultiplier;

        currentVelocity.set(physics.body.getLinearVelocity());
        force.setZero();

        if (flight.flightAssistEnabled) {
            // Forward axis tracking toward target speed (P-controller, mass-relative, thrust-capped).
            float targetSpeed = effectiveThrottle * maxSpeed;
            float forwardSpeed = currentVelocity.dot(localForward);
            float fwdError = targetSpeed - forwardSpeed;
            float fwdForce = MathUtils.clamp(flight.faLinearGain * physics.mass * fwdError,
                -flight.linearThrust, flight.linearThrust);
            force.mulAdd(localForward, fwdForce);

            // Lateral velocity = velocity minus forward component.
            forwardVelComp.set(localForward).scl(forwardSpeed);
            lateralVel.set(currentVelocity).sub(forwardVelComp);

            // Strafe / vertical: intentional input adds thrust; otherwise bleed drift.
            float strafeThrust = flight.linearThrust * flight.strafeThrustFraction;
            float vertThrust   = flight.linearThrust * flight.verticalThrustFraction;
            float rightVel = lateralVel.dot(localRight);
            float upVel    = lateralVel.dot(localUp);

            float rightForce = (Math.abs(input.strafe) > 0.05f)
                ? input.strafe * strafeThrust
                : MathUtils.clamp(-flight.faLateralBleed * physics.mass * rightVel,
                    -strafeThrust, strafeThrust);
            float upForce = (Math.abs(input.verticalThrust) > 0.05f)
                ? input.verticalThrust * vertThrust
                : MathUtils.clamp(-flight.faLateralBleed * physics.mass * upVel,
                    -vertThrust, vertThrust);

            force.mulAdd(localRight, rightForce);
            force.mulAdd(localUp, upForce);
        } else {
            // Newtonian: direct thrust, no cap, no bleed.
            force.mulAdd(localForward, effectiveThrottle * flight.linearThrust);
            force.mulAdd(localRight, input.strafe * flight.linearThrust * flight.strafeThrustFraction);
            force.mulAdd(localUp, input.verticalThrust * flight.linearThrust * flight.verticalThrustFraction);
        }

        // Relativistic correction near light speed (unchanged behavior).
        final float speed = currentVelocity.len();
        if (speed > RelativisticConstants.THRESHOLD) {
            final float restMass = physics.mass;
            final Vector3 velDir = currentVelocity.cpy().nor();
            final float longComponent = force.dot(velDir);
            final Vector3 longForce = new Vector3(velDir).scl(longComponent);
            final Vector3 transForce = new Vector3(force).sub(longForce);
            final float longAccel = RelativisticMath.longitudinalAcceleration(longComponent, restMass, speed);
            final float transAccelMag = transForce.len();
            final float transAccel = transAccelMag > 0f
                ? RelativisticMath.transverseAcceleration(transAccelMag, restMass, speed)
                : 0f;
            final Vector3 relForce = new Vector3(velDir).scl(longAccel * restMass);
            if (transAccelMag > 0f) {
                relForce.add(new Vector3(transForce).nor().scl(transAccel * restMass));
            }
            physics.body.applyCentralForce(relForce);
        } else {
            physics.body.applyCentralForce(force);
        }
```

Note: `currentVelocity.nor()` mutates the vector; the original code relied on that. The replacement uses `currentVelocity.cpy().nor()` so `currentVelocity` (already consumed above) stays intact — harmless either way since it is recomputed each tick.

(d) Change the damping call. Find `physics.body.setDamping(flight.linearDrag, flight.angularDrag);` and replace with:

```java
        // FA controllers govern velocity convergence; never double-damp linear.
        // Angular auto-stop is applied in the rotation block (Task 5). Keep both 0 here.
        physics.body.setDamping(0f, 0f);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightAssistTest"`
Expected: PASS (5 tests). Re-run `ShipFlightSystemTest` to confirm no regression:
`./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightSystemTest"` → PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java \
        core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightAssistTest.java
git commit -m "feat(flight): linear Flight Assist — set-point throttle, speed cap, drift bleed"
```

---

### Task 5: Rotation rate-target controller + blue zone + FA auto-stop

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightAssistTest.java` (add cases)

- [ ] **Step 1: Write the failing tests**

Add to `ShipFlightAssistTest`:

```java
    @Test
    void flightAssistAutoStopsRotationWhenStickReleased() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, true, null);
        PhysicsBodyComponent phys = ship.getComponent(PhysicsBodyComponent.class);
        phys.body.setAngularVelocity(new Vector3(0f, 1.0f, 0f)); // spinning in yaw
        // no rotational input
        step(system, 5f);
        float spin = phys.body.getAngularVelocity().len();
        assertTrue(spin < 0.1f, "FA should auto-stop rotation, spin=" + spin);
    }

    @Test
    void flightAssistOffKeepsSpinning() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, false, null);
        PhysicsBodyComponent phys = ship.getComponent(PhysicsBodyComponent.class);
        phys.body.setAngularVelocity(new Vector3(0f, 1.0f, 0f));
        step(system, 5f);
        float spin = phys.body.getAngularVelocity().len();
        assertTrue(spin > 0.8f, "FA-off must preserve spin, spin=" + spin);
    }

    @Test
    void blueZoneThrottleTurnsFasterThanFullThrottle() {
        // Blue-zone throttle (0.6) achieves higher yaw rate than full throttle (1.0).
        Engine blueEngine = new Engine();
        ShipFlightSystem blueSys = buildShip(blueEngine, 100f, true, null);
        ship.getComponent(ShipFlightInputComponent.class).throttle = 0.6f;
        ship.getComponent(ShipFlightInputComponent.class).yawInput = 1f;
        step(blueSys, 2f);
        float blueYaw = Math.abs(ship.getComponent(PhysicsBodyComponent.class)
            .body.getAngularVelocity().y);
        tearDown(); // dispose first ship/world before building the second

        Engine fullEngine = new Engine();
        ShipFlightSystem fullSys = buildShip(fullEngine, 100f, true, null);
        ship.getComponent(ShipFlightInputComponent.class).throttle = 1.0f;
        ship.getComponent(ShipFlightInputComponent.class).yawInput = 1f;
        step(fullSys, 2f);
        float fullYaw = Math.abs(ship.getComponent(PhysicsBodyComponent.class)
            .body.getAngularVelocity().y);

        assertTrue(blueYaw > fullYaw + 0.02f,
            "blue-zone yaw (" + blueYaw + ") should exceed full-throttle yaw (" + fullYaw + ")");
    }
```

Note: the blue-zone test calls `tearDown()` manually mid-test to release the first Bullet world before building the second; `@AfterEach` then runs harmlessly (guards are null-safe after disposal — set `world`/`ship` to null at end of `tearDown` to be safe; see Step 3 adjustment).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightAssistTest"`
Expected: FAIL — current torque model has no auto-stop and no blue-zone scaling.

- [ ] **Step 3: Implement rotation controller**

First make `tearDown()` idempotent in the test: at the end of `ShipFlightAssistTest.tearDown()`, add `world = null; ship = null; solver = null; bp = null; disp = null; cc = null;` so the manual call inside the blue-zone test plus the automatic `@AfterEach` don't double-dispose.

In `ShipFlightSystem.java`, replace the existing torque block:

```java
        torque.setZero();
        torque.mulAdd(localRight, input.pitchInput * flight.pitchYawTorque);
        torque.mulAdd(localUp, -input.yawInput * flight.pitchYawTorque);
        torque.mulAdd(localForward, input.rollInput * flight.rollTorque);

        physics.body.applyTorque(torque);
```

with a rate-target controller:

```java
        // --- Rotation ---
        float maxTurnDeg = (shipData != null && shipData.maxTurnRate > 0f)
            ? shipData.maxTurnRate : DEFAULT_MAX_TURN_RATE_DEG;
        float maxTurnRad = maxTurnDeg * MathUtils.degreesToRadians;
        float blue = FlightControlMath.blueZoneFactor(effectiveThrottle,
            flight.blueZoneLow, flight.blueZoneHigh, flight.offBandTurnScale);
        float maxRate = maxTurnRad * blue;

        // Clamp raw inputs (player mouse delta may exceed 1).
        float pitchCmd = MathUtils.clamp(input.pitchInput, -1f, 1f);
        float yawCmd   = MathUtils.clamp(input.yawInput, -1f, 1f);
        float rollCmd  = MathUtils.clamp(input.rollInput, -1f, 1f);

        // Current angular velocity projected onto local axes.
        Vector3 angVel = physics.body.getAngularVelocity();
        float ratePitch = angVel.dot(localRight);
        float rateYaw   = angVel.dot(localUp);
        float rateRoll  = angVel.dot(localForward);

        torque.setZero();
        if (flight.flightAssistEnabled) {
            // Desired local rates. Pitch about +localRight, yaw about +localUp,
            // roll about +localForward. Sign matches the legacy torque mapping
            // (yaw uses -yawInput about up; pitch +pitchInput about right; roll +rollInput about fwd).
            float desiredPitch =  pitchCmd * maxRate;
            float desiredYaw   = -yawCmd   * maxRate;
            float desiredRoll  =  rollCmd  * maxRate;

            float tp = MathUtils.clamp((desiredPitch - ratePitch) / maxTurnRad * flight.rotStiffness, -1f, 1f);
            float ty = MathUtils.clamp((desiredYaw   - rateYaw)   / maxTurnRad * flight.rotStiffness, -1f, 1f);
            float tr = MathUtils.clamp((desiredRoll  - rateRoll)  / maxTurnRad * flight.rotStiffness, -1f, 1f);

            torque.mulAdd(localRight, tp * flight.pitchYawTorque);
            torque.mulAdd(localUp,    ty * flight.pitchYawTorque);
            torque.mulAdd(localForward, tr * flight.rollTorque);
        } else {
            // Newtonian: raw torque from input, no auto-stop.
            torque.mulAdd(localRight, pitchCmd * flight.pitchYawTorque);
            torque.mulAdd(localUp, -yawCmd * flight.pitchYawTorque);
            torque.mulAdd(localForward, rollCmd * flight.rollTorque);
        }

        physics.body.applyTorque(torque);
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightAssistTest"`
Expected: PASS (8 tests total).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java \
        core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightAssistTest.java
git commit -m "feat(flight): rate-target rotation with blue-zone scaling and FA auto-stop"
```

---

### Task 6: Flight-Assist toggle handling

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightAssistTest.java` (add a case)

- [ ] **Step 1: Write the failing test**

Add to `ShipFlightAssistTest`:

```java
    @Test
    void faTogglePressFlipsModeOncePerPress() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, true, null);
        ShipFlightComponent flight = ship.getComponent(ShipFlightComponent.class);
        ShipFlightInputComponent in = ship.getComponent(ShipFlightInputComponent.class);

        in.flightAssistTogglePressed = true;
        system.update(1f / 60f);
        assertFalse(flight.flightAssistEnabled, "first press disables FA");
        assertFalse(in.flightAssistTogglePressed, "flag should be consumed");

        // Held high without re-press should NOT flip again on its own; the input
        // system is responsible for edge-detection, so a consumed flag stays false.
        system.update(1f / 60f);
        assertFalse(flight.flightAssistEnabled, "no second flip without a new press");

        in.flightAssistTogglePressed = true;
        system.update(1f / 60f);
        assertTrue(flight.flightAssistEnabled, "second press re-enables FA");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightAssistTest"`
Expected: FAIL — toggle not handled.

- [ ] **Step 3: Handle the toggle**

In `ShipFlightSystem.applyFlight`, immediately after the null-guard block (after `if (physics == null || physics.body == null || flight == null) return;`) and **before** the `canThrust` early-return, add:

```java
        // Flight-Assist toggle is processed even when engines are down (it's a computer mode).
        if (input.flightAssistTogglePressed) {
            flight.flightAssistEnabled = !flight.flightAssistEnabled;
            input.flightAssistTogglePressed = false;
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightAssistTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java \
        core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightAssistTest.java
git commit -m "feat(flight): edge-triggered Flight Assist toggle"
```

---

### Task 7: Boost (gauge + cooldown + surge)

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightAssistTest.java` (add cases)

- [ ] **Step 1: Write the failing tests**

Add to `ShipFlightAssistTest`:

```java
    @Test
    void boostActivatesConsumesEnergyAndRespectsCooldown() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, true, null);
        ShipFlightComponent flight = ship.getComponent(ShipFlightComponent.class);
        flight.boostEnergy = 100f;
        flight.boostEnergyCost = 50f;
        flight.boostCooldown = 3f;
        flight.boostDuration = 2f;
        ShipFlightInputComponent in = ship.getComponent(ShipFlightInputComponent.class);

        in.boostPressed = true;
        system.update(1f / 60f);
        assertEquals(50f, flight.boostEnergy, 1e-3, "boost consumes energy");
        assertTrue(flight.boostTimer > 0f, "boost timer started");
        assertFalse(in.boostPressed, "boost flag consumed");

        // Second press during cooldown is ignored (energy unchanged).
        in.boostPressed = true;
        system.update(1f / 60f);
        assertEquals(50f, flight.boostEnergy, 1e-3, "no second boost during cooldown");
    }

    @Test
    void boostNotEnoughEnergyDoesNothing() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, true, null);
        ShipFlightComponent flight = ship.getComponent(ShipFlightComponent.class);
        flight.boostEnergy = 10f;
        flight.boostEnergyCost = 50f;
        ship.getComponent(ShipFlightInputComponent.class).boostPressed = true;

        system.update(1f / 60f);
        assertEquals(0f, flight.boostTimer, 1e-3, "no boost without energy");
        assertEquals(10f, flight.boostEnergy, 1e-3);
    }

    @Test
    void boostGaugeRechargesWhenIdle() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, true, null);
        ShipFlightComponent flight = ship.getComponent(ShipFlightComponent.class);
        flight.boostEnergy = 0f;
        flight.boostMaxEnergy = 100f;
        flight.boostRechargeRate = 50f;

        step(system, 1f); // ~50 energy back

        assertTrue(flight.boostEnergy > 40f && flight.boostEnergy <= 100f,
            "gauge recharges, energy=" + flight.boostEnergy);
    }

    @Test
    void boostRaisesAchievableSpeed() {
        Engine baseEngine = new Engine();
        ShipFlightSystem baseSys = buildShip(baseEngine, 100f, true, null);
        ship.getComponent(ShipFlightInputComponent.class).throttle = 1f;
        step(baseSys, 6f);
        float baseSpeed = ship.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().len();
        tearDown();

        Engine boostEngine = new Engine();
        ShipFlightSystem boostSys = buildShip(boostEngine, 100f, true, null);
        ShipFlightComponent flight = ship.getComponent(ShipFlightComponent.class);
        flight.boostDuration = 6f;
        flight.boostForce = 40000f;
        flight.boostEnergy = 100f;
        ShipFlightInputComponent in = ship.getComponent(ShipFlightInputComponent.class);
        in.throttle = 1f;
        in.boostPressed = true;
        step(boostSys, 4f);
        float boostSpeed = ship.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().len();

        assertTrue(boostSpeed > baseSpeed + 10f,
            "boost should exceed normal max, boost=" + boostSpeed + " base=" + baseSpeed);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightAssistTest"`
Expected: FAIL — boost not implemented.

- [ ] **Step 3: Implement boost**

In `ShipFlightSystem.applyFlight`, add boost activation **after** the `canThrust` early-return and before the throttle-management block (so a disabled-engine ship can't boost):

```java
        // --- Boost activation ---
        if (input.boostPressed) {
            input.boostPressed = false;
            if (flight.boostCooldownTimer <= 0f
                    && flight.boostEnergy >= flight.boostEnergyCost
                    && flight.boostTimer <= 0f) {
                flight.boostEnergy -= flight.boostEnergyCost;
                flight.boostTimer = flight.boostDuration;
                flight.boostCooldownTimer = flight.boostCooldown;
            }
        }
```

Add timer/gauge integration near the end of `applyFlight`, just before `physics.body.activate();`:

```java
        // --- Boost timers + gauge ---
        if (flight.boostTimer > 0f) {
            flight.boostTimer = Math.max(0f, flight.boostTimer - deltaTime);
        } else if (flight.boostEnergy < flight.boostMaxEnergy) {
            flight.boostEnergy = Math.min(flight.boostMaxEnergy,
                flight.boostEnergy + flight.boostRechargeRate * deltaTime);
        }
        if (flight.boostCooldownTimer > 0f) {
            flight.boostCooldownTimer = Math.max(0f, flight.boostCooldownTimer - deltaTime);
        }
```

Add the forward surge inside the linear block. In **both** the FA-on and FA-off branches (Task 4), after the forward force is added, apply the surge so boost works in either mode. Add this line right after `force.mulAdd(localForward, ...)` in the FA-on branch and again in the FA-off branch:

```java
        if (flight.boostTimer > 0f) force.mulAdd(localForward, flight.boostForce);
```

(The `effectiveMaxSpeed *= boostSpeedMultiplier` from Task 4 already raised the FA target speed; the extra `boostForce` provides the surge/acceleration toward it.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightAssistTest"`
Expected: PASS (all cases).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java \
        core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightAssistTest.java
git commit -m "feat(flight): engine boost with dedicated gauge, surge, and cooldown"
```

---

### Task 8: Expose set-point throttle on the HUD value + wire player input keys

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java`
- Test: covered by `FlightControlMathTest` (the pure helper); the key wiring is GL/`Gdx.input`-bound and verified in the run step.

The throttle helper is already unit-tested (Task 3). This task wires it in and adds the Z/Tab/X keys.

- [ ] **Step 1: Add a throttle ramp-rate constant + persistent stepping**

In `PlayerInputSystem.processFlightInput(...)` replace the throttle handling. Currently the method zeroes `flight.throttle` and adds ±1 while W/S held. Replace:

```java
        flight.throttle = 0;
        flight.strafe = 0;
        flight.verticalThrust = 0;
        flight.rollInput = 0;

        if (Gdx.input != null) {
            if (Gdx.input.isKeyPressed(Input.Keys.W)) flight.throttle += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) flight.throttle -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) flight.strafe -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) flight.strafe += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) flight.verticalThrust += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) flight.verticalThrust -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.Q)) flight.rollInput -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.E)) flight.rollInput += 1f;
        }
```

with (note: `throttle` is NO LONGER zeroed — it persists):

```java
        flight.strafe = 0;
        flight.verticalThrust = 0;
        flight.rollInput = 0;

        if (Gdx.input != null) {
            boolean throttleUp = Gdx.input.isKeyPressed(Input.Keys.W);
            boolean throttleDown = Gdx.input.isKeyPressed(Input.Keys.S);
            boolean throttleZero = Gdx.input.isKeyPressed(Input.Keys.X);
            flight.throttle = com.galacticodyssey.ship.systems.FlightControlMath.stepThrottle(
                flight.throttle, throttleUp, throttleDown, throttleZero,
                THROTTLE_RAMP_RATE, REVERSE_FRACTION_INPUT, Gdx.graphics.getDeltaTime());

            if (Gdx.input.isKeyPressed(Input.Keys.A)) flight.strafe -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) flight.strafe += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) flight.verticalThrust += 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) flight.verticalThrust -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.Q)) flight.rollInput -= 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.E)) flight.rollInput += 1f;
        }
```

Add the constants near the top of the class (with other private fields):

```java
    private static final float THROTTLE_RAMP_RATE = 1.5f;     // full-range in ~0.9s held
    private static final float REVERSE_FRACTION_INPUT = 0.4f;  // keyboard reverse cap
```

- [ ] **Step 2: Wire FA toggle (Z) and boost (Tab) as edges**

`PlayerInputSystem` already uses an InputProcessor `keyDown` pattern for edges (e.g. `cameraTogglePressed`, `boardPressed`, `targetLockPressed`). Locate the `keyDown(int keycode)` override and add cases that set new boolean fields:

```java
        if (keycode == Input.Keys.Z) { flightAssistTogglePressed = true; return true; }
        if (keycode == Input.Keys.TAB) { boostPressed = true; return true; }
```

Declare the fields alongside the other edge flags (e.g. near `cameraTogglePressed`):

```java
    private boolean flightAssistTogglePressed;
    private boolean boostPressed;
```

Then, in `processFlightInput`, transfer them to the component (mirroring how `cameraTogglePressed` is transferred):

```java
        if (flightAssistTogglePressed) { flight.flightAssistTogglePressed = true; flightAssistTogglePressed = false; }
        if (boostPressed) { flight.boostPressed = true; boostPressed = false; }
```

- [ ] **Step 3: Binding audit**

Search the class for existing uses of `Input.Keys.Z`, `Input.Keys.TAB`, `Input.Keys.X` to ensure no clash:

Run: `grep -n "Keys.Z\b\|Keys.TAB\|Keys.X\b" core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java`
Expected: no pre-existing piloting binding on these keys. If a clash exists, pick an unused key and note it in this step. Record the final piloting bindings as a comment block above `processFlightInput`:

```java
    // Piloting keys: W/S throttle±, X throttle-zero, A/D strafe, Space/Ctrl vertical,
    // Q/E roll, mouse pitch/yaw, Z flight-assist toggle, Tab boost.
```

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java
git commit -m "feat(flight): persistent set-point throttle + Z/Tab/X piloting keys"
```

---

### Task 9: Make set-point throttle drive the HUD throttle value

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightAssistTest.java` (add a case)

The HUD reads `flight.currentThrottle`. Ensure it reflects the commanded set-point.

- [ ] **Step 1: Write the failing test**

Add to `ShipFlightAssistTest`:

```java
    @Test
    void currentThrottleMirrorsSetPoint() {
        Engine engine = new Engine();
        ShipFlightSystem system = buildShip(engine, 100f, true, null);
        ShipFlightComponent flight = ship.getComponent(ShipFlightComponent.class);
        ship.getComponent(ShipFlightInputComponent.class).throttle = 0.7f;
        system.update(1f / 60f);
        assertEquals(0.7f, flight.currentThrottle, 0.05f, "HUD throttle should track set-point");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightAssistTest"`
Expected: FAIL if `currentThrottle` reflects the lerped engine throttle (which starts at 0 and ramps), not the commanded set-point. (May already be close; if it passes, keep the assignment from Step 3 anyway for clarity.)

- [ ] **Step 3: Set `currentThrottle` to the commanded set-point**

In `ShipFlightSystem.applyFlight`, find the existing line near the end:

```java
        flight.currentThrottle = effectiveThrottle;
```

Replace with:

```java
        // HUD/readout reflects the commanded set-point, not the lerped engine ramp.
        flight.currentThrottle = input.throttle;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightAssistTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java \
        core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightAssistTest.java
git commit -m "feat(flight): HUD throttle reflects commanded set-point"
```

---

### Task 10: Data-driven tuning through ShipClassData / Registry / Factory

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/data/ShipClassData.java`
- Modify: `core/src/main/java/com/galacticodyssey/ship/data/ShipClassRegistry.java`
- Modify: `core/src/main/java/com/galacticodyssey/ship/ShipFactory.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/data/ShipClassRegistryTest.java` (add a case)

- [ ] **Step 1: Write the failing test**

Add to `ShipClassRegistryTest` (uses the `loadShipClasses(JsonValue)` overload):

```java
    @Test
    void parsesEdFlightFieldsWithDefaults() {
        String json = "[{"
            + "\"id\":\"x\",\"name\":\"X\",\"sizeClass\":\"SMALL\",\"mass\":5000,"
            + "\"linearThrust\":50000,\"strafeThrustFraction\":0.6,\"verticalThrustFraction\":0.6,"
            + "\"pitchYawTorque\":20000,\"rollTorque\":15000,\"linearDrag\":0.6,\"angularDrag\":0.5,"
            + "\"maxIsp\":300,\"maxThrust\":50000,\"throttleResponseRate\":3,\"fuelCapacity\":100,"
            + "\"wingArea\":10,\"dragCoefficient\":0.3,\"crossSectionArea\":5,\"stallAngle\":15,"
            + "\"maxLiftCoefficient\":1.2,\"controlSurfaceAuthority\":1,\"vtolThrustFraction\":0.5,"
            + "\"boostSpeedMultiplier\":1.7}]";
        com.badlogic.gdx.utils.JsonValue root = new com.badlogic.gdx.utils.JsonReader().parse(json);
        ShipClassRegistry reg = new ShipClassRegistry();
        reg.loadShipClasses(root);
        ShipClassData d = reg.getShipClass("x");

        assertEquals(1.7f, d.boostSpeedMultiplier, 1e-4); // explicit value parsed
        assertEquals(0.4f, d.reverseFraction, 1e-4);       // default applied (absent in JSON)
        assertEquals(0.4f, d.blueZoneLow, 1e-4);
        assertEquals(0.8f, d.blueZoneHigh, 1e-4);
    }
```

(Match the existing test class's import style; if it already imports `JsonValue`/`JsonReader`, drop the fully-qualified names.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.data.ShipClassRegistryTest"`
Expected: FAIL — fields/parsing absent.

- [ ] **Step 3: Add fields to `ShipClassData`**

In `ShipClassData.java`, before the closing brace, add:

```java
    public float reverseFraction;
    public float faLinearGain;
    public float faLateralBleed;
    public float blueZoneLow;
    public float blueZoneHigh;
    public float offBandTurnScale;
    public float rotStiffness;
    public float boostSpeedMultiplier;
    public float boostForce;
    public float boostDuration;
    public float boostEnergyCost;
    public float boostMaxEnergy;
    public float boostRechargeRate;
    public float boostCooldown;
```

- [ ] **Step 4: Parse with defaults in `ShipClassRegistry`**

In `loadShipClasses(JsonValue root)`, after the `data.vtolThrustFraction = ...` line, add (uses the `getFloat(name, default)` overload so existing JSON without these keys still loads):

```java
            data.reverseFraction = entry.getFloat("reverseFraction", 0.4f);
            data.faLinearGain = entry.getFloat("faLinearGain", 1.5f);
            data.faLateralBleed = entry.getFloat("faLateralBleed", 1.2f);
            data.blueZoneLow = entry.getFloat("blueZoneLow", 0.4f);
            data.blueZoneHigh = entry.getFloat("blueZoneHigh", 0.8f);
            data.offBandTurnScale = entry.getFloat("offBandTurnScale", 0.5f);
            data.rotStiffness = entry.getFloat("rotStiffness", 4f);
            data.boostSpeedMultiplier = entry.getFloat("boostSpeedMultiplier", 1.5f);
            data.boostForce = entry.getFloat("boostForce", 0f); // 0 → factory size default
            data.boostDuration = entry.getFloat("boostDuration", 5f);
            data.boostEnergyCost = entry.getFloat("boostEnergyCost", 50f);
            data.boostMaxEnergy = entry.getFloat("boostMaxEnergy", 100f);
            data.boostRechargeRate = entry.getFloat("boostRechargeRate", 10f);
            data.boostCooldown = entry.getFloat("boostCooldown", 4f);
```

- [ ] **Step 5: Apply in `ShipFactory` (both build paths) with per-size defaults**

In `ShipFactory.java`, add new per-size constant arrays alongside the existing flight arrays (after `ANGULAR_DRAG` at line ~70):

```java
    // ED flight tuning per size class [SMALL, MEDIUM, LARGE]
    private static final float[] REVERSE_FRACTION   = { 0.40f,   0.40f,   0.35f };
    private static final float[] FA_LINEAR_GAIN      = { 1.5f,    1.3f,    1.1f };
    private static final float[] FA_LATERAL_BLEED    = { 1.2f,    1.0f,    0.8f };
    private static final float[] BLUE_ZONE_LOW       = { 0.40f,   0.40f,   0.40f };
    private static final float[] BLUE_ZONE_HIGH      = { 0.80f,   0.80f,   0.80f };
    private static final float[] OFF_BAND_TURN_SCALE = { 0.50f,   0.55f,   0.60f };
    private static final float[] ROT_STIFFNESS       = { 4.0f,    3.5f,    3.0f };
    private static final float[] BOOST_SPEED_MULT    = { 1.6f,    1.4f,    1.25f };
    private static final float[] BOOST_FORCE         = { 40_000f, 120_000f, 200_000f };
    private static final float[] BOOST_DURATION      = { 5f,      6f,      8f };
    private static final float[] BOOST_ENERGY_COST   = { 50f,     60f,     70f };
    private static final float[] BOOST_MAX_ENERGY    = { 100f,    100f,    100f };
    private static final float[] BOOST_RECHARGE       = { 12f,     8f,      5f };
    private static final float[] BOOST_COOLDOWN       = { 3f,      5f,      8f };
```

Then, in **both** flight-setup blocks (`createShip` near line 199 and `createShipFromDesign` near line 300), after `flight.currentThrottle = 0f;` and before `entity.add(flight);`, add:

```java
        flight.reverseFraction     = REVERSE_FRACTION[si];
        flight.faLinearGain        = FA_LINEAR_GAIN[si];
        flight.faLateralBleed      = FA_LATERAL_BLEED[si];
        flight.blueZoneLow         = BLUE_ZONE_LOW[si];
        flight.blueZoneHigh        = BLUE_ZONE_HIGH[si];
        flight.offBandTurnScale    = OFF_BAND_TURN_SCALE[si];
        flight.rotStiffness        = ROT_STIFFNESS[si];
        flight.boostSpeedMultiplier = BOOST_SPEED_MULT[si];
        flight.boostForce          = BOOST_FORCE[si];
        flight.boostDuration       = BOOST_DURATION[si];
        flight.boostEnergyCost     = BOOST_ENERGY_COST[si];
        flight.boostMaxEnergy      = BOOST_MAX_ENERGY[si];
        flight.boostEnergy         = BOOST_MAX_ENERGY[si];
        flight.boostRechargeRate   = BOOST_RECHARGE[si];
        flight.boostCooldown       = BOOST_COOLDOWN[si];
        flight.flightAssistEnabled = true;
```

(In `createShip` the size index is `si`; in `createShipFromDesign` it is `int si = design.sizeClass.ordinal();` already declared — reuse it.)

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.data.ShipClassRegistryTest"`
Expected: PASS. Then build the factory: `./gradlew :core:compileJava` → SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/data/ShipClassData.java \
        core/src/main/java/com/galacticodyssey/ship/data/ShipClassRegistry.java \
        core/src/main/java/com/galacticodyssey/ship/ShipFactory.java \
        core/src/test/java/com/galacticodyssey/ship/data/ShipClassRegistryTest.java
git commit -m "feat(flight): data-driven ED flight tuning per ship size class"
```

---

### Task 11: AI / NPC regression guard

**Files:**
- Test: run existing AI/NPC suites; add one focused assertion.
- Modify (only if a regression surfaces): `core/src/main/java/com/galacticodyssey/ship/ai/ShipPilotAISystem.java`

- [ ] **Step 1: Run the full AI + NPC flight suites**

Run:
```bash
./gradlew :core:test --tests "com.galacticodyssey.ship.ai.*" \
  --tests "com.galacticodyssey.ship.systems.ShipFlightSystemNpcTest" \
  --tests "com.galacticodyssey.ship.systems.PilotingIntegrationTest" \
  --tests "com.galacticodyssey.ship.systems.ShipFlightSystemRefactoredTest"
```
Expected: PASS. The rate-target rotation accepts the AI's normalized [-1,1] stick outputs; AI ships default `flightAssistEnabled = true` (set by the factory in Task 10, and `true` by component default for ad-hoc test ships).

- [ ] **Step 2: If any test regresses, diagnose with systematic-debugging before editing**

Most likely cause if a turn-convergence test fails: the AI's own `kd` damping plus the controller's rate damping over-damps, slowing convergence below the test's tick budget. Concrete remedy (only if needed): in `ShipPilotAISystem`, the steering controller's `kd` can be lowered for FA-on ships, OR the affected test's tolerance/tick-count reflects the new (still-correct) behavior. Do not change behavior speculatively — only if a test actually fails, and prefer adjusting AI tuning over weakening the flight model.

- [ ] **Step 3: Add an NPC-flies-under-FA assertion**

Add to `ShipFlightSystemNpcTest` (follow its existing harness/imports) a test that an NPC ship with `throttle=1`, `flightAssistEnabled=true`, and a fixed `maxSpeed` converges to roughly that speed:

```java
    @Test
    void npcShipConvergesToMaxSpeedUnderFlightAssist() {
        // Build an NPC ship (ShipFlightInputComponent on the ship entity itself),
        // following this class's existing buildNpcShip helper / harness. Set
        // input.throttle = 1f, flight.flightAssistEnabled = true, data.maxSpeed = 80f,
        // step ~10s, assert |velocity| within 8 m/s of 80.
    }
```

If `ShipFlightSystemNpcTest` lacks a reusable builder, copy the `buildShip` helper shape from `ShipFlightAssistTest` but attach `ShipFlightInputComponent` to the ship entity (no player entity) — that is the NPC path the system iterates (`npcShips`). Fill in the assertion concretely:

```java
        float speed = ship.getComponent(PhysicsBodyComponent.class).body.getLinearVelocity().len();
        assertEquals(80f, speed, 8f, "NPC under FA should hold ~maxSpeed, got " + speed);
```

- [ ] **Step 4: Run the new test**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightSystemNpcTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "test(flight): AI/NPC regression guard for ED flight model"
```

---

### Task 12: Minimal cockpit HUD readouts (FA + Boost)

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/CockpitHUDSystem.java`
- No unit test (Scene2D/GL rendering glue; data it reads is covered by system tests). Verified visually in Task 13.

- [ ] **Step 1: Add FA + boost labels**

In `CockpitHUDSystem.java`, declare two labels with the other instrument labels (after `private Label capacitorLabel;`):

```java
    private Label faLabel;
    private Label boostLabel;
```

In `buildLayout()`, create them with the others (after `capacitorLabel = ...`):

```java
        faLabel    = new Label("FA: ON", styleCyan);
        boostLabel = new Label("BOOST: 100%", styleWhite);
```

Add them to the bottom-left panel (after `btmLeft.add(capacitorLabel).left();` — change that line to end with `.row();` then add the two new rows):

```java
        btmLeft.add(capacitorLabel).left().row();
        btmLeft.add(faLabel).left().row();
        btmLeft.add(boostLabel).left();
```

- [ ] **Step 2: Refresh them in `refreshInstruments()`**

In the `--- Throttle ---` block, extend it (the `flight` local already exists there):

```java
        if (flight != null) {
            throttleLabel.setText(String.format("THR: %.0f%%", flight.currentThrottle * 100f));
            faLabel.setText(flight.flightAssistEnabled ? "FA: ON" : "FA: OFF");
            faLabel.setStyle(flight.flightAssistEnabled ? styleCyan : styleOrange);
            float boostPct = (flight.boostMaxEnergy > 0f)
                ? (flight.boostEnergy / flight.boostMaxEnergy * 100f) : 0f;
            String boostState = flight.boostTimer > 0f ? " (BOOSTING)"
                : (flight.boostCooldownTimer > 0f ? " (CD)" : "");
            boostLabel.setText(String.format("BOOST: %.0f%%%s", boostPct, boostState));
            boostLabel.setStyle(flight.boostTimer > 0f ? styleCyan : styleWhite);
        } else {
            throttleLabel.setText("THR: ---");
            faLabel.setText("FA: ---");
            boostLabel.setText("BOOST: ---");
        }
```

(Remove the old standalone `else { throttleLabel.setText("THR: ---"); }` so there is a single if/else.)

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/CockpitHUDSystem.java
git commit -m "feat(flight): cockpit HUD shows Flight Assist state and boost gauge"
```

---

### Task 13: Full build, full test suite, and live fly-test

**Files:** none modified (verification only).

- [ ] **Step 1: Full core test suite**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL, all tests green. Investigate any failure with `superpowers:systematic-debugging` before proceeding.

- [ ] **Step 2: Live fly-test via the run skill**

Invoke the `run-galactic-odyssey` skill to build + launch the game headless-capable, enter piloting mode, and exercise: set throttle to ~60% (hold W, release — verify it holds), hard yaw turn at 60% vs 100% throttle (blue zone), toggle FA with Z and strafe-drift, press Tab to boost. Capture a screenshot of the cockpit HUD showing THR / SPEED / FA / BOOST.

- [ ] **Step 3: Confirm the screenshot**

Verify the screenshot shows the new HUD readouts and the ship responds to set-point throttle (speed stabilizes rather than climbing indefinitely). Save it under the run skill's screenshot location and reference it in the final summary.

- [ ] **Step 4: Final commit (if any verification tweaks were needed)**

```bash
git add -A
git commit -m "chore(flight): verification pass for ED flight model"
```

---

## Self-Review

**Spec coverage:**
- Set-point throttle + speed cap → Tasks 3 (helper), 4 (cap), 8 (input), 9 (HUD value). ✓
- Flight Assist ON (forward tracking + lateral bleed + rotational auto-stop) → Tasks 4, 5. ✓
- Flight Assist OFF (Newtonian linear + rotation) → Tasks 4, 5. ✓
- FA toggle → Tasks 6 (handling), 8 (Z key). ✓
- Blue zone → Tasks 3 (helper), 5 (applied to turn rate). ✓
- Boost (gauge, surge, cooldown, recharge) → Tasks 7 (logic), 8 (Tab key), 10 (per-size data). ✓
- Engines-disabled coast → preserved by the existing `canThrust` guard; boost gated after it (Task 7); FA toggle allowed before it (Task 6). ✓
- Input mapping + binding audit → Task 8. ✓
- AI compatibility → Task 11. ✓
- Data-driven config (both factory paths) → Task 10. ✓
- HUD → Task 12. ✓
- Testing + live verification → all tasks + Task 13. ✓

**Placeholder scan:** Task 11 Step 3 intentionally references the file's existing NPC builder (which I have not read in full); the assertion code is concrete, and a fallback (copy `buildShip`, attach input to the ship) is spelled out. No "TBD"/"handle edge cases" placeholders elsewhere.

**Type consistency:** Field names are identical across `ShipFlightComponent`, `ShipFlightSnapshot`, `ShipClassData`, and `ShipFactory` arrays (`reverseFraction`, `faLinearGain`, `faLateralBleed`, `blueZoneLow/High`, `offBandTurnScale`, `rotStiffness`, `boost*`). Helper signatures `FlightControlMath.blueZoneFactor(throttle, low, high, offScale)` and `stepThrottle(current, up, down, zero, rampRate, reverseFraction, dt)` are used consistently in Tasks 3, 5, and 8. `ShipDataComponent.maxSpeed`/`maxTurnRate` reads match the deviation note (deg→rad conversion in Task 5).
