# Flora Generation Core (Cycle A) — Design

**Date:** 2026-05-28
**Status:** Approved (design); pending implementation plan

## 1. Goal & Scope

Replace the current "box trees" (a cylinder trunk + cone/sphere canopy, generated in
`data/WorldPopulator.placeTrees`) with deterministic, biome-driven, **data-defined
branching plants** generated via a **space-colonization** algorithm. Foliage is rendered
as **low-poly mesh clumps**. Generated meshes are reused from a **per-species prototype
pool** so hundreds of plants cost only a handful of unique meshes.

The space-colonization generator is general enough that "broadleaf tree," "conifer,"
"desert cactus," and "tundra shrub / lichen mound" are all just different **envelope
shapes + growth parameters** defined in JSON. So biome palettes get biome-appropriate
large flora (desert → cacti/succulents, jungle → tall canopy, tundra → lichen/dwarf
shrub) from a single generator, without per-form bespoke code.

This is **Cycle A** of a phased flora effort (mirrors the creature-generation
decomposition):

- **A — Generation Core (this spec):** framework + space-colonization branching trees +
  biome palettes; replaces box trees.
- **B — Grass / Ground Cover (later):** GPU-instanced billboards, biome density/color.
- **C — Modular Alien Plants (later):** bioluminescent / carnivorous / crystal growths,
  socket-style modular like creatures (stalk + canopy + detail).

### Out of scope / explicitly deferred to later cycles
- **GPU instancing** — no instancing infrastructure exists yet; Cycle A uses shared
  prototype meshes referenced by lightweight `ModelInstance`s. Instancing is Cycle B.
- **Grass / ground cover**, billboards, and impostors — Cycle B.
- **Modular socket-based alien plants** (bioluminescent, carnivorous, crystal) — Cycle C.
- **Wind animation**, **LOD**, and **plant collision meshes** — future work. Flora stays
  **non-collidable** in Cycle A (matches current box trees).
- **Grass/rock/animal placement** in `WorldPopulator` — untouched this cycle; only the
  tree path is replaced.

## 2. Placement (package layout)

New top-level package `com.galacticodyssey.flora`, parallel to the planned `fauna`
package — flora is a distinct content domain. (Cross-checked against CLAUDE.md folder
rules; a new top-level package is justified the same way `fauna` is for creatures.)

```
core/src/main/java/com/galacticodyssey/flora/
  data/
    FloraSpecies.java       immutable species definition (POJO loaded from JSON)
    BiomePalette.java        biome → weighted species list + density + tint jitter
    FloraRegistry.java       loads resources/data/flora/**.json; holds species + palettes
  gen/
    AttractionEnvelope.java  envelope shape enum + seeded attraction-point cloud
    SpaceColonization.java   the growth algorithm → BranchSkeleton
    BranchSkeleton.java      nodes (pos, parentIdx, radius, isTip) — pure data
    FloraMeshData.java       float[] vertices + short[] indices + bounds — pure data
    FloraMeshBuilder.java    BranchSkeleton → FloraMeshData (tapered tubes + clumps)  [GL-free]
    FloraModelFactory.java   FloraMeshData → libGDX Model  [thin GL layer]
  FloraSpec.java            resolved per-instance recipe (species + variation seed)
  FloraPrototype.java       a generated Model variant + metadata for reuse
  FloraGenerator.java       orchestrator: builds prototype pool + produces placements

core/src/main/resources/data/flora/
  species/     *.json   (one file per species, or a small grouped set)
  palettes/    *.json   (one file per biome)
```

Each unit has a single purpose and is understandable/testable in isolation, per the
"modular, isolated testability" rule.

## 3. Data Flow

