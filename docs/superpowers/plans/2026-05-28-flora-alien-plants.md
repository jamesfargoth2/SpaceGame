# Modular Alien Plants (Cycle C) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Procedural alien plants in three archetypes (bioluminescent / carnivorous / crystal) from a simple stalk→canopy→details assembly, placed via the Cycle A flora pipeline, with glowing parts self-emissive through a dedicated emissive render pass.

**Architecture:** New sub-package `com.galacticodyssey.flora.alien`. A GL-free `AlienPlantMeshBuilder` emits interleaved pos3+normal3+color4+emissive1 (stride 11) per archetype; `AlienPlantModelFactory` uploads it to a `Model` with a custom `a_emissive` vertex attribute; a dedicated `EmissiveGBufferShader` (gbuffer.vert/frag + HAS_VERTEX_COLOR + HAS_EMISSIVE_ATTRIB) makes glowing parts bloom. Placement reuses the Cycle A seeded biome-gated prototype-pool pattern into a new `PopulatedWorld.alienInstances` list rendered with a second ModelBatch in the gbuffer pass.

**Tech Stack:** Java 17, libGDX 1.13.5 (`Mesh`, `ModelBuilder`, `VertexAttribute`, `ModelBatch`, `Shader`/`ShaderProvider`, `Vector3`, `BoundingBox`, `JsonReader`), the deferred `ShaderCache`, JUnit 5. Build: Gradle `:core`.

**Per-vertex float order (stride 11):** `posX,posY,posZ, nX,nY,nZ, colR,colG,colB,colA, emissive` — vertex attributes `Position(3)`, `Normal(3)`, `ColorUnpacked(4, alias "a_color")`, `Generic(1, alias "a_emissive")`, declared in that order.

**Reuse:** `com.galacticodyssey.flora.data.BiomePalette` (biome→weighted species + density) is reused as-is for the alien palette. `SeedDeriver` (galaxy) for determinism.

---

## File Structure

**Create (main):**
- `core/src/main/java/com/galacticodyssey/flora/alien/AlienArchetype.java`
- `core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantSpecies.java`
- `core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantRegistry.java`
- `core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantMeshData.java`
- `core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantMeshBuilder.java`
- `core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantModelFactory.java`
- `core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantPlacement.java`
- `core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantGenerator.java`
- `core/src/main/java/com/galacticodyssey/rendering/EmissiveGBufferShader.java`
- `core/src/main/java/com/galacticodyssey/rendering/EmissiveGBufferShaderProvider.java`
- `core/src/main/resources/data/flora/alien_plants.json`

**Create (tests):**
- `core/src/test/java/com/galacticodyssey/flora/alien/AlienPlantRegistryTest.java`
- `core/src/test/java/com/galacticodyssey/flora/alien/AlienPlantMeshBuilderTest.java`
- `core/src/test/java/com/galacticodyssey/flora/alien/AlienPlantPlacementTest.java`

**Modify:**
- `core/src/main/java/com/galacticodyssey/data/WorldPopulator.java` — add `Array<ModelInstance> alienInstances` to `PopulatedWorld`.
- `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` — alien batch + world-load wiring + render in gbuffer pass + dispose.

---

## Task 1: AlienArchetype + AlienPlantSpecies + AlienPlantRegistry

**Files:**
- Create: `AlienArchetype.java`, `AlienPlantSpecies.java`, `AlienPlantRegistry.java` (in `flora/alien/`)
- Test: `AlienPlantRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.flora.alien;

import com.galacticodyssey.flora.data.BiomePalette;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlienPlantRegistryTest {
    private static final String JSON = "{ \"species\": [\n" +
      "  { \"id\": \"glowcap\", \"archetype\": \"BIOLUMINESCENT\",\n" +
      "    \"stalk\": { \"height\": [1.5,3.0], \"baseRadius\": 0.12, \"taper\": 0.7, \"sides\": 6, \"color\": \"2a2f4a\" },\n" +
      "    \"canopy\": { \"clumps\": [3,6], \"radius\": [0.4,0.8], \"color\": \"2fd0c0\", \"emissive\": 3.0 },\n" +
      "    \"details\": { \"count\": [3,8], \"emissive\": 2.0 }, \"prototypeVariants\": 6 },\n" +
      "  { \"id\": \"maw\", \"archetype\": \"CARNIVOROUS\",\n" +
      "    \"stalk\": { \"height\": [0.8,1.6], \"baseRadius\": 0.15, \"taper\": 0.85, \"sides\": 6, \"color\": \"3a2a1f\" },\n" +
      "    \"canopy\": { \"mouthRadius\": [0.5,0.9], \"depth\": [0.6,1.1], \"color\": \"5a1f28\", \"lureEmissive\": 2.5 },\n" +
      "    \"details\": { \"teeth\": [5,9] }, \"prototypeVariants\": 5 },\n" +
      "  { \"id\": \"shard\", \"archetype\": \"CRYSTAL\",\n" +
      "    \"stalk\": { \"height\": [0.4,1.0], \"baseRadius\": 0.2, \"taper\": 0.9, \"sides\": 5, \"color\": \"404a6a\" },\n" +
      "    \"canopy\": { \"shards\": [4,8], \"length\": [0.6,1.6], \"color\": \"8ad0ff\", \"emissive\": 0.6 },\n" +
      "    \"details\": { \"subShards\": [2,5] }, \"prototypeVariants\": 6 }\n" +
      "] }";
    private static final String PALETTE = "{ \"palette\": [\n" +
      "  { \"biome\": \"SWAMP\", \"density\": 0.5, \"species\": [ {\"id\":\"glowcap\",\"weight\":0.7}, {\"id\":\"maw\",\"weight\":0.3} ] },\n" +
      "  { \"biome\": \"VOLCANIC\", \"density\": 0.3, \"species\": [ {\"id\":\"shard\",\"weight\":1.0} ] }\n" +
      "] }";

    @Test
    void loadsSpeciesAllArchetypes() {
        AlienPlantRegistry reg = new AlienPlantRegistry();
        reg.loadSpecies(JSON);
        AlienPlantSpecies g = reg.species("glowcap");
        assertEquals(AlienArchetype.BIOLUMINESCENT, g.archetype);
        assertEquals(1.5f, g.stalkHeightMin); assertEquals(3.0f, g.stalkHeightMax);
        assertEquals(6, g.stalkSides);
        assertEquals(3, g.clumpsMin); assertEquals(6, g.clumpsMax);
        assertEquals(3.0f, g.canopyEmissive);
        assertEquals(0x2f / 255f, g.canopyColor.r, 0.01f);
        assertEquals(6, g.prototypeVariants);

        assertEquals(AlienArchetype.CARNIVOROUS, reg.species("maw").archetype);
        assertEquals(2.5f, reg.species("maw").lureEmissive);
        assertEquals(5, reg.species("maw").teethMin);

        assertEquals(AlienArchetype.CRYSTAL, reg.species("shard").archetype);
        assertEquals(4, reg.species("shard").shardsMin);
        assertEquals(0.6f, reg.species("shard").canopyEmissive);

        assertNull(reg.species("nope"));
    }

    @Test
    void loadsPalette() {
        AlienPlantRegistry reg = new AlienPlantRegistry();
        reg.loadPalette(PALETTE);
        BiomePalette p = reg.palette(BiomeType.SWAMP);
        assertNotNull(p);
        assertEquals(0.5f, p.density);
        assertEquals(2, p.entries.size());
        assertNull(reg.palette(BiomeType.DESERT));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.alien.AlienPlantRegistryTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Implement AlienArchetype**

```java
package com.galacticodyssey.flora.alien;

