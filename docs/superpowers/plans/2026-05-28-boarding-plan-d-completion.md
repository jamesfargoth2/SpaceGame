# Boarding Pipeline — Plan D: Complete the MVP Gaps

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the documented MVP gaps from Plans B & C: a real faction-hostility filter for NPC boarding, a working Tactics-gated hijack, physical removal of scrapped ships, completed bidirectional boarding (attacker spawn + repel/capture resolution), and a Scene2D resolution menu so the player can actually choose hijack/scrap/ransom/tow in-game.

**Architecture:** Builds directly on the existing boarding systems. `EnemyBoardingAISystem` gains an optional `ReputationQuery` to filter aggressors by hostility. `BoardingResolutionSystem` reads the player's `Tactics` point-skill to gate hijack and disposes ship natives on scrap. `BoardingCombatSystem` spawns attackers when an NPC breaches the player and resolves the inverted fight (`REPELLED` on clear, `ENEMY_CAPTURE` on player death). `BoardingResolutionPanel` (Scene2D) mirrors `VehicleBayPanel` and is mounted in `GameScreen`, shown by `BoardingResolutionRequestedEvent`.

**Tech Stack:** Java 17, libGDX, Ashley ECS, gdx-bullet, Scene2D, JUnit 5. Build env: `JAVA_HOME=~/.jdks/temurin-25.0.3` (Windows: `C:\Users\james\.jdks\temurin-25.0.3`). Run filtered tests with `--tests`. The full `core:test` has TWO known-unrelated pre-existing failures in `OrbitalMechanicsIntegrationTest` — ignore them.

**Builds on:** Plans A/B/C (all implemented on branch `claude/nostalgic-khorana-ad55de`).

---

## Confirmed interfaces (verified against the codebase)

- **ReputationQuery** — `com.galacticodyssey.mission.job.ReputationQuery`, `@FunctionalInterface float getStanding(String factionTag)`. `ReputationManager` implements it. Hostile threshold: standing `< -50f` (matches `ReputationTier.HOSTILE`). Reputation is NOT wired into production `GameWorld` today, so the filter must degrade gracefully (null query → fall back to "any ship hostile").
- **Player Tactics** — `com.galacticodyssey.player.components.PlayerStatsComponent.pointSkills` is `ObjectMap<PointSkill, Integer>`; key `com.galacticodyssey.player.stats.PointSkill.TACTICS`. Read: `Integer v = stats.pointSkills.get(PointSkill.TACTICS); int tactics = v != null ? v : 0;`
- **Ship natives** — exterior `PhysicsBodyComponent { btRigidBody body; btCollisionShape shape; }` (in `com.galacticodyssey.ship.components`); interior `ShipInteriorComponent implements Disposable` (its `dispose()` tears down the interior world + bodies + meshes). Main dynamics world: `bulletPhysicsSystem.getDynamicsWorld()` (a `btDiscreteDynamicsWorld`).
- **BoardingDefenseComponent.factionId** — the single faction source on a ship (default `"independent"`).
- **VehicleBayPanel** — `com.galacticodyssey.ui.VehicleBayPanel extends Table implements Disposable`; constructor `(Skin, VehicleRegistry, VehicleBayService, Supplier<Entity>, EventBus)`; subscribes to events, `TextButton`s with `ClickListener` publish/act, `dispose()` unsubscribes. Mounted in `GameScreen` via a dedicated `Stage` added first to the `InputMultiplexer`, rendered when visible, disposed on screen dispose. Skin via `game.getSkin()`; button style `"default"`, label styles `"header"`/`"body"`.

---

## File Structure

**Create:**
- `core/src/main/java/com/galacticodyssey/ui/BoardingResolutionPanel.java`
- Tests: `EnemyBoardingHostilityTest`, `BoardingHijackTacticsTest`, `BoardingScrapRemovalTest`, `EnemyBoardingCombatTest` (in `core/src/test/java/com/galacticodyssey/ship/boarding/`)

**Modify:**
- `core/src/main/java/com/galacticodyssey/ship/boarding/systems/EnemyBoardingAISystem.java` — optional `ReputationQuery` + hostility filter.
- `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingResolutionSystem.java` — Tactics gate + scrap native teardown + handle `REPELLED`/`ENEMY_CAPTURE`.
- `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingCombatSystem.java` — attacker spawn on NPC breach + inverted win + player-death capture.
- `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOutcome.java` — add `REPELLED`.
- `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOperationComponent.java` — add transient `attackersRemaining`.
- `core/src/main/java/com/galacticodyssey/core/GameWorld.java` — pass main world to `BoardingResolutionSystem`; leave `ReputationQuery` null (reputation not wired) with a TODO.
- `core/src/main/java/com/galacticodyssey/ui/GameScreen.java` — mount the resolution panel.

---

