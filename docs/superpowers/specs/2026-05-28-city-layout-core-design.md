# City Layout Core — Design Spec

**Date:** 2026-05-28
**Status:** Approved (design); pending implementation plan
**Sub-project:** A of 5 (Cities & Settlements)

---

## 0. Context: the larger "Cities & Settlements" effort

Galactic Odyssey needs fully walkable procedural cities on planet surfaces: every
building generates a type-appropriate interior with procedural furniture, NPC
populations with roles and schedules, and a per-city market. City size varies with
population. This is a system-of-systems, decomposed into five independently
spec'd/planned/implemented sub-projects:

| # | Sub-project | Owns | Depends on |
|---|---|---|---|
| **A** | **City Layout Core** *(this spec)* | `CityConfig`/`CityRequest`, population→size scaling, landmarks, streets, blocks, lots, district zoning, walls/gates, terrain conform. **Pure data model.** | SeedDeriver, faction/terrain data |
| B | Building Generation | Per-lot exterior shells + per-building interiors + procedural furniture, keyed by building function | A |
| C | NPC Population & Schedules | Citizen seeding by role, home/workplace assignment, daily schedules | A, B |
| D | City Market | Per-city `MarketComponent`, `CityEconomyGenerator` | A, existing economy |
| E | Runtime & Streaming | Realize data model as geometry/collision/physics; district streaming; interior load-on-enter; floating-origin + sphere projection | A–D |

This spec covers **only sub-project A**. B–E get their own spec → plan → implementation
cycles later.

Design-doc grounding: `docs/DESIGN.md` §4.12 (Planetary Exploration) calls for
"Handcrafted districts with procedural fill (landmark buildings hand-placed, side
streets generated)", the district set **Spaceport, Commercial, Residential,
Industrial, Government, Slums/Underground**, and a "Hybrid Procedural + Handcrafted"
philosophy (§ line 143). Both the canonical district set and authored-landmark
overrides are baked into this design.

---

## 1. Goal & scope

Produce a **deterministic, pure-data city layout** from a seed + population + faction,
with no rendering, ECS, or planet/GL coupling. The output (`CityLayout`) is the data
model that sub-projects B, C, D, and E all consume.

**In scope (A):** size scaling, form selection, landmark anchoring, street network,
block extraction, lot subdivision, district zoning, lot function tagging, walls/gates,
terrain conformance, determinism, JUnit tests, and a standalone top-down debug renderer.

**Out of scope (A):** building geometry/interiors/furniture (B); NPCs/schedules (C);
markets (D); 3D realization, collision, streaming, and sphere/galaxy projection (E).

---

## 2. Architecture: staged pipeline of independently-testable components

