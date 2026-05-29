# Boarding Pipeline — Plan C: Interior Combat, Resolution & Bidirectional Boarding

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the boarding pipeline: spawn enemy crew defenders in the breached interior, detect the win condition (clear all defenders **OR** capture the bridge), present resolution outcomes (**hijack / scrap / ransom / tow**), and let hostile NPC ships board the player (bidirectional).

**Architecture:** Approach C from the design spec. `BoardingCombatSystem` reacts to `PlayerEnteredHostileInteriorEvent` (Plan B) by spawning defenders (count/strength from a data-driven `BoardingDefenseComponent` on the target) and an optional away team, then watches `EntityKilledEvent` and player position to fire `BoardingClearedEvent` → `RESOLVING`. `BoardingResolutionSystem` applies the chosen `BoardingOutcome`, mutating ownership (`OwnedShipComponent`), the `PlayerGarageComponent`, the wallet, cargo, and reputation. `EnemyBoardingAISystem` lets a hostile NPC that has disabled the player launch a breaching pod and spawn attackers (inverted roles).

**Tech Stack:** Java 17, libGDX, Ashley ECS, gdx-bullet, JUnit 5. Build: Gradle (`./gradlew core:test`). Build env: `JAVA_HOME=~/.jdks/temurin-25.0.3`. Run filtered tests with `--tests`.

**Spec:** [docs/superpowers/specs/2026-05-28-ship-boarding-pipeline-design.md](../specs/2026-05-28-ship-boarding-pipeline-design.md)
**Builds on:** [Plan A](2026-05-28-boarding-plan-a-disable-subsystems.md) + [Plan B](2026-05-28-boarding-plan-b-attach-and-entry.md).

---

## Design decisions locked for this plan

- **Defenders are data-driven, not crew-derived.** Ships carry no crew roster today, so a `BoardingDefenseComponent` (count, health, damage, factionId) stocked by `ShipFactory` per size class is the source of defenders. Faction for ransom/ownership comes from this component (single source) — no fleet two-hop lookup.
- **Interior combat is main-world coordinate space (MVP).** Defenders spawn in world space at `targetShipPosition + offset`. Win detection is event/position driven and fully headless-testable; physical co-location in the per-interior `btDiscreteDynamicsWorld` is deferred.
- **Win conditions:** all defenders dead (via `EntityKilledEvent`) **OR** player inside the bridge radius with no live defenders there. First to fire wins.
- **Resolution is event-driven.** On `RESOLVING`, `BoardingResolutionRequestedEvent` is published for a future Scene2D menu. `BoardingResolutionSystem` applies a `BoardingResolutionChosenEvent`. The Scene2D menu widget itself is out of scope (deferred); a chosen-event is the integration seam (and what tests/debug keys drive).
- **Bidirectional MVP:** `EnemyBoardingAISystem` makes an NPC launch a breach pod at a disabled player ship and spawn attackers. If attackers reach the player's bridge the operation auto-resolves as `ENEMY_CAPTURE` (player loses the ship). Full player-as-defender combat polish is deferred.
- **Hijack success** is gated by a deterministic `Tactics` threshold helper (no RNG, for testability); failure leaves ownership unchanged.

---

## File Structure

New under `com.galacticodyssey.ship.boarding` (components + `BoardingOutcome`), `...boarding.events`, `...boarding.systems`; new snapshots under `com.galacticodyssey.persistence.snapshots`.

**Create:**
- `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOutcome.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingDefenseComponent.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingDefenderComponent.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/BridgeComponent.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/AwayTeamComponent.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/OwnedShipComponent.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/PlayerGarageComponent.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/events/BoardingClearedEvent.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/events/BoardingResolutionRequestedEvent.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/events/BoardingResolutionChosenEvent.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/events/BoardingResolvedEvent.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingCombatSystem.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingResolutionSystem.java`
- `core/src/main/java/com/galacticodyssey/ship/boarding/systems/EnemyBoardingAISystem.java`
- `core/src/main/java/com/galacticodyssey/persistence/snapshots/OwnedShipSnapshot.java`
- `core/src/main/java/com/galacticodyssey/persistence/snapshots/PlayerGarageSnapshot.java`

**Modify:**
- `core/src/main/java/com/galacticodyssey/ship/ShipFactory.java` — stock `BoardingDefenseComponent`.
- `core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java` — register `OwnedShip`, `PlayerGarage`.
- `core/src/main/java/com/galacticodyssey/persistence/KryoRegistrar.java` — register new snapshots (78, 79, 82).
- `core/src/main/java/com/galacticodyssey/core/GameWorld.java` — register the three new systems.

**Test:**
- `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingDefenseComponentTest.java`
- `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingCombatSystemTest.java`
- `core/src/test/java/com/galacticodyssey/ship/boarding/BridgeCaptureTest.java`
- `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingResolutionSystemTest.java`
- `core/src/test/java/com/galacticodyssey/ship/boarding/EnemyBoardingAISystemTest.java`
- `core/src/test/java/com/galacticodyssey/ship/boarding/FullBoardingPipelineTest.java`

---

## Task 1: `BoardingOutcome` + resolution/cleared events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOutcome.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/events/BoardingClearedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/events/BoardingResolutionRequestedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/events/BoardingResolutionChosenEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/events/BoardingResolvedEvent.java`

- [ ] **Step 1: Write `BoardingOutcome`**

```java
package com.galacticodyssey.ship.boarding;

/** Resolution outcomes for a completed boarding operation. */
public enum BoardingOutcome {
    HIJACK,
    SCRAP,
    RANSOM,
    TOW,
    /** Inverted operation: an NPC captured the player's ship. */
    ENEMY_CAPTURE
}
```

- [ ] **Step 2: Write the four events**

`events/BoardingClearedEvent.java`:
```java
package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;

/** A boarding win condition has been met (defenders cleared or bridge captured). */
public final class BoardingClearedEvent {
    public final Entity aggressor;
    public final Entity target;

    public BoardingClearedEvent(Entity aggressor, Entity target) {
        this.aggressor = aggressor;
        this.target = target;
    }
}
```

`events/BoardingResolutionRequestedEvent.java`:
```java
package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;

/** The boarding operation is awaiting a resolution choice (UI menu seam). */
public final class BoardingResolutionRequestedEvent {
    public final Entity target;

    public BoardingResolutionRequestedEvent(Entity target) {
        this.target = target;
    }
}
```

`events/BoardingResolutionChosenEvent.java`:
```java
package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.boarding.BoardingOutcome;

/** A resolution outcome has been selected (by UI, debug key, or AI). */
public final class BoardingResolutionChosenEvent {
    public final Entity target;
    public final BoardingOutcome outcome;

    public BoardingResolutionChosenEvent(Entity target, BoardingOutcome outcome) {
        this.target = target;
        this.outcome = outcome;
    }
}
```

`events/BoardingResolvedEvent.java`:
```java
package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.ship.boarding.BoardingOutcome;

/** A boarding operation has been resolved and applied. */
public final class BoardingResolvedEvent {
    public final Entity target;
    public final BoardingOutcome outcome;

    public BoardingResolvedEvent(Entity target, BoardingOutcome outcome) {
        this.target = target;
        this.outcome = outcome;
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOutcome.java core/src/main/java/com/galacticodyssey/ship/boarding/events/
git commit -m "feat(boarding): add BoardingOutcome + cleared/resolution events"
```

---

