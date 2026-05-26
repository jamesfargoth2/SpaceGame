# Star-to-Surface Generation Pipeline — Design Spec

**Date:** 2026-05-26
**Scope:** Phase 2 procedural generation — stellar classification through full planetary terrain
**Architecture:** Waterfall pipeline, lazy-loaded, deterministic from galaxy seed

---

## 1. Overview

This system extends the existing galaxy generation (Phase 1) downward: from star positions into fully explorable planetary surfaces. Each layer in the pipeline feeds the next, generating progressively deeper detail only when the player approaches.

**Pipeline:**
```
StarPosition (existing, from GalaxyChunkManager)
  → StarSystemGenerator → StarSystem
    → OrbitalLayoutGenerator → List<OrbitalSlot>
      → PlanetGenerator → Planet
        → AtmosphereGenerator → Atmosphere
          → BiomeMapper → BiomeMap
            → PlanetTerrainSystem → TerrainChunk quadtree
```

**Design constraints:**
- Deterministic: same seed always produces identical output
- No shared Random state: each object gets `new Random(derivedSeed)`
- Lazy: only generate deeper layers when the player approaches
- Testable without GL context (CLAUDE.md rule)
- Data-driven: star/planet/biome parameters in JSON files

---

## 2. Stellar Classification

**Generator:** `StarSystemGenerator`
**Input:** `StarPosition` (uniqueId, coordinates, localDensity) + `GalaxyRegion`
**Output:** `StarSystem`

### 2.1 Spectral Classes

Stars use the standard O/B/A/F/G/K/M classification with plausible sci-fi frequency distribution (boosted rare types for gameplay variety):

| Class | Frequency | Temperature (K) | Planet Count | Habitable Odds |
|-------|-----------|-----------------|--------------|----------------|
| O — Blue | 1% | 30,000–50,000 | 1–3 | 5% |
| B — Blue-white | 2% | 10,000–30,000 | 1–4 | 10% |
| A — White | 5% | 7,500–10,000 | 2–5 | 20% |
| F — Yellow-white | 8% | 6,000–7,500 | 2–6 | 40% |
| G — Yellow (Sol-like) | 12% | 5,200–6,000 | 3–6 | 60% |
| K — Orange | 22% | 3,700–5,200 | 2–6 | 45% |
| M — Red dwarf | 50% | 2,400–3,700 | 2–4 | 25% |

### 2.2 Luminosity Classes

After spectral class is rolled, 10% of stars become Giants or Supergiants. 5% chance of binary system (companion star, not a second independently generated system).

- `MAIN_SEQUENCE` — 85% of stars
- `GIANT` — 8%
- `SUPERGIANT` — 2%
- `WHITE_DWARF` — 5%

### 2.3 Galaxy Region Modifiers

The existing `GalaxyRegionClassifier` modifies generation:

| Region | Effect |
|--------|--------|
| CORE | +metallicity → more rocky planets, more O/B stars, more giants |
| INNER_RIM | Balanced — baseline distribution |
| OUTER_RIM | −metallicity → more gas giants, fewer rocky worlds |
| VOID | Ancient low-metal stars, mostly M/K, sparse systems |

### 2.4 StarSystem Data Model

```java
class StarSystem {
    long uniqueId;              // from StarPosition
    long seed;                  // SeedDeriver.forId(STAR_DOMAIN, uniqueId)

    SpectralClass spectralClass;    // O, B, A, F, G, K, M
    LuminosityClass luminosityClass; // MAIN_SEQUENCE, GIANT, SUPERGIANT, WHITE_DWARF
    float temperature;              // Kelvin
    float luminosity;               // Solar luminosities
    float mass;                     // Solar masses
    float radius;                   // Solar radii
    float age;                      // Billion years
    Color color;                    // derived from temperature

    float habZoneInner;         // √(luminosity) × 0.75 AU
    float habZoneOuter;         // √(luminosity) × 1.77 AU

    List<OrbitalSlot> orbits;
}
```

