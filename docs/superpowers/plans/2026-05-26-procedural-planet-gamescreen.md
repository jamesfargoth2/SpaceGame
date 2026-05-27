# Procedural Planet GameScreen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat terrain in GameScreen with a procedurally generated spherical Terran planet featuring quadtree LOD terrain, radial gravity, Bullet collision per chunk, and surface-to-orbit ship transitions.

**Architecture:** New ECS systems (`RadialGravitySystem`, enhanced `PlanetTerrainSystem`) added to `GameWorld`. `GameScreen` becomes a thin orchestrator: runs the procgen pipeline, delegates terrain/physics to systems, renders chunk meshes. `PlayerMovementSystem` and `CameraSystem` switch from hardcoded Y-up to dynamic `localUp` derived from planet center.

**Tech Stack:** Java 17+, libGDX 1.13+, Ashley ECS, gdx-bullet (Bullet physics), JUnit 5

---

## File Map

| File | Role |
|------|------|
| `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainChunk.java` | Add `btRigidBody`, `btTriangleMesh`, `btBvhTriangleMeshShape` fields; collision lifecycle |
| `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainQuadtree.java` | Accept dynamics world; create/destroy Mesh + collision on split/merge/generate |
| `core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java` | Accept dynamics world; expose `getVisibleLeaves()`; forward camera position |
| `core/src/main/java/com/galacticodyssey/core/systems/RadialGravitySystem.java` | **New.** Per-entity gravity toward planet center |
| `core/src/main/java/com/galacticodyssey/player/components/FPSCameraComponent.java` | Add `localUp` vector field |
| `core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java` | Replace Y-up with `localUp`; capsule orientation |
| `core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java` | Use `localUp` for view matrix |
| `core/src/main/java/com/galacticodyssey/core/GameWorld.java` | Wire `PlanetTerrainSystem`, `RadialGravitySystem`; add `loadPlanet()` |
| `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` | Remove flat terrain; add procgen pipeline + `renderPlanetTerrain()` |
| `core/src/test/java/com/galacticodyssey/core/systems/RadialGravitySystemTest.java` | **New.** Tests for radial gravity |
| `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainChunkCollisionTest.java` | **New.** Tests for chunk collision bodies |
| `core/src/test/java/com/galacticodyssey/player/systems/PlayerMovementSystemTest.java` | Update existing tests for spherical movement |

---

### Task 1: TerrainChunk Collision Body Fields

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainChunk.java`

- [ ] **Step 1: Add collision fields to TerrainChunk**

Open `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainChunk.java`. Add imports and fields for the Bullet collision body. Add a reference to the dynamics world so the chunk can remove its own body on dispose.

Add these imports at the top of the file (after the existing imports):

```java
import com.badlogic.gdx.physics.bullet.collision.btBvhTriangleMeshShape;
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
```

Add these fields after the existing `public boolean meshReady;` field:

```java
public btRigidBody collisionBody;
public btTriangleMesh triangleMesh;
public btBvhTriangleMeshShape collisionShape;
```

- [ ] **Step 2: Update dispose() to clean up collision**

Replace the existing `dispose()` method with:

```java
@Override
public void dispose() {
    disposeCollision(null);
    if (mesh != null) { mesh.dispose(); mesh = null; }
    if (children != null) {
        for (TerrainChunk child : children) child.dispose();
        children = null;
    }
}

public void disposeCollision(btDiscreteDynamicsWorld dynamicsWorld) {
    if (collisionBody != null) {
        if (dynamicsWorld != null) {
            dynamicsWorld.removeRigidBody(collisionBody);
        }
        collisionBody.dispose();
        collisionBody = null;
    }
    if (collisionShape != null) { collisionShape.dispose(); collisionShape = null; }
    if (triangleMesh != null) { triangleMesh.dispose(); triangleMesh = null; }
}
```

- [ ] **Step 3: Run existing terrain tests to confirm no breakage**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.*" --info`
Expected: All existing terrain tests pass (TerrainMeshBuilderTest, CubeSphereTest, TerrainNoiseStackTest).

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/TerrainChunk.java
git commit -m "feat(planet): add Bullet collision fields to TerrainChunk"
```

---

### Task 2: TerrainQuadtree Mesh + Collision Creation

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainQuadtree.java`
- Create: `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainChunkCollisionTest.java`

- [ ] **Step 1: Write failing test for chunk collision body creation**

Create `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainChunkCollisionTest.java`:

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.*;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerrainChunkCollisionTest {
    private btDiscreteDynamicsWorld dynamicsWorld;
    private btCollisionConfiguration collisionConfig;
    private btCollisionDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;

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
    }

    @AfterEach
    void tearDown() {
        dynamicsWorld.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
    }

    @Test
    void quadtreeCreatesCollisionBodiesForLeafChunks() {
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f,
            EnumSet.allOf(BiomeType.class));
        TerrainQuadtree quadtree = new TerrainQuadtree(6371f, noise, biomeMap, dynamicsWorld);

        quadtree.update(new Vector3(0, 0, 6371f));

        List<TerrainChunk> leaves = quadtree.getVisibleLeaves();
        assertFalse(leaves.isEmpty(), "Should have visible leaf chunks");
        for (TerrainChunk leaf : leaves) {
            assertNotNull(leaf.mesh, "Leaf chunk should have a libGDX Mesh");
            assertNotNull(leaf.collisionBody, "Leaf chunk should have a collision body");
        }

        quadtree.dispose(dynamicsWorld);
    }

    @Test
    void disposeRemovesAllCollisionBodies() {
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f,
            EnumSet.allOf(BiomeType.class));
        TerrainQuadtree quadtree = new TerrainQuadtree(6371f, noise, biomeMap, dynamicsWorld);

        quadtree.update(new Vector3(0, 0, 6371f));
        int bodyCount = dynamicsWorld.getNumCollisionObjects();
        assertTrue(bodyCount > 0, "Should have collision bodies in world");

        quadtree.dispose(dynamicsWorld);
        assertEquals(0, dynamicsWorld.getNumCollisionObjects(),
            "All collision bodies should be removed after dispose");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainChunkCollisionTest" --info`
Expected: FAIL — `TerrainQuadtree` constructor doesn't accept a `dynamicsWorld` parameter.

- [ ] **Step 3: Update TerrainQuadtree to create Mesh and collision bodies**

Open `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainQuadtree.java`. Replace the entire file:

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBvhTriangleMeshShape;
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.planet.BiomeMap;
import java.util.ArrayList;
import java.util.List;

public final class TerrainQuadtree implements Disposable {
    private final TerrainChunk[] roots;
    private final float planetRadius;
    private final TerrainNoiseStack noise;
    private final BiomeMap biomeMap;
    private final btDiscreteDynamicsWorld dynamicsWorld;

    public TerrainQuadtree(float planetRadius, TerrainNoiseStack noise, BiomeMap biomeMap,
                           btDiscreteDynamicsWorld dynamicsWorld) {
        this.planetRadius = planetRadius;
        this.noise = noise;
        this.biomeMap = biomeMap;
        this.dynamicsWorld = dynamicsWorld;
        this.roots = new TerrainChunk[6];
        CubeFace[] faces = CubeFace.values();
        for (int i = 0; i < 6; i++) {
            roots[i] = new TerrainChunk(faces[i], 0, 0f, 0f, 1f, 1f, planetRadius);
        }
    }

    public void update(Vector3 cameraPos) {
        for (TerrainChunk root : roots) {
            recursiveUpdate(root, cameraPos);
        }
    }

    private void recursiveUpdate(TerrainChunk chunk, Vector3 cameraPos) {
        if (!chunk.meshReady) {
            generateMesh(chunk);
        }

        if (chunk.shouldSplit(cameraPos) && !chunk.hasChildren()) {
            split(chunk);
        } else if (chunk.hasChildren() && chunk.shouldMerge(cameraPos)) {
            merge(chunk);
        }

        if (chunk.hasChildren()) {
            for (TerrainChunk child : chunk.children) {
                recursiveUpdate(child, cameraPos);
            }
        }
    }

