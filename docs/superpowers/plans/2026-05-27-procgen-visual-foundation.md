# Procgen Visual Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add procedural sky, distance fog, and terrain color variation to make the procgen world look production-ready — no more flat dark void, no hard biome edges, distant objects fade naturally into the horizon.

**Architecture:** Shader-first approach — enhance the existing forward pipeline without FBO/deferred changes. Sky is a fullscreen quad rendered behind everything. Fog is exponential-squared, applied in terrain, ship, and ModelBatch fragment shaders. Terrain vertex colors gain 4 noise-based layers at mesh build time. Fog color = sky horizon color so the transition is seamless.

**Tech Stack:** libGDX (ShaderProgram, Mesh, ModelBatch, DefaultShader/DefaultShaderProvider), GLSL, simplex noise

---

## File Map

| File | Role |
|------|------|
| **New:** `core/src/main/java/com/galacticodyssey/ui/SkyRenderer.java` | Fullscreen quad mesh + sky shader (gradient, sun disc, horizon haze) |
| **New:** `core/src/main/java/com/galacticodyssey/ui/FogShaderProvider.java` | ModelBatch shader provider injecting exp² distance fog |
| **New:** `core/src/test/java/com/galacticodyssey/data/WorldPopulatorColorTest.java` | Unit tests for enhanced biomeColor() |
| **Modify:** `core/src/main/java/com/galacticodyssey/data/WorldPopulator.java` | Enhance `biomeColor()` with 4 color layers; expose noise perm in PopulatedWorld |
| **Modify:** `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` | Sky render pass, fog uniforms in terrain/ship shaders, FogShaderProvider for ModelBatch, updated render order |

---

### Task 1: Terrain Color Variation

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/data/WorldPopulator.java`
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java:285-356`
- Create: `core/src/test/java/com/galacticodyssey/data/WorldPopulatorColorTest.java`

- [ ] **Step 1: Add noisePerm to PopulatedWorld and make createPermutation accessible**

In `WorldPopulator.java`, add a `noisePerm` field to `PopulatedWorld`:

```java
public static final class PopulatedWorld implements Disposable {
    public final BiomeType[] biomeGrid;
    public final int[] noisePerm;
    public final Array<ModelInstance> treeInstances = new Array<>();
    // ... rest unchanged ...

    public PopulatedWorld(BiomeType[] biomeGrid, int[] noisePerm) {
        this.biomeGrid = biomeGrid;
        this.noisePerm = noisePerm;
    }
    // ... rest unchanged ...
}
```

In `populate()`, create the perm table and pass it to PopulatedWorld:

```java
public static PopulatedWorld populate(
        float[] heightmap, int vertsX, int vertsZ,
        float worldWidth, float worldDepth, long seed) {

    Random rng = new Random(seed + 7919L);
    // ... (existing cellW/cellD/halfW/halfD/minH/maxH/heightRange/seaLevel) ...

    int[] noisePerm = createPermutation(new Random(seed + 31337L));
    BiomeType[] biomeGrid = classifyBiomes(heightmap, vertsX, vertsZ, minH, heightRange, seaLevel, seed);
    PopulatedWorld world = new PopulatedWorld(biomeGrid, noisePerm);
    world.seaLevel = seaLevel;
    // ... rest unchanged ...
}
```

Change `createPermutation` from `private` to package-private (remove `private`):

```java
static int[] createPermutation(Random rng) {
```

Also change `noise2D` from `private` to package-private since `biomeColor` needs it:

```java
static float noise2D(int[] perm, float x, float y) {
```

- [ ] **Step 2: Run tests to verify nothing is broken**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.data.*" -q`
Expected: All existing tests pass (the field change is backwards-compatible since `classifyBiomes` creates its own perm internally).

- [ ] **Step 3: Write the failing tests for enhanced biomeColor**

Create `core/src/test/java/com/galacticodyssey/data/WorldPopulatorColorTest.java`:

