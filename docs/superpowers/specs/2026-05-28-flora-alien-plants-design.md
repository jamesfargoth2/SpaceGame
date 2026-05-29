# Modular Alien Plants (Cycle C) — Design

**Date:** 2026-05-28
**Status:** Approved (design); pending implementation plan

## 1. Goal & Scope

Procedural alien plants in **three archetypes** — **bioluminescent**, **carnivorous**,
**crystal** — generated from a simple **fixed-layer modular assembly** (stalk → canopy →
details), data-driven per species, placed via the Cycle A flora pipeline, with glowing
parts rendered self-emissive through a small dedicated emissive pass. This completes the
3-cycle flora effort.

- **A — Generation Core (DONE):** space-colonization branching trees.
- **B — Grass / Ground Cover (DONE):** chunked GPU-instanced grass.
- **C — Modular Alien Plants (this spec).**

"Modular like creatures but **simpler**": a plant is three fixed layers, not the fauna
recursive socket graph. No dependency on `com.galacticodyssey.fauna`.

### Out of scope / deferred
- Crystal **refraction/transparency** — no refraction infra exists; crystals are opaque +
  emissive shimmer.
- **Real point-light emission** — glow is self-emissive only (deferred RT2 path); plants do
  not illuminate surrounding terrain this cycle.
- Animated lures/traps, plant interaction/damage, alien-plant resource harvesting.

## 2. Modular structure (3 fixed layers)

A plant is composed of exactly:
- **Stalk** — one tapered trunk/loft (height, base radius, taper, sides, optional lean +
  gentle bends). The mount point for the canopy is the stalk tip.
- **Canopy** — one archetype-driven shape atop the stalk:
  - *Bioluminescent*: a cluster of low-poly glowing bulbs/ellipsoids.
  - *Carnivorous*: a flared **pitcher/maw** (a cup that widens then pinches to a lip ring)
    with an emissive **lure** nub inside.
  - *Crystal*: a faceted angular cluster (a few intersecting prisms/bipyramids).
- **Details** — N small seeded accents on stalk/canopy: glow spots (biolum), inward
  spines/teeth (carnivorous), or budding sub-crystals (crystal).

The modularity is **data** (which canopy shape, detail count, colours, glow strength) — no
`AttachmentNode` recursion, no `PartType`/socket graph.

## 3. Placement & package layout

New sub-package `com.galacticodyssey.flora.alien`. Reuses the Cycle A placement model
(seeded, biome-gated scatter → per-species prototype pool → `ModelInstance`s), but alien
plants render with the emissive shader, so they go into a **new instance list**.

```
core/src/main/java/com/galacticodyssey/flora/alien/
  AlienArchetype.java        enum BIOLUMINESCENT, CARNIVOROUS, CRYSTAL
  AlienPlantSpecies.java      data POJO (stalk/canopy/detail params, colours, emissive)
  AlienPlantRegistry.java     loads data/flora/alien_plants.json (species + biome palette)
  AlienPlantMeshData.java     float[] verts (stride 11) + short[] indices + bounds — pure data
  AlienPlantMeshBuilder.java  species + seed -> AlienPlantMeshData (GL-free, tested)
  AlienPlantModelFactory.java AlienPlantMeshData -> libGDX Model (thin GL)
  AlienPlantPlacement.java    resolved placement (speciesId, variant, transform)
  AlienPlantGenerator.java    planPlacements (pure) + buildPrototypes + populate (GL)

core/src/main/java/com/galacticodyssey/rendering/
  EmissiveGBufferShader.java       Shader: gbuffer.vert/frag + HAS_VERTEX_COLOR + HAS_EMISSIVE_ATTRIB
  EmissiveGBufferShaderProvider.java  ModelBatch shader provider returning it

core/src/main/resources/data/flora/
  alien_plants.json          species + per-biome palette
```

`WorldPopulator.PopulatedWorld` gains `Array<ModelInstance> alienInstances` (+ prototype
`Model`s registered via `addModel` for disposal).

## 4. Vertex layout & emissive

Mesh vertices are interleaved **pos3 + normal3 + color4 + emissive1** (stride **11**).
- Albedo comes from the per-vertex colour (`a_color`, `HAS_VERTEX_COLOR`).
- Glow comes from the per-vertex emissive scalar (`a_emissive`, `HAS_EMISSIVE_ATTRIB`):
  `emissive = albedo * a_emissive * u_emissiveIntensity` in `gbuffer.frag`, written to RT2
  and added additively in the lighting pass. **The actual glow strength is baked into
  `a_emissive`** (e.g. 0 for stalk/teeth, 2–4 for biolum bulbs, ~0.6 for crystal shimmer);
  `u_emissiveIntensity` is a constant `1.0`. So one shader handles all species with no
  per-instance uniforms.

## 5. Rendering integration

- **`EmissiveGBufferShader`** mirrors `GBufferBatchShader` but compiles
  `shaderCache.get("gbuffer.vert", "gbuffer.frag", "HAS_VERTEX_COLOR", "HAS_EMISSIVE_ATTRIB")`,
  sets `u_emissiveIntensity = 1f` (plus `u_metallicScale=0`, a mid `u_roughnessScale`,
  `u_tiling`), and per-renderable sets `u_projViewTrans`/`u_worldTrans`/`u_normalMatrix`.
  Albedo is per-vertex, so it does not read a diffuse material attribute.
- `GameScreen` creates a second `ModelBatch(new EmissiveGBufferShaderProvider(shaderCache))`
  and, inside the **gbuffer pass** (after `renderTerrain()`/trees), renders
  `populatedWorld.alienInstances`. The shared tree/rock `GBufferBatchShader` is untouched.