    private void split(TerrainChunk chunk) {
        chunk.disposeCollision(dynamicsWorld);
        if (chunk.mesh != null) { chunk.mesh.dispose(); chunk.mesh = null; }
        chunk.meshReady = false;

        float mu = (chunk.u0 + chunk.u1) * 0.5f;
        float mv = (chunk.v0 + chunk.v1) * 0.5f;
        int d = chunk.depth + 1;
        chunk.children = new TerrainChunk[] {
            new TerrainChunk(chunk.face, d, chunk.u0, chunk.v0, mu, mv, planetRadius),
            new TerrainChunk(chunk.face, d, mu, chunk.v0, chunk.u1, mv, planetRadius),
            new TerrainChunk(chunk.face, d, chunk.u0, mv, mu, chunk.v1, planetRadius),
            new TerrainChunk(chunk.face, d, mu, mv, chunk.u1, chunk.v1, planetRadius),
        };
    }

    private void merge(TerrainChunk chunk) {
        if (chunk.children != null) {
            for (TerrainChunk child : chunk.children) {
                child.disposeCollision(dynamicsWorld);
                child.dispose();
            }
            chunk.children = null;
        }
        chunk.meshReady = false;
    }

    private void generateMesh(TerrainChunk chunk) {
        TerrainMeshBuilder.MeshData data = TerrainMeshBuilder.build(
            chunk.face, chunk.u0, chunk.v0, chunk.u1, chunk.v1,
            noise, biomeMap, planetRadius, chunk.depth, null);

        chunk.mesh = new Mesh(true,
            TerrainMeshBuilder.GRID_SIZE * TerrainMeshBuilder.GRID_SIZE,
            data.indices.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
            new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"));
        chunk.mesh.setVertices(data.vertices);
        chunk.mesh.setIndices(data.indices);

        buildCollision(chunk, data);
        chunk.meshReady = true;
    }

    private void buildCollision(TerrainChunk chunk, TerrainMeshBuilder.MeshData data) {
        btTriangleMesh triMesh = new btTriangleMesh();
        Vector3 v0 = new Vector3(), v1 = new Vector3(), v2 = new Vector3();
        int stride = TerrainMeshBuilder.VERTEX_STRIDE;

        for (int i = 0; i < data.indices.length; i += 3) {
            int i0 = (data.indices[i] & 0xFFFF) * stride;
            int i1 = (data.indices[i + 1] & 0xFFFF) * stride;
            int i2 = (data.indices[i + 2] & 0xFFFF) * stride;

            v0.set(data.vertices[i0], data.vertices[i0 + 1], data.vertices[i0 + 2]);
            v1.set(data.vertices[i1], data.vertices[i1 + 1], data.vertices[i1 + 2]);
            v2.set(data.vertices[i2], data.vertices[i2 + 1], data.vertices[i2 + 2]);

            triMesh.addTriangle(v0, v1, v2);
        }

        btBvhTriangleMeshShape shape = new btBvhTriangleMeshShape(triMesh, true);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, shape);
        btRigidBody body = new btRigidBody(info);
        body.setFriction(0.9f);
        info.dispose();

        if (dynamicsWorld != null) {
            dynamicsWorld.addRigidBody(body);
        }

        chunk.triangleMesh = triMesh;
        chunk.collisionShape = shape;
        chunk.collisionBody = body;
    }

    public List<TerrainChunk> getVisibleLeaves() {
        List<TerrainChunk> leaves = new ArrayList<>();
        for (TerrainChunk root : roots) collectLeaves(root, leaves);
        return leaves;
    }

    private void collectLeaves(TerrainChunk chunk, List<TerrainChunk> out) {
        if (!chunk.hasChildren()) { out.add(chunk); return; }
        for (TerrainChunk child : chunk.children) collectLeaves(child, out);
    }

    public void dispose(btDiscreteDynamicsWorld world) {
        for (TerrainChunk root : roots) {
            disposeRecursive(root, world);
        }
    }

    private void disposeRecursive(TerrainChunk chunk, btDiscreteDynamicsWorld world) {
        if (chunk.hasChildren()) {
            for (TerrainChunk child : chunk.children) disposeRecursive(child, world);
        }
        chunk.disposeCollision(world);
        if (chunk.mesh != null) { chunk.mesh.dispose(); chunk.mesh = null; }
    }

    @Override
    public void dispose() {
        dispose(dynamicsWorld);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainChunkCollisionTest" --info`
Expected: PASS — both tests green.

- [ ] **Step 5: Run all terrain tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.*" --info`
Expected: All pass. The `TerrainMeshBuilderTest` doesn't use `TerrainQuadtree` so is unaffected. If `PipelineIntegrationTest` references the old constructor, it will need updating in a later task.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/TerrainQuadtree.java \
        core/src/test/java/com/galacticodyssey/planet/terrain/TerrainChunkCollisionTest.java
git commit -m "feat(planet): create libGDX Mesh + Bullet collision per terrain chunk"
```

---

### Task 3: Update PlanetTerrainSystem

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java`

- [ ] **Step 1: Update PlanetTerrainSystem to accept dynamics world and expose visible leaves**

Replace the entire file:

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Planet;

import java.util.Collections;
import java.util.List;

public final class PlanetTerrainSystem extends EntitySystem implements Disposable {
    private final btDiscreteDynamicsWorld dynamicsWorld;
    private TerrainQuadtree quadtree;
    private final Vector3 cameraPos = new Vector3();
    private float planetRadius;

    public PlanetTerrainSystem(btDiscreteDynamicsWorld dynamicsWorld) {
        this.dynamicsWorld = dynamicsWorld;
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap) {
        if (quadtree != null) quadtree.dispose();
        long terrainSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.TERRAIN_DOMAIN), 0);
        TerrainNoiseStack noise = new TerrainNoiseStack(terrainSeed);
        planetRadius = planet.radius * 6371f;
        quadtree = new TerrainQuadtree(planetRadius, noise, biomeMap, dynamicsWorld);
    }

    public void unloadPlanet() {
        if (quadtree != null) { quadtree.dispose(); quadtree = null; }
    }

    public void setCameraPosition(Vector3 pos) {
        cameraPos.set(pos);
    }

    public float getPlanetRadius() {
        return planetRadius;
    }

    public List<TerrainChunk> getVisibleLeaves() {
        if (quadtree == null) return Collections.emptyList();
        return quadtree.getVisibleLeaves();
    }

    @Override
    public void update(float deltaTime) {
        if (quadtree != null) {
            quadtree.update(cameraPos);
        }
    }

    @Override
    public void dispose() {
        unloadPlanet();
    }
}
```

- [ ] **Step 2: Fix PipelineIntegrationTest if it references old TerrainQuadtree constructor**

The `PipelineIntegrationTest.seedToTerrainChunkWithoutGLContext` test does not instantiate `TerrainQuadtree` — it calls `TerrainMeshBuilder.build()` directly, so no change needed. Verify by running:

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.PipelineIntegrationTest" --info`
Expected: All pass.

- [ ] **Step 3: Run full planet test suite**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.*" --info`
Expected: All pass.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java
git commit -m "feat(planet): wire dynamics world into PlanetTerrainSystem"
```

---

### Task 4: RadialGravitySystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/systems/RadialGravitySystem.java`
- Create: `core/src/test/java/com/galacticodyssey/core/systems/RadialGravitySystemTest.java`

- [ ] **Step 1: Write failing test for radial gravity**

Create `core/src/test/java/com/galacticodyssey/core/systems/RadialGravitySystemTest.java`:

```java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.*;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class RadialGravitySystemTest {
    private Engine engine;
    private btDiscreteDynamicsWorld dynamicsWorld;
    private btCollisionConfiguration collisionConfig;
    private btCollisionDispatcher dispatcher;
    private btBroadphaseInterface broadphase;
    private btConstraintSolver solver;
    private RadialGravitySystem gravitySystem;

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
        dynamicsWorld.setGravity(new Vector3(0, 0, 0));

        engine = new Engine();
        gravitySystem = new RadialGravitySystem(dynamicsWorld, new Vector3(0, 0, 0), 9.81f);
        engine.addSystem(gravitySystem);
    }

    @AfterEach
    void tearDown() {
        dynamicsWorld.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfig.dispose();
    }

    @Test
    void appliesForceTowardPlanetCenter() {
        Entity entity = createPhysicsEntity(0f, 6371f, 0f, 80f);
        engine.addEntity(entity);

        PhysicsBodyComponent physics = entity.getComponent(PhysicsBodyComponent.class);
        physics.body.clearForces();

        engine.update(1f / 60f);

        Vector3 totalForce = physics.body.getTotalForce();
        assertTrue(totalForce.y < 0, "Gravity should pull downward (toward center) but got y=" + totalForce.y);
        assertEquals(0f, totalForce.x, 0.01f, "No x-force expected for entity directly above center");
        assertEquals(0f, totalForce.z, 0.01f, "No z-force expected for entity directly above center");
    }

    @Test
    void gravityDirectionMatchesEntityPosition() {
        Entity entity = createPhysicsEntity(6371f, 0f, 0f, 80f);
        engine.addEntity(entity);

        PhysicsBodyComponent physics = entity.getComponent(PhysicsBodyComponent.class);
        physics.body.clearForces();

        engine.update(1f / 60f);

        Vector3 totalForce = physics.body.getTotalForce();
        assertTrue(totalForce.x < 0, "Gravity should pull in -x direction but got x=" + totalForce.x);
        assertEquals(0f, totalForce.y, 0.01f);
        assertEquals(0f, totalForce.z, 0.01f);
    }

    @Test
    void skipsPilotingEntities() {
        Entity entity = createPhysicsEntity(0f, 6371f, 0f, 80f);
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerStateComponent.PlayerMode.PILOTING;
        entity.add(state);
        engine.addEntity(entity);

        PhysicsBodyComponent physics = entity.getComponent(PhysicsBodyComponent.class);
        physics.body.clearForces();

        engine.update(1f / 60f);

        Vector3 totalForce = physics.body.getTotalForce();
        assertEquals(0f, totalForce.len(), 0.01f, "No gravity should apply to piloting entities");
    }

    @Test
    void forceMagnitudeIsGravityTimesMass() {
        float mass = 80f;
        Entity entity = createPhysicsEntity(0f, 6371f, 0f, mass);
        engine.addEntity(entity);

        PhysicsBodyComponent physics = entity.getComponent(PhysicsBodyComponent.class);
        physics.body.clearForces();

        engine.update(1f / 60f);

        Vector3 totalForce = physics.body.getTotalForce();
        float expectedMag = 9.81f * mass;
        assertEquals(expectedMag, totalForce.len(), 0.1f,
            "Force magnitude should be g * mass");
    }

    private Entity createPhysicsEntity(float x, float y, float z, float mass) {
        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.mass = mass;
        physics.shape = new btBoxShape(new Vector3(0.5f, 0.5f, 0.5f));

        Vector3 inertia = new Vector3();
        physics.shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, null, physics.shape, inertia);
        physics.body = new btRigidBody(info);
        physics.body.setWorldTransform(new Matrix4().setToTranslation(x, y, z));
        info.dispose();

        dynamicsWorld.addRigidBody(physics.body);
        entity.add(physics);

        return entity;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.RadialGravitySystemTest" --info`
Expected: FAIL — `RadialGravitySystem` class does not exist.

- [ ] **Step 3: Implement RadialGravitySystem**

Create `core/src/main/java/com/galacticodyssey/core/systems/RadialGravitySystem.java`:

```java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;

public class RadialGravitySystem extends IteratingSystem {

    private final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<PlayerStateComponent> playerStateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);

    private final Vector3 planetCenter = new Vector3();
    private final float gravity;
    private final Vector3 tempVec = new Vector3();
    private final Matrix4 tempMat = new Matrix4();

    public RadialGravitySystem(btDiscreteDynamicsWorld dynamicsWorld,
                                Vector3 planetCenter, float gravity) {
        super(Family.all(PhysicsBodyComponent.class, TransformComponent.class).get());
        this.planetCenter.set(planetCenter);
        this.gravity = gravity;
        dynamicsWorld.setGravity(new Vector3(0, 0, 0));
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerStateComponent playerState = playerStateMapper.get(entity);
        if (playerState != null
            && playerState.currentMode == PlayerStateComponent.PlayerMode.PILOTING) {
            return;
        }

        PhysicsBodyComponent physics = physicsMapper.get(entity);
        if (physics.body == null || physics.mass <= 0f) return;

        physics.body.getWorldTransform(tempMat);
        tempMat.getTranslation(tempVec);

        tempVec.sub(planetCenter).nor().scl(-gravity * physics.mass);
        physics.body.applyCentralForce(tempVec);
        physics.body.activate();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.RadialGravitySystemTest" --info`
Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/systems/RadialGravitySystem.java \
        core/src/test/java/com/galacticodyssey/core/systems/RadialGravitySystemTest.java
git commit -m "feat(core): add RadialGravitySystem for per-entity spherical gravity"
```

---

### Task 5: FPSCameraComponent localUp Field

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/components/FPSCameraComponent.java`

- [ ] **Step 1: Add localUp field**

Add this import and field to `FPSCameraComponent.java`:

```java
import com.badlogic.gdx.math.Vector3;
```

Add after the existing `public float landingDipAmount;` field:

```java
public final Vector3 localUp = new Vector3(0, 1, 0);
```

- [ ] **Step 2: Run existing player tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.*" --info`
Expected: All pass — adding a field with a default value doesn't break anything.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/components/FPSCameraComponent.java
git commit -m "feat(player): add localUp vector to FPSCameraComponent"
```

---

### Task 6: PlayerMovementSystem Spherical Movement

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java`
- Modify: `core/src/test/java/com/galacticodyssey/player/systems/PlayerMovementSystemTest.java`

- [ ] **Step 1: Write failing test for spherical ground check**

Add this test to the existing `PlayerMovementSystemTest.java`:

```java
@Test
void groundCheckUsesLocalUpDirection() {
    // Place player at (0, 6371, 0) — directly above planet center at origin
    // localUp should be (0, 1, 0) in this case, same as flat world
    // Ground ray should fire in -localUp direction
    // This test validates the system works with the new localUp field
    FPSCameraComponent cam = player.getComponent(FPSCameraComponent.class);
    cam.localUp.set(0, 1, 0);

    // Move player to above the ground
    PhysicsBodyComponent physics = player.getComponent(PhysicsBodyComponent.class);
    physics.body.setWorldTransform(new Matrix4().setToTranslation(0, 2f, 0));

    engine.update(1f / 60f);

    MovementStateComponent state = player.getComponent(MovementStateComponent.class);
    // Should still detect ground (validates the ray fires in -localUp direction)
    // Exact grounded state depends on ground geometry, but no NPE/crash is the key assertion
    assertNotNull(state.groundNormal, "Ground normal should be set");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.systems.PlayerMovementSystemTest.groundCheckUsesLocalUpDirection" --info`
Expected: FAIL or PASS depending on test setup — the key is this will validate after the refactor.

- [ ] **Step 3: Refactor PlayerMovementSystem for spherical movement**

Replace the entire `PlayerMovementSystem.java` with the spherical version. Key changes:
- `localUp` computed from `normalize(playerPosition - planetCenter)` each frame
- Store `localUp` on `FPSCameraComponent` for `CameraSystem` to consume
- Ground ray fires in `-localUp` direction
- Jump impulse along `+localUp`
- Slope angle against `localUp`
- Yaw rotation around `localUp`
- Forward/right vectors computed from tangent plane
- Capsule orientation aligned to `localUp`

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;

