# Instanced Grass / Ground Cover (Cycle B) — Design

**Date:** 2026-05-28
**Status:** Approved (design); pending implementation plan

## 1. Goal & Scope

Replace the ~500 vertex-coloured cylinder grass `ModelInstance`s (one draw call each,
generated in `data/WorldPopulator.placeGrass`) with a **GPU-instanced, chunked grass field**
that renders tens of thousands of low-poly, vertex-coloured, wind-swaying grass tufts in a
**single instanced draw call**, biome-driven in density and colour.

This is **Cycle B** of the phased flora effort:
- **A — Generation Core (DONE):** space-colonization branching trees.
- **B — Grass / Ground Cover (this spec).**
- **C — Modular Alien Plants (later):** bioluminescent / carnivorous / crystal growths.

"Ground cover" variety (lush grass, dry savanna grass, low tundra moss) comes from
biome-tinted, biome-sized variants of the **same** instanced tuft geometry — not separate
models.

### Design decision: build for scale
World generation will scale to realistic, streamed sizes, so a single fixed full-terrain
instance buffer is rejected. The system is **chunked and camera-centred** from the start:
grass is generated per fixed-size world cell, only within a radius of the camera, and is
decoupled from the concrete terrain representation via a `TerrainSampler` interface.

### Out of scope / deferred
- Textured grass, alpha-test billboards, and distant-grass LOD impostors.
- Grass collision / trampling / interaction.
- Ground-cover props beyond tufts (pebbles, flowers, ferns as separate instanced types).
- Camera-radius **terrain** streaming itself — Cycle B consumes a `TerrainSampler`; the
  streamed implementation of that sampler is a separate concern (see scene-streaming work).

## 2. Architecture: chunked grass field, one draw call

Grass is generated **per fixed-size world cell** (default 32 m), deterministically seeded by
cell coordinates — never the whole terrain at once. Only cells within a radius of the camera
are active. As the player moves, cells entering range are generated (or pulled from an LRU
cache); cells leaving range are released to a pool. This is the standard scalable
grass-tiling approach and mirrors the planned scene-streaming decomposition.

**Decoupling — `TerrainSampler`.** Grass generation depends only on `heightAt(x,z)` and
`biomeAt(x,z)`, not on the concrete 500×500 `heightmap`/`biomeGrid` arrays. Today a thin
adapter (`HeightmapTerrainSampler`) wraps those arrays; when terrain streaming lands, a new
adapter wraps the streamed provider with **zero changes to grass code**. This abstraction is
what lets the system scale.

**Rendering.** Cells are the *generation* unit, but all active cells' per-instance data is
packed into **one dynamic instance buffer** drawn in a **single instanced draw call** per
frame. The buffer is re-packed only when the active cell set changes (crossing a cell
boundary), not every frame. Result: O(1) draw calls regardless of world size; cost bounded
by the camera radius, not by the world.

Determinism uses the existing seed hierarchy: a grass sub-seed derived from `FLORA_DOMAIN`,
then `SeedDeriver.forChunk(grassSeed, cellX, cellZ)` per cell — so the same world seed
produces identical grass regardless of which direction the player approaches a cell.

## 3. Placement (package layout)

New sub-package `com.galacticodyssey.flora.grass`.

```
core/src/main/java/com/galacticodyssey/flora/grass/
  TerrainSampler.java          interface: heightAt(x,z), biomeAt(x,z)
  HeightmapTerrainSampler.java adapter over the current heightmap + biomeGrid arrays
  GrassConfig.java             POJO: cell/radius/fade/wind globals + per-biome settings
  GrassRegistry.java           loads data/flora/grass.json (loadFromJson(String) testable)
  GrassCell.java               GL-free: (cellX,cellZ,sampler,seed,config) -> float[] instances
  GrassField.java              active-cell set by camera pos, LRU cache, packed buffer
  GrassBladeMesh.java          GL: base tuft geometry + instanced attribute layout
  GrassRenderer.java           GL: instanced shader + mesh + dynamic buffer; one draw/frame

core/src/main/resources/shaders/
  gbuffer_grass.vert           instanced transform + wind sway + distance fade (pairs with gbuffer.frag)

core/src/main/resources/data/flora/
  grass.json                   per-biome grass config
```

## 4. Instance attribute layout

