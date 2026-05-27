# Remaining Procedural Generation Systems — Design Spec

## Overview

Implement 11 missing procgen generators to complement the existing set (StarSystem, OrbitalLayout, Planet, Atmosphere, Biome, Terrain, ShipHull, ShipInterior, DebrisField, Loot). All new generators follow the same patterns:

- **Seed derivation**: `SeedDeriver.domain(parentSeed, DOMAIN_CONSTANT)` → `new Random(seed)`
- **RNG helpers**: `RngUtil.range(rng, min, max)` for float/int ranges
- **Output model**: Immutable or near-immutable data class holding generated state
- **Placement**: Matching subpackage per CLAUDE.md folder layout
- **Data files**: JSON definitions in `core/src/main/resources/data/<domain>/` where applicable

New `SeedDeriver` domain constants needed: `DERELICT_DOMAIN`, `CAVE_DOMAIN`, `DUNGEON_DOMAIN`, `ASTEROID_SHAPE_DOMAIN`, `ENCOUNTER_DOMAIN`, `ECONOMY_DOMAIN`, `CRATER_DOMAIN`.

(STATION_DOMAIN, FACTION_DOMAIN, NAME_DOMAIN, NEBULA_DOMAIN already exist.)

---

## 1. Name Generation (`core/galaxy/`)

**Purpose**: Produce deterministic names for stars, planets, stations, factions, ships, and NPCs.

**Output model**: `GeneratedName` — `{ prefix: String, root: String, suffix: String, full: String }`

**Generator**: `NameGenerator`
- `generateStarName(long seed)` — Greek letter + constellation syllables
- `generatePlanetName(long seed)` — Root + numeric/Roman suffix
- `generateStationName(long seed, StationType type)` — Type prefix + regional name
- `generateFactionName(long seed)` — Adjective + noun pattern (The Iron Collective, etc.)
- `generateShipName(long seed)` — Military naming conventions (class prefix + word)
- `generatePersonName(long seed, Species species)` — Species-appropriate phoneme chains

**Data files**: `data/names/syllables.json` (phoneme pools per species/category), `data/names/prefixes.json`

**Algorithm**: Markov-chain-lite — weighted syllable sampling from phoneme tables, constrained by min/max length. Seeded so same seed always produces same name.

---

## 2. Space Station Generation (`core/galaxy/`)

**Purpose**: Generate space station layouts — ring/hub/spoke/platform archetypes with module manifests.

**Output model**: `StationLayout`
- `type: StationType` (RING, HUB_SPOKE, ORBITAL_PLATFORM, OUTPOST)
- `modules: List<StationModule>` (DOCK, MARKET, REFINERY, HABITAT, COMMAND, DEFENSE, STORAGE)
- `dockingPorts: int`
- `populationCapacity: int`
- `defenseRating: float`
- `factionId: String`

**Generator**: `SpaceStationGenerator`
- `generate(long seed, StationType type, int tier)` → `StationLayout`
- Tier 1-5 controls module count, population cap, defense rating
- Module placement uses ring-sector or spoke-slot depending on archetype

**Data files**: `data/stations/module_templates.json`, `data/stations/station_archetypes.json`

---

## 3. Derelict/Wreck Generation (`core/galaxy/`)

**Purpose**: Generate abandoned ship wrecks with damage state, remaining loot, hazards, and backstory hooks.

**Output model**: `DerelictWreck`
- `hullClass: ShipSizeClass`
- `damageLevel: float` (0-1, how destroyed)
- `remainingModules: List<String>` (intact compartments)
- `hazards: EnumSet<WreckHazard>` (RADIATION, VACUUM_BREACH, HOSTILE_FAUNA, UNSTABLE_REACTOR, AUTOMATED_DEFENSES)
- `lootTier: int`
- `logEntries: List<String>` (procedural backstory fragments)
- `cause: DerelictCause` (PIRATE_ATTACK, REACTOR_FAILURE, ALIEN_ENCOUNTER, MUTINY, UNKNOWN)

**Generator**: `DerelictGenerator`
- `generate(long seed, ShipSizeClass sizeClass)` → `DerelictWreck`

**Data files**: `data/derelicts/log_fragments.json`, `data/derelicts/hazard_profiles.json`

---

## 4. Cave System Generation (`core/planet/`)

**Purpose**: Generate connected cave networks for planetary exploration — graph-based with room nodes and tunnel edges.

