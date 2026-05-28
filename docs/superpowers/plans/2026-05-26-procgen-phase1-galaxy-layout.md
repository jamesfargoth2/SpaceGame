# Phase 1 — Seed Infrastructure + Galaxy Layout: SeedDeriver, GalaxyConfig, density fields, star placement, chunked streaming. Deliverable: a galaxy map you can zoom into with thousands of stars.

# Phase 2 — Star Systems: Stellar classification, companion stars, habitable zones, orbital slots, planet type assignment, moons, asteroid belts, hazard zones. Deliverable: entering a star reveals its system with orbiting planets.

# Phase 3 — Planets (Terrain + Atmosphere + Biomes): Cubemap heightmaps, continent noise, erosion, atmosphere derivation, climate fields, Whittaker biome classification, LOD meshes. Deliverable: approaching a planet shows terrain, sky colour, biomes.

# Phase 4 — Space Objects: Asteroid shape generation, crater imprinting, space station module assembly, dungeon/interior generation for stations. Deliverable: asteroid fields to mine, stations to dock at and explore inside.

# Phase 5 — Content Layer: Faction territory (Voronoi), political relations, trade lanes, patrol routes, procedural name generation for everything. Deliverable: the galaxy feels inhabited — factions own territory, things have names.

# Phase 1: Seed Infrastructure + Galaxy Layout — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a deterministic seed hierarchy and chunk-streamed galaxy of ~1,000,000 stars in a spiral pattern, with nebulae and region classification.

**Architecture:** A new `galaxy` package under `com.galacticodyssey`. `GalaxyManager` sits above `CoordinateManager`, owning galaxy-scale data. Stars are generated lazily per-chunk via rejection sampling on a spiral density field. All generation flows from a single galaxy seed through `SeedDeriver`.

**Tech Stack:** Java 21, libGDX 1.13.5 (`MathUtils`, `Array`, `Json`, `Color`), JUnit 5. No GL context required for any logic.

**Spec:** `docs/superpowers/specs/2026-05-26-procgen-phase1-galaxy-layout-design.md`

---

## File Map

```
NEW FILES:
  core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java
  core/src/main/java/com/galacticodyssey/galaxy/RngUtil.java
  core/src/main/java/com/galacticodyssey/galaxy/GalaxyType.java
  core/src/main/java/com/galacticodyssey/galaxy/GalaxyConfig.java
  core/src/main/java/com/galacticodyssey/galaxy/SimplexNoise.java
  core/src/main/java/com/galacticodyssey/galaxy/GalaxyNoise.java
  core/src/main/java/com/galacticodyssey/galaxy/GalaxyDensityField.java
  core/src/main/java/com/galacticodyssey/galaxy/ChunkKey.java
  core/src/main/java/com/galacticodyssey/galaxy/StarPosition.java
  core/src/main/java/com/galacticodyssey/galaxy/GalaxyChunk.java
  core/src/main/java/com/galacticodyssey/galaxy/GalaxyChunkManager.java
  core/src/main/java/com/galacticodyssey/galaxy/NebulaType.java
  core/src/main/java/com/galacticodyssey/galaxy/NebulaRegion.java
  core/src/main/java/com/galacticodyssey/galaxy/NebulaPlacer.java
  core/src/main/java/com/galacticodyssey/galaxy/GalaxyRegion.java
  core/src/main/java/com/galacticodyssey/galaxy/GalaxyRegionClassifier.java
  core/src/main/java/com/galacticodyssey/galaxy/GalaxyManager.java
  core/src/main/resources/data/galaxy/galaxy_config.json

  core/src/test/java/com/galacticodyssey/galaxy/SeedDeriverTest.java
  core/src/test/java/com/galacticodyssey/galaxy/RngUtilTest.java
  core/src/test/java/com/galacticodyssey/galaxy/GalaxyNoiseTest.java
  core/src/test/java/com/galacticodyssey/galaxy/GalaxyDensityFieldTest.java
  core/src/test/java/com/galacticodyssey/galaxy/ChunkKeyTest.java
  core/src/test/java/com/galacticodyssey/galaxy/GalaxyChunkManagerTest.java
  core/src/test/java/com/galacticodyssey/galaxy/NebulaPlacerTest.java
  core/src/test/java/com/galacticodyssey/galaxy/GalaxyRegionClassifierTest.java
  core/src/test/java/com/galacticodyssey/galaxy/GalaxyManagerIntegrationTest.java
```

---

