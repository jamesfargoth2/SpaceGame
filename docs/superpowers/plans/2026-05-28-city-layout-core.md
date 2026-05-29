# City Layout Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a deterministic, pure-data procedural city layout generator (population-driven size, landmarks, streets, blocks, lots, districts, building-function tags, walls, terrain conformance) plus a standalone top-down debug renderer.

**Architecture:** A `CityLayoutGenerator` orchestrates a staged pipeline of small pure components (size profile → form → landmarks → street/block grid → district zoning → lot subdivision → function tagging → wall → terrain conform). Everything is plain Java + libGDX **math** types (no GL, no ECS). All content (size tiers, district mix, faction form bias) lives in JSON loaded by a `CityDataRegistry`. v1 uses axis-aligned rectangular blocks/lots; `CityForm` is realized as modulations of an axis-aligned cell grid.

**Tech Stack:** Java, libGDX (`com.badlogic.gdx.math`: `Vector2`, `Rectangle`, `Polygon`, `ConvexHull`; `com.badlogic.gdx.utils.Json`/`JsonReader`/`JsonValue`), JUnit 5, Gradle (`:core` for logic+tests, `:desktop` for the debug launcher). Reuses `SeedDeriver`, `RngUtil`, `SpaceNameGenerator`, `FactionEthos`.

**Spec:** `docs/superpowers/specs/2026-05-28-city-layout-core-design.md`