## Task 1: Faction-hostility filter in `EnemyBoardingAISystem`

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/EnemyBoardingAISystem.java`
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/EnemyBoardingHostilityTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.systems.BoardingAttachSystem;
import com.galacticodyssey.ship.boarding.systems.EnemyBoardingAISystem;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnemyBoardingHostilityTest {

    private EventBus eventBus;
    private Engine engine;
    private BoardingAttachSystem attach;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        attach = new BoardingAttachSystem(eventBus);
        engine.addSystem(attach);
    }

    private Entity npcShip(float x, String factionId) {
        Entity e = new Entity();
        TransformComponent t = new TransformComponent();
        t.position.set(x, 0, 0);
        e.add(t);
        ShipDataComponent d = new ShipDataComponent();
        d.hullHp = 200f; d.currentHullHp = 200f;
        e.add(d);
        BoardingDefenseComponent def = new BoardingDefenseComponent();
        def.factionId = factionId;
        e.add(def);
        engine.addEntity(e);
        return e;
    }

    private Entity disabledPlayerShip() {
        Entity ship = new Entity();
        ship.add(new TransformComponent());
        ShipDataComponent d = new ShipDataComponent();
        d.hullHp = 200f; d.currentHullHp = 200f;
        ship.add(d);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = ship;
        op.phase = BoardingPhase.VULNERABLE;
        ship.add(op);
        engine.addEntity(ship);
        Entity player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        PlayerStateComponent ps = new PlayerStateComponent();
        ps.currentShip = ship;
        player.add(ps);
        engine.addEntity(player);
        return ship;
    }

    private int podCount() {
        return engine.getEntitiesFor(Family.all(BreachingPodComponent.class).get()).size();
    }

    @Test
    void hostileFactionShipBoardsThePlayer() {
        // Standing -80 < -50 → hostile.
        EnemyBoardingAISystem ai = new EnemyBoardingAISystem(attach, factionId -> -80f);
        engine.addSystem(ai);
        disabledPlayerShip();
        npcShip(40, "pirates");
        engine.update(0.016f);
        assertEquals(1, podCount(), "hostile faction launches a boarding pod");
    }

    @Test
    void neutralFactionShipDoesNotBoard() {
        EnemyBoardingAISystem ai = new EnemyBoardingAISystem(attach, factionId -> 0f); // neutral
        engine.addSystem(ai);
        disabledPlayerShip();
        npcShip(40, "traders");
        engine.update(0.016f);
        assertEquals(0, podCount(), "neutral faction does not board");
    }

    @Test
    void nullQueryFallsBackToHostile() {
        EnemyBoardingAISystem ai = new EnemyBoardingAISystem(attach, null); // no reputation wired
        engine.addSystem(ai);
        disabledPlayerShip();
        npcShip(40, "anyone");
        engine.update(0.016f);
        assertEquals(1, podCount(), "with no reputation source, any ship is treated as hostile");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.boarding.EnemyBoardingHostilityTest"`
Expected: FAIL — `EnemyBoardingAISystem` has no 2-arg constructor.

- [ ] **Step 3: Add the filter to `EnemyBoardingAISystem`**

Add imports:
```java
import com.galacticodyssey.mission.job.ReputationQuery;
import com.galacticodyssey.ship.boarding.BoardingDefenseComponent;
```

Add a mapper near the others:
```java
    private static final ComponentMapper<BoardingDefenseComponent> DEF_M =
        ComponentMapper.getFor(BoardingDefenseComponent.class);
```

Add the field + a hostile-standing constant, and replace the constructor (it currently takes only `BoardingAttachSystem`):
```java
    /** Standing strictly below this is HOSTILE (matches ReputationTier.HOSTILE). */
    private static final float HOSTILE_STANDING = -50f;

    private final ReputationQuery reputation; // nullable: when null, any ship is treated hostile

    public EnemyBoardingAISystem(BoardingAttachSystem attachSystem, ReputationQuery reputation) {
        super(PRIORITY);
        this.attachSystem = attachSystem;
        this.reputation = reputation;
    }
```

In `nearestHostileNpc`, after the `if (ship == playerShip) continue;` line, add a hostility gate before the distance check:
```java
            if (!isHostile(ship)) continue;
```

Add the helper:
```java
    /** A ship is a valid boarding aggressor if its faction is hostile to the player. */
    private boolean isHostile(Entity ship) {
        if (reputation == null) return true; // no reputation wired → preserve prior behavior
        BoardingDefenseComponent def = DEF_M.get(ship);
        String factionId = (def != null) ? def.factionId : null;
        if (factionId == null) return true; // unknown faction → treat as hostile
        return reputation.getStanding(factionId) < HOSTILE_STANDING;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.boarding.EnemyBoardingHostilityTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Update `GameWorld` construction**

In `GameWorld`, the line constructing `EnemyBoardingAISystem` currently reads:
```java
        engine.addSystem(new EnemyBoardingAISystem(boardingAttachSystem));
```
Replace with (reputation is not wired into GameWorld in production yet):
```java
        // TODO: pass a real ReputationQuery once ReputationManager is wired into GameWorld;
        // null preserves "any disabled-player ship may be boarded" behavior until then.
        engine.addSystem(new EnemyBoardingAISystem(boardingAttachSystem, null));
