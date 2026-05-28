# Grenade System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a data-driven grenade system supporting hand-thrown and launcher-fired frag grenades, with timed fuse cooking and impact detonation, reusing the existing explosion and damage pipelines.

**Architecture:** Grenades are ECS entities composed of `ProjectileComponent` (physics) + `GrenadeComponent` (fuse/cook state). Two new systems handle fuse management (`GrenadeSystem`) and throw input/spawning (`GrenadeThrowSystem`). All blast damage flows through the existing `DetonationEvent` → `ExplosionSystem` → `DamageSystem` pipeline. Grenade properties are data-driven via `grenades.json`.

**Tech Stack:** Java 17, libGDX, Ashley ECS, JUnit 5, Gradle

**Spec:** `docs/superpowers/specs/2026-05-27-grenade-system-design.md`

---

### Task 1: Add FuseType and ThrowState enums

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/combat/CombatEnums.java`

- [ ] **Step 1: Add FuseType and ThrowState enums**

Open `CombatEnums.java` and add these two enums alongside the existing enums (DamageType, FiringMode, etc.):

```java
public enum FuseType {
    TIMED,
    IMPACT,
    PROXIMITY
}

public enum ThrowState {
    IDLE,
    COOKING,
    THROWN
}
```

- [ ] **Step 2: Verify compilation**

Run: `gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/CombatEnums.java
git commit -m "feat(combat): add FuseType and ThrowState enums for grenade system"
```

---

### Task 2: Create GrenadeData and GrenadeDataRegistry

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/data/GrenadeData.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/data/GrenadeDataRegistry.java`
- Create: `core/src/main/resources/data/combat/grenades.json`
- Test: `core/src/test/java/com/galacticodyssey/combat/GrenadeDataRegistryTest.java`

- [ ] **Step 1: Create grenades.json**

Create `core/src/main/resources/data/combat/grenades.json`:

```json
[
  {
    "id": "frag",
    "displayName": "M4 Fragmentation Grenade",
    "fuseType": "TIMED",
    "fuseDuration": 3.0,
    "cookable": true,
    "throwForce": 18.0,
    "mass": 0.4,
    "drag": 0.05,
    "gravity": true,
    "damage": 50.0,
    "blastRadius": 8.0,
    "blastFraction": 0.5,
    "thermalFraction": 0.1,
    "fragmentFraction": 0.4,
    "isDirectional": false,
    "bounceRestitution": 0.3,
    "maxBounces": 5,
    "statusEffect": null,
    "statusEffectChance": 0.0,
    "maxCarry": 4
  }
]
```

- [ ] **Step 2: Create GrenadeData**

Create `core/src/main/java/com/galacticodyssey/combat/data/GrenadeData.java`:

```java
package com.galacticodyssey.combat.data;

import com.galacticodyssey.combat.CombatEnums.FuseType;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public class GrenadeData {
    public String id;
    public String displayName;
    public FuseType fuseType = FuseType.TIMED;
    public float fuseDuration = 3.0f;
    public boolean cookable;
    public float throwForce = 18.0f;
    public float mass = 0.4f;
    public float drag = 0.05f;
    public boolean gravity = true;
    public float damage = 50.0f;
    public float blastRadius = 8.0f;
    public float blastFraction = 0.5f;
    public float thermalFraction = 0.1f;
    public float fragmentFraction = 0.4f;
    public boolean isDirectional;
    public float bounceRestitution = 0.3f;
    public int maxBounces = 5;
    public StatusEffectType statusEffect;
    public float statusEffectChance;
    public int maxCarry = 4;
}
```

- [ ] **Step 3: Create GrenadeDataRegistry**

Create `core/src/main/java/com/galacticodyssey/combat/data/GrenadeDataRegistry.java`:

```java
package com.galacticodyssey.combat.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.Map;

public class GrenadeDataRegistry {
    private final Map<String, GrenadeData> grenades = new HashMap<>();

    public void loadFromFile(String path) {
        Json json = new Json();
        JsonReader reader = new JsonReader();
        JsonValue root = reader.parse(Gdx.files.internal(path));
        for (JsonValue entry = root.child; entry != null; entry = entry.next) {
            GrenadeData data = json.readValue(GrenadeData.class, entry);
            grenades.put(data.id, data);
        }
    }

    public GrenadeData get(String id) {
        return grenades.get(id);
    }

    public boolean has(String id) {
        return grenades.containsKey(id);
    }

    public int size() {
        return grenades.size();
    }
}
```

- [ ] **Step 4: Write the test**

Create `core/src/test/java/com/galacticodyssey/combat/GrenadeDataRegistryTest.java`:

```java
package com.galacticodyssey.combat;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.galacticodyssey.combat.CombatEnums.FuseType;
import com.galacticodyssey.combat.data.GrenadeData;
import com.galacticodyssey.combat.data.GrenadeDataRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrenadeDataRegistryTest {

    @BeforeAll
    static void initGdx() {
        if (Gdx.app == null) {
            new HeadlessApplication(new com.badlogic.gdx.ApplicationAdapter() {}, new HeadlessApplicationConfiguration());
        }
    }

    @Test
    void loadsFragGrenadeFromJson() {
        GrenadeDataRegistry registry = new GrenadeDataRegistry();
        registry.loadFromFile("data/combat/grenades.json");

        assertEquals(1, registry.size());
        assertTrue(registry.has("frag"));

        GrenadeData frag = registry.get("frag");
        assertEquals("frag", frag.id);
        assertEquals("M4 Fragmentation Grenade", frag.displayName);
        assertEquals(FuseType.TIMED, frag.fuseType);
        assertEquals(3.0f, frag.fuseDuration, 0.001f);
        assertTrue(frag.cookable);
        assertEquals(18.0f, frag.throwForce, 0.001f);
        assertEquals(50.0f, frag.damage, 0.001f);
        assertEquals(8.0f, frag.blastRadius, 0.001f);
        assertEquals(0.5f, frag.blastFraction, 0.001f);
        assertEquals(0.3f, frag.bounceRestitution, 0.001f);
        assertEquals(5, frag.maxBounces);
        assertEquals(4, frag.maxCarry);
    }

    @Test
    void returnsNullForUnknownId() {
        GrenadeDataRegistry registry = new GrenadeDataRegistry();
        registry.loadFromFile("data/combat/grenades.json");

        assertNull(registry.get("nonexistent"));
        assertFalse(registry.has("nonexistent"));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.GrenadeDataRegistryTest" --info`
Expected: 2 tests PASSED

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/data/GrenadeData.java
git add core/src/main/java/com/galacticodyssey/combat/data/GrenadeDataRegistry.java
git add core/src/main/resources/data/combat/grenades.json
git add core/src/test/java/com/galacticodyssey/combat/GrenadeDataRegistryTest.java
git commit -m "feat(combat): add GrenadeData, GrenadeDataRegistry, and grenades.json"
```

---

### Task 3: Create GrenadeComponent and GrenadeInventoryComponent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/components/GrenadeComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/components/GrenadeInventoryComponent.java`

- [ ] **Step 1: Create GrenadeComponent**

Create `core/src/main/java/com/galacticodyssey/combat/components/GrenadeComponent.java`:

```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.galacticodyssey.combat.CombatEnums.FuseType;

public class GrenadeComponent implements Component {
    public static final ComponentMapper<GrenadeComponent> MAPPER =
            ComponentMapper.getFor(GrenadeComponent.class);

    public String grenadeTypeId;
    public FuseType fuseType = FuseType.TIMED;
    public float fuseTimer;
    public float fuseDuration;
    public float cookTime;
    public boolean cookable;
    public float proximityRadius;
    public boolean detonated;
    public float bounceRestitution = 0.3f;
    public int maxBounces = 5;
    public int bounceCount;

    public float damage;
    public float blastRadius;
    public float blastFraction = 0.5f;
    public float thermalFraction = 0.1f;
    public float fragmentFraction = 0.4f;
    public boolean isDirectional;
}
```

- [ ] **Step 2: Create GrenadeInventoryComponent**

Create `core/src/main/java/com/galacticodyssey/combat/components/GrenadeInventoryComponent.java`:

```java
package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.galacticodyssey.combat.CombatEnums.ThrowState;
import java.util.HashMap;
import java.util.Map;

public class GrenadeInventoryComponent implements Component {
    public static final ComponentMapper<GrenadeInventoryComponent> MAPPER =
            ComponentMapper.getFor(GrenadeInventoryComponent.class);

    public final Map<String, Integer> grenades = new HashMap<>();
    public String selectedGrenadeType;
    public int maxPerType = 4;
    public float cookStartTime;
    public ThrowState throwState = ThrowState.IDLE;
}
```

- [ ] **Step 3: Verify compilation**

Run: `gradlew.bat :core:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/components/GrenadeComponent.java
git add core/src/main/java/com/galacticodyssey/combat/components/GrenadeInventoryComponent.java
git commit -m "feat(combat): add GrenadeComponent and GrenadeInventoryComponent"
```

---

### Task 4: Create GrenadeThrowEvent and GrenadeBounceEvent

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/events/GrenadeThrowEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/combat/events/GrenadeBounceEvent.java`

- [ ] **Step 1: Create GrenadeThrowEvent**

Create `core/src/main/java/com/galacticodyssey/combat/events/GrenadeThrowEvent.java`:

```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public class GrenadeThrowEvent {
    public final Entity thrower;
    public final Vector3 position;
    public final Vector3 direction;
    public final String grenadeTypeId;

    public GrenadeThrowEvent(Entity thrower, Vector3 position, Vector3 direction, String grenadeTypeId) {
        this.thrower = thrower;
        this.position = new Vector3(position);
        this.direction = new Vector3(direction);
        this.grenadeTypeId = grenadeTypeId;
    }
}
```

- [ ] **Step 2: Create GrenadeBounceEvent**

Create `core/src/main/java/com/galacticodyssey/combat/events/GrenadeBounceEvent.java`:

```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public class GrenadeBounceEvent {
    public final Entity grenade;
    public final Vector3 position;
    public final Vector3 surfaceNormal;

    public GrenadeBounceEvent(Entity grenade, Vector3 position, Vector3 surfaceNormal) {
        this.grenade = grenade;
        this.position = new Vector3(position);
        this.surfaceNormal = new Vector3(surfaceNormal);
    }
}
```

- [ ] **Step 3: Verify compilation and commit**

Run: `gradlew.bat :core:compileJava`

```
git add core/src/main/java/com/galacticodyssey/combat/events/GrenadeThrowEvent.java
git add core/src/main/java/com/galacticodyssey/combat/events/GrenadeBounceEvent.java
git commit -m "feat(combat): add GrenadeThrowEvent and GrenadeBounceEvent"
```

---

### Task 5: Extend DetonationEvent with blast configuration

The current `DetonationEvent` only carries `damage`, `damageType`, and `areaOfEffect`. The `ExplosionSystem.buildExplosionData()` uses hardcoded default fractions (0.4/0.3/0.3). Grenades need custom fractions (frag uses 0.5/0.1/0.4). Add optional blast fraction fields with backward-compatible defaults.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/combat/events/DetonationEvent.java`
- Modify: `core/src/main/java/com/galacticodyssey/combat/systems/ExplosionSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/ExplosionSystemTest.java`

- [ ] **Step 1: Write test for custom blast fractions**

Add to `core/src/test/java/com/galacticodyssey/combat/ExplosionSystemTest.java`:

```java
@Test
void customBlastFractionsAreUsedFromDetonationEvent() {
    // Create a target at distance 3.0 from origin
    Entity target = new Entity();
    target.add(new TransformComponent());
    target.getComponent(TransformComponent.class).position.set(3f, 0f, 0f);
    target.add(new HealthComponent());
    target.add(new ExplosionAffectedComponent());
    engine.addEntity(target);

    // Fire two detonations: one default fractions, one custom
    List<BlastDamageEvent> defaultEvents = new ArrayList<>();
    List<BlastDamageEvent> customEvents = new ArrayList<>();

    eventBus.subscribe(BlastDamageEvent.class, defaultEvents::add);

    // Default fractions (0.4 blast, 0.3 thermal, 0.3 fragment)
    DetonationEvent defaultDet = new DetonationEvent(
            new Entity(), new Vector3(0, 0, 0), 50f,
            DamageType.EXPLOSIVE, 10f);
    eventBus.publish(defaultDet);
    engine.update(0.016f);

    eventBus.unsubscribe(BlastDamageEvent.class, defaultEvents::add);
    eventBus.subscribe(BlastDamageEvent.class, customEvents::add);

    // Custom fractions (0.8 blast, 0.1 thermal, 0.1 fragment)
    DetonationEvent customDet = new DetonationEvent(
            new Entity(), new Vector3(0, 0, 0), 50f,
            DamageType.EXPLOSIVE, 10f,
            0.8f, 0.1f, 0.1f, false);
    eventBus.publish(customDet);
    engine.update(0.016f);

    assertEquals(1, defaultEvents.size());
    assertEquals(1, customEvents.size());
    // Higher blast fraction should produce more damage at same distance
    assertTrue(customEvents.get(0).damage > defaultEvents.get(0).damage,
            "Custom blast fraction 0.8 should produce more damage than default 0.4");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.ExplosionSystemTest.customBlastFractionsAreUsedFromDetonationEvent" --info`
Expected: FAIL — DetonationEvent constructor with 9 args doesn't exist

- [ ] **Step 3: Extend DetonationEvent**

Modify `core/src/main/java/com/galacticodyssey/combat/events/DetonationEvent.java`. Add new fields and a second constructor. Keep the original constructor delegating to the new one with defaults:

```java
package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;

public class DetonationEvent {
    public final Entity owner;
    public final Vector3 position;
    public final float damage;
    public final DamageType damageType;
    public final float areaOfEffect;
    public final float blastFraction;
    public final float thermalFraction;
    public final float fragmentFraction;
    public final boolean isDirectional;

    public DetonationEvent(Entity owner, Vector3 position, float damage,
                           DamageType damageType, float areaOfEffect) {
        this(owner, position, damage, damageType, areaOfEffect, 0.4f, 0.3f, 0.3f, false);
    }

    public DetonationEvent(Entity owner, Vector3 position, float damage,
                           DamageType damageType, float areaOfEffect,
                           float blastFraction, float thermalFraction,
                           float fragmentFraction, boolean isDirectional) {
        this.owner = owner;
        this.position = new Vector3(position);
        this.damage = damage;
        this.damageType = damageType;
        this.areaOfEffect = areaOfEffect;
        this.blastFraction = blastFraction;
        this.thermalFraction = thermalFraction;
        this.fragmentFraction = fragmentFraction;
        this.isDirectional = isDirectional;
    }
}
```

- [ ] **Step 4: Update ExplosionSystem.buildExplosionData()**

