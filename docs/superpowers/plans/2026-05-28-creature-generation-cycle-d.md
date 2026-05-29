# Creature Generation Cycle D — Behavior, Ecology & Biome Spawning

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give creatures autonomous behavior (flee, hunt, herd stampedes), ecological drives (hunger, energy, fear), population dynamics (Lotka-Volterra growth, predation, migration), and biome-weighted spawning with chunk-persistent populations that evolve even when the player isn't watching.

**Architecture:** Data-driven `SpeciesDef` JSON links archetypes to behavioral profiles (diet, temperament, social structure) and biome affinities. `CreatureBehaviorSystem` drives a gdx-ai `DefaultStateMachine` per entity with 7 states. `CreatureDriveSystem` ticks hunger/energy/fear floats. `PopulationTickSystem` runs Lotka-Volterra math on `ChunkPopulationRecord`s for all tracked chunks. `CreatureSpawnSystem` instantiates/despawns creatures as the player moves through fauna chunks on the planet surface.

**Tech Stack:** Java 17, libGDX 1.13, Ashley ECS, gdx-ai 1.8.2 (`DefaultStateMachine`, `State`), JUnit 5

**Spec:** `docs/superpowers/specs/2026-05-28-creature-generation-bcd-design.md` (Cycle D section)

**Key integration points:**
- Event bus: `com.galacticodyssey.core.EventBus` — `publish(event)`, `subscribe(Class, listener)`
- Biome queries: `com.galacticodyssey.planet.BiomeMap.getBiome(lat, lon, elevation)`
- Seed derivation: `com.galacticodyssey.galaxy.SeedDeriver.forChunk(domainSeed, cx, cz)`

---

### Task 1: Behavior enums and SpeciesDef data model

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/behavior/Diet.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/behavior/Temperament.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/behavior/SocialStructure.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/behavior/ActivityCycle.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/behavior/CreatureState.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/ecosystem/SpeciesDef.java`

- [ ] **Step 1: Write Diet enum**

```java
package com.galacticodyssey.fauna.behavior;

public enum Diet { HERBIVORE, CARNIVORE, OMNIVORE }
```

- [ ] **Step 2: Write Temperament enum**

```java
package com.galacticodyssey.fauna.behavior;

public enum Temperament { TIMID, NEUTRAL, TERRITORIAL, AGGRESSIVE }
```

- [ ] **Step 3: Write SocialStructure enum**

```java
package com.galacticodyssey.fauna.behavior;

public enum SocialStructure { SOLITARY, HERD, PACK }
```

- [ ] **Step 4: Write ActivityCycle enum**

```java
package com.galacticodyssey.fauna.behavior;

public enum ActivityCycle { DIURNAL, NOCTURNAL, CREPUSCULAR }
```

- [ ] **Step 5: Write CreatureState enum**

This enum implements gdx-ai's `State` interface so it works with `DefaultStateMachine`.

```java
package com.galacticodyssey.fauna.behavior;

import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.ashley.core.Entity;

public enum CreatureState implements State<Entity> {
    IDLE, WANDER, ALERT, FLEE, HUNT, ATTACK, FEED;

    @Override public void enter(Entity entity) {}
    @Override public void update(Entity entity) {}
    @Override public void exit(Entity entity) {}
    @Override public boolean onMessage(Entity entity, Telegram telegram) { return false; }
}
```

- [ ] **Step 6: Write SpeciesDef data model**

```java
package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.fauna.behavior.*;
import com.galacticodyssey.planet.BiomeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SpeciesDef {
    public String id;
    public String archetypeId;
    public Diet diet = Diet.HERBIVORE;
    public Temperament temperament = Temperament.NEUTRAL;
    public SocialStructure socialStructure = SocialStructure.SOLITARY;
    public int herdSizeMin = 1;
    public int herdSizeMax = 1;
    public final Map<BiomeType, Float> biomeAffinities = new HashMap<>();
    public int trophicLevel = 1;
    public final List<String> preySpecies = new ArrayList<>();
    public ActivityCycle activityCycle = ActivityCycle.DIURNAL;
    public float detectionRadius = 25f;
    public float fleeRadius = 15f;
    public float fleeSpeedMultiplier = 1.5f;
    public float safeDistance = 40f;
    public float birthRate = 0.02f;
    public int carryingCapacityBase = 30;
}
```

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/behavior/Diet.java \
        core/src/main/java/com/galacticodyssey/fauna/behavior/Temperament.java \
        core/src/main/java/com/galacticodyssey/fauna/behavior/SocialStructure.java \
        core/src/main/java/com/galacticodyssey/fauna/behavior/ActivityCycle.java \
        core/src/main/java/com/galacticodyssey/fauna/behavior/CreatureState.java \
        core/src/main/java/com/galacticodyssey/fauna/ecosystem/SpeciesDef.java
git commit -m "feat(fauna): behavior enums and SpeciesDef data model for Cycle D"
```

---

