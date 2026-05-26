# Star-to-Surface Generation Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Phase 2 procedural generation pipeline that transforms galaxy-level star positions into fully explorable planetary surfaces with cube-sphere terrain.

**Architecture:** Waterfall pipeline where each generator is a pure function from (seed + parent data) → output. Six layers: StarSystemGenerator → OrbitalLayoutGenerator → PlanetGenerator → AtmosphereGenerator → BiomeMapper → PlanetTerrainSystem. All deterministic from the existing galaxy seed via SeedDeriver. Lazy-loaded — deeper layers generate only when the player approaches.

**Tech Stack:** Java 21, libGDX 1.13.5, Ashley ECS 1.7.4, gdx-bullet, JUnit 5.11.4, Gradle KTS

**Spec:** `docs/superpowers/specs/2026-05-26-star-to-surface-pipeline-design.md`

---

## File Map

### New Files — `galaxy/` (existing package)

| File | Responsibility |
|------|---------------|
| `galaxy/SpectralClass.java` | Enum: O/B/A/F/G/K/M with temp ranges, frequencies, planet count ranges |
| `galaxy/LuminosityClass.java` | Enum: MAIN_SEQUENCE, GIANT, SUPERGIANT, WHITE_DWARF with frequencies |
| `galaxy/StarSystem.java` | Data model: stellar properties, habitable zone, orbit list |
| `galaxy/StarSystemGenerator.java` | Pure function: (StarPosition, GalaxyRegion) → StarSystem |
| `galaxy/OrbitalZone.java` | Enum: INNER, HABITABLE, OUTER, DEEP |
| `galaxy/OrbitalSlot.java` | Data model: orbit radius, eccentricity, period, zone, planet |
| `galaxy/OrbitalLayoutGenerator.java` | Pure function: StarSystem → List\<OrbitalSlot\> |
| `galaxy/StarSystemCache.java` | LRU cache (64 entries) for generated StarSystems |

### New Files — `planet/` (new package)

| File | Responsibility |
|------|---------------|
| `planet/PlanetType.java` | Enum: MOLTEN through DWARF with radius/gravity/moon ranges |
| `planet/Planet.java` | Data model: type, radius, mass, gravity, day length, tilt, tidal lock |
| `planet/Moon.java` | Data model: same as Planet with constrained type (BARREN/ICE_WORLD) |
| `planet/PlanetGenerator.java` | Pure function: (OrbitalSlot, StarSystem) → Planet with moons |
| `planet/Gas.java` | Enum: N2, O2, CO2, H2O, SO2, HCl, Ar, CH4, H2, He, NH3 |
| `planet/AtmoHazard.java` | Enum: NONE, VACUUM, CRUSHING, TOXIC, CORROSIVE, EXTREME_HEAT, EXTREME_COLD |
| `planet/Atmosphere.java` | Data model: gas composition, pressure, temp, hazards, breathability |
| `planet/AtmosphereGenerator.java` | Pure function: (Planet, StarSystem) → Atmosphere |
| `planet/BiomeType.java` | Enum: 18 biomes with amplitude/ridgeMix terrain profiles |
| `planet/WhittakerGrid.java` | Static lookup: (temperature, moisture) → BiomeType |
| `planet/BiomeMap.java` | Query object: getBiome(lat, lon, elevation), getTemperature, getMoisture |
| `planet/BiomeMapper.java` | Pure function: (Planet, Atmosphere) → BiomeMap |

### New Files — `planet/terrain/` (new subpackage)

| File | Responsibility |
|------|---------------|
| `planet/terrain/CubeFace.java` | Enum: POS_X, NEG_X, POS_Y, NEG_Y, POS_Z, NEG_Z with axis vectors |
| `planet/terrain/CubeSphere.java` | Math: face UV → unit sphere direction, inverse mapping |
| `planet/terrain/TerrainNoiseStack.java` | Layered noise: continent fBm + ridged fBm + detail fBm, biome-shaped |
| `planet/terrain/TerrainMeshBuilder.java` | Builds 33×33 vertex grid with normals, biome colors, edge stitching |
| `planet/terrain/TerrainChunk.java` | Quadtree node: face, depth, UV bounds, mesh, children, collider |
| `planet/terrain/TerrainQuadtree.java` | 6-face quadtree manager: split/merge by screen-space size |
| `planet/terrain/PlanetTerrainSystem.java` | Ashley ECS system: drives quadtree update, async mesh gen |

### New Data Files

| File | Content |
|------|---------|
| `resources/data/planet/spectral_classes.json` | Per-class: frequency, tempMin/Max, planetCountMin/Max, habitableOdds |
| `resources/data/planet/planet_types.json` | Per-type: zones, radiusMin/Max, gravityMin/Max, moonMin/Max, atmo config |
| `resources/data/planet/biome_profiles.json` | Per-biome: amplitude, ridgeMix |

### Test Files

| File | Tests |
|------|-------|
| `test/.../galaxy/StarSystemGeneratorTest.java` | Determinism, spectral distribution, temp/luminosity ranges, region modifiers |
| `test/.../galaxy/OrbitalLayoutGeneratorTest.java` | Determinism, monotonic radii, zone classification, planet count in range |
| `test/.../planet/PlanetGeneratorTest.java` | Determinism, properties within type ranges, moon constraints |
| `test/.../planet/AtmosphereGeneratorTest.java` | Determinism, gas fractions sum to 1.0, pressure in range, hazard derivation |
| `test/.../planet/WhittakerGridTest.java` | Full grid coverage, no gaps, boundary transitions |
| `test/.../planet/BiomeMapperTest.java` | Determinism, allowed biomes per planet type, polar/equatorial correctness |
| `test/.../planet/terrain/CubeSphereTest.java` | Unit vectors, face seam continuity, inverse round-trip |
| `test/.../planet/terrain/TerrainNoiseStackTest.java` | Determinism, height range, biome amplitude scaling |
| `test/.../planet/terrain/TerrainMeshBuilderTest.java` | Vertex count, no degenerate tris, normals outward, edge stitching |
| `test/.../galaxy/StarSystemCacheTest.java` | LRU eviction, cache hits, determinism preserved through cache |
| `test/.../planet/PipelineIntegrationTest.java` | Seed → terrain chunk end-to-end, determinism, no GL context, perf < 50ms |

---

## Task 1: Foundation Enums & JSON Data Files

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/SpectralClass.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/LuminosityClass.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/OrbitalZone.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/PlanetType.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/Gas.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/AtmoHazard.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/BiomeType.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/CubeFace.java`
- Create: `core/src/main/resources/data/planet/spectral_classes.json`
- Create: `core/src/main/resources/data/planet/planet_types.json`
- Create: `core/src/main/resources/data/planet/biome_profiles.json`

- [ ] **Step 1: Create SpectralClass enum**

```java
// core/src/main/java/com/galacticodyssey/galaxy/SpectralClass.java
package com.galacticodyssey.galaxy;

public enum SpectralClass {
    O(0.01f, 30000f, 50000f, 1, 3, 0.05f),
    B(0.02f, 10000f, 30000f, 1, 4, 0.10f),
    A(0.05f, 7500f, 10000f, 2, 5, 0.20f),
    F(0.08f, 6000f, 7500f, 2, 6, 0.40f),
    G(0.12f, 5200f, 6000f, 3, 6, 0.60f),
    K(0.22f, 3700f, 5200f, 2, 6, 0.45f),
    M(0.50f, 2400f, 3700f, 2, 4, 0.25f);

    public final float frequency;
    public final float tempMin;
    public final float tempMax;
    public final int planetCountMin;
    public final int planetCountMax;
    public final float habitableOdds;

    SpectralClass(float frequency, float tempMin, float tempMax,
                  int planetCountMin, int planetCountMax, float habitableOdds) {
        this.frequency = frequency;
        this.tempMin = tempMin;
        this.tempMax = tempMax;
        this.planetCountMin = planetCountMin;
        this.planetCountMax = planetCountMax;
        this.habitableOdds = habitableOdds;
    }

    private static final float[] CUMULATIVE;
    static {
        SpectralClass[] values = values();
        CUMULATIVE = new float[values.length];
        float sum = 0f;
        for (int i = 0; i < values.length; i++) {
            sum += values[i].frequency;
            CUMULATIVE[i] = sum;
        }
    }

    public static SpectralClass fromRoll(float roll) {
        SpectralClass[] values = values();
        for (int i = 0; i < CUMULATIVE.length; i++) {
            if (roll < CUMULATIVE[i]) return values[i];
        }
        return M;
    }
}
```

- [ ] **Step 2: Create LuminosityClass enum**

```java
// core/src/main/java/com/galacticodyssey/galaxy/LuminosityClass.java
package com.galacticodyssey.galaxy;

public enum LuminosityClass {
    MAIN_SEQUENCE(0.85f),
    GIANT(0.08f),
    SUPERGIANT(0.02f),
    WHITE_DWARF(0.05f);

    public final float frequency;

    LuminosityClass(float frequency) {
        this.frequency = frequency;
    }

    private static final float[] CUMULATIVE;
    static {
        LuminosityClass[] values = values();
        CUMULATIVE = new float[values.length];
        float sum = 0f;
        for (int i = 0; i < values.length; i++) {
            sum += values[i].frequency;
            CUMULATIVE[i] = sum;
        }
    }

    public static LuminosityClass fromRoll(float roll) {
        LuminosityClass[] values = values();
        for (int i = 0; i < CUMULATIVE.length; i++) {
            if (roll < CUMULATIVE[i]) return values[i];
        }
        return WHITE_DWARF;
    }
}
```

- [ ] **Step 3: Create OrbitalZone enum**

```java
// core/src/main/java/com/galacticodyssey/galaxy/OrbitalZone.java
package com.galacticodyssey.galaxy;

public enum OrbitalZone {
    INNER,
    HABITABLE,
    OUTER,
    DEEP
}
```

- [ ] **Step 4: Create PlanetType enum**

```java
// core/src/main/java/com/galacticodyssey/planet/PlanetType.java
package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.OrbitalZone;
import java.util.EnumSet;

public enum PlanetType {
    MOLTEN(0.3f, 0.8f, 0.2f, 0.6f, 0, 0, false, EnumSet.of(OrbitalZone.INNER)),
    BARREN(0.4f, 1.2f, 0.3f, 0.9f, 0, 1, false, EnumSet.of(OrbitalZone.INNER, OrbitalZone.HABITABLE)),
    ARID(0.7f, 1.5f, 0.5f, 1.2f, 0, 2, true, EnumSet.of(OrbitalZone.HABITABLE)),
    TERRAN(0.8f, 1.8f, 0.7f, 1.4f, 0, 3, true, EnumSet.of(OrbitalZone.HABITABLE)),
    OCEAN(1.0f, 2.0f, 0.8f, 1.5f, 0, 2, true, EnumSet.of(OrbitalZone.HABITABLE)),
    TOXIC(0.8f, 1.6f, 0.6f, 1.3f, 0, 1, true, EnumSet.of(OrbitalZone.INNER, OrbitalZone.HABITABLE)),
    GAS_GIANT(6f, 15f, 1.5f, 3.0f, 2, 8, false, EnumSet.of(OrbitalZone.OUTER, OrbitalZone.DEEP)),
    ICE_GIANT(3f, 6f, 1.0f, 1.8f, 1, 5, false, EnumSet.of(OrbitalZone.OUTER, OrbitalZone.DEEP)),
    ICE_WORLD(0.3f, 1.0f, 0.2f, 0.7f, 0, 1, false, EnumSet.of(OrbitalZone.OUTER, OrbitalZone.DEEP)),
    DWARF(0.1f, 0.4f, 0.03f, 0.15f, 0, 0, false, EnumSet.of(OrbitalZone.DEEP));

    public final float radiusMin;
    public final float radiusMax;
    public final float gravityMin;
    public final float gravityMax;
    public final int moonMin;
    public final int moonMax;
    public final boolean hasAtmosphere;
    public final EnumSet<OrbitalZone> validZones;

    PlanetType(float radiusMin, float radiusMax, float gravityMin, float gravityMax,
               int moonMin, int moonMax, boolean hasAtmosphere, EnumSet<OrbitalZone> validZones) {
        this.radiusMin = radiusMin;
        this.radiusMax = radiusMax;
        this.gravityMin = gravityMin;
        this.gravityMax = gravityMax;
        this.moonMin = moonMin;
        this.moonMax = moonMax;
        this.hasAtmosphere = hasAtmosphere;
        this.validZones = validZones;
    }

    public boolean hasSurface() {
        return this != GAS_GIANT && this != ICE_GIANT;
    }
}
```

- [ ] **Step 5: Create Gas, AtmoHazard, BiomeType enums**

```java
// core/src/main/java/com/galacticodyssey/planet/Gas.java
package com.galacticodyssey.planet;

public enum Gas {
    N2, O2, CO2, H2O, SO2, HCl, Ar, CH4, H2, He, NH3
}
```

```java
// core/src/main/java/com/galacticodyssey/planet/AtmoHazard.java
package com.galacticodyssey.planet;

public enum AtmoHazard {
    NONE, VACUUM, CRUSHING, TOXIC, CORROSIVE, EXTREME_HEAT, EXTREME_COLD
}
```

```java
// core/src/main/java/com/galacticodyssey/planet/BiomeType.java
package com.galacticodyssey.planet;

public enum BiomeType {
    ICE_SHEET(0.1f, 0.15f),
    TUNDRA(0.25f, 0.2f),
    POLAR_DESERT(0.2f, 0.15f),
    ICE_FIELD(0.15f, 0.2f),
    BOREAL_FOREST(0.35f, 0.4f),
    TEMPERATE_FOREST(0.3f, 0.35f),
    STEPPE(0.2f, 0.1f),
    ROCKY_WASTE(0.5f, 0.8f),
    TROPICAL_FOREST(0.3f, 0.3f),
    GRASSLAND(0.2f, 0.1f),
    ARID_SHRUB(0.2f, 0.15f),
    DESERT(0.15f, 0.05f),
    SWAMP(0.1f, 0.05f),
    SAVANNA(0.2f, 0.15f),
    BADLANDS(0.45f, 0.7f),
    VOLCANIC(0.6f, 0.9f),
    OCEAN(0.05f, 0.1f),
    LAKE(0.05f, 0.05f);

