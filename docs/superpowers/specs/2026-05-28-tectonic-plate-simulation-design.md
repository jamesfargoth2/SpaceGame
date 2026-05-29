# Tectonic Plate Simulation — Design Spec

**Date:** 2026-05-28
**Status:** Approved (pending implementation plan)
**Sub-project of:** Planet Realism Improvements

## Context

This is the **first** of six planned planet-realism sub-projects. The full set,
in dependency-aware build order:

1. **Tectonic plates** (this spec) — the upstream driver
2. **Ocean simulation** — reads continent shapes from tectonics
3. **Volcanic terrain** — reads volcanic-arc / hotspot zones from tectonics
4. **Clouds / weather** — independent
5. **Ice / glacial** — independent
6. **Realistic atmospherics (Rayleigh/Mie)** — independent

Each subsequent sub-project gets its own spec → plan → build cycle.

### Why tectonics first

The current terrain pipeline derives continents and mountains from undirected
noise (`TerrainNoiseStack`). Real planets get those features from plate
boundaries. Building the plate model first lets it become the seed layer the
ocean and volcanic sub-projects read from, instead of each recomputing macro
structure.

### Existing code this plugs into

- `core/.../planet/terrain/TerrainNoiseStack.java` — `sampleAt()` builds
  elevation from a `continent` noise term (line 33) plus `ridge`/`detail` noise.
  The `continent` term is what tectonics replaces.
- `core/.../planet/BiomeMapper.java` — `generate()` sets `seaLevel` from a raw
  random (`rng.nextFloat() * 0.3f`, line 20) and feeds a `HeightSampler` to the
  climate sim.
- `core/.../planet/terrain/PlanetTerrainSystem.java` — `loadPlanet()` builds the
  `TerrainNoiseStack` + `TerrainQuadtree` per planet.
- `core/.../galaxy/SeedDeriver.java` — `TECTONIC_DOMAIN` (line 26) is already
  reserved; no new seed slot needed.
- `HeightSampler` — `@FunctionalInterface float sample(float lonRad, float latRad)`.

## Goals

- Partition a planet sphere into tectonic plates and classify their boundaries.
- Drive **macro terrain shape** (continents, mountain ranges, rift valleys,
  trenches) from those plates.
- Export a **queryable data layer** (plate IDs, boundary type+distance,
  continental fraction, tagged volcanic/hotspot/rift features) for downstream
  sub-projects to consume.
- Stay fully deterministic per `planet.seed` and runnable without a GL context.

## Non-goals (deferred to later sub-projects)

- Lava flows, caldera geometry, basalt columns — **volcanic** sub-project.
- Coastline erosion, tidal flats, coral, sea-level flooding geometry —
  **ocean** sub-project.
- Time-evolving plate motion / mantle convection — explicitly out of scope; this
  is a static snapshot.

## Approach

**Static snapshot.** Built once at planet-gen from the `TECTONIC_DOMAIN` seed.
No time evolution. Partition the sphere into plates, give each a motion vector,
classify boundaries from relative motion, and bake a macro-elevation field plus
a queryable feature map.

New package: `com.galacticodyssey.planet.tectonic`.

### Generation algorithm

1. **Seed plates** — scatter N plate centers on the unit sphere. N ≈ 7–15,
   scaled by planet radius, configured in a small data file (see Data-driven
   config). A couple of Lloyd-relaxation passes even out spacing.
2. **Assign per-plate properties:**
   - **Type** — `OCEANIC` or `CONTINENTAL`, weighted by `PlanetType` (an OCEAN
     world is mostly oceanic; ARID/BARREN skew continental).
   - **Base elevation** — continental plates sit high (above sea level),
     oceanic plates low.
   - **Motion** — an Euler pole (axis on the sphere) + angular speed, yielding a
     velocity vector at any point on the plate.
3. **Partition** — each surface point belongs to its nearest plate center
   (spherical Voronoi). Keep nearest + second-nearest to compute boundary
   distance.
4. **Classify boundaries** — at a boundary between plates A and B, project the
   relative velocity onto the boundary normal:
   - **Convergent** — continent-continent → mountain range; oceanic subduction →
     trench + `VOLCANIC_ARC` tag.
   - **Divergent** — rift valley / mid-ocean ridge (`RIFT` tag).
   - **Transform** — fault line, minor relief.
5. **Hotspots** — a few plate-independent points → `HOTSPOT` tags (volcanic
   island chains / seamounts).