```

- [ ] **Step 6: Run the existing boarding suite + commit**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.boarding.*"`
Expected: all green.

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/systems/EnemyBoardingAISystem.java core/src/main/java/com/galacticodyssey/core/GameWorld.java core/src/test/java/com/galacticodyssey/ship/boarding/EnemyBoardingHostilityTest.java
git commit -m "feat(boarding): faction-hostility filter for NPC boarding (ReputationQuery, null-safe)"
```

---

## Task 2: Tactics-gated hijack

A hijack now requires enough `Tactics` skill, scaled by the captured hull's size class. A failed hijack still resolves the operation (no soft-lock) but does not transfer ownership, restore engines, or add a garage entry.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingResolutionSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingHijackTacticsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PointSkill;
import com.galacticodyssey.ship.ShipBlueprint;
import com.galacticodyssey.ship.ShipSizeClass;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.OwnedShipComponent.Owner;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingResolutionSystem;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardingHijackTacticsTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity player;
    private PlayerStatsComponent stats;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingResolutionSystem(eventBus, null));
        player = new Entity();
        player.add(new PlayerTagComponent());
        stats = new PlayerStatsComponent();
        player.add(stats);
        player.add(new PlayerGarageComponent());
        engine.addEntity(player);
    }

    private Entity largeTarget() {
        Entity target = new Entity();
        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 3000f; data.currentHullHp = 3000f;
        data.blueprint = new ShipBlueprint(42L, ShipSizeClass.LARGE);
        target.add(data);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = target;
        op.phase = BoardingPhase.RESOLVING;
        op.playerIsAggressor = true;
        target.add(op);
        engine.addEntity(target);
        return target;
    }

    @Test
    void lowTacticsFailsLargeShipHijack() {
        stats.pointSkills.put(PointSkill.TACTICS, 5); // below LARGE requirement
        Entity target = largeTarget();
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.HIJACK));
        engine.update(0.016f);
        assertNull(target.getComponent(OwnedShipComponent.class),
            "failed hijack does not transfer ownership");
        assertEquals(0, player.getComponent(PlayerGarageComponent.class).ships.size());
        assertEquals(BoardingPhase.RESOLVED, target.getComponent(BoardingOperationComponent.class).phase,
            "operation still resolves (no soft-lock)");
    }

    @Test
    void highTacticsSucceedsLargeShipHijack() {
        stats.pointSkills.put(PointSkill.TACTICS, 50); // meets LARGE requirement
        Entity target = largeTarget();
        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.HIJACK));
        engine.update(0.016f);
        assertEquals(Owner.PLAYER, target.getComponent(OwnedShipComponent.class).owner);
        assertEquals(1, player.getComponent(PlayerGarageComponent.class).ships.size());
    }

    @Test
    void requiredTacticsScalesWithSize() {
        assertEquals(0, BoardingResolutionSystem.requiredTacticsFor(ShipSizeClass.SMALL));
        assertTrue(BoardingResolutionSystem.requiredTacticsFor(ShipSizeClass.MEDIUM)
            > BoardingResolutionSystem.requiredTacticsFor(ShipSizeClass.SMALL));
        assertTrue(BoardingResolutionSystem.requiredTacticsFor(ShipSizeClass.LARGE)
            > BoardingResolutionSystem.requiredTacticsFor(ShipSizeClass.MEDIUM));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.boarding.BoardingHijackTacticsTest"`
Expected: FAIL — `requiredTacticsFor` missing and `playerTactics` returns the stub 100 (large hijack always succeeds).

- [ ] **Step 3: Wire real Tactics into the hijack gate**

Add imports to `BoardingResolutionSystem`:
```java
import com.galacticodyssey.player.components.PlayerStatsComponent;
import com.galacticodyssey.player.stats.PointSkill;
import com.galacticodyssey.ship.ShipSizeClass;
```

Add a mapper:
```java
    private static final ComponentMapper<PlayerStatsComponent> STATS_M =
        ComponentMapper.getFor(PlayerStatsComponent.class);
```

Replace the `hijack(...)` method's gate so it uses the real required value, and replace `playerTactics`:
```java
    private void hijack(Entity target, BoardingOperationComponent op, Entity player) {
        int tactics = playerTactics(player);
        int required = requiredTacticsFor(targetSizeClass(target));
        if (!hijackSucceeds(tactics, required)) return; // crew repels the takeover
        flagOwned(target);
        restoreEngines(target);
        addGarageEntry(player, target, "HIJACK");
    }
```

```java
    private int playerTactics(Entity player) {
        if (player == null) return 0;
        PlayerStatsComponent stats = STATS_M.get(player);
        if (stats == null) return 0;
        Integer v = stats.pointSkills.get(PointSkill.TACTICS);
        return v != null ? v : 0;
    }

    private static ShipSizeClass targetSizeClass(Entity target) {
        ShipDataComponent data = DATA_M.get(target);
        if (data != null && data.blueprint != null) return data.blueprint.sizeClass;
        return ShipSizeClass.SMALL;
    }

    /** Minimum Tactics skill to hijack a hull of the given size class. */
    public static int requiredTacticsFor(ShipSizeClass sizeClass) {
        switch (sizeClass) {
            case SMALL:  return 0;
            case MEDIUM: return 15;
            default:     return 30;
        }
    }
```

