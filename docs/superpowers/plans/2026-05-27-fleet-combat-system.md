# Fleet Combat System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a large-scale fleet combat system with fleet-as-entity ECS architecture, three-tier AI, tactical map, off-screen fleet simulation, and ship capture mechanics.

**Architecture:** Fleets are Ashley ECS entities with FleetComponent/FleetFormationComponent/FleetTacticsComponent. Member ships reference their fleet via FleetMemberComponent. Collapsed fleets use aggregate stats for galaxy simulation; expanded fleets near the player spawn individual ship entities with full AI. Three-tier AI (admiral, squadron, individual) with LOD distance-based tiers.

**Tech Stack:** Java 25, libGDX 1.13+, Ashley ECS, gdx-ai behavior trees, libGDX JSON loading, JUnit 5 + Mockito

---

## File Structure

### New Files — Data Layer (`combat/fleet/data/`)
- `FleetShipClass.java` — enum mapping ship classes to size, role, expendability, firepower weight
- `FleetDoctrine.java` — enum for fleet doctrine with retreat thresholds and damage modifiers
- `FleetState.java` — enum for fleet lifecycle states
- `FleetRole.java` — enum for ship roles within a fleet
- `FleetOrder.java` — order data class (type, target, parameters)
- `FleetOrderType.java` — enum for order types
- `FleetShipEntry.java` — ship manifest entry for collapsed fleets
- `FleetCompositionData.java` — data class for fleet composition templates
- `FleetCompositionRegistry.java` — loads fleet_compositions.json
- `FormationTemplate.java` — formation slot positions
- `FormationRegistry.java` — loads formation_templates.json

### New Files — Components (`combat/fleet/components/`)
- `FleetComponent.java` — fleet identity, state, aggregate stats, composition
- `FleetMemberComponent.java` — ship's fleet membership, squadron, role, slot
- `FleetFormationComponent.java` — active formation, anchor position, heading
- `FleetTacticsComponent.java` — threat assessment, orders queue, engagement rules

### New Files — Events (`combat/fleet/events/`)
- `FleetCreatedEvent.java`
- `FleetDestroyedEvent.java`
- `FleetOrderEvent.java`
- `FleetDoctrineChangedEvent.java`
- `FleetStateChangedEvent.java`
- `FleetExpandedEvent.java`
- `FleetCollapsedEvent.java`
- `FleetEngagementStartedEvent.java`
- `FleetBattleResolvedEvent.java`
- `ShipDisabledEvent.java`
- `ShipCapturedEvent.java`
- `EscapePodLaunchedEvent.java`
- `TerritoryChangedEvent.java`
- `FactionFleetMusteredEvent.java`

### New Files — Systems (`combat/fleet/systems/`)
- `FleetFormationSystem.java` — positions ships in formation slots
- `FleetCommandSystem.java` — processes admiral AI and player orders
- `SquadronCoordinationSystem.java` — squadron-level focus fire and coordination
- `FleetSimulationSystem.java` — off-screen fleet movement and battle resolution
- `FleetExpansionSystem.java` — expand/collapse fleets near player
- `FleetLODSystem.java` — promotes/demotes ship AI tiers by distance
- `FleetPostBattleSystem.java` — post-battle cleanup, salvage, repair

### New Files — AI (`combat/fleet/ai/`)
- `AdmiralBehaviorTree.java` — constructs admiral behavior trees from doctrine

### New Files — Persistence (`persistence/snapshots/`)
- `FleetSnapshot.java`
- `FleetMemberSnapshot.java`

### New Files — Tests (`core/src/test/java/com/galacticodyssey/combat/fleet/`)
- `FleetShipClassTest.java`
- `FleetDoctrineTest.java`
- `FleetComponentTest.java`
- `FleetCompositionRegistryTest.java`
- `FormationTemplateTest.java`
- `FleetFormationSystemTest.java`
- `FleetCommandSystemTest.java`
- `SquadronCoordinationSystemTest.java`
- `FleetSimulationSystemTest.java`
- `FleetExpansionSystemTest.java`
- `FleetLODSystemTest.java`
- `FleetPostBattleSystemTest.java`

### New Data Files
- `core/src/main/resources/data/fleet/fleet_compositions.json`
- `core/src/main/resources/data/fleet/formation_templates.json`

### Modified Files
- `combat/CombatEnums.java` — no changes needed (fleet enums in separate files)
- `combat/systems/CombatAISystem.java` — add fleet-awareness check in processEntity

---

## Task 1: Fleet Enums and Data Classes

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/data/FleetShipClass.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/data/FleetDoctrine.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/data/FleetState.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/data/FleetRole.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/data/FleetOrderType.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/data/FleetOrder.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/data/FleetShipEntry.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/fleet/FleetShipClassTest.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/fleet/FleetDoctrineTest.java`

- [ ] **Step 1: Write FleetShipClass enum test**

```java
package com.galacticodyssey.combat.fleet;

