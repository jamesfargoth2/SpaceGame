# Faction/Reputation System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a data-driven reputation system that tracks player standing with factions, ripples changes through diplomatic relations, and enforces consequences (docking, pricing, missions, encounters).

**Architecture:** Hybrid ECS + event-driven service. `PlayerReputationComponent` stores standings on the player entity. `ReputationManager` subscribes to `ReputationChangeEvent` (already published by `RewardSystem`), applies Diplomacy skill modifiers, computes diplomatic ripple via `PoliticalRelationGraph`, and publishes `ReputationTierChangedEvent`. It implements the existing `ReputationQuery` interface so `JobBoard`/`ProceduralJobGenerator` work without changes.

**Tech Stack:** Java 25, libGDX, Ashley ECS, JUnit 5

---

## File Structure

### New Files

| File | Responsibility |
|---|---|
| `core/src/main/java/com/galacticodyssey/galaxy/faction/ReputationTier.java` | Enum mapping standing ranges to named tiers with price multipliers |
| `core/src/main/java/com/galacticodyssey/galaxy/faction/ReputationConfigData.java` | JSON-loaded POJO for tunable constants |
| `core/src/main/java/com/galacticodyssey/player/components/PlayerReputationComponent.java` | Ashley Component storing per-faction standings map |
| `core/src/main/java/com/galacticodyssey/core/events/ReputationTierChangedEvent.java` | Event published on tier boundary crossing |
| `core/src/main/java/com/galacticodyssey/ship/docking/events/DockingDeniedEvent.java` | Event published when docking denied due to reputation |
| `core/src/main/java/com/galacticodyssey/galaxy/faction/ReputationManager.java` | Event-driven service implementing ReputationQuery |
| `core/src/main/resources/data/factions/reputation_config.json` | Default config values |
| `core/src/test/java/com/galacticodyssey/galaxy/faction/ReputationTierTest.java` | Tier enum tests |
| `core/src/test/java/com/galacticodyssey/galaxy/faction/ReputationManagerTest.java` | Manager core logic tests |
| `core/src/test/java/com/galacticodyssey/galaxy/faction/ReputationIntegrationTest.java` | End-to-end integration test |

### Modified Files

| File | Change |
|---|---|
| `core/src/main/java/com/galacticodyssey/economy/components/MarketComponent.java` | Add `ownerFactionId` field |
| `core/src/main/java/com/galacticodyssey/economy/service/TransactionService.java` | Publish trade rep event, apply tier-based pricing, deny HOSTILE trades |
| `core/src/main/java/com/galacticodyssey/ship/docking/DockingApproachSystem.java` | Check reputation before allowing docking approach |

---

### Task 1: ReputationTier Enum

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/faction/ReputationTier.java`
- Test: `core/src/test/java/com/galacticodyssey/galaxy/faction/ReputationTierTest.java`

**Context:** This enum maps standing values (float, -100 to +100) to named tiers. Each tier has a standing range, buy/sell price multipliers, and a description. Used by `ReputationManager`, `TransactionService`, and UI.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.galaxy.faction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ReputationTierTest {

    @ParameterizedTest
    @CsvSource({
        "-100, HOSTILE",
        "-75,  HOSTILE",
        "-50.1, HOSTILE",
        "-50,  UNFRIENDLY",
        "-25,  UNFRIENDLY",
        "-0.1, UNFRIENDLY",
        "0,    NEUTRAL",
        "10,   NEUTRAL",
        "24.9, NEUTRAL",
        "25,   FRIENDLY",
        "49.9, FRIENDLY",
        "50,   ALLIED",
        "74.9, ALLIED",
        "75,   HONORED",
        "99.9, HONORED",
        "100,  EXALTED"
    })
    void fromStandingReturnsTier(float standing, ReputationTier expected) {
        assertEquals(expected, ReputationTier.fromStanding(standing));
    }

    @Test
    void hostilePriceMultipliers() {
        assertEquals(0f, ReputationTier.HOSTILE.buyMultiplier);
        assertEquals(0f, ReputationTier.HOSTILE.sellMultiplier);
    }

    @Test
    void neutralPriceMultipliers() {
        assertEquals(1.0f, ReputationTier.NEUTRAL.buyMultiplier);
        assertEquals(1.0f, ReputationTier.NEUTRAL.sellMultiplier);
    }

    @Test
    void friendlyPriceMultipliers() {
        assertEquals(0.95f, ReputationTier.FRIENDLY.buyMultiplier);
        assertEquals(1.05f, ReputationTier.FRIENDLY.sellMultiplier);
    }

    @Test
    void exaltedPriceMultipliers() {
        assertEquals(0.80f, ReputationTier.EXALTED.buyMultiplier);
        assertEquals(1.20f, ReputationTier.EXALTED.sellMultiplier);
    }

    @Test
    void clampedStandingAbove100() {
        assertEquals(ReputationTier.EXALTED, ReputationTier.fromStanding(150f));
    }

    @Test
    void clampedStandingBelow100() {
        assertEquals(ReputationTier.HOSTILE, ReputationTier.fromStanding(-200f));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.faction.ReputationTierTest" -q`
Expected: FAIL — `ReputationTier` class not found.

- [ ] **Step 3: Write the ReputationTier enum**

```java
package com.galacticodyssey.galaxy.faction;

public enum ReputationTier {

    HOSTILE    (-100f, -50f,  0f,    0f),
    UNFRIENDLY(-50f,    0f,  1.20f, 0.80f),
    NEUTRAL   (  0f,   25f,  1.00f, 1.00f),
    FRIENDLY  ( 25f,   50f,  0.95f, 1.05f),
    ALLIED    ( 50f,   75f,  0.90f, 1.10f),
    HONORED   ( 75f,  100f,  0.85f, 1.15f),
    EXALTED   (100f,  100f,  0.80f, 1.20f);

    public final float minInclusive;
    public final float maxExclusive;
    public final float buyMultiplier;
    public final float sellMultiplier;

    ReputationTier(float minInclusive, float maxExclusive,
                   float buyMultiplier, float sellMultiplier) {
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
        this.buyMultiplier = buyMultiplier;
        this.sellMultiplier = sellMultiplier;
    }

    public static ReputationTier fromStanding(float standing) {
        float clamped = Math.max(-100f, Math.min(100f, standing));
        if (clamped >= 100f) return EXALTED;
        if (clamped >= 75f)  return HONORED;
        if (clamped >= 50f)  return ALLIED;
        if (clamped >= 25f)  return FRIENDLY;
        if (clamped >= 0f)   return NEUTRAL;
        if (clamped >= -50f) return UNFRIENDLY;
        return HOSTILE;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.faction.ReputationTierTest" -q`
Expected: All 8 tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/faction/ReputationTier.java core/src/test/java/com/galacticodyssey/galaxy/faction/ReputationTierTest.java
git commit -m "feat(faction): add ReputationTier enum with standing ranges and price multipliers"
```

---

### Task 2: ReputationConfigData + JSON

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/faction/ReputationConfigData.java`
- Create: `core/src/main/resources/data/factions/reputation_config.json`

**Context:** Tunable constants for the reputation system, loaded from JSON at startup. Follows the pattern of `DamageConfigData` loaded by `CombatDataRegistry`.

- [ ] **Step 1: Create ReputationConfigData**

```java
package com.galacticodyssey.galaxy.faction;

public class ReputationConfigData {
    public float combatKillPenalty = -15.0f;
    public float tradeBonus = 1.0f;
    public float smugglingPenaltyCaught = -20.0f;
    public float diplomacyGainMultPerLevel = 0.05f;
    public float diplomacyLossReductionPerLevel = 0.03f;
    public float diplomacyMaxLossReduction = 0.5f;
}
```

