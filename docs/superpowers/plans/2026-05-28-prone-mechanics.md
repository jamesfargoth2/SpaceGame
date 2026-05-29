# Prone Mechanics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Z-toggle prone posture that physically resizes the Bullet capsule to 0.6 m (sphere), lets the player crawl through tight spaces, and wires into the existing stamina/speed/camera systems.

**Architecture:** `PhysicsBodyComponent` holds a pre-allocated prone `btCapsuleShape`; `PlayerMovementSystem` swaps shapes and checks 0.9 m upward clearance before standing; `CameraSystem` picks the eye-height target from a three-way conditional on `isProne`/`isCrouching`. A `PlayerPostureChangedEvent` is published on every posture change so audio/animation/networking can react independently.

**Tech Stack:** libGDX / Ashley ECS, gdx-bullet (`btCapsuleShape`, `btDiscreteDynamicsWorld`), JUnit 5

---

## File Map

| File | Action | What changes |
|------|--------|-------------|
| `core/.../player/PostureType.java` | **Create** | New enum: STANDING, CROUCHING, PRONE |
| `core/.../core/events/PlayerPostureChangedEvent.java` | **Create** | New event holding `previous` + `next` PostureType |
| `core/.../player/components/MovementStateComponent.java` | **Modify** | Add `boolean isProne` |
| `core/.../persistence/snapshots/MovementStateSnapshot.java` | **Modify** | Add `boolean isProne` |
| `core/.../player/components/PlayerInputComponent.java` | **Modify** | Add `boolean proneToggleRequested` |
| `core/.../player/components/FPSCameraComponent.java` | **Modify** | Add `float proneEyeHeight = 0.3f` |
| `core/.../core/components/PhysicsBodyComponent.java` | **Modify** | Add `btCollisionShape proneShape` |
| `core/.../player/systems/PlayerInputSystem.java` | **Modify** | Route Z key → `proneToggleRequested` when on foot |
| `core/.../player/systems/PlayerMovementSystem.java` | **Modify** | Add PRONE_SPEED, handleProneToggle, swapCapsule, isClearToStand; guard sprint/jump; zero slope drain while prone |
| `core/.../player/systems/CameraSystem.java` | **Modify** | Three-way eye height, halve head-bob amplitude when prone |
| `core/.../core/GameWorld.java` | **Modify** | Allocate proneShape in createPlayerEntity; dispose it; wire EventBus to PlayerMovementSystem |
| `core/.../player/systems/ProneStateTest.java` | **Create** | Pure-unit tests (no Bullet): event data, isProne default, snapshot round-trip |
| `core/.../player/systems/ProneMovementTest.java` | **Create** | Bullet integration tests: all 4 transitions, blocked stand, speed cap, no stamina drain |

---

## Task 1: PostureType enum + PlayerPostureChangedEvent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/PostureType.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/PlayerPostureChangedEvent.java`
- Create: `core/src/test/java/com/galacticodyssey/player/systems/ProneStateTest.java`

- [ ] **Step 1: Write the failing test**

```java
// core/src/test/java/com/galacticodyssey/player/systems/ProneStateTest.java
package com.galacticodyssey.player.systems;

import com.galacticodyssey.core.events.PlayerPostureChangedEvent;
import com.galacticodyssey.player.PostureType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProneStateTest {

    @Test
    void eventHoldsPostures() {
        PlayerPostureChangedEvent event =
            new PlayerPostureChangedEvent(PostureType.STANDING, PostureType.PRONE);
        assertEquals(PostureType.STANDING, event.previous);
        assertEquals(PostureType.PRONE, event.next);
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```
./gradlew :core:test --tests "com.galacticodyssey.player.systems.ProneStateTest" 2>&1 | tail -20
```
Expected: FAIL — `PostureType` and `PlayerPostureChangedEvent` do not exist.

- [ ] **Step 3: Create PostureType**

```java
// core/src/main/java/com/galacticodyssey/player/PostureType.java
package com.galacticodyssey.player;