/** The three alien-plant families. */
public enum AlienArchetype { BIOLUMINESCENT, CARNIVOROUS, CRYSTAL }
```

- [ ] **Step 4: Implement AlienPlantSpecies (flat superset POJO)**

```java
package com.galacticodyssey.flora.alien;

import com.badlogic.gdx.graphics.Color;

/** Data-driven alien-plant definition. Flat superset: which fields matter depends on archetype. */
public class AlienPlantSpecies {
    public String id;
    public AlienArchetype archetype = AlienArchetype.BIOLUMINESCENT;

    // Stalk (all archetypes)
    public float stalkHeightMin = 1f, stalkHeightMax = 2f;
    public float stalkBaseRadius = 0.12f;
    public float stalkTaper = 0.8f;
    public int stalkSides = 6;
    public Color stalkColor = new Color(0.2f, 0.2f, 0.25f, 1f);

    // Canopy (shared)
    public Color canopyColor = new Color(0.3f, 0.8f, 0.7f, 1f);
    public float canopyEmissive = 0f;

    // Bioluminescent canopy
    public int clumpsMin = 3, clumpsMax = 6;
    public float clumpRadiusMin = 0.4f, clumpRadiusMax = 0.8f;
    // Bioluminescent details
    public int detailCountMin = 0, detailCountMax = 0;
    public float detailEmissive = 0f;

    // Carnivorous canopy
    public float mouthRadiusMin = 0.5f, mouthRadiusMax = 0.9f;
    public float canopyDepthMin = 0.6f, canopyDepthMax = 1.1f;
    public float lureEmissive = 0f;
    public int teethMin = 0, teethMax = 0;

    // Crystal canopy
    public int shardsMin = 4, shardsMax = 8;
    public float shardLenMin = 0.6f, shardLenMax = 1.6f;
    public int subShardsMin = 0, subShardsMax = 0;

    public int prototypeVariants = 6;
}
```

- [ ] **Step 5: Implement AlienPlantRegistry**

```java
package com.galacticodyssey.flora.alien;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.flora.data.BiomePalette;
import com.galacticodyssey.planet.BiomeType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Loads alien-plant {@link AlienPlantSpecies} + a per-biome {@link BiomePalette}. */
public class AlienPlantRegistry {
    private final Map<String, AlienPlantSpecies> species = new HashMap<>();
    private final Map<BiomeType, BiomePalette> palettes = new EnumMap<>(BiomeType.class);

    public void load(String speciesAndPalettePath) {
        String json = Gdx.files.internal(speciesAndPalettePath).readString();
        loadSpecies(json);
        loadPalette(json);
    }

    public void loadSpecies(String json) {
        JsonValue arr = new JsonReader().parse(json).get("species");
        if (arr == null) return;
        for (JsonValue e = arr.child; e != null; e = e.next) {
            AlienPlantSpecies s = new AlienPlantSpecies();
            s.id = e.getString("id");
            s.archetype = AlienArchetype.valueOf(e.getString("archetype", s.archetype.name()));
            JsonValue st = e.get("stalk");
            if (st != null) {
                float[] h = pair(st.get("height"), s.stalkHeightMin, s.stalkHeightMax);
                s.stalkHeightMin = h[0]; s.stalkHeightMax = h[1];
                s.stalkBaseRadius = st.getFloat("baseRadius", s.stalkBaseRadius);
                s.stalkTaper = st.getFloat("taper", s.stalkTaper);
                s.stalkSides = st.getInt("sides", s.stalkSides);
                s.stalkColor = color(st.getString("color", null), s.stalkColor);
            }
            JsonValue c = e.get("canopy");
            if (c != null) {
                s.canopyColor = color(c.getString("color", null), s.canopyColor);
                s.canopyEmissive = c.getFloat("emissive", s.canopyEmissive);
                int[] clumps = ipair(c.get("clumps"), s.clumpsMin, s.clumpsMax);
                s.clumpsMin = clumps[0]; s.clumpsMax = clumps[1];
                float[] cr = pair(c.get("radius"), s.clumpRadiusMin, s.clumpRadiusMax);
                s.clumpRadiusMin = cr[0]; s.clumpRadiusMax = cr[1];
                float[] mr = pair(c.get("mouthRadius"), s.mouthRadiusMin, s.mouthRadiusMax);
                s.mouthRadiusMin = mr[0]; s.mouthRadiusMax = mr[1];
                float[] dp = pair(c.get("depth"), s.canopyDepthMin, s.canopyDepthMax);
                s.canopyDepthMin = dp[0]; s.canopyDepthMax = dp[1];
                s.lureEmissive = c.getFloat("lureEmissive", s.lureEmissive);
                int[] sh = ipair(c.get("shards"), s.shardsMin, s.shardsMax);
                s.shardsMin = sh[0]; s.shardsMax = sh[1];
                float[] sl = pair(c.get("length"), s.shardLenMin, s.shardLenMax);
                s.shardLenMin = sl[0]; s.shardLenMax = sl[1];
            }
            JsonValue d = e.get("details");
            if (d != null) {
                int[] cnt = ipair(d.get("count"), s.detailCountMin, s.detailCountMax);
                s.detailCountMin = cnt[0]; s.detailCountMax = cnt[1];
                s.detailEmissive = d.getFloat("emissive", s.detailEmissive);
                int[] teeth = ipair(d.get("teeth"), s.teethMin, s.teethMax);
                s.teethMin = teeth[0]; s.teethMax = teeth[1];
                int[] sub = ipair(d.get("subShards"), s.subShardsMin, s.subShardsMax);
                s.subShardsMin = sub[0]; s.subShardsMax = sub[1];
            }
            s.prototypeVariants = e.getInt("prototypeVariants", s.prototypeVariants);
            species.put(s.id, s);
        }
    }

    public void loadPalette(String json) {
        JsonValue arr = new JsonReader().parse(json).get("palette");
        if (arr == null) return;
        for (JsonValue e = arr.child; e != null; e = e.next) {
            BiomeType biome = BiomeType.valueOf(e.getString("biome"));
            BiomePalette p = new BiomePalette(biome);
            p.density = e.getFloat("density", 0f);
            JsonValue sp = e.get("species");
            if (sp != null) for (JsonValue se = sp.child; se != null; se = se.next) {
                p.add(se.getString("id"), se.getFloat("weight", 1f));
            }
            palettes.put(biome, p);
        }
    }