> Remove the old `// TODO: wire Tactics skill...` comment on the former stub `playerTactics` — it is now wired.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.boarding.BoardingHijackTacticsTest"`
Expected: PASS (3 tests). Also re-run `BoardingResolutionSystemTest` (its existing hijack test uses no stats → tactics 0, SMALL/no-blueprint target → required 0 → still succeeds):
Run: `./gradlew core:test --tests "com.galacticodyssey.ship.boarding.BoardingResolutionSystemTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingResolutionSystem.java core/src/test/java/com/galacticodyssey/ship/boarding/BoardingHijackTacticsTest.java
git commit -m "feat(boarding): gate hijack on player Tactics skill scaled by hull size"
```

---

## Task 3: Scrapped-ship removal (dispose natives + remove entity)

Scrapping now physically removes the ship: dispose its interior Bullet world (via `ShipInteriorComponent.dispose()`), remove + dispose its exterior rigid body from the main dynamics world, and remove the entity from the engine. All native access is null-guarded so headless tests pass.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingResolutionSystem.java`
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/BoardingScrapRemovalTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.economy.components.CargoBayComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingResolutionSystem;
import com.galacticodyssey.ship.components.ShipDataComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardingScrapRemovalTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity player;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingResolutionSystem(eventBus, null)); // null main world (headless)
        player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new PlayerWalletComponent());
        player.add(new CargoBayComponent());
        player.add(new PlayerGarageComponent());
        engine.addEntity(player);
    }

    @Test
    void scrapRemovesShipEntityFromEngine() {
        Entity target = new Entity();
        ShipDataComponent data = new ShipDataComponent();
        data.hullHp = 400f; data.currentHullHp = 400f;
        target.add(data);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = target;
        op.phase = BoardingPhase.RESOLVING;
        op.playerIsAggressor = true;
        target.add(op);
        engine.addEntity(target);
        int before = engine.getEntities().size();

        eventBus.publish(new BoardingResolutionChosenEvent(target, BoardingOutcome.SCRAP));
        engine.update(0.016f);

        assertFalse(player.getComponent(CargoBayComponent.class).contents.isEmpty(),
            "scrap still deposits materials");
        assertEquals(before - 1, engine.getEntities().size(), "scrapped ship removed from engine");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.boarding.BoardingScrapRemovalTest"`
Expected: FAIL — current `scrap` marks `currentHullHp = 0` but never removes the entity (`before - 1` assertion fails).

- [ ] **Step 3: Add a main-world field + teardown to `BoardingResolutionSystem`**

Add imports:
```java
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.galacticodyssey.core.components.PhysicsBodyComponent;
```

Add the field + update the constructor to accept the main world (nullable):
```java
    private final btDiscreteDynamicsWorld mainWorld; // nullable (headless tests)
```
Change the constructor signature from `BoardingResolutionSystem(EventBus eventBus)` to:
```java
    public BoardingResolutionSystem(EventBus eventBus, btDiscreteDynamicsWorld mainWorld) {
        super(PRIORITY);
        this.eventBus = eventBus;
        this.mainWorld = mainWorld;
        eventBus.subscribe(BoardingResolutionChosenEvent.class, pending::add);
    }
```

In `scrap(...)`, after the existing material/credit deposit and `data.currentHullHp = 0f;`, append a destruction call:
```java
        destroyShip(target);
```

Add the teardown method:
```java
    /** Disposes a scrapped ship's Bullet natives and removes its entity from the engine. */
    private void destroyShip(Entity target) {
        ShipInteriorComponent interior = target.getComponent(ShipInteriorComponent.class);
        if (interior != null) interior.dispose();

        PhysicsBodyComponent physics = target.getComponent(PhysicsBodyComponent.class);
        if (physics != null && physics.body != null) {
            if (mainWorld != null) mainWorld.removeRigidBody(physics.body);
            physics.body.dispose();
            if (physics.shape != null) physics.shape.dispose();
            physics.body = null;
        }
        getEngine().removeEntity(target);
    }
```

Add the import for `ShipInteriorComponent` if not present:
```java
import com.galacticodyssey.ship.components.ShipInteriorComponent;
```

> Note: `resolve()` continues after `scrap()` to set `op.phase = RESOLVED` and publish `BoardingResolvedEvent`. The Ashley `Entity` object remains valid (its components are still readable) after `removeEntity`, so these trailing operations are harmless. The interior `active=false` line in `resolve()` is a no-op on the disposed component (just a boolean).

- [ ] **Step 4: Update `GameWorld` construction**

Change:
```java
        engine.addSystem(new BoardingResolutionSystem(eventBus));
```
to:
```java
        engine.addSystem(new BoardingResolutionSystem(eventBus, bulletPhysicsSystem.getDynamicsWorld()));
```

