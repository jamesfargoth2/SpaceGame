# Flora Generation Core (Cycle A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the box trees with deterministic, biome-driven, data-defined branching plants generated via space colonization, reused from a per-species prototype pool.

**Architecture:** A new `com.galacticodyssey.flora` package. JSON defines species (envelope shape + growth params + colors) and per-biome palettes. A deterministic pipeline turns a seed into attraction points → a branch skeleton (space colonization) → GL-free mesh data (tapered tubes + low-poly foliage blobs) → a libGDX `Model`. A small pool of prototype Models per species is generated once per scene; each placed plant is a lightweight `ModelInstance` referencing a prototype. `WorldPopulator` delegates tree placement to the new generator.

**Tech Stack:** Java 17, libGDX 1.13 (`ModelBuilder`, `Mesh`, `Vector3`, `BoundingBox`, `JsonReader`), Ashley (unused here), JUnit 5. Build: Gradle (`:core` module).

**Key design refinements over the spec (decided during planning, all within design intent):**
- Mesh vertices use **pos3 + normal3 (stride 6)** with **per-MeshPart material colour** (trunk part + foliage part), matching the *existing, proven* box-tree render path through the deferred gbuffer — rather than baked per-vertex colour (unverified through the deferred shader). Colour variation comes from **per-prototype-variant** material colours; per-instance variation is transform (yaw + uniform scale). This still realises "slight repetition hidden by transform/tint variation."
- Content lives in **two consolidated files** — `data/flora/species.json` and `data/flora/palettes.json` (arrays) — instead of one-file-per-species, because libGDX internal **directory listing is unreliable** on the desktop classpath. Adding species is still JSON-only.

---

## File Structure

**Create:**
- `core/src/main/java/com/galacticodyssey/flora/FloraEnums.java` — `EnvelopeShape`, `FoliageStyle` enums.
- `core/src/main/java/com/galacticodyssey/flora/data/FloraSpecies.java` — immutable-ish species POJO.
- `core/src/main/java/com/galacticodyssey/flora/data/BiomePalette.java` — per-biome density + weighted species list + weighted pick.
- `core/src/main/java/com/galacticodyssey/flora/data/FloraRegistry.java` — loads species + palettes JSON.
- `core/src/main/java/com/galacticodyssey/flora/gen/AttractionEnvelope.java` — seeded attraction-point cloud per shape.
- `core/src/main/java/com/galacticodyssey/flora/gen/BranchSkeleton.java` — node graph POJO (pos, parent, relRadius, isTip).
- `core/src/main/java/com/galacticodyssey/flora/gen/SpaceColonization.java` — growth algorithm + `GrowthParams`.
- `core/src/main/java/com/galacticodyssey/flora/gen/FloraMeshData.java` — trunk/foliage float[]/short[] + bounds.
- `core/src/main/java/com/galacticodyssey/flora/gen/FloraMeshBuilder.java` — skeleton → FloraMeshData (GL-free).
- `core/src/main/java/com/galacticodyssey/flora/gen/FloraModelFactory.java` — FloraMeshData → `Model` (GL).
- `core/src/main/java/com/galacticodyssey/flora/FloraPlacement.java` — one resolved placement (species, variant, transform).
- `core/src/main/java/com/galacticodyssey/flora/FloraGenerator.java` — orchestrator (plan placements + build prototypes + populate).
- `core/src/main/resources/data/flora/species.json` — starter species set.
- `core/src/main/resources/data/flora/palettes.json` — palette per biome.

**Create (tests):**
- `core/src/test/java/com/galacticodyssey/galaxy/SeedDeriverFloraTest.java`
- `core/src/test/java/com/galacticodyssey/flora/data/FloraRegistryTest.java`
- `core/src/test/java/com/galacticodyssey/flora/data/BiomePaletteTest.java`
- `core/src/test/java/com/galacticodyssey/flora/gen/AttractionEnvelopeTest.java`
- `core/src/test/java/com/galacticodyssey/flora/gen/SpaceColonizationTest.java`
- `core/src/test/java/com/galacticodyssey/flora/gen/FloraMeshBuilderTest.java`
- `core/src/test/java/com/galacticodyssey/flora/FloraGeneratorPlacementTest.java`

**Modify:**
- `core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java` — add `FLORA_DOMAIN` + `floraDomain(long)`.
- `core/src/main/java/com/galacticodyssey/data/WorldPopulator.java` — delegate tree placement to `FloraGenerator`; remove `placeTrees`, `treeDensity`, `canopyColorForBiome`.

---