Per-instance `float[]` (one record per tuft, stride 10), produced by `GrassCell`, in this
exact order (matches the implementation and the shader's `i_params` unpacking):
`offsetX, offsetY, offsetZ, scaleXZ, scaleY, rotationY, windPhase, colorR, colorG, colorB`
— grouped into instance attributes `i_offset` (vec3), `i_params` (vec4 = scaleXZ, scaleY,
rotationY, windPhase), `i_color` (vec3). The base tuft mesh carries `a_position` +
`a_normal`; the instance attributes are added via libGDX instanced `VertexAttribute`s
(units 0/1/2, divisor 1).

## 5. Generation pipeline

1. `GrassField.update(cameraX, cameraZ)`: compute the active cell set = all cells whose
   centres lie within `radius` of the camera (a disc). Diff against the previous set.
2. For each newly active cell: look up its `float[]` in the LRU cache, or generate via
   `GrassCell.generate(cellX, cellZ, sampler, grassSeed, config)`:
   - Seed a `Random` from `SeedDeriver.forChunk(grassSeed, cellX, cellZ)`.
   - Scatter candidate points in the cell. For each: `biome = sampler.biomeAt(wx,wz)`;
     look up the biome's grass settings; if none or density 0, skip; else density-gate via
     `rng`. Snap `offsetY = sampler.heightAt(wx,wz)`. Pick height/colour from the biome's
     ranges (lerp colour A→B by `rng`), random `rotationY`, random `windPhase`.
   - Return the packed `float[]` + tuft count.
3. Release cells that left the active set to the pool (cap the LRU cache size).
4. If the active set changed, concatenate all active cells' `float[]` into the single
   instance buffer and upload (`GrassRenderer.setInstances`).
5. Each frame `GrassRenderer.render(camera, time)`: bind the instanced shader, set
   `u_projViewTrans`, `u_time`, `u_camPos`, `u_fadeRadius`, `u_fadeBand`, grass material
   constants; one `mesh.render(shader, GL_TRIANGLES)` (instanced).

## 6. Shader (`gbuffer_grass.vert`, pairs with existing `gbuffer.frag`)

- World position = per-instance `offset` + rotateY(`rotationY`) applied to the base vertex
  scaled by (`scaleXZ`, `scaleY`, `scaleXZ`).
- **Wind:** `sway = u_windAmp * sin(u_time * u_windFreq + windPhase + worldX * k)`, applied
  horizontally and multiplied by `(localY / tuftHeight)^2` so bases stay planted, tips bend.
- **Distance fade:** `f = smoothstep(u_fadeRadius, u_fadeRadius - u_fadeBand,
  distance(u_camPos, offset))`; multiply `scaleY` by `f` so edge tufts shrink into the
  ground rather than pop.
- Emits the same varyings `gbuffer.frag` already consumes (`v_viewPos`, `v_viewNormal`,
  `v_color`), so **no fragment-shader or lighting changes are required**. Grass material
  constants (metallic 0, high roughness, no emissive) are set as uniforms, matching how
  `renderTerrain()` configures the gbuffer material today.

## 7. Data schema (`data/flora/grass.json`)

```json
{
  "cellSize": 32.0, "radius": 140.0, "fadeBand": 24.0,
  "baseTuftsPerM2": 0.25, "bladesPerTuft": 3, "maxCachedCells": 256,
  "wind": { "amplitude": 0.18, "frequency": 1.3 },
  "biomes": [
    { "biome": "GRASSLAND", "density": 1.0,  "height": [0.5, 1.1], "colorA": "3a6b22", "colorB": "5a8a2e" },
    { "biome": "SAVANNA",   "density": 0.7,  "height": [0.6, 1.3], "colorA": "6a6b2a", "colorB": "8a7a30" },
    { "biome": "TEMPERATE_FOREST", "density": 0.5, "height": [0.3, 0.7], "colorA": "2f6b22", "colorB": "47852c" },
    { "biome": "STEPPE",    "density": 0.45, "height": [0.3, 0.7], "colorA": "6a6535", "colorB": "7a7240" },
    { "biome": "SWAMP",     "density": 0.4,  "height": [0.4, 0.9], "colorA": "20401a", "colorB": "356025" },
    { "biome": "TROPICAL_FOREST", "density": 0.35, "height": [0.4, 0.9], "colorA": "1f6b1a", "colorB": "2f8a24" },
    { "biome": "BOREAL_FOREST", "density": 0.3, "height": [0.2, 0.5], "colorA": "3a5a32", "colorB": "4a6a3a" },
    { "biome": "TUNDRA",    "density": 0.3,  "height": [0.15, 0.4], "colorA": "4a5a3a", "colorB": "5a6545" }
  ]
}
```
`density` is a per-biome keep-probability (0..1). Each cell scatters a **fixed** candidate
count `round(cellArea * BASE_TUFTS_PER_M2)` (with `BASE_TUFTS_PER_M2` a tuned global constant,
≈0.25), density-independent and identical for every cell. For each candidate (a seeded random
point in the cell): sample `biomeAt`, look up that biome's `density`; keep the candidate iff
`rng.nextFloat() < density` (0 / absent biome → never kept). Kept tufts get height/colour from
the biome's ranges. This is deterministic per cell (same seed → identical kept set and
`float[]`) and gives the expected relative counts: density-1.0 grassland keeps every
candidate, density-0 desert keeps none (exactly empty), intermediate biomes in between. Any
biome not listed (OCEAN, LAKE, RIVER, DESERT, ICE_SHEET, VOLCANIC, etc.) gets no
grass. Global `cellSize`/`radius`/`fadeBand`/`wind`/`maxCachedCells` tune scale and feel.

