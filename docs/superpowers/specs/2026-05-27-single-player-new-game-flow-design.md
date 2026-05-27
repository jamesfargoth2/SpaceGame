# Single Player New Game Flow — Design Spec
**Date:** 2026-05-27  
**Status:** Approved

## Overview

Restructures the main menu's "New Game" path into a full single-player entry flow: a sub-menu, a galaxy configuration screen, an async-generation loading screen, and a seeded world start. The player always spawns on a procedurally selected terran planet with their ship landed 50–100m away.

---

## Screen Navigation

```
MainMenuScreen
  "Single Player" ──────────────────> SinglePlayerMenuScreen
                                          "New Game"  ──> GameSetupScreen
                                                            "Create" ──> GalaxyLoadingScreen ──> GameScreen
                                                            "Back"   ──> SinglePlayerMenuScreen
                                          "Load Game" ──> LoadScreen (existing)
                                          "Continue"  ──> GameScreen (most recent save; button disabled if none)
                                          "Back"      ──> MainMenuScreen
```

`MainMenuScreen` renames its "New Game" button to "Single Player" and re-wires it to `SinglePlayerMenuScreen`. All other main-menu buttons are unchanged.

---

## New Screens

### `SinglePlayerMenuScreen`
- Location: `core/src/main/java/com/galacticodyssey/ui/SinglePlayerMenuScreen.java`
- Four centred buttons in the same style as `MainMenuScreen`: **New Game**, **Load Game**, **Continue**, **Back**.
- **Continue** is disabled (greyed) when no save files exist; otherwise loads the most recent save directly with no picker. "Most recent" is determined by the newest file modification timestamp in the saves directory.
- **Load Game** opens the existing `LoadScreen`.

### `GameSetupScreen`
- Location: `core/src/main/java/com/galacticodyssey/ui/GameSetupScreen.java`
- A form panel. Fields:
  - **Galaxy Name** — text field; auto-populated with a procedurally generated name derived from the seed (e.g. "Velarion Expanse"); user-editable.
  - **Seed** — text field; pre-filled with a random `long`; accepts any string (hashed to `long` internally via `String.hashCode()` combined with `System.nanoTime()` salt for random default).
  - **Galaxy Type** — dropdown: `Spiral`, `Barred Spiral`, `Elliptical`, `Irregular` (maps to existing `GalaxyType` enum).
  - **Galaxy Size** — segmented control: `Small` (500 systems), `Medium` (2 000 systems), `Large` (10 000 systems) (new `GalaxySize` enum).
  - **Starting Region** — segmented control: `Core` (dense, dangerous), `Inner Rim` (balanced), `Frontier` (sparse, quiet) (new `StartingRegion` enum).
  - **Create Game** button — validates inputs, builds a `GameSession`, transitions to `GalaxyLoadingScreen`.
  - **Back** button — returns to `SinglePlayerMenuScreen`.

### `GalaxyLoadingScreen`
- Location: `core/src/main/java/com/galacticodyssey/ui/GalaxyLoadingScreen.java`
- Full-screen dark background.
- Layout top-to-bottom: galaxy name (large), progress bar, animated flavour text block, "Please wait" subtitle.
- Receives a `GameSession` with config populated. Spawns a worker thread on `show()` that runs the generation pipeline (see below). The render thread polls `gameSession.isComplete()` and `gameSession.hasFailed()` each frame.
- Flavour text lines fade in one at a time as each generation phase completes.
- On success: transitions to `GameScreen(gameSession)`.
- On failure: shows error message ("Generation failed — please try a different seed") and a **Back** button to `GameSetupScreen`.

---

## `GameSession` Data Carrier

**Location:** `core/src/main/java/com/galacticodyssey/data/GameSession.java`

```java
public class GameSession {
    // --- Input config (set by GameSetupScreen) ---
    public final long seed;
    public final String galaxyName;
    public final GalaxyType galaxyType;
    public final GalaxySize galaxySize;
    public final StartingRegion startingRegion;

    // --- Generated results (populated by worker thread in GalaxyLoadingScreen) ---
    public volatile GalaxyData galaxy;
    public volatile StarSystem startingSystem;
    public volatile Planet startingPlanet;
    public volatile long terrainSeed;
    public volatile Vector3 playerSpawnPos;
    public volatile Vector3 shipSpawnPos;

    // --- Thread coordination ---
    public volatile boolean complete = false;
    public volatile boolean failed   = false;
    public volatile Throwable error  = null;
    public final List<String> log = Collections.synchronizedList(new ArrayList<>());
}
```

`GameScreen` accepts a `GameSession` in its constructor and reads `terrainSeed`, `playerSpawnPos`, and `shipSpawnPos` from it. The existing single-arg constructor `GameScreen(GalacticOdyssey game)` is preserved for the pause-menu load flow; it builds a minimal `GameSession` from save data with `complete = true`.

---

## Galaxy Generation Pipeline

Runs on a worker thread inside `GalaxyLoadingScreen`. Each phase appends a line to `gameSession.log`.