- [ ] **Step 5: Run tests + commit**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.boarding.BoardingScrapRemovalTest" --tests "com.galacticodyssey.ship.boarding.BoardingResolutionSystemTest"`
Expected: PASS (the existing `scrapAddsCargoCreditsAndRemovesShip` test reads `currentHullHp` BEFORE removal in `resolve()`'s flow — it still passes because the component is readable post-remove; if it instead asserts the entity is still in the engine, update it to expect removal).
> If `BoardingResolutionSystemTest.scrapAddsCargoCreditsAndRemovesShip` now fails only because it asserts the ship still exists, adjust that assertion to expect the entity removed (the test name already says "RemovesShip").

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingResolutionSystem.java core/src/main/java/com/galacticodyssey/core/GameWorld.java core/src/test/java/com/galacticodyssey/ship/boarding/BoardingScrapRemovalTest.java
git commit -m "feat(boarding): scrap disposes ship natives and removes the entity"
```

---

## Task 4: Complete bidirectional boarding (attacker spawn, repel, enemy-capture)

When an NPC breaches the player's ship (`ShipBreachedEvent` with `playerIsAggressor == false`), `BoardingCombatSystem` spawns attackers in the player's interior and advances the op to `INTERIOR_COMBAT`. The inverted win: the player clearing all attackers → `REPELLED` (no loss); the player dying → `ENEMY_CAPTURE` (ship lost). Both auto-resolve (no menu).

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOutcome.java`
- Modify: `core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOperationComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingCombatSystem.java`
- Modify: `core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingResolutionSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/ship/boarding/EnemyBoardingCombatTest.java`

- [ ] **Step 1: Add the `REPELLED` outcome + `attackersRemaining` field**

In `BoardingOutcome.java`, add `REPELLED`:
```java
public enum BoardingOutcome {
    HIJACK,
    SCRAP,
    RANSOM,
    TOW,
    /** Inverted operation: an NPC captured the player's ship. */
    ENEMY_CAPTURE,
    /** Inverted operation: the player repelled the NPC boarders. */
    REPELLED
}
```

In `BoardingOperationComponent.java`, next to the transient `defendersRemaining`, add:
```java
    /** Transient: live attacker count when an NPC boards the player. Not persisted. */
    public transient int attackersRemaining;
```

- [ ] **Step 2: Write the failing test**

```java
package com.galacticodyssey.ship.boarding;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.combat.events.EntityKilledEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.PlayerTagComponent;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.AttachMethod;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.BoardingPhase;
import com.galacticodyssey.ship.boarding.OwnedShipComponent.Owner;
import com.galacticodyssey.ship.boarding.events.BoardingResolvedEvent;
import com.galacticodyssey.ship.boarding.events.ShipBreachedEvent;
import com.galacticodyssey.ship.boarding.systems.BoardingCombatSystem;
import com.galacticodyssey.ship.boarding.systems.BoardingResolutionSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnemyBoardingCombatTest {

    private EventBus eventBus;
    private Engine engine;
    private Entity player;
    private Entity playerShip;
    private Entity npc;
    private final List<BoardingResolvedEvent> resolved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new BoardingCombatSystem(eventBus));
        engine.addSystem(new BoardingResolutionSystem(eventBus, null));

        player = new Entity();
        player.add(new PlayerTagComponent());
        player.add(new TransformComponent());
        HealthComponent ph = new HealthComponent();
        ph.currentHP = 100f; ph.maxHP = 100f; ph.alive = true;
        player.add(ph);
        engine.addEntity(player);

        npc = new Entity();
        npc.add(new TransformComponent());
        engine.addEntity(npc);

        playerShip = new Entity();
        playerShip.add(new TransformComponent());
        BoardingDefenseComponent def = new BoardingDefenseComponent();
        def.defenderCount = 3; // becomes attacker count when NPC boards
        def.defenderHealth = 40f;
        playerShip.add(def);
        BoardingOperationComponent op = new BoardingOperationComponent();
        op.targetShip = playerShip;
        op.aggressorShip = npc;
        op.phase = BoardingPhase.BREACHED;
        op.playerIsAggressor = false;
        playerShip.add(op);
        engine.addEntity(playerShip);

        eventBus.subscribe(BoardingResolvedEvent.class, resolved::add);
    }

    private void breach() {
        eventBus.publish(new ShipBreachedEvent(npc, playerShip, AttachMethod.BREACH_POD, new Vector3()));
        engine.update(0.016f);
    }

    private ImmutableArray<Entity> attackers() {
        return engine.getEntitiesFor(Family.all(BoardingDefenderComponent.class).get());
    }

    @Test
    void npcBreachSpawnsAttackersAndEntersCombat() {
        breach();
        assertEquals(3, attackers().size());
        for (Entity a : attackers()) {
            assertTrue(a.getComponent(BoardingDefenderComponent.class).attacker, "spawned as attacker");
        }
        assertEquals(BoardingPhase.INTERIOR_COMBAT,
            playerShip.getComponent(BoardingOperationComponent.class).phase);
    }

    @Test
    void clearingAllAttackersRepels() {
        breach();
        List<Entity> as = new ArrayList<>();
        for (Entity a : attackers()) as.add(a);
        for (Entity a : as) {
            a.getComponent(HealthComponent.class).alive = false;
            eventBus.publish(new EntityKilledEvent(a, player));
        }
        engine.update(0.016f); // combat detects repel → publishes chosen(REPELLED)
        engine.update(0.016f); // resolution applies it
        assertEquals(BoardingPhase.RESOLVED,
            playerShip.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(BoardingOutcome.REPELLED, resolved.get(resolved.size() - 1).outcome);
        assertNull(playerShip.getComponent(OwnedShipComponent.class),
            "repelled: player keeps the ship");
    }

    @Test
    void playerDeathYieldsEnemyCapture() {
        breach();
        player.getComponent(HealthComponent.class).alive = false;
        eventBus.publish(new EntityKilledEvent(player, npc));
        engine.update(0.016f); // combat detects player death → chosen(ENEMY_CAPTURE)
        engine.update(0.016f); // resolution applies it
        assertEquals(BoardingPhase.RESOLVED,
            playerShip.getComponent(BoardingOperationComponent.class).phase);
        assertEquals(BoardingOutcome.ENEMY_CAPTURE, resolved.get(resolved.size() - 1).outcome);
        assertEquals(Owner.NPC, playerShip.getComponent(OwnedShipComponent.class).owner);
    }
}
```

- [ ] **Step 2a: Run test to verify it fails**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.boarding.EnemyBoardingCombatTest"`
Expected: FAIL — NPC breach spawns nothing; no repel/capture.

- [ ] **Step 3: Spawn attackers + inverted win in `BoardingCombatSystem`**

Add imports:
```java
import com.galacticodyssey.ship.boarding.BoardingOutcome;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.events.ShipBreachedEvent;
import com.galacticodyssey.core.components.PlayerTagComponent;
```
(Some may already be imported — keep one copy.)

Add a mapper + a player-family reference if not present (the system already has `players`). Add:
```java
    private final java.util.Queue<ShipBreachedEvent> breaches = new java.util.ArrayDeque<>();
```

In the constructor, subscribe to `ShipBreachedEvent`:
```java
        eventBus.subscribe(ShipBreachedEvent.class, breaches::add);
```

In `update(...)`, drain breaches BEFORE the kill queue / win checks:
```java
        ShipBreachedEvent breach;
        while ((breach = breaches.poll()) != null) {
            onNpcBreach(breach);
        }
```

Add the NPC-breach spawner (mirrors `spawnForEntry` but tags attackers and targets the player's ship):
```java
    /** When an NPC breaches the player's ship, spawn attackers and begin inverted combat. */
    private void onNpcBreach(ShipBreachedEvent event) {
        Entity target = event.target; // the player's ship
        BoardingOperationComponent op = OP_M.get(target);
        if (op == null || op.playerIsAggressor || op.spawned) return;
        op.spawned = true;
        op.phase = BoardingPhase.INTERIOR_COMBAT;

        TransformComponent t = TRANSFORM_M.get(target);
        Vector3 base = (t != null) ? t.position : Vector3.Zero;
        BoardingDefenseComponent def = DEF_M.get(target);
        int count = (def != null) ? def.defenderCount : 2;
        float hp = (def != null) ? def.defenderHealth : 100f;
        float dmg = (def != null) ? def.defenderDamage : 12f;
        op.attackersRemaining = count;
        for (int i = 0; i < count; i++) {
            spawnCombatant(target, base, i, hp, dmg, /*attacker*/ true);
        }
    }
```

In `countDefenderKill(...)`, also count attacker deaths toward `attackersRemaining`. Replace the body so it handles both roles:
```java
    private void countDefenderKill(Entity dead) {
        if (dead == null) return;
        BoardingDefenderComponent tag = DEFENDER_M.get(dead);
        if (tag == null || tag.awayTeam || tag.counted || tag.operationShip == null) return;
        BoardingOperationComponent op = OP_M.get(tag.operationShip);
        if (op == null) return;
        tag.counted = true;
        if (tag.attacker) {
            op.attackersRemaining = Math.max(0, op.attackersRemaining - 1);
        } else {
            op.defendersRemaining = Math.max(0, op.defendersRemaining - 1);
        }
    }
```

Add player-death detection: in `update(...)`, the kill queue is already drained for defender/attacker counting; also check whether a drained kill was the player. Simplest: handle it inside the kill drain loop. Change the kill-draining loop to also flag player death per operation. Add a field:
```java
    private boolean playerDied;
```
In `update`, before draining kills set `playerDied = false;`, and in the kill loop, after `countDefenderKill(kill.target)`, add:
```java
            if (kill.target != null && kill.target.getComponent(PlayerTagComponent.class) != null) {
                playerDied = true;
            }
```

Extend `checkWinConditions()` to handle the inverted (NPC-aggressor) case. Inside the per-operation loop, replace the single player-aggressor check with:
```java
            if (op.playerIsAggressor) {
                if (op.defendersRemaining <= 0 || bridgeCaptured(target)) {
                    fireCleared(op, target);
                }
            } else {
                // NPC boarded the player: player wins by clearing attackers, loses on death.
                if (playerDied) {
                    autoResolve(op, target, BoardingOutcome.ENEMY_CAPTURE);
                } else if (op.attackersRemaining <= 0) {
                    autoResolve(op, target, BoardingOutcome.REPELLED);
                }
            }
```

Add the auto-resolve helper (sets RESOLVING then publishes the chosen outcome so `BoardingResolutionSystem` applies it — no menu):
```java
    /** NPC-aggressor outcomes resolve without a player menu. */
    private void autoResolve(BoardingOperationComponent op, Entity target, BoardingOutcome outcome) {
        op.phase = BoardingPhase.RESOLVING;
        eventBus.publish(new BoardingResolutionChosenEvent(target, outcome));
    }
```

> Ensure `spawnedThisFrame` logic does not suppress these inverted checks: attacker spawn happens in `onNpcBreach`; if your `spawnedThisFrame` guard skips win checks on the spawn frame, that is fine here too (player death/clear happen on later frames in the tests). Keep the guard consistent with the player-aggressor path.

- [ ] **Step 4: Handle `REPELLED` + `ENEMY_CAPTURE` in `BoardingResolutionSystem.resolve`**

In the `switch (outcome)` in `resolve(...)`, replace the `ENEMY_CAPTURE` no-op case and add `REPELLED`:
```java
            case ENEMY_CAPTURE: enemyCapture(target); break;
            case REPELLED:      /* player kept the ship; nothing to apply */ break;
```

Add the helper:
```java
    /** The NPC captured the player's ship: flag NPC ownership. (Escape-pod/respawn flow is future work.) */
    private void enemyCapture(Entity target) {
        OwnedShipComponent owned = target.getComponent(OwnedShipComponent.class);
        if (owned == null) { owned = new OwnedShipComponent(); target.add(owned); }
        owned.owner = OwnedShipComponent.Owner.NPC;
    }
```

> `resolve()`'s guard already requires `phase == RESOLVING`; `autoResolve` sets that before publishing, so the inverted outcomes flow through the same finalization (sets RESOLVED, deactivates interior, publishes `BoardingResolvedEvent`).

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.boarding.EnemyBoardingCombatTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Run the whole boarding suite + commit**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.boarding.*"`
Expected: all green.

```bash
git add core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOutcome.java core/src/main/java/com/galacticodyssey/ship/boarding/BoardingOperationComponent.java core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingCombatSystem.java core/src/main/java/com/galacticodyssey/ship/boarding/systems/BoardingResolutionSystem.java core/src/test/java/com/galacticodyssey/ship/boarding/EnemyBoardingCombatTest.java
git commit -m "feat(boarding): complete bidirectional boarding (attacker spawn, repel, enemy-capture)"
```

---

## Task 5: Scene2D resolution menu (`BoardingResolutionPanel`) + GameScreen mount

A HUD panel that appears on `BoardingResolutionRequestedEvent` with four buttons (Hijack / Scrap / Ransom / Tow), each publishing `BoardingResolutionChosenEvent`, and hides on `BoardingResolvedEvent`. Mirrors `VehicleBayPanel`. This is GL/Scene2D glue — verify it compiles and the project builds; visual behavior is confirmed by manual playtest.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/ui/BoardingResolutionPanel.java`
- Modify: `core/src/main/java/com/galacticodyssey/ui/GameScreen.java`

- [ ] **Step 1: Write `BoardingResolutionPanel`**

```java
package com.galacticodyssey.ui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.ship.boarding.BoardingOutcome;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionChosenEvent;
import com.galacticodyssey.ship.boarding.events.BoardingResolutionRequestedEvent;
import com.galacticodyssey.ship.boarding.events.BoardingResolvedEvent;

/**
 * Boarding resolution menu. Shown when a player-aggressor boarding reaches RESOLVING
 * ({@link BoardingResolutionRequestedEvent}); each button publishes a
 * {@link BoardingResolutionChosenEvent}. Hides on {@link BoardingResolvedEvent}.
 * Mirrors {@link VehicleBayPanel}'s Scene2D + EventBus pattern.
 */
public class BoardingResolutionPanel extends Table implements Disposable {

    private final EventBus eventBus;
    private final EventBus.EventListener<BoardingResolutionRequestedEvent> requestedListener;
    private final EventBus.EventListener<BoardingResolvedEvent> resolvedListener;
    private Entity target;

    public BoardingResolutionPanel(Skin skin, EventBus eventBus) {
        this.eventBus = eventBus;
        pad(16);
        setVisible(false);

        Label title = new Label("Boarding Successful", skin, "header");
        add(title).colspan(4).padBottom(12).row();
        add(button(skin, "Hijack", BoardingOutcome.HIJACK)).pad(6);
        add(button(skin, "Scrap", BoardingOutcome.SCRAP)).pad(6);
        add(button(skin, "Ransom", BoardingOutcome.RANSOM)).pad(6);
        add(button(skin, "Tow", BoardingOutcome.TOW)).pad(6);

        requestedListener = e -> show(e.target);
        resolvedListener = e -> hide();
        eventBus.subscribe(BoardingResolutionRequestedEvent.class, requestedListener);
        eventBus.subscribe(BoardingResolvedEvent.class, resolvedListener);
    }

    private TextButton button(Skin skin, String label, BoardingOutcome outcome) {
        TextButton b = new TextButton(label, skin, "default");
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                choose(outcome);
            }
        });
        return b;
    }

    /** Publishes the chosen outcome for the current target and hides the panel. */
    void choose(BoardingOutcome outcome) {
        if (target == null) return;
        eventBus.publish(new BoardingResolutionChosenEvent(target, outcome));
        hide();
    }

    public void show(Entity target) {
        this.target = target;
        setVisible(true);
    }

    public void hide() {
        this.target = null;
        setVisible(false);
    }

    @Override
    public void dispose() {
        eventBus.unsubscribe(BoardingResolutionRequestedEvent.class, requestedListener);
        eventBus.unsubscribe(BoardingResolvedEvent.class, resolvedListener);
    }
}
```

- [ ] **Step 2: Mount it in `GameScreen` (mirror the VehicleBayPanel wiring)**

Read `GameScreen.java` and replicate the `vehicleBayPanel`/`vehicleBayStage` lifecycle for a `boardingResolutionPanel`/`boardingResolutionStage`:

1. **Fields** (next to `vehicleBayPanel`/`vehicleBayStage`, ~line 180):
```java
    private BoardingResolutionPanel boardingResolutionPanel;
    private com.badlogic.gdx.scenes.scene2d.Stage boardingResolutionStage;
