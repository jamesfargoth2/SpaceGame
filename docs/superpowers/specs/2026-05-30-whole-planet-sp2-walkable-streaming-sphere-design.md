# SP2 — Walkable Streaming Sphere (stand on the planet)

**Date:** 2026-05-30
**Status:** Design (approved for spec)
**Initiative:** Whole-planet generation (Minecraft-style walk-anywhere). SP2 of 3.
**Depends on:** SP1 (floating-origin terrain spine — `core/coords`, `PlanetCoordsKM`/`LocalCoordsM`/`CoordConvert`, chunk-local metre meshes, per-chunk collision body transform, `RadialGravitySystem` radial gravity, `OriginRebasedEvent` plumbing). All merged.
**Feeds:** SP3 (seamless surface↔orbit, far-hemisphere frame unification); SP2 follow-ups (grass / vegetation / water / NPC re-anchor, texture splatting).

---

## 1. Goal & context

After SP1 the spherical terrain quadtree is **precision-correct at true planetary scale**, but the player never touches it: at ground level (camera altitude ≤ 4000 m) the game renders and collides against a finite **5000 m × 5000 m flat heightmap patch** (`data/TerrainGenerator`, `GameScreen.terrainMesh`, 255×255 verts ≈ 20 m spacing). The cube-sphere planet only **fades in above 4000 m** ([`GameScreen.renderTerrain`](../../../core/src/main/java/com/galacticodyssey/ui/GameScreen.java)) and at `MAX_DEPTH=8` is far too coarse (~1 km vertex spacing) to stand on. The flat pad is the "small surface" the player is stuck on; past its 5 km edge is void.

**SP2 makes the player walk on the real spherical planet.** Wherever the player goes, near-field sphere chunks stream in around them at walkable (~15–20 m) fidelity with collision, driven by an asynchronous generation pipeline so movement never hitches. The flat pad is **retired**. Radial gravity (SP1) already pulls toward the planet centre, so the player walks the curved surface and can traverse the entire globe — the near-field streaming shell follows them anywhere.

### What SP2 does NOT do (the SP2/SP3 seam)

Per SP1 §4.4, chunk-local metre meshes are only precision-safe while a chunk's placement stays within the float-safe local band (~±5 km, consistent with the 1000 m rebase + horizon distance). That band exactly covers the **near-field surface the player stands on**. The **far hemisphere / whole-globe-from-orbit** view exceeds the band and **remains on the existing high-altitude backdrop fade** — unifying those frames is **SP3**. Therefore SP2:

- Removes the **lower** gate (flat pad below 4000 m) and renders near-field sphere chunks from the ground up.
- **Keeps** the existing high-altitude / orbital backdrop fade-out untouched (SP3 territory).
- Does **not** re-anchor surface dressing (grass, vegetation, water, NPC/object placement). SP2 ships the geometry + collision + the `surfaceHeightAt` seam those follow-ups consume.
- Does **not** add texture splatting; SP2 chunks use the existing vertex-colour terrain shading the sphere already uses (correct, flatter-shaded up close until a later splatting pass).

The game must still boot and play after SP2: player spawns on the sphere, walks, collides, gravity is radial.

---

## 2. Approach (locked)

**Deepen the existing unified quadtree + asynchronous streaming.** Build on SP1's tested cube-sphere projection, per-chunk Bullet collision, and floating-origin rebase. The only real gaps are: (a) insufficient LOD depth + detail, (b) synchronous generation that would hitch while walking, and (c) the lower flat-pad gate. Rejected alternatives: a sphere-conformed roaming detail patch (permanent second system, fiddly seam) and a geometry clipmap (full rewrite, discards working tested code, awkward collision).