### 2.5 Derived Properties

- **Habitable zone:** `[√(luminosity) × 0.75, √(luminosity) × 1.77]` AU
- **Frost line:** `√(luminosity) × 4.85` AU
- **Color:** mapped from temperature via blackbody approximation lookup
- **System edge:** `√(luminosity) × 40` AU (for orbital placement bounds)

---

## 3. Orbital Layout & Planet Types

**Generators:** `OrbitalLayoutGenerator` + `PlanetGenerator`
**Input:** `StarSystem`
**Output:** `List<OrbitalSlot>`, each containing a `Planet`

`OrbitalLayoutGenerator` creates the orbital slots (radii, zones). `PlanetGenerator` then fills each slot with a `Planet` by rolling type from the zone's probability table and generating properties within that type's constraints. They are separate classes — orbital math vs. planet property generation — but called together during system generation.

### 3.1 Orbital Zones

Each system is divided into 4 radial zones based on the star's habitable zone and frost line:

| Zone | Range | Dominant Types |
|------|-------|----------------|
| INNER | 0.1 AU – habZoneInner | MOLTEN, BARREN, TOXIC |
| HABITABLE | habZoneInner – habZoneOuter | TERRAN, ARID, OCEAN, BARREN, TOXIC |
| OUTER | habZoneOuter – frostLine×3 | GAS_GIANT, ICE_GIANT |
| DEEP | frostLine×3 – systemEdge | ICE_GIANT, ICE_WORLD, DWARF |

### 3.2 Orbital Placement Algorithm

Titius-Bode-like exponential spacing with jitter:

```
baseAU = 0.2 + rng.nextFloat() × 0.2
for i in 0..planetCount:
    orbitalRadius[i] = baseAU × (1.4 + rng.nextFloat() × 0.8)^i
    orbitalRadius[i] *= 0.85 + rng.nextFloat() × 0.3   // ±15% jitter
```

Each orbit is classified by zone, then planet type is rolled from that zone's probability table.

### 3.3 Planet Types

| Type | Zone | Radius (R⊕) | Gravity (g) | Atmosphere | Moons |
|------|------|-------------|-------------|------------|-------|
| MOLTEN | Inner | 0.3–0.8 | 0.2–0.6 | None/trace | 0 |
| BARREN | Inner, Hab | 0.4–1.2 | 0.3–0.9 | None/thin | 0–1 |
| ARID | Hab | 0.7–1.5 | 0.5–1.2 | Thin CO₂/N₂ | 0–2 |
| TERRAN | Hab | 0.8–1.8 | 0.7–1.4 | N₂/O₂ | 0–3 |
| OCEAN | Hab | 1.0–2.0 | 0.8–1.5 | Dense H₂O/N₂ | 0–2 |
| TOXIC | Inner, Hab | 0.8–1.6 | 0.6–1.3 | Dense SO₂/CO₂ | 0–1 |
| GAS_GIANT | Outer, Deep | 6–15 | 1.5–3.0 | H₂/He (no surface) | 2–8 |
| ICE_GIANT | Outer, Deep | 3–6 | 1.0–1.8 | H₂/He/CH₄ (no surface) | 1–5 |
| ICE_WORLD | Outer, Deep | 0.3–1.0 | 0.2–0.7 | None/trace N₂ | 0–1 |
| DWARF | Deep | 0.1–0.4 | 0.03–0.15 | None | 0 |

### 3.4 Data Models

