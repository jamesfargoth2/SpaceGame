# Whole-Planet SP1 — Floating-Origin Terrain Spine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the spherical terrain quadtree, its Bullet collision, the LOD/camera math, and radial gravity correct at true planetary scale (bodies up to ~12,700 km diameter) without float-precision failure, by working in PLANET_SPACE kilometres (double) and converting to LOCAL_SCENE metres (float) only at the render/physics boundary.

**Architecture:** Introduce the `procgen-coordinate-systems` typed coordinate records (`PlanetCoordsKM`, `LocalCoordsM`, `CoordConvert`). Terrain chunk geometry, centres, and LOD distances move to double km. Chunk meshes are generated **chunk-local in metres** (subtract chunk centre in double, ×1000, cast) and *placed* by a per-chunk transform derived from the floating origin; collision uses chunk-local shapes + a body world-transform so rebasing is O(1) per chunk and reuses the existing `OriginRebasedEvent`. The flat set-piece, player spawn, and altitude fade are left intact — SP1 is the precision spine, validated by headless tests.

**Tech Stack:** Java 17, libGDX (`Vector3`, `Matrix4`, `Mesh`), gdx-bullet, Ashley ECS, JUnit 5. Gradle module `core`. Run tests with `./gradlew :core:test --tests "<FQCN>"`.

**Spec:** `docs/superpowers/specs/2026-05-29-whole-planet-sp1-floating-origin-terrain-design.md`

---

## Conventions used throughout

- **PLANET_SPACE** = kilometres, `double`, origin = planet centre. **LOCAL_SCENE** = metres, `float`, origin = floating origin (camera-near).
- `KM_TO_M = 1000.0`, `M_TO_KM = 0.001`.
- **Rule (never violate):** subtract two world positions in `double` *before* casting to `float`. `(float)((a - b) * KM_TO_M)`, never `(float)a - (float)b`.
- `planet.radius` is in Earth radii; `radiusKm = planet.radius * 6371.0`.
- Player spawns at the **+Y pole** (matches the existing `TerrainNoiseStack` north-pole spawn note), so the floating origin's planet-space position at load is `(0, radiusKm, 0)` and the planet centre sits at local `(0, -radiusKm*1000, 0)`.

---

## File structure

| File | Responsibility | New? |
|---|---|---|
| `core/src/main/java/com/galacticodyssey/core/coords/PlanetCoordsKM.java` | Double km position + vector ops | new |
| `core/src/main/java/com/galacticodyssey/core/coords/LocalCoordsM.java` | Float metre position + `toVector3()` | new |
| `core/src/main/java/com/galacticodyssey/core/coords/CoordConvert.java` | `planetToLocal`/`localToPlanet`/`surfaceUpLocal` + unit consts | new |
| `planet/terrain/TerrainMeshBuilder.java` | Emit chunk-local metre vertices | modify |
| `planet/terrain/TerrainChunk.java` | Double km centre, double-camera LOD, placement field | modify |
| `planet/terrain/TerrainQuadtree.java` | Double radius/origin, collision via body transform, rebase | modify |
| `planet/terrain/PlanetTerrainSystem.java` | Real radius, origin tracking, rebase subscription, double getters | modify |
| `core/systems/RadialGravitySystem.java` | Local planet centre + rebase shift | modify |
| `core/GameWorld.java` | Wire real radius/origin/gravity; drop 50 km override | modify |
| `ui/GameScreen.java` | Per-chunk `u_worldTrans`; fade math via planet-space | modify |
| `planet/ScatteringParams.java` | Planet radius in metres (lockstep with terrain) | modify |

Tests live under `core/src/test/java/com/galacticodyssey/...` mirroring the package.

---

