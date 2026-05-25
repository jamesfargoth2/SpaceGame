# FPS Movement System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a playable first-person character controller on procedural terrain with floating origin, Bullet physics, Ashley ECS, and a debug HUD.

**Architecture:** Pure Ashley ECS — components hold data, systems run logic. A `GameWorld` class bootstraps the Ashley engine, Bullet physics world, EventBus, and CoordinateManager. The player is a dynamic Bullet rigid body (capsule shape) with forces applied by `PlayerMovementSystem`. Camera, input, and HUD are separate systems reading shared components.

**Tech Stack:** Java 21, libGDX 1.13.5, Ashley 1.7.4 (ECS), gdx-bullet (Bullet physics), Scene2D (HUD), JUnit 5 (testing)

**Base package:** `com.galacticodyssey` under `core/src/main/java/com/galacticodyssey/`
**Test package:** `com.galacticodyssey` under `core/src/test/java/com/galacticodyssey/`

---

### Task 1: EventBus

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/EventBus.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/OriginRebasedEvent.java`
- Create: `core/src/test/java/com/galacticodyssey/core/EventBusTest.java`

- [ ] **Step 1: Write the OriginRebasedEvent class**

```java
// core/src/main/java/com/galacticodyssey/core/events/OriginRebasedEvent.java
package com.galacticodyssey.core.events;

public final class OriginRebasedEvent {
    public final float deltaX;
    public final float deltaY;
    public final float deltaZ;

    public OriginRebasedEvent(float deltaX, float deltaY, float deltaZ) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.deltaZ = deltaZ;
    }
}
```

- [ ] **Step 2: Write failing tests for EventBus**

```java
// core/src/test/java/com/galacticodyssey/core/EventBusTest.java
package com.galacticodyssey.core;

