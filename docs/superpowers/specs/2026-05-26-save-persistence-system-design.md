# Save / Persistence System Design

**Date:** 2026-05-26
**Status:** Approved
**Approach:** Component Snapshot System (Approach A)

## Requirements

- **Multiplayer-ready:** Serialization layer shared between local save files and server-side persistence / KryoNet replication.
- **Hybrid world state:** Full snapshots for current + recently visited systems (last 3); seed + modification deltas for everything else.
- **Kryo binary format:** Consistent with KryoNet for multiplayer wire format.
- **Unlimited manual saves + 3 rotating auto-saves:** Auto-saves trigger on timer (5 min), system transitions, and docking.

---

## 1. Entity Identity & References

Every saveable entity gets a `PersistenceIdComponent` holding a `java.util.UUID`. Assigned at creation, immutable thereafter.

**Purpose:**
- Save files key entities by UUID, not Ashley's internal integer ID.
- Cross-entity references (e.g., `PlayerStateComponent.currentShip`) store UUIDs instead of live `Entity` pointers.
- Same UUID identifies an entity on server and all clients in multiplayer.

**On load:** A `Map<UUID, Entity>` is built during entity reconstruction. A second resolution pass wires UUID references back to live Entity pointers.

**Transient entities** (particle effects, screen shake, temporary VFX) skip `PersistenceIdComponent` entirely.

---

## 2. Snapshot Interface & Component Serialization

### Interface

Each saveable component implements `Snapshotable<S>`:

```java
public interface Snapshotable<S> {
    S takeSnapshot();
    void restoreFromSnapshot(S snapshot);
}
```

`S` is a plain POJO containing only serializable fields: primitives, strings, UUIDs, collections, nested POJOs. No Bullet objects, no GL resources, no Entity pointers.

### Field Type Mappings

| Runtime type | Snapshot type |
|---|---|
| `Vector3` (local position) | `float x, y, z` |
| Galaxy-space position | `double gx, gy, gz` (local pos + CoordinateManager offset) |
| `Quaternion` | `float qx, qy, qz, qw` |
| `Entity` reference | `UUID` |
| `btRigidBody` | `float[3] linearVelocity, float[3] angularVelocity` (transform from TransformComponent) |
| `Texture`, `Model`, `Mesh` | Not saved — rebuilt from asset path / blueprint seed on load |
| `btDynamicsWorld` | Not saved — rebuilt from ship interior layout on load |

### Coordinate Handling

`TransformComponent`'s snapshot stores galaxy-space `double` coordinates: the local-space `Vector3` position combined with CoordinateManager's origin offset at snapshot time. On load, CoordinateManager recenters on the player's galaxy position and converts back to local-space floats.

### Tag Components

Components with no mutable state (e.g., `HostileTagComponent`) are saved as a set of component type names per entity, indicating their presence.

### Example

```java
public class ShipDataSnapshot {
    public UUID entityId;
    public long blueprintSeed;
    public String sizeClass;
    public float mass, maxThrust, maxTurnRate, maxSpeed;
    public float hullHp, currentHullHp;
    // HullGeometry regenerated from blueprint seed — not serialized
}
```

---

## 3. Save File Structure

A save is a **folder**, not a single file. This enables partial loads and keeps individual files small.

```
saves/
  my-save-2026-05-26/
    manifest.bin          — save name, timestamp, version, galaxy seed, player UUID
    player.bin            — player entity + inventory + equipment + wallet
    ships.bin             — all player-owned ships (may be in different systems)
    system_<uuid>.bin     — full entity snapshot for a loaded star system
    modifications.bin     — player modifications to unvisited systems (mined, destroyed, built)
    economy.bin           — economy state (commodity prices, market stocks, trade history)
    factions.bin           — faction relation state, territory changes, war/peace status
    discovered.bin        — discovered system/planet UUIDs + assigned names
    thumbnail.png         — screenshot at save time for save browser UI
  autosave-0/
    ... (same structure)
  autosave-1/
    ...
  autosave-2/
    ...
```

### Hybrid World Strategy

- **Current system + recently visited (last 3):** Full snapshots in `system_<uuid>.bin` — all entities with all component snapshots.
- **Other visited systems:** Only player modifications in `modifications.bin` — regenerate from seed + apply deltas on load.
- **Unvisited systems:** Nothing stored — pure seed regeneration.

### Atomic Writes

File writes go to a temp folder first, then atomically rename to the final path. A failed save never corrupts an existing save folder.

---

## 4. Save & Load Flow

### Saving

Triggered by: manual save, auto-save timer, or auto-save event.

1. `SaveCoordinator` fires `SaveBeginEvent` — systems flush pending state.
2. Capture thumbnail screenshot (async, non-blocking).
3. `EntitySnapshotBuilder` iterates all entities with `PersistenceIdComponent`:
   - For each entity, call `takeSnapshot()` on all `Snapshotable` components.
   - Combine local-space position + CoordinateManager offsets → galaxy-space doubles.
   - Group snapshots by sector/system UUID.
4. `WorldStateCollector` gathers non-entity state: economy, factions, discovered names, modification deltas for distant systems.
5. `SaveWriter` serializes via Kryo to a temp folder.
6. Atomic rename temp folder → final save folder.
7. Fire `SaveCompleteEvent` (or `SaveFailedEvent`).

### Loading

1. `SaveReader` reads `manifest.bin` — validate version, run migrations if needed.
2. Set CoordinateManager origin to player's saved galaxy-space position.
3. `EntityFactory` creates entities from `player.bin` and `ships.bin`:
   - Create entity → add components → `restoreFromSnapshot()` on each.
   - Register UUID → Entity in resolution map.
