# Instanced Grass / Ground Cover (Cycle B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace ~500 cylinder grass `ModelInstance`s with a chunked, camera-centred GPU-instanced grass field that draws tens of thousands of wind-swaying, biome-coloured tufts in one instanced draw call.

**Architecture:** Grass is generated per fixed-size world cell (deterministically seeded by cell coords), only within a radius of the camera, decoupled from the terrain via a `TerrainSampler` interface. Active cells' per-instance data is packed into one dynamic instance buffer drawn via a single instanced call. A new `gbuffer_grass.vert` (instanced transform + wind + distance fade) pairs with the existing `gbuffer.frag` — no fragment/lighting changes.

**Tech Stack:** Java 17, libGDX 1.13.5 (`Mesh.enableInstancedRendering`, `VertexAttribute`, `ShaderProgram`, `JsonReader`, `Vector3`), the project's deferred `ShaderCache`, JUnit 5. Build: Gradle `:core`. New sub-package `com.galacticodyssey.flora.grass`.

**Canonical per-instance layout (stride 10 floats, in this exact order):**
`offsetX, offsetY, offsetZ, scaleXZ, scaleY, rotationY, windPhase, colorR, colorG, colorB`
mapped to instance vertex attributes `i_offset` (vec3), `i_params` (vec4 = scaleXZ, scaleY, rotationY, windPhase), `i_color` (vec3).

---

## File Structure

**Create (main):**
- `core/src/main/java/com/galacticodyssey/flora/grass/TerrainSampler.java` — interface.
- `core/src/main/java/com/galacticodyssey/flora/grass/HeightmapTerrainSampler.java` — adapter over current arrays.
- `core/src/main/java/com/galacticodyssey/flora/grass/GrassConfig.java` — globals + per-biome POJO.
- `core/src/main/java/com/galacticodyssey/flora/grass/GrassRegistry.java` — loads `grass.json`.
- `core/src/main/java/com/galacticodyssey/flora/grass/GrassCell.java` — GL-free instance generation.
- `core/src/main/java/com/galacticodyssey/flora/grass/GrassField.java` — active-cell set + cache + packed buffer.
- `core/src/main/java/com/galacticodyssey/flora/grass/GrassBladeMesh.java` — GL: base tuft mesh + instancing.
- `core/src/main/java/com/galacticodyssey/flora/grass/GrassRenderer.java` — GL: shader + instanced draw.
- `core/src/main/resources/shaders/gbuffer_grass.vert` — instanced vertex shader.
- `core/src/main/resources/data/flora/grass.json` — per-biome grass config.

**Create (tests):**
- `core/src/test/java/com/galacticodyssey/flora/grass/GrassRegistryTest.java`
- `core/src/test/java/com/galacticodyssey/flora/grass/HeightmapTerrainSamplerTest.java`
- `core/src/test/java/com/galacticodyssey/flora/grass/GrassCellTest.java`
- `core/src/test/java/com/galacticodyssey/flora/grass/GrassFieldTest.java`

**Modify:**
- `core/src/main/java/com/galacticodyssey/data/WorldPopulator.java` — remove `placeGrass`, `grassDensity`, `grassColorForBiome`, the `placeGrass` call, and the `grassInstances` field.
- `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` — build sampler/registry/field/renderer at world load; drive grass in the gbuffer pass; remove the `grassInstances` render loop.

---

## Task 1: GrassConfig + GrassRegistry

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/flora/grass/GrassConfig.java`
- Create: `core/src/main/java/com/galacticodyssey/flora/grass/GrassRegistry.java`
- Test: `core/src/test/java/com/galacticodyssey/flora/grass/GrassRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.flora.grass;

