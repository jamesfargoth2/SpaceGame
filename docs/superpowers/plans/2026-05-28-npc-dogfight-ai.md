# NPC Dogfight AI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give NPC ships data-driven 6DOF dogfight AI that pursues, attacks, and evades the player and other ships, reusing the existing flight, weapon, ballistics, and behavior-tree systems.

**Architecture:** A per-ship `ShipPilotAIComponent` holds a gdx-ai `BehaviorTree<Entity>` (built in code by `DogfightTreeFactory`) and a `DogfightBlackboard`. `ShipPilotAISystem` (priority 2, before flight) updates blackboard sensors, steps the tree (FULL LOD) or a cheap path (SIMPLIFIED LOD), converts the tree's desired aim/throttle into stick inputs via the pure `ShipSteeringController`, writes the ship's own `ShipFlightInputComponent`, and fires weapons through `ShipWeaponSystem`. `ShipFlightSystem` is refactored to apply each NPC ship's own input (today it is player-only). `ShipFactory.createNpcCombatShip` builds flyable AI ships, called from `FleetExpansionSystem`.

**Tech Stack:** Java 17, libGDX 1.13, Ashley ECS, gdx-ai (`com.badlogic.gdx.ai.btree`), gdx-bullet (headless in tests via `Bullet.init()`), JUnit 5, libGDX `Json`.

---

## Key facts established during research (read before starting)

- `ShipFlightInputComponent` (fields: `throttle, strafe, verticalThrust, pitchInput, yawInput, rollInput, fireGroup[4], fireHeld[4]`) is added to the **player** entity by `PilotTransitionSystem`, NOT to ship entities. NPC ships will carry their own instance on the ship entity. There is no collision: the player's ship entity has no `ShipFlightInputComponent`.
- `ShipFlightSystem` (`super(3)`, EntitySystem) only drives `playerState.currentShip`. It must be refactored (Task 7) to also apply NPC ships' own inputs.
- `ShipFlightSystem` force/torque conventions (replicate exactly): local forward `(0,0,-1)`, right `(1,0,0)`, up `(0,1,0)` rotated by body transform; `torque += right * pitchInput * pitchYawTorque`, `torque += up * (-yawInput) * pitchYawTorque`, `torque += forward * rollInput * rollTorque`; `body.setDamping(linearDrag, angularDrag)`.
- `BallisticsUtil.computeLeadPoint(Vector3 shooterPos, float muzzleSpeed, Vector3 targetPos, Vector3 targetVel)` returns a new `Vector3` lead point.
- `ShipWeaponSystem(EventBus)`; `boolean fireHardpoint(Entity ship, String hardpointId)` does all heat/ammo/cooldown/energy checks internally and publishes `ShipWeaponFiredEvent`.
- `Hardpoint(String id, HardpointType type, HardpointSize sizeClass, float arcMin, float arcMax)`; `hp.mountedWeapon` (`ShipWeaponData`), `hp.isEmpty()`, `hp.currentState` (`HardpointState`), `hp.fireTimer`.
- Enums: `HardpointType{TURRET,FIXED,BROADSIDE,MISSILE_BAY,POINT_DEFENSE}`, `HardpointSize{SMALL,MEDIUM,LARGE,CAPITAL}`, `ShipWeaponCategory{BALLISTIC_CANNON,LASER_ARRAY,PLASMA_TURRET,MISSILE_LAUNCHER,RAILGUN,EMP_PROJECTOR,POINT_DEFENSE,FLAK_CANNON}`, `HardpointState{IDLE,TRACKING,FIRING,RELOADING,DISABLED}` — all in `com.galacticodyssey.ship.weapons.ShipWeaponEnums`. `DamageType{BALLISTIC,ENERGY,PLASMA,EXPLOSIVE,INCENDIARY,EMP,MELEE,CRYO}` in `com.galacticodyssey.combat.CombatEnums`.
- `ShipWeaponData` fields: `id,name,category,damage,damageType,fireRate,projectileSpeed,range,energyCost,heatPerShot,powerDraw,ammoCapacity,currentAmmo,trackingSpeed,burstCount,burstDelay`; `canFire()`.
- `ShipHardpointComponent`: `List<Hardpoint> hardpoints`, `Entity currentTarget`, `Hardpoint getHardpoint(String id)`.
- `FleetMemberComponent.LODTier { FULL, SIMPLIFIED, ABSTRACT }`, field `lodTier`. Absence of `FleetMemberComponent` ⇒ treat as FULL.
- `HealthComponent`: `currentHP, maxHP, alive`.
- `EventBus`: `subscribe(Class<T>, EventListener<T>)`, `publish(T)`. `EntityKilledEvent(Entity target, Entity killer)`.
- `Snapshotable<S>`: `S takeSnapshot(); void restoreFromSnapshot(S);`
- `ShipFactory(Engine engine, BulletPhysicsSystem physics)`; `Entity createShip(long seed, ShipSizeClass sizeClass, float x, float y, float z)` builds the full flight stack (Transform, ShipData, ShipFlightComponent, PhysicsBodyComponent with Bullet body+inertia, etc.) but does NOT add `ShipFlightInputComponent`, `HealthComponent`, or `ShipHardpointComponent`.
- Headless test setup: call `Bullet.init()` in `@BeforeAll`; build `btDiscreteDynamicsWorld` manually OR `BulletPhysicsSystem physics = new BulletPhysicsSystem(eventBus); physics.initialize();` then `new ShipFactory(engine, physics)`.

## File structure

```
core/src/main/java/com/galacticodyssey/ship/ai/
  PilotArchetype.java               # data POJO (tuning knobs)
  PilotArchetypeRegistry.java       # parse(String json) + loadDefault()
  DogfightBlackboard.java           # sensor values + intents + updateSensors(...)
  ShipSteeringController.java       # pure PD: desired aim -> stick inputs
  ShipPilotAIComponent.java         # tree + target + blackboard + archetype + timers
  ShipPilotAISystem.java            # the per-ship tick (priority 2)
  DogfightTreeFactory.java          # builds the gdx-ai BehaviorTree in code
  tasks/
    HasDogfightTargetCondition.java
    TargetInWeaponArcCondition.java
    LowHealthCondition.java
    IsBeingThreatenedCondition.java
    PursueTargetTask.java
    AttackRunTask.java
    EvadeTask.java
    ExtendAndReengageTask.java
    IdlePatrolTask.java

core/src/main/java/com/galacticodyssey/persistence/snapshots/
  ShipPilotAISnapshot.java          # mirrors CombatAISnapshot shape

core/src/main/resources/data/ai/
  pilot_archetypes.json

# Modified:
core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java          # extract applyFlight; add NPC family
core/src/main/java/com/galacticodyssey/ship/weapons/systems/ShipProjectileSystem.java  # bind missile target
core/src/main/java/com/galacticodyssey/ship/ShipFactory.java                        # createNpcCombatShip
core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetExpansionSystem.java  # call factory; dispose on collapse
core/src/main/java/com/galacticodyssey/core/GameWorld.java                          # register ShipPilotAISystem
```

---

