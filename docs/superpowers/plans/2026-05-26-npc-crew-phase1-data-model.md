# Phase 1: NPC Data Model & Crew Management — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the foundational data model for NPCs and crew — components, enums, events, data registry, procedural generator, and the two core ECS systems (crew assignment + XP).

**Architecture:** Every NPC is an Ashley entity with `NpcIdentityComponent` + `NpcStatsComponent`. Crew members additionally carry `CrewMemberComponent`. Station entities carry `CrewAssignmentComponent`. All cross-system communication goes through the existing `EventBus`. NPC content (species, backgrounds, perks, names) is data-driven via JSON files loaded by `NpcDataRegistry`. `NpcGenerator` creates deterministic NPCs from seeds using `SeedDeriver`.

**Tech Stack:** Java 17+, libGDX 1.13.5, Ashley ECS 1.7.4, gdx-ai, JUnit 5.11.4, Gradle (Kotlin DSL)

**Spec:** `docs/superpowers/specs/2026-05-26-npc-crew-system-design.md`

---

## File Structure

**Create:**
```
core/src/main/java/com/galacticodyssey/npc/NpcDisposition.java
core/src/main/java/com/galacticodyssey/npc/NpcGoal.java
core/src/main/java/com/galacticodyssey/npc/crew/CrewRole.java
core/src/main/java/com/galacticodyssey/npc/crew/CrewRank.java
core/src/main/java/com/galacticodyssey/npc/crew/MoraleState.java
core/src/main/java/com/galacticodyssey/npc/crew/FireReason.java
core/src/main/java/com/galacticodyssey/npc/components/NpcIdentityComponent.java
core/src/main/java/com/galacticodyssey/npc/components/NpcStatsComponent.java
core/src/main/java/com/galacticodyssey/npc/components/NpcScheduleComponent.java
core/src/main/java/com/galacticodyssey/npc/components/ScheduleEntry.java
core/src/main/java/com/galacticodyssey/npc/crew/CrewMemberComponent.java
core/src/main/java/com/galacticodyssey/npc/crew/CrewAssignmentComponent.java
core/src/main/java/com/galacticodyssey/npc/events/CrewMemberHiredEvent.java
core/src/main/java/com/galacticodyssey/npc/events/CrewMemberFiredEvent.java
core/src/main/java/com/galacticodyssey/npc/events/CrewPromotedEvent.java
core/src/main/java/com/galacticodyssey/npc/events/MoraleChangedEvent.java
core/src/main/java/com/galacticodyssey/npc/events/CrewInsubordinationEvent.java
core/src/main/java/com/galacticodyssey/npc/events/CrewDesertedEvent.java
core/src/main/java/com/galacticodyssey/npc/events/MutinyEvent.java
core/src/main/java/com/galacticodyssey/npc/events/CrewAssignedEvent.java
core/src/main/java/com/galacticodyssey/npc/events/CrewInjuredEvent.java
core/src/main/java/com/galacticodyssey/npc/data/SpeciesDefinition.java
core/src/main/java/com/galacticodyssey/npc/data/BackgroundDefinition.java
core/src/main/java/com/galacticodyssey/npc/data/PerkDefinition.java
core/src/main/java/com/galacticodyssey/npc/data/NpcDataRegistry.java
core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java
core/src/main/java/com/galacticodyssey/npc/systems/CrewAssignmentSystem.java
core/src/main/java/com/galacticodyssey/npc/systems/CrewXPSystem.java
core/src/main/resources/data/npcs/species.json
core/src/main/resources/data/npcs/backgrounds.json
core/src/main/resources/data/npcs/perks.json
core/src/main/resources/data/npcs/names.json
core/src/test/java/com/galacticodyssey/npc/NpcComponentsTest.java
core/src/test/java/com/galacticodyssey/npc/data/NpcDataRegistryTest.java
core/src/test/java/com/galacticodyssey/npc/data/NpcGeneratorTest.java
core/src/test/java/com/galacticodyssey/npc/systems/CrewAssignmentSystemTest.java
core/src/test/java/com/galacticodyssey/npc/systems/CrewXPSystemTest.java
```

**Modify:**
```
core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java  (add NPC_DOMAIN constant)
```

---

### Task 1: NPC and Crew Enums

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/NpcDisposition.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/NpcGoal.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/crew/CrewRole.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/crew/CrewRank.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/crew/MoraleState.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/crew/FireReason.java`

- [ ] **Step 1: Create NpcDisposition enum**

```java
// core/src/main/java/com/galacticodyssey/npc/NpcDisposition.java
package com.galacticodyssey.npc;

public enum NpcDisposition {
    FRIENDLY,
    NEUTRAL,
    HOSTILE
}
```

- [ ] **Step 2: Create NpcGoal enum**

```java
// core/src/main/java/com/galacticodyssey/npc/NpcGoal.java
package com.galacticodyssey.npc;

public enum NpcGoal {
    MAN_STATION,
    REPAIR_SYSTEM,
    HEAL_CREW,
    FIGHT,
    TAKE_COVER,
    PATROL,
    REST,
    FLEE,
    IDLE,
    FOLLOW_PLAYER,
    TRAIN_CREW,
    EXTINGUISH_FIRE
}
```

- [ ] **Step 3: Create CrewRole enum**

`CrewRole` maps each role to the relevant stat on `NpcStatsComponent` and to the `RoomType` it belongs at. The `getRelevantStat` method is used by `CrewAssignmentSystem` to compute effectiveness.

```java
// core/src/main/java/com/galacticodyssey/npc/crew/CrewRole.java
package com.galacticodyssey.npc.crew;

import com.galacticodyssey.npc.components.NpcStatsComponent;
import com.galacticodyssey.ship.RoomType;

public enum CrewRole {
    PILOT,
    GUNNER,
    ENGINEER,
    MEDIC,
    MARINE,
    SCIENTIST,
    NAVIGATOR;

    public float getRelevantStat(NpcStatsComponent stats) {
        switch (this) {
            case PILOT:     return stats.piloting;
            case GUNNER:    return stats.accuracy;
            case ENGINEER:  return stats.repair;
            case MEDIC:     return stats.medical;
            case MARINE:    return stats.combat;
            case SCIENTIST: return stats.science;
            case NAVIGATOR: return stats.piloting;
            default:        return 0f;
        }
    }

    public static CrewRole forRoomType(RoomType roomType) {
        switch (roomType) {
            case COCKPIT:     return PILOT;
            case ENGINE_ROOM: return ENGINEER;
            case MEDBAY:      return MEDIC;
            case ARMORY:      return MARINE;
            default:          return null;
        }
    }
}
```

- [ ] **Step 4: Create CrewRank enum**

XP thresholds and base wages per rank. `nextRank()` returns the next rank or `null` if already at max.

```java
// core/src/main/java/com/galacticodyssey/npc/crew/CrewRank.java
package com.galacticodyssey.npc.crew;

public enum CrewRank {
    RECRUIT(0f, 10f),
    CREWMAN(100f, 20f),
    SPECIALIST(300f, 35f),
    VETERAN(600f, 55f),
    OFFICER(1000f, 80f),
    COMMANDER(1500f, 120f);

    public final float xpThreshold;
    public final float baseWage;

    CrewRank(float xpThreshold, float baseWage) {
        this.xpThreshold = xpThreshold;
        this.baseWage = baseWage;
    }

    public CrewRank nextRank() {
        int next = ordinal() + 1;
        CrewRank[] values = values();
        return next < values.length ? values[next] : null;
    }
}
```

- [ ] **Step 5: Create MoraleState enum**

Derives state from a morale float (0-100).

```java
// core/src/main/java/com/galacticodyssey/npc/crew/MoraleState.java
package com.galacticodyssey.npc.crew;