    public final float amplitude;
    public final float ridgeMix;

    BiomeType(float amplitude, float ridgeMix) {
        this.amplitude = amplitude;
        this.ridgeMix = ridgeMix;
    }
}
```

- [ ] **Step 6: Create CubeFace enum**

```java
// core/src/main/java/com/galacticodyssey/planet/terrain/CubeFace.java
package com.galacticodyssey.planet.terrain;

public enum CubeFace {
    POS_X( 1, 0, 0),
    NEG_X(-1, 0, 0),
    POS_Y( 0, 1, 0),
    NEG_Y( 0,-1, 0),
    POS_Z( 0, 0, 1),
    NEG_Z( 0, 0,-1);

    public final int axisX;
    public final int axisY;
    public final int axisZ;

    CubeFace(int axisX, int axisY, int axisZ) {
        this.axisX = axisX;
        this.axisY = axisY;
        this.axisZ = axisZ;
    }
}
```

- [ ] **Step 7: Create JSON data files**

```json
// core/src/main/resources/data/planet/spectral_classes.json
{
    "classes": [
        { "id": "O", "frequency": 0.01, "tempMin": 30000, "tempMax": 50000, "planetCountMin": 1, "planetCountMax": 3, "habitableOdds": 0.05 },
        { "id": "B", "frequency": 0.02, "tempMin": 10000, "tempMax": 30000, "planetCountMin": 1, "planetCountMax": 4, "habitableOdds": 0.10 },
        { "id": "A", "frequency": 0.05, "tempMin": 7500,  "tempMax": 10000, "planetCountMin": 2, "planetCountMax": 5, "habitableOdds": 0.20 },
        { "id": "F", "frequency": 0.08, "tempMin": 6000,  "tempMax": 7500,  "planetCountMin": 2, "planetCountMax": 6, "habitableOdds": 0.40 },
        { "id": "G", "frequency": 0.12, "tempMin": 5200,  "tempMax": 6000,  "planetCountMin": 3, "planetCountMax": 6, "habitableOdds": 0.60 },
        { "id": "K", "frequency": 0.22, "tempMin": 3700,  "tempMax": 5200,  "planetCountMin": 2, "planetCountMax": 6, "habitableOdds": 0.45 },
        { "id": "M", "frequency": 0.50, "tempMin": 2400,  "tempMax": 3700,  "planetCountMin": 2, "planetCountMax": 4, "habitableOdds": 0.25 }
    ]
}
```

```json
// core/src/main/resources/data/planet/planet_types.json
{
    "types": [
        { "id": "MOLTEN",     "radiusMin": 0.3, "radiusMax": 0.8,  "gravityMin": 0.2,  "gravityMax": 0.6,  "moonMin": 0, "moonMax": 0, "hasAtmosphere": false, "zones": ["INNER"] },
        { "id": "BARREN",     "radiusMin": 0.4, "radiusMax": 1.2,  "gravityMin": 0.3,  "gravityMax": 0.9,  "moonMin": 0, "moonMax": 1, "hasAtmosphere": false, "zones": ["INNER", "HABITABLE"] },
        { "id": "ARID",       "radiusMin": 0.7, "radiusMax": 1.5,  "gravityMin": 0.5,  "gravityMax": 1.2,  "moonMin": 0, "moonMax": 2, "hasAtmosphere": true,  "zones": ["HABITABLE"] },
        { "id": "TERRAN",     "radiusMin": 0.8, "radiusMax": 1.8,  "gravityMin": 0.7,  "gravityMax": 1.4,  "moonMin": 0, "moonMax": 3, "hasAtmosphere": true,  "zones": ["HABITABLE"] },
        { "id": "OCEAN",      "radiusMin": 1.0, "radiusMax": 2.0,  "gravityMin": 0.8,  "gravityMax": 1.5,  "moonMin": 0, "moonMax": 2, "hasAtmosphere": true,  "zones": ["HABITABLE"] },
        { "id": "TOXIC",      "radiusMin": 0.8, "radiusMax": 1.6,  "gravityMin": 0.6,  "gravityMax": 1.3,  "moonMin": 0, "moonMax": 1, "hasAtmosphere": true,  "zones": ["INNER", "HABITABLE"] },
        { "id": "GAS_GIANT",  "radiusMin": 6.0, "radiusMax": 15.0, "gravityMin": 1.5,  "gravityMax": 3.0,  "moonMin": 2, "moonMax": 8, "hasAtmosphere": false, "zones": ["OUTER", "DEEP"] },
        { "id": "ICE_GIANT",  "radiusMin": 3.0, "radiusMax": 6.0,  "gravityMin": 1.0,  "gravityMax": 1.8,  "moonMin": 1, "moonMax": 5, "hasAtmosphere": false, "zones": ["OUTER", "DEEP"] },
        { "id": "ICE_WORLD",  "radiusMin": 0.3, "radiusMax": 1.0,  "gravityMin": 0.2,  "gravityMax": 0.7,  "moonMin": 0, "moonMax": 1, "hasAtmosphere": false, "zones": ["OUTER", "DEEP"] },
        { "id": "DWARF",      "radiusMin": 0.1, "radiusMax": 0.4,  "gravityMin": 0.03, "gravityMax": 0.15, "moonMin": 0, "moonMax": 0, "hasAtmosphere": false, "zones": ["DEEP"] }
    ]
}
```

```json
// core/src/main/resources/data/planet/biome_profiles.json
{
    "profiles": [
        { "id": "OCEAN",            "amplitude": 0.05, "ridgeMix": 0.1  },
        { "id": "LAKE",             "amplitude": 0.05, "ridgeMix": 0.05 },
        { "id": "ICE_SHEET",        "amplitude": 0.1,  "ridgeMix": 0.15 },
        { "id": "ICE_FIELD",        "amplitude": 0.15, "ridgeMix": 0.2  },
        { "id": "POLAR_DESERT",     "amplitude": 0.2,  "ridgeMix": 0.15 },
        { "id": "TUNDRA",           "amplitude": 0.25, "ridgeMix": 0.2  },
        { "id": "DESERT",           "amplitude": 0.15, "ridgeMix": 0.05 },
        { "id": "ARID_SHRUB",       "amplitude": 0.2,  "ridgeMix": 0.15 },
        { "id": "STEPPE",           "amplitude": 0.2,  "ridgeMix": 0.1  },
        { "id": "GRASSLAND",        "amplitude": 0.2,  "ridgeMix": 0.1  },
        { "id": "SAVANNA",          "amplitude": 0.2,  "ridgeMix": 0.15 },
        { "id": "SWAMP",            "amplitude": 0.1,  "ridgeMix": 0.05 },
        { "id": "TEMPERATE_FOREST", "amplitude": 0.3,  "ridgeMix": 0.35 },
        { "id": "BOREAL_FOREST",    "amplitude": 0.35, "ridgeMix": 0.4  },
        { "id": "TROPICAL_FOREST",  "amplitude": 0.3,  "ridgeMix": 0.3  },
        { "id": "ROCKY_WASTE",      "amplitude": 0.5,  "ridgeMix": 0.8  },
        { "id": "BADLANDS",         "amplitude": 0.45, "ridgeMix": 0.7  },
        { "id": "VOLCANIC",         "amplitude": 0.6,  "ridgeMix": 0.9  }
    ]
}
```

- [ ] **Step 8: Verify compilation**

Run: `gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/SpectralClass.java \
  core/src/main/java/com/galacticodyssey/galaxy/LuminosityClass.java \
  core/src/main/java/com/galacticodyssey/galaxy/OrbitalZone.java \
  core/src/main/java/com/galacticodyssey/planet/PlanetType.java \
  core/src/main/java/com/galacticodyssey/planet/Gas.java \
  core/src/main/java/com/galacticodyssey/planet/AtmoHazard.java \
  core/src/main/java/com/galacticodyssey/planet/BiomeType.java \
  core/src/main/java/com/galacticodyssey/planet/terrain/CubeFace.java \
  core/src/main/resources/data/planet/spectral_classes.json \
  core/src/main/resources/data/planet/planet_types.json \
  core/src/main/resources/data/planet/biome_profiles.json
git commit -m "feat(planet): add foundation enums and JSON data files for star-to-surface pipeline"
```

---

## Task 2: StarSystem Data Model & StarSystemGenerator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/StarSystem.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/StarSystemGenerator.java`
- Create: `core/src/test/java/com/galacticodyssey/galaxy/StarSystemGeneratorTest.java`

- [ ] **Step 1: Create StarSystem data model**

```java
// core/src/main/java/com/galacticodyssey/galaxy/StarSystem.java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;
import java.util.ArrayList;
import java.util.List;

public final class StarSystem {
    public final long uniqueId;
    public final long seed;
    public final SpectralClass spectralClass;
    public final LuminosityClass luminosityClass;
    public final float temperature;
    public final float luminosity;
    public final float mass;
    public final float radius;
    public final float age;
    public final Color color;
    public final float habZoneInner;
    public final float habZoneOuter;
    public final float frostLine;
    public final float systemEdge;
    public final List<OrbitalSlot> orbits;

    public StarSystem(long uniqueId, long seed, SpectralClass spectralClass,
                      LuminosityClass luminosityClass, float temperature,
                      float luminosity, float mass, float radius, float age,
                      Color color) {
        this.uniqueId = uniqueId;
        this.seed = seed;
        this.spectralClass = spectralClass;
        this.luminosityClass = luminosityClass;
        this.temperature = temperature;
        this.luminosity = luminosity;
        this.mass = mass;
        this.radius = radius;
        this.age = age;
        this.color = color;
        float sqrtLum = (float) Math.sqrt(luminosity);
        this.habZoneInner = sqrtLum * 0.75f;
        this.habZoneOuter = sqrtLum * 1.77f;
        this.frostLine = sqrtLum * 4.85f;
        this.systemEdge = sqrtLum * 40f;
        this.orbits = new ArrayList<>();
    }
}
```

- [ ] **Step 2: Write the failing tests**

```java
// core/src/test/java/com/galacticodyssey/galaxy/StarSystemGeneratorTest.java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StarSystemGeneratorTest {
    private static final long GALAXY_SEED = 42L;
    private StarSystemGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new StarSystemGenerator(GALAXY_SEED);
    }

    @Test
    void sameSeedProducesIdenticalStarSystem() {
        StarPosition pos = makeStarPosition(1001L, 100.0, 200.0, 5.0, 0.5f);
        StarSystem a = generator.generate(pos, GalaxyRegion.INNER_RIM);
        StarSystem b = new StarSystemGenerator(GALAXY_SEED).generate(pos, GalaxyRegion.INNER_RIM);

        assertEquals(a.spectralClass, b.spectralClass);
        assertEquals(a.luminosityClass, b.luminosityClass);
        assertEquals(a.temperature, b.temperature, 1e-6f);
        assertEquals(a.luminosity, b.luminosity, 1e-6f);
        assertEquals(a.mass, b.mass, 1e-6f);
    }

    @Test
    void temperatureWithinSpectralClassRange() {
        for (long id = 0; id < 500; id++) {
            StarPosition pos = makeStarPosition(id, id * 10.0, id * 5.0, 0.0, 0.3f);
            StarSystem sys = generator.generate(pos, GalaxyRegion.INNER_RIM);
            assertTrue(sys.temperature >= sys.spectralClass.tempMin &&
                       sys.temperature <= sys.spectralClass.tempMax,
                "Star " + id + " temp " + sys.temperature +
                " outside range for " + sys.spectralClass);
        }
    }

    @Test
    void luminosityPositive() {
        for (long id = 0; id < 200; id++) {
            StarPosition pos = makeStarPosition(id, id * 7.0, id * 3.0, 0.0, 0.5f);
            StarSystem sys = generator.generate(pos, GalaxyRegion.INNER_RIM);
            assertTrue(sys.luminosity > 0f, "Star " + id + " has non-positive luminosity");
        }
    }

    @Test
    void habitableZoneDerivedFromLuminosity() {
        StarPosition pos = makeStarPosition(999L, 50.0, 50.0, 0.0, 0.5f);
        StarSystem sys = generator.generate(pos, GalaxyRegion.INNER_RIM);
        float sqrtLum = (float) Math.sqrt(sys.luminosity);
        assertEquals(sqrtLum * 0.75f, sys.habZoneInner, 1e-5f);
        assertEquals(sqrtLum * 1.77f, sys.habZoneOuter, 1e-5f);
    }

    @Test
    void spectralClassDistributionOverManySamples() {
        int[] counts = new int[SpectralClass.values().length];
        int total = 10000;
        for (long id = 0; id < total; id++) {
            StarPosition pos = makeStarPosition(id, id * 1.1, id * 0.7, 0.0, 0.5f);
            StarSystem sys = generator.generate(pos, GalaxyRegion.INNER_RIM);
            counts[sys.spectralClass.ordinal()]++;
        }
        for (SpectralClass sc : SpectralClass.values()) {
            float actual = (float) counts[sc.ordinal()] / total;
            float expected = sc.frequency;
            assertTrue(Math.abs(actual - expected) < 0.05f,
                sc + " frequency " + actual + " deviates >5% from expected " + expected);
        }
    }

    @Test
    void coreRegionBoostsHotStars() {
        int coreHotCount = 0;
        int rimHotCount = 0;
        int total = 2000;
        for (long id = 0; id < total; id++) {
            StarPosition pos = makeStarPosition(id, id * 1.3, id * 0.9, 0.0, 0.5f);
            StarSystem core = generator.generate(pos, GalaxyRegion.CORE);
            StarSystem rim = new StarSystemGenerator(GALAXY_SEED).generate(pos, GalaxyRegion.OUTER_RIM);
            if (core.spectralClass == SpectralClass.O || core.spectralClass == SpectralClass.B) coreHotCount++;
            if (rim.spectralClass == SpectralClass.O || rim.spectralClass == SpectralClass.B) rimHotCount++;
        }
        assertTrue(coreHotCount > rimHotCount,
            "Core should have more O/B stars (" + coreHotCount + ") than outer rim (" + rimHotCount + ")");
    }

    private StarPosition makeStarPosition(long id, double x, double y, double z, float density) {
        StarPosition sp = new StarPosition();
        sp.uniqueId = id;
        sp.x = x;
        sp.y = y;
        sp.z = z;
        sp.localDensity = density;
        return sp;
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.StarSystemGeneratorTest" --info`
Expected: FAIL — `StarSystemGenerator` class doesn't exist