```

2. **Construction** (in `buildScreenTabManager()`, next to the vehicle bay panel block, ~line 756):
```java
        boardingResolutionStage = new com.badlogic.gdx.scenes.scene2d.Stage(
            new com.badlogic.gdx.utils.viewport.ScreenViewport());
        boardingResolutionPanel = new BoardingResolutionPanel(game.getSkin(), gameWorld.getEventBus());
        boardingResolutionPanel.setFillParent(true);
        boardingResolutionStage.addActor(boardingResolutionPanel);
```

3. **Input** (in the input-multiplexer setup, add the stage FIRST when the panel is visible, alongside `vehicleBayStage`, ~line 413). Add right after the `vehicleBayStage` processor line:
```java
        if (boardingResolutionStage != null && boardingResolutionPanel != null
                && boardingResolutionPanel.isVisible()) {
            inputMultiplexer.addProcessor(boardingResolutionStage);
        }
```
> If `vehicleBayStage` is added unconditionally, mirror that; the panel ignores input while invisible anyway. Keeping it conditional avoids stealing input when hidden.

4. **Render** (where `vehicleBayStage` is acted/drawn, ~line 1214):
```java
        if (boardingResolutionPanel != null && boardingResolutionPanel.isVisible()) {
            boardingResolutionStage.act(delta);
            boardingResolutionStage.draw();
        }