In `core/src/main/java/com/galacticodyssey/combat/systems/ExplosionSystem.java`, modify the `buildExplosionData()` method to use the event's fraction fields instead of relying on ExplosionData defaults:

After the line that sets `totalEnergy`, add:
```java
data.blastFraction = event.blastFraction;
data.thermalFraction = event.thermalFraction;
data.fragmentFraction = event.fragmentFraction;
data.isDirectional = event.isDirectional;
```

- [ ] **Step 5: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.ExplosionSystemTest" --info`
Expected: ALL tests PASSED (both new and existing — backward compatibility verified)

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/events/DetonationEvent.java
git add core/src/main/java/com/galacticodyssey/combat/systems/ExplosionSystem.java
git add core/src/test/java/com/galacticodyssey/combat/ExplosionSystemTest.java
git commit -m "feat(combat): extend DetonationEvent with configurable blast fractions"
```

---

### Task 6: Add grenade input to CombatInputComponent and CombatInputSystem

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/combat/components/CombatInputComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/combat/systems/CombatInputSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/CombatInputSystemTest.java`

- [ ] **Step 1: Write test for grenade input transfer**

Add to `core/src/test/java/com/galacticodyssey/combat/CombatInputSystemTest.java`:

```java
@Test
void grenadeThrowInputTransfersToComponent() {
    combatInputSystem.setGrenadeThrowInput(true);
    combatInputSystem.setGrenadeThrowHeldInput(true);
    engine.update(0.016f);

    CombatInputComponent input = entity.getComponent(CombatInputComponent.class);
    assertTrue(input.grenadeThrowRequested);
    assertTrue(input.grenadeThrowHeld);

    // One-shot flag should clear after one tick
    engine.update(0.016f);
    assertFalse(input.grenadeThrowRequested);
    assertTrue(input.grenadeThrowHeld); // Held persists
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.CombatInputSystemTest.grenadeThrowInputTransfersToComponent" --info`
Expected: FAIL — `grenadeThrowRequested` field doesn't exist

- [ ] **Step 3: Add fields to CombatInputComponent**

Add to `core/src/main/java/com/galacticodyssey/combat/components/CombatInputComponent.java`:

```java
public boolean grenadeThrowRequested;
public boolean grenadeThrowHeld;
```

- [ ] **Step 4: Add setter methods and processing to CombatInputSystem**

In `core/src/main/java/com/galacticodyssey/combat/systems/CombatInputSystem.java`:

Add private pending fields:
```java
private boolean pendingGrenadeThrow;
private boolean pendingGrenadeThrowHeld;
```

Add setter methods:
```java
public void setGrenadeThrowInput(boolean throwGrenade) {
    if (throwGrenade) pendingGrenadeThrow = true;
}

public void setGrenadeThrowHeldInput(boolean held) {
    pendingGrenadeThrowHeld = held;
}
```

In `processEntity()`, in the section that transfers pending flags to the component (around lines 137-144), add:
```java
input.grenadeThrowRequested = pendingGrenadeThrow;
input.grenadeThrowHeld = pendingGrenadeThrowHeld;
```

In the section that clears one-shot inputs (around lines 151-157), add:
```java
pendingGrenadeThrow = false;
```

- [ ] **Step 5: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.CombatInputSystemTest" --info`
Expected: ALL tests PASSED

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/components/CombatInputComponent.java
git add core/src/main/java/com/galacticodyssey/combat/systems/CombatInputSystem.java
git add core/src/test/java/com/galacticodyssey/combat/CombatInputSystemTest.java
git commit -m "feat(combat): add grenade throw input to CombatInputSystem"
```

---

### Task 7: Create GrenadeSystem (fuse logic and detonation)

The `GrenadeSystem` processes all grenade entities each tick. For ALL fuse types: check `fuseTimer <= 0` and detonate. For `TIMED` fuse, the timer decrements naturally. For `IMPACT` fuse, `ProjectileSystem` sets `fuseTimer = 0` on collision (Task 9). This gives uniform detonation handling — GrenadeSystem doesn't need to subscribe to any events.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/systems/GrenadeSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/combat/GrenadeSystemTest.java`

- [ ] **Step 1: Write test for timed fuse detonation**

Create `core/src/test/java/com/galacticodyssey/combat/GrenadeSystemTest.java`:

```java
package com.galacticodyssey.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FuseType;
import com.galacticodyssey.combat.components.GrenadeComponent;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.combat.events.DetonationEvent;
import com.galacticodyssey.combat.systems.GrenadeSystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrenadeSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private List<DetonationEvent> detonations;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        engine.addSystem(new GrenadeSystem(eventBus));
        detonations = new ArrayList<>();
        eventBus.subscribe(DetonationEvent.class, detonations::add);
    }

    private Entity createGrenade(FuseType fuseType, float fuseTimer, Vector3 position) {
        Entity grenade = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(position);
        grenade.add(transform);

        ProjectileComponent projectile = new ProjectileComponent();
        projectile.velocity.set(5f, 0f, 0f);
        projectile.damage = 50f;
        projectile.damageType = DamageType.EXPLOSIVE;
        projectile.areaOfEffect = 8f;
        projectile.owner = new Entity();
        grenade.add(projectile);

        GrenadeComponent gc = new GrenadeComponent();
        gc.grenadeTypeId = "frag";
        gc.fuseType = fuseType;
        gc.fuseTimer = fuseTimer;
        gc.fuseDuration = 3.0f;
        gc.damage = 50f;
        gc.blastRadius = 8f;
        gc.blastFraction = 0.5f;
        gc.thermalFraction = 0.1f;
        gc.fragmentFraction = 0.4f;
        grenade.add(gc);

        return grenade;
    }

    @Test
    void timedFuseDetonatesWhenTimerExpires() {
        Entity grenade = createGrenade(FuseType.TIMED, 1.0f, new Vector3(10f, 0f, 0f));
        engine.addEntity(grenade);

        // Tick 0.5s — should NOT detonate yet
        engine.update(0.5f);
        assertTrue(detonations.isEmpty());

        // Tick another 0.6s — total 1.1s, exceeds 1.0s fuse
        engine.update(0.6f);
        assertEquals(1, detonations.size());

        DetonationEvent det = detonations.get(0);
        assertEquals(10f, det.position.x, 0.01f);
        assertEquals(DamageType.EXPLOSIVE, det.damageType);
        assertEquals(8f, det.areaOfEffect, 0.01f);
        assertEquals(0.5f, det.blastFraction, 0.001f);
    }

    @Test
    void timedFuseGrenadeRemovedAfterDetonation() {
        Entity grenade = createGrenade(FuseType.TIMED, 0.1f, new Vector3(0, 0, 0));
        engine.addEntity(grenade);

        engine.update(0.2f);

        assertEquals(1, detonations.size());
        // Entity should be removed from engine
        assertEquals(0, engine.getEntities().size());
    }

    @Test
    void grenadeDoesNotDoubleDetonate() {
        Entity grenade = createGrenade(FuseType.TIMED, 0.1f, new Vector3(0, 0, 0));
        engine.addEntity(grenade);

        engine.update(0.2f);
        engine.update(0.2f);

        assertEquals(1, detonations.size());
    }

    @Test
    void impactGrenadeDetonatesWhenFuseTimerSetToZero() {
        // Simulates what ProjectileSystem does on collision for IMPACT grenades:
        // it sets fuseTimer = 0, then GrenadeSystem detonates on next processEntity
        Entity grenade = createGrenade(FuseType.IMPACT, 5.0f, new Vector3(7f, 0f, 0f));
        engine.addEntity(grenade);

        // Simulate ProjectileSystem setting fuseTimer to 0 on collision
        GrenadeComponent gc = GrenadeComponent.MAPPER.get(grenade);
        gc.fuseTimer = 0f;

        engine.update(0.016f);

        assertEquals(1, detonations.size());
        assertEquals(7f, detonations.get(0).position.x, 0.01f);
    }

    @Test
    void impactGrenadeDoesNotDetonateWithoutCollision() {
        Entity grenade = createGrenade(FuseType.IMPACT, 5.0f, new Vector3(0, 0, 0));
        engine.addEntity(grenade);

        // fuseTimer stays at 5.0 — no collision, no detonation
        engine.update(0.016f);
        engine.update(0.016f);

        assertTrue(detonations.isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.GrenadeSystemTest" --info`