- [ ] **Step 4: Implement StarSystemGenerator**

```java
// core/src/main/java/com/galacticodyssey/galaxy/StarSystemGenerator.java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;
import java.util.Random;

public final class StarSystemGenerator {
    private final long galaxySeed;

    public StarSystemGenerator(long galaxySeed) {
        this.galaxySeed = galaxySeed;
    }

    public StarSystem generate(StarPosition star, GalaxyRegion region) {
        long starSeed = SeedDeriver.forId(
            SeedDeriver.domain(galaxySeed, SeedDeriver.STAR_DOMAIN), star.uniqueId);
        Random rng = new Random(starSeed);

        SpectralClass spectral = rollSpectralClass(rng, region);
        LuminosityClass luminosity = LuminosityClass.fromRoll(rng.nextFloat());

        float temperature = RngUtil.range(rng, spectral.tempMin, spectral.tempMax);
        float stellarMass = massFromSpectral(spectral, luminosity, rng);
        float stellarLuminosity = luminosityFromMass(stellarMass, luminosity);
        float stellarRadius = radiusFromMassLuminosity(stellarMass, luminosity, rng);
        float age = RngUtil.range(rng, 0.1f, 13.0f);
        Color color = colorFromTemperature(temperature);

        return new StarSystem(star.uniqueId, starSeed, spectral, luminosity,
            temperature, stellarLuminosity, stellarMass, stellarRadius, age, color);
    }

    private SpectralClass rollSpectralClass(Random rng, GalaxyRegion region) {
        float roll = rng.nextFloat();
        float modifier = switch (region) {
            case CORE -> -0.08f;
            case INNER_RIM -> 0f;
            case OUTER_RIM -> 0.05f;
            case VOID -> 0.10f;
        };
        roll = Math.max(0f, Math.min(0.9999f, roll + modifier));
        return SpectralClass.fromRoll(roll);
    }

    private float massFromSpectral(SpectralClass sc, LuminosityClass lc, Random rng) {
        float baseMass = switch (sc) {
            case O -> RngUtil.range(rng, 16f, 50f);
            case B -> RngUtil.range(rng, 2.1f, 16f);
            case A -> RngUtil.range(rng, 1.4f, 2.1f);
            case F -> RngUtil.range(rng, 1.04f, 1.4f);
            case G -> RngUtil.range(rng, 0.8f, 1.04f);
            case K -> RngUtil.range(rng, 0.45f, 0.8f);
            case M -> RngUtil.range(rng, 0.08f, 0.45f);
        };
        if (lc == LuminosityClass.GIANT) baseMass *= RngUtil.range(rng, 1.5f, 3f);
        if (lc == LuminosityClass.SUPERGIANT) baseMass *= RngUtil.range(rng, 3f, 10f);
        if (lc == LuminosityClass.WHITE_DWARF) baseMass = RngUtil.range(rng, 0.5f, 1.4f);
        return baseMass;
    }

    private float luminosityFromMass(float mass, LuminosityClass lc) {
        if (lc == LuminosityClass.WHITE_DWARF) return 0.001f + mass * 0.01f;
        if (lc == LuminosityClass.GIANT) return (float) Math.pow(mass, 3.5) * 10f;
        if (lc == LuminosityClass.SUPERGIANT) return (float) Math.pow(mass, 3.5) * 100f;
        return (float) Math.pow(mass, 3.5);
    }

    private float radiusFromMassLuminosity(float mass, LuminosityClass lc, Random rng) {
        if (lc == LuminosityClass.WHITE_DWARF) return RngUtil.range(rng, 0.008f, 0.02f);
        if (lc == LuminosityClass.GIANT) return (float) Math.pow(mass, 0.8) * RngUtil.range(rng, 5f, 25f);
        if (lc == LuminosityClass.SUPERGIANT) return (float) Math.pow(mass, 0.8) * RngUtil.range(rng, 30f, 200f);
        return (float) Math.pow(mass, 0.8);
    }

    private Color colorFromTemperature(float tempK) {
        float t = (tempK - 2000f) / 38000f;
        t = Math.max(0f, Math.min(1f, t));
        float r = 1f;
        float g = 0.5f + t * 0.5f;
        float b = 0.3f + t * 0.7f;
        if (t > 0.5f) { r = 1.2f - t * 0.4f; g = 1.1f - t * 0.2f; }
        return new Color(
            Math.min(1f, r), Math.min(1f, g), Math.min(1f, b), 1f);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.StarSystemGeneratorTest" --info`
Expected: All 6 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/StarSystem.java \
  core/src/main/java/com/galacticodyssey/galaxy/StarSystemGenerator.java \
  core/src/test/java/com/galacticodyssey/galaxy/StarSystemGeneratorTest.java
git commit -m "feat(galaxy): add StarSystem data model and generator with TDD"
```

---

## Task 3: OrbitalSlot & OrbitalLayoutGenerator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/OrbitalSlot.java`
- Create: `core/src/main/java/com/galacticodyssey/galaxy/OrbitalLayoutGenerator.java`
- Create: `core/src/test/java/com/galacticodyssey/galaxy/OrbitalLayoutGeneratorTest.java`

- [ ] **Step 1: Create OrbitalSlot data model**

```java
// core/src/main/java/com/galacticodyssey/galaxy/OrbitalSlot.java
package com.galacticodyssey.galaxy;

import com.galacticodyssey.planet.Planet;

public final class OrbitalSlot {
    public final int index;
    public final float orbitalRadius;
    public final float eccentricity;
    public final float orbitalPeriod;
    public final OrbitalZone zone;
    public Planet planet;

    public OrbitalSlot(int index, float orbitalRadius, float eccentricity, OrbitalZone zone) {
        this.index = index;
        this.orbitalRadius = orbitalRadius;
        this.eccentricity = eccentricity;
        this.orbitalPeriod = (float) Math.pow(orbitalRadius, 1.5);
        this.zone = zone;
    }
}
```