- Disposal: the alien `ModelBatch` is disposed in `GameScreen.dispose()`; prototype `Model`s
  are owned by `PopulatedWorld` (registered via `addModel`).

## 6. Generation pipeline (mirrors Cycle A)

1. `AlienPlantGenerator.planPlacements(registry, biomeGrid, heightmap, …, planetSeed,
   attempts)` — pure/GL-free: seeded scatter, biome lookup, palette density gate, weighted
   species pick, variant index, yaw, scale → `List<AlienPlantPlacement>`. Seed derived from
   `SeedDeriver.floraDomain(planetSeed)` + an alien salt via `SeedDeriver.forId`.
2. `buildPrototypes(registry, planetSeed)` (GL) — per species, build `prototypeVariants`
   `Model`s via `AlienPlantMeshBuilder → AlienPlantModelFactory`, seeded per variant.
3. `populate(world, registry, …)` (GL) — register prototype models, plan placements, spawn
   `ModelInstance`s (translate/yaw/scale) into `world.alienInstances`.

## 7. Data schema (`data/flora/alien_plants.json`)

```json
{
  "species": [
    { "id": "glowcap", "archetype": "BIOLUMINESCENT",
      "stalk": { "height": [1.5,3.0], "baseRadius": 0.12, "taper": 0.7, "sides": 6, "color": "2a2f4a" },
      "canopy": { "clumps": [3,6], "radius": [0.4,0.8], "color": "2fd0c0", "emissive": 3.0 },
      "details": { "count": [3,8], "emissive": 2.0 },
      "prototypeVariants": 6 },
    { "id": "maw_pitcher", "archetype": "CARNIVOROUS",
      "stalk": { "height": [0.8,1.6], "baseRadius": 0.15, "taper": 0.85, "sides": 6, "color": "3a2a1f" },
      "canopy": { "mouthRadius": [0.5,0.9], "depth": [0.6,1.1], "color": "5a1f28", "lureEmissive": 2.5 },
      "details": { "teeth": [5,9] },
      "prototypeVariants": 5 },
    { "id": "shardspire", "archetype": "CRYSTAL",
      "stalk": { "height": [0.4,1.0], "baseRadius": 0.2, "taper": 0.9, "sides": 5, "color": "404a6a" },
      "canopy": { "shards": [4,8], "length": [0.6,1.6], "color": "8ad0ff", "emissive": 0.6 },
      "details": { "subShards": [2,5] },
      "prototypeVariants": 6 }
  ],
  "palette": [
    { "biome": "SWAMP",           "density": 0.5,  "species": [ {"id":"glowcap","weight":0.7}, {"id":"maw_pitcher","weight":0.3} ] },
    { "biome": "TROPICAL_FOREST", "density": 0.2,  "species": [ {"id":"glowcap","weight":0.6}, {"id":"maw_pitcher","weight":0.4} ] },
    { "biome": "BOREAL_FOREST",   "density": 0.12, "species": [ {"id":"glowcap","weight":1.0} ] },
    { "biome": "TUNDRA",          "density": 0.1,  "species": [ {"id":"glowcap","weight":1.0} ] },
    { "biome": "VOLCANIC",        "density": 0.3,  "species": [ {"id":"shardspire","weight":1.0} ] },
    { "biome": "BADLANDS",        "density": 0.15, "species": [ {"id":"shardspire","weight":1.0} ] },
    { "biome": "ROCKY_WASTE",     "density": 0.12, "species": [ {"id":"shardspire","weight":1.0} ] }
  ]
}
```

Common-band number fields are `[min,max]` ranges resolved by the per-prototype RNG. Biomes
not listed get no alien plants (they are rare accents, not ground cover). Colours are hex
(parsed like the other flora registries; 6-digit → append `ff`).

## 8. Testing (JUnit 5, headless — no GL)

- `AlienPlantRegistryTest` — parses all 3 archetypes + palette; per-archetype canopy fields;
  unknown biome → no palette; hex colours parsed.
- `AlienPlantMeshBuilderTest` — per archetype: non-empty geometry; stride 11
  (pos3+normal3+color4+emissive1); normals unit length; determinism (same seed → identical
  arrays); finite bounds. Emissive checks: bioluminescent and crystal bake some vertices with
  `a_emissive > 0`; carnivorous bakes glow only on the lure (its stalk + teeth detail
  vertices are `a_emissive == 0`).
- `AlienPlantPlacementTest` — biome-gated placement (palette biome places, unlisted biome
  empty), variant index in range, scale > 0, determinism (same seed → identical list).
- GL pieces (`AlienPlantModelFactory`, `EmissiveGBufferShader`, GameScreen wiring) verified
  **visually** via the run skill — confirm the three archetypes render and biolum/crystal
  glow (especially at night).

## 9. Risks & Notes

- **Emissive path**: the existing `GBufferBatchShader` hardcodes `u_emissiveIntensity=0` and
  no emissive define, so a dedicated `EmissiveGBufferShader` is required — it is the one new
  rendering piece, verified visually. `gbuffer.frag`'s `HAS_EMISSIVE_ATTRIB` branch already
  computes `albedo * v_emissive * u_emissiveIntensity` → RT2, so no fragment changes.
- **Custom vertex attribute**: `a_emissive` is a `VertexAttribute(Usage.Generic, 1,
  "a_emissive")`; `AlienPlantModelFactory` builds the `Mesh` with Position+Normal+
  ColorUnpacked+that attribute. The `AlienPlantMeshBuilder` core stays GL-free (raw float[]),
  same split as Cycle A.
- **Determinism**: alien salt off `FLORA_DOMAIN` via `SeedDeriver`; same planet seed → same
  alien plants.
- **Density**: alien plants are sparse accents (lower densities than trees/grass), biome-
  themed (biolum in damp/forest/tundra, crystal in volcanic/badlands/rocky).