Expected: FAIL — GrenadeSystem class doesn't exist

- [ ] **Step 3: Implement GrenadeSystem**

Create `core/src/main/java/com/galacticodyssey/combat/systems/GrenadeSystem.java`:

```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FuseType;
import com.galacticodyssey.combat.components.GrenadeComponent;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.combat.events.DetonationEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

public class GrenadeSystem extends IteratingSystem {
    public static final int PRIORITY = 8;

    private final EventBus eventBus;
    private final Array<Entity> pendingRemovals = new Array<>();

    public GrenadeSystem(EventBus eventBus) {
        super(Family.all(GrenadeComponent.class, ProjectileComponent.class,
                TransformComponent.class).get(), PRIORITY);
        this.eventBus = eventBus;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        GrenadeComponent gc = GrenadeComponent.MAPPER.get(entity);
        if (gc.detonated) return;

        // For TIMED fuse: decrement each tick
        // For IMPACT fuse: ProjectileSystem sets fuseTimer = 0 on collision (Task 9)
        // Both converge here: fuseTimer <= 0 means detonate
        if (gc.fuseType == FuseType.TIMED) {
            gc.fuseTimer -= deltaTime;
        }

        if (gc.fuseTimer <= 0f) {
            detonate(entity, gc);
        }
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        for (Entity e : pendingRemovals) {
            getEngine().removeEntity(e);
        }
        pendingRemovals.clear();
    }

    private void detonate(Entity entity, GrenadeComponent gc) {
        gc.detonated = true;
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        ProjectileComponent proj = entity.getComponent(ProjectileComponent.class);

        eventBus.publish(new DetonationEvent(
                proj.owner,
                transform.position,
                gc.damage,
                DamageType.EXPLOSIVE,
                gc.blastRadius,
                gc.blastFraction,
                gc.thermalFraction,
                gc.fragmentFraction,
                gc.isDirectional
        ));

        pendingRemovals.add(entity);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.GrenadeSystemTest" --info`
Expected: 5 tests PASSED (timedFuseDetonatesWhenTimerExpires, timedFuseGrenadeRemovedAfterDetonation, grenadeDoesNotDoubleDetonate, impactGrenadeDetonatesWhenFuseTimerSetToZero, impactGrenadeDoesNotDetonateWithoutCollision)

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/systems/GrenadeSystem.java
git add core/src/test/java/com/galacticodyssey/combat/GrenadeSystemTest.java
git commit -m "feat(combat): add GrenadeSystem with timed and impact fuse detonation"
```

---

### Task 8: Create GrenadeThrowSystem (input, cooking, spawning)

The `GrenadeThrowSystem` reads grenade throw input from `CombatInputComponent`, manages the cook state machine on `GrenadeInventoryComponent`, spawns grenade entities on release, and handles overcook self-detonation.

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/combat/systems/GrenadeThrowSystem.java`
- Create: `core/src/test/java/com/galacticodyssey/combat/GrenadeThrowSystemTest.java`

- [ ] **Step 1: Write test for basic grenade throw**

Create `core/src/test/java/com/galacticodyssey/combat/GrenadeThrowSystemTest.java`:

```java
package com.galacticodyssey.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.FuseType;
import com.galacticodyssey.combat.CombatEnums.ThrowState;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.GrenadeData;
import com.galacticodyssey.combat.data.GrenadeDataRegistry;
import com.galacticodyssey.combat.events.DetonationEvent;
import com.galacticodyssey.combat.events.GrenadeThrowEvent;
import com.galacticodyssey.combat.systems.GrenadeThrowSystem;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrenadeThrowSystemTest {

    private Engine engine;
    private EventBus eventBus;
    private GrenadeDataRegistry registry;
    private Entity player;
    private CombatInputComponent input;
    private GrenadeInventoryComponent inventory;
    private List<GrenadeThrowEvent> throwEvents;
    private List<DetonationEvent> detonations;
    private float worldTime;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        worldTime = 0f;

        registry = new GrenadeDataRegistry();
        GrenadeData frag = new GrenadeData();
        frag.id = "frag";
        frag.fuseType = FuseType.TIMED;
        frag.fuseDuration = 3.0f;
        frag.cookable = true;
        frag.throwForce = 18.0f;
        frag.mass = 0.4f;
        frag.drag = 0.05f;
        frag.gravity = true;
        frag.damage = 50.0f;
        frag.blastRadius = 8.0f;
        frag.blastFraction = 0.5f;
        frag.thermalFraction = 0.1f;
        frag.fragmentFraction = 0.4f;
        frag.bounceRestitution = 0.3f;
        frag.maxBounces = 5;
        frag.maxCarry = 4;
        registry.register(frag);

        GrenadeThrowSystem system = new GrenadeThrowSystem(eventBus, registry, () -> worldTime);
        engine.addSystem(system);

        player = new Entity();
        TransformComponent transform = new TransformComponent();
        transform.position.set(0, 1.5f, 0);
        player.add(transform);

        input = new CombatInputComponent();
        input.aimDirection.set(0, 0, -1);
        player.add(input);

        inventory = new GrenadeInventoryComponent();
        inventory.grenades.put("frag", 3);
        inventory.selectedGrenadeType = "frag";
        player.add(inventory);

        engine.addEntity(player);

        throwEvents = new ArrayList<>();
        detonations = new ArrayList<>();
        eventBus.subscribe(GrenadeThrowEvent.class, throwEvents::add);
        eventBus.subscribe(DetonationEvent.class, detonations::add);
    }

    @Test
    void throwButtonStartsCooking() {
        input.grenadeThrowRequested = true;
        input.grenadeThrowHeld = true;
        engine.update(0.016f);

        assertEquals(ThrowState.COOKING, inventory.throwState);
    }

    @Test
    void releaseThrowButtonSpawnsGrenade() {
        // Press
        input.grenadeThrowRequested = true;
        input.grenadeThrowHeld = true;
        engine.update(0.016f);
        worldTime += 0.5f;

        // Release
        input.grenadeThrowRequested = false;
        input.grenadeThrowHeld = false;
        engine.update(0.5f);

        assertEquals(ThrowState.IDLE, inventory.throwState);
        assertEquals(2, inventory.grenades.get("frag"));
        assertEquals(1, throwEvents.size());

        // Grenade entity should exist in engine (player + grenade)
        int grenadeCount = engine.getEntitiesFor(
                Family.all(GrenadeComponent.class, ProjectileComponent.class).get()).size();
        assertEquals(1, grenadeCount);
    }

    @Test
    void cookingReducesFuseTimer() {
        // Press and cook for 1 second
        input.grenadeThrowRequested = true;
        input.grenadeThrowHeld = true;
        worldTime = 10.0f;
        engine.update(0.016f);
        worldTime = 11.0f;

        // Release after 1s cook
        input.grenadeThrowHeld = false;
        engine.update(1.0f);

        // Find spawned grenade
        Entity grenade = engine.getEntitiesFor(
                Family.all(GrenadeComponent.class).get()).first();
        GrenadeComponent gc = GrenadeComponent.MAPPER.get(grenade);

        // Fuse should be fuseDuration(3.0) - cookTime(1.0) = 2.0
        assertEquals(2.0f, gc.fuseTimer, 0.1f);
    }

    @Test
    void cannotThrowWithEmptyInventory() {
        inventory.grenades.put("frag", 0);

        input.grenadeThrowRequested = true;
        input.grenadeThrowHeld = true;
        engine.update(0.016f);

        assertEquals(ThrowState.IDLE, inventory.throwState);
        assertTrue(throwEvents.isEmpty());
    }

    @Test
    void overcookDetonatesInHand() {
        input.grenadeThrowRequested = true;
        input.grenadeThrowHeld = true;
        worldTime = 0f;
        engine.update(0.016f);

        // Cook for longer than fuseDuration (3.0s)
        worldTime = 3.1f;
        engine.update(3.1f);

        assertEquals(1, detonations.size());
        assertEquals(ThrowState.IDLE, inventory.throwState);
        assertEquals(2, inventory.grenades.get("frag"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.GrenadeThrowSystemTest" --info`