import com.galacticodyssey.combat.fleet.data.FleetShipClass;
import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetShipClassTest {

    @Test
    void eachClassMapsToSizeClass() {
        assertEquals(ShipSizeClass.SMALL, FleetShipClass.FIGHTER.sizeClass);
        assertEquals(ShipSizeClass.SMALL, FleetShipClass.BOMBER.sizeClass);
        assertEquals(ShipSizeClass.SMALL, FleetShipClass.CORVETTE.sizeClass);
        assertEquals(ShipSizeClass.MEDIUM, FleetShipClass.FRIGATE.sizeClass);
        assertEquals(ShipSizeClass.MEDIUM, FleetShipClass.DESTROYER.sizeClass);
        assertEquals(ShipSizeClass.MEDIUM, FleetShipClass.CRUISER.sizeClass);
        assertEquals(ShipSizeClass.LARGE, FleetShipClass.BATTLECRUISER.sizeClass);
        assertEquals(ShipSizeClass.LARGE, FleetShipClass.BATTLESHIP.sizeClass);
        assertEquals(ShipSizeClass.LARGE, FleetShipClass.CARRIER.sizeClass);
        assertEquals(ShipSizeClass.LARGE, FleetShipClass.DREADNOUGHT.sizeClass);
    }

    @Test
    void expendableClassesAreSmallShips() {
        assertTrue(FleetShipClass.FIGHTER.expendable);
        assertTrue(FleetShipClass.BOMBER.expendable);
        assertTrue(FleetShipClass.CORVETTE.expendable);
        assertFalse(FleetShipClass.FRIGATE.expendable);
        assertFalse(FleetShipClass.DREADNOUGHT.expendable);
    }

    @Test
    void firepowerWeightIncreasesWithSize() {
        assertTrue(FleetShipClass.FIGHTER.firepowerWeight < FleetShipClass.FRIGATE.firepowerWeight);
        assertTrue(FleetShipClass.FRIGATE.firepowerWeight < FleetShipClass.CRUISER.firepowerWeight);
        assertTrue(FleetShipClass.CRUISER.firepowerWeight < FleetShipClass.BATTLESHIP.firepowerWeight);
        assertTrue(FleetShipClass.BATTLESHIP.firepowerWeight < FleetShipClass.DREADNOUGHT.firepowerWeight);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.FleetShipClassTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Write FleetShipClass enum**

```java
package com.galacticodyssey.combat.fleet.data;

import com.galacticodyssey.ship.ShipSizeClass;

public enum FleetShipClass {
    FIGHTER(ShipSizeClass.SMALL, FleetRole.INTERCEPTOR, true, 1f, 500f, 100f),
    BOMBER(ShipSizeClass.SMALL, FleetRole.FIRE_SUPPORT, true, 2f, 300f, 200f),
    CORVETTE(ShipSizeClass.SMALL, FleetRole.ESCORT, true, 3f, 600f, 300f),
    FRIGATE(ShipSizeClass.MEDIUM, FleetRole.ESCORT, false, 8f, 400f, 800f),
    DESTROYER(ShipSizeClass.MEDIUM, FleetRole.VANGUARD, false, 12f, 500f, 600f),
    CRUISER(ShipSizeClass.MEDIUM, FleetRole.FIRE_SUPPORT, false, 20f, 350f, 1500f),
    BATTLECRUISER(ShipSizeClass.LARGE, FleetRole.VANGUARD, false, 35f, 300f, 3000f),
    BATTLESHIP(ShipSizeClass.LARGE, FleetRole.FIRE_SUPPORT, false, 50f, 200f, 5000f),
    CARRIER(ShipSizeClass.LARGE, FleetRole.SUPPORT, false, 25f, 150f, 4000f),
    DREADNOUGHT(ShipSizeClass.LARGE, FleetRole.FLAGSHIP, false, 80f, 150f, 8000f);

    public final ShipSizeClass sizeClass;
    public final FleetRole defaultRole;
    public final boolean expendable;
    public final float firepowerWeight;
    public final float baseSpeed;
    public final float baseHullHp;

    FleetShipClass(ShipSizeClass sizeClass, FleetRole defaultRole, boolean expendable,
                   float firepowerWeight, float baseSpeed, float baseHullHp) {
        this.sizeClass = sizeClass;
        this.defaultRole = defaultRole;
        this.expendable = expendable;
        this.firepowerWeight = firepowerWeight;
        this.baseSpeed = baseSpeed;
        this.baseHullHp = baseHullHp;
    }
}
```

- [ ] **Step 4: Write FleetRole, FleetState, FleetOrderType enums**

```java
package com.galacticodyssey.combat.fleet.data;

public enum FleetRole {
    FLAGSHIP, VANGUARD, ESCORT, FIRE_SUPPORT, INTERCEPTOR, SUPPORT, RESERVE
}
```

```java
package com.galacticodyssey.combat.fleet.data;

public enum FleetState {
    PATROL, INTERCEPT, ENGAGED, RETREATING, REGROUPING, MUSTERING, JUMPING
}
```

```java
package com.galacticodyssey.combat.fleet.data;

public enum FleetOrderType {
    ATTACK_TARGET, HOLD_POSITION, ADVANCE, RETREAT, REGROUP, LAUNCH_FIGHTERS, RECALL_FIGHTERS,
    MOVE_TO, ESCORT_SHIP, SET_FORMATION
}
```

- [ ] **Step 5: Write FleetDoctrine enum with test**

```java
package com.galacticodyssey.combat.fleet;

import com.galacticodyssey.combat.fleet.data.FleetDoctrine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetDoctrineTest {

    @Test
    void aggressiveHasHigherRetreatThreshold() {
        assertTrue(FleetDoctrine.AGGRESSIVE.retreatThreshold > FleetDoctrine.DEFENSIVE.retreatThreshold);
    }

    @Test
    void aggressiveDealsBonusDamage() {
        assertEquals(1.2f, FleetDoctrine.AGGRESSIVE.damageDealtModifier, 0.001f);
        assertEquals(1.2f, FleetDoctrine.AGGRESSIVE.damageTakenModifier, 0.001f);
    }

    @Test
    void defensiveReducesDamage() {
        assertEquals(0.8f, FleetDoctrine.DEFENSIVE.damageDealtModifier, 0.001f);
        assertEquals(0.8f, FleetDoctrine.DEFENSIVE.damageTakenModifier, 0.001f);
    }

    @Test
    void evasiveRequiresSuperiority() {
        assertTrue(FleetDoctrine.EVASIVE.engageStrengthRatio > 1.0f);
    }
}
```

```java
package com.galacticodyssey.combat.fleet.data;

public enum FleetDoctrine {
    AGGRESSIVE(0.6f, 0.40f, 1.2f, 1.2f),
    BALANCED(0.8f, 0.30f, 1.0f, 1.0f),
    DEFENSIVE(1.0f, 0.25f, 0.8f, 0.8f),
    EVASIVE(1.2f, 0.20f, 0.9f, 0.7f);

    public final float engageStrengthRatio;
    public final float retreatThreshold;
    public final float damageDealtModifier;
    public final float damageTakenModifier;

    FleetDoctrine(float engageStrengthRatio, float retreatThreshold,
                  float damageDealtModifier, float damageTakenModifier) {
        this.engageStrengthRatio = engageStrengthRatio;
        this.retreatThreshold = retreatThreshold;
        this.damageDealtModifier = damageDealtModifier;
        this.damageTakenModifier = damageTakenModifier;
    }
}
```

- [ ] **Step 6: Write FleetOrder and FleetShipEntry data classes**

```java
package com.galacticodyssey.combat.fleet.data;

import com.badlogic.ashley.core.Entity;

public final class FleetOrder {
    public final FleetOrderType type;
    public final Entity targetEntity;
    public final float targetX, targetY, targetZ;
    public final String formationTemplateId;
    public final int[] targetSquadrons;

    public FleetOrder(FleetOrderType type, Entity targetEntity,
                      float targetX, float targetY, float targetZ,
                      String formationTemplateId, int[] targetSquadrons) {
        this.type = type;
        this.targetEntity = targetEntity;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.formationTemplateId = formationTemplateId;
        this.targetSquadrons = targetSquadrons;
    }

    public static FleetOrder attackTarget(Entity target, int[] squadrons) {
        return new FleetOrder(FleetOrderType.ATTACK_TARGET, target, 0, 0, 0, null, squadrons);
    }

    public static FleetOrder moveTo(float x, float y, float z, int[] squadrons) {
        return new FleetOrder(FleetOrderType.MOVE_TO, null, x, y, z, null, squadrons);
    }

    public static FleetOrder holdPosition(int[] squadrons) {
        return new FleetOrder(FleetOrderType.HOLD_POSITION, null, 0, 0, 0, null, squadrons);
    }

    public static FleetOrder retreat() {
        return new FleetOrder(FleetOrderType.RETREAT, null, 0, 0, 0, null, null);
    }

    public static FleetOrder setFormation(String templateId) {
        return new FleetOrder(FleetOrderType.SET_FORMATION, null, 0, 0, 0, templateId, null);
    }

    public static FleetOrder launchFighters() {
        return new FleetOrder(FleetOrderType.LAUNCH_FIGHTERS, null, 0, 0, 0, null, null);
    }
}
```

```java
package com.galacticodyssey.combat.fleet.data;

public final class FleetShipEntry {
    public FleetShipClass shipClass;
    public int count;
    public float avgHpRatio;

    public FleetShipEntry() {}

    public FleetShipEntry(FleetShipClass shipClass, int count, float avgHpRatio) {
        this.shipClass = shipClass;
        this.count = count;
        this.avgHpRatio = avgHpRatio;
    }

    public float totalFirepower() {
        return shipClass.firepowerWeight * count;
    }

    public float totalHp() {
        return shipClass.baseHullHp * count * avgHpRatio;
    }
}
```

- [ ] **Step 7: Run all tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.*" --info`
Expected: All pass

- [ ] **Step 8: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/data/ core/src/test/java/com/galacticodyssey/combat/fleet/
git commit -m "feat(fleet): add fleet enums and data classes

FleetShipClass, FleetDoctrine, FleetState, FleetRole, FleetOrderType,
FleetOrder, FleetShipEntry — foundation types for the fleet combat system."
```

---

## Task 2: Fleet Components

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/components/FleetComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/components/FleetMemberComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/components/FleetFormationComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/components/FleetTacticsComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/FleetSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/FleetMemberSnapshot.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/fleet/FleetComponentTest.java`

- [ ] **Step 1: Write FleetComponent test**

```java
package com.galacticodyssey.combat.fleet;

import com.galacticodyssey.combat.fleet.components.FleetComponent;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.persistence.snapshots.FleetSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetComponentTest {

    @Test
    void computeAggregateFirepower() {
        FleetComponent fc = new FleetComponent();
        fc.composition.add(new FleetShipEntry(FleetShipClass.FIGHTER, 10, 1.0f));
        fc.composition.add(new FleetShipEntry(FleetShipClass.CRUISER, 3, 1.0f));
        fc.recomputeAggregates();

        float expected = FleetShipClass.FIGHTER.firepowerWeight * 10
                       + FleetShipClass.CRUISER.firepowerWeight * 3;
        assertEquals(expected, fc.aggregateFirepower, 0.01f);
    }

    @Test
    void aggregateSpeedLimitedBySlowest() {
        FleetComponent fc = new FleetComponent();
        fc.composition.add(new FleetShipEntry(FleetShipClass.FIGHTER, 5, 1.0f));
        fc.composition.add(new FleetShipEntry(FleetShipClass.BATTLESHIP, 1, 1.0f));
        fc.recomputeAggregates();

        assertEquals(FleetShipClass.BATTLESHIP.baseSpeed, fc.aggregateSpeed, 0.01f);
    }

    @Test
    void snapshotRoundTrip() {
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "fleet-001";
        fc.factionId = "militarist-1";
        fc.fleetName = "Iron Fist";
        fc.doctrine = FleetDoctrine.AGGRESSIVE;
        fc.state = FleetState.PATROL;
        fc.composition.add(new FleetShipEntry(FleetShipClass.CRUISER, 3, 0.8f));
        fc.recomputeAggregates();

        FleetSnapshot snap = fc.takeSnapshot();
        FleetComponent restored = new FleetComponent();
        restored.restoreFromSnapshot(snap);

        assertEquals("fleet-001", restored.fleetId);
        assertEquals("militarist-1", restored.factionId);
        assertEquals(FleetDoctrine.AGGRESSIVE, restored.doctrine);
        assertEquals(FleetState.PATROL, restored.state);
        assertEquals(1, restored.composition.size());
        assertEquals(FleetShipClass.CRUISER, restored.composition.get(0).shipClass);
        assertEquals(3, restored.composition.get(0).count);
        assertEquals(fc.aggregateFirepower, restored.aggregateFirepower, 0.01f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.FleetComponentTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Write FleetSnapshot**

```java
package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class FleetSnapshot {
    public String fleetId;
    public String factionId;
    public String fleetName;
    public String doctrine;
    public String state;
    public float aggregateFirepower;
    public float aggregateHP;
    public float aggregateSpeed;
    public boolean expanded;
    public List<FleetShipEntrySnapshot> composition = new ArrayList<>();

    public static class FleetShipEntrySnapshot {
        public String shipClass;
        public int count;
        public float avgHpRatio;
    }
}
```

- [ ] **Step 4: Write FleetComponent**

```java
package com.galacticodyssey.combat.fleet.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.FleetSnapshot;
import java.util.ArrayList;
import java.util.List;

public class FleetComponent implements Component, Snapshotable<FleetSnapshot> {
    public String fleetId;
    public String factionId;
    public String fleetName;
    public Entity admiralEntity;
    public Entity flagshipEntity;
    public FleetDoctrine doctrine = FleetDoctrine.BALANCED;
    public FleetState state = FleetState.MUSTERING;
    public float aggregateFirepower;
    public float aggregateHP;
    public float aggregateSpeed;
    public final List<FleetShipEntry> composition = new ArrayList<>();
    public boolean expanded;

    public void recomputeAggregates() {
        aggregateFirepower = 0f;
        aggregateHP = 0f;
        aggregateSpeed = Float.MAX_VALUE;
        for (FleetShipEntry entry : composition) {
            aggregateFirepower += entry.totalFirepower();
            aggregateHP += entry.totalHp();
            aggregateSpeed = Math.min(aggregateSpeed, entry.shipClass.baseSpeed);
        }
        if (composition.isEmpty()) {
            aggregateSpeed = 0f;
        }
    }

    public float lossRatio() {
        float maxHP = 0f;
        for (FleetShipEntry entry : composition) {
            maxHP += entry.shipClass.baseHullHp * entry.count;
        }
        if (maxHP <= 0f) return 1f;
        return 1f - (aggregateHP / maxHP);
    }

    @Override
    public FleetSnapshot takeSnapshot() {
        FleetSnapshot s = new FleetSnapshot();
        s.fleetId = fleetId;
        s.factionId = factionId;
        s.fleetName = fleetName;
        s.doctrine = doctrine.name();
        s.state = state.name();
        s.aggregateFirepower = aggregateFirepower;
        s.aggregateHP = aggregateHP;
        s.aggregateSpeed = aggregateSpeed;
        s.expanded = expanded;
        for (FleetShipEntry entry : composition) {
            FleetSnapshot.FleetShipEntrySnapshot es = new FleetSnapshot.FleetShipEntrySnapshot();
            es.shipClass = entry.shipClass.name();
            es.count = entry.count;
            es.avgHpRatio = entry.avgHpRatio;
            s.composition.add(es);
        }
        return s;
    }

    @Override
    public void restoreFromSnapshot(FleetSnapshot s) {
        fleetId = s.fleetId;
        factionId = s.factionId;
        fleetName = s.fleetName;
        doctrine = FleetDoctrine.valueOf(s.doctrine);
        state = FleetState.valueOf(s.state);
        aggregateFirepower = s.aggregateFirepower;
        aggregateHP = s.aggregateHP;
        aggregateSpeed = s.aggregateSpeed;
        expanded = s.expanded;
        composition.clear();
        for (FleetSnapshot.FleetShipEntrySnapshot es : s.composition) {
            composition.add(new FleetShipEntry(
                FleetShipClass.valueOf(es.shipClass), es.count, es.avgHpRatio));
        }
    }
}
```

- [ ] **Step 5: Write FleetMemberSnapshot and FleetMemberComponent**

```java
package com.galacticodyssey.persistence.snapshots;

public class FleetMemberSnapshot {
    public String fleetId;
    public int squadronIndex;
    public String role;
    public int formationSlotIndex;
}
```

```java
package com.galacticodyssey.combat.fleet.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.data.FleetRole;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.FleetMemberSnapshot;

public class FleetMemberComponent implements Component, Snapshotable<FleetMemberSnapshot> {
    public Entity fleetEntity;
    public String fleetId;
    public int squadronIndex;
    public FleetRole role = FleetRole.ESCORT;
    public int formationSlotIndex;
    public final Vector3 localFormationOffset = new Vector3();

    @Override
    public FleetMemberSnapshot takeSnapshot() {
        FleetMemberSnapshot s = new FleetMemberSnapshot();
        s.fleetId = fleetId;
        s.squadronIndex = squadronIndex;
        s.role = role.name();
        s.formationSlotIndex = formationSlotIndex;
        return s;
    }

    @Override
    public void restoreFromSnapshot(FleetMemberSnapshot s) {
        fleetId = s.fleetId;
        squadronIndex = s.squadronIndex;
        role = FleetRole.valueOf(s.role);
        formationSlotIndex = s.formationSlotIndex;
    }
}
```

- [ ] **Step 6: Write FleetFormationComponent**

```java
package com.galacticodyssey.combat.fleet.components;

import com.badlogic.ashley.core.Component;

public class FleetFormationComponent implements Component {
    public String formationTemplateId = "wedge";
    public double anchorX, anchorY, anchorZ;
    public float localAnchorX, localAnchorY, localAnchorZ;
    public float headingYaw, headingPitch;
    public float spacingScale = 1.0f;
}
```

- [ ] **Step 7: Write FleetTacticsComponent**

```java
package com.galacticodyssey.combat.fleet.components;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.fleet.data.FleetOrder;
import com.galacticodyssey.combat.fleet.data.FleetShipClass;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class FleetTacticsComponent implements Component {
    public final Map<String, Float> threatAssessment = new HashMap<>();
    public float engageMinRange = 500f;
    public float engageMaxRange = 2000f;
    public float retreatThreshold = 0.30f;
    public FleetShipClass priorityTargetClass;
    public final Queue<FleetOrder> orders = new ArrayDeque<>();
}
```

- [ ] **Step 8: Run all tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.*" --info`
Expected: All pass

- [ ] **Step 9: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/components/ core/src/main/java/com/galacticodyssey/persistence/snapshots/FleetSnapshot.java core/src/main/java/com/galacticodyssey/persistence/snapshots/FleetMemberSnapshot.java core/src/test/java/com/galacticodyssey/combat/fleet/
git commit -m "feat(fleet): add fleet ECS components with persistence

FleetComponent, FleetMemberComponent, FleetFormationComponent,
FleetTacticsComponent with Snapshotable support for save/load."
```

---

## Task 3: Fleet Events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/FleetCreatedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/FleetDestroyedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/FleetOrderEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/FleetDoctrineChangedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/FleetStateChangedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/FleetExpandedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/FleetCollapsedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/FleetEngagementStartedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/FleetBattleResolvedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/ShipDisabledEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/ShipCapturedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/EscapePodLaunchedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/TerritoryChangedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/events/FactionFleetMusteredEvent.java`

- [ ] **Step 1: Write all event classes**

Events are simple immutable data carriers. All fields are public final, set via constructor.

```java
package com.galacticodyssey.combat.fleet.events;

public final class FleetCreatedEvent {
    public final String fleetId;
    public final String factionId;
    public final double x, y, z;

    public FleetCreatedEvent(String fleetId, String factionId, double x, double y, double z) {
        this.fleetId = fleetId;
        this.factionId = factionId;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
```

```java
package com.galacticodyssey.combat.fleet.events;

public final class FleetDestroyedEvent {
    public final String fleetId;
    public final String factionId;

    public FleetDestroyedEvent(String fleetId, String factionId) {
        this.fleetId = fleetId;
        this.factionId = factionId;
    }
}
```

```java
package com.galacticodyssey.combat.fleet.events;

import com.galacticodyssey.combat.fleet.data.FleetOrder;

public final class FleetOrderEvent {
    public final String fleetId;
    public final FleetOrder order;

    public FleetOrderEvent(String fleetId, FleetOrder order) {
        this.fleetId = fleetId;
        this.order = order;
    }
}
```

```java
package com.galacticodyssey.combat.fleet.events;

import com.galacticodyssey.combat.fleet.data.FleetDoctrine;

public final class FleetDoctrineChangedEvent {
    public final String fleetId;
    public final FleetDoctrine oldDoctrine;
    public final FleetDoctrine newDoctrine;

    public FleetDoctrineChangedEvent(String fleetId, FleetDoctrine oldDoctrine, FleetDoctrine newDoctrine) {
        this.fleetId = fleetId;
        this.oldDoctrine = oldDoctrine;
        this.newDoctrine = newDoctrine;
    }
}
```

```java
package com.galacticodyssey.combat.fleet.events;

import com.galacticodyssey.combat.fleet.data.FleetState;

public final class FleetStateChangedEvent {
    public final String fleetId;
    public final FleetState oldState;
    public final FleetState newState;

    public FleetStateChangedEvent(String fleetId, FleetState oldState, FleetState newState) {
        this.fleetId = fleetId;
        this.oldState = oldState;
        this.newState = newState;
    }
}
```

```java
package com.galacticodyssey.combat.fleet.events;

public final class FleetExpandedEvent {
    public final String fleetId;

    public FleetExpandedEvent(String fleetId) {
        this.fleetId = fleetId;
    }
}
```

```java
package com.galacticodyssey.combat.fleet.events;

public final class FleetCollapsedEvent {
    public final String fleetId;

    public FleetCollapsedEvent(String fleetId) {
        this.fleetId = fleetId;
    }
}
```

```java
package com.galacticodyssey.combat.fleet.events;

public final class FleetEngagementStartedEvent {
    public final String attackerFleetId;
    public final String defenderFleetId;
    public final double x, y, z;

    public FleetEngagementStartedEvent(String attackerFleetId, String defenderFleetId,
                                       double x, double y, double z) {
        this.attackerFleetId = attackerFleetId;
        this.defenderFleetId = defenderFleetId;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
```

```java
package com.galacticodyssey.combat.fleet.events;

import java.util.List;

public final class FleetBattleResolvedEvent {
    public final String winnerFleetId;
    public final String loserFleetId;
    public final int winnerCasualties;
    public final int loserCasualties;
    public final List<String> capturedShipIds;

    public FleetBattleResolvedEvent(String winnerFleetId, String loserFleetId,
                                    int winnerCasualties, int loserCasualties,
                                    List<String> capturedShipIds) {
        this.winnerFleetId = winnerFleetId;
        this.loserFleetId = loserFleetId;
        this.winnerCasualties = winnerCasualties;
        this.loserCasualties = loserCasualties;
        this.capturedShipIds = capturedShipIds;
    }
}
```

```java
package com.galacticodyssey.combat.fleet.events;

import com.badlogic.ashley.core.Entity;

public final class ShipDisabledEvent {
    public final Entity ship;
    public final Entity attacker;

    public ShipDisabledEvent(Entity ship, Entity attacker) {
        this.ship = ship;
        this.attacker = attacker;
    }
}
```

```java
package com.galacticodyssey.combat.fleet.events;

import com.badlogic.ashley.core.Entity;

public final class ShipCapturedEvent {
    public final Entity ship;
    public final Entity captor;
    public final String oldFactionId;

    public ShipCapturedEvent(Entity ship, Entity captor, String oldFactionId) {
        this.ship = ship;
        this.captor = captor;
        this.oldFactionId = oldFactionId;
    }
}
```

```java
package com.galacticodyssey.combat.fleet.events;

import com.badlogic.ashley.core.Entity;

public final class EscapePodLaunchedEvent {
    public final Entity pod;
    public final Entity sourceShip;

    public EscapePodLaunchedEvent(Entity pod, Entity sourceShip) {
        this.pod = pod;
        this.sourceShip = sourceShip;
    }
}
```

```java
package com.galacticodyssey.combat.fleet.events;

public final class TerritoryChangedEvent {
    public final String systemId;
    public final String oldFactionId;
    public final String newFactionId;

    public TerritoryChangedEvent(String systemId, String oldFactionId, String newFactionId) {
        this.systemId = systemId;
        this.oldFactionId = oldFactionId;
        this.newFactionId = newFactionId;
    }
}
```

```java
package com.galacticodyssey.combat.fleet.events;

public final class FactionFleetMusteredEvent {
    public final String factionId;
    public final String fleetId;

    public FactionFleetMusteredEvent(String factionId, String fleetId) {
        this.factionId = factionId;
        this.fleetId = fleetId;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava --info`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/events/
git commit -m "feat(fleet): add fleet event types

14 event classes for fleet lifecycle, orders, battles, captures,
territory changes — all immutable data carriers for the EventBus."
```

---

## Task 4: Formation Templates and Registry

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/data/FormationTemplate.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/data/FormationRegistry.java`
- Create: `core/src/main/resources/data/fleet/formation_templates.json`
- Test: `core/src/test/java/com/galacticodyssey/combat/fleet/FormationTemplateTest.java`

- [ ] **Step 1: Write FormationTemplate test**

```java
package com.galacticodyssey.combat.fleet;

import com.galacticodyssey.combat.fleet.data.FormationTemplate;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormationTemplateTest {

    @Test
    void wedgeFormationSlotZeroIsOrigin() {
        FormationTemplate wedge = FormationTemplate.wedge(20);
        Vector3 slot0 = wedge.getSlotOffset(0);
        assertEquals(0f, slot0.x, 0.01f);
        assertEquals(0f, slot0.y, 0.01f);
        assertEquals(0f, slot0.z, 0.01f);
    }

    @Test
    void slotCountMatchesRequested() {
        FormationTemplate line = FormationTemplate.line(10);
        assertEquals(10, line.slotCount());
    }

    @Test
    void lineFormationSpreadsAlongX() {
        FormationTemplate line = FormationTemplate.line(5);
        Vector3 slot0 = line.getSlotOffset(0);
        Vector3 slot4 = line.getSlotOffset(4);
        assertTrue(Math.abs(slot4.x - slot0.x) > 1f);
        assertEquals(slot0.z, slot4.z, 0.01f);
    }

    @Test
    void outOfBoundsSlotWraps() {
        FormationTemplate wedge = FormationTemplate.wedge(5);
        Vector3 slot5 = wedge.getSlotOffset(5);
        assertNotNull(slot5);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.FormationTemplateTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Write FormationTemplate**

```java
package com.galacticodyssey.combat.fleet.data;

import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
import java.util.List;

public final class FormationTemplate {
    public final String id;
    private final List<Vector3> slotOffsets;

    public FormationTemplate(String id, List<Vector3> slotOffsets) {
        this.id = id;
        this.slotOffsets = new ArrayList<>(slotOffsets);
    }

    public int slotCount() {
        return slotOffsets.size();
    }

    public Vector3 getSlotOffset(int index) {
        if (slotOffsets.isEmpty()) return new Vector3();
        return new Vector3(slotOffsets.get(index % slotOffsets.size()));
    }

    public static FormationTemplate line(int maxSlots) {
        List<Vector3> slots = new ArrayList<>();
        float spacing = 50f;
        float halfWidth = (maxSlots - 1) * spacing * 0.5f;
        for (int i = 0; i < maxSlots; i++) {
            slots.add(new Vector3(i * spacing - halfWidth, 0f, 0f));
        }
        return new FormationTemplate("line", slots);
    }

    public static FormationTemplate wedge(int maxSlots) {
        List<Vector3> slots = new ArrayList<>();
        slots.add(new Vector3(0f, 0f, 0f));
        float spacing = 60f;
        int row = 1;
        int placed = 1;
        while (placed < maxSlots) {
            float zBack = -row * spacing;
            int perRow = row + 1;
            float halfW = (perRow - 1) * spacing * 0.5f;
            for (int i = 0; i < perRow && placed < maxSlots; i++) {
                slots.add(new Vector3(i * spacing - halfW, 0f, zBack));
                placed++;
            }
            row++;
        }
        return new FormationTemplate("wedge", slots);
    }

    public static FormationTemplate box(int maxSlots) {
        List<Vector3> slots = new ArrayList<>();
        float spacing = 50f;
        int cols = (int) Math.ceil(Math.sqrt(maxSlots));
        int rows = (int) Math.ceil((double) maxSlots / cols);
        float halfW = (cols - 1) * spacing * 0.5f;
        float halfD = (rows - 1) * spacing * 0.5f;
        int placed = 0;
        for (int r = 0; r < rows && placed < maxSlots; r++) {
            for (int c = 0; c < cols && placed < maxSlots; c++) {
                slots.add(new Vector3(c * spacing - halfW, 0f, r * spacing - halfD));
                placed++;
            }
        }
        return new FormationTemplate("box", slots);
    }

    public static FormationTemplate sphere(int maxSlots) {
        List<Vector3> slots = new ArrayList<>();
        slots.add(new Vector3(0f, 0f, 0f));
        float radius = 80f;
        for (int i = 1; i < maxSlots; i++) {
            float phi = (float) (Math.acos(1 - 2.0 * i / maxSlots));
            float theta = (float) (Math.PI * (1 + Math.sqrt(5)) * i);
            float x = (float) (radius * Math.sin(phi) * Math.cos(theta));
            float y = (float) (radius * Math.sin(phi) * Math.sin(theta));
            float z = (float) (radius * Math.cos(phi));
            slots.add(new Vector3(x, y, z));
        }
        return new FormationTemplate("sphere", slots);
    }

    public static FormationTemplate wall(int maxSlots) {
        List<Vector3> slots = new ArrayList<>();
        float spacing = 45f;
        int cols = (int) Math.ceil(Math.sqrt(maxSlots));
        int rows = (int) Math.ceil((double) maxSlots / cols);
        float halfW = (cols - 1) * spacing * 0.5f;
        float halfH = (rows - 1) * spacing * 0.5f;
        int placed = 0;
        for (int r = 0; r < rows && placed < maxSlots; r++) {
            for (int c = 0; c < cols && placed < maxSlots; c++) {
                slots.add(new Vector3(c * spacing - halfW, r * spacing - halfH, 0f));
                placed++;
            }
        }
        return new FormationTemplate("wall", slots);
    }

    public static FormationTemplate scattered(int maxSlots, long seed) {
        List<Vector3> slots = new ArrayList<>();
        java.util.Random rng = new java.util.Random(seed);
        float range = 120f;
        for (int i = 0; i < maxSlots; i++) {
            slots.add(new Vector3(
                (rng.nextFloat() - 0.5f) * range * 2f,
                (rng.nextFloat() - 0.5f) * range * 0.5f,
                (rng.nextFloat() - 0.5f) * range * 2f
            ));
        }
        return new FormationTemplate("scattered", slots);
    }
}
```

- [ ] **Step 4: Write FormationRegistry**

```java
package com.galacticodyssey.combat.fleet.data;

import java.util.HashMap;
import java.util.Map;

public final class FormationRegistry {
    private final Map<String, FormationTemplate> templates = new HashMap<>();

    public void registerDefaults(int maxSlots) {
        register(FormationTemplate.line(maxSlots));
        register(FormationTemplate.wedge(maxSlots));
        register(FormationTemplate.box(maxSlots));
        register(FormationTemplate.sphere(maxSlots));
        register(FormationTemplate.wall(maxSlots));
        register(FormationTemplate.scattered(maxSlots, 42L));
    }

    public void register(FormationTemplate template) {
        templates.put(template.id, template);
    }

    public FormationTemplate get(String id) {
        return templates.get(id);
    }
}
```

- [ ] **Step 5: Write formation_templates.json**

```json
[
  { "id": "line", "description": "Ships abreast in a line — broadside engagement" },
  { "id": "wedge", "description": "V-shape, flagship at point — aggressive advance" },
  { "id": "box", "description": "Rectangular formation — balanced defense" },
  { "id": "sphere", "description": "3D sphere around flagship — all-around protection" },
  { "id": "wall", "description": "Flat plane facing enemy — maximum forward firepower" },
  { "id": "scattered", "description": "Random spread — anti-area-of-effect" }
]
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.*" --info`
Expected: All pass

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/data/FormationTemplate.java core/src/main/java/com/galacticodyssey/combat/fleet/data/FormationRegistry.java core/src/main/resources/data/fleet/formation_templates.json core/src/test/java/com/galacticodyssey/combat/fleet/FormationTemplateTest.java
git commit -m "feat(fleet): add formation templates with 6 presets

Line, wedge, box, sphere, wall, scattered formations with procedural
slot position generation and a FormationRegistry for lookup."
```

---

## Task 5: Fleet Composition Registry

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/data/FleetCompositionData.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/data/FleetCompositionRegistry.java`
- Create: `core/src/main/resources/data/fleet/fleet_compositions.json`
- Test: `core/src/test/java/com/galacticodyssey/combat/fleet/FleetCompositionRegistryTest.java`

- [ ] **Step 1: Write FleetCompositionRegistry test**

```java
package com.galacticodyssey.combat.fleet;

import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.galaxy.faction.FactionEthos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class FleetCompositionRegistryTest {

    @Test
    void registryLoadsTemplatesFromData() {
        FleetCompositionRegistry registry = new FleetCompositionRegistry();
        registry.registerDefaults();
        FleetCompositionData data = registry.getForEthos(FactionEthos.MILITARIST);
        assertNotNull(data);
        assertFalse(data.slots.isEmpty());
    }

    @Test
    void generateCompositionRespectsStrength() {
        FleetCompositionRegistry registry = new FleetCompositionRegistry();
        registry.registerDefaults();
        FleetCompositionData data = registry.getForEthos(FactionEthos.MILITARIST);

        List<FleetShipEntry> weak = data.generate(0.2f, new Random(1));
        List<FleetShipEntry> strong = data.generate(0.9f, new Random(1));

        int weakTotal = weak.stream().mapToInt(e -> e.count).sum();
        int strongTotal = strong.stream().mapToInt(e -> e.count).sum();
        assertTrue(strongTotal > weakTotal);
    }

    @Test
    void allEthosHaveCompositions() {
        FleetCompositionRegistry registry = new FleetCompositionRegistry();
        registry.registerDefaults();
        for (FactionEthos ethos : FactionEthos.values()) {
            assertNotNull(registry.getForEthos(ethos), "Missing composition for " + ethos);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.FleetCompositionRegistryTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Write FleetCompositionData**

```java
package com.galacticodyssey.combat.fleet.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class FleetCompositionData {
    public String id;
    public String factionEthos;
    public FleetDoctrine doctrineDefault;
    public final List<SlotRange> slots = new ArrayList<>();

    public static final class SlotRange {
        public FleetShipClass shipClass;
        public int minCount;
        public int maxCount;

        public SlotRange() {}

        public SlotRange(FleetShipClass shipClass, int minCount, int maxCount) {
            this.shipClass = shipClass;
            this.minCount = minCount;
            this.maxCount = maxCount;
        }
    }

    public List<FleetShipEntry> generate(float militaryStrength, Random rng) {
        List<FleetShipEntry> result = new ArrayList<>();
        for (SlotRange slot : slots) {
            int range = slot.maxCount - slot.minCount;
            int count = slot.minCount + Math.round(range * militaryStrength);
            count = Math.max(slot.minCount, Math.min(slot.maxCount,
                count + rng.nextInt(3) - 1));
            if (count > 0) {
                result.add(new FleetShipEntry(slot.shipClass, count, 1.0f));
            }
        }
        return result;
    }
}
```

- [ ] **Step 4: Write FleetCompositionRegistry**

```java
package com.galacticodyssey.combat.fleet.data;

import com.galacticodyssey.galaxy.faction.FactionEthos;
import java.util.EnumMap;
import java.util.Map;

public final class FleetCompositionRegistry {
    private final Map<FactionEthos, FleetCompositionData> compositions = new EnumMap<>(FactionEthos.class);

    public void registerDefaults() {
        register(FactionEthos.MILITARIST, militarist());
        register(FactionEthos.CORPORATE, corporate());
        register(FactionEthos.FEDERATION, federation());
        register(FactionEthos.PIRATE_SYNDICATE, pirate());
        register(FactionEthos.ISOLATIONIST, isolationist());
    }

    public void register(FactionEthos ethos, FleetCompositionData data) {
        compositions.put(ethos, data);
    }

    public FleetCompositionData getForEthos(FactionEthos ethos) {
        return compositions.get(ethos);
    }

    private static FleetCompositionData militarist() {
        FleetCompositionData d = new FleetCompositionData();
        d.id = "militarist_battle_fleet";
        d.factionEthos = "MILITARIST";
        d.doctrineDefault = FleetDoctrine.AGGRESSIVE;
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.DREADNOUGHT, 0, 1));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.BATTLESHIP, 1, 3));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CRUISER, 3, 6));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.DESTROYER, 2, 5));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FRIGATE, 4, 8));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FIGHTER, 12, 24));
        return d;
    }

    private static FleetCompositionData corporate() {
        FleetCompositionData d = new FleetCompositionData();
        d.id = "corporate_fleet";
        d.factionEthos = "CORPORATE";
        d.doctrineDefault = FleetDoctrine.BALANCED;
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.BATTLESHIP, 0, 2));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CRUISER, 3, 5));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.DESTROYER, 3, 6));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FRIGATE, 5, 10));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CORVETTE, 4, 8));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FIGHTER, 8, 16));
        return d;
    }

    private static FleetCompositionData federation() {
        FleetCompositionData d = new FleetCompositionData();
        d.id = "federation_fleet";
        d.factionEthos = "FEDERATION";
        d.doctrineDefault = FleetDoctrine.DEFENSIVE;
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CARRIER, 0, 1));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CRUISER, 4, 8));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FRIGATE, 6, 10));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CORVETTE, 3, 6));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FIGHTER, 10, 20));
        return d;
    }

    private static FleetCompositionData pirate() {
        FleetCompositionData d = new FleetCompositionData();
        d.id = "pirate_raider_pack";
        d.factionEthos = "PIRATE_SYNDICATE";
        d.doctrineDefault = FleetDoctrine.EVASIVE;
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.DESTROYER, 2, 4));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CORVETTE, 6, 12));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FIGHTER, 8, 16));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.BOMBER, 2, 4));
        return d;
    }

    private static FleetCompositionData isolationist() {
        FleetCompositionData d = new FleetCompositionData();
        d.id = "isolationist_picket";
        d.factionEthos = "ISOLATIONIST";
        d.doctrineDefault = FleetDoctrine.DEFENSIVE;
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CRUISER, 1, 3));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FRIGATE, 4, 8));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.CORVETTE, 3, 6));
        d.slots.add(new FleetCompositionData.SlotRange(FleetShipClass.FIGHTER, 10, 20));
        return d;
    }
}
```

- [ ] **Step 5: Write fleet_compositions.json**

```json
[
  {
    "id": "militarist_battle_fleet",
    "factionEthos": "MILITARIST",
    "doctrineDefault": "AGGRESSIVE",
    "slots": [
      { "shipClass": "DREADNOUGHT", "count": [0, 1] },
      { "shipClass": "BATTLESHIP", "count": [1, 3] },
      { "shipClass": "CRUISER", "count": [3, 6] },
      { "shipClass": "DESTROYER", "count": [2, 5] },
      { "shipClass": "FRIGATE", "count": [4, 8] },
      { "shipClass": "FIGHTER", "count": [12, 24] }
    ]
  },
  {
    "id": "corporate_fleet",
    "factionEthos": "CORPORATE",
    "doctrineDefault": "BALANCED",
    "slots": [
      { "shipClass": "BATTLESHIP", "count": [0, 2] },
      { "shipClass": "CRUISER", "count": [3, 5] },
      { "shipClass": "DESTROYER", "count": [3, 6] },
      { "shipClass": "FRIGATE", "count": [5, 10] },
      { "shipClass": "CORVETTE", "count": [4, 8] },
      { "shipClass": "FIGHTER", "count": [8, 16] }
    ]
  },
  {
    "id": "federation_fleet",
    "factionEthos": "FEDERATION",
    "doctrineDefault": "DEFENSIVE",
    "slots": [
      { "shipClass": "CARRIER", "count": [0, 1] },
      { "shipClass": "CRUISER", "count": [4, 8] },
      { "shipClass": "FRIGATE", "count": [6, 10] },
      { "shipClass": "CORVETTE", "count": [3, 6] },
      { "shipClass": "FIGHTER", "count": [10, 20] }
    ]
  },
  {
    "id": "pirate_raider_pack",
    "factionEthos": "PIRATE_SYNDICATE",
    "doctrineDefault": "EVASIVE",
    "slots": [
      { "shipClass": "DESTROYER", "count": [2, 4] },
      { "shipClass": "CORVETTE", "count": [6, 12] },
      { "shipClass": "FIGHTER", "count": [8, 16] },
      { "shipClass": "BOMBER", "count": [2, 4] }
    ]
  },
  {
    "id": "isolationist_picket",
    "factionEthos": "ISOLATIONIST",
    "doctrineDefault": "DEFENSIVE",
    "slots": [
      { "shipClass": "CRUISER", "count": [1, 3] },
      { "shipClass": "FRIGATE", "count": [4, 8] },
      { "shipClass": "CORVETTE", "count": [3, 6] },
      { "shipClass": "FIGHTER", "count": [10, 20] }
    ]
  }
]
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.*" --info`
Expected: All pass

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/data/FleetCompositionData.java core/src/main/java/com/galacticodyssey/combat/fleet/data/FleetCompositionRegistry.java core/src/main/resources/data/fleet/fleet_compositions.json core/src/test/java/com/galacticodyssey/combat/fleet/FleetCompositionRegistryTest.java
git commit -m "feat(fleet): add fleet composition templates per faction ethos

