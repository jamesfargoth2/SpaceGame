# Unreal Procedural Universe, Home World and Academy Hub — Design Spec
**Date:** 2026-10-25
**Status:** Implemented (`Unreal/`), pending in-editor verification

## Goal

Generate, from one seed, the universe the player starts in: the galaxy, the home star system, the
planet the player starts on, and a **procedurally generated academy hub** in orbit where the
player graduates. Reuse the project's existing 3D assets inside the generated scenes. Target
**Unreal Engine 5** (best UE practice), not libGDX.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Where | New `Unreal/` UE5 project; generation in a runtime plugin `GalacticProcGen` | Keeps the Java code untouched; the plugin can move into any UE project. |
| Algorithms | Port the Java generators (seed domains, distributions, data) and fix their bugs | The design is established and documented; the C++ is now the source of truth. Bit-exact parity with Java was not pursued (it would tie the code to `java.util.Random` and libGDX's `atan2` approximation). |
| Determinism | `GOSeed` (SplitMix64 domains, constants from `SeedDeriver`) + `FGORandom` (64-bit SplitMix64 stream) | `FRandomStream` has a 32-bit seed, which collapses the hierarchical seed tree. |
| Data | JSON in `Plugins/GalacticProcGen/Data` in DataTable import layout; DataTables override per table | Works from a fresh clone (no `.uasset`), stays reviewable as text, and imports as DataTables in the editor. |
| Scale | True-scale planets in one world; chunk components at their own origin with chunk-relative vertices | UE5 Large World Coordinates make a floating origin unnecessary; chunk-relative vertices keep GPU float precision. |
| Assets | Asset kit keyed by the libGDX manifest ids; generators place **slots**, runtime fits meshes by bounds | The models' units, pivots and forward axes are unknown (and the files are not in git); fitting by bounds makes them drop in, and placeholders keep everything playable meanwhile. |
| Hub | Grid-based hub-and-spoke layout from room templates | Guarantees connectivity by construction; walls, doors and windows fall out of cell edges; room content is pure data. |
| Testing | Shared test cases run as UE automation tests and in a headless build against a UE-API stand-in | No engine in CI; the generators are where the logic (and the bugs) live. |

## Seed tree

```
universe seed (text -> FNV-1a -> mix)
├─ StarDomain ─ chunk(cx,cy) ─ star index ─────────── star seed ─ OrbitDomain (slots)
│                                                        └─ PlanetDomain ─ slot ─ planet seed
│                                                              ├─ MoonDomain, AtmosphereDomain, BiomeDomain
│                                                              ├─ TerrainDomain ─ Continent/Ridge/Detail noise, ScatterDomain ─ cell
│                                                              ├─ LandingSiteDomain
│                                                              └─ AcademyDomain ─ hub layout, NpcDomain (names)
├─ GalaxyShapeDomain (density noise)
└─ StartSelectDomain (search viewpoint, candidate order)
```

Every node can be regenerated on its own, in any order, on any thread.

## Generation pipeline (`GOStartScenario::Generate`)

1. **Galaxy** — density field per type (spiral arms with a compact bulge, barred spiral, elliptical,
   irregular). Chunk star counts are normalised by the galaxy's mean density so totals match the
   size setting (500 / 2000 / 10000) for every type; positions are importance-sampled within a
   chunk; a galaxy-wide noise replaces the Java per-chunk noise (which was discontinuous at chunk
   borders).
2. **Start search** — gather ≥256 stars of the requested region (Core / Inner Rim / Frontier)
   around a seeded viewpoint, order them by seed, prefer main-sequence G/K, generate each system
   and score its planets; accept the first home world scoring ≥ 8.6 of 9.5, else the best of 256.
   Score = Terran (2) + breathable (3) + temperature 3·(1−|T−288|/40) + gravity 1.5·(1−|g−1|/0.6)
   − hazards. A star's properties depend only on its record (the Java version rolled them with the
   *requested* region, so a star changed depending on how it was found).
3. **Planet** — mass is Earth-normalised (`ρ/5.51 · r³`; Java gave Earth-like worlds ~5.5 g), so
   volatile inventories were retuned; the atmosphere uses the slot's real orbital distance (Java
   always used 1 AU and never generated the atmosphere before testing "breathable").
4. **Landing site** — best of 256 seeded candidates below 40° latitude: dry, gentle, preferring
   grassland/forest just above the shoreline.
5. **Academy** — hub generator (below), seeded from the home world; the hall faces a seeded
   cardinal direction; the hub orbits 420 km above the landing site.

Measured over 40 seeds (Medium spiral, Inner Rim): 40/40 breathable Terran home worlds, mean
temperatures 271–308 K, gravity 0.8–1.4 g, generation a few ms.