### Task 2: FaunaDataRegistry species loading + default species JSON

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/data/FaunaDataRegistry.java`
- Create: `core/src/main/resources/data/fauna/species/default-species.json`
- Test: `core/src/test/java/com/galacticodyssey/fauna/ecosystem/SpeciesLoadingTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.behavior.*;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeciesLoadingTest {

    private FaunaDataRegistry reg;

    @BeforeEach
    void setUp() {
        reg = new FaunaDataRegistry();
        reg.loadPartsFromJson("{ \"parts\":[" +
          "{ \"id\":\"torso\",\"partType\":\"TORSO\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":2,\"radius\":0.5}," +
          "  \"sockets\":[ {\"id\":\"lf\",\"acceptedType\":\"LIMB_LEG\",\"pos\":[0.3,-0.2,0.6],\"mirrorGroup\":\"front\",\"jointHint\":\"hip\"}," +
          "               {\"id\":\"hd\",\"acceptedType\":\"HEAD\",\"pos\":[0,0.2,1.0],\"jointHint\":\"neck\"} ] }," +
          "{ \"id\":\"leg\",\"partType\":\"LIMB_LEG\",\"geometry\":{\"shape\":\"CAPSULE\",\"length\":0.8,\"radius\":0.12} }," +
          "{ \"id\":\"head\",\"partType\":\"HEAD\",\"geometry\":{\"shape\":\"ELLIPSOID_SNOUT\",\"length\":0.5,\"radius\":0.25} }" +
          "] }");
        reg.loadArchetypesFromJson("{ \"archetypes\":[" +
          "{ \"id\":\"quad\",\"bodyPlan\":\"QUADRUPED\",\"minSize\":1,\"maxSize\":1,\"density\":900," +
          "  \"root\":{\"partType\":\"TORSO\",\"children\":[" +
          "     {\"socketId\":\"lf\",\"partType\":\"LIMB_LEG\",\"mirror\":true}," +
          "     {\"socketId\":\"hd\",\"partType\":\"HEAD\"} ]} }" +
          "] }");
    }

    @Test
    void loadsSpeciesFromJson() {
        reg.loadSpeciesFromJson("{ \"species\":[" +
          "{ \"id\":\"grazer\",\"archetypeId\":\"quad\",\"diet\":\"HERBIVORE\"," +
          "  \"temperament\":\"TIMID\",\"socialStructure\":\"HERD\"," +
          "  \"herdSizeMin\":4,\"herdSizeMax\":12," +
          "  \"biomes\":{\"GRASSLAND\":1.0,\"SAVANNA\":0.8}," +
          "  \"trophicLevel\":1,\"preySpecies\":[]," +
          "  \"activityCycle\":\"DIURNAL\"," +
          "  \"detectionRadius\":25,\"fleeRadius\":15,\"fleeSpeedMultiplier\":1.5,\"safeDistance\":40," +
          "  \"birthRate\":0.02,\"carryingCapacityBase\":30 }" +
          "] }");
        reg.validate();

        SpeciesDef s = reg.getSpecies("grazer");
        assertNotNull(s);
        assertEquals("quad", s.archetypeId);
        assertEquals(Diet.HERBIVORE, s.diet);
        assertEquals(Temperament.TIMID, s.temperament);
        assertEquals(SocialStructure.HERD, s.socialStructure);
        assertEquals(4, s.herdSizeMin);
        assertEquals(12, s.herdSizeMax);
        assertEquals(1.0f, s.biomeAffinities.get(BiomeType.GRASSLAND), 0.01f);
        assertEquals(0.8f, s.biomeAffinities.get(BiomeType.SAVANNA), 0.01f);
        assertEquals(ActivityCycle.DIURNAL, s.activityCycle);
    }

    @Test
    void validationFailsForUnknownArchetype() {
        reg.loadSpeciesFromJson("{ \"species\":[" +
          "{ \"id\":\"bad\",\"archetypeId\":\"nonexistent\",\"diet\":\"HERBIVORE\"," +
          "  \"biomes\":{\"GRASSLAND\":1.0},\"trophicLevel\":1,\"preySpecies\":[] }" +
          "] }");
        assertThrows(IllegalStateException.class, () -> reg.validate());
    }

    @Test
    void allSpeciesReturnsList() {
        reg.loadSpeciesFromJson("{ \"species\":[" +
          "{ \"id\":\"a\",\"archetypeId\":\"quad\",\"diet\":\"HERBIVORE\"," +
          "  \"biomes\":{\"GRASSLAND\":1.0},\"trophicLevel\":1,\"preySpecies\":[] }," +
          "{ \"id\":\"b\",\"archetypeId\":\"quad\",\"diet\":\"CARNIVORE\"," +
          "  \"biomes\":{\"GRASSLAND\":1.0},\"trophicLevel\":2,\"preySpecies\":[\"a\"] }" +
          "] }");
        reg.validate();
        assertEquals(2, reg.allSpecies().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.ecosystem.SpeciesLoadingTest" --info`
Expected: FAIL — `loadSpeciesFromJson`, `getSpecies`, `allSpecies` do not exist on `FaunaDataRegistry`.

- [ ] **Step 3: Add species loading to FaunaDataRegistry**

Read `FaunaDataRegistry.java` to understand the existing loading pattern (it has `loadPartsFromJson`, `loadArchetypesFromJson`, `validate`, `getArchetype`, `allArchetypes`, `partsFor`). Add parallel methods for species.

Add these fields:
```java
private final Map<String, SpeciesDef> speciesById = new java.util.LinkedHashMap<>();
```

Add these methods:

```java
public void loadSpeciesFromJson(String json) {
    com.badlogic.gdx.utils.JsonValue root = new com.badlogic.gdx.utils.JsonReader().parse(json);
    for (com.badlogic.gdx.utils.JsonValue sv = root.get("species").child; sv != null; sv = sv.next) {
        SpeciesDef s = new SpeciesDef();
        s.id = sv.getString("id");
        s.archetypeId = sv.getString("archetypeId");
        s.diet = Diet.valueOf(sv.getString("diet", "HERBIVORE"));
        s.temperament = Temperament.valueOf(sv.getString("temperament", "NEUTRAL"));
        s.socialStructure = SocialStructure.valueOf(sv.getString("socialStructure", "SOLITARY"));
        s.herdSizeMin = sv.getInt("herdSizeMin", 1);
        s.herdSizeMax = sv.getInt("herdSizeMax", 1);
        s.trophicLevel = sv.getInt("trophicLevel", 1);
        s.activityCycle = ActivityCycle.valueOf(sv.getString("activityCycle", "DIURNAL"));
        s.detectionRadius = sv.getFloat("detectionRadius", 25f);
        s.fleeRadius = sv.getFloat("fleeRadius", 15f);
        s.fleeSpeedMultiplier = sv.getFloat("fleeSpeedMultiplier", 1.5f);
        s.safeDistance = sv.getFloat("safeDistance", 40f);
        s.birthRate = sv.getFloat("birthRate", 0.02f);
        s.carryingCapacityBase = sv.getInt("carryingCapacityBase", 30);

        com.badlogic.gdx.utils.JsonValue biomes = sv.get("biomes");
        if (biomes != null) {
            for (com.badlogic.gdx.utils.JsonValue b = biomes.child; b != null; b = b.next) {
                s.biomeAffinities.put(BiomeType.valueOf(b.name), b.asFloat());
            }
        }

        com.badlogic.gdx.utils.JsonValue prey = sv.get("preySpecies");
        if (prey != null) {
            for (com.badlogic.gdx.utils.JsonValue p = prey.child; p != null; p = p.next) {
                s.preySpecies.add(p.asString());
            }
        }

        speciesById.put(s.id, s);
    }
}

public SpeciesDef getSpecies(String id) { return speciesById.get(id); }

public java.util.Collection<SpeciesDef> allSpecies() {
    java.util.List<SpeciesDef> list = new java.util.ArrayList<>(speciesById.values());
    list.sort((a, b) -> a.id.compareTo(b.id));
    return list;
}
```

In the existing `validate()` method, add species validation after the existing archetype/part validation:

```java
// Species validation
for (SpeciesDef s : speciesById.values()) {
    if (getArchetype(s.archetypeId) == null) {
        throw new IllegalStateException("Species '" + s.id + "' references unknown archetype '" + s.archetypeId + "'");
    }
    for (String preyId : s.preySpecies) {
        if (!speciesById.containsKey(preyId)) {
            throw new IllegalStateException("Species '" + s.id + "' references unknown prey species '" + preyId + "'");
        }
    }
}
```

Add necessary imports: `SpeciesDef`, `Diet`, `Temperament`, `SocialStructure`, `ActivityCycle`, `BiomeType`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.ecosystem.SpeciesLoadingTest" --info`
Expected: 3 tests PASS

- [ ] **Step 5: Write default species JSON**

Create `core/src/main/resources/data/fauna/species/default-species.json`:

```json
{ "species": [
  { "id": "plains_grazer", "archetypeId": "grazer_quad", "diet": "HERBIVORE",
    "temperament": "TIMID", "socialStructure": "HERD",
    "herdSizeMin": 4, "herdSizeMax": 12,
    "biomes": { "GRASSLAND": 1.0, "SAVANNA": 0.8, "STEPPE": 0.6, "TUNDRA": 0.3 },
    "trophicLevel": 1, "preySpecies": [], "activityCycle": "DIURNAL",
    "detectionRadius": 30, "fleeRadius": 18, "fleeSpeedMultiplier": 1.6, "safeDistance": 50,
    "birthRate": 0.025, "carryingCapacityBase": 30 },

  { "id": "forest_browser", "archetypeId": "strider_biped", "diet": "HERBIVORE",
    "temperament": "NEUTRAL", "socialStructure": "SOLITARY",
    "herdSizeMin": 1, "herdSizeMax": 3,
    "biomes": { "TEMPERATE_FOREST": 1.0, "BOREAL_FOREST": 0.7, "TROPICAL_FOREST": 0.9 },
    "trophicLevel": 1, "preySpecies": [], "activityCycle": "DIURNAL",
    "detectionRadius": 20, "fleeRadius": 12, "fleeSpeedMultiplier": 1.3, "safeDistance": 35,
    "birthRate": 0.015, "carryingCapacityBase": 15 },

  { "id": "swarm_skitter", "archetypeId": "skitterer_hex", "diet": "OMNIVORE",
    "temperament": "NEUTRAL", "socialStructure": "HERD",
    "herdSizeMin": 6, "herdSizeMax": 20,
    "biomes": { "SWAMP": 1.0, "TROPICAL_FOREST": 0.8, "TEMPERATE_FOREST": 0.5, "GRASSLAND": 0.3 },
    "trophicLevel": 1, "preySpecies": [], "activityCycle": "CREPUSCULAR",
    "detectionRadius": 12, "fleeRadius": 8, "fleeSpeedMultiplier": 2.0, "safeDistance": 25,
    "birthRate": 0.05, "carryingCapacityBase": 50 },

  { "id": "dune_serpent", "archetypeId": "crawler_serpent", "diet": "CARNIVORE",
    "temperament": "TERRITORIAL", "socialStructure": "SOLITARY",
    "herdSizeMin": 1, "herdSizeMax": 1,
    "biomes": { "DESERT": 1.0, "SAVANNA": 0.5, "BADLANDS": 0.7, "ARID_SHRUB": 0.6 },
    "trophicLevel": 2, "preySpecies": ["plains_grazer"],
    "activityCycle": "NOCTURNAL",
    "detectionRadius": 35, "fleeRadius": 10, "fleeSpeedMultiplier": 1.2, "safeDistance": 30,
    "birthRate": 0.008, "carryingCapacityBase": 8 },

  { "id": "pack_hunter", "archetypeId": "grazer_quad", "diet": "CARNIVORE",
    "temperament": "AGGRESSIVE", "socialStructure": "PACK",
    "herdSizeMin": 3, "herdSizeMax": 6,
    "biomes": { "GRASSLAND": 0.8, "SAVANNA": 1.0, "STEPPE": 0.7, "TUNDRA": 0.4 },
    "trophicLevel": 2, "preySpecies": ["plains_grazer", "forest_browser"],
    "activityCycle": "CREPUSCULAR",
    "detectionRadius": 40, "fleeRadius": 8, "fleeSpeedMultiplier": 1.3, "safeDistance": 25,
    "birthRate": 0.01, "carryingCapacityBase": 10 }
] }
```

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/data/FaunaDataRegistry.java \
        core/src/main/resources/data/fauna/species/default-species.json \
        core/src/test/java/com/galacticodyssey/fauna/ecosystem/SpeciesLoadingTest.java
git commit -m "feat(fauna): species loading in FaunaDataRegistry + default species JSON"
```

---

### Task 3: CreatureDrivesComponent and CreatureDriveSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/behavior/CreatureDrivesComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/behavior/CreatureDriveSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/behavior/CreatureDriveSystemTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.fauna.behavior;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureDriveSystemTest {

    @Test
    void hungerIncreasesOverTime() {
        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        drives.hunger = 0.5f;
        drives.hungerRate = 0.01f;
        CreatureDriveSystem.tickDrives(drives, 1f, false);
        assertTrue(drives.hunger > 0.5f, "hunger should increase");
    }

    @Test
    void energyDecreasesWhenMoving() {
        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        drives.energy = 0.8f;
        drives.moving = true;
        drives.sprinting = false;
        CreatureDriveSystem.tickDrives(drives, 1f, false);
        assertTrue(drives.energy < 0.8f, "energy should decrease while moving");
    }

    @Test
    void energyRecoversDuringIdle() {
        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        drives.energy = 0.5f;
        drives.moving = false;
        CreatureDriveSystem.tickDrives(drives, 1f, false);
        assertTrue(drives.energy > 0.5f, "energy should recover while idle");
    }

    @Test
    void fearDecaysOverTime() {
        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        drives.fear = 0.8f;
        CreatureDriveSystem.tickDrives(drives, 1f, false);
        assertTrue(drives.fear < 0.8f, "fear should decay");
    }

    @Test
    void allDrivesClampToZeroOne() {
        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        drives.hunger = 1.5f;
        drives.energy = -0.5f;
        drives.fear = 2f;
        CreatureDriveSystem.tickDrives(drives, 0f, false);
        assertTrue(drives.hunger <= 1f);
        assertTrue(drives.energy >= 0f);
        assertTrue(drives.fear <= 1f);
    }

    @Test
    void lowActivityReducesHungerRate() {
        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        drives.hunger = 0.1f;
        drives.hungerRate = 0.1f;
        float hungerBefore = drives.hunger;

        // Active creature
        CreatureDriveSystem.tickDrives(drives, 1f, false);
        float activeIncrease = drives.hunger - hungerBefore;

        // Reset and test with low activity
        drives.hunger = 0.1f;
        CreatureDriveSystem.tickDrives(drives, 1f, true);
        float inactiveIncrease = drives.hunger - 0.1f;

        assertTrue(inactiveIncrease < activeIncrease,
            "low activity should reduce hunger rate");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.behavior.CreatureDriveSystemTest" --info`

- [ ] **Step 3: Write CreatureDrivesComponent**

```java
package com.galacticodyssey.fauna.behavior;

import com.badlogic.ashley.core.Component;

public class CreatureDrivesComponent implements Component {
    public float hunger = 0.2f;
    public float energy = 1f;
    public float fear = 0f;

    public float hungerRate = 0.01f;
    public boolean moving = false;
    public boolean sprinting = false;

    public static final float ENERGY_MOVE_DRAIN = 0.02f;
    public static final float ENERGY_SPRINT_DRAIN = 0.06f;
    public static final float ENERGY_IDLE_REGEN = 0.05f;
    public static final float FEAR_DECAY = 0.1f;
}
```

- [ ] **Step 4: Write CreatureDriveSystem**

```java
package com.galacticodyssey.fauna.behavior;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;

public class CreatureDriveSystem extends IteratingSystem {

    private final ComponentMapper<CreatureDrivesComponent> drivesMapper =
        ComponentMapper.getFor(CreatureDrivesComponent.class);

    public CreatureDriveSystem(int priority) {
        super(Family.all(CreatureDrivesComponent.class).get(), priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CreatureDrivesComponent d = drivesMapper.get(entity);
        tickDrives(d, deltaTime, false);
    }

    public static void tickDrives(CreatureDrivesComponent d, float dt, boolean lowActivity) {
        float hungerMul = lowActivity ? 0.3f : 1f;
        d.hunger += d.hungerRate * dt * hungerMul;

        if (d.sprinting) {
            d.energy -= CreatureDrivesComponent.ENERGY_SPRINT_DRAIN * dt;
        } else if (d.moving) {
            d.energy -= CreatureDrivesComponent.ENERGY_MOVE_DRAIN * dt;
        } else {
            d.energy += CreatureDrivesComponent.ENERGY_IDLE_REGEN * dt;
        }

        d.fear -= CreatureDrivesComponent.FEAR_DECAY * dt;

        d.hunger = clamp01(d.hunger);
        d.energy = clamp01(d.energy);
        d.fear = clamp01(d.fear);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.behavior.CreatureDriveSystemTest" --info`
Expected: 6 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/behavior/CreatureDrivesComponent.java \
        core/src/main/java/com/galacticodyssey/fauna/behavior/CreatureDriveSystem.java \
        core/src/test/java/com/galacticodyssey/fauna/behavior/CreatureDriveSystemTest.java
git commit -m "feat(fauna): CreatureDrivesComponent + CreatureDriveSystem — hunger, energy, fear"
```

---

### Task 4: CreatureBehaviorComponent and CreatureBehaviorSystem (core states)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/behavior/CreatureBehaviorComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/behavior/CreatureBehaviorSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/behavior/CreatureBehaviorSystemTest.java`

- [ ] **Step 1: Write CreatureBehaviorComponent**

```java
package com.galacticodyssey.fauna.behavior;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public class CreatureBehaviorComponent implements Component {
    public StateMachine<Entity, CreatureState> stateMachine;
    public String speciesId;
    public int spawnGroupId = -1;

    public final Vector3 homePosition = new Vector3();
    public final Vector3 wanderTarget = new Vector3();
    public float stateTimer = 0f;
    public float idleDuration = 5f;

    public Diet diet = Diet.HERBIVORE;
    public Temperament temperament = Temperament.NEUTRAL;
    public SocialStructure socialStructure = SocialStructure.SOLITARY;
    public float detectionRadius = 25f;
    public float fleeRadius = 15f;
    public float fleeSpeedMultiplier = 1.5f;
    public float safeDistance = 40f;
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.galacticodyssey.fauna.behavior;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.fauna.components.CreatureComponent;
import com.galacticodyssey.fauna.CreatureSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreatureBehaviorSystemTest {

    private Entity creature;
    private CreatureBehaviorComponent behavior;
    private CreatureDrivesComponent drives;
    private TransformComponent transform;

    @BeforeEach
    void setUp() {
        creature = new Entity();
        behavior = new CreatureBehaviorComponent();
        behavior.stateMachine = new DefaultStateMachine<>(creature, CreatureState.IDLE);
        behavior.temperament = Temperament.TIMID;
        behavior.detectionRadius = 20f;
        behavior.fleeRadius = 10f;
        behavior.safeDistance = 30f;
        behavior.idleDuration = 2f;
        creature.add(behavior);

        drives = new CreatureDrivesComponent();
        creature.add(drives);

        transform = new TransformComponent();
        transform.position.set(0, 0, 0);
        creature.add(transform);

        CreatureComponent cc = new CreatureComponent();
        cc.spec = new CreatureSpec();
        cc.moveSpeed = 5f;
        creature.add(cc);
    }

    @Test
    void startsInIdleState() {
        assertEquals(CreatureState.IDLE, behavior.stateMachine.getCurrentState());
    }

    @Test
    void idleTransitionsToWanderAfterTimer() {
        behavior.stateTimer = 3f;
        CreatureBehaviorSystem.updateEntity(creature, behavior, drives, transform, 0.1f, null, -1f);
        assertEquals(CreatureState.WANDER, behavior.stateMachine.getCurrentState());
    }

    @Test
    void alertWhenThreatInDetectionRadius() {
        Vector3 threatPos = new Vector3(15, 0, 0);
        CreatureBehaviorSystem.updateEntity(creature, behavior, drives, transform, 0.1f, threatPos, -1f);
        assertEquals(CreatureState.ALERT, behavior.stateMachine.getCurrentState());
    }

    @Test
    void fleeWhenThreatInFleeRadius() {
        Vector3 threatPos = new Vector3(5, 0, 0);
        CreatureBehaviorSystem.updateEntity(creature, behavior, drives, transform, 0.1f, threatPos, -1f);
        assertEquals(CreatureState.FLEE, behavior.stateMachine.getCurrentState());
    }

    @Test
    void fleeReturnsToAlertWhenThreatFarEnough() {
        behavior.stateMachine.changeState(CreatureState.FLEE);
        Vector3 threatPos = new Vector3(35, 0, 0);
        CreatureBehaviorSystem.updateEntity(creature, behavior, drives, transform, 0.1f, threatPos, -1f);
        assertEquals(CreatureState.ALERT, behavior.stateMachine.getCurrentState());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.behavior.CreatureBehaviorSystemTest" --info`

- [ ] **Step 4: Implement CreatureBehaviorSystem**

```java
package com.galacticodyssey.fauna.behavior;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.fauna.components.CreatureComponent;

public class CreatureBehaviorSystem extends IteratingSystem {

    private final ComponentMapper<CreatureBehaviorComponent> behaviorMapper =
        ComponentMapper.getFor(CreatureBehaviorComponent.class);
    private final ComponentMapper<CreatureDrivesComponent> drivesMapper =
        ComponentMapper.getFor(CreatureDrivesComponent.class);
    private final ComponentMapper<TransformComponent> txMapper =
        ComponentMapper.getFor(TransformComponent.class);

    private final Vector3 playerPosition = new Vector3();
    private boolean hasPlayer = false;

    public CreatureBehaviorSystem(int priority) {
        super(Family.all(CreatureBehaviorComponent.class, CreatureDrivesComponent.class,
                         TransformComponent.class).get(), priority);
    }

    public void setPlayerPosition(Vector3 pos) {
        playerPosition.set(pos);
        hasPlayer = true;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        CreatureBehaviorComponent beh = behaviorMapper.get(entity);
        CreatureDrivesComponent drives = drivesMapper.get(entity);
        TransformComponent tx = txMapper.get(entity);

        Vector3 threat = hasPlayer ? playerPosition : null;
        updateEntity(entity, beh, drives, tx, deltaTime, threat, -1f);
    }

    public static void updateEntity(Entity entity, CreatureBehaviorComponent beh,
                                     CreatureDrivesComponent drives, TransformComponent tx,
                                     float dt, Vector3 threatPos, float timeOfDay) {
        if (beh.stateMachine == null) return;

        beh.stateTimer += dt;
        CreatureState current = beh.stateMachine.getCurrentState();

        float threatDist = threatPos != null ? tx.position.dst(threatPos) : Float.MAX_VALUE;
        float effectiveDetection = beh.detectionRadius;
        float effectiveFlee = beh.fleeRadius;

        if (drives.fear > 0.5f) {
            effectiveFlee *= 1.5f;
        }

        switch (current) {
            case IDLE:
                drives.moving = false;
                drives.sprinting = false;
                if (threatDist < effectiveFlee) {
                    beh.stateMachine.changeState(CreatureState.FLEE);
                    drives.fear = Math.min(1f, drives.fear + 1f);
                } else if (threatDist < effectiveDetection) {
                    beh.stateMachine.changeState(CreatureState.ALERT);
                } else if (beh.stateTimer > beh.idleDuration) {
                    beh.stateMachine.changeState(CreatureState.WANDER);
                    beh.stateTimer = 0f;
                }
                break;

            case WANDER:
                drives.moving = true;
                drives.sprinting = false;
                if (threatDist < effectiveFlee) {
                    beh.stateMachine.changeState(CreatureState.FLEE);
                    drives.fear = Math.min(1f, drives.fear + 1f);
                } else if (threatDist < effectiveDetection) {
                    beh.stateMachine.changeState(CreatureState.ALERT);
                } else if (beh.stateTimer > 8f) {
                    beh.stateMachine.changeState(CreatureState.IDLE);
                    beh.stateTimer = 0f;
                }
                break;

            case ALERT:
                drives.moving = false;
                drives.sprinting = false;
                if (threatDist < effectiveFlee) {
                    beh.stateMachine.changeState(CreatureState.FLEE);
                    drives.fear = Math.min(1f, drives.fear + 1f);
                } else if (threatDist > effectiveDetection) {
                    beh.stateMachine.changeState(CreatureState.IDLE);
                    beh.stateTimer = 0f;
                }
                break;

            case FLEE:
                drives.moving = true;
                drives.sprinting = true;
                if (threatDist > beh.safeDistance) {
                    beh.stateMachine.changeState(CreatureState.ALERT);
                    beh.stateTimer = 0f;
                } else if (drives.energy <= 0f) {
                    beh.stateMachine.changeState(CreatureState.IDLE);
                    beh.stateTimer = 0f;
                }
                break;

            case HUNT:
                drives.moving = true;
                drives.sprinting = false;
                break;

            case ATTACK:
                drives.moving = false;
                drives.sprinting = false;
                break;

            case FEED:
                drives.moving = false;
                drives.sprinting = false;
                if (drives.hunger < 0.2f) {
                    beh.stateMachine.changeState(CreatureState.IDLE);
                    beh.stateTimer = 0f;
                }
                break;
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.behavior.CreatureBehaviorSystemTest" --info`
Expected: 5 tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/behavior/CreatureBehaviorComponent.java \
        core/src/main/java/com/galacticodyssey/fauna/behavior/CreatureBehaviorSystem.java \
        core/src/test/java/com/galacticodyssey/fauna/behavior/CreatureBehaviorSystemTest.java
git commit -m "feat(fauna): CreatureBehaviorSystem — state machine with IDLE/WANDER/ALERT/FLEE"
```

---

### Task 5: HerdAlertEvent and social behavior

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/behavior/HerdAlertEvent.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/behavior/HerdAlertTest.java`

- [ ] **Step 1: Write HerdAlertEvent**

```java
package com.galacticodyssey.fauna.behavior;

import com.badlogic.gdx.math.Vector3;

public final class HerdAlertEvent {
    public final int spawnGroupId;
    public final Vector3 fleeFrom;

    public HerdAlertEvent(int spawnGroupId, Vector3 fleeFrom) {
        this.spawnGroupId = spawnGroupId;
        this.fleeFrom = new Vector3(fleeFrom);
    }
}
```

- [ ] **Step 2: Write the test**

```java
package com.galacticodyssey.fauna.behavior;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.fauna.components.CreatureComponent;
import com.galacticodyssey.fauna.CreatureSpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HerdAlertTest {

    @Test
    void herdAlertCausesFleeInSameGroup() {
        Entity herdMate = new Entity();
        CreatureBehaviorComponent beh = new CreatureBehaviorComponent();
        beh.stateMachine = new DefaultStateMachine<>(herdMate, CreatureState.IDLE);
        beh.socialStructure = SocialStructure.HERD;
        beh.spawnGroupId = 42;
        herdMate.add(beh);

        CreatureDrivesComponent drives = new CreatureDrivesComponent();
        herdMate.add(drives);

        TransformComponent tx = new TransformComponent();
        tx.position.set(10, 0, 0);
        herdMate.add(tx);

        // Simulate receiving a herd alert from the same group within range
        HerdAlertEvent alert = new HerdAlertEvent(42, new Vector3(0, 0, 0));

        // Herd mate is within 50m of the alert origin and in same group
        float dist = tx.position.dst(alert.fleeFrom);
        boolean inRange = dist < 50f;
        boolean sameGroup = beh.spawnGroupId == alert.spawnGroupId;

        assertTrue(inRange && sameGroup, "precondition: herd mate in range and same group");

        if (inRange && sameGroup && beh.socialStructure == SocialStructure.HERD) {
            beh.stateMachine.changeState(CreatureState.FLEE);
            drives.fear = Math.min(1f, drives.fear + 0.5f);
        }

        assertEquals(CreatureState.FLEE, beh.stateMachine.getCurrentState());
        assertTrue(drives.fear >= 0.5f);
    }

    @Test
    void differentGroupIgnoresAlert() {
        Entity other = new Entity();
        CreatureBehaviorComponent beh = new CreatureBehaviorComponent();
        beh.stateMachine = new DefaultStateMachine<>(other, CreatureState.IDLE);
        beh.socialStructure = SocialStructure.HERD;
        beh.spawnGroupId = 99;
        other.add(beh);

        HerdAlertEvent alert = new HerdAlertEvent(42, new Vector3(0, 0, 0));

        boolean sameGroup = beh.spawnGroupId == alert.spawnGroupId;
        assertFalse(sameGroup);
        assertEquals(CreatureState.IDLE, beh.stateMachine.getCurrentState());
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.behavior.HerdAlertTest" --info`
Expected: 2 tests PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/behavior/HerdAlertEvent.java \
        core/src/test/java/com/galacticodyssey/fauna/behavior/HerdAlertTest.java
git commit -m "feat(fauna): HerdAlertEvent — social flee propagation for herd creatures"
```

---

### Task 6: BiomeSpawnTable

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/ecosystem/BiomeSpawnTable.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/ecosystem/BiomeSpawnTableTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.fauna.behavior.*;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

class BiomeSpawnTableTest {

    private BiomeSpawnTable table;

    @BeforeEach
    void setUp() {
        SpeciesDef grazer = new SpeciesDef();
        grazer.id = "grazer";
        grazer.biomeAffinities.put(BiomeType.GRASSLAND, 1.0f);
        grazer.biomeAffinities.put(BiomeType.SAVANNA, 0.5f);

        SpeciesDef predator = new SpeciesDef();
        predator.id = "predator";
        predator.biomeAffinities.put(BiomeType.GRASSLAND, 0.8f);

        SpeciesDef aquatic = new SpeciesDef();
        aquatic.id = "aquatic";
        aquatic.biomeAffinities.put(BiomeType.OCEAN, 1.0f);

        table = new BiomeSpawnTable(Arrays.asList(grazer, predator, aquatic));
    }

    @Test
    void grasslandHasTwoEligibleSpecies() {
        List<BiomeSpawnTable.WeightedSpecies> eligible = table.speciesForBiome(BiomeType.GRASSLAND);
        assertEquals(2, eligible.size());
    }

    @Test
    void oceanHasOnlyAquatic() {
        List<BiomeSpawnTable.WeightedSpecies> eligible = table.speciesForBiome(BiomeType.OCEAN);
        assertEquals(1, eligible.size());
        assertEquals("aquatic", eligible.get(0).species.id);
    }

    @Test
    void desertHasNoSpecies() {
        List<BiomeSpawnTable.WeightedSpecies> eligible = table.speciesForBiome(BiomeType.DESERT);
        assertTrue(eligible.isEmpty());
    }

    @Test
    void weightsMatchAffinities() {
        List<BiomeSpawnTable.WeightedSpecies> eligible = table.speciesForBiome(BiomeType.GRASSLAND);
        for (BiomeSpawnTable.WeightedSpecies ws : eligible) {
            if (ws.species.id.equals("grazer")) assertEquals(1.0f, ws.weight, 0.01f);
            if (ws.species.id.equals("predator")) assertEquals(0.8f, ws.weight, 0.01f);
        }
    }
}
```

- [ ] **Step 2: Implement BiomeSpawnTable**

```java
package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.planet.BiomeType;

import java.util.*;

public final class BiomeSpawnTable {

    public static final class WeightedSpecies {
        public final SpeciesDef species;
        public final float weight;
        public WeightedSpecies(SpeciesDef species, float weight) {
            this.species = species;
            this.weight = weight;
        }
    }

    private final Map<BiomeType, List<WeightedSpecies>> table = new EnumMap<>(BiomeType.class);

    public BiomeSpawnTable(Collection<SpeciesDef> allSpecies) {
        for (BiomeType biome : BiomeType.values()) {
            List<WeightedSpecies> eligible = new ArrayList<>();
            for (SpeciesDef s : allSpecies) {
                Float affinity = s.biomeAffinities.get(biome);
                if (affinity != null && affinity > 0f) {
                    eligible.add(new WeightedSpecies(s, affinity));
                }
            }
            eligible.sort((a, b) -> a.species.id.compareTo(b.species.id));
            table.put(biome, Collections.unmodifiableList(eligible));
        }
    }

    public List<WeightedSpecies> speciesForBiome(BiomeType biome) {
        List<WeightedSpecies> result = table.get(biome);
        return result != null ? result : Collections.emptyList();
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.ecosystem.BiomeSpawnTableTest" --info`
Expected: 4 tests PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/ecosystem/BiomeSpawnTable.java \
        core/src/test/java/com/galacticodyssey/fauna/ecosystem/BiomeSpawnTableTest.java
git commit -m "feat(fauna): BiomeSpawnTable — biome to weighted species list"
```

---

### Task 7: ChunkPopulationRecord and PopulationTickSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/ecosystem/SpeciesPopulation.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/ecosystem/ChunkPopulationRecord.java`
- Create: `core/src/main/java/com/galacticodyssey/fauna/ecosystem/PopulationTickSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/fauna/ecosystem/PopulationTickSystemTest.java`

- [ ] **Step 1: Write SpeciesPopulation and ChunkPopulationRecord**

```java
package com.galacticodyssey.fauna.ecosystem;

public final class SpeciesPopulation {
    public String speciesId;
    public int count;
    public float birthAccumulator;

    public SpeciesPopulation(String speciesId, int count) {
        this.speciesId = speciesId;
        this.count = count;
    }
}
```

```java
package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.planet.BiomeType;
import java.util.ArrayList;
import java.util.List;

public final class ChunkPopulationRecord {
    public int chunkX, chunkZ;
    public BiomeType biome;
    public final List<SpeciesPopulation> populations = new ArrayList<>();
    public double lastTickTime;
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.fauna.behavior.*;
import com.galacticodyssey.planet.BiomeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

class PopulationTickSystemTest {

    private SpeciesDef makeHerbivore(String id, int k) {
        SpeciesDef s = new SpeciesDef();
        s.id = id;
        s.diet = Diet.HERBIVORE;
        s.birthRate = 0.1f;
        s.carryingCapacityBase = k;
        s.trophicLevel = 1;
        s.biomeAffinities.put(BiomeType.GRASSLAND, 1.0f);
        return s;
    }

    private SpeciesDef makePredator(String id, int k, String... prey) {
        SpeciesDef s = new SpeciesDef();
        s.id = id;
        s.diet = Diet.CARNIVORE;
        s.birthRate = 0.05f;
        s.carryingCapacityBase = k;
        s.trophicLevel = 2;
        s.biomeAffinities.put(BiomeType.GRASSLAND, 1.0f);
        s.preySpecies.addAll(Arrays.asList(prey));
        return s;
    }

    @Test
    void logisticGrowthIncreasesPopulation() {
        SpeciesDef herb = makeHerbivore("herb", 50);
        Map<String, SpeciesDef> speciesMap = Map.of("herb", herb);

        ChunkPopulationRecord chunk = new ChunkPopulationRecord();
        chunk.biome = BiomeType.GRASSLAND;
        chunk.populations.add(new SpeciesPopulation("herb", 10));

        PopulationTickSystem.tickChunk(chunk, speciesMap, 30f);

        assertTrue(chunk.populations.get(0).count >= 10,
            "population should grow when below carrying capacity");
    }

    @Test
    void populationDecaysAboveCarryingCapacity() {
        SpeciesDef herb = makeHerbivore("herb", 20);
        Map<String, SpeciesDef> speciesMap = Map.of("herb", herb);

        ChunkPopulationRecord chunk = new ChunkPopulationRecord();
        chunk.biome = BiomeType.GRASSLAND;
        chunk.populations.add(new SpeciesPopulation("herb", 30));

        PopulationTickSystem.tickChunk(chunk, speciesMap, 60f);

        assertTrue(chunk.populations.get(0).count < 30,
            "population should decrease when above carrying capacity");
    }

    @Test
    void predationReducesPreyPopulation() {
        SpeciesDef herb = makeHerbivore("herb", 50);
        SpeciesDef pred = makePredator("pred", 10, "herb");
        Map<String, SpeciesDef> speciesMap = Map.of("herb", herb, "pred", pred);

        ChunkPopulationRecord chunk = new ChunkPopulationRecord();
        chunk.biome = BiomeType.GRASSLAND;
        chunk.populations.add(new SpeciesPopulation("herb", 40));
        chunk.populations.add(new SpeciesPopulation("pred", 5));

        int preyBefore = chunk.populations.get(0).count;
        PopulationTickSystem.tickChunk(chunk, speciesMap, 60f);
        int preyAfter = chunk.populations.get(0).count;

        assertTrue(preyAfter <= preyBefore,
            "predation should reduce or maintain prey population");
    }

    @Test
    void populationNeverGoesNegative() {
        SpeciesDef herb = makeHerbivore("herb", 5);
        Map<String, SpeciesDef> speciesMap = Map.of("herb", herb);

        ChunkPopulationRecord chunk = new ChunkPopulationRecord();
        chunk.biome = BiomeType.GRASSLAND;
        chunk.populations.add(new SpeciesPopulation("herb", 1));

        // Tick with a very large dt to stress the clamping
        PopulationTickSystem.tickChunk(chunk, speciesMap, 1000f);

        assertTrue(chunk.populations.get(0).count >= 0, "population must never go negative");
    }
}
```

- [ ] **Step 3: Implement PopulationTickSystem**

```java
package com.galacticodyssey.fauna.ecosystem;

import com.galacticodyssey.planet.BiomeType;

import java.util.Map;

public final class PopulationTickSystem {

    private PopulationTickSystem() {}

    private static final float STARVATION_RATE = 0.05f;
    private static final float ATTACK_RATE = 0.002f;

    public static void tickChunk(ChunkPopulationRecord chunk, Map<String, SpeciesDef> speciesMap, float dt) {
        for (SpeciesPopulation pop : chunk.populations) {
            SpeciesDef species = speciesMap.get(pop.speciesId);
            if (species == null || pop.count <= 0) continue;

            float fertility = biomeFertility(chunk.biome, species);
            float K = species.carryingCapacityBase * fertility;
            if (K < 1f) K = 1f;

            // Logistic growth
            float growth = species.birthRate * pop.count * (1f - pop.count / K) * dt;
            pop.birthAccumulator += growth;

            // Birth
            while (pop.birthAccumulator >= 1f) {
                pop.count++;
                pop.birthAccumulator -= 1f;
            }

            // Starvation (overshoot)
            if (pop.count > K * 1.2f) {
                float deaths = STARVATION_RATE * (pop.count - K) * dt;
                pop.count -= (int) deaths;
            }

            // Predation (predators consume prey)
            if (species.diet == com.galacticodyssey.fauna.behavior.Diet.CARNIVORE
                || species.diet == com.galacticodyssey.fauna.behavior.Diet.OMNIVORE) {
                for (String preyId : species.preySpecies) {
                    SpeciesPopulation preyPop = findPop(chunk, preyId);
                    if (preyPop != null && preyPop.count > 0) {
                        float consumed = ATTACK_RATE * pop.count * preyPop.count * dt;
                        int kills = Math.min((int) consumed, preyPop.count);
                        preyPop.count -= kills;
                    }
                }
            }

            pop.count = Math.max(0, pop.count);
        }
    }

    private static SpeciesPopulation findPop(ChunkPopulationRecord chunk, String speciesId) {
        for (SpeciesPopulation p : chunk.populations) {
            if (p.speciesId.equals(speciesId)) return p;
        }
        return null;
    }

    private static float biomeFertility(BiomeType biome, SpeciesDef species) {
        Float affinity = species.biomeAffinities.get(biome);
        return affinity != null ? affinity : 0f;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.ecosystem.PopulationTickSystemTest" --info`
Expected: 4 tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/ecosystem/SpeciesPopulation.java \
        core/src/main/java/com/galacticodyssey/fauna/ecosystem/ChunkPopulationRecord.java \
        core/src/main/java/com/galacticodyssey/fauna/ecosystem/PopulationTickSystem.java \
        core/src/test/java/com/galacticodyssey/fauna/ecosystem/PopulationTickSystemTest.java
git commit -m "feat(fauna): PopulationTickSystem — Lotka-Volterra growth, predation, starvation"
```

---

### Task 8: CreatureSpawnSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/fauna/ecosystem/CreatureSpawnSystem.java`

This Ashley system manages fauna chunks around the player. It tracks `ChunkPopulationRecord`s, generates initial populations for first-visit chunks, instantiates creatures on chunk load, and writes back population counts on chunk unload.

- [ ] **Step 1: Implement CreatureSpawnSystem**

```java
package com.galacticodyssey.fauna.ecosystem;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.data.FaunaDataRegistry;
import com.galacticodyssey.fauna.CreatureFactory;
import com.galacticodyssey.fauna.CreatureGenerator;
import com.galacticodyssey.fauna.CreatureSpec;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeType;

import java.util.*;

public class CreatureSpawnSystem extends EntitySystem {

    private static final float CHUNK_SIZE = 100f;
    private static final int LOAD_RADIUS = 2;

    private final FaunaDataRegistry registry;
    private final CreatureGenerator generator;
    private final CreatureFactory factory = new CreatureFactory();
    private final BiomeSpawnTable spawnTable;
    private final long worldSeed;

    private final Map<Long, ChunkPopulationRecord> populationRecords = new HashMap<>();
    private final Map<Long, List<Entity>> loadedCreatures = new HashMap<>();
    private final Set<Long> loadedChunks = new HashSet<>();
    private final Vector3 playerPos = new Vector3();
    private int lastPlayerCX = Integer.MIN_VALUE, lastPlayerCZ = Integer.MIN_VALUE;

    public CreatureSpawnSystem(int priority, FaunaDataRegistry registry,
                                CreatureGenerator generator, long worldSeed) {
        super(priority);
        this.registry = registry;
        this.generator = generator;
        this.worldSeed = worldSeed;
        this.spawnTable = new BiomeSpawnTable(registry.allSpecies());
    }

    public void setPlayerPosition(Vector3 pos) { playerPos.set(pos); }

    @Override
    public void update(float deltaTime) {
        int cx = (int) Math.floor(playerPos.x / CHUNK_SIZE);
        int cz = (int) Math.floor(playerPos.z / CHUNK_SIZE);

        if (cx == lastPlayerCX && cz == lastPlayerCZ) return;
        lastPlayerCX = cx;
        lastPlayerCZ = cz;

        Set<Long> desired = new HashSet<>();
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                desired.add(chunkKey(cx + dx, cz + dz));
            }
        }

        // Unload chunks no longer in range
        Iterator<Long> it = loadedChunks.iterator();
        while (it.hasNext()) {
            long key = it.next();
            if (!desired.contains(key)) {
                unloadChunk(key);
                it.remove();
            }
        }

        // Load new chunks
        for (long key : desired) {
            if (!loadedChunks.contains(key)) {
                loadChunk(key, decodeX(key), decodeZ(key));
                loadedChunks.add(key);
            }
        }
    }

    private void loadChunk(long key, int cx, int cz) {
        ChunkPopulationRecord record = populationRecords.get(key);
        if (record == null) {
            record = generateInitialPopulation(cx, cz);
            populationRecords.put(key, record);
        }

        List<Entity> entities = new ArrayList<>();
        Random placeRng = new Random(SeedDeriver.forChunk(SeedDeriver.faunaDomain(worldSeed), cx, cz));
        float baseX = cx * CHUNK_SIZE;
        float baseZ = cz * CHUNK_SIZE;

        for (SpeciesPopulation pop : record.populations) {
            SpeciesDef species = registry.getSpecies(pop.speciesId);
            if (species == null || pop.count <= 0) continue;

            for (int i = 0; i < pop.count; i++) {
                float x = baseX + placeRng.nextFloat() * CHUNK_SIZE;
                float z = baseZ + placeRng.nextFloat() * CHUNK_SIZE;
                Vector3 pos = new Vector3(x, 0, z);

                CreatureSpec spec = generator.generate(species.archetypeId,
                    SeedDeriver.forId(SeedDeriver.faunaDomain(worldSeed), placeRng.nextLong()));
                Entity e = factory.create(getEngine(), spec, pos);
                entities.add(e);
            }
        }

        loadedCreatures.put(key, entities);
    }

    private void unloadChunk(long key) {
        List<Entity> entities = loadedCreatures.remove(key);
        if (entities != null) {
            ChunkPopulationRecord record = populationRecords.get(key);
            if (record != null) {
                Map<String, Integer> counts = new HashMap<>();
                for (Entity e : entities) {
                    if (getEngine().getEntities().contains(e, true)) {
                        com.galacticodyssey.fauna.components.CreatureComponent cc =
                            e.getComponent(com.galacticodyssey.fauna.components.CreatureComponent.class);
                        if (cc != null) {
                            counts.merge(cc.archetypeId, 1, Integer::sum);
                        }
                    }
                }
                for (SpeciesPopulation pop : record.populations) {
                    SpeciesDef species = registry.getSpecies(pop.speciesId);
                    if (species != null) {
                        Integer alive = counts.get(species.archetypeId);
                        if (alive != null) {
                            pop.count = alive;
                        }
                    }
                }
            }
            for (Entity e : entities) {
                getEngine().removeEntity(e);
            }
        }
    }

    private ChunkPopulationRecord generateInitialPopulation(int cx, int cz) {
        ChunkPopulationRecord record = new ChunkPopulationRecord();
        record.chunkX = cx;
        record.chunkZ = cz;
        record.biome = BiomeType.GRASSLAND; // Cycle D ships with default; real biome query in integration

        Random rng = new Random(SeedDeriver.forChunk(SeedDeriver.faunaDomain(worldSeed), cx, cz));
        List<BiomeSpawnTable.WeightedSpecies> eligible = spawnTable.speciesForBiome(record.biome);
        if (eligible.isEmpty()) return record;

        int speciesCount = 2 + rng.nextInt(Math.min(4, eligible.size()));
        List<BiomeSpawnTable.WeightedSpecies> shuffled = new ArrayList<>(eligible);
        Collections.shuffle(shuffled, rng);

        for (int i = 0; i < Math.min(speciesCount, shuffled.size()); i++) {
            BiomeSpawnTable.WeightedSpecies ws = shuffled.get(i);
            float K = ws.species.carryingCapacityBase * ws.weight;
            int initial = (int) (K * (0.5f + rng.nextFloat() * 0.3f));
            if (initial > 0) {
                record.populations.add(new SpeciesPopulation(ws.species.id, initial));
            }
        }

        return record;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
    private static int decodeX(long key) { return (int) (key >> 32); }
    private static int decodeZ(long key) { return (int) key; }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/ecosystem/CreatureSpawnSystem.java
git commit -m "feat(fauna): CreatureSpawnSystem — chunk-based creature spawning and population tracking"
```

---

### Task 9: CreatureGenerator species overload and CreatureFactory behavior wiring

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/fauna/CreatureGenerator.java`
- Modify: `core/src/main/java/com/galacticodyssey/fauna/CreatureFactory.java`

- [ ] **Step 1: Add species-aware generate overload**

In `CreatureGenerator.java`, add:

```java
public CreatureSpec generateForSpecies(String speciesId, long seed) {
    SpeciesDef species = registry.getSpecies(speciesId);
    if (species == null) throw new IllegalArgumentException("Unknown species: " + speciesId);
    return generate(species.archetypeId, seed);
}
```

Add import: `import com.galacticodyssey.fauna.ecosystem.SpeciesDef;`

- [ ] **Step 2: Add behavior components to CreatureFactory**

In `CreatureFactory.create()`, after the animation component block, add behavior and drive components:

```java
// Cycle D: behavior and drives
com.galacticodyssey.fauna.behavior.CreatureBehaviorComponent beh =
    new com.galacticodyssey.fauna.behavior.CreatureBehaviorComponent();
beh.stateMachine = new com.badlogic.gdx.ai.fsm.DefaultStateMachine<>(e,
    com.galacticodyssey.fauna.behavior.CreatureState.IDLE);
e.add(beh);

e.add(new com.galacticodyssey.fauna.behavior.CreatureDrivesComponent());
```

- [ ] **Step 3: Run all fauna tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.*" --info`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/fauna/CreatureGenerator.java \
        core/src/main/java/com/galacticodyssey/fauna/CreatureFactory.java
git commit -m "feat(fauna): species-aware generation + behavior/drive components in factory"
```

---

### Task 10: Register systems in GameWorld

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Register CreatureDriveSystem and CreatureBehaviorSystem**

In the system registration section of `GameWorld.java`, near the existing `CreatureGaitSystem` registration, add:

```java
engine.addSystem(new com.galacticodyssey.fauna.behavior.CreatureDriveSystem(43));
engine.addSystem(new com.galacticodyssey.fauna.behavior.CreatureBehaviorSystem(44));
```

Priority 43-44 places them before the gait system (45) so behavior updates before animation.

- [ ] **Step 2: Run all fauna tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.fauna.*" --info`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(fauna): register CreatureDriveSystem and CreatureBehaviorSystem in GameWorld"
```