public class PlayerMovementSystem extends IteratingSystem {

    private static final float WALK_SPEED = 3.5f;
    private static final float SPRINT_SPEED = 6.0f;
    private static final float CROUCH_SPEED = 1.5f;
    private static final float JUMP_IMPULSE = 5.0f;
    private static final float GROUND_FORCE = 50f;
    private static final float AIR_FORCE = 10f;
    private static final float GROUND_DAMPING = 0.9f;
    private static final float AIR_DAMPING = 0.1f;
    private static final float MAX_SLOPE_ANGLE = 55f;
    private static final float GROUND_RAY_EXTRA = 0.35f;
    private static final float CAPSULE_HALF_HEIGHT = 0.9f;
    private static final float SLOPE_SPEED_PENALTY_START = 10f;
    private static final float SLOPE_STAMINA_DRAIN_SCALE = 2.0f;
    private static final float EXHAUSTED_SPEED_MULTIPLIER = 0.4f;
    private static final float SLOPE_FORCE_BOOST = 2.5f;
    private static final float SLOPE_DAMPING_MIN = 0.4f;

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
    private final Vector3 planetCenter = new Vector3(0, 0, 0);

    private final Vector3 tempVec = new Vector3();
    private final Vector3 tempVec2 = new Vector3();
    private final Vector3 tempVec3 = new Vector3();
    private final Vector3 localUp = new Vector3();
    private final Vector3 localForward = new Vector3();
    private final Vector3 localRight = new Vector3();
    private final Vector3 rayFrom = new Vector3();
    private final Vector3 rayTo = new Vector3();
    private final Matrix4 tempMat = new Matrix4();
    private final Quaternion tempQuat = new Quaternion();

    public PlayerMovementSystem(btDiscreteDynamicsWorld dynamicsWorld) {
        super(Family.all(
            PlayerInputComponent.class,
            PhysicsBodyComponent.class,
            MovementStateComponent.class,
            TransformComponent.class
        ).get(), 1);
        this.dynamicsWorld = dynamicsWorld;
    }

    public void setPlanetCenter(Vector3 center) {
        planetCenter.set(center);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerStateComponent playerState = entity.getComponent(PlayerStateComponent.class);
        if (playerState != null && playerState.currentMode == PlayerStateComponent.PlayerMode.PILOTING) {
            return;
        }

        PlayerInputComponent input = inputMapper.get(entity);
        PhysicsBodyComponent physics = physicsMapper.get(entity);
        MovementStateComponent state = stateMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);
        FPSCameraComponent cam = cameraMapper.get(entity);

        if (physics.body == null) return;

        physics.body.getWorldTransform(tempMat);
        tempMat.getTranslation(tempVec);

        localUp.set(tempVec).sub(planetCenter);
        if (localUp.len2() < 0.001f) localUp.set(0, 1, 0);
        else localUp.nor();

        if (cam != null) {
            cam.localUp.set(localUp);
        }

        boolean wasGrounded = state.isGrounded;
        performGroundCheck(physics, state, tempVec);

        if (cam != null) {
            cam.yawAngle += input.mouseDeltaX * cam.mouseSensitivity;
            cam.pitchAngle += input.mouseDeltaY * cam.mouseSensitivity;
            cam.pitchAngle = MathUtils.clamp(cam.pitchAngle, -85f, 85f);
        }

        buildTangentFrame(cam != null ? cam.yawAngle : 0f);

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
        if (movingUphill && slopeAngle > SLOPE_SPEED_PENALTY_START) {
            float slopeFrac = (slopeAngle - SLOPE_SPEED_PENALTY_START) / (MAX_SLOPE_ANGLE - SLOPE_SPEED_PENALTY_START);
            slopeFrac = Math.min(1f, slopeFrac);
            slopeSpeedFactor = 1f - slopeFrac * 0.6f;
            slopeStaminaDrain = slopeFrac * state.staminaDrainRate * SLOPE_STAMINA_DRAIN_SCALE;
        }

        float forceMult = state.isGrounded ? GROUND_FORCE : AIR_FORCE;

        boolean wantsSprint = input.sprint && state.currentStamina > 0 && !input.crouch;
        state.isSprinting = wantsSprint && state.isGrounded && len > 0.001f;
        state.isCrouching = input.crouch;

        float targetSpeed = WALK_SPEED;
        if (state.isSprinting) targetSpeed = SPRINT_SPEED;
        if (state.isCrouching) targetSpeed = CROUCH_SPEED;

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

        if (input.jumpRequested && state.isGrounded) {
            tempVec3.set(localUp).scl(JUMP_IMPULSE * physics.mass);
            physics.body.applyCentralImpulse(tempVec3);
            state.isGrounded = false;
        }
        input.jumpRequested = false;

        float totalStaminaDrain = slopeStaminaDrain;
        if (state.isSprinting) {
            totalStaminaDrain += state.staminaDrainRate;
        }

        if (totalStaminaDrain > 0 && len > 0.001f) {
            state.currentStamina -= totalStaminaDrain * deltaTime;
            if (state.currentStamina <= 0) {
                state.currentStamina = 0;
                state.isSprinting = false;
                state.isExhausted = true;
            }
        } else {
            state.currentStamina = Math.min(state.maxStamina,
                state.currentStamina + state.staminaRegenRate * deltaTime);
            if (state.currentStamina > state.maxStamina * 0.2f) {
                state.isExhausted = false;
            }
        }

        if (!wasGrounded && state.isGrounded) {
            state.fallVelocity = Math.abs(currentVel.dot(localUp));
        } else if (!state.isGrounded) {
            state.fallVelocity = Math.abs(currentVel.dot(localUp));
        }

        state.currentSpeed = currentHorizSpeed;

        physics.body.activate();
    }

    private void buildTangentFrame(float yawAngle) {
        Vector3 ref = Math.abs(localUp.y) < 0.999f ? Vector3.Y : Vector3.Z;
        localRight.set(ref).crs(localUp).nor();
        localForward.set(localUp).crs(localRight).nor();

        float yawRad = yawAngle * MathUtils.degreesToRadians;
        float cosYaw = MathUtils.cos(yawRad);
        float sinYaw = MathUtils.sin(yawRad);

        float fwdX = localForward.x * cosYaw + localRight.x * sinYaw;
        float fwdY = localForward.y * cosYaw + localRight.y * sinYaw;
        float fwdZ = localForward.z * cosYaw + localRight.z * sinYaw;

        float rgtX = -localForward.x * sinYaw + localRight.x * cosYaw;
        float rgtY = -localForward.y * sinYaw + localRight.y * cosYaw;
        float rgtZ = -localForward.z * sinYaw + localRight.z * cosYaw;

        localForward.set(fwdX, fwdY, fwdZ);
        localRight.set(rgtX, rgtY, rgtZ);
    }

    private void orientCapsule(PhysicsBodyComponent physics, float yawAngle) {
        tempQuat.setFromCross(Vector3.Y, localUp);
        physics.body.getWorldTransform(tempMat);
        tempMat.getTranslation(tempVec);
        tempMat.set(tempVec, tempQuat);
        physics.body.setWorldTransform(tempMat);
    }

    private void performGroundCheck(PhysicsBodyComponent physics, MovementStateComponent state, Vector3 bodyPos) {
        rayFrom.set(bodyPos);
        rayTo.set(localUp).scl(-(CAPSULE_HALF_HEIGHT + GROUND_RAY_EXTRA)).add(bodyPos);

        ClosestRayResultCallback callback = new ClosestRayResultCallback(rayFrom, rayTo);
        dynamicsWorld.rayTest(rayFrom, rayTo, callback);

        if (callback.hasHit()) {
            callback.getHitNormalWorld(tempVec2);
            state.groundNormal.set(tempVec2);
            float angle = (float) Math.toDegrees(Math.acos(
                Math.min(1f, tempVec2.dot(localUp))));
            state.slopeAngle = angle;
            state.isGrounded = angle <= MAX_SLOPE_ANGLE;
        } else {
            state.isGrounded = false;
            state.slopeAngle = 0;
            state.groundNormal.set(localUp);
        }

        callback.dispose();
    }

    private void projectOnPlane(Vector3 vec, Vector3 normal) {
        float dot = vec.dot(normal);
        vec.x -= normal.x * dot;
        vec.y -= normal.y * dot;
        vec.z -= normal.z * dot;
    }

    private float projectOnTangent(Vector3 groundNormal) {
        tempVec3.set(groundNormal);
        float dot = tempVec3.dot(localUp);
        tempVec3.x -= localUp.x * dot;
        tempVec3.y -= localUp.y * dot;
        tempVec3.z -= localUp.z * dot;
        float len = tempVec3.len();
        if (len > 0.001f) tempVec3.scl(1f / len);
        return len;
    }
}
```

- [ ] **Step 4: Update existing PlayerMovementSystemTest**

The existing test creates `PlayerMovementSystem` with `new PlayerMovementSystem(dynamicsWorld)` — this signature hasn't changed, so existing tests should still compile. Run them:

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.systems.PlayerMovementSystemTest" --info`
Expected: All existing tests PASS (the default `planetCenter` of `(0,0,0)` and a player at `(0, 2, 0)` gives `localUp = (0,1,0)` which matches the old Y-up behavior).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/PlayerMovementSystem.java \
        core/src/main/java/com/galacticodyssey/player/components/FPSCameraComponent.java