```java
package com.galacticodyssey.data;

import com.badlogic.gdx.graphics.Color;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WorldPopulatorColorTest {

    private static int[] perm;

    @BeforeAll
    static void setup() {
        perm = WorldPopulator.createPermutation(new Random(42L));
    }

    @Test
    void noiseVariationChangesBaseColor() {
        BiomeType biome = BiomeType.GRASSLAND;
        int size = 5;
        BiomeType[] grid = uniformGrid(biome, size);

        Color c1 = WorldPopulator.biomeColor(biome, 0.5f, 0f,
            10f, 10f, 20f, 0f, 80f, perm, grid, size, size, 2, 2);
        Color c2 = WorldPopulator.biomeColor(biome, 0.5f, 0f,
            50f, 50f, 20f, 0f, 80f, perm, grid, size, size, 2, 2);

        assertNotEquals(c1.r, c2.r, 0.001f,
            "Noise variation should produce different red values at different world positions");
    }

    @Test
    void noiseVariationIsDeterministic() {
        BiomeType biome = BiomeType.GRASSLAND;
        int size = 5;
        BiomeType[] grid = uniformGrid(biome, size);

        Color c1 = WorldPopulator.biomeColor(biome, 0.5f, 0f,
            10f, 10f, 20f, 0f, 80f, perm, grid, size, size, 2, 2);
        Color c2 = WorldPopulator.biomeColor(biome, 0.5f, 0f,
            10f, 10f, 20f, 0f, 80f, perm, grid, size, size, 2, 2);

        assertEquals(c1.r, c2.r, 0.0001f, "Same inputs should produce identical colors");
        assertEquals(c1.g, c2.g, 0.0001f);
        assertEquals(c1.b, c2.b, 0.0001f);
    }

    @Test
    void steepSlopeBlendTowardRock() {
        BiomeType biome = BiomeType.GRASSLAND;
        int size = 5;
        BiomeType[] grid = uniformGrid(biome, size);

        Color flat = WorldPopulator.biomeColor(biome, 0.5f, 0f,
            10f, 10f, 20f, 0f, 80f, perm, grid, size, size, 2, 2);
        Color steep = WorldPopulator.biomeColor(biome, 0.5f, 0.7f,
            10f, 10f, 20f, 0f, 80f, perm, grid, size, size, 2, 2);

        float rockR = 0.42f, rockG = 0.38f, rockB = 0.32f;
        float flatDistToRock = Math.abs(flat.r - rockR) + Math.abs(flat.g - rockG) + Math.abs(flat.b - rockB);
        float steepDistToRock = Math.abs(steep.r - rockR) + Math.abs(steep.g - rockG) + Math.abs(steep.b - rockB);

        assertTrue(steepDistToRock < flatDistToRock,
            "Steep slopes should be closer to rock color than flat ground");
    }

    @Test
    void highAltitudeBlendTowardSnow() {
        BiomeType biome = BiomeType.GRASSLAND;
        int size = 5;
        BiomeType[] grid = uniformGrid(biome, size);

        Color low = WorldPopulator.biomeColor(biome, 0.3f, 0f,
            10f, 10f, 24f, 0f, 80f, perm, grid, size, size, 2, 2);
        Color high = WorldPopulator.biomeColor(biome, 0.3f, 0f,
            10f, 10f, 72f, 0f, 80f, perm, grid, size, size, 2, 2);

        float snowR = 0.92f, snowG = 0.93f, snowB = 0.95f;
        float lowDistToSnow = Math.abs(low.r - snowR) + Math.abs(low.g - snowG) + Math.abs(low.b - snowB);
        float highDistToSnow = Math.abs(high.r - snowR) + Math.abs(high.g - snowG) + Math.abs(high.b - snowB);

        assertTrue(highDistToSnow < lowDistToSnow,
            "High altitude vertices should be closer to snow color");
    }

    @Test
    void biomeEdgeBlendsDifferentFromInterior() {
        int size = 5;
        BiomeType[] grid = new BiomeType[size * size];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = BiomeType.GRASSLAND;
        }
        grid[1 * size + 2] = BiomeType.DESERT;

        Color edgeColor = WorldPopulator.biomeColor(BiomeType.GRASSLAND, 0.5f, 0f,
            10f, 10f, 40f, 0f, 80f, perm, grid, size, size, 2, 1);

        BiomeType[] uniformGrid = uniformGrid(BiomeType.GRASSLAND, size);
        Color interiorColor = WorldPopulator.biomeColor(BiomeType.GRASSLAND, 0.5f, 0f,
            10f, 10f, 40f, 0f, 80f, perm, uniformGrid, size, size, 2, 1);

        float diff = Math.abs(edgeColor.r - interiorColor.r)
            + Math.abs(edgeColor.g - interiorColor.g)
            + Math.abs(edgeColor.b - interiorColor.b);
        assertTrue(diff > 0.01f,
            "Vertices near a biome boundary should differ from interior vertices");
    }

    private static BiomeType[] uniformGrid(BiomeType biome, int size) {
        BiomeType[] grid = new BiomeType[size * size];
        for (int i = 0; i < grid.length; i++) grid[i] = biome;
        return grid;
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.data.WorldPopulatorColorTest" -q`
Expected: Compile error — `biomeColor` doesn't accept the new parameter list yet.

- [ ] **Step 5: Add new biomeColor signature with skeleton + smoothstep helper**

In `WorldPopulator.java`, add the new method above the existing `biomeColor`. Keep the old one for now (will be removed in step 8).