**Output model**: `CaveSystem`
- `rooms: List<CaveRoom>` — position, radius, type (CHAMBER, GALLERY, SINKHOLE, CRYSTAL_CAVE, LAVA_TUBE)
- `tunnels: List<CaveTunnel>` — connects two rooms, width, slope, hazards
- `depth: int` (vertical layers)
- `biomeType: CaveBiome` (ICE, VOLCANIC, FUNGAL, CRYSTAL, BARREN)
- `entrances: List<Vector3>` (surface entry points)

**Generator**: `CaveSystemGenerator`
- `generate(long seed, CaveBiome biome, int complexity)` → `CaveSystem`
- Algorithm: Random walk graph → room placement → Delaunay-like connectivity → prune to spanning tree + a few cycles

---

## 5. Dungeon Interior Generation (`core/planet/`)

**Purpose**: Generate explorable interiors of ruins, bunkers, or alien structures — grid-based rooms with corridors.

**Output model**: `DungeonLayout`
- `rooms: List<DungeonRoom>` — bounds (Rect), type (ENTRY, CORRIDOR, LOOT_ROOM, BOSS_ARENA, PUZZLE, TRAP)
- `connections: List<DungeonConnection>` — door/passage between rooms
- `encounterSlots: List<EncounterSlot>` — positions where enemies/events can spawn
- `totalArea: int` (grid cells)

**Generator**: `DungeonGenerator`
- `generate(long seed, DungeonTheme theme, int roomCount)` → `DungeonLayout`
- Algorithm: BSP tree split → room carving → corridor L-path connection → room type assignment

**Data files**: `data/dungeons/themes.json` (ALIEN_RUIN, MILITARY_BUNKER, PIRATE_HIDEOUT, ANCIENT_TEMPLE)

---

## 6. Asteroid Shape Generation (`core/planet/`)

**Purpose**: Generate 3D asteroid meshes — irregular, seed-deterministic shapes for visual variety.

**Output model**: `AsteroidShape`
- `vertices: float[]`
- `indices: short[]`
- `normals: float[]`
- `boundingRadius: float`
- `compositionType: AsteroidComposition` (CARBONACEOUS, SILICATE, METALLIC, ICE)

**Generator**: `AsteroidShapeGenerator`
- `generate(long seed, float baseRadius, AsteroidComposition composition)` → `AsteroidShape`
- Algorithm: Subdivided icosphere + 3D simplex noise displacement (octave FBM), composition controls roughness/crater density

---

## 7. Encounter Table Generation (`core/combat/`)

**Purpose**: Generate weighted encounter tables for a given region/difficulty — what enemies, events, or discoveries spawn.

**Output model**: `EncounterTable`
- `entries: List<EncounterEntry>` — each has type, weight, minDifficulty, maxDifficulty
- `regionType: RegionType`
- `dangerLevel: int` (1-10)

**EncounterEntry types**: PATROL, PIRATE_AMBUSH, TRADER_CONVOY, ASTEROID_HAZARD, DERELICT_DISCOVERY, ANOMALY, DISTRESS_SIGNAL, NOTHING

**Generator**: `EncounterTableGenerator`
- `generate(long seed, RegionType region, int dangerLevel, FactionPresence presence)` → `EncounterTable`
- Faction presence biases probabilities (pirate territory → more ambushes, etc.)

**Data files**: `data/encounters/base_weights.json`, `data/encounters/faction_modifiers.json`

---

## 8. Trade Economy Generation (`core/economy/`)

**Purpose**: Generate per-location trade parameters — prices, supply/demand curves, specializations — from planet/station properties.

**Output model**: `LocalEconomy`
- `priceModifiers: Map<String, Float>` (commodityId → price multiplier)
- `supplyLevels: Map<String, SupplyLevel>` (SURPLUS, NORMAL, SCARCE, UNAVAILABLE)
- `specializations: List<String>` (commodityIds this location produces cheaply)
- `blackMarketAvailable: boolean`
- `taxRate: float`

**Generator**: `TradeEconomyGenerator`
- `generate(long seed, IndustryType industry, int population, int techLevel, FactionTraits traits)` → `LocalEconomy`
- Industry type drives production specialization, tech level gates exotic goods, population scales volume

---

## 9. Faction Territory Generation (`core/galaxy/`)

**Purpose**: Generate faction boundaries in galaxy space — Voronoi-based territory claims with border tension zones.

**Output model**: `FactionTerritory`
- `factionId: String`
- `capitalLocation: Vector2` (galaxy coords)
- `controlledSystems: List<Long>` (system IDs)
- `borderSystems: List<Long>` (contested/border zone)
- `influence: float` (0-1, strength of control)
- `expansionBias: Vector2` (direction of growth)