6. **Bake elevation field:**
   `baseElevation = plateBase + boundaryUplift(distance falloff) + hotspotBumps`.

## Public interface

`TectonicModel` — pure data, GL-free, queryable by direction (`Vector3`) or
lat/lon:

- `float baseElevation(Vector3 dir)` — normalized macro base the noise stack
  builds on.
- `int plateAt(Vector3 dir)` / `boolean isOceanic(int plateId)`.
- `BoundaryQuery boundaryAt(Vector3 dir)` → `{ BoundaryType type, float
  distanceNormalized }`.
- `float continentalFraction()` — fraction of surface that is continental;
  drives sea level.
- `List<TectonicFeature> features()` — tagged points
  (`VOLCANIC_ARC` / `HOTSPOT` / `RIFT` / `TRENCH`) for the volcanic & ocean
  sub-projects. Geometry generation is deferred to those projects.

Supporting types:

- `BoundaryType` — `CONVERGENT_CONTINENTAL`, `CONVERGENT_OCEANIC`, `DIVERGENT`,
  `TRANSFORM`, `NONE`.
- `BoundaryQuery` — small value object `{ type, distanceNormalized }`.
- `TectonicFeature` — `{ FeatureType type, Vector3 position }`.
- `Plate` — `{ id, Vector3 center, boolean oceanic, float baseElevation,
  Vector3 eulerPole, float angularSpeed }`.
- `PlateGenerator` — builds a `TectonicModel` from `(Planet, long tectonicSeed)`.

## Integration

- **`TerrainNoiseStack`** gains a `TectonicModel` field. In `sampleAt()`, the
  `continent` noise term (line 33) is replaced by `tectonic.baseElevation(dir)`,
  lightly domain-warped by low-frequency noise for organic coastlines. The
  `ridge` and `detail`/`fine` noise (lines 38, 52, 60) remain as medium/small
  detail. Erosion, drainage, and biome classification downstream are unchanged.
- **`BiomeMapper.generate()`** derives `seaLevel` from
  `tectonic.continentalFraction()` instead of the raw random (line 20), so the
  land/ocean split matches the plate layout. The `HeightSampler` fed to the
  climate sim reflects real mountain ranges → improved orographic precipitation.
- **Wiring** — `TectonicModel` is built **once per planet** from `planet.seed`
  via `SeedDeriver.domain(planet.seed, TECTONIC_DOMAIN)`, and shared by both
  `PlanetTerrainSystem.loadPlanet()` and the biome/climate path so terrain and
  climate see the **same** macro shape. The model is constructed where the
  per-planet generation pipeline assembles atmosphere/biome/terrain, and passed
  into both consumers.

## Data-driven config

A small JSON under `core/src/main/resources/data/planet/` (e.g.
`tectonics.json`) holding tunables, loaded at runtime (per architectural rule 2,
no hardcoded content):

- plate-count range and radius scaling
- continental-fraction targets per `PlanetType`
- boundary uplift magnitudes and falloff distances
- hotspot count range

## Determinism & performance

- Pure function of `planet.seed`. Same seed → identical plate centers,
  boundary classification, and elevation samples. No GL/context dependencies →
  headless-testable (architectural rule 5).
- Built once at planet load. Per-sample query is nearest-of-N over a small N, so
  cost is negligible. A coarse lookup-grid cache is a fallback only if profiling
  shows the per-sample query is hot.

## Testing (TDD)

- **Determinism** — same seed → identical plate centers, classification, and
  elevation samples; different seed → different layout.
- **Continental fraction** — respects planet type (OCEAN < TERRAN).
- **Boundary classification** — synthetic two-plate fixtures with chosen Euler
  poles assert correct `CONVERGENT_*` / `DIVERGENT` / `TRANSFORM` results.
- **Elevation response** — convergent boundaries raise elevation above the plate
  base; divergent boundaries lower it.
- **Feature export** — oceanic-subduction convergent boundaries emit
  `VOLCANIC_ARC` features; hotspots emit `HOTSPOT` features.
- **Integration** — `TerrainNoiseStack` stays deterministic with the tectonic
  field wired in; `BiomeMapper`-derived sea level is consistent with
  `continentalFraction()`.

## Open questions

None blocking. Detailed numeric tuning (exact uplift magnitudes, falloff curves)
is deferred to implementation and the data file.
