# Persistence & Save System

The `persistence` package implements a full save/load pipeline: entity snapshotting, serialised bundle storage, save slot management, version migration, and rotating auto-saves.

---

## Save Pipeline

**`SaveCoordinator`**

Orchestrates the entire save and load cycle.

### Save flow

1. Publishes `SaveBeginEvent` so systems can flush any pending state.
2. Calls `EntitySnapshotBuilder` to snapshot every entity that has `PersistenceIdComponent`.
3. Bundles snapshots, world modifications, discovered IDs, and save metadata into a `SaveBundle`.
4. Writes the bundle to the `SaveBackend`.
5. Optionally captures a thumbnail via `ThumbnailCapture`.
6. Publishes `SaveCompleteEvent` on success or `SaveFailedEvent` on error.

**Auto-save:** runs every 300 seconds (configurable) to a rotating set of auto-save slots so manual saves are never overwritten.

### Load flow

1. Reads `SaveBundle` from the `SaveBackend`.
2. Passes snapshots through `SaveMigrator` if the bundle's save-format version differs from the current version.
3. Destroys all current entities.
4. Recreates entities from snapshots via component restoration.
5. Calls `ReferenceResolver` to re-link cross-entity references (e.g. player's current ship entity).
6. Publishes `LoadCompleteEvent`.

---

## Storage Backend

**`SaveBackend`** (interface)

Abstract storage layer. Implementations:

| Class | What it does |
|---|---|
| `LocalFileSaveBackend` | Reads/writes `SaveBundle` as JSON to the platform-specific save directory |

`SaveBundle` is the top-level serialisation container:
- `ManifestData` — metadata header (timestamp, galaxy seed, character name, playtime, format version)
- List of `EntitySnapshot`
- List of `WorldModification` (persistent world state changes: destroyed objects, moved entities)
- `DiscoveredIds` (visited star systems, known NPCs, scanned anomalies)

---

## Entity Snapshotting

**`EntitySnapshotBuilder`**

Iterates over all Ashley entities. For each entity that has `PersistenceIdComponent`, reads every `Snapshotable<T>` component and builds an `EntitySnapshot`.

**`PersistenceIdComponent`**

Every save-relevant entity has a stable UUID assigned at creation time. This ID is used to match snapshot records to entities on load, ensuring reference resolution works even if the entity list order changes.

**`Snapshotable<T>`**

Interface implemented by each persistable component. Requires `toSnapshot()` and `fromSnapshot(T)` methods. Keeps serialisation logic inside the component rather than in a central mapper.

**`EntitySnapshot`**

A map from component type name to snapshot data for one entity. Stored as a JSON object per entity.

**`ReferenceResolver`**

After all entities are recreated from snapshots, resolves any inter-entity references (e.g. `PlayerStateComponent.currentShip` stores the ship's UUID; the resolver replaces it with the actual Ashley `Entity` object).

---

## Snapshot Classes

One snapshot class exists per persistable component. Each is a simple POJO with only the fields needed to reconstruct the component's runtime state:

| Snapshot | Component it covers |
|---|---|
| `TransformSnapshot` | Position, rotation, scale |
| `HealthSnapshot` | currentHp, maxHp, alive |
| `ShieldSnapshot` | currentShield, maxShield, rechargeDelay |
| `ArmorSnapshot` | Per-region armor values, durability |
| `RangedWeaponSnapshot` | Ammo count, reload progress |
| `ShipFlightSnapshot` | Throttle, velocity, time-dilation factor |
| `ShipDataSnapshot` | Blueprint seed, size class |
| `PlayerStateSnapshot` | Player mode, current ship UUID |
| `FuelTankSnapshot` | Current fuel quantity |
| `CargoBaySnapshot` | Commodity inventory list |
| `InventorySnapshot` | Item list with quantities |
| (20+ more) | Every other persistable component |

---

## Save Migration

**`SaveMigrator`**

Checks `ManifestData.formatVersion` against the current version. If they differ, runs the applicable chain of `SaveMigration` steps in order. Each `SaveMigration` handles the delta between two consecutive format versions (e.g. renaming a field, adding a new required field with a default).

This ensures saves from older game versions remain loadable after updates.

---

## Save UI

The UI layer in `ui/` provides:
- `SaveScreen` — lists save slots with name, timestamp, and thumbnail; allows the player to write to a slot.
- `LoadScreen` — lists slots and initiates the load flow.
- `SaveSlotPanel` / `SaveSlotListener` — reusable Scene2D panel for a single slot.
- `SaveToast` — brief on-screen notification after an auto-save completes.

---

## Events Reference

| Event | When published |
|---|---|
| `SaveBeginEvent` | Immediately before snapshotting begins |
| `SaveCompleteEvent` | Save written successfully |
| `SaveFailedEvent` | Save failed (includes error reason) |
| `LoadCompleteEvent` | Entity reconstruction complete; game ready to play |