Data-driven fleet composition with per-ethos templates. Military strength
scales ship counts. Militarist gets capital-heavy fleets, pirates get
fast raider packs."
```

---

## Task 6: Fleet Formation System

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetFormationSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/fleet/FleetFormationSystemTest.java`

- [ ] **Step 1: Write FleetFormationSystem test**

```java
package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.systems.FleetFormationSystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetFormationSystemTest {

    private Engine engine;
    private FormationRegistry formationRegistry;
    private Entity fleetEntity;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        formationRegistry = new FormationRegistry();
        formationRegistry.registerDefaults(20);
        EventBus eventBus = new EventBus();
        engine.addSystem(new FleetFormationSystem(formationRegistry, eventBus));

        fleetEntity = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "test-fleet";
        fc.expanded = true;
        fleetEntity.add(fc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        ffc.formationTemplateId = "wedge";
        ffc.localAnchorX = 100f;
        ffc.localAnchorY = 0f;
        ffc.localAnchorZ = 200f;
        ffc.spacingScale = 1.0f;
        fleetEntity.add(ffc);
        engine.addEntity(fleetEntity);
    }

    @Test
    void shipMovesTowardFormationSlot() {
        Entity ship = new Entity();
        FleetMemberComponent fmc = new FleetMemberComponent();
        fmc.fleetEntity = fleetEntity;
        fmc.fleetId = "test-fleet";
        fmc.formationSlotIndex = 1;
        ship.add(fmc);
        TransformComponent tc = new TransformComponent();
        tc.position.set(0f, 0f, 0f);
        ship.add(tc);
        engine.addEntity(ship);

        engine.update(0.016f);

        Vector3 offset = fmc.localFormationOffset;
        assertFalse(offset.isZero(0.01f), "Formation offset should be computed");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.FleetFormationSystemTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Write FleetFormationSystem**

```java
package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.TransformComponent;

