# NPC System Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate valuable legacy NPC concepts (personality traits, backstory hooks, broad roles, extra stats) into the active ECS system, then delete the unused legacy classes to eliminate the parallel system.

**Architecture:** The new ECS system (`NpcStatsComponent`, `NpcIdentityComponent`, `NpcGenerator` in `npc/data/`) is the sole active system. Legacy classes (`NPCData`, `NPCRole`, `NPCGenerator`) are unused prototypes with design concepts worth keeping. We add two new ECS components (`NpcPersonalityComponent`, `NpcBackstoryComponent`), a broad `NpcRole` enum, two extra stats (`persuasion`, `stealth`), and role-based stat weighting to the ECS generator. Then we delete all legacy classes and their test.

**Tech Stack:** Java 17, Ashley ECS, libGDX, JUnit 5

---

## File Structure

**New files:**
- `core/src/main/java/com/galacticodyssey/npc/NpcRole.java` — broad role enum (crew + station + adversarial + civilian)
- `core/src/main/java/com/galacticodyssey/npc/components/NpcPersonalityComponent.java` — ECS component: traits, loyalty, greed, bravery
- `core/src/main/java/com/galacticodyssey/npc/components/NpcBackstoryComponent.java` — ECS component: list of backstory hooks
- `core/src/test/java/com/galacticodyssey/npc/data/NpcPersonalityGenerationTest.java` — tests for trait generation (contradiction rejection, uniqueness, determinism)
- `core/src/test/java/com/galacticodyssey/npc/data/NpcBackstoryGenerationTest.java` — tests for backstory hook generation
- `core/src/test/java/com/galacticodyssey/npc/data/NpcRoleStatDistributionTest.java` — tests for role-based stat weighting

**Modified files:**
- `core/src/main/java/com/galacticodyssey/npc/components/NpcStatsComponent.java` — add `persuasion`, `stealth` fields
- `core/src/main/java/com/galacticodyssey/npc/components/NpcIdentityComponent.java` — add `NpcRole role` field, `float age` field
- `core/src/main/java/com/galacticodyssey/npc/data/SpeciesDefinition.java` — add `persuasionMod`, `stealthMod`
- `core/src/main/java/com/galacticodyssey/npc/data/BackgroundDefinition.java` — add `persuasionMod`, `stealthMod`
- `core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java` — add personality/backstory generation, role-based stat weighting, new stat fields
- `core/src/test/java/com/galacticodyssey/npc/data/NpcGeneratorTest.java` — add tests for new stats, role field

**Deleted files:**
- `core/src/main/java/com/galacticodyssey/npc/NPCData.java`
- `core/src/main/java/com/galacticodyssey/npc/NPCRole.java`
- `core/src/main/java/com/galacticodyssey/npc/NPCGenerator.java`
- `core/src/test/java/com/galacticodyssey/npc/NPCGeneratorTest.java`

**Kept as-is (already belong to new system, just moved conceptually):**
- `core/src/main/java/com/galacticodyssey/npc/PersonalityTrait.java` — stays in `npc` package, unchanged
- `core/src/main/java/com/galacticodyssey/npc/BackstoryHook.java` — stays in `npc` package, unchanged
- `core/src/main/java/com/galacticodyssey/npc/HookType.java` — stays in `npc` package, unchanged

---