## Terrain

- Height = continent warp + ridges (inland only) + LOD-gated detail octaves (a geometric series:
  frequency ×2, weight ×½ per octave, so slopes stay walkable down to ~20 m vertex spacing).
- Biome amplitude/ridge mix are **blended** bilinearly across the Whittaker grid and the coastline.
  The Java stack switched them per biome, producing kilometre-high steps at biome borders; the
  continuity test now bounds 20 m height changes (max observed ~3 m).
- Climate: the planet's surface temperature is the global **mean**; equator +20 K, poles −40 K,
  −40 K over the full highland range (the Java model treated the mean as the equator temperature,
  freezing every sea past ~17°). Moisture uses 3D noise (no antimeridian seam) and dries inland.
- Seas are flattened to a walkable surface at sea level (ice where below freezing).
- Cube-sphere quadtree (Z up), nearest-corner split metric with split/merge hysteresis (1.5 / 0.75),
  max depth 14, 33×33 grid per chunk + 1-vertex border for seamless normals + skirts.
- Scatter lives on a fixed-depth cell grid; each instance draws a rank, and chunks at depth d keep
  ranks below `0.35^(14−d)` — coarser chunks show a stable subset, nothing moves on split. Over the
  per-chunk cap, the lowest ranks win (even thinning across assets).

## Academy hub

```
                 [front: stage wall, windows]
   room room     ┌──────────────────────┐     room room
 ═══════════════╡   Graduation Hall    ╞══════════════ Terminal (e.g. Observation)
   room room     └──────────╥───────────┘     room room
                    room    ║    room
                    room    ║    room
                        [Hangar] (hangar door to space)
```

- Grid of 4 m cells. The atrium template (graduation hall) is centred at the origin; up to three
  spoke corridors leave the non-front sides.
- Mandatory side rooms (highest priority first), then optional rooms by weight, are packed onto the
  least-filled spoke side; terminal rooms cap spoke ends (hangar, observation deck, engineering).
  Every placement is checked against the occupancy grid; corridors are committed as rooms extend.
- Doors join each room to its corridor; walls/windows/doors are emitted per cell edge (interior
  edges once). Kit pieces per room come from the template (`FloorAsset`, `ExteriorWallAsset`,
  `FrontWallAsset`, `DoorAsset`, ...).
- Prop rules (`Rows`, `AlongWalls`, `Ring`, `Center`, `Corners`, `Scatter`, `Stage`) place kit
  assets with footprint collision checks and door keep-outs; stage props run first so audience
  rows leave room. Rules can emit spawns (seated cadets, instructors behind lecterns, staff at
  consoles, the ship in the hangar); the player stands in the hall's centre aisle facing the stage.

## Runtime (UE)

- `UGOUniverseSettings` (Project Settings → Game → Galactic Universe), `UGOUniverseSubsystem`
  (game instance), `AGOPlanetActor`, `AGOAcademyHubActor`, `FGOKitAssets`, game module
  `AGOGameMode` / `AGOExplorerCharacter` (Enhanced Input built in code) / `AGODebugHUD`.
- Game mode, in `InitGame`: generate scenario → rotate the planet so the landing site is world +Z →
  place the chosen start (academy or landing site) at the origin → build the hub → pre-build the
  terrain in view (ParallelFor) → spawn a PlayerStart. World-bounds/KillZ checks are disabled.

## Asset reuse

Kit rows keep the libGDX ids (`chair_crew`, `console_nav`, `desk_standard`, `human_player`,
`human_npc_generic`) with `LegacySourcePath`. `Tools/SetupGalacticOdyssey.py` imports those `.glb`
files (Interchange), records the mesh path in `AssetKit.json` and rebuilds `DT_AssetKit`.
`FitToSlot` scales by mesh bounds (Uniform for props, Stretch for structure) and applies
`YawOffsetDeg`; `ActorClass` rows spawn actors (skinned characters, ships). Rows without meshes use
tinted `/Engine/BasicShapes` (composite frames for doors/windows with invisible blockers).

## Testing

21 shared cases (`Private/Tests/GOProcGenTestCases.cpp`), run as `GalacticOdyssey.ProcGen` in the
editor and by `Unreal/Tools/HeadlessTests` (CMake, `-Werror`), which also schema-checks the JSON
against UPROPERTY names and renders previews (`--preview`, `--report`).

## Open items

- In-editor compile and play-test of the runtime layer (not possible in the authoring environment).
- Custom gravity for walking around the planet; water rendering/swimming; PCG integration;
  porting the Java ship-hull, flora and creature generators into the kit.