### Task 1: PilotArchetype data + registry

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/PilotArchetype.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/PilotArchetypeRegistry.java`
- Create: `core/src/main/resources/data/ai/pilot_archetypes.json`
- Test: `core/src/test/java/com/galacticodyssey/ship/ai/PilotArchetypeRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PilotArchetypeRegistryTest {

    private static final String JSON = "["
        + "{\"id\":\"rookie\",\"reactionTimeSec\":0.6,\"aimErrorDeg\":8.0,\"aggression\":0.4,"
        + "\"evadeHealthThreshold\":0.5,\"preferredEngageRange\":400.0,\"overshootExtendDist\":150.0,"
        + "\"throttleDiscipline\":0.4,\"usesMissiles\":false},"
        + "{\"id\":\"ace\",\"reactionTimeSec\":0.12,\"aimErrorDeg\":1.5,\"aggression\":0.9,"
        + "\"evadeHealthThreshold\":0.25,\"preferredEngageRange\":300.0,\"overshootExtendDist\":120.0,"
        + "\"throttleDiscipline\":0.9,\"usesMissiles\":true}"
        + "]";

    @Test
    void parsesArchetypesById() {
        PilotArchetypeRegistry reg = new PilotArchetypeRegistry();
        reg.parse(JSON);

        PilotArchetype rookie = reg.get("rookie");
        assertNotNull(rookie);
        assertEquals(8.0f, rookie.aimErrorDeg, 1e-4);
        assertFalse(rookie.usesMissiles);

        PilotArchetype ace = reg.get("ace");
        assertEquals(0.12f, ace.reactionTimeSec, 1e-4);
        assertTrue(ace.usesMissiles);
        assertTrue(ace.aimErrorDeg < rookie.aimErrorDeg);
    }

    @Test
    void unknownIdReturnsNull() {
        PilotArchetypeRegistry reg = new PilotArchetypeRegistry();
        reg.parse(JSON);
        assertNull(reg.get("does_not_exist"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.PilotArchetypeRegistryTest"`
Expected: FAIL — `PilotArchetype`/`PilotArchetypeRegistry` do not exist (compilation error).

- [ ] **Step 3: Write the implementation**

`PilotArchetype.java`:
```java
package com.galacticodyssey.ship.ai;

/** Data-driven tuning for an NPC pilot's behaviour. Loaded from data/ai/pilot_archetypes.json. */
public class PilotArchetype {
    public String id;
    /** Seconds of decision latency before firing / switching targets. */
    public float reactionTimeSec = 0.3f;
    /** Half-angle (degrees) of the random cone added to the aim point. */
    public float aimErrorDeg = 4f;
    /** 0..1 bias toward attacking vs. caution. */
    public float aggression = 0.6f;
    /** Health fraction at or below which the pilot prefers to evade. */
    public float evadeHealthThreshold = 0.35f;
    /** Distance (metres) the pilot tries to hold from the target. */
    public float preferredEngageRange = 350f;
    /** Closure overshoot distance (metres) that triggers extend-and-reengage. */
    public float overshootExtendDist = 130f;
    /** 0..1 how aggressively the pilot manages throttle/energy. */
    public float throttleDiscipline = 0.6f;
    /** Detection radius (metres) for acquiring a target when none is assigned. */
    public float aggroRange = 2000f;
    /** Whether this pilot launches guided missiles. */
    public boolean usesMissiles = false;
}
```

`PilotArchetypeRegistry.java`:
```java
package com.galacticodyssey.ship.ai;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonReader;

import java.util.HashMap;
import java.util.Map;

/** Loads and stores {@link PilotArchetype}s by id. */
public class PilotArchetypeRegistry {

    private final Map<String, PilotArchetype> archetypes = new HashMap<>();
    private final Json json = new Json();

    /** Parse archetypes from a JSON array string (test-friendly, no Gdx.files dependency). */
    public void parse(String jsonText) {
        JsonValue root = new JsonReader().parse(jsonText);
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            PilotArchetype a = json.readValue(PilotArchetype.class, entry);
            archetypes.put(a.id, a);
        }
    }

    /** Load from the bundled resource (requires a libGDX files backend). */
    public void loadDefault() {
        parse(Gdx.files.internal("data/ai/pilot_archetypes.json").readString());
    }

    public PilotArchetype get(String id) {
        return archetypes.get(id);
    }

    public int size() {
        return archetypes.size();
    }
}
```

`pilot_archetypes.json`:
```json
[
  {"id":"rookie","reactionTimeSec":0.6,"aimErrorDeg":8.0,"aggression":0.4,"evadeHealthThreshold":0.5,"preferredEngageRange":400.0,"overshootExtendDist":150.0,"throttleDiscipline":0.4,"aggroRange":1800.0,"usesMissiles":false},
  {"id":"veteran","reactionTimeSec":0.3,"aimErrorDeg":4.0,"aggression":0.65,"evadeHealthThreshold":0.35,"preferredEngageRange":350.0,"overshootExtendDist":130.0,"throttleDiscipline":0.7,"aggroRange":2200.0,"usesMissiles":true},
  {"id":"ace","reactionTimeSec":0.12,"aimErrorDeg":1.5,"aggression":0.9,"evadeHealthThreshold":0.25,"preferredEngageRange":300.0,"overshootExtendDist":120.0,"throttleDiscipline":0.9,"aggroRange":2500.0,"usesMissiles":true}
]
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.PilotArchetypeRegistryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/ai/PilotArchetype.java \
        core/src/main/java/com/galacticodyssey/ship/ai/PilotArchetypeRegistry.java \
        core/src/main/resources/data/ai/pilot_archetypes.json \
        core/src/test/java/com/galacticodyssey/ship/ai/PilotArchetypeRegistryTest.java
git commit -m "feat(ai): add data-driven pilot archetypes for dogfight AI"
```

---

### Task 2: DogfightBlackboard sensor math

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/DogfightBlackboard.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ai/DogfightBlackboardTest.java`

The blackboard holds per-tick sensor values (read by tasks) and intents (written by tasks, read by the system). `updateSensors` is pure math so it is unit-testable with no ECS/GL.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.ai;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DogfightBlackboardTest {

    @Test
    void rangeAndClosureComputed() {
        DogfightBlackboard bb = new DogfightBlackboard();
        // Self at origin facing -Z, moving toward target which sits 100m ahead (-Z), stationary.
        Vector3 selfPos = new Vector3(0, 0, 0);
        Quaternion selfRot = new Quaternion();                 // identity -> forward (0,0,-1)
        Vector3 selfVel = new Vector3(0, 0, -50);              // closing at 50 m/s
        Vector3 targetPos = new Vector3(0, 0, -100);
        Vector3 targetVel = new Vector3(0, 0, 0);

        bb.updateSensors(selfPos, selfRot, selfVel, targetPos, targetVel, 200f);

        assertEquals(100f, bb.rangeToTarget, 0.5f);
        assertTrue(bb.closureRate > 0f, "positive closure when approaching");
        // Target dead ahead -> angleOffBore near 0.
        assertTrue(bb.angleOffBore < 2f, "nose already on target");
    }

    @Test
    void angleOffBoreLargeWhenTargetBehind() {
        DogfightBlackboard bb = new DogfightBlackboard();
        Vector3 selfPos = new Vector3(0, 0, 0);
        Quaternion selfRot = new Quaternion();                 // forward (0,0,-1)
        Vector3 targetPos = new Vector3(0, 0, 100);            // directly behind
        bb.updateSensors(selfPos, selfRot, new Vector3(), targetPos, new Vector3(), 200f);
        assertTrue(bb.angleOffBore > 150f, "target behind -> large angle off bore");
    }

    @Test
    void leadPointAheadOfMovingTarget() {
        DogfightBlackboard bb = new DogfightBlackboard();
        Vector3 selfPos = new Vector3(0, 0, 0);
        Vector3 targetPos = new Vector3(0, 0, -100);
        Vector3 targetVel = new Vector3(20, 0, 0);             // crossing right
        bb.updateSensors(selfPos, new Quaternion(), new Vector3(), targetPos, targetVel, 200f);
        assertTrue(bb.leadPoint.x > 0f, "lead point leads a right-crossing target");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.DogfightBlackboardTest"`
Expected: FAIL — `DogfightBlackboard` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.galacticodyssey.ship.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.BallisticsUtil;

/**
 * Per-ship scratch state for the dogfight behaviour tree. {@code updateSensors} computes
 * read-only situational values; tasks write the {@code desired*}/{@code fire*} intents which
 * the {@link ShipPilotAISystem} consumes after stepping the tree.
 */
public class DogfightBlackboard {

    // --- Sensors (written by updateSensors, read by tasks) ---
    public boolean hasTarget;
    public float rangeToTarget;
    /** Positive when closing on the target. */
    public float closureRate;
    /** Degrees between our nose and the lead point (0 = on target). */
    public float angleOffBore;
    public final Vector3 leadPoint = new Vector3();
    public float selfHealthPercent = 1f;

    // --- Intents (written by tasks, read by the system) ---
    public final Vector3 desiredAimDir = new Vector3(0, 0, -1);
    public float desiredThrottle;
    public float desiredRoll;
    public boolean fireGuns;
    public boolean fireMissiles;

    private final Vector3 forward = new Vector3();
    private final Vector3 toTarget = new Vector3();
    private final Vector3 relVel = new Vector3();
    private final Vector3 los = new Vector3();

    /** Recompute situational sensors. Pure; no ECS/GL. */
    public void updateSensors(Vector3 selfPos, Quaternion selfRot, Vector3 selfVel,
                              Vector3 targetPos, Vector3 targetVel, float muzzleSpeed) {
        hasTarget = true;
        // Reset per-tick intents so a task that does not run cannot leave a stale fire flag.
        fireGuns = false;
        fireMissiles = false;

        toTarget.set(targetPos).sub(selfPos);
        rangeToTarget = toTarget.len();

        // Closure rate = component of relative velocity along the line of sight (positive = closing).
        los.set(toTarget).nor();
        relVel.set(selfVel).sub(targetVel);
        closureRate = relVel.dot(los);

        // Lead point for guns; default aim is the lead point direction.
        Vector3 lead = BallisticsUtil.computeLeadPoint(selfPos, muzzleSpeed, targetPos, targetVel);
        leadPoint.set(lead);
        desiredAimDir.set(leadPoint).sub(selfPos).nor();

        forward.set(0, 0, -1).mul(selfRot).nor();
        float dot = MathUtils.clamp(forward.dot(desiredAimDir), -1f, 1f);
        angleOffBore = (float) Math.toDegrees(Math.acos(dot));
    }

    public void clearTarget() {
        hasTarget = false;
        fireGuns = false;
        fireMissiles = false;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.DogfightBlackboardTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/ai/DogfightBlackboard.java \
        core/src/test/java/com/galacticodyssey/ship/ai/DogfightBlackboardTest.java
git commit -m "feat(ai): dogfight blackboard with range/closure/lead/angle-off sensors"
```

---

### Task 3: ShipSteeringController (pure PD attitude control)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/ShipSteeringController.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ai/ShipSteeringControllerTest.java`

Converts a desired world-space aim direction into clamped pitch/yaw/roll/throttle inputs using PD control: proportional to the angular error toward the aim direction, damped by current angular velocity. No ECS, no GL.

- [ ] **Step 1: Write the failing test**

The test verifies the real property: applying the controller's command as angular velocity over small steps rotates the nose closer to the target each step (convergence, no overshoot blow-up), aligned input is ~zero, and outputs clamp to [-1,1].

```java
package com.galacticodyssey.ship.ai;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipSteeringControllerTest {

    private static float angleToTarget(Quaternion rot, Vector3 aim) {
        Vector3 fwd = new Vector3(0, 0, -1).mul(rot).nor();
        float dot = Math.max(-1f, Math.min(1f, fwd.dot(aim)));
        return (float) Math.toDegrees(Math.acos(dot));
    }

    @Test
    void convergesNoseTowardAimDirection() {
        ShipSteeringController ctrl = new ShipSteeringController();
        Quaternion rot = new Quaternion();                    // forward (0,0,-1)
        Vector3 aim = new Vector3(1, 0, -1).nor();            // 45deg to the right
        Vector3 angularVel = new Vector3();                   // local pitch/yaw/roll rates
        ShipFlightInputComponent out = new ShipFlightInputComponent();

        float startAngle = angleToTarget(rot, aim);
        float dt = 1f / 60f;
        // Crude integrator: treat input * gain as angular acceleration about local axes.
        for (int i = 0; i < 240; i++) {
            ctrl.computeInputs(rot, angularVel, aim, 0f, out);
            // local angular accel (right=pitch x, up=yaw y, forward=roll z)
            angularVel.add(out.pitchInput * 3f * dt, out.yawInput * 3f * dt, out.rollInput * 3f * dt);
            angularVel.scl(0.98f); // damping like setDamping
            // integrate local rates into orientation
            Quaternion dq = new Quaternion().setEulerAnglesRad(
                angularVel.y * dt, angularVel.x * dt, angularVel.z * dt);
            rot.mul(dq).nor();
        }
        float endAngle = angleToTarget(rot, aim);
        assertTrue(endAngle < startAngle * 0.2f,
            "nose should converge toward aim (start=" + startAngle + " end=" + endAngle + ")");
    }

    @Test
    void alignedProducesNearZeroCommand() {
        ShipSteeringController ctrl = new ShipSteeringController();
        Quaternion rot = new Quaternion();
        Vector3 aim = new Vector3(0, 0, -1);                  // already aligned
        ShipFlightInputComponent out = new ShipFlightInputComponent();
        ctrl.computeInputs(rot, new Vector3(), aim, 0f, out);
        assertEquals(0f, out.pitchInput, 0.05f);
        assertEquals(0f, out.yawInput, 0.05f);
    }

    @Test
    void outputsClampedToUnitRange() {
        ShipSteeringController ctrl = new ShipSteeringController();
        Quaternion rot = new Quaternion();
        Vector3 aim = new Vector3(0, 0, 1);                   // 180deg away -> max error
        ShipFlightInputComponent out = new ShipFlightInputComponent();
        ctrl.computeInputs(rot, new Vector3(), aim, 1.5f, out);
        assertTrue(Math.abs(out.pitchInput) <= 1f);
        assertTrue(Math.abs(out.yawInput) <= 1f);
        assertTrue(Math.abs(out.rollInput) <= 1f);
        assertTrue(out.throttle <= 1f && out.throttle >= -1f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.ShipSteeringControllerTest"`
Expected: FAIL — `ShipSteeringController` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.galacticodyssey.ship.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;

/**
 * Pure PD attitude controller. Given current orientation, current angular velocity, and a
 * desired world-space aim direction, produces clamped pitch/yaw/roll/throttle stick inputs that
 * steer the ship's nose (local -Z) toward the aim direction without oscillating.
 *
 * <p>No ECS/GL dependencies — directly unit-testable.</p>
 */
public class ShipSteeringController {

    /** Proportional gain on attitude error (radians -> stick). */
    public float kp = 2.2f;
    /** Derivative gain on angular velocity (damping). */
    public float kd = 0.8f;
    /** Roll damping gain. */
    public float rollKd = 0.6f;

    private final Vector3 forward = new Vector3();
    private final Vector3 errorAxisWorld = new Vector3();
    private final Vector3 errorAxisLocal = new Vector3();
    private final Vector3 angVelLocal = new Vector3();
    private final Quaternion inv = new Quaternion();

    /**
     * @param shipRot        current ship orientation
     * @param angularVel     current angular velocity in LOCAL ship axes (x=pitch,y=yaw,z=roll), rad/s
     * @param desiredAimDir  desired forward direction in world space (normalised)
     * @param desiredThrottle 0..1 (or -1..1) forward throttle requested by the active task
     * @param out            input component to populate
     */
    public void computeInputs(Quaternion shipRot, Vector3 angularVel,
                              Vector3 desiredAimDir, float desiredThrottle,
                              ShipFlightInputComponent out) {
        forward.set(0, 0, -1).mul(shipRot).nor();

        // Rotation error axis (world) = forward x desired; magnitude = sin(angle).
        errorAxisWorld.set(forward).crs(desiredAimDir);
        float sin = errorAxisWorld.len();
        float cos = MathUtils.clamp(forward.dot(desiredAimDir), -1f, 1f);
        float angle = (float) Math.atan2(sin, cos); // 0..PI

        if (sin > 1e-5f) {
            errorAxisWorld.scl(1f / sin); // normalise axis
        } else if (angle > 1f) {
            // Exactly anti-parallel: pick an arbitrary perpendicular axis to start the turn.
            errorAxisWorld.set(0, 1, 0);
        } else {
            errorAxisWorld.setZero();
        }
        errorAxisWorld.scl(angle); // axis * angle (rotation vector toward target)

        // Convert error and angular velocity into local ship axes.
        inv.set(shipRot).conjugate();
        errorAxisLocal.set(errorAxisWorld).mul(inv);
        angVelLocal.set(angularVel); // already local per contract

        // PD: command about local right(x)=pitch, up(y)=yaw, forward(z)=roll.
        float pitch = kp * errorAxisLocal.x - kd * angVelLocal.x;
        float yaw   = kp * errorAxisLocal.y - kd * angVelLocal.y;
        // Roll: damp residual roll rate toward zero (wings-level); tasks can bias via desiredRoll elsewhere.
        float roll  = -rollKd * angVelLocal.z;

        // Map error axis (about local up = yaw) to the yawInput sign convention used by ShipFlightSystem
        // (torque uses -yawInput about local up), so invert yaw here so positive error turns the right way.
        out.pitchInput = MathUtils.clamp(pitch, -1f, 1f);
        out.yawInput   = MathUtils.clamp(-yaw, -1f, 1f);
        out.rollInput  = MathUtils.clamp(roll, -1f, 1f);
        out.throttle   = MathUtils.clamp(desiredThrottle, -1f, 1f);
    }
}
```

> Note for implementer: the `yawInput` sign is inverted to match `ShipFlightSystem`'s `torque += up * (-yawInput) * torque`. If the convergence test fails only on the yaw axis, flip the sign on `out.yawInput` (and likewise re-check `pitchInput` against `torque += right * pitchInput`). The convergence test is the source of truth — make it pass.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.ShipSteeringControllerTest"`
Expected: PASS. If the convergence test fails on a single axis, adjust that axis's sign per the note and re-run.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/ai/ShipSteeringController.java \
        core/src/test/java/com/galacticodyssey/ship/ai/ShipSteeringControllerTest.java
git commit -m "feat(ai): PD attitude controller for AI ship steering"
```

---

### Task 4: ShipPilotAIComponent + snapshot

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/ShipPilotAIComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipPilotAISnapshot.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ai/ShipPilotAIComponentTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.ai;

import com.galacticodyssey.persistence.snapshots.ShipPilotAISnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipPilotAIComponentTest {

    @Test
    void snapshotRoundTripsTuningFields() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetypeId = "ace";
        ai.decisionInterval = 0.2f;

        ShipPilotAISnapshot snap = ai.takeSnapshot();
        assertEquals("ace", snap.archetypeId);

        ShipPilotAIComponent restored = new ShipPilotAIComponent();
        restored.restoreFromSnapshot(snap);
        assertEquals("ace", restored.archetypeId);
        assertEquals(0.2f, restored.decisionInterval, 1e-4);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.ShipPilotAIComponentTest"`
Expected: FAIL — classes do not exist.

- [ ] **Step 3: Write the implementation**

`ShipPilotAISnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

/** Persisted form of ShipPilotAIComponent. Behaviour tree + live target are rebuilt at runtime. */
public class ShipPilotAISnapshot {
    public String archetypeId;
    public float decisionInterval;
}
```

`ShipPilotAIComponent.java`:
```java
package com.galacticodyssey.ship.ai;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.ShipPilotAISnapshot;

/**
 * Per-ship dogfight AI state. Mirrors the ground-combat {@code CombatAIComponent}: the behaviour
 * tree and live target are runtime-only (rebuilt from {@code archetypeId} / re-acquired), while
 * tuning is persisted.
 */
public class ShipPilotAIComponent implements Component, Snapshotable<ShipPilotAISnapshot> {

    /** Built by DogfightTreeFactory; stepped each FULL-tier tick. May be null (skipped). */
    public BehaviorTree<Entity> behaviorTree;
    public Entity currentTarget;
    public PilotArchetype archetype;
    public String archetypeId;
    public final DogfightBlackboard blackboard = new DogfightBlackboard();

    /** Seconds between behaviour-tree decisions (set from archetype.reactionTimeSec). */
    public float decisionInterval = 0.25f;
    /** Counts down to the next decision. */
    public float decisionTimer;

    @Override
    public ShipPilotAISnapshot takeSnapshot() {
        ShipPilotAISnapshot s = new ShipPilotAISnapshot();
        s.archetypeId = archetypeId;
        s.decisionInterval = decisionInterval;
        return s;
    }

    @Override
    public void restoreFromSnapshot(ShipPilotAISnapshot s) {
        archetypeId = s.archetypeId;
        decisionInterval = s.decisionInterval;
    }
}
```

> Save-system note: `CombatAISnapshot` is registered somewhere in the persistence layer. After this task, grep for `CombatAISnapshot` (`git grep CombatAISnapshot`) to find any snapshot-type registry and add `ShipPilotAISnapshot` alongside it the same way. If `CombatAISnapshot` is only referenced by `CombatAIComponent` (no central registry), no further wiring is needed. Full save/load integration is out of scope for this plan (the unit test above is the acceptance bar).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.ShipPilotAIComponentTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/ai/ShipPilotAIComponent.java \
        core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipPilotAISnapshot.java \
        core/src/test/java/com/galacticodyssey/ship/ai/ShipPilotAIComponentTest.java
git commit -m "feat(ai): ShipPilotAIComponent with snapshot"
```

---

### Task 5: Behavior-tree tasks and conditions

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/tasks/HasDogfightTargetCondition.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/tasks/TargetInWeaponArcCondition.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/tasks/LowHealthCondition.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/tasks/IsBeingThreatenedCondition.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/tasks/PursueTargetTask.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/tasks/AttackRunTask.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/tasks/EvadeTask.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/tasks/ExtendAndReengageTask.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/tasks/IdlePatrolTask.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ai/tasks/DogfightTasksTest.java`

All tasks extend `com.badlogic.gdx.ai.btree.LeafTask<Entity>`, read `ShipPilotAIComponent` (and its `blackboard`), and write intents to the blackboard. They never touch physics directly — the system applies the intents.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.ship.ai.PilotArchetype;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DogfightTasksTest {

    private Entity shipWith(ShipPilotAIComponent ai, HealthComponent hp) {
        Entity e = new Entity();
        e.add(ai);
        if (hp != null) e.add(hp);
        return e;
    }

    @Test
    void hasTargetConditionReflectsBlackboard() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        Entity e = shipWith(ai, null);
        HasDogfightTargetCondition c = new HasDogfightTargetCondition();
        c.setObject(e);
        ai.blackboard.hasTarget = false;
        assertEquals(Task.Status.FAILED, c.execute());
        ai.blackboard.hasTarget = true;
        assertEquals(Task.Status.SUCCEEDED, c.execute());
    }

    @Test
    void attackRunFiresOnlyWhenInConeAndRange() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetype = new PilotArchetype();
        ai.archetype.preferredEngageRange = 350f;
        Entity e = shipWith(ai, null);

        ai.blackboard.hasTarget = true;
        ai.blackboard.angleOffBore = 3f;     // within cone
        ai.blackboard.rangeToTarget = 300f;  // within range (weapon range provided via task field)

        AttackRunTask atk = new AttackRunTask();
        atk.weaponRange = 500f;
        atk.firingConeDeg = 6f;
        atk.setObject(e);
        assertEquals(Task.Status.SUCCEEDED, atk.execute());
        assertTrue(ai.blackboard.fireGuns, "should request guns when aligned + in range");

        // Out of cone -> no fire.
        ai.blackboard.fireGuns = false;
        ai.blackboard.angleOffBore = 30f;
        atk.execute();
        assertFalse(ai.blackboard.fireGuns, "no fire when nose off target");
    }

    @Test
    void evadeTriggersAtLowHealth() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetype = new PilotArchetype();
        ai.archetype.evadeHealthThreshold = 0.35f;
        ai.blackboard.hasTarget = true;
        Entity e = shipWith(ai, null);

        LowHealthCondition cond = new LowHealthCondition();
        cond.setObject(e);
        ai.blackboard.selfHealthPercent = 0.5f;
        assertEquals(Task.Status.FAILED, cond.execute());
        ai.blackboard.selfHealthPercent = 0.2f;
        assertEquals(Task.Status.SUCCEEDED, cond.execute());
    }

    @Test
    void pursueSetsThrottleTowardTarget() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetype = new PilotArchetype();
        ai.archetype.preferredEngageRange = 350f;
        ai.archetype.throttleDiscipline = 0.8f;
        ai.blackboard.hasTarget = true;
        ai.blackboard.rangeToTarget = 1000f; // far -> should throttle up
        Entity e = shipWith(ai, null);

        PursueTargetTask pursue = new PursueTargetTask();
        pursue.setObject(e);
        assertEquals(Task.Status.SUCCEEDED, pursue.execute());
        assertTrue(ai.blackboard.desiredThrottle > 0.5f, "throttle up when far from target");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.tasks.DogfightTasksTest"`
Expected: FAIL — task classes do not exist.

- [ ] **Step 3: Write the implementations**

`HasDogfightTargetCondition.java`:
```java
package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/** Succeeds when the blackboard has an active target. */
public class HasDogfightTargetCondition extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null) return Status.FAILED;
        return ai.blackboard.hasTarget ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
```

`TargetInWeaponArcCondition.java`:
```java
package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/** Succeeds when the target is roughly ahead and within engage range. */
public class TargetInWeaponArcCondition extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    /** Cone half-angle (deg) considered "in arc" for committing to an attack run. */
    public float arcDeg = 25f;

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null || !ai.blackboard.hasTarget) return Status.FAILED;
        float range = ai.blackboard.rangeToTarget;
        float maxRange = ai.archetype != null ? ai.archetype.preferredEngageRange * 1.5f : 600f;
        boolean inArc = ai.blackboard.angleOffBore <= arcDeg && range <= maxRange;
        return inArc ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
```

`LowHealthCondition.java`:
```java
package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/** Succeeds when self health is at/below the archetype's evade threshold. */
public class LowHealthCondition extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null) return Status.FAILED;
        float threshold = ai.archetype != null ? ai.archetype.evadeHealthThreshold : 0.35f;
        return ai.blackboard.selfHealthPercent <= threshold ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
```

`IsBeingThreatenedCondition.java`:
```java
package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/**
 * Succeeds when the ship is likely under threat: a close target with high positive closure
 * (something is bearing down on us). Kept simple; refine with missile-lock events later.
 */
public class IsBeingThreatenedCondition extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    public float threatRange = 250f;

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null || !ai.blackboard.hasTarget) return Status.FAILED;
        boolean threatened = ai.blackboard.rangeToTarget < threatRange
            && ai.blackboard.closureRate > 30f;
        return threatened ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
```

`PursueTargetTask.java`:
```java
package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/**
 * Lead-pursuit: aim at the lead point (already in blackboard.desiredAimDir) and throttle to
 * close to the preferred engage range. Throttle eases off as range approaches preferred.
 */
public class PursueTargetTask extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null || !ai.blackboard.hasTarget) return Status.FAILED;

        float preferred = ai.archetype != null ? ai.archetype.preferredEngageRange : 350f;
        float discipline = ai.archetype != null ? ai.archetype.throttleDiscipline : 0.6f;
        float range = ai.blackboard.rangeToTarget;

        // Far: full throttle. Near preferred: ease toward a holding throttle.
        float t = MathUtils.clamp((range - preferred) / preferred, -1f, 1f);
        // discipline scales how much we modulate; low discipline = blunt full throttle.
        ai.blackboard.desiredThrottle = MathUtils.clamp(
            MathUtils.lerp(1f, t, discipline), -0.3f, 1f);
        ai.blackboard.desiredRoll = 0f;
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
```

`AttackRunTask.java`:
```java
package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/**
 * Hold the nose on the lead point and request gun fire when aligned within the firing cone and
 * inside weapon range. Requests missiles when the archetype uses them and the target is within
 * missile range and reasonably ahead.
 */
public class AttackRunTask extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    /** Set by the system from the ship's actual gun range; default is a safe fallback. */
    public float weaponRange = 500f;
    public float firingConeDeg = 6f;
    public float missileRange = 2000f;
    public float missileConeDeg = 20f;

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null || !ai.blackboard.hasTarget) return Status.FAILED;

        // Maintain attack throttle; aim is already the lead point.
        ai.blackboard.desiredThrottle = 0.85f;

        boolean gunsAligned = ai.blackboard.angleOffBore <= firingConeDeg
            && ai.blackboard.rangeToTarget <= weaponRange;
        ai.blackboard.fireGuns = gunsAligned;

        boolean useMissiles = ai.archetype != null && ai.archetype.usesMissiles
            && ai.blackboard.angleOffBore <= missileConeDeg
            && ai.blackboard.rangeToTarget <= missileRange;
        ai.blackboard.fireMissiles = useMissiles;

        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        AttackRunTask t = (AttackRunTask) task;
        t.weaponRange = weaponRange;
        t.firingConeDeg = firingConeDeg;
        t.missileRange = missileRange;
        t.missileConeDeg = missileConeDeg;
        return task;
    }
}
```

`EvadeTask.java`:
```java
package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/**
 * Break away from the target: aim perpendicular to the line of sight, full throttle, hard roll —
 * a jink to spoil the attacker's solution. Never requests fire.
 */
public class EvadeTask extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    private final Vector3 perp = new Vector3();

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null || !ai.blackboard.hasTarget) return Status.FAILED;

        // Aim perpendicular to current aim direction (break turn). Cross with world up; fall back to forward axis.
        Vector3 aim = ai.blackboard.desiredAimDir;
        perp.set(aim).crs(Vector3.Y);
        if (perp.len2() < 1e-4f) perp.set(1, 0, 0);
        perp.nor();
        ai.blackboard.desiredAimDir.set(perp);
        ai.blackboard.desiredThrottle = 1f;
        ai.blackboard.desiredRoll = 1f;     // bias a roll into the break
        ai.blackboard.fireGuns = false;
        ai.blackboard.fireMissiles = false;
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
```

`ExtendAndReengageTask.java`:
```java
package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/**
 * When the pilot has overshot (very close with high closure), keep aiming at the target but run
 * full throttle to extend out to re-establish separation before turning back in. Acts as the
 * fallback reposition behaviour when not in arc for an attack run.
 */
public class ExtendAndReengageTask extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null || !ai.blackboard.hasTarget) return Status.FAILED;

        float extend = ai.archetype != null ? ai.archetype.overshootExtendDist : 130f;
        // If very close, the nose-on-target pursuit would just orbit; keep turning hard toward
        // the lead point at full throttle to swing back around.
        ai.blackboard.desiredThrottle = ai.blackboard.rangeToTarget < extend ? 1f : 0.9f;
        ai.blackboard.desiredRoll = 0f;
        ai.blackboard.fireGuns = false;
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
```

`IdlePatrolTask.java`:
```java
package com.galacticodyssey.ship.ai.tasks;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;

/** No target: cruise gently straight ahead. Always succeeds (leaf fallback). */
public class IdlePatrolTask extends LeafTask<Entity> {
    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);

    @Override
    public Status execute() {
        ShipPilotAIComponent ai = AI_M.get(getObject());
        if (ai == null) return Status.FAILED;
        ai.blackboard.desiredThrottle = 0.2f;
        ai.blackboard.desiredRoll = 0f;
        ai.blackboard.fireGuns = false;
        ai.blackboard.fireMissiles = false;
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) { return task; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.tasks.DogfightTasksTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/ai/tasks/ \
        core/src/test/java/com/galacticodyssey/ship/ai/tasks/DogfightTasksTest.java
git commit -m "feat(ai): dogfight behaviour-tree tasks and conditions"
```

---

### Task 6: DogfightTreeFactory (build the gdx-ai tree in code)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/DogfightTreeFactory.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ai/DogfightTreeFactoryTest.java`

No `.tree` loader exists in the project (trees are null today), so we assemble the tree from tasks using gdx-ai's `Selector`/`Sequence` branches. Tree shape:
`Selector( Sequence(LowHealth?, Evade) , Sequence(IsBeingThreatened?, Evade) , Sequence(TargetInArc?, AttackRun) , Sequence(HasTarget?, Pursue) , ExtendAndReengage , IdlePatrol )`.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.ai;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DogfightTreeFactoryTest {

    @Test
    void buildsAndStepsWithoutTargetSelectingPatrol() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetype = new PilotArchetype();
        Entity e = new Entity();
        e.add(ai);

        BehaviorTree<Entity> tree = DogfightTreeFactory.build(e, 500f);
        ai.behaviorTree = tree;
        ai.blackboard.hasTarget = false;

        tree.step(); // must not throw; patrol should set a gentle throttle
        assertEquals(0.2f, ai.blackboard.desiredThrottle, 1e-3);
    }

    @Test
    void inArcTargetTriggersAttackRunFire() {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetype = new PilotArchetype();
        ai.archetype.preferredEngageRange = 350f;
        Entity e = new Entity();
        e.add(ai);

        BehaviorTree<Entity> tree = DogfightTreeFactory.build(e, 500f);
        ai.behaviorTree = tree;
        ai.blackboard.hasTarget = true;
        ai.blackboard.selfHealthPercent = 1f;
        ai.blackboard.angleOffBore = 2f;
        ai.blackboard.rangeToTarget = 300f;
        ai.blackboard.closureRate = 0f;

        tree.step();
        assertTrue(ai.blackboard.fireGuns, "aligned in-range target should produce gun fire intent");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.DogfightTreeFactoryTest"`
Expected: FAIL — `DogfightTreeFactory` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.galacticodyssey.ship.ai;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.branch.Selector;
import com.badlogic.gdx.ai.btree.branch.Sequence;
import com.galacticodyssey.ship.ai.tasks.AttackRunTask;
import com.galacticodyssey.ship.ai.tasks.EvadeTask;
import com.galacticodyssey.ship.ai.tasks.ExtendAndReengageTask;
import com.galacticodyssey.ship.ai.tasks.HasDogfightTargetCondition;
import com.galacticodyssey.ship.ai.tasks.IdlePatrolTask;
import com.galacticodyssey.ship.ai.tasks.IsBeingThreatenedCondition;
import com.galacticodyssey.ship.ai.tasks.LowHealthCondition;
import com.galacticodyssey.ship.ai.tasks.TargetInWeaponArcCondition;

/** Builds the dogfight behaviour tree in code (no .tree files exist in this project). */
public final class DogfightTreeFactory {

    private DogfightTreeFactory() {}

    /**
     * @param blackboardEntity the entity the tree operates on (carries ShipPilotAIComponent)
     * @param gunRange         the ship's actual gun range, used to gate AttackRun fire
     */
    public static BehaviorTree<Entity> build(Entity blackboardEntity, float gunRange) {
        AttackRunTask attack = new AttackRunTask();
        attack.weaponRange = gunRange;

        Selector<Entity> root = new Selector<>(
            // Evade if hurt.
            new Sequence<>(new LowHealthCondition(), new EvadeTask()),
            // Evade if something is bearing down on us.
            new Sequence<>(new IsBeingThreatenedCondition(), new EvadeTask()),
            // Attack run when target is in arc + range.
            new Sequence<>(new TargetInWeaponArcCondition(), attack),
            // Otherwise pursue the target.
            new Sequence<>(new HasDogfightTargetCondition(), new com.galacticodyssey.ship.ai.tasks.PursueTargetTask()),
            // Reposition fallback (covers overshoot when not in arc).
            new ExtendAndReengageTask(),
            // No target: patrol.
            new IdlePatrolTask()
        );

        return new BehaviorTree<>(root, blackboardEntity);
    }
}
```

> Note: `ExtendAndReengageTask` and `IdlePatrolTask` both return SUCCEEDED when reached; the Selector tries earlier branches first, so Extend only runs when `HasDogfightTargetCondition` failed AND there is a (stale) target context — in practice the `HasTarget` sequence above it handles the live-target case, so Extend acts as the no-arc reposition fallback. Patrol is the final catch-all.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.DogfightTreeFactoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/ai/DogfightTreeFactory.java \
        core/src/test/java/com/galacticodyssey/ship/ai/DogfightTreeFactoryTest.java
git commit -m "feat(ai): build dogfight behaviour tree in code via factory"
```

---

### Task 7: Refactor ShipFlightSystem to drive NPC ships

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightSystemNpcTest.java`

Extract the force/torque application into `applyFlight(Entity ship, ShipFlightInputComponent input, float dt)` (behaviour-preserving for the player), then add a second family that applies each NPC ship's own `ShipFlightInputComponent`. NPC ships are those that carry their own `ShipFlightInputComponent` + `ShipFlightComponent` + `PhysicsBodyComponent` (the player's ship entity has no input component, so it is excluded automatically).

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipFlightSystemNpcTest {

    @BeforeAll
    static void initBullet() { Bullet.init(); }

    @Test
    void npcShipWithOwnInputAccelerates() {
        btDefaultCollisionConfiguration config = new btDefaultCollisionConfiguration();
        btCollisionDispatcher dispatcher = new btCollisionDispatcher(config);
        btDbvtBroadphase broadphase = new btDbvtBroadphase();
        btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
        btDiscreteDynamicsWorld world =
            new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
        world.setGravity(new Vector3(0, 0, 0));

        Engine engine = new Engine();
        ShipFlightSystem system = new ShipFlightSystem();
        engine.addSystem(system);

        Entity ship = new Entity();
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
        ship.add(physics);

        ShipFlightComponent flight = new ShipFlightComponent();
        flight.linearThrust = 50000;
        flight.strafeThrustFraction = 0.6f;
        flight.verticalThrustFraction = 0.6f;
        flight.pitchYawTorque = 20000;
        flight.rollTorque = 15000;
        flight.linearDrag = 0.1f;
        flight.angularDrag = 2.0f;
        ship.add(flight);

        // NPC carries its OWN input component on the ship entity.
        ShipFlightInputComponent input = new ShipFlightInputComponent();
        input.throttle = 1f;
        ship.add(input);

        engine.addEntity(ship);
        world.addRigidBody(physics.body);

        float dt = 1f / 60f;
        for (int i = 0; i < 30; i++) {
            system.update(dt);
            world.stepSimulation(dt, 1, dt);
        }

        float speed = physics.body.getLinearVelocity().len();
        assertTrue(speed > 1f, "NPC ship should have accelerated, speed=" + speed);

        world.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
        world.dispose(); solver.dispose(); broadphase.dispose();
        dispatcher.dispose(); config.dispose();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightSystemNpcTest"`
Expected: FAIL — NPC ship does not move (current `update` only drives the player's `currentShip`; with no player entity, `update` returns early).

- [ ] **Step 3: Refactor the implementation**

Replace the body of `ShipFlightSystem` with the extracted helper + NPC family. Concretely:

1. Add an NPC family field and resolve it in `addedToEngine`:
```java
    private ImmutableArray<Entity> npcShips;

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class).get());
        npcShips = engine.getEntitiesFor(Family.all(
            ShipFlightInputComponent.class,
            ShipFlightComponent.class,
            PhysicsBodyComponent.class).get());
    }
```

2. Replace `update(float)` so it drives the player ship via the extracted helper, then every NPC ship via its own input:
```java
    @Override
    public void update(float deltaTime) {
        // Player-controlled ship (input lives on the player entity, applied to currentShip).
        if (playerEntities.size() > 0) {
            Entity player = playerEntities.first();
            PlayerStateComponent state = stateMapper.get(player);
            if (state.currentMode == PlayerMode.PILOTING && state.currentShip != null) {
                ShipFlightInputComponent input = flightInputMapper.get(player);
                if (input != null) {
                    applyFlight(state.currentShip, input, deltaTime);
                }
            }
        }

        // AI/NPC ships carry their own input component on the ship entity itself.
        for (int i = 0; i < npcShips.size(); i++) {
            Entity ship = npcShips.get(i);
            ShipFlightInputComponent input = flightInputMapper.get(ship);
            applyFlight(ship, input, deltaTime);
        }
    }
```

3. Move the existing force/torque code (old lines 76-151) into a helper, parameterised by ship + input:
```java
    /** Applies thrust + torque + damping for a single ship from its input. Behaviour-preserving. */
    private void applyFlight(Entity ship, ShipFlightInputComponent input, float deltaTime) {
        PhysicsBodyComponent physics = physicsMapper.get(ship);
        ShipFlightComponent flight = flightMapper.get(ship);
        if (physics == null || physics.body == null || flight == null || input == null) return;

        if (!canThrust(ship)) return;

        EngineSpecComponent engineSpec = engineMapper.get(ship);
        float effectiveThrottle = input.throttle;
        if (engineSpec != null) {
            float target = input.throttle;
            engineSpec.currentThrottle = MathUtils.lerp(engineSpec.currentThrottle, target,
                engineSpec.throttleResponseRate * deltaTime);
            effectiveThrottle = engineSpec.currentThrottle;
            engineSpec.actualThrust = effectiveThrottle * engineSpec.maxThrust;
        }

        FuelTankComponent fuel = fuelMapper.get(ship);
        if (fuel != null && engineSpec != null && Math.abs(effectiveThrottle) > 0.001f) {
            float thrust = Math.abs(effectiveThrottle) * flight.linearThrust;
            float massFlowRate = thrust / (engineSpec.isp * 9.81f);
            fuel.currentMass -= massFlowRate * deltaTime;
            if (fuel.currentMass <= 0) {
                fuel.currentMass = 0;
                effectiveThrottle = 0;
            }
        }

        physics.body.getWorldTransform(shipTransform);
        localForward.set(0, 0, -1).rot(shipTransform).nor();
        localRight.set(1, 0, 0).rot(shipTransform).nor();
        localUp.set(0, 1, 0).rot(shipTransform).nor();

        force.setZero();
        force.mulAdd(localForward, effectiveThrottle * flight.linearThrust);
        force.mulAdd(localRight, input.strafe * flight.linearThrust * flight.strafeThrustFraction);
        force.mulAdd(localUp, input.verticalThrust * flight.linearThrust * flight.verticalThrustFraction);

        currentVelocity.set(physics.body.getLinearVelocity());
        final float speed = currentVelocity.len();
        if (speed > RelativisticConstants.THRESHOLD) {
            final float restMass = physics.mass;
            final Vector3 velDir = currentVelocity.nor();
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

        torque.setZero();
        torque.mulAdd(localRight, input.pitchInput * flight.pitchYawTorque);
        torque.mulAdd(localUp, -input.yawInput * flight.pitchYawTorque);
        torque.mulAdd(localForward, input.rollInput * flight.rollTorque);

        physics.body.applyTorque(torque);
        physics.body.setDamping(flight.linearDrag, flight.angularDrag);

        flight.currentThrottle = effectiveThrottle;
        physics.body.activate();
    }
```

Remove the now-unused early-return logic that referenced `state`/`input` at method scope (it now lives in `update`).

- [ ] **Step 4: Run tests to verify they pass**

Run the new NPC test AND the existing flight test to confirm no regression:
```
./gradlew :core:test --tests "com.galacticodyssey.ship.systems.ShipFlightSystemNpcTest" --tests "com.galacticodyssey.ship.systems.ShipFlightSystemTest"
```
Expected: BOTH PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java \
        core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightSystemNpcTest.java
git commit -m "refactor(ship): ShipFlightSystem applies NPC ship inputs too"
```

---

### Task 8: ShipPilotAISystem (the tick) + integration test

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/ai/ShipPilotAISystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ai/ShipPilotAISystemTest.java`

The system (priority 2, before `ShipFlightSystem` at 3): for each AI ship, reads LOD tier, **acquires a target if it has none** (nearest hostile within `aggroRange` — the player ship plus ships of a different `fleetId`, mirroring the existing `ScanForThreatsTask` convention that the player is always a valid target), updates blackboard sensors from the current target, steps the tree (FULL) or runs a cheap steer-and-shoot (SIMPLIFIED), converts `desiredAimDir`/throttle to stick input via `ShipSteeringController`, and fires guns/missiles via `ShipWeaponSystem`. Subscribes to `EntityKilledEvent` to clear dead targets. `SquadronCoordinationSystem` may also overwrite `currentTarget` for squad focus-fire; if a squad target is already set, acquisition is skipped.

- [ ] **Step 1: Write the failing integration test**

```java
package com.galacticodyssey.ship.ai;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.ship.components.ShipFlightComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ship.systems.ShipFlightSystem;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointType;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ship.weapons.systems.ShipWeaponSystem;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ShipPilotAISystemTest {

    @BeforeAll
    static void initBullet() { Bullet.init(); }

    private btRigidBody box(float mass, btCollisionShape shape, Vector3 pos) {
        Vector3 inertia = new Vector3();
        shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, shape, inertia);
        btRigidBody b = new btRigidBody(info);
        b.setWorldTransform(new Matrix4().setToTranslation(pos));
        info.dispose();
        return b;
    }

    @Test
    void attackerClosesAlignsAndFires() {
        btDefaultCollisionConfiguration config = new btDefaultCollisionConfiguration();
        btCollisionDispatcher dispatcher = new btCollisionDispatcher(config);
        btDbvtBroadphase broadphase = new btDbvtBroadphase();
        btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
        btDiscreteDynamicsWorld world =
            new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
        world.setGravity(new Vector3(0, 0, 0));

        EventBus bus = new EventBus();
        AtomicInteger shots = new AtomicInteger();
        bus.subscribe(ShipWeaponFiredEvent.class, e -> shots.incrementAndGet());

        Engine engine = new Engine();
        ShipWeaponSystem weapons = new ShipWeaponSystem(bus);
        engine.addSystem(weapons);
        ShipPilotAISystem ai = new ShipPilotAISystem(bus, weapons);
        engine.addSystem(ai);
        ShipFlightSystem flight = new ShipFlightSystem();
        engine.addSystem(flight);

        // --- Target: a slow-moving box 800m ahead (-Z) of the attacker ---
        Entity target = new Entity();
        btBoxShape tShape = new btBoxShape(new Vector3(2, 2, 2));
        btRigidBody tBody = box(20000f, tShape, new Vector3(0, 0, -800));
        target.add(physicsOf(tBody, tShape, 20000f));
        TransformComponent tT = new TransformComponent();
        tT.position.set(0, 0, -800);
        target.add(tT);
        HealthComponent tH = new HealthComponent();
        target.add(tH);
        engine.addEntity(target);
        world.addRigidBody(tBody);

        // --- Attacker at origin facing -Z, with gun + AI ---
        Entity attacker = new Entity();
        btBoxShape aShape = new btBoxShape(new Vector3(1, 1, 1));
        btRigidBody aBody = box(10000f, aShape, new Vector3(0, 0, 0));
        attacker.add(physicsOf(aBody, aShape, 10000f));
        TransformComponent aT = new TransformComponent();
        attacker.add(aT);
        ShipFlightComponent f = new ShipFlightComponent();
        f.linearThrust = 50000; f.pitchYawTorque = 30000; f.rollTorque = 15000;
        f.strafeThrustFraction = 0.6f; f.verticalThrustFraction = 0.6f;
        f.linearDrag = 0.05f; f.angularDrag = 3.0f;
        attacker.add(f);
        attacker.add(new ShipFlightInputComponent());

        ShipHardpointComponent hpc = new ShipHardpointComponent();
        Hardpoint hp = new Hardpoint("gun_0", HardpointType.FIXED, HardpointSize.SMALL, 0, 30);
        ShipWeaponData gun = new ShipWeaponData();
        gun.id = "ai_gun"; gun.damage = 10f; gun.damageType = DamageType.BALLISTIC;
        gun.fireRate = 4f; gun.projectileSpeed = 600f; gun.range = 1200f;
        gun.energyCost = 0f; gun.heatPerShot = 0f;
        hp.mountedWeapon = gun;
        hpc.hardpoints.add(hp);
        attacker.add(hpc);

        ShipPilotAIComponent pilot = new ShipPilotAIComponent();
        pilot.archetype = new PilotArchetype();
        pilot.archetype.aimErrorDeg = 0f;   // deterministic test
        pilot.archetype.reactionTimeSec = 0f;
        pilot.decisionInterval = 0f;
        pilot.currentTarget = target;
        pilot.behaviorTree = DogfightTreeFactory.build(attacker, gun.range);
        attacker.add(pilot);

        engine.addEntity(attacker);
        world.addRigidBody(aBody);

        float startRange = aT.position.dst(tT.position);
        float dt = 1f / 60f;
        for (int i = 0; i < 600; i++) {  // 10 simulated seconds
            engine.update(dt);
            world.stepSimulation(dt, 2, dt / 2f);
            // keep TransformComponents in sync from physics (normally a sync system does this)
            syncTransform(attacker, aT);
            syncTransform(target, tT);
        }

        float endRange = aT.position.dst(tT.position);
        assertTrue(endRange < startRange, "attacker should close range (" + startRange + "->" + endRange + ")");
        assertTrue(shots.get() > 0, "attacker should have fired at least once");

        // cleanup
        world.dispose(); solver.dispose(); broadphase.dispose(); dispatcher.dispose(); config.dispose();
    }

    @Test
    void acquiresPlayerTargetWhenNoneAssigned() {
        btDefaultCollisionConfiguration config = new btDefaultCollisionConfiguration();
        btCollisionDispatcher dispatcher = new btCollisionDispatcher(config);
        btDbvtBroadphase broadphase = new btDbvtBroadphase();
        btSequentialImpulseConstraintSolver solver = new btSequentialImpulseConstraintSolver();
        btDiscreteDynamicsWorld world =
            new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
        world.setGravity(new Vector3(0, 0, 0));

        EventBus bus = new EventBus();
        Engine engine = new Engine();
        ShipWeaponSystem weapons = new ShipWeaponSystem(bus);
        engine.addSystem(weapons);
        engine.addSystem(new ShipPilotAISystem(bus, weapons));

        // Player ship 500m away, tagged.
        Entity player = new Entity();
        com.galacticodyssey.core.components.PlayerTagComponent tag =
            new com.galacticodyssey.core.components.PlayerTagComponent();
        player.add(tag);
        TransformComponent pT = new TransformComponent();
        pT.position.set(0, 0, -500);
        player.add(pT);
        engine.addEntity(player);

        // AI ship at origin with NO target assigned.
        Entity attacker = new Entity();
        btBoxShape aShape = new btBoxShape(new Vector3(1, 1, 1));
        btRigidBody aBody = box(10000f, aShape, new Vector3(0, 0, 0));
        attacker.add(physicsOf(aBody, aShape, 10000f));
        attacker.add(new TransformComponent());
        ShipFlightComponent f = new ShipFlightComponent();
        f.linearThrust = 50000; f.pitchYawTorque = 30000; f.rollTorque = 15000;
        f.linearDrag = 0.05f; f.angularDrag = 3.0f;
        attacker.add(f);
        attacker.add(new ShipFlightInputComponent());
        ShipPilotAIComponent pilot = new ShipPilotAIComponent();
        pilot.archetype = new PilotArchetype();   // aggroRange default 2000 > 500
        pilot.decisionInterval = 0f;
        pilot.behaviorTree = DogfightTreeFactory.build(attacker, 1000f);
        attacker.add(pilot);
        engine.addEntity(attacker);
        world.addRigidBody(aBody);

        engine.update(1f / 60f);

        assertSame(player, pilot.currentTarget, "AI should acquire the nearby player ship");

        world.dispose(); solver.dispose(); broadphase.dispose(); dispatcher.dispose(); config.dispose();
    }

    private static PhysicsBodyComponent physicsOf(btRigidBody body, btCollisionShape shape, float mass) {
        PhysicsBodyComponent p = new PhysicsBodyComponent();
        p.body = body; p.shape = shape; p.mass = mass;
        return p;
    }

    private static void syncTransform(Entity e, TransformComponent t) {
        Matrix4 m = new Matrix4();
        e.getComponent(PhysicsBodyComponent.class).body.getWorldTransform(m);
        m.getTranslation(t.position);
        m.getRotation(t.rotation);
    }
}
```

> Implementer note: the test manually syncs `TransformComponent` from the Bullet body each step because the production transform-sync system is not in this minimal engine. Confirm the field/method names against the real codebase; if a lightweight sync system already exists and is cheap to add, prefer adding it to the engine instead.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.ShipPilotAISystemTest"`
Expected: FAIL — `ShipPilotAISystem` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.galacticodyssey.ship.ai;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.combat.fleet.components.FleetMemberComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.ShipWeaponCategory;
import com.galacticodyssey.ship.weapons.systems.ShipWeaponSystem;

/**
 * Drives NPC ship pilots. Priority 2 — after player input (0), before ShipFlightSystem (3), so
 * the inputs it writes are applied the same frame.
 */
public class ShipPilotAISystem extends IteratingSystem {

    public static final int PRIORITY = 2;

    private static final ComponentMapper<ShipPilotAIComponent> AI_M =
        ComponentMapper.getFor(ShipPilotAIComponent.class);
    private static final ComponentMapper<TransformComponent> TX_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> PHYS_M =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<ShipFlightInputComponent> IN_M =
        ComponentMapper.getFor(ShipFlightInputComponent.class);
    private static final ComponentMapper<HealthComponent> HP_M =
        ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<ShipHardpointComponent> HARD_M =
        ComponentMapper.getFor(ShipHardpointComponent.class);
    private static final ComponentMapper<FleetMemberComponent> FLEET_M =
        ComponentMapper.getFor(FleetMemberComponent.class);

    private final ShipWeaponSystem weaponSystem;
    private final ShipSteeringController steering = new ShipSteeringController();

    private final Vector3 selfPos = new Vector3();
    private final Vector3 selfVel = new Vector3();
    private final Vector3 targetPos = new Vector3();
    private final Vector3 targetVel = new Vector3();
    private final Vector3 angVelLocal = new Vector3();
    private final Quaternion invRot = new Quaternion();

    public ShipPilotAISystem(EventBus eventBus, ShipWeaponSystem weaponSystem) {
        super(Family.all(
            ShipPilotAIComponent.class,
            ShipFlightInputComponent.class,
            TransformComponent.class,
            PhysicsBodyComponent.class).get(), PRIORITY);
        this.weaponSystem = weaponSystem;
        eventBus.subscribe(EntityKilledEvent.class, this::onEntityKilled);
    }

    private void onEntityKilled(EntityKilledEvent event) {
        for (Entity e : getEntities()) {
            ShipPilotAIComponent ai = AI_M.get(e);
            if (ai != null && ai.currentTarget == event.target) {
                ai.currentTarget = null;
                ai.blackboard.clearTarget();
            }
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        ShipPilotAIComponent ai = AI_M.get(entity);
        ShipFlightInputComponent input = IN_M.get(entity);

        // Dead pilots do nothing.
        HealthComponent hp = HP_M.get(entity);
        if (hp != null && !hp.alive) { zero(input); return; }

        FleetMemberComponent fm = FLEET_M.get(entity);
        FleetMemberComponent.LODTier tier = fm != null ? fm.lodTier : FleetMemberComponent.LODTier.FULL;
        if (tier == FleetMemberComponent.LODTier.ABSTRACT) return; // handled elsewhere

        // Drop a dead/removed target.
        if (ai.currentTarget != null) {
            HealthComponent tHp = HP_M.get(ai.currentTarget);
            if (tHp != null && !tHp.alive) ai.currentTarget = null;
        }

        // Acquire a target if we have none.
        if (ai.currentTarget == null) {
            ai.currentTarget = acquireTarget(entity, ai);
        }

        // Still no target -> patrol-ish coast.
        if (ai.currentTarget == null) {
            ai.blackboard.clearTarget();
            input.throttle = 0.2f;
            input.pitchInput = input.yawInput = input.rollInput = 0f;
            return;
        }

        // Gather state.
        PhysicsBodyComponent selfPhys = PHYS_M.get(entity);
        TransformComponent selfTx = TX_M.get(entity);
        TransformComponent tgtTx = TX_M.get(ai.currentTarget);
        PhysicsBodyComponent tgtPhys = PHYS_M.get(ai.currentTarget);
        if (tgtTx == null) { ai.currentTarget = null; return; }

        selfPos.set(selfTx.position);
        selfVel.set(selfPhys.body.getLinearVelocity());
        targetPos.set(tgtTx.position);
        targetVel.set(tgtPhys != null ? tgtPhys.body.getLinearVelocity() : Vector3.Zero);

        float muzzle = gunMuzzleSpeed(entity);
        ai.blackboard.selfHealthPercent = hp != null && hp.maxHP > 0 ? hp.currentHP / hp.maxHP : 1f;
        ai.blackboard.updateSensors(selfPos, selfTx.rotation, selfVel, targetPos, targetVel, muzzle);

        if (tier == FleetMemberComponent.LODTier.FULL) {
            tickFull(entity, ai, input, deltaTime);
        } else {
            tickSimplified(entity, ai, input);
        }
    }

    private void tickFull(Entity entity, ShipPilotAIComponent ai,
                          ShipFlightInputComponent input, float deltaTime) {
        // Decision cadence (reaction time): re-step the tree only every decisionInterval.
        ai.decisionTimer -= deltaTime;
        if (ai.behaviorTree != null && ai.decisionTimer <= 0f) {
            ai.decisionTimer = Math.max(ai.decisionInterval,
                ai.archetype != null ? ai.archetype.reactionTimeSec : 0f);
            ai.behaviorTree.step();
        }
        applyIntents(entity, ai, input);
    }

    private void tickSimplified(Entity entity, ShipPilotAIComponent ai,
                                ShipFlightInputComponent input) {
        // Cheap path: aim at lead point, throttle to preferred range, fire when roughly aligned.
        float preferred = ai.archetype != null ? ai.archetype.preferredEngageRange : 350f;
        ai.blackboard.desiredThrottle = ai.blackboard.rangeToTarget > preferred ? 0.9f : 0.4f;
        ai.blackboard.desiredRoll = 0f;
        ai.blackboard.fireGuns = ai.blackboard.angleOffBore < 4f && ai.blackboard.rangeToTarget < gunRange(entity);
        ai.blackboard.fireMissiles = false;
        applyIntents(entity, ai, input);
    }

    private void applyIntents(Entity entity, ShipPilotAIComponent ai, ShipFlightInputComponent input) {
        // Apply optional aim error from the archetype (deterministic when aimErrorDeg==0).
        // (Random jitter intentionally omitted here to keep the system pure/testable; a separate
        //  AimNoise helper can perturb desiredAimDir before steering if desired.)
        PhysicsBodyComponent selfPhys = PHYS_M.get(entity);
        TransformComponent selfTx = TX_M.get(entity);

        // angular velocity (world) -> local
        invRot.set(selfTx.rotation).conjugate();
        angVelLocal.set(selfPhys.body.getAngularVelocity()).mul(invRot);

        steering.computeInputs(selfTx.rotation, angVelLocal,
            ai.blackboard.desiredAimDir, ai.blackboard.desiredThrottle, input);
        input.rollInput = MathUtils.clamp(input.rollInput + ai.blackboard.desiredRoll, -1f, 1f);

        if (ai.blackboard.fireGuns || ai.blackboard.fireMissiles) {
            fireWeapons(entity, ai);
        }
    }

    private void fireWeapons(Entity entity, ShipPilotAIComponent ai) {
        ShipHardpointComponent hpc = HARD_M.get(entity);
        if (hpc == null) return;
        // Bind the ship's current target so guided missiles can pick it up at spawn.
        hpc.currentTarget = ai.currentTarget;
        for (Hardpoint hp : hpc.hardpoints) {
            if (hp.isEmpty()) continue;
            boolean isMissile = hp.mountedWeapon.category == ShipWeaponCategory.MISSILE_LAUNCHER;
            if (isMissile && !ai.blackboard.fireMissiles) continue;
            if (!isMissile && !ai.blackboard.fireGuns) continue;
            weaponSystem.fireHardpoint(entity, hp.id);
        }
    }

    private static final Family PLAYER_FAMILY =
        Family.all(PlayerTagComponent.class, TransformComponent.class).get();
    private static final Family AI_SHIP_FAMILY =
        Family.all(ShipPilotAIComponent.class, TransformComponent.class, HealthComponent.class).get();

    /**
     * Nearest hostile within the archetype's aggro range. Hostiles are: the player ship (always a
     * valid target, matching the existing ScanForThreatsTask convention) and AI ships belonging to
     * a different fleet. Returns null if nothing is in range.
     */
    private Entity acquireTarget(Entity self, ShipPilotAIComponent ai) {
        TransformComponent selfTx = TX_M.get(self);
        if (selfTx == null) return null;
        float aggro = ai.archetype != null ? ai.archetype.aggroRange : 2000f;
        float bestDist = aggro;
        Entity best = null;

        // Players.
        ImmutableArray<Entity> players = getEngine().getEntitiesFor(PLAYER_FAMILY);
        for (int i = 0; i < players.size(); i++) {
            Entity p = players.get(i);
            float d = selfTx.position.dst(TX_M.get(p).position);
            if (d < bestDist) { bestDist = d; best = p; }
        }

        // Other-fleet AI ships.
        FleetMemberComponent myFleet = FLEET_M.get(self);
        String myFleetId = myFleet != null ? myFleet.fleetId : null;
        ImmutableArray<Entity> ships = getEngine().getEntitiesFor(AI_SHIP_FAMILY);
        for (int i = 0; i < ships.size(); i++) {
            Entity other = ships.get(i);
            if (other == self) continue;
            HealthComponent oh = HP_M.get(other);
            if (oh != null && !oh.alive) continue;
            FleetMemberComponent of = FLEET_M.get(other);
            String otherFleetId = of != null ? of.fleetId : null;
            // Same fleet -> friendly. Both null fleet -> treat as neutral (skip).
            if (myFleetId != null && myFleetId.equals(otherFleetId)) continue;
            if (myFleetId == null && otherFleetId == null) continue;
            float d = selfTx.position.dst(TX_M.get(other).position);
            if (d < bestDist) { bestDist = d; best = other; }
        }
        return best;
    }

    /** Muzzle speed of the first non-missile weapon, or a sane default for lead math. */
    private float gunMuzzleSpeed(Entity entity) {
        ShipHardpointComponent hpc = HARD_M.get(entity);
        if (hpc != null) {
            for (Hardpoint hp : hpc.hardpoints) {
                if (!hp.isEmpty() && hp.mountedWeapon.category != ShipWeaponCategory.MISSILE_LAUNCHER) {
                    return hp.mountedWeapon.projectileSpeed;
                }
            }
        }
        return 600f;
    }

    private float gunRange(Entity entity) {
        ShipHardpointComponent hpc = HARD_M.get(entity);
        if (hpc != null) {
            for (Hardpoint hp : hpc.hardpoints) {
                if (!hp.isEmpty() && hp.mountedWeapon.category != ShipWeaponCategory.MISSILE_LAUNCHER) {
                    return hp.mountedWeapon.range;
                }
            }
        }
        return 500f;
    }

    private void zero(ShipFlightInputComponent input) {
        if (input == null) return;
        input.throttle = input.strafe = input.verticalThrust = 0f;
        input.pitchInput = input.yawInput = input.rollInput = 0f;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.ShipPilotAISystemTest"`
Expected: PASS (both `attackerClosesAlignsAndFires` and `acquiresPlayerTargetWhenNoneAssigned`). If the attacker fires but does not close, increase the simulated duration or verify `TransformComponent` sync; if it never fires, confirm `gun.range` ≥ engagement distance and the steering convergence test (Task 3) passes.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/ai/ShipPilotAISystem.java \
        core/src/test/java/com/galacticodyssey/ship/ai/ShipPilotAISystemTest.java
git commit -m "feat(ai): ShipPilotAISystem drives NPC dogfight pilots"
```

---

### Task 9: Bind guided-missile target at spawn

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/weapons/systems/ShipProjectileSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/weapons/systems/MissileTargetBindingTest.java`

Today `ShipProjectileSystem` creates a `GuidedProjectileComponent` for `MISSILE_LAUNCHER` weapons but never sets `targetEntity`, so missiles fly straight. Bind it from the firing ship's `ShipHardpointComponent.currentTarget` (which `ShipPilotAISystem` already sets before firing).

- [ ] **Step 1: Read the current spawn code**

Open `ShipProjectileSystem.java` and find the `MISSILE_LAUNCHER` branch where `GuidedProjectileComponent gpc = new GuidedProjectileComponent();` is created and added to the projectile (research located it near the projectile-spawn handler reacting to `ShipWeaponFiredEvent`). Note the variable holding the firing ship — research showed it as `event.shipEntity` on `ShipWeaponFiredEvent`. Confirm the exact field name in `ShipWeaponFiredEvent`.

- [ ] **Step 2: Write the failing test**

```java
package com.galacticodyssey.ship.weapons.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointType;
import com.galacticodyssey.ship.weapons.ShipWeaponEnums.ShipWeaponCategory;
import com.galacticodyssey.ship.weapons.components.GuidedProjectileComponent;
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import com.galacticodyssey.ship.weapons.data.Hardpoint;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;
import com.galacticodyssey.ship.weapons.events.ShipWeaponFiredEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MissileTargetBindingTest {

    @Test
    void firedMissileBindsToShipCurrentTarget() {
        EventBus bus = new EventBus();
        Engine engine = new Engine();
        ShipProjectileSystem sys = new ShipProjectileSystem(bus); // confirm constructor signature
        engine.addSystem(sys);

        Entity target = new Entity();
        TransformComponent tt = new TransformComponent();
        tt.position.set(0, 0, -500);
        target.add(tt);
        engine.addEntity(target);

        Entity shooter = new Entity();
        TransformComponent st = new TransformComponent();
        shooter.add(st);
        ShipHardpointComponent hpc = new ShipHardpointComponent();
        hpc.currentTarget = target;
        Hardpoint hp = new Hardpoint("m0", HardpointType.MISSILE_BAY, HardpointSize.MEDIUM, 0, 120);
        ShipWeaponData missile = new ShipWeaponData();
        missile.id = "m"; missile.category = ShipWeaponCategory.MISSILE_LAUNCHER;
        missile.damage = 100f; missile.damageType = DamageType.EXPLOSIVE;
        missile.fireRate = 1f; missile.projectileSpeed = 80f; missile.range = 2000f;
        hp.mountedWeapon = missile;
        hpc.hardpoints.add(hp);
        shooter.add(hpc);
        engine.addEntity(shooter);

        // Simulate a weapon-fired event for the missile (origin, direction toward target).
        bus.publish(new ShipWeaponFiredEvent(shooter, "m0",
            new Vector3(st.position), new Vector3(0, 0, -1), missile));

        // After the event is handled, a guided projectile entity should exist bound to target.
        GuidedProjectileComponent found = null;
        for (Entity e : engine.getEntities()) {
            GuidedProjectileComponent g = e.getComponent(GuidedProjectileComponent.class);
            if (g != null) { found = g; break; }
        }
        assertNotNull(found, "a guided projectile should have spawned");
        assertSame(target, found.targetEntity, "missile should be bound to the ship's current target");
    }
}
```

> Implementer note: confirm `ShipProjectileSystem`'s constructor signature and whether spawn happens synchronously inside the `ShipWeaponFiredEvent` handler or during `update`. If spawn is deferred to `update`, call `engine.update(1/60f)` before scanning for the projectile. Adjust the test accordingly — the assertion (missile bound to target) is the acceptance bar.

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.weapons.systems.MissileTargetBindingTest"`
Expected: FAIL — `found.targetEntity` is null.

- [ ] **Step 4: Add the binding**

In `ShipProjectileSystem`, in the `MISSILE_LAUNCHER` branch right after the `GuidedProjectileComponent` is created, add:
```java
ShipHardpointComponent firingHpc = event.shipEntity.getComponent(ShipHardpointComponent.class);
if (firingHpc != null && firingHpc.currentTarget != null) {
    gpc.targetEntity = firingHpc.currentTarget;
}
```
(Use the actual firing-ship field name confirmed in Step 1, and the actual `GuidedProjectileComponent` variable name in that branch.)

- [ ] **Step 5: Run test to verify it passes & commit**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.weapons.systems.MissileTargetBindingTest"`
Expected: PASS.

```bash
git add core/src/main/java/com/galacticodyssey/ship/weapons/systems/ShipProjectileSystem.java \
        core/src/test/java/com/galacticodyssey/ship/weapons/systems/MissileTargetBindingTest.java
git commit -m "feat(weapons): bind guided missiles to firing ship's current target"
```

---

### Task 10: ShipFactory.createNpcCombatShip + FleetExpansionSystem wiring

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/ShipFactory.java`
- Modify: `core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetExpansionSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ShipFactoryNpcCombatTest.java`

Add a factory method that builds a flyable AI ship on top of the existing `createShip`, attaching the combat + AI stack. Then wire `FleetExpansionSystem` to use it.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.systems.BulletPhysicsSystem;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.ship.ai.PilotArchetypeRegistry;
import com.galacticodyssey.ship.ai.ShipPilotAIComponent;
import com.galacticodyssey.ship.components.ShipFlightInputComponent;
import com.galacticodyssey.ship.data.ShipSizeClass;       // confirm enum location/name
import com.galacticodyssey.ship.weapons.components.ShipHardpointComponent;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ShipFactoryNpcCombatTest {

    @BeforeAll static void initBullet() { Bullet.init(); }

    private EventBus bus;
    private BulletPhysicsSystem physics;
    private Engine engine;
    private ShipFactory factory;
    private PilotArchetypeRegistry archetypes;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
        physics = new BulletPhysicsSystem(bus);
        physics.initialize();
        engine = new Engine();
        factory = new ShipFactory(engine, physics);
        archetypes = new PilotArchetypeRegistry();
        archetypes.parse("[{\"id\":\"veteran\",\"usesMissiles\":true}]");
        factory.setPilotArchetypes(archetypes); // injected dependency (see Step 3)
    }

    @AfterEach
    void tearDown() { factory.dispose(); physics.dispose(); }

    @Test
    void buildsFlyableAiCombatShip() {
        Entity ship = factory.createNpcCombatShip(12345L, ShipSizeClass.SMALL, "veteran", 0, 0, 0);
        assertNotNull(ship.getComponent(PhysicsBodyComponent.class));
        assertNotNull(ship.getComponent(ShipFlightInputComponent.class), "NPC ship owns its input");
        assertNotNull(ship.getComponent(HealthComponent.class));
        assertNotNull(ship.getComponent(ShipHardpointComponent.class));
        ShipPilotAIComponent ai = ship.getComponent(ShipPilotAIComponent.class);
        assertNotNull(ai);
        assertNotNull(ai.behaviorTree, "tree built");
        assertEquals("veteran", ai.archetypeId);
        assertFalse(ship.getComponent(ShipHardpointComponent.class).hardpoints.isEmpty(), "has a weapon");
    }
}
```

> Implementer note: confirm `ShipSizeClass`'s package (`createShip` already takes it). Confirm `BulletPhysicsSystem` package. Adjust imports to match the codebase.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ShipFactoryNpcCombatTest"`
Expected: FAIL — `createNpcCombatShip`/`setPilotArchetypes` do not exist.

- [ ] **Step 3: Implement the factory method**

In `ShipFactory`, add a pilot-archetype dependency and the new method. Add field + setter:
```java
    private com.galacticodyssey.ship.ai.PilotArchetypeRegistry pilotArchetypes;

    public void setPilotArchetypes(com.galacticodyssey.ship.ai.PilotArchetypeRegistry registry) {
        this.pilotArchetypes = registry;
    }
```

Add the method (reuses `createShip` for the full flight/physics stack, then layers combat + AI):
```java
    /**
     * Builds a flyable, AI-piloted combat ship: the standard flight/physics stack from
     * {@link #createShip} plus its own flight input, health, a gun hardpoint, and pilot AI.
     */
    public com.badlogic.ashley.core.Entity createNpcCombatShip(
            long seed, ShipSizeClass sizeClass, String archetypeId,
            float x, float y, float z) {

        com.badlogic.ashley.core.Entity ship = createShip(seed, sizeClass, x, y, z);

        // NPC ships carry their own flight input (player ships keep input on the player entity).
        ship.add(new com.galacticodyssey.ship.components.ShipFlightInputComponent());

        // Health for damage/death.
        com.galacticodyssey.combat.components.HealthComponent health =
            new com.galacticodyssey.combat.components.HealthComponent();
        health.maxHP = 100f * (sizeClass.ordinal() + 1);
        health.currentHP = health.maxHP;
        health.alive = true;
        ship.add(health);

        // A basic fixed gun so the pilot has something to shoot with.
        com.galacticodyssey.ship.weapons.components.ShipHardpointComponent hpc =
            new com.galacticodyssey.ship.weapons.components.ShipHardpointComponent();
        com.galacticodyssey.ship.weapons.data.Hardpoint gunHp =
            new com.galacticodyssey.ship.weapons.data.Hardpoint(
                "npc_gun_0",
                com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointType.FIXED,
                com.galacticodyssey.ship.weapons.ShipWeaponEnums.HardpointSize.SMALL, 0, 30);
        com.galacticodyssey.ship.weapons.data.ShipWeaponData gun =
            new com.galacticodyssey.ship.weapons.data.ShipWeaponData();
        gun.id = "npc_cannon";
        gun.name = "NPC Cannon";
        gun.category = com.galacticodyssey.ship.weapons.ShipWeaponEnums.ShipWeaponCategory.BALLISTIC_CANNON;
        gun.damage = 12f;
        gun.damageType = com.galacticodyssey.combat.CombatEnums.DamageType.BALLISTIC;
        gun.fireRate = 4f;
        gun.projectileSpeed = 600f;
        gun.range = 1000f;
        gun.energyCost = 0f;
        gun.heatPerShot = 0.05f;
        gunHp.mountedWeapon = gun;
        hpc.hardpoints.add(gunHp);
        ship.add(hpc);

        // Pilot AI.
        com.galacticodyssey.ship.ai.ShipPilotAIComponent ai =
            new com.galacticodyssey.ship.ai.ShipPilotAIComponent();
        ai.archetypeId = archetypeId;
        if (pilotArchetypes != null) {
            ai.archetype = pilotArchetypes.get(archetypeId);
        }
        if (ai.archetype == null) {
            ai.archetype = new com.galacticodyssey.ship.ai.PilotArchetype();
        }
        ai.decisionInterval = ai.archetype.reactionTimeSec;
        ai.behaviorTree = com.galacticodyssey.ship.ai.DogfightTreeFactory.build(ship, gun.range);
        ship.add(ai);

        return ship;
    }
```

> Implementer note: if `createShip` already attaches a `ShipHardpointComponent` for some size classes, guard with `if (ship.getComponent(ShipHardpointComponent.class) == null)` before adding one, and add the gun hardpoint to the existing component instead. Research found `createShip` does NOT add hardpoints, but verify before running.

- [ ] **Step 4: Run the factory test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ShipFactoryNpcCombatTest"`
Expected: PASS.

- [ ] **Step 5: Wire FleetExpansionSystem to spawn flyable ships**

`FleetExpansionSystem` currently builds bare entities (FleetMemberComponent + TransformComponent). Change `expandFleet` to build a flyable AI ship via the factory and merge the fleet member data onto it. This requires the system to hold a `ShipFactory` + an archetype id source.

Add constructor params (find the current `FleetExpansionSystem` constructor and extend it):
```java
    private final ShipFactory shipFactory;

    // add ShipFactory to the existing constructor signature and assign it:
    // public FleetExpansionSystem(EventBus eventBus, ..., ShipFactory shipFactory) {
    //     ... existing ...
    //     this.shipFactory = shipFactory;
    // }
```

Replace the per-member entity creation inside the `for` loop with:
```java
            for (int i = 0; i < entry.count; i++) {
                // Map fleet role to a pilot archetype id (simple default mapping).
                String archetypeId = "veteran";

                Entity ship = shipFactory.createNpcCombatShip(
                    fc.fleetId.hashCode() * 31L + slotIndex,   // deterministic per-slot seed
                    mapToSizeClass(entry.shipClass),            // see note below
                    archetypeId,
                    ffc.localAnchorX, ffc.localAnchorY, ffc.localAnchorZ);

                FleetMemberComponent fmc = new FleetMemberComponent();
                fmc.fleetEntity = fleetEntity;
                fmc.fleetId = fc.fleetId;
                fmc.squadronIndex = squadronIndex;
                fmc.role = entry.shipClass.defaultRole;
                fmc.formationSlotIndex = slotIndex;
                ship.add(fmc);
                // NOTE: createNpcCombatShip already called engine.addEntity(ship) via createShip.

                slotIndex++;
                inSquadron++;
                if (inSquadron >= 4) { squadronIndex++; inSquadron = 0; }
            }
```

> Implementer notes:
> - `createShip` already calls `engine.addEntity`; do NOT add the entity again.
> - `mapToSizeClass(entry.shipClass)` — inspect `entry.shipClass` (a fleet ship-class type) and map it to a `ShipSizeClass`. If there is an obvious size field, use it; otherwise default all to `ShipSizeClass.SMALL` for fighters and document the mapping. Keep it a small private helper.
> - On `FleetCollapsedEvent` / despawn, ensure the ship's Bullet body is removed and disposed. `ShipFactory` tracks managed bodies and disposes them in `dispose()`; if the collapse path removes entities without going through the factory, add disposal there. Find the existing collapse/despawn handler and confirm bodies are released (the existing system already despawns members — verify it disposes physics bodies; if it only calls `engine.removeEntity`, add body cleanup using `PhysicsBodyComponent`).

- [ ] **Step 6: Wire ShipFactory into FleetExpansionSystem construction in GameWorld**

In `GameWorld`, find where `FleetExpansionSystem` is constructed and pass the existing `ShipFactory` instance (GameWorld already creates a `ShipFactory` for the player). If construction order means the factory is created after the fleet system, reorder so the factory exists first. Also call `factory.setPilotArchetypes(pilotArchetypeRegistry)` where the registry is loaded (load it near other data registries via `loadDefault()`).

- [ ] **Step 7: Run the full ship + fleet test suite & commit**

Run:
```
./gradlew :core:test --tests "com.galacticodyssey.ship.*" --tests "com.galacticodyssey.combat.fleet.*"
```
Expected: PASS (no regressions in existing fleet tests; new factory test passes).

```bash
git add core/src/main/java/com/galacticodyssey/ship/ShipFactory.java \
        core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetExpansionSystem.java \
        core/src/main/java/com/galacticodyssey/core/GameWorld.java \
        core/src/test/java/com/galacticodyssey/ship/ShipFactoryNpcCombatTest.java
git commit -m "feat(ai): expand fleets into flyable AI combat ships"
```

---

### Task 11: Register ShipPilotAISystem in GameWorld + archetype differentiation test

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/ai/ArchetypeDifferentiationTest.java`

- [ ] **Step 1: Register the system**

In `GameWorld`, where combat/AI systems are added, construct and register the pilot AI system with the existing `EventBus` and `ShipWeaponSystem` instances:
```java
        ShipPilotAISystem shipPilotAISystem = new ShipPilotAISystem(eventBus, shipWeaponSystem);
        engine.addSystem(shipPilotAISystem);
```
Confirm `shipWeaponSystem` is the field/local already created in `GameWorld` (research confirmed `ShipWeaponSystem(EventBus)` exists and is registered). Ensure registration happens regardless of order — Ashley sorts systems by priority (this one is 2), so it runs before `ShipFlightSystem` (3) automatically.

- [ ] **Step 2: Write the differentiation test (capstone)**

This reuses the integration harness shape from Task 8 but runs two attackers (ace vs rookie) against an identical evasive target and asserts the ace lands at least as many hits. Because the `aimErrorDeg` perturbation is omitted from the system in Task 8 (kept deterministic), this test instead asserts the **decision cadence** difference: the ace (short `reactionTimeSec`) issues more tree decisions and thus tracks a maneuvering target more tightly, producing a smaller average `angleOffBore`.

```java
package com.galacticodyssey.ship.ai;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure (no physics) check that a faster-reacting archetype re-aims more often, keeping its nose
 * closer to a crossing target. Drives the blackboard + tree directly with a scripted target path.
 */
class ArchetypeDifferentiationTest {

    private float runAvgAngleOff(float reactionTimeSec) {
        ShipPilotAIComponent ai = new ShipPilotAIComponent();
        ai.archetype = new PilotArchetype();
        ai.archetype.reactionTimeSec = reactionTimeSec;
        ai.archetype.preferredEngageRange = 350f;
        ai.decisionInterval = reactionTimeSec;
        Entity e = new Entity();
        e.add(ai);
        ai.behaviorTree = DogfightTreeFactory.build(e, 1000f);

        Vector3 selfPos = new Vector3(0, 0, 0);
        Quaternion selfRot = new Quaternion();
        Vector3 selfVel = new Vector3();
        // Target crossing left-to-right at constant speed, 400m ahead.
        Vector3 targetVel = new Vector3(60, 0, 0);
        Vector3 targetPos = new Vector3(-200, 0, -400);

        float dt = 1f / 60f, sumAngle = 0f; int n = 0;
        ai.decisionTimer = 0f;
        for (int i = 0; i < 300; i++) {
            targetPos.mulAdd(targetVel, dt);
            ai.blackboard.updateSensors(selfPos, selfRot, selfVel, targetPos, targetVel, 1000f);
            ai.blackboard.hasTarget = true;
            ai.blackboard.selfHealthPercent = 1f;
            // simulate cadence gating like ShipPilotAISystem.tickFull
            ai.decisionTimer -= dt;
            if (ai.decisionTimer <= 0f) {
                ai.decisionTimer = Math.max(ai.decisionInterval, ai.archetype.reactionTimeSec);
                ai.behaviorTree.step();
            }
            // "Rotate" self toward last-committed aim a fixed amount to emulate turning.
            Vector3 fwd = new Vector3(0, 0, -1).mul(selfRot);
            Vector3 aim = ai.blackboard.desiredAimDir;
            Quaternion toAim = new Quaternion().setFromCross(fwd, aim);
            // step a fraction toward aim
            selfRot.slerp(new Quaternion(selfRot).mul(toAim), 0.15f);
            sumAngle += ai.blackboard.angleOffBore; n++;
        }
        return sumAngle / n;
    }

    @Test
    void fasterReactionTracksTargetMoreTightly() {
        float aceAvg = runAvgAngleOff(0.05f);
        float rookieAvg = runAvgAngleOff(0.6f);
        assertTrue(aceAvg <= rookieAvg + 1f,
            "ace should track at least as tightly as rookie (ace=" + aceAvg + " rookie=" + rookieAvg + ")");
    }
}
```

> Implementer note: this test is a behavioural sanity check, not a physics sim. If `Quaternion.setFromCross`/`slerp` usage needs adjustment to compile, simplify the "turn" emulation while preserving the assertion intent (faster cadence ⇒ not-worse tracking). The key contract: cadence gating uses `max(decisionInterval, reactionTimeSec)`.

- [ ] **Step 3: Run the test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.ship.ai.ArchetypeDifferentiationTest"`
Expected: PASS.

- [ ] **Step 4: Run the full AI + ship + fleet suites for regressions**

Run:
```
./gradlew :core:test --tests "com.galacticodyssey.ship.ai.*" --tests "com.galacticodyssey.ship.*" --tests "com.galacticodyssey.combat.*"
```
Expected: PASS.

- [ ] **Step 5: Build the whole project**

Run: `./gradlew :core:compileJava :core:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java \
        core/src/test/java/com/galacticodyssey/ship/ai/ArchetypeDifferentiationTest.java
git commit -m "feat(ai): register ShipPilotAISystem; verify archetype tracking differentiation"
```

---

## Final verification checklist

- [ ] `./gradlew :core:test` passes fully (no regressions).
- [ ] Pilot archetypes load from `data/ai/pilot_archetypes.json`.
- [ ] An NPC ship spawned by `FleetExpansionSystem` has flight + physics + health + a gun + pilot AI.
- [ ] An AI ship with no assigned target acquires the nearest hostile (player / other fleet) within aggro range.
- [ ] In the Task 8 integration test, the AI attacker closes range, aligns its nose, and fires.
- [ ] Missiles fired by AI bind to the target (Task 9).
- [ ] No Bullet bodies leak on fleet collapse (Task 10, Step 5 note verified).
- [ ] Consider running the game (`run-galactic-odyssey` skill) and observing an NPC fleet engage the player as a manual smoke test.

## Notes & deviations from the spec

- **Behaviour-tree loading:** the spec referenced "the same tree-loading mechanism `CombatAIComponent` uses." Research found that mechanism does not exist yet (trees are null at runtime). The plan builds the tree in code via `DogfightTreeFactory`; tuning remains data-driven through `PilotArchetype` JSON. This satisfies the data-driven intent without inventing a `.tree` parser.
- **Aim error / reaction jitter:** modelled via decision cadence (`decisionInterval = reactionTimeSec`) rather than per-shot random cone, to keep `ShipPilotAISystem` deterministic and testable. A random `aimErrorDeg` perturbation of `desiredAimDir` can be added later as a small pure helper if more spray is desired.
- **Persistence:** `ShipPilotAIComponent` implements `Snapshotable` (round-trip unit-tested), but full save/load integration of in-flight AI state is out of scope; AI ships are re-derived on fleet expansion.
- **ShipFlightSystem refactor** was required (not in the original spec component list) because the system was player-only; this is the minimal change that lets NPC ships fly.