```java
public static Color biomeColor(BiomeType biome, float heightFrac, float slope,
                                float worldX, float worldZ, float height,
                                float minH, float maxH, int[] noisePerm,
                                BiomeType[] biomeGrid, int vertsX, int vertsZ,
                                int gridX, int gridZ) {
    float r, g, b;

    // Base biome color
    float[] base = baseBiomeRGB(biome);
    r = base[0];
    g = base[1];
    b = base[2];

    // TODO: Layer 1 — Noise variation
    // TODO: Layer 2 — Biome edge blending
    // TODO: Layer 3 — Slope-driven rock
    // TODO: Layer 4 — Altitude snow

    return new Color(
        Math.max(0f, Math.min(1f, r)),
        Math.max(0f, Math.min(1f, g)),
        Math.max(0f, Math.min(1f, b)), 1f);
}

private static float[] baseBiomeRGB(BiomeType biome) {
    switch (biome) {
        case OCEAN:            return new float[]{0.05f, 0.15f, 0.45f};
        case ICE_SHEET:        return new float[]{0.85f, 0.90f, 0.95f};
        case ICE_FIELD:        return new float[]{0.80f, 0.85f, 0.92f};
        case TUNDRA:           return new float[]{0.45f, 0.50f, 0.42f};
        case POLAR_DESERT:     return new float[]{0.55f, 0.52f, 0.48f};
        case BOREAL_FOREST:    return new float[]{0.12f, 0.30f, 0.15f};
        case TEMPERATE_FOREST: return new float[]{0.15f, 0.40f, 0.12f};
        case STEPPE:           return new float[]{0.50f, 0.48f, 0.30f};
        case ROCKY_WASTE:      return new float[]{0.42f, 0.38f, 0.32f};
        case TROPICAL_FOREST:  return new float[]{0.08f, 0.42f, 0.10f};
        case GRASSLAND:        return new float[]{0.30f, 0.55f, 0.18f};
        case ARID_SHRUB:       return new float[]{0.58f, 0.52f, 0.30f};
        case DESERT:           return new float[]{0.76f, 0.68f, 0.42f};
        case SWAMP:            return new float[]{0.20f, 0.32f, 0.18f};
        case SAVANNA:          return new float[]{0.55f, 0.52f, 0.25f};
        case BADLANDS:         return new float[]{0.62f, 0.35f, 0.22f};
        case VOLCANIC:         return new float[]{0.25f, 0.15f, 0.12f};
        case LAKE:             return new float[]{0.10f, 0.25f, 0.50f};
        default:               return new float[]{0.30f, 0.30f, 0.30f};
    }
}

private static float smoothstep(float edge0, float edge1, float x) {
    float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
    return t * t * (3f - 2f * t);
}
```

- [ ] **Step 6: Run tests to verify they fail on assertions (not compilation)**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.data.WorldPopulatorColorTest" -q`
Expected: `noiseVariationChangesBaseColor` fails (no noise applied yet), `steepSlopeBlendTowardRock` fails, `highAltitudeBlendTowardSnow` fails, `biomeEdgeBlendsDifferentFromInterior` fails. `noiseVariationIsDeterministic` may pass since identical inputs return identical base colors.

- [ ] **Step 7: Implement all four color layers**

Replace the TODO comments in the new `biomeColor` method with the full implementation:

```java
public static Color biomeColor(BiomeType biome, float heightFrac, float slope,
                                float worldX, float worldZ, float height,
                                float minH, float maxH, int[] noisePerm,
                                BiomeType[] biomeGrid, int vertsX, int vertsZ,
                                int gridX, int gridZ) {
    float[] base = baseBiomeRGB(biome);
    float r = base[0], g = base[1], b = base[2];

    // Layer 1: Noise variation — perturb base color by ±15% using two octaves
    float n1 = noise2D(noisePerm, worldX * 0.02f, worldZ * 0.02f);
    float n2 = noise2D(noisePerm, worldX * 0.05f + 100f, worldZ * 0.05f + 100f);
    float noiseVal = (n1 * 0.7f + n2 * 0.3f) * 0.15f;
    r += noiseVal;
    g += noiseVal;
    b += noiseVal;

    // Layer 2: Biome edge blending — lerp with different-biome neighbors
    int left  = Math.max(0, gridX - 1);
    int right = Math.min(vertsX - 1, gridX + 1);
    int up    = Math.max(0, gridZ - 1);
    int down  = Math.min(vertsZ - 1, gridZ + 1);

    BiomeType[] neighbors = {
        biomeGrid[gridZ * vertsX + left],
        biomeGrid[gridZ * vertsX + right],
        biomeGrid[up * vertsX + gridX],
        biomeGrid[down * vertsX + gridX]
    };

    float blendWeight = 0f;
    float blendR = 0f, blendG = 0f, blendB = 0f;
    int blendCount = 0;
    for (BiomeType nb : neighbors) {
        if (nb != biome) {
            float[] nbRGB = baseBiomeRGB(nb);
            float noiseFactor = 0.4f + noise2D(noisePerm, worldX * 0.03f + 200f, worldZ * 0.03f + 200f) * 0.2f;
            blendR += nbRGB[0];
            blendG += nbRGB[1];
            blendB += nbRGB[2];
            blendWeight += noiseFactor;
            blendCount++;
        }
    }
    if (blendCount > 0) {
        blendR /= blendCount;
        blendG /= blendCount;
        blendB /= blendCount;
        float t = Math.min(1f, blendWeight / blendCount);
        r = r * (1f - t) + blendR * t;
        g = g * (1f - t) + blendG * t;
        b = b * (1f - t) + blendB * t;
    }

    // Layer 3: Slope-driven rock — steep slopes blend toward grey-brown
    float normalY = 1f - slope;
    float rockBlend = 1f - smoothstep(0.5f, 0.75f, normalY);
    if (rockBlend > 0f) {
        r = r * (1f - rockBlend) + 0.42f * rockBlend;
        g = g * (1f - rockBlend) + 0.38f * rockBlend;
        b = b * (1f - rockBlend) + 0.32f * rockBlend;
    }

    // Layer 4: Altitude snow — above 75th percentile, blend toward white
    float heightRange = maxH - minH + 0.001f;
    float snowLine = minH + heightRange * 0.75f;
    float snowNoise = noise2D(noisePerm, worldX * 0.01f + 300f, worldZ * 0.01f + 300f) * heightRange * 0.08f;
    float snowThreshold = snowLine + snowNoise;
    if (height > snowThreshold - heightRange * 0.1f) {
        float snowBlend = smoothstep(snowThreshold - heightRange * 0.1f, snowThreshold + heightRange * 0.05f, height);
        r = r * (1f - snowBlend) + 0.92f * snowBlend;
        g = g * (1f - snowBlend) + 0.93f * snowBlend;
        b = b * (1f - snowBlend) + 0.95f * snowBlend;
    }

    return new Color(
        Math.max(0f, Math.min(1f, r)),
        Math.max(0f, Math.min(1f, g)),
        Math.max(0f, Math.min(1f, b)), 1f);
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.data.WorldPopulatorColorTest" -q`
Expected: All 5 tests PASS.

