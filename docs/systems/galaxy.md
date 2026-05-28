# Galaxy & Procedural Generation Systems

The `galaxy` package generates and manages the large-scale structure of the game world: star placement, nebulae, faction territory, space anomalies, and the streaming system that loads/unloads regions around the player.

---

## Galaxy Layout

**`GalaxyManager`**

Top-level manager that owns the observable galaxy state. Delegates chunk loading/unloading to `GalaxyChunkManager`. Maintains a list of active `NebulaRegion` instances and provides neighbour-lookup for star systems.

**`GalaxyChunkManager`**

Manages a grid of `GalaxyChunk` objects arranged in concentric rings around the current view centre (typically the player's galaxy-space position). Loads chunks entering range and unloads chunks moving out of range. Each chunk is generated on demand using the seeded generation pipeline.

**`GalaxyChunk`**

A rectangular region of the galaxy. Stores the list of `StarPosition` objects within that region, computed on first access and then cached. Each chunk also stores any `NebulaRegion` overlaps.

**`GalaxyConfig`**

Loaded from `data/galaxy/config.json`. Defines galaxy-wide parameters: total size, star density, chunk dimensions, nebula frequency, and black hole frequency.

---

## Procedural Star Generation

All generation is deterministic — given the same seed and the same chunk coordinates, the same stars appear.

**`SeedDeriver`**

Derives a unique child seed from a parent seed and a coordinate tuple. Used throughout the generation pipeline so every level (galaxy → chunk → star → planet) has a unique but reproducible seed.

**`GalaxyNoise`**

Provides Perlin and Simplex noise fields used to modulate star density and nebula shape.

**`GalaxyDensityField`**

Samples the noise field to produce a density value at any galaxy coordinate. High-density regions produce more stars; low-density regions produce fewer.

**`GalaxyRegionClassifier`**

Classifies each galaxy coordinate into a `GalaxyRegion` (dense core, spiral arm, interarm void, rim) based on density thresholds. Used by faction territory and encounter table lookups.

**`NebulaPlacer`**

Places `NebulaRegion` objects at low-density transition zones between density bands. Nebulae affect visibility, sensor range, and shield recharge in-game.

---

## Star Data

**`StarPosition`**

Stores a star's full definition:
- Galaxy-space position (double precision)
- `SpectralClass` (O, B, A, F, G, K, M)
- `LuminosityClass` (supergiant, bright giant, giant, subgiant, main sequence, white dwarf)
- Luminosity and radius
- List of child `OrbitalZone` definitions (used to seed planet generation)

**`SpectralClass`** / **`LuminosityClass`** / **`OrbitalZone`**

Enums that encode the physical classification of a star and its orbital regions (scorched, inner habitable, outer habitable, frozen).

---

## Galaxy-Scale Entities

The galaxy procedural system also defines the data types for large-scale content, all generated on demand:

### Derelict Ships (`galaxy/derelict/`)

| Class | Purpose |
|---|---|
| `WreckType` | Classification: civilian freighter, military frigate, alien vessel, etc. |
| `HullClass` | Structural condition tier |
| `SalvageType` | Cargo category available for salvage |
| `SectionState` | Per-section structural state (intact, breached, destroyed) |
| `DerelictCause` | Cause of destruction (combat, drive failure, plague, unknown) |

### Asteroids (`galaxy/asteroid/`)

| Class | Purpose |
|---|---|
| `AsteroidType` | C-type, M-type, S-type, etc. |
| `MineralType` | Resource mineral found in the asteroid |

### Space Stations (`galaxy/station/`)

| Class | Purpose |
|---|---|
| `StationType` | Trading post, military outpost, research lab, shipyard, etc. |
| `StationModuleType` | Module category (docking bay, market, fuel depot, repair bay) |

### Encounters (`galaxy/encounter/`)

`EncounterType` — classification of procedural combat/social encounters generated in a region.

### Factions (`galaxy/faction/`)

| Class | Purpose |
|---|---|
| `FactionEthos` | Moral/political alignment of a faction |
| `PoliticalRelation` | Relation between two factions: allied, neutral, hostile, war |

### Anomalies (`galaxy/anomaly/`)

| Class | Purpose |
|---|---|
| `AnomalyType` | Type of space anomaly (gravitational lens, temporal rift, dark matter node) |
| `NebulaType` | Nebula classification (emission, reflection, dark, planetary) |
| `NebulaHazardType` | In-nebula hazards (radiation storms, EM interference, corrosive particles) |
| `EmbeddedObjectType` | Objects found inside nebulae (proto-stars, rogue planets) |

---

## Reproducibility

All galaxy generation passes through `SeedDeriver`. The galaxy seed is set at new-game time and stored in the save manifest (`ManifestData.galaxySeed`). Reloading a save and visiting the same chunk always yields identical results because the seed derivation is purely deterministic — no runtime randomness is stored per-star.