**Generator**: `FactionTerritoryGenerator`
- `generate(long galaxySeed, List<FactionSeed> factions, int systemCount)` → `List<FactionTerritory>`
- Algorithm: Place faction capitals → iterative Voronoi expansion weighted by faction strength → border zones where cells overlap

**Data files**: `data/factions/faction_seeds.json` (starting positions, strengths, affinities)

---

## 10. Crater Impact Generation (`core/planet/terrain/`)

**Purpose**: Stamp impact craters onto terrain heightmaps — size, depth, rim height, central peak, ejecta.

**Output model**: `CraterProfile`
- `centerX, centerZ: float` (local terrain coords)
- `radius: float`
- `depth: float`
- `rimHeight: float`
- `centralPeakHeight: float` (only for large craters)
- `ejectaRadius: float`
- `age: float` (0-1, controls erosion/softness)

**Generator**: `CraterGenerator`
- `generate(long seed, float baseRadius, float terrainScale)` → `CraterProfile`
- `stampOnHeightmap(CraterProfile crater, float[] heightmap, int resolution)` — applies the crater radial profile to existing terrain
- Profile function: cosine bowl + raised rim + Gaussian central peak + exponential ejecta falloff, all softened by age factor

---

## 11. Nebula Volumetric Generation (`core/galaxy/`)

**Purpose**: Generate 3D density/color fields for nebula rendering — extends existing 2D NebulaPlacer to volumetric data.

**Output model**: `NebulaVolume`
- `densityField: float[][][]` (3D grid of opacity values)
- `colorField: float[][][]` (RGB per voxel, packed)
- `resolution: int` (grid subdivisions per axis)
- `boundingRadius: float`
- `nebulaType: NebulaType` (EMISSION, REFLECTION, DARK, PLANETARY)
- `dominantColor: Color`

**Generator**: `NebulaVolumetricGenerator`
- `generate(long seed, NebulaType type, float radius, int resolution)` → `NebulaVolume`
- Algorithm: 3D FBM noise for density, type-driven color ramp, emission nebulae get hot-core gradients, dark nebulae invert density for absorption

---

## Shared Infrastructure Changes

### SeedDeriver additions
```java
public static final long DERELICT_DOMAIN       = 0x2E8BA2E8BA2E8BA3L;
public static final long CAVE_DOMAIN           = 0x7A6D76E9E6237015L;
public static final long DUNGEON_DOMAIN        = 0x5D19E57F4F22A935L;
public static final long ASTEROID_SHAPE_DOMAIN = 0x1B4E81B4E81B4E82L;
public static final long ENCOUNTER_DOMAIN      = 0x9C49FBD688E6BF6DL;
public static final long ECONOMY_DOMAIN        = 0x3F56B0C4FCA1AF8BL;
public static final long CRATER_DOMAIN         = 0xAB54A98CEB1C3F47L;
```

### New enums/data classes needed
- `StationType`, `StationModule` (galaxy package)
- `WreckHazard`, `DerelictCause` (galaxy package)
- `CaveBiome`, `CaveRoom`, `CaveTunnel` (planet package)
- `DungeonTheme`, `DungeonRoom`, `DungeonConnection`, `EncounterSlot` (planet package)
- `AsteroidComposition` (planet package)
- `RegionType`, `EncounterEntry` (combat package)
- `SupplyLevel`, `FactionTraits` (economy package)
- `NebulaType` (galaxy package)
- `Species` (npc package, if not already present)

---

## File placement summary

| Generator | Package | Data dir |
|-----------|---------|----------|
| NameGenerator | `galaxy` | `data/names/` |
| SpaceStationGenerator | `galaxy` | `data/stations/` |
| DerelictGenerator | `galaxy` | `data/derelicts/` |
| CaveSystemGenerator | `planet` | — |
| DungeonGenerator | `planet` | `data/dungeons/` |
| AsteroidShapeGenerator | `planet` | — |
| EncounterTableGenerator | `combat` | `data/encounters/` |
| TradeEconomyGenerator | `economy` | — |
| FactionTerritoryGenerator | `galaxy` | `data/factions/` |
| CraterGenerator | `planet/terrain` | — |
| NebulaVolumetricGenerator | `galaxy` | — |

---

## Non-goals

- No ECS system integration in this pass (just generators + data models)
- No rendering/visual output — generators produce data only
- No networking serialization
- No unit tests in this pass (can be added after)