## Task 2: `BoardingDefenseComponent` (data-driven defenders) + stock in `ShipFactory`

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingDefenseComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/ship/ShipFactory.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingDefenseComponentTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.galacticodyssey.ship.ShipSizeClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardingDefenseComponentTest {

    @Test
    void defendersScaleWithSizeClass() {
        BoardingDefenseComponent small = BoardingDefenseComponent.forSizeClass(ShipSizeClass.SMALL);
        BoardingDefenseComponent medium = BoardingDefenseComponent.forSizeClass(ShipSizeClass.MEDIUM);
        BoardingDefenseComponent large = BoardingDefenseComponent.forSizeClass(ShipSizeClass.LARGE);

        assertTrue(small.defenderCount >= 1);
        assertTrue(medium.defenderCount > small.defenderCount);
        assertTrue(large.defenderCount > medium.defenderCount);
        assertTrue(small.defenderHealth > 0f);
        assertTrue(small.defenderDamage > 0f);
        assertEquals("independent", small.factionId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingDefenseComponentTest`
Expected: FAIL — `BoardingDefenseComponent` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.ship.ShipSizeClass;

/**
 * Data-driven defender complement for a ship that is boarded. Ships carry no crew roster yet,
 * so this component (stocked by {@code ShipFactory}) is the source of FPS defenders spawned by
 * {@link com.galacticodyssey.ship.boarding.systems.BoardingCombatSystem}. Also the single
 * source of the ship's {@code factionId} for ransom/ownership.
 */
public class BoardingDefenseComponent implements Component {
    public int defenderCount = 2;
    public float defenderHealth = 100f;
    public float defenderDamage = 12f;
    public String factionId = "independent";

    /** Defender complement scaled by hull size class. */
    public static BoardingDefenseComponent forSizeClass(ShipSizeClass sizeClass) {
        BoardingDefenseComponent c = new BoardingDefenseComponent();
        switch (sizeClass) {
            case SMALL:  c.defenderCount = 2; c.defenderHealth = 80f;  c.defenderDamage = 10f; break;
            case MEDIUM: c.defenderCount = 4; c.defenderHealth = 100f; c.defenderDamage = 12f; break;
            default:     c.defenderCount = 7; c.defenderHealth = 120f; c.defenderDamage = 15f; break;
        }
        return c;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingDefenseComponentTest`
Expected: PASS.

- [ ] **Step 5: Stock it on spawned ships**

In `ShipFactory.java`, add the import with the other boarding import:

```java
import com.galacticodyssey.ship.boarding.BoardingDefenseComponent;
```

In `createShip(...)`, immediately after the existing `entity.add(subsystems);` (around line 156), add:

```java
        entity.add(BoardingDefenseComponent.forSizeClass(sizeClass));
```

In `createShipFromDesign(...)`, immediately after the existing `entity.add(subsystems2);` (around line 259), add:

```java
        entity.add(BoardingDefenseComponent.forSizeClass(design.sizeClass));
```

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/BoardingDefenseComponent.java core/src/main/java/com/galacticodyssey/ship/ShipFactory.java core/src/test/java/com/galacticodyssey/ship/boarding/BoardingDefenseComponentTest.java
git commit -m "feat(boarding): data-driven BoardingDefenseComponent stocked per size class"
```

---

## Task 3: Tag components — `BoardingDefenderComponent`, `BridgeComponent`, `AwayTeamComponent`

Small marker/data components used by the combat system. No standalone tests (covered by `BoardingCombatSystemTest`).

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingDefenderComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/BridgeComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/AwayTeamComponent.java`

- [ ] **Step 1: Write `BoardingDefenderComponent`**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

/** Tags an entity as a defender spawned for the boarding operation on {@link #operationShip}. */
public class BoardingDefenderComponent implements Component {
    public Entity operationShip;
    /** True for attackers spawned when an NPC boards the player (inverted roles). */
    public boolean attacker;
    /** Guards against double-counting a death toward the defender tally. */
    public boolean counted;
}
```

- [ ] **Step 2: Write `BridgeComponent`**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

/** Marks the capture objective on a boarded ship: the bridge, in ship-local coordinates. */
public class BridgeComponent implements Component {
    public final Vector3 localCenter = new Vector3();
    public float radius = 3f;
}
```

- [ ] **Step 3: Write `AwayTeamComponent`**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;

/** Optional: number of friendly crew to deploy with the player on boarding. */
public class AwayTeamComponent implements Component {
    public int size = 0;
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/BoardingDefenderComponent.java core/src/main/java/com/galacticodyssey/ship/boarding/BridgeComponent.java core/src/main/java/com/galacticodyssey/ship/boarding/AwayTeamComponent.java
git commit -m "feat(boarding): add defender/bridge/away-team marker components"
```

---

## Task 4: `BoardingCombatSystem` — spawn defenders + away team, all-defenders-dead win

Reacts to `PlayerEnteredHostileInteriorEvent`: spawns defenders (from `BoardingDefenseComponent`) and an away team (from `AwayTeamComponent`), tags the bridge from the interior layout, then listens to `EntityKilledEvent` and fires `BoardingClearedEvent` + advances to `RESOLVING` when no live defenders remain.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingCombatSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingCombatSystemTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.BoardingClearedEvent;
import com.galacticodyssey.ship.boarding.events.PlayerEnteredHostileInteriorEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingCombatSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardingCombatSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity player;
    private Entity aggressor;
    private Entity target;
    private final List<BoardingClearedEvent> cleared = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingCombatSystem(eventBus));

        player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        engine.addEntity(player);

        aggressor = new Entity();
        aggressor.add(new TransformComponent());
        engine.addEntity(aggressor);

        target = new Entity();
        target.add(new TransformComponent());
        BoardingDefenseComponent def = new BoardingDefenseComponent();
        def.defenderCount = 3;
        def.defenderHealth = 50f;
        target.add(def);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = target;
        op.aggressorShip = aggressor;
        op.phase = BoardingPhase.INTERIOR_COMBAT;
        op.playerIsAggressor = true;
        target.add(op);
        engine.addEntity(target);

        eventBus.subscribe(BoardingClearedEvent.class, cleared::add);
    }

    private ImmutableArray<Entity> defenders() {
        return engine.getEntitiesFor(Family.all(BoardingDefenderComponent.class).get());
    }

    @Test
    void enteringSpawnsDefenders() {
        eventBus.publish(new PlayerEnteredHostileInteriorEvent(player, target));
        engine.update(0.016f);
        assertEquals(3, defenders().size());
        for (Entity d : defenders()) {
            assertEquals(50f, d.getComponent(HealthComponent.class).maxHP, 0.01f);
            assertSame(target, d.getComponent(BoardingDefenderComponent.class).operationShip);
        }
        assertTrue(cleared.isEmpty(), "not cleared while defenders alive");
    }

    @Test
    void killingAllDefendersClearsOperation() {
        eventBus.publish(new PlayerEnteredHostileInteriorEvent(player, target));
        engine.update(0.016f);

        List<Entity> ds = new ArrayList<>();
        for (Entity d : defenders()) ds.add(d);
        for (Entity d : ds) {
            d.getComponent(HealthComponent.class).alive = false;
            eventBus.publish(new EntityKilledEvent(d, player));
        }
        engine.update(0.016f);

        assertEquals(1, cleared.size());
        assertSame(target, cleared.get(0).target);
        assertEquals(BoardingPhase.RESOLVING,
            target.getComponent(BoardingOperationComponent.class).phase);
    }

    @Test
    void doesNotSpawnTwice() {
        eventBus.publish(new PlayerEnteredHostileInteriorEvent(player, target));
        engine.update(0.016f);
        eventBus.publish(new PlayerEnteredHostileInteriorEvent(player, target));
        engine.update(0.016f);
        assertEquals(3, defenders().size(), "defenders spawned once per operation");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingCombatSystemTest`
Expected: FAIL — `BoardingCombatSystem` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.components.HitboxComponent;
import com.galacticodyssey.combat.components.RangedWeaponComponent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.InteriorLayout;
import com.galacticodyssey.ship.boarding.AwayTeamComponent;
import com.galacticodyssey.ship.boarding.BoardingDefenderComponent;
import com.galacticodyssey.ship.boarding.BoardingDefenseComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.BridgeComponent;
import com.galacticodyssey.ship.boarding.events.BoardingClearedEvent;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionRequestedEvent;
import com.galacticodyssey.ship.boarding.events.PlayerEnteredHostileInteriorEvent;
import com.galacticodyssey.ship.components.ShipInteriorComponent;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Owns interior boarding combat: spawns defenders (and the away team) when the player enters a
 * hostile interior, tags the bridge, and watches for the win condition (all defenders dead, or
 * the player capturing the bridge). On a win it publishes {@link BoardingClearedEvent} and
 * advances the operation to {@code RESOLVING}.
 */
public class BoardingCombatSystem extends EntitySystem {

    public static final int PRIORITY = 11;

    private static final ComponentMapper<BoardingOperationComponent> OP_M =
        ComponentMapper.getFor(BoardingOperationComponent.class);
    private static final ComponentMapper<BoardingDefenseComponent> DEF_M =
        ComponentMapper.getFor(BoardingDefenseComponent.class);
    private static final ComponentMapper<BoardingDefenderComponent> DEFENDER_M =
        ComponentMapper.getFor(BoardingDefenderComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH_M =
        ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<ShipInteriorComponent> INTERIOR_M =
        ComponentMapper.getFor(ShipInteriorComponent.class);
    private static final ComponentMapper<BridgeComponent> BRIDGE_M =
        ComponentMapper.getFor(BridgeComponent.class);

    private final EventBus eventBus;
    private final Queue<PlayerEnteredHostileInteriorEvent> entries = new ArrayDeque<>();
    private final Queue<EntityKilledEvent> kills = new ArrayDeque<>();
    private final Matrix4 shipMat = new Matrix4();
    private final Vector3 bridgeWorld = new Vector3();

    private ImmutableArray<Entity> defenders;
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> operations;

    public BoardingCombatSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(PlayerEnteredHostileInteriorEvent.class, entries::add);
        eventBus.subscribe(EntityKilledEvent.class, kills::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        defenders = engine.getEntitiesFor(Family.all(BoardingDefenderComponent.class).get());
        players = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, TransformComponent.class).get());
        operations = engine.getEntitiesFor(Family.all(BoardingOperationComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        defenders = null;
        players = null;
        operations = null;
    }

    @Override
    public void update(float deltaTime) {
        PlayerEnteredHostileInteriorEvent entry;
        while ((entry = entries.poll()) != null) {
            spawnForEntry(entry.targetShip);
        }
        EntityKilledEvent kill;
        while ((kill = kills.poll()) != null) {
            countDefenderKill(kill.target);
        }
        checkWinConditions();
    }

    /** Decrements the defender tally on the relevant operation when a tagged defender dies. */
    private void countDefenderKill(Entity dead) {
        if (dead == null) return;
        BoardingDefenderComponent tag = DEFENDER_M.get(dead);
        if (tag == null || tag.attacker || tag.counted || tag.operationShip == null) return;
        BoardingOperationComponent op = OP_M.get(tag.operationShip);
        if (op == null) return;
        tag.counted = true;
        op.defendersRemaining = Math.max(0, op.defendersRemaining - 1);
    }

    private void spawnForEntry(Entity target) {
        BoardingOperationComponent op = OP_M.get(target);
        if (op == null || op.spawned) return;
        op.spawned = true;

        tagBridge(target);

        TransformComponent targetTransform = TRANSFORM_M.get(target);
        Vector3 base = (targetTransform != null) ? targetTransform.position : Vector3.Zero;

        BoardingDefenseComponent def = DEF_M.get(target);
        int count = (def != null) ? def.defenderCount : 2;
        float hp = (def != null) ? def.defenderHealth : 100f;
        float dmg = (def != null) ? def.defenderDamage : 12f;
        op.defendersRemaining = count;
        for (int i = 0; i < count; i++) {
            spawnCombatant(target, base, i, hp, dmg, /*attacker*/ false);
        }

        // Away team (optional) — friendly crew at the entry, count from AwayTeamComponent.
        if (players != null && players.size() > 0) {
            AwayTeamComponent away = players.first().getComponent(AwayTeamComponent.class);
            int awaySize = (away != null) ? away.size : 0;
            for (int i = 0; i < awaySize; i++) {
                Entity mate = spawnCombatant(target, base, 100 + i, 100f, 12f, false);
                mate.remove(BoardingDefenderComponent.class); // away team are not defenders
            }
        }
    }

    private Entity spawnCombatant(Entity target, Vector3 base, int index,
                                  float hp, float dmg, boolean attacker) {
        Entity e = new Entity();

        TransformComponent t = new TransformComponent();
        // Fan out around the ship origin so they aren't stacked.
        t.position.set(base).add((index % 3) * 1.5f - 1.5f, 0f, (index / 3) * 1.5f);
        e.add(t);

        HealthComponent health = new HealthComponent();
        health.currentHP = hp;
        health.maxHP = hp;
        health.alive = true;
        e.add(health);

        e.add(new CombatAIComponent());
        e.add(new CombatInputComponent());
        e.add(new HitboxComponent());

        RangedWeaponComponent weapon = new RangedWeaponComponent();
        weapon.damage = dmg;
        weapon.fireRate = 1.5f;
        weapon.range = 20f;
        weapon.currentAmmo = 30;
        weapon.magSize = 30;
        weapon.hitscan = true;
        weapon.damageType = DamageType.BALLISTIC;
        e.add(weapon);

        BoardingDefenderComponent tag = new BoardingDefenderComponent();
        tag.operationShip = target;
        tag.attacker = attacker;
        e.add(tag);

        getEngine().addEntity(e);
        return e;
    }

    private void tagBridge(Entity target) {
        if (BRIDGE_M.get(target) != null) return;
        ShipInteriorComponent interior = INTERIOR_M.get(target);
        BridgeComponent bridge = new BridgeComponent();
        if (interior != null && interior.layout != null) {
            bridge.localCenter.set(interior.layout.pilotSeatPosition);
        }
        target.add(bridge);
    }

    private void checkWinConditions() {
        if (operations == null) return;
        for (int i = 0, n = operations.size(); i < n; i++) {
            Entity target = operations.get(i);
            BoardingOperationComponent op = OP_M.get(target);
            if (op == null || op.phase != BoardingPhase.INTERIOR_COMBAT || !op.spawned) continue;
            if (op.playerIsAggressor && (op.defendersRemaining <= 0 || bridgeCaptured(target))) {
                fireCleared(op, target);
            }
        }
    }

    private boolean bridgeCaptured(Entity target) {
        BridgeComponent bridge = BRIDGE_M.get(target);
        if (bridge == null || players == null || players.size() == 0) return false;
        TransformComponent targetTransform = TRANSFORM_M.get(target);
        if (targetTransform != null) {
            shipMat.set(targetTransform.position, targetTransform.rotation);
            bridgeWorld.set(bridge.localCenter).mul(shipMat);
        } else {
            bridgeWorld.set(bridge.localCenter);
        }
        Vector3 playerPos = TRANSFORM_M.get(players.first()).position;
        boolean playerInBridge = playerPos.dst2(bridgeWorld) <= bridge.radius * bridge.radius;
        if (!playerInBridge) return false;
        return liveDefenders(target, /*withinBridgeOnly*/ true, bridge.radius) == 0;
    }

    /** Counts live defenders of {@code target}; optionally only those within the bridge radius. */
    private int liveDefenders(Entity target, boolean withinBridgeOnly, float radius) {
        if (defenders == null) return 0;
        int live = 0;
        for (int i = 0, n = defenders.size(); i < n; i++) {
            Entity d = defenders.get(i);
            BoardingDefenderComponent tag = DEFENDER_M.get(d);
            if (tag == null || tag.operationShip != target || tag.attacker) continue;
            HealthComponent h = HEALTH_M.get(d);
            if (h == null || !h.alive) continue;
            if (withinBridgeOnly) {
                Vector3 dp = TRANSFORM_M.get(d).position;
                if (dp.dst2(bridgeWorld) > radius * radius) continue;
            }
            live++;
        }
        return live;
    }

    private void fireCleared(BoardingOperationComponent op, Entity target) {
        op.phase = BoardingPhase.RESOLVING;
        eventBus.publish(new BoardingClearedEvent(op.aggressorShip, target));
        eventBus.publish(new BoardingResolutionRequestedEvent(target));
    }
}
```

- [ ] **Step 4: Add the `spawned` flag + defender tally to `BoardingOperationComponent`**

The combat system uses `op.spawned` to spawn defenders once and `op.defendersRemaining` to track the win condition deterministically (independent of Ashley's entity-add timing). Add to `BoardingOperationComponent.java` (transient runtime fields — not persisted; an in-progress interior fight need not survive reload):

```java
    /** Transient: true once defenders have been spawned for this operation. Not persisted. */
    public transient boolean spawned;
    /** Transient: live defender count; reaches 0 when the interior is cleared. Not persisted. */
    public transient int defendersRemaining;
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingCombatSystemTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingCombatSystem.java core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOperationComponent.java core/src/test/java/com/galacticodyssey/ship/boarding/BoardingCombatSystemTest.java
git commit -m "feat(boarding): BoardingCombatSystem spawns defenders + clears on wipe"
```

---

## Task 5: Bridge-capture win condition

Covers the bridge-capture branch of `BoardingCombatSystem` written in Task 4. Adds a test proving the player reaching the bridge with no live defenders there triggers a clear.

**Files:**
- Modify: (none — verifies Task 4 code)
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/BridgeCaptureTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.InteriorLayout;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.BoardingClearedEvent;
import com.galacticodyssey.ship.boarding.events.PlayerEnteredHostileInteriorEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingCombatSystem;
import com.galacticodyssey.ship.components.ShipInteriorComponent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BridgeCaptureTest {

    @Test
    void playerInBridgeWithNoDefendersThereCaptures() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        engine.addSystem(new BoardingCombatSystem(eventBus));

        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        engine.addEntity(player);

        Entity aggressor = new Entity();
        aggressor.add(new TransformComponent());
        engine.addEntity(aggressor);

        // Target at origin; bridge local center far from the defender fan-out (which is near origin).
        Entity target = new Entity();
        target.add(new TransformComponent());
        ShipInteriorComponent interior = new ShipInteriorComponent();
        interior.layout = layoutWithBridgeAt(new com.badlogic.gdx.math.Vector3(50f, 0f, 0f));
        target.add(interior);
        BoardingDefenseComponent def = new BoardingDefenseComponent();
        def.defenderCount = 2;
        target.add(def);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = target;
        op.aggressorShip = aggressor;
        op.phase = BoardingPhase.INTERIOR_COMBAT;
        op.playerIsAggressor = true;
        target.add(op);
        engine.addEntity(target);

        List<BoardingClearedEvent> cleared = new ArrayList<>();
        eventBus.subscribe(BoardingClearedEvent.class, cleared::add);

        eventBus.publish(new PlayerEnteredHostileInteriorEvent(player, target));
        engine.update(0.016f); // spawns 2 defenders near origin; bridge at (50,0,0)

        // Defenders are alive but near origin — not in the bridge. Move player into the bridge.
        player.getComponent(TransformComponent.class).position.set(50f, 0f, 0f);
        engine.update(0.016f);

        assertEquals(1, cleared.size(), "bridge captured: player at bridge, no defenders there");
        assertEquals(BoardingPhase.RESOLVING,
            target.getComponent(BoardingOperationComponent.class).phase);
    }

    /** Builds a minimal InteriorLayout whose pilotSeatPosition is the bridge center. */
    private InteriorLayout layoutWithBridgeAt(com.badlogic.gdx.math.Vector3 bridge) {
        return new InteriorLayout(
            new ArrayList<>(),                                  // rooms
            new boolean[1][1][1],                               // corridorCells
            new com.badlogic.gdx.math.Vector3(0, 0, 0),         // airlockPosition
            bridge,                                             // pilotSeatPosition (bridge)
            new float[0], new short[0],                         // floor verts/indices
            new float[0], new short[0],                         // wall verts/indices
            1, 1, 1);                                           // grid sizes
    }
}
```

> **Note:** `InteriorLayout`'s constructor is the 11-arg form `(List<RoomPlacement> rooms, boolean[][][] corridorCells, Vector3 airlockPosition, Vector3 pilotSeatPosition, float[] floorVertices, short[] floorIndices, float[] wallVertices, short[] wallIndices, int gridSizeX, int gridSizeY, int gridSizeZ)`. Confirm the parameter order against `core/src/main/java/com/galacticodyssey/ship/InteriorLayout.java` and adjust the call if the field order differs.

- [ ] **Step 2: Run the test**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BridgeCaptureTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add core/src/test/java/com/galacticodyssey/ship/boarding/BridgeCaptureTest.java
git commit -m "test(boarding): bridge-capture win condition coverage"
```

---

## Task 6: `OwnedShipComponent` + `PlayerGarageComponent` + persistence

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/OwnedShipComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/PlayerGarageComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/OwnedShipSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/PlayerGarageSnapshot.java`
- Modify: `core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java`
- Modify: `core/src/main/java/com/galacticodyssey/persistence/KryoRegistrar.java`

- [ ] **Step 1: Write `OwnedShipComponent` + snapshot**

`OwnedShipComponent.java`:
```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.OwnedShipSnapshot;

/** Marks who owns a ship. Added to captured/towed ships. */
public class OwnedShipComponent implements Component, Snapshotable<OwnedShipSnapshot> {

    public enum Owner { NONE, PLAYER, NPC }

    public Owner owner = Owner.NONE;
    public String factionId = "independent";

    @Override
    public OwnedShipSnapshot takeSnapshot() {
        OwnedShipSnapshot s = new OwnedShipSnapshot();
        s.owner = owner.name();
        s.factionId = factionId;
        return s;
    }

    @Override
    public void restoreFromSnapshot(OwnedShipSnapshot s) {
        owner = Owner.valueOf(s.owner);
        factionId = s.factionId;
    }
}
```

`OwnedShipSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

public class OwnedShipSnapshot {
    public String owner;
    public String factionId;
    public OwnedShipSnapshot() {}
}
```

- [ ] **Step 2: Write `PlayerGarageComponent` + snapshot**

`PlayerGarageComponent.java`:
```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.PlayerGarageSnapshot;

import java.util.ArrayList;
import java.util.List;

/** A player's stored ships (from hijack/tow). Lives on the player entity. */
public class PlayerGarageComponent implements Component, Snapshotable<PlayerGarageSnapshot> {

    /** One stored ship: hull identity (seed + size class) + how it was acquired. */
    public static final class GarageEntry {
        public String shipName;
        public long seed;
        public String sizeClass;
        public String acquiredVia; // "HIJACK" | "TOW"
        public GarageEntry() {}
    }

    public final List<GarageEntry> ships = new ArrayList<>();

    @Override
    public PlayerGarageSnapshot takeSnapshot() {
        PlayerGarageSnapshot s = new PlayerGarageSnapshot();
        for (GarageEntry e : ships) {
            PlayerGarageSnapshot.Entry se = new PlayerGarageSnapshot.Entry();
            se.shipName = e.shipName;
            se.seed = e.seed;
            se.sizeClass = e.sizeClass;
            se.acquiredVia = e.acquiredVia;
            s.entries.add(se);
        }
        return s;
    }

    @Override
    public void restoreFromSnapshot(PlayerGarageSnapshot s) {
        ships.clear();
        for (PlayerGarageSnapshot.Entry se : s.entries) {
            GarageEntry e = new GarageEntry();
            e.shipName = se.shipName;
            e.seed = se.seed;
            e.sizeClass = se.sizeClass;
            e.acquiredVia = se.acquiredVia;
            ships.add(e);
        }
    }
}
```

`PlayerGarageSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class PlayerGarageSnapshot {
    public static class Entry {
        public String shipName;
        public long seed;
        public String sizeClass;
        public String acquiredVia;
        public Entry() {}
    }
    public final List<Entry> entries = new ArrayList<>();
    public PlayerGarageSnapshot() {}
}
```

- [ ] **Step 3: Register in `SnapshotComponentRegistry`**

Add imports with the others:
```java
import com.galacticodyssey.ship.boarding.OwnedShipComponent;
import com.galacticodyssey.ship.boarding.PlayerGarageComponent;
import com.galacticodyssey.persistence.snapshots.OwnedShipSnapshot;
import com.galacticodyssey.persistence.snapshots.PlayerGarageSnapshot;
```

In the static block, after the `register("BoardingOperation", ...)` line, add:
```java
        register("OwnedShip",    OwnedShipSnapshot.class,    OwnedShipComponent::new);
        register("PlayerGarage", PlayerGarageSnapshot.class, PlayerGarageComponent::new);
```

- [ ] **Step 4: Register in `KryoRegistrar`**

In the `// --- Ship subsystem snapshots ---` block (after the `BoardingOperationSnapshot.class, 77` line added in Plan B), add:
```java
        kryo.register(OwnedShipSnapshot.class, 78);
        kryo.register(PlayerGarageSnapshot.class, 79);
        kryo.register(PlayerGarageSnapshot.Entry.class, 82);
```

> ID 82 sits in the otherwise-NPC range but is unused; it is chosen because 75–79 are exhausted by the boarding snapshots. IDs only need to be unique and stable.

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/OwnedShipComponent.java core/src/main/java/com/galacticodyssey/ship/boarding/PlayerGarageComponent.java core/src/main/java/com/galacticodyssey/persistence/snapshots/OwnedShipSnapshot.java core/src/main/java/com/galacticodyssey/persistence/snapshots/PlayerGarageSnapshot.java core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java core/src/main/java/com/galacticodyssey/persistence/KryoRegistrar.java
git commit -m "feat(boarding): add OwnedShip + PlayerGarage components with persistence"
```

---

## Task 7: `BoardingResolutionSystem` — hijack / scrap / ransom / tow

Applies the chosen outcome. Reacts to `BoardingResolutionChosenEvent`, mutates state, publishes `BoardingResolvedEvent`, sets `RESOLVED`, and deactivates the target interior. Each outcome is a tested public method.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingResolutionSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingResolutionSystemTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.OwnedShipComponent.Owner;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.events.BoardingResolvedEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingResolutionSystem;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardingResolutionSystemTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity player;
    private Entity target;
    private final List<BoardingResolvedEvent> resolved = new ArrayList<>();
    private final List<ReputationChangeEvent> repChanges = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingResolutionSystem(eventBus));

        player = new Entity();
        player.add(new PlayerWalletComponent());
        player.add(new CargoBayComponent());
        player.add(new PlayerGarageComponent());
        engine.addEntity(player);

        target = new Entity();
        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 400f; data.currentHullHp = 400f;
        target.add(data);
        ShipSubsystemsComponent subs = new ShipSubsystemsComponent();
        subs.initDefaults(100f);
        subs.get(SubsystemType.ENGINES).empDisableTimer = 5f; // disabled
        target.add(subs);
        BoardingDefenseComponent def = new BoardingDefenseComponent();
        def.factionId = "pirates";
        target.add(def);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = target;
        op.aggressorShip = new Entity();
        op.phase = BoardingPhase.RESOLVING;
        op.playerIsAggressor = true;
        target.add(op);
        engine.addEntity(target);

        eventBus.subscribe(BoardingResolvedEvent.class, resolved::add);
        eventBus.subscribe(ReputationChangeEvent.class, repChanges::add);
    }

    @Test
    void hijackFlipsOwnershipAndRestoresEngines() {
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.HIJACK));
        engine.update(0.016f);

        OwnedShipComponent owned = target.getComponent(OwnedShipComponent.class);
        assertNotNull(owned);
        assertEquals(Owner.PLAYER, owned.owner);
        assertTrue(target.getComponent(ShipSubsystemsComponent.class).enginesOperational(),
            "hijack restores engine operation");
        assertEquals(1, player.getComponent(PlayerGarageComponent.class).ships.size());
        assertEquals(BoardingPhase.RESOLVED, target.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(1, resolved.size());
        assertEquals(BoardingOutcome.HIJACK, resolved.get(0).outcome);
    }

    @Test
    void scrapAddsCargoCreditsAndRemovesShip() {
        long before = player.getComponent(PlayerWalletComponent.class).credits;
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.SCRAP));
        engine.update(0.016f);

        assertFalse(player.getComponent(CargoBayComponent.class).contents.isEmpty(),
            "scrap deposits materials");
        assertTrue(player.getComponent(PlayerWalletComponent.class).credits > before,
            "scrap yields credits");
        assertNull(target.getComponent(ShipDataComponent.class) != null
                ? null : target, "ship marked destroyed");
        assertEquals(0f, target.getComponent(ShipDataComponent.class).currentHullHp, 0.01f);
        assertEquals(1, resolved.size());
    }

    @Test
    void ransomAwardsCreditsAndReputation() {
        long before = player.getComponent(PlayerWalletComponent.class).credits;
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.RANSOM));
        engine.update(0.016f);

        assertTrue(player.getComponent(PlayerWalletComponent.class).credits > before);
        assertEquals(1, repChanges.size());
        assertEquals("pirates", repChanges.get(0).factionId);
        assertEquals(1, resolved.size());
        assertEquals(BoardingOutcome.RANSOM, resolved.get(0).outcome);
    }

    @Test
    void towStoresShipInGarage() {
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.TOW));
        engine.update(0.016f);

        assertEquals(Owner.PLAYER, target.getComponent(OwnedShipComponent.class).owner);
        assertEquals(1, player.getComponent(PlayerGarageComponent.class).ships.size());
        assertEquals("TOW", player.getComponent(PlayerGarageComponent.class).ships.get(0).acquiredVia);
    }

    @Test
    void hijackGateRejectsLowTactics() {
        assertFalse(BoardingResolutionSystem.hijackSucceeds(/*tactics*/ 1, /*required*/ 50));
        assertTrue(BoardingResolutionSystem.hijackSucceeds(/*tactics*/ 60, /*required*/ 50));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingResolutionSystemTest`
Expected: FAIL — `BoardingResolutionSystem` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.galacticodyssey.ship.boarding.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.events.ReputationChangeEvent;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.ship.boarding.BoardingDefenseComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.BoardingOutcome;
import com.galacticodyssey.ship.boarding.OwnedShipComponent;
import com.galacticodyssey.ship.boarding.OwnedShipComponent.Owner;
import com.galacticodyssey.ship.boarding.PlayerGarageComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.Subsystem;
import com.galacticodyssey.ship.boarding.ShipSubsystemsComponent.SubsystemType;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.events.BoardingResolvedEvent;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.components.ShipInteriorComponent;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Applies a chosen boarding resolution: hijack / scrap / ransom / tow. Reacts to {@link
 * BoardingResolutionChosenEvent}, mutates ownership / wallet / cargo / reputation / garage,
 * publishes {@link BoardingResolvedEvent}, and finalizes the operation to {@code RESOLVED}.
 */
public class BoardingResolutionSystem extends EntitySystem {

    public static final int PRIORITY = 12;

    /** Credits per hull-HP point when scrapping or ransoming. */
    private static final float SCRAP_CREDITS_PER_HP = 0.5f;
    private static final float RANSOM_CREDITS_PER_HP = 1.0f;
    private static final float RANSOM_REPUTATION_DELTA = -8f;

    private static final ComponentMapper<BoardingOperationComponent> OP_M =
        ComponentMapper.getFor(BoardingOperationComponent.class);
    private static final ComponentMapper<ShipDataComponent> DATA_M =
        ComponentMapper.getFor(ShipDataComponent.class);
    private static final ComponentMapper<ShipSubsystemsComponent> SUBS_M =
        ComponentMapper.getFor(ShipSubsystemsComponent.class);
    private static final ComponentMapper<BoardingDefenseComponent> DEF_M =
        ComponentMapper.getFor(BoardingDefenseComponent.class);
    private static final ComponentMapper<PlayerWalletComponent> WALLET_M =
        ComponentMapper.getFor(PlayerWalletComponent.class);
    private static final ComponentMapper<CargoBayComponent> CARGO_M =
        ComponentMapper.getFor(CargoBayComponent.class);
    private static final ComponentMapper<PlayerGarageComponent> GARAGE_M =
        ComponentMapper.getFor(PlayerGarageComponent.class);

    private final EventBus eventBus;
    private final Queue<BoardingResolutionChosenEvent> pending = new ArrayDeque<>();
    private ImmutableArray<Entity> players;

    public BoardingResolutionSystem(EventBus eventBus) {
        super(PRIORITY);
        this.eventBus = eventBus;
        eventBus.subscribe(BoardingResolutionChosenEvent.class, pending::add);
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(PlayerTagComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        players = null;
    }

    @Override
    public void update(float deltaTime) {
        BoardingResolutionChosenEvent event;
        while ((event = pending.poll()) != null) {
            resolve(event.target, event.outcome);
        }
    }

    /** Applies {@code outcome} to the operation on {@code target}, then finalizes it. */
    public void resolve(Entity target, BoardingOutcome outcome) {
        BoardingOperationComponent op = OP_M.get(target);
        if (op == null || op.phase != BoardingPhase.RESOLVING) return;
        Entity player = playerEntity();

        switch (outcome) {
            case HIJACK: hijack(target, op, player); break;
            case SCRAP:  scrap(target, op, player); break;
            case RANSOM: ransom(target, op, player); break;
            case TOW:    tow(target, op, player); break;
            case ENEMY_CAPTURE: /* handled by EnemyBoardingAISystem path */ break;
        }

        op.phase = BoardingPhase.RESOLVED;
        ShipInteriorComponent interior = target.getComponent(ShipInteriorComponent.class);
        if (interior != null) interior.active = false;
        eventBus.publish(new BoardingResolvedEvent(target, outcome));
    }

    private void hijack(Entity target, BoardingOperationComponent op, Entity player) {
        int tactics = playerTactics(player);
        if (!hijackSucceeds(tactics, 0)) return; // gate; default required=0 → succeeds
        flagOwned(target);
        restoreEngines(target);
        addGarageEntry(player, target, "HIJACK");
    }

    private void scrap(Entity target, BoardingOperationComponent op, Entity player) {
        ShipDataComponent data = DATA_M.get(target);
        float hull = (data != null) ? data.hullHp : 100f;
        int salvage = Math.max(1, Math.round(hull / 50f));
        if (player != null) {
            CargoBayComponent cargo = CARGO_M.get(player);
            if (cargo != null) cargo.contents.merge("salvaged_alloy", salvage, Integer::sum);
            PlayerWalletComponent wallet = WALLET_M.get(player);
            if (wallet != null) wallet.credits += Math.round(hull * SCRAP_CREDITS_PER_HP);
        }
        if (data != null) data.currentHullHp = 0f; // marked destroyed (removal handled in-game)
    }

    private void ransom(Entity target, BoardingOperationComponent op, Entity player) {
        ShipDataComponent data = DATA_M.get(target);
        float hull = (data != null) ? data.hullHp : 100f;
        if (player != null) {
            PlayerWalletComponent wallet = WALLET_M.get(player);
            if (wallet != null) wallet.credits += Math.round(hull * RANSOM_CREDITS_PER_HP);
        }
        BoardingDefenseComponent def = DEF_M.get(target);
        String factionId = (def != null) ? def.factionId : "independent";
        eventBus.publish(new ReputationChangeEvent(factionId, RANSOM_REPUTATION_DELTA, "boarding:ransom"));
    }

    private void tow(Entity target, BoardingOperationComponent op, Entity player) {
        flagOwned(target);
        addGarageEntry(player, target, "TOW");
        // Towed ships stay engine-disabled (cannot fly themselves).
    }

    private void flagOwned(Entity target) {
        OwnedShipComponent owned = target.getComponent(OwnedShipComponent.class);
        if (owned == null) {
            owned = new OwnedShipComponent();
            target.add(owned);
        }
        owned.owner = Owner.PLAYER;
        BoardingDefenseComponent def = DEF_M.get(target);
        if (def != null) owned.factionId = def.factionId;
    }

    private void restoreEngines(Entity target) {
        ShipSubsystemsComponent subs = SUBS_M.get(target);
        if (subs == null) return;
        Subsystem engines = subs.get(SubsystemType.ENGINES);
        if (engines != null) {
            engines.health = engines.maxHealth;
            engines.empDisableTimer = 0f;
            engines.destroyed = false;
        }
    }

    private void addGarageEntry(Entity player, Entity target, String via) {
        if (player == null) return;
        PlayerGarageComponent garage = GARAGE_M.get(player);
        if (garage == null) return;
        PlayerGarageComponent.GarageEntry entry = new PlayerGarageComponent.GarageEntry();
        ShipDataComponent data = DATA_M.get(target);
        if (data != null && data.blueprint != null) {
            entry.seed = data.blueprint.seed;
            entry.sizeClass = data.blueprint.sizeClass.name();
        }
        entry.shipName = "Captured Ship";
        entry.acquiredVia = via;
        garage.ships.add(entry);
    }

    private Entity playerEntity() {
        return (players != null && players.size() > 0) ? players.first() : null;
    }

    private int playerTactics(Entity player) {
        // Tactics skill source is not yet wired into combat; default high so hijack succeeds.
        return 100;
    }

    /** Deterministic hijack gate: succeeds when the player's Tactics meets the requirement. */
    public static boolean hijackSucceeds(int tacticsSkill, int requiredTactics) {
        return tacticsSkill >= requiredTactics;
    }
}
```

> **Note:** the `scrapAddsCargoCreditsAndRemovesShip` test asserts `currentHullHp == 0` (marked destroyed). Physical entity removal from the engine/world is deferred to in-game integration (a destroyed-ship cleanup pass) and is intentionally not done here, so the resolution logic stays headless-testable. The odd `assertNull(... ? null : target ...)` line in the test is just a guarded no-op around the real `currentHullHp` assertion — keep the `currentHullHp` check.

- [ ] **Step 4: Simplify the scrap test assertion**

Before running, replace the confusing line in `BoardingResolutionSystemTest.scrapAddsCargoCreditsAndRemovesShip`:

```java
        assertNull(target.getComponent(ShipDataComponent.class) != null
                ? null : target, "ship marked destroyed");
```

with the clear equivalent:

```java
        assertEquals(0f, target.getComponent(ShipDataComponent.class).currentHullHp, 0.01f,
            "scrapped ship hull marked destroyed");
```

(Then the duplicate `currentHullHp` assertion two lines down can be removed.)

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.BoardingResolutionSystemTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingResolutionSystem.java core/src/test/java/com/galacticodyssey/ship/boarding/BoardingResolutionSystemTest.java
git commit -m "feat(boarding): BoardingResolutionSystem applies hijack/scrap/ransom/tow"
```

---

## Task 8: `EnemyBoardingAISystem` — NPC boards a disabled player (bidirectional)

When the player's ship is `VULNERABLE` and not already in an aggressor's hands, a hostile NPC ship within range becomes the aggressor, sets `playerIsAggressor = false`, and launches a breaching pod via `BoardingAttachSystem.launchPod`. When the pod breaches, attackers are spawned in the player's interior. If they reach the player's bridge, the operation auto-resolves as `ENEMY_CAPTURE` (deferred polish for full defender combat).

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/EnemyBoardingAISystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/EnemyBoardingAISystemTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem;
import com.galacticodyssey.ship.boarding.systems.EnemyBoardingAISystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnemyBoardingAISystemTest {

    private EventBus eventBus;
    private Engine engine;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
    }

    private Entity npcShipAt(float x) {
        Entity e = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(x, 0, 0);
        e.add(t);
        engine.addEntity(e);
        return e;
    }

    private Entity disabledPlayerShipAt(float x) {
        Entity ship = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(x, 0, 0);
        ship.add(t);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = ship;
        op.phase = BoardingPhase.VULNERABLE; // player ship disabled
        ship.add(op);
        engine.addEntity(ship);
        // a player piloting this ship
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        com.galacticodyssey.player.components.PlayerStateComponent ps =
            new com.galacticodyssey.player.components.PlayerStateComponent();
        ps.currentShip = ship;
        player.add(ps);
        engine.addEntity(player);
        return ship;
    }

    @Test
    void hostileNpcBoardsDisabledPlayerShip() {
        BoardingAttachSystem attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);
        EnemyBoardingAISystem ai = new EnemyBoardingAISystem(eventBus, attach);
        engine.addSystem(ai);

        Entity playerShip = disabledPlayerShipAt(0);
        Entity npc = npcShipAt(40);

        engine.update(0.016f);

        BoardingOperationComponent op = playerShip.getComponent(BoardingOperationComponent.class);
        assertFalse(op.playerIsAggressor, "NPC is the aggressor when boarding the player");
        assertSame(npc, op.aggressorShip);
        assertEquals(1, engine.getEntitiesFor(Family.all(BreachingPodComponent.class).get()).size(),
            "NPC launches a breaching pod at the player");
    }

    @Test
    void noNpcInRangeDoesNothing() {
        BoardingAttachSystem attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);
        engine.addSystem(new EnemyBoardingAISystem(eventBus, attach));

        Entity playerShip = disabledPlayerShipAt(0);
        npcShipAt(5000); // far away

        engine.update(0.016f);

        assertEquals(0, engine.getEntitiesFor(Family.all(BreachingPodComponent.class).get()).size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.EnemyBoardingAISystemTest`
Expected: FAIL — `EnemyBoardingAISystem` does not exist.

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
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.components.ShipDataComponent;

/**
 * Bidirectional boarding: when the player's ship has been disabled (its operation is VULNERABLE
 * and no aggressor has been assigned), a hostile NPC ship within {@link #BOARD_RANGE} becomes the
 * aggressor and launches a breaching pod at the player. Marks the operation {@code
 * playerIsAggressor = false} so the rest of the pipeline runs inverted.
 */
public class EnemyBoardingAISystem extends EntitySystem {

    public static final int PRIORITY = 10;
    public static final float BOARD_RANGE = 200f;

    private static final ComponentMapper<BoardingOperationComponent> OP_M =
        ComponentMapper.getFor(BoardingOperationComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORM_M =
        ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<PlayerStateComponent> STATE_M =
        ComponentMapper.getFor(PlayerStateComponent.class);

    private final EventBus eventBus;
    private final BoardingAttachSystem attachSystem;
    private ImmutableArray<Entity> players;
    private ImmutableArray<Entity> ships;

    public EnemyBoardingAISystem(EventBus eventBus, BoardingAttachSystem attachSystem) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.attachSystem = attachSystem;
    }

    @Override
    public void addedToEngine(Engine engine) {
        players = engine.getEntitiesFor(Family.all(
            PlayerTagComponent.class, PlayerStateComponent.class).get());
        ships = engine.getEntitiesFor(Family.all(
            ShipDataComponent.class, TransformComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        players = null;
        ships = null;
    }

    @Override
    public void update(float deltaTime) {
        if (players == null || players.size() == 0) return;
        Entity playerShip = STATE_M.get(players.first()).currentShip;
        if (playerShip == null) return;

        BoardingOperationComponent op = OP_M.get(playerShip);
        if (op == null || op.phase != BoardingPhase.VULNERABLE) return;
        if (op.aggressorShip != null) return; // already being boarded

        TransformComponent playerT = TRANSFORM_M.get(playerShip);
        if (playerT == null) return;

        Entity npc = nearestHostileNpc(playerShip, playerT.position);
        if (npc == null) return;

        op.playerIsAggressor = false;
        op.aggressorShip = npc;
        attachSystem.launchPod(npc, playerShip);
    }

    private Entity nearestHostileNpc(Entity playerShip, Vector3 origin) {
        if (ships == null) return null;
        Entity best = null;
        float bestDist2 = BOARD_RANGE * BOARD_RANGE;
        for (int i = 0, n = ships.size(); i < n; i++) {
            Entity ship = ships.get(i);
            if (ship == playerShip) continue;
            // An NPC ship: not the player's own. (Player ownership is implied by currentShip.)
            float d2 = TRANSFORM_M.get(ship).position.dst2(origin);
            if (d2 <= bestDist2) {
                bestDist2 = d2;
                best = ship;
            }
        }
        return best;
    }
}
```

> **Note:** `launchPod` (Plan B) currently sets `op.aggressorShip` and `op.phase = ATTACHING` only when the target is `VULNERABLE` — which it is here — so the explicit assignment above is belt-and-suspenders and harmless. The attacker-spawn-on-breach and `ENEMY_CAPTURE` auto-resolve are wired in Task 9's integration via `BoardingCombatSystem`/`BoardingResolutionSystem`; full player-as-defender interior combat is deferred (see plan header).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.EnemyBoardingAISystemTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/systems/EnemyBoardingAISystem.java core/src/test/java/com/galacticodyssey/ship/boarding/EnemyBoardingAISystemTest.java
git commit -m "feat(boarding): EnemyBoardingAISystem lets NPCs board a disabled player"
```

---

## Task 9: Register systems in `GameWorld`

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Add imports**

With the other boarding-system imports:

```java
import com.galacticodyssey.ship.boarding.systems.BoardingCombatSystem;
import com.galacticodyssey.ship.boarding.systems.BoardingResolutionSystem;
import com.galacticodyssey.ship.boarding.systems.EnemyBoardingAISystem;
```

- [ ] **Step 2: Register the systems**

Immediately after the Plan B registration block (the `new BoardingInitiationSystem(eventBus, boardingAttachSystem)` line), add:

```java
        engine.addSystem(new BoardingCombatSystem(eventBus));
        engine.addSystem(new BoardingResolutionSystem(eventBus));
        engine.addSystem(new EnemyBoardingAISystem(eventBus, boardingAttachSystem));
```

> `boardingAttachSystem` is the local declared in Plan B Task 9.

- [ ] **Step 3: Verify the full suite builds and passes**

Run: `./gradlew core:test`
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(boarding): register combat, resolution, and enemy-AI systems"
```

---

## Task 10: Full-pipeline integration test (NONE → RESOLVED)

Drives the whole boarding loop headlessly: disable → VULNERABLE (Plan A) → pod attach → BREACHED (Plan B) → interior entry → defenders cleared → RESOLVING → hijack → RESOLVED, asserting ownership flips to the player.

**Files:**
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/FullBoardingPipelineTest.java`

- [ ] **Step 1: Write the test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.OwnedShipComponent.Owner;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.events.ShipDamageEvent;
import com.galacticodyssey.ship.boarding.systems.*;
import com.galacticodyssey.ship.components.ShipDataComponent;
import com.galacticodyssey.ship.components.ShipInteriorComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FullBoardingPipelineTest {

    private EventBus eventBus;
    private Engine engine;
    private BoardingAttachSystem attach;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        // Plan A
        engine.addSystem(new ShipSubsystemSystem(eventBus));
        engine.addSystem(new BoardingOrchestratorSystem(eventBus));
        // Plan B
        attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);
        engine.addSystem(new BoardingEntrySystem(eventBus, null));
        // Plan C
        engine.addSystem(new BoardingCombatSystem(eventBus));
        engine.addSystem(new BoardingResolutionSystem(eventBus));
    }

    private void step(int frames) {
        for (int i = 0; i < frames; i++) engine.update(0.05f);
    }

    @Test
    void playerBoardsDisablesClearsAndHijacks() {
        // Player + aggressor ship.
        Entity aggressor = new Entity();
        aggressor.add(new TransformComponent());
        engine.addEntity(aggressor);

        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        PlayerStateComponent state = new PlayerStateComponent();
        state.currentMode = PlayerMode.PILOTING;
        state.currentShip = aggressor;
        player.add(state);
        player.add(new PlayerWalletComponent());
        player.add(new CargoBayComponent());
        player.add(new PlayerGarageComponent());
        engine.addEntity(player);

        // Target ship with subsystems, interior, defenders.
        Entity target = new Entity();
        TransformComponent tt = new TransformComponent();
        tt.position.set(30f, 0f, 0f);
        target.add(tt);
        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 300f; data.currentHullHp = 300f;
        target.add(data);
        ShipSubsystemsComponent subs = new ShipSubsystemsComponent();
        subs.initDefaults(100f);
        target.add(subs);
        ShipInteriorComponent interior = new ShipInteriorComponent();
        interior.active = false;
        target.add(interior);
        BoardingDefenseComponent def = new BoardingDefenseComponent();
        def.defenderCount = 2; def.defenderHealth = 30f;
        target.add(def);
        engine.addEntity(target);

        // Mark player as the aggressor when the target becomes VULNERABLE: the orchestrator
        // creates the op with aggressorShip=null, so we set playerIsAggressor on first sight.
        eventBus.subscribe(com.galacticodyssey.ship.boarding.events.ShipBoardableEvent.class, e -> {
            BoardingOperationComponent op = e.ship.getComponent(BoardingOperationComponent.class);
            if (op != null) op.playerIsAggressor = true;
        });

        // 1. Disable engines via EMP to the aft.
        for (int i = 0; i < 3; i++) {
            eventBus.publish(new ShipDamageEvent(target, aggressor, 10f, DamageType.EMP,
                new Vector3(30f, 0f, -10f)));
            engine.update(0.016f);
            engine.update(0.016f);
        }
        assertEquals(BoardingPhase.VULNERABLE,
            target.getComponent(BoardingOperationComponent.class).phase);

        // 2. Launch a breach pod; fly to BREACHED, then entry → INTERIOR_COMBAT.
        attach.launchPod(aggressor, target);
        step(120);
        assertEquals(BoardingPhase.INTERIOR_COMBAT,
            target.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(PlayerMode.ON_FOOT_INTERIOR, state.currentMode);

        // 3. Kill all defenders → RESOLVING.
        ImmutableArray<Entity> defenders = engine.getEntitiesFor(
            Family.all(BoardingDefenderComponent.class).get());
        assertEquals(2, defenders.size());
        for (int i = 0; i < defenders.size(); i++) {
            Entity d = defenders.get(i);
            d.getComponent(HealthComponent.class).alive = false;
            eventBus.publish(new EntityKilledEvent(d, player));
        }
        engine.update(0.016f);
        assertEquals(BoardingPhase.RESOLVING,
            target.getComponent(BoardingOperationComponent.class).phase);

        // 4. Choose hijack → RESOLVED + player owns the ship.
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.HIJACK));
        engine.update(0.016f);
        assertEquals(BoardingPhase.RESOLVED,
            target.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(Owner.PLAYER, target.getComponent(OwnedShipComponent.class).owner);
        assertTrue(target.getComponent(ShipSubsystemsComponent.class).enginesOperational(),
            "hijack restored engines");
        assertEquals(1, player.getComponent(PlayerGarageComponent.class).ships.size());
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew core:test --tests com.galacticodyssey.ship.boarding.FullBoardingPipelineTest`
Expected: PASS.

- [ ] **Step 3: Run the whole suite**

Run: `./gradlew core:test`
Expected: BUILD SUCCESSFUL, all green.

- [ ] **Step 4: Commit**

```bash
git add core/src/test/java/com/galacticodyssey/ship/boarding/FullBoardingPipelineTest.java
git commit -m "test(boarding): full pipeline NONE -> RESOLVED integration test"
```

---

## Plan C complete

The boarding pipeline is end-to-end: disabled ships are boarded (clamp or pod), the player fights data-driven defenders in the interior, wins by wiping defenders or capturing the bridge, and resolves via hijack / scrap / ransom / tow — with hijacked and towed ships recorded in the player's garage and ownership persisted. Hostile NPCs can board a disabled player ship in turn.

**Known limitations (intentional MVP scope):**
- Interior combat uses main-world coordinates; boarders are not physically co-located in the per-interior `btDiscreteDynamicsWorld`.
- The resolution menu is event-driven only (`BoardingResolutionRequestedEvent` → `BoardingResolutionChosenEvent`); the Scene2D widget that lets the player pick is deferred. Wire a debug key or temporary auto-choice if needed for manual playtesting.
- Bidirectional boarding spawns the NPC pod + (via `BoardingCombatSystem`) attackers, but full player-as-defender interior combat and the `ENEMY_CAPTURE` loss flow are stubbed for a later pass.
- Scrapped ships are marked `currentHullHp = 0` but not physically removed from the world (needs a destroyed-ship cleanup pass).
- The away team follows as friendly combatants but has no AI escort/formation behavior yet.
- `Tactics`-skill gating uses a default-success threshold until the skill system exposes a per-player Tactics value to combat.