import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrassRegistryTest {
    private static final String JSON = "{ \"cellSize\": 32.0, \"radius\": 140.0, \"fadeBand\": 24.0," +
        "  \"baseTuftsPerM2\": 0.25, \"bladesPerTuft\": 3, \"maxCachedCells\": 256," +
        "  \"wind\": { \"amplitude\": 0.18, \"frequency\": 1.3 }," +
        "  \"biomes\": [" +
        "    { \"biome\": \"GRASSLAND\", \"density\": 1.0, \"height\": [0.5,1.1], \"colorA\": \"3a6b22\", \"colorB\": \"5a8a2e\" }," +
        "    { \"biome\": \"TUNDRA\", \"density\": 0.3, \"height\": [0.15,0.4], \"colorA\": \"4a5a3a\", \"colorB\": \"5a6545\" }" +
        "  ] }";

    @Test
    void loadsGlobalsAndBiomes() {
        GrassRegistry reg = new GrassRegistry();
        reg.loadFromJson(JSON);
        GrassConfig c = reg.config();
        assertEquals(32.0f, c.cellSize);
        assertEquals(140.0f, c.radius);
        assertEquals(0.25f, c.baseTuftsPerM2);
        assertEquals(3, c.bladesPerTuft);
        assertEquals(256, c.maxCachedCells);
        assertEquals(0.18f, c.windAmplitude);
        assertEquals(1.3f, c.windFrequency);

        GrassConfig.BiomeGrass g = c.forBiome(BiomeType.GRASSLAND);
        assertNotNull(g);
        assertEquals(1.0f, g.density);
        assertEquals(0.5f, g.heightMin);
        assertEquals(1.1f, g.heightMax);
        assertEquals(0x3a / 255f, g.colorAr, 0.01f);

        assertNull(c.forBiome(BiomeType.DESERT)); // not listed -> no grass
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.grass.GrassRegistryTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Implement GrassConfig**

```java
package com.galacticodyssey.flora.grass;

import com.galacticodyssey.planet.BiomeType;
import java.util.EnumMap;
import java.util.Map;

/** Global grass tuning + per-biome grass settings. Loaded from data/flora/grass.json. */
public class GrassConfig {
    public float cellSize = 32f;
    public float radius = 140f;
    public float fadeBand = 24f;
    public float baseTuftsPerM2 = 0.25f;
    public int bladesPerTuft = 3;
    public int maxCachedCells = 256;
    public float windAmplitude = 0.18f;
    public float windFrequency = 1.3f;

    /** Per-biome grass settings; colours stored as unpacked float channels. */
    public static class BiomeGrass {
        public float density;
        public float heightMin, heightMax;
        public float colorAr, colorAg, colorAb;
        public float colorBr, colorBg, colorBb;
    }

    private final Map<BiomeType, BiomeGrass> biomes = new EnumMap<>(BiomeType.class);

    public void put(BiomeType biome, BiomeGrass g) { biomes.put(biome, g); }

    /** Returns null when the biome has no grass. */
    public BiomeGrass forBiome(BiomeType biome) { return biomes.get(biome); }
}
```

- [ ] **Step 4: Implement GrassRegistry**

```java
package com.galacticodyssey.flora.grass;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.planet.BiomeType;

/** Loads the grass {@link GrassConfig} from JSON (data/flora/grass.json). */
public class GrassRegistry {
    private final GrassConfig config = new GrassConfig();

    public void load(String path) { loadFromJson(Gdx.files.internal(path).readString()); }

    public void loadFromJson(String json) {
        JsonValue root = new JsonReader().parse(json);
        config.cellSize = root.getFloat("cellSize", config.cellSize);
        config.radius = root.getFloat("radius", config.radius);
        config.fadeBand = root.getFloat("fadeBand", config.fadeBand);
        config.baseTuftsPerM2 = root.getFloat("baseTuftsPerM2", config.baseTuftsPerM2);
        config.bladesPerTuft = root.getInt("bladesPerTuft", config.bladesPerTuft);
        config.maxCachedCells = root.getInt("maxCachedCells", config.maxCachedCells);
        JsonValue wind = root.get("wind");
        if (wind != null) {
            config.windAmplitude = wind.getFloat("amplitude", config.windAmplitude);
            config.windFrequency = wind.getFloat("frequency", config.windFrequency);
        }
        JsonValue biomes = root.get("biomes");
        if (biomes != null) {
            for (JsonValue e = biomes.child; e != null; e = e.next) {
                BiomeType biome = BiomeType.valueOf(e.getString("biome"));
                GrassConfig.BiomeGrass g = new GrassConfig.BiomeGrass();
                g.density = e.getFloat("density", 0f);
                JsonValue h = e.get("height");
                g.heightMin = (h != null && h.size >= 2) ? h.getFloat(0) : 0.3f;
                g.heightMax = (h != null && h.size >= 2) ? h.getFloat(1) : 0.7f;
                Color a = color(e.getString("colorA", "3a6b22"));
                Color b = color(e.getString("colorB", "5a8a2e"));
                g.colorAr = a.r; g.colorAg = a.g; g.colorAb = a.b;
                g.colorBr = b.r; g.colorBg = b.g; g.colorBb = b.b;
                config.put(biome, g);
            }
        }
    }

    public GrassConfig config() { return config; }

    private static Color color(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 6) h = h + "ff";
        return Color.valueOf(h);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.grass.GrassRegistryTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/grass/GrassConfig.java core/src/main/java/com/galacticodyssey/flora/grass/GrassRegistry.java core/src/test/java/com/galacticodyssey/flora/grass/GrassRegistryTest.java
git commit -m "feat(grass): GrassConfig + GrassRegistry JSON loading"
```

---

## Task 2: TerrainSampler + HeightmapTerrainSampler

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/flora/grass/TerrainSampler.java`
- Create: `core/src/main/java/com/galacticodyssey/flora/grass/HeightmapTerrainSampler.java`
- Test: `core/src/test/java/com/galacticodyssey/flora/grass/HeightmapTerrainSamplerTest.java`

Context: `TerrainGenerator.getHeightAt(float[] heightmap, int vertsX, int vertsZ, float worldWidth, float worldDepth, float wx, float wz)` (static, in `com.galacticodyssey.data`) returns interpolated height. Biome grid index math mirrors `WorldPopulator`: `gx = clamp((wx + worldWidth/2)/worldWidth*(vertsX-1), 0, vertsX-1)`.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.flora.grass;

import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeightmapTerrainSamplerTest {
    @Test
    void heightAndBiomeMatchUnderlyingArrays() {
        int v = 3;
        float w = 100f, d = 100f;
        // 3x3 flat heightmap at y=2
        float[] hm = { 2,2,2, 2,2,2, 2,2,2 };
        BiomeType[] biomes = {
            BiomeType.OCEAN, BiomeType.OCEAN, BiomeType.OCEAN,
            BiomeType.OCEAN, BiomeType.GRASSLAND, BiomeType.OCEAN,
            BiomeType.OCEAN, BiomeType.OCEAN, BiomeType.OCEAN
        };
        HeightmapTerrainSampler s = new HeightmapTerrainSampler(hm, biomes, v, v, w, d);

        assertEquals(2f, s.heightAt(0f, 0f), 1e-4f);
        // centre cell (0,0 world) maps to grid centre -> GRASSLAND
        assertEquals(BiomeType.GRASSLAND, s.biomeAt(0f, 0f));
        // far corner clamps into range, stays OCEAN
        assertEquals(BiomeType.OCEAN, s.biomeAt(-49f, -49f));
        // way out of bounds clamps to edge (no exception)
        assertEquals(BiomeType.OCEAN, s.biomeAt(9999f, 9999f));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.grass.HeightmapTerrainSamplerTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Implement TerrainSampler**

```java
package com.galacticodyssey.flora.grass;

import com.galacticodyssey.planet.BiomeType;

/** Abstracts terrain queries so grass generation is independent of how terrain is stored.
 *  Today: a heightmap+biomeGrid adapter. Later: a streamed-terrain adapter (no grass changes). */
public interface TerrainSampler {
    float heightAt(float worldX, float worldZ);
    BiomeType biomeAt(float worldX, float worldZ);
}
```

- [ ] **Step 4: Implement HeightmapTerrainSampler**

```java
package com.galacticodyssey.flora.grass;

import com.galacticodyssey.data.TerrainGenerator;
import com.galacticodyssey.planet.BiomeType;

/** TerrainSampler backed by the current fixed heightmap + biome grid arrays. */
public final class HeightmapTerrainSampler implements TerrainSampler {
    private final float[] heightmap;
    private final BiomeType[] biomeGrid;
    private final int vertsX, vertsZ;
    private final float worldWidth, worldDepth;
    private final float halfW, halfD;

    public HeightmapTerrainSampler(float[] heightmap, BiomeType[] biomeGrid,
                                   int vertsX, int vertsZ, float worldWidth, float worldDepth) {
        this.heightmap = heightmap;
        this.biomeGrid = biomeGrid;
        this.vertsX = vertsX;
        this.vertsZ = vertsZ;
        this.worldWidth = worldWidth;
        this.worldDepth = worldDepth;
        this.halfW = worldWidth / 2f;
        this.halfD = worldDepth / 2f;
    }

    @Override
    public float heightAt(float worldX, float worldZ) {
        return TerrainGenerator.getHeightAt(heightmap, vertsX, vertsZ, worldWidth, worldDepth, worldX, worldZ);
    }

    @Override
    public BiomeType biomeAt(float worldX, float worldZ) {
        int gx = clamp((int) ((worldX + halfW) / worldWidth * (vertsX - 1)), 0, vertsX - 1);
        int gz = clamp((int) ((worldZ + halfD) / worldDepth * (vertsZ - 1)), 0, vertsZ - 1);
        return biomeGrid[gz * vertsX + gx];
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.grass.HeightmapTerrainSamplerTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/grass/TerrainSampler.java core/src/main/java/com/galacticodyssey/flora/grass/HeightmapTerrainSampler.java core/src/test/java/com/galacticodyssey/flora/grass/HeightmapTerrainSamplerTest.java
git commit -m "feat(grass): TerrainSampler abstraction + heightmap adapter"
```

---

## Task 3: GrassCell (GL-free instance generation)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/flora/grass/GrassCell.java`
- Test: `core/src/test/java/com/galacticodyssey/flora/grass/GrassCellTest.java`

Per-instance float order (stride 10): `offsetX, offsetY, offsetZ, scaleXZ, scaleY, rotationY, windPhase, colorR, colorG, colorB`.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.flora.grass;

import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrassCellTest {
    static final int STRIDE = 10;

    /** Flat terrain at y=5; biome is uniform (constructor-selected). */
    static class UniformSampler implements TerrainSampler {
        final BiomeType biome;
        UniformSampler(BiomeType b) { this.biome = b; }
        public float heightAt(float x, float z) { return 5f; }
        public BiomeType biomeAt(float x, float z) { return biome; }
    }

    static GrassConfig config() {
        GrassConfig c = new GrassConfig();
        c.cellSize = 32f; c.baseTuftsPerM2 = 0.25f;
        GrassConfig.BiomeGrass g = new GrassConfig.BiomeGrass();
        g.density = 1.0f; g.heightMin = 0.5f; g.heightMax = 1.0f;
        g.colorAr = 0.2f; g.colorAg = 0.4f; g.colorAb = 0.1f;
        g.colorBr = 0.3f; g.colorBg = 0.5f; g.colorBb = 0.2f;
        c.put(BiomeType.GRASSLAND, g);
        GrassConfig.BiomeGrass t = new GrassConfig.BiomeGrass();
        t.density = 0.3f; t.heightMin = 0.1f; t.heightMax = 0.3f;
        c.put(BiomeType.TUNDRA, t);
        return c;
    }

    @Test
    void deterministicForSameCellAndSeed() {
        GrassConfig c = config();
        float[] a = GrassCell.generate(0, 0, c, new UniformSampler(BiomeType.GRASSLAND), 999L);
        float[] b = GrassCell.generate(0, 0, c, new UniformSampler(BiomeType.GRASSLAND), 999L);
        assertArrayEquals(a, b);
        assertTrue(a.length % STRIDE == 0);
        assertTrue(a.length > 0);
    }

    @Test
    void densityScalesCountGrasslandFullTundraPartialDesertEmpty() {
        GrassConfig c = config();
        int grassland = GrassCell.generate(1, 1, c, new UniformSampler(BiomeType.GRASSLAND), 7L).length / STRIDE;
        int tundra = GrassCell.generate(1, 1, c, new UniformSampler(BiomeType.TUNDRA), 7L).length / STRIDE;
        int desert = GrassCell.generate(1, 1, c, new UniformSampler(BiomeType.DESERT), 7L).length / STRIDE;
        assertEquals(0, desert, "unlisted biome -> no grass");
        assertTrue(grassland > tundra, "density 1.0 keeps more than 0.3");
        assertTrue(tundra > 0, "density 0.3 keeps some");
        // density 1.0 keeps every candidate: count == round(cellArea * baseTuftsPerM2)
        assertEquals(Math.round(32f * 32f * 0.25f), grassland);
    }

    @Test
    void offsetsWithinCellAndSnappedToHeight() {
        GrassConfig c = config();
        int cx = 2, cz = -1;
        float[] data = GrassCell.generate(cx, cz, c, new UniformSampler(BiomeType.GRASSLAND), 5L);
        float originX = cx * c.cellSize, originZ = cz * c.cellSize;
        for (int i = 0; i < data.length; i += STRIDE) {
            float ox = data[i], oy = data[i + 1], oz = data[i + 2];
            assertTrue(ox >= originX && ox <= originX + c.cellSize, "x in cell");
            assertTrue(oz >= originZ && oz <= originZ + c.cellSize, "z in cell");
            assertEquals(5f, oy, 1e-4f, "snapped to terrain height");
            float scaleY = data[i + 4];
            assertTrue(scaleY >= 0.5f && scaleY <= 1.0f, "height in biome range");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.grass.GrassCellTest"`
Expected: FAIL — `GrassCell` not defined.

- [ ] **Step 3: Implement GrassCell**

```java
package com.galacticodyssey.flora.grass;

import com.badlogic.gdx.utils.FloatArray;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeType;

import java.util.Random;

/** GL-free per-cell grass instance generation. Deterministic from (cellX, cellZ, grassSeed). */
public final class GrassCell {
    public static final int STRIDE = 10;

    private GrassCell() {}

    /** Returns packed instance data (length = tuftCount * STRIDE). May be empty. */
    public static float[] generate(int cellX, int cellZ, GrassConfig config,
                                   TerrainSampler sampler, long grassSeed) {
        Random rng = new Random(SeedDeriver.forChunk(grassSeed, cellX, cellZ));
        float cell = config.cellSize;
        float originX = cellX * cell, originZ = cellZ * cell;
        int candidates = Math.round(cell * cell * config.baseTuftsPerM2);

        FloatArray out = new FloatArray(candidates * STRIDE);
        for (int i = 0; i < candidates; i++) {
            float wx = originX + rng.nextFloat() * cell;
            float wz = originZ + rng.nextFloat() * cell;
            BiomeType biome = sampler.biomeAt(wx, wz);
            GrassConfig.BiomeGrass g = config.forBiome(biome);
            if (g == null) continue;
            if (rng.nextFloat() >= g.density) continue;

            float scaleY = g.heightMin + rng.nextFloat() * (g.heightMax - g.heightMin);
            float scaleXZ = 0.8f + rng.nextFloat() * 0.4f;
            float rotationY = rng.nextFloat() * (float) (Math.PI * 2.0);
            float phase = rng.nextFloat() * (float) (Math.PI * 2.0);
            float t = rng.nextFloat();
            float r = lerp(g.colorAr, g.colorBr, t);
            float gg = lerp(g.colorAg, g.colorBg, t);
            float b = lerp(g.colorAb, g.colorBb, t);
            float oy = sampler.heightAt(wx, wz);

            out.add(wx); out.add(oy); out.add(wz);
            out.add(scaleXZ); out.add(scaleY); out.add(rotationY); out.add(phase);
            out.add(r); out.add(gg); out.add(b);
        }
        return out.toArray();
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.grass.GrassCellTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/grass/GrassCell.java core/src/test/java/com/galacticodyssey/flora/grass/GrassCellTest.java
git commit -m "feat(grass): GL-free deterministic per-cell instance generation"
```

---

## Task 4: GrassField (active set + cache + packed buffer)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/flora/grass/GrassField.java`
- Test: `core/src/test/java/com/galacticodyssey/flora/grass/GrassFieldTest.java`

Determinism salt: `grassSeed = SeedDeriver.forId(SeedDeriver.floraDomain(worldSeed), 0x6772617373L)` (no new `SeedDeriver` constant needed).

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.flora.grass;

import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrassFieldTest {
    static class UniformSampler implements TerrainSampler {
        public float heightAt(float x, float z) { return 0f; }
        public BiomeType biomeAt(float x, float z) { return BiomeType.GRASSLAND; }
    }

    static GrassField field() {
        GrassConfig c = new GrassConfig();
        c.cellSize = 32f; c.radius = 48f; c.baseTuftsPerM2 = 0.05f; c.maxCachedCells = 64;
        GrassConfig.BiomeGrass g = new GrassConfig.BiomeGrass();
        g.density = 1f; g.heightMin = 0.5f; g.heightMax = 1f;
        c.put(BiomeType.GRASSLAND, g);
        return new GrassField(c, new UniformSampler(), 123L);
    }

    @Test
    void activeSetIsRadiusDiscAndPackedBufferMatches() {
        GrassField f = field();
        boolean changed = f.update(0f, 0f);
        assertTrue(changed, "first update populates");
        assertTrue(f.instanceCount() > 0);
        assertEquals(f.instanceCount() * GrassCell.STRIDE, f.instanceBuffer().length,
            "packed buffer length == count * stride (effective length)");
    }

    @Test
    void noChangeWhenStayingInSameCellNeighbourhood() {
        GrassField f = field();
        f.update(0f, 0f);
        boolean changed = f.update(1f, 1f); // tiny move, same active disc
        assertFalse(changed, "active cell set unchanged -> no repack");
    }

    @Test
    void movingFarChangesActiveSet() {
        GrassField f = field();
        f.update(0f, 0f);
        boolean changed = f.update(500f, 500f);
        assertTrue(changed, "moving far changes the active cell set");
    }

    @Test
    void deterministicCacheReuse() {
        GrassField f = field();
        f.update(0f, 0f);
        float[] first = f.instanceBuffer().clone();
        int firstLen = f.instanceCount();
        f.update(500f, 500f);
        f.update(0f, 0f); // return -> cached cells reused, identical packing
        assertEquals(firstLen, f.instanceCount());
    }
}
```

> Note: `instanceBuffer()` may return an oversized backing array; `instanceCount()` is authoritative. The test asserts `count*STRIDE` equals the *effective* length — implement `instanceBuffer()` to return an exactly-sized array (use `FloatArray.toArray()` when packing) so `length == count*STRIDE` holds.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.grass.GrassFieldTest"`
Expected: FAIL — `GrassField` not defined.

- [ ] **Step 3: Implement GrassField**

```java
package com.galacticodyssey.flora.grass;

import com.badlogic.gdx.utils.FloatArray;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Manages the set of grass cells active around the camera, caches their instance data,
 *  and packs the active set into one contiguous instance buffer. GL-free. */
public final class GrassField {
    private static final long GRASS_SALT = 0x6772617373L; // "grass"

    private final GrassConfig config;
    private final TerrainSampler sampler;
    private final long grassSeed;

    /** LRU cache of cell key -> packed instance data. */
    private final LinkedHashMap<Long, float[]> cache;
    private Set<Long> activeKeys = new HashSet<>();
    private float[] packed = new float[0];
    private int instanceCount;

    public GrassField(GrassConfig config, TerrainSampler sampler, long worldSeed) {
        this.config = config;
        this.sampler = sampler;
        this.grassSeed = SeedDeriver.forId(SeedDeriver.floraDomain(worldSeed), GRASS_SALT);
        final int cap = Math.max(16, config.maxCachedCells);
        this.cache = new LinkedHashMap<>(cap, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, float[]> e) {
                return size() > cap;
            }
        };
    }

    /** Recomputes the active cell set for the camera position. Returns true if it changed
     *  (and the packed buffer was rebuilt), false if nothing changed. */
    public boolean update(float camX, float camZ) {
        Set<Long> newKeys = computeActiveKeys(camX, camZ);
        if (newKeys.equals(activeKeys)) return false;
        activeKeys = newKeys;
        repack();
        return true;
    }

    Set<Long> computeActiveKeys(float camX, float camZ) {
        float cell = config.cellSize, radius = config.radius;
        int minX = (int) Math.floor((camX - radius) / cell);
        int maxX = (int) Math.floor((camX + radius) / cell);
        int minZ = (int) Math.floor((camZ - radius) / cell);
        int maxZ = (int) Math.floor((camZ + radius) / cell);
        float r2 = radius * radius;
        Set<Long> keys = new HashSet<>();
        for (int cz = minZ; cz <= maxZ; cz++) {
            for (int cx = minX; cx <= maxX; cx++) {
                float centreX = (cx + 0.5f) * cell, centreZ = (cz + 0.5f) * cell;
                float dx = centreX - camX, dz = centreZ - camZ;
                if (dx * dx + dz * dz <= r2) keys.add(key(cx, cz));
            }
        }
        return keys;
    }

    private void repack() {
        FloatArray buf = new FloatArray();
        for (Long k : activeKeys) {
            float[] cellData = cache.get(k);
            if (cellData == null) {
                cellData = GrassCell.generate(cellX(k), cellZ(k), config, sampler, grassSeed);
                cache.put(k, cellData);
            }
            buf.addAll(cellData);
        }
        packed = buf.toArray();
        instanceCount = packed.length / GrassCell.STRIDE;
    }

    public float[] instanceBuffer() { return packed; }
    public int instanceCount() { return instanceCount; }

    // --- cell key packing (two ints into a long) ---
    private static long key(int cx, int cz) { return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL); }
    private static int cellX(long k) { return (int) (k >> 32); }
    private static int cellZ(long k) { return (int) k; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.grass.GrassFieldTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/grass/GrassField.java core/src/test/java/com/galacticodyssey/flora/grass/GrassFieldTest.java
git commit -m "feat(grass): camera-centred chunked grass field with LRU cache"
```

---

## Task 5: grass.json data file

**Files:**
- Create: `core/src/main/resources/data/flora/grass.json`

- [ ] **Step 1: Create the file**

```json
{
  "cellSize": 32.0, "radius": 140.0, "fadeBand": 24.0,
  "baseTuftsPerM2": 0.25, "bladesPerTuft": 3, "maxCachedCells": 256,
  "wind": { "amplitude": 0.18, "frequency": 1.3 },
  "biomes": [
    { "biome": "GRASSLAND", "density": 1.0,  "height": [0.5, 1.1], "colorA": "3a6b22", "colorB": "5a8a2e" },
    { "biome": "SAVANNA",   "density": 0.7,  "height": [0.6, 1.3], "colorA": "6a6b2a", "colorB": "8a7a30" },
    { "biome": "TEMPERATE_FOREST", "density": 0.5, "height": [0.3, 0.7], "colorA": "2f6b22", "colorB": "47852c" },
    { "biome": "STEPPE",    "density": 0.45, "height": [0.3, 0.7], "colorA": "6a6535", "colorB": "7a7240" },
    { "biome": "SWAMP",     "density": 0.4,  "height": [0.4, 0.9], "colorA": "20401a", "colorB": "356025" },
    { "biome": "TROPICAL_FOREST", "density": 0.35, "height": [0.4, 0.9], "colorA": "1f6b1a", "colorB": "2f8a24" },
    { "biome": "BOREAL_FOREST", "density": 0.3, "height": [0.2, 0.5], "colorA": "3a5a32", "colorB": "4a6a3a" },
    { "biome": "TUNDRA",    "density": 0.3,  "height": [0.15, 0.4], "colorA": "4a5a3a", "colorB": "5a6545" }
  ]
}
```

- [ ] **Step 2: Validate JSON parses**

Run: `python -c "import json; json.load(open('core/src/main/resources/data/flora/grass.json')); print('OK')"`
Expected: `OK` (if python unavailable, skip — Task 8 loads it through libGDX).

- [ ] **Step 3: Commit**

```bash
git add core/src/main/resources/data/flora/grass.json
git commit -m "feat(grass): per-biome grass configuration data"
```

---

## Task 6: gbuffer_grass.vert instanced shader

**Files:**
- Create: `core/src/main/resources/shaders/gbuffer_grass.vert`

This pairs with the existing `gbuffer.frag` (compiled with `HAS_VERTEX_COLOR`). It must emit the same varyings `gbuffer.frag` reads: `v_viewPos` (world position — matches how `gbuffer.vert`/terrain populate it), `v_viewNormal` (view-space normal), and `v_color`. GL-bound; verified visually in Task 8.

- [ ] **Step 1: Create the shader**

```glsl
#version 330

in vec3 a_position;   // base blade vertex (unit height 1.0, centred at origin base)
in vec3 a_normal;

in vec3 i_offset;     // per-instance world position (base of tuft)
in vec4 i_params;     // scaleXZ, scaleY, rotationY, windPhase
in vec3 i_color;

uniform mat4 u_projViewTrans;
uniform mat3 u_normalMatrix;   // inverse-transpose of the view matrix (as renderTerrain sets)
uniform float u_time;
uniform vec3 u_camPos;
uniform float u_fadeRadius;
uniform float u_fadeBand;
uniform float u_windAmp;
uniform float u_windFreq;

out vec3 v_viewPos;
out vec3 v_viewNormal;
out vec4 v_color;

void main() {
    float scaleXZ = i_params.x;
    float scaleY  = i_params.y;
    float rot     = i_params.z;
    float phase   = i_params.w;

    // distance fade: shrink height to zero near the radius edge
    float dist = distance(u_camPos, i_offset);
    float fade = clamp((u_fadeRadius - dist) / max(u_fadeBand, 0.001), 0.0, 1.0);
    scaleY *= fade;

    // yaw rotation around Y
    float c = cos(rot), s = sin(rot);
    vec3 p = a_position;
    vec3 scaled = vec3(p.x * scaleXZ, p.y * scaleY, p.z * scaleXZ);
    vec3 rotated = vec3(scaled.x * c + scaled.z * s, scaled.y, -scaled.x * s + scaled.z * c);
    vec3 worldPos = i_offset + rotated;

    // wind: displace tips (height factor squared so base stays planted)
    float hf = a_position.y; // 0 at base, 1 at tip (unit blade)
    float sway = u_windAmp * sin(u_time * u_windFreq + phase + i_offset.x * 0.15) * hf * hf * scaleY;
    worldPos.x += sway;
    worldPos.z += sway * 0.5;

    // rotate the normal by the same yaw, then to view space
    vec3 n = a_normal;
    vec3 nRot = vec3(n.x * c + n.z * s, n.y, -n.x * s + n.z * c);
    v_viewNormal = normalize(u_normalMatrix * nRot);

    v_viewPos = worldPos;
    v_color = vec4(i_color, 1.0);
    gl_Position = u_projViewTrans * vec4(worldPos, 1.0);
}
```

- [ ] **Step 2: Commit** (compilation is verified in Task 8 when first loaded)

```bash
git add core/src/main/resources/shaders/gbuffer_grass.vert
git commit -m "feat(grass): instanced gbuffer vertex shader with wind + distance fade"
```

---

## Task 7: GrassBladeMesh + GrassRenderer (GL instanced)

GL-bound — no unit test. Compile-only here; behaviour verified visually in Task 8.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/flora/grass/GrassBladeMesh.java`
- Create: `core/src/main/java/com/galacticodyssey/flora/grass/GrassRenderer.java`

**IMPORTANT — libGDX instancing API:** this is the one genuinely new rendering primitive in the codebase (no existing usage). Verify the exact signatures against the libGDX 1.13.5 `Mesh` class on the classpath before assuming. The expected API:
- `mesh.enableInstancedRendering(boolean isStatic, int maxInstances, VertexAttribute... attrs)`
- instance `VertexAttribute`s must have **distinct (usage, unit)** pairs or `VertexAttributes` throws "two attributes with the same usage" — use `new VertexAttribute(Usage.Generic, n, alias, unit)` with units 0/1/2.
- `mesh.setInstanceData(float[] data, int offset, int count)` uploads `count` floats.
- `mesh.render(shader, GL20.GL_TRIANGLES)` issues the instanced draw when instancing is enabled.
If any signature differs, adapt minimally and report it.

- [ ] **Step 1: Implement GrassBladeMesh**

```java
package com.galacticodyssey.flora.grass;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;

/** Builds the shared base tuft mesh (a few crossed blades, unit height) with instanced
 *  attributes enabled. One mesh, reused for every instance. */
public final class GrassBladeMesh {
    private GrassBladeMesh() {}

    /** Creates a tuft of {@code blades} crossed quads. Base at y=0, tip at y=1, width ~0.1.
     *  Vertex layout: position(3) + normal(3). Instanced attrs: i_offset(3), i_params(4), i_color(3). */
    public static Mesh create(int blades, int maxInstances) {
        int vertsPerBlade = 4;       // quad
        int idxPerBlade = 6;
        float[] verts = new float[blades * vertsPerBlade * 6];
        short[] idx = new short[blades * idxPerBlade];
        float halfW = 0.05f;

        int vo = 0, io = 0;
        for (int b = 0; b < blades; b++) {
            double ang = Math.PI * b / blades; // spread blades around Y
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            // quad corners: bottom-left, bottom-right, top-right, top-left (top narrows slightly)
            float[][] corners = {
                { -halfW * c, 0f, -halfW * s }, {  halfW * c, 0f,  halfW * s },
                {  halfW * c * 0.4f, 1f,  halfW * s * 0.4f }, { -halfW * c * 0.4f, 1f, -halfW * s * 0.4f }
            };
            // face normal ~ perpendicular to the blade plane (in XZ), pointing up-ish
            float nx = -s, nz = c;
            int base = vo / 6;
            for (float[] p : corners) {
                verts[vo++] = p[0]; verts[vo++] = p[1]; verts[vo++] = p[2];
                verts[vo++] = nx;   verts[vo++] = 0.4f; verts[vo++] = nz;
            }
            idx[io++] = (short) base;       idx[io++] = (short) (base + 1); idx[io++] = (short) (base + 2);
            idx[io++] = (short) base;       idx[io++] = (short) (base + 2); idx[io++] = (short) (base + 3);
        }

        Mesh mesh = new Mesh(true, verts.length / 6, idx.length,
            new VertexAttribute(Usage.Position, 3, "a_position"),
            new VertexAttribute(Usage.Normal, 3, "a_normal"));
        mesh.setVertices(verts);
        mesh.setIndices(idx);

        mesh.enableInstancedRendering(false, maxInstances,
            new VertexAttribute(Usage.Generic, 3, "i_offset", 0),
            new VertexAttribute(Usage.Generic, 4, "i_params", 1),
            new VertexAttribute(Usage.Generic, 3, "i_color", 2));
        return mesh;
    }
}
```

- [ ] **Step 2: Implement GrassRenderer**

```java
package com.galacticodyssey.flora.grass;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.rendering.shaders.ShaderCache;

/** Owns the instanced grass mesh + shader and draws the active grass field in one call.
 *  Invoked inside the deferred gbuffer pass (alongside terrain). */
public final class GrassRenderer implements Disposable {
    private final ShaderCache shaderCache;
    private final GrassConfig config;
    private final Mesh mesh;
    private final int maxInstances;
    private final Matrix4 tmpView = new Matrix4();
    private final Matrix3 normalMat = new Matrix3();
    private int instanceCount;

    public GrassRenderer(ShaderCache shaderCache, GrassConfig config, int maxInstances) {
        this.shaderCache = shaderCache;
        this.config = config;
        this.maxInstances = maxInstances;
        this.mesh = GrassBladeMesh.create(config.bladesPerTuft, maxInstances);
    }

    /** Upload a new packed instance buffer (call only when the field changed). */
    public void setInstances(float[] packed, int count) {
        instanceCount = Math.min(count, maxInstances);
        if (instanceCount > 0) {
            mesh.setInstanceData(packed, 0, instanceCount * GrassCell.STRIDE);
        }
    }

    /** Render the grass field into the gbuffer. Call inside the gbuffer pass. */
    public void render(Camera camera, float time) {
        if (instanceCount <= 0) return;
        Gdx_glDepthSetup();

        ShaderProgram shader = shaderCache.get("gbuffer_grass.vert", "gbuffer.frag", "HAS_VERTEX_COLOR");
        shader.bind();
        shader.setUniformMatrix("u_projViewTrans", camera.combined);

        tmpView.set(camera.view);
        normalMat.set(tmpView).inv().transpose();
        shader.setUniformMatrix("u_normalMatrix", normalMat);

        shader.setUniformf("u_time", time);
        shader.setUniformf("u_camPos", camera.position.x, camera.position.y, camera.position.z);
        shader.setUniformf("u_fadeRadius", config.radius);
        shader.setUniformf("u_fadeBand", config.fadeBand);
        shader.setUniformf("u_windAmp", config.windAmplitude);
        shader.setUniformf("u_windFreq", config.windFrequency);

        // gbuffer.frag material uniforms (match renderTerrain)
        shader.setUniformf("u_albedoTint", 1f, 1f, 1f, 1f);
        shader.setUniformf("u_metallicScale", 0f);
        shader.setUniformf("u_roughnessScale", 0.9f);
        shader.setUniformf("u_emissiveIntensity", 0f);
        shader.setUniformf("u_tiling", 1f, 1f);

        mesh.render(shader, GL20.GL_TRIANGLES);
    }

    private static void Gdx_glDepthSetup() {
        com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        com.badlogic.gdx.Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        com.badlogic.gdx.Gdx.gl.glDepthMask(true);
    }

    @Override
    public void dispose() { mesh.dispose(); }
}
```

> The grass mesh has no per-face culling guarantees; if blades render single-sided/black, disable back-face culling for the grass draw (`Gdx.gl.glDisable(GL20.GL_CULL_FACE)`) in `render()` — decide during the Task 8 visual check.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL. If instancing API signatures differ, adapt and note it.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/grass/GrassBladeMesh.java core/src/main/java/com/galacticodyssey/flora/grass/GrassRenderer.java
git commit -m "feat(grass): instanced grass blade mesh + renderer"
```

---

## Task 8: Integrate into GameScreen + remove old grass + visual verify

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/data/WorldPopulator.java`
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Remove the old cylinder grass from WorldPopulator**

In `WorldPopulator.java`:
- Delete the `placeGrass(...)` call inside `populate(...)`.
- Delete the methods `placeGrass`, `grassDensity`, `grassColorForBiome` entirely.
- Remove the `public final Array<ModelInstance> grassInstances = new Array<>();` field from `PopulatedWorld`.
- After deletion, grep `grassInstances`, `placeGrass`, `grassDensity`, `grassColorForBiome` across `core/src` — fix any remaining reference (the only other one is the GameScreen render loop handled in Step 3).

- [ ] **Step 2: Add grass fields + world-load wiring to GameScreen**

Add imports:
```java
import com.galacticodyssey.flora.grass.GrassField;
import com.galacticodyssey.flora.grass.GrassRenderer;
import com.galacticodyssey.flora.grass.GrassRegistry;
import com.galacticodyssey.flora.grass.HeightmapTerrainSampler;
```
Add fields near the other renderer fields:
```java
    private GrassField grassField;
    private GrassRenderer grassRenderer;
    private static final int GRASS_MAX_INSTANCES = 200_000;
```
Where `populatedWorld` is created at world load (right after the `WorldPopulator.populate(...)` call, ~line 235), add:
```java
        GrassRegistry grassRegistry = new GrassRegistry();
        grassRegistry.load("data/flora/grass.json");
        HeightmapTerrainSampler grassSampler = new HeightmapTerrainSampler(
            heightmap, populatedWorld.biomeGrid, TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH);
        grassField = new GrassField(grassRegistry.config(), grassSampler, TERRAIN_SEED);
        grassRenderer = new GrassRenderer(deferredRenderer.getShaderCache(), grassRegistry.config(), GRASS_MAX_INSTANCES);
```

- [ ] **Step 3: Drive grass in the frame + gbuffer pass**

In `render(float delta)`, before the `deferredRenderer.render(...)` call, update the field:
```java
        if (grassField != null && grassField.update(camera.position.x, camera.position.z)) {
            grassRenderer.setInstances(grassField.instanceBuffer(), grassField.instanceCount());
        }
```
Inside the gbuffer callback (the lambda that calls `renderTerrain()` etc.), add a grass call after `renderTerrain()`:
```java
            () -> {
                renderTerrain();
                if (grassRenderer != null) grassRenderer.render(camera, gameTime);
                renderBoxes();
                renderWorldObjects();
                renderShips();
            },
```
In `renderWorldObjects()`, delete the loop that iterates `populatedWorld.grassInstances` (the grass render block). Leave trees/rocks/animals loops intact.

- [ ] **Step 4: Dispose grass renderer**

In `GameScreen.dispose()` (or `hide()` where other renderers are disposed), add:
```java
        if (grassRenderer != null) { grassRenderer.dispose(); grassRenderer = null; }
```

- [ ] **Step 5: Compile + run the full core test suite**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL (flora/grass tests pass; pre-existing unrelated `OrbitalMechanicsIntegrationTest` failures + gdx-bullet native crash may remain — confirm your change didn't add new failures).

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/WorldPopulator.java core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(grass): wire instanced grass field into GameScreen; remove cylinder grass"
```

- [ ] **Step 7: Visual verification (run the game)**

Use the `run-galactic-odyssey` skill: build, launch, screenshot the surface. Confirm:
- Dense grass tufts cover grassland/savanna; sparse/none in desert/rock/water; biome-appropriate colour.
- Grass sways subtly (take two screenshots a moment apart, or observe motion).
- Grass fades (doesn't hard-pop) at the view distance; moving forward reveals new grass without stutter.
- No crash; FPS reasonable (one instanced draw call for all grass).
- If blades look black/inside-out: disable face culling for the grass draw (see note in Task 7) and/or flip the blade normal; rebuild and re-check.

---

## Task 9: Update memory + final review

- [ ] **Step 1: Full test sweep**

Run: `./gradlew :core:test` — confirm grass tests pass; no new failures vs the known pre-existing ones.

- [ ] **Step 2: Update memory**

Edit `C:\Users\james\.claude\projects\C--Users-james-IdeaProjects-SpaceGame\memory\project_flora-generation.md`: mark Cycle B DONE (chunked camera-centred GPU-instanced grass, `TerrainSampler` seam for streaming, one draw call, wind+fade shader, data-driven `grass.json`). Update the `MEMORY.md` index line.

- [ ] **Step 3: Final whole-cycle code review**

Dispatch a final reviewer over the Cycle B diff (determinism, the `TerrainSampler` seam, disposal, one-draw-call claim, instancing correctness). Address any Critical/Important findings.

---

## Self-Review

**Spec coverage:**
- Chunked camera-centred field → Tasks 3, 4. ✓
- `TerrainSampler` decoupling seam → Task 2. ✓
- Single instanced draw call + packed dynamic buffer → Tasks 4 (pack), 7 (draw). ✓
- Deterministic per-cell seeding (`forChunk` off `FLORA_DOMAIN`) → Tasks 3, 4. ✓
- Wind sway + distance fade in `gbuffer_grass.vert`, pairs with `gbuffer.frag` → Task 6. ✓
- Data-driven per-biome `grass.json` → Tasks 1, 5. ✓
- Instance attribute layout (offset/params/color) → header + Tasks 3, 6, 7 (consistent order). ✓
- Remove old cylinder grass, integrate in gbuffer pass → Task 8. ✓
- Disposal ownership (renderer owns mesh/shader) → Task 7/8. ✓
- GL-free core unit-tested; GL verified visually → Tasks 1-4 (tests) vs 6-8 (visual). ✓
- Out-of-scope (textured/LOD billboards, collision, non-tuft props) → not built. ✓

**Placeholder scan:** No TBD/“handle edge cases”. The one staged marker is the libGDX-instancing verification note in Task 7 (explicit, actionable). ✓

**Type consistency:** `GrassConfig.forBiome`/`BiomeGrass` fields, `GrassRegistry.config()/loadFromJson`, `TerrainSampler.heightAt/biomeAt`, `GrassCell.generate(int,int,GrassConfig,TerrainSampler,long)→float[]` + `STRIDE=10`, `GrassField(GrassConfig,TerrainSampler,long)`/`update/instanceBuffer/instanceCount`, `GrassBladeMesh.create(blades,maxInstances)`, `GrassRenderer(ShaderCache,GrassConfig,maxInstances)`/`setInstances(float[],int)`/`render(Camera,float)`/`dispose()` — all consistent across tasks and with the shader attribute names (`i_offset`/`i_params`/`i_color`). The per-instance float order (offset, scaleXZ, scaleY, rotationY, windPhase, color) is identical in the header, `GrassCell`, and the shader's `i_params` unpacking. ✓
