# Galactic Odyssey — Unreal Engine client

A UE5 project that procedurally generates the universe, the planet you start on, and the orbital
academy you graduate from. The generation code ports the libGDX/Java generators in `core/` (same
seed tree, distributions and data), fixes several bugs found while porting, and adds an academy hub
generator and an asset kit that brings the old libGDX models into the generated scenes.

Requires Unreal Engine **5.4 or newer** (written against 5.5 APIs). Works from a fresh clone: no
maps or `.uasset` files are needed to press Play.

## Quick start

1. Right-click `GalacticOdyssey.uproject` → *Generate Visual Studio project files* (or open it
   directly and let the editor compile the `GalacticOdyssey` module and `GalacticProcGen` plugin).
2. Press **Play**. The game mode generates a universe from the seed in
   *Project Settings → Game → Galactic Universe* and starts you on graduation day in the academy,
   in orbit above your home world.
   - `?Seed=anything` on the map URL (or the *Seed Text* setting) picks the universe.
   - `?Start=Surface` (or *Start Location*) starts you at the landing site on the planet instead.
   - Controls: WASD/mouse, Space jump, **F** fly/noclip, **Shift** boost (flying), E/Q up/down.
3. Optional, once: run `Tools/SetupGalacticOdyssey.py` in the editor
   (*Tools → Execute Python Script*). It imports the legacy libGDX models, creates the
   DataTables and a lit terrain material. See [Assets](#reusing-the-existing-3d-assets).

## What gets generated

| Stage | Code | Notes |
|---|---|---|
| Galaxy | `FGOGalaxyGenerator` | Spiral / barred / elliptical / irregular density field, 500–10 000 systems, chunked and lazy (any chunk can be generated alone, on any thread). |
| Star systems | `FGOStarSystemGenerator` | Spectral class by region, stellar evolution, binaries, orbital slots and zones. |
| Planets | `FGOPlanetGenerator` | Type by zone, Earth-normalised mass/gravity, moons, atmosphere (composition, Jeans escape, pressure, greenhouse, breathability, hazards), biome parameters. |
| Start | `FGOUniverseGenerator` | Searches the chosen starting region for the best home world (breathable Terran near 288 K and 1 g). |
| Terrain | `FGOPlanetTerrain`, `GOTerrainMesh` | Cube-sphere quadtree to depth 14 (~20 m vertex spacing on an Earth-size world), biome-blended height, flat walkable seas, skirts, seamless normals. |
| Scatter | `GOTerrainMesh::BuildScatter` | Rocks and flora from the asset kit per biome, slope-filtered, stable across LOD (an instance never moves when a chunk splits). |
| Academy hub | `FGOHubGenerator` | Graduation hall + spoke corridors + rooms from data (dormitory, classroom, simulator bay, mess hall, medbay, hangar, observation deck, ...), walls/doors/windows from the grid, props from room rules, NPC/player/ship spawns. |
| Landing site | `GOLandingSite::Find` | Dry, gentle, temperate spot; the academy orbits directly above it. |

Everything above is engine-independent data generation and runs in a few milliseconds
(`GOStartScenario::Generate`). The runtime layer turns it into a world:

- `UGOUniverseSubsystem` (game instance subsystem): loads content, owns the seed and scenario
  (sync or async generation), answers galaxy queries.
- `AGOPlanetActor`: true-scale planet. Chunks are built on worker threads and shown as
  `UProceduralMeshComponent` sections, each placed at its own origin with chunk-relative vertices,
  so Large World Coordinates keep it precise without a floating origin. HISM scatter, a
  `USkyAtmosphereComponent` tuned from the generated atmosphere, and a sun coloured by the star's
  temperature with a day/night cycle.
- `AGOAcademyHubActor`: assembles the academy from instanced kit meshes (one ISM per mesh/material),
  composite placeholders for door and window frames, point lights, NPC stand-ins and an academy sign.
- `AGOGameMode` (game module): spawns both in one world, with the start point at the origin and
  the planet centre thousands of km below. It pre-builds the visible terrain and places the player.

## Reusing the existing 3D assets

The libGDX project's content manifests (`core/src/main/resources/data/assets/*.json`) list these
models: `chair_crew`, `console_nav`, `desk_standard`, `human_player`, `human_npc_generic`. They are
referenced by id but the `.glb` files are not in git. The Unreal side keeps those **ids** as rows
of the asset kit (`Plugins/GalacticProcGen/Data/AssetKit.json`), each with its `LegacySourcePath`.
The generators place assets by id: the graduation hall seats cadets in rows of `chair_crew` behind
a `desk_standard` lectern, and classrooms and consoles use `console_nav` and `desk_standard`. NPC
spawns use `human_npc_generic` and the player is `human_player`.

How a model gets into the procedural scenes:

1. Put the `.glb` files where the libGDX manifests expect them
   (`core/src/main/resources/models/...`), or pass `--legacy-root <dir>` to the script.
2. Run `Tools/SetupGalacticOdyssey.py` in the editor. It imports each file via Interchange into
   `/Game/GalacticOdyssey/Kit/<Category>/<id>`, writes the mesh path into `AssetKit.json`
   (a one-line diff per asset), and rebuilds `DT_AssetKit`.
3. Press Play. Every generated slot that uses that id now shows the real mesh.

Why this works for assets of unknown origin: the generators only reserve a **slot** (a box with a
facing) for each asset. At runtime `FGOKitAssets::FitToSlot` fits the mesh into that slot using its
bounds: uniformly for props, stretched for modular structure. This makes libGDX-era models with
arbitrary units or pivots drop in at the right size. `YawOffsetDeg` corrects models whose front
is not +X. A kit row can name an `ActorClass` instead of a mesh, which is the route for skinned
characters, interactive consoles and the starter ship; the row's id stays the same.

Rows without a mesh use `/Engine/BasicShapes` placeholders tinted by `FallbackColor`, so every
layout is playable before any art exists. New assets are just new rows. For example, an artist
modular kit for the hub replaces `hub_floor` / `hub_wall` / `hub_wall_window` / `hub_doorway` /
`hub_hangar_door`, and room templates choose their pieces per style.

## Content (data-driven)

All tunable content lives in `Plugins/GalacticProcGen/Data/*.json`, in DataTable import layout
(`"Name"` = row name, other keys = UPROPERTY names):

| File | Row struct | What |
|---|---|---|
| `SpectralClasses.json` | `FGOSpectralClassDef` | Star class frequencies, temperatures, masses, planet counts |
| `PlanetTypes.json` | `FGOPlanetTypeDef` | Radius/density ranges, moons, allowed zones |
| `Biomes.json` | `FGOBiomeDef` | Terrain amplitude, ridge mix, ground colour |
| `NameLists.json` | `FGONameListDef` | Name syllables (ported from `data/names`) |
| `HubRooms.json` | `FGOHubRoomTemplate` | Academy rooms: placement, counts, sizes, kit pieces, prop rules, spawns |
| `AssetKit.json` | `FGOAssetKitRow` | Every 3D asset the generators may place |

If the DataTables named in *Project Settings → Galactic Universe* exist, they win. Otherwise the
JSON is read directly, which is what happens on a fresh clone and in packaged builds without
tables. Treat the JSON as the source of truth: `SetupGalacticOdyssey.py --refresh-tables`
rebuilds the tables from it.

## Tests and previews

- **In editor:** *Session Frontend → Automation →* `GalacticOdyssey.ProcGen` (21 cases:
  determinism, physics sanity, home-world quality, terrain continuity, quadtree coverage, mesh
  winding/normals, scatter stability, hub connectivity/overlaps/watertightness, spawns).
- **Without Unreal:** `Tools/HeadlessTests/run_tests.sh` compiles the same generators and test
  cases against a small stand-in for the UE core API and runs them in seconds. It also loads the
  shipped JSON by UPROPERTY name and fails on unknown keys, which the editor would silently ignore.
- **Previews:** `Tools/HeadlessTests/build/GOHeadlessTests --preview <seed> <dir>` writes the
  academy floor plan (SVG), the home world's biome map (PNG) and the galaxy map (SVG).
  `--report 40` prints a one-line summary per seed.

## Layout

```
GalacticOdyssey.uproject
Config/                      Engine/game/input defaults (Entry map, game mode, settings, cooking)
Source/GalacticOdyssey/      Game module: AGOGameMode, AGOExplorerCharacter, AGODebugHUD
Plugins/GalacticProcGen/
  Data/                      JSON content (see above)
  Source/GalacticProcGen/
    Public|Private/Core      Seeds (GOSeed), RNG (FGORandom), noise, content bundle
    Public|Private/Universe  Galaxy, stars, planets, names, start selection, start scenario
    Public|Private/Planet    Terrain sampler, cube-sphere quadtree, chunk meshes, scatter, landing site
    Public|Private/Hub       Academy hub types and generator
    Public|Private/Assets    Asset kit rows, mesh resolution and slot fitting
    Public|Private/Runtime   Settings, subsystem, content loader, planet and academy actors
    Private/Tests            Shared test cases + UE automation wrapper
Tools/
  SetupGalacticOdyssey.py    Editor setup: import legacy models, materials, DataTables
  HeadlessTests/             Engine-free build of the generators + tests + previews
```

## Known limitations / next steps

- Gravity is world −Z: correct inside the academy and around the landing site (the planet is
  rotated so the site is "up"), but walking far around the planet needs custom gravity
  (`UCharacterMovementComponent::SetGravityDirection`, UE 5.4+).
- Oceans are a flat, walkable surface at sea level (no water rendering or swimming yet).
- Without the setup script the terrain uses the engine's emissive vertex-colour material, which
  shows the biomes but no lighting. The script creates a lit `M_PlanetSurface`.
- Skinned character `.glb` files import as skeletal meshes. Wrap them in a Blueprint actor and
  set the kit row's `ActorClass`.
- Only the home system is realised in the world; other planets, moons and stars exist as data
  (`UGOUniverseSubsystem::GetStarsNear/GetStarSystem/GetPlanets`) for maps and travel.
- Natural extensions: a PCG node wrapping `BuildScatter`/hub props; porting the Java ship-hull,
  flora and creature generators into the kit; a graduation ceremony sequence on top of the
  spawn points.
