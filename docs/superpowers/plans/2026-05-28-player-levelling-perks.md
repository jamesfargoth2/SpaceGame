# Player Levelling & Perks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the player progression loop — wire skill-XP awards into gameplay, add a per-skill perk-tree system with data-driven + named-id effects, surface a Character screen + level-up toast, and persist all of it across save/load.

**Architecture:** The data model (`PlayerStatsComponent`), `RealTimeSkillSystem` (level math + the three level/skill/perk events), `PlayerStatQuery`, and `SkillCheck` already exist and are wired in `GameWorld`. This plan fills four gaps: (1) a centralized `SkillXpAwardSystem` that subscribes to gameplay events and calls `awardSkillXP`, keeping gameplay systems perk-agnostic; (2) a `PerkRegistry` + `PerkSystem` driven by `data/player/perk_trees.json` (one tree per real-time skill); (3) perk effects folded into `PlayerStatQuery` (8 existing surfaces) plus 3 outgoing-damage multipliers in `DamageSystem`/`MeleeSystem`, with reserved `specialEffectId` hooks for effects whose systems don't exist yet; (4) a `CharacterScreen` tab + level-up toast overlay, and `PlayerStatsSnapshot` persistence (auto-collected by the generic `EntitySnapshotBuilder`).

**Tech Stack:** Java 17, libGDX 1.13, Ashley ECS, libGDX `Json`/`JsonReader` for data, Scene2D.UI for screens, JUnit 5 for tests. Build: Gradle (`./gradlew`).

---

## Conventions for every task

- Run a single test class: `./gradlew :core:test --tests "com.galacticodyssey.<pkg>.<Class>"`
- Run the full core suite: `./gradlew :core:test`
- All logic tests are **headless** (no GL context). Never construct `Texture`/`Stage` in a logic test.
- Commit after each task with the message shown. End commit bodies with the `Co-Authored-By` trailer used in this repo.
- Package root: `core/src/main/java/com/galacticodyssey/`. Tests mirror under `core/src/test/java/com/galacticodyssey/`.

## File structure (created / modified)

**Created**
- `player/stats/PerkTarget.java`, `ModifierOp.java`, `PerkModifier.java`, `PerkNodeDef.java`, `PerkTree.java`, `PerkRegistry.java`
- `player/systems/PerkSystem.java`, `player/systems/SkillXpAwardSystem.java`
- `player/events/PerkSelectedEvent.java`
- `persistence/snapshots/PlayerStatsSnapshot.java`
- `ui/CharacterScreen.java`, `ui/LevelUpToastOverlay.java`
- `core/src/main/resources/data/player/perk_trees.json`
- Test classes for each logic unit

**Modified**
- `player/components/PlayerStatsComponent.java` (+`unspentPerkPicks`, implements `Snapshotable`)
- `player/systems/RealTimeSkillSystem.java` (increment `unspentPerkPicks`)
- `player/stats/PlayerStatQuery.java` (fold perk modifiers; new damage/util methods)
- `combat/systems/DamageSystem.java`, `combat/systems/MeleeSystem.java` (player outgoing-damage perk multiplier)
- `persistence/SnapshotComponentRegistry.java` (register `PlayerStats`)
- `core/GameWorld.java` (construct registry/systems; getters; set `PlayerStatQuery` registry)
- `ui/GameScreen.java` (register Character tab; toast; key binding; render)
- `docs/systems/player.md`, `docs/TODO-systems.md`

---

## Task 1: Persist player stats — `PlayerStatsSnapshot` + `Snapshotable`

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/PlayerStatsSnapshot.java`
- Modify: `core/src/main/java/com/galacticodyssey/player/components/PlayerStatsComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java:84-88`
- Test: `core/src/test/java/com/galacticodyssey/player/PlayerStatsSnapshotTest.java`

Note: also add the `unspentPerkPicks` field here (used by Task 2 onward).

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.player;

import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PointSkill;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.persistence.snapshots.PlayerStatsSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStatsSnapshotTest {

    @Test
    void roundTripPreservesAllFields() {
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.characterLevel = 7;
        stats.totalXP = 1234.5f;
        stats.unspentPoints = 5;
        stats.unspentPerkPicks = 2;
        stats.realTimeSkills.get(RealTimeSkill.FIREARMS).level = 12;
        stats.realTimeSkills.get(RealTimeSkill.FIREARMS).xp = 40f;
        stats.pointSkills.put(PointSkill.ENGINEERING, 3);
        stats.perks.add("firearms_steady_hands");

        PlayerStatsSnapshot snap = stats.takeSnapshot();

        PlayerStatsComponent restored = new PlayerStatsComponent();
        restored.restoreFromSnapshot(snap);

        assertEquals(7, restored.characterLevel);
        assertEquals(1234.5f, restored.totalXP, 0.001f);
        assertEquals(5, restored.unspentPoints);
        assertEquals(2, restored.unspentPerkPicks);
        assertEquals(12, restored.realTimeSkills.get(RealTimeSkill.FIREARMS).level);
        assertEquals(40f, restored.realTimeSkills.get(RealTimeSkill.FIREARMS).xp, 0.001f);
        assertEquals(3, restored.pointSkills.get(PointSkill.ENGINEERING, 0));
        assertTrue(restored.perks.contains("firearms_steady_hands", false));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.PlayerStatsSnapshotTest"`
Expected: COMPILE FAILURE (`PlayerStatsSnapshot` and `takeSnapshot` do not exist).

- [ ] **Step 3: Create the snapshot class**

`PlayerStatsSnapshot.java`:

```java
package com.galacticodyssey.persistence.snapshots;

import java.util.HashMap;
import java.util.Map;

/** Serializable snapshot of PlayerStatsComponent. Maps key on enum name strings. */
public class PlayerStatsSnapshot {
    public int   characterLevel;
    public float totalXP;
    public int   unspentPoints;
    public int   unspentPerkPicks;

    /** RealTimeSkill name -> level. */
    public Map<String, Integer> realTimeLevels = new HashMap<>();
    /** RealTimeSkill name -> in-level xp. */
    public Map<String, Float>   realTimeXp     = new HashMap<>();
    /** PointSkill name -> allocated level. */
    public Map<String, Integer> pointLevels    = new HashMap<>();

    public String[] perks = new String[0];

    public PlayerStatsSnapshot() {}
}
```

- [ ] **Step 4: Implement `Snapshotable` on `PlayerStatsComponent` and add `unspentPerkPicks`**

Replace the body of `PlayerStatsComponent.java` with:

```java
package com.galacticodyssey.player.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.PlayerStatsSnapshot;
import com.galacticodyssey.player.stats.PointSkill;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.player.stats.SkillProgress;

public class PlayerStatsComponent implements Component, Snapshotable<PlayerStatsSnapshot> {

    public final ObjectMap<RealTimeSkill, SkillProgress> realTimeSkills = new ObjectMap<>();
    public final ObjectMap<PointSkill, Integer>          pointSkills    = new ObjectMap<>();

    public int   characterLevel   = 1;
    public float totalXP          = 0f;
    public int   unspentPoints    = 0;
    public int   unspentPerkPicks = 0;
    public final Array<String> perks = new Array<>();

    public PlayerStatsComponent() {
        for (RealTimeSkill skill : RealTimeSkill.values()) {
            realTimeSkills.put(skill, new SkillProgress());
        }
        for (PointSkill skill : PointSkill.values()) {
            pointSkills.put(skill, 0);
        }
    }

    @Override
    public PlayerStatsSnapshot takeSnapshot() {
        PlayerStatsSnapshot s = new PlayerStatsSnapshot();
        s.characterLevel   = characterLevel;
        s.totalXP          = totalXP;
        s.unspentPoints    = unspentPoints;
        s.unspentPerkPicks = unspentPerkPicks;
        for (RealTimeSkill skill : RealTimeSkill.values()) {
            SkillProgress p = realTimeSkills.get(skill);
            s.realTimeLevels.put(skill.name(), p.level);
            s.realTimeXp.put(skill.name(), p.xp);
        }
        for (PointSkill skill : PointSkill.values()) {
            s.pointLevels.put(skill.name(), pointSkills.get(skill, 0));
        }
        s.perks = perks.toArray(String.class);
        return s;
    }

    @Override
    public void restoreFromSnapshot(PlayerStatsSnapshot s) {
        characterLevel   = s.characterLevel;
        totalXP          = s.totalXP;
        unspentPoints    = s.unspentPoints;
        unspentPerkPicks = s.unspentPerkPicks;
        for (RealTimeSkill skill : RealTimeSkill.values()) {
            SkillProgress p = realTimeSkills.get(skill);
            Integer lvl = s.realTimeLevels.get(skill.name());
            Float   xp  = s.realTimeXp.get(skill.name());
            p.level = lvl != null ? lvl : 1;
            p.xp    = xp  != null ? xp  : 0f;
        }
        for (PointSkill skill : PointSkill.values()) {
            Integer lvl = s.pointLevels.get(skill.name());
            pointSkills.put(skill, lvl != null ? lvl : 0);
        }
        perks.clear();
        if (s.perks != null) perks.addAll(s.perks);
    }
}
```

- [ ] **Step 5: Register the snapshot type**

In `SnapshotComponentRegistry.java`, add the import and a registration line in the `// ----- Player -----` block (after the `PlayerWallet` line, ~line 88):

```java
import com.galacticodyssey.persistence.snapshots.PlayerStatsSnapshot;
import com.galacticodyssey.player.components.PlayerStatsComponent;
```
```java
register("PlayerStats", PlayerStatsSnapshot.class, PlayerStatsComponent::new);
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.PlayerStatsSnapshotTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/snapshots/PlayerStatsSnapshot.java \
        core/src/main/java/com/galacticodyssey/player/components/PlayerStatsComponent.java \
        core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java \
        core/src/test/java/com/galacticodyssey/player/PlayerStatsSnapshotTest.java
git commit -m "feat(player): persist PlayerStatsComponent via snapshot; add unspentPerkPicks"
```

---