- [ ] **Step 2: Create reputation_config.json**

Verify the parent directory exists: `core/src/main/resources/data/factions/` — create if needed.

```json
{
  "combatKillPenalty": -15.0,
  "tradeBonus": 1.0,
  "smugglingPenaltyCaught": -20.0,
  "diplomacyGainMultPerLevel": 0.05,
  "diplomacyLossReductionPerLevel": 0.03,
  "diplomacyMaxLossReduction": 0.5
}
```

- [ ] **Step 3: Verify it compiles**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:compileJava -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/faction/ReputationConfigData.java core/src/main/resources/data/factions/reputation_config.json
git commit -m "feat(faction): add ReputationConfigData and reputation_config.json"
```

---

### Task 3: PlayerReputationComponent + Events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/components/PlayerReputationComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/ReputationTierChangedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/docking/events/DockingDeniedEvent.java`

**Context:** The Ashley Component stores per-faction standings on the player entity. Two new events: one for tier crossings (consumed by UI/NPC behavior), one for docking denial (consumed by UI/audio). Follow the patterns in `HealthComponent` (component) and `DockingAbortEvent` (event).

- [ ] **Step 1: Create PlayerReputationComponent**

```java
package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

import java.util.HashMap;
import java.util.Map;

public class PlayerReputationComponent implements Component {

    public static final ComponentMapper<PlayerReputationComponent> MAPPER =
        ComponentMapper.getFor(PlayerReputationComponent.class);

    public final Map<String, Float> standings = new HashMap<>();
}
```

- [ ] **Step 2: Create ReputationTierChangedEvent**

```java
package com.galacticodyssey.core.events;

import com.galacticodyssey.galaxy.faction.ReputationTier;

public final class ReputationTierChangedEvent {
    public final String factionId;
    public final ReputationTier oldTier;
    public final ReputationTier newTier;
    public final float newStanding;

    public ReputationTierChangedEvent(String factionId, ReputationTier oldTier,
                                      ReputationTier newTier, float newStanding) {
        this.factionId = factionId;
        this.oldTier = oldTier;
        this.newTier = newTier;
        this.newStanding = newStanding;
    }
}
```

- [ ] **Step 3: Create DockingDeniedEvent**

Place in `core/src/main/java/com/galacticodyssey/ship/docking/events/` alongside `DockingAbortEvent`.

```java
package com.galacticodyssey.ship.docking.events;

public final class DockingDeniedEvent {
    public final String stationId;
    public final String factionId;
    public final String reason;

    public DockingDeniedEvent(String stationId, String factionId, String reason) {
        this.stationId = stationId;
        this.factionId = factionId;
        this.reason = reason;
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:compileJava -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/player/components/PlayerReputationComponent.java core/src/main/java/com/galacticodyssey/core/events/ReputationTierChangedEvent.java core/src/main/java/com/galacticodyssey/ship/docking/events/DockingDeniedEvent.java
git commit -m "feat(faction): add PlayerReputationComponent and tier/docking events"
```

---

