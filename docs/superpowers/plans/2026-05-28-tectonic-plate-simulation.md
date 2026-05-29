# Tectonic Plate Simulation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a deterministic static-snapshot tectonic plate model that drives macro terrain (continents, mountains, rifts, trenches) and exports a queryable feature layer for later ocean/volcanic sub-projects.

**Architecture:** A new `com.galacticodyssey.planet.tectonic` package. `PlateGenerator` builds a `TectonicModel` once per planet from the `TECTONIC_DOMAIN` seed: it scatters plates on the unit sphere, assigns each a type/base-elevation/Euler-pole, classifies boundaries from relative plate motion, and bakes a macro-elevation field plus tagged volcanic/hotspot/rift features. `TerrainNoiseStack` consumes `baseElevation()` as its continent term; `BiomeMapper` derives sea level from `continentalFraction()`. All core logic is pure (no GL/Gdx context) so it is headless-testable; config has hardcoded defaults plus an optional JSON loader.

**Tech Stack:** Java 17, libGDX (`com.badlogic.gdx.math.Vector3`, `MathUtils`, `Json`/`JsonValue`), Ashley ECS, JUnit 5. Build: Gradle (`./gradlew :core:test`).

---

## File Structure

**New files (core logic — no Gdx/GL dependency):**
- `core/src/main/java/com/galacticodyssey/planet/tectonic/BoundaryType.java` — enum of boundary classifications
- `core/src/main/java/com/galacticodyssey/planet/tectonic/FeatureType.java` — enum of exported feature tags
- `core/src/main/java/com/galacticodyssey/planet/tectonic/TectonicFeature.java` — `{type, position}` value object
- `core/src/main/java/com/galacticodyssey/planet/tectonic/BoundaryQuery.java` — `{type, distanceNormalized}` value object
- `core/src/main/java/com/galacticodyssey/planet/tectonic/Plate.java` — plate data + `velocityAt`
- `core/src/main/java/com/galacticodyssey/planet/tectonic/TectonicConfig.java` — tunables + `defaults()` + `fromJson()`
- `core/src/main/java/com/galacticodyssey/planet/tectonic/TectonicModel.java` — partition, classification, elevation, features
- `core/src/main/java/com/galacticodyssey/planet/tectonic/PlateGenerator.java` — builds `TectonicModel` from a `Planet`

**New data file:**
- `core/src/main/resources/data/planet/tectonics.json` — config overrides (loaded in production via `TectonicConfig.fromJson`)

**Modified files:**
- `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainNoiseStack.java` — accept a `TectonicModel`, use `baseElevation` as continent term
- `core/src/main/java/com/galacticodyssey/planet/BiomeMapper.java` — overload deriving `seaLevel` from `continentalFraction`
- `core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java` — overload accepting a shared `TectonicModel`

**New test files (mirroring main paths under `core/src/test/...`):**
- `tectonic/PlateTest.java`, `tectonic/TectonicConfigTest.java`, `tectonic/TectonicModelTest.java`, `tectonic/PlateGeneratorTest.java`
- `terrain/TerrainNoiseStackTectonicTest.java`, `planet/BiomeMapperTectonicTest.java`, `planet/TectonicPipelineIntegrationTest.java`

---

## Task 1: Boundary & feature value types

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/tectonic/BoundaryType.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/tectonic/FeatureType.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/tectonic/TectonicFeature.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/tectonic/BoundaryQuery.java`

These are tiny data/enum types with no behavior to unit-test in isolation; they are exercised by later tasks. Create them directly (no separate test).

- [ ] **Step 1: Create `BoundaryType`**

```java
package com.galacticodyssey.planet.tectonic;

/** Classification of a plate boundary derived from relative plate motion. */
public enum BoundaryType {
    NONE,
    CONVERGENT_CONTINENTAL, // continent-continent collision -> mountains
    CONVERGENT_OCEANIC,     // subduction -> trench + volcanic arc
    DIVERGENT,              // spreading -> rift valley or mid-ocean ridge
    TRANSFORM               // lateral sliding -> fault, minor relief
}
```

- [ ] **Step 2: Create `FeatureType`**

```java
package com.galacticodyssey.planet.tectonic;

/** Tagged tectonic feature kinds exported for downstream sub-projects. */
public enum FeatureType {
    VOLCANIC_ARC,
    HOTSPOT,
    RIFT,
    TRENCH
}
```

- [ ] **Step 3: Create `TectonicFeature`**

```java
package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.math.Vector3;

/** A tagged tectonic feature at a unit-sphere direction. */
public final class TectonicFeature {
    public final FeatureType type;
    public final Vector3 position; // unit vector (surface direction)

    public TectonicFeature(FeatureType type, Vector3 position) {
        this.type = type;
        this.position = position;
    }
}
```

- [ ] **Step 4: Create `BoundaryQuery`**

```java
package com.galacticodyssey.planet.tectonic;

/** Result of querying the nearest plate boundary at a point. */
public final class BoundaryQuery {
    public final BoundaryType type;
    /** 0 at the boundary line, 1 at/beyond the boundary's influence radius. */
    public final float distanceNormalized;

    public BoundaryQuery(BoundaryType type, float distanceNormalized) {
        this.type = type;
        this.distanceNormalized = distanceNormalized;
    }
}
```

- [ ] **Step 5: Compile and commit**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL

```bash
git add core/src/main/java/com/galacticodyssey/planet/tectonic/
git commit -m "feat(tectonics): boundary and feature value types"
```

---

## Task 2: Plate data + velocity

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/tectonic/Plate.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/tectonic/PlateTest.java`