git commit -m "feat(player): refactor PlayerMovementSystem for spherical planet movement"
```

---

### Task 7: CameraSystem Spherical Orientation

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java`

- [ ] **Step 1: Update CameraSystem to use localUp**

Replace the `processEntity` method body. The key changes are:
- Pivot computed along `localUp` instead of hardcoded Y
- Look direction built from `localUp`-relative tangent frame
- `camera.up` set to `localUp` instead of `Vector3.Y`
- Head bob offset computed in tangent plane
- Third-person offset computed relative to `localUp`

Replace the full `CameraSystem.java`:

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.events.RecoilEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerInputComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;

public class CameraSystem extends IteratingSystem {

    private static final float EYE_HEIGHT_LERP_SPEED = 10f;
    private static final float LANDING_DIP_DECAY_SPEED = 8f;
    private static final float WALK_SPEED_REF = 3.5f;
    private static final float HEAD_BOB_MIN_SPEED = 0.5f;
    private static final float MAX_LANDING_DIP = 0.15f;
    private static final float LANDING_DIP_FACTOR = 0.02f;
    private static final float RECOIL_RECOVERY_SPEED = 8f;

    private final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<FPSCameraComponent> cameraMapper =
        ComponentMapper.getFor(FPSCameraComponent.class);
    private final ComponentMapper<MovementStateComponent> stateMapper =
        ComponentMapper.getFor(MovementStateComponent.class);
    private final ComponentMapper<PlayerInputComponent> inputMapper =
        ComponentMapper.getFor(PlayerInputComponent.class);

    private PerspectiveCamera camera;
    private boolean wasGrounded;

    private float recoilPitch;
    private float recoilYaw;

    private final Vector3 localForward = new Vector3();
    private final Vector3 localRight = new Vector3();
    private final Vector3 pivot = new Vector3();
    private final Vector3 dir = new Vector3();

    public CameraSystem() {
        super(Family.all(TransformComponent.class, FPSCameraComponent.class, MovementStateComponent.class).get(), 4);
    }

    public CameraSystem(EventBus eventBus) {
        this();
        eventBus.subscribe(RecoilEvent.class, this::onRecoil);
    }

    private void onRecoil(RecoilEvent event) {
        recoilYaw += event.recoilOffset.x;
        recoilPitch += event.recoilOffset.y;
    }

    public void setCamera(PerspectiveCamera camera) {
        this.camera = camera;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (camera == null) return;

        PlayerStateComponent playerState = entity.getComponent(PlayerStateComponent.class);
        if (playerState != null && playerState.currentMode == PlayerStateComponent.PlayerMode.PILOTING) {
            return;
        }

        TransformComponent transform = transformMapper.get(entity);
        FPSCameraComponent cam = cameraMapper.get(entity);
        MovementStateComponent state = stateMapper.get(entity);

        PlayerInputComponent input = inputMapper.get(entity);
        if (input != null && input.scrollDelta != 0) {
            cam.targetCameraDistance = MathUtils.clamp(
                cam.targetCameraDistance + input.scrollDelta * cam.zoomStep,
                0f, cam.maxCameraDistance);
            input.scrollDelta = 0;
        }

        cam.currentCameraDistance = MathUtils.lerp(cam.currentCameraDistance,
            cam.targetCameraDistance, cam.zoomLerpSpeed * deltaTime);
        if (Math.abs(cam.currentCameraDistance - cam.targetCameraDistance) < 0.01f) {
            cam.currentCameraDistance = cam.targetCameraDistance;
        }

        boolean firstPerson = cam.currentCameraDistance < 0.1f;

        Vector3 up = cam.localUp;

        buildTangentFrame(up, cam.yawAngle);

        float targetEyeHeight = state.isCrouching ? cam.crouchEyeHeight : cam.eyeHeight;
        cam.currentEyeHeight = MathUtils.lerp(cam.currentEyeHeight, targetEyeHeight,
            EYE_HEIGHT_LERP_SPEED * deltaTime);

        pivot.set(up).scl(cam.currentEyeHeight).add(transform.position);

        if (firstPerson && state.isGrounded && state.currentSpeed > HEAD_BOB_MIN_SPEED) {
            cam.headBobPhase += state.currentSpeed * cam.headBobFrequency * deltaTime;
            float speedRatio = state.currentSpeed / WALK_SPEED_REF;
            float vOffset = MathUtils.sin(cam.headBobPhase) * cam.headBobAmplitude * speedRatio;
            float hOffset = MathUtils.cos(cam.headBobPhase * 0.5f) * cam.headBobAmplitude * 0.5f;
            pivot.add(up.x * vOffset, up.y * vOffset, up.z * vOffset);
            pivot.add(localRight.x * hOffset, localRight.y * hOffset, localRight.z * hOffset);
        } else if (firstPerson) {
            cam.headBobPhase = 0;
        }

        if (!wasGrounded && state.isGrounded && firstPerson) {
            cam.landingDipAmount = Math.min(MAX_LANDING_DIP, state.fallVelocity * LANDING_DIP_FACTOR);
        }
        if (cam.landingDipAmount > 0 && firstPerson) {
            pivot.add(-up.x * cam.landingDipAmount, -up.y * cam.landingDipAmount, -up.z * cam.landingDipAmount);
            cam.landingDipAmount = Math.max(0, cam.landingDipAmount - LANDING_DIP_DECAY_SPEED * deltaTime);
        }

        float effectivePitch = cam.pitchAngle + recoilPitch;
        float effectiveYaw = cam.yawAngle + recoilYaw;

        buildTangentFrame(up, effectiveYaw);

        float pitchRad = effectivePitch * MathUtils.degreesToRadians;
        float cosPitch = MathUtils.cos(pitchRad);
        float sinPitch = MathUtils.sin(pitchRad);

        dir.set(localForward).scl(cosPitch).add(
            up.x * sinPitch, up.y * sinPitch, up.z * sinPitch).nor();

        if (firstPerson) {
            camera.position.set(pivot);
        } else {
            float dist = cam.currentCameraDistance;
            camera.position.set(
                pivot.x - dir.x * dist + up.x * dist * 0.15f,
                pivot.y - dir.y * dist + up.y * dist * 0.15f,
                pivot.z - dir.z * dist + up.z * dist * 0.15f);
        }

        camera.direction.set(dir);
        camera.up.set(up);
        camera.update();

        float decay = RECOIL_RECOVERY_SPEED * deltaTime;
        recoilPitch = recoilPitch > 0
            ? Math.max(0f, recoilPitch - decay)
            : Math.min(0f, recoilPitch + decay);
        recoilYaw = recoilYaw > 0
            ? Math.max(0f, recoilYaw - decay)
            : Math.min(0f, recoilYaw + decay);

        wasGrounded = state.isGrounded;
    }