public enum PostureType {
    STANDING, CROUCHING, PRONE
}
```

- [ ] **Step 4: Create PlayerPostureChangedEvent**

```java
// core/src/main/java/com/galacticodyssey/core/events/PlayerPostureChangedEvent.java
package com.galacticodyssey.core.events;

import com.galacticodyssey.player.PostureType;

public class PlayerPostureChangedEvent {
    public final PostureType previous;
    public final PostureType next;
    public PlayerPostureChangedEvent(PostureType previous, PostureType next) {
        this.previous = previous;
        this.next = next;
    }
}
```

- [ ] **Step 5: Run test to confirm it passes**

```
./gradlew :core:test --tests "com.galacticodyssey.player.systems.ProneStateTest" 2>&1 | tail -10
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/PostureType.java \
        core/src/main/java/com/galacticodyssey/core/events/PlayerPostureChangedEvent.java \
        core/src/test/java/com/galacticodyssey/player/systems/ProneStateTest.java
git commit -m "feat(player): add PostureType enum and PlayerPostureChangedEvent"
```

---

## Task 2: Component data — isProne, proneToggleRequested, proneEyeHeight, proneShape, snapshot

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/components/MovementStateComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/persistence/snapshots/MovementStateSnapshot.java`
- Modify: `core/src/main/java/com/galacticodyssey/player/components/PlayerInputComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/player/components/FPSCameraComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/core/components/PhysicsBodyComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/player/systems/ProneStateTest.java`

- [ ] **Step 1: Write failing tests**

Add to `ProneStateTest.java`:

```java
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.persistence.snapshots.MovementStateSnapshot;

@Test
void isProneDefaultsFalse() {
    MovementStateComponent state = new MovementStateComponent();
    assertFalse(state.isProne);
}

@Test
void snapshotRoundTripsIsProne() {
    MovementStateComponent state = new MovementStateComponent();
    state.isProne = true;
    MovementStateSnapshot snap = state.takeSnapshot();
    assertTrue(snap.isProne);

    MovementStateComponent restored = new MovementStateComponent();
    restored.restoreFromSnapshot(snap);
    assertTrue(restored.isProne);
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
./gradlew :core:test --tests "com.galacticodyssey.player.systems.ProneStateTest" 2>&1 | tail -20
```
Expected: FAIL — `isProne` does not exist on `MovementStateComponent`.

- [ ] **Step 3: Add isProne to MovementStateComponent**

In `MovementStateComponent.java`, add `public boolean isProne;` after `public boolean isCrouching;` (line 11):

```java
    public boolean isGrounded;
    public boolean isSprinting;
    public boolean isCrouching;
    public boolean isProne;
```

Then add `isProne` to `takeSnapshot()` and `restoreFromSnapshot()`:

```java
    @Override
    public MovementStateSnapshot takeSnapshot() {
        MovementStateSnapshot s = new MovementStateSnapshot();
        s.isGrounded = isGrounded;
        s.isSprinting = isSprinting;
        s.isCrouching = isCrouching;
        s.isProne = isProne;
        s.currentSpeed = currentSpeed;
        s.currentStamina = currentStamina;
        s.maxStamina = maxStamina;
        s.staminaDrainRate = staminaDrainRate;
        s.staminaRegenRate = staminaRegenRate;
        s.slopeAngle = slopeAngle;
        s.isExhausted = isExhausted;
        s.fallVelocity = fallVelocity;
        return s;
    }

    @Override
    public void restoreFromSnapshot(MovementStateSnapshot s) {
        isGrounded = s.isGrounded;
        isSprinting = s.isSprinting;
        isCrouching = s.isCrouching;
        isProne = s.isProne;
        currentSpeed = s.currentSpeed;
        currentStamina = s.currentStamina;
        maxStamina = s.maxStamina;
        staminaDrainRate = s.staminaDrainRate;
        staminaRegenRate = s.staminaRegenRate;
        slopeAngle = s.slopeAngle;
        isExhausted = s.isExhausted;
        fallVelocity = s.fallVelocity;
    }
```

- [ ] **Step 4: Add isProne to MovementStateSnapshot**

In `MovementStateSnapshot.java`, add `public boolean isProne;` after `public boolean isCrouching;`:

```java
public class MovementStateSnapshot {
    public boolean isGrounded;
    public boolean isSprinting;
    public boolean isCrouching;
    public boolean isProne;
    public float currentSpeed;
    // ... rest unchanged
```

- [ ] **Step 5: Add proneToggleRequested to PlayerInputComponent**

In `PlayerInputComponent.java`, add after `public boolean boardPressed;`:

```java
    public boolean boardPressed;
    public boolean proneToggleRequested;
```

- [ ] **Step 6: Add proneEyeHeight to FPSCameraComponent**

In `FPSCameraComponent.java`, add after `public float crouchEyeHeight = 1.0f;` (line 10):

```java
    public float eyeHeight = 1.7f;
    public float crouchEyeHeight = 1.0f;
    public float proneEyeHeight = 0.3f;
    public float currentEyeHeight = 1.7f;
```

- [ ] **Step 7: Add proneShape to PhysicsBodyComponent**

In `PhysicsBodyComponent.java`, add after `public btCollisionShape shape;`:

```java
    public btRigidBody body;
    public btCollisionShape shape;
    public btCollisionShape proneShape;
```

- [ ] **Step 8: Run tests to confirm they pass**

```
./gradlew :core:test --tests "com.galacticodyssey.player.systems.ProneStateTest" 2>&1 | tail -10
```
Expected: all 3 tests PASS.

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/components/MovementStateComponent.java \
        core/src/main/java/com/galacticodyssey/persistence/snapshots/MovementStateSnapshot.java \
        core/src/main/java/com/galacticodyssey/player/components/PlayerInputComponent.java \
        core/src/main/java/com/galacticodyssey/player/components/FPSCameraComponent.java \
        core/src/main/java/com/galacticodyssey/core/components/PhysicsBodyComponent.java \
        core/src/test/java/com/galacticodyssey/player/systems/ProneStateTest.java
git commit -m "feat(player): add isProne, proneToggleRequested, proneEyeHeight, proneShape to components"
```

---

## Task 3: PlayerInputSystem — Z key routes to proneToggleRequested on foot

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java`

Z is already captured by `keyDown` into `flightAssistTogglePressed` regardless of mode. In flight mode, `processFlightInput` consumes it for the flight-assist toggle (unchanged). In foot mode, `processFootInput` does not currently consume it. We route it to `proneToggleRequested` there.

No new accumulator field is needed — `flightAssistTogglePressed` is used as-is.

- [ ] **Step 1: Add prone routing in processFootInput**

In `PlayerInputSystem.java`, in `processFootInput` (after line 258 where `interactPressed` is handled), add:

```java
        if (interactPressed) { input.interactPressed = true; interactPressed = false; }
        if (cameraTogglePressed) { input.cameraTogglePressed = true; cameraTogglePressed = false; }
        if (flightAssistTogglePressed) { input.proneToggleRequested = true; flightAssistTogglePressed = false; }

        targetLockPressed = false;
        nextTargetPressed = false;
    }
```

- [ ] **Step 2: Run the existing input system tests**