    public AlienPlantSpecies species(String id) { return species.get(id); }
    public java.util.Collection<AlienPlantSpecies> allSpecies() { return species.values(); }
    public BiomePalette palette(BiomeType biome) { return palettes.get(biome); }

    private static float[] pair(JsonValue v, float lo, float hi) {
        if (v == null || !v.isArray() || v.size < 2) return new float[]{lo, hi};
        return new float[]{ v.getFloat(0), v.getFloat(1) };
    }
    private static int[] ipair(JsonValue v, int lo, int hi) {
        if (v == null || !v.isArray() || v.size < 2) return new int[]{lo, hi};
        return new int[]{ v.getInt(0), v.getInt(1) };
    }
    private static Color color(String hex, Color fallback) {
        if (hex == null) return fallback;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 6) h = h + "ff";
        return Color.valueOf(h);
    }
}
```

> Note: `BiomePalette` (in `com.galacticodyssey.flora.data`) already has `density`, `entries`, `add(id,weight)`, `pickSpecies(Random)`, `isEmpty()` — reused unchanged.

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.alien.AlienPlantRegistryTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/alien/AlienArchetype.java core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantSpecies.java core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantRegistry.java core/src/test/java/com/galacticodyssey/flora/alien/AlienPlantRegistryTest.java
git commit -m "feat(alien): AlienPlantSpecies + registry (3 archetypes, reuse BiomePalette)"
```

---

## Task 2: AlienPlantMeshData + AlienPlantMeshBuilder (GL-free geometry)

**Files:**
- Create: `AlienPlantMeshData.java`, `AlienPlantMeshBuilder.java` (in `flora/alien/`)
- Test: `AlienPlantMeshBuilderTest.java`