1. **Load** — `FloraRegistry.load()` reads `resources/data/flora/species/*.json` and
   `resources/data/flora/palettes/*.json`, following the existing content-registry
   pattern. No hardcoded content (CLAUDE.md rule #2).
2. **Prototype pool** — `FloraGenerator.buildPrototypes(planetSeed)`: for each species,
   generate `prototypeVariants` (≈6–8) `Model`s via
   `SpaceColonization → FloraMeshBuilder → FloraModelFactory`. Cached, keyed by species id.
3. **Placement** (replaces `WorldPopulator.placeTrees`): for each candidate position →
   biome lookup → `BiomePalette` density gate → weighted species pick → pick a prototype
   variant → spawn a lightweight `ModelInstance` with a transform (heightmap Y, random
   yaw, scale jitter) plus a per-instance color tint. Instances are added to the existing
   `WorldPopulator.PopulatedWorld.treeInstances`; the `GameScreen` render loop is
   unchanged.

## 4. Determinism

Uses the existing `SeedDeriver` hierarchy (`galaxy/SeedDeriver`).

- Add a `FLORA_DOMAIN` constant to `SeedDeriver`.
- `floraSeed = SeedDeriver.domain(planetSeed, FLORA_DOMAIN)`.
- Prototype variant `v` of species `s` is seeded from a derivation of
  `(floraSeed, hash(speciesId, v))`.
- The placement candidate stream is seeded from `floraSeed` (replacing the ad-hoc
  `seed + 7919L` in the current `placeTrees`).
- Result: the same planet seed always produces an identical forest.

## 5. Space Colonization (bounded & simple)

- **Envelope shapes** (`AttractionEnvelope`): `COLUMN` (cactus), `ELLIPSOID`
  (broadleaf), `CONE` (conifer), `DOME` (shrub / lichen mound), `CYLINDER`. Each takes a
  height range and radius range.
- **Algorithm**: scatter `attractionPoints` seeded points inside the envelope; place a
  root node at the base. Iterate:
  - For each attraction point, find the nearest node within `influenceRadius`.
  - Each node with ≥1 attractor grows one new node a fixed `segmentLength` toward the
    mean (normalized) direction of its attractors.
  - Remove attractors within `killDistance` of any node.
  - Stop when no attractors remain active **or** node count hits `maxNodes` (hard cap →
    bounded per-tree cost).
- **Radii**: assigned by a depth / subtree-size taper (thicker near the root, thinner at
  tips), scaled by `trunk.baseRadius` and `trunk.taper`.
- **Output**: `BranchSkeleton` — a list of nodes `(pos, parentIndex, radius, isTip)`.
  Pure data, no GL.

## 6. Mesh Build — GL-free data, then thin GL upload

`FloraMeshBuilder` runs **without a GL context** and emits interleaved
**pos3 + normal3 + color4** vertices (the same 10-float stride the terrain mesh uses, so
the existing deferred PBR shaders consume flora unchanged):

- **Branches**: each segment (node → parent) becomes a tapered N-gon tube
  (`trunk.sides`, e.g. 5–6), colored with the trunk color.
- **Foliage**: at tip nodes (and optionally sub-tips), if `foliage.style != NONE`, place
  `clumpsPerTip` low-poly blobs (subdivided octahedron / coarse icosphere) sized from
  `clumpRadius` and tinted from the foliage color range.
- Computes a bounding box for culling.

This builder is the **testable core**. `FloraModelFactory` is the only GL-bound piece: it
uploads a `FloraMeshData` to a `Mesh` and wraps it in a `Model` with a material. It is
verified visually (run skill / screenshot), not unit-tested — honoring "systems must be
testable without a GL context."

## 7. Data Schemas (illustrative)

**Species** (`resources/data/flora/species/jungle_canopy_tree.json`):
```json
{
  "id": "jungle_canopy_tree",
  "displayName": "Canopy Tree",
  "envelope": { "shape": "ELLIPSOID", "height": [8, 14], "radius": [3, 5] },
  "growth": {
    "attractionPoints": 220,
    "influenceRadius": 4.0,
    "killDistance": 0.7,
    "segmentLength": 0.45,
    "maxNodes": 600
  },
  "trunk": { "sides": 6, "baseRadius": 0.35, "taper": 0.78, "color": "#5a3b22" },
  "foliage": {
    "style": "CLUMP",
    "clumpsPerTip": 1,
    "clumpRadius": [1.0, 1.8],
    "color": ["#2f6b2a", "#3f8a34"]
  },
  "prototypeVariants": 8
}
```
`foliage.style` ∈ `{ CLUMP, NONE }` (NONE → bare/woody plants like cacti).

**Palette** (`resources/data/flora/palettes/tropical_forest.json`), one per biome:
```json
{
  "biome": "TROPICAL_FOREST",
  "density": 0.85,
  "tintJitter": 0.08,
  "species": [
    { "id": "jungle_canopy_tree", "weight": 0.6 },
    { "id": "jungle_understory",  "weight": 0.4 }
  ]
}
```

Every `BiomeType` value gets a palette. `OCEAN`, `LAKE`, `RIVER`, and `ICE_SHEET` get
empty species lists (no large flora). Palette `density` supersedes the current hardcoded
`treeDensity()` method.

### Starter species set (~8–10, data-only to extend)
- Broadleaf canopy + understory (tropical / temperate forest)
- Conifer (boreal forest, tundra edge)
- Cactus + succulent (desert, arid shrub) — `foliage.style: NONE` / sparse
- Dwarf shrub + lichen mound (tundra, steppe) — low `DOME` envelopes
- Savanna tree (savanna, grassland accent)

## 8. Integration & Lifecycle

- `WorldPopulator.placeTrees()` body delegates to `FloraGenerator`, keeping the same
  `PopulatedWorld.treeInstances` output and the existing `GameScreen` render path. The
  old hardcoded density methods (`treeDensity`, etc.) are superseded by palette density.
  Grass / rock / animal placement is untouched.
- **Disposal**: `FloraGenerator` owns the prototype `Model`s (implements `Disposable`,
  freed on scene teardown). `ModelInstance`s share prototype meshes → no per-instance
  disposal. Ownership is documented at the generator.
- **Collision**: flora is non-collidable in Cycle A (matches current box trees).

## 9. Testing (JUnit 5, headless — no GL)

- `SpaceColonizationTest` — determinism (same seed → same skeleton); every non-root node
  has a valid parent index; nodes lie within the envelope bounds; termination under the
  `maxNodes` cap.
- `FloraMeshBuilderTest` — vertex/index counts > 0; finite bounds; normals normalized;
  determinism; `foliage.style: NONE` produces zero foliage vertices.
- `FloraRegistryTest` — loads sample JSON; weighted species selection is deterministic
  and roughly matches configured weights; unknown-species id is handled gracefully.
- `BiomePaletteTest` — density lookup; deterministic weighted pick.
- `AttractionEnvelopeTest` — generated points lie inside each shape; deterministic.

`FloraModelFactory` and the `GameScreen` wiring (GL-bound) are verified visually via the
run skill (screenshot), not unit-tested.

## 10. Risks & Notes

- **Per-tree cost**: space colonization is iterative; the `maxNodes` cap and the small
  prototype pool (generate once at load, not per placement) keep cost bounded.
- **Shader compatibility**: matching the terrain's pos3+normal3+color4 vertex layout is a
  hard requirement so flora flows through the deferred PBR pipeline with no shader work.
- **Bridge to Cycle B**: prototype meshes are the natural instance sources when GPU
  instancing lands, so this structure is forward-compatible.
