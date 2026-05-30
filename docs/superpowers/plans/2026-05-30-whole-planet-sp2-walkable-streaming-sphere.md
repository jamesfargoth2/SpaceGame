# SP2 — Walkable Streaming Sphere Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the player walk on the real spherical planet — near-field sphere chunks stream in around them at ~19 m walkable fidelity with collision, generated asynchronously so movement never hitches; the 5 km flat pad is retired.

**Architecture:** Deepen the existing SP1 cube-sphere quadtree (`MAX_DEPTH` 8→14) and add high-frequency LOD-gated detail octaves. Move chunk mesh + Bullet collision-shape construction off the main thread into a new `TerrainStreamer` worker pool; the main thread drains completed jobs (budgeted), uploads GL meshes, and adds rigid bodies. Collision bodies are gated to a wide-but-bounded shell around the player. `GameScreen` stops rendering/colliding the flat pad and renders sphere leaves from the ground up, keeping the existing high-altitude backdrop fade (SP3's seam). Player + starter ship spawn via a new `surfaceHeightAt` query; decorative dressing is disabled behind follow-up markers.

**Tech Stack:** Java 17, libGDX 1.13 (LWJGL3), gdx-bullet, Ashley ECS, JUnit 5 + Mockito. Build: Gradle (`./gradlew`). Tests are headless/GL-free (architectural rule 5).

**Spec:** [docs/superpowers/specs/2026-05-30-whole-planet-sp2-walkable-streaming-sphere-design.md](../specs/2026-05-30-whole-planet-sp2-walkable-streaming-sphere-design.md)

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `planet/terrain/TerrainNoiseStack.java` *(modify)* | Add high-freq detail octaves at `lod ≥ 7/9/11/13`. | 1 |
| `planet/terrain/TerrainChunk.java` *(modify)* | `MAX_DEPTH` 8→14; chunk state enum; generation token; `wantsCollision` flag. | 2 |
| `planet/terrain/PlanetTerrainSystem.java` *(modify)* | `surfaceRadiusKmAt` / `surfacePointLocal`; `MAX_LOD` constant; spawn pre-warm hook; drain streamer in `update`. | 3, 6 |
| `planet/terrain/GeneratedChunkData.java` *(new)* | Immutable result struct passed worker→main (verts, indices, optional shape/triMesh, token). | 4 |
| `planet/terrain/TerrainStreamer.java` *(new)* | Worker pool, job submit, concurrent result queue, budgeted drain callback, dispose. | 4 |
| `planet/terrain/TerrainQuadtree.java` *(modify)* | Enqueue via streamer; drain → GL upload + `addRigidBody`; stale-token discard; collision gating; bounded resident set. | 5 |
| `core/GameWorld.java` *(modify)* | `spawnPlayerOnSphere` helper using `surfacePointLocal` + pre-warm. | 6 |
| `ui/GameScreen.java` *(modify)* | Remove flat-pad render + lower gate; render sphere from ground; re-anchor player/ship spawn; disable dressing. | 7, 8 |

---

## Conventions for every task

- Run a single test class: `./gradlew :core:test --tests "com.galacticodyssey.<...>"`
- Run the full core suite: `./gradlew :core:test`
- Bullet must be initialised before constructing collision shapes in a test. Use the existing pattern (see `TerrainQuadtreeRebaseTest` / `TerrainChunkCollisionTest`): call `com.badlogic.gdx.physics.bullet.Bullet.init()` once in `@BeforeAll`.
- GL `Mesh` upload throws headless and is already caught in `TerrainQuadtree.generateMesh` (`chunk.mesh = null`); preserve that — tests assert on collision + `MeshData`, never on a live `Mesh`.
- Commit after every task with the message shown in its final step.

---

## Task 1: High-frequency detail octaves

Deeper chunks must reveal *real* meter-scale relief, not a re-tessellated smooth curve. Today `TerrainNoiseStack.sampleAt` adds detail only at `lod ≥ 3` (freq 64) and `lod ≥ 5` (freq 256). Add progressive octaves for the new deep LODs.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainNoiseStack.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainNoiseStackTest.java`

- [ ] **Step 1: Write the failing test**

Add to `TerrainNoiseStackTest`:

```java
@Test
void highLodAddsSubTwentyMetreRelief() {
    // Two surface points ~15 m apart on a 6371 km planet differ in height at high LOD,
    // proving fine octaves contribute relief at walkable scale (not flat interpolation).
    TerrainNoiseStack noise = new TerrainNoiseStack(12345L);
    BiomeMap biome = new BiomeMap(0L, 0f, 6000f, 0.5f, 293f,
        java.util.EnumSet.allOf(com.galacticodyssey.planet.BiomeType.class));

    // ~15 m angular separation: 15 m / 6_371_000 m radius (radians) on the +Z face.
    float d = 15f / 6_371_000f;
    com.badlogic.gdx.math.Vector3 a = new com.badlogic.gdx.math.Vector3(0f, 0f, 1f).nor();
    com.badlogic.gdx.math.Vector3 b = new com.badlogic.gdx.math.Vector3(d, 0f, 1f).nor();

    float hLowA = noise.heightAt(a, biome, 5);
    float hLowB = noise.heightAt(b, biome, 5);
    float hHighA = noise.heightAt(a, biome, 13);
    float hHighB = noise.heightAt(b, biome, 13);

    // At lod 5 the two points are nearly identical (coarse). At lod 13 the fine octaves
    // separate them measurably.
    float lowDelta = Math.abs(hLowA - hLowB);
    float highDelta = Math.abs(hHighA - hHighB);
    assertTrue(highDelta > lowDelta * 2f,
        "high-lod relief (" + highDelta + ") should exceed low-lod (" + lowDelta + ")");
}

@Test
void highLodIsDeterministic() {
    TerrainNoiseStack n1 = new TerrainNoiseStack(777L);
    TerrainNoiseStack n2 = new TerrainNoiseStack(777L);
    BiomeMap biome = new BiomeMap(0L, 0f, 6000f, 0.5f, 293f,
        java.util.EnumSet.allOf(com.galacticodyssey.planet.BiomeType.class));
    com.badlogic.gdx.math.Vector3 dir = new com.badlogic.gdx.math.Vector3(0.3f, 0.2f, 1f).nor();
    assertEquals(n1.heightAt(dir, biome, 13), n2.heightAt(dir, biome, 13), 0f);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainNoiseStackTest"`
Expected: `highLodAddsSubTwentyMetreRelief` FAILS (current code adds nothing past lod 5, so `highDelta ≈ lowDelta`).

- [ ] **Step 3: Add the octaves**

In `TerrainNoiseStack.sampleAt`, after the existing `if (lod >= 5) { ... }` block (currently ending at line ~85) and before `Sample s = new Sample();`, insert:

```java
        // SP2: progressive high-frequency octaves so deep chunks carry real meter-scale
        // relief instead of re-tessellating a smooth surface. Each step roughly doubles
        // frequency and halves contribution; amplitudes stay well inside the height
        // envelope so slopes remain walkable.
        if (lod >= 7) {
            float n = detailNoise.fbm(dir.x * 512f, dir.y * 512f, dir.z * 512f, 3, 0.5f, 2.0f);
            height += n * amplitude * 0.010f;
        }
        if (lod >= 9) {
            float n = detailNoise.fbm(dir.x * 1024f, dir.y * 1024f, dir.z * 1024f, 3, 0.5f, 2.0f);
            height += n * amplitude * 0.005f;
        }
        if (lod >= 11) {
            float n = detailNoise.fbm(dir.x * 2048f, dir.y * 2048f, dir.z * 2048f, 2, 0.5f, 2.0f);
            height += n * amplitude * 0.0025f;
        }
        if (lod >= 13) {
            float n = detailNoise.fbm(dir.x * 4096f, dir.y * 4096f, dir.z * 4096f, 2, 0.5f, 2.0f);
            height += n * amplitude * 0.0012f;
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainNoiseStackTest"`
Expected: PASS (both new tests + existing ones).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/TerrainNoiseStack.java core/src/test/java/com/galacticodyssey/planet/terrain/TerrainNoiseStackTest.java
git commit -m "feat(terrain): high-frequency detail octaves for deep LOD chunks"
```

---

## Task 2: Deepen LOD + chunk state/token/collision flag

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainChunk.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainChunkLodTest.java`

- [ ] **Step 1: Write the failing test**

Add to `TerrainChunkLodTest`:

```java
@Test
void finestVertexSpacingIsWalkable() {
    // At MAX_DEPTH on an Earth-radius planet, leaf vertex spacing should be ~10-25 m.
    double radiusKm = 6371.0;
    // Arc length of a full face ≈ radius * (pi/2). A depth-d chunk spans 1/2^d of the face.
    double faceArcM = radiusKm * 1000.0 * (Math.PI / 2.0);
    double chunkArcM = faceArcM / Math.pow(2, TerrainChunk.MAX_DEPTH);
    double vertexSpacingM = chunkArcM / (com.galacticodyssey.planet.terrain.TerrainMeshBuilder.GRID_SIZE - 1);
    assertTrue(vertexSpacingM > 8.0 && vertexSpacingM < 30.0,
        "finest vertex spacing was " + vertexSpacingM + " m");
}

@Test
void generationTokenBumpsOnInvalidate() {
    TerrainChunk c = new TerrainChunk(CubeFace.POS_Z, 0, 0f, 0f, 1f, 1f, 6371.0);
    int t0 = c.generationToken();
    c.invalidate();
    assertNotEquals(t0, c.generationToken());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainChunkLodTest"`
Expected: FAIL — `MAX_DEPTH=8` gives ~1.2 km spacing (too large), and `generationToken()`/`invalidate()` don't exist.

- [ ] **Step 3: Edit `TerrainChunk`**

Change the constant (line 13):

```java
    public static final int MAX_DEPTH = 14;
```

Add fields after the existing collision fields (after line 31, `public btBvhTriangleMeshShape collisionShape;`):

```java
    /** Lifecycle of this chunk's geometry. */
    public enum State { EMPTY, PENDING, GENERATING, READY }
    public State state = State.EMPTY;
    /** True when this chunk should carry a Bullet collision body (set by the collision gate). */
    public boolean wantsCollision;

    /** Bumped whenever this chunk's geometry is invalidated (split/merge), so async results
     *  generated against an older token can be detected and discarded. */
    private int generationToken;
    public int generationToken() { return generationToken; }
    public void invalidate() { generationToken++; state = State.EMPTY; }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainChunkLodTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/TerrainChunk.java core/src/test/java/com/galacticodyssey/planet/terrain/TerrainChunkLodTest.java
git commit -m "feat(terrain): deepen LOD to depth 14, add chunk state + generation token"
```

---

## Task 3: `surfaceHeightAt` seam + `MAX_LOD`

The seam consumed by spawn (Task 6) and every later dressing sub-project.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystemScaleTest.java`

- [ ] **Step 1: Write the failing test**

Add to `PlanetTerrainSystemScaleTest`:

```java
@Test
void surfaceRadiusMatchesMeshVertexHeight() {
    Planet planet = TestPlanets.earthLike(); // existing helper in this test; if absent, see note below
    BiomeMap biome = new BiomeMap(0L, 0f, 6000f, 0.5f, 293f,
        java.util.EnumSet.allOf(com.galacticodyssey.planet.BiomeType.class));
    PlanetTerrainSystem sys = new PlanetTerrainSystem(null);
    sys.loadPlanet(planet, biome, com.galacticodyssey.core.coords.PlanetCoordsKM.ORIGIN);

    com.badlogic.gdx.math.Vector3 dir = new com.badlogic.gdx.math.Vector3(0f, 1f, 0f).nor();
    double rKm = sys.surfaceRadiusKmAt(dir);
    // Must equal radiusKm + heightAt(dir, MAX_LOD) * radiusKm * 0.002 (the SP1 formula).
    double expected = sys.getRadiusKm()
        + planetHeightAt(planet, biome, dir) * sys.getRadiusKm() * 0.002; // helper below
    assertEquals(expected, rKm, 1e-6);
    assertTrue(rKm >= sys.getRadiusKm() - 50 && rKm <= sys.getRadiusKm() + 50,
        "surface radius should be within tens of km of base radius");
}
```

> Note for the implementer: if `TestPlanets.earthLike()` does not exist, construct a `Planet` the same way the surrounding tests in this file already do (copy their setup), and inline the expected height by building a `TerrainNoiseStack` with the same `terrainSeed` derivation `PlanetTerrainSystem.loadPlanet` uses, then call `heightAt(dir, biome, PlanetTerrainSystem.MAX_LOD)`. Keep the assertion that `surfaceRadiusKmAt` equals base radius + that height × radius × 0.002.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.PlanetTerrainSystemScaleTest"`
Expected: FAIL — `surfaceRadiusKmAt` and `MAX_LOD` don't exist.

- [ ] **Step 3: Implement the seam**

In `PlanetTerrainSystem`, add a constant near `EARTH_RADIUS_KM` (line 19):

```java
    /** LOD level at which the surface height query samples (matches the deepest detail octave). */
    public static final int MAX_LOD = com.galacticodyssey.planet.terrain.TerrainChunk.MAX_DEPTH;
```

Store the noise + biome so the query can sample them. Add fields near `radiusKm` (line 23):

```java
    private TerrainNoiseStack noise;
    private BiomeMap biomeMap;
```

In `loadPlanet(planet, biomeMap, tectonic, originPlanetKm)` assign them where `noise` is built (after line 45):

```java
        this.noise = noise;
        this.biomeMap = biomeMap;
```

Add the query methods after `getOriginPlanetKm()` (line 80):

```java
    /** Surface radius in km (including terrain height) at a unit direction from the planet centre. */
    public double surfaceRadiusKmAt(com.badlogic.gdx.math.Vector3 unitDir) {
        if (noise == null) return radiusKm;
        float h = noise.heightAt(unitDir, biomeMap, MAX_LOD);
        return radiusKm + h * radiusKm * 0.002; // SP1 height envelope (TerrainMeshBuilder line 38)
    }

    /** Surface point in the current LOCAL frame (metres) along the given unit direction. */
    public com.galacticodyssey.core.coords.LocalCoordsM surfacePointLocal(com.badlogic.gdx.math.Vector3 unitDir) {
        double rKm = surfaceRadiusKmAt(unitDir);
        com.galacticodyssey.core.coords.PlanetCoordsKM p = new com.galacticodyssey.core.coords.PlanetCoordsKM(
            unitDir.x * rKm, unitDir.y * rKm, unitDir.z * rKm);
        return com.galacticodyssey.core.coords.CoordConvert.planetToLocal(p, originPlanetKm);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.PlanetTerrainSystemScaleTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java core/src/test/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystemScaleTest.java
git commit -m "feat(terrain): surfaceHeightAt seam on PlanetTerrainSystem"
```

---

## Task 4: `GeneratedChunkData` + `TerrainStreamer` (async pipeline)

The core new piece. Worker threads build `MeshData` + (optionally) the `btBvhTriangleMeshShape` — all pure CPU, no GL, no world mutation. The main thread later drains results.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/GeneratedChunkData.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainStreamer.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainStreamerTest.java`

- [ ] **Step 1: Write the failing test**

Create `TerrainStreamerTest`:

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.physics.bullet.Bullet;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerrainStreamerTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    private TerrainNoiseStack noise() { return new TerrainNoiseStack(99L); }
    private BiomeMap biome() {
        return new BiomeMap(0L, 0f, 6000f, 0.5f, 293f, EnumSet.allOf(BiomeType.class));
    }

    @Test
    void drainProducesSameMeshAsSynchronousBuild() throws Exception {
        TerrainNoiseStack noise = noise();
        BiomeMap biome = biome();
        double radiusKm = 6371.0;
        TerrainChunk chunk = new TerrainChunk(CubeFace.POS_Z, 4, 0.1f, 0.1f, 0.2f, 0.2f, radiusKm);

        // Synchronous reference.
        TerrainMeshBuilder.MeshData ref = TerrainMeshBuilder.build(
            chunk.face, chunk.u0, chunk.v0, chunk.u1, chunk.v1,
            noise, biome, radiusKm, chunk.centerPlanetKm, chunk.depth, null);

        // Streamer with a single worker; collect drained results.
        TerrainStreamer streamer = new TerrainStreamer(noise, biome, radiusKm, 1);
        List<GeneratedChunkData> drained = new ArrayList<>();
        chunk.state = TerrainChunk.State.GENERATING;
        chunk.wantsCollision = false;
        streamer.submit(chunk);

        // Spin-drain until the job lands (bounded).
        for (int i = 0; i < 200 && drained.isEmpty(); i++) {
            streamer.drain(8, drained::add);
            Thread.sleep(5);
        }
        assertEquals(1, drained.size(), "expected one drained result");
        GeneratedChunkData g = drained.get(0);
        assertArrayEquals(ref.vertices, g.vertices, 0f);
        assertArrayEquals(ref.indices, g.indices);
        assertNull(g.collisionShape, "render-only chunk must not build a shape");
        streamer.dispose();
    }

    @Test
    void collisionChunkBuildsShapeOffThread() throws Exception {
        TerrainStreamer streamer = new TerrainStreamer(noise(), biome(), 6371.0, 1);
        TerrainChunk chunk = new TerrainChunk(CubeFace.POS_Z, 8, 0.1f, 0.1f, 0.11f, 0.11f, 6371.0);
        chunk.state = TerrainChunk.State.GENERATING;
        chunk.wantsCollision = true;
        streamer.submit(chunk);

        List<GeneratedChunkData> drained = new ArrayList<>();
        for (int i = 0; i < 200 && drained.isEmpty(); i++) {
            streamer.drain(8, drained::add);
            Thread.sleep(5);
        }
        assertEquals(1, drained.size());
        assertNotNull(drained.get(0).collisionShape, "collision chunk must carry a prebuilt shape");
        assertNotNull(drained.get(0).triangleMesh);
        assertEquals(chunk.generationToken(), drained.get(0).token);
        // Clean up native shape we won't be adding to a world here.
        drained.get(0).disposeNative();
        streamer.dispose();
    }

    @Test
    void disposeShutsDownPool() {
        TerrainStreamer streamer = new TerrainStreamer(noise(), biome(), 6371.0, 2);
        streamer.dispose();
        assertTrue(streamer.isShutdown());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainStreamerTest"`
Expected: FAIL — classes don't compile/exist.

- [ ] **Step 3: Create `GeneratedChunkData`**

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.physics.bullet.collision.btBvhTriangleMeshShape;
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;

/**
 * Immutable result of an off-thread chunk build, handed back to the main thread for GL upload
 * and rigid-body registration. CPU-only fields; no GL/Bullet-world references.
 */
public final class GeneratedChunkData {
    public final TerrainChunk chunk;
    public final int token;            // generation token captured at submit time
    public final float[] vertices;
    public final short[] indices;
    public final btBvhTriangleMeshShape collisionShape; // null for render-only chunks
    public final btTriangleMesh triangleMesh;           // null for render-only chunks

    public GeneratedChunkData(TerrainChunk chunk, int token, float[] vertices, short[] indices,
                              btBvhTriangleMeshShape collisionShape, btTriangleMesh triangleMesh) {
        this.chunk = chunk;
        this.token = token;
        this.vertices = vertices;
        this.indices = indices;
        this.collisionShape = collisionShape;
        this.triangleMesh = triangleMesh;
    }

    /** Dispose the prebuilt native collision objects (used when a result is stale or rejected). */
    public void disposeNative() {
        if (collisionShape != null) collisionShape.dispose();
        if (triangleMesh != null) triangleMesh.dispose();
    }
}
```

- [ ] **Step 4: Create `TerrainStreamer`**

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBvhTriangleMeshShape;
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.planet.BiomeMap;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * Builds terrain chunk geometry (and, when requested, collision shapes) on a background thread
 * pool, returning completed {@link GeneratedChunkData} for the main thread to drain. All work
 * here is pure CPU: no GL calls, no dynamics-world mutation. The main thread owns GL upload and
 * {@code dynamicsWorld.addRigidBody} (see {@link TerrainQuadtree}).
 */
public final class TerrainStreamer implements Disposable {
    private final TerrainNoiseStack noise;
    private final BiomeMap biomeMap;
    private final double radiusKm;
    private final ExecutorService pool;
    private final ConcurrentLinkedQueue<GeneratedChunkData> results = new ConcurrentLinkedQueue<>();
    private volatile boolean shutdown;

    public TerrainStreamer(TerrainNoiseStack noise, BiomeMap biomeMap, double radiusKm, int threads) {
        this.noise = noise;
        this.biomeMap = biomeMap;
        this.radiusKm = radiusKm;
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "terrain-streamer");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
        this.pool = Executors.newFixedThreadPool(Math.max(1, threads), tf);
    }

    /** Default worker count: leave headroom for the render/sim threads. */
    public static int defaultThreadCount() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    }

    /** Submit a chunk for off-thread geometry build. Caller has set state=GENERATING + wantsCollision. */
    public void submit(TerrainChunk chunk) {
        if (shutdown) return;
        final int token = chunk.generationToken();
        final boolean wantsCollision = chunk.wantsCollision;
        pool.execute(() -> {
            if (shutdown || Thread.currentThread().isInterrupted()) return;
            try {
                TerrainMeshBuilder.MeshData data = TerrainMeshBuilder.build(
                    chunk.face, chunk.u0, chunk.v0, chunk.u1, chunk.v1,
                    noise, biomeMap, radiusKm, chunk.centerPlanetKm, chunk.depth, null);

                btBvhTriangleMeshShape shape = null;
                btTriangleMesh triMesh = null;
                if (wantsCollision) {
                    triMesh = buildTriangleMesh(data);
                    shape = new btBvhTriangleMeshShape(triMesh, true); // BVH build is heavy CPU — off-thread OK
                }
                results.add(new GeneratedChunkData(chunk, token, data.vertices, data.indices, shape, triMesh));
            } catch (Throwable t) {
                // Never let a worker exception kill the pool; mark for retry by emitting an empty result.
                results.add(new GeneratedChunkData(chunk, token, null, null, null, null));
            }
        });
    }

    /** Build a chunk-local btTriangleMesh from MeshData (mirrors TerrainQuadtree.buildCollision, CPU-only). */
    private btTriangleMesh buildTriangleMesh(TerrainMeshBuilder.MeshData data) {
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
        return triMesh;
    }

    /** Drain up to {@code max} completed results on the calling (main) thread. */
    public void drain(int max, Consumer<GeneratedChunkData> consumer) {
        for (int i = 0; i < max; i++) {
            GeneratedChunkData g = results.poll();
            if (g == null) return;
            consumer.accept(g);
        }
    }

    public boolean isShutdown() { return shutdown; }

    @Override
    public void dispose() {
        shutdown = true;
        pool.shutdownNow();
        // Dispose any native shapes left in the queue so we don't leak.
        GeneratedChunkData g;
        while ((g = results.poll()) != null) g.disposeNative();
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainStreamerTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/GeneratedChunkData.java core/src/main/java/com/galacticodyssey/planet/terrain/TerrainStreamer.java core/src/test/java/com/galacticodyssey/planet/terrain/TerrainStreamerTest.java
git commit -m "feat(terrain): async TerrainStreamer worker pool for chunk generation"
```

---

## Task 5: Wire `TerrainQuadtree` to the streamer (enqueue, drain, stale-discard, collision gate)

Replace the inline `generateMesh` path with: enqueue on demand, drain results on the main thread (GL upload + `addRigidBody`), discard stale-token results, and gate collision to a bounded shell.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainQuadtree.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainQuadtreeStreamingTest.java` *(new)*

- [ ] **Step 1: Write the failing test**

Create `TerrainQuadtreeStreamingTest`:

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.physics.bullet.Bullet;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class TerrainQuadtreeStreamingTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    private TerrainQuadtree tree() {
        TerrainNoiseStack noise = new TerrainNoiseStack(5L);
        BiomeMap biome = new BiomeMap(0L, 0f, 6000f, 0.5f, 293f, EnumSet.allOf(BiomeType.class));
        // dynamicsWorld null: headless. Collision bodies are skipped but shapes/MeshData still flow.
        return new TerrainQuadtree(6371.0, PlanetCoordsKM.ORIGIN, noise, biome, null);
    }

    @Test
    void residentLeafCountStaysBounded() {
        TerrainQuadtree tree = tree();
        // Camera skimming the +Y pole surface.
        PlanetCoordsKM cam = new PlanetCoordsKM(0, 6371.0 + 0.01, 0);
        for (int frame = 0; frame < 400; frame++) {
            tree.update(cam);
            tree.pump(); // drain async results (test helper: drains until queue empty)
        }
        int leaves = tree.getVisibleLeaves().size();
        assertTrue(leaves > 0 && leaves < 4000,
            "resident leaves should be bounded, was " + leaves);
    }

    @Test
    void onlyNearDeepLeavesRequestCollision() {
        TerrainQuadtree tree = tree();
        PlanetCoordsKM cam = new PlanetCoordsKM(0, 6371.0 + 0.01, 0);
        for (int frame = 0; frame < 400; frame++) { tree.update(cam); tree.pump(); }
        long collisionWanted = tree.getVisibleLeaves().stream().filter(c -> c.wantsCollision).count();
        // Bounded: only the shell of deep leaves around the camera.
        assertTrue(collisionWanted > 0 && collisionWanted < 256,
            "collision-wanting leaves should be a bounded shell, was " + collisionWanted);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainQuadtreeStreamingTest"`
Expected: FAIL — `pump()`, collision gating, and streamer wiring don't exist yet.

- [ ] **Step 3: Rewrite the quadtree generation path**

In `TerrainQuadtree`, add fields after `dynamicsWorld` (line 26):

```java
    private final TerrainStreamer streamer;
    /** Collision shell: leaves at depth >= this within COLLISION_RADIUS_M of the camera get bodies. */
    private static final int COLLISION_MIN_DEPTH = TerrainChunk.MAX_DEPTH - 2;
    private static final double COLLISION_RADIUS_KM = 2.0; // wide / vehicle-friendly, inside float-safe band
    private static final int MAX_UPLOADS_PER_FRAME = 8;
    private PlanetCoordsKM lastCameraKm = PlanetCoordsKM.ORIGIN;
```

In the constructor (after line 38) create the streamer:

```java
        this.streamer = new TerrainStreamer(noise, biomeMap, radiusKm, TerrainStreamer.defaultThreadCount());
```

Replace `update` (lines 41-43) so it records the camera and drains:

```java
    public void update(PlanetCoordsKM cameraKm) {
        this.lastCameraKm = cameraKm;
        for (TerrainChunk root : roots) recursiveUpdate(root, cameraKm);
        streamer.drain(MAX_UPLOADS_PER_FRAME, this::applyResult);
    }

    /** Test/main helper: drain ALL pending results now (used in headless tests and spawn pre-warm). */
    public void pump() {
        streamer.drain(Integer.MAX_VALUE, this::applyResult);
    }
```

Replace `recursiveUpdate` (lines 45-59) so it enqueues instead of building inline, and sets the collision flag:

```java
    private void recursiveUpdate(TerrainChunk chunk, PlanetCoordsKM cameraKm) {
        if (chunk.shouldSplit(cameraKm) && !chunk.hasChildren()) {
            split(chunk);
        } else if (chunk.hasChildren() && chunk.shouldMerge(cameraKm)) {
            merge(chunk);
        }

        if (chunk.hasChildren()) {
            for (TerrainChunk child : chunk.children) recursiveUpdate(child, cameraKm);
            return; // interior node: no geometry of its own
        }

        // Leaf: decide collision membership, then ensure geometry exists.
        boolean nearShell = chunk.depth >= COLLISION_MIN_DEPTH
            && chunk.centerPlanetKm.dst(cameraKm) <= COLLISION_RADIUS_KM;
        chunk.wantsCollision = nearShell;

        if (chunk.state == TerrainChunk.State.EMPTY) {
            chunk.state = TerrainChunk.State.GENERATING;
            streamer.submit(chunk);
        }
    }
```

Add the drain handler (place after `generateMesh`, reusing `buildCollision` is no longer needed for async — collision arrives prebuilt; keep `buildCollision` only if other callers use it, otherwise delete it):

```java
    /** Main-thread: turn a completed async result into a live GL mesh + (optional) rigid body. */
    private void applyResult(GeneratedChunkData g) {
        TerrainChunk chunk = g.chunk;
        // Stale: chunk split/merged (or was invalidated) since submit — drop and free native objects.
        if (g.token != chunk.generationToken() || chunk.hasChildren()) {
            g.disposeNative();
            return;
        }
        if (g.vertices == null) { // worker failed — allow a retry next pass
            chunk.state = TerrainChunk.State.EMPTY;
            return;
        }
        // GL upload (skipped/handled headless).
        try {
            chunk.mesh = new Mesh(true,
                TerrainMeshBuilder.GRID_SIZE * TerrainMeshBuilder.GRID_SIZE,
                g.indices.length,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"));
            chunk.mesh.setVertices(g.vertices);
            chunk.mesh.setIndices(g.indices);
        } catch (Exception e) {
            chunk.mesh = null; // headless / no GL
        }
        // Collision: register the prebuilt shape on the main thread (Bullet world mutation only here).
        if (g.collisionShape != null) {
            try {
                btRigidBody.btRigidBodyConstructionInfo info =
                    new btRigidBody.btRigidBodyConstructionInfo(0f, null, g.collisionShape);
                btRigidBody body;
                try { body = new btRigidBody(info); } finally { info.dispose(); }
                body.setFriction(0.9f);
                tmpMat.idt().setTranslation(
                    /* placement set below via applyPlacement */ 0f, 0f, 0f);
                if (dynamicsWorld != null) dynamicsWorld.addRigidBody(body);
                chunk.triangleMesh = g.triangleMesh;
                chunk.collisionShape = g.collisionShape;
                chunk.collisionBody = body;
            } catch (Exception e) {
                g.disposeNative();
            }
        }
        applyPlacement(chunk);
        chunk.state = TerrainChunk.State.READY;
    }
```

Update `split` and `merge` to invalidate tokens so in-flight results are rejected. In `split` (lines 61-75), at the top add:

```java
        chunk.invalidate();
```

In `merge` (lines 77-86), for each child add `child.invalidate();` before disposing, and after clearing children set `chunk.state = TerrainChunk.State.EMPTY;` (already does `meshReady=false` — replace that line with the state reset).

Update `dispose()` (line 205) to also dispose the streamer:

```java
    @Override
    public void dispose() {
        streamer.dispose();
        dispose(dynamicsWorld);
    }
```

> Implementer note: `applyPlacement` already sets the body's world transform from `chunk.placementLocal`; that's why `applyResult` sets a zero translation first and relies on `applyPlacement(chunk)` to fix it. Confirm `applyPlacement` runs after `collisionBody` is assigned (it does — called at the end). Remove the now-unused `meshReady` field references if the compiler flags them, or keep `meshReady` as an alias for `state == READY` if other code reads it (grep first: `grep -rn meshReady core/src`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainQuadtreeStreamingTest"`
Then the existing rebase/collision/LOD suites:
Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.*"`
Expected: PASS (new + existing). Fix any `meshReady` references surfaced by the rebase test.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/TerrainQuadtree.java core/src/test/java/com/galacticodyssey/planet/terrain/TerrainQuadtreeStreamingTest.java
git commit -m "feat(terrain): stream chunks via TerrainStreamer with collision gating + stale discard"
```

---

## Task 6: Drain in `PlanetTerrainSystem.update` + spawn pre-warm + `GameWorld.spawnPlayerOnSphere`

`PlanetTerrainSystem.update` already calls `quadtree.update(...)` (which now drains). Add a synchronous pre-warm so collision exists under the spawn point at frame 1, and a `GameWorld` helper to place the player.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java`
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/PlanetSpawnPrewarmTest.java` *(new)*

- [ ] **Step 1: Write the failing test**

Create `PlanetSpawnPrewarmTest`:

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.Planet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class PlanetSpawnPrewarmTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    @Test
    void prewarmCreatesLeafGeometryUnderSpawnDirection() {
        Planet planet = PlanetTestSupport.earthLike(); // reuse the helper other terrain tests use
        BiomeMap biome = new BiomeMap(0L, 0f, 6000f, 0.5f, 293f, EnumSet.allOf(BiomeType.class));
        PlanetTerrainSystem sys = new PlanetTerrainSystem(null);
        // Player at +Y pole, origin at the surface there (matches GameWorld.loadPlanetTerrain).
        double radiusKm = planet.radius * PlanetTerrainSystem.EARTH_RADIUS_KM;
        sys.loadPlanet(planet, biome, new PlanetCoordsKM(0, radiusKm, 0));

        Vector3 spawnDir = new Vector3(0, 1, 0).nor();
        sys.prewarmAround(spawnDir);

        // After pre-warm, a READY leaf should exist whose centre direction is near the spawn dir.
        boolean hasDeepLeaf = sys.getVisibleLeaves().stream().anyMatch(c ->
            !c.hasChildren() && c.depth >= TerrainChunk.MAX_DEPTH - 2);
        assertTrue(hasDeepLeaf, "pre-warm should subdivide to near-max depth under the spawn point");
    }
}
```

> Implementer note: if no shared `earthLike()` helper exists, create `PlanetTestSupport.earthLike()` in the test package building a minimal `Planet` (radius=1.0, a `PlanetType` with `hasSurface()==true`, seed set) the same way `PlanetTerrainSystemScaleTest` constructs its planet.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.PlanetSpawnPrewarmTest"`
Expected: FAIL — `prewarmAround` doesn't exist.

- [ ] **Step 3: Implement `prewarmAround`**

In `PlanetTerrainSystem`, add:

```java
    /**
     * Synchronously subdivide and build the chunk chain under a spawn direction down to near-max
     * depth, draining the streamer to completion, so a collision body exists before the player
     * is placed (prevents fall-through while async streaming catches up).
     */
    public void prewarmAround(com.badlogic.gdx.math.Vector3 unitDir) {
        if (quadtree == null) return;
        PlanetCoordsKM camKm = new PlanetCoordsKM(
            unitDir.x * radiusKm, unitDir.y * radiusKm, unitDir.z * radiusKm);
        // Repeatedly update (split toward camKm) + pump (drain async builds) until depth stabilises.
        for (int pass = 0; pass < TerrainChunk.MAX_DEPTH + 4; pass++) {
            quadtree.update(camKm);
            quadtree.pump();
        }
    }
```

- [ ] **Step 4: Add `GameWorld.spawnPlayerOnSphere`**

In `GameWorld`, add (near `loadPlanetTerrain`, after line 1184):

```java
    /**
     * Spawn the player capsule on the sphere surface along {@code unitDir} from the planet centre.
     * Pre-warms terrain under the spawn so collision exists at frame 1. Returns the local spawn point.
     */
    public Vector3 spawnPlayerOnSphere(com.badlogic.gdx.math.Vector3 unitDir, float eyeOffsetM) {
        planetTerrainSystem.prewarmAround(unitDir);
        com.galacticodyssey.core.coords.LocalCoordsM surface = planetTerrainSystem.surfacePointLocal(unitDir);
        // "Up" is radial; offset the spawn outward along it.
        Vector3 up = com.galacticodyssey.core.coords.CoordConvert.surfaceUpLocal(
            new com.galacticodyssey.core.coords.PlanetCoordsKM(unitDir.x, unitDir.y, unitDir.z));
        Vector3 pos = surface.toVector3().mulAdd(up, eyeOffsetM);
        createPlayerEntity(pos.x, pos.y, pos.z);
        return pos;
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.PlanetSpawnPrewarmTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java core/src/main/java/com/galacticodyssey/core/GameWorld.java core/src/test/java/com/galacticodyssey/planet/terrain/PlanetSpawnPrewarmTest.java
git commit -m "feat(terrain): spawn pre-warm + spawnPlayerOnSphere helper"
```

---

## Task 7: `GameScreen` — render sphere from the ground, retire the flat pad render

This and Task 8 are GL/scene wiring that the headless suite can't cover; verify with the run skill at the end. Make focused edits, keep the high-altitude backdrop fade.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Remove the lower altitude gate in `renderTerrain`**

In `renderTerrain` (around line 1713), the sphere currently activates only above `FLAT_TERRAIN_FADE_END_Y`. Replace the activation so the sphere renders from the ground up while keeping the orbital fade-out. Change:

```java
        boolean useSphereTerrain = false;
        float terrainFade = 1f;
        if (sphereRadius > 0f) {
            float altitudeAboveSurface = camera.position.y - 2f;
            useSphereTerrain = altitudeAboveSurface > FLAT_TERRAIN_FADE_END_Y;
            if (useSphereTerrain) {
                ...
                float fadeIn = Math.min(1f, (altitudeAboveSurface - FLAT_TERRAIN_FADE_END_Y) / 1000f);
                ...
                terrainFade = Math.min(fadeIn, fadeOut);
            }
        }
```

to:

```java
        boolean useSphereTerrain = sphereRadius > 0f; // SP2: sphere is the ground now
        float terrainFade = 1f;
        if (useSphereTerrain) {
            float requiredFar = sphereRadius * 3f;
            if (camera.far < requiredFar) { camera.far = requiredFar; camera.update(); }
            pts.setCameraPositionLocal(camera.position);
            // Keep ONLY the orbital fade-OUT (SP3 owns the far-hemisphere/orbit transition).
            float distFromCenter = camera.position.dst(pts.getPlanetCenterLocal());
            float altAboveSphere = distFromCenter - sphereRadius;
            float fadeOutFrac = (altAboveSphere - sphereRadius * TERRAIN_FADE_START_FRAC)
                              / (sphereRadius * (TERRAIN_FADE_END_FRAC - TERRAIN_FADE_START_FRAC));
            terrainFade = 1f - Math.min(1f, Math.max(0f, fadeOutFrac));
        }
```

- [ ] **Step 2: Delete the flat-pad render branch**

In `renderTerrain`, delete the entire `else { ... }` block that renders `terrainMesh` (the flat-pad path, around lines 1780-1822 — `gbuffer_terrain` shader, terrain material texture binds, `terrainMesh.render(...)`, and the `camera.far > 5000` clamp). The sphere `if (useSphereTerrain)` block remains and is now always taken.

- [ ] **Step 3: Build the project**

Run: `./gradlew :core:compileJava`
Expected: compile errors for now-unused fields (`terrainMesh`, `heightmap`, splat textures, `FLAT_TERRAIN_FADE_*`) only if referenced elsewhere — Task 8 removes their producers. If the only errors are "unused", proceed; otherwise note them for Task 8.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(terrain): render sphere terrain from the ground, drop flat-pad render branch"
```

---

## Task 8: `GameScreen` — spawn on sphere + disable flat-pad-pinned dressing

Re-anchor player + starter ship spawn to the sphere; disable decorative dressing that pins to the dead `heightmap`, behind clear SP2-follow-up markers so the planet boots bare-but-walkable.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Re-anchor player spawn**

Replace the player-spawn block (lines 324-331) with:

```java
        // SP2: spawn on the sphere surface at the +Y pole (matches loadPlanetTerrain origin).
        Vector3 spawnDir = new Vector3(0, 1, 0).nor();
        if (session != null && session.playerSpawnPos != null) {
            gameWorld.createPlayerEntity(
                session.playerSpawnPos.x, session.playerSpawnPos.y, session.playerSpawnPos.z);
        } else {
            gameWorld.spawnPlayerOnSphere(spawnDir, 2f);
        }
```

- [ ] **Step 2: Re-anchor the starter ship; disable demo ships**

Replace the ship-spawn block (lines 342-368) with:

```java
        // SP2: starter ship sits just off the spawn point on the sphere surface.
        com.galacticodyssey.core.coords.LocalCoordsM shipSurface =
            gameWorld.getPlanetTerrainSystem().surfacePointLocal(spawnDir);
        Vector3 shipPos = shipSurface.toVector3();
        Entity starterShip = shipFactory.createShip(
            (session != null) ? session.seed : 42L, ShipSizeClass.SMALL,
            shipPos.x + 20f, shipPos.y + 2f, shipPos.z);
        shipEntities.add(starterShip);
        // TODO(SP2-followup): re-anchor the medium/large demo ships + test NPC to surfaceHeightAt.
```

- [ ] **Step 3: Disable heightmap-pinned dressing**

The dressing currently built at init pins to `heightmap` (flat pad). Guard it out for SP2 so nothing floats. Wrap the grass/alien-plant/ocean/scatter/WorldPopulator/test-NPC creation (lines ~283-337 region: `heightmap = ...`, `populatedWorld = ...`, `grassField/grassRenderer`, `AlienPlantGenerator.populate`, `getOceanSpawner().spawnSeawater`, `createTerrainMesh`, `createTerrainPhysics`, `createScatterBoxes`, `spawnTestNpc`) with an early `SP2_FLAT_DRESSING` switch:

```java
        // SP2: the flat 5km pad is retired. Grass, alien plants, water, scatter, and test NPCs
        // were pinned to its heightmap; re-anchoring them to the sphere surface sampler is the
        // next sub-project (SP2-followup A/B/C). Disabled here so nothing floats over the sphere.
        final boolean SP2_FLAT_DRESSING = false;
        if (SP2_FLAT_DRESSING) {
            // ...original heightmap/grass/alien/ocean/scatter/terrainMesh/physics/test-NPC block...
        }
```

Move the `gameWorld.loadPlanetTerrain(...)` call and `particleRenderSystem.initialize(...)` OUT of the guard (they are not flat-pad dressing and must still run). Keep `gameWorld.initializeSystems(camera)` before `loadPlanetTerrain`.

> Implementer note: fields only read inside the guarded block (`heightmap`, `terrainMesh`, splat textures, `populatedWorld`, `grassField`, `grassRenderer`, `alienBatch`, `FLAT_TERRAIN_FADE_*`, `TERRAIN_WIDTH/DEPTH/VERTS`) can stay declared (cheap) to minimise churn; the render-side reads were removed in Task 7. Where the render loop still references `grassRenderer`/`alienBatch`/ocean for drawing, guard those draw calls with `if (SP2_FLAT_DRESSING)` too. Grep to confirm: `grep -n "grassRenderer\|alienBatch\|terrainMesh\|heightmap" core/src/main/java/com/galacticodyssey/ui/GameScreen.java` and guard each draw/update site.

- [ ] **Step 4: Build**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Full headless suite**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL — all tests green.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(terrain): spawn player/ship on sphere; disable flat-pad dressing (SP2)"
```

---

## Task 9: Enable floating-origin rebase while on a planet

SP1 left rebase **dormant during planet walking**: `GameWorld.update` only calls `coordinateManager.checkRebase(...)` when no planet is loaded (`getRadiusKm() <= 0`, line ~1117). Without this, the player's local position grows without bound as they walk the globe → float-precision failure. The `OriginRebasedEvent` plumbing already routes to `planetTerrainSystem.onOriginRebased` + gravity + player movement (GameWorld lines 371-375), so enabling the call is the fix; the risk is regressions, so it gets an integration test.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`
- Test: `core/src/test/java/com/galacticodyssey/core/PlanetRebaseIntegrationTest.java` *(new)*

- [ ] **Step 1: Write the failing test**

Create `PlanetRebaseIntegrationTest` — wires `CoordinateManager` + `PlanetTerrainSystem` the way `GameWorld` does and asserts that crossing the rebase threshold on a loaded planet shifts the planet-space origin while preserving the player's height above the surface:

```java
package com.galacticodyssey.core;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.core.events.EventBus;
import com.galacticodyssey.core.events.OriginRebasedEvent;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.terrain.PlanetTerrainSystem;
import com.galacticodyssey.planet.terrain.PlanetTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlanetRebaseIntegrationTest {
    @BeforeAll static void initBullet() { Bullet.init(); }

    @Test
    void walkingPastThresholdRebasesPlanetOrigin() {
        EventBus bus = new EventBus();
        CoordinateManager coords = new CoordinateManager(bus);
        PlanetTerrainSystem terrain = new PlanetTerrainSystem(null);
        // Wire the same routing GameWorld does.
        bus.subscribe(OriginRebasedEvent.class, e -> terrain.onOriginRebased(e.deltaX, e.deltaY, e.deltaZ));
        AtomicInteger rebases = new AtomicInteger();
        bus.subscribe(OriginRebasedEvent.class, e -> rebases.incrementAndGet());

        double radiusKm = PlanetTestSupport.earthLike().radius * PlanetTerrainSystem.EARTH_RADIUS_KM;
        BiomeMap biome = new BiomeMap(0L, 0f, 6000f, 0.5f, 293f, EnumSet.allOf(BiomeType.class));
        terrain.loadPlanet(PlanetTestSupport.earthLike(), biome, new PlanetCoordsKM(0, radiusKm, 0));

        PlanetCoordsKM originBefore = terrain.getOriginPlanetKm();
        // Player walks 2 km along local +X — well past the 1000 m rebase threshold.
        coords.checkRebase(new Vector3(2000f, 0f, 0f));

        assertTrue(rebases.get() >= 1, "crossing the threshold should fire a rebase on a planet");
        PlanetCoordsKM originAfter = terrain.getOriginPlanetKm();
        assertNotEquals(originBefore.x(), originAfter.x(), 1e-9,
            "planet-space origin should shift with the rebase");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.PlanetRebaseIntegrationTest"`
Expected: PASS already at the `CoordinateManager` level (this test exercises the wiring directly, not the gated `GameWorld.update`). If it passes, it documents the contract; the gate fix below is what makes it true *in-game*. If `CoordinateManager.checkRebase` itself has a planet gate, the test FAILS — fix it in Step 3.

- [ ] **Step 3: Remove the planet gate in `GameWorld.update`**

Replace the gated block (lines 1117-1122):

```java
        if (planetTerrainSystem == null || planetTerrainSystem.getRadiusKm() <= 0) {
            Entity player = engine.getEntitiesFor(
                com.badlogic.ashley.core.Family.all(PlayerTagComponent.class, TransformComponent.class).get()).first();
            TransformComponent t = player.getComponent(TransformComponent.class);
            coordinateManager.checkRebase(t.position);
        }
```

with (rebase always active, planet or not — SP2 needs it while walking the sphere):

```java
        // SP2: floating-origin rebase must run while walking on a planet too, or local
        // coordinates grow unbounded as the player crosses the globe.
        com.badlogic.ashley.utils.ImmutableArray<Entity> players = engine.getEntitiesFor(
            com.badlogic.ashley.core.Family.all(PlayerTagComponent.class, TransformComponent.class).get());
        if (players.size() > 0) {
            TransformComponent t = players.first().getComponent(TransformComponent.class);
            coordinateManager.checkRebase(t.position);
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.PlanetRebaseIntegrationTest"`
Then the SP1 rebase suite (must stay green):
Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainQuadtreeRebaseTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java core/src/test/java/com/galacticodyssey/core/PlanetRebaseIntegrationTest.java
git commit -m "fix(terrain): activate floating-origin rebase while walking on a planet"
```

---

## Task 10: Far-chunk render cull (float-precision guard)

With the lower altitude gate removed (Task 7), the sphere's coarse far-side/below-horizon chunks are placed in float metres at magnitudes up to ±planet-diameter (~1e10 m) — far outside the float-safe band, causing jitter/z-fighting if drawn. They're beyond the horizon and invisible anyway. Cull them from rendering by placement magnitude. (Collision is already bounded by the Task 5 gate; this is the render-side guard.)

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainQuadtree.java`
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainQuadtreeStreamingTest.java` *(extend)*

- [ ] **Step 1: Write the failing test**

Add to `TerrainQuadtreeStreamingTest`:

```java
@Test
void renderableLeavesExcludeFarSideChunks() {
    TerrainQuadtree tree = tree();
    PlanetCoordsKM cam = new PlanetCoordsKM(0, 6371.0 + 0.01, 0); // +Y pole
    for (int frame = 0; frame < 400; frame++) { tree.update(cam); tree.pump(); }

    var all = tree.getVisibleLeaves();
    var renderable = tree.getRenderableLeaves();
    assertTrue(renderable.size() <= all.size());
    assertTrue(renderable.size() > 0, "near-field chunks must remain renderable");
    // Every renderable chunk is within the float-safe render band.
    for (var c : renderable) {
        float mag = c.placementLocal.toVector3().len();
        assertTrue(mag <= TerrainQuadtree.RENDER_BAND_M,
            "renderable chunk beyond band: " + mag);
    }
    // The far-side root (−Y) is in the full set but not renderable (placement ~ planet diameter).
    boolean someFarExcluded = all.size() > renderable.size();
    assertTrue(someFarExcluded, "far-side chunks should be culled from rendering");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainQuadtreeStreamingTest"`
Expected: FAIL — `getRenderableLeaves()` / `RENDER_BAND_M` don't exist.

- [ ] **Step 3: Add the render band + filter to `TerrainQuadtree`**

Add the constant near the collision constants (Task 5):

```java
    /** Chunks whose local placement exceeds this are beyond the horizon / float-safe band and are
     *  not rendered (SP2). The far hemisphere is the orbital backdrop's job until SP3. */
    public static final float RENDER_BAND_M = 200_000f; // 200 km local
```

Add the filtered accessor next to `getVisibleLeaves` (line 179):

```java
    private final List<TerrainChunk> renderCache = new ArrayList<>();

    public List<TerrainChunk> getRenderableLeaves() {
        renderCache.clear();
        for (TerrainChunk leaf : getVisibleLeaves()) {
            if (leaf.placementLocal.toVector3().len() <= RENDER_BAND_M) renderCache.add(leaf);
        }
        return renderCache;
    }
```

> Implementer note: `getVisibleLeaves` reuses `leafCache`; copy into `renderCache` (separate list) so the two accessors don't alias. The `toVector3()` allocation per leaf is acceptable at the bounded renderable count; if profiling flags it, add a `placementMagnitudeM()` to `TerrainChunk` computed at placement time.

- [ ] **Step 4: Use it in `GameScreen`**

In `renderTerrain` (the sphere draw loop added/kept in Task 7), change the iteration from `pts.getVisibleLeaves()` to `pts.getRenderableLeaves()`. Add the pass-through on `PlanetTerrainSystem`:

```java
    public java.util.List<TerrainChunk> getRenderableLeaves() {
        if (quadtree == null) return java.util.Collections.emptyList();
        return quadtree.getRenderableLeaves();
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainQuadtreeStreamingTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/TerrainQuadtree.java core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java core/src/main/java/com/galacticodyssey/ui/GameScreen.java core/src/test/java/com/galacticodyssey/planet/terrain/TerrainQuadtreeStreamingTest.java
git commit -m "feat(terrain): cull far-side chunks beyond float-safe render band"
```

---

## Task 11: Manual verification (run the game)

**Files:** none (verification only).

- [ ] **Step 1: Launch and observe**

Use the `run-galactic-odyssey` skill to build, launch, and screenshot a new game on a planet surface.

- [ ] **Step 2: Verify the acceptance criteria**

- Player stands on terrain (does not fall through) at spawn.
- Terrain extends to the horizon in every direction — no 5 km flat-pad edge / void.
- Walking in any direction streams new terrain without a hard edge; no multi-second freeze (async streaming).
- Walking a long distance (past several rebase thresholds) shows no position jitter or terrain shimmer — floating-origin rebase stays active on the planet (Task 9).
- Looking down shows curved/lumpy ground with meter-to-tens-of-meters relief, not a flat plane.
- Flying straight up still transitions smoothly toward the orbital backdrop (the high-altitude fade is intact).

- [ ] **Step 3: Capture a before/after screenshot** and report observations. If terrain feel needs tuning, adjust the Task 1 octave amplitudes and/or `MAX_DEPTH` (Task 2) and re-run; record the chosen values.

---

## Self-Review

**Spec coverage:**
- Deeper LOD → Task 2. High-freq detail → Task 1. Async streamer → Task 4. Quadtree wiring + collision gating + stale discard → Task 5. Drain in `update` + spawn pre-warm → Task 6. `surfaceHeightAt` seam → Task 3. Retire flat pad / remove lower gate / keep orbital fade → Task 7. Spawn on sphere + dressing disabled → Task 8. **SP1 carry-overs:** activate floating-origin rebase while walking a planet → Task 9; far-chunk float-precision render cull → Task 10. Acceptance → Task 11. All spec §3 items + SP1 carry-overs mapped.
- Out-of-scope items (grass/veg/water/NPC re-anchor, splatting, SP3 frame unification) are explicitly deferred via the `SP2_FLAT_DRESSING` guard + TODO markers — not silently dropped.

**Type consistency:** `surfaceRadiusKmAt(Vector3)`, `surfacePointLocal(Vector3)`, `prewarmAround(Vector3)`, `spawnPlayerOnSphere(Vector3,float)`, `TerrainStreamer.submit(TerrainChunk)/drain(int,Consumer)/dispose()`, `GeneratedChunkData(chunk,token,vertices,indices,shape,triMesh)/disposeNative()`, `TerrainChunk.State`, `generationToken()/invalidate()`, `wantsCollision`, `TerrainQuadtree.pump()` — used consistently across tasks.

**Threading invariants (primary risk):** GL `Mesh` upload + `dynamicsWorld.addRigidBody` happen only in `applyResult`/`drain`, called from the main thread (`update`/`pump`). `btTriangleMesh`/`btBvhTriangleMeshShape` are built on the worker (no world mutation) — verified safe. Stale results free native objects via `disposeNative()`.

**Open tuning items (expected, not blockers):** octave amplitudes (Task 1), `MAX_DEPTH` 14 vs 15 (Task 2), `COLLISION_RADIUS_KM` / `MAX_UPLOADS_PER_FRAME` (Task 5) — all tunable in Task 9.