```java
class OrbitalSlot {
    int index;                  // 0-based from star
    float orbitalRadius;        // AU
    float eccentricity;         // 0.0–0.3
    float orbitalPeriod;        // derived: r^1.5 (Kepler)
    OrbitalZone zone;           // INNER, HABITABLE, OUTER, DEEP
    Planet planet;
}

class Planet {
    long seed;
    PlanetType type;
    float radius;               // Earth radii
    float mass;                 // Earth masses
    float surfaceGravity;       // g (derived: mass/radius²)
    float dayLength;            // hours
    float axialTilt;            // degrees
    boolean tidallyLocked;      // common for inner-zone M-dwarf planets
    Atmosphere atmosphere;      // null for airless bodies
    List<Moon> moons;
}
```

Moons reuse the Planet generation pipeline with type constrained to BARREN or ICE_WORLD and smaller parameter ranges.

---

## 4. Atmosphere Generation

**Generator:** `AtmosphereGenerator`
**Input:** `Planet` (type, mass, orbital radius) + `StarSystem` (luminosity)
**Output:** `Atmosphere`

### 4.1 Atmosphere Per Planet Type

| Planet Type | Has Atmo? | Primary Gases | Pressure (atm) | Greenhouse Mult |
|-------------|-----------|---------------|-----------------|-----------------|
| MOLTEN | No | — | 0 | — |
| BARREN | 10% chance | CO₂ trace | 0.001–0.01 | 1.0 |
| ARID | Yes | CO₂ 80%, N₂ 15%, Ar 5% | 0.01–0.5 | 1.1–1.4 |
| TERRAN | Yes | N₂ 60–80%, O₂ 15–25%, trace | 0.5–2.0 | 1.1–1.3 |
| OCEAN | Yes | N₂ 50–70%, H₂O 20–40%, CO₂ | 1.0–4.0 | 1.3–1.8 |
| TOXIC | Yes | SO₂ 40%, CO₂ 35%, HCl, N₂ | 2.0–90.0 | 1.5–3.0 |
| GAS_GIANT | N/A | H₂/He (no surface) | — | — |
| ICE_GIANT | N/A | H₂/He/CH₄ (no surface) | — | — |
| ICE_WORLD | 30% chance | N₂ thin | 0.001–0.05 | 1.0 |
| DWARF | No | — | 0 | — |

Gas giants and ice giants have no `Atmosphere` object — they are flagged as "no surface" at the planet type level.

### 4.2 Temperature Calculation

```
// Step 1: Equilibrium temperature from star
T_eq = 278 × (luminosity^0.25) / (orbitalRadius^0.5)    // Kelvin

// Step 2: Greenhouse warming
T_surface = T_eq × greenhouseMultiplier

// Step 3: Latitude gradient
T(lat) = T_surface × (1.0 − 0.4 × sin²(lat))

// Step 4: Tidal lock override
if (tidallyLocked) {
    T_day = T_surface × 1.4         // permanent day side
    T_night = T_surface × 0.3       // permanent night side
    T_terminator = T_surface × 0.85  // twilight band (most habitable)
}
```

### 4.3 Hazard Classification

| Hazard | Condition | Player Effect |
|--------|-----------|---------------|
| VACUUM | pressure < 0.01 | Sealed suit + O₂ supply required |
| CRUSHING | pressure > 10.0 | Pressure suit, limited EVA time |
| TOXIC | SO₂/HCl/NH₃ > 5% | Filtered suit, equipment damage |
| CORROSIVE | SO₂ > 20% + pressure > 5.0 | Suit degradation, limited stay |
| EXTREME_HEAT | surfaceTemp > 400K | Thermal suit + coolant |
| EXTREME_COLD | surfaceTemp < 180K | Thermal suit + heater |

A planet can have multiple hazards (e.g., TOXIC + CRUSHING for a Venus-like world). All applicable hazards are stored in the `hazards` EnumSet, derived deterministically from the atmosphere's composition, pressure, and temperature during generation.

### 4.4 Atmosphere Data Model

