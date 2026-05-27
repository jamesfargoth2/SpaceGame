# Save / Persistence System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a save/persistence system using Component Snapshot architecture with Kryo binary serialization, multiplayer-ready save backend abstraction, hybrid world state, and schema migration.

**Architecture:** Each saveable Ashley Component implements `Snapshotable<S>` producing a plain POJO snapshot. Snapshots are serialized via Kryo to a folder-based save structure. Entity identity uses UUIDs (`PersistenceIdComponent`). Cross-entity references store UUIDs and resolve in a second pass on load. Procgen state is regenerated from the galaxy seed; only player modifications are persisted.

**Tech Stack:** Java 17, libGDX, Ashley ECS, Kryo 5.6.0, JUnit 5

**Spec:** `docs/superpowers/specs/2026-05-26-save-persistence-system-design.md`

---

## File Structure

### New files (core/src/main/java/com/galacticodyssey/persistence/)

| File | Responsibility |
|---|---|
| `PersistenceIdComponent.java` | UUID identity for saveable entities |
| `Snapshotable.java` | Interface: `takeSnapshot()` / `restoreFromSnapshot()` |
| `EntitySnapshot.java` | Container: one entity's UUID + all its component snapshots + tag set |
| `SaveBundle.java` | In-memory container for all save data (manifest, player, ships, systems, mods) |
| `ManifestData.java` | Save metadata: name, timestamp, version, galaxy seed, player UUID |
| `KryoRegistrar.java` | Single-point Kryo type registration with fixed IDs |
| `EntitySnapshotBuilder.java` | Iterates Ashley entities, collects snapshots, converts coords to galaxy-space |
| `ReferenceResolver.java` | UUID → Entity second pass after load |
| `SaveWriter.java` | Kryo serialization to files |
| `SaveReader.java` | Kryo deserialization from files |
| `SaveBackend.java` | Backend interface (writeSave, readSave, listSaves, deleteSave) |
| `LocalFileSaveBackend.java` | Folder-based file implementation of SaveBackend |
| `SaveCoordinator.java` | Orchestrates full save/load flow, auto-save timer/events |
| `WorldModification.java` | Delta record for player changes to a star system |
| `WorldModificationStore.java` | Collects and applies modification deltas |

### New files (persistence/snapshots/)

| File | Responsibility |
|---|---|
| `TransformSnapshot.java` | Galaxy-space double position + quaternion rotation |
| `HealthSnapshot.java` | HP, alive state |
| `PlayerStateSnapshot.java` | Player mode, current ship UUID, interaction target UUID |
| `MovementStateSnapshot.java` | Stamina, grounded, sprinting, crouching, slope, fall velocity |
| `FPSCameraSnapshot.java` | Pitch, yaw, eye height, camera distance |
| `PlayerWalletSnapshot.java` | Credits |
| `ShieldSnapshot.java` | Shield HP, recharge, type |
| `ArmorSnapshot.java` | Per-region armor ratings, resistances, durability |
| `RangedWeaponSnapshot.java` | Damage, ammo, firing mode, reload state |
| `MeleeWeaponSnapshot.java` | Damage, reach, weight class, directional modifiers |
| `WeaponInventorySnapshot.java` | Active slot, switch state |
| `StatusEffectsSnapshot.java` | Active effects list |
| `InventorySnapshot.java` | Grid dimensions, weight, item list with positions |
| `EquipmentSlotsSnapshot.java` | Slot → item mapping |
| `ShipDataSnapshot.java` | Blueprint seed, mass, thrust, HP |
| `ShipFlightSnapshot.java` | Thrust fractions, drag, throttle |
| `CargoBaySnapshot.java` | Capacity, contents map |
| `EngineSpecSnapshot.java` | ISP, thrust, throttle, gimbal |
| `FuelTankSnapshot.java` | Max/current mass, position, venting state |
| `ThermalStateSnapshot.java` | Per-subsystem temps, heat sink, radiator state |
| `StructuralIntegritySnapshot.java` | Per-zone health values |
| `CompartmentAtmosphereSnapshot.java` | O2/CO2/N2 pressures, temp, volume |
| `DockingStateSnapshot.java` | Phase, target UUID, approach geometry |
| `CombatAISnapshot.java` | Ranges, aggression, archetype, last known position |
| `SquadSnapshot.java` | Squad ID |
| `ItemSnapshot.java` | Shared item data POJO for inventory/equipment serialization |

### New files (persistence/migration/)

| File | Responsibility |
|---|---|
| `SaveMigration.java` | Migration interface: fromVersion, toVersion, migrate |
| `SaveMigrator.java` | Runs migration chain sequentially |

### New files (core/events/)

| File | Responsibility |
|---|---|
| `SaveBeginEvent.java` | Fired before snapshot collection |
| `SaveCompleteEvent.java` | Fired after successful save |
| `SaveFailedEvent.java` | Fired on save failure, carries exception |
| `LoadCompleteEvent.java` | Fired after successful load |

### Modified files

| File | Change |
|---|---|
| `core/build.gradle.kts` | Add Kryo 5.6.0 dependency |
| `core/.../core/GameWorld.java` | Add PersistenceIdComponent to entity creation, wire SaveCoordinator |
| `core/.../core/components/TransformComponent.java` | Implement `Snapshotable<TransformSnapshot>` |
| `core/.../combat/components/HealthComponent.java` | Implement `Snapshotable<HealthSnapshot>` |
| `core/.../player/components/PlayerStateComponent.java` | Store ship UUID alongside Entity ref, implement Snapshotable |
| `core/.../player/components/MovementStateComponent.java` | Implement Snapshotable |
| `core/.../player/components/FPSCameraComponent.java` | Implement Snapshotable |
| `core/.../economy/components/PlayerWalletComponent.java` | Implement Snapshotable |
| `core/.../economy/components/CargoBayComponent.java` | Implement Snapshotable |
| `core/.../combat/components/ShieldComponent.java` | Implement Snapshotable |
| `core/.../combat/components/ArmorComponent.java` | Implement Snapshotable |
| `core/.../combat/components/StatusEffectsComponent.java` | Implement Snapshotable |
| `core/.../combat/components/RangedWeaponComponent.java` | Implement Snapshotable |
| `core/.../combat/components/MeleeWeaponComponent.java` | Implement Snapshotable |
| `core/.../combat/components/WeaponInventoryComponent.java` | Implement Snapshotable |
| `core/.../combat/components/CombatAIComponent.java` | Implement Snapshotable |
| `core/.../combat/components/SquadComponent.java` | Implement Snapshotable |
| `core/.../ship/components/ShipDataComponent.java` | Implement Snapshotable |
| `core/.../ship/flight/components/ShipFlightComponent.java` | Implement Snapshotable |
| `core/.../ship/propulsion/components/EngineSpecComponent.java` | Implement Snapshotable |
| `core/.../ship/propulsion/components/FuelTankComponent.java` | Implement Snapshotable |
| `core/.../ship/thermal/components/ThermalStateComponent.java` | Implement Snapshotable |
| `core/.../ship/structure/components/StructuralIntegrityComponent.java` | Implement Snapshotable |
| `core/.../ship/lifesupport/components/CompartmentAtmosphereComponent.java` | Implement Snapshotable |
| `core/.../ship/docking/components/DockingStateComponent.java` | Implement Snapshotable |
| `core/.../ship/components/ShipInteriorComponent.java` | Implement Snapshotable (layout only) |
| `core/.../equipment/components/InventoryComponent.java` | Implement Snapshotable |
| `core/.../equipment/components/EquipmentSlotsComponent.java` | Implement Snapshotable |

### Test files (core/src/test/java/com/galacticodyssey/persistence/)

| File | What it tests |
|---|---|
| `PersistenceIdComponentTest.java` | UUID assignment, immutability |
| `TransformSnapshotTest.java` | Round-trip with galaxy-space coordinate conversion |
| `SnapshotRoundTripTest.java` | Representative component snapshot → restore cycle |
| `KryoRegistrarTest.java` | All types register, round-trip via Kryo |
| `EntitySnapshotBuilderTest.java` | Collects snapshots from entities with PersistenceIdComponent |
| `ReferenceResolverTest.java` | UUID → Entity resolution, missing reference handling |
| `SaveWriterReaderTest.java` | Write SaveBundle to temp dir, read back, verify equality |
| `LocalFileSaveBackendTest.java` | File operations, atomic write, list/delete |
| `SaveMigratorTest.java` | Sequential migration chain |
| `SaveCoordinatorTest.java` | Full save/load integration round-trip |

---

## Task 1: Add Kryo Dependency and Core Interfaces

**Files:**
- Modify: `core/build.gradle.kts`
- Create: `core/src/main/java/com/galacticodyssey/persistence/PersistenceIdComponent.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/Snapshotable.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/EntitySnapshot.java`
- Test: `core/src/test/java/com/galacticodyssey/persistence/PersistenceIdComponentTest.java`

- [ ] **Step 1: Add Kryo dependency to build.gradle.kts**

In `core/build.gradle.kts`, add to the `dependencies` block:

```kotlin
api("com.esotericsoftware:kryo:5.6.0")
```

- [ ] **Step 2: Write PersistenceIdComponent test**

```java
package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Component;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceIdComponentTest {

    @Test
    void assignsUuidOnConstruction() {
        PersistenceIdComponent id = new PersistenceIdComponent();
        assertNotNull(id.uuid);
    }

    @Test
    void acceptsExplicitUuid() {
        UUID explicit = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        PersistenceIdComponent id = new PersistenceIdComponent(explicit);
        assertEquals(explicit, id.uuid);
    }

    @Test
    void twoInstancesHaveDifferentUuids() {
        PersistenceIdComponent a = new PersistenceIdComponent();
        PersistenceIdComponent b = new PersistenceIdComponent();
        assertNotEquals(a.uuid, b.uuid);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.PersistenceIdComponentTest" --info`
Expected: Compilation failure — class does not exist yet.

- [ ] **Step 4: Implement PersistenceIdComponent**

```java
package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Component;
import java.util.UUID;

public class PersistenceIdComponent implements Component {
    public final UUID uuid;

    public PersistenceIdComponent() {
        this.uuid = UUID.randomUUID();
    }

    public PersistenceIdComponent(UUID uuid) {
        this.uuid = uuid;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.PersistenceIdComponentTest" --info`
Expected: 3 tests PASS.

- [ ] **Step 6: Implement Snapshotable interface**

```java
package com.galacticodyssey.persistence;

public interface Snapshotable<S> {
    S takeSnapshot();
    void restoreFromSnapshot(S snapshot);
}
```

- [ ] **Step 7: Implement EntitySnapshot container**

```java
package com.galacticodyssey.persistence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EntitySnapshot {
    public UUID entityId;
    public Map<String, Object> componentSnapshots = new HashMap<>();
    public Set<String> tagComponents = new HashSet<>();

    public EntitySnapshot() {}

    public EntitySnapshot(UUID entityId) {
        this.entityId = entityId;
    }

    public void putSnapshot(String componentType, Object snapshot) {
        componentSnapshots.put(componentType, snapshot);
    }

    public <T> T getSnapshot(String componentType, Class<T> snapshotClass) {
        Object raw = componentSnapshots.get(componentType);
        if (raw == null) return null;
        return snapshotClass.cast(raw);
    }

    public void addTag(String tagComponentType) {
        tagComponents.add(tagComponentType);
    }

    public boolean hasTag(String tagComponentType) {
        return tagComponents.contains(tagComponentType);
    }
}
```

- [ ] **Step 8: Commit**

```bash
git add core/build.gradle.kts core/src/main/java/com/galacticodyssey/persistence/ core/src/test/java/com/galacticodyssey/persistence/
git commit -m "feat(persistence): add Kryo dependency, PersistenceIdComponent, Snapshotable interface, EntitySnapshot"
```

---

## Task 2: ManifestData, SaveBundle, and Save Events

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/ManifestData.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/SaveBundle.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/WorldModification.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/SaveBeginEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/SaveCompleteEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/SaveFailedEvent.java`
- Create: `core/src/main/java/com/galacticodyssey/core/events/LoadCompleteEvent.java`

- [ ] **Step 1: Implement ManifestData**

```java
package com.galacticodyssey.persistence;

import java.util.UUID;

public class ManifestData {
    public static final int CURRENT_VERSION = 1;

    public String saveName;
    public long timestampMillis;
    public int saveVersion = CURRENT_VERSION;
    public long galaxySeed;
    public UUID playerEntityId;
    public UUID currentSystemId;

    public ManifestData() {}

    public ManifestData(String saveName, long galaxySeed, UUID playerEntityId, UUID currentSystemId) {
        this.saveName = saveName;
        this.timestampMillis = System.currentTimeMillis();
        this.galaxySeed = galaxySeed;
        this.playerEntityId = playerEntityId;
        this.currentSystemId = currentSystemId;
    }
}
```

- [ ] **Step 2: Implement WorldModification**

```java
package com.galacticodyssey.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorldModification {
    public UUID systemId;
    public List<EntitySnapshot> addedEntities = new ArrayList<>();
    public List<UUID> removedEntityIds = new ArrayList<>();
    public List<EntitySnapshot> modifiedEntities = new ArrayList<>();

    public WorldModification() {}

    public WorldModification(UUID systemId) {
        this.systemId = systemId;
    }
}
```

- [ ] **Step 3: Implement SaveBundle**

```java
package com.galacticodyssey.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SaveBundle {
    public ManifestData manifest;
    public EntitySnapshot playerSnapshot;
    public List<EntitySnapshot> ownedShipSnapshots = new ArrayList<>();
    public Map<UUID, List<EntitySnapshot>> systemSnapshots = new HashMap<>();
    public Map<UUID, WorldModification> worldModifications = new HashMap<>();
    public Map<String, Object> economyState = new HashMap<>();
    public Map<String, Object> factionState = new HashMap<>();
    public Set<UUID> discoveredSystemIds = new HashSet<>();
    public Set<UUID> discoveredPlanetIds = new HashSet<>();
    public List<UUID> recentSystemIds = new ArrayList<>();

    public SaveBundle() {}
}
```

- [ ] **Step 4: Implement save events**

`SaveBeginEvent.java`:
```java
package com.galacticodyssey.core.events;

