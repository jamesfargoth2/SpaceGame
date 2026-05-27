---
name: libgdx-save-persistence
description: >
  Enforces correct save/load architecture including Kryo binary serialization
  for single-player, PostgreSQL + Redis for multiplayer persistence, savegame
  versioning, incremental delta saves, and corrupt save recovery for a libGDX
  3D space game. Use this skill whenever writing or modifying: save file
  serialization, load/restore game state, save slot management, database schema
  for multiplayer persistence, Redis caching, savegame migration between versions,
  autosave triggers, or any code that must persist or restore game state. Also
  triggers when adding new serializable components or debugging save corruption.
---

# libGDX Save / Persistence System

## Architecture

| Backend | Mode | Technology | Use Case |
|---|---|---|---|
| Local | Single-player | Kryo binary + SQLite | Player saves, progression |
| Server | Multiplayer | PostgreSQL + Redis | World state, accounts |

## Game State Manifest

```java
public class SaveState {
    public int version;
    public long timestamp;
    public PlayerStatsComponent playerStats;
    public InventoryComponent inventory;
    public Vector3 localPosition;
    public double[] galaxyPosition;
    public ShipData currentShip;
    public Array<CrewSaveData> crew;
    public ObjectMap<String, Float> factionStandings;
    public MissionTracker missions;
    public Array<String> discoveredSectors;
    public Array<String> completedQuests;
    public long galaxySeed;
}
```

## Kryo Serialization

Register custom serializers for libGDX types (Vector3, Quaternion).

## Save/Load

Write to temp file then rename for atomicity. Keep one backup:

```java
public class LocalSaveManager {
    public void save(SaveState state, String slotName) {
        state.version = SaveMigration.CURRENT_VERSION;
        state.timestamp = System.currentTimeMillis();
        FileHandle temp = Gdx.files.local("saves/" + slotName + ".tmp");
        FileHandle target = Gdx.files.local("saves/" + slotName + ".sav");
        try (Output output = new Output(temp.write(false))) {
            serializer.kryo.writeObject(output, state);
        }
        if (target.exists()) target.copyTo(Gdx.files.local("saves/" + slotName + ".bak"));
        temp.moveTo(target);
    }
}
```

## Savegame Versioning

Sequential idempotent migrators per version bump.

## Autosave

Trigger at natural breaks (mission complete, docking, sector change), not fixed timers. Save async on background thread.

## Server-Side Persistence

Redis for fast reads (1hr TTL), PostgreSQL for durability. Write Redis first, then async to PostgreSQL.

## Tuning Parameters

| Parameter | Default | Purpose |
|---|---|---|
| Save format | Kryo binary | Fast, compact |
| Backup count | 1 | Corruption recovery |
| Autosave triggers | Mission, dock, sector | Natural breaks |
| Incremental interval | 30s | Crash recovery |
| Redis TTL | 3600s | Cache expiry |
| Max save slots | 10 | UI slot count |

## Common Mistakes

| Mistake | Fix |
|---|---|
| Writing directly to final file | Write to temp, then rename |
| No save versioning | Always include version + migration |
| Saving on main thread | Use async background thread |
| libGDX types without custom serializers | Register Kryo serializers |
| No backup on overwrite | Copy to .bak first |
| Galaxy coordinates as floats | Save as doubles |