```java
class Atmosphere {
    Map<Gas, Float> composition;     // gas → fraction (sums to 1.0)
    float surfacePressure;           // atmospheres
    float greenhouseMultiplier;      // 1.0 = no greenhouse
    float equilibriumTemp;           // K (before greenhouse)
    float surfaceTemp;               // K (after greenhouse, at equator)
    boolean breathable;              // O₂ 15–25%, pressure 0.5–2.0, no toxics
    EnumSet<AtmoHazard> hazards;     // all applicable hazards (can be multiple)
}

enum Gas { N2, O2, CO2, H2O, SO2, HCl, Ar, CH4, H2, He, NH3 }

enum AtmoHazard { NONE, VACUUM, CRUSHING, TOXIC, CORROSIVE, EXTREME_HEAT, EXTREME_COLD }
```

---

## 5. Biome Mapping

**Generator:** `BiomeMapper`
**Input:** `Planet` + `Atmosphere`
**Output:** `BiomeMap`

### 5.1 Whittaker Classification Grid

Biomes are determined by temperature (x-axis) and moisture (y-axis):

|  | Freezing | Cool | Warm | Hot |
|--|----------|------|------|-----|
| **Wet** | ICE_SHEET | BOREAL_FOREST | TROPICAL_FOREST | SWAMP |
| **Moist** | TUNDRA | TEMPERATE_FOREST | GRASSLAND | SAVANNA |
| **Dry** | POLAR_DESERT | STEPPE | ARID_SHRUB | BADLANDS |
| **Arid** | ICE_FIELD | ROCKY_WASTE | DESERT | VOLCANIC |

Plus OCEAN and LAKE as elevation-based overrides.

### 5.2 Biome Assignment Pipeline

```
// For each surface point (lat, lon):

// 1. Temperature from latitude
float temp = surfaceTemp × (1.0 − 0.4 × sin²(lat));

// 2. Base moisture from atmosphere type
float baseMoisture = atmosphereMoistureIndex(atmosphere);
//   OCEAN planet → 0.8–1.0, TERRAN → 0.3–0.7, ARID → 0.05–0.2

// 3. Local moisture variation (seeded Simplex fBm)
float moistureNoise = SimplexNoise.fbm(lon, lat, seed, octaves=4);
float moisture = clamp(baseMoisture + moistureNoise × 0.3, 0, 1);

// 4. Elevation modifier
temp -= elevation × 6.5f / 1000f;    // lapse rate: 6.5K per 1000m
moisture -= elevation × 0.1f;

// 5. Whittaker grid lookup
BiomeType biome = WhittakerGrid.classify(temp, moisture);

// 6. Elevation overrides
if (elevation < seaLevel && temp > 273) biome = OCEAN;
if (elevation < seaLevel && temp <= 273) biome = ICE_SHEET;
if (elevation > snowLine) biome = ICE_FIELD;
```

### 5.3 Planet-Type Biome Constraints

| Planet Type | Allowed Biomes |
|-------------|----------------|
| TERRAN | All 16 biomes |
| OCEAN | OCEAN, ICE_SHEET, SWAMP, TROPICAL_FOREST (islands) |
| ARID | DESERT, ARID_SHRUB, BADLANDS, ROCKY_WASTE, STEPPE, POLAR_DESERT |
| TOXIC | VOLCANIC, BADLANDS, ROCKY_WASTE |
| ICE_WORLD | ICE_SHEET, ICE_FIELD, POLAR_DESERT, TUNDRA |
| BARREN | ROCKY_WASTE, DESERT, POLAR_DESERT |
| MOLTEN | VOLCANIC only |

### 5.4 BiomeMap Data Model

```java
class BiomeMap {
    long seed;
    float seaLevel;             // 0.0–0.5 of elevation range
    float snowLine;             // elevation threshold → ice
    float baseMoisture;         // from atmosphere
    Set<BiomeType> allowedBiomes;

    // Query interface
    BiomeType getBiome(float lat, float lon, float elevation);
    float getTemperature(float lat, float lon);
    float getMoisture(float lat, float lon);
}

enum BiomeType {
    ICE_SHEET, TUNDRA, POLAR_DESERT, ICE_FIELD,
    BOREAL_FOREST, TEMPERATE_FOREST, STEPPE, ROCKY_WASTE,
    TROPICAL_FOREST, GRASSLAND, ARID_SHRUB, DESERT,
    SWAMP, SAVANNA, BADLANDS, VOLCANIC,
    OCEAN, LAKE
}
```