public class FleetFormationSystem extends EntitySystem {
    public static final int PRIORITY = 7;

    private static final ComponentMapper<FleetMemberComponent> MEMBER_M =
        ComponentMapper.getFor(FleetMemberComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<FleetComponent> FLEET_M =
        ComponentMapper.getFor(FleetComponent.class);
    private static final ComponentMapper<FleetFormationComponent> FORMATION_M =
        ComponentMapper.getFor(FleetFormationComponent.class);

    private static final Family MEMBER_FAMILY = Family.all(
        FleetMemberComponent.class, TransformComponent.class
    ).get();

    private final FormationRegistry formationRegistry;
    private final EventBus eventBus;
    private final Vector3 tmpOffset = new Vector3();
    private final Quaternion tmpQuat = new Quaternion();
    private Engine engine;

    public FleetFormationSystem(FormationRegistry formationRegistry, EventBus eventBus) {
        super(PRIORITY);
        this.formationRegistry = formationRegistry;
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        this.engine = null;
    }

    @Override
    public void update(float deltaTime) {
        for (Entity member : engine.getEntitiesFor(MEMBER_FAMILY)) {
            FleetMemberComponent fmc = MEMBER_M.get(member);
            if (fmc.fleetEntity == null) continue;

            FleetComponent fc = FLEET_M.get(fmc.fleetEntity);
            if (fc == null || !fc.expanded) continue;

            FleetFormationComponent ffc = FORMATION_M.get(fmc.fleetEntity);
            if (ffc == null) continue;

            FormationTemplate template = formationRegistry.get(ffc.formationTemplateId);
            if (template == null) continue;

            Vector3 slotOffset = template.getSlotOffset(fmc.formationSlotIndex);
            tmpOffset.set(slotOffset).scl(ffc.spacingScale);
            tmpQuat.setEulerAngles(ffc.headingYaw, ffc.headingPitch, 0f);
            tmpOffset.mul(tmpQuat);

            fmc.localFormationOffset.set(
                ffc.localAnchorX + tmpOffset.x,
                ffc.localAnchorY + tmpOffset.y,
                ffc.localAnchorZ + tmpOffset.z
            );
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.*" --info`
Expected: All pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetFormationSystem.java core/src/test/java/com/galacticodyssey/combat/fleet/FleetFormationSystemTest.java
git commit -m "feat(fleet): add FleetFormationSystem

Positions ships at formation slot offsets rotated by fleet heading.
Reads formation template from registry, applies spacing scale."
```

---

## Task 7: Fleet Simulation System (Off-Screen Battles)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetSimulationSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/fleet/FleetSimulationSystemTest.java`

- [ ] **Step 1: Write FleetSimulationSystem test**

```java
package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.events.FleetBattleResolvedEvent;
import com.galacticodyssey.combat.fleet.systems.FleetSimulationSystem;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class FleetSimulationSystemTest {

    private Engine engine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new FleetSimulationSystem(eventBus));
    }

    private Entity createFleet(String id, String factionId, FleetDoctrine doctrine,
                                FleetShipClass shipClass, int count) {
        Entity e = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = id;
        fc.factionId = factionId;
        fc.doctrine = doctrine;
        fc.state = FleetState.ENGAGED;
        fc.expanded = false;
        fc.composition.add(new FleetShipEntry(shipClass, count, 1.0f));
        fc.recomputeAggregates();
        e.add(fc);

        FleetTacticsComponent ftc = new FleetTacticsComponent();
        ftc.retreatThreshold = doctrine.retreatThreshold;
        ftc.threatAssessment.put(id.equals("attacker") ? "defender" : "attacker", 1.0f);
        e.add(ftc);

        FleetFormationComponent ffc = new FleetFormationComponent();
        e.add(ffc);
        return e;
    }

    @Test
    void strongerFleetWinsBattle() {
        Entity attacker = createFleet("attacker", "faction-a", FleetDoctrine.AGGRESSIVE,
            FleetShipClass.CRUISER, 10);
        Entity defender = createFleet("defender", "faction-b", FleetDoctrine.DEFENSIVE,
            FleetShipClass.FIGHTER, 5);
        engine.addEntity(attacker);
        engine.addEntity(defender);

        AtomicReference<FleetBattleResolvedEvent> result = new AtomicReference<>();
        eventBus.subscribe(FleetBattleResolvedEvent.class, result::set);

        for (int i = 0; i < 100; i++) {
            engine.update(5.0f);
            if (result.get() != null) break;
        }

        assertNotNull(result.get(), "Battle should resolve");
        assertEquals("attacker", result.get().winnerFleetId);
    }

    @Test
    void fleetRetreatsAtThreshold() {
        Entity strong = createFleet("attacker", "faction-a", FleetDoctrine.AGGRESSIVE,
            FleetShipClass.BATTLESHIP, 5);
        Entity weak = createFleet("defender", "faction-b", FleetDoctrine.DEFENSIVE,
            FleetShipClass.CORVETTE, 8);
        engine.addEntity(strong);
        engine.addEntity(weak);

        AtomicReference<FleetBattleResolvedEvent> result = new AtomicReference<>();
        eventBus.subscribe(FleetBattleResolvedEvent.class, result::set);

        for (int i = 0; i < 100; i++) {
            engine.update(5.0f);
            if (result.get() != null) break;
        }

        assertNotNull(result.get());
        assertEquals("defender", result.get().loserFleetId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.FleetSimulationSystemTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Write FleetSimulationSystem**

```java
package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.events.*;
import com.galacticodyssey.core.EventBus;
import java.util.ArrayList;
import java.util.List;

public class FleetSimulationSystem extends EntitySystem {
    public static final int PRIORITY = 2;
    private static final float TICK_INTERVAL = 5.0f;

    private static final ComponentMapper<FleetComponent> FLEET_M =
        ComponentMapper.getFor(FleetComponent.class);
    private static final ComponentMapper<FleetTacticsComponent> TACTICS_M =
        ComponentMapper.getFor(FleetTacticsComponent.class);

    private static final Family FLEET_FAMILY = Family.all(
        FleetComponent.class, FleetTacticsComponent.class, FleetFormationComponent.class
    ).get();

    private final EventBus eventBus;
    private float accumulator;
    private Engine engine;

    public FleetSimulationSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        this.engine = null;
    }

    @Override
    public void update(float deltaTime) {
        accumulator += deltaTime;
        if (accumulator < TICK_INTERVAL) return;
        accumulator -= TICK_INTERVAL;

        List<Entity> engagedFleets = new ArrayList<>();
        for (Entity e : engine.getEntitiesFor(FLEET_FAMILY)) {
            FleetComponent fc = FLEET_M.get(e);
            if (fc.state == FleetState.ENGAGED && !fc.expanded) {
                engagedFleets.add(e);
            }
        }

        for (int i = 0; i < engagedFleets.size(); i++) {
            Entity a = engagedFleets.get(i);
            FleetComponent fcA = FLEET_M.get(a);
            FleetTacticsComponent tcA = TACTICS_M.get(a);

            for (int j = i + 1; j < engagedFleets.size(); j++) {
                Entity b = engagedFleets.get(j);
                FleetComponent fcB = FLEET_M.get(b);
                FleetTacticsComponent tcB = TACTICS_M.get(b);

                if (!tcA.threatAssessment.containsKey(fcB.fleetId) &&
                    !tcB.threatAssessment.containsKey(fcA.fleetId)) {
                    continue;
                }

                resolveRound(a, fcA, tcA, b, fcB, tcB);
            }
        }
    }

    private void resolveRound(Entity a, FleetComponent fcA, FleetTacticsComponent tcA,
                              Entity b, FleetComponent fcB, FleetTacticsComponent tcB) {
        float damageToB = fcA.aggregateFirepower * fcA.doctrine.damageDealtModifier
                        * fcB.doctrine.damageTakenModifier;
        float damageToA = fcB.aggregateFirepower * fcB.doctrine.damageDealtModifier
                        * fcA.doctrine.damageTakenModifier;

        int casualtiesA = applyDamage(fcA, damageToA);
        int casualtiesB = applyDamage(fcB, damageToB);
        fcA.recomputeAggregates();
        fcB.recomputeAggregates();

        boolean aRetreats = fcA.lossRatio() >= tcA.retreatThreshold;
        boolean bRetreats = fcB.lossRatio() >= tcB.retreatThreshold;

        if (aRetreats || bRetreats) {
            String winnerId, loserId;
            int winnerCasualties, loserCasualties;
            if (aRetreats && !bRetreats) {
                winnerId = fcB.fleetId; loserId = fcA.fleetId;
                winnerCasualties = casualtiesB; loserCasualties = casualtiesA;
                fcA.state = FleetState.RETREATING;
                fcB.state = FleetState.PATROL;
            } else if (bRetreats && !aRetreats) {
                winnerId = fcA.fleetId; loserId = fcB.fleetId;
                winnerCasualties = casualtiesA; loserCasualties = casualtiesB;
                fcB.state = FleetState.RETREATING;
                fcA.state = FleetState.PATROL;
            } else {
                winnerId = fcA.aggregateFirepower >= fcB.aggregateFirepower ? fcA.fleetId : fcB.fleetId;
                loserId = winnerId.equals(fcA.fleetId) ? fcB.fleetId : fcA.fleetId;
                winnerCasualties = winnerId.equals(fcA.fleetId) ? casualtiesA : casualtiesB;
                loserCasualties = winnerId.equals(fcA.fleetId) ? casualtiesB : casualtiesA;
                fcA.state = FleetState.RETREATING;
                fcB.state = FleetState.RETREATING;
            }
            eventBus.publish(new FleetBattleResolvedEvent(
                winnerId, loserId, winnerCasualties, loserCasualties, List.of()));
        }
    }

    private int applyDamage(FleetComponent fc, float damage) {
        int totalCasualties = 0;
        float remaining = damage;
        for (int i = 0; i < fc.composition.size() && remaining > 0; i++) {
            FleetShipEntry entry = fc.composition.get(i);
            float hpPerShip = entry.shipClass.baseHullHp * entry.avgHpRatio;
            if (hpPerShip <= 0) continue;
            int killed = Math.min(entry.count, (int) (remaining / hpPerShip));
            if (killed > 0) {
                entry.count -= killed;
                remaining -= killed * hpPerShip;
                totalCasualties += killed;
            }
            if (remaining > 0 && entry.count > 0) {
                float hpLost = remaining / (entry.count * entry.shipClass.baseHullHp);
                entry.avgHpRatio = Math.max(0.1f, entry.avgHpRatio - hpLost);
                remaining = 0;
            }
        }
        fc.composition.removeIf(e -> e.count <= 0);
        return totalCasualties;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.*" --info`
Expected: All pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetSimulationSystem.java core/src/test/java/com/galacticodyssey/combat/fleet/FleetSimulationSystemTest.java
git commit -m "feat(fleet): add off-screen fleet battle simulation

FleetSimulationSystem ticks every 5s, resolves combat rounds between
collapsed engaged fleets using aggregate firepower and doctrine modifiers.
Publishes FleetBattleResolvedEvent when a fleet retreats."
```

---

## Task 8: Fleet Command System

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetCommandSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/fleet/FleetCommandSystemTest.java`

- [ ] **Step 1: Write FleetCommandSystem test**

```java
package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.events.*;
import com.galacticodyssey.combat.fleet.systems.FleetCommandSystem;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetCommandSystemTest {

    private Engine engine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new FleetCommandSystem(eventBus));
    }

    @Test
    void orderEventEnqueuesOnFleet() {
        Entity fleet = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "fleet-1";
        fc.expanded = true;
        fc.state = FleetState.ENGAGED;
        fleet.add(fc);

        FleetTacticsComponent ftc = new FleetTacticsComponent();
        fleet.add(ftc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        fleet.add(ffc);
        engine.addEntity(fleet);

        FleetOrder order = FleetOrder.retreat();
        eventBus.publish(new FleetOrderEvent("fleet-1", order));

        engine.update(1.0f);

        assertEquals(FleetState.RETREATING, fc.state);
    }

    @Test
    void doctrineChangePublishesEvent() {
        Entity fleet = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "fleet-1";
        fc.doctrine = FleetDoctrine.BALANCED;
        fc.expanded = true;
        fleet.add(fc);
        FleetTacticsComponent ftc = new FleetTacticsComponent();
        fleet.add(ftc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        fleet.add(ffc);
        engine.addEntity(fleet);

        FleetOrder order = new FleetOrder(FleetOrderType.SET_FORMATION, null, 0, 0, 0, "line", null);
        eventBus.publish(new FleetOrderEvent("fleet-1", order));

        engine.update(1.0f);

        assertEquals("line", ffc.formationTemplateId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.FleetCommandSystemTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Write FleetCommandSystem**

```java
package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.events.*;
import com.galacticodyssey.core.EventBus;
import java.util.ArrayList;
import java.util.List;

public class FleetCommandSystem extends EntitySystem {
    public static final int PRIORITY = 5;
    private static final float TICK_INTERVAL = 1.0f;

    private static final ComponentMapper<FleetComponent> FLEET_M =
        ComponentMapper.getFor(FleetComponent.class);
    private static final ComponentMapper<FleetTacticsComponent> TACTICS_M =
        ComponentMapper.getFor(FleetTacticsComponent.class);
    private static final ComponentMapper<FleetFormationComponent> FORMATION_M =
        ComponentMapper.getFor(FleetFormationComponent.class);

    private static final Family FLEET_FAMILY = Family.all(
        FleetComponent.class, FleetTacticsComponent.class, FleetFormationComponent.class
    ).get();

    private final EventBus eventBus;
    private final List<FleetOrderEvent> pendingOrders = new ArrayList<>();
    private float accumulator;
    private Engine engine;

    public FleetCommandSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(FleetOrderEvent.class, pendingOrders::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        this.engine = null;
    }

    @Override
    public void update(float deltaTime) {
        if (!pendingOrders.isEmpty()) {
            List<FleetOrderEvent> orders = new ArrayList<>(pendingOrders);
            pendingOrders.clear();
            for (FleetOrderEvent evt : orders) {
                processOrder(evt);
            }
        }

        accumulator += deltaTime;
        if (accumulator < TICK_INTERVAL) return;
        accumulator -= TICK_INTERVAL;

        for (Entity e : engine.getEntitiesFor(FLEET_FAMILY)) {
            FleetComponent fc = FLEET_M.get(e);
            if (!fc.expanded) continue;

            FleetTacticsComponent tc = TACTICS_M.get(e);
            while (!tc.orders.isEmpty()) {
                FleetOrder order = tc.orders.poll();
                executeOrder(e, fc, tc, order);
            }
        }
    }

    private void processOrder(FleetOrderEvent evt) {
        for (Entity e : engine.getEntitiesFor(FLEET_FAMILY)) {
            FleetComponent fc = FLEET_M.get(e);
            if (fc.fleetId.equals(evt.fleetId)) {
                FleetTacticsComponent tc = TACTICS_M.get(e);
                tc.orders.add(evt.order);
                break;
            }
        }
    }

    private void executeOrder(Entity fleetEntity, FleetComponent fc,
                              FleetTacticsComponent tc, FleetOrder order) {
        switch (order.type) {
            case RETREAT:
                FleetState oldState = fc.state;
                fc.state = FleetState.RETREATING;
                eventBus.publish(new FleetStateChangedEvent(fc.fleetId, oldState, fc.state));
                break;
            case SET_FORMATION:
                if (order.formationTemplateId != null) {
                    FleetFormationComponent ffc = FORMATION_M.get(fleetEntity);
                    if (ffc != null) {
                        ffc.formationTemplateId = order.formationTemplateId;
                    }
                }
                break;
            case HOLD_POSITION:
                fc.state = FleetState.ENGAGED;
                break;
            case ATTACK_TARGET:
                fc.state = FleetState.ENGAGED;
                break;
            case ADVANCE:
                fc.state = FleetState.INTERCEPT;
                break;
            case REGROUP:
                fc.state = FleetState.REGROUPING;
                break;
            case MOVE_TO:
                break;
            case ESCORT_SHIP:
                break;
            case LAUNCH_FIGHTERS:
                break;
            case RECALL_FIGHTERS:
                break;
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.*" --info`
Expected: All pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetCommandSystem.java core/src/test/java/com/galacticodyssey/combat/fleet/FleetCommandSystemTest.java
git commit -m "feat(fleet): add FleetCommandSystem for order processing

Listens for FleetOrderEvent on EventBus, routes orders to the matching
fleet's tactics component. Executes orders each tick — retreat, formation
changes, state transitions."
```

---

## Task 9: Squadron Coordination System

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/systems/SquadronCoordinationSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/fleet/SquadronCoordinationSystemTest.java`

- [ ] **Step 1: Write SquadronCoordinationSystem test**

```java
package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.systems.SquadronCoordinationSystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SquadronCoordinationSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Entity fleetEntity;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new SquadronCoordinationSystem(eventBus));

        fleetEntity = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "test-fleet";
        fc.expanded = true;
        fc.state = FleetState.ENGAGED;
        fleetEntity.add(fc);
        FleetTacticsComponent ftc = new FleetTacticsComponent();
        fleetEntity.add(ftc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        fleetEntity.add(ffc);
        engine.addEntity(fleetEntity);
    }

    private Entity createShip(int squadronIndex) {
        Entity ship = new Entity();
        FleetMemberComponent fmc = new FleetMemberComponent();
        fmc.fleetEntity = fleetEntity;
        fmc.fleetId = "test-fleet";
        fmc.squadronIndex = squadronIndex;
        ship.add(fmc);
        CombatAIComponent ai = new CombatAIComponent();
        ship.add(ai);
        HealthComponent hp = new HealthComponent();
        ship.add(hp);
        TransformComponent tc = new TransformComponent();
        ship.add(tc);
        return ship;
    }

    @Test
    void squadronMembersShareTarget() {
        Entity ship1 = createShip(0);
        Entity ship2 = createShip(0);
        Entity enemy = new Entity();
        HealthComponent enemyHp = new HealthComponent();
        enemy.add(enemyHp);
        TransformComponent enemyTc = new TransformComponent();
        enemy.add(enemyTc);

        CombatAIComponent ai1 = ship1.getComponent(CombatAIComponent.class);
        ai1.currentTarget = enemy;

        engine.addEntity(ship1);
        engine.addEntity(ship2);
        engine.addEntity(enemy);

        engine.update(0.5f);

        CombatAIComponent ai2 = ship2.getComponent(CombatAIComponent.class);
        assertEquals(enemy, ai2.currentTarget);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.SquadronCoordinationSystemTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Write SquadronCoordinationSystem**

```java
package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.utils.IntMap;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.TransformComponent;
import java.util.ArrayList;
import java.util.List;

public class SquadronCoordinationSystem extends EntitySystem {
    public static final int PRIORITY = 6;
    private static final float TICK_INTERVAL = 0.5f;

    private static final ComponentMapper<FleetMemberComponent> MEMBER_M =
        ComponentMapper.getFor(FleetMemberComponent.class);
    private static final ComponentMapper<CombatAIComponent> AI_M =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);

    private static final Family MEMBER_FAMILY = Family.all(
        FleetMemberComponent.class, CombatAIComponent.class,
        HealthComponent.class, TransformComponent.class
    ).get();

    private final EventBus eventBus;
    private final IntMap<List<Entity>> squadronGroups = new IntMap<>();
    private float accumulator;
    private Engine engine;

    public SquadronCoordinationSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        this.engine = null;
    }

    @Override
    public void update(float deltaTime) {
        accumulator += deltaTime;
        if (accumulator < TICK_INTERVAL) return;
        accumulator -= TICK_INTERVAL;

        squadronGroups.clear();
        for (Entity e : engine.getEntitiesFor(MEMBER_FAMILY)) {
            FleetMemberComponent fmc = MEMBER_M.get(e);
            HealthComponent hp = HEALTH_M.get(e);
            if (hp == null || !hp.alive) continue;

            int key = computeGroupKey(fmc);
            List<Entity> group = squadronGroups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                squadronGroups.put(key, group);
            }
            group.add(e);
        }

        for (IntMap.Entry<List<Entity>> entry : squadronGroups) {
            coordinateSquadron(entry.value);
        }
    }

    private int computeGroupKey(FleetMemberComponent fmc) {
        return (fmc.fleetId != null ? fmc.fleetId.hashCode() : 0) * 31 + fmc.squadronIndex;
    }

    private void coordinateSquadron(List<Entity> members) {
        Entity bestTarget = null;
        float highestThreat = -1f;

        for (Entity member : members) {
            CombatAIComponent ai = AI_M.get(member);
            if (ai.currentTarget != null) {
                HealthComponent targetHp = HEALTH_M.get(ai.currentTarget);
                if (targetHp != null && targetHp.alive && ai.threatLevel > highestThreat) {
                    bestTarget = ai.currentTarget;
                    highestThreat = ai.threatLevel;
                }
            }
        }

        if (bestTarget != null) {
            for (Entity member : members) {
                CombatAIComponent ai = AI_M.get(member);
                if (ai.currentTarget == null) {
                    ai.currentTarget = bestTarget;
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.*" --info`
Expected: All pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/systems/SquadronCoordinationSystem.java core/src/test/java/com/galacticodyssey/combat/fleet/SquadronCoordinationSystemTest.java
git commit -m "feat(fleet): add SquadronCoordinationSystem for focus fire

Groups ships by fleet+squadron index, shares targets within the squadron
so all members focus fire on the highest-threat target."
```

---

## Task 10: Fleet LOD System

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetLODSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/fleet/FleetLODSystemTest.java`

- [ ] **Step 1: Write FleetLODSystem test**

```java
package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.systems.FleetLODSystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.TransformComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetLODSystemTest {

    @Test
    void nearbyShipGetsFullLOD() {
        Engine engine = new Engine();
        Vector3 playerPos = new Vector3(0f, 0f, 0f);
        engine.addSystem(new FleetLODSystem(new EventBus(), () -> playerPos));

        Entity ship = createShip(100f, 0f, 0f);
        engine.addEntity(ship);

        engine.update(1.0f);

        FleetMemberComponent fmc = ship.getComponent(FleetMemberComponent.class);
        assertEquals(FleetMemberComponent.LODTier.FULL, fmc.lodTier);
    }

    @Test
    void distantShipGetsAbstractLOD() {
        Engine engine = new Engine();
        Vector3 playerPos = new Vector3(0f, 0f, 0f);
        engine.addSystem(new FleetLODSystem(new EventBus(), () -> playerPos));

        Entity ship = createShip(30000f, 0f, 0f);
        engine.addEntity(ship);

        engine.update(1.0f);

        FleetMemberComponent fmc = ship.getComponent(FleetMemberComponent.class);
        assertEquals(FleetMemberComponent.LODTier.ABSTRACT, fmc.lodTier);
    }

    private Entity createShip(float x, float y, float z) {
        Entity ship = new Entity();
        FleetMemberComponent fmc = new FleetMemberComponent();
        fmc.fleetId = "fleet-1";
        ship.add(fmc);
        TransformComponent tc = new TransformComponent();
        tc.position.set(x, y, z);
        ship.add(tc);
        CombatAIComponent ai = new CombatAIComponent();
        ship.add(ai);
        HealthComponent hp = new HealthComponent();
        ship.add(hp);
        return ship;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.FleetLODSystemTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Add lodTier field to FleetMemberComponent**

Add to `FleetMemberComponent.java`:

```java
public FleetLODSystem.LODTier lodTier = FleetLODSystem.LODTier.FULL;
```

Since this creates a circular dependency, define LODTier separately. Instead, put the enum on FleetMemberComponent:

Add the field and a nested enum to `FleetMemberComponent.java`:

```java
// Add at the top of the class, after existing fields:
public LODTier lodTier = LODTier.FULL;

public enum LODTier {
    FULL, SIMPLIFIED, ABSTRACT
}
```

Then update the snapshot methods to include lodTier:

In `takeSnapshot()` add: `s.lodTier = lodTier.name();`
In `restoreFromSnapshot()` add: `lodTier = LODTier.valueOf(s.lodTier);`
Add to `FleetMemberSnapshot`: `public String lodTier;`

- [ ] **Step 4: Write FleetLODSystem**

```java
package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.components.FleetMemberComponent;
import com.galacticodyssey.combat.fleet.components.FleetMemberComponent.LODTier;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.TransformComponent;
import java.util.function.Supplier;

public class FleetLODSystem extends EntitySystem {
    public static final int PRIORITY = 8;
    private static final float TICK_INTERVAL = 1.0f;

    private static final float FULL_RANGE = 2000f;
    private static final float SIMPLIFIED_RANGE = 10000f;

    private static final ComponentMapper<FleetMemberComponent> MEMBER_M =
        ComponentMapper.getFor(FleetMemberComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private static final Family MEMBER_FAMILY = Family.all(
        FleetMemberComponent.class, TransformComponent.class
    ).get();

    @SuppressWarnings("unused")
    private final EventBus eventBus;
    private final Supplier<Vector3> playerPositionSupplier;
    private float accumulator;
    private Engine engine;

    public FleetLODSystem(EventBus eventBus, Supplier<Vector3> playerPositionSupplier) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.playerPositionSupplier = playerPositionSupplier;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        this.engine = null;
    }

    @Override
    public void update(float deltaTime) {
        accumulator += deltaTime;
        if (accumulator < TICK_INTERVAL) return;
        accumulator -= TICK_INTERVAL;

        Vector3 playerPos = playerPositionSupplier.get();

        for (Entity e : engine.getEntitiesFor(MEMBER_FAMILY)) {
            FleetMemberComponent fmc = MEMBER_M.get(e);
            TransformComponent tc = TRANSFORM_M.get(e);
            float dist = playerPos.dst(tc.position);

            LODTier newTier;
            if (dist < FULL_RANGE) {
                newTier = LODTier.FULL;
            } else if (dist < SIMPLIFIED_RANGE) {
                newTier = LODTier.SIMPLIFIED;
            } else {
                newTier = LODTier.ABSTRACT;
            }
            fmc.lodTier = newTier;
        }
    }

}
```

LODTier lives on FleetMemberComponent to avoid circular dependencies. Here is the corrected FleetLODSystem:

```java
package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.components.FleetMemberComponent;
import com.galacticodyssey.combat.fleet.components.FleetMemberComponent.LODTier;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.player.components.TransformComponent;
import java.util.function.Supplier;

public class FleetLODSystem extends EntitySystem {
    public static final int PRIORITY = 8;
    private static final float TICK_INTERVAL = 1.0f;

    private static final float FULL_RANGE = 2000f;
    private static final float SIMPLIFIED_RANGE = 10000f;

    private static final ComponentMapper<FleetMemberComponent> MEMBER_M =
        ComponentMapper.getFor(FleetMemberComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);

    private static final Family MEMBER_FAMILY = Family.all(
        FleetMemberComponent.class, TransformComponent.class
    ).get();

    private final EventBus eventBus;
    private final Supplier<Vector3> playerPositionSupplier;
    private float accumulator;
    private Engine engine;

    public FleetLODSystem(EventBus eventBus, Supplier<Vector3> playerPositionSupplier) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.playerPositionSupplier = playerPositionSupplier;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        this.engine = null;
    }

    @Override
    public void update(float deltaTime) {
        accumulator += deltaTime;
        if (accumulator < TICK_INTERVAL) return;
        accumulator -= TICK_INTERVAL;

        Vector3 playerPos = playerPositionSupplier.get();

        for (Entity e : engine.getEntitiesFor(MEMBER_FAMILY)) {
            FleetMemberComponent fmc = MEMBER_M.get(e);
            TransformComponent tc = TRANSFORM_M.get(e);
            float dist = playerPos.dst(tc.position);

            if (dist < FULL_RANGE) {
                fmc.lodTier = LODTier.FULL;
            } else if (dist < SIMPLIFIED_RANGE) {
                fmc.lodTier = LODTier.SIMPLIFIED;
            } else {
                fmc.lodTier = LODTier.ABSTRACT;
            }
        }
    }
}
```

The test above already uses `FleetMemberComponent.LODTier` — no further changes needed.

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.*" --info`
Expected: All pass

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetLODSystem.java core/src/main/java/com/galacticodyssey/combat/fleet/components/FleetMemberComponent.java core/src/main/java/com/galacticodyssey/persistence/snapshots/FleetMemberSnapshot.java core/src/test/java/com/galacticodyssey/combat/fleet/FleetLODSystemTest.java
git commit -m "feat(fleet): add FleetLODSystem for distance-based AI tiers

LODTier enum (FULL/SIMPLIFIED/ABSTRACT) on FleetMemberComponent.
FleetLODSystem checks distance to player every 1s and assigns tiers
at 2km/10km boundaries."
```

---

## Task 11: Fleet Expansion System

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetExpansionSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/fleet/FleetExpansionSystemTest.java`

- [ ] **Step 1: Write FleetExpansionSystem test**

```java
package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.systems.FleetExpansionSystem;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetExpansionSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private Vector3 playerPos;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        playerPos = new Vector3(0f, 0f, 0f);
        FormationRegistry formationRegistry = new FormationRegistry();
        formationRegistry.registerDefaults(50);
        engine.addSystem(new FleetExpansionSystem(eventBus, () -> playerPos, formationRegistry));
    }

    @Test
    void expandsFleetWhenPlayerIsNearby() {
        Entity fleet = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "fleet-1";
        fc.factionId = "faction-a";
        fc.expanded = false;
        fc.state = FleetState.PATROL;
        fc.composition.add(new FleetShipEntry(FleetShipClass.FIGHTER, 3, 1.0f));
        fc.recomputeAggregates();
        fleet.add(fc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        ffc.localAnchorX = 100f;
        fleet.add(ffc);
        FleetTacticsComponent ftc = new FleetTacticsComponent();
        fleet.add(ftc);
        engine.addEntity(fleet);

        playerPos.set(500f, 0f, 0f);
        engine.update(1.0f);

        assertTrue(fc.expanded);
        int shipCount = engine.getEntitiesFor(
            Family.all(FleetMemberComponent.class).get()).size();
        assertEquals(3, shipCount);
    }

    @Test
    void collapsesFleetWhenPlayerIsFar() {
        Entity fleet = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "fleet-2";
        fc.factionId = "faction-a";
        fc.expanded = true;
        fc.state = FleetState.PATROL;
        fc.composition.add(new FleetShipEntry(FleetShipClass.FIGHTER, 2, 1.0f));
        fc.recomputeAggregates();
        fleet.add(fc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        ffc.localAnchorX = 0f;
        fleet.add(ffc);
        FleetTacticsComponent ftc = new FleetTacticsComponent();
        fleet.add(ftc);
        engine.addEntity(fleet);

        Entity ship1 = createShipFor("fleet-2", fleet);
        Entity ship2 = createShipFor("fleet-2", fleet);
        engine.addEntity(ship1);
        engine.addEntity(ship2);

        playerPos.set(100000f, 0f, 0f);
        engine.update(1.0f);

        assertFalse(fc.expanded);
        int shipCount = engine.getEntitiesFor(
            Family.all(FleetMemberComponent.class).get()).size();
        assertEquals(0, shipCount);
    }

    private Entity createShipFor(String fleetId, Entity fleetEntity) {
        Entity ship = new Entity();
        FleetMemberComponent fmc = new FleetMemberComponent();
        fmc.fleetId = fleetId;
        fmc.fleetEntity = fleetEntity;
        ship.add(fmc);
        return ship;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.FleetExpansionSystemTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Write FleetExpansionSystem**

```java
package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.events.*;
import com.galacticodyssey.core.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class FleetExpansionSystem extends EntitySystem {
    public static final int PRIORITY = 3;
    private static final float EXPAND_RANGE = 50000f;
    private static final float COLLAPSE_RANGE = 60000f;

    private static final ComponentMapper<FleetComponent> FLEET_M =
        ComponentMapper.getFor(FleetComponent.class);
    private static final ComponentMapper<FleetFormationComponent> FORMATION_M =
        ComponentMapper.getFor(FleetFormationComponent.class);
    private static final ComponentMapper<FleetMemberComponent> MEMBER_M =
        ComponentMapper.getFor(FleetMemberComponent.class);

    private static final Family FLEET_FAMILY = Family.all(
        FleetComponent.class, FleetFormationComponent.class, FleetTacticsComponent.class
    ).get();

    private static final Family MEMBER_FAMILY = Family.all(
        FleetMemberComponent.class
    ).get();

    private final EventBus eventBus;
    private final Supplier<Vector3> playerPositionSupplier;
    private final FormationRegistry formationRegistry;
    private Engine engine;

    public FleetExpansionSystem(EventBus eventBus, Supplier<Vector3> playerPositionSupplier,
                                FormationRegistry formationRegistry) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.playerPositionSupplier = playerPositionSupplier;
        this.formationRegistry = formationRegistry;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        this.engine = null;
    }

    @Override
    public void update(float deltaTime) {
        Vector3 playerPos = playerPositionSupplier.get();

        for (Entity fleetEntity : engine.getEntitiesFor(FLEET_FAMILY)) {
            FleetComponent fc = FLEET_M.get(fleetEntity);
            FleetFormationComponent ffc = FORMATION_M.get(fleetEntity);
            float dist = playerPos.dst(ffc.localAnchorX, ffc.localAnchorY, ffc.localAnchorZ);

            if (!fc.expanded && dist < EXPAND_RANGE) {
                expandFleet(fleetEntity, fc, ffc);
            } else if (fc.expanded && dist > COLLAPSE_RANGE) {
                collapseFleet(fleetEntity, fc);
            }
        }
    }

    private void expandFleet(Entity fleetEntity, FleetComponent fc, FleetFormationComponent ffc) {
        FormationTemplate template = formationRegistry.get(ffc.formationTemplateId);
        int slotIndex = 0;
        int squadronIndex = 0;
        int inSquadron = 0;

        for (FleetShipEntry entry : fc.composition) {
            for (int i = 0; i < entry.count; i++) {
                Entity ship = new Entity();

                FleetMemberComponent fmc = new FleetMemberComponent();
                fmc.fleetEntity = fleetEntity;
                fmc.fleetId = fc.fleetId;
                fmc.squadronIndex = squadronIndex;
                fmc.role = entry.shipClass.defaultRole;
                fmc.formationSlotIndex = slotIndex;
                ship.add(fmc);

                engine.addEntity(ship);
                slotIndex++;
                inSquadron++;
                if (inSquadron >= 4) {
                    squadronIndex++;
                    inSquadron = 0;
                }
            }
        }

        fc.expanded = true;
        eventBus.publish(new FleetExpandedEvent(fc.fleetId));
    }

    private void collapseFleet(Entity fleetEntity, FleetComponent fc) {
        List<Entity> toRemove = new ArrayList<>();
        for (Entity e : engine.getEntitiesFor(MEMBER_FAMILY)) {
            FleetMemberComponent fmc = MEMBER_M.get(e);
            if (fc.fleetId.equals(fmc.fleetId)) {
                toRemove.add(e);
            }
        }
        for (Entity e : toRemove) {
            engine.removeEntity(e);
        }

        fc.expanded = false;
        eventBus.publish(new FleetCollapsedEvent(fc.fleetId));
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.*" --info`
Expected: All pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetExpansionSystem.java core/src/test/java/com/galacticodyssey/combat/fleet/FleetExpansionSystemTest.java
git commit -m "feat(fleet): add FleetExpansionSystem for expand/collapse

Expands collapsed fleets into individual ship entities when player is
within 50km. Collapses back when player moves beyond 60km. Assigns
squadron indices and formation slots on expansion."
```

---

## Task 12: Fleet Post-Battle System

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetPostBattleSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/fleet/FleetPostBattleSystemTest.java`

- [ ] **Step 1: Write FleetPostBattleSystem test**

```java
package com.galacticodyssey.combat.fleet;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.combat.fleet.systems.FleetPostBattleSystem;
import com.galacticodyssey.core.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleetPostBattleSystemTest {

    private Engine engine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        engine = new Engine();
        eventBus = new EventBus();
        engine.addSystem(new FleetPostBattleSystem(eventBus));
    }

    @Test
    void autoRepairRestoresHullOverTime() {
        Entity fleet = new Entity();
        FleetComponent fc = new FleetComponent();
        fc.fleetId = "fleet-1";
        fc.expanded = true;
        fc.state = FleetState.REGROUPING;
        fleet.add(fc);
        FleetTacticsComponent ftc = new FleetTacticsComponent();
        fleet.add(ftc);
        FleetFormationComponent ffc = new FleetFormationComponent();
        fleet.add(ffc);
        engine.addEntity(fleet);

        Entity ship = new Entity();
        FleetMemberComponent fmc = new FleetMemberComponent();
        fmc.fleetId = "fleet-1";
        fmc.fleetEntity = fleet;
        ship.add(fmc);
        HealthComponent hp = new HealthComponent();
        hp.maxHP = 1000f;
        hp.currentHP = 500f;
        ship.add(hp);
        engine.addEntity(ship);

        for (int i = 0; i < 60; i++) {
            engine.update(1.0f);
        }

        assertTrue(hp.currentHP > 500f, "HP should have increased from auto-repair");
        assertTrue(hp.currentHP <= hp.maxHP, "HP should not exceed max");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.FleetPostBattleSystemTest" --info`
Expected: FAIL — class not found

- [ ] **Step 3: Write FleetPostBattleSystem**

```java
package com.galacticodyssey.combat.fleet.systems;

import com.badlogic.ashley.core.*;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.core.EventBus;

public class FleetPostBattleSystem extends EntitySystem {
    public static final int PRIORITY = 15;
    private static final float REPAIR_RATE = 0.5f / 60f;

    private static final ComponentMapper<FleetComponent> FLEET_M =
        ComponentMapper.getFor(FleetComponent.class);
    private static final ComponentMapper<FleetMemberComponent> MEMBER_M =
        ComponentMapper.getFor(FleetMemberComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);

    private static final Family MEMBER_FAMILY = Family.all(
        FleetMemberComponent.class, HealthComponent.class
    ).get();

    private final EventBus eventBus;
    private Engine engine;

    public FleetPostBattleSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        this.engine = null;
    }

    @Override
    public void update(float deltaTime) {
        for (Entity e : engine.getEntitiesFor(MEMBER_FAMILY)) {
            FleetMemberComponent fmc = MEMBER_M.get(e);
            if (fmc.fleetEntity == null) continue;

            FleetComponent fc = FLEET_M.get(fmc.fleetEntity);
            if (fc == null) continue;
            if (fc.state != FleetState.REGROUPING && fc.state != FleetState.PATROL) continue;

            HealthComponent hp = HEALTH_M.get(e);
            if (hp.currentHP < hp.maxHP) {
                float missing = hp.maxHP - hp.currentHP;
                float repair = missing * REPAIR_RATE * deltaTime;
                hp.currentHP = Math.min(hp.maxHP, hp.currentHP + repair);
            }
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.*" --info`
Expected: All pass

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetPostBattleSystem.java core/src/test/java/com/galacticodyssey/combat/fleet/FleetPostBattleSystemTest.java
git commit -m "feat(fleet): add FleetPostBattleSystem for auto-repair

Ships in REGROUPING or PATROL fleets slowly repair — restoring 50%
of missing HP over 60 seconds."
```

---

## Task 13: Admiral Behavior Tree

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/fleet/ai/AdmiralBehaviorTree.java`
- No test — behavior tree is tested via FleetCommandSystem integration

- [ ] **Step 1: Write AdmiralBehaviorTree**

```java
package com.galacticodyssey.combat.fleet.ai;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.fleet.components.*;
import com.galacticodyssey.combat.fleet.data.*;
import com.galacticodyssey.core.EventBus;

public final class AdmiralBehaviorTree {

    private static final ComponentMapper<FleetComponent> FLEET_M =
        ComponentMapper.getFor(FleetComponent.class);
    private static final ComponentMapper<FleetTacticsComponent> TACTICS_M =
        ComponentMapper.getFor(FleetTacticsComponent.class);
    private static final ComponentMapper<FleetMemberComponent> MEMBER_M =
        ComponentMapper.getFor(FleetMemberComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);

    private AdmiralBehaviorTree() {}

    public static void evaluate(Entity fleetEntity, Engine engine, EventBus eventBus) {
        FleetComponent fc = FLEET_M.get(fleetEntity);
        FleetTacticsComponent tc = TACTICS_M.get(fleetEntity);
        if (fc == null || tc == null) return;
        if (fc.state == FleetState.RETREATING || fc.state == FleetState.REGROUPING) return;

        float lossRatio = computeLossRatio(fc, engine);
        if (lossRatio >= tc.retreatThreshold) {
            tc.orders.add(FleetOrder.retreat());
            return;
        }

        float enemyStrength = computeEnemyStrength(tc);
        float strengthRatio = enemyStrength > 0 ? fc.aggregateFirepower / enemyStrength : 10f;

        if (strengthRatio >= fc.doctrine.engageStrengthRatio) {
            if (fc.state != FleetState.ENGAGED) {
                fc.state = FleetState.ENGAGED;
            }
        }
    }

    private static float computeLossRatio(FleetComponent fc, Engine engine) {
        if (!fc.expanded) return fc.lossRatio();

        int alive = 0, total = 0;
        for (Entity e : engine.getEntitiesFor(
                Family.all(FleetMemberComponent.class, HealthComponent.class).get())) {
            FleetMemberComponent fmc = MEMBER_M.get(e);
            if (!fc.fleetId.equals(fmc.fleetId)) continue;
            total++;
            HealthComponent hp = HEALTH_M.get(e);
            if (hp.alive) alive++;
        }
        if (total == 0) return 1f;
        return 1f - ((float) alive / total);
    }

    private static float computeEnemyStrength(FleetTacticsComponent tc) {
        float total = 0f;
        for (float threat : tc.threatAssessment.values()) {
            total += threat;
        }
        return total;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :core:compileJava --info`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/ai/AdmiralBehaviorTree.java
git commit -m "feat(fleet): add AdmiralBehaviorTree for fleet-level AI

Evaluates retreat thresholds and engagement decisions based on doctrine.
Called by FleetCommandSystem each tick for NPC fleet admirals."
```

---

## Task 14: Integration — Wire FleetCommandSystem to Admiral AI

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetCommandSystem.java`

- [ ] **Step 1: Add admiral AI evaluation to FleetCommandSystem update loop**

In `FleetCommandSystem.update()`, after processing pending orders and before the existing order execution loop, add:

```java
// After the accumulator check, before the order execution loop:
for (Entity e : engine.getEntitiesFor(FLEET_FAMILY)) {
    FleetComponent fc = FLEET_M.get(e);
    if (!fc.expanded) continue;
    if (fc.admiralEntity != null) {
        AdmiralBehaviorTree.evaluate(e, engine, eventBus);
    }
}
```

Add import: `import com.galacticodyssey.combat.fleet.ai.AdmiralBehaviorTree;`

- [ ] **Step 2: Run tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.*" --info`
Expected: All pass

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/fleet/systems/FleetCommandSystem.java
git commit -m "feat(fleet): wire admiral AI into FleetCommandSystem

NPC fleets with an admiralEntity now run AdmiralBehaviorTree.evaluate()
each tick to make retreat and engagement decisions."
```

---

## Task 15: Full Test Suite Verification

- [ ] **Step 1: Run all fleet tests**

Run: `./gradlew :core:test --tests "com.galacticodyssey.combat.fleet.*" --info`
Expected: All pass

- [ ] **Step 2: Run full project test suite**

Run: `./gradlew test --info`
Expected: All pass (2 pre-existing failures in shipSpawnIs75mEastOfPlayer and staminaDrainsWhileSprinting are known and unrelated)

- [ ] **Step 3: Verify compilation of entire project**

Run: `./gradlew :core:compileJava :desktop:compileJava --info`
Expected: BUILD SUCCESSFUL