- [ ] **Step 2: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/galaxy/OrbitalLayoutGeneratorTest.java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalLayoutGeneratorTest {
    private static final long GALAXY_SEED = 42L;
    private OrbitalLayoutGenerator layoutGen;
    private StarSystemGenerator starGen;

    @BeforeEach
    void setUp() {
        starGen = new StarSystemGenerator(GALAXY_SEED);
        layoutGen = new OrbitalLayoutGenerator();
    }

    @Test
    void sameSeedProducesIdenticalLayout() {
        StarSystem sys = generateTestSystem(100L);
        List<OrbitalSlot> a = layoutGen.generate(sys);
        List<OrbitalSlot> b = new OrbitalLayoutGenerator().generate(sys);

        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).orbitalRadius, b.get(i).orbitalRadius, 1e-6f);
            assertEquals(a.get(i).zone, b.get(i).zone);
        }
    }

    @Test
    void planetCountWithinSpectralClassRange() {
        for (long id = 0; id < 300; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            assertTrue(orbits.size() >= sys.spectralClass.planetCountMin &&
                       orbits.size() <= sys.spectralClass.planetCountMax,
                "Star " + id + " (" + sys.spectralClass + ") has " + orbits.size() +
                " planets, expected " + sys.spectralClass.planetCountMin +
                "-" + sys.spectralClass.planetCountMax);
        }
    }

    @Test
    void orbitalRadiiIncreaseMonotonically() {
        for (long id = 0; id < 200; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (int i = 1; i < orbits.size(); i++) {
                assertTrue(orbits.get(i).orbitalRadius > orbits.get(i - 1).orbitalRadius,
                    "Star " + id + " orbit " + i + " radius " + orbits.get(i).orbitalRadius +
                    " not greater than orbit " + (i - 1) + " radius " + orbits.get(i - 1).orbitalRadius);
            }
        }
    }

    @Test
    void zoneClassificationMatchesHabitableZone() {
        for (long id = 0; id < 200; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (OrbitalSlot slot : orbits) {
                if (slot.orbitalRadius < sys.habZoneInner) {
                    assertEquals(OrbitalZone.INNER, slot.zone,
                        "Star " + id + " orbit at " + slot.orbitalRadius + " AU should be INNER");
                } else if (slot.orbitalRadius <= sys.habZoneOuter) {
                    assertEquals(OrbitalZone.HABITABLE, slot.zone,
                        "Star " + id + " orbit at " + slot.orbitalRadius + " AU should be HABITABLE");
                } else if (slot.orbitalRadius <= sys.frostLine * 3f) {
                    assertEquals(OrbitalZone.OUTER, slot.zone,
                        "Star " + id + " orbit at " + slot.orbitalRadius + " AU should be OUTER");
                } else {
                    assertEquals(OrbitalZone.DEEP, slot.zone,
                        "Star " + id + " orbit at " + slot.orbitalRadius + " AU should be DEEP");
                }
            }
        }
    }

    @Test
    void orbitalPeriodFollowsKeplerThirdLaw() {
        StarSystem sys = generateTestSystem(42L);
        List<OrbitalSlot> orbits = layoutGen.generate(sys);
        for (OrbitalSlot slot : orbits) {
            float expected = (float) Math.pow(slot.orbitalRadius, 1.5);
            assertEquals(expected, slot.orbitalPeriod, 1e-4f);
        }
    }

    private StarSystem generateTestSystem(long id) {
        StarPosition pos = new StarPosition();
        pos.uniqueId = id;
        pos.x = id * 10.0;
        pos.y = id * 5.0;
        pos.z = 0.0;
        pos.localDensity = 0.5f;
        return starGen.generate(pos, GalaxyRegion.INNER_RIM);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.OrbitalLayoutGeneratorTest" --info`
Expected: FAIL — `OrbitalLayoutGenerator` class doesn't exist

- [ ] **Step 4: Implement OrbitalLayoutGenerator**

```java
// core/src/main/java/com/galacticodyssey/galaxy/OrbitalLayoutGenerator.java
package com.galacticodyssey.galaxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class OrbitalLayoutGenerator {

    public List<OrbitalSlot> generate(StarSystem system) {
        Random rng = new Random(system.seed + 1);
        int planetCount = RngUtil.range(rng,
            system.spectralClass.planetCountMin,
            system.spectralClass.planetCountMax + 1);

        float baseAU = 0.2f + rng.nextFloat() * 0.2f;
        List<OrbitalSlot> slots = new ArrayList<>(planetCount);

        for (int i = 0; i < planetCount; i++) {
            float radius = baseAU * (float) Math.pow(1.4f + rng.nextFloat() * 0.8f, i);
            radius *= 0.85f + rng.nextFloat() * 0.3f;
            float eccentricity = rng.nextFloat() * 0.3f;
            OrbitalZone zone = classifyZone(radius, system);

            slots.add(new OrbitalSlot(i, radius, eccentricity, zone));
        }
        return slots;
    }

    private OrbitalZone classifyZone(float radiusAU, StarSystem system) {
        if (radiusAU < system.habZoneInner) return OrbitalZone.INNER;
        if (radiusAU <= system.habZoneOuter) return OrbitalZone.HABITABLE;
        if (radiusAU <= system.frostLine * 3f) return OrbitalZone.OUTER;
        return OrbitalZone.DEEP;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.OrbitalLayoutGeneratorTest" --info`
Expected: All 5 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/OrbitalSlot.java \
  core/src/main/java/com/galacticodyssey/galaxy/OrbitalLayoutGenerator.java \
  core/src/test/java/com/galacticodyssey/galaxy/OrbitalLayoutGeneratorTest.java
git commit -m "feat(galaxy): add OrbitalSlot and OrbitalLayoutGenerator with TDD"
```

---

## Task 4: Planet, Moon & PlanetGenerator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/Planet.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/Moon.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/PlanetGenerator.java`
- Create: `core/src/test/java/com/galacticodyssey/planet/PlanetGeneratorTest.java`

- [ ] **Step 1: Create Planet and Moon data models**

```java
// core/src/main/java/com/galacticodyssey/planet/Planet.java
package com.galacticodyssey.planet;

import java.util.ArrayList;
import java.util.List;

public final class Planet {
    public final long seed;
    public final PlanetType type;
    public final float radius;
    public final float mass;
    public final float surfaceGravity;
    public final float dayLength;
    public final float axialTilt;
    public final boolean tidallyLocked;
    public Atmosphere atmosphere;
    public final List<Moon> moons;

    public Planet(long seed, PlanetType type, float radius, float mass,
                  float dayLength, float axialTilt, boolean tidallyLocked) {
        this.seed = seed;
        this.type = type;
        this.radius = radius;
        this.mass = mass;
        this.surfaceGravity = mass / (radius * radius);
        this.dayLength = dayLength;
        this.axialTilt = axialTilt;
        this.tidallyLocked = tidallyLocked;
        this.moons = new ArrayList<>();
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/planet/Moon.java
package com.galacticodyssey.planet;

public final class Moon {
    public final long seed;
    public final PlanetType type;
    public final float radius;
    public final float mass;
    public final float surfaceGravity;

    public Moon(long seed, PlanetType type, float radius, float mass) {
        this.seed = seed;
        this.type = type;
        this.radius = radius;
        this.mass = mass;
        this.surfaceGravity = mass / (radius * radius);
    }
}
```

- [ ] **Step 2: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/planet/PlanetGeneratorTest.java
package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PlanetGeneratorTest {
    private static final long GALAXY_SEED = 42L;
    private PlanetGenerator planetGen;
    private StarSystemGenerator starGen;
    private OrbitalLayoutGenerator layoutGen;

    @BeforeEach
    void setUp() {
        planetGen = new PlanetGenerator(GALAXY_SEED);
        starGen = new StarSystemGenerator(GALAXY_SEED);
        layoutGen = new OrbitalLayoutGenerator();
    }

    @Test
    void sameSeedProducesIdenticalPlanet() {
        StarSystem sys = generateTestSystem(100L);
        List<OrbitalSlot> orbits = layoutGen.generate(sys);
        OrbitalSlot slot = orbits.get(0);
        Planet a = planetGen.generate(slot, sys);
        Planet b = new PlanetGenerator(GALAXY_SEED).generate(slot, sys);

        assertEquals(a.type, b.type);
        assertEquals(a.radius, b.radius, 1e-6f);
        assertEquals(a.mass, b.mass, 1e-6f);
        assertEquals(a.moons.size(), b.moons.size());
    }

    @Test
    void planetTypeValidForOrbitalZone() {
        for (long id = 0; id < 200; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                slot.planet = p;
                assertTrue(p.type.validZones.contains(slot.zone),
                    "Star " + id + " orbit " + slot.index + ": " + p.type +
                    " not valid for zone " + slot.zone);
            }
        }
    }

    @Test
    void radiusWithinTypeRange() {
        for (long id = 0; id < 200; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                assertTrue(p.radius >= p.type.radiusMin && p.radius <= p.type.radiusMax,
                    "Star " + id + " planet radius " + p.radius +
                    " outside range for " + p.type);
            }
        }
    }

    @Test
    void moonCountWithinTypeRange() {
        for (long id = 0; id < 200; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                assertTrue(p.moons.size() >= p.type.moonMin && p.moons.size() <= p.type.moonMax,
                    "Star " + id + " " + p.type + " has " + p.moons.size() +
                    " moons, expected " + p.type.moonMin + "-" + p.type.moonMax);
            }
        }
    }

    @Test
    void moonsAreBarrenOrIceWorld() {
        for (long id = 0; id < 200; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                for (Moon moon : p.moons) {
                    assertTrue(moon.type == PlanetType.BARREN || moon.type == PlanetType.ICE_WORLD,
                        "Moon type " + moon.type + " should be BARREN or ICE_WORLD");
                }
            }
        }
    }

    @Test
    void gravityDerivedFromMassAndRadius() {
        StarSystem sys = generateTestSystem(42L);
        List<OrbitalSlot> orbits = layoutGen.generate(sys);
        Planet p = planetGen.generate(orbits.get(0), sys);
        assertEquals(p.mass / (p.radius * p.radius), p.surfaceGravity, 1e-5f);
    }

    private StarSystem generateTestSystem(long id) {
        StarPosition pos = new StarPosition();
        pos.uniqueId = id;
        pos.x = id * 10.0;
        pos.y = id * 5.0;
        pos.z = 0.0;
        pos.localDensity = 0.5f;
        return starGen.generate(pos, GalaxyRegion.INNER_RIM);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.planet.PlanetGeneratorTest" --info`
Expected: FAIL — `PlanetGenerator` class doesn't exist

- [ ] **Step 4: Implement PlanetGenerator**

```java
// core/src/main/java/com/galacticodyssey/planet/PlanetGenerator.java
package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.*;
import java.util.Random;

public final class PlanetGenerator {
    private final long galaxySeed;

    public PlanetGenerator(long galaxySeed) {
        this.galaxySeed = galaxySeed;
    }

    public Planet generate(OrbitalSlot slot, StarSystem system) {
        long planetSeed = SeedDeriver.forId(
            SeedDeriver.domain(system.seed, SeedDeriver.PLANET_DOMAIN), slot.index);
        Random rng = new Random(planetSeed);

        PlanetType type = rollPlanetType(rng, slot.zone);
        float radius = RngUtil.range(rng, type.radiusMin, type.radiusMax);
        float mass = radius * radius * RngUtil.range(rng, 0.7f, 1.3f);
        float dayLength = RngUtil.range(rng, 5f, 2000f);
        float axialTilt = rng.nextFloat() * 45f;

        boolean tidallyLocked = false;
        if (slot.zone == OrbitalZone.INNER && system.spectralClass == SpectralClass.M) {
            tidallyLocked = rng.nextFloat() < 0.7f;
        } else if (slot.zone == OrbitalZone.INNER) {
            tidallyLocked = rng.nextFloat() < 0.3f;
        }
        if (tidallyLocked) dayLength = slot.orbitalPeriod * 24f * 365.25f;

        Planet planet = new Planet(planetSeed, type, radius, mass, dayLength, axialTilt, tidallyLocked);

        int moonCount = (type.moonMax > type.moonMin)
            ? RngUtil.range(rng, type.moonMin, type.moonMax + 1)
            : type.moonMin;
        for (int m = 0; m < moonCount; m++) {
            long moonSeed = SeedDeriver.forId(
                SeedDeriver.domain(planetSeed, SeedDeriver.MOON_DOMAIN), m);
            Random moonRng = new Random(moonSeed);
            PlanetType moonType = moonRng.nextFloat() < 0.5f ? PlanetType.BARREN : PlanetType.ICE_WORLD;
            float moonRadius = RngUtil.range(moonRng, 0.05f, radius * 0.3f);
            float moonMass = moonRadius * moonRadius * RngUtil.range(moonRng, 0.5f, 1.0f);
            planet.moons.add(new Moon(moonSeed, moonType, moonRadius, moonMass));
        }

        return planet;
    }

    private PlanetType rollPlanetType(Random rng, OrbitalZone zone) {
        PlanetType[] candidates = PlanetType.values();
        float totalWeight = 0f;
        float[] weights = new float[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            weights[i] = candidates[i].validZones.contains(zone) ? 1f : 0f;
            totalWeight += weights[i];
        }
        float roll = rng.nextFloat() * totalWeight;
        float cumulative = 0f;
        for (int i = 0; i < candidates.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return candidates[i];
        }
        return PlanetType.BARREN;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.planet.PlanetGeneratorTest" --info`
Expected: All 6 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/Planet.java \
  core/src/main/java/com/galacticodyssey/planet/Moon.java \
  core/src/main/java/com/galacticodyssey/planet/PlanetGenerator.java \
  core/src/test/java/com/galacticodyssey/planet/PlanetGeneratorTest.java
git commit -m "feat(planet): add Planet, Moon data models and PlanetGenerator with TDD"
```

---

## Task 5: Atmosphere & AtmosphereGenerator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/Atmosphere.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/AtmosphereGenerator.java`
- Create: `core/src/test/java/com/galacticodyssey/planet/AtmosphereGeneratorTest.java`

- [ ] **Step 1: Create Atmosphere data model**

```java
// core/src/main/java/com/galacticodyssey/planet/Atmosphere.java
package com.galacticodyssey.planet;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class Atmosphere {
    public final Map<Gas, Float> composition;
    public final float surfacePressure;
    public final float greenhouseMultiplier;
    public final float equilibriumTemp;
    public final float surfaceTemp;
    public final boolean breathable;
    public final EnumSet<AtmoHazard> hazards;

    public Atmosphere(Map<Gas, Float> composition, float surfacePressure,
                      float greenhouseMultiplier, float equilibriumTemp,
                      float surfaceTemp, boolean breathable,
                      EnumSet<AtmoHazard> hazards) {
        this.composition = new EnumMap<>(composition);
        this.surfacePressure = surfacePressure;
        this.greenhouseMultiplier = greenhouseMultiplier;
        this.equilibriumTemp = equilibriumTemp;
        this.surfaceTemp = surfaceTemp;
        this.breathable = breathable;
        this.hazards = hazards;
    }

    public float getTemperatureAtLatitude(float latRadians) {
        float sinLat = (float) Math.sin(latRadians);
        return surfaceTemp * (1.0f - 0.4f * sinLat * sinLat);
    }
}
```

- [ ] **Step 2: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/planet/AtmosphereGeneratorTest.java
package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereGeneratorTest {
    private static final long GALAXY_SEED = 42L;
    private AtmosphereGenerator atmoGen;
    private StarSystemGenerator starGen;
    private OrbitalLayoutGenerator layoutGen;
    private PlanetGenerator planetGen;

    @BeforeEach
    void setUp() {
        atmoGen = new AtmosphereGenerator();
        starGen = new StarSystemGenerator(GALAXY_SEED);
        layoutGen = new OrbitalLayoutGenerator();
        planetGen = new PlanetGenerator(GALAXY_SEED);
    }

    @Test
    void sameSeedProducesIdenticalAtmosphere() {
        TestPlanetResult r = generateTestPlanetWithAtmosphere(100L);
        if (r == null) return;
        Atmosphere a = atmoGen.generate(r.planet, r.system);
        Atmosphere b = new AtmosphereGenerator().generate(r.planet, r.system);

        assertEquals(a.surfacePressure, b.surfacePressure, 1e-6f);
        assertEquals(a.surfaceTemp, b.surfaceTemp, 1e-6f);
        assertEquals(a.breathable, b.breathable);
    }

    @Test
    void gasFractionsSumToOne() {
        for (long id = 0; id < 200; id++) {
            TestPlanetResult r = generateTestPlanetWithAtmosphere(id);
            if (r == null) continue;
            Atmosphere atmo = atmoGen.generate(r.planet, r.system);
            if (atmo == null) continue;
            float sum = 0f;
            for (float fraction : atmo.composition.values()) {
                sum += fraction;
            }
            assertEquals(1.0f, sum, 0.01f,
                "Star " + id + " atmosphere gas fractions sum to " + sum);
        }
    }

    @Test
    void pressureWithinTypeRange() {
        for (long id = 0; id < 300; id++) {
            TestPlanetResult r = generateTestPlanetWithAtmosphere(id);
            if (r == null) continue;
            Atmosphere atmo = atmoGen.generate(r.planet, r.system);
            if (atmo == null) continue;
            assertTrue(atmo.surfacePressure > 0f,
                "Star " + id + " " + r.planet.type + " pressure should be positive");
        }
    }

    @Test
    void breathabilityRequiresOxygenAndSafePressure() {
        for (long id = 0; id < 300; id++) {
            TestPlanetResult r = generateTestPlanetWithAtmosphere(id);
            if (r == null) continue;
            Atmosphere atmo = atmoGen.generate(r.planet, r.system);
            if (atmo == null) continue;
            if (atmo.breathable) {
                Float o2 = atmo.composition.getOrDefault(Gas.O2, 0f);
                assertTrue(o2 >= 0.15f && o2 <= 0.25f,
                    "Breathable atmosphere O2 = " + o2 + " outside 15-25%");
                assertTrue(atmo.surfacePressure >= 0.5f && atmo.surfacePressure <= 2.0f,
                    "Breathable atmosphere pressure = " + atmo.surfacePressure + " outside 0.5-2.0");
            }
        }
    }

    @Test
    void vacuumHazardWhenLowPressure() {
        for (long id = 0; id < 300; id++) {
            TestPlanetResult r = generateTestPlanetWithAtmosphere(id);
            if (r == null) continue;
            Atmosphere atmo = atmoGen.generate(r.planet, r.system);
            if (atmo == null) continue;
            if (atmo.surfacePressure < 0.01f) {
                assertTrue(atmo.hazards.contains(AtmoHazard.VACUUM),
                    "Low-pressure atmosphere should have VACUUM hazard");
            }
        }
    }

    @Test
    void airlessPlanetsReturnNull() {
        for (long id = 0; id < 300; id++) {
            TestPlanetResult r = generateTestPlanetWithAtmosphere(id);
            if (r == null) continue;
            if (r.planet.type == PlanetType.MOLTEN || r.planet.type == PlanetType.DWARF) {
                Atmosphere atmo = atmoGen.generate(r.planet, r.system);
                assertNull(atmo, r.planet.type + " should have null atmosphere");
            }
        }
    }

    private TestPlanetResult generateTestPlanetWithAtmosphere(long id) {
        StarPosition pos = new StarPosition();
        pos.uniqueId = id;
        pos.x = id * 10.0;
        pos.y = id * 5.0;
        pos.z = 0.0;
        pos.localDensity = 0.5f;
        StarSystem sys = starGen.generate(pos, GalaxyRegion.INNER_RIM);
        List<OrbitalSlot> orbits = layoutGen.generate(sys);
        if (orbits.isEmpty()) return null;
        Planet planet = planetGen.generate(orbits.get(0), sys);
        return new TestPlanetResult(planet, sys, orbits.get(0));
    }

    private record TestPlanetResult(Planet planet, StarSystem system, OrbitalSlot slot) {}
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.planet.AtmosphereGeneratorTest" --info`
Expected: FAIL — `AtmosphereGenerator` class doesn't exist

- [ ] **Step 4: Implement AtmosphereGenerator**

```java
// core/src/main/java/com/galacticodyssey/planet/AtmosphereGenerator.java
package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Random;

public final class AtmosphereGenerator {

    public Atmosphere generate(Planet planet, com.galacticodyssey.galaxy.StarSystem system) {
        if (planet.type == PlanetType.MOLTEN || planet.type == PlanetType.DWARF
            || planet.type == PlanetType.GAS_GIANT || planet.type == PlanetType.ICE_GIANT) {
            return null;
        }

        long atmoSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.ATMOSPHERE_DOMAIN), 0);
        Random rng = new Random(atmoSeed);

        if (planet.type == PlanetType.BARREN && rng.nextFloat() >= 0.1f) return null;
        if (planet.type == PlanetType.ICE_WORLD && rng.nextFloat() >= 0.3f) return null;

        Map<Gas, Float> composition = generateComposition(planet.type, rng);
        float pressure = generatePressure(planet.type, rng);
        float greenhouse = generateGreenhouse(planet.type, rng);

        float sqrtLum = (float) Math.sqrt(system.luminosity);
        float equilibriumTemp = 278f * (float) Math.pow(system.luminosity, 0.25)
            / (float) Math.sqrt(findOrbitalRadius(planet, system));
        float surfaceTemp = equilibriumTemp * greenhouse;

        boolean breathable = isBreathable(composition, pressure);
        EnumSet<AtmoHazard> hazards = deriveHazards(composition, pressure, surfaceTemp);

        return new Atmosphere(composition, pressure, greenhouse,
            equilibriumTemp, surfaceTemp, breathable, hazards);
    }

    private Map<Gas, Float> generateComposition(PlanetType type, Random rng) {
        Map<Gas, Float> comp = new EnumMap<>(Gas.class);
        switch (type) {
            case ARID -> {
                float co2 = RngUtil.range(rng, 0.75f, 0.85f);
                float n2 = RngUtil.range(rng, 0.10f, 0.20f);
                comp.put(Gas.CO2, co2);
                comp.put(Gas.N2, n2);
                comp.put(Gas.Ar, 1f - co2 - n2);
            }
            case TERRAN -> {
                float n2 = RngUtil.range(rng, 0.60f, 0.80f);
                float o2 = RngUtil.range(rng, 0.15f, 0.25f);
                comp.put(Gas.N2, n2);
                comp.put(Gas.O2, o2);
                comp.put(Gas.Ar, 1f - n2 - o2);
            }
            case OCEAN -> {
                float n2 = RngUtil.range(rng, 0.50f, 0.70f);
                float h2o = RngUtil.range(rng, 0.20f, 0.40f);
                comp.put(Gas.N2, n2);
                comp.put(Gas.H2O, h2o);
                comp.put(Gas.CO2, 1f - n2 - h2o);
            }
            case TOXIC -> {
                float so2 = RngUtil.range(rng, 0.35f, 0.45f);
                float co2 = RngUtil.range(rng, 0.30f, 0.40f);
                float hcl = RngUtil.range(rng, 0.02f, 0.08f);
                comp.put(Gas.SO2, so2);
                comp.put(Gas.CO2, co2);
                comp.put(Gas.HCl, hcl);
                comp.put(Gas.N2, 1f - so2 - co2 - hcl);
            }
            case BARREN -> {
                comp.put(Gas.CO2, 0.9f + rng.nextFloat() * 0.1f);
                comp.put(Gas.N2, 1f - comp.get(Gas.CO2));
            }
            case ICE_WORLD -> {
                comp.put(Gas.N2, 0.85f + rng.nextFloat() * 0.15f);
                comp.put(Gas.Ar, 1f - comp.get(Gas.N2));
            }
            default -> comp.put(Gas.N2, 1f);
        }
        normalizeComposition(comp);
        return comp;
    }

    private void normalizeComposition(Map<Gas, Float> comp) {
        float sum = 0f;
        for (float v : comp.values()) sum += v;
        if (sum <= 0f) return;
        for (Map.Entry<Gas, Float> e : comp.entrySet()) {
            e.setValue(e.getValue() / sum);
        }
    }

    private float generatePressure(PlanetType type, Random rng) {
        return switch (type) {
            case BARREN -> RngUtil.range(rng, 0.001f, 0.01f);
            case ARID -> RngUtil.range(rng, 0.01f, 0.5f);
            case TERRAN -> RngUtil.range(rng, 0.5f, 2.0f);
            case OCEAN -> RngUtil.range(rng, 1.0f, 4.0f);
            case TOXIC -> RngUtil.range(rng, 2.0f, 90.0f);
            case ICE_WORLD -> RngUtil.range(rng, 0.001f, 0.05f);
            default -> 0f;
        };
    }

    private float generateGreenhouse(PlanetType type, Random rng) {
        return switch (type) {
            case ARID -> RngUtil.range(rng, 1.1f, 1.4f);
            case TERRAN -> RngUtil.range(rng, 1.1f, 1.3f);
            case OCEAN -> RngUtil.range(rng, 1.3f, 1.8f);
            case TOXIC -> RngUtil.range(rng, 1.5f, 3.0f);
            default -> 1.0f;
        };
    }

    private float findOrbitalRadius(Planet planet, com.galacticodyssey.galaxy.StarSystem system) {
        for (var slot : system.orbits) {
            if (slot.planet == planet) return slot.orbitalRadius;
        }
        return 1.0f;
    }

    private boolean isBreathable(Map<Gas, Float> comp, float pressure) {
        float o2 = comp.getOrDefault(Gas.O2, 0f);
        if (o2 < 0.15f || o2 > 0.25f) return false;
        if (pressure < 0.5f || pressure > 2.0f) return false;
        if (comp.getOrDefault(Gas.SO2, 0f) > 0.01f) return false;
        if (comp.getOrDefault(Gas.HCl, 0f) > 0.01f) return false;
        if (comp.getOrDefault(Gas.NH3, 0f) > 0.01f) return false;
        return true;
    }

    private EnumSet<AtmoHazard> deriveHazards(Map<Gas, Float> comp, float pressure, float surfaceTemp) {
        EnumSet<AtmoHazard> hazards = EnumSet.noneOf(AtmoHazard.class);
        if (pressure < 0.01f) hazards.add(AtmoHazard.VACUUM);
        if (pressure > 10.0f) hazards.add(AtmoHazard.CRUSHING);
        float so2 = comp.getOrDefault(Gas.SO2, 0f);
        float hcl = comp.getOrDefault(Gas.HCl, 0f);
        float nh3 = comp.getOrDefault(Gas.NH3, 0f);
        if (so2 > 0.05f || hcl > 0.05f || nh3 > 0.05f) hazards.add(AtmoHazard.TOXIC);
        if (so2 > 0.20f && pressure > 5.0f) hazards.add(AtmoHazard.CORROSIVE);
        if (surfaceTemp > 400f) hazards.add(AtmoHazard.EXTREME_HEAT);
        if (surfaceTemp < 180f) hazards.add(AtmoHazard.EXTREME_COLD);
        if (hazards.isEmpty()) hazards.add(AtmoHazard.NONE);
        return hazards;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.planet.AtmosphereGeneratorTest" --info`
Expected: All 6 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/Atmosphere.java \
  core/src/main/java/com/galacticodyssey/planet/AtmosphereGenerator.java \
  core/src/test/java/com/galacticodyssey/planet/AtmosphereGeneratorTest.java
git commit -m "feat(planet): add Atmosphere data model and AtmosphereGenerator with TDD"
```

---

## Task 6: WhittakerGrid, BiomeMap & BiomeMapper

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/WhittakerGrid.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/BiomeMap.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/BiomeMapper.java`
- Create: `core/src/test/java/com/galacticodyssey/planet/WhittakerGridTest.java`
- Create: `core/src/test/java/com/galacticodyssey/planet/BiomeMapperTest.java`

- [ ] **Step 1: Implement WhittakerGrid**

```java
// core/src/main/java/com/galacticodyssey/planet/WhittakerGrid.java
package com.galacticodyssey.planet;

public final class WhittakerGrid {
    private static final float TEMP_FREEZING = 253f;
    private static final float TEMP_COOL = 273f;
    private static final float TEMP_WARM = 303f;

    private static final float MOISTURE_ARID = 0.25f;
    private static final float MOISTURE_DRY = 0.50f;
    private static final float MOISTURE_MOIST = 0.75f;

    private static final BiomeType[][] GRID = {
        // [moisture row][temperature col]: Freezing, Cool, Warm, Hot
        { BiomeType.ICE_FIELD,     BiomeType.ROCKY_WASTE,      BiomeType.DESERT,           BiomeType.VOLCANIC },
        { BiomeType.POLAR_DESERT,  BiomeType.STEPPE,           BiomeType.ARID_SHRUB,       BiomeType.BADLANDS },
        { BiomeType.TUNDRA,        BiomeType.TEMPERATE_FOREST, BiomeType.GRASSLAND,         BiomeType.SAVANNA },
        { BiomeType.ICE_SHEET,     BiomeType.BOREAL_FOREST,    BiomeType.TROPICAL_FOREST,  BiomeType.SWAMP },
    };

    public static BiomeType classify(float temperatureK, float moisture) {
        int tempIdx;
        if (temperatureK < TEMP_FREEZING) tempIdx = 0;
        else if (temperatureK < TEMP_COOL) tempIdx = 1;
        else if (temperatureK < TEMP_WARM) tempIdx = 2;
        else tempIdx = 3;

        int moistIdx;
        if (moisture < MOISTURE_ARID) moistIdx = 0;
        else if (moisture < MOISTURE_DRY) moistIdx = 1;
        else if (moisture < MOISTURE_MOIST) moistIdx = 2;
        else moistIdx = 3;

        return GRID[moistIdx][tempIdx];
    }
}
```

- [ ] **Step 2: Write WhittakerGrid tests**

```java
// core/src/test/java/com/galacticodyssey/planet/WhittakerGridTest.java
package com.galacticodyssey.planet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WhittakerGridTest {

    @Test
    void coldWetIsIceSheet() {
        assertEquals(BiomeType.ICE_SHEET, WhittakerGrid.classify(240f, 0.9f));
    }

    @Test
    void hotDryIsVolcanic() {
        assertEquals(BiomeType.VOLCANIC, WhittakerGrid.classify(320f, 0.1f));
    }

    @Test
    void warmMoistIsGrassland() {
        assertEquals(BiomeType.GRASSLAND, WhittakerGrid.classify(290f, 0.6f));
    }

    @Test
    void coolMidIsTempForest() {
        assertEquals(BiomeType.TEMPERATE_FOREST, WhittakerGrid.classify(265f, 0.6f));
    }

    @Test
    void allGridCellsReachable() {
        float[] temps = { 240f, 260f, 290f, 320f };
        float[] moists = { 0.1f, 0.35f, 0.6f, 0.85f };
        java.util.Set<BiomeType> seen = new java.util.HashSet<>();
        for (float t : temps) {
            for (float m : moists) {
                seen.add(WhittakerGrid.classify(t, m));
            }
        }
        assertEquals(16, seen.size(), "All 16 grid biomes should be reachable");
    }

    @Test
    void noNullReturns() {
        for (float t = 150f; t < 500f; t += 10f) {
            for (float m = 0f; m <= 1.0f; m += 0.1f) {
                assertNotNull(WhittakerGrid.classify(t, m),
                    "Null at temp=" + t + " moisture=" + m);
            }
        }
    }
}
```

- [ ] **Step 3: Run WhittakerGrid tests**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.planet.WhittakerGridTest" --info`
Expected: All 6 tests PASS

- [ ] **Step 4: Implement BiomeMap and BiomeMapper**

```java
// core/src/main/java/com/galacticodyssey/planet/BiomeMap.java
package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.GalaxyNoise;
import java.util.EnumSet;

public final class BiomeMap {
    public final long seed;
    public final float seaLevel;
    public final float snowLine;
    public final float baseMoisture;
    public final float surfaceTemp;
    public final EnumSet<BiomeType> allowedBiomes;
    private final GalaxyNoise moistureNoise;

    public BiomeMap(long seed, float seaLevel, float snowLine, float baseMoisture,
                    float surfaceTemp, EnumSet<BiomeType> allowedBiomes) {
        this.seed = seed;
        this.seaLevel = seaLevel;
        this.snowLine = snowLine;
        this.baseMoisture = baseMoisture;
        this.surfaceTemp = surfaceTemp;
        this.allowedBiomes = allowedBiomes;
        this.moistureNoise = new GalaxyNoise(seed);
    }

    public float getTemperature(float latRadians, float lonRadians) {
        float sinLat = (float) Math.sin(latRadians);
        return surfaceTemp * (1.0f - 0.4f * sinLat * sinLat);
    }

    public float getMoisture(float latRadians, float lonRadians) {
        float noise = moistureNoise.fbm(
            (float)(lonRadians * 2.0), (float)(latRadians * 2.0), 4, 0.5f, 2.0f);
        return Math.max(0f, Math.min(1f, baseMoisture + noise * 0.3f));
    }

    public BiomeType getBiome(float latRadians, float lonRadians, float elevation) {
        float temp = getTemperature(latRadians, lonRadians);
        float moisture = getMoisture(latRadians, lonRadians);

        temp -= elevation * 6.5f / 1000f;
        moisture -= elevation * 0.1f;
        moisture = Math.max(0f, Math.min(1f, moisture));

        if (elevation < seaLevel && temp > 273f) return filterBiome(BiomeType.OCEAN);
        if (elevation < seaLevel && temp <= 273f) return filterBiome(BiomeType.ICE_SHEET);
        if (elevation > snowLine) return filterBiome(BiomeType.ICE_FIELD);

        BiomeType biome = WhittakerGrid.classify(temp, moisture);
        return filterBiome(biome);
    }

    private BiomeType filterBiome(BiomeType biome) {
        if (allowedBiomes.contains(biome)) return biome;
        for (BiomeType allowed : allowedBiomes) return allowed;
        return BiomeType.ROCKY_WASTE;
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/planet/BiomeMapper.java
package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.SeedDeriver;
import java.util.EnumSet;
import java.util.Random;

public final class BiomeMapper {

    public BiomeMap generate(Planet planet, Atmosphere atmosphere) {
        long biomeSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.BIOME_DOMAIN), 0);
        Random rng = new Random(biomeSeed);

        float seaLevel = rng.nextFloat() * 0.3f;
        float snowLine = 0.6f + rng.nextFloat() * 0.3f;
        float baseMoisture = moistureFromType(planet.type, rng);
        float surfaceTemp = atmosphere != null ? atmosphere.surfaceTemp : 200f;
        EnumSet<BiomeType> allowed = allowedBiomesForType(planet.type);

        return new BiomeMap(biomeSeed, seaLevel, snowLine, baseMoisture, surfaceTemp, allowed);
    }

    private float moistureFromType(PlanetType type, Random rng) {
        return switch (type) {
            case OCEAN -> 0.8f + rng.nextFloat() * 0.2f;
            case TERRAN -> 0.3f + rng.nextFloat() * 0.4f;
            case ARID -> 0.05f + rng.nextFloat() * 0.15f;
            case TOXIC -> 0.05f + rng.nextFloat() * 0.1f;
            case ICE_WORLD -> 0.2f + rng.nextFloat() * 0.3f;
            case BARREN -> 0.01f + rng.nextFloat() * 0.05f;
            case MOLTEN -> 0.0f;
            default -> 0.3f;
        };
    }

    private EnumSet<BiomeType> allowedBiomesForType(PlanetType type) {
        return switch (type) {
            case TERRAN -> EnumSet.allOf(BiomeType.class);
            case OCEAN -> EnumSet.of(BiomeType.OCEAN, BiomeType.ICE_SHEET, BiomeType.SWAMP, BiomeType.TROPICAL_FOREST);
            case ARID -> EnumSet.of(BiomeType.DESERT, BiomeType.ARID_SHRUB, BiomeType.BADLANDS, BiomeType.ROCKY_WASTE, BiomeType.STEPPE, BiomeType.POLAR_DESERT);
            case TOXIC -> EnumSet.of(BiomeType.VOLCANIC, BiomeType.BADLANDS, BiomeType.ROCKY_WASTE);
            case ICE_WORLD -> EnumSet.of(BiomeType.ICE_SHEET, BiomeType.ICE_FIELD, BiomeType.POLAR_DESERT, BiomeType.TUNDRA);
            case BARREN -> EnumSet.of(BiomeType.ROCKY_WASTE, BiomeType.DESERT, BiomeType.POLAR_DESERT);
            case MOLTEN -> EnumSet.of(BiomeType.VOLCANIC);
            default -> EnumSet.of(BiomeType.ROCKY_WASTE);
        };
    }
}
```

- [ ] **Step 5: Write BiomeMapper tests**

```java
// core/src/test/java/com/galacticodyssey/planet/BiomeMapperTest.java
package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BiomeMapperTest {
    private static final long GALAXY_SEED = 42L;
    private BiomeMapper biomeMapper;
    private AtmosphereGenerator atmoGen;
    private StarSystemGenerator starGen;
    private OrbitalLayoutGenerator layoutGen;
    private PlanetGenerator planetGen;

    @BeforeEach
    void setUp() {
        biomeMapper = new BiomeMapper();
        atmoGen = new AtmosphereGenerator();
        starGen = new StarSystemGenerator(GALAXY_SEED);
        layoutGen = new OrbitalLayoutGenerator();
        planetGen = new PlanetGenerator(GALAXY_SEED);
    }

    @Test
    void sameSeedProducesIdenticalBiomeMap() {
        Planet planet = generateTerranPlanet();
        if (planet == null) return;
        Atmosphere atmo = atmoGen.generate(planet, generateTestSystem(42L));
        BiomeMap a = biomeMapper.generate(planet, atmo);
        BiomeMap b = new BiomeMapper().generate(planet, atmo);
        assertEquals(a.seaLevel, b.seaLevel, 1e-6f);
        assertEquals(a.baseMoisture, b.baseMoisture, 1e-6f);
    }

    @Test
    void onlyAllowedBiomesReturnedForPlanetType() {
        for (long id = 0; id < 100; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                if (!p.type.hasSurface()) continue;
                Atmosphere atmo = atmoGen.generate(p, sys);
                BiomeMap bm = biomeMapper.generate(p, atmo);
                for (float lat = -1.5f; lat <= 1.5f; lat += 0.5f) {
                    for (float lon = -3.0f; lon <= 3.0f; lon += 1.0f) {
                        BiomeType biome = bm.getBiome(lat, lon, 0.3f);
                        assertTrue(bm.allowedBiomes.contains(biome),
                            "Planet type " + p.type + " returned disallowed biome " + biome);
                    }
                }
            }
        }
    }

    @Test
    void polarRegionsColderThanEquator() {
        Planet planet = generateTerranPlanet();
        if (planet == null) return;
        Atmosphere atmo = atmoGen.generate(planet, generateTestSystem(42L));
        BiomeMap bm = biomeMapper.generate(planet, atmo);
        float equatorTemp = bm.getTemperature(0f, 0f);
        float poleTemp = bm.getTemperature((float)(Math.PI / 2.0), 0f);
        assertTrue(equatorTemp > poleTemp,
            "Equator temp " + equatorTemp + " should exceed pole temp " + poleTemp);
    }

    private Planet generateTerranPlanet() {
        for (long id = 0; id < 500; id++) {
            StarSystem sys = generateTestSystem(id);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                if (p.type == PlanetType.TERRAN) return p;
            }
        }
        return null;
    }

    private StarSystem generateTestSystem(long id) {
        StarPosition pos = new StarPosition();
        pos.uniqueId = id;
        pos.x = id * 10.0;
        pos.y = id * 5.0;
        pos.z = 0.0;
        pos.localDensity = 0.5f;
        return starGen.generate(pos, GalaxyRegion.INNER_RIM);
    }
}
```

- [ ] **Step 6: Run all biome tests**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.planet.WhittakerGridTest" --tests "com.galacticodyssey.planet.BiomeMapperTest" --info`
Expected: All tests PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/WhittakerGrid.java \
  core/src/main/java/com/galacticodyssey/planet/BiomeMap.java \
  core/src/main/java/com/galacticodyssey/planet/BiomeMapper.java \
  core/src/test/java/com/galacticodyssey/planet/WhittakerGridTest.java \
  core/src/test/java/com/galacticodyssey/planet/BiomeMapperTest.java
git commit -m "feat(planet): add WhittakerGrid, BiomeMap and BiomeMapper with TDD"
```

---

## Task 7: CubeSphere Math

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/CubeSphere.java`
- Create: `core/src/test/java/com/galacticodyssey/planet/terrain/CubeSphereTest.java`

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/planet/terrain/CubeSphereTest.java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CubeSphereTest {

    @Test
    void allFacesProduceUnitVectors() {
        for (CubeFace face : CubeFace.values()) {
            for (float u = 0f; u <= 1f; u += 0.25f) {
                for (float v = 0f; v <= 1f; v += 0.25f) {
                    Vector3 dir = CubeSphere.toSphere(face, u, v);
                    float len = dir.len();
                    assertEquals(1f, len, 1e-5f,
                        face + " u=" + u + " v=" + v + " produced vector of length " + len);
                }
            }
        }
    }

    @Test
    void faceCentersPointAlongAxis() {
        Vector3 px = CubeSphere.toSphere(CubeFace.POS_X, 0.5f, 0.5f);
        assertEquals(1f, px.x, 0.01f);
        assertEquals(0f, px.y, 0.01f);
        assertEquals(0f, px.z, 0.01f);

        Vector3 ny = CubeSphere.toSphere(CubeFace.NEG_Y, 0.5f, 0.5f);
        assertEquals(0f, ny.x, 0.01f);
        assertEquals(-1f, ny.y, 0.01f);
        assertEquals(0f, ny.z, 0.01f);
    }

    @Test
    void adjacentFacesShareEdgeVertices() {
        // POS_X face right edge (u=1) should match POS_Z face left edge (u=0)
        for (float v = 0f; v <= 1f; v += 0.1f) {
            Vector3 a = CubeSphere.toSphere(CubeFace.POS_X, 1f, v);
            Vector3 b = CubeSphere.toSphere(CubeFace.NEG_Z, 0f, v);
            assertEquals(a.x, b.x, 1e-4f, "Seam mismatch at v=" + v);
            assertEquals(a.y, b.y, 1e-4f, "Seam mismatch at v=" + v);
            assertEquals(a.z, b.z, 1e-4f, "Seam mismatch at v=" + v);
        }
    }

    @Test
    void inverseRoundTrips() {
        for (CubeFace face : CubeFace.values()) {
            for (float u = 0.1f; u <= 0.9f; u += 0.2f) {
                for (float v = 0.1f; v <= 0.9f; v += 0.2f) {
                    Vector3 dir = CubeSphere.toSphere(face, u, v);
                    float[] uv = CubeSphere.toFaceUV(dir);
                    CubeFace resolvedFace = CubeSphere.dominantFace(dir);
                    assertEquals(face, resolvedFace,
                        "Face mismatch for " + face + " u=" + u + " v=" + v);
                    assertEquals(u, uv[0], 0.02f,
                        "U mismatch for " + face + " u=" + u + " v=" + v);
                    assertEquals(v, uv[1], 0.02f,
                        "V mismatch for " + face + " u=" + u + " v=" + v);
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.CubeSphereTest" --info`
Expected: FAIL — `CubeSphere` class doesn't exist

- [ ] **Step 3: Implement CubeSphere**

```java
// core/src/main/java/com/galacticodyssey/planet/terrain/CubeSphere.java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;

public final class CubeSphere {

    public static Vector3 toSphere(CubeFace face, float u, float v) {
        float cu = u * 2f - 1f;
        float cv = v * 2f - 1f;

        float x, y, z;
        switch (face) {
            case POS_X -> { x =  1f; y = cv; z = -cu; }
            case NEG_X -> { x = -1f; y = cv; z =  cu; }
            case POS_Y -> { x = cu;  y =  1f; z = -cv; }
            case NEG_Y -> { x = cu;  y = -1f; z =  cv; }
            case POS_Z -> { x = cu;  y = cv; z =  1f; }
            case NEG_Z -> { x = -cu; y = cv; z = -1f; }
            default -> throw new IllegalArgumentException();
        }

        return new Vector3(x, y, z).nor();
    }

    public static CubeFace dominantFace(Vector3 dir) {
        float ax = Math.abs(dir.x);
        float ay = Math.abs(dir.y);
        float az = Math.abs(dir.z);
        if (ax >= ay && ax >= az) return dir.x > 0 ? CubeFace.POS_X : CubeFace.NEG_X;
        if (ay >= ax && ay >= az) return dir.y > 0 ? CubeFace.POS_Y : CubeFace.NEG_Y;
        return dir.z > 0 ? CubeFace.POS_Z : CubeFace.NEG_Z;
    }

    public static float[] toFaceUV(Vector3 dir) {
        CubeFace face = dominantFace(dir);
        float cu, cv;
        switch (face) {
            case POS_X -> { cu = -dir.z / dir.x; cv = dir.y / dir.x; }
            case NEG_X -> { cu = dir.z / (-dir.x); cv = dir.y / (-dir.x); }
            case POS_Y -> { cu = dir.x / dir.y; cv = -dir.z / dir.y; }
            case NEG_Y -> { cu = dir.x / (-dir.y); cv = dir.z / (-dir.y); }
            case POS_Z -> { cu = dir.x / dir.z; cv = dir.y / dir.z; }
            case NEG_Z -> { cu = -dir.x / (-dir.z); cv = dir.y / (-dir.z); }
            default -> throw new IllegalArgumentException();
        }
        return new float[] { (cu + 1f) * 0.5f, (cv + 1f) * 0.5f };
    }

    public static float latitudeOf(Vector3 dir) {
        return (float) Math.asin(dir.y);
    }

    public static float longitudeOf(Vector3 dir) {
        return (float) Math.atan2(dir.z, dir.x);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.CubeSphereTest" --info`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/CubeSphere.java \
  core/src/test/java/com/galacticodyssey/planet/terrain/CubeSphereTest.java
git commit -m "feat(terrain): add CubeSphere projection math with TDD"
```

---

## Task 8: TerrainNoiseStack

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainNoiseStack.java`
- Create: `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainNoiseStackTest.java`

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/planet/terrain/TerrainNoiseStackTest.java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class TerrainNoiseStackTest {

    @Test
    void sameSeedProducesIdenticalHeight() {
        TerrainNoiseStack a = new TerrainNoiseStack(12345L);
        TerrainNoiseStack b = new TerrainNoiseStack(12345L);
        BiomeMap biomeMap = makeBiomeMap();
        Vector3 dir = new Vector3(0.5f, 0.3f, 0.8f).nor();

        float ha = a.heightAt(dir, biomeMap, 3);
        float hb = b.heightAt(dir, biomeMap, 3);
        assertEquals(ha, hb, 1e-6f);
    }

    @Test
    void differentSeedsProduceDifferentHeight() {
        TerrainNoiseStack a = new TerrainNoiseStack(111L);
        TerrainNoiseStack b = new TerrainNoiseStack(222L);
        BiomeMap biomeMap = makeBiomeMap();
        Vector3 dir = new Vector3(0.5f, 0.3f, 0.8f).nor();

        float ha = a.heightAt(dir, biomeMap, 3);
        float hb = b.heightAt(dir, biomeMap, 3);
        assertNotEquals(ha, hb, 1e-3f);
    }

    @Test
    void heightWithinReasonableBounds() {
        TerrainNoiseStack stack = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = makeBiomeMap();
        for (float theta = 0; theta < 6.28f; theta += 0.5f) {
            for (float phi = -1.5f; phi < 1.5f; phi += 0.5f) {
                Vector3 dir = new Vector3(
                    (float)(Math.cos(theta) * Math.cos(phi)),
                    (float)(Math.sin(phi)),
                    (float)(Math.sin(theta) * Math.cos(phi))
                ).nor();
                float h = stack.heightAt(dir, biomeMap, 3);
                assertTrue(h > -2f && h < 2f,
                    "Height " + h + " at theta=" + theta + " phi=" + phi + " out of bounds");
            }
        }
    }

    @Test
    void volcanicBiomeProducesHigherAmplitude() {
        TerrainNoiseStack stack = new TerrainNoiseStack(42L);
        BiomeMap flat = makeBiomeMapWith(BiomeType.DESERT);
        BiomeMap rough = makeBiomeMapWith(BiomeType.VOLCANIC);
        Vector3 dir = new Vector3(1f, 0f, 0f);

        float sumFlat = 0f, sumRough = 0f;
        int n = 50;
        for (int i = 0; i < n; i++) {
            Vector3 d = new Vector3(1f, i * 0.02f, i * 0.03f).nor();
            sumFlat += Math.abs(stack.heightAt(d, flat, 3));
            sumRough += Math.abs(stack.heightAt(d, rough, 3));
        }
        assertTrue(sumRough > sumFlat,
            "Volcanic avg amplitude " + sumRough/n + " should exceed desert " + sumFlat/n);
    }

    private BiomeMap makeBiomeMap() {
        return makeBiomeMapWith(BiomeType.GRASSLAND);
    }

    private BiomeMap makeBiomeMapWith(BiomeType type) {
        return new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f, EnumSet.of(type));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.TerrainNoiseStackTest" --info`
Expected: FAIL — `TerrainNoiseStack` doesn't exist

- [ ] **Step 3: Implement TerrainNoiseStack**

```java
// core/src/main/java/com/galacticodyssey/planet/terrain/TerrainNoiseStack.java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.galaxy.GalaxyNoise;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;

public final class TerrainNoiseStack {
    private final GalaxyNoise continentNoise;
    private final GalaxyNoise ridgeNoise;
    private final GalaxyNoise detailNoise;

    public TerrainNoiseStack(long seed) {
        this.continentNoise = new GalaxyNoise(seed);
        this.ridgeNoise = new GalaxyNoise(seed + 1);
        this.detailNoise = new GalaxyNoise(seed + 2);
    }

    public float heightAt(Vector3 dir, BiomeMap biomeMap, int lod) {
        float cx = dir.x * 2f;
        float cy = dir.y * 2f;
        float continent = continentNoise.fbm(cx, cy, 6, 0.5f, 2.0f);

        float rx = dir.x * 8f;
        float ry = dir.y * 8f;
        float ridge = Math.abs(ridgeNoise.fbm(rx, ry, 5, 0.5f, 2.0f));

        float lat = CubeSphere.latitudeOf(dir);
        float lon = CubeSphere.longitudeOf(dir);
        BiomeType biome = biomeMap.getBiome(lat, lon, continent);
        float amplitude = biome.amplitude;
        float ridgeMix = biome.ridgeMix;

        float height = continent + ridge * ridgeMix * amplitude;

        if (lod >= 3) {
            float dx = dir.x * 64f;
            float dy = dir.y * 64f;
            float detail = detailNoise.fbm(dx, dy, 3, 0.5f, 2.0f);
            height += detail * amplitude * 0.1f;
        }

        return height;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.TerrainNoiseStackTest" --info`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/TerrainNoiseStack.java \
  core/src/test/java/com/galacticodyssey/planet/terrain/TerrainNoiseStackTest.java
git commit -m "feat(terrain): add TerrainNoiseStack with biome-shaped noise layers"
```

---

## Task 9: TerrainMeshBuilder

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainMeshBuilder.java`
- Create: `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainMeshBuilderTest.java`

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/planet/terrain/TerrainMeshBuilderTest.java
package com.galacticodyssey.planet.terrain;

import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;

class TerrainMeshBuilderTest {
    private static final int GRID_SIZE = 33;
    private static final int VERTEX_STRIDE = 10; // pos(3) + normal(3) + color(4)

    @Test
    void vertexCountIs33x33() {
        TerrainMeshBuilder.MeshData data = buildTestMesh();
        assertEquals(GRID_SIZE * GRID_SIZE * VERTEX_STRIDE, data.vertices.length,
            "Expected " + (GRID_SIZE * GRID_SIZE) + " vertices × " + VERTEX_STRIDE + " floats");
    }

    @Test
    void indexCountProducesValidTriangles() {
        TerrainMeshBuilder.MeshData data = buildTestMesh();
        assertEquals(0, data.indices.length % 3, "Index count must be divisible by 3");
        int expectedQuads = (GRID_SIZE - 1) * (GRID_SIZE - 1);
        assertEquals(expectedQuads * 6, data.indices.length,
            "Expected " + expectedQuads + " quads × 6 indices");
    }

    @Test
    void normalsPointOutward() {
        TerrainMeshBuilder.MeshData data = buildTestMesh();
        for (int i = 0; i < data.vertices.length; i += VERTEX_STRIDE) {
            float px = data.vertices[i];
            float py = data.vertices[i + 1];
            float pz = data.vertices[i + 2];
            float nx = data.vertices[i + 3];
            float ny = data.vertices[i + 4];
            float nz = data.vertices[i + 5];
            float dot = px * nx + py * ny + pz * nz;
            assertTrue(dot > 0f, "Normal at vertex " + (i / VERTEX_STRIDE) +
                " points inward (dot=" + dot + ")");
        }
    }

    @Test
    void noNaNInVertices() {
        TerrainMeshBuilder.MeshData data = buildTestMesh();
        for (int i = 0; i < data.vertices.length; i++) {
            assertFalse(Float.isNaN(data.vertices[i]),
                "NaN found at vertex float index " + i);
        }
    }

    private TerrainMeshBuilder.MeshData buildTestMesh() {
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f,
            EnumSet.allOf(BiomeType.class));
        return TerrainMeshBuilder.build(CubeFace.POS_Z, 0f, 0f, 1f, 1f,
            noise, biomeMap, 1.0f, 2, null);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.TerrainMeshBuilderTest" --info`
Expected: FAIL — `TerrainMeshBuilder` doesn't exist

- [ ] **Step 3: Implement TerrainMeshBuilder**

```java
// core/src/main/java/com/galacticodyssey/planet/terrain/TerrainMeshBuilder.java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;

public final class TerrainMeshBuilder {
    public static final int GRID_SIZE = 33;
    public static final int VERTEX_STRIDE = 10; // pos(3) + normal(3) + color(4)

    public static class MeshData {
        public final float[] vertices;
        public final short[] indices;
        public MeshData(float[] vertices, short[] indices) {
            this.vertices = vertices;
            this.indices = indices;
        }
    }

    public static MeshData build(CubeFace face, float u0, float v0, float u1, float v1,
                                  TerrainNoiseStack noise, BiomeMap biomeMap,
                                  float planetRadius, int lod, int[] neighborLods) {
        float[] vertices = new float[GRID_SIZE * GRID_SIZE * VERTEX_STRIDE];
        float[] heights = new float[GRID_SIZE * GRID_SIZE];
        Vector3[] positions = new Vector3[GRID_SIZE * GRID_SIZE];

        for (int gy = 0; gy < GRID_SIZE; gy++) {
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                float u = u0 + (u1 - u0) * gx / (GRID_SIZE - 1f);
                float v = v0 + (v1 - v0) * gy / (GRID_SIZE - 1f);
                Vector3 dir = CubeSphere.toSphere(face, u, v);
                float h = noise.heightAt(dir, biomeMap, lod);
                heights[gy * GRID_SIZE + gx] = h;
                positions[gy * GRID_SIZE + gx] = dir.scl(planetRadius + h * planetRadius * 0.01f);
            }
        }

        for (int gy = 0; gy < GRID_SIZE; gy++) {
            for (int gx = 0; gx < GRID_SIZE; gx++) {
                int idx = gy * GRID_SIZE + gx;
                Vector3 pos = positions[idx];
                Vector3 normal = computeNormal(positions, gx, gy);

                float lat = CubeSphere.latitudeOf(pos.cpy().nor());
                float lon = CubeSphere.longitudeOf(pos.cpy().nor());
                BiomeType biome = biomeMap.getBiome(lat, lon, heights[idx]);
                float[] color = biomeColor(biome);

                int vi = idx * VERTEX_STRIDE;
                vertices[vi    ] = pos.x;
                vertices[vi + 1] = pos.y;
                vertices[vi + 2] = pos.z;
                vertices[vi + 3] = normal.x;
                vertices[vi + 4] = normal.y;
                vertices[vi + 5] = normal.z;
                vertices[vi + 6] = color[0];
                vertices[vi + 7] = color[1];
                vertices[vi + 8] = color[2];
                vertices[vi + 9] = color[3];
            }
        }

        short[] indices = buildIndices(neighborLods);
        return new MeshData(vertices, indices);
    }

    private static Vector3 computeNormal(Vector3[] positions, int gx, int gy) {
        int cx = Math.max(0, Math.min(GRID_SIZE - 1, gx));
        int cy = Math.max(0, Math.min(GRID_SIZE - 1, gy));
        int left = cy * GRID_SIZE + Math.max(0, cx - 1);
        int right = cy * GRID_SIZE + Math.min(GRID_SIZE - 1, cx + 1);
        int down = Math.max(0, cy - 1) * GRID_SIZE + cx;
        int up = Math.min(GRID_SIZE - 1, cy + 1) * GRID_SIZE + cx;

        Vector3 dx = positions[right].cpy().sub(positions[left]);
        Vector3 dy = positions[up].cpy().sub(positions[down]);
        return dy.crs(dx).nor();
    }

    private static short[] buildIndices(int[] neighborLods) {
        int quads = (GRID_SIZE - 1) * (GRID_SIZE - 1);
        short[] indices = new short[quads * 6];
        int i = 0;
        for (int gy = 0; gy < GRID_SIZE - 1; gy++) {
            for (int gx = 0; gx < GRID_SIZE - 1; gx++) {
                short tl = (short)(gy * GRID_SIZE + gx);
                short tr = (short)(gy * GRID_SIZE + gx + 1);
                short bl = (short)((gy + 1) * GRID_SIZE + gx);
                short br = (short)((gy + 1) * GRID_SIZE + gx + 1);
                indices[i++] = tl;
                indices[i++] = bl;
                indices[i++] = tr;
                indices[i++] = tr;
                indices[i++] = bl;
                indices[i++] = br;
            }
        }
        return indices;
    }

    private static float[] biomeColor(BiomeType biome) {
        return switch (biome) {
            case OCEAN ->           new float[] { 0.1f, 0.3f, 0.7f, 1f };
            case LAKE ->            new float[] { 0.2f, 0.4f, 0.8f, 1f };
            case ICE_SHEET ->       new float[] { 0.9f, 0.95f, 1.0f, 1f };
            case ICE_FIELD ->       new float[] { 0.8f, 0.85f, 0.9f, 1f };
            case TUNDRA ->          new float[] { 0.6f, 0.65f, 0.55f, 1f };
            case POLAR_DESERT ->    new float[] { 0.7f, 0.7f, 0.65f, 1f };
            case DESERT ->          new float[] { 0.85f, 0.75f, 0.45f, 1f };
            case ARID_SHRUB ->      new float[] { 0.7f, 0.65f, 0.4f, 1f };
            case STEPPE ->          new float[] { 0.65f, 0.6f, 0.35f, 1f };
            case GRASSLAND ->       new float[] { 0.4f, 0.7f, 0.3f, 1f };
            case SAVANNA ->         new float[] { 0.75f, 0.7f, 0.35f, 1f };
            case SWAMP ->           new float[] { 0.3f, 0.5f, 0.25f, 1f };
            case TEMPERATE_FOREST ->new float[] { 0.2f, 0.55f, 0.2f, 1f };
            case BOREAL_FOREST ->   new float[] { 0.15f, 0.4f, 0.2f, 1f };
            case TROPICAL_FOREST -> new float[] { 0.1f, 0.5f, 0.15f, 1f };
            case ROCKY_WASTE ->     new float[] { 0.5f, 0.45f, 0.4f, 1f };
            case BADLANDS ->        new float[] { 0.7f, 0.4f, 0.25f, 1f };
            case VOLCANIC ->        new float[] { 0.3f, 0.15f, 0.1f, 1f };
        };
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.planet.terrain.TerrainMeshBuilderTest" --info`
Expected: All 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/TerrainMeshBuilder.java \
  core/src/test/java/com/galacticodyssey/planet/terrain/TerrainMeshBuilderTest.java
git commit -m "feat(terrain): add TerrainMeshBuilder with 33x33 grid, normals, biome colors"
```

---

## Task 10: TerrainChunk, TerrainQuadtree & PlanetTerrainSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainChunk.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainQuadtree.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java`

- [ ] **Step 1: Implement TerrainChunk**

```java
// core/src/main/java/com/galacticodyssey/planet/terrain/TerrainChunk.java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

public final class TerrainChunk implements Disposable {
    public static final int MAX_DEPTH = 5;
    public static final float SPLIT_THRESHOLD = 1.5f;
    public static final float MERGE_THRESHOLD = 0.75f;

    public final CubeFace face;
    public final int depth;
    public final float u0, v0, u1, v1;
    public final Vector3 center;
    public final float arcLength;
    public TerrainChunk[] children;
    public Mesh mesh;
    public boolean meshReady;

    public TerrainChunk(CubeFace face, int depth, float u0, float v0, float u1, float v1, float planetRadius) {
        this.face = face;
        this.depth = depth;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.center = CubeSphere.toSphere(face, (u0 + u1) * 0.5f, (v0 + v1) * 0.5f).scl(planetRadius);
        this.arcLength = planetRadius * (u1 - u0) * 1.57f;
        this.meshReady = false;
    }

    public boolean hasChildren() {
        return children != null;
    }

    public boolean shouldSplit(Vector3 cameraPos) {
        float dist = cameraPos.dst(center);
        float screenSize = arcLength / Math.max(dist, 0.001f);
        return screenSize > SPLIT_THRESHOLD && depth < MAX_DEPTH;
    }

    public boolean shouldMerge(Vector3 cameraPos) {
        float dist = cameraPos.dst(center);
        float screenSize = arcLength / Math.max(dist, 0.001f);
        return screenSize < MERGE_THRESHOLD;
    }

    @Override
    public void dispose() {
        if (mesh != null) { mesh.dispose(); mesh = null; }
        if (children != null) {
            for (TerrainChunk child : children) child.dispose();
            children = null;
        }
    }
}
```

- [ ] **Step 2: Implement TerrainQuadtree**

```java
// core/src/main/java/com/galacticodyssey/planet/terrain/TerrainQuadtree.java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.planet.BiomeMap;
import java.util.ArrayList;
import java.util.List;

public final class TerrainQuadtree implements Disposable {
    private final TerrainChunk[] roots;
    private final float planetRadius;
    private final TerrainNoiseStack noise;
    private final BiomeMap biomeMap;

    public TerrainQuadtree(float planetRadius, TerrainNoiseStack noise, BiomeMap biomeMap) {
        this.planetRadius = planetRadius;
        this.noise = noise;
        this.biomeMap = biomeMap;
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
            for (TerrainChunk child : chunk.children) child.dispose();
            chunk.children = null;
        }
    }

    private void generateMesh(TerrainChunk chunk) {
        TerrainMeshBuilder.MeshData data = TerrainMeshBuilder.build(
            chunk.face, chunk.u0, chunk.v0, chunk.u1, chunk.v1,
            noise, biomeMap, planetRadius, chunk.depth, null);
        chunk.meshReady = true;
        // Mesh GPU upload deferred to render thread — store raw data for now
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

    @Override
    public void dispose() {
        for (TerrainChunk root : roots) root.dispose();
    }
}
```

- [ ] **Step 3: Implement PlanetTerrainSystem**

```java
// core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java
package com.galacticodyssey.planet.terrain;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Planet;

public final class PlanetTerrainSystem extends EntitySystem implements Disposable {
    private TerrainQuadtree quadtree;
    private final Vector3 cameraPos = new Vector3();

    public void loadPlanet(Planet planet, BiomeMap biomeMap) {
        if (quadtree != null) quadtree.dispose();
        long terrainSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.TERRAIN_DOMAIN), 0);
        TerrainNoiseStack noise = new TerrainNoiseStack(terrainSeed);
        float radiusKm = planet.radius * 6371f;
        quadtree = new TerrainQuadtree(radiusKm, noise, biomeMap);
    }

    public void unloadPlanet() {
        if (quadtree != null) { quadtree.dispose(); quadtree = null; }
    }

    public void setCameraPosition(Vector3 pos) {
        cameraPos.set(pos);
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

- [ ] **Step 4: Verify compilation**

Run: `gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/TerrainChunk.java \
  core/src/main/java/com/galacticodyssey/planet/terrain/TerrainQuadtree.java \
  core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java
git commit -m "feat(terrain): add TerrainChunk quadtree, TerrainQuadtree manager, PlanetTerrainSystem"
```

---

## Task 11: StarSystemCache

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/StarSystemCache.java`
- Create: `core/src/test/java/com/galacticodyssey/galaxy/StarSystemCacheTest.java`

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/galaxy/StarSystemCacheTest.java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StarSystemCacheTest {
    private static final long GALAXY_SEED = 42L;
    private StarSystemCache cache;

    @BeforeEach
    void setUp() {
        cache = new StarSystemCache(GALAXY_SEED, 4);
    }

    @Test
    void cacheReturnsSameInstance() {
        StarPosition pos = makeStarPosition(1L);
        StarSystem a = cache.get(pos, GalaxyRegion.INNER_RIM);
        StarSystem b = cache.get(pos, GalaxyRegion.INNER_RIM);
        assertSame(a, b);
    }

    @Test
    void cacheEvictsLeastRecentlyUsed() {
        StarPosition p1 = makeStarPosition(1L);
        StarPosition p2 = makeStarPosition(2L);
        StarPosition p3 = makeStarPosition(3L);
        StarPosition p4 = makeStarPosition(4L);
        StarPosition p5 = makeStarPosition(5L);

        StarSystem s1 = cache.get(p1, GalaxyRegion.INNER_RIM);
        cache.get(p2, GalaxyRegion.INNER_RIM);
        cache.get(p3, GalaxyRegion.INNER_RIM);
        cache.get(p4, GalaxyRegion.INNER_RIM);

        // p1 is LRU, adding p5 should evict it
        cache.get(p5, GalaxyRegion.INNER_RIM);
        StarSystem s1Again = cache.get(p1, GalaxyRegion.INNER_RIM);
        assertNotSame(s1, s1Again, "p1 should have been evicted and regenerated");
    }

    @Test
    void cachedResultIsDeterministic() {
        StarPosition pos = makeStarPosition(42L);
        StarSystem a = cache.get(pos, GalaxyRegion.CORE);
        StarSystemCache cache2 = new StarSystemCache(GALAXY_SEED, 4);
        StarSystem b = cache2.get(pos, GalaxyRegion.CORE);
        assertEquals(a.spectralClass, b.spectralClass);
        assertEquals(a.temperature, b.temperature, 1e-6f);
    }

    private StarPosition makeStarPosition(long id) {
        StarPosition sp = new StarPosition();
        sp.uniqueId = id;
        sp.x = id * 100.0;
        sp.y = id * 50.0;
        sp.z = 0.0;
        sp.localDensity = 0.5f;
        return sp;
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.StarSystemCacheTest" --info`
Expected: FAIL — `StarSystemCache` class doesn't exist

- [ ] **Step 3: Implement StarSystemCache**

```java
// core/src/main/java/com/galacticodyssey/galaxy/StarSystemCache.java
package com.galacticodyssey.galaxy;

import java.util.LinkedHashMap;
import java.util.Map;

public final class StarSystemCache {
    private final StarSystemGenerator generator;
    private final LinkedHashMap<Long, StarSystem> cache;

    public StarSystemCache(long galaxySeed, int maxSize) {
        this.generator = new StarSystemGenerator(galaxySeed);
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, StarSystem> eldest) {
                return size() > maxSize;
            }
        };
    }

    public StarSystem get(StarPosition star, GalaxyRegion region) {
        StarSystem cached = cache.get(star.uniqueId);
        if (cached != null) return cached;
        StarSystem system = generator.generate(star, region);
        cache.put(star.uniqueId, system);
        return system;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.StarSystemCacheTest" --info`
Expected: All 3 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/StarSystemCache.java \
  core/src/test/java/com/galacticodyssey/galaxy/StarSystemCacheTest.java
git commit -m "feat(galaxy): add StarSystemCache with LRU eviction"
```

---

## Task 12: Full Pipeline Integration Test

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/planet/PipelineIntegrationTest.java`

- [ ] **Step 1: Write integration test**

```java
// core/src/test/java/com/galacticodyssey/planet/PipelineIntegrationTest.java
package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.*;
import com.galacticodyssey.planet.terrain.*;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PipelineIntegrationTest {
    private static final long GALAXY_SEED = 12345L;

    @Test
    void fullPipelineDeterministic() {
        StarSystem sysA = runPipelineTo(GALAXY_SEED, 42L);
        StarSystem sysB = runPipelineTo(GALAXY_SEED, 42L);

        assertEquals(sysA.spectralClass, sysB.spectralClass);
        assertEquals(sysA.orbits.size(), sysB.orbits.size());
        for (int i = 0; i < sysA.orbits.size(); i++) {
            assertEquals(sysA.orbits.get(i).orbitalRadius,
                         sysB.orbits.get(i).orbitalRadius, 1e-6f);
            Planet pa = sysA.orbits.get(i).planet;
            Planet pb = sysB.orbits.get(i).planet;
            assertEquals(pa.type, pb.type);
            assertEquals(pa.radius, pb.radius, 1e-6f);
        }
    }

    @Test
    void seedToTerrainChunkWithoutGLContext() {
        StarSystemGenerator starGen = new StarSystemGenerator(GALAXY_SEED);
        OrbitalLayoutGenerator layoutGen = new OrbitalLayoutGenerator();
        PlanetGenerator planetGen = new PlanetGenerator(GALAXY_SEED);
        AtmosphereGenerator atmoGen = new AtmosphereGenerator();
        BiomeMapper biomeMapper = new BiomeMapper();

        StarPosition pos = new StarPosition();
        pos.uniqueId = 42L;
        pos.x = 1000.0;
        pos.y = 500.0;
        pos.z = 0.0;
        pos.localDensity = 0.5f;

        StarSystem sys = starGen.generate(pos, GalaxyRegion.INNER_RIM);
        List<OrbitalSlot> orbits = layoutGen.generate(sys);
        sys.orbits.addAll(orbits);

        Planet planet = null;
        OrbitalSlot planetSlot = null;
        for (OrbitalSlot slot : orbits) {
            Planet p = planetGen.generate(slot, sys);
            slot.planet = p;
            if (p.type.hasSurface()) { planet = p; planetSlot = slot; break; }
        }
        assertNotNull(planet, "Should find at least one surface planet");

        Atmosphere atmo = atmoGen.generate(planet, sys);
        planet.atmosphere = atmo;
        BiomeMap biomeMap = biomeMapper.generate(planet, atmo);

        long terrainSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.TERRAIN_DOMAIN), 0);
        TerrainNoiseStack noise = new TerrainNoiseStack(terrainSeed);
        TerrainMeshBuilder.MeshData mesh = TerrainMeshBuilder.build(
            CubeFace.POS_Z, 0f, 0f, 1f, 1f, noise, biomeMap,
            planet.radius * 6371f, 2, null);

        assertEquals(TerrainMeshBuilder.GRID_SIZE * TerrainMeshBuilder.GRID_SIZE *
                     TerrainMeshBuilder.VERTEX_STRIDE, mesh.vertices.length);
        assertTrue(mesh.indices.length > 0);
    }

    @Test
    void systemGenerationPerformance() {
        StarSystemGenerator starGen = new StarSystemGenerator(GALAXY_SEED);
        OrbitalLayoutGenerator layoutGen = new OrbitalLayoutGenerator();
        PlanetGenerator planetGen = new PlanetGenerator(GALAXY_SEED);
        AtmosphereGenerator atmoGen = new AtmosphereGenerator();

        long start = System.nanoTime();
        for (long id = 0; id < 100; id++) {
            StarPosition pos = new StarPosition();
            pos.uniqueId = id;
            pos.x = id * 10.0;
            pos.y = id * 5.0;
            pos.z = 0.0;
            pos.localDensity = 0.5f;
            StarSystem sys = starGen.generate(pos, GalaxyRegion.INNER_RIM);
            List<OrbitalSlot> orbits = layoutGen.generate(sys);
            sys.orbits.addAll(orbits);
            for (OrbitalSlot slot : orbits) {
                Planet p = planetGen.generate(slot, sys);
                slot.planet = p;
                if (p.type.hasSurface()) {
                    Atmosphere atmo = atmoGen.generate(p, sys);
                    p.atmosphere = atmo;
                }
            }
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsed < 5000, "100 full system generations took " + elapsed + "ms, expected < 5000ms");
    }

    @Test
    void terrainChunkPerformance() {
        TerrainNoiseStack noise = new TerrainNoiseStack(42L);
        BiomeMap biomeMap = new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f,
            EnumSet.allOf(BiomeType.class));

        long start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            TerrainMeshBuilder.build(CubeFace.POS_Z, 0f, 0f, 1f, 1f,
                noise, biomeMap, 6371f, 2, null);
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        float perChunk = elapsed / 20f;
        assertTrue(perChunk < 50, "Terrain chunk gen took " + perChunk + "ms avg, expected < 50ms");
    }

    private StarSystem runPipelineTo(long galaxySeed, long starId) {
        StarSystemGenerator starGen = new StarSystemGenerator(galaxySeed);
        OrbitalLayoutGenerator layoutGen = new OrbitalLayoutGenerator();
        PlanetGenerator planetGen = new PlanetGenerator(galaxySeed);

        StarPosition pos = new StarPosition();
        pos.uniqueId = starId;
        pos.x = 1000.0;
        pos.y = 500.0;
        pos.z = 0.0;
        pos.localDensity = 0.5f;

        StarSystem sys = starGen.generate(pos, GalaxyRegion.INNER_RIM);
        List<OrbitalSlot> orbits = layoutGen.generate(sys);
        sys.orbits.addAll(orbits);
        for (OrbitalSlot slot : orbits) {
            slot.planet = planetGen.generate(slot, sys);
        }
        return sys;
    }
}
```

- [ ] **Step 2: Run the integration test**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.planet.PipelineIntegrationTest" --info`
Expected: All 4 tests PASS

- [ ] **Step 3: Run ALL tests to confirm nothing is broken**

Run: `gradlew.bat :core:test --info`
Expected: All tests PASS (existing galaxy tests + all new tests)

- [ ] **Step 4: Commit**

```bash
git add core/src/test/java/com/galacticodyssey/planet/PipelineIntegrationTest.java
git commit -m "test(planet): add full pipeline integration test — seed to terrain chunk"
```

---

## Summary

| Task | Component | Files | Tests |
|------|-----------|-------|-------|
| 1 | Foundation enums + JSON | 8 Java + 3 JSON | Compile check |
| 2 | StarSystem + Generator | 2 Java + 1 test | 6 tests |
| 3 | OrbitalSlot + Layout | 2 Java + 1 test | 5 tests |
| 4 | Planet + Moon + Generator | 3 Java + 1 test | 6 tests |
| 5 | Atmosphere + Generator | 2 Java + 1 test | 6 tests |
| 6 | Whittaker + BiomeMap + Mapper | 3 Java + 2 tests | 9 tests |
| 7 | CubeSphere math | 1 Java + 1 test | 4 tests |
| 8 | TerrainNoiseStack | 1 Java + 1 test | 4 tests |
| 9 | TerrainMeshBuilder | 1 Java + 1 test | 4 tests |
| 10 | Chunk + Quadtree + System | 3 Java | Compile check |
| 11 | StarSystemCache | 1 Java + 1 test | 3 tests |
| 12 | Pipeline integration | 1 test | 4 tests |

**Totals:** 27 Java source files, 3 JSON data files, 11 test files, 51 tests