Expected: FAIL — GrenadeThrowSystem doesn't exist

- [ ] **Step 3: Add register method to GrenadeDataRegistry**

The test uses `registry.register(frag)` for manual registration in tests. Add to `GrenadeDataRegistry.java`:

```java
public void register(GrenadeData data) {
    grenades.put(data.id, data);
}
```

- [ ] **Step 4: Implement GrenadeThrowSystem**

Create `core/src/main/java/com/galacticodyssey/combat/systems/GrenadeThrowSystem.java`:

```java
package com.galacticodyssey.combat.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FuseType;
import com.galacticodyssey.combat.CombatEnums.ThrowState;
import com.galacticodyssey.combat.components.CombatInputComponent;
import com.galacticodyssey.combat.components.GrenadeComponent;
import com.galacticodyssey.combat.components.GrenadeInventoryComponent;
import com.galacticodyssey.combat.components.ProjectileComponent;
import com.galacticodyssey.combat.data.GrenadeData;
import com.galacticodyssey.combat.data.GrenadeDataRegistry;
import com.galacticodyssey.combat.events.DetonationEvent;
import com.galacticodyssey.combat.events.GrenadeThrowEvent;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;

import java.util.function.Supplier;

public class GrenadeThrowSystem extends IteratingSystem {
    public static final int PRIORITY = 3;
    private static final float ARC_BIAS_DEGREES = 15f;
    private static final float FORWARD_OFFSET = 1.0f;

    private final EventBus eventBus;
    private final GrenadeDataRegistry grenadeRegistry;
    private final Supplier<Float> worldTimeSupplier;

    public GrenadeThrowSystem(EventBus eventBus, GrenadeDataRegistry grenadeRegistry,
                              Supplier<Float> worldTimeSupplier) {
        super(Family.all(GrenadeInventoryComponent.class, CombatInputComponent.class,
                TransformComponent.class).get(), PRIORITY);
        this.eventBus = eventBus;
        this.grenadeRegistry = grenadeRegistry;
        this.worldTimeSupplier = worldTimeSupplier;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        GrenadeInventoryComponent inv = GrenadeInventoryComponent.MAPPER.get(entity);
        CombatInputComponent input = entity.getComponent(CombatInputComponent.class);
        TransformComponent transform = entity.getComponent(TransformComponent.class);

        if (inv.selectedGrenadeType == null) return;
        GrenadeData data = grenadeRegistry.get(inv.selectedGrenadeType);
        if (data == null) return;

        switch (inv.throwState) {
            case IDLE:
                if (input.grenadeThrowRequested && input.grenadeThrowHeld) {
                    int count = inv.grenades.getOrDefault(inv.selectedGrenadeType, 0);
                    if (count > 0) {
                        inv.throwState = ThrowState.COOKING;
                        inv.cookStartTime = worldTimeSupplier.get();
                    }
                }
                break;

            case COOKING:
                float cookTime = worldTimeSupplier.get() - inv.cookStartTime;

                if (data.cookable && cookTime >= data.fuseDuration) {
                    detonateInHand(entity, inv, data, transform);
                    break;
                }

                if (!input.grenadeThrowHeld) {
                    throwGrenade(entity, inv, data, input, transform, cookTime);
                }
                break;

            case THROWN:
                inv.throwState = ThrowState.IDLE;
                break;
        }
    }

    private void detonateInHand(Entity entity, GrenadeInventoryComponent inv,
                                GrenadeData data, TransformComponent transform) {
        eventBus.publish(new DetonationEvent(
                entity, transform.position, data.damage,
                DamageType.EXPLOSIVE, data.blastRadius,
                data.blastFraction, data.thermalFraction,
                data.fragmentFraction, data.isDirectional));

        int count = inv.grenades.getOrDefault(inv.selectedGrenadeType, 0);
        inv.grenades.put(inv.selectedGrenadeType, Math.max(0, count - 1));
        inv.throwState = ThrowState.IDLE;
    }

    private void throwGrenade(Entity player, GrenadeInventoryComponent inv,
                              GrenadeData data, CombatInputComponent input,
                              TransformComponent playerTransform, float cookTime) {
        Vector3 aimDir = new Vector3(input.aimDirection).nor();

        // Apply upward arc bias
        float arcRad = ARC_BIAS_DEGREES * MathUtils.degreesToRadians;
        Vector3 right = new Vector3(aimDir).crs(Vector3.Y).nor();
        Vector3 up = new Vector3(right).crs(aimDir).nor();
        aimDir.mulAdd(up, MathUtils.sin(arcRad)).nor();

        float speed = data.throwForce / data.mass;
        Vector3 spawnPos = new Vector3(playerTransform.position)
                .mulAdd(input.aimDirection, FORWARD_OFFSET);

        Entity grenade = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(spawnPos);
        grenade.add(transform);

        ProjectileComponent proj = new ProjectileComponent();
        proj.velocity.set(aimDir).scl(speed);
        proj.speed = speed;
        proj.damage = data.damage;
        proj.damageType = DamageType.EXPLOSIVE;
        proj.areaOfEffect = data.blastRadius;
        proj.owner = player;
        proj.mass = data.mass;
        proj.dragCoeff = data.drag;
        proj.affectedByGravity = data.gravity;
        proj.lifetime = data.fuseDuration + 5f;
        grenade.add(proj);

        GrenadeComponent gc = new GrenadeComponent();
        gc.grenadeTypeId = data.id;
        gc.fuseType = data.fuseType;
        gc.fuseDuration = data.fuseDuration;
        gc.fuseTimer = data.cookable ? data.fuseDuration - cookTime : data.fuseDuration;
        gc.cookTime = data.cookable ? cookTime : 0f;
        gc.cookable = data.cookable;
        gc.bounceRestitution = data.bounceRestitution;
        gc.maxBounces = data.maxBounces;
        gc.damage = data.damage;
        gc.blastRadius = data.blastRadius;
        gc.blastFraction = data.blastFraction;
        gc.thermalFraction = data.thermalFraction;
        gc.fragmentFraction = data.fragmentFraction;
        gc.isDirectional = data.isDirectional;
        grenade.add(gc);

        getEngine().addEntity(grenade);

        int count = inv.grenades.getOrDefault(inv.selectedGrenadeType, 0);
        inv.grenades.put(inv.selectedGrenadeType, Math.max(0, count - 1));
        inv.throwState = ThrowState.IDLE;

        eventBus.publish(new GrenadeThrowEvent(player, spawnPos, aimDir, data.id));
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.GrenadeThrowSystemTest" --info`
Expected: 5 tests PASSED

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/systems/GrenadeThrowSystem.java
git add core/src/main/java/com/galacticodyssey/combat/data/GrenadeDataRegistry.java
git add core/src/test/java/com/galacticodyssey/combat/GrenadeThrowSystemTest.java
git commit -m "feat(combat): add GrenadeThrowSystem with cooking and overcook detonation"
```

---

### Task 9: Modify ProjectileSystem for grenade bounce handling

When a projectile with a `GrenadeComponent` collides with a target entity and the fuse type is NOT `IMPACT`, the grenade should bounce instead of triggering a `ProjectileHitEvent`. Reflect velocity off a simplified normal, apply restitution, and publish `GrenadeBounceEvent`.

**Note:** Full surface/terrain bounce requires Bullet physics raycasting against the collision world, which is beyond this task's scope. This task implements entity-collision bounce (grenades pass through/bounce off entities) and simple ground-plane bounce (y ≤ 0). Wall bouncing is a future enhancement.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/combat/systems/ProjectileSystem.java`
- Test: `core/src/test/java/com/galacticodyssey/combat/ProjectileSystemTest.java`