- [ ] **Step 9: Update GameScreen.createTerrainMesh() to use the new signature**

In `GameScreen.java`, replace the `createTerrainMesh()` method's vertex color section. The current code (around lines 296-326) computes `slope`, `heightFrac`, and calls the old 3-arg `biomeColor`. Replace with:

```java
private void createTerrainMesh() {
    float[] normals = TerrainGenerator.computeNormals(
        heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH);

    int vertCount = TERRAIN_VERTS_X * TERRAIN_VERTS_Z;
    float[] vertices = new float[vertCount * 10];

    float cellW = TERRAIN_WIDTH / (TERRAIN_VERTS_X - 1);
    float cellD = TERRAIN_DEPTH / (TERRAIN_VERTS_Z - 1);
    float halfW = TERRAIN_WIDTH / 2f;
    float halfD = TERRAIN_DEPTH / 2f;

    float minH = Float.MAX_VALUE, maxH = -Float.MAX_VALUE;
    for (float h : heightmap) {
        minH = Math.min(minH, h);
        maxH = Math.max(maxH, h);
    }

    for (int z = 0; z < TERRAIN_VERTS_Z; z++) {
        for (int x = 0; x < TERRAIN_VERTS_X; x++) {
            int idx = z * TERRAIN_VERTS_X + x;
            int vi = idx * 10;
            float h = heightmap[idx];

            float wx = x * cellW - halfW;
            float wz = z * cellD - halfD;

            vertices[vi]     = wx;
            vertices[vi + 1] = h;
            vertices[vi + 2] = wz;

            vertices[vi + 3] = normals[idx * 3];
            vertices[vi + 4] = normals[idx * 3 + 1];
            vertices[vi + 5] = normals[idx * 3 + 2];

            float slope = 1f - normals[idx * 3 + 1];
            float heightFrac = (h - minH) / (maxH - minH + 0.001f);

            BiomeType biome = populatedWorld.biomeGrid[idx];
            Color biomeCol = WorldPopulator.biomeColor(biome, heightFrac, slope,
                wx, wz, h, minH, maxH, populatedWorld.noisePerm,
                populatedWorld.biomeGrid, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, x, z);

            vertices[vi + 6] = biomeCol.r;
            vertices[vi + 7] = biomeCol.g;
            vertices[vi + 8] = biomeCol.b;
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
```

- [ ] **Step 10: Remove the old 3-arg biomeColor method**

In `WorldPopulator.java`, delete the old `biomeColor(BiomeType, float, float)` method (the one at lines 144-174 that only takes biome, heightFrac, slope). All callers now use the new 14-arg version.

- [ ] **Step 11: Run all tests**

Run: `gradlew.bat :core:test -q`
Expected: All tests pass including the new WorldPopulatorColorTest.

- [ ] **Step 12: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/WorldPopulator.java core/src/main/java/com/galacticodyssey/ui/GameScreen.java core/src/test/java/com/galacticodyssey/data/WorldPopulatorColorTest.java
git commit -m "feat(terrain): add 4-layer vertex color variation — noise, biome blending, slope rock, altitude snow"
```

---

### Task 2: SkyRenderer

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/SkyRenderer.java`

- [ ] **Step 1: Create SkyRenderer.java with fullscreen quad and sky shader**

Create `core/src/main/java/com/galacticodyssey/ui/SkyRenderer.java`:

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