public final class SaveBeginEvent {
    public final String saveName;

    public SaveBeginEvent(String saveName) {
        this.saveName = saveName;
    }
}
```

`SaveCompleteEvent.java`:
```java
package com.galacticodyssey.core.events;

public final class SaveCompleteEvent {
    public final String saveName;
    public final long durationMillis;

    public SaveCompleteEvent(String saveName, long durationMillis) {
        this.saveName = saveName;
        this.durationMillis = durationMillis;
    }
}
```

`SaveFailedEvent.java`:
```java
package com.galacticodyssey.core.events;

public final class SaveFailedEvent {
    public final String saveName;
    public final Exception cause;

    public SaveFailedEvent(String saveName, Exception cause) {
        this.saveName = saveName;
        this.cause = cause;
    }
}
```

`LoadCompleteEvent.java`:
```java
package com.galacticodyssey.core.events;

public final class LoadCompleteEvent {
    public final String saveName;
    public final long durationMillis;

    public LoadCompleteEvent(String saveName, long durationMillis) {
        this.saveName = saveName;
        this.durationMillis = durationMillis;
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/ManifestData.java \
        core/src/main/java/com/galacticodyssey/persistence/SaveBundle.java \
        core/src/main/java/com/galacticodyssey/persistence/WorldModification.java \
        core/src/main/java/com/galacticodyssey/core/events/SaveBeginEvent.java \
        core/src/main/java/com/galacticodyssey/core/events/SaveCompleteEvent.java \
        core/src/main/java/com/galacticodyssey/core/events/SaveFailedEvent.java \
        core/src/main/java/com/galacticodyssey/core/events/LoadCompleteEvent.java
git commit -m "feat(persistence): add ManifestData, SaveBundle, WorldModification, save/load events"
```

---

## Task 3: TransformSnapshot with Galaxy-Space Coordinate Handling

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/TransformSnapshot.java`
- Modify: `core/src/main/java/com/galacticodyssey/core/components/TransformComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/persistence/TransformSnapshotTest.java`

- [ ] **Step 1: Write TransformSnapshot test**

```java
package com.galacticodyssey.persistence;

import com.galacticodyssey.persistence.snapshots.TransformSnapshot;
import com.galacticodyssey.core.components.TransformComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransformSnapshotTest {

    @Test
    void roundTripPreservesLocalPosition() {
        TransformComponent tc = new TransformComponent();
        tc.position.set(1.5f, 2.5f, 3.5f);
        tc.rotation.set(0f, 0.707f, 0f, 0.707f);

        TransformSnapshot snap = tc.takeSnapshot(0.0, 0.0, 0.0);
        assertEquals(1.5, snap.galaxyX, 1e-5);
        assertEquals(2.5, snap.galaxyY, 1e-5);
        assertEquals(3.5, snap.galaxyZ, 1e-5);

        TransformComponent restored = new TransformComponent();
        restored.restoreFromSnapshot(snap, 0.0, 0.0, 0.0);
        assertEquals(1.5f, restored.position.x, 1e-4f);
        assertEquals(2.5f, restored.position.y, 1e-4f);
        assertEquals(3.5f, restored.position.z, 1e-4f);
        assertEquals(0.707f, restored.rotation.y, 1e-3f);
    }

    @Test
    void roundTripWithOriginOffset() {
        TransformComponent tc = new TransformComponent();
        tc.position.set(5f, 10f, 15f);

        double offsetX = 1_000_000.0;
        double offsetY = 2_000_000.0;
        double offsetZ = 3_000_000.0;

        TransformSnapshot snap = tc.takeSnapshot(offsetX, offsetY, offsetZ);
        assertEquals(1_000_005.0, snap.galaxyX, 1e-5);
        assertEquals(2_000_010.0, snap.galaxyY, 1e-5);
        assertEquals(3_000_015.0, snap.galaxyZ, 1e-5);

        TransformComponent restored = new TransformComponent();
        double newOffsetX = 1_000_003.0;
        double newOffsetY = 2_000_008.0;
        double newOffsetZ = 3_000_012.0;
        restored.restoreFromSnapshot(snap, newOffsetX, newOffsetY, newOffsetZ);

        assertEquals(2.0f, restored.position.x, 1e-2f);
        assertEquals(2.0f, restored.position.y, 1e-2f);
        assertEquals(3.0f, restored.position.z, 1e-2f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.TransformSnapshotTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Implement TransformSnapshot**

```java
package com.galacticodyssey.persistence.snapshots;

public class TransformSnapshot {
    public double galaxyX;
    public double galaxyY;
    public double galaxyZ;
    public float rotX;
    public float rotY;
    public float rotZ;
    public float rotW;

    public TransformSnapshot() {}

    public TransformSnapshot(double gx, double gy, double gz,
                             float rx, float ry, float rz, float rw) {
        this.galaxyX = gx;
        this.galaxyY = gy;
        this.galaxyZ = gz;
        this.rotX = rx;
        this.rotY = ry;
        this.rotZ = rz;
        this.rotW = rw;
    }
}
```

- [ ] **Step 4: Add snapshot methods to TransformComponent**

TransformComponent uses a special signature because it needs the CoordinateManager's origin offset. It does NOT implement `Snapshotable<TransformSnapshot>` directly — instead it has explicit methods that accept the offset:

```java
// Add these methods to TransformComponent:

public TransformSnapshot takeSnapshot(double originOffsetX, double originOffsetY, double originOffsetZ) {
    return new TransformSnapshot(
        position.x + originOffsetX,
        position.y + originOffsetY,
        position.z + originOffsetZ,
        rotation.x, rotation.y, rotation.z, rotation.w
    );
}

public void restoreFromSnapshot(TransformSnapshot snapshot,
                                double originOffsetX, double originOffsetY, double originOffsetZ) {
    position.set(
        (float)(snapshot.galaxyX - originOffsetX),
        (float)(snapshot.galaxyY - originOffsetY),
        (float)(snapshot.galaxyZ - originOffsetZ)
    );
    rotation.set(snapshot.rotX, snapshot.rotY, snapshot.rotZ, snapshot.rotW);
}
```

Add imports at the top of TransformComponent:
```java
import com.galacticodyssey.persistence.snapshots.TransformSnapshot;
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.TransformSnapshotTest" --info`
Expected: 2 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/snapshots/TransformSnapshot.java \
        core/src/main/java/com/galacticodyssey/core/components/TransformComponent.java \
        core/src/test/java/com/galacticodyssey/persistence/TransformSnapshotTest.java
git commit -m "feat(persistence): add TransformSnapshot with galaxy-space coordinate conversion"
```

---

## Task 4: Player Component Snapshots

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/HealthSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/PlayerStateSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/MovementStateSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/FPSCameraSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/PlayerWalletSnapshot.java`
- Modify: `core/src/main/java/com/galacticodyssey/combat/components/HealthComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/player/components/PlayerStateComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/player/components/MovementStateComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/player/components/FPSCameraComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/economy/components/PlayerWalletComponent.java`
- Test: `core/src/test/java/com/galacticodyssey/persistence/SnapshotRoundTripTest.java`

- [ ] **Step 1: Write round-trip test for player snapshots**

```java
package com.galacticodyssey.persistence;

import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.economy.components.PlayerWalletComponent;
import com.galacticodyssey.persistence.snapshots.*;
import com.galacticodyssey.player.components.FPSCameraComponent;
import com.galacticodyssey.player.components.MovementStateComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotRoundTripTest {

    @Test
    void healthRoundTrip() {
        HealthComponent h = new HealthComponent();
        h.currentHP = 42f;
        h.maxHP = 150f;
        h.alive = true;

        HealthSnapshot snap = h.takeSnapshot();
        HealthComponent restored = new HealthComponent();
        restored.restoreFromSnapshot(snap);

        assertEquals(42f, restored.currentHP);
        assertEquals(150f, restored.maxHP);
        assertTrue(restored.alive);
    }

    @Test
    void playerStateRoundTrip() {
        PlayerStateComponent ps = new PlayerStateComponent();
        ps.currentMode = PlayerStateComponent.PlayerMode.PILOTING;
        ps.currentShipId = UUID.randomUUID();
        ps.interactionTargetId = null;

        PlayerStateSnapshot snap = ps.takeSnapshot();
        assertEquals(PlayerStateComponent.PlayerMode.PILOTING, snap.currentMode);
        assertEquals(ps.currentShipId, snap.currentShipId);
        assertNull(snap.interactionTargetId);

        PlayerStateComponent restored = new PlayerStateComponent();
        restored.restoreFromSnapshot(snap);
        assertEquals(PlayerStateComponent.PlayerMode.PILOTING, restored.currentMode);
        assertEquals(ps.currentShipId, restored.currentShipId);
    }

    @Test
    void movementStateRoundTrip() {
        MovementStateComponent ms = new MovementStateComponent();
        ms.isSprinting = true;
        ms.currentStamina = 73f;
        ms.fallVelocity = -9.8f;
        ms.isExhausted = true;

        MovementStateSnapshot snap = ms.takeSnapshot();
        MovementStateComponent restored = new MovementStateComponent();
        restored.restoreFromSnapshot(snap);

        assertTrue(restored.isSprinting);
        assertEquals(73f, restored.currentStamina);
        assertEquals(-9.8f, restored.fallVelocity);
        assertTrue(restored.isExhausted);
    }

    @Test
    void walletRoundTrip() {
        PlayerWalletComponent w = new PlayerWalletComponent();
        w.credits = 999_999L;

        PlayerWalletSnapshot snap = w.takeSnapshot();
        PlayerWalletComponent restored = new PlayerWalletComponent();
        restored.restoreFromSnapshot(snap);

        assertEquals(999_999L, restored.credits);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.SnapshotRoundTripTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Implement HealthSnapshot and wire HealthComponent**

`HealthSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

public class HealthSnapshot {
    public float currentHP;
    public float maxHP;
    public boolean alive;

    public HealthSnapshot() {}
}
```

Add to `HealthComponent.java` — implement `Snapshotable<HealthSnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.HealthSnapshot;

// Add "implements Snapshotable<HealthSnapshot>" to class declaration

@Override
public HealthSnapshot takeSnapshot() {
    HealthSnapshot s = new HealthSnapshot();
    s.currentHP = this.currentHP;
    s.maxHP = this.maxHP;
    s.alive = this.alive;
    return s;
}

@Override
public void restoreFromSnapshot(HealthSnapshot s) {
    this.currentHP = s.currentHP;
    this.maxHP = s.maxHP;
    this.alive = s.alive;
}
```

- [ ] **Step 4: Implement PlayerStateSnapshot and wire PlayerStateComponent**

`PlayerStateSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode;
import java.util.UUID;

public class PlayerStateSnapshot {
    public PlayerMode currentMode;
    public UUID currentShipId;
    public UUID interactionTargetId;

    public PlayerStateSnapshot() {}
}
```

Add a `UUID currentShipId` field to `PlayerStateComponent` alongside the existing `Entity currentShip` field. This stores the UUID for persistence. Also add `UUID interactionTargetId`. Then implement `Snapshotable<PlayerStateSnapshot>`:

```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.PlayerStateSnapshot;
import java.util.UUID;

// Add fields:
public UUID currentShipId;
public UUID interactionTargetId;

// Add "implements Snapshotable<PlayerStateSnapshot>" to class declaration

@Override
public PlayerStateSnapshot takeSnapshot() {
    PlayerStateSnapshot s = new PlayerStateSnapshot();
    s.currentMode = this.currentMode;
    s.currentShipId = this.currentShipId;
    s.interactionTargetId = this.interactionTargetId;
    return s;
}

@Override
public void restoreFromSnapshot(PlayerStateSnapshot s) {
    this.currentMode = s.currentMode;
    this.currentShipId = s.currentShipId;
    this.interactionTargetId = s.interactionTargetId;
}
```

- [ ] **Step 5: Implement MovementStateSnapshot and wire MovementStateComponent**

`MovementStateSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

public class MovementStateSnapshot {
    public boolean isGrounded;
    public boolean isSprinting;
    public boolean isCrouching;
    public float currentSpeed;
    public float currentStamina;
    public float maxStamina;
    public float staminaDrainRate;
    public float staminaRegenRate;
    public float slopeAngle;
    public boolean isExhausted;
    public float fallVelocity;

    public MovementStateSnapshot() {}
}
```

Add to `MovementStateComponent` — implement `Snapshotable<MovementStateSnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.MovementStateSnapshot;

// Add "implements Snapshotable<MovementStateSnapshot>" to class declaration

@Override
public MovementStateSnapshot takeSnapshot() {
    MovementStateSnapshot s = new MovementStateSnapshot();
    s.isGrounded = this.isGrounded;
    s.isSprinting = this.isSprinting;
    s.isCrouching = this.isCrouching;
    s.currentSpeed = this.currentSpeed;
    s.currentStamina = this.currentStamina;
    s.maxStamina = this.maxStamina;
    s.staminaDrainRate = this.staminaDrainRate;
    s.staminaRegenRate = this.staminaRegenRate;
    s.slopeAngle = this.slopeAngle;
    s.isExhausted = this.isExhausted;
    s.fallVelocity = this.fallVelocity;
    return s;
}

@Override
public void restoreFromSnapshot(MovementStateSnapshot s) {
    this.isGrounded = s.isGrounded;
    this.isSprinting = s.isSprinting;
    this.isCrouching = s.isCrouching;
    this.currentSpeed = s.currentSpeed;
    this.currentStamina = s.currentStamina;
    this.maxStamina = s.maxStamina;
    this.staminaDrainRate = s.staminaDrainRate;
    this.staminaRegenRate = s.staminaRegenRate;
    this.slopeAngle = s.slopeAngle;
    this.isExhausted = s.isExhausted;
    this.fallVelocity = s.fallVelocity;
}
```

- [ ] **Step 6: Implement FPSCameraSnapshot and wire FPSCameraComponent**

`FPSCameraSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

public class FPSCameraSnapshot {
    public float pitchAngle;
    public float yawAngle;
    public float currentEyeHeight;
    public float mouseSensitivity;
    public float currentCameraDistance;
    public float maxCameraDistance;

    public FPSCameraSnapshot() {}
}
```

Add to `FPSCameraComponent` — implement `Snapshotable<FPSCameraSnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.FPSCameraSnapshot;

// Add "implements Snapshotable<FPSCameraSnapshot>" to class declaration

@Override
public FPSCameraSnapshot takeSnapshot() {
    FPSCameraSnapshot s = new FPSCameraSnapshot();
    s.pitchAngle = this.pitchAngle;
    s.yawAngle = this.yawAngle;
    s.currentEyeHeight = this.currentEyeHeight;
    s.mouseSensitivity = this.mouseSensitivity;
    s.currentCameraDistance = this.currentCameraDistance;
    s.maxCameraDistance = this.maxCameraDistance;
    return s;
}

@Override
public void restoreFromSnapshot(FPSCameraSnapshot s) {
    this.pitchAngle = s.pitchAngle;
    this.yawAngle = s.yawAngle;
    this.currentEyeHeight = s.currentEyeHeight;
    this.mouseSensitivity = s.mouseSensitivity;
    this.currentCameraDistance = s.currentCameraDistance;
    this.maxCameraDistance = s.maxCameraDistance;
}
```

- [ ] **Step 7: Implement PlayerWalletSnapshot and wire PlayerWalletComponent**

`PlayerWalletSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

public class PlayerWalletSnapshot {
    public long credits;

    public PlayerWalletSnapshot() {}
}
```

Add to `PlayerWalletComponent` — implement `Snapshotable<PlayerWalletSnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.PlayerWalletSnapshot;

// Add "implements Snapshotable<PlayerWalletSnapshot>" to class declaration

@Override
public PlayerWalletSnapshot takeSnapshot() {
    PlayerWalletSnapshot s = new PlayerWalletSnapshot();
    s.credits = this.credits;
    return s;
}

@Override
public void restoreFromSnapshot(PlayerWalletSnapshot s) {
    this.credits = s.credits;
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.SnapshotRoundTripTest" --info`
Expected: 4 tests PASS.

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/snapshots/ \
        core/src/main/java/com/galacticodyssey/combat/components/HealthComponent.java \
        core/src/main/java/com/galacticodyssey/player/components/ \
        core/src/main/java/com/galacticodyssey/economy/components/PlayerWalletComponent.java \
        core/src/test/java/com/galacticodyssey/persistence/SnapshotRoundTripTest.java
git commit -m "feat(persistence): add player component snapshots (Health, PlayerState, Movement, Camera, Wallet)"
```

---

## Task 5: Combat Component Snapshots

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/ShieldSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/ArmorSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/RangedWeaponSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/MeleeWeaponSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/WeaponInventorySnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/StatusEffectsSnapshot.java`
- Modify: Corresponding component classes to implement Snapshotable

- [ ] **Step 1: Implement ShieldSnapshot + wire ShieldComponent**

`ShieldSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.combat.CombatEnums.ShieldType;

public class ShieldSnapshot {
    public float currentShield;
    public float maxShield;
    public float rechargeRate;
    public float rechargeDelay;
    public float timeSinceLastHit;
    public ShieldType shieldType;

    public ShieldSnapshot() {}
}
```

Add to `ShieldComponent` — implement `Snapshotable<ShieldSnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.ShieldSnapshot;

@Override
public ShieldSnapshot takeSnapshot() {
    ShieldSnapshot s = new ShieldSnapshot();
    s.currentShield = this.currentShield;
    s.maxShield = this.maxShield;
    s.rechargeRate = this.rechargeRate;
    s.rechargeDelay = this.rechargeDelay;
    s.timeSinceLastHit = this.timeSinceLastHit;
    s.shieldType = this.shieldType;
    return s;
}

@Override
public void restoreFromSnapshot(ShieldSnapshot s) {
    this.currentShield = s.currentShield;
    this.maxShield = s.maxShield;
    this.rechargeRate = s.rechargeRate;
    this.rechargeDelay = s.rechargeDelay;
    this.timeSinceLastHit = s.timeSinceLastHit;
    this.shieldType = s.shieldType;
}
```

- [ ] **Step 2: Implement ArmorSnapshot + wire ArmorComponent**

`ArmorSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.HitRegion;
import java.util.EnumMap;
import java.util.Map;

public class ArmorSnapshot {
    public Map<HitRegion, Float> armorRating = new EnumMap<>(HitRegion.class);
    public Map<HitRegion, Map<DamageType, Float>> resistances = new EnumMap<>(HitRegion.class);
    public Map<HitRegion, Float> durability = new EnumMap<>(HitRegion.class);
    public Map<HitRegion, Float> maxDurability = new EnumMap<>(HitRegion.class);

    public ArmorSnapshot() {}
}
```

Add to `ArmorComponent` — implement `Snapshotable<ArmorSnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.ArmorSnapshot;

@Override
public ArmorSnapshot takeSnapshot() {
    ArmorSnapshot s = new ArmorSnapshot();
    s.armorRating.putAll(this.armorRating);
    for (Map.Entry<HitRegion, Map<DamageType, Float>> e : this.resistances.entrySet()) {
        s.resistances.put(e.getKey(), new EnumMap<>(e.getValue()));
    }
    s.durability.putAll(this.durability);
    s.maxDurability.putAll(this.maxDurability);
    return s;
}

@Override
public void restoreFromSnapshot(ArmorSnapshot s) {
    this.armorRating.clear();
    this.armorRating.putAll(s.armorRating);
    this.resistances.clear();
    for (Map.Entry<HitRegion, Map<DamageType, Float>> e : s.resistances.entrySet()) {
        this.resistances.put(e.getKey(), new EnumMap<>(e.getValue()));
    }
    this.durability.clear();
    this.durability.putAll(s.durability);
    this.maxDurability.clear();
    this.maxDurability.putAll(s.maxDurability);
}
```

- [ ] **Step 3: Implement RangedWeaponSnapshot + wire RangedWeaponComponent**

`RangedWeaponSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.FiringMode;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public class RangedWeaponSnapshot {
    public float damage;
    public float fireRate;
    public float spread;
    public float range;
    public float recoil;
    public int currentAmmo;
    public int magSize;
    public float reloadTime;
    public float reloadTimer;
    public boolean reloading;
    public FiringMode firingMode;
    public boolean hitscan;
    public DamageType damageType;
    public StatusEffectType statusEffect;
    public float statusEffectChance;
    public Float projectileSpeed;
    public String ammoTypeId;

    public RangedWeaponSnapshot() {}
}
```

Add to `RangedWeaponComponent` — implement `Snapshotable<RangedWeaponSnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.RangedWeaponSnapshot;

@Override
public RangedWeaponSnapshot takeSnapshot() {
    RangedWeaponSnapshot s = new RangedWeaponSnapshot();
    s.damage = this.damage;
    s.fireRate = this.fireRate;
    s.spread = this.spread;
    s.range = this.range;
    s.recoil = this.recoil;
    s.currentAmmo = this.currentAmmo;
    s.magSize = this.magSize;
    s.reloadTime = this.reloadTime;
    s.reloadTimer = this.reloadTimer;
    s.reloading = this.reloading;
    s.firingMode = this.firingMode;
    s.hitscan = this.hitscan;
    s.damageType = this.damageType;
    s.statusEffect = this.statusEffect;
    s.statusEffectChance = this.statusEffectChance;
    s.projectileSpeed = this.projectileSpeed;
    s.ammoTypeId = this.ammoTypeId;
    return s;
}

@Override
public void restoreFromSnapshot(RangedWeaponSnapshot s) {
    this.damage = s.damage;
    this.fireRate = s.fireRate;
    this.spread = s.spread;
    this.range = s.range;
    this.recoil = s.recoil;
    this.currentAmmo = s.currentAmmo;
    this.magSize = s.magSize;
    this.reloadTime = s.reloadTime;
    this.reloadTimer = s.reloadTimer;
    this.reloading = s.reloading;
    this.firingMode = s.firingMode;
    this.hitscan = s.hitscan;
    this.damageType = s.damageType;
    this.statusEffect = s.statusEffect;
    this.statusEffectChance = s.statusEffectChance;
    this.projectileSpeed = s.projectileSpeed;
    this.ammoTypeId = s.ammoTypeId;
}
```

- [ ] **Step 4: Implement MeleeWeaponSnapshot + wire MeleeWeaponComponent**

`MeleeWeaponSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.combat.CombatEnums.AttackDirection;
import com.galacticodyssey.combat.CombatEnums.DamageType;
import com.galacticodyssey.combat.CombatEnums.WeightClass;
import java.util.EnumMap;
import java.util.Map;

public class MeleeWeaponSnapshot {
    public float baseDamage;
    public float reach;
    public float swingSpeed;
    public float blockEfficiency;
    public DamageType damageType;
    public WeightClass weightClass;
    public Map<AttackDirection, Float> directionalModifiers = new EnumMap<>(AttackDirection.class);
    public Map<AttackDirection, Float> staminaCosts = new EnumMap<>(AttackDirection.class);

    public MeleeWeaponSnapshot() {}
}
```

Add to `MeleeWeaponComponent` — implement `Snapshotable<MeleeWeaponSnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.MeleeWeaponSnapshot;

@Override
public MeleeWeaponSnapshot takeSnapshot() {
    MeleeWeaponSnapshot s = new MeleeWeaponSnapshot();
    s.baseDamage = this.baseDamage;
    s.reach = this.reach;
    s.swingSpeed = this.swingSpeed;
    s.blockEfficiency = this.blockEfficiency;
    s.damageType = this.damageType;
    s.weightClass = this.weightClass;
    s.directionalModifiers.putAll(this.directionalModifiers);
    s.staminaCosts.putAll(this.staminaCosts);
    return s;
}

@Override
public void restoreFromSnapshot(MeleeWeaponSnapshot s) {
    this.baseDamage = s.baseDamage;
    this.reach = s.reach;
    this.swingSpeed = s.swingSpeed;
    this.blockEfficiency = s.blockEfficiency;
    this.damageType = s.damageType;
    this.weightClass = s.weightClass;
    this.directionalModifiers.clear();
    this.directionalModifiers.putAll(s.directionalModifiers);
    this.staminaCosts.clear();
    this.staminaCosts.putAll(s.staminaCosts);
}
```

- [ ] **Step 5: Implement WeaponInventorySnapshot + wire WeaponInventoryComponent**

`WeaponInventorySnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

public class WeaponInventorySnapshot {
    public int activeSlotIndex;
    public boolean switching;
    public float switchTimer;

    public WeaponInventorySnapshot() {}
}
```

Add to `WeaponInventoryComponent` — implement `Snapshotable<WeaponInventorySnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.WeaponInventorySnapshot;

@Override
public WeaponInventorySnapshot takeSnapshot() {
    WeaponInventorySnapshot s = new WeaponInventorySnapshot();
    s.activeSlotIndex = this.activeSlotIndex;
    s.switching = this.switching;
    s.switchTimer = this.switchTimer;
    return s;
}

@Override
public void restoreFromSnapshot(WeaponInventorySnapshot s) {
    this.activeSlotIndex = s.activeSlotIndex;
    this.switching = s.switching;
    this.switchTimer = s.switchTimer;
}
```

- [ ] **Step 6: Implement StatusEffectsSnapshot + wire StatusEffectsComponent**

`StatusEffectsSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.combat.CombatEnums.StatusEffectType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StatusEffectsSnapshot {

    public static class ActiveEffectData {
        public StatusEffectType type;
        public float remainingDuration;
        public float tickRate;
        public float magnitude;
        public float tickAccumulator;
        public UUID sourceEntityId;
        public int stacks;

        public ActiveEffectData() {}
    }

    public List<ActiveEffectData> activeEffects = new ArrayList<>();

    public StatusEffectsSnapshot() {}
}
```

Add to `StatusEffectsComponent` — implement `Snapshotable<StatusEffectsSnapshot>`. The `ActiveStatusEffect.source` is an Entity reference that must be converted to UUID:

```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.PersistenceIdComponent;
import com.galacticodyssey.persistence.snapshots.StatusEffectsSnapshot;

@Override
public StatusEffectsSnapshot takeSnapshot() {
    StatusEffectsSnapshot s = new StatusEffectsSnapshot();
    for (ActiveStatusEffect effect : this.activeEffects) {
        StatusEffectsSnapshot.ActiveEffectData data = new StatusEffectsSnapshot.ActiveEffectData();
        data.type = effect.type;
        data.remainingDuration = effect.remainingDuration;
        data.tickRate = effect.tickRate;
        data.magnitude = effect.magnitude;
        data.tickAccumulator = effect.tickAccumulator;
        data.stacks = effect.stacks;
        if (effect.source != null) {
            PersistenceIdComponent pid = effect.source.getComponent(PersistenceIdComponent.class);
            if (pid != null) data.sourceEntityId = pid.uuid;
        }
        s.activeEffects.add(data);
    }
    return s;
}

@Override
public void restoreFromSnapshot(StatusEffectsSnapshot s) {
    this.activeEffects.clear();
    for (StatusEffectsSnapshot.ActiveEffectData data : s.activeEffects) {
        ActiveStatusEffect effect = new ActiveStatusEffect(
            data.type, data.remainingDuration, data.tickRate, data.magnitude, null
        );
        effect.tickAccumulator = data.tickAccumulator;
        effect.stacks = data.stacks;
        this.activeEffects.add(effect);
    }
}
```

Note: The `source` Entity field is restored as `null` during initial restore. The `ReferenceResolver` (Task 10) resolves `sourceEntityId` → Entity in a second pass.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/snapshots/ \
        core/src/main/java/com/galacticodyssey/combat/components/
git commit -m "feat(persistence): add combat component snapshots (Shield, Armor, Weapons, StatusEffects)"
```

---

## Task 6: Equipment Component Snapshots

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/ItemSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/InventorySnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/EquipmentSlotsSnapshot.java`
- Modify: `core/src/main/java/com/galacticodyssey/equipment/components/InventoryComponent.java`
- Modify: `core/src/main/java/com/galacticodyssey/equipment/components/EquipmentSlotsComponent.java`

- [ ] **Step 1: Implement ItemSnapshot**

This is a shared POJO used by both InventorySnapshot and EquipmentSlotsSnapshot. It captures the serializable identity and state of an Item. Consult the actual `Item` class fields and map them. At minimum, it needs the item's registry ID and any mutable state (durability, ammo count, mod slots):

```java
package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.equipment.EquipmentEnums.ItemType;
import com.galacticodyssey.combat.CombatEnums.QualityTier;
import java.util.HashMap;
import java.util.Map;

public class ItemSnapshot {
    public String itemId;
    public ItemType itemType;
    public QualityTier quality;
    public String displayName;
    public float weight;
    public int gridX;
    public int gridY;
    public int gridWidth;
    public int gridHeight;
    public int stackCount;
    public int maxStack;
    public float durability;
    public float maxDurability;
    public Map<String, Object> customData = new HashMap<>();

    public ItemSnapshot() {}
}
```

- [ ] **Step 2: Implement InventorySnapshot + wire InventoryComponent**

`InventorySnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class InventorySnapshot {
    public int gridWidth;
    public int gridHeight;
    public float maxWeight;
    public List<ItemSnapshot> items = new ArrayList<>();

    public InventorySnapshot() {}
}
```

Add to `InventoryComponent` — implement `Snapshotable<InventorySnapshot>`. The `takeSnapshot()` method iterates `getAllItems()` and creates an `ItemSnapshot` for each. The `restoreFromSnapshot()` clears the grid and re-adds items. Consult the actual `Item` class to map its fields into `ItemSnapshot`.

```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.InventorySnapshot;
import com.galacticodyssey.persistence.snapshots.ItemSnapshot;

// implements Snapshotable<InventorySnapshot>

@Override
public InventorySnapshot takeSnapshot() {
    InventorySnapshot s = new InventorySnapshot();
    s.gridWidth = this.gridWidth;
    s.gridHeight = this.gridHeight;
    s.maxWeight = this.maxWeight;
    for (Item item : this.allItems) {
        s.items.add(item.toItemSnapshot());
    }
    return s;
}

@Override
public void restoreFromSnapshot(InventorySnapshot s) {
    this.allItems.clear();
    this.grid = new Item[s.gridWidth][s.gridHeight];
    for (ItemSnapshot is : s.items) {
        Item item = Item.fromItemSnapshot(is);
        tryAdd(item);
    }
}
```

Note: `Item.toItemSnapshot()` and `Item.fromItemSnapshot()` are helper methods to add to the `Item` class. The implementer should consult the actual `Item` class fields and map them to `ItemSnapshot` fields. If Item is an interface or abstract class, adapt accordingly.

- [ ] **Step 3: Implement EquipmentSlotsSnapshot + wire EquipmentSlotsComponent**

`EquipmentSlotsSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot;
import java.util.EnumMap;
import java.util.Map;

public class EquipmentSlotsSnapshot {
    public Map<EquipmentSlot, ItemSnapshot> slots = new EnumMap<>(EquipmentSlot.class);

    public EquipmentSlotsSnapshot() {}
}
```

Add to `EquipmentSlotsComponent` — implement `Snapshotable<EquipmentSlotsSnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.EquipmentSlotsSnapshot;
import com.galacticodyssey.persistence.snapshots.ItemSnapshot;

@Override
public EquipmentSlotsSnapshot takeSnapshot() {
    EquipmentSlotsSnapshot s = new EquipmentSlotsSnapshot();
    for (Map.Entry<EquipmentSlot, Item> entry : this.slots.entrySet()) {
        if (entry.getValue() != null) {
            s.slots.put(entry.getKey(), entry.getValue().toItemSnapshot());
        }
    }
    return s;
}

@Override
public void restoreFromSnapshot(EquipmentSlotsSnapshot s) {
    this.slots.clear();
    for (Map.Entry<EquipmentSlot, ItemSnapshot> entry : s.slots.entrySet()) {
        if (entry.getValue() != null) {
            this.slots.put(entry.getKey(), Item.fromItemSnapshot(entry.getValue()));
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/snapshots/ \
        core/src/main/java/com/galacticodyssey/equipment/components/
git commit -m "feat(persistence): add equipment snapshots (Item, Inventory, EquipmentSlots)"
```

---

## Task 7: Ship Component Snapshots

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipDataSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/ShipFlightSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/CargoBaySnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/EngineSpecSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/FuelTankSnapshot.java`
- Modify: corresponding component classes

- [ ] **Step 1: Implement ShipDataSnapshot + wire ShipDataComponent**

`ShipDataSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

public class ShipDataSnapshot {
    public long blueprintSeed;
    public String sizeClass;
    public float spineLength;
    public int crossSectionCount;
    public float maxWidth;
    public float maxHeight;
    public int wingPairs;
    public int enginePodCount;
    public float mass;
    public float maxThrust;
    public float maxTurnRate;
    public float maxSpeed;
    public float hullHp;
    public float currentHullHp;

    public ShipDataSnapshot() {}
}
```

Add to `ShipDataComponent` — implement `Snapshotable<ShipDataSnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.ShipDataSnapshot;

@Override
public ShipDataSnapshot takeSnapshot() {
    ShipDataSnapshot s = new ShipDataSnapshot();
    if (this.blueprint != null) {
        s.blueprintSeed = this.blueprint.seed;
        s.sizeClass = this.blueprint.sizeClass;
        s.spineLength = this.blueprint.spineLength;
        s.crossSectionCount = this.blueprint.crossSectionCount;
        s.maxWidth = this.blueprint.maxWidth;
        s.maxHeight = this.blueprint.maxHeight;
        s.wingPairs = this.blueprint.wingPairs;
        s.enginePodCount = this.blueprint.enginePodCount;
    }
    s.mass = this.mass;
    s.maxThrust = this.maxThrust;
    s.maxTurnRate = this.maxTurnRate;
    s.maxSpeed = this.maxSpeed;
    s.hullHp = this.hullHp;
    s.currentHullHp = this.currentHullHp;
    return s;
}

@Override
public void restoreFromSnapshot(ShipDataSnapshot s) {
    this.blueprint = new ShipBlueprint(s.blueprintSeed, s.sizeClass, s.spineLength,
        s.crossSectionCount, s.maxWidth, s.maxHeight, s.wingPairs, s.enginePodCount);
    this.mass = s.mass;
    this.maxThrust = s.maxThrust;
    this.maxTurnRate = s.maxTurnRate;
    this.maxSpeed = s.maxSpeed;
    this.hullHp = s.hullHp;
    this.currentHullHp = s.currentHullHp;
    // hullGeometry is NOT restored — it's regenerated from the blueprint seed by MeshRebuilder
}
```

- [ ] **Step 2: Implement ShipFlightSnapshot + wire ShipFlightComponent**

`ShipFlightSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

public class ShipFlightSnapshot {
    public float linearThrust;
    public float strafeThrustFraction;
    public float verticalThrustFraction;
    public float pitchYawTorque;
    public float rollTorque;
    public float linearDrag;
    public float angularDrag;
    public float currentThrottle;

    public ShipFlightSnapshot() {}
}
```

Add to `ShipFlightComponent` — implement `Snapshotable<ShipFlightSnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.ShipFlightSnapshot;

@Override
public ShipFlightSnapshot takeSnapshot() {
    ShipFlightSnapshot s = new ShipFlightSnapshot();
    s.linearThrust = this.linearThrust;
    s.strafeThrustFraction = this.strafeThrustFraction;
    s.verticalThrustFraction = this.verticalThrustFraction;
    s.pitchYawTorque = this.pitchYawTorque;
    s.rollTorque = this.rollTorque;
    s.linearDrag = this.linearDrag;
    s.angularDrag = this.angularDrag;
    s.currentThrottle = this.currentThrottle;
    return s;
}

@Override
public void restoreFromSnapshot(ShipFlightSnapshot s) {
    this.linearThrust = s.linearThrust;
    this.strafeThrustFraction = s.strafeThrustFraction;
    this.verticalThrustFraction = s.verticalThrustFraction;
    this.pitchYawTorque = s.pitchYawTorque;
    this.rollTorque = s.rollTorque;
    this.linearDrag = s.linearDrag;
    this.angularDrag = s.angularDrag;
    this.currentThrottle = s.currentThrottle;
}
```

- [ ] **Step 3: Implement CargoBaySnapshot + wire CargoBayComponent**

`CargoBaySnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import java.util.HashMap;
import java.util.Map;

public class CargoBaySnapshot {
    public float capacity;
    public Map<String, Integer> contents = new HashMap<>();
    public float usedVolume;

    public CargoBaySnapshot() {}
}
```

Add to `CargoBayComponent` — implement `Snapshotable<CargoBaySnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.CargoBaySnapshot;

@Override
public CargoBaySnapshot takeSnapshot() {
    CargoBaySnapshot s = new CargoBaySnapshot();
    s.capacity = this.capacity;
    s.contents.putAll(this.contents);
    s.usedVolume = this.usedVolume;
    return s;
}

@Override
public void restoreFromSnapshot(CargoBaySnapshot s) {
    this.capacity = s.capacity;
    this.contents.clear();
    this.contents.putAll(s.contents);
    this.usedVolume = s.usedVolume;
}
```

- [ ] **Step 4: Implement EngineSpecSnapshot + wire EngineSpecComponent**

`EngineSpecSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

public class EngineSpecSnapshot {
    public String name;
    public float isp;
    public float maxThrust;
    public float currentThrottle;
    public float minThrottle;
    public float throttleResponseRate;
    public float gimbalAngle;
    public float actualThrust;

    public EngineSpecSnapshot() {}
}
```

Add to `EngineSpecComponent` — implement `Snapshotable<EngineSpecSnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.EngineSpecSnapshot;

@Override
public EngineSpecSnapshot takeSnapshot() {
    EngineSpecSnapshot s = new EngineSpecSnapshot();
    s.name = this.name;
    s.isp = this.isp;
    s.maxThrust = this.maxThrust;
    s.currentThrottle = this.currentThrottle;
    s.minThrottle = this.minThrottle;
    s.throttleResponseRate = this.throttleResponseRate;
    s.gimbalAngle = this.gimbalAngle;
    s.actualThrust = this.actualThrust;
    return s;
}

@Override
public void restoreFromSnapshot(EngineSpecSnapshot s) {
    this.name = s.name;
    this.isp = s.isp;
    this.maxThrust = s.maxThrust;
    this.currentThrottle = s.currentThrottle;
    this.minThrottle = s.minThrottle;
    this.throttleResponseRate = s.throttleResponseRate;
    this.gimbalAngle = s.gimbalAngle;
    this.actualThrust = s.actualThrust;
}
```

- [ ] **Step 5: Implement FuelTankSnapshot + wire FuelTankComponent**

`FuelTankSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

public class FuelTankSnapshot {
    public float maxMass;
    public float currentMass;
    public float localX;
    public float localY;
    public float localZ;
    public boolean isVenting;

    public FuelTankSnapshot() {}
}
```

Add to `FuelTankComponent` — implement `Snapshotable<FuelTankSnapshot>`:
```java
import com.galacticodyssey.persistence.Snapshotable;
import com.galacticodyssey.persistence.snapshots.FuelTankSnapshot;

@Override
public FuelTankSnapshot takeSnapshot() {
    FuelTankSnapshot s = new FuelTankSnapshot();
    s.maxMass = this.maxMass;
    s.currentMass = this.currentMass;
    s.localX = this.localPosition.x;
    s.localY = this.localPosition.y;
    s.localZ = this.localPosition.z;
    s.isVenting = this.isVenting;
    return s;
}

@Override
public void restoreFromSnapshot(FuelTankSnapshot s) {
    this.maxMass = s.maxMass;
    this.currentMass = s.currentMass;
    this.localPosition.set(s.localX, s.localY, s.localZ);
    this.isVenting = s.isVenting;
}
```

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/snapshots/ \
        core/src/main/java/com/galacticodyssey/ship/ \
        core/src/main/java/com/galacticodyssey/economy/components/CargoBayComponent.java
git commit -m "feat(persistence): add ship component snapshots (ShipData, Flight, CargoBay, Engine, FuelTank)"
```

---

## Task 8: Ship Subsystem and NPC Snapshots

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/ThermalStateSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/StructuralIntegritySnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/CompartmentAtmosphereSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/DockingStateSnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/CombatAISnapshot.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/snapshots/SquadSnapshot.java`
- Modify: corresponding component classes

- [ ] **Step 1: Implement ThermalStateSnapshot + wire ThermalStateComponent**

`ThermalStateSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

public class ThermalStateSnapshot {
    public float engineTemp;
    public float weaponsTemp;
    public float hullTemp;
    public float reactorTemp;
    public float heatSinkCharge;
    public float heatSinkCapacity;
    public float radiatorArea;
    public float radiatorEfficiency;
    public float engineTempLimit;
    public float weaponsTempLimit;
    public float hullTempLimit;
    public float reactorTempLimit;

    public ThermalStateSnapshot() {}
}
```

Add to `ThermalStateComponent` — implement `Snapshotable<ThermalStateSnapshot>`. Map the per-subsystem temperature fields and heat sink/radiator properties. Follow the same `takeSnapshot()`/`restoreFromSnapshot()` pattern.

- [ ] **Step 2: Implement StructuralIntegritySnapshot + wire StructuralIntegrityComponent**

`StructuralIntegritySnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import java.util.ArrayList;
import java.util.List;

public class StructuralIntegritySnapshot {

    public static class ZoneData {
        public String zoneName;
        public float currentHealth;
        public float maxHealth;

        public ZoneData() {}
    }

    public List<ZoneData> zones = new ArrayList<>();

    public StructuralIntegritySnapshot() {}
}
```

Add to `StructuralIntegrityComponent` — implement `Snapshotable<StructuralIntegritySnapshot>`. Iterate the `Array<StructuralZone>` and create a `ZoneData` per zone.

- [ ] **Step 3: Implement CompartmentAtmosphereSnapshot + wire CompartmentAtmosphereComponent**

`CompartmentAtmosphereSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

public class CompartmentAtmosphereSnapshot {
    public float o2Pressure;
    public float co2Pressure;
    public float n2Pressure;
    public float volume;
    public float temperature;
    public int crewCount;
    public float activityLevel;

    public CompartmentAtmosphereSnapshot() {}
}
```

Add to `CompartmentAtmosphereComponent` — implement `Snapshotable<CompartmentAtmosphereSnapshot>`.

- [ ] **Step 4: Implement DockingStateSnapshot + wire DockingStateComponent**

`DockingStateSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import java.util.UUID;

public class DockingStateSnapshot {
    public String dockingPhase;
    public UUID targetEntityId;
    public float approachAxisX;
    public float approachAxisY;
    public float approachAxisZ;
    public float coneHalfAngleDeg;
    public float maxApproachSpeed;

    public DockingStateSnapshot() {}
}
```

Add to `DockingStateComponent` — implement `Snapshotable<DockingStateSnapshot>`. Convert `DockingPhase` enum to string, convert `targetEntity` to UUID via `PersistenceIdComponent`.

- [ ] **Step 5: Implement CombatAISnapshot + wire CombatAIComponent**

`CombatAISnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

import java.util.UUID;

public class CombatAISnapshot {
    public UUID currentTargetId;
    public float aggroRange;
    public float engageRange;
    public float preferredRangeMin;
    public float preferredRangeMax;
    public float aggression;
    public float threatLevel;
    public float lastKnownX;
    public float lastKnownY;
    public float lastKnownZ;
    public boolean hasLastKnownPosition;
    public float searchTimer;
    public float searchDuration;
    public String archetypeId;

    public CombatAISnapshot() {}
}
```

Add to `CombatAIComponent` — implement `Snapshotable<CombatAISnapshot>`. The `behaviorTree` field is NOT serialized — it's rebuilt from the `archetypeId` on load. The `currentTarget` Entity is converted to UUID.

- [ ] **Step 6: Implement SquadSnapshot + wire SquadComponent**

`SquadSnapshot.java`:
```java
package com.galacticodyssey.persistence.snapshots;

public class SquadSnapshot {
    public int squadId;

    public SquadSnapshot() {}
}
```

Add to `SquadComponent` — implement `Snapshotable<SquadSnapshot>`.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/snapshots/ \
        core/src/main/java/com/galacticodyssey/ship/ \
        core/src/main/java/com/galacticodyssey/combat/components/
git commit -m "feat(persistence): add ship subsystem and NPC snapshots (Thermal, Structural, Atmo, Docking, AI, Squad)"
```

---

## Task 9: KryoRegistrar

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/KryoRegistrar.java`
- Test: `core/src/test/java/com/galacticodyssey/persistence/KryoRegistrarTest.java`

- [ ] **Step 1: Write KryoRegistrar test**

```java
package com.galacticodyssey.persistence;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.galacticodyssey.persistence.snapshots.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KryoRegistrarTest {
    private Kryo kryo;

    @BeforeEach
    void setUp() {
        kryo = new Kryo();
        KryoRegistrar.register(kryo);
    }

    @Test
    void roundTripManifestData() {
        ManifestData original = new ManifestData("test-save", 42L,
            UUID.randomUUID(), UUID.randomUUID());

        byte[] bytes = serialize(original);
        ManifestData restored = deserialize(bytes, ManifestData.class);

        assertEquals("test-save", restored.saveName);
        assertEquals(42L, restored.galaxySeed);
        assertEquals(original.playerEntityId, restored.playerEntityId);
    }

    @Test
    void roundTripEntitySnapshot() {
        EntitySnapshot original = new EntitySnapshot(UUID.randomUUID());
        original.putSnapshot("Health", new HealthSnapshot());
        original.addTag("HostileTag");

        byte[] bytes = serialize(original);
        EntitySnapshot restored = deserialize(bytes, EntitySnapshot.class);

        assertEquals(original.entityId, restored.entityId);
        assertTrue(restored.hasTag("HostileTag"));
        assertNotNull(restored.componentSnapshots.get("Health"));
    }

    @Test
    void roundTripTransformSnapshot() {
        TransformSnapshot original = new TransformSnapshot(
            1_000_000.5, 2_000_000.5, 3_000_000.5,
            0f, 0.707f, 0f, 0.707f);

        byte[] bytes = serialize(original);
        TransformSnapshot restored = deserialize(bytes, TransformSnapshot.class);

        assertEquals(1_000_000.5, restored.galaxyX, 1e-10);
        assertEquals(0.707f, restored.rotY, 1e-5f);
    }

    private byte[] serialize(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Output output = new Output(baos)) {
            kryo.writeObject(output, obj);
        }
        return baos.toByteArray();
    }

    private <T> T deserialize(byte[] bytes, Class<T> type) {
        try (Input input = new Input(new ByteArrayInputStream(bytes))) {
            return kryo.readObject(input, type);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.KryoRegistrarTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Implement KryoRegistrar**

```java
package com.galacticodyssey.persistence;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;
import com.galacticodyssey.persistence.snapshots.*;

import java.util.*;

public class KryoRegistrar {

    public static void register(Kryo kryo) {
        kryo.setDefaultSerializer(CompatibleFieldSerializer.class);
        kryo.setRegistrationRequired(true);

        // JDK types
        kryo.register(UUID.class, 10);
        kryo.register(ArrayList.class, 11);
        kryo.register(HashMap.class, 12);
        kryo.register(HashSet.class, 13);
        kryo.register(EnumMap.class, 14);
        kryo.register(byte[].class, 15);
        kryo.register(float[].class, 16);
        kryo.register(int[].class, 17);
        kryo.register(String[].class, 18);

        // Persistence core
        kryo.register(ManifestData.class, 20);
        kryo.register(SaveBundle.class, 21);
        kryo.register(EntitySnapshot.class, 22);
        kryo.register(WorldModification.class, 23);

        // Player snapshots
        kryo.register(TransformSnapshot.class, 30);
        kryo.register(HealthSnapshot.class, 31);
        kryo.register(PlayerStateSnapshot.class, 32);
        kryo.register(MovementStateSnapshot.class, 33);
        kryo.register(FPSCameraSnapshot.class, 34);
        kryo.register(PlayerWalletSnapshot.class, 35);

        // Combat snapshots
        kryo.register(ShieldSnapshot.class, 40);
        kryo.register(ArmorSnapshot.class, 41);
        kryo.register(RangedWeaponSnapshot.class, 42);
        kryo.register(MeleeWeaponSnapshot.class, 43);
        kryo.register(WeaponInventorySnapshot.class, 44);
        kryo.register(StatusEffectsSnapshot.class, 45);
        kryo.register(StatusEffectsSnapshot.ActiveEffectData.class, 46);

        // Equipment snapshots
        kryo.register(ItemSnapshot.class, 50);
        kryo.register(InventorySnapshot.class, 51);
        kryo.register(EquipmentSlotsSnapshot.class, 52);

        // Ship snapshots
        kryo.register(ShipDataSnapshot.class, 60);
        kryo.register(ShipFlightSnapshot.class, 61);
        kryo.register(CargoBaySnapshot.class, 62);
        kryo.register(EngineSpecSnapshot.class, 63);
        kryo.register(FuelTankSnapshot.class, 64);

        // Ship subsystem snapshots
        kryo.register(ThermalStateSnapshot.class, 70);
        kryo.register(StructuralIntegritySnapshot.class, 71);
        kryo.register(StructuralIntegritySnapshot.ZoneData.class, 72);
        kryo.register(CompartmentAtmosphereSnapshot.class, 73);
        kryo.register(DockingStateSnapshot.class, 74);

        // NPC snapshots
        kryo.register(CombatAISnapshot.class, 80);
        kryo.register(SquadSnapshot.class, 81);

        // Enums used in snapshots — register all enums referenced by snapshot fields
        registerEnums(kryo);
    }

    private static void registerEnums(Kryo kryo) {
        // Register starting at ID 100
        kryo.register(com.galacticodyssey.player.components.PlayerStateComponent.PlayerMode.class, 100);
        kryo.register(com.galacticodyssey.combat.CombatEnums.ShieldType.class, 101);
        kryo.register(com.galacticodyssey.combat.CombatEnums.DamageType.class, 102);
        kryo.register(com.galacticodyssey.combat.CombatEnums.HitRegion.class, 103);
        kryo.register(com.galacticodyssey.combat.CombatEnums.FiringMode.class, 104);
        kryo.register(com.galacticodyssey.combat.CombatEnums.WeightClass.class, 105);
        kryo.register(com.galacticodyssey.combat.CombatEnums.AttackDirection.class, 106);
        kryo.register(com.galacticodyssey.combat.CombatEnums.StatusEffectType.class, 107);
        kryo.register(com.galacticodyssey.combat.CombatEnums.QualityTier.class, 108);
        kryo.register(com.galacticodyssey.equipment.EquipmentEnums.EquipmentSlot.class, 109);
        kryo.register(com.galacticodyssey.equipment.EquipmentEnums.ItemType.class, 110);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.KryoRegistrarTest" --info`
Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/KryoRegistrar.java \
        core/src/test/java/com/galacticodyssey/persistence/KryoRegistrarTest.java
git commit -m "feat(persistence): add KryoRegistrar with fixed IDs for all snapshot types"
```

---

## Task 10: EntitySnapshotBuilder

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/EntitySnapshotBuilder.java`
- Test: `core/src/test/java/com/galacticodyssey/persistence/EntitySnapshotBuilderTest.java`

- [ ] **Step 1: Write EntitySnapshotBuilder test**

```java
package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.core.CoordinateManager;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.persistence.snapshots.HealthSnapshot;
import com.galacticodyssey.persistence.snapshots.TransformSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntitySnapshotBuilderTest {

    @Test
    void collectsSnapshotsFromEntitiesWithPersistenceId() {
        Engine engine = new Engine();

        Entity player = new Entity();
        PersistenceIdComponent pid = new PersistenceIdComponent();
        player.add(pid);

        TransformComponent tc = new TransformComponent();
        tc.position.set(10f, 20f, 30f);
        player.add(tc);

        HealthComponent hc = new HealthComponent();
        hc.currentHP = 75f;
        hc.maxHP = 100f;
        player.add(hc);

        engine.addEntity(player);

        EntitySnapshotBuilder builder = new EntitySnapshotBuilder();
        List<EntitySnapshot> snapshots = builder.buildSnapshots(engine, 0.0, 0.0, 0.0);

        assertEquals(1, snapshots.size());
        EntitySnapshot snap = snapshots.get(0);
        assertEquals(pid.uuid, snap.entityId);

        TransformSnapshot ts = snap.getSnapshot("Transform", TransformSnapshot.class);
        assertNotNull(ts);
        assertEquals(10.0, ts.galaxyX, 1e-5);

        HealthSnapshot hs = snap.getSnapshot("Health", HealthSnapshot.class);
        assertNotNull(hs);
        assertEquals(75f, hs.currentHP);
    }

    @Test
    void skipsEntitiesWithoutPersistenceId() {
        Engine engine = new Engine();

        Entity particle = new Entity();
        particle.add(new TransformComponent());
        engine.addEntity(particle);

        EntitySnapshotBuilder builder = new EntitySnapshotBuilder();
        List<EntitySnapshot> snapshots = builder.buildSnapshots(engine, 0.0, 0.0, 0.0);

        assertEquals(0, snapshots.size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.EntitySnapshotBuilderTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Implement EntitySnapshotBuilder**

```java
package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.persistence.snapshots.TransformSnapshot;

import java.util.ArrayList;
import java.util.List;

public class EntitySnapshotBuilder {
    private static final ComponentMapper<PersistenceIdComponent> persistenceMapper =
        ComponentMapper.getFor(PersistenceIdComponent.class);
    private static final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    public List<EntitySnapshot> buildSnapshots(Engine engine,
                                                double originOffsetX,
                                                double originOffsetY,
                                                double originOffsetZ) {
        List<EntitySnapshot> result = new ArrayList<>();
        ImmutableArray<Entity> entities = engine.getEntitiesFor(
            Family.all(PersistenceIdComponent.class).get());

        for (Entity entity : entities) {
            PersistenceIdComponent pid = persistenceMapper.get(entity);
            EntitySnapshot snapshot = new EntitySnapshot(pid.uuid);

            // Handle TransformComponent specially (needs origin offset)
            TransformComponent tc = transformMapper.get(entity);
            if (tc != null) {
                snapshot.putSnapshot("Transform",
                    tc.takeSnapshot(originOffsetX, originOffsetY, originOffsetZ));
            }

            // Handle all other Snapshotable components
            for (Component component : entity.getComponents()) {
                if (component instanceof TransformComponent) continue;
                if (component instanceof PersistenceIdComponent) continue;

                if (component instanceof Snapshotable<?>) {
                    String typeName = component.getClass().getSimpleName()
                        .replace("Component", "");
                    snapshot.putSnapshot(typeName, ((Snapshotable<?>) component).takeSnapshot());
                } else {
                    // Tag component — no mutable state, just record presence
                    String typeName = component.getClass().getSimpleName();
                    snapshot.addTag(typeName);
                }
            }

            result.add(snapshot);
        }
        return result;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.EntitySnapshotBuilderTest" --info`
Expected: 2 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/EntitySnapshotBuilder.java \
        core/src/test/java/com/galacticodyssey/persistence/EntitySnapshotBuilderTest.java
git commit -m "feat(persistence): add EntitySnapshotBuilder — collects snapshots from all persistent entities"
```

---

## Task 11: ReferenceResolver

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/ReferenceResolver.java`
- Test: `core/src/test/java/com/galacticodyssey/persistence/ReferenceResolverTest.java`

- [ ] **Step 1: Write ReferenceResolver test**

```java
package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.player.components.PlayerStateComponent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceResolverTest {

    @Test
    void resolvesPlayerShipReference() {
        Engine engine = new Engine();

        UUID shipUuid = UUID.randomUUID();
        Entity ship = new Entity();
        PersistenceIdComponent shipPid = new PersistenceIdComponent(shipUuid);
        ship.add(shipPid);
        engine.addEntity(ship);

        Entity player = new Entity();
        PlayerStateComponent ps = new PlayerStateComponent();
        ps.currentShipId = shipUuid;
        player.add(ps);
        player.add(new PersistenceIdComponent());
        engine.addEntity(player);

        Map<UUID, Entity> entityMap = new HashMap<>();
        entityMap.put(shipUuid, ship);
        entityMap.put(player.getComponent(PersistenceIdComponent.class).uuid, player);

        ReferenceResolver resolver = new ReferenceResolver();
        resolver.resolve(engine, entityMap);

        assertSame(ship, ps.currentShip);
    }

    @Test
    void handlesNullReferencesGracefully() {
        Engine engine = new Engine();

        Entity player = new Entity();
        PlayerStateComponent ps = new PlayerStateComponent();
        ps.currentShipId = null;
        player.add(ps);
        player.add(new PersistenceIdComponent());
        engine.addEntity(player);

        Map<UUID, Entity> entityMap = new HashMap<>();
        entityMap.put(player.getComponent(PersistenceIdComponent.class).uuid, player);

        ReferenceResolver resolver = new ReferenceResolver();
        resolver.resolve(engine, entityMap);

        assertNull(ps.currentShip);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.ReferenceResolverTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Implement ReferenceResolver**

```java
package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.combat.components.CombatAIComponent;
import com.galacticodyssey.player.components.PlayerStateComponent;
import com.galacticodyssey.ship.docking.components.DockingStateComponent;

import java.util.Map;
import java.util.UUID;

public class ReferenceResolver {
    private static final ComponentMapper<PlayerStateComponent> playerStateMapper =
        ComponentMapper.getFor(PlayerStateComponent.class);
    private static final ComponentMapper<CombatAIComponent> combatAIMapper =
        ComponentMapper.getFor(CombatAIComponent.class);
    private static final ComponentMapper<DockingStateComponent> dockingMapper =
        ComponentMapper.getFor(DockingStateComponent.class);

    public void resolve(Engine engine, Map<UUID, Entity> entityMap) {
        ImmutableArray<Entity> entities = engine.getEntitiesFor(
            Family.all(PersistenceIdComponent.class).get());

        for (Entity entity : entities) {
            PlayerStateComponent ps = playerStateMapper.get(entity);
            if (ps != null) {
                ps.currentShip = resolveRef(ps.currentShipId, entityMap);
                ps.interactionTarget = resolveRef(ps.interactionTargetId, entityMap);
            }

            CombatAIComponent ai = combatAIMapper.get(entity);
            if (ai != null && ai.currentTargetId != null) {
                ai.currentTarget = resolveRef(ai.currentTargetId, entityMap);
            }

            DockingStateComponent dock = dockingMapper.get(entity);
            if (dock != null && dock.targetEntityId != null) {
                dock.targetEntity = resolveRef(dock.targetEntityId, entityMap);
            }
        }
    }

    private Entity resolveRef(UUID uuid, Map<UUID, Entity> entityMap) {
        if (uuid == null) return null;
        return entityMap.get(uuid);
    }
}
```

Note: `CombatAIComponent` and `DockingStateComponent` need a `UUID currentTargetId` / `UUID targetEntityId` field added alongside their existing Entity reference fields (same pattern as `PlayerStateComponent.currentShipId`). The implementer should add these fields when wiring the Snapshotable implementations in Tasks 5 and 8.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.ReferenceResolverTest" --info`
Expected: 2 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/ReferenceResolver.java \
        core/src/test/java/com/galacticodyssey/persistence/ReferenceResolverTest.java
git commit -m "feat(persistence): add ReferenceResolver — UUID-to-Entity second pass on load"
```

---

## Task 12: SaveWriter and SaveReader

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/SaveWriter.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/SaveReader.java`
- Test: `core/src/test/java/com/galacticodyssey/persistence/SaveWriterReaderTest.java`

- [ ] **Step 1: Write SaveWriter/SaveReader round-trip test**

```java
package com.galacticodyssey.persistence;

import com.galacticodyssey.persistence.snapshots.HealthSnapshot;
import com.galacticodyssey.persistence.snapshots.TransformSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SaveWriterReaderTest {

    @TempDir
    File tempDir;

    @Test
    void roundTripSaveBundle() {
        SaveBundle original = new SaveBundle();
        original.manifest = new ManifestData("test", 12345L,
            UUID.randomUUID(), UUID.randomUUID());

        EntitySnapshot playerSnap = new EntitySnapshot(original.manifest.playerEntityId);
        HealthSnapshot hs = new HealthSnapshot();
        hs.currentHP = 80f;
        hs.maxHP = 100f;
        hs.alive = true;
        playerSnap.putSnapshot("Health", hs);

        TransformSnapshot ts = new TransformSnapshot(1e6, 2e6, 3e6, 0, 0, 0, 1);
        playerSnap.putSnapshot("Transform", ts);
        original.playerSnapshot = playerSnap;

        UUID systemId = UUID.randomUUID();
        EntitySnapshot npcSnap = new EntitySnapshot(UUID.randomUUID());
        npcSnap.addTag("HostileTagComponent");
        original.systemSnapshots.put(systemId, List.of(npcSnap));

        File saveDir = new File(tempDir, "test-save");

        SaveWriter writer = new SaveWriter();
        writer.write(original, saveDir);

        assertTrue(new File(saveDir, "manifest.bin").exists());
        assertTrue(new File(saveDir, "player.bin").exists());

        SaveReader reader = new SaveReader();
        SaveBundle restored = reader.read(saveDir);

        assertEquals("test", restored.manifest.saveName);
        assertEquals(12345L, restored.manifest.galaxySeed);
        assertEquals(original.manifest.playerEntityId, restored.manifest.playerEntityId);

        HealthSnapshot rhs = restored.playerSnapshot.getSnapshot("Health", HealthSnapshot.class);
        assertEquals(80f, rhs.currentHP);

        TransformSnapshot rts = restored.playerSnapshot.getSnapshot("Transform", TransformSnapshot.class);
        assertEquals(1e6, rts.galaxyX, 1e-5);

        assertTrue(restored.systemSnapshots.containsKey(systemId));
        assertEquals(1, restored.systemSnapshots.get(systemId).size());
        assertTrue(restored.systemSnapshots.get(systemId).get(0).hasTag("HostileTagComponent"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.SaveWriterReaderTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Implement SaveWriter**

```java
package com.galacticodyssey.persistence;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SaveWriter {
    private final Kryo kryo;

    public SaveWriter() {
        this.kryo = new Kryo();
        KryoRegistrar.register(kryo);
    }

    public void write(SaveBundle bundle, File saveDir) {
        File tempDir = new File(saveDir.getParentFile(),
            saveDir.getName() + ".tmp." + System.currentTimeMillis());
        tempDir.mkdirs();

        try {
            writeObject(new File(tempDir, "manifest.bin"), bundle.manifest);
            writeObject(new File(tempDir, "player.bin"), bundle.playerSnapshot);

            if (!bundle.ownedShipSnapshots.isEmpty()) {
                writeObject(new File(tempDir, "ships.bin"), bundle.ownedShipSnapshots);
            }

            for (Map.Entry<UUID, List<EntitySnapshot>> entry : bundle.systemSnapshots.entrySet()) {
                writeObject(new File(tempDir, "system_" + entry.getKey() + ".bin"),
                    entry.getValue());
            }

            if (!bundle.worldModifications.isEmpty()) {
                writeObject(new File(tempDir, "modifications.bin"), bundle.worldModifications);
            }
            if (!bundle.economyState.isEmpty()) {
                writeObject(new File(tempDir, "economy.bin"), bundle.economyState);
            }
            if (!bundle.factionState.isEmpty()) {
                writeObject(new File(tempDir, "factions.bin"), bundle.factionState);
            }
            if (!bundle.discoveredSystemIds.isEmpty() || !bundle.discoveredPlanetIds.isEmpty()) {
                writeObject(new File(tempDir, "discovered.bin"), new Object[]{
                    bundle.discoveredSystemIds, bundle.discoveredPlanetIds
                });
            }

            // Atomic swap: delete old, rename temp to final
            if (saveDir.exists()) {
                deleteRecursive(saveDir);
            }
            if (!tempDir.renameTo(saveDir)) {
                throw new IOException("Failed to rename temp save dir to " + saveDir);
            }
        } catch (Exception e) {
            deleteRecursive(tempDir);
            throw new RuntimeException("Save failed", e);
        }
    }

    private void writeObject(File file, Object obj) {
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeObject(output, obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write " + file.getName(), e);
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
```

- [ ] **Step 4: Implement SaveReader**

```java
package com.galacticodyssey.persistence;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class SaveReader {
    private final Kryo kryo;

    public SaveReader() {
        this.kryo = new Kryo();
        KryoRegistrar.register(kryo);
    }

    public SaveBundle read(File saveDir) {
        SaveBundle bundle = new SaveBundle();

        bundle.manifest = readObject(new File(saveDir, "manifest.bin"), ManifestData.class);
        bundle.playerSnapshot = readObject(new File(saveDir, "player.bin"), EntitySnapshot.class);

        File shipsFile = new File(saveDir, "ships.bin");
        if (shipsFile.exists()) {
            @SuppressWarnings("unchecked")
            ArrayList<EntitySnapshot> ships = readObject(shipsFile, ArrayList.class);
            bundle.ownedShipSnapshots = ships;
        }

        // Read system snapshot files
        File[] systemFiles = saveDir.listFiles((dir, name) ->
            name.startsWith("system_") && name.endsWith(".bin"));
        if (systemFiles != null) {
            for (File sf : systemFiles) {
                String uuidStr = sf.getName()
                    .replace("system_", "")
                    .replace(".bin", "");
                UUID systemId = UUID.fromString(uuidStr);
                @SuppressWarnings("unchecked")
                ArrayList<EntitySnapshot> entities = readObject(sf, ArrayList.class);
                bundle.systemSnapshots.put(systemId, entities);
            }
        }

        File modsFile = new File(saveDir, "modifications.bin");
        if (modsFile.exists()) {
            @SuppressWarnings("unchecked")
            HashMap<UUID, WorldModification> mods = readObject(modsFile, HashMap.class);
            bundle.worldModifications = mods;
        }

        File econFile = new File(saveDir, "economy.bin");
        if (econFile.exists()) {
            @SuppressWarnings("unchecked")
            HashMap<String, Object> econ = readObject(econFile, HashMap.class);
            bundle.economyState = econ;
        }

        File factionFile = new File(saveDir, "factions.bin");
        if (factionFile.exists()) {
            @SuppressWarnings("unchecked")
            HashMap<String, Object> factions = readObject(factionFile, HashMap.class);
            bundle.factionState = factions;
        }

        return bundle;
    }

    private <T> T readObject(File file, Class<T> type) {
        try (Input input = new Input(new FileInputStream(file))) {
            return kryo.readObject(input, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read " + file.getName(), e);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.SaveWriterReaderTest" --info`
Expected: 1 test PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/SaveWriter.java \
        core/src/main/java/com/galacticodyssey/persistence/SaveReader.java \
        core/src/test/java/com/galacticodyssey/persistence/SaveWriterReaderTest.java
git commit -m "feat(persistence): add SaveWriter and SaveReader — Kryo serialization to/from save folders"
```

---

## Task 13: SaveBackend and LocalFileSaveBackend

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/SaveBackend.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/LocalFileSaveBackend.java`
- Test: `core/src/test/java/com/galacticodyssey/persistence/LocalFileSaveBackendTest.java`

- [ ] **Step 1: Write LocalFileSaveBackend test**

```java
package com.galacticodyssey.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileSaveBackendTest {

    @TempDir
    File tempDir;

    @Test
    void writeAndReadSave() {
        LocalFileSaveBackend backend = new LocalFileSaveBackend(tempDir);

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("my-save", 42L,
            UUID.randomUUID(), UUID.randomUUID());
        bundle.playerSnapshot = new EntitySnapshot(bundle.manifest.playerEntityId);

        backend.writeSave("my-save", bundle);

        SaveBundle restored = backend.readSave("my-save");
        assertEquals("my-save", restored.manifest.saveName);
        assertEquals(42L, restored.manifest.galaxySeed);
    }

    @Test
    void listSaves() {
        LocalFileSaveBackend backend = new LocalFileSaveBackend(tempDir);

        SaveBundle b1 = new SaveBundle();
        b1.manifest = new ManifestData("save-a", 1L, UUID.randomUUID(), UUID.randomUUID());
        b1.playerSnapshot = new EntitySnapshot(b1.manifest.playerEntityId);
        backend.writeSave("save-a", b1);

        SaveBundle b2 = new SaveBundle();
        b2.manifest = new ManifestData("save-b", 2L, UUID.randomUUID(), UUID.randomUUID());
        b2.playerSnapshot = new EntitySnapshot(b2.manifest.playerEntityId);
        backend.writeSave("save-b", b2);

        List<ManifestData> saves = backend.listSaves();
        assertEquals(2, saves.size());
    }

    @Test
    void deleteSave() {
        LocalFileSaveBackend backend = new LocalFileSaveBackend(tempDir);

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("doomed", 0L, UUID.randomUUID(), UUID.randomUUID());
        bundle.playerSnapshot = new EntitySnapshot(bundle.manifest.playerEntityId);
        backend.writeSave("doomed", bundle);

        backend.deleteSave("doomed");
        assertEquals(0, backend.listSaves().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.LocalFileSaveBackendTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Implement SaveBackend interface**

```java
package com.galacticodyssey.persistence;

import java.util.List;

public interface SaveBackend {
    void writeSave(String saveId, SaveBundle bundle);
    SaveBundle readSave(String saveId);
    List<ManifestData> listSaves();
    void deleteSave(String saveId);
}
```

- [ ] **Step 4: Implement LocalFileSaveBackend**

```java
package com.galacticodyssey.persistence;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LocalFileSaveBackend implements SaveBackend {
    private final File savesRoot;
    private final SaveWriter writer;
    private final SaveReader reader;

    public LocalFileSaveBackend(File savesRoot) {
        this.savesRoot = savesRoot;
        this.writer = new SaveWriter();
        this.reader = new SaveReader();
        if (!savesRoot.exists()) {
            savesRoot.mkdirs();
        }
    }

    @Override
    public void writeSave(String saveId, SaveBundle bundle) {
        File saveDir = new File(savesRoot, saveId);
        writer.write(bundle, saveDir);
    }

    @Override
    public SaveBundle readSave(String saveId) {
        File saveDir = new File(savesRoot, saveId);
        if (!saveDir.exists()) {
            throw new RuntimeException("Save not found: " + saveId);
        }
        return reader.read(saveDir);
    }

    @Override
    public List<ManifestData> listSaves() {
        List<ManifestData> result = new ArrayList<>();
        File[] dirs = savesRoot.listFiles(File::isDirectory);
        if (dirs == null) return result;

        for (File dir : dirs) {
            File manifestFile = new File(dir, "manifest.bin");
            if (manifestFile.exists()) {
                try {
                    SaveBundle bundle = reader.read(dir);
                    result.add(bundle.manifest);
                } catch (Exception e) {
                    // Corrupted save — skip
                }
            }
        }

        result.sort((a, b) -> Long.compare(b.timestampMillis, a.timestampMillis));
        return result;
    }

    @Override
    public void deleteSave(String saveId) {
        File saveDir = new File(savesRoot, saveId);
        if (saveDir.exists()) {
            deleteRecursive(saveDir);
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.LocalFileSaveBackendTest" --info`
Expected: 3 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/SaveBackend.java \
        core/src/main/java/com/galacticodyssey/persistence/LocalFileSaveBackend.java \
        core/src/test/java/com/galacticodyssey/persistence/LocalFileSaveBackendTest.java
git commit -m "feat(persistence): add SaveBackend interface and LocalFileSaveBackend"
```

---

## Task 14: SaveMigration Framework

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/migration/SaveMigration.java`
- Create: `core/src/main/java/com/galacticodyssey/persistence/migration/SaveMigrator.java`
- Test: `core/src/test/java/com/galacticodyssey/persistence/SaveMigratorTest.java`

- [ ] **Step 1: Write SaveMigrator test**

```java
package com.galacticodyssey.persistence;

import com.galacticodyssey.persistence.migration.SaveMigration;
import com.galacticodyssey.persistence.migration.SaveMigrator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SaveMigratorTest {

    @Test
    void migratesFromOldVersion() {
        SaveMigrator migrator = new SaveMigrator();
        migrator.addMigration(new SaveMigration() {
            @Override public int fromVersion() { return 1; }
            @Override public int toVersion() { return 2; }
            @Override public void migrate(SaveBundle bundle) {
                bundle.manifest.saveName = bundle.manifest.saveName + "-migrated";
            }
        });

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("old-save", 0L, UUID.randomUUID(), UUID.randomUUID());
        bundle.manifest.saveVersion = 1;

        migrator.migrateToCurrentVersion(bundle, 2);

        assertEquals(2, bundle.manifest.saveVersion);
        assertEquals("old-save-migrated", bundle.manifest.saveName);
    }

    @Test
    void chainsMultipleMigrations() {
        SaveMigrator migrator = new SaveMigrator();
        migrator.addMigration(new SaveMigration() {
            @Override public int fromVersion() { return 1; }
            @Override public int toVersion() { return 2; }
            @Override public void migrate(SaveBundle bundle) {
                bundle.manifest.saveName = bundle.manifest.saveName + "-v2";
            }
        });
        migrator.addMigration(new SaveMigration() {
            @Override public int fromVersion() { return 2; }
            @Override public int toVersion() { return 3; }
            @Override public void migrate(SaveBundle bundle) {
                bundle.manifest.saveName = bundle.manifest.saveName + "-v3";
            }
        });

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("test", 0L, UUID.randomUUID(), UUID.randomUUID());
        bundle.manifest.saveVersion = 1;

        migrator.migrateToCurrentVersion(bundle, 3);

        assertEquals(3, bundle.manifest.saveVersion);
        assertEquals("test-v2-v3", bundle.manifest.saveName);
    }

    @Test
    void rejectsFutureSaveVersion() {
        SaveMigrator migrator = new SaveMigrator();

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("future", 0L, UUID.randomUUID(), UUID.randomUUID());
        bundle.manifest.saveVersion = 99;

        assertThrows(IllegalArgumentException.class, () ->
            migrator.migrateToCurrentVersion(bundle, 1));
    }

    @Test
    void noOpIfAlreadyCurrent() {
        SaveMigrator migrator = new SaveMigrator();

        SaveBundle bundle = new SaveBundle();
        bundle.manifest = new ManifestData("current", 0L, UUID.randomUUID(), UUID.randomUUID());
        bundle.manifest.saveVersion = 1;

        migrator.migrateToCurrentVersion(bundle, 1);
        assertEquals("current", bundle.manifest.saveName);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.SaveMigratorTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Implement SaveMigration interface**

```java
package com.galacticodyssey.persistence.migration;

import com.galacticodyssey.persistence.SaveBundle;

public interface SaveMigration {
    int fromVersion();
    int toVersion();
    void migrate(SaveBundle bundle);
}
```

- [ ] **Step 4: Implement SaveMigrator**

```java
package com.galacticodyssey.persistence.migration;

import com.galacticodyssey.persistence.SaveBundle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SaveMigrator {
    private final List<SaveMigration> migrations = new ArrayList<>();

    public void addMigration(SaveMigration migration) {
        migrations.add(migration);
        migrations.sort(Comparator.comparingInt(SaveMigration::fromVersion));
    }

    public void migrateToCurrentVersion(SaveBundle bundle, int currentVersion) {
        int saveVersion = bundle.manifest.saveVersion;

        if (saveVersion > currentVersion) {
            throw new IllegalArgumentException(
                "Save version " + saveVersion + " is newer than game version " + currentVersion
                + ". Cannot load saves from a newer build.");
        }

        while (bundle.manifest.saveVersion < currentVersion) {
            int fromVersion = bundle.manifest.saveVersion;
            SaveMigration migration = findMigration(fromVersion);
            if (migration == null) {
                throw new IllegalStateException(
                    "No migration found from version " + fromVersion);
            }
            migration.migrate(bundle);
            bundle.manifest.saveVersion = migration.toVersion();
        }
    }

    private SaveMigration findMigration(int fromVersion) {
        for (SaveMigration m : migrations) {
            if (m.fromVersion() == fromVersion) return m;
        }
        return null;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.SaveMigratorTest" --info`
Expected: 4 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/migration/ \
        core/src/test/java/com/galacticodyssey/persistence/SaveMigratorTest.java
git commit -m "feat(persistence): add SaveMigration interface and SaveMigrator chain"
```

---

## Task 15: SaveCoordinator

**Files:**
- Create: `core/src/main/java/com/galacticodyssey/persistence/SaveCoordinator.java`
- Test: `core/src/test/java/com/galacticodyssey/persistence/SaveCoordinatorTest.java`

- [ ] **Step 1: Write SaveCoordinator test**

```java
package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.components.HealthComponent;
import com.galacticodyssey.core.CoordinateManager;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.SaveCompleteEvent;
import com.galacticodyssey.core.events.LoadCompleteEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SaveCoordinatorTest {

    @TempDir
    File tempDir;

    @Test
    void saveAndLoadRoundTrip() {
        EventBus eventBus = new EventBus();
        Engine engine = new Engine();
        LocalFileSaveBackend backend = new LocalFileSaveBackend(tempDir);

        // Create a player entity
        Entity player = new Entity();
        PersistenceIdComponent pid = new PersistenceIdComponent();
        player.add(pid);

        TransformComponent tc = new TransformComponent();
        tc.position.set(5f, 10f, 15f);
        player.add(tc);

        HealthComponent hc = new HealthComponent();
        hc.currentHP = 55f;
        hc.maxHP = 100f;
        player.add(hc);

        engine.addEntity(player);

        SaveCoordinator coordinator = new SaveCoordinator(
            eventBus, engine, backend, 42L, pid.uuid, null);

        // Track events
        AtomicBoolean saveComplete = new AtomicBoolean(false);
        eventBus.subscribe(SaveCompleteEvent.class, e -> saveComplete.set(true));

        // Save
        coordinator.save("test-save");
        assertTrue(saveComplete.get());

        // Modify state
        hc.currentHP = 10f;
        tc.position.set(0f, 0f, 0f);

        // Load
        AtomicBoolean loadComplete = new AtomicBoolean(false);
        eventBus.subscribe(LoadCompleteEvent.class, e -> loadComplete.set(true));

        coordinator.load("test-save");
        assertTrue(loadComplete.get());

        // Verify restored state — find the player entity by PersistenceId
        Entity restored = null;
        for (Entity e : engine.getEntitiesFor(
                com.badlogic.ashley.core.Family.all(PersistenceIdComponent.class).get())) {
            if (e.getComponent(PersistenceIdComponent.class).uuid.equals(pid.uuid)) {
                restored = e;
                break;
            }
        }
        assertNotNull(restored);
        assertEquals(55f, restored.getComponent(HealthComponent.class).currentHP);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.SaveCoordinatorTest" --info`
Expected: Compilation failure.

- [ ] **Step 3: Implement SaveCoordinator**

```java
package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.galacticodyssey.core.CoordinateManager;
import com.galacticodyssey.core.EventBus;
import com.galacticodyssey.core.components.TransformComponent;
import com.galacticodyssey.core.events.LoadCompleteEvent;
import com.galacticodyssey.core.events.SaveBeginEvent;
import com.galacticodyssey.core.events.SaveCompleteEvent;
import com.galacticodyssey.core.events.SaveFailedEvent;
import com.galacticodyssey.persistence.migration.SaveMigrator;
import com.galacticodyssey.persistence.snapshots.TransformSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SaveCoordinator {
    private static final int AUTO_SAVE_SLOT_COUNT = 3;
    private static final float DEFAULT_AUTO_SAVE_INTERVAL = 300f; // 5 minutes

    private final EventBus eventBus;
    private final Engine engine;
    private final SaveBackend backend;
    private final EntitySnapshotBuilder snapshotBuilder;
    private final ReferenceResolver referenceResolver;
    private final SaveMigrator migrator;

    private final long galaxySeed;
    private final UUID playerEntityId;
    private final CoordinateManager coordinateManager;

    private float autoSaveTimer;
    private float autoSaveInterval = DEFAULT_AUTO_SAVE_INTERVAL;
    private int autoSaveSlotIndex;
    private boolean autoSaveEnabled = true;

    private static final ComponentMapper<PersistenceIdComponent> pidMapper =
        ComponentMapper.getFor(PersistenceIdComponent.class);
    private static final ComponentMapper<TransformComponent> transformMapper =
        ComponentMapper.getFor(TransformComponent.class);

    public SaveCoordinator(EventBus eventBus, Engine engine, SaveBackend backend,
                           long galaxySeed, UUID playerEntityId,
                           CoordinateManager coordinateManager) {
        this.eventBus = eventBus;
        this.engine = engine;
        this.backend = backend;
        this.galaxySeed = galaxySeed;
        this.playerEntityId = playerEntityId;
        this.coordinateManager = coordinateManager;
        this.snapshotBuilder = new EntitySnapshotBuilder();
        this.referenceResolver = new ReferenceResolver();
        this.migrator = new SaveMigrator();
    }

    public void save(String saveName) {
        long startTime = System.currentTimeMillis();
        eventBus.publish(new SaveBeginEvent(saveName));

        try {
            double ox = coordinateManager != null ? coordinateManager.getOriginOffsetX() : 0.0;
            double oy = coordinateManager != null ? coordinateManager.getOriginOffsetY() : 0.0;
            double oz = coordinateManager != null ? coordinateManager.getOriginOffsetZ() : 0.0;

            SaveBundle bundle = new SaveBundle();
            bundle.manifest = new ManifestData(saveName, galaxySeed, playerEntityId, null);

            List<EntitySnapshot> allSnapshots = snapshotBuilder.buildSnapshots(engine, ox, oy, oz);

            for (EntitySnapshot snap : allSnapshots) {
                if (snap.entityId.equals(playerEntityId)) {
                    bundle.playerSnapshot = snap;
                } else {
                    bundle.ownedShipSnapshots.add(snap);
                }
            }

            backend.writeSave(saveName, bundle);

            long duration = System.currentTimeMillis() - startTime;
            eventBus.publish(new SaveCompleteEvent(saveName, duration));
        } catch (Exception e) {
            eventBus.publish(new SaveFailedEvent(saveName, e));
        }
    }

    public void load(String saveName) {
        long startTime = System.currentTimeMillis();

        try {
            SaveBundle bundle = backend.readSave(saveName);
            migrator.migrateToCurrentVersion(bundle, ManifestData.CURRENT_VERSION);

            // Clear existing persistent entities
            ImmutableArray<Entity> existing = engine.getEntitiesFor(
                Family.all(PersistenceIdComponent.class).get());
            Entity[] toRemove = new Entity[existing.size()];
            for (int i = 0; i < existing.size(); i++) {
                toRemove[i] = existing.get(i);
            }
            for (Entity e : toRemove) {
                engine.removeEntity(e);
            }

            double ox = coordinateManager != null ? coordinateManager.getOriginOffsetX() : 0.0;
            double oy = coordinateManager != null ? coordinateManager.getOriginOffsetY() : 0.0;
            double oz = coordinateManager != null ? coordinateManager.getOriginOffsetZ() : 0.0;

            Map<UUID, Entity> entityMap = new HashMap<>();

            // Restore player
            if (bundle.playerSnapshot != null) {
                Entity player = restoreEntity(bundle.playerSnapshot, ox, oy, oz);
                engine.addEntity(player);
                entityMap.put(bundle.playerSnapshot.entityId, player);
            }

            // Restore ships
            for (EntitySnapshot shipSnap : bundle.ownedShipSnapshots) {
                Entity ship = restoreEntity(shipSnap, ox, oy, oz);
                engine.addEntity(ship);
                entityMap.put(shipSnap.entityId, ship);
            }

            // Restore system entities
            for (List<EntitySnapshot> systemEntities : bundle.systemSnapshots.values()) {
                for (EntitySnapshot snap : systemEntities) {
                    Entity e = restoreEntity(snap, ox, oy, oz);
                    engine.addEntity(e);
                    entityMap.put(snap.entityId, e);
                }
            }

            // Second pass: resolve entity references
            referenceResolver.resolve(engine, entityMap);

            long duration = System.currentTimeMillis() - startTime;
            eventBus.publish(new LoadCompleteEvent(saveName, duration));
        } catch (Exception e) {
            throw new RuntimeException("Load failed: " + saveName, e);
        }
    }

    public void update(float deltaTime) {
        if (!autoSaveEnabled) return;
        autoSaveTimer += deltaTime;
        if (autoSaveTimer >= autoSaveInterval) {
            autoSaveTimer = 0f;
            triggerAutoSave();
        }
    }

    public void triggerAutoSave() {
        String slotName = "autosave-" + autoSaveSlotIndex;
        autoSaveSlotIndex = (autoSaveSlotIndex + 1) % AUTO_SAVE_SLOT_COUNT;
        save(slotName);
    }

    public void setAutoSaveEnabled(boolean enabled) {
        this.autoSaveEnabled = enabled;
    }

    public void setAutoSaveInterval(float seconds) {
        this.autoSaveInterval = seconds;
    }

    private Entity restoreEntity(EntitySnapshot snapshot,
                                  double ox, double oy, double oz) {
        Entity entity = new Entity();
        entity.add(new PersistenceIdComponent(snapshot.entityId));

        // Restore TransformComponent
        TransformSnapshot ts = snapshot.getSnapshot("Transform", TransformSnapshot.class);
        if (ts != null) {
            TransformComponent tc = new TransformComponent();
            tc.restoreFromSnapshot(ts, ox, oy, oz);
            entity.add(tc);
        }

        // Restore all other Snapshotable components
        for (Map.Entry<String, Object> entry : snapshot.componentSnapshots.entrySet()) {
            if ("Transform".equals(entry.getKey())) continue;
            Component component = createComponentFromSnapshot(entry.getKey(), entry.getValue());
            if (component != null) {
                entity.add(component);
            }
        }

        // Restore tag components
        for (String tag : snapshot.tagComponents) {
            Component tagComponent = createTagComponent(tag);
            if (tagComponent != null) {
                entity.add(tagComponent);
            }
        }

        return entity;
    }

    private Component createComponentFromSnapshot(String typeName, Object snapshot) {
        // Map snapshot type names to component constructors + restore calls.
        // This registry-based approach avoids reflection. Each case creates
        // the component and calls restoreFromSnapshot().
        // The implementer should add cases for all Snapshotable components.
        return SnapshotComponentRegistry.createAndRestore(typeName, snapshot);
    }

    private Component createTagComponent(String tagName) {
        return SnapshotComponentRegistry.createTag(tagName);
    }
}
```

- [ ] **Step 4: Implement SnapshotComponentRegistry**

This is the central mapping from snapshot type names to component factory + restore logic. Create it as a separate file:

```java
package com.galacticodyssey.persistence;

import com.badlogic.ashley.core.Component;
import com.galacticodyssey.combat.components.*;
import com.galacticodyssey.economy.components.*;
import com.galacticodyssey.equipment.components.*;
import com.galacticodyssey.persistence.snapshots.*;
import com.galacticodyssey.player.components.*;
import com.galacticodyssey.ship.components.*;
import com.galacticodyssey.ship.docking.components.DockingStateComponent;
import com.galacticodyssey.ship.flight.components.ShipFlightComponent;
import com.galacticodyssey.ship.lifesupport.components.CompartmentAtmosphereComponent;
import com.galacticodyssey.ship.propulsion.components.EngineSpecComponent;
import com.galacticodyssey.ship.propulsion.components.FuelTankComponent;
import com.galacticodyssey.ship.structure.components.StructuralIntegrityComponent;
import com.galacticodyssey.ship.thermal.components.ThermalStateComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class SnapshotComponentRegistry {
    private static final Map<String, RestoreEntry<?>> REGISTRY = new HashMap<>();
    private static final Map<String, Supplier<Component>> TAG_REGISTRY = new HashMap<>();

    static {
        register("Health", HealthSnapshot.class, HealthComponent::new);
        register("PlayerState", PlayerStateSnapshot.class, PlayerStateComponent::new);
        register("MovementState", MovementStateSnapshot.class, MovementStateComponent::new);
        register("FPSCamera", FPSCameraSnapshot.class, FPSCameraComponent::new);
        register("PlayerWallet", PlayerWalletSnapshot.class, PlayerWalletComponent::new);
        register("Shield", ShieldSnapshot.class, ShieldComponent::new);
        register("Armor", ArmorSnapshot.class, ArmorComponent::new);
        register("RangedWeapon", RangedWeaponSnapshot.class, RangedWeaponComponent::new);
        register("MeleeWeapon", MeleeWeaponSnapshot.class, MeleeWeaponComponent::new);
        register("WeaponInventory", WeaponInventorySnapshot.class, WeaponInventoryComponent::new);
        register("StatusEffects", StatusEffectsSnapshot.class, StatusEffectsComponent::new);
        register("ShipData", ShipDataSnapshot.class, ShipDataComponent::new);
        register("ShipFlight", ShipFlightSnapshot.class, ShipFlightComponent::new);
        register("CargoBay", CargoBaySnapshot.class, CargoBayComponent::new);
        register("EngineSpec", EngineSpecSnapshot.class, EngineSpecComponent::new);
        register("FuelTank", FuelTankSnapshot.class, FuelTankComponent::new);
        register("ThermalState", ThermalStateSnapshot.class, ThermalStateComponent::new);
        register("StructuralIntegrity", StructuralIntegritySnapshot.class, StructuralIntegrityComponent::new);
        register("CompartmentAtmosphere", CompartmentAtmosphereSnapshot.class, CompartmentAtmosphereComponent::new);
        register("DockingState", DockingStateSnapshot.class, DockingStateComponent::new);
        register("CombatAI", CombatAISnapshot.class, CombatAIComponent::new);
        register("Squad", SquadSnapshot.class, SquadComponent::new);

        TAG_REGISTRY.put("HostileTagComponent", HostileTagComponent::new);
        TAG_REGISTRY.put("PlayerTagComponent", PlayerTagComponent::new);
    }

    @SuppressWarnings("unchecked")
    private static <S> void register(String name, Class<S> snapshotClass,
                                      Supplier<? extends Component> factory) {
        REGISTRY.put(name, new RestoreEntry<>(snapshotClass, factory));
    }

    @SuppressWarnings("unchecked")
    public static Component createAndRestore(String typeName, Object snapshot) {
        RestoreEntry<?> entry = REGISTRY.get(typeName);
        if (entry == null) return null;
        return entry.createAndRestore(snapshot);
    }

    public static Component createTag(String tagName) {
        Supplier<Component> supplier = TAG_REGISTRY.get(tagName);
        return supplier != null ? supplier.get() : null;
    }

    private static class RestoreEntry<S> {
        final Class<S> snapshotClass;
        final Supplier<? extends Component> factory;

        RestoreEntry(Class<S> snapshotClass, Supplier<? extends Component> factory) {
            this.snapshotClass = snapshotClass;
            this.factory = factory;
        }

        @SuppressWarnings("unchecked")
        Component createAndRestore(Object snapshot) {
            Component component = factory.get();
            if (component instanceof Snapshotable) {
                ((Snapshotable<S>) component).restoreFromSnapshot(snapshotClass.cast(snapshot));
            }
            return component;
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.galacticodyssey.persistence.SaveCoordinatorTest" --info`
Expected: 1 test PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/persistence/SaveCoordinator.java \
        core/src/main/java/com/galacticodyssey/persistence/SnapshotComponentRegistry.java \
        core/src/test/java/com/galacticodyssey/persistence/SaveCoordinatorTest.java
git commit -m "feat(persistence): add SaveCoordinator — orchestrates save/load flow with auto-save timer"
```

---

## Task 16: Wire SaveCoordinator into GameWorld

**Files:**
- Modify: `core/src/main/java/com/galacticodyssey/core/GameWorld.java`

- [ ] **Step 1: Add PersistenceIdComponent to createPlayerEntity**

In `GameWorld.createPlayerEntity()`, add a `PersistenceIdComponent` to the player entity. Store the player's UUID as a field on GameWorld for the SaveCoordinator:

```java
import com.galacticodyssey.persistence.PersistenceIdComponent;

// Add field to GameWorld:
private UUID playerEntityId;

// In createPlayerEntity(), add:
PersistenceIdComponent pid = new PersistenceIdComponent();
entity.add(pid);
this.playerEntityId = pid.uuid;
```

- [ ] **Step 2: Add PersistenceIdComponent to ship entities**

In any method that creates ship entities (check for `createShipEntity` or similar), add:

```java
entity.add(new PersistenceIdComponent());
```

Also add to `createHostileNPC()`:

```java
entity.add(new PersistenceIdComponent());
```

- [ ] **Step 3: Initialize SaveCoordinator in GameWorld**

Add SaveCoordinator as a field and initialize it in the constructor or `create()` method:

```java
import com.galacticodyssey.persistence.SaveCoordinator;
import com.galacticodyssey.persistence.LocalFileSaveBackend;

// Field:
private SaveCoordinator saveCoordinator;

// In initialization (after engine, eventBus, and coordinateManager exist):
File savesDir = Gdx.files.external("GalacticOdyssey/saves").file();
LocalFileSaveBackend saveBackend = new LocalFileSaveBackend(savesDir);
this.saveCoordinator = new SaveCoordinator(
    eventBus, engine, saveBackend, galaxySeed, playerEntityId, coordinateManager);
```

- [ ] **Step 4: Call SaveCoordinator.update() in the game loop**

In `GameWorld.update(float deltaTime)`, add:

```java
saveCoordinator.update(deltaTime);
```

- [ ] **Step 5: Expose save/load methods**

Add public methods to GameWorld for UI to call:

```java
public void saveGame(String saveName) {
    saveCoordinator.save(saveName);
}

public void loadGame(String saveName) {
    saveCoordinator.load(saveName);
}

public void triggerAutoSave() {
    saveCoordinator.triggerAutoSave();
}

public SaveCoordinator getSaveCoordinator() {
    return saveCoordinator;
}
```

- [ ] **Step 6: Run the full test suite**

Run: `./gradlew :core:test --info`
Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/galacticodyssey/core/GameWorld.java
git commit -m "feat(persistence): wire SaveCoordinator into GameWorld, add PersistenceIdComponent to entities"
```

---

## Dependency Graph

```
Task 1 (Kryo + interfaces)
  ├── Task 2 (ManifestData, SaveBundle, events)
  ├── Task 3 (TransformSnapshot)
  │     └── Task 4 (Player snapshots)
  │           ├── Task 5 (Combat snapshots)
  │           ├── Task 6 (Equipment snapshots)
  │           └── Task 7 (Ship snapshots)
  │                 └── Task 8 (Ship subsystem + NPC snapshots)
  └── Task 9 (KryoRegistrar) ← depends on all snapshot tasks
        └── Task 10 (EntitySnapshotBuilder)
              └── Task 11 (ReferenceResolver)
                    └── Task 12 (SaveWriter + SaveReader)
                          └── Task 13 (SaveBackend + LocalFileSaveBackend)
                                └── Task 14 (SaveMigrator)
                                      └── Task 15 (SaveCoordinator)
                                            └── Task 16 (Wire into GameWorld)
```

Tasks 4-8 (snapshot POJOs) can be parallelized. Tasks 2 and 3 can also run in parallel. The serialization pipeline (Tasks 9-16) is sequential.