- [ ] **Step 1: Write test for grenade bounce on entity collision**

Add to `core/src/test/java/com/galacticodyssey/combat/ProjectileSystemTest.java`:

```java
@Test
void timedGrenadeBouncesOnEntityCollisionInsteadOfHitting() {
    Entity thrower = new Entity();

    // Create a grenade with TIMED fuse heading toward a target
    Entity grenade = new Entity();
    TransformComponent grenadeTransform = new TransformComponent();
    grenadeTransform.position.set(0, 1f, 0);
    grenade.add(grenadeTransform);

    ProjectileComponent proj = new ProjectileComponent();
    proj.velocity.set(10f, 0f, 0f);
    proj.speed = 10f;
    proj.damage = 50f;
    proj.damageType = DamageType.EXPLOSIVE;
    proj.areaOfEffect = 8f;
    proj.owner = thrower;
    proj.lifetime = 10f;
    grenade.add(proj);

    GrenadeComponent gc = new GrenadeComponent();
    gc.fuseType = FuseType.TIMED;
    gc.fuseTimer = 3.0f;
    gc.bounceRestitution = 0.3f;
    gc.maxBounces = 5;
    grenade.add(gc);

    engine.addEntity(grenade);

    // Create target entity within collision radius
    Entity target = new Entity();
    TransformComponent targetTransform = new TransformComponent();
    targetTransform.position.set(0.5f, 1f, 0f);
    target.add(targetTransform);
    target.add(new HitboxComponent());
    target.add(new HealthComponent());
    engine.addEntity(target);

    List<ProjectileHitEvent> hits = new ArrayList<>();
    List<GrenadeBounceEvent> bounces = new ArrayList<>();
    eventBus.subscribe(ProjectileHitEvent.class, hits::add);
    eventBus.subscribe(GrenadeBounceEvent.class, bounces::add);

    engine.update(0.016f);

    assertTrue(hits.isEmpty(), "Timed grenade should NOT publish ProjectileHitEvent");
    assertEquals(1, bounces.size(), "Should publish GrenadeBounceEvent");
    // Grenade should still exist (not removed)
    assertTrue(engine.getEntities().size() > 1);
    // Velocity should have been reflected and reduced
    assertTrue(proj.velocity.x < 10f, "Velocity should decrease after bounce");
}

@Test
void impactGrenadeSetsFuseTimerToZeroOnEntityCollision() {
    Entity thrower = new Entity();

    Entity grenade = new Entity();
    TransformComponent grenadeTransform = new TransformComponent();
    grenadeTransform.position.set(0, 1f, 0);
    grenade.add(grenadeTransform);

    ProjectileComponent proj = new ProjectileComponent();
    proj.velocity.set(10f, 0f, 0f);
    proj.speed = 10f;
    proj.damage = 50f;
    proj.damageType = DamageType.EXPLOSIVE;
    proj.areaOfEffect = 8f;
    proj.owner = thrower;
    proj.lifetime = 10f;
    grenade.add(proj);

    GrenadeComponent gc = new GrenadeComponent();
    gc.fuseType = FuseType.IMPACT;
    gc.fuseTimer = 5.0f;
    grenade.add(gc);

    engine.addEntity(grenade);

    Entity target = new Entity();
    TransformComponent targetTransform = new TransformComponent();
    targetTransform.position.set(0.5f, 1f, 0f);
    target.add(targetTransform);
    target.add(new HitboxComponent());
    target.add(new HealthComponent());
    engine.addEntity(target);

    List<ProjectileHitEvent> hits = new ArrayList<>();
    eventBus.subscribe(ProjectileHitEvent.class, hits::add);

    engine.update(0.016f);

    // IMPACT grenade should NOT publish ProjectileHitEvent (explosion handles damage)
    assertTrue(hits.isEmpty(), "Impact grenade should not publish ProjectileHitEvent");
    // fuseTimer should be set to 0 so GrenadeSystem detonates it
    assertEquals(0f, gc.fuseTimer, 0.001f);
    // Grenade entity should NOT be removed by ProjectileSystem (GrenadeSystem handles removal)
    assertTrue(engine.getEntities().size() > 1, "Grenade entity should still exist for GrenadeSystem");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.ProjectileSystemTest.timedGrenadeBouncesOnEntityCollisionInsteadOfHitting" --info`
Expected: FAIL — current code publishes ProjectileHitEvent for all projectile collisions

- [ ] **Step 3: Modify ProjectileSystem collision handler**

In `core/src/main/java/com/galacticodyssey/combat/systems/ProjectileSystem.java`, modify the collision detection section (around lines 156-184).

Add import for `GrenadeComponent`, `GrenadeBounceEvent`, and `FuseType` at the top.

In the collision handler, before publishing `ProjectileHitEvent`, add a grenade bounce check:

```java
// Inside the collision detection block, after finding a hit:
GrenadeComponent gc = GrenadeComponent.MAPPER.get(entity);
if (gc != null) {
    if (gc.fuseType == FuseType.IMPACT) {
        // Trigger detonation: set fuseTimer to 0 so GrenadeSystem detonates next tick
        gc.fuseTimer = 0f;
        proj.velocity.setZero();
        // Do NOT publish ProjectileHitEvent — explosion handles all damage
        // Do NOT remove entity — GrenadeSystem handles removal after detonation
    } else {
        // Bounce instead of hit (TIMED, PROXIMITY)
        if (gc.bounceCount < gc.maxBounces) {
            Vector3 normal = new Vector3(transform.position).sub(targetTransform.position).nor();
            float dot = proj.velocity.dot(normal);
            proj.velocity.mulAdd(normal, -2f * dot);
            proj.velocity.scl(gc.bounceRestitution);
            gc.bounceCount++;

            if (proj.velocity.len() < 0.5f) {
                proj.velocity.setZero();
            }

            eventBus.publish(new GrenadeBounceEvent(entity, transform.position, normal));
        } else {
            proj.velocity.setZero();
        }
    }
    continue; // Skip normal projectile hit processing for ALL grenades
}

// ... existing ProjectileHitEvent publishing code ...
```

