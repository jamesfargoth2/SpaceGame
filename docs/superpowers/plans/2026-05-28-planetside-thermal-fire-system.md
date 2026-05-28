# Planetside Thermal & Fire System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a general per-object temperature simulation plus a wildfire propagation model for planet surfaces — objects heat/cool toward their environment, ignite when too hot, freeze when too cold, and fire spreads across terrain biased by wind/moisture/oxygen.

**Architecture:** Ashley ECS. A data-driven `ThermalMaterial` registry defines thresholds and burn behavior. A pluggable `ThermalEnvironment` resolves ambient temperature, oxygen, and wind from the existing `BiomeMap`/`ClimateData`/`Atmosphere` APIs. Four systems run in a fixed priority band: heat deposition (`HeatSourceSystem` 29, `CombustionSystem` 30, `WildfireSystem` 28) all write into a per-frame `incomingHeat` accumulator **before** `ObjectTemperatureSystem` (31) integrates temperature and resets the accumulator. Ship thermal (`ship/thermal/`) and compartment fire (`ship/lifesupport/`) are untouched.

**Tech Stack:** Java 17, libGDX 1.13 (`com.badlogic.gdx.utils.Json`, `Vector3`, `MathUtils`), Ashley ECS, JUnit 5, Mockito (not required — tests use lightweight stubs).

**Spec:** [docs/superpowers/specs/2026-05-28-planetside-thermal-fire-system-design.md](../specs/2026-05-28-planetside-thermal-fire-system-design.md)

---

## Critical ordering invariant (read before any task)

`incomingHeat` on `TemperatureComponent` is a per-frame Watt accumulator. Every system that **deposits** heat must run before the integrator, and the integrator resets it last:

| Priority | System | Role |
|---|---|---|
| 28 | `WildfireSystem` | grid propagation; deposits burning-cell heat into nearby entities' `incomingHeat` |
| 29 | `HeatSourceSystem` | emitters (flamethrower/lava/cryo) deposit into `incomingHeat` |
| 30 | `CombustionSystem` | burning entities deposit self + neighbor-spread heat; burn fuel; DoT; consume; frozen lifecycle |
| 31 | `ObjectTemperatureSystem` | integrate temp from `incomingHeat` + radiation/conduction toward ambient; emit ignition/freeze/thaw events; **reset `incomingHeat` to 0** |

Events published at 31 (ignition/freeze/thaw) are consumed by `CombustionSystem`'s subscriptions, so the resulting component add/remove happens on the next frame (acceptable 1-frame latency).

---

## Task 1: ThermalMaterial data model + registry

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/ThermalMaterial.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/ThermalMaterialRegistry.java`
- Create: `core/src/main/resources/data/thermal/materials.json`
- Test: `core/src/test/java/com/galacticodyssey/planet/thermal/ThermalMaterialRegistryTest.java`

- [ ] **Step 1: Write the data model**

`ThermalMaterial.java`:
```java
package com.galacticodyssey.planet.thermal;