---

## 6. Terrain Generation

**System:** `PlanetTerrainSystem`
**Input:** `BiomeMap` + `Planet` properties
**Output:** `TerrainChunk` quadtree with meshes and Bullet colliders

### 6.1 Cube-Sphere Projection

A unit cube with 6 faces (`CubeFace` enum: POS_X, NEG_X, POS_Y, NEG_Y, POS_Z, NEG_Z) is inflated onto a sphere by normalizing each vertex. This avoids polar pinching artifacts from lat/lon grids. Each face is subdivided into a quadtree of terrain chunks.

### 6.2 LOD Levels

| LOD | Grid per Chunk | Detail Scale | Distance from Camera |
|-----|---------------|--------------|---------------------|
| 0 (coarsest) | 33×33 | Continental | > 10× planet radius |
| 1 | 33×33 | Mountain ranges | 2×–10× radius |
| 2 | 33×33 | Valleys, ridges | 0.5×–2× radius |
| 3 | 33×33 | Hills, craters | 0.1×–0.5× radius |
| 4 | 33×33 | Boulders, dunes | 0.01×–0.1× radius |
| 5 (finest) | 33×33 | Ground-level | < 0.01× radius |

Each chunk is always 33×33 vertices (32×32 quads). LOD is controlled by quadtree depth, not vertex density. Max depth 5 = 6 × 4^5 = 6,144 possible leaf chunks, but only ~50–100 are active near the camera at any time.

### 6.3 Heightmap Generation — Biome-Shaped Noise

```
// For each vertex in a terrain chunk:

// 1. Cube-sphere mapping
Vector3 dir = cubeToSphere(face, u, v);
float lat = asin(dir.y);
float lon = atan2(dir.z, dir.x);

// 2. Continental noise (low frequency, high amplitude)
float continentNoise = fbm(dir × 2.0, seed, octaves=6, persistence=0.5);

// 3. Ridge noise (medium frequency)
float ridgeNoise = ridgedFbm(dir × 8.0, seed+1, octaves=5);

// 4. Detail noise (high frequency, LOD-dependent)
float detailNoise = fbm(dir × 64.0, seed+2, octaves=3);

// 5. Biome-specific shaping
BiomeType biome = biomeMap.getBiome(lat, lon, continentNoise);
float amplitude = biomeAmplitude(biome);
float ridgeMix = biomeRidgeMix(biome);

// 6. Combine
float height = continentNoise
             + ridgeNoise × ridgeMix × amplitude
             + detailNoise × amplitude × 0.1;
```

### 6.4 Biome Terrain Profiles

| Biome | Amplitude | Ridge Mix | Character |
|-------|-----------|-----------|-----------|
| OCEAN | 0.05 | 0.1 | Flat seabed, gentle ridges |
| LAKE | 0.05 | 0.05 | Shallow basin, minimal relief |
| ICE_SHEET | 0.1 | 0.15 | Smooth glacial surface, pressure ridges |
| ICE_FIELD | 0.15 | 0.2 | Cracked ice plains, low hummocks |
| POLAR_DESERT | 0.2 | 0.15 | Flat frozen gravel, sparse outcrops |
| TUNDRA | 0.25 | 0.2 | Low rolling hills, frozen plateaus |
| DESERT | 0.15 | 0.05 | Rolling dunes, mostly flat |
| ARID_SHRUB | 0.2 | 0.15 | Dry hills, scattered rocky outcrops |
| STEPPE | 0.2 | 0.1 | Broad rolling plains, shallow valleys |
| GRASSLAND | 0.2 | 0.1 | Gentle hills and valleys |
| SAVANNA | 0.2 | 0.15 | Flat with scattered kopjes, dry riverbeds |
| SWAMP | 0.1 | 0.05 | Low-lying, flat, waterlogged basins |
| TEMPERATE_FOREST | 0.3 | 0.35 | Rolling wooded hills, river valleys |
| BOREAL_FOREST | 0.35 | 0.4 | Mountainous with valleys |
| TROPICAL_FOREST | 0.3 | 0.3 | Hilly, river valleys, plateaus |
| ROCKY_WASTE | 0.5 | 0.8 | Jagged peaks, canyons |
| BADLANDS | 0.45 | 0.7 | Eroded mesas, deep ravines |
| VOLCANIC | 0.6 | 0.9 | Calderas, sharp peaks, lava channels |