- [ ] **Step 4: Add ground-plane bounce check**

In the physics integration section of `processEntity()` (after position update, around line 149), add ground bounce for grenades:

```java
// After position update
GrenadeComponent gc = GrenadeComponent.MAPPER.get(entity);
if (gc != null && gc.fuseType != FuseType.IMPACT && transform.position.y <= 0f) {
    transform.position.y = 0f;
    if (gc.bounceCount < gc.maxBounces) {
        proj.velocity.y = -proj.velocity.y * gc.bounceRestitution;
        proj.velocity.x *= gc.bounceRestitution;
        proj.velocity.z *= gc.bounceRestitution;
        gc.bounceCount++;
        if (proj.velocity.len() < 0.5f) {
            proj.velocity.setZero();
        }
        eventBus.publish(new GrenadeBounceEvent(entity, transform.position, Vector3.Y));
    } else {
        proj.velocity.setZero();
    }
}
```

- [ ] **Step 5: Run all ProjectileSystem tests**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.ProjectileSystemTest" --info`
Expected: ALL tests PASSED (existing + new)

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/systems/ProjectileSystem.java
git add core/src/test/java/com/galacticodyssey/combat/ProjectileSystemTest.java
git commit -m "feat(combat): add grenade bounce handling to ProjectileSystem"
```

---

### Task 10: Add Thumper GL launcher integration

Connect the grenade launcher weapon to the grenade system. Add `grenadeTypeId` to weapon data so `ProjectileSystem` attaches a `GrenadeComponent` to launcher-fired projectiles.

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/combat/data/WeaponFrameData.java`
- Modify: `core/src/main/java/com/galacticodyssey/combat/components/RangedWeaponComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/combat/systems/ProjectileSystem.java`
- Modify: `core/src/main/resources/data/weapons/frames.json`
- Test: `core/src/test/java/com/galacticodyssey/combat/ProjectileSystemTest.java`

- [ ] **Step 1: Add grenadeTypeId to WeaponFrameData**

In `core/src/main/java/com/galacticodyssey/combat/data/WeaponFrameData.java`, add:

```java
public String grenadeTypeId;
```

- [ ] **Step 2: Add grenadeTypeId to RangedWeaponComponent**

In `core/src/main/java/com/galacticodyssey/combat/components/RangedWeaponComponent.java`, add:

```java
public String grenadeTypeId;
```

The `WeaponStatsResolver` (or wherever weapons are assembled from frame data) needs to copy this field. Find the method that builds `RangedWeaponComponent` from `WeaponFrameData` and add:

```java
component.grenadeTypeId = frameData.grenadeTypeId;
```

- [ ] **Step 3: Write test for launcher GrenadeComponent attachment**

Add to `core/src/test/java/com/galacticodyssey/combat/ProjectileSystemTest.java`:

```java
@Test
void launcherProjectileGetsGrenadeComponent() {
    // Set up a GrenadeDataRegistry with frag data
    GrenadeData frag = new GrenadeData();
    frag.id = "frag";
    frag.fuseType = FuseType.TIMED;
    frag.fuseDuration = 3.0f;
    frag.damage = 50f;
    frag.blastRadius = 8f;
    frag.blastFraction = 0.5f;
    frag.thermalFraction = 0.1f;
    frag.fragmentFraction = 0.4f;
    frag.bounceRestitution = 0.3f;
    frag.maxBounces = 5;
    GrenadeDataRegistry grenadeRegistry = new GrenadeDataRegistry();
    grenadeRegistry.register(frag);

    // Inject the registry into ProjectileSystem
    projectileSystem.setGrenadeDataRegistry(grenadeRegistry);

    // Create shooter with weapon that has grenadeTypeId
    Entity shooter = new Entity();
    TransformComponent shooterTransform = new TransformComponent();
    shooterTransform.position.set(0, 0, 0);
    shooter.add(shooterTransform);

    RangedWeaponComponent weapon = new RangedWeaponComponent();
    weapon.damage = 50f;
    weapon.range = 120f;
    weapon.projectileSpeed = 40f;
    weapon.grenadeTypeId = "frag";
    weapon.damageType = DamageType.EXPLOSIVE;
    shooter.add(weapon);
    engine.addEntity(shooter);

    // Fire weapon
    eventBus.publish(new WeaponFiredEvent(shooter, new Vector3(0, 0, -1), false));
    engine.update(0.016f);

    // Find the spawned projectile
    Entity projectile = null;
    for (Entity e : engine.getEntitiesFor(Family.all(ProjectileComponent.class).get())) {
        if (e != shooter) {
            projectile = e;
            break;
        }
    }

    assertNotNull(projectile, "Projectile should be spawned");
    GrenadeComponent gc = GrenadeComponent.MAPPER.get(projectile);
    assertNotNull(gc, "Launcher projectile should have GrenadeComponent");
    assertEquals("frag", gc.grenadeTypeId);
    assertEquals(FuseType.TIMED, gc.fuseType);
    assertEquals(3.0f, gc.fuseDuration, 0.001f);
}
```

- [ ] **Step 4: Modify ProjectileSystem to attach GrenadeComponent**

In `ProjectileSystem.java`, add a field and setter for the grenade registry:

```java
private GrenadeDataRegistry grenadeRegistry;

public void setGrenadeDataRegistry(GrenadeDataRegistry registry) {
    this.grenadeRegistry = registry;
}
```

In the `onWeaponFired()` handler, after creating the projectile entity and adding `ProjectileComponent`, check for `grenadeTypeId`:

```java
RangedWeaponComponent weapon = entity.getComponent(RangedWeaponComponent.class);
// ... existing projectile creation code ...

// Attach GrenadeComponent if this is a grenade launcher
if (weapon.grenadeTypeId != null && grenadeRegistry != null) {
    GrenadeData gData = grenadeRegistry.get(weapon.grenadeTypeId);
    if (gData != null) {
        GrenadeComponent gc = new GrenadeComponent();
        gc.grenadeTypeId = gData.id;
        gc.fuseType = gData.fuseType;
        gc.fuseDuration = gData.fuseDuration;
        gc.fuseTimer = gData.fuseDuration;
        gc.cookTime = 0f;
        gc.cookable = false;
        gc.bounceRestitution = gData.bounceRestitution;
        gc.maxBounces = gData.maxBounces;
        gc.damage = gData.damage;
        gc.blastRadius = gData.blastRadius;
        gc.blastFraction = gData.blastFraction;
        gc.thermalFraction = gData.thermalFraction;
        gc.fragmentFraction = gData.fragmentFraction;
        gc.isDirectional = gData.isDirectional;
        projectileEntity.add(gc);
    }
}

engine.addEntity(projectileEntity);
```

- [ ] **Step 5: Add Thumper GL to frames.json**

Add to `core/src/main/resources/data/weapons/frames.json`:

```json
{
  "id": "heavy_grenade",
  "category": "HEAVY",
  "baseDamage": 90.0,
  "baseFireRate": 1.0,
  "baseSpread": 3.0,
  "baseRecoil": 8.0,
  "magSize": 6,
  "modSlotCount": 1,
  "weight": 6.5,
  "firingMode": "SEMI",
  "hitscan": false,
  "range": 120.0,
  "reloadTime": 4.5,
  "grenadeTypeId": "frag"
}
```