**User-confirmed decisions:**
- **Threading:** full asynchronous worker queue (background mesh+collision build, main-thread GL upload + Bullet world mutation).
- **Collision range:** wider / vehicle-friendly (collision shell large enough that fast surface vehicles don't outrun it), bounded by the float-safe band.

---

## 3. Components & changes

### 3.1 Deeper LOD — `TerrainChunk`

- `MAX_DEPTH` 8 → **14** (tune 14–15 during planning/feel). At Earth radius the finest leaf vertex spacing ≈ `radiusKm·π/2 / 2^depth / (GRID_SIZE−1)` ≈ **~19 m** at depth 14 — matching the retired flat pad. Grid stays **33×33** (short-index overflow constraint, fe5595d — unchanged).
- Because the quadtree only subdivides toward the camera, resident leaf count stays bounded (~one split-chain per level + siblings per face under the player). A test asserts a **resident-chunk ceiling** as the camera orbits.
- Add a per-chunk **generation token** (monotonic `int`) so async results from a chunk that has since split/merged are discarded (see 3.3).

### 3.2 High-frequency detail — `TerrainNoiseStack.sampleAt`

The stack already adds LOD-gated octaves at `lod ≥ 3` (freq 64) and `lod ≥ 5` (freq 256). Extend with progressive octaves gated at **`lod ≥ 7 / 9 / 11 / 13`** (freq ~512 / 1024 / 2048 / 4096, decreasing amplitude) so subdividing chunks reveal *real* meter-scale relief instead of re-tessellating a smooth curve. Deterministic (seeded `detailNoise`/dedicated domain). Amplitudes tuned so:
- total height stays within the existing envelope (`rKm = radiusKm + height·radiusKm·0.002`, SP1), and
- local slopes stay walkable (no vertical cliffs from high-freq octaves at the finest scale).

The existing `heightAt(dir, biomeMap, lod)` is the basis for the `surfaceHeightAt` seam (3.6).

### 3.3 Asynchronous chunk generation — `TerrainStreamer` (new) + `TerrainQuadtree`

The core new piece. Today `TerrainQuadtree.recursiveUpdate` calls `generateMesh` **inline** (GL `Mesh` + `btBvhTriangleMeshShape` + `addRigidBody`) — fine for 6 coarse roots, fatal for streaming depth-14 chunks while walking.

**Pipeline:**

1. **Enqueue (main, in `update`):** when a chunk needs geometry, set state `PENDING`, capture its generation token, and submit a job to `TerrainStreamer`. No inline build.
2. **Worker thread(s) (CPU only — no GL, no Bullet world mutation):**
   - `TerrainMeshBuilder.build(...)` → `MeshData` (float[] verts, short[] indices) — already pure CPU.
   - Build the `btTriangleMesh` + `btBvhTriangleMeshShape` from chunk-local verts (BVH build is heavy CPU; safe off-thread because it does not touch `dynamicsWorld`). Collision is only built for chunks that pass the collision policy (3.4) — render-only chunks skip it.
   - Package a `GeneratedChunk` result (MeshData + optional shape/triMesh + token).
3. **Drain (main, budgeted — `MAX_UPLOADS_PER_FRAME`):**
   - If the result's token ≠ the chunk's current token (it split/merged in flight), **dispose** the shape/triMesh and drop it.
   - Else: create + upload the GL `Mesh` (GL thread), construct the `btRigidBody` and `dynamicsWorld.addRigidBody` (Bullet world mutation is main-thread-only), apply placement transform (`planetToLocal`, SP1), set state `READY`.

**Chunk states:** `PENDING → GENERATING → READY` (+ implicit `STALE` via token mismatch). Worker exceptions mark the chunk failed and leave it eligible for re-enqueue next pass; the main loop never throws.

**Thread-safety:** each job reads only immutable inputs (seeded `TerrainNoiseStack`, `BiomeMap`, face/uv/radius). No shared mutable terrain state crosses the boundary. The result queue is a concurrent queue drained on the main thread. The streamer owns a small fixed thread pool; `dispose()` shuts it down and drains/disposes pending results.

**Origin rebase in flight:** chunk-local meshes are origin-independent (SP1); only the *placement translation* depends on `originPlanetKm`, and it is computed at **drain time** from the current origin (and updated by the existing `setOrigin` for live chunks). In-flight jobs are therefore rebase-safe with no special handling.

### 3.4 Collision gating (wider / vehicle-friendly)

Building a triangle-mesh body for every visible leaf is wasteful and exceeds Bullet's comfort far from origin. Policy: a leaf chunk gets a collision body iff it is **a leaf at depth ≥ `COLLISION_MIN_DEPTH`** *and* within **`COLLISION_RADIUS_M`** of the player, where `COLLISION_RADIUS_M` is sized for fast surface vehicles yet kept inside the float-safe band (≲ a few km). Chunks outside the policy are **render-only** (mesh, no body). When a chunk leaves the collision shell (player moves away) its body is removed; entering chunks get a body built (off-thread) and added. A test asserts the body count stays bounded and that bodies exist under/around the player.

### 3.5 Retire the flat pad / remove the lower gate — `GameScreen`

In `renderTerrain`:
- **Delete** the `else` (flat-pad) branch: `terrainMesh` render, `gbuffer_terrain` splat path, `data/TerrainGenerator` heightmap build, flat-pad collision, and the `heightmap`-based camera/NPC/object placement that depends on `TERRAIN_WIDTH/DEPTH/VERTS`.
- **Remove the lower activation gate** (`useSphereTerrain = altitude > FLAT_TERRAIN_FADE_END_Y`): always render quadtree leaves from the ground up.
- **Keep** the existing high-altitude / orbital `terrainFade` fade-out (far-hemisphere backdrop) — that is SP3's seam and must not regress.

This deletes a meaningful slice of `GameScreen` (the entire flat-pad subsystem). That removal is the point — the dual system is what created the seam. Constants `FLAT_TERRAIN_FADE_*`, `TERRAIN_WIDTH/DEPTH`, `TERRAIN_VERTS_*`, the `heightmap` field, `terrainMesh`, `pushCameraAboveTerrain`, `debugTerrainPenetration`, and the four terrain splat textures are removed or made sphere-driven.

### 3.6 `surfaceHeightAt` seam — `PlanetTerrainSystem`

Expose the surface query that player spawn (3.7) and all later dressing sub-projects consume:

```java
/** Surface radius (km, incl. terrain height) at a unit direction from planet centre. */
double surfaceRadiusKmAt(Vector3 unitDir);
/** Convenience: surface point in the current LOCAL frame (metres). */
LocalCoordsM surfacePointLocal(Vector3 unitDir);
```

Implemented over `noise.heightAt(dir, biomeMap, MAX_LOD)` and the SP1 radius formula. Deterministic; GL-free; unit-testable. A test asserts it agrees (within tolerance) with the actual mesh vertex height at a sampled direction.

### 3.7 Player spawn + walk on the sphere — `GameWorld` / `GameScreen`

- On `loadPlanet`, **synchronously pre-warm** the chunk chain under the spawn direction down to leaf depth (bypass the async queue for just this chain) so a collision body exists at frame 1; place the capsule at `surfacePointLocal(spawnDir)` + capsule half-height along the radial up.
- Player physics already uses `RadialGravitySystem` (radial down) + capsule collision (SP1). Spawn-time pre-warm prevents fall-through before async streaming catches up.
- NPC/object spawn that previously sampled the flat heightmap is, for SP2, either pinned to the pre-warmed spawn region via `surfaceHeightAt` or deferred with the dressing follow-ups (planning decides per call-site; SP2 must not leave NPCs floating or falling forever).

---

## 4. Files changed / added

| File | Change |
|---|---|
| **`planet/terrain/TerrainStreamer.java`** *(new)* | Async generation: thread pool, job submit, concurrent result queue, budgeted drain, shutdown/dispose. |
| `planet/terrain/TerrainChunk.java` | `MAX_DEPTH`→14; chunk state enum; generation token; collision-policy flag. |
| `planet/terrain/TerrainNoiseStack.java` | Progressive high-freq octaves at `lod ≥ 7/9/11/13`. |
| `planet/terrain/TerrainQuadtree.java` | Enqueue via `TerrainStreamer` instead of inline `generateMesh`; drain results (GL upload + `addRigidBody`); token-based stale discard; collision gating; bounded resident set. |
| `planet/terrain/PlanetTerrainSystem.java` | Own/drive `TerrainStreamer` (drain in `update`); `surfaceRadiusKmAt` / `surfacePointLocal`; spawn pre-warm entry point. |
| `core/GameWorld.java` | Spawn player on sphere via `surfacePointLocal`; pre-warm; drop flat-pad-derived thermal/scene 50 km assumptions where they depended on the pad. |
| `ui/GameScreen.java` | Delete flat-pad branch + lower gate + heightmap placement; render quadtree leaves from ground up; keep orbital fade. Remove dead flat-pad fields/constants/textures. |

Out of scope (later): `flora/grass/*`, `flora/*` vegetation, `water/*`, NPC placement re-anchor; sphere texture splatting; SP3 frame unification.

---

## 5. Contracts touched (convert-at-boundary, don't break)

1. `GameScreen.renderTerrain` no longer renders `terrainMesh`; any external reader of the flat `heightmap` (NPC/object/grass placement) must move to `surfaceHeightAt` or be deferred — enumerate every `heightmap`/`TerrainGenerator.getHeightAt` caller during planning and resolve each (re-anchor vs. defer-cleanly).
2. `PlanetTerrainSystem.update` now drains the async queue every frame (GL + Bullet on main thread) — ordering vs. render and physics step must be correct (drain before render; body add before physics step that frame or next).
3. Existing SP1 invariants (chunk-local precision, placement = `planetToLocal`, rebase shifts placement+body equally, LOD determinism across rebase) must remain green.
4. Collision body lifecycle: bodies added/removed as the shell moves — must `removeRigidBody` before disposing shape/triMesh (existing `disposeCollision` pattern); no leaks, no use-after-free across threads (shapes built off-thread are only added/removed on main).
5. Headless/no-GL: mesh upload skipped (existing pattern), collision still built — async path must preserve this so existing collision tests pass.

---

## 6. Testing (headless, isolated — architectural rule 5)

1. **Finest spacing:** at `MAX_DEPTH`, finest leaf vertex spacing ≈ target (~19 m) on a 6371 km planet (analytic).
2. **Detail octaves:** `heightAt` differs at sub-20 m separation between adjacent surface points at high lod (proves real fine relief, not interpolation); deterministic for a fixed seed.
3. **Resident bound:** drive `update` with a camera path skimming the surface and orbiting; assert resident leaf count and collision-body count stay under ceilings.
4. **Async == sync (determinism):** a chunk built via the streamer produces byte-identical `MeshData` to a direct `TerrainMeshBuilder.build` (drain the queue deterministically in the test; pool size 1 / inline executor).
5. **Stale-job discard:** enqueue, split the chunk (token bump), drain — result is discarded and its shape/triMesh disposed (no leak, body not added).
6. **Collision gating:** only leaves at depth ≥ `COLLISION_MIN_DEPTH` within `COLLISION_RADIUS_M` have bodies; bodies exist under the player; count bounded.
7. **`surfaceHeightAt` agreement:** matches the actual mesh vertex height at sampled directions within tolerance.
8. **Spawn no-fall-through:** after pre-warm, a collision body covers the spawn point and a capsule dropped at spawn rests at `surface + halfHeight` (simulated, headless Bullet).
9. **Rebase still correct:** SP1 rebase/LOD/collision tests remain green with the async path; in-flight job during a simulated rebase drains to the correct placement.
10. **Dispose/shutdown:** `TerrainStreamer.dispose` stops the pool and disposes all pending/queued native resources (no Bullet/GL leaks).

---

## 7. Risks / open items

- **GL/Bullet thread discipline (primary risk):** GL `Mesh` upload and `dynamicsWorld` mutation **only** on the main thread; only pure-CPU mesh/BVH build off-thread. The plan must state this explicitly and the drain budget must bound per-frame main-thread cost.
- **Detail-octave game feel:** amplitudes/frequencies for walkable-but-interesting terrain will need iteration on-device; spec fixes the structure, tuning is expected to follow.
- **Float precision at depth 14:** chunk-local meshes subtract centre in double (SP1) → safe; verify normals/seams at the finest depth and at face boundaries.
- **`GameScreen` removal blast radius:** the flat pad is woven into spawn, NPC/object/grass placement, thermal scene setup, and debug helpers. Planning must enumerate every dependency before deleting, so nothing silently breaks.
- **Collision shell churn for vehicles:** the wider radius means more body add/remove as a vehicle moves fast; budget body builds (off-thread) and removals to avoid main-thread spikes.
- **Far view unchanged:** SP2 deliberately keeps the high-altitude backdrop fade; the whole-globe-from-orbit seam and far-hemisphere frame unification are SP3 and are explicitly not solved here.