**Conventions observed in this repo:**
- Procgen is deterministic via `SeedDeriver.domain(parentSeed, DOMAIN_CONST)` / `forId(domainSeed, id)`; never `Math.random` / wall-clock.
- Data registries: public-field definition POJOs + a registry that parses `JsonValue` (cf. `CommodityRegistry`). Tests build registries in-memory or parse classpath JSON (no `Gdx` backend needed — `new JsonReader().parse(reader)` works without GL).
- Run tests: `./gradlew :core:test` (single class: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.CitySizeProfileTest"`).

**Package layout (all new):**
- `com.galacticodyssey.city.layout.model` — enums + data POJOs + `CityLayout`
- `com.galacticodyssey.city.layout` — `CityRequest`, `TerrainSampler`, stages, `CityLayoutGenerator`
- `com.galacticodyssey.city.data` — `CityDataRegistry` + definition POJOs
- `core/src/main/resources/data/cities/` — JSON content
- `desktop/...` — debug launcher + renderer

---

## File Structure

| File | Responsibility |
|---|---|
| `core/.../galaxy/SeedDeriver.java` (modify) | Add `CITY_DOMAIN` + `cityDomain()` |
| `core/.../city/layout/model/CityType.java` | 7 size-tier enum |
| `core/.../city/layout/model/CityForm.java` | GRID/ORGANIC/RADIAL/LINEAR/SPRAWL |
| `core/.../city/layout/model/StreetTier.java` | AVENUE/STREET/ALLEY |
| `core/.../city/layout/model/DistrictType.java` | district set |
| `core/.../city/layout/model/BuildingFunction.java` | A/B contract tags |
| `core/.../city/layout/model/LandmarkType.java` | landmark kinds |
| `core/.../city/layout/model/GalaxyAnchor.java` | double-precision placeholder for E |
| `core/.../city/layout/model/Landmark.java` | type + local position + authored flag |
| `core/.../city/layout/model/Street.java` | start/end + tier |
| `core/.../city/layout/model/CityBlock.java` | footprint + district |
| `core/.../city/layout/model/BuildingLot.java` | footprint + district + function |
| `core/.../city/layout/model/CityWall.java` / `CityGate.java` | hull + gates |
| `core/.../city/layout/model/CityLayout.java` | aggregate output |
| `core/.../city/layout/TerrainSampler.java` / `FlatTerrainSampler.java` | injected terrain interface + flat default |
| `core/.../city/layout/AuthoredLandmark.java` | hand-placed landmark input |
| `core/.../city/layout/CityRequest.java` | generator input |
| `core/.../city/data/SizeTierDef.java` / `DistrictMixDef.java` / `FunctionWeight.java` | definition POJOs |
| `core/.../city/data/CityDataRegistry.java` | loads + serves JSON content |
| `core/.../city/layout/CitySizeProfile.java` | population → spatial params |
| `core/.../city/layout/CityFormSelector.java` | pick CityForm |
| `core/.../city/layout/LandmarkPlacer.java` | place/merge landmarks |
| `core/.../city/layout/StreetNetwork.java` | `{streets, blocks}` holder |
| `core/.../city/layout/StreetNetworkBuilder.java` | grid + form modulation |
| `core/.../city/layout/DistrictZoner.java` | assign districts |
| `core/.../city/layout/LotSubdivider.java` | blocks → lots |
| `core/.../city/layout/LotFunctionAssigner.java` | tag lots with functions |
| `core/.../city/layout/WallBuilder.java` | hull wall + gates |
| `core/.../city/layout/TerrainConformer.java` | purge bad-ground streets/lots |
| `core/.../city/layout/CityLayoutGenerator.java` | orchestrator |
| `core/.../data/names/SpaceNameGenerator.java` (modify) | add `cityName(rng)` |
| `core/src/main/resources/data/cities/*.json` | content |
| `desktop/.../CityLayoutDebugLauncher.java` / `CityLayoutDebugRenderer.java` | top-down debug view |

---

## Task 1: Seed domain for cities

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java`
- Test: `core/src/test/java/com/galacticodyssey/galaxy/SeedDeriverCityTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeedDeriverCityTest {
    @Test
    void cityDomainIsDeterministicAndDistinct() {
        long a = SeedDeriver.cityDomain(42L);
        long b = SeedDeriver.cityDomain(42L);
        assertEquals(a, b, "same parent seed -> same city domain");
        assertNotEquals(SeedDeriver.cityDomain(42L), SeedDeriver.cityDomain(43L));
        // Distinct from an unrelated domain on the same parent seed
        assertNotEquals(SeedDeriver.cityDomain(42L), SeedDeriver.npcDomain(42L));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.SeedDeriverCityTest"`
Expected: FAIL — `cannot find symbol: method cityDomain`.

- [ ] **Step 3: Add the constant and helper**

In `SeedDeriver.java`, add a constant alongside the others (use a fresh unique value):

```java
    public static final long CITY_DOMAIN = 0xC17ED00DCAFEF00DL;
```

And a helper next to `npcDomain`:

```java
    public static long cityDomain(long parentSeed) {
        return domain(parentSeed, CITY_DOMAIN);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.SeedDeriverCityTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java core/src/test/java/com/galacticodyssey/galaxy/SeedDeriverCityTest.java
git commit -m "feat(city): add CITY_DOMAIN seed derivation"
```

---

## Task 2: Model enums

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/CityType.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/CityForm.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/StreetTier.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/DistrictType.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/BuildingFunction.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/LandmarkType.java`
- Test: `core/src/test/java/com/galacticodyssey/city/layout/model/EnumsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.city.layout.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnumsTest {
    @Test
    void cityTypeHasSevenTiersInPopulationOrder() {
        CityType[] v = CityType.values();
        assertEquals(7, v.length);
        assertEquals(CityType.OUTPOST, v[0]);
        assertEquals(CityType.LARGE_METROPOLIS, v[6]);
    }

    @Test
    void enumsContainExpectedMembers() {
        assertNotNull(CityForm.valueOf("RADIAL"));
        assertNotNull(StreetTier.valueOf("AVENUE"));
        assertNotNull(DistrictType.valueOf("GOVERNMENT"));
        assertNotNull(DistrictType.valueOf("SLUMS"));
        assertNotNull(BuildingFunction.valueOf("FACTION_HQ"));
        assertNotNull(BuildingFunction.valueOf("CANTINA"));
        assertNotNull(LandmarkType.valueOf("SPACEPORT"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.model.EnumsTest"`
Expected: FAIL — packages/enums do not exist.

- [ ] **Step 3: Create the enums**

`CityType.java` (declaration order = ascending population):
```java
package com.galacticodyssey.city.layout.model;

public enum CityType {
    OUTPOST, FRONTIER_TOWN, COLONY, CITY, LARGE_CITY, METROPOLIS, LARGE_METROPOLIS
}
```

`CityForm.java`:
```java
package com.galacticodyssey.city.layout.model;

public enum CityForm { GRID, ORGANIC, RADIAL, LINEAR, SPRAWL }
```

`StreetTier.java`:
```java
package com.galacticodyssey.city.layout.model;

public enum StreetTier { AVENUE, STREET, ALLEY }
```

`DistrictType.java`:
```java
package com.galacticodyssey.city.layout.model;

public enum DistrictType {
    SPACEPORT, COMMERCIAL, RESIDENTIAL, INDUSTRIAL, GOVERNMENT, SLUMS,
    RELIGIOUS, GARDEN, MILITARY, UNKNOWN
}
```

`BuildingFunction.java`:
```java
package com.galacticodyssey.city.layout.model;

public enum BuildingFunction {
    HOUSE, APARTMENT, TENEMENT, SHOP, MARKET_STALL, CANTINA, WAREHOUSE, WORKSHOP,
    FACTORY, FACTION_HQ, COURTHOUSE, TOWN_HALL, BARRACKS, TEMPLE, SHRINE, CLINIC,
    HANGAR, TERMINAL, PARK, EMPTY_LOT
}
```

`LandmarkType.java`:
```java
package com.galacticodyssey.city.layout.model;

public enum LandmarkType { CIVIC_CENTRE, SPACEPORT, MARKET_PLAZA, FACTION_LANDMARK, AUTHORED }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.model.EnumsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/city/layout/model/
git add core/src/test/java/com/galacticodyssey/city/layout/model/EnumsTest.java
git commit -m "feat(city): add city layout model enums"
```

---

## Task 3: Model data POJOs

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/GalaxyAnchor.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/Landmark.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/Street.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/CityBlock.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/BuildingLot.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/CityGate.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/CityWall.java`
- Test: `core/src/test/java/com/galacticodyssey/city/layout/model/ModelPojoTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.city.layout.model;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelPojoTest {
    @Test
    void blockCentroidIsRectangleCentre() {
        CityBlock b = new CityBlock(new Rectangle(10, 20, 40, 60));
        assertEquals(new Vector2(30, 50), b.centroid());
    }

    @Test
    void lotCarriesDistrictAndFunction() {
        BuildingLot lot = new BuildingLot(new Rectangle(0, 0, 10, 10), DistrictType.COMMERCIAL);
        lot.function = BuildingFunction.SHOP;
        assertEquals(DistrictType.COMMERCIAL, lot.district);
        assertEquals(BuildingFunction.SHOP, lot.function);
    }

    @Test
    void galaxyAnchorDefaultsToIdentity() {
        GalaxyAnchor a = new GalaxyAnchor();
        assertFalse(a.assigned, "A leaves the anchor unassigned for E to fill");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.model.ModelPojoTest"`
Expected: FAIL — classes missing.

- [ ] **Step 3: Create the POJOs**

`GalaxyAnchor.java`:
```java
package com.galacticodyssey.city.layout.model;

/** Double-precision galaxy/planet placement metadata. Sub-project A leaves this
 *  unassigned; sub-project E fills it when projecting the local layout onto a planet. */
public final class GalaxyAnchor {
    public boolean assigned = false;
    public double galaxyX, galaxyY, galaxyZ; // floating-origin galaxy coords
    public double latitudeDeg, longitudeDeg; // surface position
    public float  headingDeg;                // local-frame orientation
}
```

`Landmark.java`:
```java
package com.galacticodyssey.city.layout.model;

import com.badlogic.gdx.math.Vector2;

public final class Landmark {
    public final LandmarkType type;
    public final Vector2 position; // local metres
    public final boolean authored;

    public Landmark(LandmarkType type, Vector2 position, boolean authored) {
        this.type = type;
        this.position = position;
        this.authored = authored;
    }
}
```

`Street.java`:
```java
package com.galacticodyssey.city.layout.model;

import com.badlogic.gdx.math.Vector2;

public final class Street {
    public final Vector2 start;
    public final Vector2 end;
    public final StreetTier tier;

    public Street(Vector2 start, Vector2 end, StreetTier tier) {
        this.start = start;
        this.end = end;
        this.tier = tier;
    }
}
```

`CityBlock.java`:
```java
package com.galacticodyssey.city.layout.model;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public final class CityBlock {
    public final Rectangle footprint; // axis-aligned, local metres
    public DistrictType district = DistrictType.UNKNOWN;

    public CityBlock(Rectangle footprint) {
        this.footprint = footprint;
    }

    public Vector2 centroid() {
        return new Vector2(footprint.x + footprint.width / 2f,
                           footprint.y + footprint.height / 2f);
    }
}
```

`BuildingLot.java`:
```java
package com.galacticodyssey.city.layout.model;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

public final class BuildingLot {
    public final Rectangle footprint; // axis-aligned, local metres
    public final DistrictType district;
    public BuildingFunction function = BuildingFunction.EMPTY_LOT;

    public BuildingLot(Rectangle footprint, DistrictType district) {
        this.footprint = footprint;
        this.district = district;
    }

    public Vector2 centroid() {
        return new Vector2(footprint.x + footprint.width / 2f,
                           footprint.y + footprint.height / 2f);
    }
}
```

`CityGate.java`:
```java
package com.galacticodyssey.city.layout.model;

import com.badlogic.gdx.math.Vector2;

public final class CityGate {
    public final Vector2 position;
    public CityGate(Vector2 position) { this.position = position; }
}
```

`CityWall.java`:
```java
package com.galacticodyssey.city.layout.model;

import com.badlogic.gdx.math.Vector2;
import java.util.ArrayList;
import java.util.List;

public final class CityWall {
    public final List<Vector2> hull;          // ordered hull vertices, local metres
    public final List<CityGate> gates = new ArrayList<>();
    public float heightM = 8f;
    public float thicknessM = 2f;

    public CityWall(List<Vector2> hull) { this.hull = hull; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.model.ModelPojoTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/city/layout/model/
git add core/src/test/java/com/galacticodyssey/city/layout/model/ModelPojoTest.java
git commit -m "feat(city): add city layout model POJOs"
```

---

## Task 4: TerrainSampler interface + flat default

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/city/layout/TerrainSampler.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/FlatTerrainSampler.java`
- Test: `core/src/test/java/com/galacticodyssey/city/layout/FlatTerrainSamplerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.city.layout;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlatTerrainSamplerTest {
    @Test
    void flatSamplerIsAlwaysBuildable() {
        TerrainSampler t = new FlatTerrainSampler();
        assertEquals(0f, t.heightAt(123f, -456f));
        assertFalse(t.isWater(123f, -456f));
        assertEquals(0f, t.slopeAt(123f, -456f));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.FlatTerrainSamplerTest"`
Expected: FAIL — classes missing.

- [ ] **Step 3: Implement**

`TerrainSampler.java`:
```java
package com.galacticodyssey.city.layout;

/** Local-planar terrain queries injected into city generation. Real planet hookup
 *  is sub-project E; A ships {@link FlatTerrainSampler} for tests and standalone use. */
public interface TerrainSampler {
    float heightAt(float localX, float localZ);   // metres
    boolean isWater(float localX, float localZ);
    float slopeAt(float localX, float localZ);     // 0..1 (tan of slope angle)
}
```

`FlatTerrainSampler.java`:
```java
package com.galacticodyssey.city.layout;

public final class FlatTerrainSampler implements TerrainSampler {
    @Override public float heightAt(float x, float z) { return 0f; }
    @Override public boolean isWater(float x, float z) { return false; }
    @Override public float slopeAt(float x, float z) { return 0f; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.FlatTerrainSamplerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/city/layout/TerrainSampler.java core/src/main/java/com/galacticodyssey/city/layout/FlatTerrainSampler.java core/src/test/java/com/galacticodyssey/city/layout/FlatTerrainSamplerTest.java
git commit -m "feat(city): add TerrainSampler interface + flat default"
```

---

## Task 5: Data definitions + CityDataRegistry + JSON content

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/city/data/FunctionWeight.java`
- Create: `core/src/main/java/com/galacticodyssey/city/data/SizeTierDef.java`
- Create: `core/src/main/java/com/galacticodyssey/city/data/DistrictMixDef.java`
- Create: `core/src/main/java/com/galacticodyssey/city/data/CityDataRegistry.java`
- Create: `core/src/main/resources/data/cities/size_tiers.json`
- Create: `core/src/main/resources/data/cities/district_mix.json`
- Create: `core/src/main/resources/data/cities/faction_form_bias.json`
- Test: `core/src/test/java/com/galacticodyssey/city/data/CityDataRegistryTest.java`

- [ ] **Step 1: Create the JSON content files**

`core/src/main/resources/data/cities/size_tiers.json`:
```json
[
  {"type":"OUTPOST","minPopulation":0,"maxPopulation":49,"radiusMin":30,"radiusMax":50,"wall":"no","density":0.15,"formBias":["LINEAR","SPRAWL"]},
  {"type":"FRONTIER_TOWN","minPopulation":50,"maxPopulation":799,"radiusMin":90,"radiusMax":150,"wall":"no","density":0.3,"formBias":["GRID","GRID","SPRAWL"]},
  {"type":"COLONY","minPopulation":800,"maxPopulation":7999,"radiusMin":240,"radiusMax":360,"wall":"maybe","density":0.45,"formBias":["GRID","ORGANIC"]},
  {"type":"CITY","minPopulation":8000,"maxPopulation":59999,"radiusMin":600,"radiusMax":800,"wall":"yes","density":0.6,"formBias":["RADIAL","GRID"]},
  {"type":"LARGE_CITY","minPopulation":60000,"maxPopulation":99999,"radiusMin":1000,"radiusMax":1200,"wall":"yes","density":0.7,"formBias":["RADIAL"]},
  {"type":"METROPOLIS","minPopulation":100000,"maxPopulation":499999,"radiusMin":1600,"radiusMax":2000,"wall":"yes","density":0.8,"formBias":["RADIAL","SPRAWL"]},
  {"type":"LARGE_METROPOLIS","minPopulation":500000,"maxPopulation":2147483647,"radiusMin":2800,"radiusMax":3600,"wall":"yes","density":0.9,"formBias":["SPRAWL","RADIAL"]}
]
```

`core/src/main/resources/data/cities/district_mix.json`:
```json
{
  "GOVERNMENT":  {"minLot":400,"maxLot":2000,"functions":[{"function":"FACTION_HQ","weight":1},{"function":"COURTHOUSE","weight":2},{"function":"TOWN_HALL","weight":2},{"function":"SHOP","weight":1}]},
  "COMMERCIAL":  {"minLot":80,"maxLot":250,"functions":[{"function":"SHOP","weight":4},{"function":"CANTINA","weight":2},{"function":"MARKET_STALL","weight":2},{"function":"APARTMENT","weight":1}]},
  "RESIDENTIAL": {"minLot":60,"maxLot":250,"functions":[{"function":"HOUSE","weight":5},{"function":"APARTMENT","weight":3},{"function":"SHOP","weight":1}]},
  "INDUSTRIAL":  {"minLot":200,"maxLot":800,"functions":[{"function":"WAREHOUSE","weight":3},{"function":"WORKSHOP","weight":3},{"function":"FACTORY","weight":2}]},
  "SLUMS":       {"minLot":20,"maxLot":60,"functions":[{"function":"TENEMENT","weight":5},{"function":"HOUSE","weight":2},{"function":"CANTINA","weight":1}]},
  "SPACEPORT":   {"minLot":300,"maxLot":1500,"functions":[{"function":"HANGAR","weight":3},{"function":"TERMINAL","weight":2},{"function":"WAREHOUSE","weight":2}]},
  "RELIGIOUS":   {"minLot":150,"maxLot":800,"functions":[{"function":"TEMPLE","weight":2},{"function":"SHRINE","weight":3},{"function":"HOUSE","weight":1}]},
  "GARDEN":      {"minLot":200,"maxLot":600,"functions":[{"function":"PARK","weight":4},{"function":"SHRINE","weight":1}]},
  "MILITARY":    {"minLot":200,"maxLot":800,"functions":[{"function":"BARRACKS","weight":4},{"function":"WAREHOUSE","weight":1},{"function":"CLINIC","weight":1}]},
  "UNKNOWN":     {"minLot":60,"maxLot":250,"functions":[{"function":"EMPTY_LOT","weight":1}]}
}
```

`core/src/main/resources/data/cities/faction_form_bias.json`:
```json
{
  "CORPORATE":        ["GRID","GRID","RADIAL"],
  "MILITARIST":       ["GRID","RADIAL"],
  "ISOLATIONIST":     ["ORGANIC","SPRAWL"],
  "FEDERATION":       ["RADIAL","GRID"],
  "PIRATE_SYNDICATE": ["SPRAWL","ORGANIC"]
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.galacticodyssey.city.data;

import com.galacticodyssey.city.layout.model.BuildingFunction;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.city.layout.model.DistrictType;
import com.galacticodyssey.galaxy.faction.FactionEthos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CityDataRegistryTest {
    private CityDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CityDataRegistry();
        registry.loadFromClasspath(); // reads core resources off the test classpath, no Gdx
    }

    @Test
    void allSevenTiersLoaded() {
        assertEquals(7, registry.sizeTiers().size());
    }

    @Test
    void tierLookupByPopulationPicksCorrectTier() {
        assertEquals("OUTPOST", registry.tierForPopulation(10).type);
        assertEquals("CITY", registry.tierForPopulation(30000).type);
        assertEquals("LARGE_METROPOLIS", registry.tierForPopulation(2_000_000).type);
    }

    @Test
    void districtMixHasFunctionsAndLotSizes() {
        DistrictMixDef commercial = registry.districtMix(DistrictType.COMMERCIAL);
        assertNotNull(commercial);
        assertTrue(commercial.minLot < commercial.maxLot);
        assertTrue(commercial.functions.stream()
                .anyMatch(fw -> fw.function == BuildingFunction.SHOP));
    }

    @Test
    void factionFormBiasReturnsForms() {
        List<CityForm> forms = registry.factionFormBias(FactionEthos.ISOLATIONIST);
        assertTrue(forms.contains(CityForm.ORGANIC));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.data.CityDataRegistryTest"`
Expected: FAIL — classes missing.

- [ ] **Step 4: Implement the definition POJOs**

`FunctionWeight.java`:
```java
package com.galacticodyssey.city.data;

import com.galacticodyssey.city.layout.model.BuildingFunction;

/** One weighted building function within a district mix. Public fields for libGDX Json. */
public class FunctionWeight {
    public BuildingFunction function;
    public int weight = 1;
}
```

`SizeTierDef.java`:
```java
package com.galacticodyssey.city.data;

import com.badlogic.gdx.utils.Array;

/** One population tier row from size_tiers.json. Public fields for libGDX Json. */
public class SizeTierDef {
    public String type;            // CityType name
    public int minPopulation;
    public int maxPopulation;      // inclusive
    public float radiusMin;
    public float radiusMax;
    public String wall;            // "yes" | "no" | "maybe"
    public float density;          // 0..1
    public Array<String> formBias = new Array<>(); // CityForm names, repeats = weight
}
```

`DistrictMixDef.java`:
```java
package com.galacticodyssey.city.data;

import java.util.ArrayList;
import java.util.List;

public class DistrictMixDef {
    public float minLot;
    public float maxLot;
    public List<FunctionWeight> functions = new ArrayList<>();
}
```

- [ ] **Step 5: Implement CityDataRegistry**

`CityDataRegistry.java`:
```java
package com.galacticodyssey.city.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.city.layout.model.DistrictType;
import com.galacticodyssey.galaxy.faction.FactionEthos;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Loads and serves city layout content (size tiers, district mix, faction form bias). */
public class CityDataRegistry {

    private final List<SizeTierDef> sizeTiers = new ArrayList<>();
    private final Map<DistrictType, DistrictMixDef> districtMix = new EnumMap<>(DistrictType.class);
    private final Map<FactionEthos, List<CityForm>> factionFormBias = new EnumMap<>(FactionEthos.class);

    /** Runtime load via the libGDX files backend. */
    public void loadFromFiles() {
        loadSizeTiers(Gdx.files.internal("data/cities/size_tiers.json").reader());
        loadDistrictMix(Gdx.files.internal("data/cities/district_mix.json").reader());
        loadFactionFormBias(Gdx.files.internal("data/cities/faction_form_bias.json").reader());
    }

    /** Test/standalone load straight off the JVM classpath — no Gdx backend required. */
    public void loadFromClasspath() {
        loadSizeTiers(classpathReader("data/cities/size_tiers.json"));
        loadDistrictMix(classpathReader("data/cities/district_mix.json"));
        loadFactionFormBias(classpathReader("data/cities/faction_form_bias.json"));
    }

    private Reader classpathReader(String path) {
        InputStream in = getClass().getClassLoader().getResourceAsStream(path);
        if (in == null) throw new IllegalStateException("Missing classpath resource: " + path);
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    void loadSizeTiers(Reader reader) {
        Json json = new Json();
        JsonValue root = new JsonReader().parse(reader);
        sizeTiers.clear();
        for (JsonValue e = root.child; e != null; e = e.next) {
            sizeTiers.add(json.readValue(SizeTierDef.class, e));
        }
    }

    void loadDistrictMix(Reader reader) {
        Json json = new Json();
        JsonValue root = new JsonReader().parse(reader);
        districtMix.clear();
        for (JsonValue e = root.child; e != null; e = e.next) {
            DistrictType type = DistrictType.valueOf(e.name);
            districtMix.put(type, json.readValue(DistrictMixDef.class, e));
        }
    }

    void loadFactionFormBias(Reader reader) {
        JsonValue root = new JsonReader().parse(reader);
        factionFormBias.clear();
        for (JsonValue e = root.child; e != null; e = e.next) {
            FactionEthos ethos = FactionEthos.valueOf(e.name);
            List<CityForm> forms = new ArrayList<>();
            for (JsonValue f = e.child; f != null; f = f.next) {
                forms.add(CityForm.valueOf(f.asString()));
            }
            factionFormBias.put(ethos, forms);
        }
    }

    public List<SizeTierDef> sizeTiers() { return sizeTiers; }

    public SizeTierDef tierForPopulation(int population) {
        for (SizeTierDef t : sizeTiers) {
            if (population >= t.minPopulation && population <= t.maxPopulation) return t;
        }
        // Fall back to the last (largest) tier for out-of-range high values.
        return sizeTiers.get(sizeTiers.size() - 1);
    }

    public DistrictMixDef districtMix(DistrictType type) {
        return districtMix.getOrDefault(type, districtMix.get(DistrictType.UNKNOWN));
    }

    public List<CityForm> factionFormBias(FactionEthos ethos) {
        return factionFormBias.getOrDefault(ethos, new ArrayList<>());
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.data.CityDataRegistryTest"`
Expected: PASS.

> If `formBias` deserialization fails, confirm `SizeTierDef.formBias` is a libGDX `Array<String>` (the `Json` reader maps JSON string arrays to `Array`). The test asserts 7 tiers and correct lookups; a green run confirms parsing.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/city/data/ core/src/main/resources/data/cities/ core/src/test/java/com/galacticodyssey/city/data/CityDataRegistryTest.java
git commit -m "feat(city): add city data registry + JSON content (tiers, district mix, form bias)"
```

---

## Task 6: CitySizeProfile (population → spatial params)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/city/layout/CitySizeProfile.java`
- Test: `core/src/test/java/com/galacticodyssey/city/layout/CitySizeProfileTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.CityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CitySizeProfileTest {
    private CityDataRegistry reg;

    @BeforeEach
    void setUp() {
        reg = new CityDataRegistry();
        reg.loadFromClasspath();
    }

    @Test
    void mapsPopulationToTypeAndRadiusBand() {
        CitySizeProfile p = CitySizeProfile.from(reg, 30000, 99L);
        assertEquals(CityType.CITY, p.type);
        assertTrue(p.radiusMetres >= 600f && p.radiusMetres <= 800f);
        assertTrue(p.hasWall);
        assertTrue(p.density > 0f && p.density <= 1f);
    }

    @Test
    void deterministicForSameSeed() {
        CitySizeProfile a = CitySizeProfile.from(reg, 30000, 99L);
        CitySizeProfile b = CitySizeProfile.from(reg, 30000, 99L);
        assertEquals(a.radiusMetres, b.radiusMetres);
        assertEquals(a.hasWall, b.hasWall);
    }

    @Test
    void biggerPopulationNeverShrinksRadius() {
        int[] pops = {10, 400, 4000, 30000, 80000, 250000, 1_000_000};
        float prev = -1f;
        for (int pop : pops) {
            float r = CitySizeProfile.from(reg, pop, 7L).radiusMetres;
            assertTrue(r >= prev, "radius should be monotonic non-decreasing with population");
            prev = r;
        }
    }

    @Test
    void outpostHasNoWall() {
        assertFalse(CitySizeProfile.from(reg, 10, 1L).hasWall);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.CitySizeProfileTest"`
Expected: FAIL — `CitySizeProfile` missing.

- [ ] **Step 3: Implement**

`CitySizeProfile.java`:
```java
package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.data.SizeTierDef;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.city.layout.model.CityType;
import com.galacticodyssey.galaxy.RngUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Spatial parameters derived from population. Population is the single size driver. */
public final class CitySizeProfile {
    public final CityType type;
    public final float radiusMetres;
    public final boolean hasWall;
    public final float density;          // 0..1
    public final List<CityForm> formBias;

    private CitySizeProfile(CityType type, float radiusMetres, boolean hasWall,
                            float density, List<CityForm> formBias) {
        this.type = type;
        this.radiusMetres = radiusMetres;
        this.hasWall = hasWall;
        this.density = density;
        this.formBias = formBias;
    }

    public static CitySizeProfile from(CityDataRegistry reg, int population, long citySeed) {
        SizeTierDef tier = reg.tierForPopulation(population);
        Random rng = new Random(citySeed ^ 0x512E1Z); // distinct salt for size stage
        float radius = RngUtil.range(rng, tier.radiusMin, tier.radiusMax);
        boolean wall = resolveWall(tier.wall, rng);
        List<CityForm> bias = new ArrayList<>();
        for (String f : tier.formBias) bias.add(CityForm.valueOf(f));
        return new CitySizeProfile(CityType.valueOf(tier.type), radius, wall, tier.density, bias);
    }

    private static boolean resolveWall(String wall, Random rng) {
        switch (wall) {
            case "yes":   return true;
            case "no":    return false;
            case "maybe": return rng.nextFloat() < 0.5f;
            default:      return false;
        }
    }
}
```

> NOTE: the salt literal `0x512E1Z` above is intentionally invalid to force you to choose a real constant — replace it with a hex long literal, e.g. `0x512E1ABCL`. (See Step 3b.)

- [ ] **Step 3b: Fix the salt constant**

Replace `citySeed ^ 0x512E1Z` with:
```java
        Random rng = new Random(citySeed ^ 0x512E1A5EL);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.CitySizeProfileTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/city/layout/CitySizeProfile.java core/src/test/java/com/galacticodyssey/city/layout/CitySizeProfileTest.java
git commit -m "feat(city): population-driven CitySizeProfile"
```

---

## Task 7: CityFormSelector

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/city/layout/CityFormSelector.java`
- Test: `core/src/test/java/com/galacticodyssey/city/layout/CityFormSelectorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.galaxy.faction.FactionEthos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CityFormSelectorTest {
    private CityDataRegistry reg;

    @BeforeEach
    void setUp() { reg = new CityDataRegistry(); reg.loadFromClasspath(); }

    @Test
    void deterministicForSameSeed() {
        CitySizeProfile p = CitySizeProfile.from(reg, 30000, 5L);
        CityForm a = CityFormSelector.select(reg, FactionEthos.CORPORATE, p, 5L);
        CityForm b = CityFormSelector.select(reg, FactionEthos.CORPORATE, p, 5L);
        assertEquals(a, b);
    }

    @Test
    void choiceComesFromTierOrFactionBias() {
        CitySizeProfile p = CitySizeProfile.from(reg, 80000, 11L); // LARGE_CITY: tier bias RADIAL
        CityForm form = CityFormSelector.select(reg, FactionEthos.ISOLATIONIST, p, 11L);
        // Candidate pool = tier formBias (RADIAL) + faction bias (ORGANIC, SPRAWL)
        assertTrue(form == CityForm.RADIAL || form == CityForm.ORGANIC || form == CityForm.SPRAWL);
    }

    @Test
    void alwaysReturnsAFormEvenWithEmptyFactionBias() {
        CitySizeProfile p = CitySizeProfile.from(reg, 10, 3L); // OUTPOST: LINEAR/SPRAWL
        CityForm form = CityFormSelector.select(reg, FactionEthos.CORPORATE, p, 3L);
        assertNotNull(form);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.CityFormSelectorTest"`
Expected: FAIL — class missing.

- [ ] **Step 3: Implement**

`CityFormSelector.java`:
```java
package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.galaxy.faction.FactionEthos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Picks a CityForm from the combined tier + faction bias pools (each entry = one vote). */
public final class CityFormSelector {
    private CityFormSelector() {}

    public static CityForm select(CityDataRegistry reg, FactionEthos ethos,
                                  CitySizeProfile profile, long citySeed) {
        List<CityForm> pool = new ArrayList<>(profile.formBias);
        pool.addAll(reg.factionFormBias(ethos));
        if (pool.isEmpty()) return CityForm.GRID; // safe default
        Random rng = new Random(citySeed ^ 0xF02MBIA5L); // replaced in Step 3b
        return pool.get(rng.nextInt(pool.size()));
    }
}
```

- [ ] **Step 3b: Fix the salt constant**

Replace `0xF02MBIA5L` (invalid) with a valid hex long:
```java
        Random rng = new Random(citySeed ^ 0xF0125B1A5L);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.CityFormSelectorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/city/layout/CityFormSelector.java core/src/test/java/com/galacticodyssey/city/layout/CityFormSelectorTest.java
git commit -m "feat(city): CityFormSelector (tier + faction bias)"
```

---

## Task 8: AuthoredLandmark + LandmarkPlacer

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/city/layout/AuthoredLandmark.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/LandmarkPlacer.java`
- Test: `core/src/test/java/com/galacticodyssey/city/layout/LandmarkPlacerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.Landmark;
import com.galacticodyssey.city.layout.model.LandmarkType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LandmarkPlacerTest {

    @Test
    void placesCivicCentreNearOrigin() {
        List<Landmark> lm = LandmarkPlacer.place(700f, true, new ArrayList<>(), 1L);
        Landmark civic = find(lm, LandmarkType.CIVIC_CENTRE);
        assertNotNull(civic);
        assertTrue(civic.position.len() < 0.1f * 700f);
    }

    @Test
    void spaceportSitsInOuterBand() {
        List<Landmark> lm = LandmarkPlacer.place(700f, true, new ArrayList<>(), 2L);
        Landmark sp = find(lm, LandmarkType.SPACEPORT);
        assertNotNull(sp);
        float d = sp.position.len();
        assertTrue(d >= 0.65f * 700f && d <= 0.85f * 700f, "spaceport at 65-85% radius, was " + d);
    }

    @Test
    void marketIsNotAtCentre() {
        List<Landmark> lm = LandmarkPlacer.place(700f, true, new ArrayList<>(), 3L);
        Landmark mk = find(lm, LandmarkType.MARKET_PLAZA);
        assertNotNull(mk);
        assertTrue(mk.position.len() > 0.15f * 700f);
    }

    @Test
    void authoredLandmarksArePreservedAndFlagged() {
        List<AuthoredLandmark> authored = new ArrayList<>();
        authored.add(new AuthoredLandmark(LandmarkType.FACTION_LANDMARK, new Vector2(123f, -45f)));
        List<Landmark> lm = LandmarkPlacer.place(700f, true, authored, 4L);
        Landmark a = lm.stream().filter(l -> l.authored).findFirst().orElse(null);
        assertNotNull(a);
        assertEquals(123f, a.position.x, 0.0001f);
        assertEquals(-45f, a.position.y, 0.0001f);
    }

    @Test
    void deterministic() {
        assertEquals(LandmarkPlacer.place(700f, true, new ArrayList<>(), 9L).size(),
                     LandmarkPlacer.place(700f, true, new ArrayList<>(), 9L).size());
    }

    private Landmark find(List<Landmark> lm, LandmarkType t) {
        return lm.stream().filter(l -> l.type == t).findFirst().orElse(null);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.LandmarkPlacerTest"`
Expected: FAIL — classes missing.

- [ ] **Step 3: Implement AuthoredLandmark**

`AuthoredLandmark.java`:
```java
package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.LandmarkType;

/** A hand-placed landmark supplied by the caller (hybrid procedural + handcrafted). */
public final class AuthoredLandmark {
    public final LandmarkType type;
    public final Vector2 position; // local metres

    public AuthoredLandmark(LandmarkType type, Vector2 position) {
        this.type = type;
        this.position = position;
    }
}
```

- [ ] **Step 4: Implement LandmarkPlacer**

`LandmarkPlacer.java`:
```java
package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.Landmark;
import com.galacticodyssey.city.layout.model.LandmarkType;
import com.galacticodyssey.galaxy.RngUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Places civic centre, spaceport, market plaza, faction landmark; merges authored ones first. */
public final class LandmarkPlacer {
    private LandmarkPlacer() {}

    public static List<Landmark> place(float radius, boolean hasSpaceport,
                                       List<AuthoredLandmark> authored, long citySeed) {
        Random rng = new Random(citySeed ^ 0x1A0D3A2BL);
        List<Landmark> out = new ArrayList<>();

        // 1. Authored landmarks first (preserved verbatim, flagged authored).
        for (AuthoredLandmark a : authored) {
            out.add(new Landmark(a.type, a.position.cpy(), true));
        }

        // 2. Civic centre near origin (unless one was authored).
        if (!hasType(out, LandmarkType.CIVIC_CENTRE)) {
            Vector2 jitter = polar(rng.nextFloat() * MathUtils.PI2,
                                   RngUtil.range(rng, 0f, 0.05f * radius));
            out.add(new Landmark(LandmarkType.CIVIC_CENTRE, jitter, false));
        }

        // 3. Spaceport in outer band.
        Vector2 spaceportPos = null;
        if (hasSpaceport && !hasType(out, LandmarkType.SPACEPORT)) {
            float angle = rng.nextFloat() * MathUtils.PI2;
            float dist = RngUtil.range(rng, 0.65f * radius, 0.85f * radius);
            spaceportPos = polar(angle, dist);
            out.add(new Landmark(LandmarkType.SPACEPORT, spaceportPos, false));
        }

        // 4. Market plaza between centre and spaceport (or its own outer-ish spot).
        if (!hasType(out, LandmarkType.MARKET_PLAZA)) {
            Vector2 centre = positionOf(out, LandmarkType.CIVIC_CENTRE, Vector2.Zero);
            Vector2 toward = spaceportPos != null ? spaceportPos
                    : polar(rng.nextFloat() * MathUtils.PI2, 0.5f * radius);
            float t = RngUtil.range(rng, 0.3f, 0.5f);
            Vector2 market = centre.cpy().lerp(toward, t)
                    .add(polar(rng.nextFloat() * MathUtils.PI2, 0.08f * radius));
            // Guarantee it isn't inside the central plaza void.
            if (market.len() < 0.16f * radius) market.setLength(0.2f * radius);
            out.add(new Landmark(LandmarkType.MARKET_PLAZA, market, false));
        }

        // 5. Faction landmark at mid radius (unless authored).
        if (!hasType(out, LandmarkType.FACTION_LANDMARK)) {
            Vector2 fl = polar(rng.nextFloat() * MathUtils.PI2, RngUtil.range(rng, 0.3f * radius, 0.55f * radius));
            out.add(new Landmark(LandmarkType.FACTION_LANDMARK, fl, false));
        }

        return out;
    }

    private static boolean hasType(List<Landmark> lm, LandmarkType t) {
        for (Landmark l : lm) if (l.type == t) return true;
        return false;
    }

    private static Vector2 positionOf(List<Landmark> lm, LandmarkType t, Vector2 fallback) {
        for (Landmark l : lm) if (l.type == t) return l.position.cpy();
        return fallback.cpy();
    }

    private static Vector2 polar(float angle, float dist) {
        return new Vector2(MathUtils.cos(angle) * dist, MathUtils.sin(angle) * dist);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.LandmarkPlacerTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/city/layout/AuthoredLandmark.java core/src/main/java/com/galacticodyssey/city/layout/LandmarkPlacer.java core/src/test/java/com/galacticodyssey/city/layout/LandmarkPlacerTest.java
git commit -m "feat(city): LandmarkPlacer with authored-landmark merge"
```

---

## Task 9: StreetNetwork + StreetNetworkBuilder (grid + form modulation)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/city/layout/StreetNetwork.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/StreetNetworkBuilder.java`
- Test: `core/src/test/java/com/galacticodyssey/city/layout/StreetNetworkBuilderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.city.layout.model.Street;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreetNetworkBuilderTest {

    @Test
    void producesBlocksAndStreetsWithinRadius() {
        StreetNetwork n = StreetNetworkBuilder.build(CityForm.GRID, 300f, 0.5f, 1L);
        assertFalse(n.blocks.isEmpty());
        assertFalse(n.streets.isEmpty());
        for (CityBlock b : n.blocks) {
            assertTrue(b.centroid().len() <= 300f + 1f, "block centroid within radius");
        }
    }

    @Test
    void deterministicBlockCount() {
        int a = StreetNetworkBuilder.build(CityForm.ORGANIC, 300f, 0.5f, 2L).blocks.size();
        int b = StreetNetworkBuilder.build(CityForm.ORGANIC, 300f, 0.5f, 2L).blocks.size();
        assertEquals(a, b);
    }

    @Test
    void biggerRadiusYieldsMoreBlocks() {
        int small = StreetNetworkBuilder.build(CityForm.GRID, 150f, 0.5f, 3L).blocks.size();
        int big = StreetNetworkBuilder.build(CityForm.GRID, 600f, 0.5f, 3L).blocks.size();
        assertTrue(big > small);
    }

    @Test
    void higherDensityYieldsMoreBlocksAtSameRadius() {
        int sparse = StreetNetworkBuilder.build(CityForm.GRID, 400f, 0.2f, 4L).blocks.size();
        int dense = StreetNetworkBuilder.build(CityForm.GRID, 400f, 0.9f, 4L).blocks.size();
        assertTrue(dense > sparse, "denser city packs more (smaller) blocks");
    }

    @Test
    void linearFormIsElongated() {
        StreetNetwork n = StreetNetworkBuilder.build(CityForm.LINEAR, 400f, 0.5f, 5L);
        float maxX = 0f, maxY = 0f;
        for (CityBlock b : n.blocks) {
            maxX = Math.max(maxX, Math.abs(b.centroid().x));
            maxY = Math.max(maxY, Math.abs(b.centroid().y));
        }
        assertTrue(maxX > maxY * 1.5f, "LINEAR cities extend further along X than Y");
    }

    @Test
    void blocksDoNotOverlap() {
        StreetNetwork n = StreetNetworkBuilder.build(CityForm.GRID, 300f, 0.6f, 6L);
        for (int i = 0; i < n.blocks.size(); i++) {
            for (int j = i + 1; j < n.blocks.size(); j++) {
                assertFalse(n.blocks.get(i).footprint.overlaps(n.blocks.get(j).footprint),
                        "blocks must not overlap");
            }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.StreetNetworkBuilderTest"`
Expected: FAIL — classes missing.

- [ ] **Step 3: Implement StreetNetwork**

`StreetNetwork.java`:
```java
package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.Street;

import java.util.ArrayList;
import java.util.List;

/** Output of the street builder: the road segments plus the rectangular blocks they bound. */
public final class StreetNetwork {
    public final List<Street> streets = new ArrayList<>();
    public final List<CityBlock> blocks = new ArrayList<>();
}
```

- [ ] **Step 4: Implement StreetNetworkBuilder**

`StreetNetworkBuilder.java`:
```java
package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.CityForm;
import com.galacticodyssey.city.layout.model.Street;
import com.galacticodyssey.city.layout.model.StreetTier;

import java.util.Random;

/**
 * Builds an axis-aligned cell grid of blocks (within a disk of {@code radius}) plus the
 * streets between them. {@link CityForm} modulates the grid (see spec v1 simplification).
 */
public final class StreetNetworkBuilder {
    private static final float MAX_BLOCK = 60f;
    private static final float MIN_BLOCK = 20f;
    private static final float STREET_WIDTH = 8f;

    private StreetNetworkBuilder() {}

    public static StreetNetwork build(CityForm form, float radius, float density, long citySeed) {
        StreetNetwork net = new StreetNetwork();
        Random rng = new Random(citySeed ^ 0x57BEE7A1L);

        float baseBlock = MathUtils.lerp(MAX_BLOCK, MIN_BLOCK, density);
        float spacing = baseBlock + STREET_WIDTH;
        int half = (int) Math.ceil(radius / spacing) + 1;

        // LINEAR strip half-width (perpendicular to the long X axis).
        float stripHalf = radius * 0.32f;

        int lineIndex = 0;
        for (int i = -half; i <= half; i++) {
            for (int j = -half; j <= half; j++) {
                float cx = i * spacing;
                float cy = j * spacing;
                float dist = (float) Math.sqrt(cx * cx + cy * cy);
                if (dist > radius) continue;

                // RADIAL: leave a central plaza void for the civic landmark.
                if (form == CityForm.RADIAL && dist < radius * 0.1f) continue;
                // LINEAR: clip to an elongated strip along X.
                if (form == CityForm.LINEAR && Math.abs(cy) > stripHalf) continue;
                // SPRAWL: drop a fraction of peripheral cells for an irregular edge.
                if (form == CityForm.SPRAWL && dist > radius * 0.6f && rng.nextFloat() < 0.25f) continue;
                // ORGANIC: drop a small fraction of cells anywhere.
                if (form == CityForm.ORGANIC && rng.nextFloat() < 0.1f) continue;

                // Per-cell block size: RADIAL shrinks toward the centre.
                float blockSize = baseBlock;
                if (form == CityForm.RADIAL) {
                    float t = MathUtils.clamp(dist / radius, 0f, 1f);
                    blockSize = MathUtils.lerp(MIN_BLOCK, baseBlock, t);
                }

                Rectangle rect = new Rectangle(cx - blockSize / 2f, cy - blockSize / 2f,
                                               blockSize, blockSize);

                // ORGANIC: jitter the footprint slightly (kept inside the cell gap).
                if (form == CityForm.ORGANIC) {
                    float jx = (rng.nextFloat() - 0.5f) * STREET_WIDTH * 0.5f;
                    float jy = (rng.nextFloat() - 0.5f) * STREET_WIDTH * 0.5f;
                    rect.x += jx;
                    rect.y += jy;
                }
                net.blocks.add(new CityBlock(rect));
            }
        }

        // Streets: grid lines spanning the disk; every 3rd line is an AVENUE.
        for (int i = -half; i <= half; i++) {
            float p = i * spacing - spacing / 2f; // street runs in the gap before cell i
            float ext = chordHalfLength(radius, p);
            if (ext <= 0f) continue;
            StreetTier tier = (i % 3 == 0) ? StreetTier.AVENUE : StreetTier.STREET;
            // Vertical street (constant X)
            net.streets.add(new Street(new Vector2(p, -ext), new Vector2(p, ext), tier));
            // Horizontal street (constant Y)
            net.streets.add(new Street(new Vector2(-ext, p), new Vector2(ext, p), tier));
        }

        return net;
    }

    /** Half-length of the chord of a circle of radius r at perpendicular offset |p|. */
    private static float chordHalfLength(float r, float p) {
        float d = r * r - p * p;
        return d <= 0f ? 0f : (float) Math.sqrt(d);
    }
}
```

> The `higherDensityYieldsMoreBlocks` test relies on smaller blocks (higher density) ⇒ tighter spacing ⇒ more cells in the disk — which the `baseBlock` lerp guarantees. The `linearFormIsElongated` test relies on the strip clip. If `blocksDoNotOverlap` fails for ORGANIC, reduce the jitter magnitude (it must stay below `STREET_WIDTH/2`).

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.StreetNetworkBuilderTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/city/layout/StreetNetwork.java core/src/main/java/com/galacticodyssey/city/layout/StreetNetworkBuilder.java core/src/test/java/com/galacticodyssey/city/layout/StreetNetworkBuilderTest.java
git commit -m "feat(city): grid-based StreetNetworkBuilder with form modulation"
```

---

## Task 10: DistrictZoner

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/city/layout/DistrictZoner.java`
- Test: `core/src/test/java/com/galacticodyssey/city/layout/DistrictZonerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DistrictZonerTest {

    private List<CityBlock> ringOfBlocks(int n, float radius) {
        List<CityBlock> blocks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float a = (float) (i * 2 * Math.PI / n);
            float x = (float) Math.cos(a) * radius, y = (float) Math.sin(a) * radius;
            blocks.add(new CityBlock(new Rectangle(x - 10, y - 10, 20, 20)));
        }
        return blocks;
    }

    @Test
    void everyBlockGetsANonNullDistrict() {
        List<CityBlock> blocks = ringOfBlocks(12, 200f);
        blocks.add(new CityBlock(new Rectangle(-10, -10, 20, 20))); // centre
        DistrictZoner.zone(blocks, new ArrayList<>(), 700f, 1L);
        for (CityBlock b : blocks) assertNotNull(b.district);
    }

    @Test
    void centreBlockIsGovernment() {
        List<CityBlock> blocks = new ArrayList<>();
        CityBlock centre = new CityBlock(new Rectangle(-10, -10, 20, 20));
        blocks.add(centre);
        DistrictZoner.zone(blocks, new ArrayList<>(), 700f, 2L);
        assertEquals(DistrictType.GOVERNMENT, centre.district);
    }

    @Test
    void blockAdjacentToSpaceportLandmarkBecomesSpaceport() {
        CityBlock near = new CityBlock(new Rectangle(495, -5, 20, 20)); // centroid ~ (505,5)
        List<CityBlock> blocks = new ArrayList<>();
        blocks.add(near);
        List<Landmark> lm = new ArrayList<>();
        lm.add(new Landmark(LandmarkType.SPACEPORT, new Vector2(505, 5), false));
        DistrictZoner.zone(blocks, lm, 700f, 3L);
        assertEquals(DistrictType.SPACEPORT, near.district);
    }

    @Test
    void deterministic() {
        List<CityBlock> a = ringOfBlocks(20, 300f);
        List<CityBlock> b = ringOfBlocks(20, 300f);
        DistrictZoner.zone(a, new ArrayList<>(), 700f, 7L);
        DistrictZoner.zone(b, new ArrayList<>(), 700f, 7L);
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).district, b.get(i).district);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.DistrictZonerTest"`
Expected: FAIL — class missing.

- [ ] **Step 3: Implement**

`DistrictZoner.java`:
```java
package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.DistrictType;
import com.galacticodyssey.city.layout.model.Landmark;
import com.galacticodyssey.city.layout.model.LandmarkType;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.List;
import java.util.Random;

/** Assigns a DistrictType to each block via centre-out gradient + landmark adjacency. */
public final class DistrictZoner {
    private static final float LANDMARK_ADJ_RADIUS = 30f;

    private DistrictZoner() {}

    public static void zone(List<CityBlock> blocks, List<Landmark> landmarks,
                            float radius, long citySeed) {
        Random rng = new Random(SeedDeriver.forId(citySeed, 0xD15741C7L));
        for (CityBlock block : blocks) {
            Vector2 c = block.centroid();

            Landmark adj = nearestLandmark(c, landmarks);
            if (adj != null) {
                block.district = landmarkZone(adj.type);
                continue;
            }
            float t = MathUtils.clamp(c.len() / radius, 0f, 1f);
            block.district = rollByDepth(t, rng);
        }
    }

    private static Landmark nearestLandmark(Vector2 c, List<Landmark> landmarks) {
        for (Landmark l : landmarks) {
            if (c.dst(l.position) <= LANDMARK_ADJ_RADIUS) return l;
        }
        return null;
    }

    private static DistrictType landmarkZone(LandmarkType t) {
        switch (t) {
            case SPACEPORT:        return DistrictType.SPACEPORT;
            case CIVIC_CENTRE:     return DistrictType.GOVERNMENT;
            case MARKET_PLAZA:     return DistrictType.COMMERCIAL;
            case FACTION_LANDMARK: return DistrictType.GOVERNMENT;
            default:               return DistrictType.GOVERNMENT;
        }
    }

    private static DistrictType rollByDepth(float t, Random rng) {
        if (t < 0.15f) return DistrictType.GOVERNMENT;
        if (t < 0.30f) {
            float r = rng.nextFloat();
            if (r < 0.40f) return DistrictType.COMMERCIAL;
            if (r < 0.70f) return DistrictType.RESIDENTIAL;
            return DistrictType.RELIGIOUS;
        }
        if (t < 0.55f) {
            float r = rng.nextFloat();
            if (r < 0.35f) return DistrictType.RESIDENTIAL;
            if (r < 0.60f) return DistrictType.COMMERCIAL;
            if (r < 0.75f) return DistrictType.INDUSTRIAL;
            return DistrictType.GARDEN;
        }
        if (t < 0.80f) {
            float r = rng.nextFloat();
            if (r < 0.40f) return DistrictType.INDUSTRIAL;
            if (r < 0.65f) return DistrictType.RESIDENTIAL;
            if (r < 0.80f) return DistrictType.SLUMS;
            return DistrictType.MILITARY;
        }
        float r = rng.nextFloat();
        if (r < 0.45f) return DistrictType.SLUMS;
        if (r < 0.70f) return DistrictType.INDUSTRIAL;
        return DistrictType.MILITARY;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.DistrictZonerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/city/layout/DistrictZoner.java core/src/test/java/com/galacticodyssey/city/layout/DistrictZonerTest.java
git commit -m "feat(city): DistrictZoner (gradient + landmark adjacency)"
```

---

## Task 11: LotSubdivider

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/city/layout/LotSubdivider.java`
- Test: `core/src/test/java/com/galacticodyssey/city/layout/LotSubdividerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Rectangle;
import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.BuildingLot;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.DistrictType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LotSubdividerTest {
    private CityDataRegistry reg;

    @BeforeEach
    void setUp() { reg = new CityDataRegistry(); reg.loadFromClasspath(); }

    @Test
    void largeCommercialBlockSplitsIntoMultipleLots() {
        CityBlock block = new CityBlock(new Rectangle(0, 0, 60, 60)); // 3600 m^2
        block.district = DistrictType.COMMERCIAL;                     // maxLot 250
        List<CityBlock> blocks = new ArrayList<>();
        blocks.add(block);
        List<BuildingLot> lots = LotSubdivider.subdivide(blocks, reg, 1L);
        assertTrue(lots.size() > 1, "block much bigger than maxLot should split");
        for (BuildingLot lot : lots) assertEquals(DistrictType.COMMERCIAL, lot.district);
    }

    @Test
    void lotsStayWithinTheirBlock() {
        CityBlock block = new CityBlock(new Rectangle(10, 20, 80, 40));
        block.district = DistrictType.RESIDENTIAL;
        List<CityBlock> blocks = new ArrayList<>();
        blocks.add(block);
        for (BuildingLot lot : LotSubdivider.subdivide(blocks, reg, 2L)) {
            assertTrue(lot.footprint.x >= 10 - 0.001f);
            assertTrue(lot.footprint.y >= 20 - 0.001f);
            assertTrue(lot.footprint.x + lot.footprint.width <= 90 + 0.001f);
            assertTrue(lot.footprint.y + lot.footprint.height <= 60 + 0.001f);
        }
    }

    @Test
    void deterministic() {
        CityBlock b1 = new CityBlock(new Rectangle(0, 0, 100, 100));
        b1.district = DistrictType.INDUSTRIAL;
        CityBlock b2 = new CityBlock(new Rectangle(0, 0, 100, 100));
        b2.district = DistrictType.INDUSTRIAL;
        List<CityBlock> l1 = new ArrayList<>(); l1.add(b1);
        List<CityBlock> l2 = new ArrayList<>(); l2.add(b2);
        assertEquals(LotSubdivider.subdivide(l1, reg, 5L).size(),
                     LotSubdivider.subdivide(l2, reg, 5L).size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.LotSubdividerTest"`
Expected: FAIL — class missing.

- [ ] **Step 3: Implement**

`LotSubdivider.java`:
```java
package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.data.DistrictMixDef;
import com.galacticodyssey.city.layout.model.BuildingLot;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/** Splits each block into building lots by iterative binary subdivision along the long axis. */
public final class LotSubdivider {
    private LotSubdivider() {}

    public static List<BuildingLot> subdivide(List<CityBlock> blocks, CityDataRegistry reg, long citySeed) {
        List<BuildingLot> lots = new ArrayList<>();
        long domain = SeedDeriver.forId(citySeed, 0x107D1V1DL); // replaced in Step 3b
        long blockIndex = 0;
        for (CityBlock block : blocks) {
            Random rng = new Random(SeedDeriver.forId(domain, blockIndex++));
            DistrictMixDef mix = reg.districtMix(block.district);
            splitBlock(block, mix, rng, lots);
        }
        return lots;
    }

    private static void splitBlock(CityBlock block, DistrictMixDef mix, Random rng, List<BuildingLot> out) {
        Deque<Rectangle> queue = new ArrayDeque<>();
        queue.add(new Rectangle(block.footprint));
        while (!queue.isEmpty()) {
            Rectangle cell = queue.removeFirst();
            float area = cell.width * cell.height;
            if (area <= mix.minLot || rng.nextFloat() < stopProbability(area, mix.maxLot)) {
                out.add(new BuildingLot(cell, block.district));
                continue;
            }
            boolean splitW = cell.width >= cell.height;
            float f = RngUtil.range(rng, 0.4f, 0.6f);
            if (splitW) {
                float w1 = cell.width * f;
                queue.add(new Rectangle(cell.x, cell.y, w1, cell.height));
                queue.add(new Rectangle(cell.x + w1, cell.y, cell.width - w1, cell.height));
            } else {
                float h1 = cell.height * f;
                queue.add(new Rectangle(cell.x, cell.y, cell.width, h1));
                queue.add(new Rectangle(cell.x, cell.y + h1, cell.width, cell.height - h1));
            }
        }
    }

    private static float stopProbability(float area, float maxLot) {
        return MathUtils.clamp(1f - (area / maxLot), 0f, 0.85f);
    }
}
```

- [ ] **Step 3b: Fix the salt constant**

Replace `0x107D1V1DL` (invalid) with:
```java
        long domain = SeedDeriver.forId(citySeed, 0x107D1D1DL);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.LotSubdividerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/city/layout/LotSubdivider.java core/src/test/java/com/galacticodyssey/city/layout/LotSubdividerTest.java
git commit -m "feat(city): LotSubdivider (zone-aware binary split)"
```

---

## Task 12: LotFunctionAssigner

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/city/layout/LotFunctionAssigner.java`
- Test: `core/src/test/java/com/galacticodyssey/city/layout/LotFunctionAssignerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LotFunctionAssignerTest {
    private CityDataRegistry reg;

    @BeforeEach
    void setUp() { reg = new CityDataRegistry(); reg.loadFromClasspath(); }

    @Test
    void everyLotGetsAFunctionFromItsDistrictMix() {
        List<BuildingLot> lots = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            lots.add(new BuildingLot(new Rectangle(i * 30, 0, 20, 20), DistrictType.RESIDENTIAL));
        }
        LotFunctionAssigner.assign(lots, new ArrayList<>(), reg, 1L);
        for (BuildingLot lot : lots) {
            assertNotEquals(BuildingFunction.EMPTY_LOT, lot.function);
            // RESIDENTIAL mix is HOUSE/APARTMENT/SHOP only
            assertTrue(lot.function == BuildingFunction.HOUSE
                    || lot.function == BuildingFunction.APARTMENT
                    || lot.function == BuildingFunction.SHOP);
        }
    }

    @Test
    void factionLandmarkLotBecomesFactionHq() {
        BuildingLot lot = new BuildingLot(new Rectangle(95, 95, 20, 20), DistrictType.GOVERNMENT);
        List<BuildingLot> lots = new ArrayList<>();
        lots.add(lot);
        List<Landmark> lm = new ArrayList<>();
        lm.add(new Landmark(LandmarkType.FACTION_LANDMARK, new Vector2(105, 105), false)); // inside lot
        LotFunctionAssigner.assign(lots, lm, reg, 2L);
        assertEquals(BuildingFunction.FACTION_HQ, lot.function);
    }

    @Test
    void deterministic() {
        List<BuildingLot> a = new ArrayList<>();
        List<BuildingLot> b = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            a.add(new BuildingLot(new Rectangle(i * 30, 0, 20, 20), DistrictType.COMMERCIAL));
            b.add(new BuildingLot(new Rectangle(i * 30, 0, 20, 20), DistrictType.COMMERCIAL));
        }
        LotFunctionAssigner.assign(a, new ArrayList<>(), reg, 9L);
        LotFunctionAssigner.assign(b, new ArrayList<>(), reg, 9L);
        for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).function, b.get(i).function);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.LotFunctionAssignerTest"`
Expected: FAIL — class missing.

- [ ] **Step 3: Implement**

`LotFunctionAssigner.java`:
```java
package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.data.DistrictMixDef;
import com.galacticodyssey.city.data.FunctionWeight;
import com.galacticodyssey.city.layout.model.BuildingFunction;
import com.galacticodyssey.city.layout.model.BuildingLot;
import com.galacticodyssey.city.layout.model.Landmark;
import com.galacticodyssey.city.layout.model.LandmarkType;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.List;
import java.util.Random;

/** Tags each lot with a BuildingFunction from its district mix; landmark lots get special functions. */
public final class LotFunctionAssigner {
    private LotFunctionAssigner() {}

    public static void assign(List<BuildingLot> lots, List<Landmark> landmarks,
                              CityDataRegistry reg, long citySeed) {
        long domain = SeedDeriver.forId(citySeed, 0xF0C70F00L);
        long idx = 0;
        for (BuildingLot lot : lots) {
            Random rng = new Random(SeedDeriver.forId(domain, idx++));

            BuildingFunction special = landmarkFunction(lot, landmarks);
            if (special != null) {
                lot.function = special;
                continue;
            }
            DistrictMixDef mix = reg.districtMix(lot.district);
            lot.function = weightedPick(mix, rng);
        }
    }

    private static BuildingFunction landmarkFunction(BuildingLot lot, List<Landmark> landmarks) {
        for (Landmark l : landmarks) {
            if (lot.footprint.contains(l.position.x, l.position.y)) {
                switch (l.type) {
                    case FACTION_LANDMARK: return BuildingFunction.FACTION_HQ;
                    case CIVIC_CENTRE:     return BuildingFunction.TOWN_HALL;
                    case SPACEPORT:        return BuildingFunction.TERMINAL;
                    case MARKET_PLAZA:     return BuildingFunction.MARKET_STALL;
                    default:               return null;
                }
            }
        }
        return null;
    }

    private static BuildingFunction weightedPick(DistrictMixDef mix, Random rng) {
        int total = 0;
        for (FunctionWeight fw : mix.functions) total += Math.max(0, fw.weight);
        if (total <= 0) return BuildingFunction.EMPTY_LOT;
        int roll = rng.nextInt(total);
        for (FunctionWeight fw : mix.functions) {
            roll -= Math.max(0, fw.weight);
            if (roll < 0) return fw.function;
        }
        return mix.functions.get(mix.functions.size() - 1).function;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.LotFunctionAssignerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/city/layout/LotFunctionAssigner.java core/src/test/java/com/galacticodyssey/city/layout/LotFunctionAssignerTest.java
git commit -m "feat(city): LotFunctionAssigner (A/B contract tagging)"
```

---

## Task 13: WallBuilder

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/city/layout/WallBuilder.java`
- Test: `core/src/test/java/com/galacticodyssey/city/layout/WallBuilderTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Rectangle;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.CityWall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WallBuilderTest {

    private List<CityBlock> diskOfBlocks(float radius, float step) {
        List<CityBlock> blocks = new ArrayList<>();
        for (float x = -radius; x <= radius; x += step) {
            for (float y = -radius; y <= radius; y += step) {
                if (Math.sqrt(x * x + y * y) <= radius) {
                    blocks.add(new CityBlock(new Rectangle(x - 5, y - 5, 10, 10)));
                }
            }
        }
        return blocks;
    }

    @Test
    void wallHullEnclosesAllBlockCentroids() {
        List<CityBlock> blocks = diskOfBlocks(200f, 40f);
        CityWall wall = WallBuilder.build(blocks);
        assertNotNull(wall);
        assertTrue(wall.hull.size() >= 3, "hull needs at least 3 vertices");
        // Every block centroid must be inside (or on) the hull's bounding extent.
        float maxHull = 0f;
        for (com.badlogic.gdx.math.Vector2 v : wall.hull) maxHull = Math.max(maxHull, v.len());
        for (CityBlock b : blocks) assertTrue(b.centroid().len() <= maxHull + 0.001f);
    }

    @Test
    void producesAtLeastOneGate() {
        CityWall wall = WallBuilder.build(diskOfBlocks(200f, 40f));
        assertFalse(wall.gates.isEmpty());
    }

    @Test
    void deterministic() {
        CityWall a = WallBuilder.build(diskOfBlocks(200f, 40f));
        CityWall b = WallBuilder.build(diskOfBlocks(200f, 40f));
        assertEquals(a.hull.size(), b.hull.size());
        assertEquals(a.gates.size(), b.gates.size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.WallBuilderTest"`
Expected: FAIL — class missing.

- [ ] **Step 3: Implement**

`WallBuilder.java`:
```java
package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.ConvexHull;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.FloatArray;
import com.galacticodyssey.city.layout.model.CityBlock;
import com.galacticodyssey.city.layout.model.CityGate;
import com.galacticodyssey.city.layout.model.CityWall;

import java.util.ArrayList;
import java.util.List;

/** Builds a convex-hull wall around all block corners and cuts gates where the cardinal
 *  axes (the main avenue centrelines through the origin) pierce the hull. */
public final class WallBuilder {
    private WallBuilder() {}

    public static CityWall build(List<CityBlock> blocks) {
        FloatArray pts = new FloatArray();
        for (CityBlock b : blocks) {
            pts.add(b.footprint.x);                       pts.add(b.footprint.y);
            pts.add(b.footprint.x + b.footprint.width);   pts.add(b.footprint.y);
            pts.add(b.footprint.x);                       pts.add(b.footprint.y + b.footprint.height);
            pts.add(b.footprint.x + b.footprint.width);   pts.add(b.footprint.y + b.footprint.height);
        }
        // ConvexHull.computePolygon requires a sorted flag; false = it sorts internally.
        FloatArray hullPts = new ConvexHull().computePolygon(pts, false);
        List<Vector2> hull = new ArrayList<>();
        // computePolygon repeats the first point at the end; drop the duplicate.
        for (int i = 0; i < hullPts.size - 2; i += 2) {
            hull.add(new Vector2(hullPts.get(i), hullPts.get(i + 1)));
        }
        CityWall wall = new CityWall(hull);

        float far = farthest(hull) * 2f + 10f;
        addGateAlongRay(wall, new Vector2(0, 0), new Vector2(far, 0));   // +X
        addGateAlongRay(wall, new Vector2(0, 0), new Vector2(-far, 0));  // -X
        addGateAlongRay(wall, new Vector2(0, 0), new Vector2(0, far));   // +Y
        addGateAlongRay(wall, new Vector2(0, 0), new Vector2(0, -far));  // -Y
        return wall;
    }

    private static float farthest(List<Vector2> hull) {
        float m = 0f;
        for (Vector2 v : hull) m = Math.max(m, v.len());
        return m;
    }

    private static void addGateAlongRay(CityWall wall, Vector2 from, Vector2 to) {
        List<Vector2> hull = wall.hull;
        Vector2 hit = new Vector2();
        for (int i = 0; i < hull.size(); i++) {
            Vector2 a = hull.get(i);
            Vector2 b = hull.get((i + 1) % hull.size());
            if (Intersector.intersectSegments(from, to, a, b, hit)) {
                wall.gates.add(new CityGate(new Vector2(hit)));
                return;
            }
        }
    }
}
```

> If `ConvexHull.computePolygon` ordering ever yields fewer than 3 points (degenerate, e.g. a 1-block city), the caller (Task 15) only builds a wall when `profile.hasWall`, and walled tiers always have many blocks — so this is safe in practice. The deterministic test passes because `ConvexHull` is deterministic for identical input.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.WallBuilderTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/city/layout/WallBuilder.java core/src/test/java/com/galacticodyssey/city/layout/WallBuilderTest.java
git commit -m "feat(city): WallBuilder (convex hull + axis gates)"
```

---

## Task 14: TerrainConformer

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/city/layout/TerrainConformer.java`
- Test: `core/src/test/java/com/galacticodyssey/city/layout/TerrainConformerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.layout.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerrainConformerTest {

    /** Water everywhere with x > 0; flat/dry elsewhere. */
    private TerrainSampler rightHalfWater() {
        return new TerrainSampler() {
            public float heightAt(float x, float z) { return 0f; }
            public boolean isWater(float x, float z) { return x > 0f; }
            public float slopeAt(float x, float z) { return 0f; }
        };
    }

    @Test
    void removesLotsMostlyOnWater() {
        List<BuildingLot> lots = new ArrayList<>();
        BuildingLot wet = new BuildingLot(new Rectangle(10, 0, 20, 20), DistrictType.RESIDENTIAL);
        BuildingLot dry = new BuildingLot(new Rectangle(-30, 0, 20, 20), DistrictType.RESIDENTIAL);
        lots.add(wet); lots.add(dry);
        List<Street> streets = new ArrayList<>();
        TerrainConformer.conform(streets, lots, rightHalfWater());
        assertTrue(lots.contains(dry));
        assertFalse(lots.contains(wet), "lot fully on water must be removed");
    }

    @Test
    void removesStreetsCrossingWater() {
        List<Street> streets = new ArrayList<>();
        Street wet = new Street(new Vector2(10, 0), new Vector2(40, 0), StreetTier.STREET);
        Street dry = new Street(new Vector2(-40, 0), new Vector2(-10, 0), StreetTier.STREET);
        streets.add(wet); streets.add(dry);
        TerrainConformer.conform(streets, new ArrayList<>(), rightHalfWater());
        assertTrue(streets.contains(dry));
        assertFalse(streets.contains(wet));
    }

    @Test
    void flatTerrainKeepsEverything() {
        List<BuildingLot> lots = new ArrayList<>();
        lots.add(new BuildingLot(new Rectangle(10, 0, 20, 20), DistrictType.RESIDENTIAL));
        List<Street> streets = new ArrayList<>();
        streets.add(new Street(new Vector2(0, 0), new Vector2(50, 0), StreetTier.AVENUE));
        TerrainConformer.conform(streets, lots, new FlatTerrainSampler());
        assertEquals(1, lots.size());
        assertEquals(1, streets.size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.TerrainConformerTest"`
Expected: FAIL — class missing.

- [ ] **Step 3: Implement**

`TerrainConformer.java`:
```java
package com.galacticodyssey.city.layout;

import com.badlogic.gdx.math.Rectangle;
import com.galacticodyssey.city.layout.model.BuildingLot;
import com.galacticodyssey.city.layout.model.Street;

import java.util.Iterator;
import java.util.List;

/** Removes streets crossing water/steep slope and lots mostly on inaccessible ground. */
public final class TerrainConformer {
    private static final float MAX_SLOPE = 0.577f;      // tan(30 degrees)
    private static final float MAX_INACCESSIBLE = 0.40f;

    private TerrainConformer() {}

    public static void conform(List<Street> streets, List<BuildingLot> lots, TerrainSampler terrain) {
        Iterator<Street> sit = streets.iterator();
        while (sit.hasNext()) {
            Street s = sit.next();
            if (streetIsBad(s, terrain)) sit.remove();
        }
        Iterator<BuildingLot> lit = lots.iterator();
        while (lit.hasNext()) {
            BuildingLot lot = lit.next();
            if (lotFractionInaccessible(lot.footprint, terrain) > MAX_INACCESSIBLE) lit.remove();
        }
    }

    private static boolean streetIsBad(Street s, TerrainSampler t) {
        // Sample endpoints and midpoint.
        float[][] pts = {
            {s.start.x, s.start.y},
            {s.end.x, s.end.y},
            {(s.start.x + s.end.x) / 2f, (s.start.y + s.end.y) / 2f}
        };
        for (float[] p : pts) {
            if (t.isWater(p[0], p[1]) || t.slopeAt(p[0], p[1]) > MAX_SLOPE) return true;
        }
        return false;
    }

    private static float lotFractionInaccessible(Rectangle r, TerrainSampler t) {
        // Sample a 3x3 grid; fraction of samples that are water or too steep.
        int bad = 0, total = 0;
        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) {
                float x = r.x + r.width * (i / 2f);
                float y = r.y + r.height * (j / 2f);
                total++;
                if (t.isWater(x, y) || t.slopeAt(x, y) > MAX_SLOPE) bad++;
            }
        }
        return (float) bad / total;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.TerrainConformerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/city/layout/TerrainConformer.java core/src/test/java/com/galacticodyssey/city/layout/TerrainConformerTest.java
git commit -m "feat(city): TerrainConformer (purge water/steep streets and lots)"
```

---

## Task 15: cityName + CityRequest + CityLayout + CityLayoutGenerator (orchestrator)

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/data/names/SpaceNameGenerator.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/model/CityLayout.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/CityRequest.java`
- Create: `core/src/main/java/com/galacticodyssey/city/layout/CityLayoutGenerator.java`
- Test: `core/src/test/java/com/galacticodyssey/city/layout/CityLayoutGeneratorTest.java`

- [ ] **Step 1: Add `cityName` to SpaceNameGenerator**

In `SpaceNameGenerator.java`, add after `planetName`:
```java
    /** Generates a city/settlement name using the HUMAN_COLONY style. */
    public String cityName(Random rng) {
        return generator.generate(LanguageStyles.HUMAN_COLONY, rng, "city");
    }
```

- [ ] **Step 2: Implement CityRequest**

`CityRequest.java`:
```java
package com.galacticodyssey.city.layout;

import com.galacticodyssey.galaxy.faction.FactionEthos;

import java.util.ArrayList;
import java.util.List;

/** Input contract for {@link CityLayoutGenerator}. Population is the size driver. */
public final class CityRequest {
    public long seed;
    public int population;
    public FactionEthos rulingEthos = FactionEthos.FEDERATION;
    public String factionId = "unknown";
    public TerrainSampler terrain = new FlatTerrainSampler();
    public boolean hasSpaceport = true;
    public List<AuthoredLandmark> authoredLandmarks = new ArrayList<>();
}
```

- [ ] **Step 3: Implement CityLayout**

`CityLayout.java`:
```java
package com.galacticodyssey.city.layout.model;

import com.galacticodyssey.galaxy.faction.FactionEthos;

import java.util.ArrayList;
import java.util.List;

/** The complete, deterministic, pure-data output of sub-project A. */
public final class CityLayout {
    public long cityId;
    public String name;
    public long seed;
    public int population;
    public CityType type;
    public CityForm form;
    public FactionEthos rulingEthos;
    public String factionId;
    public final GalaxyAnchor localToGalaxyAnchor = new GalaxyAnchor(); // filled by sub-project E

    public final List<Landmark> landmarks = new ArrayList<>();
    public final List<Street> streets = new ArrayList<>();
    public final List<CityBlock> blocks = new ArrayList<>();
    public final List<BuildingLot> lots = new ArrayList<>();
    public CityWall wall; // nullable
}
```

- [ ] **Step 4: Write the failing test**

```java
package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.BuildingFunction;
import com.galacticodyssey.city.layout.model.BuildingLot;
import com.galacticodyssey.city.layout.model.CityLayout;
import com.galacticodyssey.city.layout.model.CityType;
import com.galacticodyssey.galaxy.faction.FactionEthos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CityLayoutGeneratorTest {
    private CityLayoutGenerator gen;

    @BeforeEach
    void setUp() {
        CityDataRegistry reg = new CityDataRegistry();
        reg.loadFromClasspath();
        gen = new CityLayoutGenerator(reg);
    }

    private CityRequest req(int population, long seed) {
        CityRequest r = new CityRequest();
        r.population = population;
        r.seed = seed;
        r.rulingEthos = FactionEthos.FEDERATION;
        r.factionId = "fed";
        return r;
    }

    @Test
    void deterministicEndToEnd() {
        CityLayout a = gen.generate(req(30000, 123L));
        CityLayout b = gen.generate(req(30000, 123L));
        assertEquals(a.type, b.type);
        assertEquals(a.form, b.form);
        assertEquals(a.name, b.name);
        assertEquals(a.lots.size(), b.lots.size());
        assertEquals(a.blocks.size(), b.blocks.size());
        assertEquals(a.streets.size(), b.streets.size());
        for (int i = 0; i < a.lots.size(); i++) {
            assertEquals(a.lots.get(i).function, b.lots.get(i).function);
            assertEquals(a.lots.get(i).district, b.lots.get(i).district);
        }
    }

    @Test
    void cityTypeMatchesPopulation() {
        assertEquals(CityType.OUTPOST, gen.generate(req(10, 1L)).type);
        assertEquals(CityType.CITY, gen.generate(req(30000, 1L)).type);
        assertEquals(CityType.LARGE_METROPOLIS, gen.generate(req(2_000_000, 1L)).type);
    }

    @Test
    void biggerPopulationProducesMoreLots() {
        int town = gen.generate(req(400, 5L)).lots.size();
        int city = gen.generate(req(30000, 5L)).lots.size();
        assertTrue(city > town, "a CITY should have far more lots than a FRONTIER_TOWN");
    }

    @Test
    void everyLotHasDistrictAndFunction() {
        CityLayout layout = gen.generate(req(30000, 7L));
        assertFalse(layout.lots.isEmpty());
        for (BuildingLot lot : layout.lots) {
            assertNotNull(lot.district);
            assertNotNull(lot.function);
        }
    }

    @Test
    void walledTierHasWallAndOutpostDoesNot() {
        assertNotNull(gen.generate(req(30000, 9L)).wall, "CITY tier is walled");
        assertNull(gen.generate(req(10, 9L)).wall, "OUTPOST has no wall");
    }

    @Test
    void anchorLeftUnassignedForSubProjectE() {
        assertFalse(gen.generate(req(30000, 9L)).localToGalaxyAnchor.assigned);
    }

    @Test
    void nameIsNonEmpty() {
        assertNotNull(gen.generate(req(30000, 11L)).name);
        assertFalse(gen.generate(req(30000, 11L)).name.isEmpty());
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.CityLayoutGeneratorTest"`
Expected: FAIL — `CityLayoutGenerator` missing.

- [ ] **Step 6: Implement the orchestrator**

`CityLayoutGenerator.java`:
```java
package com.galacticodyssey.city.layout;

import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.model.CityLayout;
import com.galacticodyssey.data.names.SpaceNameGenerator;
import com.galacticodyssey.galaxy.SeedDeriver;

import java.util.Random;

/** Orchestrates the staged pipeline into a complete, deterministic CityLayout. */
public final class CityLayoutGenerator {
    private final CityDataRegistry registry;
    private final SpaceNameGenerator nameGen;

    public CityLayoutGenerator(CityDataRegistry registry) {
        this(registry, new SpaceNameGenerator());
    }

    public CityLayoutGenerator(CityDataRegistry registry, SpaceNameGenerator nameGen) {
        this.registry = registry;
        this.nameGen = nameGen;
    }

    public CityLayout generate(CityRequest req) {
        long citySeed = SeedDeriver.cityDomain(req.seed);

        CitySizeProfile profile = CitySizeProfile.from(registry, req.population, citySeed);
        CityLayout layout = new CityLayout();
        layout.cityId = citySeed;
        layout.seed = req.seed;
        layout.population = req.population;
        layout.rulingEthos = req.rulingEthos;
        layout.factionId = req.factionId;
        layout.type = profile.type;
        layout.form = CityFormSelector.select(registry, req.rulingEthos, profile, citySeed);

        layout.name = nameGen.cityName(new Random(SeedDeriver.forId(citySeed, 0x4A33L)));

        layout.landmarks.addAll(LandmarkPlacer.place(
                profile.radiusMetres, req.hasSpaceport, req.authoredLandmarks, citySeed));

        StreetNetwork net = StreetNetworkBuilder.build(
                layout.form, profile.radiusMetres, profile.density, citySeed);
        layout.streets.addAll(net.streets);
        layout.blocks.addAll(net.blocks);

        DistrictZoner.zone(layout.blocks, layout.landmarks, profile.radiusMetres, citySeed);
        layout.lots.addAll(LotSubdivider.subdivide(layout.blocks, registry, citySeed));
        LotFunctionAssigner.assign(layout.lots, layout.landmarks, registry, citySeed);

        if (profile.hasWall && !layout.blocks.isEmpty()) {
            layout.wall = WallBuilder.build(layout.blocks);
        }

        TerrainConformer.conform(layout.streets, layout.lots, req.terrain);
        return layout;
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.city.layout.CityLayoutGeneratorTest"`
Expected: PASS.

- [ ] **Step 8: Run the full core test suite**

Run: `./gradlew :core:test`
Expected: PASS (all city tests + no regressions).

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/names/SpaceNameGenerator.java core/src/main/java/com/galacticodyssey/city/layout/CityRequest.java core/src/main/java/com/galacticodyssey/city/layout/model/CityLayout.java core/src/main/java/com/galacticodyssey/city/layout/CityLayoutGenerator.java core/src/test/java/com/galacticodyssey/city/layout/CityLayoutGeneratorTest.java
git commit -m "feat(city): CityLayoutGenerator orchestrator + cityName"
```

---

## Task 16: Top-down debug renderer (desktop)

**Files:**
- Create: `desktop/src/main/java/com/galacticodyssey/desktop/city/CityLayoutDebugRenderer.java`
- Create: `desktop/src/main/java/com/galacticodyssey/desktop/city/CityLayoutDebugLauncher.java`

> This is a visual/manual tool, not unit-tested. Verification is launching it and eyeballing the city. First open the existing desktop launcher to copy the exact `Lwjgl3ApplicationConfiguration` usage and package conventions.

- [ ] **Step 1: Inspect the existing desktop launcher**

Run: `ls desktop/src/main/java/com/galacticodyssey/desktop/` and open the main launcher file (e.g. `DesktopLauncher.java`). Note the libGDX version's `Lwjgl3Application` + `Lwjgl3ApplicationConfiguration` API used, and mirror it.

- [ ] **Step 2: Implement the renderer (ApplicationAdapter)**

`CityLayoutDebugRenderer.java`:
```java
package com.galacticodyssey.desktop.city;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.galacticodyssey.city.data.CityDataRegistry;
import com.galacticodyssey.city.layout.CityLayoutGenerator;
import com.galacticodyssey.city.layout.CityRequest;
import com.galacticodyssey.city.layout.model.BuildingLot;
import com.galacticodyssey.city.layout.model.CityLayout;
import com.galacticodyssey.city.layout.model.CityWall;
import com.galacticodyssey.city.layout.model.DistrictType;
import com.galacticodyssey.city.layout.model.Landmark;
import com.galacticodyssey.city.layout.model.Street;
import com.galacticodyssey.galaxy.faction.FactionEthos;

/** Top-down debug view of a generated CityLayout. Keys:
 *  SPACE reroll seed, UP/DOWN change population tier, F cycle faction ethos. */
public class CityLayoutDebugRenderer extends ApplicationAdapter {
    private ShapeRenderer shapes;
    private OrthographicCamera cam;
    private CityLayoutGenerator gen;

    private long seed = 1L;
    private final int[] pops = {10, 400, 4000, 30000, 80000, 250000, 1_000_000};
    private int popIndex = 3;
    private final FactionEthos[] ethoses = FactionEthos.values();
    private int ethosIndex = 0;
    private CityLayout layout;

    @Override
    public void create() {
        shapes = new ShapeRenderer();
        cam = new OrthographicCamera();
        CityDataRegistry reg = new CityDataRegistry();
        reg.loadFromFiles(); // Gdx is available inside the running app
        gen = new CityLayoutGenerator(reg);
        regenerate();
    }

    private void regenerate() {
        CityRequest r = new CityRequest();
        r.seed = seed;
        r.population = pops[popIndex];
        r.rulingEthos = ethoses[ethosIndex];
        r.factionId = "debug";
        layout = gen.generate(r);
        Gdx.graphics.setTitle("City: " + layout.name + " | " + layout.type + " | " + layout.form
                + " | pop=" + layout.population + " | lots=" + layout.lots.size());
    }

    private void handleInput() {
        boolean changed = false;
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) { seed++; changed = true; }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) { popIndex = Math.min(pops.length - 1, popIndex + 1); changed = true; }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) { popIndex = Math.max(0, popIndex - 1); changed = true; }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) { ethosIndex = (ethosIndex + 1) % ethoses.length; changed = true; }
        if (changed) regenerate();
    }

    @Override
    public void render() {
        handleInput();
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Fit camera to the city extent.
        float span = 0f;
        for (BuildingLot lot : layout.lots) {
            span = Math.max(span, Math.abs(lot.footprint.x) + lot.footprint.width);
            span = Math.max(span, Math.abs(lot.footprint.y) + lot.footprint.height);
        }
        span = Math.max(span, 60f) * 1.1f;
        float aspect = (float) Gdx.graphics.getWidth() / Gdx.graphics.getHeight();
        cam.viewportWidth = span * 2f * aspect;
        cam.viewportHeight = span * 2f;
        cam.position.set(0, 0, 0);
        cam.update();
        shapes.setProjectionMatrix(cam.combined);

        // Lots filled by district colour.
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (BuildingLot lot : layout.lots) {
            shapes.setColor(districtColor(lot.district));
            Rectangle f = lot.footprint;
            shapes.rect(f.x, f.y, f.width, f.height);
        }
        // Landmarks as bright dots.
        shapes.setColor(Color.YELLOW);
        for (Landmark lm : layout.landmarks) shapes.circle(lm.position.x, lm.position.y, span * 0.012f, 12);
        shapes.end();

        // Streets as lines.
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.5f, 0.5f, 0.55f, 1f);
        for (Street s : layout.streets) shapes.line(s.start.x, s.start.y, s.end.x, s.end.y);

        // Wall + gates.
        CityWall wall = layout.wall;
        if (wall != null) {
            shapes.setColor(Color.LIGHT_GRAY);
            for (int i = 0; i < wall.hull.size(); i++) {
                Vector2 a = wall.hull.get(i);
                Vector2 b = wall.hull.get((i + 1) % wall.hull.size());
                shapes.line(a.x, a.y, b.x, b.y);
            }
            shapes.setColor(Color.RED);
            for (com.galacticodyssey.city.layout.model.CityGate g : wall.gates) {
                shapes.circle(g.position.x, g.position.y, span * 0.015f, 10);
            }
        }
        shapes.end();
    }

    private Color districtColor(DistrictType d) {
        switch (d) {
            case GOVERNMENT:  return new Color(0.85f, 0.85f, 0.95f, 1f);
            case COMMERCIAL:  return new Color(0.95f, 0.75f, 0.2f, 1f);
            case RESIDENTIAL: return new Color(0.3f, 0.7f, 0.3f, 1f);
            case INDUSTRIAL:  return new Color(0.6f, 0.45f, 0.3f, 1f);
            case SLUMS:       return new Color(0.4f, 0.3f, 0.3f, 1f);
            case SPACEPORT:   return new Color(0.3f, 0.6f, 0.9f, 1f);
            case RELIGIOUS:   return new Color(0.8f, 0.5f, 0.9f, 1f);
            case GARDEN:      return new Color(0.2f, 0.55f, 0.25f, 1f);
            case MILITARY:    return new Color(0.7f, 0.25f, 0.25f, 1f);
            default:          return Color.DARK_GRAY;
        }
    }

    @Override
    public void dispose() {
        if (shapes != null) shapes.dispose();
    }
}
```

- [ ] **Step 3: Implement the launcher**

`CityLayoutDebugLauncher.java` (adapt the config lines to match the existing desktop launcher you inspected in Step 1):
```java
package com.galacticodyssey.desktop.city;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Standalone launcher for the city layout top-down debug view. */
public final class CityLayoutDebugLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("City Layout Debug");
        config.setWindowedMode(1100, 900);
        new Lwjgl3Application(new CityLayoutDebugRenderer(), config);
    }
}
```

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew :desktop:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Launch and eyeball (manual)**

Run: `./gradlew :desktop:run -PmainClass=com.galacticodyssey.desktop.city.CityLayoutDebugLauncher`
(or run `CityLayoutDebugLauncher.main` from the IDE if the `:desktop:run` task isn't parameterized this way — check `desktop/build.gradle.kts` for the `application`/`mainClass` setup and adjust).
Expected: A window showing a top-down city. Press SPACE to reroll, UP/DOWN to change population (watch it grow across tiers), F to cycle faction. Confirm districts are colour-coded, a central GOVERNMENT core exists, a spaceport sits near the edge, and walled tiers show a hull with red gates.

- [ ] **Step 6: Commit**

```bash
git add desktop/src/main/java/com/galacticodyssey/desktop/city/
git commit -m "feat(city): top-down debug renderer + launcher"
```

---

## Self-Review (completed during planning)

**Spec coverage check:**
- §2 staged pipeline → Tasks 6–15 (one component per stage). ✓
- §3 inputs (CityRequest, TerrainSampler, AuthoredLandmark) → Tasks 4, 8, 15. ✓
- §4 population→size 7 tiers → Tasks 5 (JSON) + 6 (`CitySizeProfile`) + 15 test `cityTypeMatchesPopulation`. ✓
- §5 output model (incl. `GalaxyAnchor` placeholder) → Tasks 2, 3, 15. ✓
- §6 data files + registry (classpath load for tests) → Task 5. ✓
- §7 determinism (`CITY_DOMAIN`, per-stage sub-seeds, no wall-clock) → Task 1 + per-stage seeds + Task 15 `deterministicEndToEnd`. ✓
- §8 tests (determinism, scaling, no overlaps, completeness, walls, terrain, authored) → covered across Tasks 6–15. ✓
- §8 debug renderer → Task 16. ✓
- §9 package placement → all tasks use `com.galacticodyssey.city.*`. ✓
- §10 out-of-scope items (B–E) → not implemented (correct). ✓

**Type-consistency check:** `CityDataRegistry.tierForPopulation` returns `SizeTierDef` (used in Task 6); `districtMix(DistrictType)` returns `DistrictMixDef` (Tasks 11, 12); `factionFormBias(FactionEthos)` returns `List<CityForm>` (Task 7). `StreetNetworkBuilder.build(form, radius, density, seed)` signature matches its call in Task 15. `LandmarkPlacer.place(radius, hasSpaceport, authored, seed)` matches Task 15. `CitySizeProfile.from(reg, population, seed)` matches. All consistent. ✓

**Placeholder scan:** The two intentionally-invalid salt literals (`0x512E1Z`, `0xF02MBIA5L`, `0x107D1V1DL`) are each immediately followed by an explicit "Step 3b" fix with the real value — deliberate teaching guardrails, not unresolved placeholders. No `TBD`/`TODO`/"handle edge cases"/"similar to Task N". ✓