### Task 1: SeedDeriver + RngUtil

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/RngUtil.java`
- Create: `core/src/test/java/com/galacticodyssey/galaxy/SeedDeriverTest.java`
- Create: `core/src/test/java/com/galacticodyssey/galaxy/RngUtilTest.java`

- [ ] **Step 1: Write SeedDeriver tests**

```java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SeedDeriverTest {

    @Test
    void domainReturnsSameOutputForSameInput() {
        long seed = 12345L;
        long domain = 0x9E3779B97F4A7C15L;
        assertEquals(SeedDeriver.domain(seed, domain), SeedDeriver.domain(seed, domain));
    }

    @Test
    void domainReturnsDifferentOutputForDifferentDomains() {
        long seed = 12345L;
        long domainA = 0x9E3779B97F4A7C15L;
        long domainB = 0x6C62272E07BB0142L;
        assertNotEquals(SeedDeriver.domain(seed, domainA), SeedDeriver.domain(seed, domainB));
    }

    @Test
    void forIdReturnsSameOutputForSameInput() {
        long domainSeed = 99999L;
        assertEquals(SeedDeriver.forId(domainSeed, 42), SeedDeriver.forId(domainSeed, 42));
    }

    @Test
    void forIdReturnsDifferentOutputForDifferentIds() {
        long domainSeed = 99999L;
        assertNotEquals(SeedDeriver.forId(domainSeed, 0), SeedDeriver.forId(domainSeed, 1));
    }

    @Test
    void forChunkReturnsSameOutputForSameCoordinates() {
        long domainSeed = 77777L;
        assertEquals(SeedDeriver.forChunk(domainSeed, 5, 10), SeedDeriver.forChunk(domainSeed, 5, 10));
    }

    @Test
    void forChunkReturnsDifferentOutputForDifferentCoordinates() {
        long domainSeed = 77777L;
        assertNotEquals(SeedDeriver.forChunk(domainSeed, 0, 0), SeedDeriver.forChunk(domainSeed, 1, 0));
        assertNotEquals(SeedDeriver.forChunk(domainSeed, 0, 0), SeedDeriver.forChunk(domainSeed, 0, 1));
    }

    @Test
    void forChunkHandlesNegativeCoordinates() {
        long domainSeed = 77777L;
        long pos = SeedDeriver.forChunk(domainSeed, 5, 5);
        long neg = SeedDeriver.forChunk(domainSeed, -5, -5);
        assertNotEquals(pos, neg);
    }

    @Test
    void allDomainConstantsAreUnique() {
        long seed = 1L;
        Set<Long> derived = new HashSet<>();
        derived.add(SeedDeriver.starDomain(seed));
        derived.add(SeedDeriver.nebulaDomain(seed));
        assertEquals(2, derived.size(), "Domain constants must produce unique seeds");
    }

    @Test
    void mixProducesGoodDistribution() {
        int buckets = 256;
        int[] counts = new int[buckets];
        int samples = 10000;
        for (int i = 0; i < samples; i++) {
            long seed = SeedDeriver.forId(42L, i);
            int bucket = (int) (Math.abs(seed) % buckets);
            counts[bucket]++;
        }
        float expected = samples / (float) buckets;
        for (int count : counts) {
            assertTrue(count > expected * 0.5f && count < expected * 2f,
                "Bucket count " + count + " deviates too far from expected " + expected);
        }
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.SeedDeriverTest" --info`
Expected: Compilation error — `SeedDeriver` does not exist.

- [ ] **Step 3: Implement SeedDeriver**

```java
package com.galacticodyssey.galaxy;

public final class SeedDeriver {

    public static final long STAR_DOMAIN       = 0x6C62272E07BB0142L;
    public static final long PLANET_DOMAIN     = 0x517CC1B727220A95L;
    public static final long MOON_DOMAIN       = 0xBF58476D1CE4E5B9L;
    public static final long TERRAIN_DOMAIN    = 0x94D049BB133111EBL;
    public static final long ATMOSPHERE_DOMAIN = 0xC4CEB9FE1A85EC53L;
    public static final long BIOME_DOMAIN      = 0xD2A98B26625EEE7BL;
    public static final long STATION_DOMAIN    = 0x3C79AC492BA7B653L;
    public static final long INTERIOR_DOMAIN   = 0xE7037ED1A0B428DBL;
    public static final long FACTION_DOMAIN    = 0x4F6CDD1CB33DA28DL;
    public static final long NAME_DOMAIN       = 0x8C4F9B29D25B9E63L;
    public static final long NEBULA_DOMAIN     = 0xA2F9836E4E441529L;

    private SeedDeriver() {}

    public static long domain(long parentSeed, long domainConstant) {
        return mix(parentSeed ^ domainConstant);
    }

    public static long starDomain(long galaxySeed) {
        return domain(galaxySeed, STAR_DOMAIN);
    }

    public static long nebulaDomain(long galaxySeed) {
        return domain(galaxySeed, NEBULA_DOMAIN);
    }

    public static long forId(long domainSeed, long id) {
        return mix(domainSeed ^ id);
    }

    public static long forChunk(long domainSeed, int cx, int cy) {
        long h = domainSeed;
        h ^= ((long) cx) * 0x9E3779B97F4A7C15L;
        h ^= ((long) cy) * 0x6C62272E07BB0142L;
        return mix(h);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
```

- [ ] **Step 4: Run SeedDeriver tests — verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.SeedDeriverTest" --info`
Expected: All pass.

- [ ] **Step 5: Write RngUtil tests**

```java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RngUtilTest {

    @Test
    void floatRangeReturnsValueInBounds() {
        Random rng = new Random(42L);
        for (int i = 0; i < 1000; i++) {
            float v = RngUtil.range(rng, 5f, 10f);
            assertTrue(v >= 5f && v < 10f, "Value " + v + " out of range [5, 10)");
        }
    }

    @Test
    void floatRangeIsDeterministic() {
        Random rng1 = new Random(42L);
        Random rng2 = new Random(42L);
        for (int i = 0; i < 100; i++) {
            assertEquals(RngUtil.range(rng1, 0f, 1f), RngUtil.range(rng2, 0f, 1f));
        }
    }

    @Test
    void intRangeReturnsValueInBounds() {
        Random rng = new Random(42L);
        for (int i = 0; i < 1000; i++) {
            int v = RngUtil.range(rng, 3, 7);
            assertTrue(v >= 3 && v < 7, "Value " + v + " out of range [3, 7)");
        }
    }
}
```

- [ ] **Step 6: Implement RngUtil**

```java
package com.galacticodyssey.galaxy;

import java.util.Random;

public final class RngUtil {

    private RngUtil() {}

    public static float range(Random rng, float min, float max) {
        return min + rng.nextFloat() * (max - min);
    }

    public static int range(Random rng, int min, int maxExclusive) {
        return min + rng.nextInt(maxExclusive - min);
    }
}
```

- [ ] **Step 7: Run all tests — verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.*" --info`
Expected: All pass.

- [ ] **Step 8: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java \
        core/src/main/java/com/galacticodyssey/galaxy/RngUtil.java \
        core/src/test/java/com/galacticodyssey/galaxy/SeedDeriverTest.java \
        core/src/test/java/com/galacticodyssey/galaxy/RngUtilTest.java
git commit -m "feat(galaxy): add SeedDeriver and RngUtil for deterministic procgen"
```

---

### Task 2: GalaxyType + GalaxyConfig + JSON

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/GalaxyType.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/GalaxyConfig.java`
- Create: `core/src/main/resources/data/galaxy/galaxy_config.json`

- [ ] **Step 1: Create GalaxyType enum**

```java
package com.galacticodyssey.galaxy;

public enum GalaxyType {
    SPIRAL,
    BARRED_SPIRAL,
    ELLIPTICAL,
    IRREGULAR
}
```

- [ ] **Step 2: Create GalaxyConfig**

```java
package com.galacticodyssey.galaxy;

public class GalaxyConfig {
    public GalaxyType type = GalaxyType.SPIRAL;
    public int targetStarCount = 1000000;
    public float radiusLY = 50000f;
    public int armCount = 4;
    public float armWindingAngle = 4.0f;
    public float armWidth = 0.15f;
    public float coreDensityFactor = 3.0f;
    public int nebulaCount = 200;
    public float chunkSizeLY = 100f;
    public int maxLoadedChunks = 512;

    public static GalaxyConfig defaults() {
        return new GalaxyConfig();
    }
}
```

- [ ] **Step 3: Create galaxy_config.json**

```json
{
    "type": "SPIRAL",
    "targetStarCount": 1000000,
    "radiusLY": 50000.0,
    "armCount": 4,
    "armWindingAngle": 4.0,
    "armWidth": 0.15,
    "coreDensityFactor": 3.0,
    "nebulaCount": 200,
    "chunkSizeLY": 100.0,
    "maxLoadedChunks": 512
}
```

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/GalaxyType.java \
        core/src/main/java/com/galacticodyssey/galaxy/GalaxyConfig.java \
        core/src/main/resources/data/galaxy/galaxy_config.json
git commit -m "feat(galaxy): add GalaxyType, GalaxyConfig, and default JSON"
```

---

### Task 3: SimplexNoise + GalaxyNoise

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/SimplexNoise.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/GalaxyNoise.java`
- Create: `core/src/test/java/com/galacticodyssey/galaxy/GalaxyNoiseTest.java`

- [ ] **Step 1: Write GalaxyNoise tests**

```java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GalaxyNoiseTest {

    @Test
    void fbmReturnsSameValueForSameInput() {
        GalaxyNoise noise = new GalaxyNoise(42L);
        float a = noise.fbm(1.5f, 2.5f, 4, 0.5f, 2.0f);
        float b = noise.fbm(1.5f, 2.5f, 4, 0.5f, 2.0f);
        assertEquals(a, b, 1e-6f);
    }

    @Test
    void fbmReturnsDifferentValuesForDifferentInputs() {
        GalaxyNoise noise = new GalaxyNoise(42L);
        float a = noise.fbm(0f, 0f, 4, 0.5f, 2.0f);
        float b = noise.fbm(100f, 100f, 4, 0.5f, 2.0f);
        assertNotEquals(a, b, 1e-6f);
    }

    @Test
    void fbmOutputInExpectedRange() {
        GalaxyNoise noise = new GalaxyNoise(42L);
        for (int i = 0; i < 1000; i++) {
            float x = i * 0.37f;
            float y = i * 0.71f;
            float v = noise.fbm(x, y, 6, 0.5f, 2.0f);
            assertTrue(v >= -1.1f && v <= 1.1f,
                "fBm value " + v + " outside expected range at (" + x + "," + y + ")");
        }
    }

    @Test
    void differentSeedsProduceDifferentNoise() {
        GalaxyNoise noiseA = new GalaxyNoise(1L);
        GalaxyNoise noiseB = new GalaxyNoise(2L);
        float a = noiseA.fbm(5f, 5f, 4, 0.5f, 2.0f);
        float b = noiseB.fbm(5f, 5f, 4, 0.5f, 2.0f);
        assertNotEquals(a, b, 1e-6f);
    }

    @Test
    void warpedNoiseReturnsDeterministicValues() {
        GalaxyNoise noise = new GalaxyNoise(42L);
        float a = noise.warpedNoise(1f, 1f, 0.5f);
        float b = noise.warpedNoise(1f, 1f, 0.5f);
        assertEquals(a, b, 1e-6f);
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.GalaxyNoiseTest" --info`
Expected: Compilation error — classes not found.

- [ ] **Step 3: Implement SimplexNoise**

This is a seeded 2D Simplex noise following the pattern from the existing `TerrainGenerator`.

```java
package com.galacticodyssey.galaxy;

import java.util.Random;

public final class SimplexNoise {

    private static final float F2 = 0.5f * ((float) Math.sqrt(3.0) - 1.0f);
    private static final float G2 = (3.0f - (float) Math.sqrt(3.0)) / 6.0f;

    private static final int[][] GRAD2 = {
        {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private final int[] perm = new int[512];

    public SimplexNoise(long seed) {
        int[] base = new int[256];
        for (int i = 0; i < 256; i++) base[i] = i;
        Random rng = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = base[i];
            base[i] = base[j];
            base[j] = tmp;
        }
        for (int i = 0; i < 512; i++) perm[i] = base[i & 255];
    }

    public float noise(float x, float y) {
        float s = (x + y) * F2;
        int i = fastFloor(x + s);
        int j = fastFloor(y + s);

        float t = (i + j) * G2;
        float x0 = x - (i - t);
        float y0 = y - (j - t);

        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; }
        else          { i1 = 0; j1 = 1; }

        float x1 = x0 - i1 + G2;
        float y1 = y0 - j1 + G2;
        float x2 = x0 - 1f + 2f * G2;
        float y2 = y0 - 1f + 2f * G2;

        int ii = i & 255;
        int jj = j & 255;

        float n0 = contribution(x0, y0, ii, jj);
        float n1 = contribution(x1, y1, ii + i1, jj + j1);
        float n2 = contribution(x2, y2, ii + 1, jj + 1);

        return 70f * (n0 + n1 + n2);
    }

    private float contribution(float x, float y, int gi, int gj) {
        float t = 0.5f - x * x - y * y;
        if (t < 0f) return 0f;
        t *= t;
        int[] g = GRAD2[perm[(gi + perm[gj & 255]) & 511] & 7];
        return t * t * (g[0] * x + g[1] * y);
    }

    private static int fastFloor(float x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}
```

- [ ] **Step 4: Implement GalaxyNoise**

```java
package com.galacticodyssey.galaxy;

public class GalaxyNoise {

    private final SimplexNoise simplex;

    public GalaxyNoise(long seed) {
        this.simplex = new SimplexNoise(seed);
    }

    public float fbm(float x, float y, int octaves, float persistence, float lacunarity) {
        float value = 0f;
        float amplitude = 1f;
        float frequency = 1f;
        float maxValue = 0f;
        for (int i = 0; i < octaves; i++) {
            value += simplex.noise(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return value / maxValue;
    }

    public float warpedNoise(float x, float y, float warpStrength) {
        float warpX = fbm(x + 1.7f, y + 9.2f, 4, 0.5f, 2.0f);
        float warpY = fbm(x + 8.3f, y + 2.8f, 4, 0.5f, 2.0f);
        return fbm(x + warpStrength * warpX, y + warpStrength * warpY, 6, 0.5f, 2.0f);
    }
}
```

- [ ] **Step 5: Run tests — verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.GalaxyNoiseTest" --info`
Expected: All pass.

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/SimplexNoise.java \
        core/src/main/java/com/galacticodyssey/galaxy/GalaxyNoise.java \
        core/src/test/java/com/galacticodyssey/galaxy/GalaxyNoiseTest.java
git commit -m "feat(galaxy): add SimplexNoise and GalaxyNoise with fBm and domain warp"
```

---

### Task 4: GalaxyDensityField

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/GalaxyDensityField.java`
- Create: `core/src/test/java/com/galacticodyssey/galaxy/GalaxyDensityFieldTest.java`

- [ ] **Step 1: Write tests**

```java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GalaxyDensityFieldTest {

    private GalaxyDensityField field;
    private GalaxyConfig config;
    private GalaxyNoise noise;

    @BeforeEach
    void setUp() {
        config = GalaxyConfig.defaults();
        noise = new GalaxyNoise(42L);
        field = new GalaxyDensityField();
    }

    @Test
    void coreDensityHigherThanRim() {
        float core = field.density(0f, 0f, config, noise);
        float rim = field.density(0.9f, 0f, config, noise);
        assertTrue(core > rim, "Core density " + core + " should exceed rim density " + rim);
    }

    @Test
    void centreDensityAboveHalf() {
        float centre = field.density(0f, 0f, config, noise);
        assertTrue(centre > 0.5f, "Centre density " + centre + " should be > 0.5");
    }

    @Test
    void densityOutsideDiskIsZero() {
        float outside = field.density(1.5f, 0f, config, noise);
        assertEquals(0f, outside, 1e-5f);
    }

    @Test
    void densityClampedBetweenZeroAndOne() {
        for (float x = -1f; x <= 1f; x += 0.1f) {
            for (float y = -1f; y <= 1f; y += 0.1f) {
                float d = field.density(x, y, config, noise);
                assertTrue(d >= 0f && d <= 1f, "Density " + d + " out of [0,1] at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void densityIsDeterministic() {
        GalaxyNoise noise2 = new GalaxyNoise(42L);
        float a = field.density(0.3f, 0.4f, config, noise);
        float b = field.density(0.3f, 0.4f, config, noise2);
        assertEquals(a, b, 1e-6f);
    }

    @Test
    void spiralArmDenserThanInterArm() {
        // At r=0.5, check density on an arm vs between arms.
        // With 4 arms, arm 0 starts at angle 0. Arm angular separation is PI/2.
        // So angle PI/4 (45 degrees) is between arms.
        float onArm = field.density(0.5f, 0f, config, noise);
        float offArm = field.density(
            0.5f * (float) Math.cos(Math.PI / 4),
            0.5f * (float) Math.sin(Math.PI / 4),
            config, noise);
        assertTrue(onArm > offArm,
            "On-arm density " + onArm + " should exceed off-arm density " + offArm);
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.GalaxyDensityFieldTest" --info`
Expected: Compilation error.

- [ ] **Step 3: Implement GalaxyDensityField**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.math.MathUtils;

public class GalaxyDensityField {

    public float density(float nx, float ny, GalaxyConfig cfg, GalaxyNoise noise) {
        float r = (float) Math.sqrt(nx * nx + ny * ny);
        if (r > 1f) return 0f;

        float angle = MathUtils.atan2(ny, nx);
        float d = spiralDensity(r, angle, cfg);

        float coreBulge = cfg.coreDensityFactor * (float) Math.exp(-r * 4f);
        d = Math.max(0f, d + coreBulge);

        d *= 0.85f + 0.3f * noise.fbm(nx * 3f, ny * 3f, 3, 0.5f, 2f);

        return Math.min(Math.max(d, 0f), 1f);
    }

    private float spiralDensity(float r, float angle, GalaxyConfig cfg) {
        float maxDensity = 0f;
        for (int arm = 0; arm < cfg.armCount; arm++) {
            float armOffset = arm * (MathUtils.PI2 / cfg.armCount);
            float spiralAngle = cfg.armWindingAngle * (float) Math.log(r + 0.1f) + armOffset;
            float angleDiff = Math.abs(normaliseAngle(angle - spiralAngle));
            float armDensity = (float) Math.exp(
                -angleDiff * angleDiff / (2f * cfg.armWidth * cfg.armWidth));
            armDensity *= (1f - r * 0.7f);
            maxDensity = Math.max(maxDensity, armDensity);
        }
        return maxDensity;
    }

    private float normaliseAngle(float a) {
        while (a > MathUtils.PI) a -= MathUtils.PI2;
        while (a < -MathUtils.PI) a += MathUtils.PI2;
        return a;
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.GalaxyDensityFieldTest" --info`
Expected: All pass.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/GalaxyDensityField.java \
        core/src/test/java/com/galacticodyssey/galaxy/GalaxyDensityFieldTest.java
git commit -m "feat(galaxy): add GalaxyDensityField with spiral density and core bulge"
```

---

### Task 5: ChunkKey + StarPosition + GalaxyChunk

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/ChunkKey.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/StarPosition.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/GalaxyChunk.java`
- Create: `core/src/test/java/com/galacticodyssey/galaxy/ChunkKeyTest.java`

- [ ] **Step 1: Write ChunkKey tests**

```java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkKeyTest {

    @Test
    void equalKeysAreEqual() {
        ChunkKey a = new ChunkKey(5, 10);
        ChunkKey b = new ChunkKey(5, 10);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentKeysAreNotEqual() {
        assertNotEquals(new ChunkKey(0, 0), new ChunkKey(1, 0));
        assertNotEquals(new ChunkKey(0, 0), new ChunkKey(0, 1));
    }

    @Test
    void worksAsHashMapKey() {
        Map<ChunkKey, String> map = new HashMap<>();
        map.put(new ChunkKey(1, 2), "hello");
        assertEquals("hello", map.get(new ChunkKey(1, 2)));
        assertNull(map.get(new ChunkKey(1, 3)));
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.ChunkKeyTest" --info`
Expected: Compilation error.

- [ ] **Step 3: Implement ChunkKey**

```java
package com.galacticodyssey.galaxy;

import java.util.Objects;

public final class ChunkKey {

    public final int cx;
    public final int cy;

    public ChunkKey(int cx, int cy) {
        this.cx = cx;
        this.cy = cy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkKey)) return false;
        ChunkKey that = (ChunkKey) o;
        return cx == that.cx && cy == that.cy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cx, cy);
    }

    @Override
    public String toString() {
        return "ChunkKey(" + cx + ", " + cy + ")";
    }
}
```

- [ ] **Step 4: Implement StarPosition**

```java
package com.galacticodyssey.galaxy;

public class StarPosition {

    public long uniqueId;
    public double x;
    public double y;
    public double z;
    public float localDensity;
}
```

- [ ] **Step 5: Implement GalaxyChunk**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.utils.Array;

public class GalaxyChunk {

    public final ChunkKey key;
    public final Array<StarPosition> stars;
    public final double centreX;
    public final double centreY;
    public final float averageDensity;

    public GalaxyChunk(ChunkKey key, Array<StarPosition> stars,
                       double centreX, double centreY, float averageDensity) {
        this.key = key;
        this.stars = stars;
        this.centreX = centreX;
        this.centreY = centreY;
        this.averageDensity = averageDensity;
    }
}
```

- [ ] **Step 6: Run tests — verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.ChunkKeyTest" --info`
Expected: All pass.

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/ChunkKey.java \
        core/src/main/java/com/galacticodyssey/galaxy/StarPosition.java \
        core/src/main/java/com/galacticodyssey/galaxy/GalaxyChunk.java \
        core/src/test/java/com/galacticodyssey/galaxy/ChunkKeyTest.java
git commit -m "feat(galaxy): add ChunkKey, StarPosition, and GalaxyChunk data structures"
```

---

### Task 6: GalaxyChunkManager

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/GalaxyChunkManager.java`
- Create: `core/src/test/java/com/galacticodyssey/galaxy/GalaxyChunkManagerTest.java`

- [ ] **Step 1: Write tests**

```java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GalaxyChunkManagerTest {

    private GalaxyChunkManager manager;
    private GalaxyConfig config;

    @BeforeEach
    void setUp() {
        config = GalaxyConfig.defaults();
        config.targetStarCount = 10000;
        config.radiusLY = 5000f;
        config.chunkSizeLY = 100f;
        config.maxLoadedChunks = 16;
        long starDomain = SeedDeriver.starDomain(42L);
        manager = new GalaxyChunkManager(config, starDomain);
    }

    @Test
    void sameChunkCoordinatesProduceSameStars() {
        GalaxyChunk a = manager.getOrGenerate(0, 0);
        long starDomain = SeedDeriver.starDomain(42L);
        GalaxyChunkManager manager2 = new GalaxyChunkManager(config, starDomain);
        GalaxyChunk b = manager2.getOrGenerate(0, 0);

        assertEquals(a.stars.size, b.stars.size);
        for (int i = 0; i < a.stars.size; i++) {
            assertEquals(a.stars.get(i).uniqueId, b.stars.get(i).uniqueId);
            assertEquals(a.stars.get(i).x, b.stars.get(i).x, 1e-6);
            assertEquals(a.stars.get(i).y, b.stars.get(i).y, 1e-6);
            assertEquals(a.stars.get(i).z, b.stars.get(i).z, 1e-6);
        }
    }

    @Test
    void differentChunkCoordinatesProduceDifferentStars() {
        GalaxyChunk a = manager.getOrGenerate(0, 0);
        GalaxyChunk b = manager.getOrGenerate(5, 5);
        if (a.stars.size > 0 && b.stars.size > 0) {
            assertNotEquals(a.stars.get(0).uniqueId, b.stars.get(0).uniqueId);
        }
    }

    @Test
    void starsAreWithinChunkBounds() {
        int cx = 3, cy = 4;
        GalaxyChunk chunk = manager.getOrGenerate(cx, cy);
        double minX = cx * config.chunkSizeLY;
        double maxX = (cx + 1) * config.chunkSizeLY;
        double minY = cy * config.chunkSizeLY;
        double maxY = (cy + 1) * config.chunkSizeLY;
        for (StarPosition star : chunk.stars) {
            assertTrue(star.x >= minX && star.x < maxX,
                "Star x=" + star.x + " outside chunk bounds [" + minX + "," + maxX + ")");
            assertTrue(star.y >= minY && star.y < maxY,
                "Star y=" + star.y + " outside chunk bounds [" + minY + "," + maxY + ")");
        }
    }

    @Test
    void chunkOutsideGalaxyDiskHasNoStars() {
        int farCX = (int)(config.radiusLY * 2 / config.chunkSizeLY);
        GalaxyChunk chunk = manager.getOrGenerate(farCX, farCX);
        assertEquals(0, chunk.stars.size);
    }

    @Test
    void getOrGenerateCachesChunks() {
        GalaxyChunk a = manager.getOrGenerate(1, 1);
        GalaxyChunk b = manager.getOrGenerate(1, 1);
        assertSame(a, b);
    }

    @Test
    void unloadDistantChunksRemovesChunks() {
        manager.getOrGenerate(0, 0);
        manager.getOrGenerate(100, 100);
        manager.unloadDistantChunks(0, 0, 500);
        assertEquals(1, manager.getLoadedChunkCount());
    }

    @Test
    void evictsWhenOverCapacity() {
        for (int i = 0; i < config.maxLoadedChunks + 5; i++) {
            manager.getOrGenerate(i, 0);
        }
        assertTrue(manager.getLoadedChunkCount() <= config.maxLoadedChunks);
    }

    @Test
    void coreChunksHaveStars() {
        GalaxyChunk core = manager.getOrGenerate(0, 0);
        assertTrue(core.stars.size > 0, "Core chunk at (0,0) should have stars");
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.GalaxyChunkManagerTest" --info`
Expected: Compilation error.

- [ ] **Step 3: Implement GalaxyChunkManager**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class GalaxyChunkManager {

    private final Map<ChunkKey, GalaxyChunk> loaded = new LinkedHashMap<>();
    private final GalaxyConfig config;
    private final GalaxyDensityField densityField;
    private final long starDomainSeed;

    public GalaxyChunkManager(GalaxyConfig config, long starDomainSeed) {
        this.config = config;
        this.starDomainSeed = starDomainSeed;
        this.densityField = new GalaxyDensityField();
    }

    public GalaxyChunk getOrGenerate(int cx, int cy) {
        ChunkKey key = new ChunkKey(cx, cy);
        GalaxyChunk cached = loaded.get(key);
        if (cached != null) return cached;

        GalaxyChunk chunk = generateChunk(cx, cy);
        loaded.put(key, chunk);
        evictIfOverCapacity();
        return chunk;
    }

    private GalaxyChunk generateChunk(int cx, int cy) {
        long chunkSeed = SeedDeriver.forChunk(starDomainSeed, cx, cy);
        Random rng = new Random(chunkSeed);
        GalaxyNoise noise = new GalaxyNoise(chunkSeed);

        float chunkWorldX = cx * config.chunkSizeLY;
        float chunkWorldY = cy * config.chunkSizeLY;

        float centreNX = (chunkWorldX + config.chunkSizeLY * 0.5f) / config.radiusLY;
        float centreNY = (chunkWorldY + config.chunkSizeLY * 0.5f) / config.radiusLY;

        if (centreNX * centreNX + centreNY * centreNY > 1.5f * 1.5f) {
            return new GalaxyChunk(new ChunkKey(cx, cy), new Array<>(0),
                chunkWorldX + config.chunkSizeLY * 0.5,
                chunkWorldY + config.chunkSizeLY * 0.5, 0f);
        }

        float avgDensity = densityField.density(centreNX, centreNY, config, noise);

        float chunkArea = config.chunkSizeLY * config.chunkSizeLY;
        float galaxyArea = MathUtils.PI * config.radiusLY * config.radiusLY;
        int expectedStars = (int)(config.targetStarCount * (chunkArea / galaxyArea) * avgDensity * 4f);

        Array<StarPosition> stars = new Array<>();
        int maxAttempts = Math.max(expectedStars * 5, 1);
        int attempts = 0;

        while (stars.size < expectedStars && attempts < maxAttempts) {
            attempts++;
            float localX = rng.nextFloat() * config.chunkSizeLY;
            float localY = rng.nextFloat() * config.chunkSizeLY;

            float worldX = chunkWorldX + localX;
            float worldY = chunkWorldY + localY;
            float nx = worldX / config.radiusLY;
            float ny = worldY / config.radiusLY;

            if (nx * nx + ny * ny > 1f) continue;

            float d = densityField.density(nx, ny, config, noise);
            if (rng.nextFloat() > d) continue;

            float r = (float) Math.sqrt(nx * nx + ny * ny);
            float zScale = 0.02f + 0.01f * (1f - r);
            float z = (float)(rng.nextGaussian() * zScale) * config.radiusLY;

            StarPosition star = new StarPosition();
            star.uniqueId = SeedDeriver.forId(chunkSeed, stars.size);
            star.x = worldX;
            star.y = worldY;
            star.z = z;
            star.localDensity = d;
            stars.add(star);
        }

        return new GalaxyChunk(new ChunkKey(cx, cy), stars,
            chunkWorldX + config.chunkSizeLY * 0.5,
            chunkWorldY + config.chunkSizeLY * 0.5,
            avgDensity);
    }

    public void loadChunksAround(double viewCentreX, double viewCentreY, float radiusLY) {
        int minCX = (int) Math.floor((viewCentreX - radiusLY) / config.chunkSizeLY);
        int maxCX = (int) Math.ceil((viewCentreX + radiusLY) / config.chunkSizeLY);
        int minCY = (int) Math.floor((viewCentreY - radiusLY) / config.chunkSizeLY);
        int maxCY = (int) Math.ceil((viewCentreY + radiusLY) / config.chunkSizeLY);

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cy = minCY; cy <= maxCY; cy++) {
                getOrGenerate(cx, cy);
            }
        }
    }

    public void unloadDistantChunks(double viewCentreX, double viewCentreY, float unloadRadiusLY) {
        loaded.entrySet().removeIf(e -> {
            GalaxyChunk c = e.getValue();
            double dx = c.centreX - viewCentreX;
            double dy = c.centreY - viewCentreY;
            return Math.sqrt(dx * dx + dy * dy) > unloadRadiusLY;
        });
    }

    public Iterable<GalaxyChunk> getLoadedChunks() {
        return loaded.values();
    }

    public int getLoadedChunkCount() {
        return loaded.size();
    }

    private void evictIfOverCapacity() {
        while (loaded.size() > config.maxLoadedChunks) {
            Iterator<ChunkKey> it = loaded.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }
}
```

- [ ] **Step 4: Run tests — verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.GalaxyChunkManagerTest" --info`
Expected: All pass.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/GalaxyChunkManager.java \
        core/src/test/java/com/galacticodyssey/galaxy/GalaxyChunkManagerTest.java
git commit -m "feat(galaxy): add GalaxyChunkManager with lazy generation and LRU eviction"
```

---

### Task 7: NebulaType + NebulaRegion + NebulaPlacer

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/NebulaType.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/NebulaRegion.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/NebulaPlacer.java`
- Create: `core/src/test/java/com/galacticodyssey/galaxy/NebulaPlacerTest.java`

- [ ] **Step 1: Write tests**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NebulaPlacerTest {

    private GalaxyConfig config;
    private NebulaPlacer placer;

    @BeforeEach
    void setUp() {
        config = GalaxyConfig.defaults();
        config.nebulaCount = 20;
        config.radiusLY = 5000f;
        placer = new NebulaPlacer();
    }

    @Test
    void placesCorrectNumberOfNebulae() {
        Array<NebulaRegion> nebulae = placer.place(config, 42L);
        assertEquals(config.nebulaCount, nebulae.size);
    }

    @Test
    void nebulaePlacedWithinGalaxyDisk() {
        Array<NebulaRegion> nebulae = placer.place(config, 42L);
        for (NebulaRegion n : nebulae) {
            double dist = Math.sqrt(n.centreX * n.centreX + n.centreY * n.centreY);
            assertTrue(dist < config.radiusLY * 1.5f,
                "Nebula at distance " + dist + " too far from galaxy centre");
        }
    }

    @Test
    void allNebulaTypesRepresented() {
        config.nebulaCount = 200;
        Array<NebulaRegion> nebulae = placer.place(config, 42L);
        boolean[] seen = new boolean[NebulaType.values().length];
        for (NebulaRegion n : nebulae) {
            seen[n.type.ordinal()] = true;
        }
        for (NebulaType type : NebulaType.values()) {
            assertTrue(seen[type.ordinal()], "Nebula type " + type + " never appeared");
        }
    }

    @Test
    void nebulaeAreDeterministic() {
        Array<NebulaRegion> a = placer.place(config, 42L);
        Array<NebulaRegion> b = placer.place(config, 42L);
        assertEquals(a.size, b.size);
        for (int i = 0; i < a.size; i++) {
            assertEquals(a.get(i).centreX, b.get(i).centreX, 1e-6);
            assertEquals(a.get(i).centreY, b.get(i).centreY, 1e-6);
            assertEquals(a.get(i).type, b.get(i).type);
        }
    }

    @Test
    void nebulaeHavePositiveRadius() {
        Array<NebulaRegion> nebulae = placer.place(config, 42L);
        for (NebulaRegion n : nebulae) {
            assertTrue(n.radiusLY > 0f, "Nebula radius must be positive");
        }
    }

    @Test
    void nebulaeHaveNonNullColour() {
        Array<NebulaRegion> nebulae = placer.place(config, 42L);
        for (NebulaRegion n : nebulae) {
            assertNotNull(n.colour);
        }
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.NebulaPlacerTest" --info`
Expected: Compilation error.

- [ ] **Step 3: Implement NebulaType**

```java
package com.galacticodyssey.galaxy;

public enum NebulaType {
    EMISSION,
    REFLECTION,
    DARK,
    PLANETARY
}
```

- [ ] **Step 4: Implement NebulaRegion**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;

public class NebulaRegion {

    public double centreX;
    public double centreY;
    public float radiusLY;
    public NebulaType type;
    public Color colour;
}
```

- [ ] **Step 5: Implement NebulaPlacer**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;

import java.util.Random;

public class NebulaPlacer {

    public Array<NebulaRegion> place(GalaxyConfig cfg, long galaxySeed) {
        long nebulaSeed = SeedDeriver.domain(galaxySeed, SeedDeriver.NEBULA_DOMAIN);
        Random rng = new Random(nebulaSeed);
        GalaxyNoise noise = new GalaxyNoise(nebulaSeed);
        GalaxyDensityField density = new GalaxyDensityField();

        Array<NebulaRegion> nebulae = new Array<>();

        for (int i = 0; i < cfg.nebulaCount; i++) {
            float nx, ny;
            int attempts = 0;
            do {
                nx = rng.nextFloat() * 2f - 1f;
                ny = rng.nextFloat() * 2f - 1f;
                attempts++;
            } while (density.density(nx, ny, cfg, noise) < 0.4f && attempts < 100);

            NebulaRegion n = new NebulaRegion();
            n.centreX = nx * cfg.radiusLY;
            n.centreY = ny * cfg.radiusLY;
            n.radiusLY = cfg.radiusLY * RngUtil.range(rng, 0.02f, 0.08f);
            n.type = NebulaType.values()[rng.nextInt(NebulaType.values().length)];
            n.colour = nebulaColour(n.type, rng);
            nebulae.add(n);
        }
        return nebulae;
    }

    private Color nebulaColour(NebulaType type, Random rng) {
        switch (type) {
            case EMISSION:   return new Color(1f, 0.2f + rng.nextFloat() * 0.3f, 0.1f, 0.6f);
            case REFLECTION: return new Color(0.2f, 0.4f, 1f, 0.5f);
            case DARK:       return new Color(0.05f, 0.05f, 0.05f, 0.8f);
            case PLANETARY:  return new Color(0.3f, 1f, 0.5f, 0.4f);
            default:         return new Color(0.8f, 0.8f, 0.8f, 0.3f);
        }
    }
}
```

- [ ] **Step 6: Run tests — verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.NebulaPlacerTest" --info`
Expected: All pass.

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/NebulaType.java \
        core/src/main/java/com/galacticodyssey/galaxy/NebulaRegion.java \
        core/src/main/java/com/galacticodyssey/galaxy/NebulaPlacer.java \
        core/src/test/java/com/galacticodyssey/galaxy/NebulaPlacerTest.java
git commit -m "feat(galaxy): add NebulaPlacer with type-based colours and density anchoring"
```

---

### Task 8: GalaxyRegion + GalaxyRegionClassifier

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/GalaxyRegion.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/GalaxyRegionClassifier.java`
- Create: `core/src/test/java/com/galacticodyssey/galaxy/GalaxyRegionClassifierTest.java`

- [ ] **Step 1: Write tests**

```java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GalaxyRegionClassifierTest {

    private GalaxyRegionClassifier classifier;
    private GalaxyConfig config;

    @BeforeEach
    void setUp() {
        classifier = new GalaxyRegionClassifier();
        config = GalaxyConfig.defaults();
    }

    @Test
    void centreIsCore() {
        assertEquals(GalaxyRegion.CORE, classifier.classify(0, 0, config));
    }

    @Test
    void innerRadiusIsInnerRim() {
        double x = config.radiusLY * 0.2;
        assertEquals(GalaxyRegion.INNER_RIM, classifier.classify(x, 0, config));
    }

    @Test
    void outerRadiusIsOuterRim() {
        double x = config.radiusLY * 0.6;
        assertEquals(GalaxyRegion.OUTER_RIM, classifier.classify(x, 0, config));
    }

    @Test
    void beyondDiskIsVoid() {
        double x = config.radiusLY * 1.5;
        assertEquals(GalaxyRegion.VOID, classifier.classify(x, 0, config));
    }

    @Test
    void classificationUsesRadialDistance() {
        double r = config.radiusLY * 0.2;
        double x = r * Math.cos(Math.PI / 4);
        double y = r * Math.sin(Math.PI / 4);
        assertEquals(GalaxyRegion.INNER_RIM, classifier.classify(x, y, config));
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.GalaxyRegionClassifierTest" --info`
Expected: Compilation error.

- [ ] **Step 3: Implement GalaxyRegion**

```java
package com.galacticodyssey.galaxy;

public enum GalaxyRegion {
    CORE,
    INNER_RIM,
    OUTER_RIM,
    VOID
}
```

- [ ] **Step 4: Implement GalaxyRegionClassifier**

```java
package com.galacticodyssey.galaxy;

public class GalaxyRegionClassifier {

    public GalaxyRegion classify(double x, double y, GalaxyConfig cfg) {
        float nx = (float)(x / cfg.radiusLY);
        float ny = (float)(y / cfg.radiusLY);
        float r = (float) Math.sqrt(nx * nx + ny * ny);

        if (r > 1.0f) return GalaxyRegion.VOID;
        if (r < 0.1f) return GalaxyRegion.CORE;
        if (r < 0.4f) return GalaxyRegion.INNER_RIM;
        return GalaxyRegion.OUTER_RIM;
    }
}
```

- [ ] **Step 5: Run tests — verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.GalaxyRegionClassifierTest" --info`
Expected: All pass.

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/GalaxyRegion.java \
        core/src/main/java/com/galacticodyssey/galaxy/GalaxyRegionClassifier.java \
        core/src/test/java/com/galacticodyssey/galaxy/GalaxyRegionClassifierTest.java
git commit -m "feat(galaxy): add GalaxyRegionClassifier for core/rim/void tagging"
```

---

### Task 9: GalaxyManager

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/GalaxyManager.java`
- Create: `core/src/test/java/com/galacticodyssey/galaxy/GalaxyManagerIntegrationTest.java`

- [ ] **Step 1: Write integration tests**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GalaxyManagerIntegrationTest {

    private GalaxyManager manager;
    private GalaxyConfig config;
    private static final long SEED = 12345L;

    @BeforeEach
    void setUp() {
        config = GalaxyConfig.defaults();
        config.targetStarCount = 10000;
        config.radiusLY = 5000f;
        config.chunkSizeLY = 100f;
        config.maxLoadedChunks = 64;
        config.nebulaCount = 20;
        manager = new GalaxyManager(SEED, config);
    }

    @Test
    void updateViewLoadsStarsNearCentre() {
        manager.updateView(0, 0, 300f);
        List<StarPosition> stars = collectStars();
        assertTrue(stars.size() > 0, "Core view should contain stars");
    }

    @Test
    void coreHasMoreStarsThanRim() {
        manager.updateView(0, 0, 200f);
        int coreStars = collectStars().size();

        GalaxyManager rimManager = new GalaxyManager(SEED, config);
        rimManager.updateView(config.radiusLY * 0.8, 0, 200f);
        int rimStars = collectStars(rimManager).size();

        assertTrue(coreStars > rimStars,
            "Core stars (" + coreStars + ") should exceed rim stars (" + rimStars + ")");
    }

    @Test
    void nebulaeGeneratedOnConstruction() {
        Array<NebulaRegion> nebulae = manager.getNebulae();
        assertEquals(config.nebulaCount, nebulae.size);
    }

    @Test
    void regionClassificationWorks() {
        assertEquals(GalaxyRegion.CORE, manager.getRegion(0, 0));
        assertEquals(GalaxyRegion.VOID, manager.getRegion(config.radiusLY * 2, 0));
    }

    @Test
    void findNearestStarReturnsResult() {
        manager.updateView(0, 0, 300f);
        StarPosition nearest = manager.findNearestStar(0, 0);
        assertNotNull(nearest, "Should find a star near galaxy centre");
    }

    @Test
    void sameSeedProducesIdenticalGalaxy() {
        manager.updateView(0, 0, 300f);
        List<StarPosition> starsA = collectStars();

        GalaxyManager manager2 = new GalaxyManager(SEED, config);
        manager2.updateView(0, 0, 300f);
        List<StarPosition> starsB = collectStars(manager2);

        assertEquals(starsA.size(), starsB.size());
        for (int i = 0; i < starsA.size(); i++) {
            assertEquals(starsA.get(i).uniqueId, starsB.get(i).uniqueId);
        }
    }

    @Test
    void galaxySeedAccessible() {
        assertEquals(SEED, manager.getGalaxySeed());
    }

    private List<StarPosition> collectStars() {
        return collectStars(manager);
    }

    private List<StarPosition> collectStars(GalaxyManager mgr) {
        List<StarPosition> list = new ArrayList<>();
        for (StarPosition s : mgr.getLoadedStars()) {
            list.add(s);
        }
        return list;
    }
}
```

- [ ] **Step 2: Run tests — verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.GalaxyManagerIntegrationTest" --info`
Expected: Compilation error.

- [ ] **Step 3: Implement GalaxyManager**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class GalaxyManager implements Disposable {

    private final long galaxySeed;
    private final GalaxyConfig config;
    private final GalaxyChunkManager chunkManager;
    private final GalaxyRegionClassifier regionClassifier;
    private final Array<NebulaRegion> nebulae;

    public GalaxyManager(long galaxySeed, GalaxyConfig config) {
        this.galaxySeed = galaxySeed;
        this.config = config;
        long starDomain = SeedDeriver.starDomain(galaxySeed);
        this.chunkManager = new GalaxyChunkManager(config, starDomain);
        this.regionClassifier = new GalaxyRegionClassifier();
        this.nebulae = new NebulaPlacer().place(config, galaxySeed);
    }

    public void updateView(double viewCentreX, double viewCentreY, float viewRadiusLY) {
        chunkManager.loadChunksAround(viewCentreX, viewCentreY, viewRadiusLY);
        chunkManager.unloadDistantChunks(viewCentreX, viewCentreY, viewRadiusLY * 2f);
    }

    public Iterable<StarPosition> getLoadedStars() {
        return () -> new StarIterator(chunkManager.getLoadedChunks().iterator());
    }

    public StarPosition findNearestStar(double x, double y) {
        int cx = (int) Math.floor(x / config.chunkSizeLY);
        int cy = (int) Math.floor(y / config.chunkSizeLY);

        StarPosition nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                GalaxyChunk chunk = chunkManager.getOrGenerate(cx + dx, cy + dy);
                for (StarPosition star : chunk.stars) {
                    double ddx = star.x - x;
                    double ddy = star.y - y;
                    double distSq = ddx * ddx + ddy * ddy;
                    if (distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest = star;
                    }
                }
            }
        }
        return nearest;
    }

    public GalaxyRegion getRegion(double x, double y) {
        return regionClassifier.classify(x, y, config);
    }

    public Array<NebulaRegion> getNebulae() {
        return nebulae;
    }

    public long getGalaxySeed() {
        return galaxySeed;
    }

    public GalaxyConfig getConfig() {
        return config;
    }

    @Override
    public void dispose() {
    }

    private static class StarIterator implements Iterator<StarPosition> {
        private final Iterator<GalaxyChunk> chunkIterator;
        private int starIndex;
        private GalaxyChunk currentChunk;

        StarIterator(Iterator<GalaxyChunk> chunkIterator) {
            this.chunkIterator = chunkIterator;
            this.starIndex = 0;
            advance();
        }

        private void advance() {
            while (currentChunk == null || starIndex >= currentChunk.stars.size) {
                if (!chunkIterator.hasNext()) {
                    currentChunk = null;
                    return;
                }
                currentChunk = chunkIterator.next();
                starIndex = 0;
            }
        }

        @Override
        public boolean hasNext() {
            return currentChunk != null && starIndex < currentChunk.stars.size;
        }

        @Override
        public StarPosition next() {
            if (!hasNext()) throw new NoSuchElementException();
            StarPosition star = currentChunk.stars.get(starIndex);
            starIndex++;
            advance();
            return star;
        }
    }
}
```

- [ ] **Step 4: Run integration tests — verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.GalaxyManagerIntegrationTest" --info`
Expected: All pass.

- [ ] **Step 5: Run ALL galaxy tests — verify everything passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.*" --info`
Expected: All tests pass across all test classes.

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/GalaxyManager.java \
        core/src/test/java/com/galacticodyssey/galaxy/GalaxyManagerIntegrationTest.java
git commit -m "feat(galaxy): add GalaxyManager coordinator with star iteration and nearest-star search"
```

---

### Task 10: Full Test Suite + Final Verification

**Files:**
- No new files — verify all existing tests still pass.

- [ ] **Step 1: Run complete project test suite**

Run: `./gradlew :core:test --info`
Expected: ALL tests pass (galaxy tests + existing combat/player/core tests).

- [ ] **Step 2: Verify file structure matches spec**

Run: `find core/src/main/java/com/galacticodyssey/galaxy -name "*.java" | sort`

Expected output (17 files):
```
ChunkKey.java
GalaxyChunk.java
GalaxyChunkManager.java
GalaxyConfig.java
GalaxyDensityField.java
GalaxyManager.java
GalaxyNoise.java
GalaxyRegion.java
GalaxyRegionClassifier.java
GalaxyType.java
NebulaPlacer.java
NebulaRegion.java
NebulaType.java
RngUtil.java
SeedDeriver.java
SimplexNoise.java
StarPosition.java
```

- [ ] **Step 3: Verify JSON data file exists**

Run: `cat core/src/main/resources/data/galaxy/galaxy_config.json`
Expected: Valid JSON with all config fields.

- [ ] **Step 4: Commit any final adjustments**

If tests required any fixes, commit those now:
```
git add -A
git commit -m "test(galaxy): verify full test suite passes with galaxy procgen phase 1"
```