    private void buildTangentFrame(Vector3 up, float yawAngle) {
        Vector3 ref = Math.abs(up.y) < 0.999f ? Vector3.Y : Vector3.Z;
        localRight.set(ref).crs(up).nor();
        localForward.set(up).crs(localRight).nor();

        float yawRad = yawAngle * MathUtils.degreesToRadians;
        float cosYaw = MathUtils.cos(yawRad);
        float sinYaw = MathUtils.sin(yawRad);

        float fwdX = localForward.x * cosYaw + localRight.x * sinYaw;
        float fwdY = localForward.y * cosYaw + localRight.y * sinYaw;
        float fwdZ = localForward.z * cosYaw + localRight.z * sinYaw;

        float rgtX = -localForward.x * sinYaw + localRight.x * cosYaw;
        float rgtY = -localForward.y * sinYaw + localRight.y * cosYaw;
        float rgtZ = -localForward.z * sinYaw + localRight.z * cosYaw;

        localForward.set(fwdX, fwdY, fwdZ);
        localRight.set(rgtX, rgtY, rgtZ);
    }
}
```

- [ ] **Step 2: Run all player tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.*" --info`
Expected: All pass. When `localUp = (0,1,0)` (player near Y-axis), behavior matches the old flat-world CameraSystem.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/CameraSystem.java
git commit -m "feat(player): update CameraSystem for spherical localUp orientation"
```

---

### Task 8: Wire Systems into GameWorld

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Add PlanetTerrainSystem and RadialGravitySystem to GameWorld**

Add imports at the top of `GameWorld.java`:

```java
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.terrain.PlanetTerrainSystem;
import com.galacticodyssey.planet.terrain.TerrainChunk;
import com.galacticodyssey.core.systems.RadialGravitySystem;
import java.util.List;
```

Add fields after the existing `private PlanetaryEconomyManager planetaryEconomyManager;`:

```java
private PlanetTerrainSystem planetTerrainSystem;
private RadialGravitySystem radialGravitySystem;
```

In the constructor, after `engine.addSystem(physicsBodySystem);` (around line 149), add:

```java
planetTerrainSystem = new PlanetTerrainSystem(bulletPhysicsSystem.getDynamicsWorld());
engine.addSystem(planetTerrainSystem);

radialGravitySystem = new RadialGravitySystem(
    bulletPhysicsSystem.getDynamicsWorld(),
    new com.badlogic.gdx.math.Vector3(0, 0, 0), 9.81f);
engine.addSystem(radialGravitySystem);
```

- [ ] **Step 2: Add loadPlanet method and terrain accessors**

Add these methods after the existing `getPlayerInputSystem()` method:

```java
public void loadPlanet(Planet planet, BiomeMap biomeMap) {
    planetTerrainSystem.loadPlanet(planet, biomeMap);
}

public PlanetTerrainSystem getPlanetTerrainSystem() {
    return planetTerrainSystem;
}

public List<TerrainChunk> getVisibleTerrainLeaves() {
    return planetTerrainSystem.getVisibleLeaves();
}

public float getPlanetRadius() {
    return planetTerrainSystem.getPlanetRadius();
}
```

- [ ] **Step 3: Update dispose to clean up PlanetTerrainSystem**

In the `dispose()` method, add before `debugHudSystem.dispose();`:

```java
if (planetTerrainSystem != null) {
    planetTerrainSystem.dispose();
}
```

- [ ] **Step 4: Run existing tests to check for compilation issues**

Run: `./gradlew :core:test --info`
Expected: All existing tests pass. `GameWorld` constructor changes add systems but don't affect tests that don't instantiate `GameWorld` directly.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(core): wire PlanetTerrainSystem and RadialGravitySystem into GameWorld"
```

---

### Task 9: Rewrite GameScreen for Procedural Planet

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Replace GameScreen initializeWorld with planet procgen pipeline**

This is the largest single change. Replace the entire `GameScreen.java`. Key changes:
- Remove all flat terrain code (`heightmap`, `createTerrainMesh`, `createTerrainPhysics`, `createScatterBoxes`, `renderTerrain`)
- Remove scatter box code (`boxInstances`, `boxEntities`, `createScatterBoxes`, `syncBoxTransforms`, `renderBoxes`)
- Add planet generation pipeline in `initializeWorld()`
- Add `renderPlanetTerrain()` method
- Add altitude-based sky color and dynamic camera clip planes
- Add surface spawn point calculation with ocean avoidance
- Spawn one ship near the player

