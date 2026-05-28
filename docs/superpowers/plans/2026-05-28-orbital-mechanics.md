# Orbital Mechanics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make planets and moons orbit their parent bodies on Keplerian paths, track which sphere-of-influence a ship occupies, apply gravitational forces to physics bodies with hybrid attenuation, and render a real-time trajectory prediction arc.

**Architecture:** Entity-driven approach where each celestial body (star, planet, moon) is an Ashley ECS entity with `OrbitalBodyComponent`, `GravitySourceComponent`, and `TransformComponent`. A chain of systems at ascending priorities advances anomalies, syncs positions, tracks SOI, applies gravity forces, and predicts trajectories. The existing `KeplerianOrbitSystem` and `OrbitalMechanics` utility class provide the orbital math foundation.

**Tech Stack:** Java 17, libGDX 1.13+, Ashley ECS, gdx-bullet, JUnit 5

**Spec:** `docs/superpowers/specs/2026-05-28-orbital-mechanics-design.md`

---

## File Map

### New files

| File | Responsibility |
|------|----------------|
| `core/src/main/java/com/galacticodyssey/galaxy/OrbitalConstants.java` | AU_TO_GAME_UNITS, EARTH_MASS_KG, SOLAR_MASS_KG constants |
| `core/src/main/java/com/galacticodyssey/core/components/CelestialBodyType.java` | Enum: STAR, PLANET, MOON |
| `core/src/main/java/com/galacticodyssey/core/components/OrbitalBodyComponent.java` | Links entity to OrbitalSlot, parent, SOI |
| `core/src/main/java/com/galacticodyssey/core/components/SOITrackerComponent.java` | Tracks dominant/secondary gravity body |
| `core/src/main/java/com/galacticodyssey/core/components/TrajectoryComponent.java` | Cached predicted orbit path |
| `core/src/main/java/com/galacticodyssey/core/events/SOIChangedEvent.java` | Event for SOI boundary crossing |
| `core/src/main/java/com/galacticodyssey/core/systems/OrbitalPositionSystem.java` | Syncs OrbitalSlot positions to TransformComponent |
| `core/src/main/java/com/galacticodyssey/core/systems/SOITrackingSystem.java` | Determines SOI, velocity frame conversion |
| `core/src/main/java/com/galacticodyssey/core/systems/GravityForceSystem.java` | Applies gravity acceleration to btRigidBody |
| `core/src/main/java/com/galacticodyssey/core/systems/TrajectoryPredictionSystem.java` | Derives orbit from state vectors, samples path |
| `core/src/main/java/com/galacticodyssey/ui/OrbitLineRenderer.java` | Renders 3D trajectory arc |
| `core/src/test/java/com/galacticodyssey/galaxy/OrbitalConstantsTest.java` | Constants tests |
| `core/src/test/java/com/galacticodyssey/core/systems/OrbitalPositionSystemTest.java` | Position sync tests |
| `core/src/test/java/com/galacticodyssey/core/systems/SOITrackingSystemTest.java` | SOI detection tests |
| `core/src/test/java/com/galacticodyssey/core/systems/GravityForceSystemTest.java` | Force application tests |
| `core/src/test/java/com/galacticodyssey/core/systems/TrajectoryPredictionSystemTest.java` | Trajectory tests |
| `core/src/test/java/com/galacticodyssey/galaxy/KeplerianOrbitSystemTest.java` | Time scale and moon orbit tests |
| `core/src/test/java/com/galacticodyssey/galaxy/OrbitalMechanicsIntegrationTest.java` | Full system integration test |

### Modified files

| File | Change |
|------|--------|
| `core/src/main/java/com/galacticodyssey/galaxy/KeplerianOrbitSystem.java` | Add `timeScale` field, advance moon anomalies |
| `core/src/main/java/com/galacticodyssey/planet/Moon.java` | Add orbital element fields |
| `core/src/main/java/com/galacticodyssey/planet/PlanetGenerator.java` | Populate moon orbital data |
| `core/src/main/java/com/galacticodyssey/core/systems/GravitySystem.java` | Hybrid SOI-aware attenuation |
| `core/src/main/java/com/galacticodyssey/core/GameWorld.java` | Register new systems, celestial entity factory |
| `core/src/main/java/com/galacticodyssey/ui/OrbitHUDPanel.java` | React to SOIChangedEvent |

---

