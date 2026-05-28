# Boarding Pipeline — Plan A: Subsystems, EMP Disable & Orchestrator Foundation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make enemy ships disablable — add targetable ship subsystems (engines/shields/weapons/life-support), an EMP soft-disable mechanic, a ship-scale damage seam, the boarding-operation state component, and the orchestrator that flips a disabled ship into the `VULNERABLE` (boardable) state.

**Architecture:** Approach C from the design spec. A `BoardingOperationComponent` holds shared operation state (lives on the *target* ship, added lazily). `ShipSubsystemsComponent` holds functional subsystem health, distinct from the physical `StructuralIntegrityComponent`. A new `ShipDamageEvent` is the ship-scale analog of `DamageDealtEvent`; `ShipSubsystemSystem` consumes it to apply hull + subsystem damage and EMP timers, publishing `SubsystemDisabledEvent`. `BoardingOrchestratorSystem` reacts to engine-disable and advances the phase. `ShipFlightSystem` gains an engines-operational guard so a disabled ship coasts.

**Tech Stack:** Java 17, libGDX, Ashley ECS, gdx-bullet, JUnit 5. Build: Gradle (`./gradlew core:test`).

**Spec:** [docs/superpowers/specs/2026-05-28-ship-boarding-pipeline-design.md](../specs/2026-05-28-ship-boarding-pipeline-design.md)

---

## File Structure

New package: `com.galacticodyssey.ship.boarding` (components + systems) and
`com.galacticodyssey.ship.boarding.events` (events).

**Create:**
- `core/src/main/java/com/galacticodyssey/ship/boarding/ShipSubsystemsComponent.java` — functional subsystem health (engines/shields/weapons/life-support), EMP timers, `enginesOperational()`.
- `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOperationComponent.java` — the operation state machine data (phase, aggressor/target, attach method, entry point, playerIsAggressor).
- `core/src/main/java/com/galacticodyssey/ship/boarding/events/ShipDamageEvent.java` — ship-scale damage signal (target, attacker, damage, damageType, hit position).
- `core/src/main/java/com/galacticodyssey/ship/boarding/events/SubsystemDisabledEvent.java` — a subsystem went non-operational.
- `core/src/main/java/com/galacticodyssey/ship/boarding/events/ShipBoardableEvent.java` — target entered VULNERABLE.
- `core/src/main/java/com/galacticodyssey/ship/boarding/systems/ShipSubsystemSystem.java` — consumes `ShipDamageEvent`, applies hull + subsystem damage + EMP, publishes `SubsystemDisabledEvent`.
- `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingOrchestratorSystem.java` — phase-transition rules.
- `core/src/main/java/com/galacticodyssey/ship/boarding/systems/ShipProjectileImpactSystem.java` — detects projectile↔ship proximity, publishes `ShipDamageEvent`.
- `core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipSubsystemsSnapshot.java` — persistence DTO.
- `core/src/main/java/com/galacticodyssey/persistence/snapshots/BoardingOperationSnapshot.java` — persistence DTO.

**Modify:**
- `core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java` — engines-operational guard.
- `core/src/main/java/com/galacticodyssey/ship/ShipFactory.java` — attach a default `ShipSubsystemsComponent` to spawned ships.
- `core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java` — register the two new snapshot components.
- `core/src/main/java/com/galacticodyssey/core/GameWorld.java` — register the three new systems.

**Test:**
- `core/src/test/java/com/galacticodyssey/ship/boarding/ShipSubsystemsComponentTest.java`
- `core/src/test/java/com/galacticodyssey/ship/boarding/ShipSubsystemSystemTest.java`
- `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingOrchestratorSystemTest.java`
- `core/src/test/java/com/galacticodyssey/ship/boarding/ShipProjectileImpactSystemTest.java`
- `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightEnginesGuardTest.java`

---