4. `ReferenceResolver` second pass: resolve all UUID references to live Entity pointers.
5. `PhysicsRebuilder` reconstructs Bullet rigid bodies from snapshot data (mass, shape type, velocity).
6. `MeshRebuilder` regenerates ship meshes from blueprint seeds, loads player model.
7. `SystemStateLoader` reads `system_<uuid>.bin` for the current system — spawns NPCs, stations, debris.
8. Regenerate unloaded systems from galaxy seed; apply `modifications.bin` deltas as systems are entered.
9. Fire `LoadCompleteEvent` — systems resume normal tick.

### Auto-Save Triggers

- Every 5 minutes (configurable).
- On system transition (jump to new star system).
- On docking with a station.
- Rotates through `autosave-0` → `autosave-1` → `autosave-2`.

### Thread Model

Snapshot collection (steps 1-4) runs on the game thread in a single frame (fast field copies). Kryo serialization and file I/O (steps 5-6) run on a background thread to avoid hitching.

---

## 5. Multiplayer Readiness

### Shared Snapshot Language

The same snapshot POJOs written to disk are sent over KryoNet to clients. A single `KryoRegistrar` registers all snapshot types with fixed IDs, shared by `SaveWriter`/`SaveReader` and KryoNet's `EndPoint`.

```java
public class KryoRegistrar {
    public static void register(Kryo kryo) {
        kryo.register(ManifestData.class, 20);
        kryo.register(PlayerSnapshot.class, 21);
        kryo.register(ShipDataSnapshot.class, 22);
        // ... all snapshot types with fixed IDs
    }
}
```

### Server-Authoritative Saves

In multiplayer, the server owns `SaveCoordinator`. Clients never write save files — they receive snapshot deltas. The server runs the same save flow but writes to server-side storage.

### Save Backend Abstraction

```java
public interface SaveBackend {
    void writeSave(String saveId, SaveBundle bundle);
    SaveBundle readSave(String saveId);
    List<SaveMetadata> listSaves();
    void deleteSave(String saveId);
}
```

Two implementations:
- `LocalFileSaveBackend` — folder-based, for single-player and local testing.
- `ServerSaveBackend` — wraps server storage (PostgreSQL + Redis), for multiplayer.

`SaveCoordinator` is backend-agnostic.

### Network Delta Optimization (Future)

Initial multiplayer sends full snapshots at a low rate. Later optimization: diff previous vs current snapshot, send only changed fields.

---

## 6. Schema Evolution & Migration

### Version Strategy

`manifest.bin` contains a `saveVersion` integer (starts at 1). Every breaking schema change increments it.

### Migration Chain

```java
public interface SaveMigration {
    int fromVersion();
    int toVersion();
    void migrate(SaveBundle bundle);
}
```

Migrations run sequentially on load to bring old saves to current version.

### Kryo Compatibility

- **Default serializer:** `CompatibleFieldSerializer` for all snapshot classes — tolerates added/removed fields without explicit migration.
- **Explicit migration required for:** field renames, type changes, semantic restructuring.

### Safety Net

If `manifest.saveVersion` exceeds the game's current version (save from a newer build), refuse to load and display an error.

---

## 7. Package Layout

```
core/src/main/java/com/galacticodyssey/
  persistence/
    PersistenceIdComponent.java       — UUID identity component
    Snapshotable.java                 — snapshot interface
    SaveCoordinator.java              — orchestrates save/load flow
    SaveBundle.java                   — in-memory container for all save data
    SaveBackend.java                  — backend interface
    LocalFileSaveBackend.java         — folder-based file backend
    SaveWriter.java                   — Kryo serialization to files
    SaveReader.java                   — Kryo deserialization from files
    EntitySnapshotBuilder.java        — iterates entities, collects snapshots
    ReferenceResolver.java            — UUID → Entity second pass
    PhysicsRebuilder.java             — reconstructs Bullet bodies on load
    MeshRebuilder.java                — regenerates meshes on load
    WorldStateCollector.java          — gathers non-entity state
    SystemStateLoader.java            — loads/regenerates star system state
    KryoRegistrar.java                — shared Kryo type registration
    ManifestData.java                 — save metadata POJO
    snapshots/                        — snapshot POJOs per component
      TransformSnapshot.java
      ShipDataSnapshot.java
      PlayerSnapshot.java
      HealthSnapshot.java
      InventorySnapshot.java
      ... (one per saveable component)
    migration/
      SaveMigration.java              — migration interface
      SaveMigrator.java               — runs migration chain
      Migration_1_to_2.java           — example migration
  core/
    events/
      SaveBeginEvent.java
      SaveCompleteEvent.java
      SaveFailedEvent.java
      LoadCompleteEvent.java
```

---

## 8. Key Invariants

1. **Galaxy-space doubles in save files.** Local-space floats are NEVER written to disk — always convert via CoordinateManager offset first.
2. **No GL/Bullet objects in snapshots.** Snapshots contain only pure data. Physics bodies and meshes are rebuilt on load.
3. **UUID-based identity.** Entity cross-references use UUIDs, never Ashley integer IDs or object pointers.
4. **Atomic writes.** A failed save never corrupts an existing save folder.
5. **Seed reproducibility.** Procgen state is regenerated from the galaxy seed, not serialized. Only player modifications are persisted.
6. **CompatibleFieldSerializer by default.** New snapshot fields silently default; removed fields silently drop. Explicit migrations only for semantic changes.
7. **One KryoRegistrar.** All snapshot type registrations live in a single class shared by save I/O and network transport.