```
./gradlew :core:test --tests "com.galacticodyssey.player.systems.PlayerInputSystemPilotingTest" --tests "com.galacticodyssey.player.systems.PlayerInputTargetCycleTest" 2>&1 | tail -15
```
Expected: all existing tests PASS (no regression).

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java
git commit -m "feat(player): route Z key to proneToggleRequested when on foot"
```

---

## Task 4: PlayerMovementSystem — prone core (toggle, capsule swap, clearance, speed, stamina)

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/player/systems/ProneMovementTest.java`

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/player/systems/ProneMovementTest.java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class ProneMovementTest {

    private Engine engine;
    private PlayerMovementSystem movementSystem;
    private btDiscreteDynamicsWorld dynamicsWorld;
    private btCollisionConfiguration collisionConfig;
    private btCollisionDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;
    private btCollisionShape groundShape;
    private btRigidBody groundBody;

    @BeforeAll
    static void initBullet() { Bullet.init(); }

    @BeforeEach
    void setUp() {
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3(0, -9.81f, 0));

        groundShape = new btBoxShape(new Vector3(500, 0.5f, 500));
        btRigidBody.btRigidBodyConstructionInfo groundInfo =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, groundShape);
        groundBody = new btRigidBody(groundInfo);
        groundBody.setWorldTransform(new Matrix4().setToTranslation(0, -0.5f, 0));
        dynamicsWorld.addRigidBody(groundBody);
        groundInfo.dispose();

        engine = new Engine();
        movementSystem = new PlayerMovementSystem(dynamicsWorld);
        movementSystem.setPlanetCenter(new Vector3(0f, -1_000_000f, 0f));
        engine.addSystem(movementSystem);
    }

    @AfterEach
    void tearDown() {
        engine.removeAllEntities();
        engine.removeAllSystems();
        dynamicsWorld.removeRigidBody(groundBody);
        groundBody.dispose();
        groundShape.dispose();
        dynamicsWorld.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
    }

    private Entity createPlayerEntity(float startY) {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(0, startY, 0);

        PlayerInputComponent input = new PlayerInputComponent();
        MovementStateComponent movement = new MovementStateComponent();
        FPSCameraComponent camera = new FPSCameraComponent();

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btCapsuleShape(0.3f, 1.2f);
        physics.proneShape = new btCapsuleShape(0.3f, 0.0f);
        physics.mass = 80f;
        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(physics.mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(physics.mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(0, startY, 0));
        physics.body.setAngularFactor(new Vector3(0, 0, 0));
        physics.body.setFriction(1.0f);
        physics.body.setRestitution(0f);
        dynamicsWorld.addRigidBody(physics.body);
        info.dispose();

        entity.add(transform);
        entity.add(input);
        entity.add(movement);
        entity.add(camera);
        entity.add(physics);
        engine.addEntity(entity);
        return entity;
    }

    private void settle(Entity entity) {
        for (int i = 0; i < 120; i++) {
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
            engine.update(1f / 60f);
        }
    }

    private void cleanupEntity(Entity entity) {
        PhysicsBodyComponent physics = entity.getComponent(PhysicsBodyComponent.class);
        dynamicsWorld.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
        physics.proneShape.dispose();
    }

    @Test
    void standingToProne() {
        Entity player = createPlayerEntity(1.0f);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        settle(player);

        input.proneToggleRequested = true;
        engine.update(1f / 60f);

        assertTrue(state.isProne, "Player should be prone after toggle");
        assertFalse(state.isCrouching, "Crouching should be false while prone");
        cleanupEntity(player);
    }

    @Test
    void crouchingToProne() {
        Entity player = createPlayerEntity(1.0f);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        settle(player);

        input.crouch = true;
        engine.update(1f / 60f);
        assertTrue(state.isCrouching, "Should be crouching before prone toggle");

        input.proneToggleRequested = true;
        engine.update(1f / 60f);

        assertTrue(state.isProne, "Player should be prone after toggle from crouch");
        assertFalse(state.isCrouching, "Crouching should be cleared when prone");
        cleanupEntity(player);
    }

    @Test
    void proneToStandingWhenClear() {
        Entity player = createPlayerEntity(1.0f);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        settle(player);

        // Go prone
        input.proneToggleRequested = true;
        engine.update(1f / 60f);
        assertTrue(state.isProne);

        // Stand back up — no ceiling, should succeed
        input.proneToggleRequested = true;
        engine.update(1f / 60f);

        assertFalse(state.isProne, "Player should be standing — no ceiling blocking");
        cleanupEntity(player);
    }

    @Test
    void proneToStandingBlockedByCeiling() {
        Entity player = createPlayerEntity(1.0f);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        settle(player);

        // Go prone — body centre stays at settled standing height (~0.9 m)
        input.proneToggleRequested = true;
        engine.update(1f / 60f);
        assertTrue(state.isProne);

        // Add a ceiling at 1.6 m — between prone top (0.9+0.3=1.2) and standing top (0.9+0.9=1.8)
        btBoxShape ceilingShape = new btBoxShape(new Vector3(500, 0.1f, 500));
        btRigidBody.btRigidBodyConstructionInfo ceilingInfo =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, ceilingShape);
        btRigidBody ceilingBody = new btRigidBody(ceilingInfo);
        ceilingBody.setWorldTransform(new Matrix4().setToTranslation(0, 1.6f, 0));
        dynamicsWorld.addRigidBody(ceilingBody);
        ceilingInfo.dispose();

        // Try to stand — should be blocked
        input.proneToggleRequested = true;
        engine.update(1f / 60f);

        assertTrue(state.isProne, "Player should remain prone — ceiling blocks standing");

        dynamicsWorld.removeRigidBody(ceilingBody);
        ceilingBody.dispose();
        ceilingShape.dispose();
        cleanupEntity(player);
    }

    @Test
    void proneSpeedCapIsLowerThanCrouch() {
        Entity player = createPlayerEntity(1.0f);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        settle(player);

        // Go prone and move forward
        input.proneToggleRequested = true;
        engine.update(1f / 60f);

        input.moveForward = 1f;
        for (int i = 0; i < 120; i++) {
            engine.update(1f / 60f);
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
        }

        // Horizontal speed should be near PRONE_SPEED (0.8 m/s), well below CROUCH_SPEED (1.5)
        PhysicsBodyComponent physics = player.getComponent(PhysicsBodyComponent.class);
        Vector3 vel = physics.body.getLinearVelocity();
        float hSpeed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        assertTrue(hSpeed <= 1.1f,
            "Prone speed should be at most 1.1 m/s (near PRONE_SPEED=0.8), got " + hSpeed);
        assertTrue(hSpeed > 0.1f, "Player should be moving while prone");
        cleanupEntity(player);
    }

    @Test
    void staminaDoesNotDrainWhileProne() {
        Entity player = createPlayerEntity(1.0f);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        settle(player);

        input.proneToggleRequested = true;
        engine.update(1f / 60f);
        assertTrue(state.isProne);

        float staminaBefore = state.currentStamina;
        input.moveForward = 1f;
        for (int i = 0; i < 60; i++) {
            engine.update(1f / 60f);
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
        }

        assertTrue(state.currentStamina >= staminaBefore,
            "Stamina must not drain while prone. Before=" + staminaBefore + " After=" + state.currentStamina);
        cleanupEntity(player);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```
./gradlew :core:test --tests "com.galacticodyssey.player.systems.ProneMovementTest" 2>&1 | tail -20
```
Expected: all 6 tests FAIL — `proneToggleRequested`, `isProne`, and `proneShape` not yet wired in PlayerMovementSystem.

- [ ] **Step 3: Add imports and constants to PlayerMovementSystem**

At the top of `PlayerMovementSystem.java`, add imports:

```java
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.PlayerPostureChangedEvent;
import com.galacticodyssey.player.PostureType;
```

After the existing constants block (after line 39, `SLOPE_DAMPING_MIN`), add:

```java
    private static final float PRONE_SPEED = 0.8f;
```

After the existing field declarations (after the `tempQuat` field), add:

```java
    private EventBus eventBus;

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }
```

- [ ] **Step 4: Add handleProneToggle, swapCapsule, isClearToStand methods**

Add these three private methods at the bottom of `PlayerMovementSystem`, before the closing `}`:

```java
    private void handleProneToggle(PhysicsBodyComponent physics, MovementStateComponent state) {
        if (state.isProne) {
            if (isClearToStand(physics)) {
                swapCapsule(physics, physics.shape);
                PostureType prev = PostureType.PRONE;
                state.isProne = false;
                if (eventBus != null) {
                    eventBus.publish(new PlayerPostureChangedEvent(prev, PostureType.STANDING));
                }
            }
        } else {
            PostureType prev = state.isCrouching ? PostureType.CROUCHING : PostureType.STANDING;
            swapCapsule(physics, physics.proneShape);
            state.isCrouching = false;
            state.isProne = true;
            if (eventBus != null) {
                eventBus.publish(new PlayerPostureChangedEvent(prev, PostureType.PRONE));
            }
        }
    }

    private void swapCapsule(PhysicsBodyComponent physics, btCollisionShape newShape) {
        dynamicsWorld.removeRigidBody(physics.body);
        physics.body.setCollisionShape(newShape);
        newShape.calculateLocalInertia(physics.mass, tempVec2);
        physics.body.setMassProps(physics.mass, tempVec2);
        dynamicsWorld.addRigidBody(physics.body);
        physics.body.activate();
    }

    private boolean isClearToStand(PhysicsBodyComponent physics) {
        physics.body.getWorldTransform(tempMat);
        tempMat.getTranslation(rayFrom);
        rayTo.set(localUp).scl(CAPSULE_HALF_HEIGHT).add(rayFrom);
        ClosestRayResultCallback callback = new ClosestRayResultCallback(rayFrom, rayTo);
        dynamicsWorld.rayTest(rayFrom, rayTo, callback);
        boolean clear = !callback.hasHit();
        callback.dispose();
        return clear;
    }
```

- [ ] **Step 5: Update processEntity — prone toggle, sprint/crouch guards, speed, jump, stamina**

Replace the block from `MovementStateComponent state = stateMapper.get(entity);` through `input.jumpRequested = false;` (lines 114–200) with the following. Changes are annotated with `// CHANGED` or `// NEW`:

```java
        MovementStateComponent state = stateMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);

        // NEW: consume prone toggle before any movement decisions
        if (input.proneToggleRequested) {
            handleProneToggle(physics, state);
            input.proneToggleRequested = false;
        }

        boolean wasGrounded = state.isGrounded;
        performGroundCheck(physics, state, tempVec);

        buildTangentFrame(cam != null ? -cam.yawAngle : 0f);

        float dirFwd = input.moveForward;
        float dirRight = input.moveStrafe;
        tempVec2.set(localForward).scl(dirFwd).add(tempVec3.set(localRight).scl(dirRight));
        float len = tempVec2.len();
        if (len > 0.001f) tempVec2.scl(1f / len);

        orientCapsule(physics, cam != null ? cam.yawAngle : 0f);

        float slopeAngle = state.slopeAngle;
        boolean movingUphill = false;
        if (state.isGrounded && len > 0.001f && slopeAngle > 1f) {
            float slopeHorizLen = projectOnTangent(state.groundNormal);
            if (slopeHorizLen > 0.001f) {
                float dot = tempVec2.x * tempVec3.x + tempVec2.y * tempVec3.y + tempVec2.z * tempVec3.z;
                movingUphill = dot > 0.1f;
            }
        }

        float slopeSpeedFactor = 1f;
        float slopeStaminaDrain = 0f;
        // CHANGED: no slope stamina drain while prone
        if (!state.isProne && movingUphill && slopeAngle > SLOPE_SPEED_PENALTY_START) {
            float slopeFrac = (slopeAngle - SLOPE_SPEED_PENALTY_START) / (MAX_SLOPE_ANGLE - SLOPE_SPEED_PENALTY_START);
            slopeFrac = Math.min(1f, slopeFrac);
            slopeSpeedFactor = 1f - slopeFrac * 0.6f;
            slopeStaminaDrain = slopeFrac * state.staminaDrainRate * SLOPE_STAMINA_DRAIN_SCALE;
        }

        float forceMult = state.isGrounded ? GROUND_FORCE : AIR_FORCE;

        // CHANGED: block sprint while prone
        boolean wantsSprint = input.sprint && state.currentStamina > 0 && !input.crouch && !state.isProne;
        state.isSprinting = wantsSprint && state.isGrounded && len > 0.001f;
        // CHANGED: block crouch while prone (hold-state from CTRL)
        state.isCrouching = input.crouch && !state.isProne;

        float targetSpeed = WALK_SPEED;
        if (state.isSprinting) targetSpeed = SPRINT_SPEED;
        if (state.isCrouching) targetSpeed = CROUCH_SPEED;
        if (state.isProne) targetSpeed = PRONE_SPEED;   // NEW: prone overrides crouch

        targetSpeed *= slopeSpeedFactor;
        if (state.isExhausted) targetSpeed *= EXHAUSTED_SPEED_MULTIPLIER;

        Vector3 currentVel = physics.body.getLinearVelocity();
        float upComponent = currentVel.dot(localUp);
        float currentHorizSpeed = (float) Math.sqrt(
            currentVel.len2() - upComponent * upComponent);

        if (len > 0.001f && currentHorizSpeed < targetSpeed) {
            if (state.isGrounded && slopeAngle > 1f) {
                projectOnPlane(tempVec2, state.groundNormal);
                if (tempVec2.len2() > 0.001f) tempVec2.nor();
                float boost = movingUphill ? SLOPE_FORCE_BOOST : 1f;
                tempVec2.scl(forceMult * physics.mass * boost);
            } else {
                tempVec2.scl(forceMult * physics.mass);
            }
            physics.body.applyCentralForce(tempVec2);
        }

        if (state.isGrounded && slopeAngle > 1f) {
            float nDotUp = state.groundNormal.dot(localUp);
            float lateralScale = 1f - nDotUp * nDotUp;
            tempVec3.set(localUp).scl(9.81f * lateralScale * physics.mass);
            physics.body.applyCentralForce(tempVec3);
        }

        float groundDamp = GROUND_DAMPING;
        if (state.isGrounded && slopeAngle > SLOPE_SPEED_PENALTY_START) {
            float slopeFrac = Math.min(1f,
                (slopeAngle - SLOPE_SPEED_PENALTY_START) / (MAX_SLOPE_ANGLE - SLOPE_SPEED_PENALTY_START));
            groundDamp = GROUND_DAMPING - slopeFrac * (GROUND_DAMPING - SLOPE_DAMPING_MIN);
        }
        physics.body.setDamping(
            state.isGrounded ? groundDamp : AIR_DAMPING, 0f);

        // CHANGED: block jump while prone
        if (input.jumpRequested && state.isGrounded && !state.isProne) {
            tempVec3.set(localUp).scl(JUMP_IMPULSE * physics.mass);
            physics.body.applyCentralImpulse(tempVec3);
            state.isGrounded = false;
        }
        input.jumpRequested = false;
```

- [ ] **Step 6: Run the new prone movement tests**

```
./gradlew :core:test --tests "com.galacticodyssey.player.systems.ProneMovementTest" 2>&1 | tail -20
```
Expected: all 6 tests PASS.

- [ ] **Step 7: Run all player movement tests to check for regressions**

```
./gradlew :core:test --tests "com.galacticodyssey.player.systems.PlayerMovementSystemTest" 2>&1 | tail -15
```
Expected: all existing tests PASS.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java \
        core/src/test/java/com/galacticodyssey/player/systems/ProneMovementTest.java
git commit -m "feat(player): implement prone toggle with capsule swap, clearance check, and speed cap"
```

---

## Task 5: CameraSystem — three-way eye height + reduced head bob while prone

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/player/systems/ProneStateTest.java`

- [ ] **Step 1: Write failing tests**

Add to `ProneStateTest.java`:

```java
import com.galacticodyssey.player.components.FPSCameraComponent;

@Test
void eyeHeightTargetWhenProne() {
    MovementStateComponent state = new MovementStateComponent();
    FPSCameraComponent cam = new FPSCameraComponent();
    state.isProne = true;

    float target = state.isProne ? cam.proneEyeHeight
                 : state.isCrouching ? cam.crouchEyeHeight
                 : cam.eyeHeight;

    assertEquals(0.3f, target, 0.001f);
}

@Test
void eyeHeightTargetWhenCrouching() {
    MovementStateComponent state = new MovementStateComponent();
    FPSCameraComponent cam = new FPSCameraComponent();
    state.isCrouching = true;

    float target = state.isProne ? cam.proneEyeHeight
                 : state.isCrouching ? cam.crouchEyeHeight
                 : cam.eyeHeight;

    assertEquals(1.0f, target, 0.001f);
}

@Test
void eyeHeightTargetWhenStanding() {
    MovementStateComponent state = new MovementStateComponent();
    FPSCameraComponent cam = new FPSCameraComponent();

    float target = state.isProne ? cam.proneEyeHeight
                 : state.isCrouching ? cam.crouchEyeHeight
                 : cam.eyeHeight;

    assertEquals(1.7f, target, 0.001f);
}
```

- [ ] **Step 2: Run tests to confirm they pass** (these test the logic expression, not yet CameraSystem)

```
./gradlew :core:test --tests "com.galacticodyssey.player.systems.ProneStateTest" 2>&1 | tail -15
```
Expected: all tests PASS (the expression evaluates correctly with existing fields).

- [ ] **Step 3: Update CameraSystem — eye height selection**

In `CameraSystem.java`, replace line 79:

```java
        float targetEyeHeight = state.isCrouching ? cam.crouchEyeHeight : cam.eyeHeight;
```

with:

```java
        float targetEyeHeight;
        if (state.isProne) targetEyeHeight = cam.proneEyeHeight;
        else if (state.isCrouching) targetEyeHeight = cam.crouchEyeHeight;
        else targetEyeHeight = cam.eyeHeight;
```

- [ ] **Step 4: Update CameraSystem — halve head-bob amplitude while prone**

In `CameraSystem.java`, replace the head-bob block (lines 87–98):

```java
        if (state.isGrounded && state.currentSpeed > HEAD_BOB_MIN_SPEED) {
            cam.headBobPhase += state.currentSpeed * cam.headBobFrequency * deltaTime;
            float speedRatio = state.currentSpeed / WALK_SPEED_REF;
            float vOffset = MathUtils.sin(cam.headBobPhase) * cam.headBobAmplitude * speedRatio;
            float hOffset = MathUtils.cos(cam.headBobPhase * 0.5f) * cam.headBobAmplitude * 0.5f;
```

with:

```java
        if (state.isGrounded && state.currentSpeed > HEAD_BOB_MIN_SPEED) {
            cam.headBobPhase += state.currentSpeed * cam.headBobFrequency * deltaTime;
            float speedRatio = state.currentSpeed / WALK_SPEED_REF;
            float bobAmplitude = state.isProne ? cam.headBobAmplitude * 0.5f : cam.headBobAmplitude;
            float vOffset = MathUtils.sin(cam.headBobPhase) * bobAmplitude * speedRatio;
            float hOffset = MathUtils.cos(cam.headBobPhase * 0.5f) * bobAmplitude * 0.5f;
```

- [ ] **Step 5: Run full test suite for the player package**

```
./gradlew :core:test --tests "com.galacticodyssey.player.*" 2>&1 | tail -20
```
Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java \
        core/src/test/java/com/galacticodyssey/player/systems/ProneStateTest.java
git commit -m "feat(player): camera uses three-way eye height and halves head bob while prone"
```

---

## Task 6: GameWorld — pre-allocate prone shape + wire EventBus to PlayerMovementSystem

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Pre-allocate proneShape in createPlayerEntity**

In `GameWorld.java`, after line 879:

```java
        physics.shape = new btCapsuleShape(0.3f, 1.2f);
```

add:

```java
        physics.proneShape = new btCapsuleShape(0.3f, 0.0f);
```

- [ ] **Step 2: Dispose proneShape alongside the standing shape**

In the disposable lambda (around line 928), change:

```java
        disposables.add(() -> {
            bulletPhysicsSystem.getDynamicsWorld().removeRigidBody(physics.body);
            physics.body.dispose();
            physics.shape.dispose();
        });
```

to:

```java
        disposables.add(() -> {
            bulletPhysicsSystem.getDynamicsWorld().removeRigidBody(physics.body);
            physics.body.dispose();
            physics.shape.dispose();
            physics.proneShape.dispose();
        });
```

- [ ] **Step 3: Wire EventBus into PlayerMovementSystem**

In `GameWorld.java`, after line 344:

```java
        playerMovementSystem = new PlayerMovementSystem(bulletPhysicsSystem.getDynamicsWorld());
```

add:

```java
        playerMovementSystem.setEventBus(eventBus);
```

- [ ] **Step 4: Run the full core test suite**

```
./gradlew :core:test 2>&1 | tail -20
```
Expected: all tests PASS, zero failures.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(player): pre-allocate prone capsule shape and wire EventBus to PlayerMovementSystem"
```