### 6.5 Chunk Lifecycle

```
// Quadtree update each frame:
for (each face root chunk) {
    recursiveUpdate(chunk, cameraPos);
}

void recursiveUpdate(chunk, cam) {
    float dist = chunk.boundingSphere.distanceTo(cam);
    float screenSize = chunk.arcLength / dist;

    if (screenSize > SPLIT_THRESHOLD && chunk.depth < MAX_DEPTH) {
        chunk.split();       // create 4 children, generate meshes async
        for (child : chunk.children) recursiveUpdate(child, cam);
    } else if (screenSize < MERGE_THRESHOLD && chunk.hasChildren()) {
        chunk.merge();       // dispose children, use this chunk's mesh
    }
}

// Mesh generation is async (background thread):
// 1. Compute 33×33 heightmap
// 2. Query biomeMap for each vertex
// 3. Build vertex buffer (position + normal + biome color)
// 4. Post to GL thread for upload
// Parent chunk stays visible until children are ready (no popping)
```

### 6.6 Edge Stitching

Adjacent chunks at different LODs create T-junctions. When a chunk's neighbor is coarser, the finer chunk skips every other edge vertex on that border to match. Handled in `TerrainMeshBuilder`.

### 6.7 TerrainChunk Data Model

```java
class TerrainChunk {
    CubeFace face;
    int depth;                  // 0–5
    float u0, v0, u1, v1;      // face-UV bounds
    BoundingSphere bounds;
    TerrainChunk[] children;    // null if leaf
    Mesh mesh;                  // 33×33 grid
    TerrainChunk[] neighbors;   // N/S/E/W for stitching
    boolean meshReady;          // false until async gen completes
    btRigidBody collider;       // Bullet heightfield for physics
}
```

---

## 7. Integration & Lazy Loading

### 7.1 Generation Triggers

| Player Action | Generated | Approx Time |
|---------------|-----------|-------------|
| Opens galaxy map | StarPositions (already loaded, Phase 1) | 0ms |
| Warps to star | StarSystem + OrbitalLayout | ~1ms |
| Enters planet SOI | Atmosphere + BiomeMap | ~5ms |
| Enters orbit / begins landing | Terrain quadtree LOD 0–2 | ~2s (async) |
| Lands / exits ship | Terrain LOD 3–5 + Bullet colliders | Continuous |

### 7.2 Cache Strategy

- **StarSystemCache:** LRU, 64 entries. Evicts least-recently-visited systems.
- **Atmosphere / BiomeMap:** Per-planet, evicted when player leaves the planet.
- **TerrainChunks:** Owned by quadtree lifecycle. Split/merge manages memory.

### 7.3 Seed Flow

All seeds derive from the existing `SeedDeriver` hierarchy:

```
galaxySeed
  → SeedDeriver.forId(STAR_DOMAIN, starUniqueId) → starSeed
    → SeedDeriver.forId(PLANET_DOMAIN, planetIndex) → planetSeed
      → SeedDeriver.forId(ATMOSPHERE_DOMAIN, 0) → atmosphereSeed
      → SeedDeriver.forId(BIOME_DOMAIN, 0) → biomeSeed
      → SeedDeriver.forId(TERRAIN_DOMAIN, 0) → terrainSeed
```