/** Data-driven thermal/combustion properties for a material. Loaded from data/thermal/materials.json. */
public class ThermalMaterial {
    public String id;
    public String name;
    public float specificHeat;        // J/(kg*K)
    public float emissivity = 0.85f;  // 0..1, radiative coefficient
    public float ignitionPoint;       // K -- at/above this (and enough O2), ignites
    public float freezePoint;         // K -- at/below this, freezes
    public boolean flammable;
    public boolean freezable;
    public float flammability;        // 0..1, weights ignition ease & wildfire spread
    public float combustionEnergy;    // J -- total fuel energy released over a full burn
    public float burnHeatOutput;      // W emitted while burning at intensity 1.0
    public boolean consumedWhenBurnt; // true -> object destroyed when fuel exhausts
    public String charMaterialId;     // material it becomes after burning (nullable)
    public float frozenSpeedMultiplier = 1f; // movement multiplier other systems read when frozen
    public boolean brittleWhenFrozen; // shatters on impact when frozen
}
```

- [ ] **Step 2: Write the failing test**

`ThermalMaterialRegistryTest.java`:
```java
package com.galacticodyssey.planet.thermal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermalMaterialRegistryTest {

    @Test
    void registersAndRetrievesMaterialById() {
        ThermalMaterialRegistry registry = new ThermalMaterialRegistry();
        ThermalMaterial wood = new ThermalMaterial();
        wood.id = "wood";
        wood.ignitionPoint = 573f;
        wood.flammable = true;
        registry.register(wood);

        ThermalMaterial fetched = registry.get("wood");
        assertNotNull(fetched);
        assertEquals(573f, fetched.ignitionPoint, 0.001f);
        assertTrue(fetched.flammable);
        assertNull(registry.get("missing"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.thermal.ThermalMaterialRegistryTest"`
Expected: FAIL — `ThermalMaterialRegistry` does not exist (compilation error).

- [ ] **Step 4: Implement the registry**

`ThermalMaterialRegistry.java` (mirrors `combat/data/WeaponDataRegistry`):
```java
package com.galacticodyssey.planet.thermal;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.Map;

/** Loads and indexes {@link ThermalMaterial} definitions from data/thermal/materials.json. */
public class ThermalMaterialRegistry {
    private final Map<String, ThermalMaterial> materials = new HashMap<>();

    public void loadFromFiles() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal("data/thermal/materials.json"));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            ThermalMaterial m = json.readValue(ThermalMaterial.class, entry);
            materials.put(m.id, m);
        }
    }

    public ThermalMaterial get(String id) { return materials.get(id); }

    public void register(ThermalMaterial m) { materials.put(m.id, m); }
}
```

- [ ] **Step 5: Create the materials data file**

`core/src/main/resources/data/thermal/materials.json`:
```json
[
  { "id": "dry_grass", "name": "Dry Grass", "specificHeat": 1700, "emissivity": 0.9,
    "ignitionPoint": 533, "freezePoint": 0, "flammable": true, "freezable": false,
    "flammability": 0.95, "combustionEnergy": 30000, "burnHeatOutput": 4000,
    "consumedWhenBurnt": true, "charMaterialId": "ash", "brittleWhenFrozen": false },
  { "id": "wood", "name": "Wood", "specificHeat": 1700, "emissivity": 0.9,
    "ignitionPoint": 573, "freezePoint": 0, "flammable": true, "freezable": false,
    "flammability": 0.7, "combustionEnergy": 200000, "burnHeatOutput": 8000,
    "consumedWhenBurnt": true, "charMaterialId": "ash", "brittleWhenFrozen": false },
  { "id": "ash", "name": "Ash", "specificHeat": 840, "emissivity": 0.9,
    "ignitionPoint": 100000, "freezePoint": 0, "flammable": false, "freezable": false,
    "flammability": 0, "combustionEnergy": 0, "burnHeatOutput": 0,
    "consumedWhenBurnt": false, "charMaterialId": null, "brittleWhenFrozen": false },
  { "id": "metal_crate", "name": "Metal Crate", "specificHeat": 500, "emissivity": 0.3,
    "ignitionPoint": 100000, "freezePoint": 0, "flammable": false, "freezable": false,
    "flammability": 0, "combustionEnergy": 0, "burnHeatOutput": 0,
    "consumedWhenBurnt": false, "charMaterialId": null, "brittleWhenFrozen": false },
  { "id": "flesh", "name": "Flesh", "specificHeat": 3500, "emissivity": 0.98,
    "ignitionPoint": 723, "freezePoint": 261, "flammable": true, "freezable": true,
    "flammability": 0.4, "combustionEnergy": 80000, "burnHeatOutput": 3000,
    "consumedWhenBurnt": false, "charMaterialId": null,
    "frozenSpeedMultiplier": 0.3, "brittleWhenFrozen": false },
  { "id": "water_ice", "name": "Water/Ice", "specificHeat": 4186, "emissivity": 0.96,
    "ignitionPoint": 100000, "freezePoint": 273, "flammable": false, "freezable": true,
    "flammability": 0, "combustionEnergy": 0, "burnHeatOutput": 0,
    "consumedWhenBurnt": false, "charMaterialId": null,
    "frozenSpeedMultiplier": 0.0, "brittleWhenFrozen": true }
]
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.thermal.ThermalMaterialRegistryTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/thermal/ThermalMaterial.java \
        core/src/main/java/com/galacticodyssey/planet/thermal/ThermalMaterialRegistry.java \
        core/src/main/resources/data/thermal/materials.json \
        core/src/test/java/com/galacticodyssey/planet/thermal/ThermalMaterialRegistryTest.java
git commit -m "feat(thermal): data-driven ThermalMaterial registry"
```

---

## Task 2: ThermalMath util + TemperatureComponent + ThermalState

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/ThermalMath.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/ThermalState.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/TemperatureComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/thermal/ThermalMathTest.java`

- [ ] **Step 1: Write the failing test**

`ThermalMathTest.java`:
```java
package com.galacticodyssey.planet.thermal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermalMathTest {

    @Test
    void radiativeCoolingIsPositiveWhenHotterThanAmbient() {
        float w = ThermalMath.radiativeCooling(1000f, 300f, 2f, 0.9f);
        assertTrue(w > 0f, "hot body should radiate net heat out");
    }

    @Test
    void radiativeCoolingIsNegativeWhenColderThanAmbient() {
        // Net heat flows IN (negative "cooling") when the body is colder than ambient.
        float w = ThermalMath.radiativeCooling(200f, 300f, 2f, 0.9f);
        assertTrue(w < 0f);
    }

    @Test
    void conductionPullsTowardAmbient() {
        assertTrue(ThermalMath.conduction(400f, 300f, 2f) > 0f);  // hot -> loses heat
        assertTrue(ThermalMath.conduction(200f, 300f, 2f) < 0f);  // cold -> gains heat
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.thermal.ThermalMathTest"`
Expected: FAIL — `ThermalMath` does not exist.

- [ ] **Step 3: Implement ThermalMath**

`ThermalMath.java`:
```java
package com.galacticodyssey.planet.thermal;

/** Stateless thermal physics helpers shared by the planetside thermal systems. */
public final class ThermalMath {
    private ThermalMath() {}

    /** Stefan-Boltzmann constant (W / m^2 K^4). */
    public static final float STEFAN_BOLTZMANN = 5.67e-8f;

    /** Linear conduction/convection coefficient toward ambient (W / m^2 K). */
    public static final float CONDUCTION_COEFF = 5f;

    /** Net radiative power leaving the surface (W). Negative when ambient is hotter. */
    public static float radiativeCooling(float surfaceTemp, float ambientTemp,
                                         float area, float emissivity) {
        float t4 = surfaceTemp * surfaceTemp * surfaceTemp * surfaceTemp;
        float ta4 = ambientTemp * ambientTemp * ambientTemp * ambientTemp;
        return emissivity * STEFAN_BOLTZMANN * area * (t4 - ta4);
    }

    /** Net conductive power leaving the surface toward ambient (W). Negative when ambient is hotter. */
    public static float conduction(float surfaceTemp, float ambientTemp, float area) {
        return CONDUCTION_COEFF * area * (surfaceTemp - ambientTemp);
    }
}
```

- [ ] **Step 4: Implement ThermalState + TemperatureComponent**

`ThermalState.java`:
```java
package com.galacticodyssey.planet.thermal;

public enum ThermalState { NORMAL, BURNING, FROZEN }
```

`TemperatureComponent.java`:
```java
package com.galacticodyssey.planet.thermal;

import com.badlogic.ashley.core.Component;

/** Per-object thermal state for any entity participating in the planetside thermal sim. */
public class TemperatureComponent implements Component {
    public float temperature = 293f;   // K (current)
    public float thermalMass = 1000f;  // J/K (mass * specificHeat)
    public float surfaceArea = 1f;     // m^2 (for radiation/conduction)
    public String materialId;
    public ThermalMaterial material;   // resolved lazily by systems from materialId
    public ThermalState state = ThermalState.NORMAL;
    public float incomingHeat = 0f;    // W -- per-frame accumulator, reset by ObjectTemperatureSystem
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.thermal.ThermalMathTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/thermal/ThermalMath.java \
        core/src/main/java/com/galacticodyssey/planet/thermal/ThermalState.java \
        core/src/main/java/com/galacticodyssey/planet/thermal/TemperatureComponent.java \
        core/src/test/java/com/galacticodyssey/planet/thermal/ThermalMathTest.java
git commit -m "feat(thermal): ThermalMath helpers and TemperatureComponent"
```

---

## Task 3: ThermalEnvironment interface + PlanetSurfaceEnvironment

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/ThermalEnvironment.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/PlanetSurfaceEnvironment.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/thermal/PlanetSurfaceEnvironmentTest.java`

- [ ] **Step 1: Write the interface**

`ThermalEnvironment.java`:
```java
package com.galacticodyssey.planet.thermal;

import com.badlogic.gdx.math.Vector3;

/** Resolves the surrounding conditions an object exchanges heat with. */
public interface ThermalEnvironment {
    /** Ambient temperature (K) the object trends toward at the given local-scene position. */
    float ambientTemp(Vector3 localPos);

    /** Atmospheric O2 fraction (0..1) at the position; gates combustion. */
    float oxygenFraction(Vector3 localPos);

    /** Writes the local-space wind vector (m/s; x=east, z=north) into {@code out}. */
    void wind(Vector3 localPos, Vector3 out);
}
```

- [ ] **Step 2: Write the failing test**

`PlanetSurfaceEnvironmentTest.java`:
```java
package com.galacticodyssey.planet.thermal;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.planet.Atmosphere;
import com.galacticodyssey.planet.AtmoHazard;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.BiomeType;
import com.galacticodyssey.planet.Gas;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanetSurfaceEnvironmentTest {

    private Atmosphere atmosphere() {
        Map<Gas, Float> comp = new EnumMap<>(Gas.class);
        comp.put(Gas.N2, 0.78f);
        comp.put(Gas.O2, 0.21f);
        return new Atmosphere(comp, 101325f, 1f, 255f, 288f, true, EnumSet.noneOf(AtmoHazard.class));
    }

    @Test
    void ambientTempComesFromBiomeMapAtSceneLatLon() {
        // BiomeMap with no ClimateData falls back to surfaceTemp * (1 - 0.4 sin^2 lat).
        BiomeMap biome = new BiomeMap(1L, 0f, 9999f, 0.5f, 300f,
                EnumSet.of(BiomeType.GRASSLAND));
        PlanetSurfaceEnvironment env = new PlanetSurfaceEnvironment(
                biome, atmosphere(), 0f /*lat*/, 0f /*lon*/, 6_371_000f /*radius*/);

        float t = env.ambientTemp(new Vector3(0, 0, 0));
        assertEquals(300f, t, 1f); // at equator sin(lat)=0
    }

    @Test
    void oxygenFractionReadsAtmosphereComposition() {
        PlanetSurfaceEnvironment env = new PlanetSurfaceEnvironment(
                new BiomeMap(1L, 0f, 9999f, 0.5f, 300f, EnumSet.of(BiomeType.GRASSLAND)),
                atmosphere(), 0f, 0f, 6_371_000f);
        assertEquals(0.21f, env.oxygenFraction(new Vector3()), 0.001f);
    }
}
```

(If the `Atmosphere` constructor signature in your tree differs, match the one in `core/src/main/java/com/galacticodyssey/planet/Atmosphere.java`.)

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.thermal.PlanetSurfaceEnvironmentTest"`
Expected: FAIL — `PlanetSurfaceEnvironment` does not exist.

- [ ] **Step 4: Implement PlanetSurfaceEnvironment**

`PlanetSurfaceEnvironment.java`:
```java
package com.galacticodyssey.planet.thermal;

import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.planet.Atmosphere;
import com.galacticodyssey.planet.BiomeMap;
import com.galacticodyssey.planet.Gas;

/**
 * {@link ThermalEnvironment} backed by the active planet's biome/climate/atmosphere data.
 * The active surface scene is a local patch centered on (sceneLat, sceneLon); local-scene
 * metres are converted to small lat/lon offsets via the planet radius so biome variation
 * across the patch is preserved.
 */
public class PlanetSurfaceEnvironment implements ThermalEnvironment {
    private final BiomeMap biomeMap;
    private final Atmosphere atmosphere;
    private final float sceneLatRad;
    private final float sceneLonRad;
    private final float planetRadius;
    private final float cosLat;

    public PlanetSurfaceEnvironment(BiomeMap biomeMap, Atmosphere atmosphere,
                                    float sceneLatRad, float sceneLonRad, float planetRadius) {
        this.biomeMap = biomeMap;
        this.atmosphere = atmosphere;
        this.sceneLatRad = sceneLatRad;
        this.sceneLonRad = sceneLonRad;
        this.planetRadius = Math.max(1f, planetRadius);
        this.cosLat = Math.max(0.01f, (float) Math.cos(sceneLatRad));
    }

    private float latAt(Vector3 p) { return sceneLatRad + p.z / planetRadius; }
    private float lonAt(Vector3 p) { return sceneLonRad + p.x / (planetRadius * cosLat); }

    @Override
    public float ambientTemp(Vector3 localPos) {
        return biomeMap.getTemperature(latAt(localPos), lonAt(localPos));
    }

    @Override
    public float oxygenFraction(Vector3 localPos) {
        if (atmosphere == null) return 0f;
        Float o2 = atmosphere.composition.get(Gas.O2);
        return o2 == null ? 0f : o2;
    }

    @Override
    public void wind(Vector3 localPos, Vector3 out) {
        // Without ClimateData wind sampling here we expose a calm baseline; WildfireSystem
        // tolerates zero wind (no directional bias). Climate-driven wind can be layered in
        // by sampling ClimateData.windU/windV at latAt/lonAt when a ClimateData ref is wired.
        out.set(0f, 0f, 0f);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.thermal.PlanetSurfaceEnvironmentTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/thermal/ThermalEnvironment.java \
        core/src/main/java/com/galacticodyssey/planet/thermal/PlanetSurfaceEnvironment.java \
        core/src/test/java/com/galacticodyssey/planet/thermal/PlanetSurfaceEnvironmentTest.java
git commit -m "feat(thermal): ThermalEnvironment abstraction + planet surface impl"
```

---

## Task 4: Thermal events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/events/IgnitionEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/events/ExtinguishedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/events/FreezeEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/events/ThawEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/events/ObjectConsumedByFireEvent.java`

- [ ] **Step 1: Create the four entity-only events**

Each file follows the existing final-class-with-public-fields event pattern.

`IgnitionEvent.java`:
```java
package com.galacticodyssey.planet.thermal.events;

import com.badlogic.ashley.core.Entity;

public final class IgnitionEvent {
    public final Entity entity;
    public IgnitionEvent(Entity entity) { this.entity = entity; }
}
```

`ExtinguishedEvent.java`:
```java
package com.galacticodyssey.planet.thermal.events;

import com.badlogic.ashley.core.Entity;

public final class ExtinguishedEvent {
    public final Entity entity;
    public ExtinguishedEvent(Entity entity) { this.entity = entity; }
}
```

`FreezeEvent.java`:
```java
package com.galacticodyssey.planet.thermal.events;

import com.badlogic.ashley.core.Entity;

public final class FreezeEvent {
    public final Entity entity;
    public FreezeEvent(Entity entity) { this.entity = entity; }
}
```

`ThawEvent.java`:
```java
package com.galacticodyssey.planet.thermal.events;

import com.badlogic.ashley.core.Entity;

public final class ThawEvent {
    public final Entity entity;
    public ThawEvent(Entity entity) { this.entity = entity; }
}
```

`ObjectConsumedByFireEvent.java`:
```java
package com.galacticodyssey.planet.thermal.events;

import com.badlogic.ashley.core.Entity;

public final class ObjectConsumedByFireEvent {
    public final Entity entity;
    public final String charMaterialId; // nullable -> entity removed
    public ObjectConsumedByFireEvent(Entity entity, String charMaterialId) {
        this.entity = entity;
        this.charMaterialId = charMaterialId;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/thermal/events/
git commit -m "feat(thermal): ignition/extinguish/freeze/thaw/consume events"
```

---

## Task 5: ObjectTemperatureSystem (integration + threshold transitions)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/ObjectTemperatureSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/thermal/ObjectTemperatureSystemTest.java`

- [ ] **Step 1: Write the failing test**

`ObjectTemperatureSystemTest.java`:
```java
package com.galacticodyssey.planet.thermal;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.planet.thermal.events.FreezeEvent;
import com.galacticodyssey.planet.thermal.events.IgnitionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectTemperatureSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private ThermalMaterialRegistry registry;
    private final List<IgnitionEvent> ignitions = new ArrayList<>();
    private final List<FreezeEvent> freezes = new ArrayList<>();

    /** Adjustable stub environment. */
    static class StubEnv implements ThermalEnvironment {
        float ambient = 293f, o2 = 0.21f;
        public float ambientTemp(Vector3 p) { return ambient; }
        public float oxygenFraction(Vector3 p) { return o2; }
        public void wind(Vector3 p, Vector3 out) { out.set(0,0,0); }
    }

    private final StubEnv env = new StubEnv();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        registry = new ThermalMaterialRegistry();
        ThermalMaterial wood = new ThermalMaterial();
        wood.id = "wood"; wood.flammable = true; wood.ignitionPoint = 573f;
        registry.register(wood);
        ThermalMaterial flesh = new ThermalMaterial();
        flesh.id = "flesh"; flesh.freezable = true; flesh.freezePoint = 261f;
        registry.register(flesh);

        engine = new Engine();
        engine.addSystem(new ObjectTemperatureSystem(eventBus, registry, env));
        eventBus.subscribe(IgnitionEvent.class, ignitions::add);
        eventBus.subscribe(FreezeEvent.class, freezes::add);
    }

    private Entity object(String materialId, float temp) {
        Entity e = new Entity();
        TransformComponent tr = new TransformComponent();
        TemperatureComponent t = new TemperatureComponent();
        t.materialId = materialId; t.temperature = temp; t.thermalMass = 1000f; t.surfaceArea = 1f;
        e.add(tr); e.add(t);
        engine.addEntity(e);
        return e;
    }

    @Test
    void incomingHeatRaisesTemperatureThenResets() {
        Entity e = object("wood", 293f);
        TemperatureComponent t = e.getComponent(TemperatureComponent.class);
        t.incomingHeat = 100_000f; // W
        env.ambient = 293f;        // no radiative/conductive drive at ambient
        engine.update(0.1f);
        assertTrue(t.temperature > 293f, "100kW for 0.1s over 1000 J/K should raise temp");
        assertEquals(0f, t.incomingHeat, 0.0001f, "accumulator reset after integration");
    }

    @Test
    void coolsTowardColdAmbient() {
        Entity e = object("wood", 500f);
        TemperatureComponent t = e.getComponent(TemperatureComponent.class);
        env.ambient = 250f;
        engine.update(1.0f);
        assertTrue(t.temperature < 500f);
    }

    @Test
    void ignitesWhenAboveIgnitionPointWithOxygen() {
        Entity e = object("wood", 600f); // above 573 ignition
        env.o2 = 0.21f;
        engine.update(0.016f);
        assertEquals(1, ignitions.size());
        assertEquals(ThermalState.BURNING, e.getComponent(TemperatureComponent.class).state);
    }

    @Test
    void doesNotIgniteWithoutOxygen() {
        Entity e = object("wood", 600f);
        env.o2 = 0.02f; // too low
        engine.update(0.016f);
        assertTrue(ignitions.isEmpty());
    }

    @Test
    void freezesWhenBelowFreezePoint() {
        Entity e = object("flesh", 250f); // below 261 freeze
        engine.update(0.016f);
        assertEquals(1, freezes.size());
        assertEquals(ThermalState.FROZEN, e.getComponent(TemperatureComponent.class).state);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.thermal.ObjectTemperatureSystemTest"`
Expected: FAIL — `ObjectTemperatureSystem` does not exist.

- [ ] **Step 3: Implement the system**

`ObjectTemperatureSystem.java`:
```java
package com.galacticodyssey.planet.thermal;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.planet.thermal.events.FreezeEvent;
import com.galacticodyssey.planet.thermal.events.IgnitionEvent;
import com.galacticodyssey.planet.thermal.events.ThawEvent;

/**
 * Integrates per-object temperature from accumulated {@code incomingHeat} plus radiative
 * and conductive exchange with the {@link ThermalEnvironment}, then emits ignition / freeze
 * / thaw transition events. Resets {@code incomingHeat} after integrating.
 *
 * <p>Priority 31 -- runs after all heat-deposition systems (28-30). See plan ordering table.</p>
 */
public class ObjectTemperatureSystem extends EntitySystem {

    public static final int PRIORITY = 31;
    public static final float O2_MIN_COMBUSTION = 0.10f;
    public static final float THAW_HYSTERESIS = 2f;

    private static final ComponentMapper<TemperatureComponent> TEMP_M =
            ComponentMapper.getFor(TemperatureComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
            ComponentMapper.getFor(TransformComponent.class);

    private final EventBus eventBus;
    private final ThermalMaterialRegistry registry;
    private final ThermalEnvironment environment;
    private ImmutableArray<Entity> entities;

    public ObjectTemperatureSystem(EventBus eventBus, ThermalMaterialRegistry registry,
                                   ThermalEnvironment environment) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.registry = registry;
        this.environment = environment;
    }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(
                Family.all(TemperatureComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float dt) {
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            TemperatureComponent t = TEMP_M.get(e);
            TransformComponent tr = TRANSFORM_M.get(e);
            resolveMaterial(t);

            Vector3 pos = tr.position;
            float ambient = environment.ambientTemp(pos);
            float emissivity = (t.material != null) ? t.material.emissivity : 0.85f;

            float qOut = ThermalMath.radiativeCooling(t.temperature, ambient, t.surfaceArea, emissivity)
                       + ThermalMath.conduction(t.temperature, ambient, t.surfaceArea);
            float netWatts = t.incomingHeat - qOut;
            if (t.thermalMass > 0f) {
                t.temperature += (netWatts / t.thermalMass) * dt;
            }
            t.incomingHeat = 0f;

            checkTransitions(e, t, pos);
        }
    }

    private void resolveMaterial(TemperatureComponent t) {
        if (t.material == null && t.materialId != null) {
            t.material = registry.get(t.materialId);
        }
    }

    private void checkTransitions(Entity e, TemperatureComponent t, Vector3 pos) {
        ThermalMaterial m = t.material;
        if (m == null) return;

        if (t.state == ThermalState.NORMAL) {
            if (m.flammable && t.temperature >= m.ignitionPoint
                    && environment.oxygenFraction(pos) >= O2_MIN_COMBUSTION) {
                t.state = ThermalState.BURNING;
                eventBus.publish(new IgnitionEvent(e));
            } else if (m.freezable && t.temperature <= m.freezePoint) {
                t.state = ThermalState.FROZEN;
                eventBus.publish(new FreezeEvent(e));
            }
        } else if (t.state == ThermalState.FROZEN
                && t.temperature > m.freezePoint + THAW_HYSTERESIS) {
            t.state = ThermalState.NORMAL;
            eventBus.publish(new ThawEvent(e));
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.thermal.ObjectTemperatureSystemTest"`
Expected: PASS (all 5 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/thermal/ObjectTemperatureSystem.java \
        core/src/test/java/com/galacticodyssey/planet/thermal/ObjectTemperatureSystemTest.java
git commit -m "feat(thermal): ObjectTemperatureSystem integration + transitions"
```

---

## Task 6: HeatSourceComponent + HeatSourceSystem (emitters)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/HeatSourceComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/thermal/HeatSourceSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/thermal/HeatSourceSystemTest.java`

- [ ] **Step 1: Implement the component**

`HeatSourceComponent.java`:
```java
package com.galacticodyssey.planet.thermal;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

/** A heat (or cold) emitter: flamethrower cone, lava patch, cryo field. */
public class HeatSourceComponent implements Component {
    public float power = 0f;        // W delivered at the source; negative = cooling
    public float radius = 5f;       // m -- effective range (sphere, or cone length)
    public boolean cone = false;    // false = spherical falloff
    public final Vector3 direction = new Vector3(0f, 0f, -1f); // cone axis (local)
    public float coneHalfAngleRad = 0.5f;
    public float lifetime = -1f;    // < 0 = permanent; otherwise seconds remaining
}
```

- [ ] **Step 2: Write the failing test**

`HeatSourceSystemTest.java`:
```java
package com.galacticodyssey.planet.thermal;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeatSourceSystemTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        engine.addSystem(new HeatSourceSystem());
    }

    private Entity emitter(float x, float power, float radius) {
        Entity e = new Entity();
        TransformComponent tr = new TransformComponent();
        tr.position.set(x, 0, 0);
        HeatSourceComponent h = new HeatSourceComponent();
        h.power = power; h.radius = radius; h.cone = false;
        e.add(tr); e.add(h);
        engine.addEntity(e);
        return e;
    }

    private Entity target(float x) {
        Entity e = new Entity();
        TransformComponent tr = new TransformComponent();
        tr.position.set(x, 0, 0);
        e.add(tr); e.add(new TemperatureComponent());
        engine.addEntity(e);
        return e;
    }

    @Test
    void depositsHeatIntoInRangeTarget() {
        emitter(0f, 10_000f, 5f);
        Entity tgt = target(2f); // within radius
        engine.update(0.016f);
        assertTrue(tgt.getComponent(TemperatureComponent.class).incomingHeat > 0f);
    }

    @Test
    void ignoresOutOfRangeTarget() {
        emitter(0f, 10_000f, 5f);
        Entity tgt = target(20f); // outside radius
        engine.update(0.016f);
        assertEquals(0f, tgt.getComponent(TemperatureComponent.class).incomingHeat, 0.0001f);
    }

    @Test
    void transientEmitterExpires() {
        Entity e = emitter(0f, 10_000f, 5f);
        e.getComponent(HeatSourceComponent.class).lifetime = 0.01f;
        engine.update(0.016f); // consumes lifetime
        Entity tgt = target(2f);
        engine.update(0.016f); // emitter now expired -> no deposit
        assertEquals(0f, tgt.getComponent(TemperatureComponent.class).incomingHeat, 0.0001f);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.thermal.HeatSourceSystemTest"`
Expected: FAIL — `HeatSourceSystem` does not exist.

- [ ] **Step 4: Implement the system**

`HeatSourceSystem.java`:
```java
package com.galacticodyssey.planet.thermal;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;

/**
 * Deposits watts from {@link HeatSourceComponent} emitters into the {@code incomingHeat}
 * accumulator of nearby {@link TemperatureComponent} entities, with linear distance falloff
 * (and optional cone gating). Priority 29 -- before {@link ObjectTemperatureSystem} (31).
 */
public class HeatSourceSystem extends EntitySystem {

    public static final int PRIORITY = 29;

    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
            ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<HeatSourceComponent> SOURCE_M =
            ComponentMapper.getFor(HeatSourceComponent.class);
    private static final ComponentMapper<TemperatureComponent> TEMP_M =
            ComponentMapper.getFor(TemperatureComponent.class);

    private ImmutableArray<Entity> emitters;
    private ImmutableArray<Entity> targets;

    private final Vector3 toTarget = new Vector3();

    public HeatSourceSystem() { super(PRIORITY); }

    @Override
    public void addedToEngine(Engine engine) {
        emitters = engine.getEntitiesFor(
                Family.all(HeatSourceComponent.class, TransformComponent.class).get());
        targets = engine.getEntitiesFor(
                Family.all(TemperatureComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float dt) {
        for (int i = 0; i < emitters.size(); i++) {
            Entity emitterEntity = emitters.get(i);
            HeatSourceComponent src = SOURCE_M.get(emitterEntity);

            if (src.lifetime >= 0f) {
                src.lifetime -= dt;
                if (src.lifetime < 0f) {
                    emitterEntity.remove(HeatSourceComponent.class);
                    continue;
                }
            }
            Vector3 origin = TRANSFORM_M.get(emitterEntity).position;
            depositToTargets(src, origin);
        }
    }

    private void depositToTargets(HeatSourceComponent src, Vector3 origin) {
        for (int j = 0; j < targets.size(); j++) {
            Entity tgtEntity = targets.get(j);
            TransformComponent tgtTr = TRANSFORM_M.get(tgtEntity);
            toTarget.set(tgtTr.position).sub(origin);
            float dist = toTarget.len();
            if (dist > src.radius || dist <= 0f) continue;

            if (src.cone) {
                float angle = (float) Math.acos(
                        MathUtils.clamp(toTarget.nor().dot(src.direction), -1f, 1f));
                if (angle > src.coneHalfAngleRad) continue;
            }
            float falloff = 1f - (dist / src.radius); // linear: full at source, 0 at edge
            TEMP_M.get(tgtEntity).incomingHeat += src.power * falloff;
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.thermal.HeatSourceSystemTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/thermal/HeatSourceComponent.java \
        core/src/main/java/com/galacticodyssey/planet/thermal/HeatSourceSystem.java \
        core/src/test/java/com/galacticodyssey/planet/thermal/HeatSourceSystemTest.java
git commit -m "feat(thermal): HeatSourceSystem emitter deposition (flamethrower/lava/cryo)"
```

---

## Task 7: BurningComponent + FrozenComponent + CombustionSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/fire/BurningComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/fire/FrozenComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/fire/events/IgniteAtEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/fire/CombustionSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/fire/CombustionSystemTest.java`

- [ ] **Step 1: Implement components + IgniteAtEvent**

`BurningComponent.java`:
```java
package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.Component;

/** Present while an entity is on fire. Added by CombustionSystem on IgnitionEvent. */
public class BurningComponent implements Component {
    public float intensity = 1f;
    public float fuelRemaining = 0f; // J -- initialized from material.combustionEnergy
    public float heatOutput = 0f;    // W -- copied from material.burnHeatOutput
    public boolean statusApplied = false; // BURNING status applied once
}
```

`FrozenComponent.java`:
```java
package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.Component;

/** Present while an entity is frozen solid. Other systems read these for penalties. */
public class FrozenComponent implements Component {
    public float frozenFraction = 1f;       // 0..1
    public float speedMultiplier = 1f;       // from material.frozenSpeedMultiplier
    public boolean brittle = false;          // from material.brittleWhenFrozen
}
```

`IgniteAtEvent.java`:
```java
package com.galacticodyssey.planet.fire.events;

/** Request to ignite the fuel grid near a world (local-scene) position. */
public final class IgniteAtEvent {
    public final float worldX;
    public final float worldZ;
    public final float strength; // 0..1+ ignition progress contribution
    public IgniteAtEvent(float worldX, float worldZ, float strength) {
        this.worldX = worldX; this.worldZ = worldZ; this.strength = strength;
    }
}
```

- [ ] **Step 2: Write the failing test**

`CombustionSystemTest.java`:
```java
package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.StatusEffectsComponent;
import com.galacticodyssey.combat.events.StatusEffectAppliedEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.planet.thermal.TemperatureComponent;
import com.galacticodyssey.planet.thermal.ThermalMaterial;
import com.galacticodyssey.planet.thermal.ThermalMaterialRegistry;
import com.galacticodyssey.planet.thermal.ThermalState;
import com.galacticodyssey.planet.thermal.events.IgnitionEvent;
import com.galacticodyssey.planet.thermal.events.ObjectConsumedByFireEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombustionSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private ThermalMaterialRegistry registry;
    private final List<StatusEffectAppliedEvent> statusEvents = new ArrayList<>();
    private final List<ObjectConsumedByFireEvent> consumed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        registry = new ThermalMaterialRegistry();
        ThermalMaterial grass = new ThermalMaterial();
        grass.id = "dry_grass"; grass.flammable = true; grass.burnHeatOutput = 4000f;
        grass.combustionEnergy = 8000f; grass.consumedWhenBurnt = true; grass.charMaterialId = "ash";
        registry.register(grass);

        engine = new Engine();
        engine.addSystem(new CombustionSystem(eventBus, registry));
        eventBus.subscribe(StatusEffectAppliedEvent.class, statusEvents::add);
        eventBus.subscribe(ObjectConsumedByFireEvent.class, consumed::add);
    }

    private Entity burnable(String materialId, boolean withHealth) {
        Entity e = new Entity();
        TransformComponent tr = new TransformComponent();
        TemperatureComponent t = new TemperatureComponent();
        t.materialId = materialId; t.material = registry.get(materialId);
        t.state = ThermalState.BURNING;
        e.add(tr); e.add(t);
        if (withHealth) { e.add(new HealthComponent()); e.add(new StatusEffectsComponent()); }
        engine.addEntity(e);
        return e;
    }

    @Test
    void ignitionEventAddsBurningComponentInitializedFromMaterial() {
        Entity e = burnable("dry_grass", false);
        eventBus.publish(new IgnitionEvent(e));
        engine.update(0.016f);
        BurningComponent b = e.getComponent(BurningComponent.class);
        assertNotNull(b);
        assertEquals(4000f, b.heatOutput, 0.001f);
        assertEquals(8000f, b.fuelRemaining, 4000f); // started at 8000, minus one tick
    }

    @Test
    void burningEntityDepositsHeatIntoOwnIncomingHeat() {
        Entity e = burnable("dry_grass", false);
        eventBus.publish(new IgnitionEvent(e));
        engine.update(0.016f);
        assertTrue(e.getComponent(TemperatureComponent.class).incomingHeat > 0f);
    }

    @Test
    void appliesBurningStatusOnceToEntityWithHealth() {
        Entity e = burnable("dry_grass", true);
        eventBus.publish(new IgnitionEvent(e));
        engine.update(0.016f);
        engine.update(0.016f);
        long burningApplied = statusEvents.stream()
                .filter(ev -> ev.effectType == StatusEffectType.BURNING).count();
        assertEquals(1, burningApplied, "BURNING status applied exactly once");
    }

    @Test
    void consumesEntityWhenFuelExhausted() {
        Entity e = burnable("dry_grass", false);
        eventBus.publish(new IgnitionEvent(e));
        // 8000 J fuel / (4000 W) = 2s; step well past it
        for (int i = 0; i < 200; i++) engine.update(0.016f);
        assertEquals(1, consumed.size());
        assertEquals("ash", consumed.get(0).charMaterialId);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.fire.CombustionSystemTest"`
Expected: FAIL — `CombustionSystem` does not exist.

- [ ] **Step 4: Implement the system**

`CombustionSystem.java`:
```java
package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import com.galacticodyssey.combat.components.StatusEffectsComponent;
import com.galacticodyssey.combat.events.StatusEffectAppliedEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.planet.fire.events.IgniteAtEvent;
import com.galacticodyssey.planet.thermal.TemperatureComponent;
import com.galacticodyssey.planet.thermal.ThermalMaterial;
import com.galacticodyssey.planet.thermal.ThermalMaterialRegistry;
import com.galacticodyssey.planet.thermal.ThermalState;
import com.galacticodyssey.planet.thermal.events.ExtinguishedEvent;
import com.galacticodyssey.planet.thermal.events.FreezeEvent;
import com.galacticodyssey.planet.thermal.events.IgnitionEvent;
import com.galacticodyssey.planet.thermal.events.ObjectConsumedByFireEvent;
import com.galacticodyssey.planet.thermal.events.ThawEvent;

/**
 * Owns the burning / frozen lifecycle of entities. Subscribes to ignition/freeze/thaw events
 * from {@link com.galacticodyssey.planet.thermal.ObjectTemperatureSystem} and manages
 * {@link BurningComponent} / {@link FrozenComponent}. Each tick, burning entities deposit
 * their heat output into their own (and neighbours') {@code incomingHeat}, burn fuel down,
 * apply the BURNING status to entities with health, request ground ignition under them, and
 * are consumed (removed or charred) when fuel exhausts.
 *
 * <p>Priority 30 -- deposits heat before {@link com.galacticodyssey.planet.thermal.ObjectTemperatureSystem} (31).</p>
 */
public class CombustionSystem extends EntitySystem {

    public static final int PRIORITY = 30;
    public static final float SPREAD_RADIUS = 3f;      // m -- fire-to-fire heating range
    public static final float SPREAD_FRACTION = 0.25f; // fraction of heatOutput shed to neighbours
    public static final float GROUND_IGNITE_STRENGTH = 0.5f;

    private static final ComponentMapper<TemperatureComponent> TEMP_M =
            ComponentMapper.getFor(TemperatureComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
            ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<BurningComponent> BURN_M =
            ComponentMapper.getFor(BurningComponent.class);
    private static final ComponentMapper<StatusEffectsComponent> STATUS_M =
            ComponentMapper.getFor(StatusEffectsComponent.class);

    private final EventBus eventBus;
    private final ThermalMaterialRegistry registry;
    private ImmutableArray<Entity> burning;
    private ImmutableArray<Entity> allTemp;

    public CombustionSystem(EventBus eventBus, ThermalMaterialRegistry registry) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.registry = registry;
        eventBus.subscribe(IgnitionEvent.class, this::onIgnition);
        eventBus.subscribe(FreezeEvent.class, this::onFreeze);
        eventBus.subscribe(ThawEvent.class, this::onThaw);
    }

    @Override
    public void addedToEngine(Engine engine) {
        burning = engine.getEntitiesFor(
                Family.all(BurningComponent.class, TemperatureComponent.class, TransformComponent.class).get());
        allTemp = engine.getEntitiesFor(
                Family.all(TemperatureComponent.class, TransformComponent.class).get());
    }

    private void onIgnition(IgnitionEvent ev) {
        Entity e = ev.entity;
        if (BURN_M.get(e) != null) return;
        TemperatureComponent t = TEMP_M.get(e);
        ThermalMaterial m = (t != null) ? t.material : null;
        BurningComponent b = new BurningComponent();
        if (m != null) {
            b.heatOutput = m.burnHeatOutput;
            b.fuelRemaining = m.combustionEnergy;
        }
        e.add(b);

        if (STATUS_M.get(e) != null && !b.statusApplied) {
            eventBus.publish(new StatusEffectAppliedEvent(e, StatusEffectType.BURNING, null));
            b.statusApplied = true;
        }
    }

    private void onFreeze(FreezeEvent ev) {
        Entity e = ev.entity;
        TemperatureComponent t = TEMP_M.get(e);
        ThermalMaterial m = (t != null) ? t.material : null;
        FrozenComponent f = new FrozenComponent();
        if (m != null) {
            f.speedMultiplier = m.frozenSpeedMultiplier;
            f.brittle = m.brittleWhenFrozen;
        }
        e.add(f);
    }

    private void onThaw(ThawEvent ev) {
        ev.entity.remove(FrozenComponent.class);
    }

    @Override
    public void update(float dt) {
        for (int i = burning.size() - 1; i >= 0; i--) {
            Entity e = burning.get(i);
            BurningComponent b = BURN_M.get(e);
            TemperatureComponent t = TEMP_M.get(e);

            // Self-heating keeps the fire hot (deposited before integration at priority 31).
            t.incomingHeat += b.heatOutput * b.intensity;

            // Spread heat to nearby flammable/normal objects.
            spreadHeat(e, b, dt);

            // Ignite the ground/fuel grid beneath the burning entity.
            eventBus.publish(new IgniteAtEvent(
                    TRANSFORM_M.get(e).position.x, TRANSFORM_M.get(e).position.z, GROUND_IGNITE_STRENGTH));

            // Burn fuel.
            b.fuelRemaining -= b.heatOutput * b.intensity * dt;
            if (b.fuelRemaining <= 0f) {
                consume(e, t);
            }
        }
    }

    private void spreadHeat(Entity source, BurningComponent b, float dt) {
        com.badlogic.gdx.math.Vector3 srcPos = TRANSFORM_M.get(source).position;
        for (int j = 0; j < allTemp.size(); j++) {
            Entity other = allTemp.get(j);
            if (other == source) continue;
            float dist = TRANSFORM_M.get(other).position.dst(srcPos);
            if (dist > SPREAD_RADIUS || dist <= 0f) continue;
            float falloff = 1f - (dist / SPREAD_RADIUS);
            TEMP_M.get(other).incomingHeat += b.heatOutput * SPREAD_FRACTION * falloff;
        }
    }

    private void consume(Entity e, TemperatureComponent t) {
        ThermalMaterial m = t.material;
        e.remove(BurningComponent.class);
        t.state = ThermalState.NORMAL;
        eventBus.publish(new ExtinguishedEvent(e));

        if (m != null && m.consumedWhenBurnt) {
            String charId = m.charMaterialId;
            eventBus.publish(new ObjectConsumedByFireEvent(e, charId));
            if (charId != null) {
                t.materialId = charId;
                t.material = registry.get(charId);
            } else {
                getEngine().removeEntity(e);
            }
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.fire.CombustionSystemTest"`
Expected: PASS (all 4 tests).

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/fire/BurningComponent.java \
        core/src/main/java/com/galacticodyssey/planet/fire/FrozenComponent.java \
        core/src/main/java/com/galacticodyssey/planet/fire/events/IgniteAtEvent.java \
        core/src/main/java/com/galacticodyssey/planet/fire/CombustionSystem.java \
        core/src/test/java/com/galacticodyssey/planet/fire/CombustionSystemTest.java
git commit -m "feat(fire): CombustionSystem burning/frozen lifecycle, DoT, spread, consume"
```

---

## Task 8: FuelGridComponent + WildfireSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/planet/fire/FuelGridComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/fire/events/WildfireCellIgnitedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/planet/fire/WildfireSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/planet/fire/WildfireSystemTest.java`

- [ ] **Step 1: Implement FuelGridComponent + event**

`FuelGridComponent.java`:
```java
package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.Component;

/**
 * A 2D fuel-load grid over the active surface scene (local-scene coordinates).
 * Cell states: 0 = UNBURNT, 1 = IGNITING, 2 = BURNING, 3 = BURNT.
 */
public class FuelGridComponent implements Component {
    public static final byte UNBURNT = 0, IGNITING = 1, BURNING = 2, BURNT = 3;

    public int width;
    public int height;
    public float cellSize;     // m per cell
    public float originX;      // local-scene world X of cell (0,0) corner
    public float originZ;      // local-scene world Z of cell (0,0) corner

    public float[] fuelLoad;       // J of fuel per cell
    public float[] moisture;       // 0..1 (raises ignition threshold)
    public byte[] state;
    public float[] ignitionProgress; // accumulates toward 1.0 -> ignites
    public float[] burnTimer;      // seconds a cell has been burning

    public FuelGridComponent(int width, int height, float cellSize, float originX, float originZ) {
        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
        this.originX = originX;
        this.originZ = originZ;
        int n = width * height;
        this.fuelLoad = new float[n];
        this.moisture = new float[n];
        this.state = new byte[n];
        this.ignitionProgress = new float[n];
        this.burnTimer = new float[n];
    }

    public int index(int x, int y) { return y * width + x; }
    public boolean inBounds(int x, int y) { return x >= 0 && x < width && y >= 0 && y < height; }
    public int cellX(float worldX) { return (int) Math.floor((worldX - originX) / cellSize); }
    public int cellY(float worldZ) { return (int) Math.floor((worldZ - originZ) / cellSize); }
    public float cellCenterX(int x) { return originX + (x + 0.5f) * cellSize; }
    public float cellCenterZ(int y) { return originZ + (y + 0.5f) * cellSize; }
}
```

`WildfireCellIgnitedEvent.java`:
```java
package com.galacticodyssey.planet.fire.events;

public final class WildfireCellIgnitedEvent {
    public final int cellX;
    public final int cellY;
    public final float worldX;
    public final float worldZ;
    public WildfireCellIgnitedEvent(int cellX, int cellY, float worldX, float worldZ) {
        this.cellX = cellX; this.cellY = cellY; this.worldX = worldX; this.worldZ = worldZ;
    }
}
```

- [ ] **Step 2: Write the failing test**

`WildfireSystemTest.java`:
```java
package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.planet.fire.events.IgniteAtEvent;
import com.galacticodyssey.planet.thermal.ThermalEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WildfireSystemTest {

    static class StubEnv implements ThermalEnvironment {
        float o2 = 0.21f;
        public float ambientTemp(Vector3 p) { return 293f; }
        public float oxygenFraction(Vector3 p) { return o2; }
        public void wind(Vector3 p, Vector3 out) { out.set(0,0,0); }
    }

    private EventBus eventBus;
    private Engine engine;
    private StubEnv env;
    private FuelGridComponent grid;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        env = new StubEnv();
        engine = new Engine();
        engine.addSystem(new WildfireSystem(eventBus, env));

        grid = new FuelGridComponent(5, 5, 1f, 0f, 0f);
        for (int i = 0; i < grid.fuelLoad.length; i++) {
            grid.fuelLoad[i] = 10000f;
            grid.moisture[i] = 0f;
        }
        Entity gridEntity = new Entity();
        gridEntity.add(grid);
        engine.addEntity(gridEntity);
    }

    @Test
    void igniteAtEventStartsACellBurning() {
        eventBus.publish(new IgniteAtEvent(2.5f, 2.5f, 2f)); // strength >= 1 -> ignites immediately
        engine.update(0.1f);
        int idx = grid.index(2, 2);
        assertEquals(FuelGridComponent.BURNING, grid.state[idx]);
    }

    @Test
    void fireSpreadsToAdjacentCellOverTime() {
        eventBus.publish(new IgniteAtEvent(2.5f, 2.5f, 2f));
        for (int i = 0; i < 100; i++) engine.update(0.1f);
        // A 4-neighbour should have ignited (or burnt out) -- no longer pristine UNBURNT.
        assertNotEquals(FuelGridComponent.UNBURNT, grid.state[grid.index(2, 1)]);
    }

    @Test
    void burningCellConsumesFuelAndBurnsOut() {
        eventBus.publish(new IgniteAtEvent(2.5f, 2.5f, 2f));
        for (int i = 0; i < 500; i++) engine.update(0.1f);
        assertEquals(FuelGridComponent.BURNT, grid.state[grid.index(2, 2)]);
        assertTrue(grid.fuelLoad[grid.index(2, 2)] <= 0f);
    }

    @Test
    void noOxygenPreventsSpread() {
        env.o2 = 0.02f;
        eventBus.publish(new IgniteAtEvent(2.5f, 2.5f, 2f));
        for (int i = 0; i < 50; i++) engine.update(0.1f);
        assertEquals(FuelGridComponent.UNBURNT, grid.state[grid.index(0, 0)]);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.fire.WildfireSystemTest"`
Expected: FAIL — `WildfireSystem` does not exist.

- [ ] **Step 4: Implement the system**

`WildfireSystem.java`:
```java
package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.planet.fire.events.IgniteAtEvent;
import com.galacticodyssey.planet.fire.events.WildfireCellIgnitedEvent;
import com.galacticodyssey.planet.thermal.ThermalEnvironment;

/**
 * Propagates fire across a {@link FuelGridComponent}. Burning cells consume fuel, emit heat,
 * and deterministically raise neighbours' {@code ignitionProgress} weighted by flammability
 * (fuel presence), dryness (1 - moisture), wind alignment, and local oxygen. A cell ignites
 * when its progress reaches 1.0. Spread/burn stop when oxygen is below the combustion minimum.
 *
 * <p>Priority 28 -- runs before {@link com.galacticodyssey.planet.thermal.ObjectTemperatureSystem} (31).</p>
 */
public class WildfireSystem extends EntitySystem {

    public static final int PRIORITY = 28;
    public static final float BURN_RATE = 4000f;     // W per burning cell
    public static final float SPREAD_RATE = 0.6f;    // base progress/sec from one burning neighbour
    public static final float O2_MIN = 0.10f;

    private static final ComponentMapper<FuelGridComponent> GRID_M =
            ComponentMapper.getFor(FuelGridComponent.class);

    private final EventBus eventBus;
    private final ThermalEnvironment environment;
    private ImmutableArray<Entity> gridEntities;

    private final Vector3 scratch = new Vector3();
    private final Vector3 wind = new Vector3();

    // Pending ignition requests (worldX, worldZ, strength) applied at the start of update.
    private final com.badlogic.gdx.utils.Array<IgniteAtEvent> pending =
            new com.badlogic.gdx.utils.Array<>();

    public WildfireSystem(EventBus eventBus, ThermalEnvironment environment) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.environment = environment;
        eventBus.subscribe(IgniteAtEvent.class, pending::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        gridEntities = engine.getEntitiesFor(Family.all(FuelGridComponent.class).get());
    }

    @Override
    public void update(float dt) {
        if (gridEntities.size() == 0) { pending.clear(); return; }
        FuelGridComponent grid = GRID_M.get(gridEntities.get(0));

        applyPendingIgnitions(grid);
        propagate(grid, dt);
    }

    private void applyPendingIgnitions(FuelGridComponent grid) {
        for (int i = 0; i < pending.size; i++) {
            IgniteAtEvent ev = pending.get(i);
            int cx = grid.cellX(ev.worldX);
            int cy = grid.cellY(ev.worldZ);
            if (!grid.inBounds(cx, cy)) continue;
            int idx = grid.index(cx, cy);
            if (grid.state[idx] == FuelGridComponent.UNBURNT && grid.fuelLoad[idx] > 0f) {
                grid.ignitionProgress[idx] += ev.strength;
                maybeIgnite(grid, cx, cy, idx);
            }
        }
        pending.clear();
    }

    private void propagate(FuelGridComponent grid, float dt) {
        boolean o2ok = environmentO2Ok(grid);

        // Pass 1: burn fuel in BURNING cells; spread progress to neighbours.
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                int idx = grid.index(x, y);
                if (grid.state[idx] != FuelGridComponent.BURNING) continue;

                if (!o2ok) { grid.state[idx] = FuelGridComponent.BURNT; continue; }

                grid.burnTimer[idx] += dt;
                grid.fuelLoad[idx] -= BURN_RATE * dt;
                if (grid.fuelLoad[idx] <= 0f) {
                    grid.fuelLoad[idx] = 0f;
                    grid.state[idx] = FuelGridComponent.BURNT;
                    continue;
                }
                spreadToNeighbours(grid, x, y, dt);
            }
        }

        // Pass 2: promote cells whose progress reached threshold.
        if (o2ok) {
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    int idx = grid.index(x, y);
                    if (grid.state[idx] == FuelGridComponent.UNBURNT) {
                        maybeIgnite(grid, x, y, idx);
                    }
                }
            }
        }
    }

    private void spreadToNeighbours(FuelGridComponent grid, int x, int y, float dt) {
        environment.wind(scratch.set(grid.cellCenterX(x), 0f, grid.cellCenterZ(y)), wind);
        addProgress(grid, x + 1, y, +1f, 0f, dt);
        addProgress(grid, x - 1, y, -1f, 0f, dt);
        addProgress(grid, x, y + 1, 0f, +1f, dt);
        addProgress(grid, x, y - 1, 0f, -1f, dt);
    }

    private void addProgress(FuelGridComponent grid, int nx, int ny,
                             float dirX, float dirZ, float dt) {
        if (!grid.inBounds(nx, ny)) return;
        int nidx = grid.index(nx, ny);
        if (grid.state[nidx] != FuelGridComponent.UNBURNT || grid.fuelLoad[nidx] <= 0f) return;

        float dryness = 1f - clamp01(grid.moisture[nidx]);
        // Wind alignment in [0.5 .. 1.5]: downwind cells catch faster, no wind = neutral 1.0.
        float windMag = wind.len();
        float windAlign = 1f;
        if (windMag > 1e-4f) {
            float dot = (dirX * wind.x + dirZ * wind.z) / windMag;
            windAlign = 1f + 0.5f * dot;
        }
        grid.ignitionProgress[nidx] += SPREAD_RATE * dryness * windAlign * dt;
    }

    private void maybeIgnite(FuelGridComponent grid, int x, int y, int idx) {
        if (grid.ignitionProgress[idx] >= 1f && grid.fuelLoad[idx] > 0f) {
            grid.state[idx] = FuelGridComponent.BURNING;
            eventBus.publish(new WildfireCellIgnitedEvent(
                    x, y, grid.cellCenterX(x), grid.cellCenterZ(y)));
        }
    }

    private boolean environmentO2Ok(FuelGridComponent grid) {
        return environment.oxygenFraction(
                scratch.set(grid.cellCenterX(grid.width / 2), 0f, grid.cellCenterZ(grid.height / 2)))
                >= O2_MIN;
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.fire.WildfireSystemTest"`
Expected: PASS (all 4 tests).

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/planet/fire/FuelGridComponent.java \
        core/src/main/java/com/galacticodyssey/planet/fire/events/WildfireCellIgnitedEvent.java \
        core/src/main/java/com/galacticodyssey/planet/fire/WildfireSystem.java \
        core/src/test/java/com/galacticodyssey/planet/fire/WildfireSystemTest.java
git commit -m "feat(fire): WildfireSystem grid propagation with wind/moisture/O2 gating"
```

---

## Task 9: Integration test + GameWorld wiring

**Files:**
- Test: `core/src/test/java/com/galacticodyssey/planet/fire/ThermalFireIntegrationTest.java`
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java` (surface-scene system registration, near line 314 where `surfaceAnchorSystem` is added)

- [ ] **Step 1: Write the integration test**

`ThermalFireIntegrationTest.java`:
```java
package com.galacticodyssey.planet.fire;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.planet.thermal.HeatSourceComponent;
import com.galacticodyssey.planet.thermal.HeatSourceSystem;
import com.galacticodyssey.planet.thermal.ObjectTemperatureSystem;
import com.galacticodyssey.planet.thermal.TemperatureComponent;
import com.galacticodyssey.planet.thermal.ThermalEnvironment;
import com.galacticodyssey.planet.thermal.ThermalMaterial;
import com.galacticodyssey.planet.thermal.ThermalMaterialRegistry;
import com.galacticodyssey.planet.thermal.ThermalState;
import com.galacticodyssey.planet.thermal.events.IgnitionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThermalFireIntegrationTest {

    static class StubEnv implements ThermalEnvironment {
        public float ambientTemp(Vector3 p) { return 293f; }
        public float oxygenFraction(Vector3 p) { return 0.21f; }
        public void wind(Vector3 p, Vector3 out) { out.set(0,0,0); }
    }

    private Engine engine;
    private EventBus eventBus;
    private ThermalMaterialRegistry registry;
    private final List<IgnitionEvent> ignitions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        registry = new ThermalMaterialRegistry();
        ThermalMaterial grass = new ThermalMaterial();
        grass.id = "dry_grass"; grass.flammable = true; grass.ignitionPoint = 533f;
        grass.burnHeatOutput = 4000f; grass.combustionEnergy = 8000f;
        grass.consumedWhenBurnt = true; grass.charMaterialId = "ash";
        registry.register(grass);
        ThermalMaterial ash = new ThermalMaterial();
        ash.id = "ash"; ash.ignitionPoint = 100000f;
        registry.register(ash);

        ThermalEnvironment env = new StubEnv();
        engine = new Engine();
        engine.addSystem(new WildfireSystem(eventBus, env));                       // 28
        engine.addSystem(new HeatSourceSystem());                                  // 29
        engine.addSystem(new CombustionSystem(eventBus, registry));                // 30
        engine.addSystem(new ObjectTemperatureSystem(eventBus, registry, env));    // 31
        eventBus.subscribe(IgnitionEvent.class, ignitions::add);
    }

    @Test
    void flamethrowerIgnitesGrassAndItBurnsOut() {
        // Target grass object.
        Entity grass = new Entity();
        grass.add(new TransformComponent());
        TemperatureComponent t = new TemperatureComponent();
        t.materialId = "dry_grass"; t.material = registry.get("dry_grass");
        t.temperature = 293f; t.thermalMass = 200f; t.surfaceArea = 1f;
        grass.add(t);
        engine.addEntity(grass);

        // Flamethrower: strong heat emitter co-located with the grass.
        Entity flame = new Entity();
        TransformComponent ftr = new TransformComponent();
        ftr.position.set(0.2f, 0, 0);
        flame.add(ftr);
        HeatSourceComponent src = new HeatSourceComponent();
        src.power = 200_000f; src.radius = 2f; src.lifetime = 0.5f;
        flame.add(src);
        engine.addEntity(flame);

        // Run a few seconds.
        for (int i = 0; i < 300; i++) engine.update(0.05f);

        assertFalse(ignitions.isEmpty(), "grass should have ignited from the flamethrower");
        // Fuel (8000 J at 4000 W ~ 2s) exhausts -> consumed -> char material 'ash'.
        assertEquals("ash", t.materialId);
        assertEquals(ThermalState.NORMAL, t.state);
        assertNull(grass.getComponent(BurningComponent.class));
    }
}
```

- [ ] **Step 2: Run the integration test to verify it fails, then passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.fire.ThermalFireIntegrationTest"`
Expected: PASS (all production classes already exist from Tasks 1–8; this test asserts they compose correctly). If it fails, debug the composition before wiring into GameWorld.

- [ ] **Step 3: Wire systems into the planet surface scene**

In `core/src/main/java/com/galacticodyssey/core/GameWorld.java`, locate the surface-scene block where `surfaceAnchorSystem` is created and added (around line 311–318). Add the thermal/fire systems immediately after `engine.addSystem(surfaceAnchorSystem);`. Use the scene's planet data to build the environment; the field names below (`biomeMap`, `atmosphere`, `planetRadius`, `sceneLatRad`, `sceneLonRad`) must be replaced with the actual references available in that method — search the surrounding code for how the surface scene already obtains its `BiomeMap`/`Atmosphere`.

```java
// --- Planetside thermal & fire ---
thermalMaterialRegistry = new ThermalMaterialRegistry();
thermalMaterialRegistry.loadFromFiles();
ThermalEnvironment thermalEnv = new PlanetSurfaceEnvironment(
        biomeMap, atmosphere, sceneLatRad, sceneLonRad, planetRadius);
engine.addSystem(new WildfireSystem(eventBus, thermalEnv));
engine.addSystem(new HeatSourceSystem());
engine.addSystem(new CombustionSystem(eventBus, thermalMaterialRegistry));
engine.addSystem(new ObjectTemperatureSystem(eventBus, thermalMaterialRegistry, thermalEnv));
```

Add the required imports at the top of `GameWorld.java`:
```java
import com.galacticodyssey.planet.thermal.HeatSourceSystem;
import com.galacticodyssey.planet.thermal.ObjectTemperatureSystem;
import com.galacticodyssey.planet.thermal.PlanetSurfaceEnvironment;
import com.galacticodyssey.planet.thermal.ThermalEnvironment;
import com.galacticodyssey.planet.thermal.ThermalMaterialRegistry;
import com.galacticodyssey.planet.fire.CombustionSystem;
import com.galacticodyssey.planet.fire.WildfireSystem;
```

And declare the `thermalMaterialRegistry` field alongside the other system fields in the class:
```java
private ThermalMaterialRegistry thermalMaterialRegistry;
```

> If the surface-scene method does not already hold a `BiomeMap`/`Atmosphere` reference, obtain them the same way the biome/terrain systems in that method do (search for `BiomeMap` usage in `GameWorld.java`). The `FuelGridComponent` is created and added to the scene by whatever spawns surface vegetation; seed `fuelLoad`/`moisture` from `biomeMap.getMoisture(...)` and biome type at each cell. Creating that grid is part of surface-scene population, not this wiring step — leaving it unseeded simply means no wildfire until vegetation seeding is added, while object-level temperature/fire already works.

- [ ] **Step 4: Compile and run the full thermal/fire test suite**

Run: `./gradlew :core:test --tests "com.galacticodyssey.planet.*"`
Expected: BUILD SUCCESSFUL; all thermal and fire tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/src/test/java/com/galacticodyssey/planet/fire/ThermalFireIntegrationTest.java \
        core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(thermal): integration test + wire thermal/fire systems into surface scene"
```

---

## Self-Review Notes (resolved during planning)

- **Spec coverage:** per-object temperature (Task 5), full-physics radiation/conduction (Task 2), data-driven materials (Task 1), environment provider (Task 3), heat sources/flamethrower hook (Task 6), ignition+DoT+consume (Task 7), wildfire spread with wind/moisture/O2 (Task 8), all four effects — DoT (Task 7 BURNING status), spread (Tasks 7+8), state change/destruction (Task 7 consume + char), penalties (Task 7 `FrozenComponent`) — and integration/wiring (Task 9). Freezing thresholds/transitions (Task 5) + frozen lifecycle (Task 7).
- **Type consistency:** `incomingHeat`, `ThermalState`, `materialId`/`material`, `FuelGridComponent` state constants, and event constructors are referenced identically across tasks. System priorities match the ordering table (28/29/30/31).
- **Known limitation surfaced (not a placeholder):** `PlanetSurfaceEnvironment.wind()` returns calm; `WildfireSystem` already handles zero wind as neutral bias. Climate-driven wind sampling is an explicit later enhancement, noted in code. `FuelGridComponent` seeding from vegetation is owned by surface-scene population (Task 9 Step 3 note), not this plan — object-level fire/temperature works without it.
```