`CityLayoutGenerator` orchestrates discrete, pure components. Each takes data in and
returns data out — no GL, no ECS, no global mutable state, no wall-clock, no
`Math.random`. Each stage is unit-testable in isolation (architectural rule #5).

```
CityLayoutGenerator.generate(CityRequest request) -> CityLayout
```

### Pipeline stages (each its own class)

1. **CityForm selection** — faction ethos + seed pick `GRID / ORGANIC / RADIAL / LINEAR / SPRAWL` (data-driven faction bias, modulated by the size tier's form bias).

> **v1 geometry simplification:** Blocks and lots are **axis-aligned rectangles**
> throughout (the skill's `LotSubdivider` assumes rectangular cells). `CityForm` is
> realized as *modulations of an axis-aligned cell grid* rather than literal curved
> geometry: GRID = uniform grid; RADIAL = aggressive centre-ward block-size gradient +
> central plaza void; ORGANIC = per-cell boundary jitter + occasional cell merge/drop;
> LINEAR = grid clipped to an elongated strip; SPRAWL = grid with a fraction of
> peripheral cells omitted and enlarged. This yields visibly distinct city shapes while
> keeping lots rectangular, deterministic, and unit-testable. Curved radial avenues /
> winding organic streets are a deferred enhancement (later A refinement or E).
2. **LandmarkPlacer** — places civic/government centre, spaceport (65–85% radius), market plaza, faction landmark. **Merges any `AuthoredLandmark`s from the request first**, then fills remaining required landmarks procedurally (hybrid procedural + handcrafted).
3. **StreetNetworkBuilder** — strategy per form (`GridStreetBuilder`, `OrganicStreetBuilder` L-system, `RadialStreetBuilder`, `LinearStreetBuilder`, `SprawlStreetBuilder`). Produces tiered streets (`AVENUE / STREET / ALLEY`) with loops (extra cross-streets), never a bare spanning tree.
4. **Block derivation** — each street builder emits its `CityBlock`s **directly as the cells it creates** (grid cells between avenues; radial ring-sectors; perturbed cells for organic/sprawl/linear). This avoids fragile general planar-graph face extraction and keeps block derivation deterministic and per-form. (`StreetNetworkBuilder.build(...)` returns a `StreetNetwork{ streets, blocks }`.)
5. **DistrictZoner** — assigns `DistrictType` per block via centre-out gradient + landmark adjacency override + faction rules.
6. **LotSubdivider** — blocks → `BuildingLot`s using zone-specific min/max lot sizes (iterative binary subdivision along longest axis).
7. **LotFunctionAssigner** — tags each lot with a `BuildingFunction` drawn from its district's allowed-mix table. **This is the A/B handoff contract.**
8. **WallBuilder** — if the size tier is fortified: convex-hull wall around periphery lots + gates cut where avenues pierce the perimeter.
9. **TerrainConformer** — using the injected `TerrainSampler`: purge/reroute streets crossing water or slope >30° (tan > 0.577); drop lots with >40% footprint on inaccessible ground.

Stage order is fixed; later stages consume earlier outputs. A failed terrain conform
that would orphan the city (e.g. all lots removed) returns a valid empty-ish layout
rather than throwing — caller decides whether the site is viable (relevant to E).

---

## 3. Inputs

### `CityRequest`
- `long seed` — derived via a new `SeedDeriver.CITY_DOMAIN` constant.
- `int population` — **the single size driver.**
- `FactionEthos rulingEthos` — drives form bias and district mix (the codebase has **no `FactionId` enum**; factions are `String id` + `FactionEthos`, cf. `SystemEconomyGenerator.generate(seed, "CORPORATE", dist)`).
- `String factionId` — opaque faction identifier carried into the layout for downstream sub-projects.
- `TerrainSampler terrain` — injected interface (see below).
- `List<AuthoredLandmark> authoredLandmarks` — optional; hand-placed landmarks merged before procedural fill. Empty list = fully procedural.

### `TerrainSampler` (interface, defined in A)
```java
public interface TerrainSampler {
    float   heightAt(float localX, float localZ);   // metres
    boolean isWater(float localX, float localZ);
    float   slopeAt(float localX, float localZ);     // 0..1 (tan of angle)
}
```
A ships `FlatTerrainSampler` (height 0, no water, slope 0) as the default for tests and
standalone use. Real planet-surface hookup is sub-project E's responsibility.

---

## 4. Population → size mapping (headline requirement)

A `CitySizeProfile` derives all spatial parameters from `population` via data-driven
thresholds in `data/cities/size_tiers.json`. Seven tiers:

| population | CityType | radius (m) | form bias | wall? |
|---|---|---|---|---|
| < 50 | `OUTPOST` | ~40 | LINEAR/SPRAWL | no |
| 50–800 | `FRONTIER_TOWN` | ~120 | GRID | no |
| 800–8k | `COLONY` | ~300 | GRID/ORGANIC | maybe |
| 8k–60k | `CITY` | ~700 | RADIAL | yes |
| 60k–100k | `LARGE_CITY` | ~1100 | RADIAL | yes |
| 100k–500k | `METROPOLIS` | ~1800 | RADIAL/SPRAWL | yes |
| 500k+ | `LARGE_METROPOLIS` | ~3000+ | SPRAWL/RADIAL (multi-layered) | yes |

Derived per tier (all in JSON, tunable without recompiling):
- `radiusMetres` (with seed jitter within a tier band)
- `formBias` (weighted list of `CityForm`)
- `hasDefensiveWall` (`yes` / `no` / `maybe` → seed roll)
- `targetBuildingCount` ≈ `f(population, density)`
- `populationDensity` (0–1; tightens block/lot size near centre)

Radii for the new/renamed tiers are starting proposals; final values are tuned in JSON.

---

## 5. Output data model (`CityLayout`)

Pure POJOs. Uses libGDX **math** types (`Vector2`, `Rectangle`/`Rect`, `Polygon`) but
**no rendering types** (no `Model`, `Texture`, `ModelInstance`). Fully serializable.

```
CityLayout
  long        cityId
  String      name                  // via SpaceNameGenerator (faction naming style)
  long        seed
  int         population
  CityType    type
  CityForm    form
  FactionEthos rulingEthos
  String      factionId
  GalaxyAnchor localToGalaxyAnchor   // double-precision placeholder; A leaves unset/identity, E fills
  List<Landmark>     landmarks       // type + local position (+ authored flag)
  List<Street>       streets         // start, end, tier
  List<CityBlock>    blocks          // polygon + districtType
  List<BuildingLot>  lots            // footprint Rect + districtType + buildingFunction
  CityWall           wall            // nullable; hull polygon + gates
```

### Enums

`CityType` — the 7 tiers above.

`CityForm` — `GRID, ORGANIC, RADIAL, LINEAR, SPRAWL`.

`StreetTier` — `AVENUE, STREET, ALLEY`.

`DistrictType` — design-doc set + superset:
`SPACEPORT, COMMERCIAL, RESIDENTIAL, INDUSTRIAL, GOVERNMENT, SLUMS,
RELIGIOUS, GARDEN, MILITARY, UNKNOWN`.
(`GOVERNMENT` is the civic/administration district from the design doc.)

`BuildingFunction` — the A/B contract. Coarse function per lot; B expands each into a
specific archetype + interior + furniture. Initial set (extensible via JSON):
`HOUSE, APARTMENT, TENEMENT, SHOP, MARKET_STALL, CANTINA, WAREHOUSE, WORKSHOP,
FACTORY, FACTION_HQ, COURTHOUSE, TOWN_HALL, BARRACKS, TEMPLE, SHRINE, CLINIC,
HANGAR, TERMINAL, PARK, EMPTY_LOT`.

`LandmarkType` — `CIVIC_CENTRE, SPACEPORT, MARKET_PLAZA, FACTION_LANDMARK, AUTHORED`.

`GalaxyAnchor` — holds double-precision galaxy/planet placement metadata (position +
lat/lon + local-frame orientation). A constructs it empty/identity; documented as
"filled by E." Included now so the contract is stable.

---

## 6. Data files (`core/src/main/resources/data/cities/`)

- `size_tiers.json` — the 7-tier table (thresholds, radius bands, form bias, wall flag, density, target building count).
- `district_mix.json` — per `DistrictType`, the weighted allowed `BuildingFunction` mix and zone-specific min/max lot sizes.
- `faction_form_bias.json` — per `FactionEthos`, weighting over `CityForm` (combined with the tier's form bias).

Loaded via the established registry/loader pattern (cf. `CommodityRegistry`,
`FaunaDataRegistry`): a `CityDataRegistry` parses these on init and exposes typed lookups.
The registry exposes a `loadFromReader(Reader)` form so unit tests can load the same
JSON off the classpath with no `Gdx` files backend, while runtime uses
`Gdx.files.internal(...)`.

---

## 7. Determinism (per `procgen-seed-reproducibility`)

- A new `SeedDeriver.CITY_DOMAIN` constant; the city seed derives from it.
- Each pipeline stage gets its own sub-seed via `SeedDeriver.domain(citySeed, STAGE_CONSTANT)`.
- No `Math.random`, no `System.currentTimeMillis`, no `new Date()`, no iteration over
  unordered collections in a way that affects output.
- **Invariant:** identical `CityRequest` (same seed, population, faction, terrain,
  authored landmarks) → byte-identical `CityLayout`, every run, any platform.
- Regeneration from seed is the default; the model is also serializable for persistence
  if a caller chooses (save/load fidelity is E's concern, but the model supports it).

---

## 8. Deliverable & verification

### JUnit tests (no GL context required)
- **Determinism:** same request twice → equal `CityLayout` (deep equality).
- **Population scaling:** monotonic — higher population ⇒ `CityType` tier ≥ previous,
  radius ≥ previous, lot count ≥ previous (across representative samples).
- **Tier boundaries:** populations at each threshold map to the correct `CityType`.
- **No overlaps:** lots do not overlap each other; streets do not overlap lots.
- **Connectivity:** the street network is connected (every block reachable); loops exist
  (more edges than a spanning tree).
- **Completeness:** every lot has a `DistrictType` and a `BuildingFunction`; every
  required landmark present; spaceport (if any) at 65–85% radius; market not at centre.
- **Walls:** fortified tiers produce a wall whose gates align with avenues; unfortified
  tiers produce none.
- **Terrain conformance:** with a sampler that marks a region water/steep, no surviving
  street crosses it and no surviving lot is >40% on bad ground.
- **Authored landmarks:** supplied authored landmarks appear in output at their given
  positions and are flagged authored.

### Standalone top-down debug renderer
A launchable desktop tool (under `desktop/` or a debug launcher) that draws, for a given
seed + population + faction:
- streets (tier = line weight), color-coded districts, landmark icons, lot footprints
  with `BuildingFunction` labels, wall + gates.
- Keys: reroll seed, increase/decrease population (watch the city grow across tiers),
  cycle faction, toggle a sample water/slope region.

This is a tuning/eyeball surface only; it reads the pure `CityLayout` and does no
generation logic of its own.

---

## 9. Package & placement

- New subpackage `com.galacticodyssey.city.layout` (sanctioned by DESIGN.md §4.12).
  - Generator + stages: `com.galacticodyssey.city.layout`
  - Data model POJOs/enums: `com.galacticodyssey.city.layout.model`
  - Data registry + definitions: `com.galacticodyssey.city.data`
- Resources: `core/src/main/resources/data/cities/`
- Tests: `core/src/test/java/com/galacticodyssey/city/layout/`
- Debug renderer: `desktop/` (or existing debug-launcher location).

Reuses existing infrastructure: `SeedDeriver`, `RngUtil`, `SpaceNameGenerator`,
`FactionId`, and the JSON registry/loader pattern.

---

## 10. Open items deferred to later sub-projects (not A)

- Mapping `BuildingFunction` → concrete building archetype, interior, furniture (B).
- Citizen seeding / home & workplace assignment / schedules from lot functions (C).
- Per-city `MarketComponent` and city economy (D).
- 3D mesh/collision realization, district streaming, interior load-on-enter, and
  projecting the local-planar layout onto the planet sphere via `GalaxyAnchor` (E).