public enum MoraleState {
    MUTINOUS(0, 24),
    DISGRUNTLED(25, 49),
    GRUMBLING(50, 79),
    CONTENT(80, 100);

    public final int minMorale;
    public final int maxMorale;

    MoraleState(int minMorale, int maxMorale) {
        this.minMorale = minMorale;
        this.maxMorale = maxMorale;
    }

    public static MoraleState fromMorale(float morale) {
        if (morale >= 80f) return CONTENT;
        if (morale >= 50f) return GRUMBLING;
        if (morale >= 25f) return DISGRUNTLED;
        return MUTINOUS;
    }

    public float effectivenessModifier() {
        switch (this) {
            case CONTENT:     return 1.1f;
            case GRUMBLING:   return 1.0f;
            case DISGRUNTLED: return 0.85f;
            case MUTINOUS:    return 0.7f;
            default:          return 1.0f;
        }
    }
}
```

- [ ] **Step 6: Create FireReason enum**

```java
// core/src/main/java/com/galacticodyssey/npc/crew/FireReason.java
package com.galacticodyssey.npc.crew;

public enum FireReason {
    DISMISSED,
    DESERTED,
    KILLED_IN_ACTION,
    MUTINY_QUELLED
}
```

- [ ] **Step 7: Verify compilation**

Run: `.\gradlew.bat core:compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```
git add core/src/main/java/com/galacticodyssey/npc/NpcDisposition.java \
        core/src/main/java/com/galacticodyssey/npc/NpcGoal.java \
        core/src/main/java/com/galacticodyssey/npc/crew/CrewRole.java \
        core/src/main/java/com/galacticodyssey/npc/crew/CrewRank.java \
        core/src/main/java/com/galacticodyssey/npc/crew/MoraleState.java \
        core/src/main/java/com/galacticodyssey/npc/crew/FireReason.java
git commit -m "feat(npc): add NPC and crew enums"
```

---

### Task 2: NPC and Crew Components

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/components/NpcIdentityComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/components/NpcStatsComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/components/ScheduleEntry.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/components/NpcScheduleComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/crew/CrewMemberComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/crew/CrewAssignmentComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/npc/NpcComponentsTest.java`

- [ ] **Step 1: Create NpcIdentityComponent**

```java
// core/src/main/java/com/galacticodyssey/npc/components/NpcIdentityComponent.java
package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.npc.NpcDisposition;

public class NpcIdentityComponent implements Component {
    public String npcId;
    public String name;
    public String species;
    public String background;
    public String portraitId;
    public NpcDisposition disposition = NpcDisposition.NEUTRAL;
    public String factionId;
    public boolean recruitable;
}
```

- [ ] **Step 2: Create NpcStatsComponent**

```java
// core/src/main/java/com/galacticodyssey/npc/components/NpcStatsComponent.java
package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;

public class NpcStatsComponent implements Component {
    public float accuracy;
    public float repair;
    public float medical;
    public float piloting;
    public float science;
    public float combat;
}
```

- [ ] **Step 3: Create ScheduleEntry and NpcScheduleComponent**

```java
// core/src/main/java/com/galacticodyssey/npc/components/ScheduleEntry.java
package com.galacticodyssey.npc.components;

public class ScheduleEntry {
    public float hourOfDay;
    public String locationId;
    public String activity;

    public ScheduleEntry() {}

    public ScheduleEntry(float hourOfDay, String locationId, String activity) {
        this.hourOfDay = hourOfDay;
        this.locationId = locationId;
        this.activity = activity;
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/npc/components/NpcScheduleComponent.java
package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;
import java.util.ArrayList;
import java.util.List;

public class NpcScheduleComponent implements Component {
    public final List<ScheduleEntry> entries = new ArrayList<>();
}
```

- [ ] **Step 4: Create CrewMemberComponent**

```java
// core/src/main/java/com/galacticodyssey/npc/crew/CrewMemberComponent.java
package com.galacticodyssey.npc.crew;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import java.util.ArrayList;
import java.util.List;

public class CrewMemberComponent implements Component {
    public CrewRole role;
    public CrewRank rank = CrewRank.RECRUIT;
    public float xp;
    public float morale = 75f;
    public float loyalty = 50f;
    public MoraleState moraleState = MoraleState.GRUMBLING;
    public float wage;
    public final List<String> perkIds = new ArrayList<>();
    public Entity assignedStation;
}
```

- [ ] **Step 5: Create CrewAssignmentComponent**

```java
// core/src/main/java/com/galacticodyssey/npc/crew/CrewAssignmentComponent.java
package com.galacticodyssey.npc.crew;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

public class CrewAssignmentComponent implements Component {
    public CrewRole requiredRole;
    public Entity assignedCrew;
    public float effectivenessMultiplier;
}
```

- [ ] **Step 6: Write component smoke test**

This test verifies entities can be constructed with the NPC and crew components, that component mappers work, and that default values are correct.

```java
// core/src/test/java/com/galacticodyssey/npc/NpcComponentsTest.java
package com.galacticodyssey.npc;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.npc.components.NpcStatsComponent;
import com.galacticodyssey.npc.components.NpcScheduleComponent;
import com.galacticodyssey.npc.components.ScheduleEntry;
import com.galacticodyssey.npc.crew.CrewAssignmentComponent;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRank;
import com.galacticodyssey.npc.crew.CrewRole;
import com.galacticodyssey.npc.crew.MoraleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcComponentsTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        engine = new Engine();
    }

    @Test
    void createNpcEntityWithIdentityAndStats() {
        Entity npc = new Entity();
        NpcIdentityComponent identity = new NpcIdentityComponent();
        identity.npcId = "npc_001";
        identity.name = "Test NPC";
        identity.species = "human";
        identity.recruitable = true;
        npc.add(identity);

        NpcStatsComponent stats = new NpcStatsComponent();
        stats.accuracy = 60f;
        stats.repair = 40f;
        npc.add(stats);

        engine.addEntity(npc);

        var entities = engine.getEntitiesFor(
            Family.all(NpcIdentityComponent.class, NpcStatsComponent.class).get());
        assertEquals(1, entities.size());

        ComponentMapper<NpcIdentityComponent> idMapper =
            ComponentMapper.getFor(NpcIdentityComponent.class);
        assertEquals("npc_001", idMapper.get(npc).npcId);
        assertTrue(idMapper.get(npc).recruitable);
    }

    @Test
    void crewMemberDefaultValues() {
        CrewMemberComponent crew = new CrewMemberComponent();
        assertEquals(CrewRank.RECRUIT, crew.rank);
        assertEquals(75f, crew.morale);
        assertEquals(50f, crew.loyalty);
        assertEquals(MoraleState.GRUMBLING, crew.moraleState);
        assertNull(crew.assignedStation);
        assertTrue(crew.perkIds.isEmpty());
    }

    @Test
    void moraleStateFromMorale() {
        assertEquals(MoraleState.CONTENT, MoraleState.fromMorale(90f));
        assertEquals(MoraleState.CONTENT, MoraleState.fromMorale(80f));
        assertEquals(MoraleState.GRUMBLING, MoraleState.fromMorale(79f));
        assertEquals(MoraleState.GRUMBLING, MoraleState.fromMorale(50f));
        assertEquals(MoraleState.DISGRUNTLED, MoraleState.fromMorale(49f));
        assertEquals(MoraleState.DISGRUNTLED, MoraleState.fromMorale(25f));
        assertEquals(MoraleState.MUTINOUS, MoraleState.fromMorale(24f));
        assertEquals(MoraleState.MUTINOUS, MoraleState.fromMorale(0f));
    }

    @Test
    void crewRankNextRank() {
        assertEquals(CrewRank.CREWMAN, CrewRank.RECRUIT.nextRank());
        assertEquals(CrewRank.SPECIALIST, CrewRank.CREWMAN.nextRank());
        assertNull(CrewRank.COMMANDER.nextRank());
    }