## Task 1: Add the flora seed domain

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java`
- Test: `core/src/test/java/com/galacticodyssey/galaxy/SeedDeriverFloraTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeedDeriverFloraTest {
    @Test
    void floraDomainIsDeterministicAndDistinct() {
        long a = SeedDeriver.floraDomain(12345L);
        long b = SeedDeriver.floraDomain(12345L);
        assertEquals(a, b, "same seed must derive the same flora domain");
        assertNotEquals(SeedDeriver.floraDomain(12345L), SeedDeriver.floraDomain(12346L));
        // distinct from an unrelated domain on the same parent seed
        assertNotEquals(SeedDeriver.floraDomain(12345L), SeedDeriver.npcDomain(12345L));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.SeedDeriverFloraTest"`
Expected: FAIL — `floraDomain` / `FLORA_DOMAIN` not defined (compile error).

- [ ] **Step 3: Add the constant and helper**

In `SeedDeriver.java`, add a constant alongside the others (after `EROSION_DOMAIN`, line ~27):

```java
    public static final long FLORA_DOMAIN          = 0x6A1F4C8B3E7D2059L;
```

And a helper alongside `npcDomain` (after line ~45):

```java
    public static long floraDomain(long parentSeed) {
        return domain(parentSeed, FLORA_DOMAIN);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.SeedDeriverFloraTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java core/src/test/java/com/galacticodyssey/galaxy/SeedDeriverFloraTest.java
git commit -m "feat(flora): add FLORA_DOMAIN seed derivation"
```

---

## Task 2: Flora enums and species/palette data models

No behaviour yet (pure data holders) — verified via the registry test in Task 3. These compile-only tasks have no standalone test step.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/flora/FloraEnums.java`
- Create: `core/src/main/java/com/galacticodyssey/flora/data/FloraSpecies.java`

- [ ] **Step 1: Create the enums**

`FloraEnums.java`:

```java
package com.galacticodyssey.flora;

/** Shared enums for flora generation. */
public final class FloraEnums {
    private FloraEnums() {}

    /** Coarse shape of the attraction-point volume a plant grows into. */
    public enum EnvelopeShape { ELLIPSOID, CONE, COLUMN, DOME, CYLINDER }

    /** How (or whether) foliage clumps are attached to branch tips. */
    public enum FoliageStyle { CLUMP, NONE }
}
```

- [ ] **Step 2: Create the species POJO**

`FloraSpecies.java`:

```java
package com.galacticodyssey.flora.data;

import com.badlogic.gdx.graphics.Color;
import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;
import com.galacticodyssey.flora.FloraEnums.FoliageStyle;

/** Data-driven definition of a flora species. Loaded from data/flora/species.json. */
public class FloraSpecies {
    public String id;
    public String displayName;

    // Envelope
    public EnvelopeShape shape = EnvelopeShape.ELLIPSOID;
    public float heightMin = 6f, heightMax = 10f;
    public float radiusMin = 2f, radiusMax = 3f;

    // Growth (space colonization)
    public int attractionPoints = 160;
    public float influenceRadius = 4f;
    public float killDistance = 0.7f;
    public float segmentLength = 0.45f;
    public int maxNodes = 500;

    // Trunk / branches
    public int trunkSides = 6;
    public float baseRadius = 0.3f;   // radius at the root, in world units
    public float taper = 0.8f;        // 0..1, higher = slower thinning toward tips
    public Color trunkColor = new Color(0.35f, 0.22f, 0.10f, 1f);

    // Foliage
    public FoliageStyle foliageStyle = FoliageStyle.CLUMP;
    public int clumpsPerTip = 1;
    public float clumpRadiusMin = 1.0f, clumpRadiusMax = 1.6f;
    public Color foliageColorA = new Color(0.15f, 0.42f, 0.12f, 1f);
    public Color foliageColorB = new Color(0.20f, 0.50f, 0.15f, 1f);

    // Prototype pool
    public int prototypeVariants = 6;
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/FloraEnums.java core/src/main/java/com/galacticodyssey/flora/data/FloraSpecies.java
git commit -m "feat(flora): add flora enums and species data model"
```

---

## Task 3: FloraRegistry — load species JSON

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/flora/data/FloraRegistry.java`
- Test: `core/src/test/java/com/galacticodyssey/flora/data/FloraRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.flora.data;

import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;
import com.galacticodyssey.flora.FloraEnums.FoliageStyle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FloraRegistryTest {
    private static final String SPECIES = "{ \"species\": [\n" +
        "  { \"id\": \"jungle_tree\", \"displayName\": \"Canopy Tree\",\n" +
        "    \"shape\": \"ELLIPSOID\", \"height\": [8,14], \"radius\": [3,5],\n" +
        "    \"growth\": { \"attractionPoints\": 220, \"influenceRadius\": 4.0,\n" +
        "      \"killDistance\": 0.7, \"segmentLength\": 0.45, \"maxNodes\": 600 },\n" +
        "    \"trunk\": { \"sides\": 6, \"baseRadius\": 0.35, \"taper\": 0.78, \"color\": \"5a3b22\" },\n" +
        "    \"foliage\": { \"style\": \"CLUMP\", \"clumpsPerTip\": 2,\n" +
        "      \"clumpRadius\": [1.0,1.8], \"colorA\": \"2f6b2a\", \"colorB\": \"3f8a34\" },\n" +
        "    \"prototypeVariants\": 8 },\n" +
        "  { \"id\": \"desert_cactus\", \"shape\": \"COLUMN\", \"height\": [2,4], \"radius\": [0.5,0.8],\n" +
        "    \"foliage\": { \"style\": \"NONE\" } }\n" +
        "] }";

    @Test
    void loadsSpeciesWithNestedFields() {
        FloraRegistry reg = new FloraRegistry();
        reg.loadSpecies(SPECIES);

        FloraSpecies t = reg.species("jungle_tree");
        assertNotNull(t);
        assertEquals("Canopy Tree", t.displayName);
        assertEquals(EnvelopeShape.ELLIPSOID, t.shape);
        assertEquals(8f, t.heightMin);
        assertEquals(14f, t.heightMax);
        assertEquals(220, t.attractionPoints);
        assertEquals(0.45f, t.segmentLength);
        assertEquals(6, t.trunkSides);
        assertEquals(FoliageStyle.CLUMP, t.foliageStyle);
        assertEquals(2, t.clumpsPerTip);
        assertEquals(8, t.prototypeVariants);
        // colour "5a3b22" parsed to ~ (0.353, 0.231, 0.133)
        assertEquals(0x5a / 255f, t.trunkColor.r, 0.01f);

        FloraSpecies c = reg.species("desert_cactus");
        assertEquals(FoliageStyle.NONE, c.foliageStyle);
        assertEquals(EnvelopeShape.COLUMN, c.shape);
    }

    @Test
    void unknownSpeciesReturnsNull() {
        FloraRegistry reg = new FloraRegistry();
        reg.loadSpecies(SPECIES);
        assertNull(reg.species("nope"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.data.FloraRegistryTest"`
Expected: FAIL — `FloraRegistry` not defined.

- [ ] **Step 3: Implement the registry (species half)**

`FloraRegistry.java`:

```java
package com.galacticodyssey.flora.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;
import com.galacticodyssey.flora.FloraEnums.FoliageStyle;
import com.galacticodyssey.planet.BiomeType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Loads and holds flora {@link FloraSpecies} and per-biome {@link BiomePalette}s. */
public class FloraRegistry {
    private final Map<String, FloraSpecies> species = new HashMap<>();
    private final Map<BiomeType, BiomePalette> palettes = new EnumMap<>(BiomeType.class);

    /** Loads both files from the internal (classpath) filesystem at bootstrap. */
    public void load(String speciesPath, String palettePath) {
        loadSpecies(Gdx.files.internal(speciesPath).readString());
        loadPalettes(Gdx.files.internal(palettePath).readString());
    }

    /** Parses species from a raw JSON string (unit-test friendly). */
    public void loadSpecies(String json) {
        JsonValue arr = new JsonReader().parse(json).get("species");
        for (JsonValue e = arr.child; e != null; e = e.next) {
            FloraSpecies s = new FloraSpecies();
            s.id = e.getString("id");
            s.displayName = e.getString("displayName", s.id);
            s.shape = EnvelopeShape.valueOf(e.getString("shape", s.shape.name()));
            float[] h = floatPair(e.get("height"), s.heightMin, s.heightMax);
            s.heightMin = h[0]; s.heightMax = h[1];
            float[] r = floatPair(e.get("radius"), s.radiusMin, s.radiusMax);
            s.radiusMin = r[0]; s.radiusMax = r[1];

            JsonValue g = e.get("growth");
            if (g != null) {
                s.attractionPoints = g.getInt("attractionPoints", s.attractionPoints);
                s.influenceRadius = g.getFloat("influenceRadius", s.influenceRadius);
                s.killDistance = g.getFloat("killDistance", s.killDistance);
                s.segmentLength = g.getFloat("segmentLength", s.segmentLength);
                s.maxNodes = g.getInt("maxNodes", s.maxNodes);
            }
            JsonValue tr = e.get("trunk");
            if (tr != null) {
                s.trunkSides = tr.getInt("sides", s.trunkSides);
                s.baseRadius = tr.getFloat("baseRadius", s.baseRadius);
                s.taper = tr.getFloat("taper", s.taper);
                s.trunkColor = color(tr.getString("color", null), s.trunkColor);
            }
            JsonValue f = e.get("foliage");
            if (f != null) {
                s.foliageStyle = FoliageStyle.valueOf(f.getString("style", s.foliageStyle.name()));
                s.clumpsPerTip = f.getInt("clumpsPerTip", s.clumpsPerTip);
                float[] cr = floatPair(f.get("clumpRadius"), s.clumpRadiusMin, s.clumpRadiusMax);
                s.clumpRadiusMin = cr[0]; s.clumpRadiusMax = cr[1];
                s.foliageColorA = color(f.getString("colorA", null), s.foliageColorA);
                s.foliageColorB = color(f.getString("colorB", null), s.foliageColorB);
            }
            s.prototypeVariants = e.getInt("prototypeVariants", s.prototypeVariants);
            species.put(s.id, s);
        }
    }

    public FloraSpecies species(String id) { return species.get(id); }
    public java.util.Collection<FloraSpecies> allSpecies() { return species.values(); }
    public BiomePalette palette(BiomeType biome) { return palettes.get(biome); }

    // --- helpers ---

    private static float[] floatPair(JsonValue v, float defLo, float defHi) {
        if (v == null || !v.isArray() || v.size < 2) return new float[]{defLo, defHi};
        return new float[]{ v.getFloat(0), v.getFloat(1) };
    }

    private static Color color(String hex, Color fallback) {
        if (hex == null) return fallback;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 6) h = h + "ff"; // Color.valueOf wants rrggbbaa
        return Color.valueOf(h);
    }

    // loadPalettes() added in Task 4
}
```

> Note: leave a `// loadPalettes() added in Task 4` placeholder — the `palettes` map and `palette(...)` getter are already declared so the class compiles.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.data.FloraRegistryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/data/FloraRegistry.java core/src/test/java/com/galacticodyssey/flora/data/FloraRegistryTest.java
git commit -m "feat(flora): FloraRegistry loads species JSON"
```

---

## Task 4: BiomePalette + palette loading + weighted species pick

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/flora/data/BiomePalette.java`
- Modify: `core/src/main/java/com/galacticodyssey/flora/data/FloraRegistry.java` (add `loadPalettes`)
- Test: `core/src/test/java/com/galacticodyssey/flora/data/BiomePaletteTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.flora.data;

import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class BiomePaletteTest {
    private static final String PALETTES = "{ \"palettes\": [\n" +
        "  { \"biome\": \"TROPICAL_FOREST\", \"density\": 0.85, \"tintJitter\": 0.08,\n" +
        "    \"species\": [ { \"id\": \"jungle_tree\", \"weight\": 0.75 },\n" +
        "                  { \"id\": \"understory\", \"weight\": 0.25 } ] },\n" +
        "  { \"biome\": \"OCEAN\", \"density\": 0.0, \"species\": [] }\n" +
        "] }";

    @Test
    void loadsPaletteByBiome() {
        FloraRegistry reg = new FloraRegistry();
        reg.loadPalettes(PALETTES);
        BiomePalette p = reg.palette(BiomeType.TROPICAL_FOREST);
        assertNotNull(p);
        assertEquals(0.85f, p.density);
        assertEquals(2, p.entries.size());
        assertTrue(reg.palette(BiomeType.OCEAN).isEmpty());
        assertNull(reg.palette(BiomeType.DESERT)); // not defined
    }

    @Test
    void weightedPickIsDeterministicAndRespectsWeights() {
        FloraRegistry reg = new FloraRegistry();
        reg.loadPalettes(PALETTES);
        BiomePalette p = reg.palette(BiomeType.TROPICAL_FOREST);

        // determinism: same seed -> same pick
        assertEquals(p.pickSpecies(new Random(42)), p.pickSpecies(new Random(42)));

        // distribution: jungle_tree (0.75) should dominate over many draws
        Random rng = new Random(7);
        int jungle = 0;
        for (int i = 0; i < 2000; i++) if ("jungle_tree".equals(p.pickSpecies(rng))) jungle++;
        assertTrue(jungle > 1300 && jungle < 1700, "expected ~75% jungle_tree, got " + jungle);
    }

    @Test
    void emptyPalettePicksNull() {
        FloraRegistry reg = new FloraRegistry();
        reg.loadPalettes(PALETTES);
        assertNull(reg.palette(BiomeType.OCEAN).pickSpecies(new Random(1)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.data.BiomePaletteTest"`
Expected: FAIL — `BiomePalette` / `loadPalettes` not defined.

- [ ] **Step 3: Implement BiomePalette**

`BiomePalette.java`:

```java
package com.galacticodyssey.flora.data;

import com.galacticodyssey.planet.BiomeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Per-biome flora rules: overall density + a weighted list of species ids. */
public class BiomePalette {
    public static class Entry {
        public final String speciesId;
        public final float weight;
        public Entry(String speciesId, float weight) { this.speciesId = speciesId; this.weight = weight; }
    }

    public final BiomeType biome;
    public float density;
    public float tintJitter = 0.06f;
    public final List<Entry> entries = new ArrayList<>();
    private float totalWeight;

    public BiomePalette(BiomeType biome) { this.biome = biome; }

    public void add(String speciesId, float weight) {
        entries.add(new Entry(speciesId, weight));
        totalWeight += weight;
    }

    public boolean isEmpty() { return entries.isEmpty() || totalWeight <= 0f; }

    /** Deterministic weighted choice; returns null for an empty palette. */
    public String pickSpecies(Random rng) {
        if (isEmpty()) return null;
        float r = rng.nextFloat() * totalWeight;
        for (Entry e : entries) {
            r -= e.weight;
            if (r <= 0f) return e.speciesId;
        }
        return entries.get(entries.size() - 1).speciesId; // float guard
    }
}
```

- [ ] **Step 4: Add `loadPalettes` to FloraRegistry**

Replace the `// loadPalettes() added in Task 4` comment with:

```java
    /** Parses per-biome palettes from a raw JSON string (unit-test friendly). */
    public void loadPalettes(String json) {
        JsonValue arr = new JsonReader().parse(json).get("palettes");
        for (JsonValue e = arr.child; e != null; e = e.next) {
            BiomeType biome = BiomeType.valueOf(e.getString("biome"));
            BiomePalette p = new BiomePalette(biome);
            p.density = e.getFloat("density", 0f);
            p.tintJitter = e.getFloat("tintJitter", p.tintJitter);
            JsonValue sp = e.get("species");
            if (sp != null) {
                for (JsonValue se = sp.child; se != null; se = se.next) {
                    p.add(se.getString("id"), se.getFloat("weight", 1f));
                }
            }
            palettes.put(biome, p);
        }
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.data.BiomePaletteTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/data/BiomePalette.java core/src/main/java/com/galacticodyssey/flora/data/FloraRegistry.java core/src/test/java/com/galacticodyssey/flora/data/BiomePaletteTest.java
git commit -m "feat(flora): BiomePalette weighted selection + palette loading"
```

---

## Task 5: AttractionEnvelope — seeded point cloud per shape

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/flora/gen/AttractionEnvelope.java`
- Test: `core/src/test/java/com/galacticodyssey/flora/gen/AttractionEnvelopeTest.java`

Local coordinate convention: the plant's base is at `(0,0,0)`, growing up +Y.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class AttractionEnvelopeTest {
    @Test
    void generatesRequestedCountWithinBounds() {
        float height = 10f, radius = 3f;
        for (EnvelopeShape shape : EnvelopeShape.values()) {
            Array<Vector3> pts = AttractionEnvelope.generate(shape, height, radius, 150, new Random(1));
            assertEquals(150, pts.size, "count for " + shape);
            for (Vector3 p : pts) {
                assertTrue(p.y >= -0.01f && p.y <= height + 0.01f, shape + " y out of range: " + p.y);
                float horiz = (float) Math.sqrt(p.x * p.x + p.z * p.z);
                assertTrue(horiz <= radius + 0.01f, shape + " horiz out of range: " + horiz);
            }
        }
    }

    @Test
    void isDeterministic() {
        Array<Vector3> a = AttractionEnvelope.generate(EnvelopeShape.ELLIPSOID, 8f, 2f, 80, new Random(99));
        Array<Vector3> b = AttractionEnvelope.generate(EnvelopeShape.ELLIPSOID, 8f, 2f, 80, new Random(99));
        assertEquals(a.size, b.size);
        for (int i = 0; i < a.size; i++) assertEquals(a.get(i), b.get(i));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.gen.AttractionEnvelopeTest"`
Expected: FAIL — `AttractionEnvelope` not defined.

- [ ] **Step 3: Implement AttractionEnvelope**

```java
package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;

import java.util.Random;

/** Generates a seeded cloud of attraction points filling a plant's growth envelope. */
public final class AttractionEnvelope {
    private AttractionEnvelope() {}

    public static Array<Vector3> generate(EnvelopeShape shape, float height, float radius,
                                          int count, Random rng) {
        Array<Vector3> pts = new Array<>(count);
        for (int i = 0; i < count; i++) {
            switch (shape) {
                case ELLIPSOID: pts.add(ellipsoid(height, radius, rng)); break;
                case CONE:      pts.add(cone(height, radius, rng)); break;
                case COLUMN:    pts.add(column(height, radius, rng)); break;
                case DOME:      pts.add(dome(height, radius, rng)); break;
                case CYLINDER:  pts.add(cylinder(height, radius, rng)); break;
            }
        }
        return pts;
    }

    /** Sphere centred at 0.6*height, vertical semi-axis 0.4*height, horizontal = radius. */
    private static Vector3 ellipsoid(float height, float radius, Random rng) {
        Vector3 u = inUnitSphere(rng);
        float cy = height * 0.6f, sy = height * 0.4f;
        return new Vector3(u.x * radius, cy + u.y * sy, u.z * radius);
    }

    /** Cone: widest at base of canopy (0.3*height), narrowing to apex at height. */
    private static Vector3 cone(float height, float radius, Random rng) {
        float y = lerp(height * 0.3f, height, rng.nextFloat());
        float frac = (y - height * 0.3f) / (height - height * 0.3f); // 0 at base .. 1 at apex
        float r = radius * (1f - frac) * (float) Math.sqrt(rng.nextFloat());
        return atAngle(r, y, rng);
    }

    /** Column (cactus): narrow vertical cylinder clustered around the axis. */
    private static Vector3 column(float height, float radius, Random rng) {
        float y = lerp(height * 0.15f, height, rng.nextFloat());
        float r = radius * 0.5f * (float) Math.sqrt(rng.nextFloat());
        return atAngle(r, y, rng);
    }

    /** Dome (shrub / lichen mound): hemisphere of the given radius sitting on the ground. */
    private static Vector3 dome(float height, float radius, Random rng) {
        Vector3 u = inUnitSphere(rng);
        float y = Math.abs(u.y) * height;
        return new Vector3(u.x * radius, y, u.z * radius);
    }

    private static Vector3 cylinder(float height, float radius, Random rng) {
        float y = rng.nextFloat() * height;
        float r = radius * (float) Math.sqrt(rng.nextFloat());
        return atAngle(r, y, rng);
    }

    private static Vector3 atAngle(float r, float y, Random rng) {
        double a = rng.nextFloat() * Math.PI * 2.0;
        return new Vector3(r * (float) Math.cos(a), y, r * (float) Math.sin(a));
    }

    /** Rejection-sampled point inside the unit sphere. */
    private static Vector3 inUnitSphere(Random rng) {
        float x, y, z;
        do {
            x = rng.nextFloat() * 2f - 1f;
            y = rng.nextFloat() * 2f - 1f;
            z = rng.nextFloat() * 2f - 1f;
        } while (x * x + y * y + z * z > 1f);
        return new Vector3(x, y, z);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.gen.AttractionEnvelopeTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/gen/AttractionEnvelope.java core/src/test/java/com/galacticodyssey/flora/gen/AttractionEnvelopeTest.java
git commit -m "feat(flora): seeded attraction-point envelopes"
```

---

## Task 6: BranchSkeleton + SpaceColonization growth

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/flora/gen/BranchSkeleton.java`
- Create: `core/src/main/java/com/galacticodyssey/flora/gen/SpaceColonization.java`
- Test: `core/src/test/java/com/galacticodyssey/flora/gen/SpaceColonizationTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SpaceColonizationTest {
    private static SpaceColonization.GrowthParams params() {
        SpaceColonization.GrowthParams p = new SpaceColonization.GrowthParams();
        p.influenceRadius = 4f; p.killDistance = 0.7f; p.segmentLength = 0.45f; p.maxNodes = 400;
        return p;
    }

    private static BranchSkeleton grow(long seed) {
        Random rng = new Random(seed);
        Array<Vector3> pts = AttractionEnvelope.generate(EnvelopeShape.ELLIPSOID, 10f, 3f, 160, rng);
        return SpaceColonization.grow(pts, params(), new Random(seed));
    }

    @Test
    void producesConnectedBoundedSkeleton() {
        BranchSkeleton s = grow(1);
        assertTrue(s.size() >= 2, "should grow past the root");
        assertTrue(s.size() <= params().maxNodes, "must respect maxNodes");
        assertEquals(-1, s.parent(0), "node 0 is the root");
        for (int i = 1; i < s.size(); i++) {
            int p = s.parent(i);
            assertTrue(p >= 0 && p < i, "parent index must precede child: node " + i + " -> " + p);
        }
    }

    @Test
    void rootIsThickestTipsAreThin() {
        BranchSkeleton s = grow(2);
        assertEquals(1f, s.relRadius(0), 1e-4f, "root has full relative radius");
        for (int i = 0; i < s.size(); i++) {
            assertTrue(s.relRadius(i) > 0f && s.relRadius(i) <= 1f);
            if (s.isTip(i)) assertTrue(s.relRadius(i) < 1f, "a tip should be thinner than the root");
        }
    }

    @Test
    void isDeterministic() {
        BranchSkeleton a = grow(123), b = grow(123);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.position(i), b.position(i));
            assertEquals(a.parent(i), b.parent(i));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.gen.SpaceColonizationTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Implement BranchSkeleton**

```java
package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;

/** A grown branch graph: nodes with positions, parent links, relative radius, tip flags. */
public final class BranchSkeleton {
    private final Array<Vector3> positions = new Array<>();
    private final IntArray parents = new IntArray();
    private float[] relRadius = new float[0];
    private boolean[] tip = new boolean[0];

    int addNode(Vector3 pos, int parent) {
        positions.add(new Vector3(pos));
        parents.add(parent);
        return positions.size - 1;
    }

    void finalizeRadii(float[] relRadius, boolean[] tip) {
        this.relRadius = relRadius;
        this.tip = tip;
    }

    public int size() { return positions.size; }
    public Vector3 position(int i) { return positions.get(i); }
    public int parent(int i) { return parents.get(i); }
    public float relRadius(int i) { return relRadius[i]; }
    public boolean isTip(int i) { return tip[i]; }
}
```

- [ ] **Step 4: Implement SpaceColonization**

```java
package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

import java.util.Random;

/** Space-colonization branch growth: nodes grow toward nearby attraction points. */
public final class SpaceColonization {
    private SpaceColonization() {}

    public static class GrowthParams {
        public float influenceRadius = 4f;
        public float killDistance = 0.7f;
        public float segmentLength = 0.45f;
        public int maxNodes = 500;
    }

    public static BranchSkeleton grow(Array<Vector3> attractors, GrowthParams p, Random rng) {
        BranchSkeleton skel = new BranchSkeleton();
        skel.addNode(new Vector3(0, 0, 0), -1);

        Array<Vector3> live = new Array<>(attractors);
        int safety = p.maxNodes * 4;

        while (live.size > 0 && skel.size() < p.maxNodes && safety-- > 0) {
            // Accumulate a growth direction per node from its influencing attractors.
            Vector3[] dir = new Vector3[skel.size()];
            int[] hits = new int[skel.size()];
            float inf2 = p.influenceRadius * p.influenceRadius;

            for (Vector3 a : live) {
                int nearest = -1; float best = Float.MAX_VALUE;
                for (int n = 0; n < skel.size(); n++) {
                    float d2 = skel.position(n).dst2(a);
                    if (d2 < best) { best = d2; nearest = n; }
                }
                if (nearest >= 0 && best <= inf2) {
                    if (dir[nearest] == null) dir[nearest] = new Vector3();
                    dir[nearest].add(new Vector3(a).sub(skel.position(nearest)).nor());
                    hits[nearest]++;
                }
            }

            boolean grew = false;
            int existing = skel.size();
            for (int n = 0; n < existing; n++) {
                if (hits[n] == 0) continue;
                Vector3 d = dir[n].nor();
                if (d.isZero(1e-5f)) continue;
                Vector3 np = new Vector3(skel.position(n)).mulAdd(d, p.segmentLength);
                skel.addNode(np, n);
                grew = true;
                if (skel.size() >= p.maxNodes) break;
            }
            if (!grew) break;

            // Remove attractors that any node has now reached.
            float kill2 = p.killDistance * p.killDistance;
            for (int i = live.size - 1; i >= 0; i--) {
                Vector3 a = live.get(i);
                for (int n = 0; n < skel.size(); n++) {
                    if (skel.position(n).dst2(a) <= kill2) { live.removeIndex(i); break; }
                }
            }
        }

        assignRadii(skel);
        return skel;
    }

    /** Relative radius from descendant count: thick at the root (1.0), thin at tips. */
    private static void assignRadii(BranchSkeleton skel) {
        int n = skel.size();
        int[] descendants = new int[n];
        boolean[] hasChild = new boolean[n];
        // children always have a higher index than their parent, so iterate in reverse.
        for (int i = n - 1; i >= 1; i--) {
            int p = skel.parent(i);
            descendants[p] += descendants[i] + 1;
            hasChild[p] = true;
        }
        float rootD = descendants[0] + 1f;
        float[] rel = new float[n];
        boolean[] tip = new boolean[n];
        for (int i = 0; i < n; i++) {
            float frac = (descendants[i] + 1f) / rootD;            // 0..1
            rel[i] = Math.max(0.08f, (float) Math.sqrt(frac));      // sqrt keeps mid-branches visible
            tip[i] = !hasChild[i];
        }
        rel[0] = 1f;
        skel.finalizeRadii(rel, tip);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.gen.SpaceColonizationTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/gen/BranchSkeleton.java core/src/main/java/com/galacticodyssey/flora/gen/SpaceColonization.java core/src/test/java/com/galacticodyssey/flora/gen/SpaceColonizationTest.java
git commit -m "feat(flora): space-colonization branch skeleton growth"
```

---

## Task 7: FloraMeshData + FloraMeshBuilder (GL-free geometry)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/flora/gen/FloraMeshData.java`
- Create: `core/src/main/java/com/galacticodyssey/flora/gen/FloraMeshBuilder.java`
- Test: `core/src/test/java/com/galacticodyssey/flora/gen/FloraMeshBuilderTest.java`

Vertex stride = 6 floats: `pos.x, pos.y, pos.z, n.x, n.y, n.z`. Colour is applied per MeshPart material in Task 8, not here.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.flora.FloraEnums.EnvelopeShape;
import com.galacticodyssey.flora.FloraEnums.FoliageStyle;
import com.galacticodyssey.flora.data.FloraSpecies;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class FloraMeshBuilderTest {
    private static BranchSkeleton skeleton(long seed) {
        Random rng = new Random(seed);
        Array<Vector3> pts = AttractionEnvelope.generate(EnvelopeShape.ELLIPSOID, 10f, 3f, 160, rng);
        SpaceColonization.GrowthParams p = new SpaceColonization.GrowthParams();
        return SpaceColonization.grow(pts, p, new Random(seed));
    }

    private static FloraSpecies species(FoliageStyle style) {
        FloraSpecies s = new FloraSpecies();
        s.trunkSides = 6; s.baseRadius = 0.3f; s.taper = 0.8f;
        s.foliageStyle = style; s.clumpsPerTip = 1;
        s.clumpRadiusMin = 1f; s.clumpRadiusMax = 1.5f;
        return s;
    }

    @Test
    void buildsNonEmptyTrunkGeometry() {
        FloraMeshData m = FloraMeshBuilder.build(skeleton(1), species(FoliageStyle.CLUMP), new Random(1));
        assertTrue(m.trunkVertices.length > 0);
        assertEquals(0, m.trunkVertices.length % 6, "stride is 6 floats");
        assertEquals(0, m.trunkIndices.length % 3, "triangles");
        assertFalse(m.bounds.getDimensions(new Vector3()).isZero(), "bounds must be non-degenerate");
    }

    @Test
    void normalsAreUnitLength() {
        FloraMeshData m = FloraMeshBuilder.build(skeleton(2), species(FoliageStyle.CLUMP), new Random(2));
        for (float[] verts : new float[][]{ m.trunkVertices, m.foliageVertices }) {
            for (int i = 0; i + 6 <= verts.length; i += 6) {
                float nx = verts[i + 3], ny = verts[i + 4], nz = verts[i + 5];
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                assertEquals(1f, len, 0.05f, "normal not unit length at vertex " + (i / 6));
            }
        }
    }

    @Test
    void foliageNoneProducesNoFoliage() {
        FloraMeshData m = FloraMeshBuilder.build(skeleton(3), species(FoliageStyle.NONE), new Random(3));
        assertEquals(0, m.foliageVertices.length);
        assertEquals(0, m.foliageIndices.length);
    }

    @Test
    void isDeterministic() {
        FloraMeshData a = FloraMeshBuilder.build(skeleton(4), species(FoliageStyle.CLUMP), new Random(4));
        FloraMeshData b = FloraMeshBuilder.build(skeleton(4), species(FoliageStyle.CLUMP), new Random(4));
        assertArrayEquals(a.trunkVertices, b.trunkVertices);
        assertArrayEquals(a.foliageVertices, b.foliageVertices);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.gen.FloraMeshBuilderTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Implement FloraMeshData**

```java
package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.collision.BoundingBox;

/** GL-free mesh data for one flora model: separate trunk and foliage geometry (stride 6). */
public final class FloraMeshData {
    public final float[] trunkVertices;
    public final short[] trunkIndices;
    public final float[] foliageVertices;
    public final short[] foliageIndices;
    public final BoundingBox bounds;

    public FloraMeshData(float[] trunkVertices, short[] trunkIndices,
                         float[] foliageVertices, short[] foliageIndices, BoundingBox bounds) {
        this.trunkVertices = trunkVertices;
        this.trunkIndices = trunkIndices;
        this.foliageVertices = foliageVertices;
        this.foliageIndices = foliageIndices;
        this.bounds = bounds;
    }

    public boolean hasFoliage() { return foliageVertices.length > 0; }
}
```

- [ ] **Step 4: Implement FloraMeshBuilder**

```java
package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.galacticodyssey.flora.FloraEnums.FoliageStyle;
import com.galacticodyssey.flora.data.FloraSpecies;

import java.util.Random;

/** Turns a {@link BranchSkeleton} into GL-free {@link FloraMeshData}. No GL context required. */
public final class FloraMeshBuilder {
    private static final int FOLIAGE_RINGS = 4;
    private static final int FOLIAGE_SECTORS = 6;

    private FloraMeshBuilder() {}

    public static FloraMeshData build(BranchSkeleton skel, FloraSpecies sp, Random rng) {
        FloatArray tv = new FloatArray();
        ShortArray ti = new ShortArray();
        FloatArray fv = new FloatArray();
        ShortArray fi = new ShortArray();
        BoundingBox bounds = new BoundingBox();
        bounds.inf();

        // Trunk + branches: a tapered tube per (child -> parent) segment.
        for (int i = 1; i < skel.size(); i++) {
            int p = skel.parent(i);
            Vector3 a = skel.position(p);
            Vector3 b = skel.position(i);
            float ra = relToWorld(sp, skel.relRadius(p));
            float rb = relToWorld(sp, skel.relRadius(i));
            addSegment(tv, ti, a, ra, b, rb, sp.trunkSides, bounds);
        }

        // Foliage clumps at tips.
        if (sp.foliageStyle == FoliageStyle.CLUMP) {
            for (int i = 0; i < skel.size(); i++) {
                if (!skel.isTip(i)) continue;
                for (int c = 0; c < sp.clumpsPerTip; c++) {
                    float r = sp.clumpRadiusMin + rng.nextFloat() * (sp.clumpRadiusMax - sp.clumpRadiusMin);
                    Vector3 center = new Vector3(skel.position(i));
                    center.add((rng.nextFloat() - 0.5f) * r, (rng.nextFloat() - 0.5f) * r * 0.5f,
                               (rng.nextFloat() - 0.5f) * r);
                    addBlob(fv, fi, center, r, FOLIAGE_RINGS, FOLIAGE_SECTORS, bounds);
                }
            }
        }

        return new FloraMeshData(tv.toArray(), ti.toArray(), fv.toArray(), fi.toArray(), bounds);
    }

    private static float relToWorld(FloraSpecies sp, float rel) {
        // taper widens or narrows the mid-branch profile; baseRadius scales the whole tree.
        return Math.max(0.02f, sp.baseRadius * (sp.taper * rel + (1f - sp.taper) * rel * rel));
    }

    /** Tapered N-gon tube from a (radius ra) to b (radius rb). */
    private static void addSegment(FloatArray v, ShortArray idx, Vector3 a, float ra,
                                   Vector3 b, float rb, int sides, BoundingBox bounds) {
        Vector3 dir = new Vector3(b).sub(a);
        if (dir.len2() < 1e-8f) return;
        dir.nor();
        Vector3 up = Math.abs(dir.y) < 0.99f ? new Vector3(0, 1, 0) : new Vector3(1, 0, 0);
        Vector3 t1 = new Vector3(up).crs(dir).nor();
        Vector3 t2 = new Vector3(dir).crs(t1).nor();

        int base = v.size / 6;
        for (int s = 0; s < sides; s++) {
            double ang = 2.0 * Math.PI * s / sides;
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            Vector3 radial = new Vector3(t1).scl(cos).add(new Vector3(t2).scl(sin)).nor();
            addVertex(v, a.x + radial.x * ra, a.y + radial.y * ra, a.z + radial.z * ra, radial, bounds);
            addVertex(v, b.x + radial.x * rb, b.y + radial.y * rb, b.z + radial.z * rb, radial, bounds);
        }
        for (int s = 0; s < sides; s++) {
            int sn = (s + 1) % sides;
            short b0 = (short) (base + 2 * s), t0 = (short) (base + 2 * s + 1);
            short b1 = (short) (base + 2 * sn), tt = (short) (base + 2 * sn + 1);
            idx.add(b0); idx.add(t0); idx.add(b1);
            idx.add(b1); idx.add(t0); idx.add(tt);
        }
    }

    /** Low-poly UV-sphere foliage blob centred at c. */
    private static void addBlob(FloatArray v, ShortArray idx, Vector3 c, float r,
                                int rings, int sectors, BoundingBox bounds) {
        int base = v.size / 6;
        for (int ri = 0; ri <= rings; ri++) {
            double phi = Math.PI * ri / rings;
            float y = (float) Math.cos(phi), rad = (float) Math.sin(phi);
            for (int si = 0; si <= sectors; si++) {
                double theta = 2.0 * Math.PI * si / sectors;
                float x = rad * (float) Math.cos(theta), z = rad * (float) Math.sin(theta);
                Vector3 nrm = new Vector3(x, y, z); // already unit
                addVertex(v, c.x + x * r, c.y + y * r, c.z + z * r, nrm, bounds);
            }
        }
        int stride = sectors + 1;
        for (int ri = 0; ri < rings; ri++) {
            for (int si = 0; si < sectors; si++) {
                short p0 = (short) (base + ri * stride + si);
                short p1 = (short) (base + (ri + 1) * stride + si);
                short p2 = (short) (base + ri * stride + si + 1);
                short p3 = (short) (base + (ri + 1) * stride + si + 1);
                idx.add(p0); idx.add(p1); idx.add(p2);
                idx.add(p2); idx.add(p1); idx.add(p3);
            }
        }
    }

    private static void addVertex(FloatArray v, float x, float y, float z, Vector3 n, BoundingBox bounds) {
        v.add(x); v.add(y); v.add(z);
        v.add(n.x); v.add(n.y); v.add(n.z);
        bounds.ext(x, y, z);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.gen.FloraMeshBuilderTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/gen/FloraMeshData.java core/src/main/java/com/galacticodyssey/flora/gen/FloraMeshBuilder.java core/src/test/java/com/galacticodyssey/flora/gen/FloraMeshBuilderTest.java
git commit -m "feat(flora): GL-free flora mesh builder (tubes + foliage blobs)"
```

---

## Task 8: FloraModelFactory (mesh data → libGDX Model)

GL-bound — no unit test (a `Mesh` needs a GL context). Correctness is confirmed in the Task 12 visual check. This task is implement + compile only.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/flora/gen/FloraModelFactory.java`

- [ ] **Step 1: Implement FloraModelFactory**

```java
package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

/** Uploads {@link FloraMeshData} into a libGDX {@link Model}. Requires a GL context. */
public final class FloraModelFactory {
    private static final VertexAttributes ATTRS = new VertexAttributes(
        new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
        new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"));

    private FloraModelFactory() {}

    /** Builds a Model with a trunk part (+ foliage part when present). Caller owns disposal. */
    public static Model toModel(FloraMeshData data, Color trunkColor, Color foliageColor) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.part("trunk", makeMesh(data.trunkVertices, data.trunkIndices),
            GL20.GL_TRIANGLES, new Material(ColorAttribute.createDiffuse(trunkColor)));
        if (data.hasFoliage()) {
            mb.part("foliage", makeMesh(data.foliageVertices, data.foliageIndices),
                GL20.GL_TRIANGLES, new Material(ColorAttribute.createDiffuse(foliageColor)));
        }
        return mb.end();
    }

    private static Mesh makeMesh(float[] vertices, short[] indices) {
        Mesh mesh = new Mesh(true, vertices.length / 6, indices.length, ATTRS);
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/gen/FloraModelFactory.java
git commit -m "feat(flora): FloraModelFactory uploads mesh data to a Model"
```

---

## Task 9: FloraPlacement + FloraGenerator.planPlacements (pure, testable)

This is the deterministic placement decision — no GL. The GL parts (prototype building, instance creation) come in Task 10.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/flora/FloraPlacement.java`
- Create: `core/src/main/java/com/galacticodyssey/flora/FloraGenerator.java` (partial — `planPlacements` only)
- Test: `core/src/test/java/com/galacticodyssey/flora/FloraGeneratorPlacementTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.flora;

import com.galacticodyssey.flora.data.FloraRegistry;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FloraGeneratorPlacementTest {
    private static final String SPECIES = "{ \"species\": [" +
        "{ \"id\": \"tree\", \"shape\": \"ELLIPSOID\", \"prototypeVariants\": 4 } ] }";
    private static final String PALETTES = "{ \"palettes\": [" +
        "{ \"biome\": \"TROPICAL_FOREST\", \"density\": 1.0, \"species\": [ { \"id\": \"tree\", \"weight\": 1 } ] }," +
        "{ \"biome\": \"DESERT\", \"density\": 0.0, \"species\": [] } ] }";

    private static FloraRegistry registry() {
        FloraRegistry r = new FloraRegistry();
        r.loadSpecies(SPECIES);
        r.loadPalettes(PALETTES);
        return r;
    }

    /** Flat terrain at height 1.0, all above the sea level we pass (0.0). */
    private static float[] flatHeightmap(int verts) {
        float[] h = new float[verts * verts];
        java.util.Arrays.fill(h, 1.0f);
        return h;
    }

    private static BiomeType[] uniformBiome(int verts, BiomeType b) {
        BiomeType[] g = new BiomeType[verts * verts];
        java.util.Arrays.fill(g, b);
        return g;
    }

    @Test
    void placesInForestNotInDesert() {
        int v = 33;
        float[] hm = flatHeightmap(v);
        FloraRegistry reg = registry();

        List<FloraPlacement> forest = FloraGenerator.planPlacements(
            reg, uniformBiome(v, BiomeType.TROPICAL_FOREST), hm, v, v, 100f, 100f, 0f, 999L, 300);
        List<FloraPlacement> desert = FloraGenerator.planPlacements(
            reg, uniformBiome(v, BiomeType.DESERT), hm, v, v, 100f, 100f, 0f, 999L, 300);

        assertFalse(forest.isEmpty(), "density 1.0 forest should place trees");
        assertTrue(desert.isEmpty(), "density 0 desert should place nothing");
        for (FloraPlacement p : forest) {
            assertEquals("tree", p.speciesId);
            assertTrue(p.variantIndex >= 0 && p.variantIndex < 4);
            assertTrue(p.scale > 0f);
        }
    }

    @Test
    void isDeterministic() {
        int v = 33;
        float[] hm = flatHeightmap(v);
        FloraRegistry reg = registry();
        List<FloraPlacement> a = FloraGenerator.planPlacements(
            reg, uniformBiome(v, BiomeType.TROPICAL_FOREST), hm, v, v, 100f, 100f, 0f, 5L, 300);
        List<FloraPlacement> b = FloraGenerator.planPlacements(
            reg, uniformBiome(v, BiomeType.TROPICAL_FOREST), hm, v, v, 100f, 100f, 0f, 5L, 300);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).x, b.get(i).x);
            assertEquals(a.get(i).z, b.get(i).z);
            assertEquals(a.get(i).variantIndex, b.get(i).variantIndex);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.FloraGeneratorPlacementTest"`
Expected: FAIL — classes not defined.

- [ ] **Step 3: Implement FloraPlacement**

```java
package com.galacticodyssey.flora;

/** One resolved flora instance: which species/variant, where, and how oriented/scaled. */
public final class FloraPlacement {
    public final String speciesId;
    public final int variantIndex;
    public final float x, y, z;
    public final float yawDeg;
    public final float scale;

    public FloraPlacement(String speciesId, int variantIndex, float x, float y, float z,
                          float yawDeg, float scale) {
        this.speciesId = speciesId;
        this.variantIndex = variantIndex;
        this.x = x; this.y = y; this.z = z;
        this.yawDeg = yawDeg;
        this.scale = scale;
    }
}
```

- [ ] **Step 4: Implement FloraGenerator with planPlacements only**

```java
package com.galacticodyssey.flora;

import com.galacticodyssey.data.TerrainGenerator;
import com.galacticodyssey.flora.data.BiomePalette;
import com.galacticodyssey.flora.data.FloraRegistry;
import com.galacticodyssey.flora.data.FloraSpecies;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Orchestrates flora: deterministic placement planning + (Task 10) prototype/instance building. */
public final class FloraGenerator {
    private FloraGenerator() {}

    /**
     * Pure, GL-free placement decision: where flora goes, what species/variant, transform.
     * Mirrors the old WorldPopulator.placeTrees scatter, but data-driven and seeded via FLORA_DOMAIN.
     */
    public static List<FloraPlacement> planPlacements(
            FloraRegistry registry, BiomeType[] biomeGrid, float[] heightmap,
            int vertsX, int vertsZ, float worldWidth, float worldDepth,
            float seaLevel, long planetSeed, int attempts) {

        List<FloraPlacement> out = new ArrayList<>();
        long floraSeed = SeedDeriver.floraDomain(planetSeed);
        Random rng = new Random(floraSeed);
        float halfW = worldWidth / 2f, halfD = worldDepth / 2f;

        for (int i = 0; i < attempts; i++) {
            float wx = (rng.nextFloat() - 0.5f) * worldWidth * 0.9f;
            float wz = (rng.nextFloat() - 0.5f) * worldDepth * 0.9f;
            float h = TerrainGenerator.getHeightAt(heightmap, vertsX, vertsZ, worldWidth, worldDepth, wx, wz);
            if (h < seaLevel + 0.5f) continue;

            int gx = clamp((int) ((wx + halfW) / worldWidth * (vertsX - 1)), 0, vertsX - 1);
            int gz = clamp((int) ((wz + halfD) / worldDepth * (vertsZ - 1)), 0, vertsZ - 1);
            BiomeType biome = biomeGrid[gz * vertsX + gx];

            BiomePalette palette = registry.palette(biome);
            if (palette == null || palette.isEmpty()) continue;
            if (rng.nextFloat() > palette.density) continue;

            String speciesId = palette.pickSpecies(rng);
            if (speciesId == null) continue;
            FloraSpecies sp = registry.species(speciesId);
            if (sp == null) continue;

            int variant = rng.nextInt(Math.max(1, sp.prototypeVariants));
            float yaw = rng.nextFloat() * 360f;
            float scale = 0.85f + rng.nextFloat() * 0.4f;
            out.add(new FloraPlacement(speciesId, variant, wx, h, wz, yaw, scale));
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // buildPrototypes() + populate() added in Task 10
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.flora.FloraGeneratorPlacementTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/FloraPlacement.java core/src/main/java/com/galacticodyssey/flora/FloraGenerator.java core/src/test/java/com/galacticodyssey/flora/FloraGeneratorPlacementTest.java
git commit -m "feat(flora): deterministic data-driven flora placement planning"
```

---

## Task 10: FloraGenerator prototype pool + populate (GL)

GL-bound — no unit test. Implement + compile. Verified in Task 12.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/flora/FloraGenerator.java` (add `buildPrototypes`, `populate`)

- [ ] **Step 1: Add the GL methods**

Add the imports at the top of `FloraGenerator.java`:

```java
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.data.WorldPopulator.PopulatedWorld;
import com.galacticodyssey.flora.gen.AttractionEnvelope;
import com.galacticodyssey.flora.gen.BranchSkeleton;
import com.galacticodyssey.flora.gen.FloraMeshBuilder;
import com.galacticodyssey.flora.gen.FloraMeshData;
import com.galacticodyssey.flora.gen.FloraModelFactory;
import com.galacticodyssey.flora.gen.SpaceColonization;
import java.util.HashMap;
import java.util.Map;
```

Replace the `// buildPrototypes() + populate() added in Task 10` comment with:

```java
    /** Builds N prototype Models per species (one pool per planet seed). Requires GL. */
    public static Map<String, Model[]> buildPrototypes(FloraRegistry registry, long planetSeed) {
        long floraSeed = SeedDeriver.floraDomain(planetSeed);
        Map<String, Model[]> pool = new HashMap<>();
        for (FloraSpecies sp : registry.allSpecies()) {
            int variants = Math.max(1, sp.prototypeVariants);
            Model[] models = new Model[variants];
            for (int v = 0; v < variants; v++) {
                long vseed = SeedDeriver.forId(floraSeed, ((long) sp.id.hashCode() << 20) ^ v);
                models[v] = buildOne(sp, vseed);
            }
            pool.put(sp.id, models);
        }
        return pool;
    }

    private static Model buildOne(FloraSpecies sp, long seed) {
        Random rng = new Random(seed);
        float height = sp.heightMin + rng.nextFloat() * (sp.heightMax - sp.heightMin);
        float radius = sp.radiusMin + rng.nextFloat() * (sp.radiusMax - sp.radiusMin);
        Array<Vector3> pts = AttractionEnvelope.generate(sp.shape, height, radius, sp.attractionPoints, rng);

        SpaceColonization.GrowthParams gp = new SpaceColonization.GrowthParams();
        gp.influenceRadius = sp.influenceRadius;
        gp.killDistance = sp.killDistance;
        gp.segmentLength = sp.segmentLength;
        gp.maxNodes = sp.maxNodes;
        BranchSkeleton skel = SpaceColonization.grow(pts, gp, new Random(seed ^ 0x9E3779B9L));

        FloraMeshData mesh = FloraMeshBuilder.build(skel, sp, new Random(seed ^ 0x1234ABCDL));
        Color trunk = jitter(sp.trunkColor, rng, 0.05f);
        Color foliage = lerpColor(sp.foliageColorA, sp.foliageColorB, rng.nextFloat());
        return FloraModelFactory.toModel(mesh, trunk, foliage);
    }

    /**
     * Builds flora ModelInstances into {@code world.treeInstances} and registers prototype
     * Models for disposal via {@code world.addModel}. Requires GL.
     */
    public static void populate(PopulatedWorld world, FloraRegistry registry, float[] heightmap,
                                int vertsX, int vertsZ, float worldWidth, float worldDepth,
                                float seaLevel, long planetSeed) {
        Map<String, Model[]> prototypes = buildPrototypes(registry, planetSeed);
        for (Model[] models : prototypes.values()) {
            for (Model m : models) world.addModel(m);
        }
        List<FloraPlacement> placements = planPlacements(
            registry, world.biomeGrid, heightmap, vertsX, vertsZ, worldWidth, worldDepth,
            seaLevel, planetSeed, 300);

        for (FloraPlacement pl : placements) {
            Model[] variants = prototypes.get(pl.speciesId);
            if (variants == null || variants.length == 0) continue;
            Model proto = variants[pl.variantIndex % variants.length];
            ModelInstance inst = new ModelInstance(proto);
            inst.transform.setToTranslation(pl.x, pl.y, pl.z);
            inst.transform.rotate(Vector3.Y, pl.yawDeg);
            inst.transform.scale(pl.scale, pl.scale, pl.scale);
            world.treeInstances.add(inst);
        }
    }

    private static Color jitter(Color base, Random rng, float amt) {
        float d = (rng.nextFloat() - 0.5f) * 2f * amt;
        return new Color(clamp01(base.r + d), clamp01(base.g + d), clamp01(base.b + d), 1f);
    }

    private static Color lerpColor(Color a, Color b, float t) {
        return new Color(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t, 1f);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/flora/FloraGenerator.java
git commit -m "feat(flora): prototype pool generation + scene population"
```

---

## Task 11: Authoring data — species.json + palettes.json

**Files:**
- Create: `core/src/main/resources/data/flora/species.json`
- Create: `core/src/main/resources/data/flora/palettes.json`

- [ ] **Step 1: Create species.json**

```json
{
  "species": [
    { "id": "tropical_canopy", "displayName": "Canopy Tree", "shape": "ELLIPSOID",
      "height": [9, 15], "radius": [3, 5],
      "growth": { "attractionPoints": 240, "influenceRadius": 4.5, "killDistance": 0.8, "segmentLength": 0.5, "maxNodes": 650 },
      "trunk": { "sides": 6, "baseRadius": 0.38, "taper": 0.8, "color": "5a3b22" },
      "foliage": { "style": "CLUMP", "clumpsPerTip": 2, "clumpRadius": [1.2, 2.0], "colorA": "1f6b1a", "colorB": "2f8a24" },
      "prototypeVariants": 8 },

    { "id": "tropical_understory", "displayName": "Understory Palm", "shape": "ELLIPSOID",
      "height": [3, 6], "radius": [1.5, 2.5],
      "growth": { "attractionPoints": 90, "influenceRadius": 3.0, "killDistance": 0.6, "segmentLength": 0.4, "maxNodes": 250 },
      "trunk": { "sides": 5, "baseRadius": 0.2, "taper": 0.85, "color": "4a3a20" },
      "foliage": { "style": "CLUMP", "clumpsPerTip": 1, "clumpRadius": [0.8, 1.3], "colorA": "2a7a2a", "colorB": "3f9a3a" },
      "prototypeVariants": 6 },

    { "id": "temperate_broadleaf", "displayName": "Broadleaf", "shape": "ELLIPSOID",
      "height": [7, 12], "radius": [2.5, 4],
      "growth": { "attractionPoints": 200, "influenceRadius": 4.0, "killDistance": 0.75, "segmentLength": 0.45, "maxNodes": 550 },
      "trunk": { "sides": 6, "baseRadius": 0.32, "taper": 0.78, "color": "5b4126" },
      "foliage": { "style": "CLUMP", "clumpsPerTip": 1, "clumpRadius": [1.1, 1.8], "colorA": "2a6b1f", "colorB": "4a8a2a" },
      "prototypeVariants": 8 },

    { "id": "boreal_conifer", "displayName": "Conifer", "shape": "CONE",
      "height": [8, 14], "radius": [2, 3],
      "growth": { "attractionPoints": 180, "influenceRadius": 3.5, "killDistance": 0.7, "segmentLength": 0.45, "maxNodes": 500 },
      "trunk": { "sides": 5, "baseRadius": 0.28, "taper": 0.7, "color": "4a3422" },
      "foliage": { "style": "CLUMP", "clumpsPerTip": 1, "clumpRadius": [0.9, 1.4], "colorA": "1a4a22", "colorB": "245a2a" },
      "prototypeVariants": 7 },

    { "id": "savanna_acacia", "displayName": "Acacia", "shape": "DOME",
      "height": [5, 8], "radius": [3, 5],
      "growth": { "attractionPoints": 160, "influenceRadius": 4.0, "killDistance": 0.7, "segmentLength": 0.5, "maxNodes": 420 },
      "trunk": { "sides": 6, "baseRadius": 0.3, "taper": 0.82, "color": "6a4a28" },
      "foliage": { "style": "CLUMP", "clumpsPerTip": 2, "clumpRadius": [1.4, 2.2], "colorA": "5a6b22", "colorB": "6f7a2a" },
      "prototypeVariants": 6 },

    { "id": "desert_cactus", "displayName": "Column Cactus", "shape": "COLUMN",
      "height": [2, 4.5], "radius": [0.4, 0.7],
      "growth": { "attractionPoints": 50, "influenceRadius": 2.5, "killDistance": 0.5, "segmentLength": 0.35, "maxNodes": 120 },
      "trunk": { "sides": 8, "baseRadius": 0.25, "taper": 0.95, "color": "3a6a32" },
      "foliage": { "style": "NONE" },
      "prototypeVariants": 5 },

    { "id": "arid_succulent", "displayName": "Succulent", "shape": "DOME",
      "height": [0.6, 1.4], "radius": [0.6, 1.0],
      "growth": { "attractionPoints": 40, "influenceRadius": 2.0, "killDistance": 0.4, "segmentLength": 0.3, "maxNodes": 90 },
      "trunk": { "sides": 6, "baseRadius": 0.18, "taper": 0.9, "color": "4a5a30" },
      "foliage": { "style": "CLUMP", "clumpsPerTip": 1, "clumpRadius": [0.4, 0.7], "colorA": "5a7a3a", "colorB": "6a8a45" },
      "prototypeVariants": 5 },

    { "id": "tundra_shrub", "displayName": "Dwarf Shrub", "shape": "DOME",
      "height": [0.5, 1.2], "radius": [0.8, 1.4],
      "growth": { "attractionPoints": 60, "influenceRadius": 2.0, "killDistance": 0.4, "segmentLength": 0.3, "maxNodes": 140 },
      "trunk": { "sides": 5, "baseRadius": 0.12, "taper": 0.85, "color": "4a3a2a" },
      "foliage": { "style": "CLUMP", "clumpsPerTip": 1, "clumpRadius": [0.4, 0.8], "colorA": "4a5a32", "colorB": "5a6540" },
      "prototypeVariants": 5 },

    { "id": "lichen_mound", "displayName": "Lichen Mound", "shape": "DOME",
      "height": [0.2, 0.5], "radius": [0.6, 1.1],
      "growth": { "attractionPoints": 30, "influenceRadius": 1.5, "killDistance": 0.35, "segmentLength": 0.25, "maxNodes": 60 },
      "trunk": { "sides": 5, "baseRadius": 0.08, "taper": 0.9, "color": "5a5a40" },
      "foliage": { "style": "CLUMP", "clumpsPerTip": 1, "clumpRadius": [0.3, 0.6], "colorA": "7a7a55", "colorB": "8a8a60" },
      "prototypeVariants": 4 }
  ]
}
```

- [ ] **Step 2: Create palettes.json (every BiomeType)**

```json
{
  "palettes": [
    { "biome": "TROPICAL_FOREST", "density": 0.9, "tintJitter": 0.08,
      "species": [ { "id": "tropical_canopy", "weight": 0.6 }, { "id": "tropical_understory", "weight": 0.4 } ] },
    { "biome": "TEMPERATE_FOREST", "density": 0.85, "tintJitter": 0.07,
      "species": [ { "id": "temperate_broadleaf", "weight": 0.7 }, { "id": "boreal_conifer", "weight": 0.3 } ] },
    { "biome": "BOREAL_FOREST", "density": 0.8, "tintJitter": 0.06,
      "species": [ { "id": "boreal_conifer", "weight": 0.85 }, { "id": "tundra_shrub", "weight": 0.15 } ] },
    { "biome": "SWAMP", "density": 0.5, "tintJitter": 0.08,
      "species": [ { "id": "temperate_broadleaf", "weight": 0.5 }, { "id": "tropical_understory", "weight": 0.5 } ] },
    { "biome": "SAVANNA", "density": 0.2, "tintJitter": 0.06,
      "species": [ { "id": "savanna_acacia", "weight": 1.0 } ] },
    { "biome": "GRASSLAND", "density": 0.08, "tintJitter": 0.06,
      "species": [ { "id": "savanna_acacia", "weight": 0.6 }, { "id": "temperate_broadleaf", "weight": 0.4 } ] },
    { "biome": "STEPPE", "density": 0.06, "tintJitter": 0.06,
      "species": [ { "id": "arid_succulent", "weight": 0.6 }, { "id": "tundra_shrub", "weight": 0.4 } ] },
    { "biome": "ARID_SHRUB", "density": 0.2, "tintJitter": 0.07,
      "species": [ { "id": "arid_succulent", "weight": 0.7 }, { "id": "desert_cactus", "weight": 0.3 } ] },
    { "biome": "DESERT", "density": 0.12, "tintJitter": 0.05,
      "species": [ { "id": "desert_cactus", "weight": 0.7 }, { "id": "arid_succulent", "weight": 0.3 } ] },
    { "biome": "TUNDRA", "density": 0.15, "tintJitter": 0.05,
      "species": [ { "id": "tundra_shrub", "weight": 0.6 }, { "id": "lichen_mound", "weight": 0.4 } ] },
    { "biome": "POLAR_DESERT", "density": 0.05, "tintJitter": 0.04,
      "species": [ { "id": "lichen_mound", "weight": 1.0 } ] },
    { "biome": "ICE_FIELD", "density": 0.02, "tintJitter": 0.04,
      "species": [ { "id": "lichen_mound", "weight": 1.0 } ] },
    { "biome": "BADLANDS", "density": 0.04, "tintJitter": 0.06,
      "species": [ { "id": "desert_cactus", "weight": 0.5 }, { "id": "arid_succulent", "weight": 0.5 } ] },
    { "biome": "ROCKY_WASTE", "density": 0.03, "tintJitter": 0.05,
      "species": [ { "id": "tundra_shrub", "weight": 1.0 } ] },
    { "biome": "VOLCANIC", "density": 0.0, "species": [] },
    { "biome": "ICE_SHEET", "density": 0.0, "species": [] },
    { "biome": "OCEAN", "density": 0.0, "species": [] },
    { "biome": "LAKE", "density": 0.0, "species": [] },
    { "biome": "RIVER", "density": 0.0, "species": [] }
  ]
}
```

- [ ] **Step 3: Sanity-check the JSON parses (reuse the registry test harness)**

Add a temporary check by running the existing registry tests — they don't read these files, so instead verify via a quick one-off: confirm the files are valid JSON with a parser. Run:

```bash
node -e "JSON.parse(require('fs').readFileSync('core/src/main/resources/data/flora/species.json','utf8')); JSON.parse(require('fs').readFileSync('core/src/main/resources/data/flora/palettes.json','utf8')); console.log('OK')"
```
Expected: `OK` (if Node isn't available, skip — Task 12 loads them through libGDX and will surface any parse error).

- [ ] **Step 4: Commit**

```bash
git add core/src/main/resources/data/flora/species.json core/src/main/resources/data/flora/palettes.json
git commit -m "feat(flora): starter species set + per-biome palettes"
```

---

## Task 12: Integrate into WorldPopulator + visual verification

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/data/WorldPopulator.java`

- [ ] **Step 1: Add imports and a shared registry field**

At the top of `WorldPopulator.java`, add:

```java
import com.galacticodyssey.flora.FloraGenerator;
import com.galacticodyssey.flora.data.FloraRegistry;
```

Add a lazily-loaded static registry field inside the class (after `private WorldPopulator() {}`):

```java
    private static FloraRegistry floraRegistry;

    private static FloraRegistry floraRegistry() {
        if (floraRegistry == null) {
            floraRegistry = new FloraRegistry();
            floraRegistry.load("data/flora/species.json", "data/flora/palettes.json");
        }
        return floraRegistry;
    }
```

- [ ] **Step 2: Replace the placeTrees call**

In `populate(...)`, replace this line (around line 106):

```java
        placeTrees(world, mb, attrs, heightmap, biomeGrid, vertsX, vertsZ, worldWidth, worldDepth, rng, seaLevel);
```

with:

```java
        FloraGenerator.populate(world, floraRegistry(), heightmap, vertsX, vertsZ,
            worldWidth, worldDepth, seaLevel, seed);
```

- [ ] **Step 3: Delete the dead box-tree code**

Delete these three now-unused private methods entirely: `placeTrees` (lines ~259-306), `treeDensity` (lines ~308-319), and `canopyColorForBiome` (lines ~321-331).

- [ ] **Step 4: Verify the whole module compiles and all tests pass**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL — all flora tests plus the existing suite pass. (Note: `WorldPopulatorColorTest` does not touch trees, so it must still pass.)

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/WorldPopulator.java
git commit -m "feat(flora): replace box trees with space-colonization flora generator"
```

- [ ] **Step 6: Visual verification (run the game)**

Use the `run-galactic-odyssey` skill to launch the game, walk onto a planet surface, and screenshot. Confirm:
- Trees show branching trunks with foliage clumps (not boxes).
- Forest biomes are densely vegetated; desert shows cacti/succulents; tundra shows low shrubs/lichen.
- No crash on scene load (registry + GL upload path works).

If foliage/trunk colours don't render through the deferred gbuffer (objects appear untinted/black), the fallback is to confirm the gbuffer shader honours `ColorAttribute.Diffuse` — the existing rocks/grass use the same material colour path, so this should already work; investigate the shader binding only if rocks also render wrong.

---

## Task 13: Update memory + final verification

**Files:** none (memory + git)

- [ ] **Step 1: Full test sweep**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Record progress in memory**

Create `C:\Users\james\.claude\projects\C--Users-james-IdeaProjects-SpaceGame\memory\project_flora-generation.md` describing Cycle A as DONE (package `com.galacticodyssey.flora`, space colonization, prototype pool, JSON species + palettes) and Cycles B (grass instancing) / C (modular alien plants) as pending. Add an index line to `MEMORY.md`. Link `[[project_creature-generation]]`.

- [ ] **Step 3: Final commit (if memory is tracked in-repo; otherwise skip)**

Memory lives outside the repo, so no repo commit is needed for Step 2. The branch is ready for the `finishing-a-development-branch` skill.

---

## Self-Review

**Spec coverage:**
- Space-colonization branching → Tasks 5, 6. ✓
- Low-poly foliage clumps → Task 7 (`addBlob`). ✓
- Prototype pool per species → Task 10 (`buildPrototypes`). ✓
- JSON-driven species + biome palettes → Tasks 3, 4, 11. ✓
- Determinism via `SeedDeriver` FLORA_DOMAIN → Tasks 1, 9, 10. ✓
- GL-free testable mesh core + thin GL upload → Tasks 7 (tested) vs 8 (GL). ✓
- Replace box trees, keep render path, remove old density methods → Task 12. ✓
- Starter species set covering biome groups → Task 11. ✓
- Non-collidable (no collision work) → honoured (no collision code added). ✓
- Headless JUnit coverage of generation core; GL verified visually → Tasks 1,3,4,5,6,7,9 (tests) + 8,10,12 (visual). ✓
- Out-of-scope items (instancing, grass, alien plants, wind, LOD) → not implemented. ✓

**Placeholder scan:** No "TBD"/"add error handling"/"similar to". The two intentional in-code comment markers (`// loadPalettes() added in Task 4`, `// buildPrototypes() + populate() added in Task 10`) are explicit staged-build instructions, each resolved in a later task. ✓

**Type consistency:** `FloraRegistry.species(id)`/`palette(biome)`/`allSpecies()`, `BiomePalette.pickSpecies(Random)`/`isEmpty()`/`density`/`entries`, `SpaceColonization.grow(Array<Vector3>, GrowthParams, Random)`, `BranchSkeleton.size()/position/parent/relRadius/isTip`, `FloraMeshBuilder.build(BranchSkeleton, FloraSpecies, Random)`, `FloraMeshData.{trunk,foliage}{Vertices,Indices}/bounds/hasFoliage()`, `FloraModelFactory.toModel(FloraMeshData, Color, Color)`, `FloraGenerator.planPlacements(...)/buildPrototypes(...)/populate(...)`, `FloraPlacement.{speciesId,variantIndex,x,y,z,yawDeg,scale}` — all consistent across tasks. `WorldPopulator.PopulatedWorld.{biomeGrid,treeInstances,addModel}` match the existing class. ✓