public class SkyRenderer implements Disposable {

    private static final String VERTEX_SHADER =
        "attribute vec2 a_position;\n" +
        "uniform mat4 u_invViewProj;\n" +
        "uniform vec3 u_cameraPos;\n" +
        "varying vec3 v_rayDir;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(a_position, 0.9999, 1.0);\n" +
        "    vec4 farPoint = u_invViewProj * vec4(a_position, 1.0, 1.0);\n" +
        "    v_rayDir = farPoint.xyz / farPoint.w - u_cameraPos;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
        "#ifdef GL_ES\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "uniform vec3 u_sunDirection;\n" +
        "uniform vec3 u_zenithColor;\n" +
        "uniform vec3 u_horizonColor;\n" +
        "uniform vec3 u_sunColor;\n" +
        "varying vec3 v_rayDir;\n" +
        "void main() {\n" +
        "    vec3 ray = normalize(v_rayDir);\n" +
        "    float altitude = ray.y;\n" +
        "    float gradientFactor = smoothstep(-0.1, 0.5, altitude);\n" +
        "    vec3 skyColor = mix(u_horizonColor, u_zenithColor, gradientFactor);\n" +
        "    float hazeFactor = exp(-abs(altitude) * 10.0);\n" +
        "    skyColor = mix(skyColor, u_horizonColor, hazeFactor * 0.5);\n" +
        "    float sunDot = dot(ray, u_sunDirection);\n" +
        "    float sunDisc = pow(max(sunDot, 0.0), 500.0);\n" +
        "    float sunGlow = pow(max(sunDot, 0.0), 8.0);\n" +
        "    skyColor += u_sunColor * sunDisc * 2.0;\n" +
        "    skyColor += u_sunColor * sunGlow * 0.15;\n" +
        "    if (altitude < 0.0) {\n" +
        "        skyColor = mix(u_horizonColor, skyColor, exp(altitude * 5.0));\n" +
        "    }\n" +
        "    gl_FragColor = vec4(skyColor, 1.0);\n" +
        "}\n";

    private final Mesh quad;
    private final ShaderProgram shader;
    private final Matrix4 invViewProj = new Matrix4();

    public final Vector3 zenithColor = new Vector3(0.05f, 0.1f, 0.3f);
    public final Vector3 horizonColor = new Vector3(0.6f, 0.55f, 0.45f);
    public final Vector3 sunColor = new Vector3(1.0f, 0.9f, 0.7f);

    public SkyRenderer() {
        float[] verts = {-1, -1, 1, -1, 1, 1, -1, 1};
        short[] indices = {0, 1, 2, 0, 2, 3};
        quad = new Mesh(true, 4, 6,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"));
        quad.setVertices(verts);
        quad.setIndices(indices);

        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            Gdx.app.error("SkyRenderer", "Shader failed to compile:\n" + shader.getLog());
        }
    }

    public void render(PerspectiveCamera camera, Vector3 sunDirection) {
        invViewProj.set(camera.combined).inv();

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);

        shader.bind();
        shader.setUniformMatrix("u_invViewProj", invViewProj);
        shader.setUniformf("u_cameraPos", camera.position);
        shader.setUniformf("u_sunDirection", sunDirection);
        shader.setUniformf("u_zenithColor", zenithColor);
        shader.setUniformf("u_horizonColor", horizonColor);
        shader.setUniformf("u_sunColor", sunColor);