    @Test
    void crewRoleRelevantStat() {
        NpcStatsComponent stats = new NpcStatsComponent();
        stats.accuracy = 80f;
        stats.repair = 60f;
        stats.piloting = 70f;

        assertEquals(70f, CrewRole.PILOT.getRelevantStat(stats));
        assertEquals(80f, CrewRole.GUNNER.getRelevantStat(stats));
        assertEquals(60f, CrewRole.ENGINEER.getRelevantStat(stats));
    }

    @Test
    void scheduleComponentHoldsEntries() {
        NpcScheduleComponent schedule = new NpcScheduleComponent();
        schedule.entries.add(new ScheduleEntry(8f, "market_01", "TRADE"));
        schedule.entries.add(new ScheduleEntry(20f, "home_01", "REST"));
        assertEquals(2, schedule.entries.size());
        assertEquals(8f, schedule.entries.get(0).hourOfDay);
    }

    @Test
    void crewAssignmentComponentDefaults() {
        CrewAssignmentComponent assignment = new CrewAssignmentComponent();
        assertNull(assignment.requiredRole);
        assertNull(assignment.assignedCrew);
        assertEquals(0f, assignment.effectivenessMultiplier);
    }

    @Test
    void moraleStateEffectivenessModifiers() {
        assertEquals(1.1f, MoraleState.CONTENT.effectivenessModifier());
        assertEquals(1.0f, MoraleState.GRUMBLING.effectivenessModifier());
        assertEquals(0.85f, MoraleState.DISGRUNTLED.effectivenessModifier());
        assertEquals(0.7f, MoraleState.MUTINOUS.effectivenessModifier());
    }
}
```

- [ ] **Step 7: Run tests**

Run: `.\gradlew.bat core:test --tests "com.galacticodyssey.npc.NpcComponentsTest" -q`
Expected: All 8 tests pass

- [ ] **Step 8: Commit**

```
git add core/src/main/java/com/galacticodyssey/npc/components/ \
        core/src/main/java/com/galacticodyssey/npc/crew/CrewMemberComponent.java \
        core/src/main/java/com/galacticodyssey/npc/crew/CrewAssignmentComponent.java \
        core/src/test/java/com/galacticodyssey/npc/NpcComponentsTest.java
git commit -m "feat(npc): add NPC and crew ECS components with tests"
```

---

### Task 3: NPC Events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/events/CrewMemberHiredEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/events/CrewMemberFiredEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/events/CrewPromotedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/events/MoraleChangedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/events/CrewInsubordinationEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/events/CrewDesertedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/events/MutinyEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/events/CrewAssignedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/events/CrewInjuredEvent.java`

All events follow the existing pattern: `public final` class with `public final` fields, constructor takes all fields.

- [ ] **Step 1: Create all 9 event classes**

```java
// core/src/main/java/com/galacticodyssey/npc/events/CrewMemberHiredEvent.java
package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.crew.CrewRole;

public final class CrewMemberHiredEvent {
    public final Entity npc;
    public final CrewRole role;

    public CrewMemberHiredEvent(Entity npc, CrewRole role) {
        this.npc = npc;
        this.role = role;
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/npc/events/CrewMemberFiredEvent.java
package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.crew.FireReason;

public final class CrewMemberFiredEvent {
    public final Entity npc;
    public final FireReason reason;

    public CrewMemberFiredEvent(Entity npc, FireReason reason) {
        this.npc = npc;
        this.reason = reason;
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/npc/events/CrewPromotedEvent.java
package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.crew.CrewRank;

public final class CrewPromotedEvent {
    public final Entity npc;
    public final CrewRank oldRank;
    public final CrewRank newRank;

    public CrewPromotedEvent(Entity npc, CrewRank oldRank, CrewRank newRank) {
        this.npc = npc;
        this.oldRank = oldRank;
        this.newRank = newRank;
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/npc/events/MoraleChangedEvent.java
package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.crew.MoraleState;

public final class MoraleChangedEvent {
    public final Entity npc;
    public final MoraleState oldState;
    public final MoraleState newState;

    public MoraleChangedEvent(Entity npc, MoraleState oldState, MoraleState newState) {
        this.npc = npc;
        this.oldState = oldState;
        this.newState = newState;
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/npc/events/CrewInsubordinationEvent.java
package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.NpcGoal;

public final class CrewInsubordinationEvent {
    public final Entity npc;
    public final NpcGoal refusedGoal;

    public CrewInsubordinationEvent(Entity npc, NpcGoal refusedGoal) {
        this.npc = npc;
        this.refusedGoal = refusedGoal;
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/npc/events/CrewDesertedEvent.java
package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;

public final class CrewDesertedEvent {
    public final Entity npc;
    public final String locationId;

    public CrewDesertedEvent(Entity npc, String locationId) {
        this.npc = npc;
        this.locationId = locationId;
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/npc/events/MutinyEvent.java
package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import java.util.List;

public final class MutinyEvent {
    public final List<Entity> mutineers;

    public MutinyEvent(List<Entity> mutineers) {
        this.mutineers = List.copyOf(mutineers);
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/npc/events/CrewAssignedEvent.java
package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;

public final class CrewAssignedEvent {
    public final Entity npc;
    public final Entity station;

    public CrewAssignedEvent(Entity npc, Entity station) {
        this.npc = npc;
        this.station = station;
    }
}
```

```java
// core/src/main/java/com/galacticodyssey/npc/events/CrewInjuredEvent.java
package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;

public final class CrewInjuredEvent {
    public final Entity npc;
    public final float damage;

    public CrewInjuredEvent(Entity npc, float damage) {
        this.npc = npc;
        this.damage = damage;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `.\gradlew.bat core:compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/npc/events/
git commit -m "feat(npc): add NPC and crew event classes"
```

---

### Task 4: Data Definitions and JSON Content Files

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/data/SpeciesDefinition.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/data/BackgroundDefinition.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/data/PerkDefinition.java`
- Create: `core/src/main/resources/data/npcs/species.json`
- Create: `core/src/main/resources/data/npcs/backgrounds.json`
- Create: `core/src/main/resources/data/npcs/perks.json`
- Create: `core/src/main/resources/data/npcs/names.json`

- [ ] **Step 1: Create SpeciesDefinition**

Each species has stat affinities (additive modifiers to base stats during NPC generation) and a pool of portrait asset IDs.

```java
// core/src/main/java/com/galacticodyssey/npc/data/SpeciesDefinition.java
package com.galacticodyssey.npc.data;

import java.util.ArrayList;
import java.util.List;

public class SpeciesDefinition {
    public String id;
    public String name;
    public float accuracyMod;
    public float repairMod;
    public float medicalMod;
    public float pilotingMod;
    public float scienceMod;
    public float combatMod;
    public List<String> portraitIds = new ArrayList<>();
}
```

- [ ] **Step 2: Create BackgroundDefinition**

```java
// core/src/main/java/com/galacticodyssey/npc/data/BackgroundDefinition.java
package com.galacticodyssey.npc.data;

public class BackgroundDefinition {
    public String id;
    public String name;
    public String description;
    public float accuracyMod;
    public float repairMod;
    public float medicalMod;
    public float pilotingMod;
    public float scienceMod;
    public float combatMod;
}
```

- [ ] **Step 3: Create PerkDefinition**

```java
// core/src/main/java/com/galacticodyssey/npc/data/PerkDefinition.java
package com.galacticodyssey.npc.data;

public class PerkDefinition {
    public String id;
    public String name;
    public String description;
    public String minRank;
    public String applicableRole;
}
```

- [ ] **Step 4: Create species.json**