import com.galacticodyssey.core.events.OriginRebasedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
    }

    @Test
    void publishDeliversToSubscriber() {
        List<OriginRebasedEvent> received = new ArrayList<>();
        eventBus.subscribe(OriginRebasedEvent.class, received::add);

        var event = new OriginRebasedEvent(1f, 2f, 3f);
        eventBus.publish(event);

        assertEquals(1, received.size());
        assertSame(event, received.get(0));
    }

    @Test
    void publishDeliversToMultipleSubscribers() {
        List<String> order = new ArrayList<>();
        eventBus.subscribe(OriginRebasedEvent.class, e -> order.add("first"));
        eventBus.subscribe(OriginRebasedEvent.class, e -> order.add("second"));

        eventBus.publish(new OriginRebasedEvent(0, 0, 0));

        assertEquals(List.of("first", "second"), order);
    }

    @Test
    void unsubscribeStopsDelivery() {
        List<OriginRebasedEvent> received = new ArrayList<>();
        EventBus.EventListener<OriginRebasedEvent> listener = received::add;
        eventBus.subscribe(OriginRebasedEvent.class, listener);
        eventBus.unsubscribe(OriginRebasedEvent.class, listener);

        eventBus.publish(new OriginRebasedEvent(1, 2, 3));

        assertTrue(received.isEmpty());
    }

    @Test
    void publishWithNoSubscribersDoesNotThrow() {
        assertDoesNotThrow(() -> eventBus.publish(new OriginRebasedEvent(0, 0, 0)));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.EventBusTest" --info`
Expected: Compilation failure — `EventBus` class does not exist yet.

- [ ] **Step 4: Implement EventBus**

```java
// core/src/main/java/com/galacticodyssey/core/EventBus.java
package com.galacticodyssey.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EventBus {

    @FunctionalInterface
    public interface EventListener<T> {
        void onEvent(T event);
    }

    private final Map<Class<?>, List<EventListener<?>>> listeners = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, EventListener<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    }

    public <T> void unsubscribe(Class<T> eventType, EventListener<T> listener) {
        var list = listeners.get(eventType);
        if (list != null) {
            list.remove(listener);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        var list = listeners.get(event.getClass());
        if (list != null) {
            for (var listener : list) {
                ((EventListener<T>) listener).onEvent(event);
            }
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.EventBusTest" --info`
Expected: All 4 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/EventBus.java \
        core/src/main/java/com/galacticodyssey/core/events/OriginRebasedEvent.java \
        core/src/test/java/com/galacticodyssey/core/EventBusTest.java
git commit -m "feat: add EventBus with synchronous pub/sub and OriginRebasedEvent"
```

---

### Task 2: CoordinateManager

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/CoordinateManager.java`
- Create: `core/src/test/java/com/galacticodyssey/core/CoordinateManagerTest.java`

- [ ] **Step 1: Write failing tests for CoordinateManager**

```java
// core/src/test/java/com/galacticodyssey/core/CoordinateManagerTest.java
package com.galacticodyssey.core;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.events.OriginRebasedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoordinateManagerTest {

    private EventBus eventBus;
    private CoordinateManager coordManager;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        coordManager = new CoordinateManager(eventBus);
    }

    @Test
    void toLocalSpaceAtOriginReturnsZero() {
        Vector3 local = coordManager.toLocalSpace(0, 0, 0);
        assertEquals(0f, local.x, 0.001f);
        assertEquals(0f, local.y, 0.001f);
        assertEquals(0f, local.z, 0.001f);
    }

    @Test
    void toLocalSpaceReturnsOffsetFromOrigin() {
        Vector3 local = coordManager.toLocalSpace(100.0, 200.0, 300.0);
        assertEquals(100f, local.x, 0.001f);
        assertEquals(200f, local.y, 0.001f);
        assertEquals(300f, local.z, 0.001f);
    }

    @Test
    void toGalaxySpaceInvertsToLocalSpace() {
        Vector3 local = new Vector3(50f, 60f, 70f);
        double[] galaxy = coordManager.toGalaxySpace(local);
        assertEquals(50.0, galaxy[0], 0.001);
        assertEquals(60.0, galaxy[1], 0.001);
        assertEquals(70.0, galaxy[2], 0.001);
    }

    @Test
    void rebasePreservesDoublePrecision() {
        double farX = 1_000_000_000.123;
        double farY = 0;
        double farZ = 0;

        Vector3 local = coordManager.toLocalSpace(farX, farY, farZ);
        coordManager.checkRebase(local);

        double[] galaxy = coordManager.toGalaxySpace(new Vector3(0, 0, 0));
        assertEquals(farX, galaxy[0], 0.01);
    }

    @Test
    void checkRebaseBelowThresholdDoesNotFire() {
        List<OriginRebasedEvent> events = new ArrayList<>();
        eventBus.subscribe(OriginRebasedEvent.class, events::add);

        coordManager.checkRebase(new Vector3(500f, 0f, 0f));

        assertTrue(events.isEmpty());
    }

    @Test
    void checkRebaseAboveThresholdFiresEvent() {
        List<OriginRebasedEvent> events = new ArrayList<>();
        eventBus.subscribe(OriginRebasedEvent.class, events::add);

        coordManager.checkRebase(new Vector3(1500f, 0f, 200f));

        assertEquals(1, events.size());
        assertEquals(1500f, events.get(0).deltaX, 0.001f);
        assertEquals(0f, events.get(0).deltaY, 0.001f);
        assertEquals(200f, events.get(0).deltaZ, 0.001f);
    }

    @Test
    void afterRebaseLocalSpaceShifts() {
        coordManager.checkRebase(new Vector3(2000f, 0f, 0f));

        Vector3 local = coordManager.toLocalSpace(2000.0, 0, 0);
        assertEquals(0f, local.x, 0.01f);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.CoordinateManagerTest" --info`
Expected: Compilation failure — `CoordinateManager` class does not exist yet.

- [ ] **Step 3: Implement CoordinateManager**

```java
// core/src/main/java/com/galacticodyssey/core/CoordinateManager.java
package com.galacticodyssey.core;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.events.OriginRebasedEvent;

public final class CoordinateManager {

    private static final float REBASE_THRESHOLD = 1000f;

    private final EventBus eventBus;

    private double originOffsetX;
    private double originOffsetY;
    private double originOffsetZ;

    public CoordinateManager(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public Vector3 toLocalSpace(double galaxyX, double galaxyY, double galaxyZ) {
        return new Vector3(
            (float) (galaxyX - originOffsetX),
            (float) (galaxyY - originOffsetY),
            (float) (galaxyZ - originOffsetZ)
        );
    }

    public double[] toGalaxySpace(Vector3 local) {
        return new double[]{
            local.x + originOffsetX,
            local.y + originOffsetY,
            local.z + originOffsetZ
        };
    }

    public void checkRebase(Vector3 playerLocalPos) {
        if (playerLocalPos.len() > REBASE_THRESHOLD) {
            float dx = playerLocalPos.x;
            float dy = playerLocalPos.y;
            float dz = playerLocalPos.z;

            originOffsetX += dx;
            originOffsetY += dy;
            originOffsetZ += dz;

            eventBus.publish(new OriginRebasedEvent(dx, dy, dz));
        }
    }

    public double getOriginOffsetX() { return originOffsetX; }
    public double getOriginOffsetY() { return originOffsetY; }
    public double getOriginOffsetZ() { return originOffsetZ; }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.CoordinateManagerTest" --info`
Expected: All 7 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/CoordinateManager.java \
        core/src/test/java/com/galacticodyssey/core/CoordinateManagerTest.java
git commit -m "feat: add CoordinateManager with floating origin rebase"
```

---

### Task 3: Core ECS Components

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/components/TransformComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/components/PhysicsBodyComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/components/PlayerTagComponent.java`

- [ ] **Step 1: Create TransformComponent**

```java
// core/src/main/java/com/galacticodyssey/core/components/TransformComponent.java
package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

public class TransformComponent implements Component {
    public final Vector3 position = new Vector3();
    public final Quaternion rotation = new Quaternion();
}
```

- [ ] **Step 2: Create PhysicsBodyComponent**

```java
// core/src/main/java/com/galacticodyssey/core/components/PhysicsBodyComponent.java
package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;

public class PhysicsBodyComponent implements Component {
    public btRigidBody body;
    public btCollisionShape shape;
    public float mass;
    public float friction = 0.5f;
    public float restitution = 0f;
    public short collisionGroup = 1;
    public short collisionMask = -1;
    public boolean rebaseOnOriginShift = true;
}
```

- [ ] **Step 3: Create PlayerTagComponent**

```java
// core/src/main/java/com/galacticodyssey/core/components/PlayerTagComponent.java
package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;

public class PlayerTagComponent implements Component {
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/components/TransformComponent.java \
        core/src/main/java/com/galacticodyssey/core/components/PhysicsBodyComponent.java \
        core/src/main/java/com/galacticodyssey/core/components/PlayerTagComponent.java
git commit -m "feat: add core ECS components (Transform, PhysicsBody, PlayerTag)"
```

---

### Task 4: BulletPhysicsSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/systems/BulletPhysicsSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/core/systems/BulletPhysicsSystemTest.java`

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/core/systems/BulletPhysicsSystemTest.java
package com.galacticodyssey.core.systems;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btSphereShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.OriginRebasedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BulletPhysicsSystemTest {

    private EventBus eventBus;
    private BulletPhysicsSystem physicsSystem;

    @BeforeAll
    static void initBullet() {
        Bullet.init();
    }

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        physicsSystem = new BulletPhysicsSystem(eventBus);
        physicsSystem.initialize();
    }

    @AfterEach
    void tearDown() {
        physicsSystem.dispose();
    }

    @Test
    void worldHasGravity() {
        Vector3 gravity = new Vector3();
        physicsSystem.getDynamicsWorld().getGravity(gravity);
        assertEquals(-9.81f, gravity.y, 0.01f);
        assertEquals(0f, gravity.x, 0.01f);
        assertEquals(0f, gravity.z, 0.01f);
    }

    @Test
    void dynamicBodyFallsUnderGravity() {
        btCollisionShape shape = new btSphereShape(0.5f);
        Vector3 inertia = new Vector3();
        shape.calculateLocalInertia(1f, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(1f, null, shape, inertia);
        btRigidBody body = new btRigidBody(info);
        body.setWorldTransform(new Matrix4().setToTranslation(0, 10, 0));

        physicsSystem.getDynamicsWorld().addRigidBody(body);

        for (int i = 0; i < 60; i++) {
            physicsSystem.stepWorld(1f / 60f);
        }

        Matrix4 transform = new Matrix4();
        body.getWorldTransform(transform);
        Vector3 pos = new Vector3();
        transform.getTranslation(pos);

        assertTrue(pos.y < 10f, "Body should have fallen from y=10, actual y=" + pos.y);

        physicsSystem.getDynamicsWorld().removeRigidBody(body);
        body.dispose();
        info.dispose();
        shape.dispose();
    }

    @Test
    void originRebaseShiftsBodyTransforms() {
        btCollisionShape shape = new btBoxShape(new Vector3(1, 1, 1));
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, shape);
        btRigidBody body = new btRigidBody(info);
        body.setWorldTransform(new Matrix4().setToTranslation(100, 0, 0));

        physicsSystem.getDynamicsWorld().addRigidBody(body);
        physicsSystem.addManagedBody(body);

        eventBus.publish(new OriginRebasedEvent(100f, 0f, 0f));

        Matrix4 transform = new Matrix4();
        body.getWorldTransform(transform);
        Vector3 pos = new Vector3();
        transform.getTranslation(pos);

        assertEquals(0f, pos.x, 0.01f);

        physicsSystem.getDynamicsWorld().removeRigidBody(body);
        physicsSystem.removeManagedBody(body);
        body.dispose();
        info.dispose();
        shape.dispose();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.BulletPhysicsSystemTest" --info`
Expected: Compilation failure — `BulletPhysicsSystem` class does not exist.

- [ ] **Step 3: Implement BulletPhysicsSystem**

```java
// core/src/main/java/com/galacticodyssey/core/systems/BulletPhysicsSystem.java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.OriginRebasedEvent;

public class BulletPhysicsSystem extends EntitySystem implements Disposable {

    private static final float FIXED_TIMESTEP = 1f / 60f;
    private static final int MAX_SUBSTEPS = 3;

    private final EventBus eventBus;
    private final Array<btRigidBody> managedBodies = new Array<>();
    private final EventBus.EventListener<OriginRebasedEvent> rebaseListener = this::onOriginRebased;

    private btCollisionConfiguration collisionConfig;
    private btCollisionDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;
    private btDiscreteDynamicsWorld dynamicsWorld;

    private final Vector3 tempVec = new Vector3();
    private final Matrix4 tempMat = new Matrix4();

    public BulletPhysicsSystem(EventBus eventBus) {
        super(2);
        this.eventBus = eventBus;
    }

    public void initialize() {
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3(0, -9.81f, 0));

        eventBus.subscribe(OriginRebasedEvent.class, rebaseListener);
    }

    @Override
    public void update(float deltaTime) {
        stepWorld(deltaTime);
    }

    public void stepWorld(float deltaTime) {
        dynamicsWorld.stepSimulation(deltaTime, MAX_SUBSTEPS, FIXED_TIMESTEP);
    }

    private void onOriginRebased(OriginRebasedEvent event) {
        for (int i = 0; i < managedBodies.size; i++) {
            btRigidBody body = managedBodies.get(i);
            body.getWorldTransform(tempMat);
            tempMat.getTranslation(tempVec);
            tempVec.sub(event.deltaX, event.deltaY, event.deltaZ);
            tempMat.setTranslation(tempVec);
            body.setWorldTransform(tempMat);
        }
    }

    public void addManagedBody(btRigidBody body) {
        managedBodies.add(body);
    }

    public void removeManagedBody(btRigidBody body) {
        managedBodies.removeValue(body, true);
    }

    public btDiscreteDynamicsWorld getDynamicsWorld() {
        return dynamicsWorld;
    }

    @Override
    public void dispose() {
        eventBus.unsubscribe(OriginRebasedEvent.class, rebaseListener);
        if (dynamicsWorld != null) dynamicsWorld.dispose();
        if (solver != null) solver.dispose();
        if (broadphase != null) broadphase.dispose();
        if (dispatcher != null) dispatcher.dispose();
        if (collisionConfig != null) collisionConfig.dispose();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.BulletPhysicsSystemTest" --info`
Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/systems/BulletPhysicsSystem.java \
        core/src/test/java/com/galacticodyssey/core/systems/BulletPhysicsSystemTest.java
git commit -m "feat: add BulletPhysicsSystem with dynamics world and origin rebase"
```

---

### Task 5: PhysicsBodySystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/systems/PhysicsBodySystem.java`
- Create: `core/src/test/java/com/galacticodyssey/core/systems/PhysicsBodySystemTest.java`

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/core/systems/PhysicsBodySystemTest.java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhysicsBodySystemTest {

    private Engine engine;
    private PhysicsBodySystem physicsBodySystem;
    private btBoxShape shape;
    private btRigidBody.btRigidBodyConstructionInfo constructionInfo;

    @BeforeAll
    static void initBullet() {
        Bullet.init();
    }

    @BeforeEach
    void setUp() {
        engine = new Engine();
        physicsBodySystem = new PhysicsBodySystem();
        engine.addSystem(physicsBodySystem);

        shape = new btBoxShape(new Vector3(1, 1, 1));
        constructionInfo = new btRigidBody.btRigidBodyConstructionInfo(0f, null, shape);
    }

    @AfterEach
    void tearDown() {
        engine.removeAllEntities();
        engine.removeAllSystems();
        constructionInfo.dispose();
        shape.dispose();
    }

    @Test
    void syncsCopyRigidBodyPositionToTransform() {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        PhysicsBodyComponent physicsBody = new PhysicsBodyComponent();
        physicsBody.body = new btRigidBody(constructionInfo);
        physicsBody.body.setWorldTransform(new Matrix4().setToTranslation(5f, 10f, 15f));
        physicsBody.shape = shape;

        entity.add(transform);
        entity.add(physicsBody);
        engine.addEntity(entity);

        engine.update(1f / 60f);

        assertEquals(5f, transform.position.x, 0.01f);
        assertEquals(10f, transform.position.y, 0.01f);
        assertEquals(15f, transform.position.z, 0.01f);

        physicsBody.body.dispose();
    }

    @Test
    void syncsCopyRigidBodyRotationToTransform() {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        PhysicsBodyComponent physicsBody = new PhysicsBodyComponent();
        physicsBody.body = new btRigidBody(constructionInfo);
        Matrix4 mat = new Matrix4();
        mat.setToRotation(Vector3.Y, 90f);
        mat.setTranslation(0, 0, 0);
        physicsBody.body.setWorldTransform(mat);
        physicsBody.shape = shape;

        entity.add(transform);
        entity.add(physicsBody);
        engine.addEntity(entity);

        engine.update(1f / 60f);

        float yaw = transform.rotation.getYaw();
        assertEquals(90f, Math.abs(yaw), 1f);

        physicsBody.body.dispose();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.PhysicsBodySystemTest" --info`
Expected: Compilation failure — `PhysicsBodySystem` class does not exist.

- [ ] **Step 3: Implement PhysicsBodySystem**

```java
// core/src/main/java/com/galacticodyssey/core/systems/PhysicsBodySystem.java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;

public class PhysicsBodySystem extends IteratingSystem {

    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private final Matrix4 tempMat = new Matrix4();
    private final Vector3 tempVec = new Vector3();

    public PhysicsBodySystem() {
        super(Family.all(TransformComponent.class, PhysicsBodyComponent.class).get(), 3);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = transformMapper.get(entity);
        PhysicsBodyComponent physics = physicsMapper.get(entity);

        if (physics.body == null) return;

        physics.body.getWorldTransform(tempMat);
        tempMat.getTranslation(transform.position);
        tempMat.getRotation(transform.rotation);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.PhysicsBodySystemTest" --info`
Expected: All 2 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/systems/PhysicsBodySystem.java \
        core/src/test/java/com/galacticodyssey/core/systems/PhysicsBodySystemTest.java
git commit -m "feat: add PhysicsBodySystem to sync Bullet transforms to ECS"
```

---

### Task 6: Player Components

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/components/PlayerInputComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/player/components/MovementStateComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/player/components/FPSCameraComponent.java`

- [ ] **Step 1: Create PlayerInputComponent**

```java
// core/src/main/java/com/galacticodyssey/player/components/PlayerInputComponent.java
package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;

public class PlayerInputComponent implements Component {
    public float moveForward;
    public float moveStrafe;
    public boolean sprint;
    public boolean jumpRequested;
    public boolean crouch;
    public float mouseDeltaX;
    public float mouseDeltaY;
}
```

- [ ] **Step 2: Create MovementStateComponent**

```java
// core/src/main/java/com/galacticodyssey/player/components/MovementStateComponent.java
package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

public class MovementStateComponent implements Component {
    public boolean isGrounded;
    public boolean isSprinting;
    public boolean isCrouching;
    public float currentSpeed;
    public float currentStamina = 100f;
    public float maxStamina = 100f;
    public float staminaDrainRate = 20f;
    public float staminaRegenRate = 10f;
    public final Vector3 groundNormal = new Vector3(0, 1, 0);
    public float fallVelocity;
}
```

- [ ] **Step 3: Create FPSCameraComponent**

```java
// core/src/main/java/com/galacticodyssey/player/components/FPSCameraComponent.java
package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;

public class FPSCameraComponent implements Component {
    public float eyeHeight = 1.7f;
    public float crouchEyeHeight = 1.0f;
    public float currentEyeHeight = 1.7f;
    public float headBobAmplitude = 0.04f;
    public float headBobFrequency = 8.0f;
    public float headBobPhase;
    public float pitchAngle;
    public float yawAngle;
    public float mouseSensitivity = 0.15f;
    public float landingDipAmount;
}
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/components/PlayerInputComponent.java \
        core/src/main/java/com/galacticodyssey/player/components/MovementStateComponent.java \
        core/src/main/java/com/galacticodyssey/player/components/FPSCameraComponent.java
git commit -m "feat: add player ECS components (Input, MovementState, FPSCamera)"
```

---

### Task 7: PlayerInputSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java`

This system requires a GL context (calls `Gdx.input`), so it cannot be unit tested headlessly. It will be tested manually via the test scene.

- [ ] **Step 1: Implement PlayerInputSystem**

```java
// core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;

public class PlayerInputSystem extends IteratingSystem {

    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);

    private float accumulatedMouseDeltaX;
    private float accumulatedMouseDeltaY;
    private boolean jumpPressed;
    private boolean cursorCatched = true;

    private final InputAdapter inputAdapter = new InputAdapter() {
        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            accumulatedMouseDeltaX += -Gdx.input.getDeltaX();
            accumulatedMouseDeltaY += -Gdx.input.getDeltaY();
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            accumulatedMouseDeltaX += -Gdx.input.getDeltaX();
            accumulatedMouseDeltaY += -Gdx.input.getDeltaY();
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.SPACE) {
                jumpPressed = true;
                return true;
            }
            if (keycode == Input.Keys.ESCAPE) {
                cursorCatched = !cursorCatched;
                Gdx.input.setCursorCatched(cursorCatched);
                return true;
            }
            return false;
        }
    };

    public PlayerInputSystem() {
        super(Family.all(PlayerInputComponent.class, PlayerTagComponent.class).get(), 0);
    }

    public void initialize() {
        Gdx.input.setInputProcessor(inputAdapter);
        Gdx.input.setCursorCatched(true);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerInputComponent input = inputMapper.get(entity);

        input.moveForward = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) input.moveForward += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) input.moveForward -= 1f;

        input.moveStrafe = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) input.moveStrafe -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) input.moveStrafe += 1f;

        input.sprint = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT);
        input.crouch = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT);

        if (jumpPressed) {
            input.jumpRequested = true;
            jumpPressed = false;
        }

        input.mouseDeltaX = accumulatedMouseDeltaX;
        input.mouseDeltaY = accumulatedMouseDeltaY;
        accumulatedMouseDeltaX = 0;
        accumulatedMouseDeltaY = 0;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/PlayerInputSystem.java
git commit -m "feat: add PlayerInputSystem for WASD + mouse look input capture"
```

---

### Task 8: PlayerMovementSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/player/systems/PlayerMovementSystemTest.java`

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/player/systems/PlayerMovementSystemTest.java
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerMovementSystemTest {

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
    static void initBullet() {
        Bullet.init();
    }

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

    @Test
    void playerOnGroundIsGrounded() {
        Entity player = createPlayerEntity(1.0f);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);

        for (int i = 0; i < 120; i++) {
            engine.update(1f / 60f);
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
        }

        engine.update(1f / 60f);
        assertTrue(state.isGrounded, "Player resting on ground should be grounded");

        PhysicsBodyComponent physics = player.getComponent(PhysicsBodyComponent.class);
        dynamicsWorld.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
    }

    @Test
    void forwardInputAppliesForce() {
        Entity player = createPlayerEntity(1.0f);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        PhysicsBodyComponent physics = player.getComponent(PhysicsBodyComponent.class);

        for (int i = 0; i < 120; i++) {
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
            engine.update(1f / 60f);
        }

        input.moveForward = 1f;

        for (int i = 0; i < 30; i++) {
            engine.update(1f / 60f);
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
        }

        Vector3 velocity = physics.body.getLinearVelocity();
        float horizontalSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        assertTrue(horizontalSpeed > 0.1f,
            "Player should be moving horizontally, speed=" + horizontalSpeed);

        dynamicsWorld.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
    }

    @Test
    void staminaDrainsWhileSprinting() {
        Entity player = createPlayerEntity(1.0f);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);
        PhysicsBodyComponent physics = player.getComponent(PhysicsBodyComponent.class);

        for (int i = 0; i < 120; i++) {
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
            engine.update(1f / 60f);
        }

        float staminaBefore = state.currentStamina;
        input.moveForward = 1f;
        input.sprint = true;

        for (int i = 0; i < 60; i++) {
            engine.update(1f / 60f);
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
        }

        assertTrue(state.currentStamina < staminaBefore,
            "Stamina should drain while sprinting");

        dynamicsWorld.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
    }

    @Test
    void jumpAppliesUpwardImpulse() {
        Entity player = createPlayerEntity(1.0f);
        PlayerInputComponent input = player.getComponent(PlayerInputComponent.class);
        PhysicsBodyComponent physics = player.getComponent(PhysicsBodyComponent.class);
        MovementStateComponent state = player.getComponent(MovementStateComponent.class);

        for (int i = 0; i < 120; i++) {
            dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);
            engine.update(1f / 60f);
        }

        input.jumpRequested = true;
        engine.update(1f / 60f);
        dynamicsWorld.stepSimulation(1f / 60f, 3, 1f / 60f);

        Vector3 velocity = physics.body.getLinearVelocity();
        assertTrue(velocity.y > 0f, "Player should have upward velocity after jump, vy=" + velocity.y);

        assertFalse(input.jumpRequested, "Jump flag should be consumed");

        dynamicsWorld.removeRigidBody(physics.body);
        physics.body.dispose();
        physics.shape.dispose();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.systems.PlayerMovementSystemTest" --info`
Expected: Compilation failure — `PlayerMovementSystem` class does not exist.

- [ ] **Step 3: Implement PlayerMovementSystem**

```java
// core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;

public class PlayerMovementSystem extends IteratingSystem {

    private static final float WALK_SPEED = 3.5f;
    private static final float SPRINT_SPEED = 6.0f;
    private static final float CROUCH_SPEED = 1.5f;
    private static final float JUMP_IMPULSE = 5.0f;
    private static final float GROUND_FORCE = 50f;
    private static final float AIR_FORCE = 10f;
    private static final float GROUND_DAMPING = 0.9f;
    private static final float AIR_DAMPING = 0.1f;
    private static final float MAX_SLOPE_ANGLE = 45f;
    private static final float GROUND_RAY_EXTRA = 0.15f;
    private static final float CAPSULE_HALF_HEIGHT = 0.9f;

    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);
    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<MovementStateComponent> stateMapper =
        ComponentMapper.getFor(MovementStateComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<FPSCameraComponent> cameraMapper =
        ComponentMapper.getFor(FPSCameraComponent.class);

    private final btDiscreteDynamicsWorld dynamicsWorld;

    private final Vector3 tempVec = new Vector3();
    private final Vector3 tempVec2 = new Vector3();
    private final Vector3 rayFrom = new Vector3();
    private final Vector3 rayTo = new Vector3();
    private final Matrix4 tempMat = new Matrix4();

    public PlayerMovementSystem(btDiscreteDynamicsWorld dynamicsWorld) {
        super(Family.all(
            PlayerInputComponent.class,
            PhysicsBodyComponent.class,
            MovementStateComponent.class,
            TransformComponent.class
        ).get(), 1);
        this.dynamicsWorld = dynamicsWorld;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerInputComponent input = inputMapper.get(entity);
        PhysicsBodyComponent physics = physicsMapper.get(entity);
        MovementStateComponent state = stateMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);
        FPSCameraComponent cam = cameraMapper.get(entity);

        if (physics.body == null) return;

        physics.body.getWorldTransform(tempMat);
        tempMat.getTranslation(tempVec);

        boolean wasGrounded = state.isGrounded;
        performGroundCheck(physics, state, tempVec);

        if (cam != null) {
            cam.yawAngle += input.mouseDeltaX * cam.mouseSensitivity;
            cam.pitchAngle += input.mouseDeltaY * cam.mouseSensitivity;
            cam.pitchAngle = MathUtils.clamp(cam.pitchAngle, -85f, 85f);

            tempMat.setToRotation(Vector3.Y, cam.yawAngle);
            tempMat.setTranslation(tempVec);
            physics.body.setWorldTransform(tempMat);
        }

        float yawRad = cam != null ? cam.yawAngle * MathUtils.degreesToRadians : 0;
        float forwardX = -MathUtils.sin(yawRad);
        float forwardZ = -MathUtils.cos(yawRad);
        float rightX = MathUtils.cos(yawRad);
        float rightZ = -MathUtils.sin(yawRad);

        float dirX = forwardX * input.moveForward + rightX * input.moveStrafe;
        float dirZ = forwardZ * input.moveForward + rightZ * input.moveStrafe;
        float len = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len > 0.001f) {
            dirX /= len;
            dirZ /= len;
        }

        float forceMult = state.isGrounded ? GROUND_FORCE : AIR_FORCE;

        boolean wantsSprint = input.sprint && state.currentStamina > 0 && !input.crouch;
        state.isSprinting = wantsSprint && state.isGrounded && len > 0.001f;
        state.isCrouching = input.crouch;

        float targetSpeed = WALK_SPEED;
        if (state.isSprinting) targetSpeed = SPRINT_SPEED;
        if (state.isCrouching) targetSpeed = CROUCH_SPEED;

        Vector3 currentVel = physics.body.getLinearVelocity();
        float currentHorizSpeed = (float) Math.sqrt(currentVel.x * currentVel.x + currentVel.z * currentVel.z);

        if (len > 0.001f && currentHorizSpeed < targetSpeed) {
            tempVec2.set(dirX * forceMult * physics.mass, 0, dirZ * forceMult * physics.mass);
            physics.body.applyCentralForce(tempVec2);
        }

        physics.body.setDamping(
            state.isGrounded ? GROUND_DAMPING : AIR_DAMPING, 0f);

        if (input.jumpRequested && state.isGrounded) {
            tempVec2.set(0, JUMP_IMPULSE * physics.mass, 0);
            physics.body.applyCentralImpulse(tempVec2);
            state.isGrounded = false;
        }
        input.jumpRequested = false;

        if (state.isSprinting) {
            state.currentStamina -= state.staminaDrainRate * deltaTime;
            if (state.currentStamina <= 0) {
                state.currentStamina = 0;
                state.isSprinting = false;
            }
        } else {
            state.currentStamina = Math.min(state.maxStamina,
                state.currentStamina + state.staminaRegenRate * deltaTime);
        }

        if (!wasGrounded && state.isGrounded) {
            state.fallVelocity = Math.abs(currentVel.y);
        } else if (!state.isGrounded) {
            state.fallVelocity = Math.abs(currentVel.y);
        }

        state.currentSpeed = currentHorizSpeed;

        physics.body.activate();
    }

    private void performGroundCheck(PhysicsBodyComponent physics, MovementStateComponent state, Vector3 bodyPos) {
        rayFrom.set(bodyPos.x, bodyPos.y, bodyPos.z);
        rayTo.set(bodyPos.x, bodyPos.y - CAPSULE_HALF_HEIGHT - GROUND_RAY_EXTRA, bodyPos.z);

        ClosestRayResultCallback callback = new ClosestRayResultCallback(rayFrom, rayTo);
        dynamicsWorld.rayTest(rayFrom, rayTo, callback);

        if (callback.hasHit()) {
            callback.getHitNormalWorld(tempVec2);
            state.groundNormal.set(tempVec2);
            float angle = (float) Math.toDegrees(Math.acos(
                Math.min(1f, tempVec2.dot(Vector3.Y))));
            state.isGrounded = angle <= MAX_SLOPE_ANGLE;
        } else {
            state.isGrounded = false;
            state.groundNormal.set(0, 1, 0);
        }

        callback.dispose();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.systems.PlayerMovementSystemTest" --info`
Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java \
        core/src/test/java/com/galacticodyssey/player/systems/PlayerMovementSystemTest.java
git commit -m "feat: add PlayerMovementSystem with forces, jumping, stamina, ground detection"
```

---

### Task 9: CameraSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java`

Requires GL context (PerspectiveCamera) — tested manually via the test scene.

- [ ] **Step 1: Implement CameraSystem**

```java
// core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;

public class CameraSystem extends IteratingSystem {

    private static final float EYE_HEIGHT_LERP_SPEED = 10f;
    private static final float LANDING_DIP_DECAY_SPEED = 8f;
    private static final float WALK_SPEED_REF = 3.5f;
    private static final float HEAD_BOB_MIN_SPEED = 0.5f;
    private static final float MAX_LANDING_DIP = 0.15f;
    private static final float LANDING_DIP_FACTOR = 0.02f;

    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<FPSCameraComponent> cameraMapper =
        ComponentMapper.getFor(FPSCameraComponent.class);
    private final ComponentMapper<MovementStateComponent> stateMapper =
        ComponentMapper.getFor(MovementStateComponent.class);

    private PerspectiveCamera camera;
    private boolean wasGrounded;

    public CameraSystem() {
        super(Family.all(TransformComponent.class, FPSCameraComponent.class, MovementStateComponent.class).get(), 4);
    }

    public void setCamera(PerspectiveCamera camera) {
        this.camera = camera;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (camera == null) return;

        TransformComponent transform = transformMapper.get(entity);
        FPSCameraComponent cam = cameraMapper.get(entity);
        MovementStateComponent state = stateMapper.get(entity);

        float targetEyeHeight = state.isCrouching ? cam.crouchEyeHeight : cam.eyeHeight;
        cam.currentEyeHeight = MathUtils.lerp(cam.currentEyeHeight, targetEyeHeight,
            EYE_HEIGHT_LERP_SPEED * deltaTime);

        float camX = transform.position.x;
        float camY = transform.position.y + cam.currentEyeHeight;
        float camZ = transform.position.z;

        if (state.isGrounded && state.currentSpeed > HEAD_BOB_MIN_SPEED) {
            cam.headBobPhase += state.currentSpeed * cam.headBobFrequency * deltaTime;
            float speedRatio = state.currentSpeed / WALK_SPEED_REF;
            float vOffset = MathUtils.sin(cam.headBobPhase) * cam.headBobAmplitude * speedRatio;
            float hOffset = MathUtils.cos(cam.headBobPhase * 0.5f) * cam.headBobAmplitude * 0.5f;
            camY += vOffset;

            float yawRad = cam.yawAngle * MathUtils.degreesToRadians;
            camX += MathUtils.cos(yawRad) * hOffset;
            camZ += -MathUtils.sin(yawRad) * hOffset;
        } else {
            cam.headBobPhase = 0;
        }

        if (!wasGrounded && state.isGrounded) {
            cam.landingDipAmount = Math.min(MAX_LANDING_DIP, state.fallVelocity * LANDING_DIP_FACTOR);
        }
        if (cam.landingDipAmount > 0) {
            camY -= cam.landingDipAmount;
            cam.landingDipAmount = Math.max(0, cam.landingDipAmount - LANDING_DIP_DECAY_SPEED * deltaTime);
        }

        camera.position.set(camX, camY, camZ);

        float pitchRad = cam.pitchAngle * MathUtils.degreesToRadians;
        float yawRad = cam.yawAngle * MathUtils.degreesToRadians;

        camera.direction.set(
            -MathUtils.sin(yawRad) * MathUtils.cos(pitchRad),
            MathUtils.sin(pitchRad),
            -MathUtils.cos(yawRad) * MathUtils.cos(pitchRad)
        ).nor();

        camera.up.set(Vector3.Y);
        camera.update();

        wasGrounded = state.isGrounded;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java
git commit -m "feat: add CameraSystem with head bob, crouch lerp, and landing dip"
```

---

### Task 10: TerrainGenerator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/data/TerrainGenerator.java`
- Create: `core/src/test/java/com/galacticodyssey/data/TerrainGeneratorTest.java`

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/data/TerrainGeneratorTest.java
package com.galacticodyssey.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerrainGeneratorTest {

    @Test
    void heightmapHasCorrectDimensions() {
        float[] heights = TerrainGenerator.generateHeightmap(257, 257, 500f, 500f, 42L);
        assertEquals(257 * 257, heights.length);
    }

    @Test
    void heightmapValuesAreInRange() {
        float[] heights = TerrainGenerator.generateHeightmap(257, 257, 500f, 500f, 42L);
        for (float h : heights) {
            assertTrue(h >= -50f && h <= 50f,
                "Height " + h + " is outside expected range [-50, 50]");
        }
    }

    @Test
    void getHeightAtReturnsInterpolatedValue() {
        float[] heights = TerrainGenerator.generateHeightmap(257, 257, 500f, 500f, 42L);
        float h = TerrainGenerator.getHeightAt(heights, 257, 257, 500f, 500f, 0f, 0f);
        assertFalse(Float.isNaN(h), "Height at center should be a valid number");
    }

    @Test
    void normalsAreUnitLength() {
        float[] heights = TerrainGenerator.generateHeightmap(5, 5, 10f, 10f, 42L);
        float[] normals = TerrainGenerator.computeNormals(heights, 5, 5, 10f, 10f);
        assertEquals(5 * 5 * 3, normals.length);

        for (int i = 0; i < normals.length; i += 3) {
            float len = (float) Math.sqrt(
                normals[i] * normals[i] +
                normals[i + 1] * normals[i + 1] +
                normals[i + 2] * normals[i + 2]);
            assertEquals(1f, len, 0.01f, "Normal at index " + i + " should be unit length");
        }
    }

    @Test
    void differentSeedsProduceDifferentTerrain() {
        float[] h1 = TerrainGenerator.generateHeightmap(33, 33, 100f, 100f, 1L);
        float[] h2 = TerrainGenerator.generateHeightmap(33, 33, 100f, 100f, 2L);

        boolean anyDifferent = false;
        for (int i = 0; i < h1.length; i++) {
            if (Math.abs(h1[i] - h2[i]) > 0.001f) {
                anyDifferent = true;
                break;
            }
        }
        assertTrue(anyDifferent, "Different seeds should produce different terrain");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.data.TerrainGeneratorTest" --info`
Expected: Compilation failure — `TerrainGenerator` class does not exist.

- [ ] **Step 3: Implement TerrainGenerator**

Uses a simple permutation-based simplex noise implementation to avoid adding external dependencies.

```java
// core/src/main/java/com/galacticodyssey/data/TerrainGenerator.java
package com.galacticodyssey.data;

import java.util.Random;

public final class TerrainGenerator {

    private TerrainGenerator() {}

    public static float[] generateHeightmap(int vertsX, int vertsZ, float worldWidth, float worldDepth, long seed) {
        float[] heights = new float[vertsX * vertsZ];
        Random rng = new Random(seed);
        int[] perm = createPermutation(rng);

        float cellWidth = worldWidth / (vertsX - 1);
        float cellDepth = worldDepth / (vertsZ - 1);
        float halfWidth = worldWidth / 2f;
        float halfDepth = worldDepth / 2f;

        for (int z = 0; z < vertsZ; z++) {
            for (int x = 0; x < vertsX; x++) {
                float wx = x * cellWidth - halfWidth;
                float wz = z * cellDepth - halfDepth;
                float h = noise2D(perm, wx * 0.005f, wz * 0.005f) * 30f
                        + noise2D(perm, wx * 0.02f + 100f, wz * 0.02f + 100f) * 5f;
                heights[z * vertsX + x] = h;
            }
        }
        return heights;
    }

    public static float getHeightAt(float[] heights, int vertsX, int vertsZ,
                                     float worldWidth, float worldDepth,
                                     float worldX, float worldZ) {
        float halfW = worldWidth / 2f;
        float halfD = worldDepth / 2f;
        float fx = (worldX + halfW) / worldWidth * (vertsX - 1);
        float fz = (worldZ + halfD) / worldDepth * (vertsZ - 1);

        int ix = Math.max(0, Math.min(vertsX - 2, (int) fx));
        int iz = Math.max(0, Math.min(vertsZ - 2, (int) fz));
        float fracX = fx - ix;
        float fracZ = fz - iz;

        float h00 = heights[iz * vertsX + ix];
        float h10 = heights[iz * vertsX + ix + 1];
        float h01 = heights[(iz + 1) * vertsX + ix];
        float h11 = heights[(iz + 1) * vertsX + ix + 1];

        float h0 = h00 + (h10 - h00) * fracX;
        float h1 = h01 + (h11 - h01) * fracX;
        return h0 + (h1 - h0) * fracZ;
    }

    public static float[] computeNormals(float[] heights, int vertsX, int vertsZ,
                                          float worldWidth, float worldDepth) {
        float[] normals = new float[vertsX * vertsZ * 3];
        float cellW = worldWidth / (vertsX - 1);
        float cellD = worldDepth / (vertsZ - 1);

        for (int z = 0; z < vertsZ; z++) {
            for (int x = 0; x < vertsX; x++) {
                float hL = heights[z * vertsX + Math.max(0, x - 1)];
                float hR = heights[z * vertsX + Math.min(vertsX - 1, x + 1)];
                float hD = heights[Math.max(0, z - 1) * vertsX + x];
                float hU = heights[Math.min(vertsZ - 1, z + 1) * vertsX + x];

                float nx = (hL - hR) / (2f * cellW);
                float nz = (hD - hU) / (2f * cellD);
                float ny = 1f;

                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                int idx = (z * vertsX + x) * 3;
                normals[idx] = nx / len;
                normals[idx + 1] = ny / len;
                normals[idx + 2] = nz / len;
            }
        }
        return normals;
    }

    private static int[] createPermutation(Random rng) {
        int[] p = new int[512];
        int[] base = new int[256];
        for (int i = 0; i < 256; i++) base[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = base[i]; base[i] = base[j]; base[j] = tmp;
        }
        for (int i = 0; i < 512; i++) p[i] = base[i & 255];
        return p;
    }

    private static final float F2 = 0.5f * ((float) Math.sqrt(3.0) - 1.0f);
    private static final float G2 = (3.0f - (float) Math.sqrt(3.0)) / 6.0f;

    private static final int[][] GRAD2 = {
        {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private static float noise2D(int[] perm, float x, float y) {
        float s = (x + y) * F2;
        int i = fastFloor(x + s);
        int j = fastFloor(y + s);
        float t = (i + j) * G2;
        float x0 = x - (i - t);
        float y0 = y - (j - t);

        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; }
        else { i1 = 0; j1 = 1; }

        float x1 = x0 - i1 + G2;
        float y1 = y0 - j1 + G2;
        float x2 = x0 - 1f + 2f * G2;
        float y2 = y0 - 1f + 2f * G2;

        int ii = i & 255;
        int jj = j & 255;

        float n0 = cornerContribution(perm, x0, y0, ii, jj);
        float n1 = cornerContribution(perm, x1, y1, ii + i1, jj + j1);
        float n2 = cornerContribution(perm, x2, y2, ii + 1, jj + 1);

        return 70f * (n0 + n1 + n2);
    }

    private static float cornerContribution(int[] perm, float x, float y, int gi, int gj) {
        float t = 0.5f - x * x - y * y;
        if (t < 0) return 0;
        t *= t;
        int[] g = GRAD2[perm[perm[gi & 255] + (gj & 255)] & 7];
        return t * t * (g[0] * x + g[1] * y);
    }

    private static int fastFloor(float x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.data.TerrainGeneratorTest" --info`
Expected: All 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/TerrainGenerator.java \
        core/src/test/java/com/galacticodyssey/data/TerrainGeneratorTest.java
git commit -m "feat: add TerrainGenerator with simplex noise heightmap and normals"
```

---

### Task 11: DebugHudSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/systems/DebugHudSystem.java`

Requires GL context (Scene2D, BitmapFont, SpriteBatch) — tested manually.

- [ ] **Step 1: Implement DebugHudSystem**

```java
// core/src/main/java/com/galacticodyssey/ui/systems/DebugHudSystem.java
package com.galacticodyssey.ui.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.CoordinateManager;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.MovementStateComponent;

public class DebugHudSystem extends EntitySystem implements Disposable {

    private final CoordinateManager coordinateManager;

    private SpriteBatch batch;
    private Stage stage;
    private BitmapFont font;
    private Label galaxyPosLabel;
    private Label localPosLabel;
    private Label velocityLabel;
    private Label groundLabel;
    private Label stateLabel;
    private Label staminaLabel;
    private Label fpsLabel;

    private boolean visible = true;

    private ImmutableArray<Entity> playerEntities;

    public DebugHudSystem(CoordinateManager coordinateManager) {
        super(10);
        this.coordinateManager = coordinateManager;
    }

    @Override
    public void addedToEngine(Engine engine) {
        playerEntities = engine.getEntitiesFor(
            Family.all(PlayerTagComponent.class, TransformComponent.class, MovementStateComponent.class).get());
    }

    public void initialize() {
        batch = new SpriteBatch();
        stage = new Stage(new ScreenViewport(), batch);
        font = new BitmapFont();
        font.setColor(Color.WHITE);

        Label.LabelStyle style = new Label.LabelStyle(font, Color.WHITE);

        Table table = new Table();
        table.top().left();
        table.setFillParent(true);
        table.pad(10);

        galaxyPosLabel = new Label("Galaxy: -", style);
        localPosLabel = new Label("Local: -", style);
        velocityLabel = new Label("Velocity: -", style);
        groundLabel = new Label("Ground: -", style);
        stateLabel = new Label("State: -", style);
        staminaLabel = new Label("Stamina: -", style);
        fpsLabel = new Label("FPS: -", style);

        table.add(fpsLabel).left().row();
        table.add(galaxyPosLabel).left().row();
        table.add(localPosLabel).left().row();
        table.add(velocityLabel).left().row();
        table.add(groundLabel).left().row();
        table.add(stateLabel).left().row();
        table.add(staminaLabel).left().row();

        stage.addActor(table);
    }

    @Override
    public void update(float deltaTime) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            visible = !visible;
        }
        if (!visible) return;

        if (playerEntities.size() > 0) {
            Entity player = playerEntities.first();
            TransformComponent transform = player.getComponent(TransformComponent.class);
            MovementStateComponent state = player.getComponent(MovementStateComponent.class);

            double[] galaxy = coordinateManager.toGalaxySpace(transform.position);
            galaxyPosLabel.setText(String.format("Galaxy: %.2f, %.2f, %.2f", galaxy[0], galaxy[1], galaxy[2]));
            localPosLabel.setText(String.format("Local: %.2f, %.2f, %.2f",
                transform.position.x, transform.position.y, transform.position.z));
            velocityLabel.setText(String.format("Velocity: %.2f m/s", state.currentSpeed));
            groundLabel.setText("Ground: " + state.isGrounded);

            String stateStr;
            if (!state.isGrounded) stateStr = "airborne";
            else if (state.isSprinting) stateStr = "sprinting";
            else if (state.isCrouching) stateStr = "crouching";
            else stateStr = "walking";
            stateLabel.setText("State: " + stateStr);

            staminaLabel.setText(String.format("Stamina: %.0f / %.0f", state.currentStamina, state.maxStamina));
        }

        fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());

        stage.act(deltaTime);
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (font != null) font.dispose();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/systems/DebugHudSystem.java
git commit -m "feat: add DebugHudSystem with Scene2D overlay for debug info"
```

---

### Task 12: GameWorld

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Implement GameWorld**

```java
// core/src/main/java/com/galacticodyssey/core/GameWorld.java
package com.galacticodyssey.core;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCapsuleShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.systems.BulletPhysicsSystem;
import com.galacticodyssey.core.systems.PhysicsBodySystem;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.systems.CameraSystem;
import com.galacticodyssey.player.systems.PlayerInputSystem;
import com.galacticodyssey.player.systems.PlayerMovementSystem;
import com.galacticodyssey.ui.systems.DebugHudSystem;

public class GameWorld implements Disposable {

    private final Engine engine;
    private final EventBus eventBus;
    private final CoordinateManager coordinateManager;
    private final BulletPhysicsSystem bulletPhysicsSystem;
    private final PhysicsBodySystem physicsBodySystem;
    private final PlayerInputSystem playerInputSystem;
    private final PlayerMovementSystem playerMovementSystem;
    private final CameraSystem cameraSystem;
    private final DebugHudSystem debugHudSystem;

    private final Array<Disposable> disposables = new Array<>();

    public GameWorld(EventBus eventBus, CoordinateManager coordinateManager) {
        this.engine = new Engine();
        this.eventBus = eventBus;
        this.coordinateManager = coordinateManager;

        bulletPhysicsSystem = new BulletPhysicsSystem(eventBus);
        bulletPhysicsSystem.initialize();

        physicsBodySystem = new PhysicsBodySystem();
        playerInputSystem = new PlayerInputSystem();
        playerMovementSystem = new PlayerMovementSystem(bulletPhysicsSystem.getDynamicsWorld());
        cameraSystem = new CameraSystem();
        debugHudSystem = new DebugHudSystem(coordinateManager);

        engine.addSystem(playerInputSystem);
        engine.addSystem(playerMovementSystem);
        engine.addSystem(bulletPhysicsSystem);
        engine.addSystem(physicsBodySystem);
        engine.addSystem(cameraSystem);
        engine.addSystem(debugHudSystem);
    }

    public void initializeSystems(PerspectiveCamera camera) {
        playerInputSystem.initialize();
        cameraSystem.setCamera(camera);
        debugHudSystem.initialize();
    }

    public Entity createPlayerEntity(float spawnX, float spawnY, float spawnZ) {
        Entity player = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(spawnX, spawnY, spawnZ);
        player.add(transform);

        player.add(new PlayerTagComponent());
        player.add(new PlayerInputComponent());
        player.add(new MovementStateComponent());
        player.add(new FPSCameraComponent());

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btCapsuleShape(0.3f, 1.2f);
        physics.mass = 80f;
        physics.friction = 1.0f;
        physics.restitution = 0f;

        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(physics.mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(physics.mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(spawnX, spawnY, spawnZ));
        physics.body.setAngularFactor(new Vector3(0, 0, 0));
        physics.body.setFriction(physics.friction);
        physics.body.setRestitution(physics.restitution);
        info.dispose();

        player.add(physics);

        bulletPhysicsSystem.getDynamicsWorld().addRigidBody(physics.body);
        bulletPhysicsSystem.addManagedBody(physics.body);

        engine.addEntity(player);
        disposables.add(() -> {
            bulletPhysicsSystem.getDynamicsWorld().removeRigidBody(physics.body);
            physics.body.dispose();
            physics.shape.dispose();
        });

        return player;
    }

    public Entity createStaticBox(float x, float y, float z, float halfExtent) {
        return createBox(x, y, z, halfExtent, 0f);
    }

    public Entity createDynamicBox(float x, float y, float z, float halfExtent, float mass) {
        return createBox(x, y, z, halfExtent, mass);
    }

    private Entity createBox(float x, float y, float z, float halfExtent, float mass) {
        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.shape = new btBoxShape(new Vector3(halfExtent, halfExtent, halfExtent));
        physics.mass = mass;

        Vector3 inertia = new Vector3();
        if (mass > 0) {
            physics.shape.calculateLocalInertia(mass, inertia);
        }
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(x, y, z));
        physics.body.setFriction(0.8f);
        info.dispose();

        entity.add(physics);

        bulletPhysicsSystem.getDynamicsWorld().addRigidBody(physics.body);
        bulletPhysicsSystem.addManagedBody(physics.body);

        engine.addEntity(entity);
        disposables.add(() -> {
            bulletPhysicsSystem.getDynamicsWorld().removeRigidBody(physics.body);
            physics.body.dispose();
            physics.shape.dispose();
        });

        return entity;
    }

    public void addTerrainBody(btRigidBody terrainBody) {
        bulletPhysicsSystem.getDynamicsWorld().addRigidBody(terrainBody);
        bulletPhysicsSystem.addManagedBody(terrainBody);
        disposables.add(() -> {
            bulletPhysicsSystem.getDynamicsWorld().removeRigidBody(terrainBody);
            terrainBody.dispose();
        });
    }

    public void update(float delta) {
        engine.update(delta);

        Entity player = engine.getEntitiesFor(
            com.badlogic.ashley.core.Family.all(PlayerTagComponent.class, TransformComponent.class).get()).first();
        TransformComponent t = player.getComponent(TransformComponent.class);
        coordinateManager.checkRebase(t.position);
    }

    public void resize(int width, int height) {
        debugHudSystem.resize(width, height);
    }

    public BulletPhysicsSystem getBulletPhysicsSystem() {
        return bulletPhysicsSystem;
    }

    @Override
    public void dispose() {
        for (int i = disposables.size - 1; i >= 0; i--) {
            disposables.get(i).dispose();
        }
        disposables.clear();
        debugHudSystem.dispose();
        bulletPhysicsSystem.dispose();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat: add GameWorld ECS bootstrap with system registration and entity factories"
```

---

### Task 13: GalacticOdyssey Integration

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GalacticOdyssey.java`

- [ ] **Step 1: Rewrite GalacticOdyssey to wire everything together**

```java
// core/src/main/java/com/galacticodyssey/core/GalacticOdyssey.java
package com.galacticodyssey.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btHeightfieldTerrainShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.galacticodyssey.data.TerrainGenerator;

import java.nio.FloatBuffer;
import java.util.Random;

public class GalacticOdyssey extends ApplicationAdapter {

    private static final int TERRAIN_VERTS_X = 257;
    private static final int TERRAIN_VERTS_Z = 257;
    private static final float TERRAIN_WIDTH = 500f;
    private static final float TERRAIN_DEPTH = 500f;
    private static final long TERRAIN_SEED = 42L;

    private EventBus eventBus;
    private CoordinateManager coordinateManager;
    private GameWorld gameWorld;
    private PerspectiveCamera camera;

    private Mesh terrainMesh;
    private float[] heightmap;

    private ModelBatch modelBatch;
    private Environment environment;
    private Array<ModelInstance> boxInstances = new Array<>();
    private Array<Model> boxModels = new Array<>();
    private Array<com.badlogic.ashley.core.Entity> boxEntities = new Array<>();

    private btHeightfieldTerrainShape terrainShape;
    private FloatBuffer heightmapBuffer;

    @Override
    public void create() {
        Bullet.init();

        eventBus = new EventBus();
        coordinateManager = new CoordinateManager(eventBus);

        heightmap = TerrainGenerator.generateHeightmap(
            TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, TERRAIN_SEED);

        gameWorld = new GameWorld(eventBus, coordinateManager);

        camera = new PerspectiveCamera(75, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 5000f;

        gameWorld.initializeSystems(camera);

        createTerrainMesh();
        createTerrainPhysics();
        createScatterBoxes();

        float spawnHeight = TerrainGenerator.getHeightAt(
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, 0, 0) + 2f;
        gameWorld.createPlayerEntity(0, spawnHeight, 0);

        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.35f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.75f, -0.4f, -0.8f, -0.3f));

        Gdx.app.log("GalacticOdyssey", "Galactic Odyssey started.");
    }

    private void createTerrainMesh() {
        float[] normals = TerrainGenerator.computeNormals(
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH);

        int vertCount = TERRAIN_VERTS_X * TERRAIN_VERTS_Z;
        float[] vertices = new float[vertCount * 10];

        float cellW = TERRAIN_WIDTH / (TERRAIN_VERTS_X - 1);
        float cellD = TERRAIN_DEPTH / (TERRAIN_VERTS_Z - 1);
        float halfW = TERRAIN_WIDTH / 2f;
        float halfD = TERRAIN_DEPTH / 2f;

        float minH = Float.MAX_VALUE, maxH = Float.MIN_VALUE;
        for (float h : heightmap) {
            minH = Math.min(minH, h);
            maxH = Math.max(maxH, h);
        }

        for (int z = 0; z < TERRAIN_VERTS_Z; z++) {
            for (int x = 0; x < TERRAIN_VERTS_X; x++) {
                int idx = z * TERRAIN_VERTS_X + x;
                int vi = idx * 10;
                float h = heightmap[idx];

                vertices[vi]     = x * cellW - halfW;
                vertices[vi + 1] = h;
                vertices[vi + 2] = z * cellD - halfD;

                vertices[vi + 3] = normals[idx * 3];
                vertices[vi + 4] = normals[idx * 3 + 1];
                vertices[vi + 5] = normals[idx * 3 + 2];

                float slope = 1f - normals[idx * 3 + 1];
                float heightFrac = (h - minH) / (maxH - minH + 0.001f);
                float r = 0.2f + slope * 0.4f + heightFrac * 0.1f;
                float g = 0.4f - slope * 0.2f + heightFrac * 0.05f;
                float b = 0.1f + heightFrac * 0.05f;

                vertices[vi + 6] = r;
                vertices[vi + 7] = g;
                vertices[vi + 8] = b;
                vertices[vi + 9] = 1f;
            }
        }

        int quadCount = (TERRAIN_VERTS_X - 1) * (TERRAIN_VERTS_Z - 1);
        short[] indices = new short[quadCount * 6];
        int ii = 0;
        for (int z = 0; z < TERRAIN_VERTS_Z - 1; z++) {
            for (int x = 0; x < TERRAIN_VERTS_X - 1; x++) {
                short topLeft = (short) (z * TERRAIN_VERTS_X + x);
                short topRight = (short) (topLeft + 1);
                short botLeft = (short) ((z + 1) * TERRAIN_VERTS_X + x);
                short botRight = (short) (botLeft + 1);

                indices[ii++] = topLeft;
                indices[ii++] = botLeft;
                indices[ii++] = topRight;
                indices[ii++] = topRight;
                indices[ii++] = botLeft;
                indices[ii++] = botRight;
            }
        }

        terrainMesh = new Mesh(true, vertCount, indices.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
            new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"));

        terrainMesh.setVertices(vertices);
        terrainMesh.setIndices(indices);
    }

    private void createTerrainPhysics() {
        heightmapBuffer = BufferUtils.newFloatBuffer(heightmap.length);
        heightmapBuffer.put(heightmap);
        heightmapBuffer.flip();

        float minH = Float.MAX_VALUE, maxH = Float.MIN_VALUE;
        for (float h : heightmap) {
            minH = Math.min(minH, h);
            maxH = Math.max(maxH, h);
        }

        terrainShape = new btHeightfieldTerrainShape(
            TERRAIN_VERTS_X, TERRAIN_VERTS_Z, heightmapBuffer,
            1f, minH, maxH, 1, false);

        Vector3 localScale = new Vector3(
            TERRAIN_WIDTH / (TERRAIN_VERTS_X - 1),
            1f,
            TERRAIN_DEPTH / (TERRAIN_VERTS_Z - 1));
        terrainShape.setLocalScaling(localScale);

        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, terrainShape);
        btRigidBody terrainBody = new btRigidBody(info);

        float midH = (minH + maxH) / 2f;
        terrainBody.setWorldTransform(new Matrix4().setToTranslation(0, midH, 0));
        terrainBody.setFriction(0.9f);
        info.dispose();

        gameWorld.addTerrainBody(terrainBody);
    }

    private void createScatterBoxes() {
        Random rng = new Random(123L);
        ModelBuilder modelBuilder = new ModelBuilder();

        for (int i = 0; i < 15; i++) {
            float halfExt = 0.5f + rng.nextFloat() * 1.0f;
            float bx = (rng.nextFloat() - 0.5f) * TERRAIN_WIDTH * 0.6f;
            float bz = (rng.nextFloat() - 0.5f) * TERRAIN_DEPTH * 0.6f;
            float by = TerrainGenerator.getHeightAt(
                heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, bx, bz)
                + halfExt + 1f;
            float mass = 50f + rng.nextFloat() * 150f;

            com.badlogic.ashley.core.Entity boxEntity = gameWorld.createDynamicBox(bx, by, bz, halfExt, mass);
            boxEntities.add(boxEntity);

            float r = 0.3f + rng.nextFloat() * 0.7f;
            float g = 0.3f + rng.nextFloat() * 0.7f;
            float b = 0.3f + rng.nextFloat() * 0.7f;

            Model boxModel = modelBuilder.createBox(
                halfExt * 2, halfExt * 2, halfExt * 2,
                new Material(ColorAttribute.createDiffuse(new Color(r, g, b, 1f))),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
            boxModels.add(boxModel);

            ModelInstance instance = new ModelInstance(boxModel);
            instance.transform.setToTranslation(bx, by, bz);
            boxInstances.add(instance);
        }
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f, true);

        float delta = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);
        gameWorld.update(delta);

        syncBoxTransforms();
        renderTerrain();
        renderBoxes();
    }

    private void syncBoxTransforms() {
        for (int i = 0; i < boxEntities.size; i++) {
            com.badlogic.ashley.core.Entity entity = boxEntities.get(i);
            com.galacticodyssey.core.components.TransformComponent t =
                entity.getComponent(com.galacticodyssey.core.components.TransformComponent.class);
            boxInstances.get(i).transform.setToTranslation(t.position);
            boxInstances.get(i).transform.rotate(t.rotation);
        }
    }

    private void renderTerrain() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        ShaderProgram shader = getTerrainShader();
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);

        Matrix4 modelMat = new Matrix4();
        shader.setUniformMatrix("u_worldTrans", modelMat);
        shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
        shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);

        terrainMesh.render(shader, GL20.GL_TRIANGLES);
    }

    private void renderBoxes() {
        modelBatch.begin(camera);
        for (ModelInstance instance : boxInstances) {
            modelBatch.render(instance, environment);
        }
        modelBatch.end();
    }

    private ShaderProgram terrainShader;

    private ShaderProgram getTerrainShader() {
        if (terrainShader != null) return terrainShader;
        terrainShader = createTerrainShader();
        return terrainShader;
    }

    private ShaderProgram createTerrainShader() {
        String vert = """
            attribute vec3 a_position;
            attribute vec3 a_normal;
            attribute vec4 a_color;
            uniform mat4 u_projViewTrans;
            uniform mat4 u_worldTrans;
            varying vec3 v_normal;
            varying vec4 v_color;
            void main() {
                v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);
                v_color = a_color;
                gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);
            }
            """;
        String frag = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec3 v_normal;
            varying vec4 v_color;
            uniform vec3 u_lightDir;
            uniform vec4 u_ambientColor;
            void main() {
                vec3 lightDir = normalize(-u_lightDir);
                float diff = max(dot(v_normal, lightDir), 0.0);
                vec3 color = v_color.rgb * (u_ambientColor.rgb + diff * vec3(0.8, 0.8, 0.75));
                gl_FragColor = vec4(color, 1.0);
            }
            """;
        ShaderProgram shader = new ShaderProgram(vert, frag);
        if (!shader.isCompiled()) {
            Gdx.app.error("Shader", shader.getLog());
        }
        return shader;
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        gameWorld.resize(width, height);
    }

    @Override
    public void dispose() {
        gameWorld.dispose();
        if (terrainMesh != null) terrainMesh.dispose();
        if (terrainShader != null) terrainShader.dispose();
        if (modelBatch != null) modelBatch.dispose();
        for (Model m : boxModels) m.dispose();
        if (terrainShape != null) terrainShape.dispose();
        Gdx.app.log("GalacticOdyssey", "Shutting down.");
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GalacticOdyssey.java
git commit -m "feat: integrate all systems into GalacticOdyssey with terrain and player spawn"
```

---

### Task 14: Run All Tests

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew :core:test --info`
Expected: All tests PASS (EventBusTest, CoordinateManagerTest, BulletPhysicsSystemTest, PhysicsBodySystemTest, PlayerMovementSystemTest, TerrainGeneratorTest).

- [ ] **Step 2: Fix any test failures**

If any tests fail, read the failure output and fix the issue. Re-run until green.

- [ ] **Step 3: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve test failures from integration"
```

---

### Task 15: Launch and Verify

- [ ] **Step 1: Launch the desktop application**

Run: `./gradlew :desktop:run`

Expected behavior:
- Window opens at 1280×720 with title "Galactic Odyssey"
- Procedural terrain is visible with green-brown coloring
- Colored boxes are scattered on the terrain
- Mouse cursor is captured
- WASD moves the player across terrain
- Mouse look rotates the camera
- Shift sprints (faster movement)
- Space jumps
- Ctrl crouches (lower viewpoint)
- F3 toggles the debug HUD showing position, velocity, ground state, stamina, FPS
- Escape releases the cursor

- [ ] **Step 2: Fix any runtime issues**

If the game crashes or behaves incorrectly, check the console output, diagnose the issue, fix, and re-launch. Common issues to watch for:
- Shader compilation errors (check `Gdx.app.error` output)
- Bullet physics initialization failures
- Terrain heightfield shape mismatch (scaling, centering)
- Player spawning below terrain
- Camera looking in wrong direction

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "fix: runtime fixes from manual testing"
```