A plate's surface velocity from rigid rotation about an Euler pole is `v = (ω · pole) × p`, tangent to the sphere at `p`.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlateTest {

    @Test
    void velocityIsTangentToSphere() {
        // Pole along +Y, query point on the equator (+X): velocity should be tangent (dot with p ~ 0).
        Plate plate = new Plate(0, new Vector3(1, 0, 0), true, -0.3f,
                new Vector3(0, 1, 0), 0.5f);
        Vector3 p = new Vector3(1, 0, 0).nor();
        Vector3 v = plate.velocityAt(p, new Vector3());
        assertEquals(0f, v.dot(p), 1e-5f, "surface velocity must be tangent to the sphere");
        assertTrue(v.len() > 0f, "velocity should be non-zero away from the pole");
    }

    @Test
    void velocityZeroAtPole() {
        Plate plate = new Plate(0, new Vector3(0, 1, 0), false, 0.3f,
                new Vector3(0, 1, 0), 0.5f);
        Vector3 pole = new Vector3(0, 1, 0).nor();
        Vector3 v = plate.velocityAt(pole, new Vector3());
        assertEquals(0f, v.len(), 1e-5f, "velocity at the Euler pole is zero");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.tectonic.PlateTest"`
Expected: FAIL — `Plate` does not exist / cannot resolve symbol.

- [ ] **Step 3: Write minimal implementation**

```java
package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.math.Vector3;

/** One rigid tectonic plate: a region anchored at a center direction with a rigid rotation. */
public final class Plate {
    public final int id;
    public final Vector3 center;       // unit vector
    public final boolean oceanic;
    public final float baseElevation;  // normalized; continental >= 0, oceanic < 0
    public final Vector3 eulerPole;    // unit vector (rotation axis)
    public final float angularSpeed;   // arbitrary units; only relative motion matters

    public Plate(int id, Vector3 center, boolean oceanic, float baseElevation,
                 Vector3 eulerPole, float angularSpeed) {
        this.id = id;
        this.center = center.cpy().nor();
        this.oceanic = oceanic;
        this.baseElevation = baseElevation;
        this.eulerPole = eulerPole.cpy().nor();
        this.angularSpeed = angularSpeed;
    }

    /** Surface velocity at unit-direction p: v = (angularSpeed * pole) x p. Writes into out, returns out. */
    public Vector3 velocityAt(Vector3 p, Vector3 out) {
        out.set(eulerPole).crs(p).scl(angularSpeed);
        return out;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.tectonic.PlateTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/tectonic/Plate.java core/src/test/java/com/galacticodyssey/planet/tectonic/PlateTest.java
git commit -m "feat(tectonics): Plate data with Euler-pole surface velocity"
```

---

## Task 3: Config with defaults + JSON loader

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/tectonic/TectonicConfig.java`
- Create: `core/src/main/resources/data/planet/tectonics.json`
- Test: `core/src/test/java/com/galacticodyssey/planet/tectonic/TectonicConfigTest.java`

Defaults are hardcoded so core logic is testable without `Gdx.files`. `fromJson` is used only by production wiring.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.planet.PlanetType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TectonicConfigTest {

    @Test
    void defaultsAreSane() {
        TectonicConfig c = TectonicConfig.defaults();
        assertTrue(c.plateCountMin >= 3 && c.plateCountMin <= c.plateCountMax);
        assertTrue(c.boundaryInfluence > 0f);
        // Ocean worlds should target less continental crust than Terran worlds.
        assertTrue(c.continentalFractionTarget(PlanetType.OCEAN)
                 < c.continentalFractionTarget(PlanetType.TERRAN));
    }

    @Test
    void fromJsonOverridesDefaults() {
        String jsonText = "{ plateCountMin: 9, plateCountMax: 11, mountainUplift: 0.9 }";
        JsonValue root = new JsonReader().parse(jsonText);
        TectonicConfig c = TectonicConfig.fromJson(root);
        assertEquals(9, c.plateCountMin);
        assertEquals(11, c.plateCountMax);
        assertEquals(0.9f, c.mountainUplift, 1e-6f);
        // Unspecified fields keep their defaults.
        assertEquals(TectonicConfig.defaults().boundaryInfluence, c.boundaryInfluence, 1e-6f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.tectonic.TectonicConfigTest"`
Expected: FAIL — `TectonicConfig` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.planet.PlanetType;

/** Tunable parameters for tectonic generation. Defaults are hardcoded; JSON can override. */
public final class TectonicConfig {
    public int plateCountMin = 7;
    public int plateCountMax = 15;
    public float plateCountPerRadius = 2.0f; // extra plates per unit planet radius
    public int lloydIterations = 2;
    public int hotspotMin = 1;
    public int hotspotMax = 4;

    public float continentalBase = 0.35f;  // base elevation of continental plates
    public float oceanicDepth = -0.35f;     // base elevation of oceanic plates
    public float mountainUplift = 0.55f;
    public float trenchDepth = 0.45f;
    public float riftDepth = 0.18f;
    public float ridgeUplift = 0.12f;
    public float hotspotUplift = 0.30f;

    public float boundaryInfluence = 0.18f; // angular radians of boundary effect
    public float hotspotInfluence = 0.08f;  // angular radians of hotspot bump

    // Continental-fraction targets per planet type (probability a plate is continental).
    public float continentalFractionOcean = 0.15f;
    public float continentalFractionTerran = 0.45f;
    public float continentalFractionArid = 0.70f;
    public float continentalFractionDefault = 0.50f;

    public float continentalFractionTarget(PlanetType type) {
        return switch (type) {
            case OCEAN -> continentalFractionOcean;
            case TERRAN -> continentalFractionTerran;
            case ARID, BARREN, TOXIC -> continentalFractionArid;
            default -> continentalFractionDefault;
        };
    }

    public static TectonicConfig defaults() {
        return new TectonicConfig();
    }

    /** Overlays any present fields from JSON onto a fresh defaults() instance. */
    public static TectonicConfig fromJson(JsonValue root) {
        TectonicConfig c = defaults();
        if (root == null) return c;
        c.plateCountMin = root.getInt("plateCountMin", c.plateCountMin);
        c.plateCountMax = root.getInt("plateCountMax", c.plateCountMax);
        c.plateCountPerRadius = root.getFloat("plateCountPerRadius", c.plateCountPerRadius);
        c.lloydIterations = root.getInt("lloydIterations", c.lloydIterations);
        c.hotspotMin = root.getInt("hotspotMin", c.hotspotMin);
        c.hotspotMax = root.getInt("hotspotMax", c.hotspotMax);
        c.continentalBase = root.getFloat("continentalBase", c.continentalBase);
        c.oceanicDepth = root.getFloat("oceanicDepth", c.oceanicDepth);
        c.mountainUplift = root.getFloat("mountainUplift", c.mountainUplift);
        c.trenchDepth = root.getFloat("trenchDepth", c.trenchDepth);
        c.riftDepth = root.getFloat("riftDepth", c.riftDepth);
        c.ridgeUplift = root.getFloat("ridgeUplift", c.ridgeUplift);
        c.hotspotUplift = root.getFloat("hotspotUplift", c.hotspotUplift);
        c.boundaryInfluence = root.getFloat("boundaryInfluence", c.boundaryInfluence);
        c.hotspotInfluence = root.getFloat("hotspotInfluence", c.hotspotInfluence);
        c.continentalFractionOcean = root.getFloat("continentalFractionOcean", c.continentalFractionOcean);
        c.continentalFractionTerran = root.getFloat("continentalFractionTerran", c.continentalFractionTerran);
        c.continentalFractionArid = root.getFloat("continentalFractionArid", c.continentalFractionArid);
        c.continentalFractionDefault = root.getFloat("continentalFractionDefault", c.continentalFractionDefault);
        return c;
    }
}
```

- [ ] **Step 4: Create the data file**

Create `core/src/main/resources/data/planet/tectonics.json` (values match defaults; present so designers can tune without code changes):

```json
{
  "plateCountMin": 7,
  "plateCountMax": 15,
  "plateCountPerRadius": 2.0,
  "lloydIterations": 2,
  "hotspotMin": 1,
  "hotspotMax": 4,
  "continentalBase": 0.35,
  "oceanicDepth": -0.35,
  "mountainUplift": 0.55,
  "trenchDepth": 0.45,
  "riftDepth": 0.18,
  "ridgeUplift": 0.12,
  "hotspotUplift": 0.30,
  "boundaryInfluence": 0.18,
  "hotspotInfluence": 0.08,
  "continentalFractionOcean": 0.15,
  "continentalFractionTerran": 0.45,
  "continentalFractionArid": 0.70,
  "continentalFractionDefault": 0.50
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.tectonic.TectonicConfigTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/tectonic/TectonicConfig.java core/src/main/resources/data/planet/tectonics.json core/src/test/java/com/galacticodyssey/planet/tectonic/TectonicConfigTest.java
git commit -m "feat(tectonics): TectonicConfig with defaults and JSON loader"
```

---

## Task 4: TectonicModel — partition & boundary classification

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/tectonic/TectonicModel.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/tectonic/TectonicModelTest.java`

This task builds the model's spatial query + classification. Elevation/features/continentalFraction come in Task 5 (same file, extended). The test uses an explicit-plates constructor so classification is checked without RNG.

Classification math: at `dir`, let A = nearest plate, B = second-nearest. `n` = the tangential unit vector pointing from B's center toward A's center. `vRel = vA - vB`. `normalComp = vRel·n`. Convergent when `normalComp < 0` (plates closing); divergent when `> 0`; transform when the tangential component dominates.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TectonicModelTest {

    // Plate A near +X, plate B near -X, shared boundary near +Z. Both use Euler pole +Y.
    // velocityAt the +Z point: pole +Y with +speed -> +X ; with -speed -> -X.
    // CONVERGENT (plates close on the boundary): A moves -X (toward boundary) AND B moves +X
    //   -> A has -speed, B has +speed.
    // DIVERGENT (plates pull apart): A moves +X (toward its own center) AND B moves -X
    //   -> A has +speed, B has -speed.
    // The boundary normal n points from B's center toward A's center (~ +X here), so
    // vRel = vA - vB has normalComp < 0 for the convergent case and > 0 for the divergent case.
    private Vector3 boundaryDir() { return new Vector3(0, 0, 1).nor(); }

    @Test
    void plateAtReturnsNearestPlate() {
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), false, 0.3f, new Vector3(0,1,0), 0f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), false, 0.3f, new Vector3(0,1,0), 0f);
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
        assertEquals(0, m.plateAt(new Vector3(1, 0, 0).nor()));
        assertEquals(1, m.plateAt(new Vector3(-1, 0, 0).nor()));
    }

    @Test
    void convergentOceanicWhenSubducting() {
        // Both oceanic, plates closing on the +Z boundary -> subduction.
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), true, -0.3f, new Vector3(0,1,0), -1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), true, -0.3f, new Vector3(0,1,0), 1f);
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
        BoundaryQuery q = m.boundaryAt(boundaryDir());
        assertEquals(BoundaryType.CONVERGENT_OCEANIC, q.type);
        assertTrue(q.distanceNormalized < 0.2f, "boundary point should be close to the line");
    }

    @Test
    void convergentContinentalWhenBothContinental() {
        // Both continental, plates closing -> mountain collision.
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), false, 0.4f, new Vector3(0,1,0), -1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), false, 0.4f, new Vector3(0,1,0), 1f);
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
        assertEquals(BoundaryType.CONVERGENT_CONTINENTAL, m.boundaryAt(boundaryDir()).type);
    }

    @Test
    void divergentBoundaryClassified() {
        // Plates moving apart at the +Z boundary -> spreading.
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), true, -0.3f, new Vector3(0,1,0), 1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), true, -0.3f, new Vector3(0,1,0), -1f);
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
        assertEquals(BoundaryType.DIVERGENT, m.boundaryAt(boundaryDir()).type);
    }

    @Test
    void interiorPointHasNoBoundary() {
        Plate a = new Plate(0, new Vector3(1, 0, 0), true, -0.3f, new Vector3(0,1,0), 1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0), true, -0.3f, new Vector3(0,1,0), -1f);
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
        // Deep inside plate A (its center) the boundary is far -> NONE, distance saturated.
        BoundaryQuery q = m.boundaryAt(new Vector3(1, 0, 0).nor());
        assertEquals(BoundaryType.NONE, q.type);
        assertEquals(1f, q.distanceNormalized, 1e-4f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.tectonic.TectonicModelTest"`
Expected: FAIL — `TectonicModel` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;

/** Static-snapshot tectonic model: spatial plate queries, boundary classification,
 *  macro-elevation field, and exported features. Pure logic; no GL/Gdx context. */
public final class TectonicModel {
    private final List<Plate> plates;
    private final List<Vector3> hotspots;
    private final TectonicConfig config;

    // Filled in Task 5.
    private float continentalFraction;
    private final List<TectonicFeature> features = new ArrayList<>();

    // Scratch vectors (single-threaded generation/query).
    private final Vector3 va = new Vector3();
    private final Vector3 vb = new Vector3();
    private final Vector3 n = new Vector3();
    private final Vector3 rel = new Vector3();

    public TectonicModel(List<Plate> plates, List<Vector3> hotspots, TectonicConfig config) {
        this.plates = plates;
        this.hotspots = hotspots;
        this.config = config;
        bakeContinentalFraction(); // Task 5
        bakeFeatures();            // Task 5
    }

    public int plateAt(Vector3 dir) {
        return nearest(dir).id;
    }

    public boolean isOceanic(int plateId) {
        for (Plate p : plates) if (p.id == plateId) return p.oceanic;
        return false;
    }

    public BoundaryQuery boundaryAt(Vector3 dir) {
        Plate a = null, b = null;
        float bestDot = -2f, secondDot = -2f;
        for (Plate p : plates) {
            float d = dir.dot(p.center);
            if (d > bestDot) { secondDot = bestDot; b = a; bestDot = d; a = p; }
            else if (d > secondDot) { secondDot = d; b = p; }
        }
        if (a == null || b == null) return new BoundaryQuery(BoundaryType.NONE, 1f);

        float angA = (float) Math.acos(MathUtils.clamp(bestDot, -1f, 1f));
        float angB = (float) Math.acos(MathUtils.clamp(secondDot, -1f, 1f));
        float half = 0.5f * (angB - angA);
        float dn = MathUtils.clamp(half / config.boundaryInfluence, 0f, 1f);
        if (dn >= 1f) return new BoundaryQuery(BoundaryType.NONE, 1f);

        // Tangential boundary normal pointing from B's center toward A's center.
        n.set(a.center).sub(b.center);
        n.mulAdd(dir, -n.dot(dir)); // project onto tangent plane at dir
        if (n.len2() < 1e-12f) return new BoundaryQuery(BoundaryType.TRANSFORM, dn);
        n.nor();

        a.velocityAt(dir, va);
        b.velocityAt(dir, vb);
        rel.set(va).sub(vb);
        float normalComp = rel.dot(n);
        float tangMag = (float) Math.sqrt(Math.max(0f, rel.len2() - normalComp * normalComp));

        BoundaryType type;
        if (Math.abs(normalComp) >= tangMag) {
            if (normalComp < 0f) {
                type = (!a.oceanic && !b.oceanic)
                        ? BoundaryType.CONVERGENT_CONTINENTAL
                        : BoundaryType.CONVERGENT_OCEANIC;
            } else {
                type = BoundaryType.DIVERGENT;
            }
        } else {
            type = BoundaryType.TRANSFORM;
        }
        return new BoundaryQuery(type, dn);
    }

    Plate nearest(Vector3 dir) {
        Plate best = null;
        float bestDot = -2f;
        for (Plate p : plates) {
            float d = dir.dot(p.center);
            if (d > bestDot) { bestDot = d; best = p; }
        }
        return best;
    }

    // --- baked in Task 5 ---
    private void bakeContinentalFraction() { /* Task 5 */ }
    private void bakeFeatures() { /* Task 5 */ }

    List<Plate> plates() { return plates; }
    TectonicConfig config() { return config; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.tectonic.TectonicModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/tectonic/TectonicModel.java core/src/test/java/com/galacticodyssey/planet/tectonic/TectonicModelTest.java
git commit -m "feat(tectonics): plate partition and boundary classification"
```

---

## Task 5: TectonicModel — elevation, continental fraction, features

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/tectonic/TectonicModel.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/tectonic/TectonicModelTest.java` (add tests)

Adds `baseElevation()`, fills `continentalFraction()` and `features()`. Both bakes use a deterministic Fibonacci-sphere sample set (no RNG).

- [ ] **Step 1: Add failing tests**

Append these methods to `TectonicModelTest`:

```java
    @Test
    void convergentRaisesElevationAboveBase() {
        // Continental plates closing on the boundary (A -speed, B +speed) -> mountains.
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), false, 0.4f, new Vector3(0,1,0), -1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), false, 0.4f, new Vector3(0,1,0), 1f);
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
        Vector3 bd = new Vector3(0, 0, 1).nor();
        assertTrue(m.baseElevation(bd) > 0.4f, "mountains at a convergent boundary exceed plate base");
    }

    @Test
    void divergentLowersElevationBelowBase() {
        // Continental plates pulling apart (A +speed, B -speed) -> rift drops below base.
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), false, 0.4f, new Vector3(0,1,0), 1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), false, 0.4f, new Vector3(0,1,0), -1f);
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
        Vector3 bd = new Vector3(0, 0, 1).nor();
        assertTrue(m.baseElevation(bd) < 0.4f, "continental rift drops below plate base");
    }

    @Test
    void continentalFractionReflectsPlateMix() {
        // 3 continental + 1 oceanic plate, roughly evenly spread -> fraction in a sane band.
        Plate c1 = new Plate(0, new Vector3(1,0,0), false, 0.35f, new Vector3(0,1,0), 0f);
        Plate c2 = new Plate(1, new Vector3(-1,0,0), false, 0.35f, new Vector3(0,1,0), 0f);
        Plate c3 = new Plate(2, new Vector3(0,1,0), false, 0.35f, new Vector3(1,0,0), 0f);
        Plate o1 = new Plate(3, new Vector3(0,-1,0), true, -0.35f, new Vector3(1,0,0), 0f);
        TectonicModel m = new TectonicModel(List.of(c1,c2,c3,o1), List.of(), TectonicConfig.defaults());
        float f = m.continentalFraction();
        assertTrue(f > 0.4f && f < 0.95f, "continental fraction was " + f);
    }

    @Test
    void hotspotsAndArcsExported() {
        // Oceanic plate A subducting under continental plate B (closing: A -speed, B +speed)
        // -> a CONVERGENT_OCEANIC boundary that must export a volcanic arc.
        Plate a = new Plate(0, new Vector3(1, 0, 0.2f), true, -0.35f, new Vector3(0,1,0), -1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0.2f), false, 0.4f, new Vector3(0,1,0), 1f);
        Vector3 hotspot = new Vector3(0, 1, 0).nor();
        TectonicModel m = new TectonicModel(List.of(a, b), List.of(hotspot), TectonicConfig.defaults());
        boolean hasHotspot = m.features().stream().anyMatch(f -> f.type == FeatureType.HOTSPOT);
        boolean hasArc = m.features().stream().anyMatch(f -> f.type == FeatureType.VOLCANIC_ARC);
        assertTrue(hasHotspot, "hotspot must be exported as a feature");
        assertTrue(hasArc, "oceanic subduction must export a volcanic arc");
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.tectonic.TectonicModelTest"`
Expected: FAIL — `baseElevation` undefined; `continentalFraction()`/`features()` undefined; bakes are empty so feature tests fail.

- [ ] **Step 3: Implement elevation + bakes**

In `TectonicModel`, add the public methods and replace the two empty bake methods:

```java
    public float baseElevation(Vector3 dir) {
        Plate p = nearest(dir);
        float e = p.baseElevation;

        BoundaryQuery q = boundaryAt(dir);
        float falloff = 1f - q.distanceNormalized;      // 1 at boundary, 0 at edge
        float s = falloff * falloff * (3f - 2f * falloff); // smoothstep
        switch (q.type) {
            case CONVERGENT_CONTINENTAL -> e += config.mountainUplift * s;
            case CONVERGENT_OCEANIC -> e -= config.trenchDepth * s;
            case DIVERGENT -> e += (p.oceanic ? config.ridgeUplift : -config.riftDepth) * s;
            default -> { /* TRANSFORM/NONE: no elevation change */ }
        }

        for (Vector3 h : hotspots) {
            float ang = (float) Math.acos(MathUtils.clamp(dir.dot(h), -1f, 1f));
            if (ang < config.hotspotInfluence) {
                float hf = 1f - ang / config.hotspotInfluence;
                e += config.hotspotUplift * (hf * hf * (3f - 2f * hf));
            }
        }
        return e;
    }

    public float continentalFraction() { return continentalFraction; }

    public List<TectonicFeature> features() { return features; }
```

Replace `bakeContinentalFraction()` and `bakeFeatures()`:

```java
    private static final int SAMPLE_COUNT = 600;

    private void bakeContinentalFraction() {
        int land = 0;
        Vector3 d = new Vector3();
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            fibSphere(i, SAMPLE_COUNT, d);
            if (nearest(d).baseElevation >= 0f) land++;
        }
        continentalFraction = land / (float) SAMPLE_COUNT;
    }

    private void bakeFeatures() {
        for (Vector3 h : hotspots) {
            features.add(new TectonicFeature(FeatureType.HOTSPOT, h.cpy().nor()));
        }
        List<Vector3> placed = new ArrayList<>();
        Vector3 d = new Vector3();
        float minSpacing = config.boundaryInfluence * 2f;
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            fibSphere(i, SAMPLE_COUNT, d);
            BoundaryQuery q = boundaryAt(d);
            if (q.distanceNormalized > 0.25f) continue;
            FeatureType ft;
            switch (q.type) {
                case CONVERGENT_OCEANIC -> ft = FeatureType.VOLCANIC_ARC;
                case DIVERGENT -> ft = FeatureType.RIFT;
                default -> { continue; }
            }
            boolean tooClose = false;
            for (Vector3 placedDir : placed) {
                if (Math.acos(MathUtils.clamp(placedDir.dot(d), -1f, 1f)) < minSpacing) { tooClose = true; break; }
            }
            if (tooClose) continue;
            Vector3 pos = d.cpy().nor();
            placed.add(pos);
            features.add(new TectonicFeature(ft, pos));
            if (ft == FeatureType.VOLCANIC_ARC) {
                features.add(new TectonicFeature(FeatureType.TRENCH, pos)); // colocated trench
            }
        }
    }

    /** Deterministic evenly-distributed point i of n on the unit sphere (Fibonacci spiral). */
    private static void fibSphere(int i, int n, Vector3 out) {
        float ga = MathUtils.PI * (3f - (float) Math.sqrt(5.0)); // golden angle
        float y = 1f - 2f * (i + 0.5f) / n;
        float r = (float) Math.sqrt(Math.max(0f, 1f - y * y));
        float theta = ga * i;
        out.set(r * MathUtils.cos(theta), y, r * MathUtils.sin(theta)).nor();
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.tectonic.TectonicModelTest"`
Expected: PASS (all model tests)

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/tectonic/TectonicModel.java core/src/test/java/com/galacticodyssey/planet/tectonic/TectonicModelTest.java
git commit -m "feat(tectonics): elevation field, continental fraction, feature export"
```

---

## Task 6: PlateGenerator — build a model from a Planet

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/tectonic/PlateGenerator.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/tectonic/PlateGeneratorTest.java`

Seeds plates from `TECTONIC_DOMAIN`, applies Lloyd relaxation for even spacing, assigns types by the planet's continental-fraction target, gives each an Euler pole + speed, and scatters hotspots. Pure RNG from `java.util.Random(tectonicSeed)` → deterministic.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.tectonic;

import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlateGeneratorTest {

    private Planet planet(long seed, PlanetType type) {
        return new Planet(seed, type, 0.8f, 1.5f, 0.7f, 24f, 0.4f, false);
    }

    @Test
    void sameSeedProducesIdenticalModel() {
        PlateGenerator gen = new PlateGenerator();
        TectonicModel a = gen.generate(planet(777L, PlanetType.TERRAN));
        TectonicModel b = gen.generate(planet(777L, PlanetType.TERRAN));
        assertEquals(a.continentalFraction(), b.continentalFraction(), 1e-6f);
        assertEquals(a.features().size(), b.features().size());
        // Spot-check identical elevation at several directions.
        for (int i = 0; i < 20; i++) {
            com.badlogic.gdx.math.Vector3 d =
                new com.badlogic.gdx.math.Vector3(i - 10, 3, i * 0.5f - 2).nor();
            assertEquals(a.baseElevation(d), b.baseElevation(d), 1e-6f);
        }
    }

    @Test
    void differentSeedsDiffer() {
        PlateGenerator gen = new PlateGenerator();
        TectonicModel a = gen.generate(planet(1L, PlanetType.TERRAN));
        TectonicModel b = gen.generate(planet(2L, PlanetType.TERRAN));
        com.badlogic.gdx.math.Vector3 d = new com.badlogic.gdx.math.Vector3(0.4f, 0.3f, 0.8f).nor();
        assertNotEquals(a.baseElevation(d), b.baseElevation(d), 1e-4f);
    }

    @Test
    void oceanWorldsHaveLessContinentThanTerran() {
        PlateGenerator gen = new PlateGenerator();
        // Average across several seeds to wash out per-seed variance.
        float oceanSum = 0f, terranSum = 0f;
        int n = 8;
        for (long s = 0; s < n; s++) {
            oceanSum += gen.generate(planet(s, PlanetType.OCEAN)).continentalFraction();
            terranSum += gen.generate(planet(s, PlanetType.TERRAN)).continentalFraction();
        }
        assertTrue(oceanSum / n < terranSum / n,
            "ocean avg " + oceanSum/n + " should be < terran avg " + terranSum/n);
    }

    @Test
    void plateCountWithinConfiguredRange() {
        PlateGenerator gen = new PlateGenerator();
        TectonicModel m = gen.generate(planet(42L, PlanetType.TERRAN));
        int count = m.plateCount();
        TectonicConfig c = TectonicConfig.defaults();
        assertTrue(count >= c.plateCountMin && count <= c.plateCountMax + 64,
            "plate count " + count + " out of expected range");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.tectonic.PlateGeneratorTest"`
Expected: FAIL — `PlateGenerator` does not exist; `TectonicModel.plateCount()` missing.

- [ ] **Step 3: Add `plateCount()` to `TectonicModel`**

In `TectonicModel`, add:

```java
    public int plateCount() { return plates.size(); }
```

- [ ] **Step 4: Write `PlateGenerator`**

```java
package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.Planet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Builds a deterministic {@link TectonicModel} for a planet from the TECTONIC_DOMAIN seed. */
public final class PlateGenerator {

    public TectonicModel generate(Planet planet) {
        return generate(planet, TectonicConfig.defaults());
    }

    public TectonicModel generate(Planet planet, TectonicConfig config) {
        long tectonicSeed = SeedDeriver.domain(planet.seed, SeedDeriver.TECTONIC_DOMAIN);
        Random rng = new Random(tectonicSeed);

        int count = config.plateCountMin
                + Math.round(planet.radius * config.plateCountPerRadius)
                + rng.nextInt(Math.max(1, config.plateCountMax - config.plateCountMin + 1));

        List<Vector3> centers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) centers.add(randomUnit(rng));
        lloydRelax(centers, config.lloydIterations);

        float continentalTarget = config.continentalFractionTarget(planet.type);
        List<Plate> plates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            boolean oceanic = rng.nextFloat() >= continentalTarget;
            float base = oceanic ? config.oceanicDepth : config.continentalBase;
            Vector3 pole = randomUnit(rng);
            float speed = 0.3f + rng.nextFloat() * 0.7f;
            plates.add(new Plate(i, centers.get(i), oceanic, base, pole, speed));
        }

        int hotspotCount = config.hotspotMin + rng.nextInt(Math.max(1, config.hotspotMax - config.hotspotMin + 1));
        List<Vector3> hotspots = new ArrayList<>(hotspotCount);
        for (int i = 0; i < hotspotCount; i++) hotspots.add(randomUnit(rng));

        return new TectonicModel(plates, hotspots, config);
    }

    /** Uniform random unit vector via normalized Gaussian triple. */
    private static Vector3 randomUnit(Random rng) {
        float x = (float) rng.nextGaussian();
        float y = (float) rng.nextGaussian();
        float z = (float) rng.nextGaussian();
        Vector3 v = new Vector3(x, y, z);
        if (v.len2() < 1e-12f) v.set(0, 1, 0);
        return v.nor();
    }

    /** Lloyd relaxation: move each center toward the average of the sphere samples nearest to it. */
    private static void lloydRelax(List<Vector3> centers, int iterations) {
        if (iterations <= 0 || centers.size() < 2) return;
        int samples = 2000;
        for (int it = 0; it < iterations; it++) {
            Vector3[] accum = new Vector3[centers.size()];
            int[] counts = new int[centers.size()];
            for (int i = 0; i < accum.length; i++) accum[i] = new Vector3();
            Vector3 d = new Vector3();
            for (int s = 0; s < samples; s++) {
                fibSphere(s, samples, d);
                int best = 0; float bestDot = -2f;
                for (int i = 0; i < centers.size(); i++) {
                    float dot = d.dot(centers.get(i));
                    if (dot > bestDot) { bestDot = dot; best = i; }
                }
                accum[best].add(d);
                counts[best]++;
            }
            for (int i = 0; i < centers.size(); i++) {
                if (counts[i] > 0 && accum[i].len2() > 1e-12f) {
                    centers.set(i, accum[i].nor());
                }
            }
        }
    }

    private static void fibSphere(int i, int n, Vector3 out) {
        float ga = MathUtils.PI * (3f - (float) Math.sqrt(5.0));
        float y = 1f - 2f * (i + 0.5f) / n;
        float r = (float) Math.sqrt(Math.max(0f, 1f - y * y));
        float theta = ga * i;
        out.set(r * MathUtils.cos(theta), y, r * MathUtils.sin(theta)).nor();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.tectonic.PlateGeneratorTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/tectonic/PlateGenerator.java core/src/main/java/com/galacticodyssey/planet/tectonic/TectonicModel.java core/src/test/java/com/galacticodyssey/planet/tectonic/PlateGeneratorTest.java
git commit -m "feat(tectonics): PlateGenerator builds deterministic model per planet"
```

---

## Task 7: Integrate into TerrainNoiseStack

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/TerrainNoiseStack.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/terrain/TerrainNoiseStackTectonicTest.java`

The continent term becomes `tectonic.baseElevation(dir)` (with a small noise warp blend for organic edges) when a model is supplied; with `null` the legacy continent noise is used so all existing terrain tests pass unchanged.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.tectonic.Plate;
import com.galacticodyssey.planet.tectonic.TectonicConfig;
import com.galacticodyssey.planet.tectonic.TectonicModel;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TerrainNoiseStackTectonicTest {

    private TectonicModel twoContinentModel() {
        Plate a = new Plate(0, new Vector3(1, 0, 0), false, 0.4f, new Vector3(0,1,0), 1f);
        Plate b = new Plate(1, new Vector3(-1, 0, 0), false, 0.4f, new Vector3(0,1,0), -1f);
        return new TectonicModel(List.of(a, b), List.of(), TectonicConfig.defaults());
    }

    private BiomeMap biomeMap() {
        return new BiomeMap(42L, 0.2f, 0.8f, 0.5f, 288f, EnumSet.of(BiomeType.GRASSLAND));
    }

    @Test
    void modelMakesGenerationDeterministic() {
        TerrainNoiseStack a = new TerrainNoiseStack(42L, twoContinentModel());
        TerrainNoiseStack b = new TerrainNoiseStack(42L, twoContinentModel());
        Vector3 dir = new Vector3(0.6f, 0.2f, 0.3f).nor();
        assertEquals(a.heightAt(dir, biomeMap(), 3), b.heightAt(dir, biomeMap(), 3), 1e-6f);
    }

    @Test
    void tectonicBaseShiftsHeightVsNoiseOnly() {
        // Deep continental interior should sit higher than the same point with no tectonics.
        Vector3 interior = new Vector3(1, 0, 0).nor();
        TerrainNoiseStack withTec = new TerrainNoiseStack(42L, twoContinentModel());
        TerrainNoiseStack noiseOnly = new TerrainNoiseStack(42L); // legacy path, tectonic == null
        float ht = withTec.heightAt(interior, biomeMap(), 3);
        float hn = noiseOnly.heightAt(interior, biomeMap(), 3);
        assertNotEquals(ht, hn, 1e-4f, "tectonic base must change the continent term");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainNoiseStackTectonicTest"`
Expected: FAIL — constructor `TerrainNoiseStack(long, TectonicModel)` does not exist.

- [ ] **Step 3: Modify `TerrainNoiseStack`**

Add the import and field, a new constructor, and use the model in `sampleAt`. The full updated file:

```java
package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.galaxy.GalaxyNoise;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.tectonic.TectonicModel;

public final class TerrainNoiseStack {
    private final GalaxyNoise continentNoise;
    private final GalaxyNoise ridgeNoise;
    private final GalaxyNoise detailNoise;
    private final TectonicModel tectonic; // nullable: null -> legacy noise-only continents

    public static final class Sample {
        public float height;
        public BiomeType biome;
    }

    public TerrainNoiseStack(long seed) {
        this(seed, null);
    }

    public TerrainNoiseStack(long seed, TectonicModel tectonic) {
        this.continentNoise = new GalaxyNoise(seed);
        this.ridgeNoise = new GalaxyNoise(seed + 1);
        this.detailNoise = new GalaxyNoise(seed + 2);
        this.tectonic = tectonic;
    }

    public float heightAt(Vector3 dir, BiomeMap biomeMap, int lod) {
        Sample s = sampleAt(dir, biomeMap, lod);
        return s.height;
    }

    public Sample sampleAt(Vector3 dir, BiomeMap biomeMap, int lod) {
        float cx = dir.x * 2f;
        float cy = dir.y * 2f;
        float cz = dir.z * 2f;
        float warp = continentNoise.domainWarp3D(cx, cy, cz, 0.7f, 3, 6);
        float continent;
        if (tectonic != null) {
            // Tectonics drives the macro shape; noise only warps coastlines.
            continent = tectonic.baseElevation(dir) + 0.15f * warp;
        } else {
            continent = warp;
        }

        float rx = dir.x * 8f;
        float ry = dir.y * 8f;
        float rz = dir.z * 8f;
        float ridge = ridgeNoise.ridgedFbm(rx, ry, rz, 6, 2.0f, 2.0f);

        float lat = CubeSphere.latitudeOf(dir);
        float lon = CubeSphere.longitudeOf(dir);
        BiomeType biome = biomeMap.getBiome(lat, lon, continent);
        float amplitude = biome.amplitude;
        float ridgeMix = biome.ridgeMix;

        float height = (continent * (1f - ridgeMix) + ridge * ridgeMix) * amplitude;

        if (lod >= 3) {
            float dx = dir.x * 64f;
            float dy = dir.y * 64f;
            float dz = dir.z * 64f;
            float detail = detailNoise.billowFbm(dx, dy, dz, 4, 0.5f, 2.0f);
            height += detail * amplitude * 0.1f;
        }

        if (lod >= 5) {
            float fx = dir.x * 256f;
            float fy = dir.y * 256f;
            float fz = dir.z * 256f;
            float fine = detailNoise.fbm(fx, fy, fz, 3, 0.5f, 2.0f);
            height += fine * amplitude * 0.02f;
        }

        Sample s = new Sample();
        s.height = height;
        s.biome = biome;
        return s;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.terrain.TerrainNoiseStackTectonicTest" --tests "com.galacticodyssey.planet.terrain.TerrainNoiseStackTest"`
Expected: PASS (new tests pass; existing `TerrainNoiseStackTest` still passes via the legacy `null` path).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/TerrainNoiseStack.java core/src/test/java/com/galacticodyssey/planet/terrain/TerrainNoiseStackTectonicTest.java
git commit -m "feat(tectonics): drive TerrainNoiseStack continents from plate base elevation"
```

---

## Task 8: Derive sea level in BiomeMapper

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/BiomeMapper.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/BiomeMapperTectonicTest.java`

Add a 4-arg overload that derives `seaLevel` from `continentalFraction()`: more continent → lower sea level (less flooding). Existing 2-/3-arg overloads are unchanged so `BiomeMapperTest` stays green.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.planet;

import com.galacticodyssey.planet.tectonic.PlateGenerator;
import com.galacticodyssey.planet.tectonic.TectonicModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BiomeMapperTectonicTest {

    private Planet planet(long seed, PlanetType type) {
        return new Planet(seed, type, 0.8f, 1.5f, 0.7f, 24f, 0.4f, false);
    }

    @Test
    void seaLevelDerivedFromContinentalFraction() {
        BiomeMapper mapper = new BiomeMapper();
        PlateGenerator gen = new PlateGenerator();
        Planet p = planet(123L, PlanetType.TERRAN);
        Atmosphere atmo = new AtmosphereGenerator().generate(p, null);
        TectonicModel model = gen.generate(p);

        BiomeMap map = mapper.generate(p, atmo, (lon, lat) -> 0f, model);
        // Expected mapping: seaLevel = clamp(0.3 * (1 - continentalFraction), 0, 0.3)
        float expected = Math.max(0f, Math.min(0.3f, 0.3f * (1f - model.continentalFraction())));
        assertEquals(expected, map.seaLevel, 1e-5f);
    }

    @Test
    void moreContinentMeansLowerSeaLevel() {
        BiomeMapper mapper = new BiomeMapper();
        PlateGenerator gen = new PlateGenerator();
        Planet ocean = planet(5L, PlanetType.OCEAN);
        Planet terran = planet(5L, PlanetType.TERRAN);
        Atmosphere atmo = new AtmosphereGenerator().generate(terran, null);

        float oceanSea = mapper.generate(ocean, atmo, (lon, lat) -> 0f, gen.generate(ocean)).seaLevel;
        float terranSea = mapper.generate(terran, atmo, (lon, lat) -> 0f, gen.generate(terran)).seaLevel;
        assertTrue(oceanSea > terranSea,
            "ocean world sea level " + oceanSea + " should exceed terran " + terranSea);
    }
}
```

> Note: `AtmosphereGenerator.generate(planet, system)` is the existing signature used in `PipelineIntegrationTest`. Passing `null` for the system is acceptable here only if `AtmosphereGenerator` tolerates it; if the test throws an NPE, replace the atmosphere with a direct `new Atmosphere(...)` matching the constructor used in `GameWorld` (`airComposition, 101325f, 1.0f, 255f, 293f, true, EnumSet.noneOf(AtmoHazard.class)`). Verify `AtmosphereGenerator` before running and pick whichever compiles; the assertion is unaffected.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.BiomeMapperTectonicTest"`
Expected: FAIL — 4-arg `generate(...)` overload does not exist.

- [ ] **Step 3: Add the overload to `BiomeMapper`**

Add the import and a new overload; refactor the existing 3-arg `generate` to share a private helper so sea-level logic isn't duplicated:

```java
import com.galacticodyssey.planet.tectonic.TectonicModel;
```

Replace the existing 3-arg `generate` method with these three methods:

```java
    public BiomeMap generate(Planet planet, Atmosphere atmosphere, HeightSampler heightSampler) {
        return generateInternal(planet, atmosphere, heightSampler, null);
    }

    public BiomeMap generate(Planet planet, Atmosphere atmosphere, HeightSampler heightSampler,
                             TectonicModel tectonic) {
        return generateInternal(planet, atmosphere, heightSampler, tectonic);
    }

    private BiomeMap generateInternal(Planet planet, Atmosphere atmosphere, HeightSampler heightSampler,
                                      TectonicModel tectonic) {
        long biomeSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.BIOME_DOMAIN), 0);
        Random rng = new Random(biomeSeed);

        float seaLevel = (tectonic != null)
            ? Math.max(0f, Math.min(0.3f, 0.3f * (1f - tectonic.continentalFraction())))
            : rng.nextFloat() * 0.3f;
        float snowLine = 0.6f + rng.nextFloat() * 0.3f;
        float baseMoisture = moistureFromType(planet.type, rng);
        float surfaceTemp = atmosphere != null ? atmosphere.surfaceTemp : 200f;
        EnumSet<BiomeType> allowed = allowedBiomesForType(planet.type);

        long climateSeed = SeedDeriver.domain(biomeSeed, SeedDeriver.CLIMATE_DOMAIN);
        ClimateSimulator simulator = new ClimateSimulator(climateSeed);
        ClimateData climate = simulator.simulate(planet, atmosphere, heightSampler);

        return new BiomeMap(biomeSeed, seaLevel, snowLine, baseMoisture, surfaceTemp, allowed, climate);
    }
```

Keep the existing 2-arg `generate(planet, atmosphere)` method as-is (it already delegates to the 3-arg via `(lon, lat) -> 0f`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.BiomeMapperTectonicTest" --tests "com.galacticodyssey.planet.BiomeMapperTest"`
Expected: PASS (new tests pass; existing `BiomeMapperTest` unaffected since the random path is retained when `tectonic == null`).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/BiomeMapper.java core/src/test/java/com/galacticodyssey/planet/BiomeMapperTectonicTest.java
git commit -m "feat(tectonics): derive BiomeMapper sea level from continental fraction"
```

---

## Task 9: Wire PlanetTerrainSystem + end-to-end integration test

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/TectonicPipelineIntegrationTest.java`

`loadPlanet` gains a 3-arg overload taking a shared `TectonicModel`; the 2-arg overload builds one from the planet so existing callers still get tectonic terrain. The integration test proves a single shared model drives both terrain and sea level, deterministically, with no GL context.

- [ ] **Step 1: Write the failing integration test**

```java
package com.galacticodyssey.planet;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.planet.terrain.TerrainNoiseStack;
import com.galacticodyssey.planet.tectonic.PlateGenerator;
import com.galacticodyssey.planet.tectonic.TectonicModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** End-to-end: one shared TectonicModel drives both terrain heights and biome sea level,
 *  fully deterministic, with no GL context. */
class TectonicPipelineIntegrationTest {

    private Planet planet(long seed) {
        return new Planet(seed, PlanetType.TERRAN, 0.8f, 1.5f, 0.7f, 24f, 0.4f, false);
    }

    @Test
    void sharedModelDrivesTerrainAndSeaLevelDeterministically() {
        Planet p = planet(2024L);
        Atmosphere atmo = new AtmosphereGenerator().generate(p, null);

        // Build the model ONCE and share it (the production assembly contract).
        TectonicModel model = new PlateGenerator().generate(p);

        BiomeMap biomeMap = new BiomeMapper().generate(p, atmo, (lon, lat) -> 0f, model);
        TerrainNoiseStack noise = new TerrainNoiseStack(
            com.galacticodyssey.galaxy.SeedDeriver.forId(
                com.galacticodyssey.galaxy.SeedDeriver.domain(p.seed,
                    com.galacticodyssey.galaxy.SeedDeriver.TERRAIN_DOMAIN), 0),
            model);

        // Sea level consistent with the model.
        float expectedSea = Math.max(0f, Math.min(0.3f, 0.3f * (1f - model.continentalFraction())));
        assertEquals(expectedSea, biomeMap.seaLevel, 1e-5f);

        // Terrain sampling is deterministic and bounded.
        for (int i = 0; i < 50; i++) {
            Vector3 d = new Vector3(i - 25, 5, i * 0.7f - 10).nor();
            float h = noise.heightAt(d, biomeMap, 3);
            assertTrue(Float.isFinite(h), "height must be finite at sample " + i);
        }
        Vector3 probe = new Vector3(0.3f, 0.5f, 0.8f).nor();
        assertEquals(noise.heightAt(probe, biomeMap, 4), noise.heightAt(probe, biomeMap, 4), 1e-6f);
    }

    @Test
    void differentPlanetSeedsProduceDifferentTerrain() {
        Vector3 d = new Vector3(0.2f, 0.4f, 0.9f).nor();
        TectonicModel m1 = new PlateGenerator().generate(planet(1L));
        TectonicModel m2 = new PlateGenerator().generate(planet(2L));
        assertNotEquals(m1.baseElevation(d), m2.baseElevation(d), 1e-4f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails or passes partially**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.TectonicPipelineIntegrationTest"`
Expected: PASS already, since Tasks 6–8 provide everything this test uses. (This test is the integration safety net; if it fails, fix the offending task before continuing.)

- [ ] **Step 3: Add the `loadPlanet` overload**

Modify `PlanetTerrainSystem` — add the import and replace `loadPlanet`:

```java
import com.galacticodyssey.planet.tectonic.PlateGenerator;
import com.galacticodyssey.planet.tectonic.TectonicModel;
```

```java
    public void loadPlanet(Planet planet, BiomeMap biomeMap) {
        // Convenience path: build a tectonic model from the planet seed (deterministic).
        loadPlanet(planet, biomeMap, new PlateGenerator().generate(planet));
    }

    public void loadPlanet(Planet planet, BiomeMap biomeMap, TectonicModel tectonic) {
        if (quadtree != null) quadtree.dispose();
        long terrainSeed = SeedDeriver.forId(
            SeedDeriver.domain(planet.seed, SeedDeriver.TERRAIN_DOMAIN), 0);
        TerrainNoiseStack noise = new TerrainNoiseStack(terrainSeed, tectonic);
        planetRadius = planet.radius * 6371f;
        quadtree = new TerrainQuadtree(planetRadius, noise, biomeMap, dynamicsWorld);
    }
```

> Production assembly note (no code change required here, documented for the streaming sub-project): the caller that streams a planet should build the `TectonicModel` once, pass it to `BiomeMapper.generate(planet, atmo, heightSampler, model)` AND to `loadPlanet(planet, biomeMap, model)` so terrain and sea level share the same plates. The 2-arg `loadPlanet` is a convenience that rebuilds the (deterministic, identical) model when no shared instance is threaded through.

- [ ] **Step 4: Run the full planet test suite**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.*"`
Expected: PASS — all tectonic tests plus the existing planet/terrain suites (`TerrainNoiseStackTest`, `BiomeMapperTest`, `PipelineIntegrationTest`, etc.) stay green.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/terrain/PlanetTerrainSystem.java core/src/test/java/com/galacticodyssey/planet/TectonicPipelineIntegrationTest.java
git commit -m "feat(tectonics): share tectonic model across terrain and biome generation"
```

---

## Final verification

- [ ] **Run the entire core test suite**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL, no regressions.

- [ ] **Confirm determinism end-to-end** — `TectonicPipelineIntegrationTest` and `PlateGeneratorTest` both assert same-seed reproducibility; verify they pass.

---

## Self-Review (completed during planning)

**Spec coverage:**
- Generation algorithm (seed plates, Lloyd, type/base/Euler-pole, partition, classify, hotspots, bake elevation) → Tasks 3–6.
- Public interface (`baseElevation`, `plateAt`, `isOceanic`, `boundaryAt`, `continentalFraction`, `features`) → Tasks 4–5 (+`plateCount` helper in Task 6).
- Supporting types (`BoundaryType`, `BoundaryQuery`, `TectonicFeature`, `Plate`, `PlateGenerator`) → Tasks 1, 2, 6.
- Integration (TerrainNoiseStack continent term, BiomeMapper sea level, shared-model wiring) → Tasks 7, 8, 9.
- Data-driven config → Task 3 (`tectonics.json` + `fromJson`).
- Determinism & headless testability → asserted in Tasks 6 and 9; all core types are Gdx-context-free.
- Non-goals (lava/caldera/coastline geometry, time evolution) → respected; features are exported as data only.

**Type consistency:** `TectonicModel` methods (`plateAt`, `isOceanic`, `boundaryAt`, `baseElevation`, `continentalFraction`, `features`, `plateCount`) are used identically across Tasks 5–9. `BoundaryQuery.{type, distanceNormalized}` and `Plate` constructor signature `(int, Vector3, boolean, float, Vector3, float)` are consistent in every task. `BiomeMapper.generate(...)` 4-arg signature matches between Tasks 8 and 9.

**Placeholder scan:** No TBD/TODO. The Task 4 bake methods are intentional empty stubs (`/* Task 5 */`) that Task 5 Step 3 fully implements; this is sequenced, not a placeholder.