## Task 2: `RealTimeSkillSystem` grants a perk pick every 5 levels

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/RealTimeSkillSystem.java:74-85`
- Test: `core/src/test/java/com/galacticodyssey/player/RealTimeSkillSystemPerkPickTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.player;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.player.systems.RealTimeSkillSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RealTimeSkillSystemPerkPickTest {

    @Test
    void crossingLevelFiveGrantsExactlyOnePerkPick() {
        EventBus bus = new EventBus();
        RealTimeSkillSystem system = new RealTimeSkillSystem(bus);
        Entity player = new Entity();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        player.add(stats);

        // totalXP for character level 5 = (5-1)^2 * 250 = 4000. Award enough to reach level >=5.
        system.awardSkillXP(player, RealTimeSkill.FIREARMS, 4000f, 1f);

        assertTrue(stats.characterLevel >= 5);
        assertEquals(1, stats.unspentPerkPicks,
            "exactly one perk pick granted when crossing the level-5 boundary once");
    }

    @Test
    void noPerkPickBeforeLevelFive() {
        EventBus bus = new EventBus();
        RealTimeSkillSystem system = new RealTimeSkillSystem(bus);
        Entity player = new Entity();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        player.add(stats);

        system.awardSkillXP(player, RealTimeSkill.FIREARMS, 500f, 1f); // level ~2-3

        assertTrue(stats.characterLevel < 5);
        assertEquals(0, stats.unspentPerkPicks);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.RealTimeSkillSystemPerkPickTest"`
Expected: FAIL (`unspentPerkPicks` stays 0 — not incremented yet).

- [ ] **Step 3: Increment the counter on perk-available**

In `RealTimeSkillSystem.checkCharacterLevelUp`, add the increment alongside the existing event publish:

```java
            if (stats.characterLevel % 5 == 0) {
                stats.unspentPerkPicks++;
                eventBus.publish(new PerkAvailableEvent(player));
            }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.RealTimeSkillSystemPerkPickTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/RealTimeSkillSystem.java \
        core/src/test/java/com/galacticodyssey/player/RealTimeSkillSystemPerkPickTest.java
git commit -m "feat(player): grant an unspent perk pick every 5 character levels"
```

---

## Task 3: Perk effect vocabulary — `PerkTarget`, `ModifierOp`, `PerkModifier`

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/stats/PerkTarget.java`
- Create: `core/src/main/java/com/galacticodyssey/player/stats/ModifierOp.java`
- Create: `core/src/main/java/com/galacticodyssey/player/stats/PerkModifier.java`

No test in this task — these are plain types exercised by Task 7.

- [ ] **Step 1: Create `PerkTarget`**

```java
package com.galacticodyssey.player.stats;

/**
 * Stat surfaces a perk modifier can affect. Each value MUST have a consumption site:
 * the first block is read by {@link PlayerStatQuery}; the damage values are applied to
 * player outgoing damage in DamageSystem/MeleeSystem.
 */
public enum PerkTarget {
    TRADE_PRICE,        // multiplicative, <1 = better
    REP_GAIN,           // multiplicative
    MAX_CREW,           // additive (whole crew slots)
    CRAFT_QUALITY,      // multiplicative
    HAZARD_RESIST,      // additive (0-1)
    CREW_XP,            // multiplicative
    HEAL_EFF,           // multiplicative
    SCAN_QUALITY,       // multiplicative
    DAMAGE_BALLISTIC,   // multiplicative, player outgoing
    DAMAGE_ENERGY,      // multiplicative, player outgoing
    DAMAGE_MELEE        // multiplicative, player outgoing
}
```

- [ ] **Step 2: Create `ModifierOp`**

```java
package com.galacticodyssey.player.stats;

public enum ModifierOp {
    ADD,   // base + value
    MULT   // base * value
}
```

- [ ] **Step 3: Create `PerkModifier`**

```java
package com.galacticodyssey.player.stats;

/** One typed effect contributed by a perk. Fields are public for libGDX Json binding. */
public class PerkModifier {
    public PerkTarget target;
    public ModifierOp op;
    public float value;

    public PerkModifier() {}

    public PerkModifier(PerkTarget target, ModifierOp op, float value) {
        this.target = target;
        this.op = op;
        this.value = value;
    }
}
```

- [ ] **Step 4: Compile**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/stats/PerkTarget.java \
        core/src/main/java/com/galacticodyssey/player/stats/ModifierOp.java \
        core/src/main/java/com/galacticodyssey/player/stats/PerkModifier.java
git commit -m "feat(player): add perk modifier vocabulary (PerkTarget/ModifierOp/PerkModifier)"
```

---

## Task 4: Perk node + tree data classes

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/stats/PerkNodeDef.java`
- Create: `core/src/main/java/com/galacticodyssey/player/stats/PerkTree.java`

- [ ] **Step 1: Create `PerkNodeDef`**

```java
package com.galacticodyssey.player.stats;

import java.util.ArrayList;
import java.util.List;

/** A single perk node. Public fields + no-arg ctor for libGDX Json binding. */
public class PerkNodeDef {
    public String id;
    public String name;
    public String description;
    public String treeSkill;            // RealTimeSkill enum name, e.g. "FIREARMS"
    public int    tier;                 // depth, 0 = root
    public int    requiredSkillLevel;   // gate on the anchoring skill's level
    public List<String> prerequisitePerkIds = new ArrayList<>();
    public List<PerkModifier> modifiers      = new ArrayList<>();
    public String specialEffectId;      // nullable; named-handler id when no modifier fits

    public PerkNodeDef() {}
}
```

- [ ] **Step 2: Create `PerkTree`**

```java
package com.galacticodyssey.player.stats;

import com.badlogic.gdx.utils.Array;

/** All perk nodes anchored to one real-time skill, ordered by tier. */
public class PerkTree {
    public final RealTimeSkill skill;
    public final Array<PerkNodeDef> nodes = new Array<>();

    public PerkTree(RealTimeSkill skill) {
        this.skill = skill;
    }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/stats/PerkNodeDef.java \
        core/src/main/java/com/galacticodyssey/player/stats/PerkTree.java
git commit -m "feat(player): add PerkNodeDef and PerkTree data classes"
```

---

## Task 5: Perk content + `PerkRegistry` loading

**Files:**
- Create: `core/src/main/resources/data/player/perk_trees.json`
- Create: `core/src/main/java/com/galacticodyssey/player/stats/PerkRegistry.java`
- Test: `core/src/test/java/com/galacticodyssey/player/PerkRegistryLoadTest.java`

The JSON authors one tree per real-time skill (9 trees, 3 tiers each). Modifiers use only wired
`PerkTarget`s; effects that need not-yet-built systems use `specialEffectId` (reserved hooks,
documented in Task 17). Reserved special ids in this content set:
`piloting_evasive`, `piloting_ace`, `stealth_silent_step`, `stealth_shadow`, `stealth_ghost`,
`firearms_rapid_reload`, `energy_heat_sink`, `melee_executioner`, `mining_rich_veins`,
`mining_deep_core`, `repair_field`, `repair_overhaul`, `athletics_marathon`, `athletics_free_runner`.

- [ ] **Step 1: Create the data file**

`core/src/main/resources/data/player/perk_trees.json`:

```json
[
  { "id": "firearms_steady_hands", "name": "Steady Hands", "description": "Ballistic damage +5%.", "treeSkill": "FIREARMS", "tier": 0, "requiredSkillLevel": 0, "prerequisitePerkIds": [], "modifiers": [ { "target": "DAMAGE_BALLISTIC", "op": "MULT", "value": 1.05 } ] },
  { "id": "firearms_marksman", "name": "Marksman", "description": "Ballistic damage +10%.", "treeSkill": "FIREARMS", "tier": 1, "requiredSkillLevel": 10, "prerequisitePerkIds": ["firearms_steady_hands"], "modifiers": [ { "target": "DAMAGE_BALLISTIC", "op": "MULT", "value": 1.10 } ] },
  { "id": "firearms_rapid_reload", "name": "Rapid Reload", "description": "Reload speed greatly improved.", "treeSkill": "FIREARMS", "tier": 2, "requiredSkillLevel": 25, "prerequisitePerkIds": ["firearms_marksman"], "modifiers": [], "specialEffectId": "firearms_rapid_reload" },

  { "id": "energy_overcharge", "name": "Overcharge", "description": "Energy damage +5%.", "treeSkill": "ENERGY_WEAPONS", "tier": 0, "requiredSkillLevel": 0, "prerequisitePerkIds": [], "modifiers": [ { "target": "DAMAGE_ENERGY", "op": "MULT", "value": 1.05 } ] },
  { "id": "energy_focused_beam", "name": "Focused Beam", "description": "Energy damage +10%.", "treeSkill": "ENERGY_WEAPONS", "tier": 1, "requiredSkillLevel": 10, "prerequisitePerkIds": ["energy_overcharge"], "modifiers": [ { "target": "DAMAGE_ENERGY", "op": "MULT", "value": 1.10 } ] },
  { "id": "energy_heat_sink", "name": "Heat Sink", "description": "Energy weapons run cool far longer.", "treeSkill": "ENERGY_WEAPONS", "tier": 2, "requiredSkillLevel": 25, "prerequisitePerkIds": ["energy_focused_beam"], "modifiers": [], "specialEffectId": "energy_heat_sink" },

  { "id": "melee_brawler", "name": "Brawler", "description": "Melee damage +8%.", "treeSkill": "MELEE", "tier": 0, "requiredSkillLevel": 0, "prerequisitePerkIds": [], "modifiers": [ { "target": "DAMAGE_MELEE", "op": "MULT", "value": 1.08 } ] },
  { "id": "melee_crusher", "name": "Crusher", "description": "Melee damage +15%.", "treeSkill": "MELEE", "tier": 1, "requiredSkillLevel": 10, "prerequisitePerkIds": ["melee_brawler"], "modifiers": [ { "target": "DAMAGE_MELEE", "op": "MULT", "value": 1.15 } ] },
  { "id": "melee_executioner", "name": "Executioner", "description": "Heavy finishers on weakened foes.", "treeSkill": "MELEE", "tier": 2, "requiredSkillLevel": 25, "prerequisitePerkIds": ["melee_crusher"], "modifiers": [], "specialEffectId": "melee_executioner" },

  { "id": "piloting_brace", "name": "Brace for Impact", "description": "Hazard resistance +5%.", "treeSkill": "PILOTING", "tier": 0, "requiredSkillLevel": 0, "prerequisitePerkIds": [], "modifiers": [ { "target": "HAZARD_RESIST", "op": "ADD", "value": 0.05 } ] },
  { "id": "piloting_evasive", "name": "Evasive Maneuvers", "description": "Tighter handling under fire.", "treeSkill": "PILOTING", "tier": 1, "requiredSkillLevel": 10, "prerequisitePerkIds": ["piloting_brace"], "modifiers": [], "specialEffectId": "piloting_evasive" },
  { "id": "piloting_ace", "name": "Ace Pilot", "description": "Higher G-tolerance and boost control.", "treeSkill": "PILOTING", "tier": 2, "requiredSkillLevel": 25, "prerequisitePerkIds": ["piloting_evasive"], "modifiers": [], "specialEffectId": "piloting_ace" },

  { "id": "athletics_conditioning", "name": "Conditioning", "description": "Hazard resistance +5%.", "treeSkill": "ATHLETICS", "tier": 0, "requiredSkillLevel": 0, "prerequisitePerkIds": [], "modifiers": [ { "target": "HAZARD_RESIST", "op": "ADD", "value": 0.05 } ] },
  { "id": "athletics_marathon", "name": "Marathon", "description": "Sprint far longer before tiring.", "treeSkill": "ATHLETICS", "tier": 1, "requiredSkillLevel": 10, "prerequisitePerkIds": ["athletics_conditioning"], "modifiers": [], "specialEffectId": "athletics_marathon" },
  { "id": "athletics_free_runner", "name": "Free Runner", "description": "Higher jumps, faster climbs.", "treeSkill": "ATHLETICS", "tier": 2, "requiredSkillLevel": 25, "prerequisitePerkIds": ["athletics_marathon"], "modifiers": [], "specialEffectId": "athletics_free_runner" },

  { "id": "stealth_evasion", "name": "Evasion Training", "description": "Hazard resistance +5%.", "treeSkill": "STEALTH", "tier": 0, "requiredSkillLevel": 0, "prerequisitePerkIds": [], "modifiers": [ { "target": "HAZARD_RESIST", "op": "ADD", "value": 0.05 } ] },
  { "id": "stealth_shadow", "name": "Shadow", "description": "Reduced detection radius.", "treeSkill": "STEALTH", "tier": 1, "requiredSkillLevel": 10, "prerequisitePerkIds": ["stealth_evasion"], "modifiers": [], "specialEffectId": "stealth_shadow" },
  { "id": "stealth_ghost", "name": "Ghost", "description": "Near-silent movement.", "treeSkill": "STEALTH", "tier": 2, "requiredSkillLevel": 25, "prerequisitePerkIds": ["stealth_shadow"], "modifiers": [], "specialEffectId": "stealth_ghost" },

  { "id": "trading_haggler", "name": "Haggler", "description": "Trade prices 3% better.", "treeSkill": "TRADING", "tier": 0, "requiredSkillLevel": 0, "prerequisitePerkIds": [], "modifiers": [ { "target": "TRADE_PRICE", "op": "MULT", "value": 0.97 } ] },
  { "id": "trading_broker", "name": "Broker", "description": "Trade prices a further 3% better.", "treeSkill": "TRADING", "tier": 1, "requiredSkillLevel": 10, "prerequisitePerkIds": ["trading_haggler"], "modifiers": [ { "target": "TRADE_PRICE", "op": "MULT", "value": 0.97 } ] },
  { "id": "trading_magnate", "name": "Magnate", "description": "Reputation gains +10%.", "treeSkill": "TRADING", "tier": 2, "requiredSkillLevel": 25, "prerequisitePerkIds": ["trading_broker"], "modifiers": [ { "target": "REP_GAIN", "op": "MULT", "value": 1.10 } ] },

  { "id": "mining_ore_sense", "name": "Ore Sense", "description": "Crafting quality +5%.", "treeSkill": "MINING", "tier": 0, "requiredSkillLevel": 0, "prerequisitePerkIds": [], "modifiers": [ { "target": "CRAFT_QUALITY", "op": "MULT", "value": 1.05 } ] },
  { "id": "mining_rich_veins", "name": "Rich Veins", "description": "Higher rare-material yield.", "treeSkill": "MINING", "tier": 1, "requiredSkillLevel": 10, "prerequisitePerkIds": ["mining_ore_sense"], "modifiers": [], "specialEffectId": "mining_rich_veins" },
  { "id": "mining_deep_core", "name": "Deep Core", "description": "Faster extraction of dense ores.", "treeSkill": "MINING", "tier": 2, "requiredSkillLevel": 25, "prerequisitePerkIds": ["mining_rich_veins"], "modifiers": [], "specialEffectId": "mining_deep_core" },

  { "id": "repair_tinkerer", "name": "Tinkerer", "description": "Crafting quality +5%.", "treeSkill": "REPAIR", "tier": 0, "requiredSkillLevel": 0, "prerequisitePerkIds": [], "modifiers": [ { "target": "CRAFT_QUALITY", "op": "MULT", "value": 1.05 } ] },
  { "id": "repair_field", "name": "Field Repair", "description": "Faster emergency repairs.", "treeSkill": "REPAIR", "tier": 1, "requiredSkillLevel": 10, "prerequisitePerkIds": ["repair_tinkerer"], "modifiers": [], "specialEffectId": "repair_field" },
  { "id": "repair_overhaul", "name": "Overhaul", "description": "Repairs restore more integrity.", "treeSkill": "REPAIR", "tier": 2, "requiredSkillLevel": 25, "prerequisitePerkIds": ["repair_field"], "modifiers": [], "specialEffectId": "repair_overhaul" }
]
```

- [ ] **Step 2: Write the failing load test**

```java
package com.galacticodyssey.player;

import com.galacticodyssey.player.stats.PerkNodeDef;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.stats.RealTimeSkill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerkRegistryLoadTest {

    /** Loads the registry from the test classpath copy of the data file. */
    private PerkRegistry load() {
        return PerkRegistry.fromClasspath("data/player/perk_trees.json");
    }

    @Test
    void loadsEveryRealTimeSkillTree() {
        PerkRegistry reg = load();
        for (RealTimeSkill skill : RealTimeSkill.values()) {
            assertTrue(reg.getTree(skill).size > 0,
                "expected a perk tree for " + skill);
        }
    }

    @Test
    void resolvesNodeById() {
        PerkRegistry reg = load();
        PerkNodeDef node = reg.get("firearms_marksman");
        assertNotNull(node);
        assertEquals("FIREARMS", node.treeSkill);
        assertEquals(1, node.tier);
        assertTrue(node.prerequisitePerkIds.contains("firearms_steady_hands"));
    }
}
```

`PerkRegistry.fromClasspath` reads via the classpath so the test runs without a GL/libGDX
file backend. The production path (Task 13) uses `Gdx.files.internal`.

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.PerkRegistryLoadTest"`
Expected: COMPILE FAILURE (`PerkRegistry` does not exist).

- [ ] **Step 4: Implement `PerkRegistry` (load + lookup only for now)**

```java
package com.galacticodyssey.player.stats;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Loads and indexes player perk-tree content. Effects are applied via
 * {@link #applyModifiers} (data-driven) and {@link #has} (named-id specials).
 */
public final class PerkRegistry {

    private final ObjectMap<String, PerkNodeDef> byId = new ObjectMap<>();
    private final ObjectMap<RealTimeSkill, PerkTree> trees = new ObjectMap<>();

    private PerkRegistry() {
        for (RealTimeSkill s : RealTimeSkill.values()) trees.put(s, new PerkTree(s));
    }

    /** Production loader (libGDX file backend required). */
    public static PerkRegistry fromFile(FileHandle handle) {
        return parse(handle.readString("UTF-8"));
    }

    /** Test/headless loader — reads from the JVM classpath, no GL context needed. */
    public static PerkRegistry fromClasspath(String resourcePath) {
        try (InputStream in = PerkRegistry.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalArgumentException("resource not found: " + resourcePath);
            try (Scanner scanner = new Scanner(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                scanner.useDelimiter("\\A");
                return parse(scanner.hasNext() ? scanner.next() : "");
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to load perk trees: " + resourcePath, e);
        }
    }

    private static PerkRegistry parse(String jsonText) {
        PerkRegistry reg = new PerkRegistry();
        Json json = new Json();
        PerkNodeDef[] nodes = json.fromJson(PerkNodeDef[].class, jsonText);
        if (nodes == null) return reg;
        for (PerkNodeDef node : nodes) {
            reg.byId.put(node.id, node);
            RealTimeSkill skill = RealTimeSkill.valueOf(node.treeSkill);
            reg.trees.get(skill).nodes.add(node);
        }
        return reg;
    }

    public PerkNodeDef get(String perkId) {
        return byId.get(perkId);
    }

    public Array<PerkNodeDef> getTree(RealTimeSkill skill) {
        return trees.get(skill).nodes;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.PerkRegistryLoadTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/resources/data/player/perk_trees.json \
        core/src/main/java/com/galacticodyssey/player/stats/PerkRegistry.java \
        core/src/test/java/com/galacticodyssey/player/PerkRegistryLoadTest.java
git commit -m "feat(player): add perk_trees.json content + PerkRegistry loader"
```

---

## Task 6: `PerkRegistry.canSelect` — prerequisite gating

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/stats/PerkRegistry.java`
- Test: `core/src/test/java/com/galacticodyssey/player/PerkRegistryCanSelectTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.player;

import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.stats.RealTimeSkill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerkRegistryCanSelectTest {

    private PerkRegistry reg() { return PerkRegistry.fromClasspath("data/player/perk_trees.json"); }

    @Test
    void rootSelectableAtSkillZeroNoPrereqs() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        assertTrue(reg.canSelect(stats, "firearms_steady_hands"));
    }

    @Test
    void childBlockedUntilPrereqOwned() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.realTimeSkills.get(RealTimeSkill.FIREARMS).level = 50; // skill gate met
        assertFalse(reg.canSelect(stats, "firearms_marksman"), "needs parent perk");
        stats.perks.add("firearms_steady_hands");
        assertTrue(reg.canSelect(stats, "firearms_marksman"));
    }

    @Test
    void childBlockedByInsufficientSkillLevel() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.perks.add("firearms_steady_hands");
        stats.realTimeSkills.get(RealTimeSkill.FIREARMS).level = 5; // < required 10
        assertFalse(reg.canSelect(stats, "firearms_marksman"));
    }

    @Test
    void alreadyOwnedNotSelectable() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.perks.add("firearms_steady_hands");
        assertFalse(reg.canSelect(stats, "firearms_steady_hands"));
    }

    @Test
    void unknownPerkNotSelectable() {
        assertFalse(reg().canSelect(new PlayerStatsComponent(), "does_not_exist"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.PerkRegistryCanSelectTest"`
Expected: COMPILE FAILURE (`canSelect` not defined).

- [ ] **Step 3: Implement `canSelect`**

Add to `PerkRegistry`:

```java
    /** True iff not already owned, skill-level gate met, and all prerequisite perks owned. */
    public boolean canSelect(PlayerStatsComponent stats, String perkId) {
        PerkNodeDef node = byId.get(perkId);
        if (node == null) return false;
        if (stats.perks.contains(perkId, false)) return false;
        RealTimeSkill skill = RealTimeSkill.valueOf(node.treeSkill);
        if (stats.realTimeSkills.get(skill).level < node.requiredSkillLevel) return false;
        for (String prereq : node.prerequisitePerkIds) {
            if (!stats.perks.contains(prereq, false)) return false;
        }
        return true;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.PerkRegistryCanSelectTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/stats/PerkRegistry.java \
        core/src/test/java/com/galacticodyssey/player/PerkRegistryCanSelectTest.java
git commit -m "feat(player): PerkRegistry.canSelect prerequisite gating"
```

---

## Task 7: `PerkRegistry.applyModifiers` + `has`

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/stats/PerkRegistry.java`
- Test: `core/src/test/java/com/galacticodyssey/player/PerkRegistryModifierTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.player;

import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.stats.PerkTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerkRegistryModifierTest {

    private PerkRegistry reg() { return PerkRegistry.fromClasspath("data/player/perk_trees.json"); }

    @Test
    void noPerksLeavesBaseUnchanged() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        assertEquals(100f, reg.applyModifiers(stats, PerkTarget.DAMAGE_BALLISTIC, 100f), 0.001f);
    }

    @Test
    void multiplicativeModifiersStack() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.perks.add("firearms_steady_hands"); // *1.05
        stats.perks.add("firearms_marksman");     // *1.10
        // 100 * 1.05 * 1.10 = 115.5
        assertEquals(115.5f, reg.applyModifiers(stats, PerkTarget.DAMAGE_BALLISTIC, 100f), 0.01f);
    }

    @Test
    void additiveModifiersStackOntoBase() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.perks.add("piloting_brace");    // +0.05 HAZARD_RESIST
        stats.perks.add("athletics_conditioning"); // +0.05 HAZARD_RESIST
        assertEquals(0.10f, reg.applyModifiers(stats, PerkTarget.HAZARD_RESIST, 0f), 0.001f);
    }

    @Test
    void hasDetectsSpecialEffect() {
        PerkRegistry reg = reg();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        assertFalse(reg.has(stats, "firearms_rapid_reload"));
        stats.perks.add("firearms_rapid_reload");
        assertTrue(reg.has(stats, "firearms_rapid_reload"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.PerkRegistryModifierTest"`
Expected: COMPILE FAILURE (`applyModifiers`/`has` not defined).

- [ ] **Step 3: Implement `applyModifiers` and `has`**

Add to `PerkRegistry` (add `import com.badlogic.gdx.utils.Array;` already present):

```java
    /**
     * Folds every owned perk's modifiers for {@code target} onto {@code base}:
     * result = (base + sum(ADD values)) * product(MULT values).
     */
    public float applyModifiers(PlayerStatsComponent stats, PerkTarget target, float base) {
        float add = 0f;
        float mult = 1f;
        for (String perkId : stats.perks) {
            PerkNodeDef node = byId.get(perkId);
            if (node == null) continue;
            for (PerkModifier mod : node.modifiers) {
                if (mod.target != target) continue;
                if (mod.op == ModifierOp.ADD)  add  += mod.value;
                else                            mult *= mod.value;
            }
        }
        return (base + add) * mult;
    }

    /** True if the player owns a perk carrying the given specialEffectId. */
    public boolean has(PlayerStatsComponent stats, String specialEffectId) {
        for (String perkId : stats.perks) {
            PerkNodeDef node = byId.get(perkId);
            if (node != null && specialEffectId.equals(node.specialEffectId)) return true;
        }
        return false;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.PerkRegistryModifierTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/stats/PerkRegistry.java \
        core/src/test/java/com/galacticodyssey/player/PerkRegistryModifierTest.java
git commit -m "feat(player): PerkRegistry.applyModifiers + has (perk effect aggregation)"
```

---

## Task 8: `PerkSelectedEvent` + `PerkSystem.selectPerk`

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/events/PerkSelectedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/player/systems/PerkSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/player/PerkSystemTest.java`

- [ ] **Step 1: Create the event**

```java
package com.galacticodyssey.player.events;

import com.badlogic.ashley.core.Entity;

public final class PerkSelectedEvent {
    public final Entity player;
    public final String perkId;

    public PerkSelectedEvent(Entity player, String perkId) {
        this.player = player;
        this.perkId = perkId;
    }
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.galacticodyssey.player;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.events.PerkSelectedEvent;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.systems.PerkSystem;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PerkSystemTest {

    private PerkSystem system(EventBus bus) {
        return new PerkSystem(bus, PerkRegistry.fromClasspath("data/player/perk_trees.json"));
    }

    private Entity player(PlayerStatsComponent stats) {
        Entity e = new Entity();
        e.add(stats);
        return e;
    }

    @Test
    void selectsPerkAndConsumesPick() {
        EventBus bus = new EventBus();
        AtomicReference<PerkSelectedEvent> seen = new AtomicReference<>();
        bus.subscribe(PerkSelectedEvent.class, seen::set);

        PerkSystem sys = system(bus);
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.unspentPerkPicks = 1;
        Entity p = player(stats);

        assertTrue(sys.selectPerk(p, "firearms_steady_hands"));
        assertTrue(stats.perks.contains("firearms_steady_hands", false));
        assertEquals(0, stats.unspentPerkPicks);
        assertNotNull(seen.get());
        assertEquals("firearms_steady_hands", seen.get().perkId);
    }

    @Test
    void failsWithNoPicks() {
        EventBus bus = new EventBus();
        PerkSystem sys = system(bus);
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.unspentPerkPicks = 0;
        assertFalse(sys.selectPerk(player(stats), "firearms_steady_hands"));
        assertFalse(stats.perks.contains("firearms_steady_hands", false));
    }

    @Test
    void failsWhenPrereqsUnmet() {
        EventBus bus = new EventBus();
        PerkSystem sys = system(bus);
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.unspentPerkPicks = 1; // but marksman needs the parent + skill 10
        assertFalse(sys.selectPerk(player(stats), "firearms_marksman"));
        assertEquals(1, stats.unspentPerkPicks, "pick not consumed on failure");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.PerkSystemTest"`
Expected: COMPILE FAILURE (`PerkSystem` not defined).

- [ ] **Step 4: Implement `PerkSystem`**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.events.PerkSelectedEvent;
import com.galacticodyssey.player.stats.PerkRegistry;

/** Validates and applies permanent player perk selections. No respec API. */
public class PerkSystem extends EntitySystem {

    public static final int PRIORITY = 26;

    private static final ComponentMapper<PlayerStatsComponent> STATS_M =
        ComponentMapper.getFor(PlayerStatsComponent.class);

    private final EventBus eventBus;
    private final PerkRegistry perkRegistry;

    public PerkSystem(EventBus eventBus, PerkRegistry perkRegistry) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.perkRegistry = perkRegistry;
    }

    /** @return true if the perk was selected. False if no pick available or prerequisites unmet. */
    public boolean selectPerk(Entity player, String perkId) {
        PlayerStatsComponent stats = STATS_M.get(player);
        if (stats == null || stats.unspentPerkPicks <= 0) return false;
        if (!perkRegistry.canSelect(stats, perkId)) return false;
        stats.perks.add(perkId);
        stats.unspentPerkPicks--;
        eventBus.publish(new PerkSelectedEvent(player, perkId));
        return true;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.PerkSystemTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/events/PerkSelectedEvent.java \
        core/src/main/java/com/galacticodyssey/player/systems/PerkSystem.java \
        core/src/test/java/com/galacticodyssey/player/PerkSystemTest.java
git commit -m "feat(player): PerkSystem.selectPerk with permanent allocation + event"
```

---

## Task 9: Fold perk modifiers into `PlayerStatQuery`

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/stats/PlayerStatQuery.java`
- Test: `core/src/test/java/com/galacticodyssey/player/PlayerStatQueryPerkTest.java`

Approach: `PlayerStatQuery` keeps a process-wide `PerkRegistry` set once at startup
(`setPerkRegistry`). When null (default in isolated tests that don't set it), perk folding is
skipped — existing callers and tests keep working unchanged. New damage methods are added for
the combat wiring in Task 10.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.player;

import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.stats.PlayerStatQuery;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStatQueryPerkTest {

    @BeforeEach
    void setUp() {
        PlayerStatQuery.setPerkRegistry(PerkRegistry.fromClasspath("data/player/perk_trees.json"));
    }

    @AfterEach
    void tearDown() {
        PlayerStatQuery.setPerkRegistry(null);
    }

    @Test
    void tradePerkImprovesTradeModifier() {
        PlayerStatsComponent stats = new PlayerStatsComponent();
        float before = PlayerStatQuery.getTradeModifier(stats);
        stats.perks.add("trading_haggler"); // *0.97
        float after = PlayerStatQuery.getTradeModifier(stats);
        assertTrue(after < before, "trade modifier should drop (cheaper) with the perk");
        assertEquals(before * 0.97f, after, 0.0001f);
    }

    @Test
    void ballisticDamagePerkMultipliesOutgoing() {
        PlayerStatsComponent stats = new PlayerStatsComponent();
        assertEquals(1f, PlayerStatQuery.getOutgoingDamageMultiplier(stats, DamageType.BALLISTIC), 0.0001f);
        stats.perks.add("firearms_steady_hands"); // *1.05
        assertEquals(1.05f, PlayerStatQuery.getOutgoingDamageMultiplier(stats, DamageType.BALLISTIC), 0.0001f);
    }

    @Test
    void nullRegistryLeavesBaseMath() {
        PlayerStatQuery.setPerkRegistry(null);
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.perks.add("trading_haggler");
        // No registry => perks ignored; equals the documented base of getTradeModifier at zero skill.
        assertEquals(1f, PlayerStatQuery.getTradeModifier(stats), 0.0001f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.PlayerStatQueryPerkTest"`
Expected: COMPILE FAILURE (`setPerkRegistry`/`getOutgoingDamageMultiplier` missing).

- [ ] **Step 3: Add registry holder + fold modifiers + damage methods**

Edit `PlayerStatQuery.java`. Add imports and a static holder at the top of the class:

```java
import com.galacticodyssey.combat.CombatEnums.DamageType;

    private static PerkRegistry perkRegistry;

    /** Set once at startup (GameWorld). Null disables perk folding (used by isolated tests). */
    public static void setPerkRegistry(PerkRegistry registry) { perkRegistry = registry; }

    private static float withPerks(PlayerStatsComponent stats, PerkTarget target, float base) {
        return perkRegistry == null ? base : perkRegistry.applyModifiers(stats, target, base);
    }
```

Then wrap each existing return in `withPerks(...)`. Updated method bodies:

```java
    public static float getTradeModifier(PlayerStatsComponent stats) {
        int trading   = stats.realTimeSkills.get(RealTimeSkill.TRADING).level;
        int diplomacy = stats.pointSkills.get(PointSkill.DIPLOMACY, 0);
        float base = 1f - (trading * 0.002f + diplomacy * 0.003f);
        return withPerks(stats, PerkTarget.TRADE_PRICE, base);
    }

    public static float getRepGainModifier(PlayerStatsComponent stats) {
        float base = 1f + stats.pointSkills.get(PointSkill.DIPLOMACY, 0) * 0.005f;
        return withPerks(stats, PerkTarget.REP_GAIN, base);
    }

    public static int getMaxCrewSize(PlayerStatsComponent stats) {
        float base = 4 + stats.pointSkills.get(PointSkill.LEADERSHIP, 0) / 10f;
        return Math.round(withPerks(stats, PerkTarget.MAX_CREW, base));
    }

    public static float getCraftingQuality(PlayerStatsComponent stats) {
        float base = 1f + stats.pointSkills.get(PointSkill.ENGINEERING, 0) * 0.005f;
        return withPerks(stats, PerkTarget.CRAFT_QUALITY, base);
    }

    public static float getHazardResistance(PlayerStatsComponent stats) {
        float base = stats.pointSkills.get(PointSkill.SURVIVAL, 0) * 0.01f;
        return withPerks(stats, PerkTarget.HAZARD_RESIST, base);
    }

    public static float getCrewXPMultiplier(PlayerStatsComponent stats) {
        float base = 1f + stats.pointSkills.get(PointSkill.LEADERSHIP, 0) * 0.004f;
        return withPerks(stats, PerkTarget.CREW_XP, base);
    }

    public static float getHealingEffectiveness(PlayerStatsComponent stats) {
        float base = 1f + stats.pointSkills.get(PointSkill.MEDICINE, 0) * 0.005f;
        return withPerks(stats, PerkTarget.HEAL_EFF, base);
    }

    public static float getScanQuality(PlayerStatsComponent stats) {
        float base = 1f + stats.pointSkills.get(PointSkill.SCIENCE, 0) * 0.005f;
        return withPerks(stats, PerkTarget.SCAN_QUALITY, base);
    }

    /** Player outgoing-damage multiplier for the given damage type (1.0 if no perks). */
    public static float getOutgoingDamageMultiplier(PlayerStatsComponent stats, DamageType type) {
        PerkTarget target;
        switch (type) {
            case BALLISTIC: target = PerkTarget.DAMAGE_BALLISTIC; break;
            case ENERGY:
            case PLASMA:    target = PerkTarget.DAMAGE_ENERGY; break;
            case MELEE:     target = PerkTarget.DAMAGE_MELEE; break;
            default:        return 1f;
        }
        return withPerks(stats, target, 1f);
    }
```

Note: `getMaxCrewSize` base is computed in float now to let an additive perk add whole slots;
behavior at zero perks is unchanged (`4 + n/10` truncated vs rounded differs only when
`n%10>=5` — acceptable and documented). If you prefer to preserve exact legacy truncation,
keep `int base = 4 + .../10;` and only round after `withPerks`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.PlayerStatQueryPerkTest"`
Expected: PASS.

- [ ] **Step 5: Run existing stat-query consumers' tests to confirm no regression**

Run: `./gradlew :core:test --tests "com.galacticodyssey.crafting.*"`
Expected: PASS (registry is null in those tests → base math unchanged).

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/stats/PlayerStatQuery.java \
        core/src/test/java/com/galacticodyssey/player/PlayerStatQueryPerkTest.java
git commit -m "feat(player): fold perk modifiers into PlayerStatQuery + damage multiplier"
```

---

## Task 10: Apply player outgoing-damage perks in combat

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/combat/systems/DamageSystem.java:~105-152`
- Modify: `core/src/main/java/com/galacticodyssey/combat/systems/MeleeSystem.java` (damage application site)
- Test: `core/src/test/java/com/galacticodyssey/combat/PlayerDamagePerkTest.java`

The goal: when the **player** is the attacker, scale the final damage by
`PlayerStatQuery.getOutgoingDamageMultiplier`. Apply at the point damage is computed, before
`DamageDealtEvent`/health subtraction.

- [ ] **Step 1: Read the two damage sites**

Run: open `DamageSystem.java` around line 105-152 and `MeleeSystem.java` around line 180-320.
Identify the local `float damage`/`finalDamage` and the attacker entity in scope. Confirm the
player attacker carries `PlayerStatsComponent` (the player entity created in `GameWorld` does).

- [ ] **Step 2: Write the failing test (DamageSystem path)**

This test exercises the helper the systems will call, keeping it headless (no physics world):

```java
package com.galacticodyssey.combat;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.stats.PlayerStatQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the perk damage multiplier the combat systems apply to player-sourced damage. */
class PlayerDamagePerkTest {

    @BeforeEach
    void setUp() {
        PlayerStatQuery.setPerkRegistry(PerkRegistry.fromClasspath("data/player/perk_trees.json"));
    }

    @AfterEach
    void tearDown() { PlayerStatQuery.setPerkRegistry(null); }

    private float scaled(Entity attacker, float raw, DamageType type) {
        PlayerStatsComponent stats = attacker.getComponent(PlayerStatsComponent.class);
        return stats == null ? raw : raw * PlayerStatQuery.getOutgoingDamageMultiplier(stats, type);
    }

    @Test
    void playerWithBallisticPerkDealsMore() {
        Entity player = new Entity();
        PlayerStatsComponent stats = new PlayerStatsComponent();
        stats.perks.add("firearms_steady_hands"); // *1.05
        player.add(stats);
        assertEquals(105f, scaled(player, 100f, DamageType.BALLISTIC), 0.01f);
    }

    @Test
    void nonPlayerAttackerUnaffected() {
        Entity npc = new Entity();
        assertEquals(100f, scaled(npc, 100f, DamageType.BALLISTIC), 0.01f);
    }
}
```

- [ ] **Step 3: Run test to verify it passes against the helper logic**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.PlayerDamagePerkTest"`
Expected: PASS (the test embeds the same logic the systems will use). This locks the contract.

- [ ] **Step 4: Apply the multiplier in `DamageSystem`**

In `DamageSystem`, just before `eventBus.publish(new DamageDealtEvent(...))` (line ~152) and
before health is reduced, scale `damage` when the attacker is the player. Add an import
`import com.galacticodyssey.player.components.PlayerStatsComponent;` and
`import com.galacticodyssey.player.stats.PlayerStatQuery;`, then:

```java
        if (attacker != null) {
            PlayerStatsComponent attackerStats = attacker.getComponent(PlayerStatsComponent.class);
            if (attackerStats != null) {
                damage *= PlayerStatQuery.getOutgoingDamageMultiplier(attackerStats, damageType);
            }
        }
```

Place this assignment before `damage` is used for health subtraction and the event. Use the
actual local variable names present in the method (`damage`/`finalDamage`, `attacker`,
`damageType`).

- [ ] **Step 5: Apply the multiplier in `MeleeSystem`**

At the melee damage application site (where `damage` is finalized before applying to the target
/ publishing `MeleeHitEvent`), add the same guard using `DamageType.MELEE`:

```java
        if (attacker != null) {
            PlayerStatsComponent attackerStats = attacker.getComponent(PlayerStatsComponent.class);
            if (attackerStats != null) {
                damage *= PlayerStatQuery.getOutgoingDamageMultiplier(attackerStats, DamageType.MELEE);
            }
        }
```

Add the same two imports.

- [ ] **Step 6: Run combat tests to confirm no regression**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/combat/systems/DamageSystem.java \
        core/src/main/java/com/galacticodyssey/combat/systems/MeleeSystem.java \
        core/src/test/java/com/galacticodyssey/combat/PlayerDamagePerkTest.java
git commit -m "feat(combat): apply player outgoing-damage perk multiplier"
```

---

## Task 11: `SkillXpAwardSystem` — discrete-event XP hooks

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/player/systems/SkillXpAwardSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/player/SkillXpAwardSystemTest.java`

The system finds the player via a `PlayerStatsComponent` family, subscribes to gameplay events
in its constructor, and forwards to `RealTimeSkillSystem.awardSkillXP`. It awards only for
player-sourced actions (attacker == player; single-player events like trade/mining/resource
assume the player as actor).

XP tuning constants (documented in the system):
- Firearms/Energy: `finalDamage * 0.1` per damage event; +`15` on kill.
- Melee: `damage * 0.15`.
- Mining: `amount * 2f` (ResourceCollectedEvent).
- Trading: `totalPrice * 0.01f`.
- Repair: flat `10f` per HullRepairEvent.
- Stealth: flat `20f` when an NPC returns to `UNAWARE` from a higher state (evasion).

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import com.galacticodyssey.combat.events.DamageDealtEvent;
import com.galacticodyssey.combat.events.MeleeHitEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ResourceCollectedEvent;
import com.galacticodyssey.economy.events.TradeCompletedEvent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.player.systems.RealTimeSkillSystem;
import com.galacticodyssey.player.systems.SkillXpAwardSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillXpAwardSystemTest {

    private Engine engine;
    private EventBus bus;
    private RealTimeSkillSystem skills;
    private Entity player;
    private PlayerStatsComponent stats;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        bus = new EventBus();
        skills = new RealTimeSkillSystem(bus);
        engine.addSystem(skills);
        player = new Entity();
        stats = new PlayerStatsComponent();
        player.add(stats);
        engine.addEntity(player);
        // constructing the system wires the subscriptions and discovers the player
        SkillXpAwardSystem awardSystem = new SkillXpAwardSystem(bus, skills, engine);
        engine.addSystem(awardSystem);
    }

    private float ballisticXp() {
        return stats.realTimeSkills.get(RealTimeSkill.FIREARMS).xp
             + xpForLevels(RealTimeSkill.FIREARMS);
    }
    // helper: accumulated whole-level XP isn't needed for the assertions below; we check level/xp directly.
    private float xpForLevels(RealTimeSkill s) { return 0f; }

    @Test
    void ballisticDamageByPlayerAwardsFirearms() {
        Entity enemy = new Entity();
        bus.publish(new DamageDealtEvent(enemy, player, 100f, DamageType.BALLISTIC, HitRegion.TORSO));
        // 100 * 0.1 = 10 xp into FIREARMS (still level 1, threshold 102)
        assertEquals(10f, stats.realTimeSkills.get(RealTimeSkill.FIREARMS).xp, 0.01f);
        assertEquals(0f, stats.realTimeSkills.get(RealTimeSkill.ENERGY_WEAPONS).xp, 0.01f);
    }

    @Test
    void energyDamageByPlayerAwardsEnergyWeapons() {
        Entity enemy = new Entity();
        bus.publish(new DamageDealtEvent(enemy, player, 50f, DamageType.ENERGY, HitRegion.TORSO));
        assertEquals(5f, stats.realTimeSkills.get(RealTimeSkill.ENERGY_WEAPONS).xp, 0.01f);
    }

    @Test
    void damageByNonPlayerAwardsNothing() {
        Entity npc = new Entity();
        Entity enemy = new Entity();
        bus.publish(new DamageDealtEvent(enemy, npc, 100f, DamageType.BALLISTIC, HitRegion.TORSO));
        assertEquals(0f, stats.realTimeSkills.get(RealTimeSkill.FIREARMS).xp, 0.01f);
    }

    @Test
    void meleeHitByPlayerAwardsMelee() {
        Entity enemy = new Entity();
        bus.publish(new MeleeHitEvent(player, enemy, AttackDirection.OVERHEAD, HitRegion.HEAD, 40f, DamageType.MELEE));
        assertEquals(6f, stats.realTimeSkills.get(RealTimeSkill.MELEE).xp, 0.01f);
    }

    @Test
    void resourceCollectedAwardsMining() {
        bus.publish(new ResourceCollectedEvent("iron_ore", 3));
        assertEquals(6f, stats.realTimeSkills.get(RealTimeSkill.MINING).xp, 0.01f);
    }

    @Test
    void tradeAwardsTrading() {
        bus.publish(new TradeCompletedEvent("station_1", "water", 10, 5, 500, true));
        assertEquals(5f, stats.realTimeSkills.get(RealTimeSkill.TRADING).xp, 0.01f);
    }
}
```

(Enum constants used: `AttackDirection.OVERHEAD`, `HitRegion.TORSO`/`HEAD`, `DamageType.BALLISTIC`/
`ENERGY`/`MELEE` — all confirmed present in `CombatEnums`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.SkillXpAwardSystemTest"`
Expected: COMPILE FAILURE (`SkillXpAwardSystem` not defined).

- [ ] **Step 3: Implement the system (discrete hooks)**

```java
package com.galacticodyssey.player.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.events.DamageDealtEvent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.combat.events.MeleeHitEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ResourceCollectedEvent;
import com.galacticodyssey.economy.events.TradeCompletedEvent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.stealth.AwarenessState;
import com.galacticodyssey.stealth.events.AwarenessChangedEvent;
import com.galacticodyssey.water.events.HullRepairEvent;

/**
 * Centralizes XP awards: subscribes to gameplay events and forwards to
 * {@link RealTimeSkillSystem}. Keeps gameplay systems unaware of progression
 * (architectural rule #3). Only player-sourced actions award XP.
 *
 * <p>Accrual-based skills (Athletics, Piloting) are handled in {@link #update}.</p>
 *
 * <p>TODO hooks (no source event exists yet): Repair currently maps only to
 * {@link HullRepairEvent} (water hull). Ship-module repair XP awaits a repair-complete
 * event.</p>
 */
public class SkillXpAwardSystem extends EntitySystem {

    public static final int PRIORITY = 27;

    private final EventBus eventBus;
    private final RealTimeSkillSystem skillSystem;
    private final ImmutableArray<Entity> players;

    public SkillXpAwardSystem(EventBus eventBus, RealTimeSkillSystem skillSystem, Engine engine) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.skillSystem = skillSystem;
        this.players = engine.getEntitiesFor(Family.all(PlayerStatsComponent.class).get());
        subscribe();
    }

    private Entity player() {
        return players.size() > 0 ? players.first() : null;
    }

    private boolean isPlayer(Entity e) {
        Entity p = player();
        return p != null && p == e;
    }

    private void subscribe() {
        eventBus.subscribe(DamageDealtEvent.class, e -> {
            if (!isPlayer(e.attacker)) return;
            RealTimeSkill skill = weaponSkill(e.damageType);
            if (skill != null) skillSystem.awardSkillXP(player(), skill, e.finalDamage * 0.1f, 1f);
        });
        eventBus.subscribe(EntityKilledEvent.class, e -> {
            if (!isPlayer(e.killer)) return;
            // Kill bonus to Firearms by default; energy/melee kills still get the per-hit XP above.
            skillSystem.awardSkillXP(player(), RealTimeSkill.FIREARMS, 15f, 1f);
        });
        eventBus.subscribe(MeleeHitEvent.class, e -> {
            if (!isPlayer(e.attacker)) return;
            skillSystem.awardSkillXP(player(), RealTimeSkill.MELEE, e.damage * 0.15f, 1f);
        });
        eventBus.subscribe(ResourceCollectedEvent.class, e ->
            awardIfPlayer(RealTimeSkill.MINING, e.amount * 2f));
        eventBus.subscribe(TradeCompletedEvent.class, e ->
            awardIfPlayer(RealTimeSkill.TRADING, e.totalPrice * 0.01f));
        eventBus.subscribe(HullRepairEvent.class, e -> {
            if (!isPlayer(e.player)) return;
            skillSystem.awardSkillXP(player(), RealTimeSkill.REPAIR, 10f, 1f);
        });
        eventBus.subscribe(AwarenessChangedEvent.class, e -> {
            // Successful evasion: an NPC that was looking returns to UNAWARE.
            if (e.newState == AwarenessState.UNAWARE && e.oldState != AwarenessState.UNAWARE) {
                awardIfPlayer(RealTimeSkill.STEALTH, 20f);
            }
        });
    }

    /** For single-player events with no source entity, the player is the actor. */
    private void awardIfPlayer(RealTimeSkill skill, float baseXP) {
        Entity p = player();
        if (p != null) skillSystem.awardSkillXP(p, skill, baseXP, 1f);
    }

    private static RealTimeSkill weaponSkill(DamageType type) {
        switch (type) {
            case BALLISTIC: return RealTimeSkill.FIREARMS;
            case ENERGY:
            case PLASMA:    return RealTimeSkill.ENERGY_WEAPONS;
            default:        return null; // explosive/EMP/etc. don't train a weapon skill
        }
    }

    @Override
    public void update(float deltaTime) {
        // Accrual hooks added in Task 12.
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.SkillXpAwardSystemTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/SkillXpAwardSystem.java \
        core/src/test/java/com/galacticodyssey/player/SkillXpAwardSystemTest.java
git commit -m "feat(player): SkillXpAwardSystem discrete-event XP hooks"
```

---

## Task 12: `SkillXpAwardSystem` — accrual hooks (Athletics, Piloting)

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/player/systems/SkillXpAwardSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/player/SkillXpAwardAccrualTest.java`

Accrual model (in `update`):
- **Athletics:** while `MovementStateComponent.isSprinting && isGrounded`, accumulate
  `currentSpeed * delta` distance; award `1f` XP per `10` distance units.
- **Piloting:** while `PlayerStateComponent.currentMode == PILOTING`, accumulate time;
  award `2f` XP per `1.0` second.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.player;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.player.systems.RealTimeSkillSystem;
import com.galacticodyssey.player.systems.SkillXpAwardSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillXpAwardAccrualTest {

    private Engine engine;
    private Entity player;
    private PlayerStatsComponent stats;
    private MovementStateComponent move;
    private PlayerStateComponent state;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        EventBus bus = new EventBus();
        RealTimeSkillSystem skills = new RealTimeSkillSystem(bus);
        engine.addSystem(skills);
        player = new Entity();
        stats = new PlayerStatsComponent();
        move = new MovementStateComponent();
        state = new PlayerStateComponent();
        player.add(stats); player.add(move); player.add(state);
        engine.addEntity(player);
        engine.addSystem(new SkillXpAwardSystem(bus, skills, engine));
    }

    @Test
    void sprintingAccruesAthletics() {
        move.isSprinting = true;
        move.isGrounded = true;
        move.currentSpeed = 5f;
        engine.update(1f); // 5 units this tick; need 10 per xp -> 0 yet
        engine.update(1f); // total 10 units -> 1 xp
        assertEquals(1f, stats.realTimeSkills.get(RealTimeSkill.ATHLETICS).xp, 0.01f);
    }

    @Test
    void pilotingAccruesPiloting() {
        state.currentMode = PlayerStateComponent.PlayerMode.PILOTING;
        engine.update(1f); // 1s -> 2 xp
        assertEquals(2f, stats.realTimeSkills.get(RealTimeSkill.PILOTING).xp, 0.01f);
    }

    @Test
    void idleAccruesNothing() {
        engine.update(1f);
        assertEquals(0f, stats.realTimeSkills.get(RealTimeSkill.ATHLETICS).xp, 0.01f);
        assertEquals(0f, stats.realTimeSkills.get(RealTimeSkill.PILOTING).xp, 0.01f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.SkillXpAwardAccrualTest"`
Expected: FAIL (no XP accrued — `update` is empty).

- [ ] **Step 3: Implement accrual in `update`**

Add imports and fields to `SkillXpAwardSystem`:

```java
import com.badlogic.ashley.core.ComponentMapper;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
```
```java
    private static final ComponentMapper<MovementStateComponent> MOVE_M =
        ComponentMapper.getFor(MovementStateComponent.class);
    private static final ComponentMapper<PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(PlayerStateComponent.class);

    private static final float ATHLETICS_DISTANCE_PER_XP = 10f;
    private static final float PILOTING_XP_PER_SECOND     = 2f;

    private float sprintDistanceAccum;
```

Replace `update` with:

```java
    @Override
    public void update(float deltaTime) {
        Entity p = player();
        if (p == null) return;

        MovementStateComponent move = MOVE_M.get(p);
        if (move != null && move.isSprinting && move.isGrounded && move.currentSpeed > 0f) {
            sprintDistanceAccum += move.currentSpeed * deltaTime;
            if (sprintDistanceAccum >= ATHLETICS_DISTANCE_PER_XP) {
                int chunks = (int) (sprintDistanceAccum / ATHLETICS_DISTANCE_PER_XP);
                sprintDistanceAccum -= chunks * ATHLETICS_DISTANCE_PER_XP;
                skillSystem.awardSkillXP(p, RealTimeSkill.ATHLETICS, chunks, 1f);
            }
        }

        PlayerStateComponent state = STATE_M.get(p);
        if (state != null && state.currentMode == PlayerStateComponent.PlayerMode.PILOTING) {
            skillSystem.awardSkillXP(p, RealTimeSkill.PILOTING, PILOTING_XP_PER_SECOND * deltaTime, 1f);
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.player.SkillXpAwardAccrualTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/player/systems/SkillXpAwardSystem.java \
        core/src/test/java/com/galacticodyssey/player/SkillXpAwardAccrualTest.java
git commit -m "feat(player): SkillXpAwardSystem accrual hooks (athletics, piloting)"
```

---

## Task 13: Wire registry + systems into `GameWorld`

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java` (imports; fields near :231;
  construction near :303; getters near :959)

This is integration glue — verified by build + the full suite + the run in Task 18.

- [ ] **Step 1: Add imports**

Near the other player-system imports (~line 70):

```java
import com.galacticodyssey.player.systems.PerkSystem;
import com.galacticodyssey.player.systems.SkillXpAwardSystem;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.stats.PlayerStatQuery;
```

- [ ] **Step 2: Add fields**

Near `private RealTimeSkillSystem realTimeSkillSystem;` (~line 231):

```java
    private PerkRegistry perkRegistry;
    private PerkSystem perkSystem;
    private SkillXpAwardSystem skillXpAwardSystem;
```

- [ ] **Step 3: Construct after `realTimeSkillSystem`**

After lines 303-304 (`realTimeSkillSystem = ...; engine.addSystem(realTimeSkillSystem);`):

```java
        perkRegistry = PerkRegistry.fromFile(Gdx.files.internal("data/player/perk_trees.json"));
        PlayerStatQuery.setPerkRegistry(perkRegistry);

        perkSystem = new PerkSystem(eventBus, perkRegistry);
        engine.addSystem(perkSystem);

        skillXpAwardSystem = new SkillXpAwardSystem(eventBus, realTimeSkillSystem, engine);
        engine.addSystem(skillXpAwardSystem);
```

(Confirm `com.badlogic.gdx.Gdx` is already imported in `GameWorld`; it is used elsewhere.)

- [ ] **Step 4: Add getters near the other `getXxx()` methods (~line 959)**

```java
    public RealTimeSkillSystem getRealTimeSkillSystem() { return realTimeSkillSystem; }
    public PerkSystem getPerkSystem() { return perkSystem; }
    public PerkRegistry getPerkRegistry() { return perkRegistry; }
```

(If `getRealTimeSkillSystem` already exists, keep the existing one.)

- [ ] **Step 5: Verify the player entity is persistable**

Open `GameWorld.createPlayerEntity` (around line 701 where `player.add(new PlayerStatsComponent());`
is). Confirm the player entity is also given a `PersistenceIdComponent` (required for
`EntitySnapshotBuilder` to collect `PlayerStatsComponent`). If it is **not**, add:

```java
        player.add(new com.galacticodyssey.persistence.PersistenceIdComponent());
```

(Use the same construction the other persistent entities use — check `PersistenceIdComponent`'s
constructor; if it needs a UUID, generate one with `UUID.randomUUID()`.)

- [ ] **Step 6: Build + full suite**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(core): wire PerkRegistry, PerkSystem, SkillXpAwardSystem into GameWorld"
```

---

## Task 14: `CharacterScreen` — skills overview, point allocation, perk trees

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/CharacterScreen.java`
- (No headless unit test — Scene2D screen; verified by running the game in Task 18.)

Model it on `QuestJournalOverlay`/`InventoryScreenSystem`: implement `ManagedScreen`, build a
`Stage` with a root `Table`, expose `initialize(...)` to receive the engine + systems. Reads the
player's `PlayerStatsComponent`; calls `RealTimeSkillSystem.spendPoint` and
`PerkSystem.selectPerk`. Never mutates the component directly.

- [ ] **Step 1: Create the screen**

```java
package com.galacticodyssey.ui;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PerkNodeDef;
import com.galacticodyssey.player.stats.PerkRegistry;
import com.galacticodyssey.player.stats.PointSkill;
import com.galacticodyssey.player.stats.RealTimeSkill;
import com.galacticodyssey.player.stats.SkillProgress;
import com.galacticodyssey.player.systems.PerkSystem;
import com.galacticodyssey.player.systems.RealTimeSkillSystem;

/** Character sheet: skill levels, point allocation, and per-skill perk trees. */
public class CharacterScreen implements ManagedScreen {

    private final Stage stage;
    private final Skin skin;
    private boolean open;

    private Engine engine;
    private RealTimeSkillSystem skillSystem;
    private PerkSystem perkSystem;
    private PerkRegistry perkRegistry;

    private final Table root = new Table();
    private final Table body = new Table();

    public CharacterScreen(EventBus eventBus, Skin skin) {
        this.skin = skin;
        this.stage = new Stage(new ScreenViewport());
        root.setFillParent(true);
        root.top();
        ScrollPane scroll = new ScrollPane(body, skin);
        scroll.setFadeScrollBars(false);
        root.add(scroll).expand().fill().pad(20);
        stage.addActor(root);
    }

    public void initialize(Engine engine, RealTimeSkillSystem skillSystem,
                           PerkSystem perkSystem, PerkRegistry perkRegistry) {
        this.engine = engine;
        this.skillSystem = skillSystem;
        this.perkSystem = perkSystem;
        this.perkRegistry = perkRegistry;
    }

    private Entity player() {
        var arr = engine.getEntitiesFor(Family.all(PlayerStatsComponent.class).get());
        return arr.size() > 0 ? arr.first() : null;
    }

    private void rebuild() {
        body.clear();
        Entity p = player();
        if (p == null) return;
        PlayerStatsComponent stats = p.getComponent(PlayerStatsComponent.class);

        body.add(new Label("Character Level " + stats.characterLevel
            + "   XP: " + (int) stats.totalXP, skin)).left().padBottom(4).row();
        body.add(new Label("Unspent skill points: " + stats.unspentPoints
            + "    Perk picks: " + stats.unspentPerkPicks, skin)).left().padBottom(12).row();

        body.add(new Label("Real-Time Skills", skin)).left().padBottom(6).row();
        for (RealTimeSkill skill : RealTimeSkill.values()) {
            SkillProgress prog = stats.realTimeSkills.get(skill);
            Table rowT = new Table();
            rowT.add(new Label(skill.name(), skin)).width(160).left();
            rowT.add(new Label("Lv " + prog.level, skin)).width(60).left();
            ProgressBar bar = new ProgressBar(0f, thresholdFor(prog.level), 1f, false, skin);
            bar.setValue(prog.xp);
            rowT.add(bar).width(200);
            body.add(rowT).left().padBottom(2).row();
        }

        body.add(new Label("Point Skills", skin)).left().padTop(12).padBottom(6).row();
        for (PointSkill skill : PointSkill.values()) {
            int lvl = stats.pointSkills.get(skill, 0);
            Table rowT = new Table();
            rowT.add(new Label(skill.name(), skin)).width(160).left();
            rowT.add(new Label("Lv " + lvl, skin)).width(60).left();
            TextButton plus = new TextButton("+", skin);
            boolean canSpend = stats.unspentPoints > 0 && lvl < RealTimeSkillSystem.MAX_SKILL_LEVEL;
            plus.setDisabled(!canSpend);
            plus.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent e, com.badlogic.gdx.scenes.scene2d.Actor a) {
                    if (skillSystem.spendPoint(p, skill)) rebuild();
                }
            });
            rowT.add(plus).width(40);
            body.add(rowT).left().padBottom(2).row();
        }

        body.add(new Label("Perk Trees (permanent)", skin)).left().padTop(12).padBottom(6).row();
        for (RealTimeSkill skill : RealTimeSkill.values()) {
            var nodes = perkRegistry.getTree(skill);
            if (nodes.size == 0) continue;
            body.add(new Label(skill.name(), skin)).left().padTop(6).row();
            for (PerkNodeDef node : nodes) {
                Table rowT = new Table();
                boolean owned = stats.perks.contains(node.id, false);
                boolean selectable = !owned && stats.unspentPerkPicks > 0
                    && perkRegistry.canSelect(stats, node.id);
                String status = owned ? "[owned] " : (selectable ? "" : "[locked] ");
                rowT.add(new Label("  T" + node.tier + " " + status + node.name
                    + " — " + node.description, skin)).left().width(420);
                if (selectable) {
                    TextButton take = new TextButton("Select", skin);
                    take.addListener(new ChangeListener() {
                        @Override public void changed(ChangeEvent e, com.badlogic.gdx.scenes.scene2d.Actor a) {
                            if (perkSystem.selectPerk(p, node.id)) rebuild();
                        }
                    });
                    rowT.add(take).width(80);
                }
                body.add(rowT).left().padBottom(1).row();
            }
        }
    }

    private static float thresholdFor(int level) {
        return 100f + level * level * 2f;
    }

    @Override public String getDisplayName() { return "Character"; }

    @Override public void open() {
        open = true;
        rebuild();
    }

    @Override public void close() { open = false; }
    @Override public boolean isOpen() { return open; }
    @Override public Stage getStage() { return stage; }

    @Override public void render(float delta) {
        if (!open) return;
        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void dispose() { stage.dispose(); }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL. (If `ProgressBar`/`ScrollPane` styles are missing from the skin, the
run in Task 18 will surface it; swap `ProgressBar` for a `Label "xp/threshold"` if the skin lacks
a default `ProgressBar` style.)

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/CharacterScreen.java
git commit -m "feat(ui): CharacterScreen — skills, point allocation, perk trees"
```

---

## Task 15: `LevelUpToastOverlay`

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/LevelUpToastOverlay.java`

Transient HUD overlay. Subscribes to the three events and shows fading messages. Constructed with
`(EventBus, Skin)` like `HackingOverlay`; `render(delta)` called each frame from `GameScreen`.

- [ ] **Step 1: Create the overlay**

```java
package com.galacticodyssey.ui;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.events.CharacterLevelUpEvent;
import com.galacticodyssey.player.events.PerkAvailableEvent;
import com.galacticodyssey.player.events.SkillLevelUpEvent;

/** Fading top-center notifications for level/skill/perk events. */
public class LevelUpToastOverlay implements Disposable {

    private static final float HOLD = 2.5f;
    private static final float FADE = 0.75f;

    private final Stage stage;
    private final Table root = new Table();
    private final Label label;
    private float timer;

    public LevelUpToastOverlay(EventBus eventBus, Skin skin) {
        stage = new Stage(new ScreenViewport());
        label = new Label("", skin);
        label.setAlignment(Align.center);
        root.setFillParent(true);
        root.top().padTop(80);
        root.add(label);
        root.getColor().a = 0f;
        stage.addActor(root);

        eventBus.subscribe(CharacterLevelUpEvent.class, e ->
            show("LEVEL UP!  Character Level " + e.newLevel + "   +" + e.pointsAwarded + " skill points"));
        eventBus.subscribe(SkillLevelUpEvent.class, e ->
            show(e.skill.name() + "  ->  Lv " + e.newLevel));
        eventBus.subscribe(PerkAvailableEvent.class, e ->
            show("Perk available — open the Character screen"));
    }

    private void show(String text) {
        label.setText(text);
        root.getColor().a = 1f;
        timer = HOLD + FADE;
    }

    public void render(float delta) {
        if (timer > 0f) {
            timer -= delta;
            if (timer < FADE) root.getColor().a = Math.max(0f, timer / FADE);
            stage.act(delta);
            stage.draw();
        }
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void dispose() { stage.dispose(); }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/LevelUpToastOverlay.java
git commit -m "feat(ui): level-up toast overlay for level/skill/perk events"
```

---

## Task 16: Wire the Character tab + toast into `GameScreen`

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`
  (field declarations; `buildScreenTabManager` ~line 685; a `buildLevelUpToast` call; the key
  handler block ~line 338-357; the render block ~line 1142; resize ~line 1220; dispose ~line 1299)

- [ ] **Step 1: Add fields**

Near the other UI fields (e.g. near `private QuestJournalOverlay questJournalOverlay;`):

```java
    private com.galacticodyssey.ui.CharacterScreen characterScreen;
    private com.galacticodyssey.ui.LevelUpToastOverlay levelUpToast;
```

- [ ] **Step 2: Register the Character tab**

In `buildScreenTabManager`, after the `recruitment` registration (~line 687):

```java
        characterScreen = new com.galacticodyssey.ui.CharacterScreen(eventBus, skin);
        characterScreen.initialize(gameWorld.getEngine(),
            gameWorld.getRealTimeSkillSystem(), gameWorld.getPerkSystem(), gameWorld.getPerkRegistry());
        screenTabManager.register("character", characterScreen);
```

- [ ] **Step 3: Construct the toast**

At the end of `buildScreenTabManager` (or in a small `buildLevelUpToast()` called from the same
place the other overlays are built):

```java
        levelUpToast = new com.galacticodyssey.ui.LevelUpToastOverlay(eventBus, skin);
```

- [ ] **Step 4: Add a key binding to open/close the Character tab**

In the key-handler block alongside the existing inventory/journal toggles (~line 338-357), add a
toggle on the `C` key (mirror the journal pattern exactly):

```java
                    if (keycode == com.badlogic.gdx.Input.Keys.C) {
                        if (isAnyScreenOpen() && "character".equals(screenTabManager.getActiveScreenName())) {
                            screenTabManager.closeActive();
                        } else {
                            screenTabManager.switchTo("character");
                        }
                        return true;
                    }
```

(Confirm `C` is not already bound near that block; if it is, use `K`. Match the exact structure of
the surrounding `if (keycode == ...)` cases, including the `InputProcessor` they live in.)

- [ ] **Step 5: Render the toast each frame**

In `render`, next to `if (hackingOverlay != null) hackingOverlay.render(delta);` (~line 1142):

```java
        if (levelUpToast != null) levelUpToast.render(delta);
```

- [ ] **Step 6: Resize + dispose**

In `resize` (~line 1220) add:

```java
        if (levelUpToast != null) levelUpToast.resize(width, height);
```

In `dispose` (~line 1299) add:

```java
        if (levelUpToast != null) { levelUpToast.dispose(); levelUpToast = null; }
```

(The `characterScreen` is owned by `screenTabManager`, which already disposes its registered
screens — do not double-dispose it.)

- [ ] **Step 7: Build**

Run: `./gradlew :core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(ui): register Character tab + level-up toast in GameScreen"
```

---

## Task 17: Documentation

**Files:**
- Modify: `docs/systems/player.md` (Skills & Progression section)
- Modify: `docs/TODO-systems.md` (move items to "Already Implemented")

- [ ] **Step 1: Update `docs/systems/player.md`**

Replace the "Skills & Progression" section with content describing: `RealTimeSkillSystem`
(unchanged) + the new `SkillXpAwardSystem` (event→skill mapping table), `PerkRegistry`
(`perk_trees.json`, per-skill trees, modifiers vs `specialEffectId`), `PerkSystem.selectPerk`
(permanent), `PlayerStatQuery` perk folding + `getOutgoingDamageMultiplier`, the `CharacterScreen`
tab, the level-up toast, and `PlayerStatsSnapshot` persistence. Add a **Reserved special-effect
ids** subsection listing the ids from Task 5's note and their intended (not-yet-wired) systems, so
they aren't mistaken for dead content.

Add `PerkSelectedEvent` to the Events table and `unspentPerkPicks` to the Components reference.

- [ ] **Step 2: Update `docs/TODO-systems.md`**

Remove the **Player levelling & perks** row from "High Impact" and the **Level-up overlay** row
from "UI & UX". Add to the "Already Implemented" table:

```
| Player levelling & perks (XP hooks, perk trees, effects, Character screen, persistence) | coresystemFinish |
```

- [ ] **Step 3: Commit**

```bash
git add docs/systems/player.md docs/TODO-systems.md
git commit -m "docs: document player levelling & perks completion"
```

---

## Task 18: Full verification — build, test, run

**Files:** none (verification only)

- [ ] **Step 1: Full test suite**

Run: `./gradlew :core:test`
Expected: BUILD SUCCESSFUL; all new tests + existing suite pass.

- [ ] **Step 2: Launch the game and exercise the loop**

Use the `run-galactic-odyssey` skill (or `./gradlew :desktop:run`). In-game:
1. Press **C** → the Character screen opens and lists skills, point skills, and perk trees.
2. Trigger XP: fire a ballistic weapon at an enemy / sprint → reopen Character, confirm the
   relevant skill's XP bar advanced and a level-up toast appeared at the top.
3. Reach a skill-point award → use **+** on a point skill; confirm `unspentPoints` decremented
   and the level rose; confirm it cannot go below available points (button disables).
4. At a perk pick (every 5 character levels), select a root perk; confirm it shows `[owned]` and
   the pick was consumed.
5. Save, reload → confirm character level, skill levels, allocated points, and perks persist.

- [ ] **Step 3: Screenshot verification**

Capture the Character screen and a toast via the run skill; confirm layout renders (no missing
skin style). If `ProgressBar`/`ScrollPane`/`ProgressBar` styles are absent, fall back to label-only
rows (noted in Task 14) and re-run.

- [ ] **Step 4: Final commit (if any fixups were needed)**

```bash
git add -A
git commit -m "fix(player): verification fixups for levelling & perks"
```

---

## Self-review notes (coverage vs spec)

- Spec §3 perk data/registry → Tasks 3-7. §4 selection logic → Task 8 (+`unspentPerkPicks` Task 1-2).
  §5 effects via `PlayerStatQuery` (resolved as a `setPerkRegistry` holder; null = base math) → Tasks 9-10.
  §6 XP hooks → Tasks 11-12 (Repair limited to `HullRepairEvent`; ship-module repair documented as a
  TODO hook — no silent omission). §7 UI → Tasks 14-16. §8 persistence → Task 1 (auto-collected by the
  generic `EntitySnapshotBuilder`; player `PersistenceIdComponent` verified in Task 13 step 5). §9 tests →
  per-task headless tests.
- Reserved `specialEffectId` perks (Piloting/Stealth/Mining/Repair tiers, reload/heat-sink/executioner)
  are recorded on the player and documented (Task 17) but not yet consumed — matching the spec's
  "documented hook, no silent no-op."