```json
[
  {
    "id": "human",
    "name": "Human",
    "accuracyMod": 0,
    "repairMod": 5,
    "medicalMod": 5,
    "pilotingMod": 0,
    "scienceMod": 0,
    "combatMod": 0,
    "portraitIds": ["portrait_human_01", "portrait_human_02", "portrait_human_03"]
  },
  {
    "id": "veloxi",
    "name": "Veloxi",
    "accuracyMod": 10,
    "repairMod": -5,
    "medicalMod": 0,
    "pilotingMod": 5,
    "scienceMod": 5,
    "combatMod": -5,
    "portraitIds": ["portrait_veloxi_01", "portrait_veloxi_02"]
  },
  {
    "id": "krethian",
    "name": "Krethian",
    "accuracyMod": -5,
    "repairMod": 10,
    "medicalMod": -5,
    "pilotingMod": 0,
    "scienceMod": 0,
    "combatMod": 10,
    "portraitIds": ["portrait_krethian_01", "portrait_krethian_02"]
  }
]
```

- [ ] **Step 5: Create backgrounds.json**

```json
[
  {
    "id": "military",
    "name": "Military",
    "description": "Former enlisted or officer in a faction navy.",
    "accuracyMod": 10,
    "repairMod": 0,
    "medicalMod": 0,
    "pilotingMod": 5,
    "scienceMod": -5,
    "combatMod": 10
  },
  {
    "id": "civilian",
    "name": "Civilian",
    "description": "Ordinary citizen seeking a life among the stars.",
    "accuracyMod": -5,
    "repairMod": 5,
    "medicalMod": 5,
    "pilotingMod": 0,
    "scienceMod": 0,
    "combatMod": -5
  },
  {
    "id": "academic",
    "name": "Academic",
    "description": "Researcher or professor from a university or institute.",
    "accuracyMod": -5,
    "repairMod": 0,
    "medicalMod": 10,
    "pilotingMod": -5,
    "scienceMod": 15,
    "combatMod": -10
  },
  {
    "id": "criminal",
    "name": "Criminal",
    "description": "Former pirate, smuggler, or black market dealer.",
    "accuracyMod": 5,
    "repairMod": 5,
    "medicalMod": -5,
    "pilotingMod": 5,
    "scienceMod": -5,
    "combatMod": 5
  }
]
```

- [ ] **Step 6: Create perks.json**

```json
[
  {
    "id": "quick_hands",
    "name": "Quick Hands",
    "description": "Repair speed increased by 20%.",
    "minRank": "SPECIALIST",
    "applicableRole": "ENGINEER"
  },
  {
    "id": "steady_aim",
    "name": "Steady Aim",
    "description": "Turret accuracy increased by 15%.",
    "minRank": "SPECIALIST",
    "applicableRole": "GUNNER"
  },
  {
    "id": "field_medic",
    "name": "Field Medic",
    "description": "Can heal crew during combat without penalty.",
    "minRank": "VETERAN",
    "applicableRole": "MEDIC"
  },
  {
    "id": "iron_will",
    "name": "Iron Will",
    "description": "Morale cannot drop below 30.",
    "minRank": "VETERAN",
    "applicableRole": null
  },
  {
    "id": "tactical_command",
    "name": "Tactical Command",
    "description": "Squad members gain +10% accuracy.",
    "minRank": "OFFICER",
    "applicableRole": "MARINE"
  },
  {
    "id": "fleet_captain",
    "name": "Fleet Captain",
    "description": "Can captain a secondary ship in your fleet.",
    "minRank": "COMMANDER",
    "applicableRole": null
  }
]
```

- [ ] **Step 7: Create names.json**

```json
{
  "human": {
    "first": ["James", "Elena", "Marcus", "Priya", "Viktor", "Amara", "Chen", "Rosa", "Dex", "Yuki"],
    "last": ["Voss", "Chen", "Okafor", "Petrov", "Reyes", "Nakamura", "Adeyemi", "Larsson", "Zhao", "Okoro"]
  },
  "veloxi": {
    "first": ["Zek'thar", "Vix'nal", "Thri'kex", "Nex'ori", "Kel'vaan", "Syx'ura", "Dri'thal", "Qix'ren"],
    "last": ["of Nexus", "of Veil", "of Spire", "of Drift", "of Shard", "of Bloom", "of Arc", "of Crest"]
  },
  "krethian": {
    "first": ["Grun", "Bolk", "Tharn", "Krix", "Draven", "Mogg", "Jurk", "Skarl"],
    "last": ["Ironhide", "Stonefist", "Hammerjaw", "Steelback", "Bonecrush", "Ashforge", "Warborn", "Deepcore"]
  }
}
```

- [ ] **Step 8: Verify compilation**

Run: `.\gradlew.bat core:compileJava -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```
git add core/src/main/java/com/galacticodyssey/npc/data/SpeciesDefinition.java \
        core/src/main/java/com/galacticodyssey/npc/data/BackgroundDefinition.java \
        core/src/main/java/com/galacticodyssey/npc/data/PerkDefinition.java \
        core/src/main/resources/data/npcs/
git commit -m "feat(npc): add NPC data definitions and JSON content files"
```

---

### Task 5: NpcDataRegistry (TDD)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/data/NpcDataRegistry.java`
- Test: `core/src/test/java/com/galacticodyssey/npc/data/NpcDataRegistryTest.java`