        quad.render(shader, GL20.GL_TRIANGLES);

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
    }

    @Override
    public void dispose() {
        quad.dispose();
        shader.dispose();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `gradlew.bat :core:compileJava -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/SkyRenderer.java
git commit -m "feat(sky): add SkyRenderer with fullscreen quad, gradient, sun disc, horizon haze"
```

---

### Task 3: FogShaderProvider

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/FogShaderProvider.java`

- [ ] **Step 1: Create FogShaderProvider.java**

Create `core/src/main/java/com/galacticodyssey/ui/FogShaderProvider.java`:

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.math.Vector3;

public class FogShaderProvider extends DefaultShaderProvider {

    private static final String VERTEX_SHADER =
        "attribute vec3 a_position;\n" +
        "attribute vec3 a_normal;\n" +
        "uniform mat4 u_projViewTrans;\n" +
        "uniform mat4 u_worldTrans;\n" +
        "varying vec3 v_normal;\n" +
        "varying vec3 v_worldPos;\n" +
        "void main() {\n" +
        "    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);\n" +
        "    v_worldPos = worldPos.xyz;\n" +
        "    v_normal = normalize(mat3(u_worldTrans[0].xyz, u_worldTrans[1].xyz, u_worldTrans[2].xyz) * a_normal);\n" +
        "    gl_Position = u_projViewTrans * worldPos;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
        "#ifdef GL_ES\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "uniform vec4 u_diffuseColor;\n" +
        "uniform vec3 u_lightDir;\n" +
        "uniform vec4 u_ambientColor;\n" +
        "uniform vec3 u_cameraPos;\n" +
        "uniform float u_fogDensity;\n" +
        "uniform vec3 u_fogColor;\n" +
        "varying vec3 v_normal;\n" +
        "varying vec3 v_worldPos;\n" +
        "void main() {\n" +
        "    vec3 lightDir = normalize(-u_lightDir);\n" +
        "    float diff = max(dot(v_normal, lightDir), 0.0);\n" +
        "    vec3 lit = u_diffuseColor.rgb * (u_ambientColor.rgb + diff * vec3(0.8, 0.8, 0.75));\n" +
        "    float dist = length(v_worldPos - u_cameraPos);\n" +
        "    float fogFactor = exp(-u_fogDensity * dist * u_fogDensity * dist);\n" +
        "    fogFactor = clamp(fogFactor, 0.0, 1.0);\n" +
        "    vec3 color = mix(u_fogColor, lit, fogFactor);\n" +
        "    gl_FragColor = vec4(color, u_diffuseColor.a);\n" +
        "}\n";

    float fogDensity = 0.004f;
    final Vector3 fogColor = new Vector3(0.6f, 0.55f, 0.45f);
    final Vector3 lightDir = new Vector3(-0.4f, -0.8f, -0.3f);
    final float[] ambientColor = {0.3f, 0.3f, 0.35f, 1f};

    public FogShaderProvider() {
        super(createConfig());
    }

    private static DefaultShader.Config createConfig() {
        DefaultShader.Config config = new DefaultShader.Config();
        config.vertexShader = VERTEX_SHADER;
        config.fragmentShader = FRAGMENT_SHADER;
        return config;
    }

    public void setFogParams(float density, Vector3 fogCol) {
        this.fogDensity = density;
        this.fogColor.set(fogCol);
    }

    @Override
    protected Shader createShader(Renderable renderable) {
        return new FogShader(renderable, config, this);
    }

    private static class FogShader extends DefaultShader {
        private final FogShaderProvider provider;

        FogShader(Renderable renderable, Config config, FogShaderProvider provider) {
            super(renderable, config);
            this.provider = provider;
        }

        @Override
        public void begin(Camera camera, RenderContext context) {
            super.begin(camera, context);
            program.setUniformf("u_fogDensity", provider.fogDensity);
            program.setUniformf("u_fogColor", provider.fogColor);
            program.setUniformf("u_cameraPos", camera.position);
            program.setUniformf("u_lightDir", provider.lightDir);
            program.setUniformf("u_ambientColor",
                provider.ambientColor[0], provider.ambientColor[1],
                provider.ambientColor[2], provider.ambientColor[3]);
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `gradlew.bat :core:compileJava -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/FogShaderProvider.java
git commit -m "feat(fog): add FogShaderProvider for ModelBatch distance fog"
```

---

### Task 4: GameScreen Integration — Fog in Terrain Shader

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Add fog uniforms and world-position varying to the terrain shader**

In `GameScreen.java`, replace the `getTerrainShader()` method with a version that adds fog. Replace the existing method (lines 524-561) with:

```java
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
        "varying vec3 v_worldPos;\n" +
        "void main() {\n" +
        "    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);\n" +
        "    v_worldPos = worldPos.xyz;\n" +
        "    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);\n" +
        "    v_color = a_color;\n" +
        "    gl_Position = u_projViewTrans * worldPos;\n" +
        "}\n";

    String frag =
        "#ifdef GL_ES\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "varying vec3 v_normal;\n" +
        "varying vec4 v_color;\n" +
        "varying vec3 v_worldPos;\n" +
        "uniform vec3 u_lightDir;\n" +
        "uniform vec4 u_ambientColor;\n" +
        "uniform vec3 u_cameraPos;\n" +
        "uniform float u_fogDensity;\n" +
        "uniform vec3 u_fogColor;\n" +
        "void main() {\n" +
        "    vec3 lightDir = normalize(-u_lightDir);\n" +
        "    float diff = max(dot(v_normal, lightDir), 0.0);\n" +
        "    vec3 lit = v_color.rgb * (u_ambientColor.rgb + diff * vec3(0.8, 0.8, 0.75));\n" +
        "    float dist = length(v_worldPos - u_cameraPos);\n" +
        "    float fogFactor = exp(-u_fogDensity * dist * u_fogDensity * dist);\n" +
        "    fogFactor = clamp(fogFactor, 0.0, 1.0);\n" +
        "    vec3 color = mix(u_fogColor, lit, fogFactor);\n" +
        "    gl_FragColor = vec4(color, 1.0);\n" +
        "}\n";

    terrainShader = new ShaderProgram(vert, frag);
    if (!terrainShader.isCompiled()) {
        Gdx.app.error("Shader", terrainShader.getLog());
    }
    return terrainShader;
}
```

- [ ] **Step 2: Set fog uniforms in renderTerrain()**

Replace the `renderTerrain()` method with:

```java
private void renderTerrain() {
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
    Gdx.gl.glDepthMask(true);

    ShaderProgram shader = getTerrainShader();
    shader.bind();
    shader.setUniformMatrix("u_projViewTrans", camera.combined);

    Matrix4 modelMat = new Matrix4();
    shader.setUniformMatrix("u_worldTrans", modelMat);
    shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
    shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);
    shader.setUniformf("u_cameraPos", camera.position);
    shader.setUniformf("u_fogDensity", fogDensity);
    shader.setUniformf("u_fogColor", horizonColor);

    terrainMesh.render(shader, GL20.GL_TRIANGLES);
}
```

- [ ] **Step 3: Verify compilation**

Run: `gradlew.bat :core:compileJava -q`
Expected: Compile error — `fogDensity` and `horizonColor` fields don't exist yet on GameScreen. That's expected; they'll be added in Task 6 step 1. For now, add placeholder fields to make it compile:

At the top of `GameScreen`, after the existing field declarations (around line 91), add:

```java
private static final float FOG_DENSITY = 0.004f;
private float fogDensity = FOG_DENSITY;
private final Vector3 horizonColor = new Vector3(0.6f, 0.55f, 0.45f);
private final Vector3 sunDirection = new Vector3(-0.4f, -0.8f, -0.3f).nor();
```

Add this import at the top of the file:

```java
import com.badlogic.gdx.math.Vector3;
```

(Note: `Vector3` is already imported.)

Run: `gradlew.bat :core:compileJava -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(fog): add distance fog to terrain shader"
```

---

### Task 5: GameScreen Integration — Fog in Ship Shader

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Add fog uniforms and world-position varying to the ship shader**

Replace the `getShipShader()` method with a version that adds fog. Replace the existing method (lines 455-497) with:

```java
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
        "varying vec3 v_worldPos;\n" +
        "void main() {\n" +
        "    vec4 worldPos = u_worldTrans * vec4(a_position, 1.0);\n" +
        "    v_worldPos = worldPos.xyz;\n" +
        "    v_normal = normalize((u_worldTrans * vec4(a_normal, 0.0)).xyz);\n" +
        "    v_color = a_color;\n" +
        "    v_emissive = a_emissive;\n" +
        "    gl_Position = u_projViewTrans * worldPos;\n" +
        "}\n";

    String frag =
        "#ifdef GL_ES\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "varying vec3 v_normal;\n" +
        "varying vec4 v_color;\n" +
        "varying float v_emissive;\n" +
        "varying vec3 v_worldPos;\n" +
        "uniform vec3 u_lightDir;\n" +
        "uniform vec4 u_ambientColor;\n" +
        "uniform vec3 u_cameraPos;\n" +
        "uniform float u_fogDensity;\n" +
        "uniform vec3 u_fogColor;\n" +
        "void main() {\n" +
        "    vec3 lightDir = normalize(-u_lightDir);\n" +
        "    float diff = max(dot(v_normal, lightDir), 0.0);\n" +
        "    vec3 lit = v_color.rgb * (u_ambientColor.rgb + diff * vec3(0.8, 0.8, 0.75));\n" +
        "    vec3 baseColor = mix(lit, v_color.rgb * 2.0, v_emissive);\n" +
        "    float dist = length(v_worldPos - u_cameraPos);\n" +
        "    float fogFactor = exp(-u_fogDensity * dist * u_fogDensity * dist);\n" +
        "    fogFactor = clamp(fogFactor, 0.0, 1.0);\n" +
        "    vec3 color = mix(u_fogColor, baseColor, fogFactor);\n" +
        "    gl_FragColor = vec4(color, 1.0);\n" +
        "}\n";

    shipShader = new ShaderProgram(vert, frag);
    if (!shipShader.isCompiled()) {
        Gdx.app.error("ShipShader", shipShader.getLog());
    }
    return shipShader;
}
```

- [ ] **Step 2: Set fog uniforms in renderShips()**

Replace the `renderShips()` method with:

```java
private void renderShips() {
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
    Gdx.gl.glDepthMask(true);

    ShaderProgram shader = getShipShader();
    shader.bind();
    shader.setUniformMatrix("u_projViewTrans", camera.combined);
    shader.setUniformf("u_lightDir", -0.4f, -0.8f, -0.3f);
    shader.setUniformf("u_ambientColor", 0.3f, 0.3f, 0.35f, 1f);
    shader.setUniformf("u_cameraPos", camera.position);
    shader.setUniformf("u_fogDensity", fogDensity);
    shader.setUniformf("u_fogColor", horizonColor);

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
```

- [ ] **Step 3: Verify compilation**

Run: `gradlew.bat :core:compileJava -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(fog): add distance fog to ship shader"
```

---

### Task 6: GameScreen Integration — Sky + FogShaderProvider + Render Order

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Add SkyRenderer and FogShaderProvider fields**

At the top of `GameScreen`, add these field declarations (after the existing `disposables` array, around line 91):

```java
private SkyRenderer skyRenderer;
private FogShaderProvider fogShaderProvider;
private ModelBatch fogModelBatch;
```

Add these imports at the top of the file (after the existing imports):

```java
import com.galacticodyssey.ui.SkyRenderer;
import com.galacticodyssey.ui.FogShaderProvider;
```

(Note: Since `GameScreen` is in the same `ui` package, these imports are not needed. Skip this sub-step if the compiler doesn't require them.)

- [ ] **Step 2: Create SkyRenderer and FogShaderProvider in initializeWorld()**

In the `initializeWorld()` method, after the `buildPauseMenu()` call at the end, add:

```java
skyRenderer = new SkyRenderer();
fogShaderProvider = new FogShaderProvider();
fogModelBatch = new ModelBatch(fogShaderProvider);
```

- [ ] **Step 3: Update render() with sky rendering and updated render order**

Replace the `render()` method with:

```java
@Override
public void render(float delta) {
    ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f, true);

    skyRenderer.render(camera, sunDirection);

    if (!paused) {
        float clampedDelta = Math.min(delta, 1f / 30f);
        gameWorld.update(clampedDelta);
    }

    if (!paused) {
        WorldPopulator.updateAnimals(populatedWorld, delta,
            heightmap, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH);
    }

    syncBoxTransforms();
    renderTerrain();
    renderBoxes();
    renderWorldObjects();
    renderShips();

    gameWorld.getDebugHudSystem().render(delta);

    if (paused) {
        pauseStage.act(delta);
        pauseStage.draw();
    }
}
```

- [ ] **Step 4: Update renderBoxes() to use fogModelBatch**

Replace `renderBoxes()` with:

```java
private void renderBoxes() {
    fogShaderProvider.setFogParams(fogDensity, horizonColor);
    fogModelBatch.begin(camera);
    for (int i = 0; i < boxInstances.size; i++) {
        fogModelBatch.render(boxInstances.get(i), environment);
    }
    fogModelBatch.end();
}
```

- [ ] **Step 5: Update renderWorldObjects() to use fogModelBatch**

Replace `renderWorldObjects()` with:

```java
private void renderWorldObjects() {
    Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    Gdx.gl.glDepthMask(true);

    fogShaderProvider.setFogParams(fogDensity, horizonColor);
    fogModelBatch.begin(camera);
    for (int i = 0; i < populatedWorld.treeInstances.size; i++) {
        fogModelBatch.render(populatedWorld.treeInstances.get(i), environment);
    }
    for (int i = 0; i < populatedWorld.rockInstances.size; i++) {
        fogModelBatch.render(populatedWorld.rockInstances.get(i), environment);
    }
    for (int i = 0; i < populatedWorld.grassInstances.size; i++) {
        fogModelBatch.render(populatedWorld.grassInstances.get(i), environment);
    }
    for (int i = 0; i < populatedWorld.animalInstances.size; i++) {
        fogModelBatch.render(populatedWorld.animalInstances.get(i), environment);
    }
    fogModelBatch.end();

    if (populatedWorld.waterInstance != null) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDepthMask(false);
        modelBatch.begin(camera);
        modelBatch.render(populatedWorld.waterInstance, environment);
        modelBatch.end();
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }
}
```

- [ ] **Step 6: Update dispose() to clean up new resources**

In the `dispose()` method, add cleanup for the new resources. Add these lines after the existing `modelBatch` disposal block:

```java
if (fogModelBatch != null) {
    fogModelBatch.dispose();
    fogModelBatch = null;
}
if (skyRenderer != null) {
    skyRenderer.dispose();
    skyRenderer = null;
}
```

The `fogShaderProvider` is disposed by `fogModelBatch.dispose()` (ModelBatch disposes its ShaderProvider), so no separate disposal is needed.

- [ ] **Step 7: Verify compilation**

Run: `gradlew.bat :core:compileJava -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Run the game and verify visuals**

Run: `gradlew.bat :desktop:run`

Expected visual results:
- **Sky:** Blue gradient from dark zenith to warm horizon, visible sun disc in the direction (−0.4, −0.8, −0.3) normalized, warm haze at the horizon line
- **Fog:** Distant terrain, trees, rocks, grass, animals, boxes, and ships fade smoothly into the horizon color — no hard cutoff at terrain edges
- **Terrain colors:** Biomes have visible noise variation (no more flat single-color patches), edges between biomes transition softly, steep slopes show grey-brown rock, high peaks have snow caps
- **Seamless transition:** The fog color matches the sky's horizon color exactly, so distant terrain fades directly into the sky with no visible seam

Walk around and check:
1. Close objects are fully visible, distant objects fade to the horizon color
2. Turn to see the sky in all directions — gradient should be consistent
3. Look at biome boundaries — they should be soft, not hard edges
4. Find a steep mountainside — it should show rock color
5. Find high peaks — they should have snow

- [ ] **Step 9: Run all tests**

Run: `gradlew.bat :core:test -q`
Expected: All tests pass.

- [ ] **Step 10: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(visual): integrate sky, fog, and terrain color into GameScreen render pipeline"
```