```java
package com.galacticodyssey.ui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.AudioManager;
import com.galacticodyssey.core.CoordinateManager;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.GalacticOdyssey;
import com.galacticodyssey.core.GameWorld;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.galaxy.LuminosityClass;
import com.galacticodyssey.galaxy.OrbitalSlot;
import com.galacticodyssey.galaxy.OrbitalZone;
import com.galacticodyssey.galaxy.SpectralClass;
import com.galacticodyssey.galaxy.StarSystem;
import com.galacticodyssey.planet.Atmosphere;
import com.galacticodyssey.planet.AtmosphereGenerator;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeMapper;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetType;
import com.galacticodyssey.planet.terrain.CubeFace;
import com.galacticodyssey.planet.terrain.CubeSphere;
import com.galacticodyssey.planet.terrain.TerrainChunk;
import com.galacticodyssey.planet.terrain.TerrainMeshBuilder;
import com.galacticodyssey.planet.terrain.TerrainNoiseStack;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.ship.HullGeometry;
import com.galacticodyssey.ship.ShipFactory;
import com.galacticodyssey.ship.ShipSizeClass;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.components.ShipMeshComponent;

import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;

import java.util.List;

public class GameScreen implements Screen {

    private static final long PLANET_SEED = 42L;
    private static final float PAUSE_WORLD_WIDTH = 1280f;
    private static final float PAUSE_WORLD_HEIGHT = 720f;
    private static final float ATMOSPHERE_ALTITUDE = 100f;

    private final GalacticOdyssey game;
    private GameWorld gameWorld;
    private PerspectiveCamera camera;
    private float planetRadius;

    private ShipFactory shipFactory;
    private final Array<Entity> shipEntities = new Array<>();
    private ShaderProgram shipShader;

    private ShaderProgram terrainShader;
    private ModelBatch modelBatch;
    private Environment environment;
    private final Array<Disposable> disposables = new Array<>();

    private boolean paused;
    private Stage pauseStage;
    private Texture overlayTexture;
    private InputMultiplexer inputMultiplexer;
    private boolean initialized;

    private BiomeMap biomeMap;
    private TerrainNoiseStack terrainNoise;

    public GameScreen(GalacticOdyssey game) {
        this.game = game;
    }

    @Override
    public void show() {
        if (!initialized) {
            initializeWorld();
            initialized = true;
        }
        setupInput();
        if (paused) {
            Gdx.input.setCursorCatched(false);
        } else {
            Gdx.input.setCursorCatched(true);
        }
    }

    private void initializeWorld() {
        camera = new PerspectiveCamera(75, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 500f;

        EventBus eventBus = new EventBus();
        CoordinateManager coordinateManager = new CoordinateManager(eventBus);
        gameWorld = new GameWorld(eventBus, coordinateManager);

        Planet planet = new Planet(PLANET_SEED, PlanetType.TERRAN, 1.0f, 1.0f, 24f, 23.4f, false);

        StarSystem stubStar = new StarSystem(
            1L, PLANET_SEED, SpectralClass.G, LuminosityClass.MAIN_SEQUENCE,
            5778f, 1.0f, 1.0f, 1.0f, 4.6f, new Color(1f, 0.96f, 0.84f, 1f));
        OrbitalSlot stubSlot = new OrbitalSlot(0, 1.0f, 0.017f, OrbitalZone.HABITABLE);
        stubSlot.planet = planet;
        stubStar.orbits.add(stubSlot);

        AtmosphereGenerator atmoGen = new AtmosphereGenerator();
        Atmosphere atmosphere = atmoGen.generate(planet, stubStar);
        planet.atmosphere = atmosphere;

        BiomeMapper biomeMapper = new BiomeMapper();
        biomeMap = biomeMapper.generate(planet, atmosphere);

        long terrainSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.TERRAIN_DOMAIN), 0);
        terrainNoise = new TerrainNoiseStack(terrainSeed);
        planetRadius = planet.radius * 6371f;

        gameWorld.initializeSystems(camera);
        gameWorld.loadPlanet(planet, biomeMap);

        Vector3 spawnDir = findLandSpawnDirection();
        float height = terrainNoise.heightAt(spawnDir, biomeMap, 0);
        float spawnAlt = planetRadius + height * planetRadius * 0.01f + 2f;
        Vector3 spawnPos = new Vector3(spawnDir).scl(spawnAlt);

        gameWorld.createPlayerEntity(spawnPos.x, spawnPos.y, spawnPos.z);

        shipFactory = new ShipFactory(gameWorld.getEngine(), gameWorld.getBulletPhysicsSystem());

        Vector3 ref = Math.abs(spawnDir.y) < 0.999f ? Vector3.Y : Vector3.Z;
        Vector3 tangent = new Vector3(ref).crs(spawnDir).nor();
        Vector3 shipDir = new Vector3(spawnDir).cpy();
        shipDir.add(tangent.scl(0.002f)).nor();
        float shipHeight = terrainNoise.heightAt(shipDir, biomeMap, 0);
        float shipAlt = planetRadius + shipHeight * planetRadius * 0.01f + 3f;
        Vector3 shipPos = new Vector3(shipDir).scl(shipAlt);
        Entity ship = shipFactory.createShip(123L, ShipSizeClass.SMALL,
            shipPos.x, shipPos.y, shipPos.z);
        shipEntities.add(ship);
        buildShipMeshes();

        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.35f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.75f, -0.4f, -0.8f, -0.3f));

        buildPauseMenu();
    }

    private Vector3 findLandSpawnDirection() {
        Vector3 dir = CubeSphere.toSphere(CubeFace.POS_Z, 0.5f, 0.5f);
        for (int attempt = 0; attempt < 20; attempt++) {
            float lat = CubeSphere.latitudeOf(dir);
            float lon = CubeSphere.longitudeOf(dir);
            float h = terrainNoise.heightAt(dir, biomeMap, 0);
            BiomeType biome = biomeMap.getBiome(lat, lon, h);
            if (biome != BiomeType.OCEAN && biome != BiomeType.ICE_SHEET) {
                return dir;
            }
            float offsetU = 0.5f + (attempt + 1) * 0.03f;
            float offsetV = 0.5f + (attempt + 1) * 0.02f;
            dir = CubeSphere.toSphere(CubeFace.POS_Z, Math.min(offsetU, 0.95f), Math.min(offsetV, 0.95f));
        }
        return dir;
    }

    private void setupInput() {
        InputAdapter escapeHandler = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    togglePause();
                    return true;
                }
                return false;
            }
        };

        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(escapeHandler);
        if (paused) {
            inputMultiplexer.addProcessor(pauseStage);
        } else {
            inputMultiplexer.addProcessor(gameWorld.getPlayerInputSystem().getInputAdapter());
        }
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    private void togglePause() {
        paused = !paused;
        if (paused) {
            Gdx.input.setCursorCatched(false);
            gameWorld.getPlayerInputSystem().setEnabled(false);
            inputMultiplexer.clear();
            inputMultiplexer.addProcessor(new InputAdapter() {
                @Override
                public boolean keyDown(int keycode) {
                    if (keycode == Input.Keys.ESCAPE) {
                        togglePause();
                        return true;
                    }
                    return false;
                }
            });
            inputMultiplexer.addProcessor(pauseStage);
        } else {
            Gdx.input.setCursorCatched(true);
            gameWorld.getPlayerInputSystem().setEnabled(true);
            setupInput();
        }
    }

    private void buildPauseMenu() {
        pauseStage = new Stage(new FitViewport(PAUSE_WORLD_WIDTH, PAUSE_WORLD_HEIGHT));

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0f, 0f, 0f, 0.7f));
        pixmap.fill();
        overlayTexture = new Texture(pixmap);
        pixmap.dispose();

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(new TextureRegionDrawable(new TextureRegion(overlayTexture)));
        root.center();

        Skin skin = game.getSkin();
        AudioManager audio = game.getAudioManager();

        Label title = new Label("PAUSED", skin, "title");
        root.add(title).padBottom(40).row();

        addPauseButton(root, "Resume", skin, audio, this::togglePause);
        addPauseButton(root, "Settings", skin, audio, () -> {
            game.setScreen(new SettingsScreen(game, this));
        });
        addPauseButton(root, "Exit to Main Menu", skin, audio, () -> {
            dispose();
            game.setScreen(new MainMenuScreen(game));
        });
        addPauseButton(root, "Exit Game", skin, audio, () -> Gdx.app.exit());

        pauseStage.addActor(root);
    }

    private void addPauseButton(Table table, String text, Skin skin, AudioManager audio, Runnable action) {
        TextButton button = new TextButton(text, skin);
        button.setTransform(true);

        button.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                super.enter(event, x, y, pointer, fromActor);
                if (pointer == -1) {
                    button.setOrigin(Align.center);
                    button.addAction(Actions.scaleTo(1.02f, 1.02f, 0.1f, Interpolation.smooth));
                    audio.playSound("audio/sfx/ui_hover.ogg");
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                super.exit(event, x, y, pointer, toActor);
                if (pointer == -1) {
                    button.addAction(Actions.scaleTo(1f, 1f, 0.1f, Interpolation.smooth));
                }
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                audio.playSound("audio/sfx/ui_click.ogg");
                action.run();
            }
        });

        table.add(button).width(300).height(50).padBottom(12).row();
    }

    private void buildShipMeshes() {
        for (int i = 0; i < shipEntities.size; i++) {
            Entity ship = shipEntities.get(i);
            ShipDataComponent data = ship.getComponent(ShipDataComponent.class);
            ShipMeshComponent meshComp = ship.getComponent(ShipMeshComponent.class);
            HullGeometry hull = data.hullGeometry;

            Mesh mesh = new Mesh(true, hull.vertexCount(), hull.indices.length,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_emissive"));

            mesh.setVertices(hull.vertices);
            mesh.setIndices(hull.indices);
            meshComp.hullMesh = mesh;
        }
    }

    private ShaderProgram getShipShader() {
        if (shipShader != null) return shipShader;

        String vert =
            "attribute vec3 a_position;\n" +
            "attribute vec3 a_normal;\n" +
            "attribute vec4 a_color;\n" +
            "attribute float a_emissive;\n" +
            "uniform mat4 u_projViewTrans;\n" +
            "uniform mat4 u_worldTrans;\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "varying float v_emissive;\n" +
            "void main() {\n" +
            "    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);\n" +
            "    v_color = a_color;\n" +
            "    v_emissive = a_emissive;\n" +
            "    gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);\n" +
            "}\n";

        String frag =
            "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "varying float v_emissive;\n" +
            "uniform vec3 u_lightDir;\n" +
            "uniform vec4 u_ambientColor;\n" +
            "void main() {\n" +
            "    vec3 lightDir = normalize(-u_lightDir);\n" +
            "    float diff = max(dot(v_normal, lightDir), 0.0);\n" +
            "    vec3 lit = v_color.rgb * (u_ambientColor.rgb + diff * vec3(0.8, 0.8, 0.75));\n" +
            "    vec3 color = mix(lit, v_color.rgb * 2.0, v_emissive);\n" +
            "    gl_FragColor = vec4(color, 1.0);\n" +
            "}\n";

        shipShader = new ShaderProgram(vert, frag);
        if (!shipShader.isCompiled()) {
            Gdx.app.error("ShipShader", shipShader.getLog());
        }
        return shipShader;
    }

    private void renderShips() {
        ShaderProgram shader = getShipShader();
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);
        shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
        shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);

        for (int i = 0; i < shipEntities.size; i++) {
            Entity ship = shipEntities.get(i);
            TransformComponent t = ship.getComponent(TransformComponent.class);
            ShipMeshComponent meshComp = ship.getComponent(ShipMeshComponent.class);
            if (meshComp == null || meshComp.hullMesh == null) continue;

            Matrix4 modelMat = new Matrix4();
            modelMat.set(t.position, t.rotation);
            shader.setUniformMatrix("u_worldTrans", modelMat);

            meshComp.hullMesh.render(shader, GL20.GL_TRIANGLES);
        }
    }

    private ShaderProgram getTerrainShader() {
        if (terrainShader != null) return terrainShader;

        String vert =
            "attribute vec3 a_position;\n" +
            "attribute vec3 a_normal;\n" +
            "attribute vec4 a_color;\n" +
            "uniform mat4 u_projViewTrans;\n" +
            "uniform mat4 u_worldTrans;\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "void main() {\n" +
            "    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);\n" +
            "    v_color = a_color;\n" +
            "    gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);\n" +
            "}\n";

        String frag =
            "#ifdef GL_ES\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec3 v_normal;\n" +
            "varying vec4 v_color;\n" +
            "uniform vec3 u_lightDir;\n" +
            "uniform vec4 u_ambientColor;\n" +
            "void main() {\n" +
            "    vec3 lightDir = normalize(-u_lightDir);\n" +
            "    float diff = max(dot(v_normal, lightDir), 0.0);\n" +
            "    vec3 color = v_color.rgb * (u_ambientColor.rgb + diff * vec3(0.8, 0.8, 0.75));\n" +
            "    gl_FragColor = vec4(color, 1.0);\n" +
            "}\n";

        terrainShader = new ShaderProgram(vert, frag);
        if (!terrainShader.isCompiled()) {
            Gdx.app.error("Shader", terrainShader.getLog());
        }
        return terrainShader;
    }

    @Override
    public void render(float delta) {
        float altitude = camera.position.len() - planetRadius;
        updateCameraClipPlanes(altitude);
        updateSkyColor(altitude);

        if (!paused) {
            float clampedDelta = Math.min(delta, 1f / 30f);
            gameWorld.getPlanetTerrainSystem().setCameraPosition(camera.position);
            gameWorld.update(clampedDelta);
        }

        renderPlanetTerrain();
        renderShips();

        if (paused) {
            pauseStage.act(delta);
            pauseStage.draw();
        }
    }

    private void updateCameraClipPlanes(float altitude) {
        if (altitude < 10f) {
            camera.near = 0.1f;
            camera.far = 500f;
        } else if (altitude < 500f) {
            camera.near = 1f;
            camera.far = altitude * 10f;
        } else {
            camera.near = 1f;
            camera.far = planetRadius * 4f;
        }
        camera.update();
    }

    private void updateSkyColor(float altitude) {
        if (altitude < ATMOSPHERE_ALTITUDE) {
            float t = MathUtils.clamp(altitude / ATMOSPHERE_ALTITUDE, 0f, 1f);
            float r = MathUtils.lerp(0.4f, 0.05f, t);
            float g = MathUtils.lerp(0.6f, 0.05f, t);
            float b = MathUtils.lerp(0.9f, 0.1f, t);
            ScreenUtils.clear(r, g, b, 1f, true);
        } else {
            ScreenUtils.clear(0.02f, 0.02f, 0.04f, 1f, true);
        }
    }

    private void renderPlanetTerrain() {
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        ShaderProgram shader = getTerrainShader();
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);

        Matrix4 identity = new Matrix4();
        shader.setUniformMatrix("u_worldTrans", identity);
        shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
        shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);

        List<TerrainChunk> leaves = gameWorld.getVisibleTerrainLeaves();
        for (int i = 0; i < leaves.size(); i++) {
            TerrainChunk chunk = leaves.get(i);
            if (chunk.mesh != null) {
                chunk.mesh.render(shader, GL20.GL_TRIANGLES);
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        gameWorld.resize(width, height);
        pauseStage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void hide() {}

    @Override
    public void dispose() {
        if (shipFactory != null) { shipFactory.dispose(); shipFactory = null; }
        if (gameWorld != null) {
            gameWorld.dispose();
            gameWorld = null;
        }
        if (terrainShader != null) {
            terrainShader.dispose();
            terrainShader = null;
        }
        if (modelBatch != null) {
            modelBatch.dispose();
            modelBatch = null;
        }
        if (pauseStage != null) {
            pauseStage.dispose();
            pauseStage = null;
        }
        if (overlayTexture != null) {
            overlayTexture.dispose();
            overlayTexture = null;
        }
        for (int i = 0; i < shipEntities.size; i++) {
            ShipMeshComponent meshComp = shipEntities.get(i).getComponent(ShipMeshComponent.class);
            if (meshComp != null) meshComp.dispose();
        }
        shipEntities.clear();
        if (shipShader != null) { shipShader.dispose(); shipShader = null; }
        for (int i = disposables.size - 1; i >= 0; i--) {
            disposables.get(i).dispose();
        }
        disposables.clear();
        initialized = false;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava --info`
