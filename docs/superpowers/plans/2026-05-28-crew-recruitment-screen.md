# Crew Recruitment Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a cantina-scene recruitment screen where players browse NPC candidates, view partial stats, talk to them via the existing DialogSystem, negotiate wages, and hire crew members.

**Architecture:** Scene2D-based overlay screen (`RecruitmentScreenSystem`) with NPC portrait actors placed in an atmospheric cantina scene. A `CandidatePoolSystem` generates recruitable NPCs per station. The screen follows a 5-state machine (BROWSE → SELECTED → DIALOG → OFFER → RESULT). All game content is data-driven via JSON files. Events flow through the existing `EventBus`.

**Tech Stack:** Java 17, libGDX (Scene2D, Ashley ECS), JUnit 5, Mockito

---

## File Structure

### New files — Components & Enums

| File | Responsibility |
|------|---------------|
| `core/src/main/java/com/galacticodyssey/npc/components/StatType.java` | Enum mapping stat names to `NpcStatsComponent` fields |
| `core/src/main/java/com/galacticodyssey/npc/components/RecruitInteractionState.java` | Enum: UNMET, TALKED, OFFERED, DECLINED |
| `core/src/main/java/com/galacticodyssey/npc/components/RecruitConditionType.java` | Enum: SPECIES_AVERSION, FACILITY_REQUIRED, FACTION_ALLEGIANCE, PERSONAL_QUEST |
| `core/src/main/java/com/galacticodyssey/npc/components/RecruitCondition.java` | Condition data class (type, targetId, description, met) |
| `core/src/main/java/com/galacticodyssey/npc/components/RecruitableComponent.java` | Ashley Component: wage range, conditions, revealed stats, dialog tree, interaction state |
| `core/src/main/java/com/galacticodyssey/npc/components/CantinaSeatComponent.java` | Ashley Component: seat position in cantina scene |

### New files — Events

| File | Responsibility |
|------|---------------|
| `core/src/main/java/com/galacticodyssey/npc/events/RecruitmentOpenedEvent.java` | Carries stationId |
| `core/src/main/java/com/galacticodyssey/npc/events/RecruitmentClosedEvent.java` | No payload |
| `core/src/main/java/com/galacticodyssey/npc/events/CandidateSelectedEvent.java` | Carries npcEntity |
| `core/src/main/java/com/galacticodyssey/npc/events/StatRevealedEvent.java` | Carries npcEntity, StatType, value |
| `core/src/main/java/com/galacticodyssey/npc/events/WageNegotiatedEvent.java` | Carries npcEntity, finalWage, discountPercent |

### New files — Data & Systems

| File | Responsibility |
|------|---------------|
| `core/src/main/java/com/galacticodyssey/npc/data/RecruitmentDataRegistry.java` | Loads recruit_conditions.json, cantina_layouts.json |
| `core/src/main/java/com/galacticodyssey/npc/data/CantinaSeatDefinition.java` | Data class for seat position from JSON |
| `core/src/main/java/com/galacticodyssey/npc/data/CantinaLayoutDefinition.java` | Data class for per-station cantina config |
| `core/src/main/java/com/galacticodyssey/npc/data/RecruitConditionDefinition.java` | Data class for condition template from JSON |
| `core/src/main/java/com/galacticodyssey/npc/systems/CandidatePoolSystem.java` | Generates recruitable NPC entities per station |

### New files — UI Actors

| File | Responsibility |
|------|---------------|
| `core/src/main/java/com/galacticodyssey/ui/actors/CantinaSceneActor.java` | Background scene rendering (gradient + ambient elements) |
| `core/src/main/java/com/galacticodyssey/ui/actors/NpcPortraitActor.java` | Clickable circular portrait + name tag for one NPC |
| `core/src/main/java/com/galacticodyssey/ui/actors/CandidateDetailOverlay.java` | Bottom slide-up panel with partial stats, quote, Talk/Dismiss |
| `core/src/main/java/com/galacticodyssey/ui/actors/HiringBoardOverlay.java` | List/filter modal overlay with role tabs and candidate rows |
| `core/src/main/java/com/galacticodyssey/ui/actors/HireConfirmationDialog.java` | Modal: full stats, negotiated wage, conditions, Hire/Decline |
| `core/src/main/java/com/galacticodyssey/ui/actors/ResultToast.java` | Brief "X hired" banner that fades out |
| `core/src/main/java/com/galacticodyssey/ui/systems/RecruitmentScreenSystem.java` | Owns Stage, manages state machine, wires actors and events |

### New files — Data (JSON)

| File | Responsibility |
|------|---------------|
| `core/src/main/resources/data/npcs/recruit_conditions.json` | Condition templates with weights |
| `core/src/main/resources/data/npcs/recruit_dialog_templates.json` | Parameterized dialog trees for recruitment |
| `core/src/main/resources/data/stations/cantina_layouts.json` | Per-station seat positions, capacity, background key |

### New files — Tests

| File | Responsibility |
|------|---------------|
| `core/src/test/java/com/galacticodyssey/npc/components/StatTypeTest.java` | StatType → NpcStatsComponent field mapping |
| `core/src/test/java/com/galacticodyssey/npc/data/RecruitmentDataRegistryTest.java` | JSON loading, condition lookup |
| `core/src/test/java/com/galacticodyssey/npc/systems/CandidatePoolSystemTest.java` | Candidate generation, pool refresh, persistence |
| `core/src/test/java/com/galacticodyssey/ui/systems/RecruitmentScreenSystemTest.java` | State machine transitions, hiring sequence |

### Modified files

| File | Change |
|------|--------|
| `core/src/main/java/com/galacticodyssey/core/GameWorld.java` | Register CandidatePoolSystem, RecruitmentScreenSystem |
| `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` | Wire RecruitmentScreenSystem rendering and input |

---