Vertex stride 11: `pos3 + normal3 + color4 + emissive1`.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.flora.alien;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class AlienPlantMeshBuilderTest {
    static final int STRIDE = 11;

    private static AlienPlantSpecies species(AlienArchetype a) {
        AlienPlantSpecies s = new AlienPlantSpecies();
        s.id = "t"; s.archetype = a;
        s.stalkHeightMin = 1.5f; s.stalkHeightMax = 2f; s.stalkBaseRadius = 0.12f;
        s.stalkSides = 6; s.stalkColor = new Color(0.2f,0.2f,0.25f,1f);
        s.canopyColor = new Color(0.3f,0.8f,0.7f,1f);
        s.clumpsMin = 3; s.clumpsMax = 4; s.clumpRadiusMin = 0.4f; s.clumpRadiusMax = 0.6f;
        s.canopyEmissive = 3f; s.detailCountMin = 3; s.detailCountMax = 4; s.detailEmissive = 2f;
        s.mouthRadiusMin = 0.5f; s.mouthRadiusMax = 0.7f; s.canopyDepthMin = 0.6f; s.canopyDepthMax = 0.8f;
        s.lureEmissive = 2.5f; s.teethMin = 5; s.teethMax = 6;
        s.shardsMin = 5; s.shardsMax = 6; s.shardLenMin = 0.6f; s.shardLenMax = 1f;
        s.subShardsMin = 2; s.subShardsMax = 3;
        return s;
    }

    private static float maxEmissive(float[] v) {
        float m = 0; for (int i = 10; i < v.length; i += STRIDE) m = Math.max(m, v[i]); return m;
    }

    @Test
    void buildsValidGeometryForEachArchetype() {
        for (AlienArchetype a : AlienArchetype.values()) {
            AlienPlantMeshData m = AlienPlantMeshBuilder.build(species(a), new Random(1));
            assertTrue(m.vertices.length > 0, "verts " + a);
            assertEquals(0, m.vertices.length % STRIDE, "stride " + a);
            assertEquals(0, m.indices.length % 3, "tris " + a);
            assertFalse(m.bounds.getDimensions(new Vector3()).isZero(), "bounds " + a);
            for (int i = 0; i < m.vertices.length; i += STRIDE) {
                float nx=m.vertices[i+3], ny=m.vertices[i+4], nz=m.vertices[i+5];
                float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                assertEquals(1f, len, 0.05f, "normal " + a + " @" + (i/STRIDE));
            }
        }
    }

    @Test
    void glowingArchetypesEmitNonGlowingStalkDoesNot() {
        assertTrue(maxEmissive(AlienPlantMeshBuilder.build(species(AlienArchetype.BIOLUMINESCENT), new Random(2)).vertices) > 0f);
        assertTrue(maxEmissive(AlienPlantMeshBuilder.build(species(AlienArchetype.CRYSTAL), new Random(2)).vertices) > 0f);
        // carnivorous: lure glows, so overall max > 0
        assertTrue(maxEmissive(AlienPlantMeshBuilder.build(species(AlienArchetype.CARNIVOROUS), new Random(2)).vertices) > 0f);
    }

    @Test
    void stalkOnlySpeciesHasZeroEmissiveWhenCanopyEmissiveZero() {
        AlienPlantSpecies s = species(AlienArchetype.CARNIVOROUS);
        s.lureEmissive = 0f; // no glow anywhere
        float[] v = AlienPlantMeshBuilder.build(s, new Random(3)).vertices;
        assertEquals(0f, maxEmissive(v), 1e-6f);
    }

    @Test
    void isDeterministic() {
        for (AlienArchetype a : AlienArchetype.values()) {
            float[] x = AlienPlantMeshBuilder.build(species(a), new Random(7)).vertices;
            float[] y = AlienPlantMeshBuilder.build(species(a), new Random(7)).vertices;
            assertArrayEquals(x, y, "determinism " + a);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.alien.AlienPlantMeshBuilderTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Implement AlienPlantMeshData**

```java
package com.galacticodyssey.flora.alien;

import com.badlogic.gdx.math.collision.BoundingBox;

/** GL-free mesh data for one alien plant. Stride 11: pos3 + normal3 + color4 + emissive1. */
public final class AlienPlantMeshData {
    public final float[] vertices;
    public final short[] indices;
    public final BoundingBox bounds;

    public AlienPlantMeshData(float[] vertices, short[] indices, BoundingBox bounds) {
        this.vertices = vertices;
        this.indices = indices;
        this.bounds = bounds;
    }
}
```

- [ ] **Step 4: Implement AlienPlantMeshBuilder**

```java
package com.galacticodyssey.flora.alien;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;

import java.util.Random;

/** Builds a stalk + archetype canopy + details into GL-free {@link AlienPlantMeshData}. */
public final class AlienPlantMeshBuilder {
    public static final int STRIDE = 11;
    private AlienPlantMeshBuilder() {}

    public static AlienPlantMeshData build(AlienPlantSpecies sp, Random rng) {
        FloatArray v = new FloatArray();
        ShortArray idx = new ShortArray();
        BoundingBox b = new BoundingBox();
        b.inf();

        float height = lerp(sp.stalkHeightMin, sp.stalkHeightMax, rng.nextFloat());
        float baseR = sp.stalkBaseRadius;
        float topR = Math.max(0.02f, baseR * sp.stalkTaper);
        Vector3 base = new Vector3(0, 0, 0);
        Vector3 top = new Vector3(0, height, 0);
        // stalk
        addTube(v, idx, base, baseR, top, topR, sp.stalkSides, sp.stalkColor, 0f, b);

        switch (sp.archetype) {
            case BIOLUMINESCENT: buildBiolum(v, idx, sp, top, height, rng, b); break;
            case CARNIVOROUS:    buildCarnivorous(v, idx, sp, top, rng, b); break;
            case CRYSTAL:        buildCrystal(v, idx, sp, top, rng, b); break;
        }
        return new AlienPlantMeshData(v.toArray(), idx.toArray(), b);
    }

    private static void buildBiolum(FloatArray v, ShortArray idx, AlienPlantSpecies sp,
                                    Vector3 top, float height, Random rng, BoundingBox b) {
        int clumps = irange(sp.clumpsMin, sp.clumpsMax, rng);
        for (int i = 0; i < clumps; i++) {
            float r = lerp(sp.clumpRadiusMin, sp.clumpRadiusMax, rng.nextFloat());
            Vector3 c = new Vector3(top).add(
                (rng.nextFloat() - 0.5f) * r * 1.5f,
                rng.nextFloat() * r,
                (rng.nextFloat() - 0.5f) * r * 1.5f);
            addBlob(v, idx, c, r, 4, 6, sp.canopyColor, sp.canopyEmissive, b);
        }
        int details = irange(sp.detailCountMin, sp.detailCountMax, rng);
        for (int i = 0; i < details; i++) {
            float r = 0.04f + rng.nextFloat() * 0.06f;
            float t = 0.2f + rng.nextFloat() * 0.7f;
            Vector3 c = new Vector3((rng.nextFloat() - 0.5f) * 0.1f, height * t, (rng.nextFloat() - 0.5f) * 0.1f);
            addBlob(v, idx, c, r, 3, 5, sp.canopyColor, sp.detailEmissive, b);
        }
    }

    private static void buildCarnivorous(FloatArray v, ShortArray idx, AlienPlantSpecies sp,
                                         Vector3 top, Random rng, BoundingBox b) {
        float mouthR = lerp(sp.mouthRadiusMin, sp.mouthRadiusMax, rng.nextFloat());
        float depth = lerp(sp.canopyDepthMin, sp.canopyDepthMax, rng.nextFloat());
        Vector3 cupTop = new Vector3(top).add(0, depth, 0);
        // pitcher cup: narrow at stalk, flaring to mouth
        addTube(v, idx, top, mouthR * 0.25f, cupTop, mouthR, 10, sp.canopyColor, 0f, b);
        // lip ring (short wide rim)
        Vector3 lipTop = new Vector3(cupTop).add(0, depth * 0.12f, 0);
        addTube(v, idx, cupTop, mouthR, lipTop, mouthR * 1.1f, 10, sp.canopyColor, 0f, b);
        // lure inside the cup (glows)
        Vector3 lure = new Vector3(top).add(0, depth * 0.5f, 0);
        addBlob(v, idx, lure, mouthR * 0.2f, 3, 5, sp.canopyColor, sp.lureEmissive, b);
        // teeth: thin inward-pointing cones around the rim
        int teeth = irange(sp.teethMin, sp.teethMax, rng);
        for (int i = 0; i < teeth; i++) {
            double a = 2.0 * Math.PI * i / Math.max(1, teeth);
            Vector3 rimPt = new Vector3(cupTop).add(mouthR * (float) Math.cos(a), 0, mouthR * (float) Math.sin(a));
            Vector3 tip = new Vector3(rimPt).add(-mouthR * 0.3f * (float) Math.cos(a), depth * 0.2f, -mouthR * 0.3f * (float) Math.sin(a));
            addTube(v, idx, rimPt, mouthR * 0.06f, tip, 0.005f, 4, sp.stalkColor, 0f, b);
        }
    }

    private static void buildCrystal(FloatArray v, ShortArray idx, AlienPlantSpecies sp,
                                     Vector3 top, Random rng, BoundingBox b) {
        int shards = irange(sp.shardsMin, sp.shardsMax, rng);
        for (int i = 0; i < shards; i++) {
            spawnShard(v, idx, top, lerp(sp.shardLenMin, sp.shardLenMax, rng.nextFloat()),
                0.08f + rng.nextFloat() * 0.08f, sp, rng, b);
        }
        int sub = irange(sp.subShardsMin, sp.subShardsMax, rng);
        for (int i = 0; i < sub; i++) {
            Vector3 origin = new Vector3(top).add((rng.nextFloat() - 0.5f) * 0.3f, rng.nextFloat() * 0.2f, (rng.nextFloat() - 0.5f) * 0.3f);
            spawnShard(v, idx, origin, lerp(sp.shardLenMin, sp.shardLenMax, rng.nextFloat()) * 0.5f,
                0.04f + rng.nextFloat() * 0.04f, sp, rng, b);
        }
    }

    private static void spawnShard(FloatArray v, ShortArray idx, Vector3 origin, float len, float r,
                                   AlienPlantSpecies sp, Random rng, BoundingBox b) {
        // direction: mostly up, splayed outward
        Vector3 dir = new Vector3((rng.nextFloat() - 0.5f) * 1.4f, 0.6f + rng.nextFloat() * 0.8f, (rng.nextFloat() - 0.5f) * 1.4f).nor();
        Vector3 tip = new Vector3(origin).mulAdd(dir, len);
        // a faceted shard: wide-ish base ring tapering to a point (prism->point)
        addTube(v, idx, origin, r, tip, 0.01f, 5, sp.canopyColor, sp.canopyEmissive, b);
    }

    // ---- geometry helpers (write stride-11 vertices) ----

    /** Tapered N-gon tube from a (radius ra) to b (radius rb). */
    private static void addTube(FloatArray v, ShortArray idx, Vector3 a, float ra,
                                Vector3 b, float rb, int sides, Color col, float emissive, BoundingBox bb) {
        Vector3 dir = new Vector3(b).sub(a);
        if (dir.len2() < 1e-8f) return;
        dir.nor();
        Vector3 up = Math.abs(dir.y) < 0.99f ? new Vector3(0, 1, 0) : new Vector3(1, 0, 0);
        Vector3 t1 = new Vector3(up).crs(dir).nor();
        Vector3 t2 = new Vector3(dir).crs(t1).nor();
        int base = v.size / STRIDE;
        for (int s = 0; s < sides; s++) {
            double ang = 2.0 * Math.PI * s / sides;
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            Vector3 radial = new Vector3(t1).scl(cos).add(new Vector3(t2).scl(sin)).nor();
            vert(v, a.x + radial.x * ra, a.y + radial.y * ra, a.z + radial.z * ra, radial, col, emissive, bb);
            vert(v, b.x + radial.x * rb, b.y + radial.y * rb, b.z + radial.z * rb, radial, col, emissive, bb);
        }
        for (int s = 0; s < sides; s++) {
            int sn = (s + 1) % sides;
            short b0 = (short) (base + 2 * s), t0 = (short) (base + 2 * s + 1);
            short b1 = (short) (base + 2 * sn), tt = (short) (base + 2 * sn + 1);
            idx.add(b0); idx.add(t0); idx.add(b1);
            idx.add(b1); idx.add(t0); idx.add(tt);
        }
    }

    /** Low-poly UV-sphere blob. */
    private static void addBlob(FloatArray v, ShortArray idx, Vector3 c, float r,
                                int rings, int sectors, Color col, float emissive, BoundingBox bb) {
        int base = v.size / STRIDE;
        for (int ri = 0; ri <= rings; ri++) {
            double phi = Math.PI * ri / rings;
            float y = (float) Math.cos(phi), rad = (float) Math.sin(phi);
            for (int si = 0; si <= sectors; si++) {
                double th = 2.0 * Math.PI * si / sectors;
                float x = rad * (float) Math.cos(th), z = rad * (float) Math.sin(th);
                Vector3 n = new Vector3(x, y, z);
                vert(v, c.x + x * r, c.y + y * r, c.z + z * r, n, col, emissive, bb);
            }
        }
        int stride = sectors + 1;
        for (int ri = 0; ri < rings; ri++) for (int si = 0; si < sectors; si++) {
            short p0 = (short) (base + ri * stride + si);
            short p1 = (short) (base + (ri + 1) * stride + si);
            short p2 = (short) (base + ri * stride + si + 1);
            short p3 = (short) (base + (ri + 1) * stride + si + 1);
            idx.add(p0); idx.add(p1); idx.add(p2);
            idx.add(p2); idx.add(p1); idx.add(p3);
        }
    }

    private static void vert(FloatArray v, float x, float y, float z, Vector3 n, Color c, float emissive, BoundingBox bb) {
        v.add(x); v.add(y); v.add(z);
        v.add(n.x); v.add(n.y); v.add(n.z);
        v.add(c.r); v.add(c.g); v.add(c.b); v.add(c.a);
        v.add(emissive);
        bb.ext(x, y, z);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static int irange(int lo, int hi, Random rng) { return hi <= lo ? lo : lo + rng.nextInt(hi - lo + 1); }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.alien.AlienPlantMeshBuilderTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantMeshData.java core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantMeshBuilder.java core/src/test/java/com/galacticodyssey/flora/alien/AlienPlantMeshBuilderTest.java
git commit -m "feat(alien): GL-free stalk+canopy+detail mesh builder (3 archetypes, baked emissive)"
```

---

## Task 3: AlienPlantModelFactory (mesh data → Model)

GL-bound — no unit test (a `Mesh` needs GL). Compile-only; verified visually in Task 8.

**Files:**
- Create: `AlienPlantModelFactory.java` (in `flora/alien/`)

- [ ] **Step 1: Implement AlienPlantModelFactory**

```java
package com.galacticodyssey.flora.alien;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

/** Uploads {@link AlienPlantMeshData} (stride 11) into a libGDX {@link Model}. Requires GL. */
public final class AlienPlantModelFactory {
    private static final VertexAttributes ATTRS = new VertexAttributes(
        new VertexAttribute(Usage.Position, 3, "a_position"),
        new VertexAttribute(Usage.Normal, 3, "a_normal"),
        new VertexAttribute(Usage.ColorUnpacked, 4, "a_color"),
        new VertexAttribute(Usage.Generic, 1, "a_emissive"));

    private AlienPlantModelFactory() {}

    public static Model toModel(AlienPlantMeshData data) {
        Mesh mesh = new Mesh(true, data.vertices.length / AlienPlantMeshBuilder.STRIDE, data.indices.length, ATTRS);
        mesh.setVertices(data.vertices);
        mesh.setIndices(data.indices);
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.part("alien", mesh, GL20.GL_TRIANGLES, new Material());
        return mb.end();
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL. (`Usage.ColorUnpacked` alias is libGDX's `a_color`; `Usage.Generic` requires the 3-arg `VertexAttribute(usage, n, alias)` — confirm that constructor exists; it does in 1.13.5. If the mesh's color attribute needs a specific alias for the gbuffer shader, it is `a_color` per the shader's `HAS_VERTEX_COLOR` input.)

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantModelFactory.java
git commit -m "feat(alien): AlienPlantModelFactory uploads stride-11 mesh with a_emissive"
```

---

## Task 4: EmissiveGBufferShader + provider

GL-bound — no unit test. Compile-only; verified visually in Task 8. Mirrors `GBufferBatchShader`/`GBufferBatchShaderProvider` but enables vertex colour + emissive.

**Files:**
- Create: `EmissiveGBufferShader.java`, `EmissiveGBufferShaderProvider.java` (in `rendering/`)

- [ ] **Step 1: Implement EmissiveGBufferShader**

```java
package com.galacticodyssey.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Matrix4;
import com.galacticodyssey.rendering.shaders.ShaderCache;

/** Gbuffer shader for vertex-coloured, self-emissive meshes (alien plants). Glow strength is
 *  baked into the per-vertex a_emissive; u_emissiveIntensity is a constant 1. */
public class EmissiveGBufferShader implements Shader {
    private final ShaderProgram program;
    private Camera camera;
    private final Matrix3 normalMatrix = new Matrix3();
    private final Matrix4 tmpMat = new Matrix4();

    public EmissiveGBufferShader(ShaderCache shaderCache) {
        this.program = shaderCache.get("gbuffer.vert", "gbuffer.frag", "HAS_VERTEX_COLOR", "HAS_EMISSIVE_ATTRIB");
    }

    @Override public void init() {}
    @Override public int compareTo(Shader other) { return 0; }
    @Override public boolean canRender(Renderable instance) { return true; }

    @Override
    public void begin(Camera camera, RenderContext context) {
        this.camera = camera;
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);
        program.bind();
        program.setUniformf("u_albedoTint", 1f, 1f, 1f, 1f);
        program.setUniformf("u_metallicScale", 0f);
        program.setUniformf("u_roughnessScale", 0.6f);
        program.setUniformf("u_emissiveIntensity", 1f);
        program.setUniformf("u_tiling", 1f, 1f);
    }

    @Override
    public void render(Renderable renderable) {
        program.setUniformMatrix("u_projViewTrans", camera.combined);
        program.setUniformMatrix("u_worldTrans", renderable.worldTransform);
        tmpMat.set(camera.view).mul(renderable.worldTransform);
        normalMatrix.set(tmpMat).inv().transpose();
        program.setUniformMatrix("u_normalMatrix", normalMatrix);
        renderable.meshPart.render(program);
    }

    @Override public void end() {}
    @Override public void dispose() {}
}
```

- [ ] **Step 2: Implement EmissiveGBufferShaderProvider**

```java
package com.galacticodyssey.rendering;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
import com.galacticodyssey.rendering.shaders.ShaderCache;

public class EmissiveGBufferShaderProvider implements ShaderProvider {
    private final EmissiveGBufferShader shader;
    public EmissiveGBufferShaderProvider(ShaderCache shaderCache) { this.shader = new EmissiveGBufferShader(shaderCache); }
    @Override public Shader getShader(Renderable renderable) { return shader; }
    @Override public void dispose() {}
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/rendering/EmissiveGBufferShader.java core/src/main/java/com/galacticodyssey/rendering/EmissiveGBufferShaderProvider.java
git commit -m "feat(alien): EmissiveGBufferShader (vertex colour + emissive gbuffer pass)"
```

---

## Task 5: AlienPlantPlacement + AlienPlantGenerator.planPlacements (pure, testable)

**Files:**
- Create: `AlienPlantPlacement.java`, `AlienPlantGenerator.java` (planPlacements + clamp only)
- Test: `AlienPlantPlacementTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.flora.alien;

import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AlienPlantPlacementTest {
    private static final String JSON = "{ \"species\": [ { \"id\": \"glowcap\", \"archetype\": \"BIOLUMINESCENT\", \"prototypeVariants\": 4 } ]," +
        " \"palette\": [ { \"biome\": \"SWAMP\", \"density\": 1.0, \"species\": [ {\"id\":\"glowcap\",\"weight\":1} ] }," +
        "               { \"biome\": \"DESERT\", \"density\": 0.0, \"species\": [] } ] }";

    private static AlienPlantRegistry reg() {
        AlienPlantRegistry r = new AlienPlantRegistry();
        r.loadSpecies(JSON); r.loadPalette(JSON); return r;
    }
    private static float[] flat(int v){ float[] h=new float[v*v]; java.util.Arrays.fill(h,1f); return h; }
    private static BiomeType[] uniform(int v, BiomeType b){ BiomeType[] g=new BiomeType[v*v]; java.util.Arrays.fill(g,b); return g; }

    @Test
    void placesInPaletteBiomeNotElsewhere() {
        int v=33; float[] hm=flat(v); AlienPlantRegistry r=reg();
        List<AlienPlantPlacement> swamp = AlienPlantGenerator.planPlacements(r, uniform(v,BiomeType.SWAMP), hm, v,v,100f,100f,0f,99L,300);
        List<AlienPlantPlacement> desert = AlienPlantGenerator.planPlacements(r, uniform(v,BiomeType.DESERT), hm, v,v,100f,100f,0f,99L,300);
        assertFalse(swamp.isEmpty());
        assertTrue(desert.isEmpty());
        for (AlienPlantPlacement p : swamp) {
            assertEquals("glowcap", p.speciesId);
            assertTrue(p.variantIndex >= 0 && p.variantIndex < 4);
            assertTrue(p.scale > 0f);
        }
    }

    @Test
    void isDeterministic() {
        int v=33; float[] hm=flat(v); AlienPlantRegistry r=reg();
        List<AlienPlantPlacement> a = AlienPlantGenerator.planPlacements(r, uniform(v,BiomeType.SWAMP), hm, v,v,100f,100f,0f,5L,300);
        List<AlienPlantPlacement> b = AlienPlantGenerator.planPlacements(r, uniform(v,BiomeType.SWAMP), hm, v,v,100f,100f,0f,5L,300);
        assertEquals(a.size(), b.size());
        for (int i=0;i<a.size();i++){ assertEquals(a.get(i).x,b.get(i).x); assertEquals(a.get(i).variantIndex,b.get(i).variantIndex); }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.alien.AlienPlantPlacementTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Implement AlienPlantPlacement**

```java
package com.galacticodyssey.flora.alien;

/** One resolved alien-plant instance. */
public final class AlienPlantPlacement {
    public final String speciesId;
    public final int variantIndex;
    public final float x, y, z, yawDeg, scale;
    public AlienPlantPlacement(String speciesId, int variantIndex, float x, float y, float z, float yawDeg, float scale) {
        this.speciesId = speciesId; this.variantIndex = variantIndex;
        this.x = x; this.y = y; this.z = z; this.yawDeg = yawDeg; this.scale = scale;
    }
}
```

- [ ] **Step 4: Implement AlienPlantGenerator (planPlacements only)**

```java
package com.galacticodyssey.flora.alien;

import com.galacticodyssey.data.TerrainGenerator;
import com.galacticodyssey.flora.data.BiomePalette;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Alien-plant placement (pure) + prototype/instance building (GL, added in Task 6). */
public final class AlienPlantGenerator {
    static final long ALIEN_SALT = 0x616C69656EL; // "alien"
    private AlienPlantGenerator() {}

    public static List<AlienPlantPlacement> planPlacements(
            AlienPlantRegistry registry, BiomeType[] biomeGrid, float[] heightmap,
            int vertsX, int vertsZ, float worldWidth, float worldDepth,
            float seaLevel, long planetSeed, int attempts) {
        List<AlienPlantPlacement> out = new ArrayList<>();
        long seed = SeedDeriver.forId(SeedDeriver.floraDomain(planetSeed), ALIEN_SALT);
        Random rng = new Random(seed);
        float halfW = worldWidth / 2f, halfD = worldDepth / 2f;
        for (int i = 0; i < attempts; i++) {
            float wx = (rng.nextFloat() - 0.5f) * worldWidth * 0.9f;
            float wz = (rng.nextFloat() - 0.5f) * worldDepth * 0.9f;
            float h = TerrainGenerator.getHeightAt(heightmap, vertsX, vertsZ, worldWidth, worldDepth, wx, wz);
            if (h < seaLevel + 0.5f) continue;
            int gx = clamp((int) ((wx + halfW) / worldWidth * (vertsX - 1)), 0, vertsX - 1);
            int gz = clamp((int) ((wz + halfD) / worldDepth * (vertsZ - 1)), 0, vertsZ - 1);
            BiomePalette palette = registry.palette(biomeGrid[gz * vertsX + gx]);
            if (palette == null || palette.isEmpty()) continue;
            if (rng.nextFloat() > palette.density) continue;
            String speciesId = palette.pickSpecies(rng);
            if (speciesId == null) continue;
            AlienPlantSpecies sp = registry.species(speciesId);
            if (sp == null) continue;
            int variant = rng.nextInt(Math.max(1, sp.prototypeVariants));
            float yaw = rng.nextFloat() * 360f;
            float scale = 0.85f + rng.nextFloat() * 0.4f;
            out.add(new AlienPlantPlacement(speciesId, variant, wx, h, wz, yaw, scale));
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // buildPrototypes() + populate() added in Task 6
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.alien.AlienPlantPlacementTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantPlacement.java core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantGenerator.java core/src/test/java/com/galacticodyssey/flora/alien/AlienPlantPlacementTest.java
git commit -m "feat(alien): deterministic biome-gated alien-plant placement planning"
```

---

## Task 6: AlienPlantGenerator prototype pool + populate (GL) + PopulatedWorld field

GL-bound — no unit test. Compile-only.

**Files:**
- Modify: `AlienPlantGenerator.java` (add buildPrototypes, populate, helpers)
- Modify: `core/src/main/java/com/galacticodyssey/data/WorldPopulator.java` (add `alienInstances` field)

- [ ] **Step 1: Add `alienInstances` to PopulatedWorld**

In `WorldPopulator.java`, in the `PopulatedWorld` class, next to `treeInstances`:
```java
        public final Array<ModelInstance> alienInstances = new Array<>();
```
(`Array` and `ModelInstance` are already imported in WorldPopulator.)

- [ ] **Step 2: Add imports + GL methods to AlienPlantGenerator**

Add imports:
```java
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.data.WorldPopulator.PopulatedWorld;
import java.util.HashMap;
import java.util.Map;
```
Replace the `// buildPrototypes() + populate() added in Task 6` comment with:

```java
    /** Builds N prototype Models per species (GL). */
    public static Map<String, Model[]> buildPrototypes(AlienPlantRegistry registry, long planetSeed) {
        long seed = SeedDeriver.forId(SeedDeriver.floraDomain(planetSeed), ALIEN_SALT);
        Map<String, Model[]> pool = new HashMap<>();
        for (AlienPlantSpecies sp : registry.allSpecies()) {
            int variants = Math.max(1, sp.prototypeVariants);
            Model[] models = new Model[variants];
            for (int vi = 0; vi < variants; vi++) {
                long vseed = SeedDeriver.forId(seed, ((long) sp.id.hashCode() << 20) ^ vi);
                models[vi] = AlienPlantModelFactory.toModel(AlienPlantMeshBuilder.build(sp, new Random(vseed)));
            }
            pool.put(sp.id, models);
        }
        return pool;
    }

    /** Builds alien-plant ModelInstances into world.alienInstances; registers prototypes for disposal. */
    public static void populate(PopulatedWorld world, AlienPlantRegistry registry, float[] heightmap,
                                int vertsX, int vertsZ, float worldWidth, float worldDepth,
                                float seaLevel, long planetSeed) {
        Map<String, Model[]> prototypes = buildPrototypes(registry, planetSeed);
        for (Model[] arr : prototypes.values()) for (Model m : arr) world.addModel(m);
        List<AlienPlantPlacement> placements = planPlacements(
            registry, world.biomeGrid, heightmap, vertsX, vertsZ, worldWidth, worldDepth, seaLevel, planetSeed, 300);
        for (AlienPlantPlacement pl : placements) {
            Model[] variants = prototypes.get(pl.speciesId);
            if (variants == null || variants.length == 0) continue;
            ModelInstance inst = new ModelInstance(variants[pl.variantIndex % variants.length]);
            inst.transform.setToTranslation(pl.x, pl.y, pl.z);
            inst.transform.rotate(Vector3.Y, pl.yawDeg);
            inst.transform.scale(pl.scale, pl.scale, pl.scale);
            world.alienInstances.add(inst);
        }
    }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/alien/AlienPlantGenerator.java core/src/main/java/com/galacticodyssey/data/WorldPopulator.java
git commit -m "feat(alien): prototype pool + populate into PopulatedWorld.alienInstances"
```

---

## Task 7: alien_plants.json data

**Files:**
- Create: `core/src/main/resources/data/flora/alien_plants.json`

- [ ] **Step 1: Create the file**

```json
{
  "species": [
    { "id": "glowcap", "archetype": "BIOLUMINESCENT",
      "stalk": { "height": [1.5,3.0], "baseRadius": 0.12, "taper": 0.7, "sides": 6, "color": "2a2f4a" },
      "canopy": { "clumps": [3,6], "radius": [0.4,0.8], "color": "2fd0c0", "emissive": 3.0 },
      "details": { "count": [3,8], "emissive": 2.0 }, "prototypeVariants": 6 },
    { "id": "violet_lantern", "archetype": "BIOLUMINESCENT",
      "stalk": { "height": [2.0,3.5], "baseRadius": 0.1, "taper": 0.65, "sides": 6, "color": "241a30" },
      "canopy": { "clumps": [2,4], "radius": [0.5,0.9], "color": "9a4dff", "emissive": 3.5 },
      "details": { "count": [2,5], "emissive": 2.5 }, "prototypeVariants": 6 },
    { "id": "maw_pitcher", "archetype": "CARNIVOROUS",
      "stalk": { "height": [0.8,1.6], "baseRadius": 0.15, "taper": 0.85, "sides": 6, "color": "3a2a1f" },
      "canopy": { "mouthRadius": [0.5,0.9], "depth": [0.6,1.1], "color": "5a1f28", "lureEmissive": 2.5 },
      "details": { "teeth": [5,9] }, "prototypeVariants": 5 },
    { "id": "shardspire", "archetype": "CRYSTAL",
      "stalk": { "height": [0.4,1.0], "baseRadius": 0.2, "taper": 0.9, "sides": 5, "color": "404a6a" },
      "canopy": { "shards": [4,8], "length": [0.6,1.6], "color": "8ad0ff", "emissive": 0.6 },
      "details": { "subShards": [2,5] }, "prototypeVariants": 6 }
  ],
  "palette": [
    { "biome": "SWAMP",           "density": 0.5,  "species": [ {"id":"glowcap","weight":0.6}, {"id":"maw_pitcher","weight":0.4} ] },
    { "biome": "TROPICAL_FOREST", "density": 0.2,  "species": [ {"id":"glowcap","weight":0.5}, {"id":"violet_lantern","weight":0.3}, {"id":"maw_pitcher","weight":0.2} ] },
    { "biome": "BOREAL_FOREST",   "density": 0.12, "species": [ {"id":"violet_lantern","weight":1.0} ] },
    { "biome": "TUNDRA",          "density": 0.1,  "species": [ {"id":"violet_lantern","weight":1.0} ] },
    { "biome": "VOLCANIC",        "density": 0.3,  "species": [ {"id":"shardspire","weight":1.0} ] },
    { "biome": "BADLANDS",        "density": 0.15, "species": [ {"id":"shardspire","weight":1.0} ] },
    { "biome": "ROCKY_WASTE",     "density": 0.12, "species": [ {"id":"shardspire","weight":1.0} ] }
  ]
}
```

- [ ] **Step 2: Validate JSON + referential integrity**

Run: `node -e "const j=JSON.parse(require('fs').readFileSync('core/src/main/resources/data/flora/alien_plants.json','utf8')); const ids=new Set(j.species.map(s=>s.id)); for(const p of j.palette) for(const s of p.species) if(!ids.has(s.id)) throw new Error('bad id '+s.id); console.log('OK')"`
Expected: `OK`. (If node unavailable, manually confirm every palette `id` ∈ {glowcap, violet_lantern, maw_pitcher, shardspire} and every `biome` is a real `BiomeType`.)

- [ ] **Step 3: Commit**

```bash
git add core/src/main/resources/data/flora/alien_plants.json
git commit -m "feat(alien): starter alien-plant species + per-biome palette"
```

---

## Task 8: GameScreen integration + visual verify

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Add imports + fields**

Imports:
```java
import com.galacticodyssey.flora.alien.AlienPlantGenerator;
import com.galacticodyssey.flora.alien.AlienPlantRegistry;
import com.galacticodyssey.rendering.EmissiveGBufferShaderProvider;
```
Fields (near `grassRenderer`):
```java
    private ModelBatch alienBatch;
```

- [ ] **Step 2: World-load wiring**

Right after the grass wiring at world load (after `grassRenderer = new GrassRenderer(...)`), add:
```java
        AlienPlantRegistry alienRegistry = new AlienPlantRegistry();
        alienRegistry.load("data/flora/alien_plants.json");
        AlienPlantGenerator.populate(populatedWorld, alienRegistry, heightmap,
            TERRAIN_VERTS_X, TERRAIN_VERTS_Z, TERRAIN_WIDTH, TERRAIN_DEPTH, populatedWorld.seaLevel, TERRAIN_SEED);
        alienBatch = new ModelBatch(new EmissiveGBufferShaderProvider(deferredRenderer.getShaderCache()));
```
(If the local world seed variable is `terrainSeed` rather than `TERRAIN_SEED`, use that — match the grass call's seed argument exactly. `populatedWorld.seaLevel` exists; if the grass call used a different sea-level source, match it.)

- [ ] **Step 3: Render alien plants in the gbuffer pass**

In the gbuffer-pass lambda, after `renderWorldObjects();` add:
```java
                renderAlienPlants();
```
Add the method near `renderWorldObjects()`:
```java
    private void renderAlienPlants() {
        if (alienBatch == null || populatedWorld == null || populatedWorld.alienInstances.size == 0) return;
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        alienBatch.begin(camera);
        for (int i = 0; i < populatedWorld.alienInstances.size; i++) {
            alienBatch.render(populatedWorld.alienInstances.get(i));
        }
        alienBatch.end();
    }
```

- [ ] **Step 4: Dispose**

In `dispose()`, alongside `grassRenderer`:
```java
        if (alienBatch != null) { alienBatch.dispose(); alienBatch = null; }
```

- [ ] **Step 5: Compile + full core test sweep**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL — alien tests pass; only the KNOWN pre-existing unrelated failures (`OrbitalMechanicsIntegrationTest`, possible gdx-bullet native crash) may remain. Confirm no NEW failures from this change.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(alien): wire alien-plant generation + emissive render pass into GameScreen"
```

- [ ] **Step 7: Visual verification (run the game)**

Use the `run-galactic-odyssey` skill. Build → launch → New Game (a seed/biome with swamp/volcanic if possible) → walk the surface, screenshot. Confirm:
- The three archetypes appear in their biomes (glowing caps/lanterns in swamp/forest, pitcher maws, crystal spires in volcanic/badlands).
- Bioluminescent + crystal plants **glow** (most visible at night/dusk — emissive blooms in the deferred pipeline).
- No crash; no shader-compile error in the log (`[GRASS-DEBUG]`-style: check `game_stderr.log` for exceptions / `gbuffer.vert`/`gbuffer.frag` link errors with the new defines).
- If alien plants render unlit/black instead of glowing: confirm `EmissiveGBufferShader` compiled with both defines and that `a_emissive`/`a_color` attributes match the shader inputs.

---

## Task 9: Update memory + final review

- [ ] **Step 1: Full test sweep** — `./gradlew :core:test`; confirm alien tests pass, no new failures.
- [ ] **Step 2: Update memory** — mark Cycle C DONE in `project_flora-generation.md` (alien plants: 3 archetypes, stalk+canopy+detail, dedicated `EmissiveGBufferShader`, reuses placement; data-driven `alien_plants.json`); update `MEMORY.md` line. The flora 3-cycle effort is then complete.
- [ ] **Step 3: Final whole-cycle review** — dispatch a reviewer over the Cycle C diff (determinism; emissive baked-vs-shader; the stride-11 attribute alignment between `AlienPlantMeshBuilder`, `AlienPlantModelFactory` `VertexAttributes`, and the `gbuffer.vert` `a_color`/`a_emissive` inputs; disposal; no fauna dependency). Address Critical/Important findings.

---

## Self-Review

**Spec coverage:**
- 3 archetypes (biolum/carnivorous/crystal) → Task 2 (`buildBiolum`/`buildCarnivorous`/`buildCrystal`). ✓
- Stalk→canopy→detail fixed assembly, no fauna dep → Task 2. ✓
- Per-vertex baked emissive + dedicated emissive shader → Tasks 2 (bake), 4 (shader). ✓
- Stride-11 layout consistent across builder/factory/shader → header + Tasks 2, 3, 4. ✓
- Data-driven species + reuse BiomePalette → Tasks 1, 7. ✓
- Placement reuse (seeded, biome-gated, prototype pool) into `alienInstances` → Tasks 5, 6. ✓
- Determinism via FLORA_DOMAIN + alien salt → Tasks 5, 6. ✓
- Render in gbuffer pass via second ModelBatch; trees/rocks shader untouched → Task 8. ✓
- GL-free core unit-tested; GL verified visually → Tasks 1,2,5 (tests) vs 3,4,6,8 (visual). ✓
- Out-of-scope (refraction, point lights, animation) → not built. ✓

**Placeholder scan:** No TBD/"handle edge cases". Staged markers (`// buildPrototypes() + populate() added in Task 6`) are explicit and resolved in Task 6. ✓

**Type consistency:** `AlienPlantSpecies` fields used in Task 2 builder match Task 1 definitions (stalkHeightMin/Max, stalkSides, canopyColor/Emissive, clumpsMin/Max, clumpRadiusMin/Max, mouthRadiusMin/Max, canopyDepthMin/Max, lureEmissive, teethMin/Max, shardsMin/Max, shardLenMin/Max, subShardsMin/Max, detailCountMin/Max, detailEmissive, prototypeVariants). `AlienPlantMeshBuilder.STRIDE=11` referenced by factory (Task 3). `build(AlienPlantSpecies, Random)→AlienPlantMeshData`, `toModel(AlienPlantMeshData)→Model`, `planPlacements(...)→List<AlienPlantPlacement>`, `buildPrototypes(...)→Map<String,Model[]>`, `populate(...)`, `AlienPlantPlacement.{speciesId,variantIndex,x,y,z,yawDeg,scale}` — consistent. Vertex attribute aliases `a_color`/`a_emissive` (Task 3) match `gbuffer.vert` `HAS_VERTEX_COLOR`/`HAS_EMISSIVE_ATTRIB` inputs. `BiomePalette.{density,entries,add,pickSpecies,isEmpty}` reused from flora.data. ✓