```

5. **Resize** (next to the vehicle bay viewport update, ~line 1306):
```java
        if (boardingResolutionStage != null) boardingResolutionStage.getViewport().update(width, height, true);
```

6. **Dispose** (next to the vehicle bay disposal, ~line 1390):
```java
        if (boardingResolutionPanel != null) { boardingResolutionPanel.dispose(); boardingResolutionPanel = null; }
        if (boardingResolutionStage != null) { boardingResolutionStage.dispose(); boardingResolutionStage = null; }
```

Add the import at the top of `GameScreen.java` if the class isn't in the same package (it is — both are `com.galacticodyssey.ui`, so no import needed).

> **Input-multiplexer note:** because the panel must be re-added/removed as visibility changes, ensure the multiplexer is rebuilt when the panel toggles. If `GameScreen` rebuilds the multiplexer each frame or on state change, the conditional add above suffices. If it builds it once, instead add `boardingResolutionStage` unconditionally near `vehicleBayStage` (the invisible panel consumes no input because its actors aren't hit when `setVisible(false)`), and rely on the render-gate. Match whichever approach `vehicleBayStage` uses; read the surrounding code and follow it. Report DONE_WITH_CONCERNS if the existing multiplexer lifecycle makes event-driven visibility awkward.

- [ ] **Step 3: Verify it compiles and the project builds**

Run: `./gradlew core:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the full test suite (sanity — no regressions)**