### Task 1: StatType Enum

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/components/StatType.java`
- Test: `core/src/test/java/com/galacticodyssey/npc/components/StatTypeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.npc.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatTypeTest {

    @Test
    void getValueReadsCorrectField() {
        NpcStatsComponent stats = new NpcStatsComponent();
        stats.accuracy = 78f;
        stats.repair = 85f;
        stats.medical = 60f;
        stats.piloting = 55f;
        stats.science = 68f;
        stats.combat = 72f;
        stats.persuasion = 45f;
        stats.stealth = 30f;

        assertEquals(78f, StatType.ACCURACY.getValue(stats));
        assertEquals(85f, StatType.REPAIR.getValue(stats));
        assertEquals(60f, StatType.MEDICAL.getValue(stats));
        assertEquals(55f, StatType.PILOTING.getValue(stats));
        assertEquals(68f, StatType.SCIENCE.getValue(stats));
        assertEquals(72f, StatType.COMBAT.getValue(stats));
        assertEquals(45f, StatType.PERSUASION.getValue(stats));
        assertEquals(30f, StatType.STEALTH.getValue(stats));
    }

    @Test
    void allValuesHaveThreeLetterAbbreviation() {
        for (StatType type : StatType.values()) {
            assertEquals(3, type.abbreviation.length(),
                type.name() + " abbreviation should be 3 chars");
        }
    }

    @Test
    void getTopNReturnsHighestStats() {
        NpcStatsComponent stats = new NpcStatsComponent();
        stats.accuracy = 10f;
        stats.repair = 85f;
        stats.medical = 20f;
        stats.piloting = 30f;
        stats.science = 68f;
        stats.combat = 5f;
        stats.persuasion = 40f;
        stats.stealth = 15f;

        var top2 = StatType.getTopN(stats, 2);
        assertEquals(2, top2.size());
        assertEquals(StatType.REPAIR, top2.get(0));
        assertEquals(StatType.SCIENCE, top2.get(1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests "com.galacticodyssey.npc.components.StatTypeTest" --info`
Expected: Compilation error — `StatType` does not exist.

- [ ] **Step 3: Implement StatType**

```java
package com.galacticodyssey.npc.components;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

public enum StatType {
    ACCURACY("ACC", s -> s.accuracy),
    REPAIR("REP", s -> s.repair),
    MEDICAL("MED", s -> s.medical),
    PILOTING("PIL", s -> s.piloting),
    SCIENCE("SCI", s -> s.science),
    COMBAT("CMB", s -> s.combat),
    PERSUASION("PER", s -> s.persuasion),
    STEALTH("STL", s -> s.stealth);

    public final String abbreviation;
    private final ToDoubleFunction<NpcStatsComponent> getter;

    StatType(String abbreviation, ToDoubleFunction<NpcStatsComponent> getter) {
        this.abbreviation = abbreviation;
        this.getter = getter;
    }

    public float getValue(NpcStatsComponent stats) {
        return (float) getter.applyAsDouble(stats);
    }

    public static List<StatType> getTopN(NpcStatsComponent stats, int n) {
        List<StatType> sorted = new ArrayList<>(List.of(values()));
        sorted.sort(Comparator.comparingDouble((StatType t) -> t.getValue(stats)).reversed());
        return sorted.subList(0, Math.min(n, sorted.size()));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests "com.galacticodyssey.npc.components.StatTypeTest" --info`
Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/npc/components/StatType.java \
        core/src/test/java/com/galacticodyssey/npc/components/StatTypeTest.java
git commit -m "feat(recruitment): add StatType enum with NpcStatsComponent field mapping"
```

---

### Task 2: Enums and RecruitCondition

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/components/RecruitInteractionState.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/components/RecruitConditionType.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/components/RecruitCondition.java`

- [ ] **Step 1: Create RecruitInteractionState**

```java
package com.galacticodyssey.npc.components;

public enum RecruitInteractionState {
    UNMET,
    TALKED,
    OFFERED,
    DECLINED
}
```

- [ ] **Step 2: Create RecruitConditionType**

```java
package com.galacticodyssey.npc.components;

public enum RecruitConditionType {
    SPECIES_AVERSION,
    FACILITY_REQUIRED,
    FACTION_ALLEGIANCE,
    PERSONAL_QUEST
}
```

- [ ] **Step 3: Create RecruitCondition**

```java
package com.galacticodyssey.npc.components;

public final class RecruitCondition {
    public final RecruitConditionType type;
    public final String targetId;
    public final String description;
    public boolean met;

    public RecruitCondition(RecruitConditionType type, String targetId, String description) {
        this.type = type;
        this.targetId = targetId;
        this.description = description;
        this.met = false;
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/npc/components/RecruitInteractionState.java \
        core/src/main/java/com/galacticodyssey/npc/components/RecruitConditionType.java \
        core/src/main/java/com/galacticodyssey/npc/components/RecruitCondition.java
git commit -m "feat(recruitment): add RecruitInteractionState, RecruitConditionType, RecruitCondition"
```

---

### Task 3: ECS Components

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/components/RecruitableComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/components/CantinaSeatComponent.java`

- [ ] **Step 1: Create RecruitableComponent**

```java
package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class RecruitableComponent implements Component {
    public float askingWageMin;
    public float askingWageMax;
    public float negotiatedWage = -1f;
    public final List<RecruitCondition> conditions = new ArrayList<>();
    public final EnumSet<StatType> revealedStats = EnumSet.noneOf(StatType.class);
    public String dialogTreeId;
    public RecruitInteractionState interactionState = RecruitInteractionState.UNMET;
    public String hookLine;
}
```

- [ ] **Step 2: Create CantinaSeatComponent**

```java
package com.galacticodyssey.npc.components;

import com.badlogic.ashley.core.Component;

public class CantinaSeatComponent implements Component {
    public String seatId;
    public float sceneX;
    public float sceneY;
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/npc/components/RecruitableComponent.java \
        core/src/main/java/com/galacticodyssey/npc/components/CantinaSeatComponent.java
git commit -m "feat(recruitment): add RecruitableComponent and CantinaSeatComponent"
```

---

### Task 4: Events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/events/RecruitmentOpenedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/events/RecruitmentClosedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/events/CandidateSelectedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/events/StatRevealedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/events/WageNegotiatedEvent.java`

- [ ] **Step 1: Create RecruitmentOpenedEvent**

```java
package com.galacticodyssey.npc.events;

public final class RecruitmentOpenedEvent {
    public final String stationId;

    public RecruitmentOpenedEvent(String stationId) {
        this.stationId = stationId;
    }
}
```

- [ ] **Step 2: Create RecruitmentClosedEvent**

```java
package com.galacticodyssey.npc.events;

public final class RecruitmentClosedEvent {
    public RecruitmentClosedEvent() {}
}
```

- [ ] **Step 3: Create CandidateSelectedEvent**

```java
package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;

public final class CandidateSelectedEvent {
    public final Entity npcEntity;

    public CandidateSelectedEvent(Entity npcEntity) {
        this.npcEntity = npcEntity;
    }
}
```

- [ ] **Step 4: Create StatRevealedEvent**

```java
package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.npc.components.StatType;

public final class StatRevealedEvent {
    public final Entity npcEntity;
    public final StatType stat;
    public final float value;

    public StatRevealedEvent(Entity npcEntity, StatType stat, float value) {
        this.npcEntity = npcEntity;
        this.stat = stat;
        this.value = value;
    }
}
```

- [ ] **Step 5: Create WageNegotiatedEvent**

```java
package com.galacticodyssey.npc.events;

import com.badlogic.ashley.core.Entity;

public final class WageNegotiatedEvent {
    public final Entity npcEntity;
    public final float finalWage;
    public final float discountPercent;

    public WageNegotiatedEvent(Entity npcEntity, float finalWage, float discountPercent) {
        this.npcEntity = npcEntity;
        this.finalWage = finalWage;
        this.discountPercent = discountPercent;
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/npc/events/RecruitmentOpenedEvent.java \
        core/src/main/java/com/galacticodyssey/npc/events/RecruitmentClosedEvent.java \
        core/src/main/java/com/galacticodyssey/npc/events/CandidateSelectedEvent.java \
        core/src/main/java/com/galacticodyssey/npc/events/StatRevealedEvent.java \
        core/src/main/java/com/galacticodyssey/npc/events/WageNegotiatedEvent.java
git commit -m "feat(recruitment): add recruitment events"
```

---

### Task 5: JSON Data Files

**Files:**
- Create: `core/src/main/resources/data/npcs/recruit_conditions.json`
- Create: `core/src/main/resources/data/npcs/recruit_dialog_templates.json`
- Create: `core/src/main/resources/data/stations/cantina_layouts.json`

- [ ] **Step 1: Create recruit_conditions.json**

```json
[
  {
    "id": "no_krethians",
    "type": "SPECIES_AVERSION",
    "targetId": "krethian",
    "description": "Won't serve alongside Krethians",
    "weight": 0.15
  },
  {
    "id": "no_veloxi",
    "type": "SPECIES_AVERSION",
    "targetId": "veloxi",
    "description": "Won't serve alongside Veloxi",
    "weight": 0.10
  },
  {
    "id": "needs_medbay",
    "type": "FACILITY_REQUIRED",
    "targetId": "MEDBAY",
    "description": "Requires a functional med bay on board",
    "weight": 0.10
  },
  {
    "id": "needs_engine_room",
    "type": "FACILITY_REQUIRED",
    "targetId": "ENGINE_ROOM",
    "description": "Requires a proper engine room",
    "weight": 0.08
  },
  {
    "id": "anti_hegemony",
    "type": "FACTION_ALLEGIANCE",
    "targetId": "hegemony",
    "description": "Refuses jobs for the Hegemony",
    "weight": 0.08
  },
  {
    "id": "anti_syndicate",
    "type": "FACTION_ALLEGIANCE",
    "targetId": "syndicate",
    "description": "Won't work with the Syndicate",
    "weight": 0.08
  },
  {
    "id": "find_brother",
    "type": "PERSONAL_QUEST",
    "targetId": "quest_find_brother",
    "description": "Help me find my missing brother",
    "weight": 0.05
  },
  {
    "id": "clear_name",
    "type": "PERSONAL_QUEST",
    "targetId": "quest_clear_name",
    "description": "Clear my name from false charges",
    "weight": 0.05
  }
]
```

- [ ] **Step 2: Create recruit_dialog_templates.json**

```json
{
  "recruitment_standard": {
    "entryNode": "greeting",
    "nodes": {
      "greeting": {
        "speaker": "npc",
        "text": "{{npc.hookLine}}",
        "choices": [
          { "text": "Tell me about your combat experience.", "next": "combat_reveal", "reveals": ["COMBAT", "ACCURACY"] },
          { "text": "What's your technical background?", "next": "tech_reveal", "reveals": ["REPAIR", "SCIENCE"] },
          { "text": "Ever piloted a ship?", "next": "pilot_reveal", "reveals": ["PILOTING"] },
          { "text": "How do you handle people?", "next": "social_reveal", "reveals": ["PERSUASION"] },
          { "text": "Can you move quietly when needed?", "next": "stealth_reveal", "reveals": ["STEALTH"] },
          { "text": "What medical training do you have?", "next": "medical_reveal", "reveals": ["MEDICAL"] },
          { "text": "What's your asking wage?", "next": "wage_discuss" },
          { "text": "I'd like to offer you a position.", "next": "offer" },
          { "text": "Not interested. Good luck.", "next": "exit" }
        ]
      },
      "combat_reveal": {
        "speaker": "npc",
        "text": "{{npc.combatResponse}}",
        "choices": [
          { "text": "Impressive. What else can you tell me?", "next": "greeting" },
          { "text": "Let's talk terms.", "next": "wage_discuss" }
        ]
      },
      "tech_reveal": {
        "speaker": "npc",
        "text": "{{npc.techResponse}}",
        "choices": [
          { "text": "Good to know. What else?", "next": "greeting" },
          { "text": "Let's talk terms.", "next": "wage_discuss" }
        ]
      },
      "pilot_reveal": {
        "speaker": "npc",
        "text": "{{npc.pilotResponse}}",
        "choices": [
          { "text": "Interesting. Tell me more about yourself.", "next": "greeting" },
          { "text": "Let's talk terms.", "next": "wage_discuss" }
        ]
      },
      "social_reveal": {
        "speaker": "npc",
        "text": "{{npc.socialResponse}}",
        "choices": [
          { "text": "Good. Anything else I should know?", "next": "greeting" },
          { "text": "Let's talk terms.", "next": "wage_discuss" }
        ]
      },
      "stealth_reveal": {
        "speaker": "npc",
        "text": "{{npc.stealthResponse}}",
        "choices": [
          { "text": "Noted. What else?", "next": "greeting" },
          { "text": "Let's talk terms.", "next": "wage_discuss" }
        ]
      },
      "medical_reveal": {
        "speaker": "npc",
        "text": "{{npc.medicalResponse}}",
        "choices": [
          { "text": "Thanks. Tell me more.", "next": "greeting" },
          { "text": "Let's talk terms.", "next": "wage_discuss" }
        ]
      },
      "wage_discuss": {
        "speaker": "npc",
        "text": "I'm looking for {{npc.wageMin}} to {{npc.wageMax}} credits a week. Fair for what I bring.",
        "choices": [
          { "text": "That's steep. Can we work something out?", "next": "negotiate", "action": "NEGOTIATE" },
          { "text": "That sounds fair.", "next": "offer" },
          { "text": "I need to think about it.", "next": "exit_friendly" }
        ]
      },
      "negotiate": {
        "speaker": "npc",
        "text": "{{npc.negotiateResponse}}",
        "choices": [
          { "text": "I'd like to offer you a position.", "next": "offer" },
          { "text": "Let me think about it.", "next": "exit_friendly" }
        ]
      },
      "offer": {
        "speaker": "system",
        "text": "",
        "action": "SHOW_OFFER"
      },
      "exit_friendly": {
        "speaker": "npc",
        "text": "Sure, I'll be around. Come find me if you change your mind.",
        "action": "CLOSE_DIALOG"
      },
      "exit": {
        "speaker": "npc",
        "text": "Your loss. Good luck out there.",
        "action": "CLOSE_DIALOG"
      }
    }
  }
}
```

- [ ] **Step 3: Create stations directory and cantina_layouts.json**

First verify the data/stations directory exists:
Run: `ls core/src/main/resources/data/stations/ 2>/dev/null || mkdir -p core/src/main/resources/data/stations`

```json
{
  "nexus_station": {
    "backgroundKey": "cantina_nexus",
    "capacity": 5,
    "seats": [
      { "id": "bar_stool_1", "x": 0.12, "y": 0.35 },
      { "id": "bar_stool_2", "x": 0.25, "y": 0.38 },
      { "id": "center_table", "x": 0.45, "y": 0.48 },
      { "id": "corner_booth", "x": 0.72, "y": 0.30 },
      { "id": "wall_standing", "x": 0.82, "y": 0.55 }
    ],
    "hiringBoard": { "x": 0.30, "y": 0.22 }
  },
  "frontier_outpost": {
    "backgroundKey": "cantina_frontier",
    "capacity": 3,
    "seats": [
      { "id": "counter_1", "x": 0.20, "y": 0.40 },
      { "id": "table_1", "x": 0.50, "y": 0.50 },
      { "id": "corner_1", "x": 0.78, "y": 0.35 }
    ],
    "hiringBoard": { "x": 0.15, "y": 0.25 }
  },
  "trade_hub": {
    "backgroundKey": "cantina_trade",
    "capacity": 8,
    "seats": [
      { "id": "bar_1", "x": 0.08, "y": 0.32 },
      { "id": "bar_2", "x": 0.16, "y": 0.35 },
      { "id": "bar_3", "x": 0.24, "y": 0.33 },
      { "id": "booth_1", "x": 0.42, "y": 0.28 },
      { "id": "booth_2", "x": 0.42, "y": 0.52 },
      { "id": "table_1", "x": 0.60, "y": 0.42 },
      { "id": "standing_1", "x": 0.78, "y": 0.30 },
      { "id": "standing_2", "x": 0.85, "y": 0.55 }
    ],
    "hiringBoard": { "x": 0.35, "y": 0.18 }
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add core/src/main/resources/data/npcs/recruit_conditions.json \
        core/src/main/resources/data/npcs/recruit_dialog_templates.json \
        core/src/main/resources/data/stations/cantina_layouts.json
git commit -m "feat(recruitment): add JSON data files for conditions, dialogs, and cantina layouts"
```

---

### Task 6: Recruitment Data Registry

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/data/RecruitConditionDefinition.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/data/CantinaSeatDefinition.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/data/CantinaLayoutDefinition.java`
- Create: `core/src/main/java/com/galacticodyssey/npc/data/RecruitmentDataRegistry.java`
- Test: `core/src/test/java/com/galacticodyssey/npc/data/RecruitmentDataRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.npc.data;

import com.galacticodyssey.npc.components.RecruitConditionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecruitmentDataRegistryTest {

    private RecruitmentDataRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RecruitmentDataRegistry();
    }

    @Test
    void registerAndRetrieveCondition() {
        var def = new RecruitConditionDefinition();
        def.id = "no_krethians";
        def.type = RecruitConditionType.SPECIES_AVERSION;
        def.targetId = "krethian";
        def.description = "Won't serve alongside Krethians";
        def.weight = 0.15f;

        registry.registerCondition(def);

        assertEquals(1, registry.getAllConditions().size());
        assertSame(def, registry.getCondition("no_krethians"));
    }

    @Test
    void registerAndRetrieveLayout() {
        var seat = new CantinaSeatDefinition();
        seat.id = "bar_stool_1";
        seat.x = 0.12f;
        seat.y = 0.35f;

        var layout = new CantinaLayoutDefinition();
        layout.backgroundKey = "cantina_nexus";
        layout.capacity = 5;
        layout.seats.add(seat);
        layout.hiringBoardX = 0.30f;
        layout.hiringBoardY = 0.22f;

        registry.registerLayout("nexus_station", layout);

        assertSame(layout, registry.getLayout("nexus_station"));
        assertEquals(5, layout.capacity);
        assertEquals(1, layout.seats.size());
    }

    @Test
    void getLayoutReturnsNullForUnknownStation() {
        assertNull(registry.getLayout("unknown"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests "com.galacticodyssey.npc.data.RecruitmentDataRegistryTest" --info`
Expected: Compilation error — classes don't exist.

- [ ] **Step 3: Create data classes**

`RecruitConditionDefinition.java`:
```java
package com.galacticodyssey.npc.data;

import com.galacticodyssey.npc.components.RecruitConditionType;

public class RecruitConditionDefinition {
    public String id;
    public RecruitConditionType type;
    public String targetId;
    public String description;
    public float weight;
}
```

`CantinaSeatDefinition.java`:
```java
package com.galacticodyssey.npc.data;

public class CantinaSeatDefinition {
    public String id;
    public float x;
    public float y;
}
```

`CantinaLayoutDefinition.java`:
```java
package com.galacticodyssey.npc.data;

import java.util.ArrayList;
import java.util.List;

public class CantinaLayoutDefinition {
    public String backgroundKey;
    public int capacity;
    public final List<CantinaSeatDefinition> seats = new ArrayList<>();
    public float hiringBoardX;
    public float hiringBoardY;
}
```

- [ ] **Step 4: Create RecruitmentDataRegistry**

```java
package com.galacticodyssey.npc.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.galacticodyssey.npc.components.RecruitConditionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecruitmentDataRegistry {

    private final Map<String, RecruitConditionDefinition> conditions = new HashMap<>();
    private final Map<String, CantinaLayoutDefinition> layouts = new HashMap<>();

    public void registerCondition(RecruitConditionDefinition def) {
        conditions.put(def.id, def);
    }

    public RecruitConditionDefinition getCondition(String id) {
        return conditions.get(id);
    }

    public List<RecruitConditionDefinition> getAllConditions() {
        return new ArrayList<>(conditions.values());
    }

    public void registerLayout(String stationId, CantinaLayoutDefinition layout) {
        layouts.put(stationId, layout);
    }

    public CantinaLayoutDefinition getLayout(String stationId) {
        return layouts.get(stationId);
    }

    public void loadFromFiles() {
        JsonReader reader = new JsonReader();
        Json json = new Json();

        JsonValue condRoot = reader.parse(Gdx.files.internal("data/npcs/recruit_conditions.json"));
        for (JsonValue entry = condRoot.child; entry != null; entry = entry.next) {
            RecruitConditionDefinition def = new RecruitConditionDefinition();
            def.id = entry.getString("id");
            def.type = RecruitConditionType.valueOf(entry.getString("type"));
            def.targetId = entry.getString("targetId");
            def.description = entry.getString("description");
            def.weight = entry.getFloat("weight");
            registerCondition(def);
        }

        JsonValue layoutRoot = reader.parse(Gdx.files.internal("data/stations/cantina_layouts.json"));
        for (JsonValue entry = layoutRoot.child; entry != null; entry = entry.next) {
            CantinaLayoutDefinition layout = new CantinaLayoutDefinition();
            layout.backgroundKey = entry.getString("backgroundKey");
            layout.capacity = entry.getInt("capacity");
            JsonValue seatsArr = entry.get("seats");
            for (JsonValue s = seatsArr.child; s != null; s = s.next) {
                CantinaSeatDefinition seat = new CantinaSeatDefinition();
                seat.id = s.getString("id");
                seat.x = s.getFloat("x");
                seat.y = s.getFloat("y");
                layout.seats.add(seat);
            }
            JsonValue board = entry.get("hiringBoard");
            layout.hiringBoardX = board.getFloat("x");
            layout.hiringBoardY = board.getFloat("y");
            registerLayout(entry.name, layout);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew core:test --tests "com.galacticodyssey.npc.data.RecruitmentDataRegistryTest" --info`
Expected: All 3 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/npc/data/RecruitConditionDefinition.java \
        core/src/main/java/com/galacticodyssey/npc/data/CantinaSeatDefinition.java \
        core/src/main/java/com/galacticodyssey/npc/data/CantinaLayoutDefinition.java \
        core/src/main/java/com/galacticodyssey/npc/data/RecruitmentDataRegistry.java \
        core/src/test/java/com/galacticodyssey/npc/data/RecruitmentDataRegistryTest.java
git commit -m "feat(recruitment): add RecruitmentDataRegistry with condition and layout loading"
```

---

### Task 7: CandidatePoolSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/npc/systems/CandidatePoolSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/npc/systems/CandidatePoolSystemTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.components.*;
import com.galacticodyssey.npc.data.*;
import com.galacticodyssey.npc.events.RecruitmentOpenedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CandidatePoolSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private NpcDataRegistry npcRegistry;
    private RecruitmentDataRegistry recruitRegistry;
    private CandidatePoolSystem system;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        npcRegistry = new NpcDataRegistry();
        recruitRegistry = new RecruitmentDataRegistry();

        // Register minimal species/background/names data
        SpeciesDefinition human = new SpeciesDefinition();
        human.id = "human";
        human.name = "Human";
        npcRegistry.registerSpecies(human);

        BackgroundDefinition military = new BackgroundDefinition();
        military.id = "military";
        military.name = "Military";
        npcRegistry.registerBackground(military);

        npcRegistry.registerNames("human",
            java.util.List.of("Kira", "Orin", "Garak"),
            java.util.List.of("Voss", "Mael", "Durn"));

        // Register a cantina layout
        CantinaLayoutDefinition layout = new CantinaLayoutDefinition();
        layout.backgroundKey = "cantina_test";
        layout.capacity = 3;
        for (int i = 0; i < 3; i++) {
            CantinaSeatDefinition seat = new CantinaSeatDefinition();
            seat.id = "seat_" + i;
            seat.x = 0.2f + i * 0.3f;
            seat.y = 0.4f;
            layout.seats.add(seat);
        }
        layout.hiringBoardX = 0.5f;
        layout.hiringBoardY = 0.2f;
        recruitRegistry.registerLayout("test_station", layout);

        NpcGenerator generator = new NpcGenerator(npcRegistry);
        system = new CandidatePoolSystem(eventBus, generator, recruitRegistry);
        engine.addSystem(system);
    }

    @Test
    void generatesCorrectNumberOfCandidates() {
        eventBus.publish(new RecruitmentOpenedEvent("test_station"));

        var candidates = engine.getEntitiesFor(
            Family.all(RecruitableComponent.class, CantinaSeatComponent.class).get());

        assertEquals(3, candidates.size());
    }

    @Test
    void candidatesHaveRequiredComponents() {
        eventBus.publish(new RecruitmentOpenedEvent("test_station"));

        var candidates = engine.getEntitiesFor(
            Family.all(RecruitableComponent.class, CantinaSeatComponent.class).get());

        for (Entity e : candidates) {
            assertNotNull(e.getComponent(NpcIdentityComponent.class));
            assertNotNull(e.getComponent(NpcStatsComponent.class));
            assertNotNull(e.getComponent(RecruitableComponent.class));
            assertNotNull(e.getComponent(CantinaSeatComponent.class));

            RecruitableComponent rc = e.getComponent(RecruitableComponent.class);
            assertEquals(RecruitInteractionState.UNMET, rc.interactionState);
            assertTrue(rc.askingWageMin > 0);
            assertTrue(rc.askingWageMax >= rc.askingWageMin);
            assertNotNull(rc.hookLine);
            assertFalse(rc.revealedStats.isEmpty());
        }
    }

    @Test
    void doesNotRegenerateIfPoolAlreadyExists() {
        eventBus.publish(new RecruitmentOpenedEvent("test_station"));
        eventBus.publish(new RecruitmentOpenedEvent("test_station"));

        var candidates = engine.getEntitiesFor(
            Family.all(RecruitableComponent.class, CantinaSeatComponent.class).get());

        assertEquals(3, candidates.size());
    }

    @Test
    void noOpForUnknownStation() {
        eventBus.publish(new RecruitmentOpenedEvent("nonexistent"));

        var candidates = engine.getEntitiesFor(
            Family.all(RecruitableComponent.class, CantinaSeatComponent.class).get());

        assertEquals(0, candidates.size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests "com.galacticodyssey.npc.systems.CandidatePoolSystemTest" --info`
Expected: Compilation error — `CandidatePoolSystem` does not exist.

- [ ] **Step 3: Implement CandidatePoolSystem**

```java
package com.galacticodyssey.npc.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.components.*;
import com.galacticodyssey.npc.data.*;
import com.galacticodyssey.npc.events.RecruitmentOpenedEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class CandidatePoolSystem extends EntitySystem {

    private final EventBus eventBus;
    private final NpcGenerator generator;
    private final RecruitmentDataRegistry recruitRegistry;
    private final Set<String> activeStations = new HashSet<>();
    private Engine engine;

    public CandidatePoolSystem(EventBus eventBus, NpcGenerator generator,
                                RecruitmentDataRegistry recruitRegistry) {
        super(0);
        this.eventBus = eventBus;
        this.generator = generator;
        this.recruitRegistry = recruitRegistry;
        eventBus.subscribe(RecruitmentOpenedEvent.class, this::onRecruitmentOpened);
    }

    @Override
    public void addedToEngine(Engine engine) {
        this.engine = engine;
    }

    private void onRecruitmentOpened(RecruitmentOpenedEvent event) {
        if (activeStations.contains(event.stationId)) {
            return;
        }

        CantinaLayoutDefinition layout = recruitRegistry.getLayout(event.stationId);
        if (layout == null) {
            return;
        }

        activeStations.add(event.stationId);
        generateCandidates(event.stationId, layout);
    }

    private void generateCandidates(String stationId, CantinaLayoutDefinition layout) {
        int count = Math.min(layout.capacity, layout.seats.size());
        List<RecruitConditionDefinition> allConditions = recruitRegistry.getAllConditions();

        for (int i = 0; i < count; i++) {
            long seed = stationId.hashCode() ^ (long) i ^ System.nanoTime();
            Entity npc = generator.generate(engine, seed);

            NpcStatsComponent stats = npc.getComponent(NpcStatsComponent.class);
            NpcIdentityComponent identity = npc.getComponent(NpcIdentityComponent.class);

            RecruitableComponent rc = new RecruitableComponent();
            float baseWage = com.galacticodyssey.npc.crew.CrewRank.RECRUIT.baseWage;
            float statAvg = (stats.accuracy + stats.repair + stats.medical +
                             stats.piloting + stats.science + stats.combat +
                             stats.persuasion + stats.stealth) / 8f;
            float qualityMod = 1f + (statAvg - 50f) / 100f;
            Random rng = new Random(seed);
            rc.askingWageMin = baseWage * qualityMod * (0.9f + rng.nextFloat() * 0.1f);
            rc.askingWageMax = rc.askingWageMin * (1.1f + rng.nextFloat() * 0.1f);
            rc.dialogTreeId = "recruitment_standard";
            rc.interactionState = RecruitInteractionState.UNMET;

            List<StatType> topStats = StatType.getTopN(stats, 2);
            rc.revealedStats.addAll(topStats);

            String name = identity.name != null ? identity.name : "Stranger";
            rc.hookLine = generateHookLine(identity, rng);

            rollConditions(rc, allConditions, rng);

            npc.add(rc);

            CantinaSeatComponent seat = new CantinaSeatComponent();
            CantinaSeatDefinition seatDef = layout.seats.get(i);
            seat.seatId = seatDef.id;
            seat.sceneX = seatDef.x;
            seat.sceneY = seatDef.y;
            npc.add(seat);
        }
    }

    private String generateHookLine(NpcIdentityComponent identity, Random rng) {
        String[] hooks = {
            "Looking for work. Got skills.",
            "Need a ship. You need crew. Simple.",
            "Heard you're hiring. I'm available.",
            "I can handle myself. Try me.",
            "Don't let appearances fool you."
        };
        return hooks[rng.nextInt(hooks.length)];
    }

    private void rollConditions(RecruitableComponent rc,
                                 List<RecruitConditionDefinition> allConditions, Random rng) {
        float roll = rng.nextFloat();
        int conditionCount;
        if (roll < 0.60f) conditionCount = 0;
        else if (roll < 0.90f) conditionCount = 1;
        else conditionCount = 2;

        for (int c = 0; c < conditionCount && !allConditions.isEmpty(); c++) {
            float totalWeight = 0f;
            for (RecruitConditionDefinition def : allConditions) {
                totalWeight += def.weight;
            }
            float pick = rng.nextFloat() * totalWeight;
            float acc = 0f;
            for (RecruitConditionDefinition def : allConditions) {
                acc += def.weight;
                if (acc >= pick) {
                    rc.conditions.add(new RecruitCondition(def.type, def.targetId, def.description));
                    break;
                }
            }
        }
    }

    public void clearStation(String stationId) {
        activeStations.remove(stationId);
        if (engine == null) return;

        ImmutableArray<Entity> candidates = engine.getEntitiesFor(
            Family.all(RecruitableComponent.class, CantinaSeatComponent.class).get());
        for (int i = candidates.size() - 1; i >= 0; i--) {
            engine.removeEntity(candidates.get(i));
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests "com.galacticodyssey.npc.systems.CandidatePoolSystemTest" --info`
Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/npc/systems/CandidatePoolSystem.java \
        core/src/test/java/com/galacticodyssey/npc/systems/CandidatePoolSystemTest.java
git commit -m "feat(recruitment): add CandidatePoolSystem for generating recruitable NPCs"
```

---

### Task 8: CantinaSceneActor

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/actors/CantinaSceneActor.java`

- [ ] **Step 1: Implement CantinaSceneActor**

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Disposable;

public class CantinaSceneActor extends Group implements Disposable {

    private Texture backgroundTexture;

    public CantinaSceneActor(float width, float height) {
        setSize(width, height);
        createPlaceholderBackground();
    }

    private void createPlaceholderBackground() {
        int w = 256;
        int h = 144;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);

        for (int y = 0; y < h; y++) {
            float t = (float) y / h;
            int r = (int) (13 + t * 20);
            int g = (int) (17 + t * 10);
            int b = (int) (30 + t * 15);
            pm.setColor(r / 255f, g / 255f, b / 255f, 1f);
            pm.drawLine(0, y, w - 1, y);
        }

        // Bar counter line
        int barY = (int) (h * 0.7f);
        pm.setColor(0.25f, 0.22f, 0.2f, 1f);
        pm.drawLine(w / 10, barY, w * 6 / 10, barY);
        pm.drawLine(w / 10, barY + 1, w * 6 / 10, barY + 1);

        // Ceiling light spots
        pm.setColor(0.9f, 0.27f, 0.37f, 0.12f);
        pm.fillCircle(w / 4, 5, 20);
        pm.setColor(0.2f, 1f, 0.47f, 0.08f);
        pm.fillCircle(w * 3 / 5, 5, 20);

        backgroundTexture = new Texture(pm);
        pm.dispose();

        Image bgImage = new Image(new TextureRegion(backgroundTexture));
        bgImage.setSize(getWidth(), getHeight());
        addActor(bgImage);
    }

    @Override
    public void dispose() {
        if (backgroundTexture != null) {
            backgroundTexture.dispose();
            backgroundTexture = null;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/actors/CantinaSceneActor.java
git commit -m "feat(recruitment): add CantinaSceneActor with placeholder gradient background"
```

---

### Task 9: NpcPortraitActor

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/actors/NpcPortraitActor.java`

- [ ] **Step 1: Implement NpcPortraitActor**

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.npc.components.*;

import java.util.List;
import java.util.function.Consumer;

public class NpcPortraitActor extends Group implements Disposable {

    private static final float PORTRAIT_SIZE = 64f;
    private static final float HOVER_SCALE = 1.05f;

    private static final ComponentMapper<NpcIdentityComponent> IDENTITY_M =
        ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);
    private static final ComponentMapper<RecruitableComponent> RECRUIT_M =
        ComponentMapper.getFor(RecruitableComponent.class);

    private final Entity entity;
    private Texture portraitTexture;
    private boolean selected;

    public NpcPortraitActor(Entity entity, Skin skin, Consumer<Entity> onClick) {
        this.entity = entity;
        setTouchable(Touchable.enabled);

        NpcIdentityComponent identity = IDENTITY_M.get(entity);
        NpcStatsComponent stats = STATS_M.get(entity);
        RecruitableComponent rc = RECRUIT_M.get(entity);

        Color speciesColor = getSpeciesColor(identity.species);

        // Portrait circle (placeholder)
        portraitTexture = createCircleTexture(32, speciesColor);
        Image portrait = new Image(new TextureRegion(portraitTexture));
        portrait.setSize(PORTRAIT_SIZE, PORTRAIT_SIZE);
        portrait.setPosition(0, 0);
        addActor(portrait);

        // Name tag below portrait
        Table nameTag = new Table();
        nameTag.setBackground(createSolidDrawable(new Color(0.04f, 0.05f, 0.09f, 0.8f)));
        nameTag.pad(4);

        String name = identity.name != null ? identity.name : "Unknown";
        Label nameLabel = new Label(name, skin, "slot-name");
        nameLabel.setColor(speciesColor);
        nameTag.add(nameLabel).left().row();

        String subtitle = (identity.species != null ? identity.species : "Unknown") +
            " · " + (identity.role != null ? identity.role.name() : "");
        Label subtitleLabel = new Label(subtitle, skin, "slot-meta");
        subtitleLabel.setColor(0.53f, 0.55f, 0.7f, 1f);
        nameTag.add(subtitleLabel).left().row();

        if (rc != null && stats != null) {
            List<StatType> topStats = StatType.getTopN(stats, 2);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < topStats.size(); i++) {
                if (i > 0) sb.append(" · ");
                StatType st = topStats.get(i);
                sb.append(st.abbreviation).append(" ").append((int) st.getValue(stats));
            }
            Label statsLabel = new Label(sb.toString(), skin, "slot-meta");
            statsLabel.setColor(0.33f, 0.81f, 0.55f, 1f);
            nameTag.add(statsLabel).left();
        }

        nameTag.pack();
        nameTag.setPosition(PORTRAIT_SIZE / 2 - nameTag.getWidth() / 2, -nameTag.getHeight() - 6);
        addActor(nameTag);

        setSize(PORTRAIT_SIZE, PORTRAIT_SIZE);
        setOrigin(Align.center);

        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onClick.accept(entity);
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer,
                              com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                if (!selected) {
                    addAction(Actions.scaleTo(HOVER_SCALE, HOVER_SCALE, 0.1f));
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer,
                             com.badlogic.gdx.scenes.scene2d.Actor toActor) {
                if (!selected) {
                    addAction(Actions.scaleTo(1f, 1f, 0.1f));
                }
            }
        });
    }

    public Entity getEntity() {
        return entity;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        if (selected) {
            addAction(Actions.scaleTo(HOVER_SCALE, HOVER_SCALE, 0.15f));
        } else {
            addAction(Actions.scaleTo(1f, 1f, 0.15f));
        }
    }

    private Texture createCircleTexture(int radius, Color color) {
        int size = radius * 2;
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setColor(color.r * 0.3f, color.g * 0.3f, color.b * 0.3f, 1f);
        pm.fillCircle(radius, radius, radius);
        pm.setColor(color);
        pm.drawCircle(radius, radius, radius - 1);
        pm.drawCircle(radius, radius, radius - 2);
        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    private TextureRegionDrawable createSolidDrawable(Color color) {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(color);
        pm.fill();
        Texture tex = new Texture(pm);
        pm.dispose();
        return new TextureRegionDrawable(new TextureRegion(tex));
    }

    private Color getSpeciesColor(String species) {
        if (species == null) return new Color(0.9f, 0.27f, 0.37f, 1f);
        return switch (species.toLowerCase()) {
            case "veloxi" -> new Color(0.2f, 1f, 0.47f, 1f);
            case "krethian" -> new Color(0.8f, 0.5f, 0.2f, 1f);
            default -> new Color(0.9f, 0.27f, 0.37f, 1f);
        };
    }

    @Override
    public void dispose() {
        if (portraitTexture != null) {
            portraitTexture.dispose();
            portraitTexture = null;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/actors/NpcPortraitActor.java
git commit -m "feat(recruitment): add NpcPortraitActor with portrait, name tag, and hover effects"
```

---

### Task 10: CandidateDetailOverlay

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/actors/CandidateDetailOverlay.java`

- [ ] **Step 1: Implement CandidateDetailOverlay**

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.npc.components.*;
import com.galacticodyssey.npc.crew.CrewRank;

import java.util.EnumSet;
import java.util.function.Consumer;

public class CandidateDetailOverlay extends Table implements Disposable {

    private static final ComponentMapper<NpcIdentityComponent> IDENTITY_M =
        ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);
    private static final ComponentMapper<RecruitableComponent> RECRUIT_M =
        ComponentMapper.getFor(RecruitableComponent.class);

    private final Skin skin;
    private final Consumer<Entity> onTalk;
    private final Runnable onDismiss;
    private Texture bgTexture;
    private Entity currentEntity;

    public CandidateDetailOverlay(Skin skin, Consumer<Entity> onTalk, Runnable onDismiss) {
        this.skin = skin;
        this.onTalk = onTalk;
        this.onDismiss = onDismiss;

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(0.04f, 0.05f, 0.09f, 0.92f);
        pm.fill();
        bgTexture = new Texture(pm);
        pm.dispose();
        setBackground(new TextureRegionDrawable(new TextureRegion(bgTexture)));

        pad(16);
        setVisible(false);
    }

    public void showCandidate(Entity entity) {
        this.currentEntity = entity;
        clearChildren();
        setVisible(true);

        NpcIdentityComponent identity = IDENTITY_M.get(entity);
        NpcStatsComponent stats = STATS_M.get(entity);
        RecruitableComponent rc = RECRUIT_M.get(entity);

        // Top row: name + info + wage + buttons
        Table topRow = new Table();

        // Name block
        Table nameBlock = new Table();
        String name = identity.name != null ? identity.name : "Unknown";
        Label nameLabel = new Label(name, skin, "header");
        nameBlock.add(nameLabel).left().row();

        String info = (identity.species != null ? identity.species : "Unknown") +
            " · " + (identity.role != null ? identity.role.name() : "Unknown") +
            " · " + CrewRank.RECRUIT.name();
        Label infoLabel = new Label(info, skin, "slot-detail");
        nameBlock.add(infoLabel).left().row();

        // Quote
        if (rc != null && rc.hookLine != null) {
            Label quoteLabel = new Label("\"" + rc.hookLine + "\"", skin, "slot-meta");
            quoteLabel.setColor(0.53f, 0.55f, 0.7f, 1f);
            quoteLabel.setFontScale(0.9f);
            nameBlock.add(quoteLabel).left().padTop(4).row();
        }

        topRow.add(nameBlock).expandX().left();

        // Wage
        if (rc != null) {
            String wage = (int) rc.askingWageMin + "–" + (int) rc.askingWageMax + " cr/wk";
            Label wageLabel = new Label(wage, skin, "slot-name");
            wageLabel.setColor(1f, 0.84f, 0f, 1f);
            topRow.add(wageLabel).right().padRight(16);
        }

        // Buttons
        Table buttons = new Table();
        TextButton talkBtn = new TextButton("Talk", skin, "default");
        talkBtn.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                if (currentEntity != null) onTalk.accept(currentEntity);
            }
        });
        buttons.add(talkBtn).width(120).height(36).padBottom(4).row();

        TextButton dismissBtn = new TextButton("Dismiss", skin, "default");
        dismissBtn.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                onDismiss.run();
            }
        });
        buttons.add(dismissBtn).width(120).height(36);

        topRow.add(buttons).right();

        add(topRow).expandX().fillX().row();

        // Stat bars
        if (stats != null && rc != null) {
            Table statsTable = new Table();
            statsTable.padTop(8);
            EnumSet<StatType> revealed = rc.revealedStats;
            int col = 0;
            for (StatType st : StatType.values()) {
                String text;
                Color color;
                if (revealed.contains(st)) {
                    text = st.abbreviation + " " + (int) st.getValue(stats);
                    color = new Color(0.33f, 0.81f, 0.55f, 1f);
                } else {
                    text = st.abbreviation + " ???";
                    color = new Color(0.33f, 0.33f, 0.33f, 1f);
                }
                Label statLabel = new Label(text, skin, "slot-meta");
                statLabel.setColor(color);
                statsTable.add(statLabel).left().padRight(20).minWidth(80);
                col++;
                if (col % 4 == 0) statsTable.row();
            }
            add(statsTable).expandX().left();
        }

        // Slide-up animation
        float targetY = getY();
        setY(targetY - getHeight());
        addAction(Actions.moveTo(getX(), targetY, 0.2f, Interpolation.circleOut));
    }

    public void hide() {
        addAction(Actions.sequence(
            Actions.moveBy(0, -getHeight(), 0.15f, Interpolation.circleIn),
            Actions.run(() -> {
                setVisible(false);
                currentEntity = null;
            })
        ));
    }

    public void refreshStats(Entity entity) {
        if (currentEntity == entity) {
            showCandidate(entity);
        }
    }

    public Entity getCurrentEntity() {
        return currentEntity;
    }

    @Override
    public void dispose() {
        if (bgTexture != null) {
            bgTexture.dispose();
            bgTexture = null;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/actors/CandidateDetailOverlay.java
git commit -m "feat(recruitment): add CandidateDetailOverlay with partial stats and slide-up animation"
```

---

### Task 11: HiringBoardOverlay

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/actors/HiringBoardOverlay.java`

- [ ] **Step 1: Implement HiringBoardOverlay**

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.npc.components.*;
import com.galacticodyssey.npc.crew.CrewRole;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HiringBoardOverlay extends Table implements Disposable {

    private static final ComponentMapper<NpcIdentityComponent> IDENTITY_M =
        ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);
    private static final ComponentMapper<RecruitableComponent> RECRUIT_M =
        ComponentMapper.getFor(RecruitableComponent.class);

    private final Skin skin;
    private final Consumer<Entity> onCandidateSelected;
    private final Runnable onClose;
    private final List<Entity> allCandidates = new ArrayList<>();
    private String activeFilter = null;
    private Table listContainer;
    private Texture bgTexture;

    public HiringBoardOverlay(Skin skin, Consumer<Entity> onCandidateSelected, Runnable onClose) {
        this.skin = skin;
        this.onCandidateSelected = onCandidateSelected;
        this.onClose = onClose;

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(0.04f, 0.05f, 0.09f, 0.95f);
        pm.fill();
        bgTexture = new Texture(pm);
        pm.dispose();
        setBackground(new TextureRegionDrawable(new TextureRegion(bgTexture)));

        pad(16);
        setVisible(false);
    }

    public void show(String stationName, List<Entity> candidates) {
        this.allCandidates.clear();
        this.allCandidates.addAll(candidates);
        this.activeFilter = null;
        setVisible(true);
        rebuild(stationName);
    }

    public void hide() {
        setVisible(false);
    }

    private void rebuild(String stationName) {
        clearChildren();

        // Header
        Table header = new Table();
        Label title = new Label("HIRING BOARD — " + stationName, skin, "header");
        title.setColor(0f, 0.9f, 1f, 1f);
        header.add(title).expandX().left();

        TextButton closeBtn = new TextButton("[ESC] Close", skin, "default");
        closeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onClose.run();
            }
        });
        header.add(closeBtn).right();
        add(header).expandX().fillX().padBottom(12).row();

        // Role filter tabs
        Table filterBar = new Table();
        addFilterTab(filterBar, "All", null);
        for (CrewRole role : CrewRole.values()) {
            addFilterTab(filterBar, role.name(), role.name());
        }
        add(filterBar).left().padBottom(12).row();

        // Candidate list
        listContainer = new Table();
        rebuildList();
        ScrollPane scrollPane = new ScrollPane(listContainer, skin);
        scrollPane.setFadeScrollBars(false);
        add(scrollPane).expand().fill();
    }

    private void addFilterTab(Table bar, String label, String filterValue) {
        long count = allCandidates.stream()
            .filter(e -> {
                if (filterValue == null) return true;
                NpcIdentityComponent id = IDENTITY_M.get(e);
                return id.role != null && id.role.name().equalsIgnoreCase(filterValue);
            }).count();

        TextButton tab = new TextButton(label + "(" + count + ")", skin, "default");
        tab.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                activeFilter = filterValue;
                rebuildList();
            }
        });
        bar.add(tab).padRight(4);
    }

    private void rebuildList() {
        listContainer.clearChildren();

        for (Entity entity : allCandidates) {
            NpcIdentityComponent identity = IDENTITY_M.get(entity);
            if (activeFilter != null && (identity.role == null ||
                !identity.role.name().equalsIgnoreCase(activeFilter))) {
                continue;
            }

            NpcStatsComponent stats = STATS_M.get(entity);
            RecruitableComponent rc = RECRUIT_M.get(entity);

            Table row = new Table();
            row.pad(8);

            String name = identity.name != null ? identity.name : "Unknown";
            Label nameLabel = new Label(name, skin, "slot-name");
            nameLabel.setColor(0.9f, 0.27f, 0.37f, 1f);
            row.add(nameLabel).left().minWidth(120);

            String info = (identity.species != null ? identity.species : "") +
                " · " + (identity.role != null ? identity.role.name() : "");
            Label infoLabel = new Label(info, skin, "slot-meta");
            infoLabel.setColor(0.53f, 0.55f, 0.7f, 1f);
            row.add(infoLabel).left().expandX().padLeft(8);

            if (stats != null) {
                List<StatType> top = StatType.getTopN(stats, 1);
                if (!top.isEmpty()) {
                    StatType best = top.get(0);
                    Label statLabel = new Label(
                        best.abbreviation + " " + (int) best.getValue(stats), skin, "slot-meta");
                    statLabel.setColor(0.33f, 0.81f, 0.55f, 1f);
                    row.add(statLabel).right().padRight(16);
                }
            }

            if (rc != null) {
                Label wageLabel = new Label("~" + (int) ((rc.askingWageMin + rc.askingWageMax) / 2) + " cr",
                    skin, "slot-meta");
                wageLabel.setColor(1f, 0.84f, 0f, 1f);
                row.add(wageLabel).right();
            }

            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onCandidateSelected.accept(entity);
                }
            });

            listContainer.add(row).expandX().fillX().row();
        }
    }

    @Override
    public void dispose() {
        if (bgTexture != null) {
            bgTexture.dispose();
            bgTexture = null;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/actors/HiringBoardOverlay.java
git commit -m "feat(recruitment): add HiringBoardOverlay with role filtering and candidate rows"
```

---

### Task 12: HireConfirmationDialog and ResultToast

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/actors/HireConfirmationDialog.java`
- Create: `core/src/main/java/com/galacticodyssey/ui/actors/ResultToast.java`

- [ ] **Step 1: Implement HireConfirmationDialog**

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.npc.components.*;

import java.util.function.Consumer;

public class HireConfirmationDialog extends Table implements Disposable {

    private static final ComponentMapper<NpcIdentityComponent> IDENTITY_M =
        ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<NpcStatsComponent> STATS_M =
        ComponentMapper.getFor(NpcStatsComponent.class);
    private static final ComponentMapper<RecruitableComponent> RECRUIT_M =
        ComponentMapper.getFor(RecruitableComponent.class);

    private final Skin skin;
    private final Consumer<Entity> onHire;
    private final Runnable onDecline;
    private Texture bgTexture;
    private Entity currentEntity;

    public HireConfirmationDialog(Skin skin, Consumer<Entity> onHire, Runnable onDecline) {
        this.skin = skin;
        this.onHire = onHire;
        this.onDecline = onDecline;

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(0.06f, 0.08f, 0.14f, 0.96f);
        pm.fill();
        bgTexture = new Texture(pm);
        pm.dispose();
        setBackground(new TextureRegionDrawable(new TextureRegion(bgTexture)));

        pad(20);
        setVisible(false);
    }

    public void show(Entity entity, int crewSlotsFilled, int crewSlotsMax, long playerCredits) {
        this.currentEntity = entity;
        clearChildren();
        setVisible(true);

        NpcIdentityComponent identity = IDENTITY_M.get(entity);
        NpcStatsComponent stats = STATS_M.get(entity);
        RecruitableComponent rc = RECRUIT_M.get(entity);

        // Title
        String name = identity.name != null ? identity.name : "Unknown";
        Label title = new Label("HIRE " + name.toUpperCase() + "?", skin, "header");
        title.setColor(1f, 0.84f, 0f, 1f);
        add(title).left().padBottom(12).row();

        // Full stat sheet (all revealed at offer time)
        if (stats != null) {
            Table statsTable = new Table();
            for (StatType st : StatType.values()) {
                Label statLabel = new Label(
                    st.abbreviation + " " + (int) st.getValue(stats), skin, "slot-detail");
                statLabel.setColor(0.33f, 0.81f, 0.55f, 1f);
                statsTable.add(statLabel).left().padRight(16).minWidth(80);
            }
            add(statsTable).left().padBottom(8).row();
        }

        // Wage
        if (rc != null) {
            float wage = rc.negotiatedWage > 0 ? rc.negotiatedWage : rc.askingWageMax;
            Label wageLabel = new Label("Wage: " + (int) wage + " cr/week", skin, "slot-name");
            wageLabel.setColor(1f, 0.84f, 0f, 1f);
            add(wageLabel).left().padBottom(4).row();

            long signingBonus = (long) (wage * 2);
            Label bonusLabel = new Label("Signing bonus: " + signingBonus + " cr", skin, "slot-detail");
            add(bonusLabel).left().padBottom(8).row();

            // Conditions
            if (!rc.conditions.isEmpty()) {
                Label condHeader = new Label("Conditions:", skin, "slot-detail");
                add(condHeader).left().padBottom(4).row();
                for (RecruitCondition cond : rc.conditions) {
                    Label condLabel = new Label("• " + cond.description, skin, "slot-meta");
                    condLabel.setColor(cond.met ? new Color(0.33f, 0.81f, 0.55f, 1f) :
                                                  new Color(0.9f, 0.27f, 0.37f, 1f));
                    add(condLabel).left().row();
                }
            }

            // Crew slots
            Label slotLabel = new Label(
                "Crew: " + (crewSlotsFilled + 1) + "/" + crewSlotsMax + " slots",
                skin, "slot-detail");
            add(slotLabel).left().padTop(8).padBottom(12).row();

            // Buttons
            Table buttons = new Table();
            boolean canAfford = playerCredits >= signingBonus;

            TextButton hireBtn = new TextButton("Hire", skin, "default");
            hireBtn.setDisabled(!canAfford);
            if (!canAfford) {
                hireBtn.setColor(0.4f, 0.4f, 0.4f, 1f);
            }
            hireBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (canAfford && currentEntity != null) {
                        onHire.accept(currentEntity);
                    }
                }
            });
            buttons.add(hireBtn).width(140).height(40).padRight(12);

            TextButton declineBtn = new TextButton("Decline", skin, "default");
            declineBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    onDecline.run();
                }
            });
            buttons.add(declineBtn).width(140).height(40);

            add(buttons).center();
        }
    }

    public void hide() {
        setVisible(false);
        currentEntity = null;
    }

    @Override
    public void dispose() {
        if (bgTexture != null) {
            bgTexture.dispose();
            bgTexture = null;
        }
    }
}
```

- [ ] **Step 2: Implement ResultToast**

```java
package com.galacticodyssey.ui.actors;

import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

public class ResultToast extends Table {

    private final Skin skin;

    public ResultToast(Skin skin) {
        this.skin = skin;
        setVisible(false);
    }

    public void show(String message, Runnable onComplete) {
        clearChildren();
        clearActions();
        setVisible(true);

        Label label = new Label(message, skin, "header");
        label.setColor(0.2f, 1f, 0.47f, 1f);
        add(label).center();

        getColor().a = 0f;
        addAction(Actions.sequence(
            Actions.fadeIn(0.3f),
            Actions.delay(2f),
            Actions.fadeOut(0.5f),
            Actions.run(() -> {
                setVisible(false);
                if (onComplete != null) onComplete.run();
            })
        ));
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/actors/HireConfirmationDialog.java \
        core/src/main/java/com/galacticodyssey/ui/actors/ResultToast.java
git commit -m "feat(recruitment): add HireConfirmationDialog and ResultToast actors"
```

---

### Task 13: RecruitmentScreenSystem

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/systems/RecruitmentScreenSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ui/systems/RecruitmentScreenSystemTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ui.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.components.*;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRole;
import com.galacticodyssey.npc.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecruitmentScreenSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private List<CrewMemberHiredEvent> hiredEvents;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        hiredEvents = new ArrayList<>();
        eventBus.subscribe(CrewMemberHiredEvent.class, hiredEvents::add);
    }

    @Test
    void hiringSequenceAttachesCrewComponent() {
        Entity npc = new Entity();
        NpcIdentityComponent identity = new NpcIdentityComponent();
        identity.name = "Threx-Ka";
        identity.species = "veloxi";
        identity.role = com.galacticodyssey.npc.components.NPCRole.ENGINEER;
        npc.add(identity);

        NpcStatsComponent stats = new NpcStatsComponent();
        stats.repair = 85f;
        npc.add(stats);

        RecruitableComponent rc = new RecruitableComponent();
        rc.askingWageMin = 500f;
        rc.askingWageMax = 650f;
        rc.negotiatedWage = 580f;
        npc.add(rc);

        CantinaSeatComponent seat = new CantinaSeatComponent();
        seat.seatId = "test";
        npc.add(seat);

        engine.addEntity(npc);

        RecruitmentScreenSystem.executeHire(npc, eventBus);

        assertNotNull(npc.getComponent(CrewMemberComponent.class));
        CrewMemberComponent crew = npc.getComponent(CrewMemberComponent.class);
        assertEquals(580f, crew.wage);
        assertEquals(75f, crew.morale);
        assertEquals(50f, crew.loyalty);

        assertNull(npc.getComponent(RecruitableComponent.class));
        assertNull(npc.getComponent(CantinaSeatComponent.class));

        assertEquals(1, hiredEvents.size());
        assertSame(npc, hiredEvents.get(0).npc);
    }

    @Test
    void hiringUsesMaxWageWhenNotNegotiated() {
        Entity npc = new Entity();
        NpcIdentityComponent identity = new NpcIdentityComponent();
        identity.name = "Test";
        identity.role = com.galacticodyssey.npc.components.NPCRole.GUNNER;
        npc.add(identity);
        npc.add(new NpcStatsComponent());

        RecruitableComponent rc = new RecruitableComponent();
        rc.askingWageMin = 400f;
        rc.askingWageMax = 500f;
        rc.negotiatedWage = -1f;
        npc.add(rc);
        npc.add(new CantinaSeatComponent());

        engine.addEntity(npc);

        RecruitmentScreenSystem.executeHire(npc, eventBus);

        CrewMemberComponent crew = npc.getComponent(CrewMemberComponent.class);
        assertEquals(500f, crew.wage);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests "com.galacticodyssey.ui.systems.RecruitmentScreenSystemTest" --info`
Expected: Compilation error — `RecruitmentScreenSystem` does not exist.

- [ ] **Step 3: Implement RecruitmentScreenSystem**

```java
package com.galacticodyssey.ui.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.npc.components.*;
import com.galacticodyssey.npc.crew.CrewMemberComponent;
import com.galacticodyssey.npc.crew.CrewRank;
import com.galacticodyssey.npc.crew.CrewRole;
import com.galacticodyssey.npc.events.*;
import com.galacticodyssey.ui.actors.*;

import java.util.ArrayList;
import java.util.List;

public class RecruitmentScreenSystem implements Disposable {

    public enum ScreenState { CLOSED, BROWSE, SELECTED, DIALOG, OFFER, RESULT }

    private static final ComponentMapper<NpcIdentityComponent> IDENTITY_M =
        ComponentMapper.getFor(NpcIdentityComponent.class);
    private static final ComponentMapper<RecruitableComponent> RECRUIT_M =
        ComponentMapper.getFor(RecruitableComponent.class);
    private static final ComponentMapper<CantinaSeatComponent> SEAT_M =
        ComponentMapper.getFor(CantinaSeatComponent.class);

    private final EventBus eventBus;
    private final Skin skin;
    private Stage stage;
    private Engine engine;

    private ScreenState state = ScreenState.CLOSED;
    private String currentStationId;
    private Entity selectedEntity;

    private CantinaSceneActor sceneActor;
    private CandidateDetailOverlay detailOverlay;
    private HiringBoardOverlay boardOverlay;
    private HireConfirmationDialog confirmDialog;
    private ResultToast resultToast;
    private Table headerBar;
    private final List<NpcPortraitActor> portraitActors = new ArrayList<>();

    public RecruitmentScreenSystem(EventBus eventBus, Skin skin) {
        this.eventBus = eventBus;
        this.skin = skin;

        eventBus.subscribe(RecruitmentOpenedEvent.class, this::onRecruitmentOpened);
        eventBus.subscribe(StatRevealedEvent.class, this::onStatRevealed);
        eventBus.subscribe(WageNegotiatedEvent.class, this::onWageNegotiated);
    }

    public void initialize(Engine engine) {
        this.engine = engine;
        stage = new Stage(new FitViewport(1280, 720));

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    handleEscape();
                    return true;
                }
                return false;
            }
        });
    }

    private void onRecruitmentOpened(RecruitmentOpenedEvent event) {
        this.currentStationId = event.stationId;
        open();
    }

    public void open() {
        if (state != ScreenState.CLOSED || stage == null) return;

        state = ScreenState.BROWSE;
        buildScene();
    }

    public void close() {
        if (state == ScreenState.CLOSED) return;

        state = ScreenState.CLOSED;
        selectedEntity = null;
        currentStationId = null;
        clearScene();
        eventBus.publish(new RecruitmentClosedEvent());
    }

    private void buildScene() {
        stage.clear();
        portraitActors.clear();

        float w = stage.getViewport().getWorldWidth();
        float h = stage.getViewport().getWorldHeight();

        // Background
        sceneActor = new CantinaSceneActor(w, h);
        stage.addActor(sceneActor);

        // Hiring Board (clickable scene element that opens the list overlay)
        TextButton boardButton = new TextButton("HIRING\nBOARD", skin, "default");
        boardButton.setSize(100, 60);
        boardButton.setPosition(w * 0.30f, h * (1f - 0.22f));
        boardButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                openHiringBoard();
            }
        });
        stage.addActor(boardButton);

        // NPC portraits
        ImmutableArray<Entity> candidates = engine.getEntitiesFor(
            Family.all(RecruitableComponent.class, CantinaSeatComponent.class).get());

        for (Entity entity : candidates) {
            CantinaSeatComponent seat = SEAT_M.get(entity);
            NpcPortraitActor portrait = new NpcPortraitActor(entity, skin, this::onNpcClicked);
            portrait.setPosition(seat.sceneX * w, (1f - seat.sceneY) * h);
            stage.addActor(portrait);
            portraitActors.add(portrait);
        }

        // Detail overlay
        detailOverlay = new CandidateDetailOverlay(skin, this::onTalkClicked, this::onDismissClicked);
        detailOverlay.setSize(w, h * 0.25f);
        detailOverlay.setPosition(0, 0);
        stage.addActor(detailOverlay);

        // Hiring board overlay
        boardOverlay = new HiringBoardOverlay(skin, this::onBoardCandidateSelected, this::onBoardClosed);
        boardOverlay.setSize(w * 0.6f, h * 0.7f);
        boardOverlay.setPosition(w * 0.2f, h * 0.15f);
        stage.addActor(boardOverlay);

        // Confirmation dialog
        confirmDialog = new HireConfirmationDialog(skin, this::onHireConfirmed, this::onDeclined);
        confirmDialog.setSize(w * 0.4f, h * 0.6f);
        confirmDialog.setPosition(w * 0.3f, h * 0.2f);
        stage.addActor(confirmDialog);

        // Result toast
        resultToast = new ResultToast(skin);
        resultToast.setSize(w, 60);
        resultToast.setPosition(0, h * 0.7f);
        stage.addActor(resultToast);

        // Header bar
        headerBar = new Table();
        headerBar.setSize(w, 40);
        headerBar.setPosition(0, h - 40);
        headerBar.pad(8, 16, 8, 16);

        String stationName = currentStationId != null ? currentStationId.replace("_", " ") : "Unknown";
        Label locationLabel = new Label(stationName + " — Cantina", skin, "slot-name");
        locationLabel.setColor(0.9f, 0.27f, 0.37f, 1f);
        headerBar.add(locationLabel).expandX().left();

        Label candidateCount = new Label(candidates.size() + " candidates", skin, "slot-detail");
        headerBar.add(candidateCount).right();

        stage.addActor(headerBar);
    }

    private void clearScene() {
        for (NpcPortraitActor portrait : portraitActors) {
            portrait.dispose();
        }
        portraitActors.clear();
        if (sceneActor != null) {
            sceneActor.dispose();
            sceneActor = null;
        }
        if (detailOverlay != null) {
            detailOverlay.dispose();
            detailOverlay = null;
        }
        if (boardOverlay != null) {
            boardOverlay.dispose();
            boardOverlay = null;
        }
        if (confirmDialog != null) {
            confirmDialog.dispose();
            confirmDialog = null;
        }
        if (stage != null) {
            stage.clear();
        }
    }

    // -- State transitions --

    private void onNpcClicked(Entity entity) {
        if (state == ScreenState.BROWSE || state == ScreenState.SELECTED) {
            for (NpcPortraitActor p : portraitActors) {
                p.setSelected(p.getEntity() == entity);
            }
            selectedEntity = entity;
            state = ScreenState.SELECTED;
            detailOverlay.showCandidate(entity);
        }
    }

    private void onTalkClicked(Entity entity) {
        if (state != ScreenState.SELECTED) return;
        state = ScreenState.DIALOG;
        detailOverlay.hide();

        NpcIdentityComponent identity = IDENTITY_M.get(entity);
        RecruitableComponent rc = RECRUIT_M.get(entity);
        if (identity != null && rc != null) {
            rc.interactionState = RecruitInteractionState.TALKED;
            eventBus.publish(new com.galacticodyssey.npc.events.CandidateSelectedEvent(entity));
        }
    }

    public void onDialogComplete(Entity entity, boolean offerMade) {
        if (offerMade) {
            state = ScreenState.OFFER;
            RecruitableComponent rc = RECRUIT_M.get(entity);
            if (rc != null) {
                for (StatType st : StatType.values()) {
                    rc.revealedStats.add(st);
                }
                rc.interactionState = RecruitInteractionState.OFFERED;
            }
            confirmDialog.show(entity, getCrewCount(), getMaxCrewSlots(), getPlayerCredits());
        } else {
            state = ScreenState.BROWSE;
            selectedEntity = null;
            for (NpcPortraitActor p : portraitActors) {
                p.setSelected(false);
            }
        }
    }

    private void onHireConfirmed(Entity entity) {
        state = ScreenState.RESULT;
        confirmDialog.hide();

        executeHire(entity, eventBus);

        NpcIdentityComponent identity = IDENTITY_M.get(entity);
        CrewMemberComponent crew = entity.getComponent(CrewMemberComponent.class);
        String name = identity != null && identity.name != null ? identity.name : "Unknown";
        String role = crew != null && crew.role != null ? crew.role.name() : "";
        int wage = crew != null ? (int) crew.wage : 0;

        // Remove portrait from scene
        portraitActors.removeIf(p -> {
            if (p.getEntity() == entity) {
                p.dispose();
                p.remove();
                return true;
            }
            return false;
        });

        resultToast.show(name + " hired — " + role + ", " + wage + " cr/wk", () -> {
            state = ScreenState.BROWSE;
            selectedEntity = null;
        });
    }

    private void onDeclined() {
        state = ScreenState.BROWSE;
        confirmDialog.hide();
        selectedEntity = null;
        for (NpcPortraitActor p : portraitActors) {
            p.setSelected(false);
        }
    }

    private void onDismissClicked() {
        state = ScreenState.BROWSE;
        detailOverlay.hide();
        selectedEntity = null;
        for (NpcPortraitActor p : portraitActors) {
            p.setSelected(false);
        }
    }

    public void openHiringBoard() {
        if (state != ScreenState.BROWSE) return;
        List<Entity> candidates = new ArrayList<>();
        for (NpcPortraitActor p : portraitActors) {
            candidates.add(p.getEntity());
        }
        String stationName = currentStationId != null ? currentStationId.replace("_", " ") : "Station";
        boardOverlay.show(stationName, candidates);
    }

    private void onBoardCandidateSelected(Entity entity) {
        boardOverlay.hide();
        onNpcClicked(entity);
    }

    private void onBoardClosed() {
        boardOverlay.hide();
    }

    private void handleEscape() {
        switch (state) {
            case SELECTED -> onDismissClicked();
            case OFFER -> onDeclined();
            case BROWSE -> {
                if (boardOverlay != null && boardOverlay.isVisible()) {
                    onBoardClosed();
                } else {
                    close();
                }
            }
            default -> {}
        }
    }

    private void onStatRevealed(StatRevealedEvent event) {
        if (detailOverlay != null) {
            detailOverlay.refreshStats(event.npcEntity);
        }
    }

    private void onWageNegotiated(WageNegotiatedEvent event) {
        RecruitableComponent rc = RECRUIT_M.get(event.npcEntity);
        if (rc != null) {
            rc.negotiatedWage = event.finalWage;
        }
    }

    // -- Hire logic (static for testability) --

    public static void executeHire(Entity npc, EventBus eventBus) {
        NpcIdentityComponent identity = ComponentMapper.getFor(NpcIdentityComponent.class).get(npc);
        RecruitableComponent rc = ComponentMapper.getFor(RecruitableComponent.class).get(npc);

        CrewMemberComponent crew = new CrewMemberComponent();
        crew.role = mapRole(identity != null ? identity.role : null);
        crew.rank = CrewRank.RECRUIT;
        crew.morale = 75f;
        crew.loyalty = 50f;
        crew.wage = rc != null && rc.negotiatedWage > 0 ? rc.negotiatedWage : (rc != null ? rc.askingWageMax : 0);
        npc.add(crew);

        npc.remove(RecruitableComponent.class);
        npc.remove(CantinaSeatComponent.class);

        eventBus.publish(new CrewMemberHiredEvent(npc, crew.role));
    }

    private static CrewRole mapRole(NPCRole npcRole) {
        if (npcRole == null) return CrewRole.MARINE;
        return switch (npcRole) {
            case PILOT -> CrewRole.PILOT;
            case GUNNER -> CrewRole.GUNNER;
            case ENGINEER -> CrewRole.ENGINEER;
            case MEDIC -> CrewRole.MEDIC;
            case MARINE -> CrewRole.MARINE;
            case SCIENTIST -> CrewRole.SCIENTIST;
            case NAVIGATOR -> CrewRole.NAVIGATOR;
            default -> CrewRole.MARINE;
        };
    }

    // -- Queries --

    private int getCrewCount() {
        if (engine == null) return 0;
        return engine.getEntitiesFor(Family.all(CrewMemberComponent.class).get()).size();
    }

    private int getMaxCrewSlots() {
        return 6;
    }

    private long getPlayerCredits() {
        if (engine == null) return 0;
        var players = engine.getEntitiesFor(Family.all(
            com.galacticodyssey.economy.components.PlayerWalletComponent.class).get());
        if (players.size() == 0) return 0;
        return players.first().getComponent(
            com.galacticodyssey.economy.components.PlayerWalletComponent.class).credits;
    }

    // -- Render & lifecycle --

    public void render(float delta) {
        if (state == ScreenState.CLOSED || stage == null) return;
        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
    }

    public boolean isOpen() {
        return state != ScreenState.CLOSED;
    }

    public ScreenState getState() {
        return state;
    }

    public Stage getStage() {
        return stage;
    }

    @Override
    public void dispose() {
        clearScene();
        if (stage != null) {
            stage.dispose();
            stage = null;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests "com.galacticodyssey.ui.systems.RecruitmentScreenSystemTest" --info`
Expected: All 2 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/systems/RecruitmentScreenSystem.java \
        core/src/test/java/com/galacticodyssey/ui/systems/RecruitmentScreenSystemTest.java
git commit -m "feat(recruitment): add RecruitmentScreenSystem with state machine and hiring logic"
```

---

### Task 14: GameWorld and GameScreen Integration

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Add CandidatePoolSystem to GameWorld**

In `GameWorld.java`, after the existing `DialogSystem` initialization (around line 562), add:

```java
// Recruitment systems
RecruitmentDataRegistry recruitmentDataRegistry = new RecruitmentDataRegistry();
if (Gdx.files != null) {
    try {
        recruitmentDataRegistry.loadFromFiles();
    } catch (Exception e) {
        Gdx.app.error("GameWorld", "Failed to load recruitment data", e);
    }
}
CandidatePoolSystem candidatePoolSystem = new CandidatePoolSystem(
    eventBus, new NpcGenerator(npcDataRegistry), recruitmentDataRegistry);
engine.addSystem(candidatePoolSystem);
```

Add required imports at the top of `GameWorld.java`:
```java
import com.galacticodyssey.npc.data.RecruitmentDataRegistry;
import com.galacticodyssey.npc.systems.CandidatePoolSystem;
```

Add a field and getter:
```java
private CandidatePoolSystem candidatePoolSystem;
// ...
public CandidatePoolSystem getCandidatePoolSystem() { return candidatePoolSystem; }
```

- [ ] **Step 2: Add RecruitmentScreenSystem to GameScreen**

In `GameScreen.java`, after the `InventoryScreenSystem` initialization, add:

```java
recruitmentScreen = new RecruitmentScreenSystem(eventBus, skin);
recruitmentScreen.initialize(gameWorld.getEngine());
```

Add the field:
```java
private RecruitmentScreenSystem recruitmentScreen;
```

In the `render` method, after inventory screen rendering:
```java
if (recruitmentScreen.isOpen()) {
    recruitmentScreen.render(delta);
}
```

In the `resize` method:
```java
recruitmentScreen.resize(width, height);
```

In the `dispose` method:
```java
recruitmentScreen.dispose();
```

Wire the stage into `InputMultiplexer` — in the section where `InventoryScreenSystem` stage is added, add:
```java
if (recruitmentScreen.isOpen()) {
    inputMultiplexer.addProcessor(recruitmentScreen.getStage());
}
```

Add import:
```java
import com.galacticodyssey.ui.systems.RecruitmentScreenSystem;
```

- [ ] **Step 3: Verify the game compiles**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java \
        core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(recruitment): wire CandidatePoolSystem and RecruitmentScreenSystem into game"
```

---

### Task 15: Dialog Integration for Recruitment

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/systems/RecruitmentScreenSystem.java`

- [ ] **Step 1: Wire dialog open/close to screen state**

Add subscriptions in the `RecruitmentScreenSystem` constructor:

```java
eventBus.subscribe(com.galacticodyssey.npc.events.DialogClosedEvent.class, this::onDialogClosed);
```

Add the handler:

```java
private void onDialogClosed(com.galacticodyssey.npc.events.DialogClosedEvent event) {
    if (state == ScreenState.DIALOG && selectedEntity != null) {
        NpcIdentityComponent identity = IDENTITY_M.get(selectedEntity);
        if (identity != null && identity.npcId != null && identity.npcId.equals(event.npcId)) {
            RecruitableComponent rc = RECRUIT_M.get(selectedEntity);
            boolean offered = rc != null && rc.interactionState == RecruitInteractionState.OFFERED;
            onDialogComplete(selectedEntity, offered);
        }
    }
}
```

Update `onTalkClicked` to open the dialog system:

```java
private void onTalkClicked(Entity entity) {
    if (state != ScreenState.SELECTED) return;
    state = ScreenState.DIALOG;
    detailOverlay.hide();

    NpcIdentityComponent identity = IDENTITY_M.get(entity);
    RecruitableComponent rc = RECRUIT_M.get(entity);
    if (identity != null && rc != null) {
        rc.interactionState = RecruitInteractionState.TALKED;
        String npcId = identity.npcId != null ? identity.npcId : identity.name;
        String npcName = identity.name != null ? identity.name : "Unknown";
        String dialogId = rc.dialogTreeId != null ? rc.dialogTreeId : "recruitment_standard";
        eventBus.publish(new com.galacticodyssey.npc.events.CandidateSelectedEvent(entity));
        // Open dialog via the existing NpcDialogueEvent
        eventBus.publish(new com.galacticodyssey.npc.events.NpcDialogueEvent(npcId, dialogId));
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/systems/RecruitmentScreenSystem.java
git commit -m "feat(recruitment): wire dialog open/close into recruitment screen state machine"
```

---

### Task 16: Run Full Test Suite

- [ ] **Step 1: Run all recruitment-related tests**

Run: `./gradlew core:test --tests "com.galacticodyssey.npc.components.StatTypeTest" --tests "com.galacticodyssey.npc.data.RecruitmentDataRegistryTest" --tests "com.galacticodyssey.npc.systems.CandidatePoolSystemTest" --tests "com.galacticodyssey.ui.systems.RecruitmentScreenSystemTest" --info`

Expected: All tests PASS.

- [ ] **Step 2: Run full project test suite**

Run: `./gradlew core:test --info`
Expected: All existing tests still pass — no regressions.

- [ ] **Step 3: Verify game launches**

Run: `./gradlew desktop:run`
Expected: Game launches without errors. The recruitment screen won't be visually accessible yet without a cantina interaction point, but the system initializes without crashes.

- [ ] **Step 4: Commit (if any test fixes were needed)**

```bash
git add -A
git commit -m "fix: resolve any test failures from recruitment integration"
```