## Task 1: `ShipSubsystemsComponent` (data + behavior)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/ShipSubsystemsComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/ShipSubsystemsComponentTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipSubsystemsComponentTest {

    @Test
    void newComponentHasAllSubsystemsOperational() {
        ShipSubsystemsComponent c = new ShipSubsystemsComponent();
        c.initDefaults(100f);
        assertTrue(c.enginesOperational());
        assertEquals(100f, c.get(SubsystemType.ENGINES).health, 0.001f);
        assertNotNull(c.get(SubsystemType.SHIELDS));
        assertNotNull(c.get(SubsystemType.WEAPONS));
        assertNotNull(c.get(SubsystemType.LIFE_SUPPORT));
    }

    @Test
    void enginesNotOperationalWhenHealthZero() {
        ShipSubsystemsComponent c = new ShipSubsystemsComponent();
        c.initDefaults(100f);
        c.get(SubsystemType.ENGINES).health = 0f;
        assertFalse(c.enginesOperational());
    }

    @Test
    void enginesNotOperationalWhileEmpTimerActive() {
        ShipSubsystemsComponent c = new ShipSubsystemsComponent();
        c.initDefaults(100f);
        c.get(SubsystemType.ENGINES).empDisableTimer = 3f;
        assertFalse(c.enginesOperational());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.ShipSubsystemsComponentTest`
Expected: FAIL — `ShipSubsystemsComponent` does not exist (compile error).

- [ ] **Step 3: Write minimal implementation**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.ShipSubsystemsSnapshot;

import java.util.EnumMap;
import java.util.Map;

/**
 * Functional health of a ship's targetable subsystems. Distinct from
 * {@link com.galacticodyssey.ship.structure.StructuralIntegrityComponent}, which models
 * physical hull zones/pressure — this models whether a system still works.
 */
public class ShipSubsystemsComponent implements Component, Snapshotable<ShipSubsystemsSnapshot> {

    public enum SubsystemType { ENGINES, SHIELDS, WEAPONS, LIFE_SUPPORT }

    public static final class Subsystem {
        public float health;
        public float maxHealth;
        /** Seconds of remaining EMP soft-disable. > 0 means temporarily offline. */
        public float empDisableTimer;
        public boolean destroyed;
    }

    public final EnumMap<SubsystemType, Subsystem> subsystems = new EnumMap<>(SubsystemType.class);

    /** Populate all subsystems at full health. Call once when a ship is built. */
    public void initDefaults(float healthPerSubsystem) {
        for (SubsystemType type : SubsystemType.values()) {
            Subsystem s = new Subsystem();
            s.health = healthPerSubsystem;
            s.maxHealth = healthPerSubsystem;
            s.empDisableTimer = 0f;
            s.destroyed = false;
            subsystems.put(type, s);
        }
    }

    public Subsystem get(SubsystemType type) {
        return subsystems.get(type);
    }

    public boolean enginesOperational() {
        Subsystem engines = subsystems.get(SubsystemType.ENGINES);
        return engines != null && engines.health > 0f && engines.empDisableTimer <= 0f;
    }

    @Override
    public ShipSubsystemsSnapshot takeSnapshot() {
        ShipSubsystemsSnapshot snap = new ShipSubsystemsSnapshot();
        for (Map.Entry<SubsystemType, Subsystem> e : subsystems.entrySet()) {
            ShipSubsystemsSnapshot.Entry se = new ShipSubsystemsSnapshot.Entry();
            se.type = e.getKey().name();
            se.health = e.getValue().health;
            se.maxHealth = e.getValue().maxHealth;
            se.empDisableTimer = e.getValue().empDisableTimer;
            se.destroyed = e.getValue().destroyed;
            snap.entries.add(se);
        }
        return snap;
    }

    @Override
    public void restoreFromSnapshot(ShipSubsystemsSnapshot snap) {
        subsystems.clear();
        for (ShipSubsystemsSnapshot.Entry se : snap.entries) {
            Subsystem s = new Subsystem();
            s.health = se.health;
            s.maxHealth = se.maxHealth;
            s.empDisableTimer = se.empDisableTimer;
            s.destroyed = se.destroyed;
            subsystems.put(SubsystemType.valueOf(se.type), s);
        }
    }
}
```

Also create the snapshot DTO it references:

`core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipSubsystemsSnapshot.java`
```java
package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class ShipSubsystemsSnapshot {
    public static class Entry {
        public String type;
        public float health;
        public float maxHealth;
        public float empDisableTimer;
        public boolean destroyed;
        public Entry() {}
    }
    public final List<Entry> entries = new ArrayList<>();
    public ShipSubsystemsSnapshot() {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.ShipSubsystemsComponentTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/ShipSubsystemsComponent.java core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipSubsystemsSnapshot.java core/src/test/java/com/galacticodyssey/ship/boarding/ShipSubsystemsComponentTest.java
git commit -m "feat(boarding): add ShipSubsystemsComponent with EMP-aware engine state"
```

---

## Task 2: `BoardingOperationComponent` (state machine data)

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOperationComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/BoardingOperationSnapshot.java`
- Test: covered by `BoardingOrchestratorSystemTest` in Task 6 (this task is plain data + snapshot; no standalone behavior test needed).

- [ ] **Step 1: Write the component**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.BoardingOperationSnapshot;

import java.util.UUID;

/**
 * Shared state for a single boarding operation. Lives on the boarding TARGET ship
 * (added lazily when the ship is first disabled), so the same systems run whether the
 * player or an NPC is the aggressor.
 */
public class BoardingOperationComponent implements Component, Snapshotable<BoardingOperationSnapshot> {

    public enum BoardingPhase {
        NONE,
        DISABLING,
        VULNERABLE,
        ATTACHING,
        BREACHED,
        INTERIOR_COMBAT,
        RESOLVING,
        RESOLVED
    }

    public enum AttachMethod { CLAMP, BREACH_POD }

    public BoardingPhase phase = BoardingPhase.NONE;
    public Entity aggressorShip;
    public Entity targetShip;
    public AttachMethod attachMethod;
    public Entity entryPoint;
    public boolean playerIsAggressor;

    // Persisted entity references (resolved by ReferenceResolver on load).
    public UUID aggressorShipId;
    public UUID targetShipId;
    public UUID entryPointId;

    @Override
    public BoardingOperationSnapshot takeSnapshot() {
        BoardingOperationSnapshot s = new BoardingOperationSnapshot();
        s.phase = phase.name();
        s.attachMethod = attachMethod == null ? null : attachMethod.name();
        s.playerIsAggressor = playerIsAggressor;
        s.aggressorShipId = aggressorShipId;
        s.targetShipId = targetShipId;
        s.entryPointId = entryPointId;
        return s;
    }

    @Override
    public void restoreFromSnapshot(BoardingOperationSnapshot s) {
        phase = BoardingPhase.valueOf(s.phase);
        attachMethod = s.attachMethod == null ? null : AttachMethod.valueOf(s.attachMethod);
        playerIsAggressor = s.playerIsAggressor;
        aggressorShipId = s.aggressorShipId;
        targetShipId = s.targetShipId;
        entryPointId = s.entryPointId;
        // Entity references resolved later by ReferenceResolver.
    }
}
```

- [ ] **Step 2: Write the snapshot DTO**

`core/src/main/java/com/galacticodyssey/persistence/snapshots/BoardingOperationSnapshot.java`
```java
package com.galacticodyssey.persistence.snapshots;

import java.util.UUID;

public class BoardingOperationSnapshot {
    public String phase;
    public String attachMethod;
    public boolean playerIsAggressor;
    public UUID aggressorShipId;
    public UUID targetShipId;
    public UUID entryPointId;
    public BoardingOperationSnapshot() {}
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOperationComponent.java core/src/main/java/com/galacticodyssey/persistence/snapshots/BoardingOperationSnapshot.java
git commit -m "feat(boarding): add BoardingOperationComponent state machine data"
```

---

## Task 3: Boarding events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/events/ShipDamageEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/events/SubsystemDisabledEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/events/ShipBoardableEvent.java`

- [ ] **Step 1: Write `ShipDamageEvent`**

```java
package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;

/** A ship has taken weapon damage at a world-space hit position. */
public final class ShipDamageEvent {
    public final Entity target;
    public final Entity attacker;
    public final float damage;
    public final DamageType damageType;
    public final Vector3 hitPosition;

    public ShipDamageEvent(Entity target, Entity attacker, float damage,
                           DamageType damageType, Vector3 hitPosition) {
        this.target = target;
        this.attacker = attacker;
        this.damage = damage;
        this.damageType = damageType;
        this.hitPosition = new Vector3(hitPosition);
    }
}
```

- [ ] **Step 2: Write `SubsystemDisabledEvent`**

```java
package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;

/** A ship subsystem became non-operational (destroyed or EMP-disabled). */
public final class SubsystemDisabledEvent {
    public final Entity ship;
    public final SubsystemType subsystem;

    public SubsystemDisabledEvent(Entity ship, SubsystemType subsystem) {
        this.ship = ship;
        this.subsystem = subsystem;
    }
}
```

- [ ] **Step 3: Write `ShipBoardableEvent`**

```java
package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;

/** A ship has become boardable (engines disabled → VULNERABLE phase). */
public final class ShipBoardableEvent {
    public final Entity ship;
    public final Entity aggressor;

    public ShipBoardableEvent(Entity ship, Entity aggressor) {
        this.ship = ship;
        this.aggressor = aggressor;
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/events/
git commit -m "feat(boarding): add ShipDamage, SubsystemDisabled, ShipBoardable events"
```

---

## Task 4: `ShipSubsystemSystem` — apply damage, tick EMP, publish disable

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/ShipSubsystemSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/ShipSubsystemSystemTest.java`

Hit-position → subsystem mapping uses the ship-local Z of the hit relative to the ship
transform: aft (most negative local Z) = ENGINES, fore = WEAPONS, mid = SHIELDS, else
LIFE_SUPPORT. For Plan A the mapping is intentionally simple and fully unit-testable; a
richer hardpoint-geometry mapping can replace `resolveSubsystem` later without touching
callers.

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import com.galacticodyssey.ship.boarding.events.ShipDamageEvent;
import com.galacticodyssey.ship.boarding.events.SubsystemDisabledEvent;
import com.galacticodyssey.ship.boarding.systems.ShipSubsystemSystem;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipSubsystemSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity ship;
    private ShipSubsystemsComponent subsystems;
    private final List<SubsystemDisabledEvent> disabled = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new ShipSubsystemSystem(eventBus));

        ship = new Entity();
        TransformComponent tc = new TransformComponent();
        tc.position.set(0, 0, 0);
        ship.add(tc);

        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 500f;
        data.currentHullHp = 500f;
        ship.add(data);

        subsystems = new ShipSubsystemsComponent();
        subsystems.initDefaults(100f);
        ship.add(subsystems);

        engine.addEntity(ship);
        eventBus.subscribe(SubsystemDisabledEvent.class, disabled::add);
    }

    @Test
    void kineticAftHitDamagesEnginesAndHull() {
        eventBus.publish(new ShipDamageEvent(ship, null, 40f,
            DamageType.BALLISTIC, new Vector3(0, 0, -10)));
        engine.update(0.016f);
        assertEquals(60f, subsystems.get(SubsystemType.ENGINES).health, 0.01f);
        assertEquals(460f, ship.getComponent(ShipDataComponent.class).currentHullHp, 0.01f);
        assertTrue(disabled.isEmpty());
    }

    @Test
    void destroyingEnginesPublishesDisabledEvent() {
        eventBus.publish(new ShipDamageEvent(ship, null, 120f,
            DamageType.BALLISTIC, new Vector3(0, 0, -10)));
        engine.update(0.016f);
        assertEquals(0f, subsystems.get(SubsystemType.ENGINES).health, 0.01f);
        assertFalse(subsystems.enginesOperational());
        assertEquals(1, disabled.size());
        assertEquals(SubsystemType.ENGINES, disabled.get(0).subsystem);
    }

    @Test
    void empAftHitDisablesEnginesWithoutDestroying() {
        eventBus.publish(new ShipDamageEvent(ship, null, 30f,
            DamageType.EMP, new Vector3(0, 0, -10)));
        engine.update(0.016f);
        assertEquals(100f, subsystems.get(SubsystemType.ENGINES).health, 0.01f,
            "EMP must not reduce health");
        assertTrue(subsystems.get(SubsystemType.ENGINES).empDisableTimer > 0f);
        assertFalse(subsystems.enginesOperational());
        assertEquals(1, disabled.size());
    }

    @Test
    void empTimerRecoversOverTime() {
        eventBus.publish(new ShipDamageEvent(ship, null, 1f,
            DamageType.EMP, new Vector3(0, 0, -10)));
        engine.update(0.016f); // applies EMP, sets timer
        float timer = subsystems.get(SubsystemType.ENGINES).empDisableTimer;
        assertTrue(timer > 0f);
        // Advance well past the timer.
        engine.update(timer + 1f);
        assertTrue(subsystems.enginesOperational(), "engines recover after EMP expires");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.ShipSubsystemSystemTest`
Expected: FAIL — `ShipSubsystemSystem` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.Subsystem;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import com.galacticodyssey.ship.boarding.events.ShipDamageEvent;
import com.galacticodyssey.ship.boarding.events.SubsystemDisabledEvent;
import com.galacticodyssey.ship.components.ShipDataComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumes {@link ShipDamageEvent}: applies hull damage, routes the hit to a subsystem,
 * applies EMP soft-disable timers, and ticks those timers down each frame. Publishes
 * {@link SubsystemDisabledEvent} the moment a subsystem transitions to non-operational.
 */
public class ShipSubsystemSystem extends EntitySystem {

    public static final int PRIORITY = 9;

    /** Seconds of engine lockout per EMP hit (capped). */
    private static final float EMP_DURATION_PER_HIT = 4f;
    private static final float EMP_MAX_TIMER = 12f;

    private static final ComponentMapper<ShipSubsystemsComponent> SUB_M =
        ComponentMapper.getFor(ShipSubsystemsComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<ShipDataComponent> DATA_M =
        ComponentMapper.getFor(ShipDataComponent.class);

    private final EventBus eventBus;
    private final List<ShipDamageEvent> pending = new ArrayList<>();
    private ImmutableArray<Entity> empTickEntities;

    public ShipSubsystemSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(ShipDamageEvent.class, pending::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        empTickEntities = engine.getEntitiesFor(Family.all(ShipSubsystemsComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        empTickEntities = null;
    }

    @Override
    public void update(float deltaTime) {
        // 1. Apply queued damage.
        for (int i = 0; i < pending.size(); i++) {
            applyDamage(pending.get(i));
        }
        pending.clear();

        // 2. Tick EMP timers; recovery does not re-publish disable events.
        if (empTickEntities != null) {
            for (int i = 0, n = empTickEntities.size(); i < n; i++) {
                ShipSubsystemsComponent c = SUB_M.get(empTickEntities.get(i));
                for (Subsystem s : c.subsystems.values()) {
                    if (s.empDisableTimer > 0f) {
                        s.empDisableTimer = Math.max(0f, s.empDisableTimer - deltaTime);
                    }
                }
            }
        }
    }

    private void applyDamage(ShipDamageEvent event) {
        ShipSubsystemsComponent subs = SUB_M.get(event.target);
        if (subs == null) return;

        // Hull damage (EMP does ~no hull damage).
        if (event.damageType != DamageType.EMP) {
            ShipDataComponent data = DATA_M.get(event.target);
            if (data != null) {
                data.currentHullHp = Math.max(0f, data.currentHullHp - event.damage);
            }
        }

        SubsystemType type = resolveSubsystem(event.target, event.hitPosition);
        Subsystem sub = subs.get(type);
        if (sub == null) return;

        boolean wasOperational = isOperational(sub);

        if (event.damageType == DamageType.EMP) {
            sub.empDisableTimer = Math.min(EMP_MAX_TIMER, sub.empDisableTimer + EMP_DURATION_PER_HIT);
        } else {
            sub.health = Math.max(0f, sub.health - event.damage);
            if (sub.health <= 0f) sub.destroyed = true;
        }

        boolean nowOperational = isOperational(sub);
        if (wasOperational && !nowOperational) {
            eventBus.publish(new SubsystemDisabledEvent(event.target, type));
        }
    }

    private static boolean isOperational(Subsystem s) {
        return s.health > 0f && s.empDisableTimer <= 0f;
    }

    /**
     * Map a world-space hit to a subsystem by the hit's ship-local Z.
     * Aft (forward is -Z in this engine) = ENGINES, fore = WEAPONS, mid = SHIELDS.
     */
    private SubsystemType resolveSubsystem(Entity ship, Vector3 hitWorld) {
        TransformComponent tc = TRANSFORM_M.get(ship);
        float localZ = tc == null ? 0f : (hitWorld.z - tc.position.z);
        if (localZ < -3f) return SubsystemType.ENGINES;   // aft
        if (localZ > 3f) return SubsystemType.WEAPONS;     // fore
        return SubsystemType.SHIELDS;                      // midships
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.ShipSubsystemSystemTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/systems/ShipSubsystemSystem.java core/src/test/java/com/galacticodyssey/ship/boarding/ShipSubsystemSystemTest.java
git commit -m "feat(boarding): ShipSubsystemSystem applies hull/subsystem/EMP damage"
```

---

## Task 5: `ShipFlightSystem` engines-operational guard

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightEnginesGuardTest.java`

- [ ] **Step 1: Write the failing test**

This test asserts the *guard logic* directly via a small static helper, so it does not need
a full Bullet body. We add `ShipFlightSystem.canThrust(Entity ship)` and test it.

```java
package com.galacticodyssey.ship.systems;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipFlightEnginesGuardTest {

    @Test
    void shipWithNoSubsystemsCanThrust() {
        Entity ship = new Entity();
        assertTrue(ShipFlightSystem.canThrust(ship));
    }

    @Test
    void shipWithOperationalEnginesCanThrust() {
        Entity ship = new Entity();
        ShipSubsystemsComponent subs = new ShipSubsystemsComponent();
        subs.initDefaults(100f);
        ship.add(subs);
        assertTrue(ShipFlightSystem.canThrust(ship));
    }

    @Test
    void shipWithDisabledEnginesCannotThrust() {
        Entity ship = new Entity();
        ShipSubsystemsComponent subs = new ShipSubsystemsComponent();
        subs.initDefaults(100f);
        subs.get(SubsystemType.ENGINES).empDisableTimer = 3f;
        ship.add(subs);
        assertFalse(ShipFlightSystem.canThrust(ship));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.systems.ShipFlightEnginesGuardTest`
Expected: FAIL — `canThrust` not defined.

- [ ] **Step 3: Add the helper and wire the guard**

Add this static method to `ShipFlightSystem` (anywhere in the class body):

```java
    /** False when the ship has subsystems and its engines are non-operational. */
    public static boolean canThrust(Entity ship) {
        com.galacticodyssey.ship.boarding.ShipSubsystemsComponent subs =
            ship.getComponent(com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.class);
        return subs == null || subs.enginesOperational();
    }
```

Then in `update(float deltaTime)`, immediately after the existing line that resolves the
ship and its flight component (the block that reads
`Entity ship = state.currentShip; ... ShipFlightComponent flight = flightMapper.get(ship);`
and its null guard `if (physics == null || physics.body == null || flight == null) return;`),
insert:

```java
        // Engines disabled (destroyed or EMP) → ship coasts, ignore pilot thrust/turn input.
        if (!canThrust(ship)) {
            return;
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.systems.ShipFlightEnginesGuardTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/systems/ShipFlightSystem.java core/src/test/java/com/galacticodyssey/ship/systems/ShipFlightEnginesGuardTest.java
git commit -m "feat(boarding): disabled engines zero ship thrust (coast)"
```

---

## Task 6: `BoardingOrchestratorSystem` — disable → VULNERABLE

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingOrchestratorSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingOrchestratorSystemTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import com.galacticodyssey.ship.boarding.events.ShipBoardableEvent;
import com.galacticodyssey.ship.boarding.events.SubsystemDisabledEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingOrchestratorSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardingOrchestratorSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity ship;
    private final List<ShipBoardableEvent> boardable = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingOrchestratorSystem(eventBus));
        ship = new Entity();
        engine.addEntity(ship);
        eventBus.subscribe(ShipBoardableEvent.class, boardable::add);
    }

    @Test
    void enginesDisabledLazilyAddsOperationAndGoesVulnerable() {
        eventBus.publish(new SubsystemDisabledEvent(ship, SubsystemType.ENGINES));
        engine.update(0.016f);

        BoardingOperationComponent op = ship.getComponent(BoardingOperationComponent.class);
        assertNotNull(op, "operation component added lazily on engine disable");
        assertEquals(BoardingPhase.VULNERABLE, op.phase);
        assertEquals(1, boardable.size());
        assertSame(ship, boardable.get(0).ship);
    }

    @Test
    void nonEngineSubsystemDoesNotMakeBoardable() {
        eventBus.publish(new SubsystemDisabledEvent(ship, SubsystemType.WEAPONS));
        engine.update(0.016f);
        assertNull(ship.getComponent(BoardingOperationComponent.class));
        assertTrue(boardable.isEmpty());
    }

    @Test
    void alreadyVulnerableDoesNotRepublish() {
        eventBus.publish(new SubsystemDisabledEvent(ship, SubsystemType.ENGINES));
        engine.update(0.016f);
        eventBus.publish(new SubsystemDisabledEvent(ship, SubsystemType.ENGINES));
        engine.update(0.016f);
        assertEquals(1, boardable.size(), "VULNERABLE is entered only once");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingOrchestratorSystemTest`
Expected: FAIL — `BoardingOrchestratorSystem` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import com.galacticodyssey.ship.boarding.events.ShipBoardableEvent;
import com.galacticodyssey.ship.boarding.events.SubsystemDisabledEvent;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Owns boarding phase-transition rules only. In Plan A it handles the single transition
 * NONE/absent → VULNERABLE when a ship's ENGINES are disabled, lazily attaching a
 * {@link BoardingOperationComponent} to the target. Later plans add ATTACHING..RESOLVED.
 */
public class BoardingOrchestratorSystem extends EntitySystem {

    public static final int PRIORITY = 1;

    private final EventBus eventBus;
    private final Queue<SubsystemDisabledEvent> pending = new ArrayDeque<>();

    public BoardingOrchestratorSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(SubsystemDisabledEvent.class, pending::add);
    }

    @Override
    public void update(float deltaTime) {
        SubsystemDisabledEvent event;
        while ((event = pending.poll()) != null) {
            if (event.subsystem != SubsystemType.ENGINES) continue;
            onEnginesDisabled(event.ship);
        }
    }

    private void onEnginesDisabled(Entity ship) {
        BoardingOperationComponent op = ship.getComponent(BoardingOperationComponent.class);
        if (op == null) {
            op = new BoardingOperationComponent();
            op.targetShip = ship;
            ship.add(op);
        }
        if (op.phase == BoardingPhase.NONE || op.phase == BoardingPhase.DISABLING) {
            op.phase = BoardingPhase.VULNERABLE;
            // aggressor is wired in Plan B (from the attach context); null is acceptable here.
            eventBus.publish(new ShipBoardableEvent(ship, op.aggressorShip));
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingOrchestratorSystemTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingOrchestratorSystem.java core/src/test/java/com/galacticodyssey/ship/boarding/BoardingOrchestratorSystemTest.java
git commit -m "feat(boarding): orchestrator flips disabled ships to VULNERABLE"
```

---

## Task 7: `ShipProjectileImpactSystem` — produce `ShipDamageEvent` in-game

This wires real ship projectiles (spawned by `ShipProjectileSystem`) to actually damage ships,
which is currently unimplemented (`currentHullHp` is never decremented). Detection is simple
sphere proximity: a projectile within `impactRadius` of a ship it does not own publishes a
`ShipDamageEvent` and is removed.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/ShipProjectileImpactSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/ShipProjectileImpactSystemTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.events.ShipDamageEvent;
import com.galacticodyssey.ship.boarding.systems.ShipProjectileImpactSystem;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShipProjectileImpactSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity ship;
    private final List<ShipDamageEvent> hits = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new ShipProjectileImpactSystem(eventBus));

        ship = new Entity();
        TransformComponent tc = new TransformComponent();
        tc.position.set(0, 0, 0);
        ship.add(tc);
        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 500f; data.currentHullHp = 500f;
        ship.add(data);
        engine.addEntity(ship);

        eventBus.subscribe(ShipDamageEvent.class, hits::add);
    }

    private Entity projectileAt(float x, float y, float z, Entity owner) {
        Entity p = new Entity();
        TransformComponent tc = new TransformComponent();
        tc.position.set(x, y, z);
        p.add(tc);
        ProjectileComponent pc = new ProjectileComponent();
        pc.damage = 25f;
        pc.damageType = DamageType.BALLISTIC;
        pc.owner = owner;
        p.add(pc);
        engine.addEntity(p);
        return p;
    }

    @Test
    void projectileTouchingShipPublishesDamageAndIsRemoved() {
        Entity p = projectileAt(0, 0, 1f, null); // within default impact radius
        int before = engine.getEntities().size();
        engine.update(0.016f);
        assertEquals(1, hits.size());
        assertEquals(25f, hits.get(0).damage, 0.01f);
        assertEquals(DamageType.BALLISTIC, hits.get(0).damageType);
        assertEquals(before - 1, engine.getEntities().size(), "projectile consumed on impact");
    }

    @Test
    void distantProjectileDoesNotHit() {
        projectileAt(0, 0, 500f, null);
        engine.update(0.016f);
        assertTrue(hits.isEmpty());
    }

    @Test
    void projectileDoesNotHitItsOwner() {
        projectileAt(0, 0, 1f, ship); // owner == ship
        engine.update(0.016f);
        assertTrue(hits.isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.ShipProjectileImpactSystemTest`
Expected: FAIL — `ShipProjectileImpactSystem` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.events.ShipDamageEvent;
import com.galacticodyssey.ship.components.ShipDataComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Sphere-proximity collision between active projectiles and ships. On contact with a ship
 * the projectile does not own, publishes a {@link ShipDamageEvent} and removes the projectile.
 * This is the in-game producer of ship damage (no ship-vs-ship hull damage existed before).
 */
public class ShipProjectileImpactSystem extends EntitySystem {

    public static final int PRIORITY = 8;

    /** Coarse hull radius for impact in metres. Replace with per-hull bounds later. */
    private static final float IMPACT_RADIUS = 8f;

    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<ProjectileComponent> PROJ_M =
        ComponentMapper.getFor(ProjectileComponent.class);

    private final EventBus eventBus;
    private ImmutableArray<Entity> projectiles;
    private ImmutableArray<Entity> ships;
    private final List<Entity> toRemove = new ArrayList<>();

    public ShipProjectileImpactSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    public void addedToEngine(Engine engine) {
        projectiles = engine.getEntitiesFor(
            Family.all(ProjectileComponent.class, TransformComponent.class).get());
        ships = engine.getEntitiesFor(
            Family.all(ShipDataComponent.class, TransformComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        projectiles = null;
        ships = null;
    }

    @Override
    public void update(float deltaTime) {
        if (projectiles == null || ships == null) return;
        toRemove.clear();

        for (int i = 0, n = projectiles.size(); i < n; i++) {
            Entity proj = projectiles.get(i);
            ProjectileComponent pc = PROJ_M.get(proj);
            Vector3 ppos = TRANSFORM_M.get(proj).position;

            for (int j = 0, m = ships.size(); j < m; j++) {
                Entity ship = ships.get(j);
                if (pc.owner == ship) continue;
                Vector3 spos = TRANSFORM_M.get(ship).position;
                if (ppos.dst2(spos) <= IMPACT_RADIUS * IMPACT_RADIUS) {
                    eventBus.publish(new ShipDamageEvent(
                        ship, pc.owner, pc.damage, pc.damageType, ppos));
                    toRemove.add(proj);
                    break;
                }
            }
        }

        for (int i = 0; i < toRemove.size(); i++) {
            getEngine().removeEntity(toRemove.get(i));
        }
    }
}
```

> **Note:** If `ProjectileComponent` lacks a `damageType` or `owner` field, check
> `core/src/main/java/com/galacticodyssey/combat/components/ProjectileComponent.java` — the
> design assumes both exist (ShipProjectileSystem sets `pc.damageType` and `pc.owner`). They do.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.ShipProjectileImpactSystemTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/systems/ShipProjectileImpactSystem.java core/src/test/java/com/galacticodyssey/ship/boarding/ShipProjectileImpactSystemTest.java
git commit -m "feat(boarding): projectile-vs-ship impact publishes ShipDamageEvent"
```

---

## Task 8: Attach `ShipSubsystemsComponent` to spawned ships

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/ShipFactory.java`

`ShipFactory` builds ships in two places (around lines 150 and 244 — each ends with an
`entity.add(...)` for the ship data component). Add a default subsystems component in both.

- [ ] **Step 1: Add the import**

At the top of `ShipFactory.java` with the other `com.galacticodyssey.ship...` imports:

```java
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent;
```

- [ ] **Step 2: Add the component in the first build path**

Immediately after the line `entity.add(shipData);` (near line 152), insert:

```java
        ShipSubsystemsComponent subsystems = new ShipSubsystemsComponent();
        subsystems.initDefaults(shipData.hullHp * 0.25f);
        entity.add(subsystems);
```

- [ ] **Step 3: Add the component in the second build path**

Immediately after the line `entity.add(data);` (near line 246), insert:

```java
        ShipSubsystemsComponent subsystems2 = new ShipSubsystemsComponent();
        subsystems2.initDefaults(data.hullHp * 0.25f);
        entity.add(subsystems2);
```

- [ ] **Step 4: Verify it compiles and existing ship tests pass**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.ShipFactoryTest`
Expected: PASS (existing tests unaffected).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/ShipFactory.java
git commit -m "feat(boarding): spawn ships with default targetable subsystems"
```

---

## Task 9: Register snapshots for persistence

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java`

- [ ] **Step 1: Add imports**

With the other snapshot/component imports at the top of the file:

```java
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.persistence.snapshots.ShipSubsystemsSnapshot;
import com.galacticodyssey.persistence.snapshots.BoardingOperationSnapshot;
```

- [ ] **Step 2: Add registrations**

In the `static { ... }` block, alongside the existing `register("StructuralIntegrity", ...)`
and `register("DockingState", ...)` lines, add:

```java
        register("ShipSubsystems",     ShipSubsystemsSnapshot.class,     ShipSubsystemsComponent::new);
        register("BoardingOperation",  BoardingOperationSnapshot.class,  BoardingOperationComponent::new);
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java
git commit -m "feat(boarding): register subsystem/operation snapshots for save-load"
```

---

## Task 10: Register systems in `GameWorld`

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Add imports**

With the other system imports:

```java
import com.galacticodyssey.ship.boarding.systems.ShipSubsystemSystem;
import com.galacticodyssey.ship.boarding.systems.BoardingOrchestratorSystem;
import com.galacticodyssey.ship.boarding.systems.ShipProjectileImpactSystem;
```

- [ ] **Step 2: Register the systems**

In the `GameWorld` constructor, near the existing ship/combat system registrations (after the
`DamageSystem` registration block is a good spot), add:

```java
        engine.addSystem(new BoardingOrchestratorSystem(eventBus));
        engine.addSystem(new ShipProjectileImpactSystem(eventBus));
        engine.addSystem(new ShipSubsystemSystem(eventBus));
```

- [ ] **Step 3: Verify the full suite still builds and passes**

Run: `./gradlew core:test`
Expected: BUILD SUCCESSFUL; all tests pass (new + existing).

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(boarding): register subsystem, impact, and orchestrator systems"
```

---

## Task 11: End-to-end integration test (disable → VULNERABLE)

**Files:**
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/DisablePipelineIntegrationTest.java`

Proves the Plan A slice end-to-end with no GL context: EMP damage to a ship's aft disables
its engines and the orchestrator flips it to VULNERABLE. We drive the chain with direct
`ShipDamageEvent` publishes (rather than spawning projectiles) so the engine-zone hit is
deterministic — the coarse impact sphere in `ShipProjectileImpactSystem` can't distinguish
aft hits at this scale, and that system is already covered by its own test in Task 7.

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.ShipBoardableEvent;
import com.galacticodyssey.ship.boarding.events.ShipDamageEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingOrchestratorSystem;
import com.galacticodyssey.ship.boarding.systems.ShipSubsystemSystem;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DisablePipelineIntegrationTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity target;
    private final List<ShipBoardableEvent> boardable = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new ShipSubsystemSystem(eventBus));        // priority 9
        engine.addSystem(new BoardingOrchestratorSystem(eventBus)); // priority 1

        target = new Entity();
        TransformComponent tc = new TransformComponent();
        tc.position.set(0, 0, 0);
        target.add(tc);
        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 400f; data.currentHullHp = 400f;
        target.add(data);
        ShipSubsystemsComponent subs = new ShipSubsystemsComponent();
        subs.initDefaults(100f);
        target.add(subs);
        engine.addEntity(target);

        eventBus.subscribe(ShipBoardableEvent.class, boardable::add);
    }

    @Test
    void empBarrageToAftDisablesEnginesAndMakesBoardable() {
        // Aft hit (local z = -10 → ENGINES). Step two frames per hit so the
        // ShipSubsystemSystem -> SubsystemDisabledEvent -> BoardingOrchestratorSystem
        // chain fully propagates.
        for (int i = 0; i < 3; i++) {
            eventBus.publish(new ShipDamageEvent(
                target, null, 10f, DamageType.EMP, new Vector3(0, 0, -10)));
            engine.update(0.016f); // applies EMP, may publish SubsystemDisabledEvent
            engine.update(0.016f); // orchestrator consumes SubsystemDisabledEvent
        }

        ShipSubsystemsComponent subs = target.getComponent(ShipSubsystemsComponent.class);
        assertFalse(subs.enginesOperational(), "EMP barrage disables engines");
        BoardingOperationComponent op = target.getComponent(BoardingOperationComponent.class);
        assertNotNull(op);
        assertEquals(BoardingPhase.VULNERABLE, op.phase);
        assertFalse(boardable.isEmpty());
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.DisablePipelineIntegrationTest`
Expected: PASS.

- [ ] **Step 3: Run the whole suite once more**

Run: `./gradlew core:test`
Expected: BUILD SUCCESSFUL, all green.

- [ ] **Step 4: Commit**

```bash
git add core/src/test/java/com/galacticodyssey/ship/boarding/DisablePipelineIntegrationTest.java
git commit -m "test(boarding): integration test for disable -> VULNERABLE pipeline"
```

---

## Plan A complete

At this point: ships carry targetable subsystems; weapons (kinetic destroy, EMP soft-disable)
knock out engines; disabled ships coast (no thrust); and a disabled ship auto-enters the
`VULNERABLE`/boardable state with a persisted `BoardingOperationComponent`. This is the
foundation Plan B (attach: clamp + breaching pod, then interior entry) builds on.

**Hand-off interfaces locked for Plan B/C:**
- `BoardingOperationComponent` (phase enum, `aggressorShip`, `targetShip`, `attachMethod`, `entryPoint`, `playerIsAggressor`).
- `ShipBoardableEvent(ship, aggressor)` — Plan B listens to start the attach phase.
- `BoardingOrchestratorSystem` — Plan B/C extend it with the remaining phase transitions.
- `ShipSubsystemsComponent.enginesOperational()` — Plan C's hijack restores engine operation.