## 8. Integration

- `GameScreen` (world load): build a `HeightmapTerrainSampler` over the existing
  `heightmap`+`biomeGrid`, a `GrassRegistry` (load `data/flora/grass.json`), a `GrassField`,
  and a `GrassRenderer`. Derive the grass seed from the world seed via `FLORA_DOMAIN`.
- `GameScreen` (frame): `grassField.update(camX, camZ)` (re-packs buffer on cell change),
  then invoke `grassRenderer.render(camera, elapsedTime)` inside the **gbuffer pass**,
  alongside `renderTerrain()`.
- Remove the old cylinder grass: delete `WorldPopulator.placeGrass`, `grassDensity`,
  `grassColorForBiome`, the `grassInstances` population, and its render loop in
  `renderWorldObjects`. (Rocks/trees/animals untouched.)
- **Disposal:** `GrassRenderer` owns the base mesh, instance buffer, and shader (Disposable);
  disposed on scene teardown. `GrassField`'s cached `float[]`s are plain arrays (GC'd).

## 9. Testing (JUnit 5, headless — no GL)

- `GrassCellTest` — determinism (same cell+seed → identical `float[]`); tuft count scales
  with biome density (grassland ≫ tundra ≫ desert = 0); every offset lies within the cell
  bounds; `offsetY` equals `sampler.heightAt`; zero-density biome → empty.
- `GrassFieldTest` — active-cell set for a camera position is the correct radius disc;
  moving one cell over changes the set by only the entering/leaving cells (incremental,
  cached cells reused, not regenerated); packed-buffer length = Σ active cell instance
  counts × stride.
- `GrassRegistryTest` — `grass.json` parses (globals + per-biome); per-biome lookup; unknown
  biome → null/no grass; hex colours parsed.
- `HeightmapTerrainSamplerTest` — `heightAt`/`biomeAt` match the underlying arrays at sample
  points (incl. clamping at edges).
- GL pieces (`GrassBladeMesh`, `GrassRenderer`, `gbuffer_grass.vert`, wind/fade) verified
  **visually** in-game via the run skill — same split as Cycle A.

## 10. Risks & Notes

- **Instanced API:** libGDX 1.13.5 supports `Mesh.enableInstancedRendering` + instanced
  `VertexAttribute`s + `setInstanceData`. Confirmed available; no existing usage in-repo, so
  this is the one genuinely new rendering primitive — verified visually.
- **Buffer churn:** re-pack only on cell-boundary crossings, not per frame; pool/LRU the
  per-cell arrays to bound allocation.
- **Shader compatibility:** the grass vertex shader must emit exactly the varyings
  `gbuffer.frag` expects; mismatches surface as link errors, caught at first run.
- **Scale path:** the `TerrainSampler` seam is the single point that the future streamed
  world plugs into — grass code does not change when terrain streaming arrives.