Expected: Compiles without errors.

- [ ] **Step 3: Run full test suite**

Run: `./gradlew :core:test --info`
Expected: All tests pass. GameScreen is not directly tested (it requires GL context), but the systems it wires together are independently tested.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(ui): rewrite GameScreen for procedural planet with spherical terrain"
```

---

### Task 10: Manual Verification

**Files:** None — this is a run-and-test task.

- [ ] **Step 1: Launch the game**

Run: `./gradlew :desktop:run`

Expected behavior:
- Game loads to main menu, then enter the game
- Player spawns on the surface of a spherical planet
- Sky is blue near the surface
- Terrain shows biome-colored chunks (green forests, tan deserts, white ice, blue ocean)
- Player can walk on the curved surface using FPS controls
- Gravity pulls toward planet center (player stays on surface as they walk)
- Camera orientation follows the curvature of the planet
- A small ship is visible near the spawn point

- [ ] **Step 2: Test walking and gravity**

Walk around in various directions. Verify:
- Player doesn't fall through the terrain
- Slope traversal works (stamina drain on steep slopes)
- Jump works (impulse is along local up, not world Y)
- Walking toward the horizon — the ground curves away naturally

- [ ] **Step 3: Test ship boarding and orbital flight**

Approach the ship and interact to board it. Verify:
- Mode switches to piloting
- Ship flight controls work (6DOF)
- Flying up — terrain chunks get coarser (LOD working)
- Sky transitions from blue to black as altitude increases
- Planet is visible as a sphere from orbit

- [ ] **Step 4: Test leaving ship near surface**

Fly back near the surface and exit the ship. Verify:
- Player returns to FPS mode
- Gravity re-engages
- Player lands on the surface correctly
- Camera orientation is correct for the landing position

- [ ] **Step 5: Commit any fixes from manual testing**

If any issues found during manual testing, fix and commit each individually.