Run: `./gradlew core:test`
Expected: BUILD SUCCESSFUL except the two known-unrelated `OrbitalMechanicsIntegrationTest` failures.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/ui/BoardingResolutionPanel.java core/src/main/java/com/galacticodyssey/ui/GameScreen.java
git commit -m "feat(boarding): Scene2D resolution menu (hijack/scrap/ransom/tow) mounted in GameScreen"
```

---

## Task 6: Final verification

- [ ] **Step 1: Full boarding suite**

Run: `./gradlew core:test --tests "com.galacticodyssey.ship.boarding.*" --tests "com.galacticodyssey.player.systems.HostileInteriorExitTest"`
Expected: all green.

- [ ] **Step 2: Full suite (confirm only the 2 known failures)**

Run: `./gradlew core:test`
Expected: only `OrbitalMechanicsIntegrationTest.planetMovesAfterOrbitTicks` and `shipInsidePlanetSOIGetsPlanetAsDominant` fail.

- [ ] **Step 3: Commit any final fixups (if needed)**

---

## Plan D complete

Closes the documented MVP gaps: NPC boarding is faction-gated (where reputation is wired), hijack requires `Tactics` scaled by hull size, scrapped ships are removed and their natives disposed, the player can be boarded and either repel attackers or lose the ship, and a Scene2D menu lets the player choose hijack/scrap/ransom/tow in-game.

**Still deferred (out of scope, with reason):**
- **Dual-world interior physics** (boarders co-located in the per-interior `btDynamicsWorld`): a project-wide on-foot-interior gap (entering your own ship doesn't co-locate either), high-risk, only verifiable by manual playtest — belongs in a dedicated effort.
- **Away-team escort AI** (formation/follow): minor polish.
- **NPC ship faction assignment** + wiring `ReputationManager` into `GameWorld`: separate content/economy concern (the faction filter is ready for it).
- **Escape-pod / respawn flow** after `ENEMY_CAPTURE`: larger gameplay-loop feature.