## Task 1: `PlanetCoordsKM` record

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/coords/PlanetCoordsKM.java`
- Test: `core/src/test/java/com/galacticodyssey/core/coords/PlanetCoordsKMTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.core.coords;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanetCoordsKMTest {
    @Test
    void subAndLenComputeDistanceInKm() {
        PlanetCoordsKM a = new PlanetCoordsKM(6371.0, 0, 0);
        PlanetCoordsKM b = new PlanetCoordsKM(6370.0, 0, 0);
        assertEquals(1.0, a.sub(b).len(), 1e-9);
    }

    @Test
    void dstMatchesManualDistance() {
        PlanetCoordsKM a = new PlanetCoordsKM(0, 6371.0, 0);
        PlanetCoordsKM b = new PlanetCoordsKM(0, 6371.0, 3.0);
        assertEquals(3.0, a.dst(b), 1e-9);
    }

    @Test
    void norProducesUnitLength() {
        PlanetCoordsKM n = new PlanetCoordsKM(0, 6371.0, 0).nor();
        assertEquals(1.0, n.len(), 1e-12);
        assertEquals(1.0, n.y(), 1e-12);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.coords.PlanetCoordsKMTest"`
Expected: FAIL — `PlanetCoordsKM` does not exist (compile error).

- [ ] **Step 3: Write minimal implementation**

```java
package com.galacticodyssey.core.coords;

/** Planet-space position in kilometres (double), relative to the planet centre. */
public record PlanetCoordsKM(double x, double y, double z) {
    public static final PlanetCoordsKM ORIGIN = new PlanetCoordsKM(0, 0, 0);

    public PlanetCoordsKM add(PlanetCoordsKM o) { return new PlanetCoordsKM(x + o.x, y + o.y, z + o.z); }
    public PlanetCoordsKM sub(PlanetCoordsKM o) { return new PlanetCoordsKM(x - o.x, y - o.y, z - o.z); }
    public PlanetCoordsKM scl(double s)         { return new PlanetCoordsKM(x * s, y * s, z * s); }
    public double len()  { return Math.sqrt(x * x + y * y + z * z); }
    public double dst(PlanetCoordsKM o) { double dx = x - o.x, dy = y - o.y, dz = z - o.z; return Math.sqrt(dx*dx + dy*dy + dz*dz); }
    public PlanetCoordsKM nor() { double l = len(); return l == 0 ? this : new PlanetCoordsKM(x / l, y / l, z / l); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.coords.PlanetCoordsKMTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/coords/PlanetCoordsKM.java core/src/test/java/com/galacticodyssey/core/coords/PlanetCoordsKMTest.java
git commit -m "feat(coords): add PlanetCoordsKM double-precision position record"
```

---

## Task 2: `LocalCoordsM` record

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/coords/LocalCoordsM.java`
- Test: `core/src/test/java/com/galacticodyssey/core/coords/LocalCoordsMTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.core.coords;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalCoordsMTest {
    @Test
    void toVector3PreservesComponents() {
        LocalCoordsM l = new LocalCoordsM(1.5f, -2.0f, 3.25f);
        Vector3 v = l.toVector3();
        assertEquals(1.5f, v.x, 0f);
        assertEquals(-2.0f, v.y, 0f);
        assertEquals(3.25f, v.z, 0f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.coords.LocalCoordsMTest"`
Expected: FAIL — `LocalCoordsM` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.galacticodyssey.core.coords;

import com.badlogic.gdx.math.Vector3;

/** Local-scene position in metres (float), relative to the current floating origin. */
public record LocalCoordsM(float x, float y, float z) {
    public Vector3 toVector3() { return new Vector3(x, y, z); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.coords.LocalCoordsMTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/coords/LocalCoordsM.java core/src/test/java/com/galacticodyssey/core/coords/LocalCoordsMTest.java
git commit -m "feat(coords): add LocalCoordsM float position record"
```

---

## Task 3: `CoordConvert` — the floating-origin boundary

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/coords/CoordConvert.java`
- Test: `core/src/test/java/com/galacticodyssey/core/coords/CoordConvertTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.core.coords;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoordConvertTest {
    @Test
    void planetToLocalSubtractsInDoubleThenScalesToMetres() {
        // Origin at the +Y pole of a 6371 km planet; a point 2 km "north-east" on the surface.
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 6371.0, 0);
        PlanetCoordsKM point  = new PlanetCoordsKM(0.001, 6371.0, 0.002); // +1 m x, +2 m z (in km)
        LocalCoordsM local = CoordConvert.planetToLocal(point, origin);
        assertEquals(1.0f, local.x(), 1e-3f);
        assertEquals(0.0f, local.y(), 1e-3f);
        assertEquals(2.0f, local.z(), 1e-3f);
    }

    @Test
    void roundTripPlanetLocalPlanet() {
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 6371.0, 0);
        PlanetCoordsKM point  = new PlanetCoordsKM(0.5, 6371.2, -0.3);
        LocalCoordsM local = CoordConvert.planetToLocal(point, origin);
        PlanetCoordsKM back = CoordConvert.localToPlanet(local, origin);
        assertEquals(point.x(), back.x(), 1e-4);
        assertEquals(point.y(), back.y(), 1e-4);
        assertEquals(point.z(), back.z(), 1e-4);
    }

    @Test
    void subtractInDoubleSurvivesLargeCoordinatesWhereFloatWouldFail() {
        // Two points 0.5 m apart, 6371 km from centre. Naive float subtraction loses this.
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 6371.0, 0);
        PlanetCoordsKM point  = new PlanetCoordsKM(0, 6371.0005, 0); // +0.5 m
        LocalCoordsM local = CoordConvert.planetToLocal(point, origin);
        assertEquals(0.5f, local.y(), 1e-3f);
    }

    @Test
    void surfaceUpLocalIsRadialUnitVector() {
        PlanetCoordsKM atPole = new PlanetCoordsKM(0, 6371.0, 0);
        Vector3 up = CoordConvert.surfaceUpLocal(atPole);
        assertEquals(0f, up.x, 1e-6f);
        assertEquals(1f, up.y, 1e-6f);
        assertEquals(0f, up.z, 1e-6f);
        assertEquals(1f, up.len(), 1e-6f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.coords.CoordConvertTest"`
Expected: FAIL — `CoordConvert` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.galacticodyssey.core.coords;

import com.badlogic.gdx.math.Vector3;

/** Conversions between PLANET_SPACE (km, double) and LOCAL_SCENE (m, float). */
public final class CoordConvert {
    public static final double KM_TO_M = 1000.0;
    public static final double M_TO_KM = 0.001;

    private CoordConvert() {}

    /** Planet-space km -> local-scene metres, given the floating origin's planet-space position.
     *  Subtracts in double BEFORE casting to float (catastrophic-cancellation safe). */
    public static LocalCoordsM planetToLocal(PlanetCoordsKM world, PlanetCoordsKM originKm) {
        return new LocalCoordsM(
            (float) ((world.x() - originKm.x()) * KM_TO_M),
            (float) ((world.y() - originKm.y()) * KM_TO_M),
            (float) ((world.z() - originKm.z()) * KM_TO_M));
    }

    /** Local-scene metres -> planet-space km, given the floating origin's planet-space position. */
    public static PlanetCoordsKM localToPlanet(LocalCoordsM local, PlanetCoordsKM originKm) {
        return new PlanetCoordsKM(
            originKm.x() + local.x() * M_TO_KM,
            originKm.y() + local.y() * M_TO_KM,
            originKm.z() + local.z() * M_TO_KM);
    }

    /** "Up" at a planet-space point = normalized radial direction (planet centre at origin). */
    public static Vector3 surfaceUpLocal(PlanetCoordsKM planetKm) {
        double r = planetKm.len();
        if (r == 0) return new Vector3(0, 1, 0);
        return new Vector3((float) (planetKm.x() / r), (float) (planetKm.y() / r), (float) (planetKm.z() / r));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.coords.CoordConvertTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/coords/CoordConvert.java core/src/test/java/com/galacticodyssey/core/coords/CoordConvertTest.java
git commit -m "feat(coords): add CoordConvert planet<->local floating-origin boundary"
```

---

## Task 4: `TerrainMeshBuilder` emits chunk-local metre vertices

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainMeshBuilder.java:20-36`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainMeshBuilderLocalTest.java`

The new `build` signature replaces `float planetRadius` with `double radiusKm, PlanetCoordsKM chunkCenterKm`. Vertices are computed in double planet-space km, offset by the chunk centre, scaled to metres, cast to float.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.terrain;

import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.planet.BiomeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainMeshBuilderLocalTest {
    @Test
    void verticesAreChunkLocalMetresNotPlanetMagnitude() {
        double radiusKm = 6371.0; // Earth
        BiomeMap biomeMap = new BiomeMap(42L);
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        // A small chunk near the +Y pole (POS_Y face, centre of face).
        float u0 = 0.49f, v0 = 0.49f, u1 = 0.51f, v1 = 0.51f;
        float mu = (u0 + u1) * 0.5f, mv = (v0 + v1) * 0.5f;
        com.badlogic.gdx.math.Vector3 c = CubeSphere.toSphere(CubeFace.POS_Y, mu, mv);
        PlanetCoordsKM chunkCenter = new PlanetCoordsKM(c.x * radiusKm, c.y * radiusKm, c.z * radiusKm);

        TerrainMeshBuilder.MeshData d = TerrainMeshBuilder.build(
            CubeFace.POS_Y, u0, v0, u1, v1, noise, biomeMap, radiusKm, chunkCenter, 3, null);

        int stride = TerrainMeshBuilder.VERTEX_STRIDE;
        for (int i = 0; i < d.vertices.length; i += stride) {
            float x = d.vertices[i], y = d.vertices[i + 1], z = d.vertices[i + 2];
            // Chunk spans ~0.02 of a face ≈ 0.02 * (π/2) * 6371 km ≈ 200 km? No — 0.02*1.57*6371 ≈ 200 km.
            // The LOCAL magnitudes must stay within a few hundred km in metres, NOT ~6.37e9.
            assertTrue(Math.abs(x) < 5.0e5, "x local metres too large: " + x);
            assertTrue(Math.abs(z) < 5.0e5, "z local metres too large: " + z);
            // Reconstruct planet-space km and confirm it lies on/near the sphere surface.
            double px = chunkCenter.x() + x * 0.001;
            double py = chunkCenter.y() + y * 0.001;
            double pz = chunkCenter.z() + z * 0.001;
            double rKm = Math.sqrt(px*px + py*py + pz*pz);
            assertEquals(radiusKm, rKm, radiusKm * 0.02, "reconstructed radius off-surface");
        }
    }

    @Test
    void centreVertexIsNearLocalOrigin() {
        double radiusKm = 6371.0;
        BiomeMap biomeMap = new BiomeMap(7L);
        TerrainNoiseStack noise = new TerrainNoiseStack(7L);
        float u0 = 0.40f, v0 = 0.40f, u1 = 0.60f, v1 = 0.60f;
        float mu = 0.5f, mv = 0.5f;
        com.badlogic.gdx.math.Vector3 c = CubeSphere.toSphere(CubeFace.POS_Y, mu, mv);
        PlanetCoordsKM chunkCenter = new PlanetCoordsKM(c.x * radiusKm, c.y * radiusKm, c.z * radiusKm);
        TerrainMeshBuilder.MeshData d = TerrainMeshBuilder.build(
            CubeFace.POS_Y, u0, v0, u1, v1, noise, biomeMap, radiusKm, chunkCenter, 1, null);
        // Centre grid vertex (index 16,16 of 33x33) should be within a few hundred metres of local origin
        // (only terrain height offsets it from exactly 0).
        int centerIdx = (16 * TerrainMeshBuilder.GRID_SIZE + 16) * TerrainMeshBuilder.VERTEX_STRIDE;
        float cx = d.vertices[centerIdx], cz = d.vertices[centerIdx + 2];
        assertTrue(Math.hypot(cx, cz) < 1000f, "centre vertex not near local origin: " + cx + "," + cz);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainMeshBuilderLocalTest"`
Expected: FAIL — old `build` signature takes `float planetRadius`, not `double radiusKm, PlanetCoordsKM chunkCenter` (compile error).

- [ ] **Step 3: Modify implementation**

Replace the signature and the per-vertex position computation. Edit `TerrainMeshBuilder.java`:

```java
import com.galacticodyssey.core.coords.CoordConvert;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
```

```java
    public static MeshData build(CubeFace face, float u0, float v0, float u1, float v1,
                                  TerrainNoiseStack noise, BiomeMap biomeMap,
                                  double radiusKm, PlanetCoordsKM chunkCenterKm,
                                  int lod, int[] neighborLods) {
        float[] vertices = new float[GRID_SIZE * GRID_SIZE * VERTEX_STRIDE];
        BiomeType[] biomes = new BiomeType[GRID_SIZE * GRID_SIZE];
        Vector3[] positions = new Vector3[GRID_SIZE * GRID_SIZE];

        for (int gy = 0; gy < GRID_SIZE; gy++) {
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                float u = u0 + (u1 - u0) * gx / (GRID_SIZE - 1f);
                float v = v0 + (v1 - v0) * gy / (GRID_SIZE - 1f);
                Vector3 dir = CubeSphere.toSphere(face, u, v);
                TerrainNoiseStack.Sample sample = noise.sampleAt(dir, biomeMap, lod);
                biomes[gy * GRID_SIZE + gx] = sample.biome;
                // Planet-space radius at this vertex (km), including terrain height.
                double rKm = radiusKm + sample.height * radiusKm * 0.002;
                // Chunk-local position in METRES: subtract chunk centre in double, then scale.
                float lx = (float) ((dir.x * rKm - chunkCenterKm.x()) * CoordConvert.KM_TO_M);
                float ly = (float) ((dir.y * rKm - chunkCenterKm.y()) * CoordConvert.KM_TO_M);
                float lz = (float) ((dir.z * rKm - chunkCenterKm.z()) * CoordConvert.KM_TO_M);
                positions[gy * GRID_SIZE + gx] = new Vector3(lx, ly, lz);
            }
        }
        // ... (the second loop writing vertices[], computeNormal, biomeColor, buildIndices unchanged)
```

The remainder of the method (second loop, `computeNormal`, `biomeColor`, `buildIndices`) is unchanged — it reads `positions[]`, which now holds chunk-local metres.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainMeshBuilderLocalTest"`
Expected: PASS (2 tests). (Other callers won't compile yet — that's fixed in Task 6; do not run the full module build at this step.)

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/TerrainMeshBuilder.java core/src/test/java/com/galacticodyssey/planet/terrain/TerrainMeshBuilderLocalTest.java
git commit -m "feat(terrain): generate chunk-local metre meshes via double planet-space subtraction"
```

---

## Task 5: `TerrainChunk` — double km centre, double-camera LOD, placement field

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainChunk.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainChunkLodTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.terrain;

import com.galacticodyssey.core.coords.PlanetCoordsKM;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainChunkLodTest {
    @Test
    void centerIsPlanetSpaceKmAtRadius() {
        double radiusKm = 6371.0;
        TerrainChunk root = new TerrainChunk(CubeFace.POS_Y, 0, 0f, 0f, 1f, 1f, radiusKm);
        // POS_Y face centre points straight up -> (0, radiusKm, 0).
        assertEquals(0.0, root.centerPlanetKm.x(), 1e-6);
        assertEquals(radiusKm, root.centerPlanetKm.y(), 1e-6);
        assertEquals(0.0, root.centerPlanetKm.z(), 1e-6);
    }

    @Test
    void splitsWhenCameraIsCloseRelativeToArc() {
        double radiusKm = 6371.0;
        TerrainChunk root = new TerrainChunk(CubeFace.POS_Y, 0, 0f, 0f, 1f, 1f, radiusKm);
        // Camera 1 km above the face centre -> huge screen size -> should split.
        PlanetCoordsKM near = new PlanetCoordsKM(0, radiusKm + 1.0, 0);
        assertTrue(root.shouldSplit(near));
        // Camera one planet-radius away -> small screen size -> should merge.
        PlanetCoordsKM far = new PlanetCoordsKM(0, radiusKm * 3.0, 0);
        assertTrue(root.shouldMerge(far));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainChunkLodTest"`
Expected: FAIL — `centerPlanetKm` field and `double`-based constructor/`shouldSplit(PlanetCoordsKM)` don't exist.

- [ ] **Step 3: Modify implementation**

Edit `TerrainChunk.java` — change imports, the `center`/`arcLength` fields, constructor, and LOD methods. Add a placement field.

```java
import com.galacticodyssey.core.coords.LocalCoordsM;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
```

```java
    public final PlanetCoordsKM centerPlanetKm; // planet-space km
    public final double arcLengthKm;            // visual span, km
    /** Placement of this chunk's local-metre mesh in the current floating-origin frame.
     *  Recomputed by the quadtree on (re)placement / rebase. */
    public LocalCoordsM placementLocal = new LocalCoordsM(0, 0, 0);
```

```java
    public TerrainChunk(CubeFace face, int depth, float u0, float v0, float u1, float v1, double radiusKm) {
        this.face = face;
        this.depth = depth;
        this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
        com.badlogic.gdx.math.Vector3 d = CubeSphere.toSphere(face, (u0 + u1) * 0.5f, (v0 + v1) * 0.5f);
        this.centerPlanetKm = new PlanetCoordsKM(d.x * radiusKm, d.y * radiusKm, d.z * radiusKm);
        this.arcLengthKm = radiusKm * (u1 - u0) * 1.57;
        this.meshReady = false;
    }

    public boolean shouldSplit(PlanetCoordsKM cameraKm) {
        double dist = centerPlanetKm.dst(cameraKm);
        double screenSize = arcLengthKm / Math.max(dist, 1e-6);
        return screenSize > SPLIT_THRESHOLD && depth < MAX_DEPTH;
    }

    public boolean shouldMerge(PlanetCoordsKM cameraKm) {
        double dist = centerPlanetKm.dst(cameraKm);
        double screenSize = arcLengthKm / Math.max(dist, 1e-6);
        return screenSize < MERGE_THRESHOLD;
    }
```

Remove the old `public final Vector3 center;` and `public final float arcLength;` fields and the old `Vector3`-based `shouldSplit/shouldMerge`. Keep `SPLIT_THRESHOLD`/`MERGE_THRESHOLD`/`MAX_DEPTH` and `dispose*` methods as-is.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainChunkLodTest"`
Expected: PASS (2 tests). (`TerrainQuadtree` won't compile yet — fixed next task.)

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/TerrainChunk.java core/src/test/java/com/galacticodyssey/planet/terrain/TerrainChunkLodTest.java
git commit -m "feat(terrain): TerrainChunk in planet-space km with double-camera LOD + placement"
```

---

## Task 6: `TerrainQuadtree` — double radius/origin, collision via body transform, rebase

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainQuadtree.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainQuadtreeRebaseTest.java`

The quadtree now carries `double radiusKm` and a mutable `PlanetCoordsKM originPlanetKm`. `update` takes a `PlanetCoordsKM cameraKm`. `generateMesh` passes the chunk centre to the builder, computes `placementLocal = planetToLocal(centerKm, originPlanetKm)`, builds collision from chunk-local verts, and sets the body's world transform to `placementLocal`. `setOrigin(newOrigin)` recomputes every live chunk's `placementLocal` and updates collision body transforms (O(1) per chunk, no rebuild).

- [ ] **Step 1: Write the failing test** (headless — no GL; meshes are null, collision bodies exist only if a dynamicsWorld is passed, so pass `null` and assert placement math)

```java
package com.galacticodyssey.planet.terrain;

import com.galacticodyssey.core.coords.LocalCoordsM;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.planet.BiomeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainQuadtreeRebaseTest {
    private TerrainQuadtree newTree(PlanetCoordsKM origin) {
        double radiusKm = 6371.0;
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = new BiomeMap(42L);
        return new TerrainQuadtree(radiusKm, origin, noise, biomeMap, null); // null world = headless
    }

    @Test
    void placementEqualsPlanetToLocalOfChunkCentre() {
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 6371.0, 0);
        TerrainQuadtree tree = newTree(origin);
        tree.update(new PlanetCoordsKM(0, 6371.001, 0)); // camera 1 m up -> forces some generation
        for (TerrainChunk leaf : tree.getVisibleLeaves()) {
            LocalCoordsM expected = com.galacticodyssey.core.coords.CoordConvert
                .planetToLocal(leaf.centerPlanetKm, origin);
            assertEquals(expected.x(), leaf.placementLocal.x(), 0.5f);
            assertEquals(expected.y(), leaf.placementLocal.y(), 0.5f);
            assertEquals(expected.z(), leaf.placementLocal.z(), 0.5f);
        }
    }

    @Test
    void rebaseShiftsEveryPlacementByTheSameDelta() {
        PlanetCoordsKM origin = new PlanetCoordsKM(0, 6371.0, 0);
        TerrainQuadtree tree = newTree(origin);
        tree.update(new PlanetCoordsKM(0, 6371.001, 0));
        // snapshot placements
        java.util.Map<TerrainChunk, float[]> before = new java.util.HashMap<>();
        for (TerrainChunk leaf : tree.getVisibleLeaves())
            before.put(leaf, new float[]{leaf.placementLocal.x(), leaf.placementLocal.y(), leaf.placementLocal.z()});
        // Origin shifts +500 m in x (player walked +x, origin rebased): new origin is +0.5 km x.
        PlanetCoordsKM newOrigin = new PlanetCoordsKM(0.5, 6371.0, 0);
        tree.setOrigin(newOrigin);
        for (TerrainChunk leaf : tree.getVisibleLeaves()) {
            float[] b = before.get(leaf);
            assertNotNull(b);
            // Every placement.x must drop by 500 m; y,z unchanged.
            assertEquals(b[0] - 500f, leaf.placementLocal.x(), 1.0f);
            assertEquals(b[1], leaf.placementLocal.y(), 1.0f);
            assertEquals(b[2], leaf.placementLocal.z(), 1.0f);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainQuadtreeRebaseTest"`
Expected: FAIL — `TerrainQuadtree(double, PlanetCoordsKM, ...)`, `update(PlanetCoordsKM)`, and `setOrigin` don't exist.

- [ ] **Step 3: Modify implementation**

Edit `TerrainQuadtree.java`. Change fields/constructor, `update`, `generateMesh`, `buildCollision`, and add `setOrigin`.

```java
import com.galacticodyssey.core.coords.CoordConvert;
import com.galacticodyssey.core.coords.LocalCoordsM;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.badlogic.gdx.math.Matrix4;
```

```java
    private final TerrainChunk[] roots;
    private final double radiusKm;
    private PlanetCoordsKM originPlanetKm;
    private final TerrainNoiseStack noise;
    private final BiomeMap biomeMap;
    private final btDiscreteDynamicsWorld dynamicsWorld;

    public TerrainQuadtree(double radiusKm, PlanetCoordsKM originPlanetKm, TerrainNoiseStack noise,
                           BiomeMap biomeMap, btDiscreteDynamicsWorld dynamicsWorld) {
        this.radiusKm = radiusKm;
        this.originPlanetKm = originPlanetKm;
        this.noise = noise;
        this.biomeMap = biomeMap;
        this.dynamicsWorld = dynamicsWorld;
        this.roots = new TerrainChunk[6];
        CubeFace[] faces = CubeFace.values();
        for (int i = 0; i < 6; i++) {
            roots[i] = new TerrainChunk(faces[i], 0, 0f, 0f, 1f, 1f, radiusKm);
        }
    }

    public void update(PlanetCoordsKM cameraKm) {
        for (TerrainChunk root : roots) recursiveUpdate(root, cameraKm);
    }

    private void recursiveUpdate(TerrainChunk chunk, PlanetCoordsKM cameraKm) {
        if (!chunk.meshReady) generateMesh(chunk);
        if (chunk.shouldSplit(cameraKm) && !chunk.hasChildren()) split(chunk);
        else if (chunk.hasChildren() && chunk.shouldMerge(cameraKm)) merge(chunk);
        if (chunk.hasChildren()) for (TerrainChunk child : chunk.children) recursiveUpdate(child, cameraKm);
    }

    /** Recompute every live chunk's local placement after a floating-origin rebase. */
    public void setOrigin(PlanetCoordsKM newOrigin) {
        this.originPlanetKm = newOrigin;
        for (TerrainChunk root : roots) replaceRecursive(root);
    }

    private void replaceRecursive(TerrainChunk chunk) {
        applyPlacement(chunk);
        if (chunk.hasChildren()) for (TerrainChunk child : chunk.children) replaceRecursive(child);
    }

    private final Matrix4 tmpMat = new Matrix4();

    private void applyPlacement(TerrainChunk chunk) {
        chunk.placementLocal = CoordConvert.planetToLocal(chunk.centerPlanetKm, originPlanetKm);
        if (chunk.collisionBody != null) {
            tmpMat.idt().setTranslation(chunk.placementLocal.x(), chunk.placementLocal.y(), chunk.placementLocal.z());
            chunk.collisionBody.setWorldTransform(tmpMat);
        }
    }
```

In `split` keep the existing body/mesh disposal and child creation, but children are constructed with `radiusKm` (double):

```java
        chunk.children = new TerrainChunk[] {
            new TerrainChunk(chunk.face, d, chunk.u0, chunk.v0, mu, mv, radiusKm),
            new TerrainChunk(chunk.face, d, mu, chunk.v0, chunk.u1, mv, radiusKm),
            new TerrainChunk(chunk.face, d, chunk.u0, mv, mu, chunk.v1, radiusKm),
            new TerrainChunk(chunk.face, d, mu, mv, chunk.u1, chunk.v1, radiusKm),
        };
```

`generateMesh` passes the chunk centre to the builder and applies placement after building:

```java
    private void generateMesh(TerrainChunk chunk) {
        TerrainMeshBuilder.MeshData data = TerrainMeshBuilder.build(
            chunk.face, chunk.u0, chunk.v0, chunk.u1, chunk.v1,
            noise, biomeMap, radiusKm, chunk.centerPlanetKm, chunk.depth, null);
        try {
            chunk.mesh = new Mesh(true,
                TerrainMeshBuilder.GRID_SIZE * TerrainMeshBuilder.GRID_SIZE, data.indices.length,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"));
            chunk.mesh.setVertices(data.vertices);
            chunk.mesh.setIndices(data.indices);
        } catch (Exception e) {
            chunk.mesh = null; // headless / no GL
        }
        buildCollision(chunk, data);
        applyPlacement(chunk); // sets placementLocal and (if present) body transform
        chunk.meshReady = true;
    }
```

`buildCollision` now adds the **chunk-local** vertices directly (no `planetCenter` offset) — delete the `.add(planetCenter)`:

```java
            v0.set(data.vertices[i0], data.vertices[i0 + 1], data.vertices[i0 + 2]);
            v1.set(data.vertices[i1], data.vertices[i1 + 1], data.vertices[i1 + 2]);
            v2.set(data.vertices[i2], data.vertices[i2 + 1], data.vertices[i2 + 2]);
```

(The body's world position is set by `applyPlacement`, so the shape stays chunk-local.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainQuadtreeRebaseTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/TerrainQuadtree.java core/src/test/java/com/galacticodyssey/planet/terrain/TerrainQuadtreeRebaseTest.java
git commit -m "feat(terrain): quadtree placement via floating-origin transform + O(1) rebase"
```

---

## Task 7: `PlanetTerrainSystem` — real radius, origin tracking, rebase subscription, double getters

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystemScaleTest.java`

The system stores `radiusKm` (double) and `originPlanetKm`, subscribes to `OriginRebasedEvent`, feeds the quadtree a **planet-km camera** each frame, and exposes double getters. Earth radius derives from `planet.radius * 6371`. `loadPlanet` no longer takes a `float gameWorldRadius` toy override; callers pass the planet + the floating origin's planet-space position.

> Check `OriginRebasedEvent` field names before writing (the explore notes call them `deltaX/deltaY/deltaZ`; confirm in `core/events/OriginRebasedEvent.java`). The code below assumes `event.deltaX/deltaY/deltaZ` (metres) — adjust if the real accessors differ.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.terrain;

import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanetTerrainSystemScaleTest {
    private Planet earthLike() {
        // radius 1.0 Earth radii; constructor (seed,type,radius,mass,density,dayLength,axialTilt,tidallyLocked)
        return new Planet(123L, PlanetType.TERRAN, 1.0f, 1.0f, 5.5f, 24f, 23f, false);
    }

    @Test
    void radiusKmDerivesFromEarthRadii() {
        PlanetTerrainSystem sys = new PlanetTerrainSystem(null);
        sys.loadPlanet(earthLike(), new BiomeMap(123L), new PlanetCoordsKM(0, 6371.0, 0));
        assertEquals(6371.0, sys.getRadiusKm(), 1e-6);
    }

    @Test
    void moonScaleIsHundredsOfKm() {
        Planet moon = new Planet(9L, PlanetType.BARREN, 0.05f, 0.01f, 3.0f, 100f, 5f, false);
        PlanetTerrainSystem sys = new PlanetTerrainSystem(null);
        sys.loadPlanet(moon, new BiomeMap(9L), new PlanetCoordsKM(0, 0.05 * 6371.0, 0));
        assertEquals(0.05 * 6371.0, sys.getRadiusKm(), 1e-6);
    }

    @Test
    void cameraWorldIsConvertedToPlanetKm() {
        PlanetTerrainSystem sys = new PlanetTerrainSystem(null);
        sys.loadPlanet(earthLike(), new BiomeMap(123L), new PlanetCoordsKM(0, 6371.0, 0));
        sys.setCameraPositionLocal(new com.badlogic.gdx.math.Vector3(0, 10f, 0)); // 10 m above origin
        PlanetCoordsKM cam = sys.getCameraPlanetKm();
        assertEquals(0.0, cam.x(), 1e-9);
        assertEquals(6371.0 + 0.010, cam.y(), 1e-9); // +10 m == +0.010 km
        assertEquals(0.0, cam.z(), 1e-9);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.PlanetTerrainSystemScaleTest"`
Expected: FAIL — new `loadPlanet(Planet, BiomeMap, PlanetCoordsKM)` overload and `getRadiusKm`/`getCameraPlanetKm`/`setCameraPositionLocal` don't exist.

- [ ] **Step 3: Modify implementation**

Rewrite the system. Keep the `EntitySystem`/`Disposable` shape and the `PlateGenerator` tectonic wiring.

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.coords.CoordConvert;
import com.galacticodyssey.core.coords.LocalCoordsM;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.tectonic.PlateGenerator;
import com.galacticodyssey.planet.tectonic.TectonicModel;

import java.util.Collections;
import java.util.List;

public final class PlanetTerrainSystem extends EntitySystem implements Disposable {
    public static final double EARTH_RADIUS_KM = 6371.0;

    private final btDiscreteDynamicsWorld dynamicsWorld;
    private TerrainQuadtree quadtree;
    private double radiusKm;
    private PlanetCoordsKM originPlanetKm = PlanetCoordsKM.ORIGIN;
    private final Vector3 cameraLocal = new Vector3();

    public PlanetTerrainSystem(btDiscreteDynamicsWorld dynamicsWorld) {
        this.dynamicsWorld = dynamicsWorld;
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap, PlanetCoordsKM originPlanetKm) {
        loadPlanet(planet, biomeMap, new PlateGenerator().generate(planet), originPlanetKm);
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap, TectonicModel tectonic,
                           PlanetCoordsKM originPlanetKm) {
        if (quadtree != null) quadtree.dispose();
        long terrainSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.TERRAIN_DOMAIN), 0);
        TerrainNoiseStack noise = new TerrainNoiseStack(terrainSeed, tectonic);
        this.radiusKm = planet.radius * EARTH_RADIUS_KM;
        this.originPlanetKm = originPlanetKm;
        quadtree = new TerrainQuadtree(radiusKm, originPlanetKm, noise, biomeMap, dynamicsWorld);
    }

    public void unloadPlanet() {
        if (quadtree != null) { quadtree.dispose(); quadtree = null; }
    }

    /** Player/camera position in the current LOCAL_SCENE (metres). */
    public void setCameraPositionLocal(Vector3 localMetres) { cameraLocal.set(localMetres); }

    public PlanetCoordsKM getCameraPlanetKm() {
        return CoordConvert.localToPlanet(new LocalCoordsM(cameraLocal.x, cameraLocal.y, cameraLocal.z), originPlanetKm);
    }

    /** Apply a floating-origin rebase (metre deltas) — shift the planet-space origin. */
    public void onOriginRebased(float dxM, float dyM, float dzM) {
        originPlanetKm = new PlanetCoordsKM(
            originPlanetKm.x() + dxM * CoordConvert.M_TO_KM,
            originPlanetKm.y() + dyM * CoordConvert.M_TO_KM,
            originPlanetKm.z() + dzM * CoordConvert.M_TO_KM);
        if (quadtree != null) quadtree.setOrigin(originPlanetKm);
    }

    public double getRadiusKm() { return radiusKm; }
    /** Planet radius in LOCAL metres (for far-plane / fade scaling). */
    public float getRadiusLocalMetres() { return (float) (radiusKm * CoordConvert.KM_TO_M); }
    public PlanetCoordsKM getOriginPlanetKm() { return originPlanetKm; }

    /** Planet centre in the current LOCAL frame (large magnitude — fade/far-plane use only, NOT meshes). */
    public Vector3 getPlanetCenterLocal() {
        return CoordConvert.planetToLocal(PlanetCoordsKM.ORIGIN, originPlanetKm).toVector3();
    }

    public List<TerrainChunk> getVisibleLeaves() {
        if (quadtree == null) return Collections.emptyList();
        return quadtree.getVisibleLeaves();
    }

    @Override
    public void update(float deltaTime) {
        if (quadtree != null) quadtree.update(getCameraPlanetKm());
    }

    @Override
    public void dispose() { unloadPlanet(); }
}
```

> The `EventBus` subscription is wired in `GameWorld` (Task 9) by registering a handler that calls `onOriginRebased(...)`. Keeping the system free of the bus keeps it unit-testable.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.PlanetTerrainSystemScaleTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java core/src/test/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystemScaleTest.java
git commit -m "feat(terrain): real-scale radius + planet-space origin tracking in PlanetTerrainSystem"
```

---

## Task 8: `RadialGravitySystem` — local planet centre + rebase shift

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/systems/RadialGravitySystem.java`
- Test: `core/src/test/java/com/galacticodyssey/core/systems/RadialGravityDirectionTest.java`

Gravity direction is precision-safe in float even at a 6371 km lever arm (the normalize divides out the magnitude). The only change: the planet centre lives in the **local** frame and must shift by `-delta` on rebase, exactly like physics bodies. Add `onOriginRebased(dx,dy,dz)` and a setter, and extract a pure `computeGravityForce(bodyLocal, out)` for headless testing.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.core.systems;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RadialGravityDirectionTest {
    @Test
    void forcePointsFromBodyTowardPlanetCentre() {
        // Planet centre 6371 km below local origin; gravity 9.81, mass 80 kg.
        RadialGravitySystem sys = new RadialGravitySystem(null, new Vector3(0, -6_371_000f, 0), 9.81f);
        Vector3 out = new Vector3();
        sys.computeGravityForce(new Vector3(0, 2f, 0), 80f, out); // body 2 m above origin
        // Up is +Y, so gravity force is -Y, magnitude 9.81*80.
        assertEquals(0f, out.x, 1e-2f);
        assertEquals(-9.81f * 80f, out.y, 1e-1f);
        assertEquals(0f, out.z, 1e-2f);
    }

    @Test
    void rebaseKeepsForceDirectionStable() {
        RadialGravitySystem sys = new RadialGravitySystem(null, new Vector3(0, -6_371_000f, 0), 9.81f);
        // Player walked +x 800 m, origin rebased +800 m x -> every fixed point shifts -800 m x.
        sys.onOriginRebased(800f, 0f, 0f);
        Vector3 out = new Vector3();
        sys.computeGravityForce(new Vector3(0, 2f, 0), 80f, out);
        // The body at the new origin is now ~800 m east of the sub-point; tilt is ~800/6.371e6 rad ≈ 1.3e-4.
        assertEquals(-9.81f * 80f, out.y, 1f);            // still essentially straight down
        assertTrue(Math.abs(out.x) < 2f, "tiny eastward tilt expected, got " + out.x);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.RadialGravityDirectionTest"`
Expected: FAIL — `computeGravityForce` and `onOriginRebased` don't exist; constructor tolerates `null` world only if we guard `setGravity`.

- [ ] **Step 3: Modify implementation**

Guard the `null` dynamicsWorld (tests pass `null`), add the pure force method and rebase shift, and route `processEntity` through it.

```java
    public RadialGravitySystem(btDiscreteDynamicsWorld dynamicsWorld,
                                Vector3 planetCenter, float gravity) {
        super(Family.all(PhysicsBodyComponent.class, TransformComponent.class).get());
        this.planetCenter.set(planetCenter);
        this.gravity = gravity;
        if (dynamicsWorld != null) dynamicsWorld.setGravity(new Vector3(0, 0, 0));
    }

    public void setPlanetCenterLocal(Vector3 c) { this.planetCenter.set(c); }

    /** Shift the local planet centre on a floating-origin rebase (every fixed point moves by -delta). */
    public void onOriginRebased(float dxM, float dyM, float dzM) {
        planetCenter.sub(dxM, dyM, dzM);
    }

    /** Pure radial-gravity force for a body at a local position. Headless-testable. */
    public void computeGravityForce(Vector3 bodyLocal, float mass, Vector3 out) {
        out.set(bodyLocal).sub(planetCenter).nor().scl(-gravity * mass);
    }
```

Replace the force lines in `processEntity`:

```java
        physics.body.getWorldTransform(tempMat);
        tempMat.getTranslation(tempVec);
        computeGravityForce(tempVec, physics.mass, tempVec);
        physics.body.applyCentralForce(tempVec);
        physics.body.activate();
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.RadialGravityDirectionTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/systems/RadialGravitySystem.java core/src/test/java/com/galacticodyssey/core/systems/RadialGravityDirectionTest.java
git commit -m "feat(gravity): rebase-aware local planet centre + headless force method"
```

---

## Task 9: `GameWorld` wiring — real radius, origin, gravity centre, rebase routing

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java:354-364`, `:1160-1167`
- No new unit test (integration glue). Verified by build + the run-galactic-odyssey skill at the end.

This wires the real planet centre. The flat patch still spawns the player at local `y≈2`; the planet centre goes `radiusKm*1000` below. `loadPlanetTerrain` passes the floating origin's planet-space position (`(0, radiusKm, 0)`), and an `EventBus` subscription routes `OriginRebasedEvent` into both the terrain system and gravity.

- [ ] **Step 1: Update gravity-system construction (around line 360)**

Replace the toy 50 km centre with a centre derived later from the loaded planet. At construction time (before a planet is loaded) keep a safe default; `loadPlanetTerrain` re-centres it.

```java
        // Planet centre is re-set by loadPlanetTerrain once the real radius is known.
        radialGravitySystem = new RadialGravitySystem(
            bulletPhysicsSystem.getDynamicsWorld(),
            defaultPlanetCenter, 9.81f);
        engine.addSystem(radialGravitySystem);
        playerMovementSystem.setPlanetCenter(defaultPlanetCenter);

        // Route floating-origin rebases into terrain + gravity (planet centre is a fixed world point).
        eventBus.subscribe(com.galacticodyssey.core.events.OriginRebasedEvent.class, e -> {
            planetTerrainSystem.onOriginRebased(e.deltaX, e.deltaY, e.deltaZ);
            radialGravitySystem.onOriginRebased(e.deltaX, e.deltaY, e.deltaZ);
            playerMovementSystem.setPlanetCenter(radialGravitySystem.getPlanetCenterLocalCopy());
        });
```

> Confirm the `EventBus.subscribe` signature and `OriginRebasedEvent` accessor names in `core/EventBus.java` / `core/events/OriginRebasedEvent.java`; adapt the lambda to the real API (it may be `addListener`/`register`). Add a `getPlanetCenterLocalCopy()` returning `new Vector3(planetCenter)` to `RadialGravitySystem` if `PlayerMovementSystem` needs it; otherwise drop that line.

- [ ] **Step 2: Rewrite `loadPlanetTerrain` (around line 1161)**

```java
    public void loadPlanetTerrain(com.galacticodyssey.planet.Planet planet,
                                   com.galacticodyssey.planet.BiomeMap biomeMap) {
        double radiusKm = planet.radius * com.galacticodyssey.planet.terrain.PlanetTerrainSystem.EARTH_RADIUS_KM;
        // Player spawns at the +Y pole; the floating origin sits at the surface there.
        com.galacticodyssey.core.coords.PlanetCoordsKM origin =
            new com.galacticodyssey.core.coords.PlanetCoordsKM(0, radiusKm, 0);
        planetTerrainSystem.loadPlanet(planet, biomeMap, origin);
        // Re-centre radial gravity: planet centre is radiusKm*1000 metres straight down in local space.
        com.badlogic.gdx.math.Vector3 centerLocal =
            planetTerrainSystem.getPlanetCenterLocal(); // == (0, -radiusKm*1000, 0) at load
        radialGravitySystem.setPlanetCenterLocal(centerLocal);
        playerMovementSystem.setPlanetCenter(centerLocal);
    }
```

- [ ] **Step 3: Build the module to confirm compilation across all call sites**

Run: `./gradlew :core:compileJava :core:compileTestJava`
Expected: BUILD SUCCESSFUL. If any other reader of the removed `getPlanetCenter()`/`getPlanetRadius()`/old `loadPlanet` overloads fails, fix it to use `getPlanetCenterLocal()` / `getRadiusLocalMetres()` / the new overload (see Task 10 for `GameScreen`; grep `getPlanetCenter\|getPlanetRadius\|loadPlanet` for stragglers).

- [ ] **Step 4: Run the full terrain + coords test suite**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.coords.*" --tests "com.galacticodyssey.planet.terrain.*" --tests "com.galacticodyssey.core.systems.RadialGravityDirectionTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java core/src/main/java/com/galacticodyssey/core/systems/RadialGravitySystem.java
git commit -m "feat(world): wire real-scale planet centre, origin, and rebase routing"
```

---

## Task 10: `GameScreen.renderTerrain` — per-chunk transform + planet-space fade math

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java:1709-1777`
- Verified visually (GL path; not unit-tested). Build must pass; final game-run verifies.

Two changes: (1) feed the planet-space camera correctly and read radius/centre via the new metre getters; (2) set `u_worldTrans` **per chunk** from `chunk.placementLocal` instead of one shared planet-centre translate.

- [ ] **Step 1: Replace radius/camera/centre reads (lines ~1709, 1724, 1728)**

```java
        float sphereRadius = (pts != null) ? pts.getRadiusLocalMetres() : 0f; // metres
```
```java
                pts.setCameraPositionLocal(camera.position); // was setCameraPositionWorld
```
```java
                float distFromCenter = camera.position.dst(pts.getPlanetCenterLocal()); // was getPlanetCenter()
```

- [ ] **Step 2: Replace the shared-transform render loop (lines ~1763-1777)**

```java
                ShaderProgram shader = deferredRenderer.getShaderCache()
                    .get("gbuffer.vert", "gbuffer.frag", "HAS_VERTEX_COLOR", "TERRAIN_FADE");
                shader.bind();
                shader.setUniformMatrix("u_projViewTrans", camera.combined);
                shader.setUniformf("u_albedoTint", 1f, 1f, 1f, 1f);
                shader.setUniformf("u_metallicScale", 0f);
                shader.setUniformf("u_roughnessScale", 0.85f);
                shader.setUniformf("u_emissiveIntensity", 0f);
                shader.setUniformf("u_tiling", 1f, 1f);
                shader.setUniformf("u_terrainFade", terrainFade);
                // Normal matrix (rotation/scale only) — translation does not affect normals.
                tmpMat4.set(camera.view);
                tmpNormalMat3.set(tmpMat4).inv().transpose();
                shader.setUniformMatrix("u_normalMatrix", tmpNormalMat3);

                for (com.galacticodyssey.planet.terrain.TerrainChunk chunk : pts.getVisibleLeaves()) {
                    if (chunk.mesh != null) {
                        // Each chunk's mesh is local to its own centre; place it via its floating-origin transform.
                        tmpMat4.idt().setTranslation(
                            chunk.placementLocal.x(), chunk.placementLocal.y(), chunk.placementLocal.z());
                        shader.setUniformMatrix("u_worldTrans", tmpMat4);
                        chunk.mesh.render(shader, GL20.GL_TRIANGLES);
                    }
                }
```

- [ ] **Step 3: Build**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(render): per-chunk terrain placement transform for chunk-local meshes"
```

---

## Task 11: `ScatteringParams` — planet radius in metres (lockstep with terrain)

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/ScatteringParams.java:68,146,191`
- Test: `core/src/test/java/com/galacticodyssey/planet/ScatteringParamsScaleTest.java`

The sky shader's `u_planetRadius` must match the terrain's local-frame metre scale. Today it is km-magnitude (`planet.radius * 6371`). Multiply by 1000 so the atmosphere shell sits on the real-scale terrain.

> NOTE: this changes the sky integral's scale; SP3 owns full sky/altitude retuning. SP1 only keeps the shell attached to the terrain. If `atmosphereRadius` is derived as `planetRadius + thickness`, scale the thickness consistently (also ×1000 if it was km).

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScatteringParamsScaleTest {
    @Test
    void planetRadiusIsMetresForEarthLikePlanet() {
        Planet earth = new Planet(1L, PlanetType.TERRAN, 1.0f, 1.0f, 5.5f, 24f, 23f, false);
        earth.atmosphere = new Atmosphere(); // ensure non-vacuum path if required; adjust to real ctor
        ScatteringParams p = ScatteringParams.forPlanet(earth); // use the real factory name
        assertEquals(6_371_000f, p.planetRadius, 1f);
        assertTrue(p.atmosphereRadius > p.planetRadius);
    }
}
```

> Adjust the factory call (`forPlanet`/`fromPlanet`/etc.) and the `Atmosphere` construction to the real API before running — read `ScatteringParams.java` lines 80-180 for the actual entry point and whether a vacuum planet is acceptable for the test.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.ScatteringParamsScaleTest"`
Expected: FAIL — `planetRadius` is `6371`, not `6_371_000`.

- [ ] **Step 3: Modify implementation**

At each derivation site, convert km→m. Lines 146 and 191:

```java
        float pR   = planet.radius * 6371f * 1000f; // metres (lockstep with real-scale terrain)
```
```java
        float pR = radiusEarthRadii * 6371f * 1000f; // metres
```

For the constant default at line 68 (`float pR = 6371f;`) → `float pR = 6371f * 1000f;`. Ensure any `atmosphereRadius`/scale-height terms that were in km are scaled consistently (inspect the surrounding constructor args).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.ScatteringParamsScaleTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/ScatteringParams.java core/src/test/java/com/galacticodyssey/planet/ScatteringParamsScaleTest.java
git commit -m "fix(sky): planet radius in metres to match real-scale terrain frame"
```

---

## Task 12: Full suite + in-game verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full core test suite**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL. (Pre-existing `OrbitalMechanicsIntegrationTest` failures are unrelated — confirm they also fail on `master` before SP1 if they appear.)

- [ ] **Step 2: Launch the game and verify the surface still renders**

Use the **run-galactic-odyssey** skill to build, launch, and screenshot. Confirm: the game boots; the player stands on the flat patch; climbing past the flat-fade altitude shows the sphere terrain fading in without crashing. The surface will look **coarse** at altitude (≈1 km vertex spacing at Earth scale, `MAX_DEPTH=8`) — this is expected and is fixed in SP2. Note any depth-buffer artifacts on the high-altitude sphere backdrop (the far-plane is now `radius*3 ≈ 19,000 km`); record them for SP3, do not fix here.

- [ ] **Step 3: Commit any verification notes** (if a screenshot or note file was produced)

```bash
git add -A
git commit -m "chore(sp1): verification notes for floating-origin terrain spine"
```

---

## Notes / accepted limitations (carried to SP2/SP3)

- **Coarse near-field:** `MAX_DEPTH=8` ⇒ ~1 km vertex spacing at Earth scale. SP2 deepens LOD / adds a player-centred fine tile and makes the player stand on the sphere.
- **High-altitude backdrop depth precision:** the whole-sphere backdrop far-plane is now ~19,000 km; expect depth artifacts at orbital altitude until SP3 introduces the proper orbital pass / logarithmic depth.
- **Sky retuning:** `ScatteringParams` is now metre-scaled to stay attached; full sky/atmosphere transition behaviour is SP3.
- **Flat patch & spawn unchanged:** SP1 deliberately leaves `data/TerrainGenerator.java`, the spawn, and the altitude fade intact so the game stays playable.

---

## Self-review (completed by author)

- **Spec coverage:** §2 canonical model → Tasks 1-3; §4.1 origin/anchor → Task 7,9; §4.2 quadtree double km → Task 5,6; §4.3 chunk-local meshes → Task 4,6; §4.4 precision-safe band → Notes + Task 12; §4.5 gravity → Task 8,9; §4.6 sky lockstep → Task 11; §5 file list → all tasks; §7 tests 1-7 → Tasks 1-8; §6 contracts → Task 9 build-sweep + Task 10. All covered.
- **Placeholder scan:** the three "confirm the real API" notes (OriginRebasedEvent accessors, EventBus.subscribe signature, ScatteringParams factory name) are explicit verification instructions with a concrete assumed default and where to look — not blanks. Acceptable.
- **Type consistency:** `PlanetCoordsKM`/`LocalCoordsM`/`CoordConvert.planetToLocal/localToPlanet/surfaceUpLocal`, `radiusKm`, `originPlanetKm`, `placementLocal`, `onOriginRebased`, `computeGravityForce` used consistently across Tasks 1-11.