### Phase 1 — Galaxy Layout
- Call `GalaxyManager.generate(seed, galaxyType, galaxySize, startingRegion)`.
- Returns `GalaxyData` with the full star map. Only the starting region is fully resolved immediately (lazy evaluation for the rest).
- Log line: `"Mapping [N] star systems across the [galaxyName]…"`

### Phase 2 — Starting System Selection
- Filter stars in `startingRegion` by spectral class G or K (sun-like, habitable-zone candidates) using `StarSystemGenerator`.
- Select deterministically: sort candidates by `(hash(seed ^ starId)) % candidates.size()` — same seed always yields the same star.
- Log line: `"Habitable zone identified around [Star Name] ([spectral class])…"`

### Phase 3 — Terran Planet Selection
- Run `OrbitalLayoutGenerator` on the chosen star to get orbital slots.
- Run `PlanetGenerator` on each slot; filter for `PlanetType.TERRAN` with liquid water and breathable atmosphere.
- If no planet qualifies: relax the atmosphere constraint (rocky terran without breathable air) — guaranteed to produce a result for any G/K star.
- Log line: `"Terran world [Planet Name] selected as origin point…"`

### Phase 4 — Terrain Seed Derivation
```java
terrainSeed = seed ^ Long.reverse(planet.mass) ^ planet.dayLength;
```
Combines the master seed with planet-unique properties so distinct planets within the same galaxy have distinct terrain.
- Log line: `"Surveying surface of [Planet Name]…"`

### Phase 5 — Spawn Coordinates
- `playerSpawnPos` = terrain centre `(0, terrainHeight + 2, 0)` evaluated with `terrainSeed` (same logic as current `GameScreen`).
- `shipSpawnPos` = `playerSpawnPos + (75, 0, 0)` (75 m east along the X axis, same surface height + 0.5 m).
- Log line: `"Origin point confirmed. Welcome to [Galaxy Name]."`

---

## `GalaxyManager` Changes

Add a new method:
```java
public GalaxyData generate(long seed, GalaxyType type, GalaxySize size, StartingRegion region)
```
This method is the single entry point for the pipeline. Internally it delegates to the existing chunk/region infrastructure, passing the seed through. `GalaxySize` maps to star count: `SMALL=500`, `MEDIUM=2000`, `LARGE=10000`.

---

## `GameScreen` Changes

- Constructor signature becomes `GameScreen(GalacticOdyssey game, GameSession session)`.
- Replace hardcoded `TERRAIN_SEED = 42L` with `session.terrainSeed`.
- Replace hardcoded player spawn `(0, h+2, 0)` with `session.playerSpawnPos`.
- Add ship spawn at `session.shipSpawnPos` using the player's default starting ship (a small scout; data-defined in `data/ships/starter_ship.json` — to be created if not present, containing at minimum: hull type `SMALL`, display name, and default weapon loadout).
- Preserve the existing single-arg constructor `GameScreen(GalacticOdyssey game)` for the load-game flow; it constructs a minimal `GameSession` from save data with `complete = true`.

---

## New Enums

| Enum | Values | Location |
|------|--------|----------|
| `GalaxySize` | `SMALL`, `MEDIUM`, `LARGE` | `core/src/main/java/com/galacticodyssey/galaxy/GalaxySize.java` |
| `StartingRegion` | `CORE`, `INNER_RIM`, `FRONTIER` | `core/src/main/java/com/galacticodyssey/galaxy/StartingRegion.java` |

(`GalaxyType` and `GalaxyRegionClassifier` already exist.)

---

## Error Handling

| Scenario | Behaviour |
|----------|-----------|
| Worker thread throws | `gameSession.failed = true`, `error` captured; loading screen shows error message + Back button |
| No terran planet found | Relax atmosphere constraint; if still no result, use best available rocky planet + log warning |
| Continue with no saves | Button disabled (greyed out); no error state |
| Invalid seed input | Parse failure defaults to `seed = Math.abs(input.hashCode())`; never an error |

---

## Testing

| Test Class | What it verifies |
|------------|-----------------|
| `GalaxyGenerationTest` | Same seed + config always selects the same `StarSystem` and `Planet` (determinism) |
| `GameSessionTest` | Terrain seed formula produces distinct values for planets with distinct `mass`/`dayLength` |
| `PlanetGeneratorTest` | For any G/K star with ≥1 habitable-zone slot, `generate()` returns at least one terran candidate |

All tests are pure logic — no GL context required.

---

## Files Changed / Created

**New:**
- `ui/SinglePlayerMenuScreen.java`
- `ui/GameSetupScreen.java`
- `ui/GalaxyLoadingScreen.java`
- `data/GameSession.java`
- `galaxy/GalaxySize.java`
- `galaxy/StartingRegion.java`

**Modified:**
- `ui/MainMenuScreen.java` — rename button, re-wire click handler
- `ui/GameScreen.java` — accept `GameSession`, remove hardcoded seed/spawn
- `galaxy/GalaxyManager.java` — add `generate(seed, type, size, region)` method

**Data:**
- `core/src/main/resources/data/ships/starter_ship.json` — starting ship definition (if not already present)