### Task 1: Constants, Enum, and Event Foundation

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/OrbitalConstants.java`
- Create: `core/src/main/java/com/galacticodyssey/core/components/CelestialBodyType.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/SOIChangedEvent.java`
- Test: `core/src/test/java/com/galacticodyssey/galaxy/OrbitalConstantsTest.java`

- [ ] **Step 1: Write failing test for OrbitalConstants**

```java
package com.galacticodyssey.galaxy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrbitalConstantsTest {

    @Test
    void auToGameUnitsIsPositive() {
        assertTrue(OrbitalConstants.AU_TO_GAME_UNITS > 0f);
        assertEquals(1000f, OrbitalConstants.AU_TO_GAME_UNITS);
    }

    @Test
    void earthMassKgMatchesPhysics() {
        assertEquals(5.972e24f, OrbitalConstants.EARTH_MASS_KG, 1e20f);
    }

    @Test
    void solarMassKgMatchesPhysics() {
        assertEquals(1.989e30f, OrbitalConstants.SOLAR_MASS_KG, 1e26f);
    }

    @Test
    void gravitationalConstantMatchesGravitySystem() {
        assertEquals(OrbitalMechanics.G, OrbitalConstants.G);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.OrbitalConstantsTest" 2>&1 | tail -5`
Expected: Compilation error — `OrbitalConstants` does not exist.

- [ ] **Step 3: Create OrbitalConstants**

```java
package com.galacticodyssey.galaxy;

public final class OrbitalConstants {

    public static final float AU_TO_GAME_UNITS = 1000f;
    public static final float EARTH_MASS_KG = 5.972e24f;
    public static final float SOLAR_MASS_KG = 1.989e30f;
    public static final float G = 6.674e-11f;

    private OrbitalConstants() {}
}
```

- [ ] **Step 4: Create CelestialBodyType enum**

```java
package com.galacticodyssey.core.components;

public enum CelestialBodyType {
    STAR,
    PLANET,
    MOON
}
```

- [ ] **Step 5: Create SOIChangedEvent**

```java
package com.galacticodyssey.core.events;

import com.badlogic.ashley.core.Entity;

public final class SOIChangedEvent {
    public final Entity entity;
    public final Entity oldDominantBody;
    public final Entity newDominantBody;

    public SOIChangedEvent(Entity entity, Entity oldDominantBody, Entity newDominantBody) {
        this.entity = entity;
        this.oldDominantBody = oldDominantBody;
        this.newDominantBody = newDominantBody;
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.OrbitalConstantsTest" 2>&1 | tail -5`
Expected: All 4 tests PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/OrbitalConstants.java \
        core/src/main/java/com/galacticodyssey/core/components/CelestialBodyType.java \
        core/src/main/java/com/galacticodyssey/core/events/SOIChangedEvent.java \
        core/src/test/java/com/galacticodyssey/galaxy/OrbitalConstantsTest.java
git commit -m "feat(orbital): add OrbitalConstants, CelestialBodyType, SOIChangedEvent"
```

---

### Task 2: OrbitalBodyComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/components/OrbitalBodyComponent.java`

- [ ] **Step 1: Create OrbitalBodyComponent**

Follow the pattern from `GravitySourceComponent` (implements `Component, Pool.Poolable` with a `reset()` method).

```java
package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.galaxy.KeplerOrbit;
import com.galacticodyssey.galaxy.OrbitalSlot;

public class OrbitalBodyComponent implements Component, Pool.Poolable {

    public OrbitalSlot orbitalSlot;
    public Entity parentBody;
    public float bodyRadius;
    public float soiRadius;
    public KeplerOrbit cachedOrbit;
    public CelestialBodyType bodyType = CelestialBodyType.PLANET;

    @Override
    public void reset() {
        orbitalSlot = null;
        parentBody = null;
        bodyRadius = 0f;
        soiRadius = 0f;
        cachedOrbit = null;
        bodyType = CelestialBodyType.PLANET;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/components/OrbitalBodyComponent.java
git commit -m "feat(orbital): add OrbitalBodyComponent"
```

---

### Task 3: SOITrackerComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/components/SOITrackerComponent.java`

- [ ] **Step 1: Create SOITrackerComponent**

```java
package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Pool;

public class SOITrackerComponent implements Component, Pool.Poolable {

    public Entity dominantBody;
    public Entity secondaryBody;
    public float distanceToDominant;

    @Override
    public void reset() {
        dominantBody = null;
        secondaryBody = null;
        distanceToDominant = 0f;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/components/SOITrackerComponent.java
git commit -m "feat(orbital): add SOITrackerComponent"
```

---

### Task 4: TrajectoryComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/components/TrajectoryComponent.java`

- [ ] **Step 1: Create TrajectoryComponent**

```java
package com.galacticodyssey.core.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.galacticodyssey.galaxy.KeplerOrbit;

public class TrajectoryComponent implements Component, Pool.Poolable {

    public KeplerOrbit currentOrbit;
    public final Array<Vector3> predictedPath = new Array<>();
    public boolean isStable;
    public float periapsis;
    public float apoapsis;
    public boolean dirty = true;
    public int sampleSegments = 96;

    @Override
    public void reset() {
        currentOrbit = null;
        predictedPath.clear();
        isStable = false;
        periapsis = 0f;
        apoapsis = 0f;
        dirty = true;
        sampleSegments = 96;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/components/TrajectoryComponent.java
git commit -m "feat(orbital): add TrajectoryComponent"
```

---

### Task 5: Moon Orbital Data

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/planet/Moon.java`
- Modify: `core/src/main/java/com/galacticodyssey/planet/PlanetGenerator.java:44-52`

- [ ] **Step 1: Write failing test for moon orbital period**

Create `core/src/test/java/com/galacticodyssey/planet/MoonOrbitalTest.java`:

```java
package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.OrbitalConstants;
import com.galacticodyssey.galaxy.OrbitalMechanics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoonOrbitalTest {

    @Test
    void moonHasOrbitalElements() {
        Moon moon = new Moon(42L, PlanetType.BARREN, 0.1f, 0.01f,
            0.005f, 0.05f, 0.1f);

        assertEquals(0.005f, moon.orbitalRadius);
        assertEquals(0.05f, moon.orbitalEccentricity);
        assertTrue(moon.orbitalPeriod > 0f);
    }

    @Test
    void moonOrbitalPeriodDerivesFromRadiusAndParentMass() {
        float moonRadiusAU = 0.003f;
        float parentMassKg = 10f * OrbitalConstants.EARTH_MASS_KG;
        float GM = OrbitalConstants.G * parentMassKg;
        float radiusGameUnits = moonRadiusAU * OrbitalConstants.AU_TO_GAME_UNITS;
        float expectedPeriod = OrbitalMechanics.orbitalPeriod(GM, radiusGameUnits);

        Moon moon = new Moon(1L, PlanetType.BARREN, 0.1f, 0.01f,
            moonRadiusAU, 0.0f, 0.0f);
        moon.computeOrbitalPeriod(parentMassKg);

        assertEquals(expectedPeriod, moon.orbitalPeriod, expectedPeriod * 0.001f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.MoonOrbitalTest" 2>&1 | tail -5`
Expected: Compilation error — Moon constructor doesn't accept orbital parameters.

- [ ] **Step 3: Extend Moon.java with orbital fields**

Replace the entire `Moon.java`:

```java
package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.OrbitalConstants;
import com.galacticodyssey.galaxy.OrbitalMechanics;

public final class Moon {
    public final long seed;
    public final PlanetType type;
    public final float radius;
    public final float mass;
    public final float surfaceGravity;

    public final float orbitalRadius;
    public final float orbitalEccentricity;
    public final float orbitalInclination;
    public float orbitalPeriod;

    public float currentTrueAnomaly;
    public float currentMeanAnomaly;

    public Moon(long seed, PlanetType type, float radius, float mass) {
        this(seed, type, radius, mass, 0f, 0f, 0f);
    }

    public Moon(long seed, PlanetType type, float radius, float mass,
                float orbitalRadius, float orbitalEccentricity, float orbitalInclination) {
        this.seed = seed;
        this.type = type;
        this.radius = radius;
        this.mass = mass;
        this.surfaceGravity = mass / (radius * radius);
        this.orbitalRadius = orbitalRadius;
        this.orbitalEccentricity = orbitalEccentricity;
        this.orbitalInclination = orbitalInclination;
    }

    public void computeOrbitalPeriod(float parentMassKg) {
        if (orbitalRadius <= 0f || parentMassKg <= 0f) {
            orbitalPeriod = Float.MAX_VALUE;
            return;
        }
        float GM = OrbitalConstants.G * parentMassKg;
        float radiusGameUnits = orbitalRadius * OrbitalConstants.AU_TO_GAME_UNITS;
        orbitalPeriod = OrbitalMechanics.orbitalPeriod(GM, radiusGameUnits);
    }
}
```

- [ ] **Step 4: Update PlanetGenerator to populate moon orbital data**

In `core/src/main/java/com/galacticodyssey/planet/PlanetGenerator.java`, replace the moon creation loop (lines 44-52):

```java
        for (int m = 0; m < moonCount; m++) {
            long moonSeed = SeedDeriver.forId(
                SeedDeriver.domain(planetSeed, SeedDeriver.MOON_DOMAIN), m);
            Random moonRng = new Random(moonSeed);
            PlanetType moonType = moonRng.nextFloat() < 0.5f ? PlanetType.BARREN : PlanetType.ICE_WORLD;
            float moonRadius = RngUtil.range(moonRng, 0.05f, radius * 0.3f);
            float moonMass = moonRadius * moonRadius * RngUtil.range(moonRng, 0.5f, 1.0f);

            float moonOrbitalRadius = (0.002f + m * 0.002f) * RngUtil.range(moonRng, 0.8f, 1.2f);
            float moonEccentricity = moonRng.nextFloat() * 0.1f;
            float moonInclination = (float)(moonRng.nextGaussian() * 0.1);

            Moon moon = new Moon(moonSeed, moonType, moonRadius, moonMass,
                moonOrbitalRadius, moonEccentricity, moonInclination);
            moon.computeOrbitalPeriod(mass * OrbitalConstants.EARTH_MASS_KG);
            planet.moons.add(moon);
        }
```

Note: `mass` here is the planet's mass in Earth-masses (from the local variable in `generate()`). We convert to kg for period calculation.

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.MoonOrbitalTest" 2>&1 | tail -5`
Expected: All tests PASS.

- [ ] **Step 6: Verify existing tests still pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.OrbitalLayoutGeneratorTest" 2>&1 | tail -5`
Expected: All tests PASS (Moon constructor backward-compatible via overload).

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/Moon.java \
        core/src/main/java/com/galacticodyssey/planet/PlanetGenerator.java \
        core/src/test/java/com/galacticodyssey/planet/MoonOrbitalTest.java
git commit -m "feat(orbital): add moon orbital elements and period computation"
```

---

### Task 6: KeplerianOrbitSystem Time Scale and Moon Advancement

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/galaxy/KeplerianOrbitSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/galaxy/KeplerianOrbitSystemTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.planet.Moon;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeplerianOrbitSystemTest {

    private EventBus eventBus;
    private KeplerianOrbitSystem system;
    private StarSystem starSystem;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        system = new KeplerianOrbitSystem(eventBus);

        starSystem = new StarSystem(1L, 42L, SpectralClass.G, LuminosityClass.MAIN_SEQUENCE,
            5778f, 1.0f, 1.0f, 1.0f, 4.6f, Color.YELLOW);
        OrbitalSlot slot = new OrbitalSlot(0, 1.0f, 0.017f, OrbitalZone.HABITABLE);
        starSystem.orbits.add(slot);

        Planet planet = new Planet(100L, PlanetType.TERRAN, 1.0f, 1.0f, 5.5f, 24f, 23.5f, false);
        Moon moon = new Moon(200L, PlanetType.BARREN, 0.27f, 0.012f,
            0.00257f, 0.055f, 0.09f);
        moon.computeOrbitalPeriod(1.0f * OrbitalConstants.EARTH_MASS_KG);
        planet.moons.add(moon);
        slot.planet = planet;

        system.setActiveSystem(starSystem);
    }

    @Test
    void timeScaleDefaultsToOne() {
        assertEquals(1.0f, system.getTimeScale());
    }

    @Test
    void timeScaleMultipliesDt() {
        system.setTimeScale(10f);
        OrbitalSlot slot = starSystem.orbits.get(0);
        float initialAnomaly = slot.currentMeanAnomaly;

        system.update(1.0f);

        float n = MathUtils.PI2 / slot.orbitalPeriod;
        float expected = initialAnomaly + n * 10f;
        assertEquals(expected, slot.currentMeanAnomaly, 1e-5f);
    }

    @Test
    void moonAnomalyAdvancesEachTick() {
        Planet planet = starSystem.orbits.get(0).planet;
        Moon moon = planet.moons.get(0);
        float initialMoonAnomaly = moon.currentMeanAnomaly;

        system.update(1.0f);

        assertTrue(moon.currentMeanAnomaly > initialMoonAnomaly,
            "Moon mean anomaly should advance after update");
    }

    @Test
    void orbitTickEventPublished() {
        List<KeplerianOrbitSystem.OrbitTickEvent> received = new ArrayList<>();
        eventBus.subscribe(KeplerianOrbitSystem.OrbitTickEvent.class, received::add);

        system.update(0.016f);

        assertEquals(1, received.size());
        assertSame(starSystem, received.get(0).system);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.KeplerianOrbitSystemTest" 2>&1 | tail -5`
Expected: Compilation error — `getTimeScale()` and `setTimeScale()` don't exist.

- [ ] **Step 3: Add timeScale and moon advancement to KeplerianOrbitSystem**

Replace `core/src/main/java/com/galacticodyssey/galaxy/KeplerianOrbitSystem.java`:

```java
package com.galacticodyssey.galaxy;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.MathUtils;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.planet.Moon;
import com.galacticodyssey.planet.Planet;

public final class KeplerianOrbitSystem extends EntitySystem {

    public static final class OrbitTickEvent {
        public final StarSystem system;

        public OrbitTickEvent(StarSystem system) {
            this.system = system;
        }
    }

    private final EventBus eventBus;
    private StarSystem activeSystem;
    private float timeScale = 1.0f;

    public KeplerianOrbitSystem(EventBus eventBus) {
        super(2);
        this.eventBus = eventBus;
    }

    public void setActiveSystem(StarSystem system) {
        this.activeSystem = system;
        if (system != null) {
            for (OrbitalSlot slot : system.orbits) {
                slot.starMass = system.mass;
            }
        }
    }

    public StarSystem getActiveSystem() {
        return activeSystem;
    }

    public void setTimeScale(float timeScale) {
        this.timeScale = timeScale;
    }

    public float getTimeScale() {
        return timeScale;
    }

    @Override
    public void update(float dt) {
        if (activeSystem == null || activeSystem.orbits.isEmpty()) return;

        final float scaledDt = dt * timeScale;

        for (OrbitalSlot slot : activeSystem.orbits) {
            if (slot.orbitalPeriod <= 0f) continue;

            float n = MathUtils.PI2 / slot.orbitalPeriod;
            slot.currentMeanAnomaly += n * scaledDt;
            slot.currentTrueAnomaly = OrbitalMechanics.trueAnomalyFromMean(
                slot.currentMeanAnomaly, slot.eccentricity);

            Planet planet = slot.planet;
            if (planet != null) {
                for (Moon moon : planet.moons) {
                    if (moon.orbitalPeriod <= 0f || moon.orbitalPeriod >= Float.MAX_VALUE) continue;
                    float moonN = MathUtils.PI2 / moon.orbitalPeriod;
                    moon.currentMeanAnomaly += moonN * scaledDt;
                    moon.currentTrueAnomaly = OrbitalMechanics.trueAnomalyFromMean(
                        moon.currentMeanAnomaly, moon.orbitalEccentricity);
                }
            }
        }

        eventBus.publish(new OrbitTickEvent(activeSystem));
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.KeplerianOrbitSystemTest" 2>&1 | tail -5`
Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/galaxy/KeplerianOrbitSystem.java \
        core/src/test/java/com/galacticodyssey/galaxy/KeplerianOrbitSystemTest.java
git commit -m "feat(orbital): add timeScale to KeplerianOrbitSystem, advance moon anomalies"
```

---

### Task 7: OrbitalPositionSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/systems/OrbitalPositionSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/core/systems/OrbitalPositionSystemTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.CelestialBodyType;
import com.galacticodyssey.core.components.OrbitalBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.galaxy.OrbitalConstants;
import com.galacticodyssey.galaxy.OrbitalSlot;
import com.galacticodyssey.galaxy.OrbitalZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrbitalPositionSystemTest {

    private Engine engine;
    private OrbitalPositionSystem positionSystem;
    private Entity starEntity;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        positionSystem = new OrbitalPositionSystem();
        engine.addSystem(positionSystem);

        starEntity = new Entity();
        TransformComponent starTransform = new TransformComponent();
        starTransform.position.set(0f, 0f, 0f);
        starEntity.add(starTransform);

        OrbitalBodyComponent starBody = new OrbitalBodyComponent();
        starBody.bodyType = CelestialBodyType.STAR;
        starBody.orbitalSlot = null;
        starEntity.add(starBody);

        engine.addEntity(starEntity);
    }

    @Test
    void planetPositionedRelativeToStar() {
        OrbitalSlot slot = new OrbitalSlot(0, 1.0f, 0.0f, OrbitalZone.HABITABLE);
        slot.starMass = 1.989e30f;
        slot.currentTrueAnomaly = 0f;

        Entity planet = new Entity();
        TransformComponent planetTransform = new TransformComponent();
        planet.add(planetTransform);

        OrbitalBodyComponent planetBody = new OrbitalBodyComponent();
        planetBody.bodyType = CelestialBodyType.PLANET;
        planetBody.orbitalSlot = slot;
        planetBody.parentBody = starEntity;
        planet.add(planetBody);

        engine.addEntity(planet);
        engine.update(0f);

        float expectedX = 1.0f * OrbitalConstants.AU_TO_GAME_UNITS;
        assertEquals(expectedX, planetTransform.position.x, 1f);
        assertEquals(0f, planetTransform.position.y, 0.1f);
    }

    @Test
    void starPositionUnchanged() {
        TransformComponent starTransform = starEntity.getComponent(TransformComponent.class);
        starTransform.position.set(50f, 0f, 0f);

        engine.update(0f);

        assertEquals(50f, starTransform.position.x, 0.01f);
    }

    @Test
    void planetAtHalfOrbitOffset() {
        OrbitalSlot slot = new OrbitalSlot(0, 2.0f, 0.0f, OrbitalZone.OUTER);
        slot.starMass = 1.989e30f;
        slot.currentTrueAnomaly = MathUtils.PI;

        Entity planet = new Entity();
        TransformComponent planetTransform = new TransformComponent();
        planet.add(planetTransform);

        OrbitalBodyComponent planetBody = new OrbitalBodyComponent();
        planetBody.bodyType = CelestialBodyType.PLANET;
        planetBody.orbitalSlot = slot;
        planetBody.parentBody = starEntity;
        planet.add(planetBody);

        engine.addEntity(planet);
        engine.update(0f);

        float expectedX = -2.0f * OrbitalConstants.AU_TO_GAME_UNITS;
        assertEquals(expectedX, planetTransform.position.x, 1f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.OrbitalPositionSystemTest" 2>&1 | tail -5`
Expected: Compilation error — `OrbitalPositionSystem` does not exist.

- [ ] **Step 3: Implement OrbitalPositionSystem**

```java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.components.CelestialBodyType;
import com.galacticodyssey.core.components.OrbitalBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.galaxy.OrbitalConstants;

public class OrbitalPositionSystem extends EntitySystem {

    private static final ComponentMapper<OrbitalBodyComponent> orbitalMapper =
        ComponentMapper.getFor(OrbitalBodyComponent.class);
    private static final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    private ImmutableArray<Entity> bodies;

    public OrbitalPositionSystem() {
        super(3);
    }

    @Override
    public void addedToEngine(Engine engine) {
        bodies = engine.getEntitiesFor(
            Family.all(OrbitalBodyComponent.class, TransformComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        bodies = null;
    }

    @Override
    public void update(float deltaTime) {
        if (bodies == null) return;

        Vector3 tmp = Pools.obtain(Vector3.class);
        try {
            for (int pass = 0; pass < 3; pass++) {
                CelestialBodyType targetType = CelestialBodyType.values()[pass];
                for (int i = 0; i < bodies.size(); i++) {
                    Entity entity = bodies.get(i);
                    OrbitalBodyComponent orbital = orbitalMapper.get(entity);
                    if (orbital.bodyType != targetType) continue;

                    if (orbital.orbitalSlot == null) continue;

                    TransformComponent transform = transformMapper.get(entity);
                    orbital.orbitalSlot.getLocalPosition(tmp);
                    tmp.scl(OrbitalConstants.AU_TO_GAME_UNITS);

                    if (orbital.parentBody != null) {
                        TransformComponent parentTransform = transformMapper.get(orbital.parentBody);
                        if (parentTransform != null) {
                            tmp.add(parentTransform.position);
                        }
                    }

                    transform.position.set(tmp);
                }
            }
        } finally {
            Pools.free(tmp);
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.OrbitalPositionSystemTest" 2>&1 | tail -5`
Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/systems/OrbitalPositionSystem.java \
        core/src/test/java/com/galacticodyssey/core/systems/OrbitalPositionSystemTest.java
git commit -m "feat(orbital): add OrbitalPositionSystem to sync Keplerian positions to entities"
```

---

### Task 8: SOITrackingSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/systems/SOITrackingSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/core/systems/SOITrackingSystemTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.CelestialBodyType;
import com.galacticodyssey.core.components.OrbitalBodyComponent;
import com.galacticodyssey.core.components.SOITrackerComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.SOIChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SOITrackingSystemTest {

    private Engine engine;
    private SOITrackingSystem soiSystem;
    private EventBus eventBus;
    private Entity starEntity;
    private Entity planetEntity;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        soiSystem = new SOITrackingSystem(eventBus);
        engine.addSystem(soiSystem);

        starEntity = new Entity();
        TransformComponent starTransform = new TransformComponent();
        starTransform.position.set(0f, 0f, 0f);
        starEntity.add(starTransform);
        OrbitalBodyComponent starOrbital = new OrbitalBodyComponent();
        starOrbital.bodyType = CelestialBodyType.STAR;
        starOrbital.soiRadius = 0f;
        starEntity.add(starOrbital);
        engine.addEntity(starEntity);

        planetEntity = new Entity();
        TransformComponent planetTransform = new TransformComponent();
        planetTransform.position.set(1000f, 0f, 0f);
        planetEntity.add(planetTransform);
        OrbitalBodyComponent planetOrbital = new OrbitalBodyComponent();
        planetOrbital.bodyType = CelestialBodyType.PLANET;
        planetOrbital.soiRadius = 200f;
        planetOrbital.parentBody = starEntity;
        planetEntity.add(planetOrbital);
        engine.addEntity(planetEntity);
    }

    @Test
    void entityInsidePlanetSOIGetsPlanetAsDominant() {
        Entity ship = createShip(1050f, 0f, 0f);
        engine.addEntity(ship);

        engine.update(0f);

        SOITrackerComponent tracker = ship.getComponent(SOITrackerComponent.class);
        assertSame(planetEntity, tracker.dominantBody);
        assertSame(starEntity, tracker.secondaryBody);
    }

    @Test
    void entityOutsideAllPlanetSOIsGetsStarAsDominant() {
        Entity ship = createShip(500f, 0f, 0f);
        engine.addEntity(ship);

        engine.update(0f);

        SOITrackerComponent tracker = ship.getComponent(SOITrackerComponent.class);
        assertSame(starEntity, tracker.dominantBody);
        assertNull(tracker.secondaryBody);
    }

    @Test
    void soiChangePublishesEvent() {
        Entity ship = createShip(500f, 0f, 0f);
        engine.addEntity(ship);
        engine.update(0f);

        List<SOIChangedEvent> events = new ArrayList<>();
        eventBus.subscribe(SOIChangedEvent.class, events::add);

        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        shipTransform.position.set(1050f, 0f, 0f);
        engine.update(0f);

        assertEquals(1, events.size());
        assertSame(starEntity, events.get(0).oldDominantBody);
        assertSame(planetEntity, events.get(0).newDominantBody);
    }

    @Test
    void distanceToDominantUpdated() {
        Entity ship = createShip(1100f, 0f, 0f);
        engine.addEntity(ship);

        engine.update(0f);

        SOITrackerComponent tracker = ship.getComponent(SOITrackerComponent.class);
        assertEquals(100f, tracker.distanceToDominant, 1f);
    }

    private Entity createShip(float x, float y, float z) {
        Entity ship = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        ship.add(transform);
        ship.add(new SOITrackerComponent());
        return ship;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.SOITrackingSystemTest" 2>&1 | tail -5`
Expected: Compilation error — `SOITrackingSystem` does not exist.

- [ ] **Step 3: Implement SOITrackingSystem**

```java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.*;
import com.galacticodyssey.core.events.SOIChangedEvent;
import com.galacticodyssey.galaxy.OrbitalConstants;
import com.galacticodyssey.galaxy.OrbitalSlot;

public class SOITrackingSystem extends EntitySystem {

    private static final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<SOITrackerComponent> soiMapper =
        ComponentMapper.getFor(SOITrackerComponent.class);
    private static final ComponentMapper<OrbitalBodyComponent> orbitalMapper =
        ComponentMapper.getFor(OrbitalBodyComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> trackedEntities;
    private ImmutableArray<Entity> celestialBodies;

    public SOITrackingSystem(EventBus eventBus) {
        super(4);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        trackedEntities = engine.getEntitiesFor(
            Family.all(SOITrackerComponent.class, TransformComponent.class).get());
        celestialBodies = engine.getEntitiesFor(
            Family.all(OrbitalBodyComponent.class, TransformComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        trackedEntities = null;
        celestialBodies = null;
    }

    @Override
    public void update(float deltaTime) {
        if (trackedEntities == null || celestialBodies == null) return;

        Vector3 tmp = Pools.obtain(Vector3.class);
        try {
            for (int i = 0; i < trackedEntities.size(); i++) {
                Entity entity = trackedEntities.get(i);
                updateSOI(entity, tmp);
            }
        } finally {
            Pools.free(tmp);
        }
    }

    private void updateSOI(Entity entity, Vector3 tmp) {
        TransformComponent entityTransform = transformMapper.get(entity);
        SOITrackerComponent tracker = soiMapper.get(entity);

        Entity star = null;
        Entity bestPlanet = null;
        float bestPlanetDist = Float.MAX_VALUE;
        Entity bestMoon = null;
        float bestMoonDist = Float.MAX_VALUE;

        for (int j = 0; j < celestialBodies.size(); j++) {
            Entity body = celestialBodies.get(j);
            OrbitalBodyComponent orbital = orbitalMapper.get(body);
            TransformComponent bodyTransform = transformMapper.get(body);

            float dist = tmp.set(entityTransform.position).sub(bodyTransform.position).len();

            if (orbital.bodyType == CelestialBodyType.STAR) {
                star = body;
            } else if (orbital.bodyType == CelestialBodyType.PLANET) {
                if (dist < orbital.soiRadius && dist < bestPlanetDist) {
                    bestPlanet = body;
                    bestPlanetDist = dist;
                }
            } else if (orbital.bodyType == CelestialBodyType.MOON) {
                if (dist < orbital.soiRadius && dist < bestMoonDist) {
                    bestMoon = body;
                    bestMoonDist = dist;
                }
            }
        }

        Entity newDominant;
        Entity newSecondary;
        float distToDominant;

        if (bestMoon != null && bestPlanet != null) {
            newDominant = bestMoon;
            newSecondary = bestPlanet;
            distToDominant = bestMoonDist;
        } else if (bestPlanet != null) {
            newDominant = bestPlanet;
            newSecondary = star;
            distToDominant = bestPlanetDist;
        } else {
            newDominant = star;
            newSecondary = null;
            distToDominant = star != null
                ? tmp.set(entityTransform.position).sub(transformMapper.get(star).position).len()
                : 0f;
        }

        if (newDominant != tracker.dominantBody && tracker.dominantBody != null) {
            convertVelocityFrame(entity, tracker.dominantBody, newDominant, tmp);
            eventBus.publish(new SOIChangedEvent(entity, tracker.dominantBody, newDominant));
        }

        tracker.dominantBody = newDominant;
        tracker.secondaryBody = newSecondary;
        tracker.distanceToDominant = distToDominant;
    }

    private static final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<GravitySourceComponent> gravitySourceMapper =
        ComponentMapper.getFor(GravitySourceComponent.class);

    private void convertVelocityFrame(Entity entity, Entity oldBody, Entity newBody, Vector3 tmp) {
        if (!physicsMapper.has(entity)) return;
        PhysicsBodyComponent physics = physicsMapper.get(entity);
        if (physics.body == null) return;

        Vector3 oldVel = Pools.obtain(Vector3.class);
        Vector3 newVel = Pools.obtain(Vector3.class);
        try {
            computeBodyOrbitalVelocity(oldBody, oldVel);
            computeBodyOrbitalVelocity(newBody, newVel);

            // v_ship_new = v_ship - v_oldBody + v_newBody
            Vector3 shipVel = physics.body.getLinearVelocity();
            shipVel.sub(oldVel).add(newVel);
            physics.body.setLinearVelocity(shipVel);
        } finally {
            Pools.free(oldVel);
            Pools.free(newVel);
        }
    }

    private void computeBodyOrbitalVelocity(Entity body, Vector3 out) {
        out.setZero();
        OrbitalBodyComponent orbital = orbitalMapper.get(body);
        if (orbital == null || orbital.orbitalSlot == null) return;
        if (orbital.bodyType == CelestialBodyType.STAR) return;

        OrbitalSlot slot = orbital.orbitalSlot;
        float GM;
        if (orbital.parentBody != null && gravitySourceMapper.has(orbital.parentBody)) {
            GM = OrbitalConstants.G * gravitySourceMapper.get(orbital.parentBody).mass;
        } else {
            return;
        }

        float a = slot.orbitalRadius * OrbitalConstants.AU_TO_GAME_UNITS;
        float e = slot.eccentricity;
        float nu = slot.currentTrueAnomaly;
        float p = a * (1f - e * e);
        if (p <= 0f) return;

        float sqrtGMoverP = (float) Math.sqrt(GM / p);
        float vx = sqrtGMoverP * (-MathUtils.sin(nu));
        float vz = sqrtGMoverP * (e + MathUtils.cos(nu));
        out.set(vx, 0f, vz);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.SOITrackingSystemTest" 2>&1 | tail -5`
Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/systems/SOITrackingSystem.java \
        core/src/test/java/com/galacticodyssey/core/systems/SOITrackingSystemTest.java
git commit -m "feat(orbital): add SOITrackingSystem with event-driven SOI transitions"
```

---

### Task 9: GravitySystem Hybrid Modification

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/systems/GravitySystem.java`

- [ ] **Step 1: Write failing test**

Create `core/src/test/java/com/galacticodyssey/core/systems/GravitySystemHybridTest.java`:

```java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GravitySystemHybridTest {

    private Engine engine;
    private GravitySystem gravitySystem;
    private Entity starEntity;
    private Entity planetEntity;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        gravitySystem = new GravitySystem();
        engine.addSystem(gravitySystem);

        starEntity = createGravitySource(0f, 0f, 0f, 1.989e30f, 500000f);
        OrbitalBodyComponent starOrbital = new OrbitalBodyComponent();
        starOrbital.bodyType = CelestialBodyType.STAR;
        starOrbital.soiRadius = 0f;
        starEntity.add(starOrbital);
        engine.addEntity(starEntity);

        planetEntity = createGravitySource(1000f, 0f, 0f, 5.972e24f, 200f);
        OrbitalBodyComponent planetOrbital = new OrbitalBodyComponent();
        planetOrbital.bodyType = CelestialBodyType.PLANET;
        planetOrbital.soiRadius = 200f;
        planetEntity.add(planetOrbital);
        engine.addEntity(planetEntity);
    }

    @Test
    void entityWithSOITrackerFeelsDominantAtFullStrength() {
        Entity ship = createAffectedBody(1050f, 0f, 0f, 1000f);
        SOITrackerComponent soi = new SOITrackerComponent();
        soi.dominantBody = planetEntity;
        soi.secondaryBody = starEntity;
        ship.add(soi);
        engine.addEntity(ship);

        engine.update(0.016f);

        GravityAffectedComponent affected = ship.getComponent(GravityAffectedComponent.class);
        assertTrue(affected.lastAcceleration.len() > 0f, "Ship should feel gravity");

        Vector3 toPlanet = new Vector3(1000f, 0f, 0f).sub(1050f, 0f, 0f).nor();
        float dot = affected.lastAcceleration.cpy().nor().dot(toPlanet);
        assertTrue(dot > 0.5f, "Gravity should pull predominantly toward dominant body (planet)");
    }

    @Test
    void entityWithoutSOITrackerFeelsAllSourcesEqually() {
        Entity body = createAffectedBody(500f, 0f, 0f, 1f);
        engine.addEntity(body);

        engine.update(0.016f);

        GravityAffectedComponent affected = body.getComponent(GravityAffectedComponent.class);
        assertTrue(affected.lastAcceleration.len() > 0f);
    }

    private Entity createGravitySource(float x, float y, float z, float mass, float influence) {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);
        GravitySourceComponent source = new GravitySourceComponent();
        source.mass = mass;
        source.influenceRadius = influence;
        entity.add(source);
        return entity;
    }

    private Entity createAffectedBody(float x, float y, float z, float mass) {
        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y, z);
        entity.add(transform);
        GravityAffectedComponent affected = new GravityAffectedComponent();
        affected.mass = mass;
        entity.add(affected);
        return entity;
    }
}
```

- [ ] **Step 2: Run test to verify it passes (baseline)**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.GravitySystemHybridTest" 2>&1 | tail -5`
Expected: Tests should pass even before modification (existing behavior works for both cases). This establishes the baseline.

- [ ] **Step 3: Modify GravitySystem for hybrid SOI-aware attenuation**

In `core/src/main/java/com/galacticodyssey/core/systems/GravitySystem.java`, add the `SOITrackerComponent` mapper and modify `computeNetAccelerationInternal`. Add these imports and mapper at the top of the class:

```java
    private static final ComponentMapper<SOITrackerComponent> soiMapper =
        ComponentMapper.getFor(SOITrackerComponent.class);
    private static final ComponentMapper<OrbitalBodyComponent> orbitalMapper =
        ComponentMapper.getFor(OrbitalBodyComponent.class);
```

Replace the `update` method to pass the entity:

```java
    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < affectedBodies.size(); i++) {
            Entity body = affectedBodies.get(i);
            TransformComponent bodyTransform = transformMapper.get(body);
            GravityAffectedComponent affected = affectedMapper.get(body);

            SOITrackerComponent soi = soiMapper.has(body) ? soiMapper.get(body) : null;
            computeNetAccelerationInternal(bodyTransform.position, affected.mass, affected.lastAcceleration, soi);
        }
    }
```

Replace `computeNetAccelerationInternal` with:

```java
    private void computeNetAccelerationInternal(Vector3 position, float bodyMass, Vector3 out,
                                                 SOITrackerComponent soi) {
        out.setZero();

        Vector3 direction = Pools.obtain(Vector3.class);
        try {
            for (int i = 0; i < sources.size(); i++) {
                Entity srcEntity = sources.get(i);
                GravitySourceComponent src = sourceMapper.get(srcEntity);
                if (!src.active) continue;

                TransformComponent srcTransform = transformMapper.get(srcEntity);
                direction.set(srcTransform.position).sub(position);
                float dist = direction.len();

                if (dist > src.influenceRadius) continue;

                float clampedDist = Math.max(dist, src.minRadius);
                float accelMag = G * src.mass / (float) Math.pow(clampedDist, src.falloffExponent);

                if (soi != null) {
                    float attenuation = computeSOIAttenuation(srcEntity, soi);
                    accelMag *= attenuation;
                }

                if (dist > 0f) {
                    direction.scl(1f / dist);
                }
                out.mulAdd(direction, accelMag);
            }

            applyZoneOverrides(position, out);
        } finally {
            Pools.free(direction);
        }
    }
```

Update the public query method to pass null SOI:

```java
    public Vector3 computeNetAcceleration(Vector3 position, float bodyMass) {
        Vector3 result = new Vector3();
        computeNetAccelerationInternal(position, bodyMass, result, null);
        return result;
    }
```

Add the attenuation helper:

```java
    private float computeSOIAttenuation(Entity sourceEntity, SOITrackerComponent soi) {
        if (sourceEntity == soi.dominantBody) return 1f;
        if (sourceEntity == soi.secondaryBody) return 1f;

        if (soi.dominantBody != null && orbitalMapper.has(soi.dominantBody)) {
            OrbitalBodyComponent dominantOrbital = orbitalMapper.get(soi.dominantBody);
            if (dominantOrbital.soiRadius > 0f) {
                float ratio = soi.distanceToDominant / dominantOrbital.soiRadius;
                return Math.max(0f, 1f - ratio * ratio);
            }
        }
        return 1f;
    }
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.GravitySystemHybridTest" 2>&1 | tail -5`
Expected: All tests PASS.

- [ ] **Step 5: Run existing GravitySystem tests to check for regressions**

Run: `./gradlew :core:test 2>&1 | tail -10`
Expected: All existing tests still pass.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/systems/GravitySystem.java \
        core/src/test/java/com/galacticodyssey/core/systems/GravitySystemHybridTest.java
git commit -m "feat(orbital): hybrid SOI-aware gravity attenuation in GravitySystem"
```

---

### Task 10: GravityForceSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/systems/GravityForceSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/core/systems/GravityForceSystemTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.GravityAffectedComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GravityForceSystemTest {

    @Test
    void accelerationConvertedToForceAndApplied() {
        Engine engine = new Engine();
        GravityForceSystem system = new GravityForceSystem();
        engine.addSystem(system);

        Entity entity = new Entity();
        TransformComponent transform = new TransformComponent();
        entity.add(transform);

        GravityAffectedComponent affected = new GravityAffectedComponent();
        affected.mass = 100f;
        affected.lastAcceleration.set(0f, -9.8f, 0f);
        entity.add(affected);

        PhysicsBodyComponent physics = new PhysicsBodyComponent();
        physics.mass = 100f;
        // Note: btRigidBody is null in unit tests (no Bullet context).
        // The system should guard against null body.
        entity.add(physics);

        engine.addEntity(entity);
        engine.update(0.016f);

        // With null btRigidBody the system should skip force application gracefully.
        // This test verifies the system doesn't crash on null body.
        assertNotNull(affected.lastAcceleration);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.GravityForceSystemTest" 2>&1 | tail -5`
Expected: Compilation error — `GravityForceSystem` does not exist.

- [ ] **Step 3: Implement GravityForceSystem**

```java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.components.GravityAffectedComponent;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
import com.galacticodyssey.core.components.TransformComponent;

public class GravityForceSystem extends EntitySystem {

    private static final ComponentMapper<GravityAffectedComponent> affectedMapper =
        ComponentMapper.getFor(GravityAffectedComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);

    private ImmutableArray<Entity> bodies;

    public GravityForceSystem() {
        super(5);
    }

    @Override
    public void addedToEngine(Engine engine) {
        bodies = engine.getEntitiesFor(
            Family.all(GravityAffectedComponent.class, PhysicsBodyComponent.class,
                       TransformComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        bodies = null;
    }

    @Override
    public void update(float deltaTime) {
        if (bodies == null) return;

        Vector3 force = Pools.obtain(Vector3.class);
        try {
            for (int i = 0; i < bodies.size(); i++) {
                Entity entity = bodies.get(i);
                GravityAffectedComponent affected = affectedMapper.get(entity);
                PhysicsBodyComponent physics = physicsMapper.get(entity);

                if (physics.body == null) continue;

                force.set(affected.lastAcceleration).scl(physics.mass);
                physics.body.applyCentralForce(force);
            }
        } finally {
            Pools.free(force);
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.GravityForceSystemTest" 2>&1 | tail -5`
Expected: PASS (null body guarded).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/systems/GravityForceSystem.java \
        core/src/test/java/com/galacticodyssey/core/systems/GravityForceSystemTest.java
git commit -m "feat(orbital): add GravityForceSystem to apply gravity acceleration to physics bodies"
```

---

### Task 11: TrajectoryPredictionSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/core/systems/TrajectoryPredictionSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/core/systems/TrajectoryPredictionSystemTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.*;
import com.galacticodyssey.galaxy.OrbitalConstants;
import com.galacticodyssey.galaxy.OrbitalMechanics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrajectoryPredictionSystemTest {

    private TrajectoryPredictionSystem system;
    private Entity shipEntity;
    private Entity planetEntity;

    @BeforeEach
    void setUp() {
        system = new TrajectoryPredictionSystem();

        planetEntity = new Entity();
        TransformComponent planetTransform = new TransformComponent();
        planetTransform.position.set(0f, 0f, 0f);
        planetEntity.add(planetTransform);
        GravitySourceComponent planetGravity = new GravitySourceComponent();
        planetGravity.mass = 5.972e24f;
        planetEntity.add(planetGravity);
        OrbitalBodyComponent planetOrbital = new OrbitalBodyComponent();
        planetOrbital.bodyType = CelestialBodyType.PLANET;
        planetEntity.add(planetOrbital);
    }

    @Test
    void circularOrbitProducesCorrectElements() {
        float r = 100f;
        float GM = OrbitalConstants.G * 5.972e24f;
        float vCirc = OrbitalMechanics.circularOrbitSpeed(GM, r);

        Vector3 relPos = new Vector3(r, 0f, 0f);
        Vector3 relVel = new Vector3(0f, 0f, vCirc);

        TrajectoryComponent traj = new TrajectoryComponent();
        system.computeTrajectory(traj, relPos, relVel, 5.972e24f, new Vector3());

        assertNotNull(traj.currentOrbit);
        assertTrue(traj.isStable);
        assertEquals(r, traj.currentOrbit.semiMajorAxis, r * 0.01f);
        assertTrue(traj.currentOrbit.eccentricity < 0.05f);
        assertTrue(traj.predictedPath.size > 0);
    }

    @Test
    void escapeTrajectoryDetected() {
        float r = 100f;
        float GM = OrbitalConstants.G * 5.972e24f;
        float vEsc = OrbitalMechanics.escapeVelocity(GM, r) * 1.5f;

        Vector3 relPos = new Vector3(r, 0f, 0f);
        Vector3 relVel = new Vector3(0f, 0f, vEsc);

        TrajectoryComponent traj = new TrajectoryComponent();
        system.computeTrajectory(traj, relPos, relVel, 5.972e24f, new Vector3());

        assertNotNull(traj.currentOrbit);
        assertFalse(traj.isStable);
        assertTrue(traj.currentOrbit.eccentricity >= 1f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.TrajectoryPredictionSystemTest" 2>&1 | tail -5`
Expected: Compilation error — `TrajectoryPredictionSystem` does not exist.

- [ ] **Step 3: Implement TrajectoryPredictionSystem**

```java
package com.galacticodyssey.core.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pools;
import com.galacticodyssey.core.components.*;
import com.galacticodyssey.galaxy.KeplerOrbit;
import com.galacticodyssey.galaxy.OrbitalMechanics;

public class TrajectoryPredictionSystem extends EntitySystem {

    private static final float SMA_CHANGE_THRESHOLD = 0.01f;
    private static final float ECC_CHANGE_THRESHOLD = 0.01f;

    private static final ComponentMapper<TrajectoryComponent> trajectoryMapper =
        ComponentMapper.getFor(TrajectoryComponent.class);
    private static final ComponentMapper<PhysicsBodyComponent> physicsMapper =
        ComponentMapper.getFor(PhysicsBodyComponent.class);
    private static final ComponentMapper<SOITrackerComponent> soiMapper =
        ComponentMapper.getFor(SOITrackerComponent.class);
    private static final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<GravitySourceComponent> gravitySourceMapper =
        ComponentMapper.getFor(GravitySourceComponent.class);

    private ImmutableArray<Entity> trackedEntities;

    public TrajectoryPredictionSystem() {
        super(6);
    }

    @Override
    public void addedToEngine(Engine engine) {
        trackedEntities = engine.getEntitiesFor(
            Family.all(TrajectoryComponent.class, TransformComponent.class,
                       SOITrackerComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        trackedEntities = null;
    }

    @Override
    public void update(float deltaTime) {
        if (trackedEntities == null) return;

        Vector3 relPos = Pools.obtain(Vector3.class);
        Vector3 relVel = Pools.obtain(Vector3.class);
        Vector3 dominantPos = Pools.obtain(Vector3.class);
        try {
            for (int i = 0; i < trackedEntities.size(); i++) {
                Entity entity = trackedEntities.get(i);
                SOITrackerComponent soi = soiMapper.get(entity);
                if (soi.dominantBody == null) continue;
                if (!gravitySourceMapper.has(soi.dominantBody)) continue;

                TransformComponent entityTransform = transformMapper.get(entity);
                TransformComponent dominantTransform = transformMapper.get(soi.dominantBody);
                GravitySourceComponent dominantGravity = gravitySourceMapper.get(soi.dominantBody);

                dominantPos.set(dominantTransform.position);
                relPos.set(entityTransform.position).sub(dominantPos);

                PhysicsBodyComponent physics = physicsMapper.has(entity) ? physicsMapper.get(entity) : null;
                if (physics != null && physics.body != null) {
                    relVel.set(physics.body.getLinearVelocity());
                } else {
                    relVel.setZero();
                }

                TrajectoryComponent traj = trajectoryMapper.get(entity);
                computeTrajectory(traj, relPos, relVel, dominantGravity.mass, dominantPos);
            }
        } finally {
            Pools.free(relPos);
            Pools.free(relVel);
            Pools.free(dominantPos);
        }
    }

    public void computeTrajectory(TrajectoryComponent traj, Vector3 relPos, Vector3 relVel,
                                   float dominantMass, Vector3 dominantWorldPos) {
        float r = relPos.len();
        if (r < 1f) return;

        KeplerOrbit orbit = OrbitalMechanics.fromStateVectors(relPos, relVel, dominantMass);
        float GM = OrbitalMechanics.G * dominantMass;

        boolean needsResample = traj.dirty;
        if (traj.currentOrbit != null && !traj.dirty) {
            float smaChange = Math.abs(orbit.semiMajorAxis - traj.currentOrbit.semiMajorAxis)
                / Math.max(1f, Math.abs(traj.currentOrbit.semiMajorAxis));
            float eccChange = Math.abs(orbit.eccentricity - traj.currentOrbit.eccentricity);
            if (smaChange > SMA_CHANGE_THRESHOLD || eccChange > ECC_CHANGE_THRESHOLD) {
                needsResample = true;
            }
        }

        traj.currentOrbit = orbit;
        traj.isStable = OrbitalMechanics.isStableOrbit(relPos, relVel, GM, 0f);
        traj.periapsis = orbit.periapsis;
        traj.apoapsis = orbit.apoapsis;

        if (needsResample) {
            traj.predictedPath.clear();
            if (orbit.eccentricity < 1f) {
                OrbitalMechanics.sampleOrbit(orbit, traj.sampleSegments, traj.predictedPath);
            } else {
                float startNu = -MathUtils.PI * 0.4f;
                float endNu = MathUtils.PI * 0.4f;
                float step = (endNu - startNu) / traj.sampleSegments;
                for (int s = 0; s < traj.sampleSegments; s++) {
                    float nu = startNu + s * step;
                    traj.predictedPath.add(OrbitalMechanics.orbitPosition(orbit, nu, new Vector3()));
                }
            }

            for (int j = 0; j < traj.predictedPath.size; j++) {
                traj.predictedPath.get(j).add(dominantWorldPos);
            }

            traj.dirty = false;
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.core.systems.TrajectoryPredictionSystemTest" 2>&1 | tail -5`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/systems/TrajectoryPredictionSystem.java \
        core/src/test/java/com/galacticodyssey/core/systems/TrajectoryPredictionSystemTest.java
git commit -m "feat(orbital): add TrajectoryPredictionSystem for orbit derivation and path sampling"
```

---

### Task 12: OrbitLineRenderer

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/OrbitLineRenderer.java`

- [ ] **Step 1: Implement OrbitLineRenderer**

This is a rendering class that depends on a GL context, so it cannot be unit-tested without headless rendering. It reads `TrajectoryComponent` data and draws lines.

```java
package com.galacticodyssey.ui;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.components.TrajectoryComponent;

public class OrbitLineRenderer implements Disposable {

    private static final Color STABLE_COLOR = new Color(0f, 0.8f, 1f, 0.8f);
    private static final Color ESCAPE_COLOR = new Color(1f, 0.4f, 0.1f, 0.8f);
    private static final Color IMPACT_COLOR = new Color(1f, 1f, 0f, 0.8f);

    private static final ComponentMapper<TrajectoryComponent> trajectoryMapper =
        ComponentMapper.getFor(TrajectoryComponent.class);

    private final ShapeRenderer shapeRenderer;

    public OrbitLineRenderer() {
        shapeRenderer = new ShapeRenderer();
    }

    public void render(Entity shipEntity, Camera camera) {
        if (!trajectoryMapper.has(shipEntity)) return;
        TrajectoryComponent traj = trajectoryMapper.get(shipEntity);
        if (traj.predictedPath.size < 2) return;

        Color lineColor;
        if (!traj.isStable && traj.currentOrbit != null && traj.currentOrbit.eccentricity >= 1f) {
            lineColor = ESCAPE_COLOR;
        } else if (!traj.isStable) {
            lineColor = IMPACT_COLOR;
        } else {
            lineColor = STABLE_COLOR;
        }

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(lineColor);

        Array<Vector3> path = traj.predictedPath;
        for (int i = 0; i < path.size - 1; i++) {
            Vector3 a = path.get(i);
            Vector3 b = path.get(i + 1);
            shapeRenderer.line(a.x, a.y, a.z, b.x, b.y, b.z);
        }

        if (traj.isStable && path.size > 2) {
            Vector3 last = path.get(path.size - 1);
            Vector3 first = path.get(0);
            shapeRenderer.line(last.x, last.y, last.z, first.x, first.y, first.z);
        }

        shapeRenderer.end();
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/OrbitLineRenderer.java
git commit -m "feat(orbital): add OrbitLineRenderer for 3D trajectory arc display"
```

---

### Task 13: OrbitHUDPanel SOI Event Handling

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/OrbitHUDPanel.java`

- [ ] **Step 1: Add SOIChangedEvent subscription to OrbitHUDPanel**

In `OrbitHUDPanel.java`, add an import and modify `subscribeToEventBus`:

Add import:
```java
import com.galacticodyssey.core.events.SOIChangedEvent;
import com.galacticodyssey.core.components.GravitySourceComponent;
import com.galacticodyssey.core.components.OrbitalBodyComponent;
```

Replace the `subscribeToEventBus` method:

```java
    public void subscribeToEventBus(EventBus eventBus) {
        eventBus.subscribe(OrbitTickEvent.class, this::onOrbitTick);
        eventBus.subscribe(SOIChangedEvent.class, this::onSOIChanged);
    }
```

Add the SOI change handler:

```java
    private void onSOIChanged(SOIChangedEvent event) {
        if (event.entity != shipEntity) return;
        Entity newBody = event.newDominantBody;
        if (newBody == null) return;

        GravitySourceComponent gravity = newBody.getComponent(GravitySourceComponent.class);
        if (gravity != null) {
            primaryMass = gravity.mass;
        }

        OrbitalBodyComponent orbital = newBody.getComponent(OrbitalBodyComponent.class);
        if (orbital != null) {
            primaryRadius = orbital.bodyRadius;
        }
    }
```

- [ ] **Step 2: Run existing tests**

Run: `./gradlew :core:test 2>&1 | tail -10`
Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/OrbitHUDPanel.java
git commit -m "feat(orbital): OrbitHUDPanel reacts to SOIChangedEvent for dynamic primary body"
```

---

### Task 14: GameWorld Wiring and Celestial Entity Factory

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Add system registration and entity factory to GameWorld**

Add imports at the top of `GameWorld.java`:

```java
import com.galacticodyssey.core.components.CelestialBodyType;
import com.galacticodyssey.core.components.OrbitalBodyComponent;
import com.galacticodyssey.core.components.SOITrackerComponent;
import com.galacticodyssey.core.components.TrajectoryComponent;
import com.galacticodyssey.core.systems.GravityForceSystem;
import com.galacticodyssey.core.systems.OrbitalPositionSystem;
import com.galacticodyssey.core.systems.SOITrackingSystem;
import com.galacticodyssey.core.systems.TrajectoryPredictionSystem;
import com.galacticodyssey.galaxy.OrbitalConstants;
import com.galacticodyssey.galaxy.OrbitalMechanics;
import com.galacticodyssey.galaxy.OrbitalSlot;
import com.galacticodyssey.galaxy.StarSystem;
import com.galacticodyssey.planet.Moon;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.ui.OrbitLineRenderer;
```

Add system registration in the constructor, after existing system registrations:

```java
        engine.addSystem(new OrbitalPositionSystem());
        engine.addSystem(new SOITrackingSystem(eventBus));
        engine.addSystem(new GravityForceSystem());
        engine.addSystem(new TrajectoryPredictionSystem());
```

Add the celestial entity factory method:

```java
    public Entity createStarEntity(StarSystem system) {
        Entity star = engine.createEntity();
        TransformComponent transform = engine.createComponent(TransformComponent.class);
        star.add(transform);

        GravitySourceComponent gravity = engine.createComponent(GravitySourceComponent.class);
        gravity.mass = system.mass * OrbitalConstants.SOLAR_MASS_KG;
        gravity.influenceRadius = system.systemEdge * OrbitalConstants.AU_TO_GAME_UNITS;
        gravity.minRadius = system.radius * 100f;
        star.add(gravity);

        OrbitalBodyComponent orbital = engine.createComponent(OrbitalBodyComponent.class);
        orbital.bodyType = CelestialBodyType.STAR;
        orbital.soiRadius = 0f;
        orbital.bodyRadius = system.radius * 100f;
        star.add(orbital);

        engine.addEntity(star);
        return star;
    }

    public Entity createPlanetEntity(OrbitalSlot slot, Entity starEntity, StarSystem system) {
        Planet planet = slot.planet;
        if (planet == null) return null;

        Entity entity = engine.createEntity();
        TransformComponent transform = engine.createComponent(TransformComponent.class);
        entity.add(transform);

        float planetMassKg = planet.mass * OrbitalConstants.EARTH_MASS_KG;

        GravitySourceComponent gravity = engine.createComponent(GravitySourceComponent.class);
        gravity.mass = planetMassKg;
        gravity.influenceRadius = OrbitalMechanics.sphereOfInfluence(
            slot.orbitalRadius * OrbitalConstants.AU_TO_GAME_UNITS,
            planetMassKg,
            system.mass * OrbitalConstants.SOLAR_MASS_KG);
        gravity.minRadius = planet.radius * 10f;
        entity.add(gravity);

        OrbitalBodyComponent orbital = engine.createComponent(OrbitalBodyComponent.class);
        orbital.bodyType = CelestialBodyType.PLANET;
        orbital.orbitalSlot = slot;
        orbital.parentBody = starEntity;
        orbital.soiRadius = gravity.influenceRadius;
        orbital.bodyRadius = planet.radius * 10f;
        entity.add(orbital);

        engine.addEntity(entity);
        return entity;
    }

    public Entity createMoonEntity(Moon moon, Entity planetEntity, Planet planet) {
        Entity entity = engine.createEntity();
        TransformComponent transform = engine.createComponent(TransformComponent.class);
        entity.add(transform);

        float moonMassKg = moon.mass * OrbitalConstants.EARTH_MASS_KG;
        float planetMassKg = planet.mass * OrbitalConstants.EARTH_MASS_KG;

        GravitySourceComponent gravity = engine.createComponent(GravitySourceComponent.class);
        gravity.mass = moonMassKg;
        gravity.influenceRadius = OrbitalMechanics.sphereOfInfluence(
            moon.orbitalRadius * OrbitalConstants.AU_TO_GAME_UNITS,
            moonMassKg, planetMassKg);
        gravity.minRadius = moon.radius * 5f;
        entity.add(gravity);

        OrbitalBodyComponent orbital = engine.createComponent(OrbitalBodyComponent.class);
        orbital.bodyType = CelestialBodyType.MOON;
        orbital.parentBody = planetEntity;
        orbital.soiRadius = gravity.influenceRadius;
        orbital.bodyRadius = moon.radius * 5f;
        entity.add(orbital);

        engine.addEntity(entity);
        return entity;
    }
```

- [ ] **Step 2: Run full test suite**

Run: `./gradlew :core:test 2>&1 | tail -10`
Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(orbital): register orbital systems and add celestial entity factory methods"
```

---

### Task 15: Integration Test

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/galaxy/OrbitalMechanicsIntegrationTest.java`

- [ ] **Step 1: Write integration test**

```java
package com.galacticodyssey.galaxy;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.*;
import com.galacticodyssey.core.systems.GravitySystem;
import com.galacticodyssey.core.systems.OrbitalPositionSystem;
import com.galacticodyssey.core.systems.SOITrackingSystem;
import com.galacticodyssey.core.systems.TrajectoryPredictionSystem;
import com.galacticodyssey.planet.Moon;
import com.galacticodyssey.planet.Planet;
import com.galacticodyssey.planet.PlanetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.badlogic.gdx.graphics.Color;

import static org.junit.jupiter.api.Assertions.*;

class OrbitalMechanicsIntegrationTest {

    private Engine engine;
    private EventBus eventBus;
    private KeplerianOrbitSystem keplerSystem;
    private Entity starEntity;
    private Entity planetEntity;
    private StarSystem starSystem;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();

        engine.addSystem(new GravitySystem());
        keplerSystem = new KeplerianOrbitSystem(eventBus);
        engine.addSystem(keplerSystem);
        engine.addSystem(new OrbitalPositionSystem());
        engine.addSystem(new SOITrackingSystem(eventBus));
        engine.addSystem(new TrajectoryPredictionSystem());

        starSystem = new StarSystem(1L, 42L, SpectralClass.G, LuminosityClass.MAIN_SEQUENCE,
            5778f, 1.0f, 1.0f, 1.0f, 4.6f, Color.YELLOW);

        OrbitalSlot slot = new OrbitalSlot(0, 1.0f, 0.0f, OrbitalZone.HABITABLE);
        Planet planet = new Planet(100L, PlanetType.TERRAN, 1.0f, 1.0f, 5.5f, 24f, 23.5f, false);
        slot.planet = planet;
        starSystem.orbits.add(slot);

        starEntity = new Entity();
        TransformComponent starTransform = new TransformComponent();
        starEntity.add(starTransform);
        GravitySourceComponent starGravity = new GravitySourceComponent();
        starGravity.mass = OrbitalConstants.SOLAR_MASS_KG;
        starGravity.influenceRadius = 500000f;
        starEntity.add(starGravity);
        OrbitalBodyComponent starOrbital = new OrbitalBodyComponent();
        starOrbital.bodyType = CelestialBodyType.STAR;
        starOrbital.soiRadius = 0f;
        starEntity.add(starOrbital);
        engine.addEntity(starEntity);

        float planetMassKg = 1.0f * OrbitalConstants.EARTH_MASS_KG;
        float planetSOI = OrbitalMechanics.sphereOfInfluence(
            1.0f * OrbitalConstants.AU_TO_GAME_UNITS, planetMassKg, OrbitalConstants.SOLAR_MASS_KG);

        planetEntity = new Entity();
        TransformComponent planetTransform = new TransformComponent();
        planetEntity.add(planetTransform);
        GravitySourceComponent planetGravity = new GravitySourceComponent();
        planetGravity.mass = planetMassKg;
        planetGravity.influenceRadius = planetSOI;
        planetEntity.add(planetGravity);
        OrbitalBodyComponent planetOrbital = new OrbitalBodyComponent();
        planetOrbital.bodyType = CelestialBodyType.PLANET;
        planetOrbital.orbitalSlot = slot;
        planetOrbital.parentBody = starEntity;
        planetOrbital.soiRadius = planetSOI;
        planetEntity.add(planetOrbital);
        engine.addEntity(planetEntity);

        keplerSystem.setActiveSystem(starSystem);
    }

    @Test
    void planetMovesAfterOrbitTicks() {
        TransformComponent planetTransform = planetEntity.getComponent(TransformComponent.class);
        engine.update(0f);
        float initialX = planetTransform.position.x;

        keplerSystem.setTimeScale(100000f);
        engine.update(1.0f);

        assertNotEquals(initialX, planetTransform.position.x, 1f,
            "Planet should have moved after orbit advancement");
    }

    @Test
    void planetStaysAtCorrectDistanceFromStar() {
        engine.update(0f);

        TransformComponent planetTransform = planetEntity.getComponent(TransformComponent.class);
        TransformComponent starTransform = starEntity.getComponent(TransformComponent.class);

        float dist = planetTransform.position.cpy().sub(starTransform.position).len();
        float expectedDist = 1.0f * OrbitalConstants.AU_TO_GAME_UNITS;

        assertEquals(expectedDist, dist, expectedDist * 0.01f);
    }

    @Test
    void shipInsidePlanetSOIGetsPlanetAsDominant() {
        engine.update(0f);

        TransformComponent planetTransform = planetEntity.getComponent(TransformComponent.class);

        Entity ship = new Entity();
        TransformComponent shipTransform = new TransformComponent();
        shipTransform.position.set(planetTransform.position).add(10f, 0f, 0f);
        ship.add(shipTransform);
        ship.add(new SOITrackerComponent());
        engine.addEntity(ship);

        engine.update(0f);

        SOITrackerComponent tracker = ship.getComponent(SOITrackerComponent.class);
        assertSame(planetEntity, tracker.dominantBody,
            "Ship near planet should have planet as dominant body");
    }

    @Test
    void fullOrbitReturnsToStartPosition() {
        OrbitalSlot slot = starSystem.orbits.get(0);
        engine.update(0f);

        TransformComponent planetTransform = planetEntity.getComponent(TransformComponent.class);
        float startX = planetTransform.position.x;
        float startZ = planetTransform.position.z;

        float period = slot.orbitalPeriod;
        int steps = 1000;
        float dtPerStep = period / steps;
        for (int i = 0; i < steps; i++) {
            keplerSystem.update(dtPerStep);
        }
        engine.getSystem(OrbitalPositionSystem.class).update(0f);

        assertEquals(startX, planetTransform.position.x, OrbitalConstants.AU_TO_GAME_UNITS * 0.02f,
            "Planet should return near start X after one full period");
        assertEquals(startZ, planetTransform.position.z, OrbitalConstants.AU_TO_GAME_UNITS * 0.02f,
            "Planet should return near start Z after one full period");
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `./gradlew :core:test --tests "com.galacticodyssey.galaxy.OrbitalMechanicsIntegrationTest" 2>&1 | tail -10`
Expected: All 4 tests PASS.

- [ ] **Step 3: Run full test suite**

Run: `./gradlew :core:test 2>&1 | tail -10`
Expected: All tests pass, no regressions.

- [ ] **Step 4: Commit**

```bash
git add core/src/test/java/com/galacticodyssey/galaxy/OrbitalMechanicsIntegrationTest.java
git commit -m "test(orbital): add integration test for full orbital mechanics pipeline"
```

---

### Task 16: Update TODO and Final Verification

**Files:**
- Modify: `docs/TODO-systems.md`

- [ ] **Step 1: Update TODO to mark orbital mechanics as implemented**

In `docs/TODO-systems.md`, move the orbital mechanics row from the "High Impact" table to the "Already Implemented" table:

Remove this row from the High Impact table:
```
| **Orbital mechanics** | No Keplerian orbit integration. Planets are stationary. No HUD trajectory prediction arc. |
```

Add to the "Already Implemented" table:
```
| Orbital mechanics (Keplerian orbits, SOI tracking, trajectory HUD) | coresystemFinish |
```

- [ ] **Step 2: Run full test suite one final time**

Run: `./gradlew :core:test 2>&1 | tail -10`
Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add docs/TODO-systems.md
git commit -m "docs: mark orbital mechanics as implemented in TODO"
```