### Task 1: Add `persuasion` and `stealth` stats to NpcStatsComponent

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/npc/components/NpcStatsComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/npc/data/SpeciesDefinition.java`
- Modify: `core/src/main/java/com/galacticodyssey/npc/data/BackgroundDefinition.java`
- Modify: `core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java`
- Modify: `core/src/test/java/com/galacticodyssey/npc/data/NpcGeneratorTest.java`

- [ ] **Step 1: Write failing test for new stats on generated NPC**

Add to `NpcGeneratorTest.java`:

```java
@Test
void generatedNpcHasPersuasionAndStealthStats() {
    Entity npc = generator.generate(engine, 12345L);
    NpcStatsComponent stats = STATS_M.get(npc);
    assertStatInRange(stats.persuasion);
    assertStatInRange(stats.stealth);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.data.NpcGeneratorTest.generatedNpcHasPersuasionAndStealthStats"`
Expected: FAIL — `persuasion` and `stealth` fields don't exist on `NpcStatsComponent`.

- [ ] **Step 3: Add fields to NpcStatsComponent**

In `NpcStatsComponent.java`, add after the `combat` field:

```java
public float persuasion;
public float stealth;
```

Full file becomes:

```java
package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;

public class NpcStatsComponent implements Component {
    public float accuracy;
    public float repair;
    public float medical;
    public float piloting;
    public float science;
    public float combat;
    public float persuasion;
    public float stealth;
}
```

- [ ] **Step 4: Add modifier fields to SpeciesDefinition**

In `SpeciesDefinition.java`, add after `combatMod`:

```java
public float persuasionMod;
public float stealthMod;
```

- [ ] **Step 5: Add modifier fields to BackgroundDefinition**

In `BackgroundDefinition.java`, add after `combatMod`:

```java
public float persuasionMod;
public float stealthMod;
```

- [ ] **Step 6: Wire new stats in NpcGenerator**

In `NpcGenerator.java`, in the `generate(Engine, long, String, String)` method, add after the `stats.combat` line:

```java
stats.persuasion = clampStat(rollBase(npcSeed, 16) + species.persuasionMod + background.persuasionMod);
stats.stealth    = clampStat(rollBase(npcSeed, 17) + species.stealthMod    + background.stealthMod);
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.data.NpcGeneratorTest"`
Expected: ALL PASS.

- [ ] **Step 8: Also update the existing `generatedNpcStatsAreInValidRange` test to cover new stats**

In `NpcGeneratorTest.java`, in the `generatedNpcStatsAreInValidRange` test, add:

```java
assertStatInRange(stats.persuasion);
assertStatInRange(stats.stealth);
```

- [ ] **Step 9: Run full test suite and verify**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.data.NpcGeneratorTest"`
Expected: ALL PASS.

- [ ] **Step 10: Commit**

```
git add core/src/main/java/com/galacticodyssey/npc/components/NpcStatsComponent.java \
       core/src/main/java/com/galacticodyssey/npc/data/SpeciesDefinition.java \
       core/src/main/java/com/galacticodyssey/npc/data/BackgroundDefinition.java \
       core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java \
       core/src/test/java/com/galacticodyssey/npc/data/NpcGeneratorTest.java
git commit -m "feat(npc): add persuasion and stealth stats to ECS NPC system"
```

---

### Task 2: Create broad `NpcRole` enum and add it to `NpcIdentityComponent`

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/NpcRole.java`
- Modify: `core/src/main/java/com/galacticodyssey/npc/components/NpcIdentityComponent.java`
- Modify: `core/src/test/java/com/galacticodyssey/npc/data/NpcGeneratorTest.java`
- Modify: `core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java`

- [ ] **Step 1: Write failing test for NpcRole on generated NPC**

Add to `NpcGeneratorTest.java`:

```java
@Test
void generatedNpcHasRoleAndAge() {
    Entity npc = generator.generate(engine, 12345L, "human", "military", NpcRole.MARINE);
    NpcIdentityComponent id = ID_M.get(npc);
    assertEquals(NpcRole.MARINE, id.role);
    assertTrue(id.age >= 18f && id.age <= 70f);
}
```

Add the import: `import com.galacticodyssey.npc.NpcRole;`

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.data.NpcGeneratorTest.generatedNpcHasRoleAndAge"`
Expected: FAIL — `NpcRole` class doesn't exist.

- [ ] **Step 3: Create NpcRole enum**

Create `core/src/main/java/com/galacticodyssey/npc/NpcRole.java`:

```java
package com.galacticodyssey.npc;

import com.galacticodyssey.npc.crew.CrewRole;

public enum NpcRole {
    // Crew roles (map 1:1 to CrewRole)
    PILOT,
    ENGINEER,
    GUNNER,
    MEDIC,
    NAVIGATOR,
    SCIENCE_OFFICER,
    MARINE,
    // Station roles
    MERCHANT,
    BARTENDER,
    INFORMATION_BROKER,
    MECHANIC,
    // Adversarial roles
    PIRATE_CAPTAIN,
    BOUNTY_HUNTER,
    MERCENARY,
    SMUGGLER,
    // Civilian roles
    COLONIST,
    SCIENTIST;

    public CrewRole toCrewRole() {
        switch (this) {
            case PILOT:           return CrewRole.PILOT;
            case ENGINEER:        return CrewRole.ENGINEER;
            case GUNNER:          return CrewRole.GUNNER;
            case MEDIC:           return CrewRole.MEDIC;
            case NAVIGATOR:       return CrewRole.NAVIGATOR;
            case SCIENCE_OFFICER: return CrewRole.SCIENTIST;
            case MARINE:          return CrewRole.MARINE;
            default:              return null;
        }
    }

    public boolean isCrewRole() {
        return toCrewRole() != null;
    }
}
```

- [ ] **Step 4: Add `role` and `age` fields to NpcIdentityComponent**

In `NpcIdentityComponent.java`, add the import and fields:

```java
import com.galacticodyssey.npc.NpcRole;
```

Add fields after `recruitable`:

```java
public NpcRole role;
public float age;
```

Full file:

```java
package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.npc.NpcDisposition;
import com.galacticodyssey.npc.NpcRole;

public class NpcIdentityComponent implements Component {
    public String npcId;
    public String name;
    public String species;
    public String background;
    public String portraitId;
    public NpcDisposition disposition = NpcDisposition.NEUTRAL;
    public String factionId;
    public boolean recruitable;
    public NpcRole role;
    public float age;
}
```

- [ ] **Step 5: Add `generate` overload with `NpcRole` to NpcGenerator**

In `NpcGenerator.java`, add a new `generate` overload and wire role/age into the existing method:

```java
public Entity generate(Engine engine, long seed, String speciesId, String backgroundId, NpcRole role) {
    long npcSeed = SeedDeriver.npcDomain(seed);
    SpeciesDefinition species = registry.getSpecies(speciesId);
    BackgroundDefinition background = registry.getBackground(backgroundId);

    Entity entity = new Entity();

    NpcIdentityComponent identity = new NpcIdentityComponent();
    identity.npcId = "npc_" + Long.toHexString(npcSeed);
    identity.species = speciesId;
    identity.background = backgroundId;
    identity.name = pickName(npcSeed, speciesId);
    identity.portraitId = pickFromList(npcSeed, 6, species.portraitIds);
    identity.disposition = NpcDisposition.NEUTRAL;
    identity.recruitable = role != null && role.isCrewRole();
    identity.role = role;
    identity.age = rollAge(npcSeed);
    entity.add(identity);

    NpcStatsComponent stats = new NpcStatsComponent();
    stats.accuracy   = clampStat(rollBase(npcSeed, 10) + species.accuracyMod   + background.accuracyMod);
    stats.repair     = clampStat(rollBase(npcSeed, 11) + species.repairMod     + background.repairMod);
    stats.medical    = clampStat(rollBase(npcSeed, 12) + species.medicalMod    + background.medicalMod);
    stats.piloting   = clampStat(rollBase(npcSeed, 13) + species.pilotingMod   + background.pilotingMod);
    stats.science    = clampStat(rollBase(npcSeed, 14) + species.scienceMod    + background.scienceMod);
    stats.combat     = clampStat(rollBase(npcSeed, 15) + species.combatMod     + background.combatMod);
    stats.persuasion = clampStat(rollBase(npcSeed, 16) + species.persuasionMod + background.persuasionMod);
    stats.stealth    = clampStat(rollBase(npcSeed, 17) + species.stealthMod    + background.stealthMod);
    entity.add(stats);

    engine.addEntity(entity);
    return entity;
}
```

Add the `rollAge` helper:

```java
private float rollAge(long npcSeed) {
    long derived = SeedDeriver.forId(npcSeed, 20);
    float normalized = ((derived & Long.MAX_VALUE) % 10000) / 10000f;
    return normalized * 52f + 18f; // 18-70
}
```

Update the existing two-arg `generate(Engine, long)` to call through with a null role:

```java
public Entity generate(Engine engine, long seed) {
    long npcSeed = SeedDeriver.npcDomain(seed);

    List<String> speciesIds = registry.getSpeciesIds();
    String speciesId = speciesIds.get(pickIndex(npcSeed, 0, speciesIds.size()));

    List<BackgroundDefinition> backgrounds = registry.getAllBackgrounds();
    String backgroundId = backgrounds.get(pickIndex(npcSeed, 1, backgrounds.size())).id;

    return generate(engine, seed, speciesId, backgroundId, null);
}
```

Update the existing three-arg `generate(Engine, long, String, String)` to delegate:

```java
public Entity generate(Engine engine, long seed, String speciesId, String backgroundId) {
    return generate(engine, seed, speciesId, backgroundId, null);
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.data.NpcGeneratorTest"`
Expected: ALL PASS.

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/npc/NpcRole.java \
       core/src/main/java/com/galacticodyssey/npc/components/NpcIdentityComponent.java \
       core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java \
       core/src/test/java/com/galacticodyssey/npc/data/NpcGeneratorTest.java
git commit -m "feat(npc): add broad NpcRole enum and age to NPC identity"
```

---

### Task 3: Create `NpcPersonalityComponent` and wire into generator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/components/NpcPersonalityComponent.java`
- Create: `core/src/test/java/com/galacticodyssey/npc/data/NpcPersonalityGenerationTest.java`
- Modify: `core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java`

- [ ] **Step 1: Write failing tests for personality generation**

Create `core/src/test/java/com/galacticodyssey/npc/data/NpcPersonalityGenerationTest.java`:

```java
package com.galacticodyssey.npc.data;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.npc.PersonalityTrait;
import com.galacticodyssey.npc.components.NpcPersonalityComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NpcPersonalityGenerationTest {

    private NpcDataRegistry registry;
    private NpcGenerator generator;
    private Engine engine;

    private static final ComponentMapper<NpcPersonalityComponent> PERSONALITY_M =
        ComponentMapper.getFor(NpcPersonalityComponent.class);

    private static final long FIXED_SEED = 12345L;

    @BeforeEach
    void setUp() {
        registry = new NpcDataRegistry();
        engine = new Engine();

        SpeciesDefinition human = new SpeciesDefinition();
        human.id = "human";
        human.name = "Human";
        human.portraitIds.addAll(List.of("portrait_human_01"));
        registry.registerSpecies(human);

        BackgroundDefinition military = new BackgroundDefinition();
        military.id = "military";
        military.name = "Military";
        registry.registerBackground(military);

        registry.registerNames("human",
            List.of("James", "Elena"),
            List.of("Voss", "Chen"));

        generator = new NpcGenerator(registry);
    }

    @Test
    void generatedNpcHasPersonalityComponent() {
        Entity npc = generator.generate(engine, FIXED_SEED);
        NpcPersonalityComponent personality = PERSONALITY_M.get(npc);
        assertNotNull(personality);
    }

    @Test
    void personalityHasTwoToFourTraits() {
        for (int i = 0; i < 200; i++) {
            Entity npc = generator.generate(engine, SeedDeriver.forId(FIXED_SEED, i));
            NpcPersonalityComponent p = PERSONALITY_M.get(npc);
            assertTrue(p.traits.size() >= 2 && p.traits.size() <= 4,
                "NPC " + i + " has " + p.traits.size() + " traits, expected 2-4");
        }
    }

    @Test
    void traitsAreUnique() {
        for (int i = 0; i < 200; i++) {
            Entity npc = generator.generate(engine, SeedDeriver.forId(FIXED_SEED, i));
            NpcPersonalityComponent p = PERSONALITY_M.get(npc);
            Set<PersonalityTrait> unique = new HashSet<>(p.traits);
            assertEquals(unique.size(), p.traits.size(),
                "NPC " + i + " has duplicate traits: " + p.traits);
        }
    }

    @Test
    void noContradictoryTraits() {
        for (int i = 0; i < 500; i++) {
            Entity npc = generator.generate(engine, SeedDeriver.forId(FIXED_SEED, i));
            NpcPersonalityComponent p = PERSONALITY_M.get(npc);
            Set<String> names = new HashSet<>();
            for (PersonalityTrait t : p.traits) {
                names.add(t.name());
            }
            assertFalse(names.contains("BRAVE") && names.contains("COWARDLY"),
                "NPC " + i + " has BRAVE + COWARDLY");
            assertFalse(names.contains("LOYAL") && names.contains("VINDICTIVE"),
                "NPC " + i + " has LOYAL + VINDICTIVE");
            assertFalse(names.contains("GENEROUS") && names.contains("GREEDY"),
                "NPC " + i + " has GENEROUS + GREEDY");
            assertFalse(names.contains("DISCIPLINED") && names.contains("RECKLESS"),
                "NPC " + i + " has DISCIPLINED + RECKLESS");
            assertFalse(names.contains("CURIOUS") && names.contains("PRAGMATIC"),
                "NPC " + i + " has CURIOUS + PRAGMATIC");
        }
    }

    @Test
    void personalityIsDeterministic() {
        Entity npc1 = generator.generate(engine, FIXED_SEED);
        Entity npc2 = generator.generate(engine, FIXED_SEED);
        NpcPersonalityComponent p1 = PERSONALITY_M.get(npc1);
        NpcPersonalityComponent p2 = PERSONALITY_M.get(npc2);
        assertEquals(p1.traits, p2.traits);
        assertEquals(p1.loyalty, p2.loyalty, 1e-6f);
        assertEquals(p1.greed, p2.greed, 1e-6f);
        assertEquals(p1.bravery, p2.bravery, 1e-6f);
    }

    @Test
    void personalityScoresAreInRange() {
        for (int i = 0; i < 200; i++) {
            Entity npc = generator.generate(engine, SeedDeriver.forId(FIXED_SEED, i));
            NpcPersonalityComponent p = PERSONALITY_M.get(npc);
            assertTrue(p.loyalty >= 0f && p.loyalty <= 1f,
                "loyalty " + p.loyalty + " out of range");
            assertTrue(p.greed >= 0f && p.greed <= 1f,
                "greed " + p.greed + " out of range");
            assertTrue(p.bravery >= 0f && p.bravery <= 1f,
                "bravery " + p.bravery + " out of range");
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.data.NpcPersonalityGenerationTest"`
Expected: FAIL — `NpcPersonalityComponent` class doesn't exist.

- [ ] **Step 3: Create NpcPersonalityComponent**

Create `core/src/main/java/com/galacticodyssey/npc/components/NpcPersonalityComponent.java`:

```java
package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.npc.PersonalityTrait;

import java.util.ArrayList;
import java.util.List;

public class NpcPersonalityComponent implements Component {
    public final List<PersonalityTrait> traits = new ArrayList<>();
    public float loyalty;
    public float greed;
    public float bravery;
}
```

- [ ] **Step 4: Add personality generation to NpcGenerator**

In `NpcGenerator.java`, add the following imports at the top:

```java
import com.galacticodyssey.npc.PersonalityTrait;
import com.galacticodyssey.npc.components.NpcPersonalityComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
```

Add the contradictory pairs constant:

```java
private static final String[][] CONTRADICTORY_PAIRS = {
    {"BRAVE", "COWARDLY"},
    {"LOYAL", "VINDICTIVE"},
    {"GENEROUS", "GREEDY"},
    {"DISCIPLINED", "RECKLESS"},
    {"CURIOUS", "PRAGMATIC"},
};
```

In the `generate(Engine, long, String, String, NpcRole)` method, add before `engine.addEntity(entity)`:

```java
NpcPersonalityComponent personality = generatePersonality(npcSeed);
entity.add(personality);
```

Add the helper methods:

```java
private NpcPersonalityComponent generatePersonality(long npcSeed) {
    Random rng = new Random(SeedDeriver.forId(npcSeed, 30));
    NpcPersonalityComponent personality = new NpcPersonalityComponent();

    int traitCount = 2 + rng.nextInt(3); // 2-4
    PersonalityTrait[] allTraits = PersonalityTrait.values();
    Set<String> selectedNames = new HashSet<>();
    int attempts = 0;
    while (personality.traits.size() < traitCount && attempts < 100) {
        attempts++;
        PersonalityTrait candidate = allTraits[rng.nextInt(allTraits.length)];
        if (selectedNames.contains(candidate.name())) continue;
        if (contradictsAny(candidate, selectedNames)) continue;
        personality.traits.add(candidate);
        selectedNames.add(candidate.name());
    }

    personality.loyalty = rng.nextFloat() * 0.8f + 0.1f;  // 0.1-0.9
    personality.greed   = rng.nextFloat() * 0.7f + 0.1f;  // 0.1-0.8
    personality.bravery = rng.nextFloat() * 0.7f + 0.2f;  // 0.2-0.9

    return personality;
}

private boolean contradictsAny(PersonalityTrait candidate, Set<String> existing) {
    String name = candidate.name();
    for (String[] pair : CONTRADICTORY_PAIRS) {
        if (pair[0].equals(name) && existing.contains(pair[1])) return true;
        if (pair[1].equals(name) && existing.contains(pair[0])) return true;
    }
    return false;
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.data.NpcPersonalityGenerationTest"`
Expected: ALL PASS.

- [ ] **Step 6: Run existing tests to check for regressions**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.data.NpcGeneratorTest"`
Expected: ALL PASS.

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/npc/components/NpcPersonalityComponent.java \
       core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java \
       core/src/test/java/com/galacticodyssey/npc/data/NpcPersonalityGenerationTest.java
git commit -m "feat(npc): add personality traits with contradiction rejection to ECS generator"
```

---

### Task 4: Create `NpcBackstoryComponent` and wire into generator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/components/NpcBackstoryComponent.java`
- Create: `core/src/test/java/com/galacticodyssey/npc/data/NpcBackstoryGenerationTest.java`
- Modify: `core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java`

- [ ] **Step 1: Write failing tests for backstory generation**

Create `core/src/test/java/com/galacticodyssey/npc/data/NpcBackstoryGenerationTest.java`:

```java
package com.galacticodyssey.npc.data;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.npc.BackstoryHook;
import com.galacticodyssey.npc.HookType;
import com.galacticodyssey.npc.NpcRole;
import com.galacticodyssey.npc.components.NpcBackstoryComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NpcBackstoryGenerationTest {

    private NpcDataRegistry registry;
    private NpcGenerator generator;
    private Engine engine;

    private static final ComponentMapper<NpcBackstoryComponent> BACKSTORY_M =
        ComponentMapper.getFor(NpcBackstoryComponent.class);

    private static final long FIXED_SEED = 12345L;

    @BeforeEach
    void setUp() {
        registry = new NpcDataRegistry();
        engine = new Engine();

        SpeciesDefinition human = new SpeciesDefinition();
        human.id = "human";
        human.name = "Human";
        human.portraitIds.addAll(List.of("portrait_human_01"));
        registry.registerSpecies(human);

        BackgroundDefinition military = new BackgroundDefinition();
        military.id = "military";
        military.name = "Military";
        registry.registerBackground(military);

        registry.registerNames("human",
            List.of("James", "Elena"),
            List.of("Voss", "Chen"));

        generator = new NpcGenerator(registry);
    }

    @Test
    void generatedNpcHasBackstoryComponent() {
        Entity npc = generator.generate(engine, FIXED_SEED);
        assertNotNull(BACKSTORY_M.get(npc));
    }

    @Test
    void backstoryHasOneToTwoHooks() {
        for (int i = 0; i < 200; i++) {
            Entity npc = generator.generate(engine, SeedDeriver.forId(FIXED_SEED, i));
            NpcBackstoryComponent b = BACKSTORY_M.get(npc);
            assertTrue(b.hooks.size() >= 1 && b.hooks.size() <= 2,
                "NPC " + i + " has " + b.hooks.size() + " hooks, expected 1-2");
        }
    }

    @Test
    void hooksHaveUniqueTypes() {
        for (int i = 0; i < 200; i++) {
            Entity npc = generator.generate(engine, SeedDeriver.forId(FIXED_SEED, i));
            NpcBackstoryComponent b = BACKSTORY_M.get(npc);
            Set<HookType> types = new HashSet<>();
            for (BackstoryHook hook : b.hooks) {
                assertTrue(types.add(hook.type),
                    "NPC " + i + " has duplicate hook type: " + hook.type);
            }
        }
    }

    @Test
    void pirateCaptainAlwaysHasWantedCriminalHook() {
        for (int i = 0; i < 100; i++) {
            Entity npc = generator.generate(engine,
                SeedDeriver.forId(FIXED_SEED, i), "human", "military", NpcRole.PIRATE_CAPTAIN);
            NpcBackstoryComponent b = BACKSTORY_M.get(npc);
            boolean hasWanted = false;
            for (BackstoryHook hook : b.hooks) {
                if (hook.type == HookType.WANTED_CRIMINAL) {
                    hasWanted = true;
                    break;
                }
            }
            assertTrue(hasWanted,
                "PIRATE_CAPTAIN " + i + " missing WANTED_CRIMINAL hook");
        }
    }

    @Test
    void backstoryIsDeterministic() {
        Entity npc1 = generator.generate(engine, FIXED_SEED);
        Entity npc2 = generator.generate(engine, FIXED_SEED);
        NpcBackstoryComponent b1 = BACKSTORY_M.get(npc1);
        NpcBackstoryComponent b2 = BACKSTORY_M.get(npc2);
        assertEquals(b1.hooks.size(), b2.hooks.size());
        for (int i = 0; i < b1.hooks.size(); i++) {
            assertEquals(b1.hooks.get(i).type, b2.hooks.get(i).type);
            assertEquals(b1.hooks.get(i).questSeed, b2.hooks.get(i).questSeed);
        }
    }

    @Test
    void hookSummariesAreNonEmpty() {
        Entity npc = generator.generate(engine, FIXED_SEED);
        NpcBackstoryComponent b = BACKSTORY_M.get(npc);
        for (BackstoryHook hook : b.hooks) {
            assertNotNull(hook.summary);
            assertFalse(hook.summary.isEmpty());
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.data.NpcBackstoryGenerationTest"`
Expected: FAIL — `NpcBackstoryComponent` class doesn't exist.

- [ ] **Step 3: Create NpcBackstoryComponent**

Create `core/src/main/java/com/galacticodyssey/npc/components/NpcBackstoryComponent.java`:

```java
package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.npc.BackstoryHook;

import java.util.ArrayList;
import java.util.List;

public class NpcBackstoryComponent implements Component {
    public final List<BackstoryHook> hooks = new ArrayList<>();
}
```

- [ ] **Step 4: Add backstory generation to NpcGenerator**

In `NpcGenerator.java`, add the imports:

```java
import com.galacticodyssey.npc.BackstoryHook;
import com.galacticodyssey.npc.HookType;
import com.galacticodyssey.npc.NpcRole;
import com.galacticodyssey.npc.components.NpcBackstoryComponent;
```

Add the hook summaries constant:

```java
private static final String[] HOOK_SUMMARIES = {
    "Owes a substantial debt to a powerful creditor.",
    "Searching for a family member lost during a colony evacuation.",
    "Wanted by authorities for past crimes.",
    "Served in a military campaign and carries the scars.",
    "Possesses knowledge someone powerful wants kept secret.",
    "Has an unresolved rivalry with another spacer."
};
```

In the `generate(Engine, long, String, String, NpcRole)` method, add before `engine.addEntity(entity)`:

```java
NpcBackstoryComponent backstory = generateBackstory(npcSeed, role);
entity.add(backstory);
```

Add the helper methods:

```java
private NpcBackstoryComponent generateBackstory(long npcSeed, NpcRole role) {
    Random rng = new Random(SeedDeriver.forId(npcSeed, 40));
    NpcBackstoryComponent backstory = new NpcBackstoryComponent();
    HookType[] allHooks = HookType.values();

    int hookCount = 1 + rng.nextInt(2); // 1-2
    Set<HookType> usedTypes = new HashSet<>();

    if (role == NpcRole.PIRATE_CAPTAIN) {
        backstory.hooks.add(makeHook(HookType.WANTED_CRIMINAL, rng));
        usedTypes.add(HookType.WANTED_CRIMINAL);
    }

    while (backstory.hooks.size() < hookCount) {
        HookType type = allHooks[rng.nextInt(allHooks.length)];
        if (usedTypes.contains(type)) continue;
        backstory.hooks.add(makeHook(type, rng));
        usedTypes.add(type);
    }

    return backstory;
}

private BackstoryHook makeHook(HookType type, Random rng) {
    boolean revealed = rng.nextFloat() < 0.3f;
    long questSeed = rng.nextLong();
    String summary = HOOK_SUMMARIES[type.ordinal()];
    return new BackstoryHook(type, revealed, questSeed, summary);
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.data.NpcBackstoryGenerationTest"`
Expected: ALL PASS.

- [ ] **Step 6: Run all NPC tests for regressions**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.*"`
Expected: ALL PASS.

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/npc/components/NpcBackstoryComponent.java \
       core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java \
       core/src/test/java/com/galacticodyssey/npc/data/NpcBackstoryGenerationTest.java
git commit -m "feat(npc): add backstory hooks with pirate-captain guarantee to ECS generator"
```

---

### Task 5: Add role-based stat weighting to NpcGenerator

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/npc/data/NpcRoleStatDistributionTest.java`
- Modify: `core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java`

- [ ] **Step 1: Write failing tests for role-based stat distribution**

Create `core/src/test/java/com/galacticodyssey/npc/data/NpcRoleStatDistributionTest.java`:

```java
package com.galacticodyssey.npc.data;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.npc.NpcRole;
import com.galacticodyssey.npc.components.NpcStatsComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcRoleStatDistributionTest {

    private NpcDataRegistry registry;
    private NpcGenerator generator;
    private Engine engine;

    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);

    private static final long FIXED_SEED = 12345L;

    @BeforeEach
    void setUp() {
        registry = new NpcDataRegistry();
        engine = new Engine();

        SpeciesDefinition human = new SpeciesDefinition();
        human.id = "human";
        human.name = "Human";
        human.portraitIds.addAll(List.of("portrait_human_01"));
        registry.registerSpecies(human);

        BackgroundDefinition bg = new BackgroundDefinition();
        bg.id = "generic";
        bg.name = "Generic";
        registry.registerBackground(bg);

        registry.registerNames("human",
            List.of("James", "Elena"),
            List.of("Voss", "Chen"));

        generator = new NpcGenerator(registry);
    }

    @Test
    void pilotRoleHasHigherPilotingStat() {
        float totalPiloting = 0;
        float totalCombat = 0;
        int count = 100;
        for (int i = 0; i < count; i++) {
            Entity npc = generator.generate(engine,
                SeedDeriver.forId(FIXED_SEED, i), "human", "generic", NpcRole.PILOT);
            NpcStatsComponent stats = STATS_M.get(npc);
            totalPiloting += stats.piloting;
            totalCombat += stats.combat;
        }
        float avgPiloting = totalPiloting / count;
        float avgCombat = totalCombat / count;
        assertTrue(avgPiloting > avgCombat,
            "PILOT avg piloting (" + avgPiloting + ") should exceed avg combat (" + avgCombat + ")");
    }

    @Test
    void medicRoleHasHigherMedicalStat() {
        float totalMedical = 0;
        float totalCombat = 0;
        int count = 100;
        for (int i = 0; i < count; i++) {
            Entity npc = generator.generate(engine,
                SeedDeriver.forId(FIXED_SEED, i), "human", "generic", NpcRole.MEDIC);
            NpcStatsComponent stats = STATS_M.get(npc);
            totalMedical += stats.medical;
            totalCombat += stats.combat;
        }
        float avgMedical = totalMedical / count;
        float avgCombat = totalCombat / count;
        assertTrue(avgMedical > avgCombat,
            "MEDIC avg medical (" + avgMedical + ") should exceed avg combat (" + avgCombat + ")");
    }

    @Test
    void merchantRoleHasHigherPersuasionStat() {
        float totalPersuasion = 0;
        float totalCombat = 0;
        int count = 100;
        for (int i = 0; i < count; i++) {
            Entity npc = generator.generate(engine,
                SeedDeriver.forId(FIXED_SEED, i), "human", "generic", NpcRole.MERCHANT);
            NpcStatsComponent stats = STATS_M.get(npc);
            totalPersuasion += stats.persuasion;
            totalCombat += stats.combat;
        }
        float avgPersuasion = totalPersuasion / count;
        float avgCombat = totalCombat / count;
        assertTrue(avgPersuasion > avgCombat,
            "MERCHANT avg persuasion (" + avgPersuasion + ") should exceed avg combat (" + avgCombat + ")");
    }

    @Test
    void nullRoleProducesUnweightedStats() {
        Entity npc = generator.generate(engine, FIXED_SEED, "human", "generic", null);
        NpcStatsComponent stats = STATS_M.get(npc);
        assertStatInRange(stats.accuracy);
        assertStatInRange(stats.piloting);
        assertStatInRange(stats.persuasion);
    }

    private void assertStatInRange(float stat) {
        assertTrue(stat >= 0f && stat <= 100f,
            "Stat " + stat + " should be in range [0, 100]");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.data.NpcRoleStatDistributionTest"`
Expected: FAIL — role-based weighting not implemented, stats are uniformly distributed so PILOT's piloting won't reliably exceed combat.

- [ ] **Step 3: Add role-based stat weighting to NpcGenerator**

In `NpcGenerator.java`, replace the stat generation section of the `generate(Engine, long, String, String, NpcRole)` method. Instead of directly computing stats, apply a role-based multiplier:

```java
NpcStatsComponent stats = new NpcStatsComponent();
float[] baseStats = new float[] {
    rollBase(npcSeed, 10), // accuracy
    rollBase(npcSeed, 11), // repair
    rollBase(npcSeed, 12), // medical
    rollBase(npcSeed, 13), // piloting
    rollBase(npcSeed, 14), // science
    rollBase(npcSeed, 15), // combat
    rollBase(npcSeed, 16), // persuasion
    rollBase(npcSeed, 17), // stealth
};
float[] weights = roleStatWeights(role);
float[] speciesMods = {
    species.accuracyMod, species.repairMod, species.medicalMod,
    species.pilotingMod, species.scienceMod, species.combatMod,
    species.persuasionMod, species.stealthMod
};
float[] bgMods = {
    background.accuracyMod, background.repairMod, background.medicalMod,
    background.pilotingMod, background.scienceMod, background.combatMod,
    background.persuasionMod, background.stealthMod
};
for (int i = 0; i < 8; i++) {
    baseStats[i] = clampStat(baseStats[i] * weights[i] + speciesMods[i] + bgMods[i]);
}
stats.accuracy   = baseStats[0];
stats.repair     = baseStats[1];
stats.medical    = baseStats[2];
stats.piloting   = baseStats[3];
stats.science    = baseStats[4];
stats.combat     = baseStats[5];
stats.persuasion = baseStats[6];
stats.stealth    = baseStats[7];
entity.add(stats);
```

Add the `roleStatWeights` method. Stat indices: 0=accuracy, 1=repair, 2=medical, 3=piloting, 4=science, 5=combat, 6=persuasion, 7=stealth. Primary stat gets 1.4x weight, secondary gets 1.15x, rest stay at 1.0x:

```java
private float[] roleStatWeights(NpcRole role) {
    float[] w = {1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f};
    if (role == null) return w;
    switch (role) {
        case PILOT:              w[3] = 1.4f; w[0] = 1.15f; break; // piloting primary, accuracy secondary
        case ENGINEER:           w[1] = 1.4f; w[4] = 1.15f; break; // repair primary, science secondary
        case GUNNER:             w[0] = 1.4f; w[5] = 1.15f; break; // accuracy primary, combat secondary
        case MEDIC:              w[2] = 1.4f; w[4] = 1.15f; break; // medical primary, science secondary
        case NAVIGATOR:          w[3] = 1.4f; w[4] = 1.15f; break; // piloting primary, science secondary
        case SCIENCE_OFFICER:    w[4] = 1.4f; w[2] = 1.15f; break; // science primary, medical secondary
        case MARINE:             w[5] = 1.4f; w[0] = 1.15f; break; // combat primary, accuracy secondary
        case MERCHANT:           w[6] = 1.4f; w[7] = 1.15f; break; // persuasion primary, stealth secondary
        case BARTENDER:          w[6] = 1.4f; w[2] = 1.15f; break; // persuasion primary, medical secondary
        case INFORMATION_BROKER: w[6] = 1.4f; w[7] = 1.15f; break; // persuasion primary, stealth secondary
        case MECHANIC:           w[1] = 1.4f; w[0] = 1.15f; break; // repair primary, accuracy secondary
        case PIRATE_CAPTAIN:     w[5] = 1.4f; w[6] = 1.15f; break; // combat primary, persuasion secondary
        case BOUNTY_HUNTER:      w[5] = 1.4f; w[7] = 1.15f; break; // combat primary, stealth secondary
        case MERCENARY:          w[5] = 1.4f; w[0] = 1.15f; break; // combat primary, accuracy secondary
        case SMUGGLER:           w[7] = 1.4f; w[6] = 1.15f; break; // stealth primary, persuasion secondary
        case COLONIST:           w[1] = 1.4f; w[2] = 1.15f; break; // repair primary, medical secondary
        case SCIENTIST:          w[4] = 1.4f; w[2] = 1.15f; break; // science primary, medical secondary
        default: break;
    }
    return w;
}
```

- [ ] **Step 4: Run role stat distribution tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.data.NpcRoleStatDistributionTest"`
Expected: ALL PASS.

- [ ] **Step 5: Run all NPC tests for regressions**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.*"`
Expected: ALL PASS. Note: the determinism test in `NpcGeneratorTest.sameSeedProducesSameNpc` still passes because `null` role gives uniform weights. The test for `generateWithExplicitSpeciesAndBackground` passes because it doesn't assert stat values.

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java \
       core/src/test/java/com/galacticodyssey/npc/data/NpcRoleStatDistributionTest.java
git commit -m "feat(npc): add role-based stat weighting to ECS NPC generator"
```

---

### Task 6: Delete legacy classes and their test

**Files:**
- Delete: `core/src/main/java/com/galacticodyssey/npc/NPCData.java`
- Delete: `core/src/main/java/com/galacticodyssey/npc/NPCRole.java`
- Delete: `core/src/main/java/com/galacticodyssey/npc/NPCGenerator.java`
- Delete: `core/src/test/java/com/galacticodyssey/npc/NPCGeneratorTest.java`

- [ ] **Step 1: Verify no references to legacy classes outside their own files**

Run: `grep -rn "NPCData\|NPCRole\|NPCGenerator" --include="*.java" core/src/` and confirm all hits are in the four files being deleted.

- [ ] **Step 2: Delete legacy files**

```
git rm core/src/main/java/com/galacticodyssey/npc/NPCData.java
git rm core/src/main/java/com/galacticodyssey/npc/NPCRole.java
git rm core/src/main/java/com/galacticodyssey/npc/NPCGenerator.java
git rm core/src/test/java/com/galacticodyssey/npc/NPCGeneratorTest.java
```

- [ ] **Step 3: Run all NPC tests to verify nothing broke**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.*"`
Expected: ALL PASS — only the deleted test is gone, all ECS tests remain green.

- [ ] **Step 4: Run full project build**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL, zero compilation errors, all tests pass.

- [ ] **Step 5: Commit**

```
git add -A
git commit -m "refactor(npc): delete legacy NPCData/NPCRole/NPCGenerator — all concepts now in ECS"
```

---

### Task 7: Final integration verification

**Files:**
- Modify: `core/src/test/java/com/galacticodyssey/npc/data/NpcGeneratorTest.java` (add one integration-style test)

- [ ] **Step 1: Write an integration test that generates a fully-loaded NPC and validates all components**

Add to `NpcGeneratorTest.java`:

```java
@Test
void fullyLoadedNpcHasAllComponents() {
    Entity npc = generator.generate(engine, 12345L, "human", "military",
        com.galacticodyssey.npc.NpcRole.ENGINEER);

    // Identity
    NpcIdentityComponent id = ID_M.get(npc);
    assertNotNull(id);
    assertEquals(com.galacticodyssey.npc.NpcRole.ENGINEER, id.role);
    assertTrue(id.age >= 18f && id.age <= 70f);
    assertTrue(id.recruitable);

    // Stats
    NpcStatsComponent stats = STATS_M.get(npc);
    assertNotNull(stats);
    assertStatInRange(stats.accuracy);
    assertStatInRange(stats.repair);
    assertStatInRange(stats.medical);
    assertStatInRange(stats.piloting);
    assertStatInRange(stats.science);
    assertStatInRange(stats.combat);
    assertStatInRange(stats.persuasion);
    assertStatInRange(stats.stealth);

    // Personality
    ComponentMapper<com.galacticodyssey.npc.components.NpcPersonalityComponent> PM =
        ComponentMapper.getFor(com.galacticodyssey.npc.components.NpcPersonalityComponent.class);
    com.galacticodyssey.npc.components.NpcPersonalityComponent personality = PM.get(npc);
    assertNotNull(personality);
    assertTrue(personality.traits.size() >= 2 && personality.traits.size() <= 4);

    // Backstory
    ComponentMapper<com.galacticodyssey.npc.components.NpcBackstoryComponent> BM =
        ComponentMapper.getFor(com.galacticodyssey.npc.components.NpcBackstoryComponent.class);
    com.galacticodyssey.npc.components.NpcBackstoryComponent backstory = BM.get(npc);
    assertNotNull(backstory);
    assertTrue(backstory.hooks.size() >= 1 && backstory.hooks.size() <= 2);
}
```

- [ ] **Step 2: Run test**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.data.NpcGeneratorTest.fullyLoadedNpcHasAllComponents"`
Expected: PASS.

- [ ] **Step 3: Run full NPC test suite**

Run: `./gradlew :core:test --tests "com.galacticodyssey.npc.*"`
Expected: ALL PASS.

- [ ] **Step 4: Run full project build**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```
git add core/src/test/java/com/galacticodyssey/npc/data/NpcGeneratorTest.java
git commit -m "test(npc): add integration test verifying all NPC components after reconciliation"
```