### Task 4: ReputationManager — Core Logic

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/galaxy/faction/ReputationManager.java`
- Test: `core/src/test/java/com/galacticodyssey/galaxy/faction/ReputationManagerTest.java`

**Context:** The heart of the system. Subscribes to `ReputationChangeEvent` (already published by `RewardSystem` on mission completion). Applies Diplomacy skill modifiers, clamps standings, detects tier crossings, and computes diplomatic ripple. Implements `ReputationQuery` (used by `JobBoard` and `ProceduralJobGenerator`).

**Key dependencies you need to import:**
- `com.galacticodyssey.core.EventBus` — subscribe/publish
- `com.galacticodyssey.core.events.ReputationChangeEvent` — incoming deltas
- `com.galacticodyssey.core.events.ReputationTierChangedEvent` — outgoing tier changes
- `com.galacticodyssey.mission.job.ReputationQuery` — interface to implement
- `com.galacticodyssey.player.components.PlayerReputationComponent` — standings storage
- `com.galacticodyssey.player.components.PlayerStatsComponent` — Diplomacy level
- `com.galacticodyssey.player.stats.PointSkill` — `PointSkill.DIPLOMACY` enum value
- `com.galacticodyssey.galaxy.faction.PoliticalRelation` — ripple direction lookup

**Important:** `PlayerStatsComponent.pointSkills` is `com.badlogic.gdx.utils.ObjectMap<PointSkill, Integer>` (libGDX collection). Its constructor initializes all skills to 0, but null-check defensively.

- [ ] **Step 1: Write the failing tests**

```java
package com.galacticodyssey.galaxy.faction;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.core.events.ReputationTierChangedEvent;
import com.galacticodyssey.mission.job.ReputationQuery;
import com.galacticodyssey.player.components.PlayerReputationComponent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PointSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReputationManagerTest {

    private EventBus eventBus;
    private ReputationConfigData config;
    private Map<String, Map<String, PoliticalRelation>> relations;
    private ReputationManager manager;
    private Entity player;
    private PlayerReputationComponent repComp;
    private PlayerStatsComponent statsComp;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        config = new ReputationConfigData();
        relations = new HashMap<>();
        manager = new ReputationManager(eventBus, config, relations);

        player = new Entity();
        repComp = new PlayerReputationComponent();
        statsComp = new PlayerStatsComponent();
        player.add(repComp);
        player.add(statsComp);
        manager.setPlayerEntity(player);
    }

    @Test
    void implementsReputationQuery() {
        assertInstanceOf(ReputationQuery.class, manager);
    }

    @Test
    void unknownFactionReturnsZero() {
        assertEquals(0f, manager.getStanding("unknown_faction"));
    }

    @Test
    void basicStandingChange() {
        eventBus.publish(new ReputationChangeEvent("fed", 10f, "test"));
        assertEquals(10f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void standingClampsToPositive100() {
        eventBus.publish(new ReputationChangeEvent("fed", 200f, "test"));
        assertEquals(100f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void standingClampsToNegative100() {
        eventBus.publish(new ReputationChangeEvent("fed", -200f, "test"));
        assertEquals(-100f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void standingAccumulates() {
        eventBus.publish(new ReputationChangeEvent("fed", 10f, "test1"));
        eventBus.publish(new ReputationChangeEvent("fed", 5f, "test2"));
        assertEquals(15f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void getTierReturnsCorrectTier() {
        eventBus.publish(new ReputationChangeEvent("fed", 30f, "test"));
        assertEquals(ReputationTier.FRIENDLY, manager.getTier("fed"));
    }

    @Test
    void diplomacyBoostsGains() {
        statsComp.pointSkills.put(PointSkill.DIPLOMACY, 5);
        // delta=10, mult = 1 + 5*0.05 = 1.25, effective = 12.5
        eventBus.publish(new ReputationChangeEvent("fed", 10f, "test"));
        assertEquals(12.5f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void diplomacyReducesLosses() {
        statsComp.pointSkills.put(PointSkill.DIPLOMACY, 5);
        // delta=-10, reduction = min(5*0.03, 0.5) = 0.15, effective = -10 * (1-0.15) = -8.5
        eventBus.publish(new ReputationChangeEvent("fed", -10f, "test"));
        assertEquals(-8.5f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void diplomacyLossReductionCapped() {
        statsComp.pointSkills.put(PointSkill.DIPLOMACY, 50);
        // reduction = min(50*0.03, 0.5) = min(1.5, 0.5) = 0.5, effective = -10 * 0.5 = -5
        eventBus.publish(new ReputationChangeEvent("fed", -10f, "test"));
        assertEquals(-5f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void tierCrossingPublishesEvent() {
        List<ReputationTierChangedEvent> events = new ArrayList<>();
        eventBus.subscribe(ReputationTierChangedEvent.class, events::add);

        // NEUTRAL(0) → FRIENDLY(25+)
        eventBus.publish(new ReputationChangeEvent("fed", 30f, "test"));

        assertEquals(1, events.size());
        assertEquals("fed", events.get(0).factionId);
        assertEquals(ReputationTier.NEUTRAL, events.get(0).oldTier);
        assertEquals(ReputationTier.FRIENDLY, events.get(0).newTier);
        assertEquals(30f, events.get(0).newStanding, 0.001f);
    }

    @Test
    void noEventWhenTierUnchanged() {
        List<ReputationTierChangedEvent> events = new ArrayList<>();
        eventBus.subscribe(ReputationTierChangedEvent.class, events::add);

        eventBus.publish(new ReputationChangeEvent("fed", 5f, "test"));

        assertTrue(events.isEmpty());
    }

    @Test
    void rippleToAlliedFaction() {
        // Set up: "fed" ALLIED with "confed"
        Map<String, PoliticalRelation> fedRelations = new HashMap<>();
        fedRelations.put("confed", PoliticalRelation.ALLIED);
        relations.put("fed", fedRelations);
        Map<String, PoliticalRelation> confedRelations = new HashMap<>();
        confedRelations.put("fed", PoliticalRelation.ALLIED);
        relations.put("confed", confedRelations);

        eventBus.publish(new ReputationChangeEvent("fed", 20f, "test"));

        assertEquals(20f, manager.getStanding("fed"), 0.001f);
        // Ripple: 20 * 0.50 * +1 = 10
        assertEquals(10f, manager.getStanding("confed"), 0.001f);
    }

    @Test
    void rippleToHostileFactionInvertsSign() {
        // Set up: "fed" HOSTILE with "pirates"
        Map<String, PoliticalRelation> fedRelations = new HashMap<>();
        fedRelations.put("pirates", PoliticalRelation.HOSTILE);
        relations.put("fed", fedRelations);
        Map<String, PoliticalRelation> pirateRelations = new HashMap<>();
        pirateRelations.put("fed", PoliticalRelation.HOSTILE);
        relations.put("pirates", pirateRelations);

        eventBus.publish(new ReputationChangeEvent("fed", 20f, "test"));

        assertEquals(20f, manager.getStanding("fed"), 0.001f);
        // Ripple: 20 * 0.25 * -1 = -5
        assertEquals(-5f, manager.getStanding("pirates"), 0.001f);
    }

    @Test
    void rippleDoesNotCascade() {
        // "fed" ALLIED with "confed", "confed" ALLIED with "terran"
        Map<String, PoliticalRelation> fedRelations = new HashMap<>();
        fedRelations.put("confed", PoliticalRelation.ALLIED);
        relations.put("fed", fedRelations);
        Map<String, PoliticalRelation> confedRelations = new HashMap<>();
        confedRelations.put("fed", PoliticalRelation.ALLIED);
        confedRelations.put("terran", PoliticalRelation.ALLIED);
        relations.put("confed", confedRelations);
        Map<String, PoliticalRelation> terranRelations = new HashMap<>();
        terranRelations.put("confed", PoliticalRelation.ALLIED);
        relations.put("terran", terranRelations);

        eventBus.publish(new ReputationChangeEvent("fed", 20f, "test"));

        assertEquals(20f, manager.getStanding("fed"), 0.001f);
        assertEquals(10f, manager.getStanding("confed"), 0.001f);
        // Terran should NOT be affected (no direct relation with fed, ripple doesn't cascade)
        assertEquals(0f, manager.getStanding("terran"), 0.001f);
    }

    @Test
    void neutralRelationNoRipple() {
        Map<String, PoliticalRelation> fedRelations = new HashMap<>();
        fedRelations.put("neutral_faction", PoliticalRelation.NEUTRAL);
        relations.put("fed", fedRelations);

        eventBus.publish(new ReputationChangeEvent("fed", 20f, "test"));

        assertEquals(20f, manager.getStanding("fed"), 0.001f);
        assertEquals(0f, manager.getStanding("neutral_faction"), 0.001f);
    }

    @Test
    void warRelationFullInverseRipple() {
        Map<String, PoliticalRelation> fedRelations = new HashMap<>();
        fedRelations.put("enemy", PoliticalRelation.WAR);
        relations.put("fed", fedRelations);

        eventBus.publish(new ReputationChangeEvent("fed", 20f, "test"));

        // WAR ripple: 20 * 0.50 * -1 = -10
        assertEquals(-10f, manager.getStanding("enemy"), 0.001f);
    }

    @Test
    void noPlayerEntitySilentlyIgnores() {
        ReputationManager orphan = new ReputationManager(eventBus, config, relations);
        // Should not throw
        eventBus.publish(new ReputationChangeEvent("fed", 10f, "test"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.faction.ReputationManagerTest" -q`
Expected: FAIL — `ReputationManager` class not found.

- [ ] **Step 3: Write ReputationManager**

```java
package com.galacticodyssey.galaxy.faction;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.core.events.ReputationTierChangedEvent;
import com.galacticodyssey.mission.job.ReputationQuery;
import com.galacticodyssey.player.components.PlayerReputationComponent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PointSkill;

import java.util.HashMap;
import java.util.Map;

public class ReputationManager implements ReputationQuery {

    private static final ComponentMapper<PlayerReputationComponent> REP_M =
        ComponentMapper.getFor(PlayerReputationComponent.class);
    private static final ComponentMapper<PlayerStatsComponent> STATS_M =
        ComponentMapper.getFor(PlayerStatsComponent.class);

    private final EventBus eventBus;
    private final ReputationConfigData config;
    private final Map<String, Map<String, PoliticalRelation>> relations;

    private PlayerReputationComponent reputationComponent;
    private PlayerStatsComponent statsComponent;

    public ReputationManager(EventBus eventBus, ReputationConfigData config,
                             Map<String, Map<String, PoliticalRelation>> relations) {
        this.eventBus = eventBus;
        this.config = config;
        this.relations = relations;

        eventBus.subscribe(ReputationChangeEvent.class, this::onReputationChange);
    }

    public void setPlayerEntity(Entity player) {
        this.reputationComponent = REP_M.get(player);
        this.statsComponent = STATS_M.get(player);
    }

    @Override
    public float getStanding(String factionId) {
        if (reputationComponent == null) return 0f;
        Float standing = reputationComponent.standings.get(factionId);
        return standing != null ? standing : 0f;
    }

    public ReputationTier getTier(String factionId) {
        return ReputationTier.fromStanding(getStanding(factionId));
    }

    private void onReputationChange(ReputationChangeEvent event) {
        if (reputationComponent == null) return;

        float delta = event.delta;
        int diplomacyLevel = getDiplomacyLevel();

        if (delta > 0f) {
            delta *= (1.0f + diplomacyLevel * config.diplomacyGainMultPerLevel);
        } else if (delta < 0f) {
            float reduction = Math.min(
                diplomacyLevel * config.diplomacyLossReductionPerLevel,
                config.diplomacyMaxLossReduction);
            delta *= (1.0f - reduction);
        }

        applyDelta(event.factionId, delta);

        Map<String, PoliticalRelation> factionRelations = relations.get(event.factionId);
        if (factionRelations != null) {
            for (Map.Entry<String, PoliticalRelation> entry : factionRelations.entrySet()) {
                float rippleFraction = getRippleFraction(entry.getValue());
                if (rippleFraction == 0f) continue;
                float sign = getRippleSign(entry.getValue());
                applyDelta(entry.getKey(), delta * rippleFraction * sign);
            }
        }
    }

    private void applyDelta(String factionId, float delta) {
        float oldStanding = getStanding(factionId);
        ReputationTier oldTier = ReputationTier.fromStanding(oldStanding);

        float newStanding = Math.max(-100f, Math.min(100f, oldStanding + delta));
        reputationComponent.standings.put(factionId, newStanding);

        ReputationTier newTier = ReputationTier.fromStanding(newStanding);
        if (oldTier != newTier) {
            eventBus.publish(new ReputationTierChangedEvent(
                factionId, oldTier, newTier, newStanding));
        }
    }

    private int getDiplomacyLevel() {
        if (statsComponent == null) return 0;
        Integer level = statsComponent.pointSkills.get(PointSkill.DIPLOMACY);
        return level != null ? level : 0;
    }

    private float getRippleFraction(PoliticalRelation relation) {
        return switch (relation) {
            case ALLIED -> 0.50f;
            case FRIENDLY -> 0.25f;
            case NEUTRAL -> 0.00f;
            case TENSE -> 0.10f;
            case HOSTILE -> 0.25f;
            case WAR -> 0.50f;
        };
    }

    private float getRippleSign(PoliticalRelation relation) {
        return switch (relation) {
            case ALLIED, FRIENDLY -> 1f;
            case NEUTRAL -> 0f;
            case TENSE, HOSTILE, WAR -> -1f;
        };
    }

    public void populateSaveData(Map<String, Object> factionState) {
        if (reputationComponent != null) {
            factionState.put("standings", new HashMap<>(reputationComponent.standings));
        }
    }

    @SuppressWarnings("unchecked")
    public void restoreFromSaveData(Map<String, Object> factionState) {
        if (reputationComponent == null) return;
        Object raw = factionState.get("standings");
        if (raw instanceof Map) {
            reputationComponent.standings.clear();
            reputationComponent.standings.putAll((Map<String, Float>) raw);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.faction.ReputationManagerTest" -q`
Expected: All 17 tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/faction/ReputationManager.java core/src/test/java/com/galacticodyssey/galaxy/faction/ReputationManagerTest.java
git commit -m "feat(faction): add ReputationManager with diplomacy modifiers and ripple effects"
```

---

### Task 5: Combat Kill Listener

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/galaxy/faction/ReputationManager.java`
- Test: `core/src/test/java/com/galacticodyssey/galaxy/faction/ReputationManagerTest.java`

**Context:** When the player kills an NPC that belongs to a faction, the system publishes a `ReputationChangeEvent` with the configured penalty. `ReputationManager` subscribes to `EntityKilledEvent` and checks if the killed entity has `NpcIdentityComponent.factionId`.

**Key types:**
- `com.galacticodyssey.combat.events.EntityKilledEvent` — has `target` (Entity) and `killer` (Entity)
- `com.galacticodyssey.npc.components.NpcIdentityComponent` — has `factionId` (String) and `npcId` (String)

- [ ] **Step 1: Add combat kill test to ReputationManagerTest**

Append these tests to the existing test class:

```java
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.npc.components.NpcIdentityComponent;

// ... inside ReputationManagerTest class ...

@Test
void combatKillAppliesPenalty() {
    Entity npc = new Entity();
    NpcIdentityComponent npcId = new NpcIdentityComponent();
    npcId.factionId = "fed";
    npcId.npcId = "npc_001";
    npc.add(npcId);

    eventBus.publish(new EntityKilledEvent(npc, player));

    assertEquals(config.combatKillPenalty, manager.getStanding("fed"), 0.001f);
}

@Test
void combatKillIgnoresNonFactionNpc() {
    Entity npc = new Entity();
    NpcIdentityComponent npcId = new NpcIdentityComponent();
    npcId.npcId = "npc_002";
    // factionId is null
    npc.add(npcId);

    eventBus.publish(new EntityKilledEvent(npc, player));

    assertEquals(0f, manager.getStanding("fed"), 0.001f);
}

@Test
void combatKillIgnoresNonPlayerKiller() {
    Entity npc = new Entity();
    NpcIdentityComponent npcId = new NpcIdentityComponent();
    npcId.factionId = "fed";
    npcId.npcId = "npc_003";
    npc.add(npcId);

    Entity otherNpc = new Entity();
    eventBus.publish(new EntityKilledEvent(npc, otherNpc));

    assertEquals(0f, manager.getStanding("fed"), 0.001f);
}
```

- [ ] **Step 2: Run tests to verify new tests fail**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.faction.ReputationManagerTest" -q`
Expected: 3 new tests FAIL (combat kill listener not implemented).

- [ ] **Step 3: Add combat kill listener to ReputationManager**

Add these imports to `ReputationManager.java`:

```java
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
```

Add a new ComponentMapper field:

```java
private static final ComponentMapper<NpcIdentityComponent> NPC_M =
    ComponentMapper.getFor(NpcIdentityComponent.class);
```

Add a field to track the player entity reference:

```java
private Entity playerEntity;
```

In the constructor, add a second subscription after the existing one:

```java
eventBus.subscribe(EntityKilledEvent.class, this::onEntityKilled);
```

Store the player entity in `setPlayerEntity`:

```java
public void setPlayerEntity(Entity player) {
    this.playerEntity = player;
    this.reputationComponent = REP_M.get(player);
    this.statsComponent = STATS_M.get(player);
}
```

Add the handler method:

```java
private void onEntityKilled(EntityKilledEvent event) {
    if (event.killer != playerEntity) return;
    NpcIdentityComponent npc = NPC_M.get(event.target);
    if (npc == null || npc.factionId == null) return;
    eventBus.publish(new ReputationChangeEvent(
        npc.factionId, config.combatKillPenalty,
        "combat:" + npc.npcId));
}
```

- [ ] **Step 4: Run tests to verify all pass**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.faction.ReputationManagerTest" -q`
Expected: All 20 tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/galaxy/faction/ReputationManager.java core/src/test/java/com/galacticodyssey/galaxy/faction/ReputationManagerTest.java
git commit -m "feat(faction): ReputationManager listens to EntityKilledEvent for combat rep penalties"
```

---

### Task 6: Trade Reputation

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/economy/components/MarketComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/economy/service/TransactionService.java`
- Test: `core/src/test/java/com/galacticodyssey/economy/TransactionServiceReputationTest.java`

**Context:** When a trade completes at a faction-controlled station, `TransactionService` publishes a `ReputationChangeEvent`. This requires: (1) adding an `ownerFactionId` field to `MarketComponent` so stations know their faction, and (2) adding a `tradeReputationBonus` parameter to `TransactionService` so it can publish the event.

**Key existing code in `TransactionService`:**
- Constructor: `TransactionService(CommodityRegistry, EventBus)`
- `buy(Entity station, Entity player, Entity ship, String commodityId, int quantity)` — reads `MarketComponent` from station, publishes `TradeCompletedEvent` at line 62
- `sell(...)` — same pattern, publishes at line 96

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.economy;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.economy.components.PricingComponent;
import com.galacticodyssey.economy.data.CommodityDefinition;
import com.galacticodyssey.economy.data.CommodityRegistry;
import com.galacticodyssey.economy.data.MarketEntry;
import com.galacticodyssey.economy.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceReputationTest {

    private EventBus eventBus;
    private TransactionService service;
    private Entity station;
    private Entity player;
    private Entity ship;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();

        CommodityDefinition fuel = new CommodityDefinition();
        fuel.id = "fuel";
        fuel.basePrice = 10;
        fuel.volume = 1f;

        CommodityRegistry registry = new CommodityRegistry();
        registry.register(fuel);

        service = new TransactionService(registry, eventBus);
        service.setTradeReputationBonus(1.0f);

        station = new Entity();
        MarketComponent market = new MarketComponent();
        market.stationId = "station_alpha";
        market.ownerFactionId = "fed";
        MarketEntry entry = new MarketEntry();
        entry.stock = 100;
        market.entries.put("fuel", entry);
        station.add(market);

        PricingComponent pricing = new PricingComponent();
        pricing.prices.put("fuel", 10);
        station.add(pricing);

        player = new Entity();
        PlayerWalletComponent wallet = new PlayerWalletComponent();
        wallet.credits = 1000;
        player.add(wallet);

        ship = new Entity();
        CargoBayComponent cargo = new CargoBayComponent();
        cargo.capacity = 100f;
        ship.add(cargo);
    }

    @Test
    void buyAtFactionStationPublishesReputationEvent() {
        List<ReputationChangeEvent> events = new ArrayList<>();
        eventBus.subscribe(ReputationChangeEvent.class, events::add);

        service.buy(station, player, ship, "fuel", 5);

        assertEquals(1, events.size());
        assertEquals("fed", events.get(0).factionId);
        assertEquals(1.0f, events.get(0).delta, 0.001f);
        assertTrue(events.get(0).sourceId.startsWith("trade:"));
    }

    @Test
    void sellAtFactionStationPublishesReputationEvent() {
        CargoBayComponent cargo = ship.getComponent(CargoBayComponent.class);
        cargo.contents.put("fuel", 10);
        cargo.usedVolume = 10f;

        List<ReputationChangeEvent> events = new ArrayList<>();
        eventBus.subscribe(ReputationChangeEvent.class, events::add);

        service.sell(station, player, ship, "fuel", 5);

        assertEquals(1, events.size());
        assertEquals("fed", events.get(0).factionId);
    }

    @Test
    void buyAtNonFactionStationNoReputationEvent() {
        MarketComponent market = station.getComponent(MarketComponent.class);
        market.ownerFactionId = null;

        List<ReputationChangeEvent> events = new ArrayList<>();
        eventBus.subscribe(ReputationChangeEvent.class, events::add);

        service.buy(station, player, ship, "fuel", 5);

        assertTrue(events.isEmpty());
    }
}
```

**Note:** `CommodityRegistry` may not have a `register()` method. Check the actual class — if registration is done differently (e.g., via `loadFromFiles()`), construct one with test data. Alternatively, use a simple approach: create a `CommodityRegistry` and load it, or use a package-visible constructor. Adjust as needed.

- [ ] **Step 2: Run test to verify it fails**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.economy.TransactionServiceReputationTest" -q`
Expected: FAIL — `setTradeReputationBonus` method not found and/or `ownerFactionId` field not found.

- [ ] **Step 3: Add `ownerFactionId` to MarketComponent**

In `core/src/main/java/com/galacticodyssey/economy/components/MarketComponent.java`, add after `stationId`:

```java
public String ownerFactionId;
```

- [ ] **Step 4: Add trade reputation publishing to TransactionService**

In `core/src/main/java/com/galacticodyssey/economy/service/TransactionService.java`:

Add import:
```java
import com.galacticodyssey.core.events.ReputationChangeEvent;
```

Add field:
```java
private float tradeReputationBonus;
```

Add setter:
```java
public void setTradeReputationBonus(float bonus) {
    this.tradeReputationBonus = bonus;
}
```

In `buy()`, after the `eventBus.publish(new TradeCompletedEvent(...))` line, add:

```java
if (market.ownerFactionId != null && tradeReputationBonus != 0f) {
    eventBus.publish(new ReputationChangeEvent(
        market.ownerFactionId, tradeReputationBonus,
        "trade:" + market.stationId));
}
```

In `sell()`, after the `eventBus.publish(new TradeCompletedEvent(...))` line, add the same block:

```java
if (market.ownerFactionId != null && tradeReputationBonus != 0f) {
    eventBus.publish(new ReputationChangeEvent(
        market.ownerFactionId, tradeReputationBonus,
        "trade:" + market.stationId));
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.economy.TransactionServiceReputationTest" -q`
Expected: All 3 tests PASS.

- [ ] **Step 6: Run existing TransactionService tests to verify no regressions**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.economy.*" -q`
Expected: All existing economy tests still PASS.

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/economy/components/MarketComponent.java core/src/main/java/com/galacticodyssey/economy/service/TransactionService.java core/src/test/java/com/galacticodyssey/economy/TransactionServiceReputationTest.java
git commit -m "feat(faction): publish ReputationChangeEvent on trade at faction stations"
```

---

### Task 7: Reputation-Based Price Modifiers

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/economy/service/TransactionService.java`
- Test: `core/src/test/java/com/galacticodyssey/economy/TransactionServiceReputationTest.java`

**Context:** Apply `ReputationTier.buyMultiplier`/`sellMultiplier` to prices at faction stations. Deny trades entirely at HOSTILE stations. `TransactionService` needs a `ReputationQuery` reference (set via setter) and uses it to look up the player's tier with the station's faction.

**Key existing code in `TransactionService.buy()`:**
- Line 40: `int unitPrice = pricing.prices.getOrDefault(commodityId, commodity.basePrice);`
- Line 41: `int totalPrice = unitPrice * quantity;`

The multiplier is applied between computing `unitPrice` and `totalPrice`.

**Also:** Find `TradeFailureReason` (likely an enum in `economy.service` or `economy.events`) and add a `HOSTILE_FACTION` value. If it's an inner enum of `TradeFailedEvent`, add it there.

- [ ] **Step 1: Add pricing tests to TransactionServiceReputationTest**

```java
import com.galacticodyssey.galaxy.faction.ReputationTier;
import com.galacticodyssey.economy.events.TradeFailedEvent;

// ... inside TransactionServiceReputationTest ...

@Test
void buyAtFriendlyStationAppliesDiscount() {
    // Stand at FRIENDLY (25+)
    service.setReputationQuery(factionId -> 30f);

    PlayerWalletComponent wallet = player.getComponent(PlayerWalletComponent.class);
    int startCredits = wallet.credits;

    service.buy(station, player, ship, "fuel", 1);

    // Base price 10, FRIENDLY buy multiplier 0.95 → 10 * 0.95 = 9.5, rounded to 10
    // (rounding depends on implementation; verify actual price paid)
    int paid = startCredits - wallet.credits;
    assertEquals(Math.round(10 * ReputationTier.FRIENDLY.buyMultiplier), paid);
}

@Test
void buyAtHostileStationDenied() {
    service.setReputationQuery(factionId -> -60f);

    List<TradeFailedEvent> failures = new ArrayList<>();
    eventBus.subscribe(TradeFailedEvent.class, failures::add);

    PlayerWalletComponent wallet = player.getComponent(PlayerWalletComponent.class);
    int startCredits = wallet.credits;

    service.buy(station, player, ship, "fuel", 1);

    assertEquals(startCredits, wallet.credits);
    assertEquals(1, failures.size());
}

@Test
void sellAtHostileStationDenied() {
    service.setReputationQuery(factionId -> -60f);

    CargoBayComponent cargo = ship.getComponent(CargoBayComponent.class);
    cargo.contents.put("fuel", 10);
    cargo.usedVolume = 10f;

    List<TradeFailedEvent> failures = new ArrayList<>();
    eventBus.subscribe(TradeFailedEvent.class, failures::add);

    service.sell(station, player, ship, "fuel", 5);

    assertEquals(1, failures.size());
    assertEquals(10, cargo.contents.get("fuel"));
}

@Test
void buyAtNeutralStationNoPriceChange() {
    service.setReputationQuery(factionId -> 5f);

    PlayerWalletComponent wallet = player.getComponent(PlayerWalletComponent.class);
    int startCredits = wallet.credits;

    service.buy(station, player, ship, "fuel", 1);

    assertEquals(startCredits - 10, wallet.credits);
}
```

- [ ] **Step 2: Run tests to verify new tests fail**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.economy.TransactionServiceReputationTest" -q`
Expected: New tests FAIL — `setReputationQuery` not found.

- [ ] **Step 3: Add TradeFailureReason.HOSTILE_FACTION**

Find `TradeFailureReason` (search for its usage in `TransactionService`). It's referenced as `TransactionService.TradeFailureReason` or as a standalone enum. Add `HOSTILE_FACTION` to it.

- [ ] **Step 4: Add pricing logic to TransactionService**

Add import:
```java
import com.galacticodyssey.galaxy.faction.ReputationTier;
import com.galacticodyssey.mission.job.ReputationQuery;
```

Add field and setter:
```java
private ReputationQuery reputationQuery;

public void setReputationQuery(ReputationQuery query) {
    this.reputationQuery = query;
}
```

In `buy()`, **before** the existing stock/funds/cargo validation block (before line 43), add the HOSTILE check:

```java
if (reputationQuery != null && market.ownerFactionId != null) {
    ReputationTier tier = ReputationTier.fromStanding(
        reputationQuery.getStanding(market.ownerFactionId));
    if (tier == ReputationTier.HOSTILE) {
        eventBus.publish(new TradeFailedEvent(
            TradeFailureReason.HOSTILE_FACTION, commodityId, quantity));
        return;
    }
}
```

Then modify the `unitPrice` computation to apply the multiplier:

```java
int unitPrice = pricing.prices.getOrDefault(commodityId, commodity.basePrice);
if (reputationQuery != null && market.ownerFactionId != null) {
    float mult = ReputationTier.fromStanding(
        reputationQuery.getStanding(market.ownerFactionId)).buyMultiplier;
    unitPrice = Math.round(unitPrice * mult);
}
int totalPrice = unitPrice * quantity;
```

In `sell()`, add the same HOSTILE check at the top, and modify `unitPrice`:

```java
if (reputationQuery != null && market.ownerFactionId != null) {
    ReputationTier tier = ReputationTier.fromStanding(
        reputationQuery.getStanding(market.ownerFactionId));
    if (tier == ReputationTier.HOSTILE) {
        eventBus.publish(new TradeFailedEvent(
            TradeFailureReason.HOSTILE_FACTION, commodityId, quantity));
        return;
    }
}
```

And for sell price:
```java
int unitPrice = pricing.prices.getOrDefault(commodityId, commodity.basePrice);
if (reputationQuery != null && market.ownerFactionId != null) {
    float mult = ReputationTier.fromStanding(
        reputationQuery.getStanding(market.ownerFactionId)).sellMultiplier;
    unitPrice = Math.round(unitPrice * mult);
}
int totalPrice = unitPrice * quantity;
```

- [ ] **Step 5: Run tests to verify all pass**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.economy.TransactionServiceReputationTest" -q`
Expected: All 7 tests PASS.

- [ ] **Step 6: Run all economy tests for regressions**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.economy.*" -q`
Expected: All PASS.

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/economy/service/TransactionService.java core/src/test/java/com/galacticodyssey/economy/TransactionServiceReputationTest.java
git commit -m "feat(faction): apply reputation-based price modifiers and deny HOSTILE trades"
```

---

### Task 8: Docking Rights Check

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/docking/DockingApproachSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/docking/DockingApproachReputationTest.java`

**Context:** When a player enters `FAR_APPROACH` phase at a faction-controlled station and their reputation is `HOSTILE`, deny docking immediately by resetting phase to `NONE` and publishing `DockingDeniedEvent`.

**Key existing code in `DockingApproachSystem`:**
- Family: `DockingStateComponent, DockingPortComponent, TransformComponent`
- `processEntity()` currently returns early unless phase is `FINAL_APPROACH` or `MIDRANGE` (line 89-91)
- The target station is `state.targetEntity`
- The reputation check must happen BEFORE the corridor/speed checks, for `FAR_APPROACH` and `MIDRANGE` phases

**DockingDeniedEvent** needs: `stationId` (from `MarketComponent.stationId`), `factionId` (from `MarketComponent.ownerFactionId`), `reason` (String).

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.docking;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.galaxy.faction.ReputationManager;
import com.galacticodyssey.galaxy.faction.ReputationTier;
import com.galacticodyssey.ship.docking.DockingStateComponent.DockingPhase;
import com.galacticodyssey.ship.docking.events.DockingDeniedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DockingApproachReputationTest {

    private EventBus eventBus;
    private DockingApproachSystem system;
    private ReputationManager reputationManager;
    private Engine engine;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        system = new DockingApproachSystem(eventBus);
        reputationManager = mock(ReputationManager.class);
        system.setReputationManager(reputationManager);

        engine = new Engine();
        engine.addSystem(system);
    }

    private Entity createChaser(Entity target, DockingPhase phase) {
        Entity chaser = new Entity();
        DockingStateComponent state = new DockingStateComponent();
        state.dockingPhase = phase;
        state.targetEntity = target;
        chaser.add(state);

        DockingPortComponent port = new DockingPortComponent();
        chaser.add(port);

        TransformComponent transform = new TransformComponent();
        transform.position.set(0, 0, 10);
        chaser.add(transform);

        return chaser;
    }

    private Entity createFactionStation(String stationId, String factionId) {
        Entity station = new Entity();
        TransformComponent transform = new TransformComponent();
        station.add(transform);
        DockingPortComponent port = new DockingPortComponent();
        station.add(port);
        MarketComponent market = new MarketComponent();
        market.stationId = stationId;
        market.ownerFactionId = factionId;
        station.add(market);
        return station;
    }

    @Test
    void hostileFactionDeniedDocking() {
        Entity station = createFactionStation("station_1", "fed");
        engine.addEntity(station);

        Entity chaser = createChaser(station, DockingPhase.FAR_APPROACH);
        engine.addEntity(chaser);

        when(reputationManager.getTier("fed")).thenReturn(ReputationTier.HOSTILE);

        List<DockingDeniedEvent> events = new ArrayList<>();
        eventBus.subscribe(DockingDeniedEvent.class, events::add);

        engine.update(0.016f);

        DockingStateComponent state = chaser.getComponent(DockingStateComponent.class);
        assertEquals(DockingPhase.NONE, state.dockingPhase);
        assertEquals(1, events.size());
        assertEquals("station_1", events.get(0).stationId);
        assertEquals("fed", events.get(0).factionId);
    }

    @Test
    void friendlyFactionAllowedDocking() {
        Entity station = createFactionStation("station_1", "fed");
        engine.addEntity(station);

        Entity chaser = createChaser(station, DockingPhase.FAR_APPROACH);
        engine.addEntity(chaser);

        when(reputationManager.getTier("fed")).thenReturn(ReputationTier.FRIENDLY);

        List<DockingDeniedEvent> events = new ArrayList<>();
        eventBus.subscribe(DockingDeniedEvent.class, events::add);

        engine.update(0.016f);

        DockingStateComponent state = chaser.getComponent(DockingStateComponent.class);
        assertEquals(DockingPhase.FAR_APPROACH, state.dockingPhase);
        assertTrue(events.isEmpty());
    }

    @Test
    void nonFactionStationAlwaysAllowed() {
        Entity station = new Entity();
        station.add(new TransformComponent());
        station.add(new DockingPortComponent());
        // No MarketComponent → no faction
        engine.addEntity(station);

        Entity chaser = createChaser(station, DockingPhase.FAR_APPROACH);
        engine.addEntity(chaser);

        engine.update(0.016f);

        DockingStateComponent state = chaser.getComponent(DockingStateComponent.class);
        assertEquals(DockingPhase.FAR_APPROACH, state.dockingPhase);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.ship.docking.DockingApproachReputationTest" -q`
Expected: FAIL — `setReputationManager` method not found.

- [ ] **Step 3: Add reputation check to DockingApproachSystem**

In `DockingApproachSystem.java`, add imports:

```java
import com.galacticodyssey.economy.components.MarketComponent;
import com.galacticodyssey.galaxy.faction.ReputationManager;
import com.galacticodyssey.galaxy.faction.ReputationTier;
import com.galacticodyssey.ship.docking.events.DockingDeniedEvent;
```

Add ComponentMapper and field:

```java
private static final ComponentMapper<MarketComponent> MARKET_M =
    ComponentMapper.getFor(MarketComponent.class);

private ReputationManager reputationManager;

public void setReputationManager(ReputationManager reputationManager) {
    this.reputationManager = reputationManager;
}
```

In `processEntity()`, **replace** the existing early-return check (lines 88-92) with a broader check that includes `FAR_APPROACH`:

```java
private void processEntity(Entity entity, float deltaTime) {
    DockingStateComponent state = STATE_M.get(entity);

    if (state.dockingPhase == DockingPhase.NONE
        || state.dockingPhase == DockingPhase.CONTACT
        || state.dockingPhase == DockingPhase.HARD_DOCK) {
        return;
    }

    Entity target = state.targetEntity;
    if (target == null) return;

    // Reputation check: deny docking at HOSTILE faction stations
    if (reputationManager != null) {
        MarketComponent targetMarket = MARKET_M.get(target);
        if (targetMarket != null && targetMarket.ownerFactionId != null) {
            if (reputationManager.getTier(targetMarket.ownerFactionId) == ReputationTier.HOSTILE) {
                state.dockingPhase = DockingPhase.NONE;
                eventBus.publish(new DockingDeniedEvent(
                    targetMarket.stationId, targetMarket.ownerFactionId,
                    "HOSTILE reputation"));
                return;
            }
        }
    }

    // Only check corridor during FINAL_APPROACH or MIDRANGE
    if (state.dockingPhase != DockingPhase.FINAL_APPROACH
        && state.dockingPhase != DockingPhase.MIDRANGE) {
        return;
    }

    // ... rest of existing corridor/speed check code ...
```

- [ ] **Step 4: Run tests to verify all pass**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.ship.docking.DockingApproachReputationTest" -q`
Expected: All 3 tests PASS.

- [ ] **Step 5: Run existing docking tests for regressions**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.ship.docking.*" -q`
Expected: All PASS.

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/ship/docking/DockingApproachSystem.java core/src/test/java/com/galacticodyssey/ship/docking/DockingApproachReputationTest.java
git commit -m "feat(faction): deny docking at stations where player is HOSTILE"
```

---

### Task 9: Persistence (Save/Load Standings)

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/galaxy/faction/ReputationManager.java` (already has save/load methods from Task 4)
- Test: `core/src/test/java/com/galacticodyssey/galaxy/faction/ReputationManagerTest.java`

**Context:** `ReputationManager` already has `populateSaveData()` and `restoreFromSaveData()` methods (added in Task 4). This task tests the save/load round-trip to verify standings survive serialization. The caller passes `SaveBundle.factionState` (a `Map<String, Object>`) to these methods.

**How persistence works:** `SaveWriter` writes `bundle.factionState` as a `HashMap<String, Object>` to `factions.bin` via Kryo. `SaveReader` reads it back. The `ReputationManager.populateSaveData()` puts `{"standings": {factionId → float}}` into this map; `restoreFromSaveData()` reads it back.

- [ ] **Step 1: Add persistence tests to ReputationManagerTest**

```java
// ... inside ReputationManagerTest class ...

@Test
void saveAndRestoreRoundTrip() {
    eventBus.publish(new ReputationChangeEvent("fed", 30f, "test"));
    eventBus.publish(new ReputationChangeEvent("pirates", -40f, "test"));

    // Save
    Map<String, Object> factionState = new HashMap<>();
    manager.populateSaveData(factionState);

    // Clear standings
    repComp.standings.clear();
    assertEquals(0f, manager.getStanding("fed"));

    // Restore
    manager.restoreFromSaveData(factionState);

    assertEquals(30f, manager.getStanding("fed"), 0.001f);
    assertEquals(-40f, manager.getStanding("pirates"), 0.001f);
}

@Test
void restoreFromEmptyStateKeepsDefaults() {
    Map<String, Object> emptyState = new HashMap<>();
    manager.restoreFromSaveData(emptyState);

    assertEquals(0f, manager.getStanding("fed"));
}

@Test
void saveWithNoStandingsProducesEmptyMap() {
    Map<String, Object> factionState = new HashMap<>();
    manager.populateSaveData(factionState);

    @SuppressWarnings("unchecked")
    Map<String, Float> standings = (Map<String, Float>) factionState.get("standings");
    assertNotNull(standings);
    assertTrue(standings.isEmpty());
}
```

- [ ] **Step 2: Run tests to verify they pass**

The `populateSaveData` and `restoreFromSaveData` methods were already implemented in Task 4. These tests verify they work correctly.

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.faction.ReputationManagerTest" -q`
Expected: All tests PASS (including 3 new persistence tests).

- [ ] **Step 3: Commit**

```
git add core/src/test/java/com/galacticodyssey/galaxy/faction/ReputationManagerTest.java
git commit -m "test(faction): add save/load round-trip tests for reputation standings"
```

---

### Task 10: Integration Test

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/galaxy/faction/ReputationIntegrationTest.java`

**Context:** End-to-end test wiring `ReputationManager` with a real `EventBus`, faction relations, and multiple event sources. Verifies the full flow: kill NPC → primary rep drops → ripple to allied faction → tier crossing event published.

- [ ] **Step 1: Write the integration test**

```java
package com.galacticodyssey.galaxy.faction;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.core.events.ReputationTierChangedEvent;
import com.galacticodyssey.npc.components.NpcIdentityComponent;
import com.galacticodyssey.player.components.PlayerReputationComponent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PointSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReputationIntegrationTest {

    private EventBus eventBus;
    private ReputationManager manager;
    private Entity player;
    private PlayerReputationComponent repComp;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        ReputationConfigData config = new ReputationConfigData();

        // Set up faction relations: fed ALLIED with confed, fed HOSTILE with pirates
        Map<String, Map<String, PoliticalRelation>> relations = new HashMap<>();
        Map<String, PoliticalRelation> fedRelations = new HashMap<>();
        fedRelations.put("confed", PoliticalRelation.ALLIED);
        fedRelations.put("pirates", PoliticalRelation.HOSTILE);
        relations.put("fed", fedRelations);

        Map<String, PoliticalRelation> confedRelations = new HashMap<>();
        confedRelations.put("fed", PoliticalRelation.ALLIED);
        relations.put("confed", confedRelations);

        Map<String, PoliticalRelation> pirateRelations = new HashMap<>();
        pirateRelations.put("fed", PoliticalRelation.HOSTILE);
        relations.put("pirates", pirateRelations);

        manager = new ReputationManager(eventBus, config, relations);

        player = new Entity();
        repComp = new PlayerReputationComponent();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.pointSkills.put(PointSkill.DIPLOMACY, 0);
        player.add(repComp);
        player.add(stats);
        manager.setPlayerEntity(player);
    }

    @Test
    void killFactionNpcRipplesToAlliesAndEnemies() {
        Entity npc = new Entity();
        NpcIdentityComponent npcId = new NpcIdentityComponent();
        npcId.factionId = "fed";
        npcId.npcId = "guard_001";
        npc.add(npcId);

        List<ReputationTierChangedEvent> tierEvents = new ArrayList<>();
        eventBus.subscribe(ReputationTierChangedEvent.class, tierEvents::add);

        // Kill a Federation NPC — penalty is -15.0
        eventBus.publish(new EntityKilledEvent(npc, player));

        // Primary: fed standing = -15
        assertEquals(-15f, manager.getStanding("fed"), 0.001f);
        assertEquals(ReputationTier.UNFRIENDLY, manager.getTier("fed"));

        // Ripple to ALLIED confed: -15 * 0.50 * +1 = -7.5
        assertEquals(-7.5f, manager.getStanding("confed"), 0.001f);

        // Ripple to HOSTILE pirates: -15 * 0.25 * -1 = +3.75
        assertEquals(3.75f, manager.getStanding("pirates"), 0.001f);

        // NEUTRAL → UNFRIENDLY tier crossing for fed
        assertTrue(tierEvents.stream().anyMatch(e ->
            e.factionId.equals("fed")
            && e.oldTier == ReputationTier.NEUTRAL
            && e.newTier == ReputationTier.UNFRIENDLY));
    }

    @Test
    void multipleKillsAccumulateToHostile() {
        Entity npc = new Entity();
        NpcIdentityComponent npcId = new NpcIdentityComponent();
        npcId.factionId = "fed";
        npcId.npcId = "guard_001";
        npc.add(npcId);

        List<ReputationTierChangedEvent> tierEvents = new ArrayList<>();
        eventBus.subscribe(ReputationTierChangedEvent.class, tierEvents::add);

        // Kill 4 NPCs: 4 * -15 = -60 → HOSTILE
        for (int i = 0; i < 4; i++) {
            eventBus.publish(new EntityKilledEvent(npc, player));
        }

        assertEquals(-60f, manager.getStanding("fed"), 0.001f);
        assertEquals(ReputationTier.HOSTILE, manager.getTier("fed"));

        assertTrue(tierEvents.stream().anyMatch(e ->
            e.factionId.equals("fed")
            && e.newTier == ReputationTier.HOSTILE));
    }

    @Test
    void missionRewardGainsReputation() {
        // Simulate mission completion by publishing ReputationChangeEvent directly
        // (RewardSystem already publishes this; we test that ReputationManager processes it)
        eventBus.publish(new ReputationChangeEvent("fed", 25f, "mission:test_001"));

        assertEquals(25f, manager.getStanding("fed"), 0.001f);
        assertEquals(ReputationTier.FRIENDLY, manager.getTier("fed"));
    }

    @Test
    void diplomacySkillAffectsKillPenalty() {
        PlayerStatsComponent stats = player.getComponent(PlayerStatsComponent.class);
        stats.pointSkills.put(PointSkill.DIPLOMACY, 10);
        // Refresh the cached statsComponent reference
        manager.setPlayerEntity(player);

        Entity npc = new Entity();
        NpcIdentityComponent npcId = new NpcIdentityComponent();
        npcId.factionId = "fed";
        npcId.npcId = "guard_001";
        npc.add(npcId);

        eventBus.publish(new EntityKilledEvent(npc, player));

        // Kill penalty = -15
        // Diplomacy reduction = min(10 * 0.03, 0.5) = 0.3
        // Effective = -15 * (1 - 0.3) = -10.5
        assertEquals(-10.5f, manager.getStanding("fed"), 0.001f);
    }

    @Test
    void saveAndRestorePreservesFullState() {
        eventBus.publish(new ReputationChangeEvent("fed", 30f, "test"));
        eventBus.publish(new ReputationChangeEvent("pirates", -20f, "test"));

        // Save
        Map<String, Object> factionState = new HashMap<>();
        manager.populateSaveData(factionState);

        // Create a new manager and player
        ReputationManager newManager = new ReputationManager(
            eventBus, new ReputationConfigData(), new HashMap<>());
        Entity newPlayer = new Entity();
        PlayerReputationComponent newRep = new PlayerReputationComponent();
        newPlayer.add(newRep);
        newPlayer.add(new PlayerStatsComponent());
        newManager.setPlayerEntity(newPlayer);

        // Restore
        newManager.restoreFromSaveData(factionState);

        assertEquals(30f, newManager.getStanding("fed"), 0.001f);
        assertEquals(-20f, newManager.getStanding("pirates"), 0.001f);
    }
}
```

- [ ] **Step 2: Run integration test**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.faction.ReputationIntegrationTest" -q`
Expected: All 5 tests PASS.

- [ ] **Step 3: Run all faction tests together**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test --tests "com.galacticodyssey.galaxy.faction.*" -q`
Expected: All tests PASS (ReputationTierTest + ReputationManagerTest + ReputationIntegrationTest).

- [ ] **Step 4: Run full test suite to verify no regressions**

Run (PowerShell): `$env:JAVA_HOME = "C:\Users\james\.jdks\temurin-25.0.3"; .\gradlew.bat :core:test -q`
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```
git add core/src/test/java/com/galacticodyssey/galaxy/faction/ReputationIntegrationTest.java
git commit -m "test(faction): add end-to-end integration test for reputation system"
```

---

## Wiring Notes (Game Initialization)

These are small edits that depend on the game's bootstrap/initialization code (likely `GameWorld` or a setup class). They don't require new files or tests — just passing `ReputationManager` to existing code that already accepts the right interfaces.

### JobBoard / ProceduralJobGenerator

`JobBoard.getAvailableJobs(ReputationQuery)` and `ProceduralJobGenerator.generate(..., ReputationQuery)` already accept `ReputationQuery`. Find where these are constructed (search for `new JobBoard(` and `new ProceduralJobGenerator(`) and pass the `ReputationManager` instance where `ReputationQuery` is expected. No API changes needed — `ReputationManager implements ReputationQuery`.

### EncounterContext Creation

`EncounterContext` already accepts `float playerReputation` in its constructor. When encounter generation is wired up, pass `reputationManager.getStanding(owningFactionId)` for the `playerReputation` parameter. Currently `EncounterContext` is not instantiated in production code — this wiring will happen when the encounter system is implemented.

### SaveBundle Integration

In the game's save orchestration code, call `reputationManager.populateSaveData(bundle.factionState)` before writing, and `reputationManager.restoreFromSaveData(bundle.factionState)` after reading. `SaveWriter`/`SaveReader` already handle `factionState` generically — no changes to those classes.