---

## 8. File Placement

### 8.1 Java Source Files (24 new files)

**`galaxy/` (existing package — 7 new files):**
- `StarSystem.java` — data model
- `SpectralClass.java` — enum (O–M + luminosity classes)
- `StarSystemGenerator.java` — StarPosition → StarSystem
- `OrbitalSlot.java` — orbit data + zone
- `OrbitalZone.java` — enum (INNER, HABITABLE, OUTER, DEEP)
- `OrbitalLayoutGenerator.java` — StarSystem → orbits
- `StarSystemCache.java` — LRU cache

**`planet/` (new package — 12 new files):**
- `Planet.java` — data model
- `PlanetType.java` — enum (MOLTEN through DWARF)
- `PlanetGenerator.java` — OrbitalSlot → Planet
- `Moon.java` — data model (extends Planet pipeline, constrained types)
- `Atmosphere.java` — data model
- `Gas.java` — enum
- `AtmoHazard.java` — enum
- `AtmosphereGenerator.java` — Planet → Atmosphere
- `BiomeType.java` — enum (18 biomes)
- `BiomeMap.java` — query interface
- `BiomeMapper.java` — Planet + Atmosphere → BiomeMap
- `WhittakerGrid.java` — temp×moisture → BiomeType lookup

**`planet/terrain/` (new subpackage — 7 new files):**
- `CubeFace.java` — enum (6 faces)
- `CubeSphere.java` — UV → sphere direction math
- `TerrainChunk.java` — quadtree node + mesh
- `TerrainQuadtree.java` — 6-face quadtree manager
- `TerrainMeshBuilder.java` — heightmap → mesh + normals + stitching
- `TerrainNoiseStack.java` — continent + ridge + detail noise layers
- `PlanetTerrainSystem.java` — ECS system: quadtree update + async gen

### 8.2 Data Files (3 new JSON files)

**`core/src/main/resources/data/planet/`:**
- `spectral_classes.json` — frequencies, temp ranges, planet count odds
- `planet_types.json` — radius/gravity/atmosphere ranges per type
- `biome_profiles.json` — amplitude/ridgeMix per biome

---

## 9. Testing Strategy

| Layer | Test Type | Verification |
|-------|-----------|-------------|
| StarSystemGenerator | Unit (JUnit 5) | Determinism. Spectral class distribution over 10K samples matches ±5%. Temp/luminosity within class ranges. |
| OrbitalLayoutGenerator | Unit (JUnit 5) | Determinism. Planet count in spec range. Radii increase monotonically. Zone classification correct. No overlapping orbits. |
| AtmosphereGenerator | Unit (JUnit 5) | Determinism. Gas fractions sum to 1.0. Pressure in type range. Breathability correct. Hazards derived correctly. |
| BiomeMapper | Unit (JUnit 5) | Determinism. Only allowed biomes for planet type. Polar regions cold, equatorial warm. Whittaker grid has no gaps. |
| CubeSphere | Unit (JUnit 5) | All 6 faces produce unit vectors. No seam discontinuities at face edges. Inverse round-trips correctly. |
| TerrainNoiseStack | Unit (JUnit 5) | Determinism. Heights in expected bounds. Biome amplitude scaling correct. |
| TerrainMeshBuilder | Unit (JUnit 5) | Vertex count = 33×33. No degenerate triangles. Normals outward. Edge stitching correct when neighbor LOD differs by 1. |
| Full Pipeline | Integration | Seed → StarSystem → Planets → Atmosphere → BiomeMap → TerrainChunk. End-to-end determinism. No GL context. Performance: system gen < 50ms, terrain chunk < 5ms. |