- [ ] **Step 6: Run tests**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.ProjectileSystemTest" --info`
Expected: ALL tests PASSED

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/galacticodyssey/combat/data/WeaponFrameData.java
git add core/src/main/java/com/galacticodyssey/combat/components/RangedWeaponComponent.java
git add core/src/main/java/com/galacticodyssey/combat/systems/ProjectileSystem.java
git add core/src/main/resources/data/weapons/frames.json
git add core/src/test/java/com/galacticodyssey/combat/ProjectileSystemTest.java
git commit -m "feat(combat): add Thumper GL launcher integration with GrenadeComponent attachment"
```

---

### Task 11: Update VFX event bindings

**Files:**
- Modify: `core/src/main/resources/data/vfx/vfx_event_bindings.json`

- [ ] **Step 1: Add grenade event bindings**

Add these entries to `core/src/main/resources/data/vfx/vfx_event_bindings.json`:

```json
"GrenadeThrowEvent": "muzzle_flash_ballistic",
"GrenadeBounceEvent": "impact_sparks"
```

The throw effect reuses `muzzle_flash_ballistic` as a placeholder. The bounce effect reuses `impact_sparks`. Both can be replaced with dedicated grenade VFX definitions later. Detonation VFX already works via `ProjectileHitEvent:EXPLOSIVE` → `impact_explosion`.

- [ ] **Step 2: Commit**

```
git add core/src/main/resources/data/vfx/vfx_event_bindings.json
git commit -m "feat(vfx): add grenade throw and bounce VFX event bindings"
```

---

### Task 12: Integration test — full grenade lifecycle

End-to-end test: player throws grenade → grenade flies with physics → timer expires → explosion damages target.

**Files:**
- Create: `core/src/test/java/com/galacticodyssey/combat/GrenadeIntegrationTest.java`

- [ ] **Step 1: Write integration test**

Create `core/src/test/java/com/galacticodyssey/combat/GrenadeIntegrationTest.java`:

```java
package com.galacticodyssey.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.combat.CombatEnums.*;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.combat.data.GrenadeData;
import com.galacticodyssey.combat.data.GrenadeDataRegistry;
import com.galacticodyssey.combat.events.*;
import com.galacticodyssey.combat.systems.*;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrenadeIntegrationTest {

    private Engine engine;
    private EventBus eventBus;
    private GrenadeDataRegistry grenadeRegistry;
    private float worldTime;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        engine = new Engine();
        worldTime = 0f;

        grenadeRegistry = new GrenadeDataRegistry();
        GrenadeData frag = new GrenadeData();
        frag.id = "frag";
        frag.fuseType = FuseType.TIMED;
        frag.fuseDuration = 3.0f;
        frag.cookable = true;
        frag.throwForce = 18.0f;
        frag.mass = 0.4f;
        frag.drag = 0.05f;
        frag.gravity = true;
        frag.damage = 50.0f;
        frag.blastRadius = 8.0f;
        frag.blastFraction = 0.5f;
        frag.thermalFraction = 0.1f;
        frag.fragmentFraction = 0.4f;
        frag.bounceRestitution = 0.3f;
        frag.maxBounces = 5;
        frag.maxCarry = 4;
        grenadeRegistry.register(frag);

        // Add all systems in priority order
        engine.addSystem(new GrenadeThrowSystem(eventBus, grenadeRegistry, () -> worldTime));
        ProjectileSystem projSystem = new ProjectileSystem(eventBus);
        projSystem.setGrenadeDataRegistry(grenadeRegistry);
        engine.addSystem(projSystem);
        engine.addSystem(new GrenadeSystem(eventBus));
        engine.addSystem(new ExplosionSystem(eventBus));
    }

    @Test
    void fullGrenadeLifecycle_throwCookExplodeDamage() {
        // Create player
        Entity player = new Entity();
        TransformComponent playerTransform = new TransformComponent();
        playerTransform.position.set(0, 1.5f, 0);
        player.add(playerTransform);

        CombatInputComponent input = new CombatInputComponent();
        input.aimDirection.set(1f, 0f, 0f);
        player.add(input);

        GrenadeInventoryComponent inv = new GrenadeInventoryComponent();
        inv.grenades.put("frag", 3);
        inv.selectedGrenadeType = "frag";
        player.add(inv);

        engine.addEntity(player);

        // Create target at distance 5 from expected landing zone
        Entity target = new Entity();
        TransformComponent targetTransform = new TransformComponent();
        targetTransform.position.set(5f, 1f, 0f);
        target.add(targetTransform);
        HealthComponent health = new HealthComponent();
        health.currentHP = 100f;
        health.maxHP = 100f;
        target.add(health);
        target.add(new ExplosionAffectedComponent());
        engine.addEntity(target);

        // Track events
        List<GrenadeThrowEvent> throws_ = new ArrayList<>();
        List<DetonationEvent> detonations = new ArrayList<>();
        List<BlastDamageEvent> blastDamages = new ArrayList<>();
        eventBus.subscribe(GrenadeThrowEvent.class, throws_::add);
        eventBus.subscribe(DetonationEvent.class, detonations::add);
        eventBus.subscribe(BlastDamageEvent.class, blastDamages::add);

        // Step 1: Press throw button (start cooking)
        input.grenadeThrowRequested = true;
        input.grenadeThrowHeld = true;
        worldTime = 0f;
        engine.update(0.016f);
        assertEquals(ThrowState.COOKING, inv.throwState);

        // Step 2: Release after 1s cook
        worldTime = 1.0f;
        input.grenadeThrowHeld = false;
        input.grenadeThrowRequested = false;
        engine.update(0.016f);

        assertEquals(1, throws_.size(), "Should publish GrenadeThrowEvent");
        assertEquals(2, inv.grenades.get("frag"), "Inventory should decrement");

        // Grenade entity should exist
        Family grenadeFamily = Family.all(GrenadeComponent.class, ProjectileComponent.class).get();
        assertEquals(1, engine.getEntitiesFor(grenadeFamily).size());

        // Verify fuse timer accounts for cook time
        Entity grenade = engine.getEntitiesFor(grenadeFamily).first();
        GrenadeComponent gc = GrenadeComponent.MAPPER.get(grenade);
        assertEquals(2.0f, gc.fuseTimer, 0.1f);

        // Step 3: Advance time until fuse expires (2.0s remaining)
        for (int i = 0; i < 130; i++) {
            engine.update(0.016f);
        }

        // Grenade should have detonated
        assertEquals(1, detonations.size(), "Grenade should detonate after fuse timer expires");
        assertEquals(0, engine.getEntitiesFor(grenadeFamily).size(), "Grenade entity should be removed");

        // Blast damage should hit the target (if within blast radius)
        DetonationEvent det = detonations.get(0);
        assertEquals(DamageType.EXPLOSIVE, det.damageType);
        assertEquals(8.0f, det.areaOfEffect, 0.01f);
        assertEquals(0.5f, det.blastFraction, 0.001f);
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.GrenadeIntegrationTest" --info`
Expected: PASSED

- [ ] **Step 3: Run full combat test suite**

Run: `gradlew.bat :core:test --tests "com.galacticodyssey.combat.*" --info`
Expected: ALL tests PASSED (no regressions)

- [ ] **Step 4: Commit**

```
git add core/src/test/java/com/galacticodyssey/combat/GrenadeIntegrationTest.java
git commit -m "test(combat): add end-to-end grenade lifecycle integration test"
```