The registry stores species, backgrounds, perks, and name pools. It has `register*()` methods for programmatic use (tests) and `loadFromFiles()` for runtime JSON loading (follows `CommodityRegistry` pattern).

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/npc/data/NpcDataRegistryTest.java
package com.galacticodyssey.npc.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcDataRegistryTest {

    private NpcDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NpcDataRegistry();

        SpeciesDefinition human = new SpeciesDefinition();
        human.id = "human";
        human.name = "Human";
        human.repairMod = 5f;
        human.portraitIds.add("portrait_human_01");
        registry.registerSpecies(human);

        SpeciesDefinition veloxi = new SpeciesDefinition();
        veloxi.id = "veloxi";
        veloxi.name = "Veloxi";
        veloxi.accuracyMod = 10f;
        registry.registerSpecies(veloxi);

        BackgroundDefinition military = new BackgroundDefinition();
        military.id = "military";
        military.name = "Military";
        military.combatMod = 10f;
        military.accuracyMod = 10f;
        registry.registerBackground(military);

        PerkDefinition quickHands = new PerkDefinition();
        quickHands.id = "quick_hands";
        quickHands.name = "Quick Hands";
        quickHands.minRank = "SPECIALIST";
        quickHands.applicableRole = "ENGINEER";
        registry.registerPerk(quickHands);

        registry.registerNames("human",
            List.of("James", "Elena"),
            List.of("Voss", "Chen"));
    }

    @Test
    void getSpeciesById() {
        SpeciesDefinition human = registry.getSpecies("human");
        assertNotNull(human);
        assertEquals("Human", human.name);
        assertEquals(5f, human.repairMod);
    }

    @Test
    void getSpeciesReturnsNullForUnknownId() {
        assertNull(registry.getSpecies("unknown"));
    }

    @Test
    void getAllSpecies() {
        List<SpeciesDefinition> all = registry.getAllSpecies();
        assertEquals(2, all.size());
    }

    @Test
    void getBackgroundById() {
        BackgroundDefinition mil = registry.getBackground("military");
        assertNotNull(mil);
        assertEquals(10f, mil.combatMod);
    }

    @Test
    void getPerkById() {
        PerkDefinition perk = registry.getPerk("quick_hands");
        assertNotNull(perk);
        assertEquals("SPECIALIST", perk.minRank);
    }

    @Test
    void getAllPerks() {
        assertEquals(1, registry.getAllPerks().size());
    }

    @Test
    void getNamePool() {
        NpcDataRegistry.NamePool pool = registry.getNamePool("human");
        assertNotNull(pool);
        assertEquals(2, pool.firstNames.size());
        assertEquals(2, pool.lastNames.size());
        assertTrue(pool.firstNames.contains("James"));
    }

    @Test
    void getNamePoolReturnsNullForUnknownSpecies() {
        assertNull(registry.getNamePool("martian"));
    }

    @Test
    void getSpeciesIds() {
        List<String> ids = registry.getSpeciesIds();
        assertEquals(2, ids.size());
        assertTrue(ids.contains("human"));
        assertTrue(ids.contains("veloxi"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat core:test --tests "com.galacticodyssey.npc.data.NpcDataRegistryTest" -q`
Expected: FAIL — `NpcDataRegistry` does not exist yet

- [ ] **Step 3: Implement NpcDataRegistry**

```java
// core/src/main/java/com/galacticodyssey/npc/data/NpcDataRegistry.java
package com.galacticodyssey.npc.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NpcDataRegistry {

    public static class NamePool {
        public final List<String> firstNames;
        public final List<String> lastNames;

        public NamePool(List<String> firstNames, List<String> lastNames) {
            this.firstNames = firstNames;
            this.lastNames = lastNames;
        }
    }

    private final Map<String, SpeciesDefinition> speciesById = new HashMap<>();
    private final Map<String, BackgroundDefinition> backgroundsById = new HashMap<>();
    private final Map<String, PerkDefinition> perksById = new HashMap<>();
    private final Map<String, NamePool> namesBySpecies = new HashMap<>();

    public void registerSpecies(SpeciesDefinition def) {
        speciesById.put(def.id, def);
    }

    public void registerBackground(BackgroundDefinition def) {
        backgroundsById.put(def.id, def);
    }

    public void registerPerk(PerkDefinition def) {
        perksById.put(def.id, def);
    }

    public void registerNames(String speciesId, List<String> firstNames, List<String> lastNames) {
        namesBySpecies.put(speciesId, new NamePool(firstNames, lastNames));
    }

    public SpeciesDefinition getSpecies(String id) {
        return speciesById.get(id);
    }

    public List<SpeciesDefinition> getAllSpecies() {
        return new ArrayList<>(speciesById.values());
    }

    public List<String> getSpeciesIds() {
        return new ArrayList<>(speciesById.keySet());
    }

    public BackgroundDefinition getBackground(String id) {
        return backgroundsById.get(id);
    }

    public List<BackgroundDefinition> getAllBackgrounds() {
        return new ArrayList<>(backgroundsById.values());
    }

    public PerkDefinition getPerk(String id) {
        return perksById.get(id);
    }

    public List<PerkDefinition> getAllPerks() {
        return new ArrayList<>(perksById.values());
    }

    public NamePool getNamePool(String speciesId) {
        return namesBySpecies.get(speciesId);
    }

    public void loadFromFiles() {
        Json json = new Json();
        JsonReader reader = new JsonReader();

        JsonValue speciesRoot = reader.parse(Gdx.files.internal("data/npcs/species.json"));
        for (JsonValue entry = speciesRoot.child; entry != null; entry = entry.next) {
            registerSpecies(json.readValue(SpeciesDefinition.class, entry));
        }

        JsonValue bgRoot = reader.parse(Gdx.files.internal("data/npcs/backgrounds.json"));
        for (JsonValue entry = bgRoot.child; entry != null; entry = entry.next) {
            registerBackground(json.readValue(BackgroundDefinition.class, entry));
        }

        JsonValue perkRoot = reader.parse(Gdx.files.internal("data/npcs/perks.json"));
        for (JsonValue entry = perkRoot.child; entry != null; entry = entry.next) {
            registerPerk(json.readValue(PerkDefinition.class, entry));
        }

        JsonValue namesRoot = reader.parse(Gdx.files.internal("data/npcs/names.json"));
        for (JsonValue species = namesRoot.child; species != null; species = species.next) {
            List<String> firsts = new ArrayList<>();
            List<String> lasts = new ArrayList<>();
            for (JsonValue n = species.get("first").child; n != null; n = n.next) {
                firsts.add(n.asString());
            }
            for (JsonValue n = species.get("last").child; n != null; n = n.next) {
                lasts.add(n.asString());
            }
            registerNames(species.name, firsts, lasts);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat core:test --tests "com.galacticodyssey.npc.data.NpcDataRegistryTest" -q`
Expected: All 9 tests pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/npc/data/NpcDataRegistry.java \
        core/src/test/java/com/galacticodyssey/npc/data/NpcDataRegistryTest.java
git commit -m "feat(npc): add NpcDataRegistry with species, backgrounds, perks, and names"
```

---

### Task 6: NpcGenerator + SeedDeriver NPC Domain (TDD)

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java`
- Test: `core/src/test/java/com/galacticodyssey/npc/data/NpcGeneratorTest.java`

The generator creates NPC entities deterministically from a seed. It picks species, background, name, and rolls stats, applying species/background modifiers.

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/npc/data/NpcGeneratorTest.java
package com.galacticodyssey.npc.data;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.NpcDisposition;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.npc.components.NpcStatsComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcGeneratorTest {

    private NpcDataRegistry registry;
    private NpcGenerator generator;
    private Engine engine;

    private static final ComponentMapper<NpcIdentityComponent> ID_M =
        ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);

    @BeforeEach
    void setUp() {
        registry = new NpcDataRegistry();
        engine = new Engine();

        SpeciesDefinition human = new SpeciesDefinition();
        human.id = "human";
        human.name = "Human";
        human.repairMod = 5f;
        human.medicalMod = 5f;
        human.portraitIds.addAll(List.of("portrait_human_01", "portrait_human_02"));
        registry.registerSpecies(human);

        BackgroundDefinition military = new BackgroundDefinition();
        military.id = "military";
        military.name = "Military";
        military.accuracyMod = 10f;
        military.combatMod = 10f;
        registry.registerBackground(military);

        BackgroundDefinition civilian = new BackgroundDefinition();
        civilian.id = "civilian";
        civilian.name = "Civilian";
        civilian.repairMod = 5f;
        registry.registerBackground(civilian);

        registry.registerNames("human",
            List.of("James", "Elena", "Marcus"),
            List.of("Voss", "Chen", "Okafor"));

        generator = new NpcGenerator(registry);
    }

    @Test
    void generatedNpcHasIdentityAndStats() {
        Entity npc = generator.generate(engine, 12345L);
        assertNotNull(ID_M.get(npc));
        assertNotNull(STATS_M.get(npc));
    }

    @Test
    void generatedNpcHasNonEmptyNameAndSpecies() {
        Entity npc = generator.generate(engine, 12345L);
        NpcIdentityComponent id = ID_M.get(npc);
        assertNotNull(id.name);
        assertFalse(id.name.isEmpty());
        assertEquals("human", id.species);
    }

    @Test
    void generatedNpcHasPortraitFromSpeciesPool() {
        Entity npc = generator.generate(engine, 12345L);
        NpcIdentityComponent id = ID_M.get(npc);
        assertTrue(id.portraitId.startsWith("portrait_human_"));
    }

    @Test
    void generatedNpcStatsAreInValidRange() {
        Entity npc = generator.generate(engine, 12345L);
        NpcStatsComponent stats = STATS_M.get(npc);
        assertStatInRange(stats.accuracy);
        assertStatInRange(stats.repair);
        assertStatInRange(stats.medical);
        assertStatInRange(stats.piloting);
        assertStatInRange(stats.science);
        assertStatInRange(stats.combat);
    }

    @Test
    void sameSeedProducesSameNpc() {
        Entity npc1 = generator.generate(engine, 99999L);
        Entity npc2 = generator.generate(engine, 99999L);
        NpcIdentityComponent id1 = ID_M.get(npc1);
        NpcIdentityComponent id2 = ID_M.get(npc2);
        assertEquals(id1.name, id2.name);
        assertEquals(id1.species, id2.species);

        NpcStatsComponent stats1 = STATS_M.get(npc1);
        NpcStatsComponent stats2 = STATS_M.get(npc2);
        assertEquals(stats1.accuracy, stats2.accuracy);
        assertEquals(stats1.combat, stats2.combat);
    }

    @Test
    void differentSeedsProduceDifferentNpcs() {
        Entity npc1 = generator.generate(engine, 11111L);
        Entity npc2 = generator.generate(engine, 22222L);
        NpcStatsComponent stats1 = STATS_M.get(npc1);
        NpcStatsComponent stats2 = STATS_M.get(npc2);
        boolean allEqual = stats1.accuracy == stats2.accuracy
            && stats1.repair == stats2.repair
            && stats1.combat == stats2.combat;
        assertFalse(allEqual, "Different seeds should produce different stats");
    }

    @Test
    void generateWithExplicitSpeciesAndBackground() {
        Entity npc = generator.generate(engine, 12345L, "human", "military");
        NpcIdentityComponent id = ID_M.get(npc);
        assertEquals("human", id.species);
        assertEquals("military", id.background);
    }

    @Test
    void defaultDispositionIsNeutral() {
        Entity npc = generator.generate(engine, 12345L);
        assertEquals(NpcDisposition.NEUTRAL, ID_M.get(npc).disposition);
    }

    @Test
    void npcIdIsDeterministicFromSeed() {
        Entity npc1 = generator.generate(engine, 42L);
        Entity npc2 = generator.generate(engine, 42L);
        assertEquals(ID_M.get(npc1).npcId, ID_M.get(npc2).npcId);
        assertFalse(ID_M.get(npc1).npcId.isEmpty());
    }

    private void assertStatInRange(float stat) {
        assertTrue(stat >= 0f && stat <= 100f,
            "Stat " + stat + " should be in range [0, 100]");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat core:test --tests "com.galacticodyssey.npc.data.NpcGeneratorTest" -q`
Expected: FAIL — `NpcGenerator` does not exist yet

- [ ] **Step 3: Add NPC_DOMAIN to SeedDeriver**

Add this constant alongside the existing domain constants in `SeedDeriver.java`:

```java
public static final long NPC_DOMAIN = 0xF2A84C39E71B5D06L;
```

And add the convenience method:

```java
public static long npcDomain(long parentSeed) {
    return domain(parentSeed, NPC_DOMAIN);
}
```

- [ ] **Step 4: Implement NpcGenerator**

```java
// core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java
package com.galacticodyssey.npc.data;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.npc.NpcDisposition;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.npc.components.NpcStatsComponent;

import java.util.List;

public class NpcGenerator {

    private final NpcDataRegistry registry;

    public NpcGenerator(NpcDataRegistry registry) {
        this.registry = registry;
    }

    public Entity generate(Engine engine, long seed) {
        long npcSeed = SeedDeriver.npcDomain(seed);

        List<String> speciesIds = registry.getSpeciesIds();
        String speciesId = speciesIds.get(pickIndex(npcSeed, 0, speciesIds.size()));

        List<BackgroundDefinition> backgrounds = registry.getAllBackgrounds();
        String backgroundId = backgrounds.get(pickIndex(npcSeed, 1, backgrounds.size())).id;

        return generate(engine, seed, speciesId, backgroundId);
    }

    public Entity generate(Engine engine, long seed, String speciesId, String backgroundId) {
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
        identity.recruitable = false;
        entity.add(identity);

        NpcStatsComponent stats = new NpcStatsComponent();
        stats.accuracy = clampStat(rollBase(npcSeed, 10) + species.accuracyMod + background.accuracyMod);
        stats.repair = clampStat(rollBase(npcSeed, 11) + species.repairMod + background.repairMod);
        stats.medical = clampStat(rollBase(npcSeed, 12) + species.medicalMod + background.medicalMod);
        stats.piloting = clampStat(rollBase(npcSeed, 13) + species.pilotingMod + background.pilotingMod);
        stats.science = clampStat(rollBase(npcSeed, 14) + species.scienceMod + background.scienceMod);
        stats.combat = clampStat(rollBase(npcSeed, 15) + species.combatMod + background.combatMod);
        entity.add(stats);

        engine.addEntity(entity);
        return entity;
    }

    private String pickName(long npcSeed, String speciesId) {
        NpcDataRegistry.NamePool pool = registry.getNamePool(speciesId);
        if (pool == null || pool.firstNames.isEmpty()) return "Unknown";
        String first = pickFromList(npcSeed, 4, pool.firstNames);
        String last = pickFromList(npcSeed, 5, pool.lastNames);
        return first + " " + last;
    }

    private <T> T pickFromList(long seed, int slot, List<T> list) {
        if (list.isEmpty()) return null;
        return list.get(pickIndex(seed, slot, list.size()));
    }

    private int pickIndex(long seed, int slot, int listSize) {
        long derived = SeedDeriver.forId(seed, slot);
        return (int) (Math.abs(derived) % listSize);
    }

    private float rollBase(long seed, int slot) {
        long derived = SeedDeriver.forId(seed, slot);
        float normalized = (Math.abs(derived) % 10000) / 10000f;
        return normalized * 60f + 20f;
    }

    private float clampStat(float value) {
        return Math.max(0f, Math.min(100f, value));
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\gradlew.bat core:test --tests "com.galacticodyssey.npc.data.NpcGeneratorTest" -q`
Expected: All 9 tests pass

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/SeedDeriver.java \
        core/src/main/java/com/galacticodyssey/npc/data/NpcGenerator.java \
        core/src/test/java/com/galacticodyssey/npc/data/NpcGeneratorTest.java
git commit -m "feat(npc): add NpcGenerator with deterministic seed-based NPC creation"
```

---

### Task 7: CrewAssignmentSystem (TDD)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/systems/CrewAssignmentSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/npc/systems/CrewAssignmentSystemTest.java`

This system iterates all entities with `CrewAssignmentComponent`, finds the assigned crew member, reads their stats/rank/morale, and computes `effectivenessMultiplier`. It runs at priority 21 with a 1-second tick interval.

**Effectiveness formula from spec:**
```
baseStat = relevant stat for the role (0-100), normalized to 0.0-1.0
rankBonus = rank.ordinal() * 0.05
moraleMod = moraleState.effectivenessModifier()
effectivenessMultiplier = (baseStat + rankBonus) * moraleMod
```

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/npc/systems/CrewAssignmentSystemTest.java
package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.components.NpcStatsComponent;
import com.galacticodyssey.npc.crew.CrewAssignmentComponent;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRank;
import com.galacticodyssey.npc.crew.CrewRole;
import com.galacticodyssey.npc.crew.MoraleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrewAssignmentSystemTest {

    private Engine engine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new CrewAssignmentSystem(eventBus));
    }

    @Test
    void unassignedStationHasZeroEffectiveness() {
        Entity station = createStation(CrewRole.ENGINEER);
        engine.addEntity(station);

        engine.update(1.1f);

        assertEquals(0f, station.getComponent(CrewAssignmentComponent.class).effectivenessMultiplier);
    }

    @Test
    void assignedCrewComputesEffectiveness() {
        Entity crew = createCrew(CrewRole.ENGINEER, CrewRank.RECRUIT, 80f, 75f);
        Entity station = createStation(CrewRole.ENGINEER);
        assignCrewToStation(crew, station);

        engine.addEntity(crew);
        engine.addEntity(station);
        engine.update(1.1f);

        CrewAssignmentComponent assignment = station.getComponent(CrewAssignmentComponent.class);
        // baseStat = 80/100 = 0.8, rankBonus = 0 * 0.05 = 0.0
        // moraleMod = GRUMBLING (75 morale) = 1.0
        // effectiveness = (0.8 + 0.0) * 1.0 = 0.8
        assertEquals(0.8f, assignment.effectivenessMultiplier, 0.001f);
    }

    @Test
    void rankBonusIncreasesEffectiveness() {
        Entity crew = createCrew(CrewRole.GUNNER, CrewRank.VETERAN, 60f, 75f);
        Entity station = createStation(CrewRole.GUNNER);
        assignCrewToStation(crew, station);

        engine.addEntity(crew);
        engine.addEntity(station);
        engine.update(1.1f);

        CrewAssignmentComponent assignment = station.getComponent(CrewAssignmentComponent.class);
        // baseStat = 60/100 = 0.6, rankBonus = 3 * 0.05 = 0.15
        // moraleMod = GRUMBLING = 1.0
        // effectiveness = (0.6 + 0.15) * 1.0 = 0.75
        assertEquals(0.75f, assignment.effectivenessMultiplier, 0.001f);
    }

    @Test
    void contentMoraleGivesBonus() {
        Entity crew = createCrew(CrewRole.MEDIC, CrewRank.RECRUIT, 50f, 90f);
        Entity station = createStation(CrewRole.MEDIC);
        assignCrewToStation(crew, station);

        engine.addEntity(crew);
        engine.addEntity(station);
        engine.update(1.1f);

        CrewAssignmentComponent assignment = station.getComponent(CrewAssignmentComponent.class);
        // baseStat = 0.5, rankBonus = 0, moraleMod = CONTENT = 1.1
        // effectiveness = 0.5 * 1.1 = 0.55
        assertEquals(0.55f, assignment.effectivenessMultiplier, 0.001f);
    }

    @Test
    void mutinousMoralePenalizesEffectiveness() {
        Entity crew = createCrew(CrewRole.PILOT, CrewRank.CREWMAN, 70f, 10f);
        Entity station = createStation(CrewRole.PILOT);
        assignCrewToStation(crew, station);

        engine.addEntity(crew);
        engine.addEntity(station);
        engine.update(1.1f);

        CrewAssignmentComponent assignment = station.getComponent(CrewAssignmentComponent.class);
        // baseStat = 0.7, rankBonus = 1 * 0.05 = 0.05, moraleMod = MUTINOUS = 0.7
        // effectiveness = (0.7 + 0.05) * 0.7 = 0.525
        assertEquals(0.525f, assignment.effectivenessMultiplier, 0.001f);
    }

    @Test
    void tickIntervalThrottlesUpdate() {
        Entity crew = createCrew(CrewRole.ENGINEER, CrewRank.RECRUIT, 80f, 75f);
        Entity station = createStation(CrewRole.ENGINEER);
        assignCrewToStation(crew, station);

        engine.addEntity(crew);
        engine.addEntity(station);

        // First update at 0.5s — under 1s threshold, should not compute yet
        engine.update(0.5f);
        assertEquals(0f, station.getComponent(CrewAssignmentComponent.class).effectivenessMultiplier);

        // Second update pushes past 1s
        engine.update(0.6f);
        assertTrue(station.getComponent(CrewAssignmentComponent.class).effectivenessMultiplier > 0f);
    }

    private Entity createCrew(CrewRole role, CrewRank rank, float relevantStat, float morale) {
        Entity entity = new Entity();
        NpcStatsComponent stats = new NpcStatsComponent();
        switch (role) {
            case PILOT:    stats.piloting = relevantStat; break;
            case GUNNER:   stats.accuracy = relevantStat; break;
            case ENGINEER: stats.repair = relevantStat; break;
            case MEDIC:    stats.medical = relevantStat; break;
            case MARINE:   stats.combat = relevantStat; break;
            case SCIENTIST: stats.science = relevantStat; break;
            case NAVIGATOR: stats.piloting = relevantStat; break;
        }
        entity.add(stats);

        CrewMemberComponent crew = new CrewMemberComponent();
        crew.role = role;
        crew.rank = rank;
        crew.morale = morale;
        crew.moraleState = MoraleState.fromMorale(morale);
        entity.add(crew);

        return entity;
    }

    private Entity createStation(CrewRole role) {
        Entity entity = new Entity();
        CrewAssignmentComponent assignment = new CrewAssignmentComponent();
        assignment.requiredRole = role;
        entity.add(assignment);
        return entity;
    }

    private void assignCrewToStation(Entity crew, Entity station) {
        crew.getComponent(CrewMemberComponent.class).assignedStation = station;
        station.getComponent(CrewAssignmentComponent.class).assignedCrew = crew;
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat core:test --tests "com.galacticodyssey.npc.systems.CrewAssignmentSystemTest" -q`
Expected: FAIL — `CrewAssignmentSystem` does not exist yet

- [ ] **Step 3: Implement CrewAssignmentSystem**

```java
// core/src/main/java/com/galacticodyssey/npc/systems/CrewAssignmentSystem.java
package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.components.NpcStatsComponent;
import com.galacticodyssey.npc.crew.CrewAssignmentComponent;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.MoraleState;

public class CrewAssignmentSystem extends EntitySystem {

    public static final int PRIORITY = 21;
    private static final float TICK_INTERVAL = 1.0f;

    private static final Family STATION_FAMILY =
        Family.all(CrewAssignmentComponent.class).get();
    private static final ComponentMapper<CrewAssignmentComponent> ASSIGN_M =
        ComponentMapper.getFor(CrewAssignmentComponent.class);
    private static final ComponentMapper<CrewMemberComponent> CREW_M =
        ComponentMapper.getFor(CrewMemberComponent.class);
    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> stations;
    private float timeSinceLastTick;

    public CrewAssignmentSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        stations = engine.getEntitiesFor(STATION_FAMILY);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        stations = null;
    }

    @Override
    public void update(float deltaTime) {
        timeSinceLastTick += deltaTime;
        if (timeSinceLastTick < TICK_INTERVAL) return;
        timeSinceLastTick = 0f;

        if (stations == null) return;

        for (int i = 0, n = stations.size(); i < n; i++) {
            Entity stationEntity = stations.get(i);
            CrewAssignmentComponent assignment = ASSIGN_M.get(stationEntity);

            Entity crewEntity = assignment.assignedCrew;
            if (crewEntity == null) {
                assignment.effectivenessMultiplier = 0f;
                continue;
            }

            CrewMemberComponent crew = CREW_M.get(crewEntity);
            NpcStatsComponent stats = STATS_M.get(crewEntity);
            if (crew == null || stats == null) {
                assignment.effectivenessMultiplier = 0f;
                continue;
            }

            float baseStat = crew.role.getRelevantStat(stats) / 100f;
            float rankBonus = crew.rank.ordinal() * 0.05f;
            float moraleMod = crew.moraleState.effectivenessModifier();
            assignment.effectivenessMultiplier = (baseStat + rankBonus) * moraleMod;
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat core:test --tests "com.galacticodyssey.npc.systems.CrewAssignmentSystemTest" -q`
Expected: All 6 tests pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/npc/systems/CrewAssignmentSystem.java \
        core/src/test/java/com/galacticodyssey/npc/systems/CrewAssignmentSystemTest.java
git commit -m "feat(npc): add CrewAssignmentSystem with effectiveness calculation"
```

---

### Task 8: CrewXPSystem (TDD)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/systems/CrewXPSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/npc/systems/CrewXPSystemTest.java`

This system awards XP to crew members and checks promotion eligibility. It has a public `awardXP` method that other systems will call. When a crew member accumulates enough XP to reach the next rank threshold, it publishes a `CrewPromotedEvent` (but promotion is manual — it sets a flag, it doesn't auto-promote). On second thought, to keep Phase 1 self-contained: the system auto-flags eligibility, and a `promote()` method handles the actual rank change when called by game logic.

- [ ] **Step 1: Write failing tests**

```java
// core/src/test/java/com/galacticodyssey/npc/systems/CrewXPSystemTest.java
package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.components.NpcStatsComponent;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRank;
import com.galacticodyssey.npc.crew.CrewRole;
import com.galacticodyssey.npc.events.CrewPromotedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrewXPSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private CrewXPSystem xpSystem;
    private final List<CrewPromotedEvent> promotedEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        xpSystem = new CrewXPSystem(eventBus);
        engine.addSystem(xpSystem);
        eventBus.subscribe(CrewPromotedEvent.class, promotedEvents::add);
    }

    @Test
    void awardXPIncreasesCrewXP() {
        Entity crew = createCrew(CrewRole.ENGINEER, CrewRank.RECRUIT, 0f);
        engine.addEntity(crew);

        xpSystem.awardXP(crew, 50f);

        assertEquals(50f, crew.getComponent(CrewMemberComponent.class).xp);
    }

    @Test
    void awardXPAccumulatesOverMultipleCalls() {
        Entity crew = createCrew(CrewRole.GUNNER, CrewRank.RECRUIT, 0f);
        engine.addEntity(crew);

        xpSystem.awardXP(crew, 30f);
        xpSystem.awardXP(crew, 25f);

        assertEquals(55f, crew.getComponent(CrewMemberComponent.class).xp);
    }

    @Test
    void promoteSetsNextRankAndResetsXP() {
        Entity crew = createCrew(CrewRole.PILOT, CrewRank.RECRUIT, 150f);
        engine.addEntity(crew);

        boolean promoted = xpSystem.promote(crew);

        assertTrue(promoted);
        CrewMemberComponent cm = crew.getComponent(CrewMemberComponent.class);
        assertEquals(CrewRank.CREWMAN, cm.rank);
        assertEquals(50f, cm.xp, 0.001f);
        assertEquals(CrewRank.CREWMAN.baseWage, cm.wage);
    }

    @Test
    void promotePublishesEvent() {
        Entity crew = createCrew(CrewRole.MEDIC, CrewRank.RECRUIT, 150f);
        engine.addEntity(crew);

        xpSystem.promote(crew);

        assertEquals(1, promotedEvents.size());
        assertEquals(CrewRank.RECRUIT, promotedEvents.get(0).oldRank);
        assertEquals(CrewRank.CREWMAN, promotedEvents.get(0).newRank);
    }

    @Test
    void promoteFailsWhenXPBelowThreshold() {
        Entity crew = createCrew(CrewRole.ENGINEER, CrewRank.RECRUIT, 50f);
        engine.addEntity(crew);

        boolean promoted = xpSystem.promote(crew);

        assertFalse(promoted);
        assertEquals(CrewRank.RECRUIT, crew.getComponent(CrewMemberComponent.class).rank);
        assertTrue(promotedEvents.isEmpty());
    }

    @Test
    void promoteFailsAtMaxRank() {
        Entity crew = createCrew(CrewRole.MARINE, CrewRank.COMMANDER, 9999f);
        engine.addEntity(crew);

        boolean promoted = xpSystem.promote(crew);

        assertFalse(promoted);
        assertEquals(CrewRank.COMMANDER, crew.getComponent(CrewMemberComponent.class).rank);
    }

    @Test
    void isPromotionEligibleReturnsCorrectly() {
        Entity eligible = createCrew(CrewRole.GUNNER, CrewRank.RECRUIT, 100f);
        Entity notEligible = createCrew(CrewRole.GUNNER, CrewRank.RECRUIT, 50f);
        engine.addEntity(eligible);
        engine.addEntity(notEligible);

        assertTrue(xpSystem.isPromotionEligible(eligible));
        assertFalse(xpSystem.isPromotionEligible(notEligible));
    }

    @Test
    void promoteUpdatesWageToNewRankBaseWage() {
        Entity crew = createCrew(CrewRole.ENGINEER, CrewRank.CREWMAN, 350f);
        crew.getComponent(CrewMemberComponent.class).wage = CrewRank.CREWMAN.baseWage;
        engine.addEntity(crew);

        xpSystem.promote(crew);

        assertEquals(CrewRank.SPECIALIST.baseWage,
            crew.getComponent(CrewMemberComponent.class).wage);
    }

    private Entity createCrew(CrewRole role, CrewRank rank, float xp) {
        Entity entity = new Entity();
        NpcStatsComponent stats = new NpcStatsComponent();
        entity.add(stats);

        CrewMemberComponent crew = new CrewMemberComponent();
        crew.role = role;
        crew.rank = rank;
        crew.xp = xp;
        crew.wage = rank.baseWage;
        entity.add(crew);

        return entity;
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat core:test --tests "com.galacticodyssey.npc.systems.CrewXPSystemTest" -q`
Expected: FAIL — `CrewXPSystem` does not exist yet

- [ ] **Step 3: Implement CrewXPSystem**

```java
// core/src/main/java/com/galacticodyssey/npc/systems/CrewXPSystem.java
package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRank;
import com.galacticodyssey.npc.events.CrewPromotedEvent;

public class CrewXPSystem extends EntitySystem {

    public static final int PRIORITY = 24;

    private static final ComponentMapper<CrewMemberComponent> CREW_M =
        ComponentMapper.getFor(CrewMemberComponent.class);

    private final EventBus eventBus;

    public CrewXPSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    public void awardXP(Entity crewEntity, float amount) {
        CrewMemberComponent crew = CREW_M.get(crewEntity);
        if (crew == null) return;
        crew.xp += amount;
    }

    public boolean isPromotionEligible(Entity crewEntity) {
        CrewMemberComponent crew = CREW_M.get(crewEntity);
        if (crew == null) return false;
        CrewRank next = crew.rank.nextRank();
        if (next == null) return false;
        return crew.xp >= next.xpThreshold;
    }

    public boolean promote(Entity crewEntity) {
        CrewMemberComponent crew = CREW_M.get(crewEntity);
        if (crew == null) return false;

        CrewRank next = crew.rank.nextRank();
        if (next == null) return false;
        if (crew.xp < next.xpThreshold) return false;

        CrewRank oldRank = crew.rank;
        crew.xp -= next.xpThreshold;
        crew.rank = next;
        crew.wage = next.baseWage;

        eventBus.publish(new CrewPromotedEvent(crewEntity, oldRank, next));
        return true;
    }

    @Override
    public void update(float deltaTime) {
        // XP awards and promotions are event-driven via public methods.
        // Future phases will subscribe to combat/mission events here.
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat core:test --tests "com.galacticodyssey.npc.systems.CrewXPSystemTest" -q`
Expected: All 8 tests pass

- [ ] **Step 5: Run full NPC test suite**

Run: `.\gradlew.bat core:test --tests "com.galacticodyssey.npc.*" -q`
Expected: All tests pass (NpcComponentsTest + NpcDataRegistryTest + NpcGeneratorTest + CrewAssignmentSystemTest + CrewXPSystemTest)

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/npc/systems/CrewXPSystem.java \
        core/src/test/java/com/galacticodyssey/npc/systems/CrewXPSystemTest.java
git commit -m "feat(npc): add CrewXPSystem with XP awards and manual promotion"
```

---

## Phase 1 Complete

After completing all 8 tasks, Phase 1 delivers:
- 6 enums, 6 components, 9 events — the full NPC/Crew data model
- `NpcDataRegistry` — loads species, backgrounds, perks, and names from JSON
- `NpcGenerator` — deterministic seed-based NPC creation
- `CrewAssignmentSystem` — computes crew effectiveness at ship stations
- `CrewXPSystem` — awards XP and handles manual promotion
- 5 test classes with ~40 tests covering all logic

**Next phases** (each gets its own plan after Phase 1 is integrated):
- Phase 2: MoraleSystem + CrewWageSystem
- Phase 3: UtilityBrain + NpcAISystem
- Phase 4: NpcNavigationSystem + ship interior nav graph
- Phase 5: Combat BTs + boarding integration
- Phase 6: World NPCs + recruitment flow
- Phase 7: Life support + advanced integration
