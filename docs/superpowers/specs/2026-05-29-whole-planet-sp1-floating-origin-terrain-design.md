# SP1 — Real-scale Planet + Floating-Origin Terrain Spine

**Date:** 2026-05-29
**Status:** Design (approved for spec)
**Initiative:** Whole-planet generation (Minecraft-style walk-anywhere). SP1 of 3.
**Depends on:** existing `planet/terrain/` cube-sphere quadtree, `CoordinateManager`, `OriginRebasedEvent`, `BulletPhysicsSystem` rebase plumbing.
**Feeds:** SP2 (stand on the sphere, near-field fidelity, collision streaming), SP3 (seamless surface↔orbit).

---

## 1. Goal & context

Move the game from a flat 5 km set-piece patch toward one continuous, real-scale, seed-generated planet. SP1 is the **precision foundation only**: make the spherical terrain quadtree, its collision, the camera/LOD math, and radial gravity correct at true planetary scale (a body up to ~12,700 km diameter) without float-precision failure, and unify the planet anchor with the engine's floating-origin model.

SP1 does **not** make the player stand on the sphere, does **not** deepen LOD, and does **not** touch the space↔surface transition. Those are SP2 and SP3. The flat patch, the existing altitude fade-in of the sphere, the player spawn, and `GameScreen` stay as-is so the game still boots and plays after SP1; the sphere spine is validated by isolated headless tests and the existing high-altitude fade.

### Why this is needed (the precision failure)

Today `TerrainMeshBuilder` emits chunk vertices in **planet-local space** (magnitude ≈ `planetRadius`), and `TerrainQuadtree.buildCollision()` bakes them to world space by adding a `float planetCenter` (`v.add(planetCenter)`). At a true Earth radius the surface sits ~6,371 km from the planet centre. Per the `procgen-coordinate-systems` precision table, a `float` at 1,000,000 m already has ~0.12 m error and at the planet-centre magnitude it is catastrophic — z-fighting, vertex jitter, collision holes. The mesh is dead **before** `planetCenter` is even added.

The current code only "works" because `GameWorld` overrides the radius with a 50 km toy value and keeps the planet 50 km below a flat patch the player actually stands on.

---

## 2. Canonical coordinate model (locked for this initiative)

SP1 adopts the `procgen-coordinate-systems` four-layer model verbatim. The two layers SP1 touches:

| Layer | Type | Unit | Origin |
|---|---|---|---|
| **PLANET_SPACE** | `double` | **kilometres** | planet centre |
| **LOCAL_SCENE** | `float` | **metres** | floating origin (camera-near) |

**Canonical rules (enforced, from the skill):**

1. **Subtract in double, cast after.** `float local = (float)((doubleA - doubleB) * KM_TO_M)`. Never subtract two large floats.
2. **Never store a world position as `float`.** Anything outside LOCAL_SCENE is `double` km (or higher layer).
3. **"Up" is the surface normal**, `normalize(playerPlanetKm)`, never `Vector3.Y`.
4. **Planet radius is data-driven km:** `radiusKm = planet.radius * 6371.0` (`planet.radius` is in Earth radii). Range: moon ~0.05 (≈318 km) → terrestrial ~2 (≈12,742 km). Gas/ice giants have no surface (`PlanetType.hasSurface()` already excludes them) and are out of scope.

### Resolved discrepancies (called out per review)

- **Units:** PLANET_SPACE = km (double), LOCAL_SCENE = metres (float) is canonical. This **supersedes** the contradictory `PlanetTerrainSystem.loadPlanet(..., float gameWorldRadius)` Javadoc ("metres") and the `planet.radius * 6371f` float fallback. The number 6371 was always km; the bug was storing/treating it as float metres.
- **Rebase threshold:** `CoordinateManager` rebases at **1000 m** (the value actually wired and consumed by `BulletPhysicsSystem`). `CLAUDE.md` and `docs/systems/core.md` claim ~10 km — those are **stale**; SP1 keeps 1000 m (well inside the float-safe ±5 km band) and does not change rebase behaviour.

### Do-not-break (locked elsewhere)

- `AU_TO_GAME_UNITS = 1000f` (orbital frame) — untouched. Only the surface frame changes.
- Cube-sphere quadtree + **33×33** chunk grid — vertex counts unchanged (short-index overflow, fe5595d). Deeper LOD is SP2.
- `Scene.galaxyAnchor` is `double[]`; the planet's galaxy anchor aligns with that for SP3.
- Tectonics/noise/biome sampling is the upstream macro-shape driver and is unchanged; the spine supplies the sphere geometry/scale it samples on.
- All logic headless/GL-free testable (architectural rule 5).

---

## 3. New coordinate types (introduced by SP1)

Per the skill, these are the prescribed types; they do not exist yet. Keep minimal, no GL deps, fully unit-testable. Placed in `core/` (new subpackage `com.galacticodyssey.core.coords`).

```java
public record PlanetCoordsKM(double x, double y, double z) { /* set/add/sub/scl/len/nor/dst */ }
public record LocalCoordsM(float x, float y, float z) { Vector3 toVector3(); }

public final class CoordConvert {
    public static final double KM_TO_M = 1000.0, M_TO_KM = 0.001;

    /** Critical floating-origin step: subtract in double, scale, cast. */
    public static LocalCoordsM planetToLocal(PlanetCoordsKM world, PlanetCoordsKM originKm);
    public static PlanetCoordsKM localToPlanet(LocalCoordsM local, PlanetCoordsKM originKm);
    /** "Up" at a planet-space point = normalized radial. */
    public static Vector3 surfaceUpLocal(PlanetCoordsKM planetKm);
}
```

These are reusable by SP2/SP3 and other planet systems (thermal, vegetation, vehicle). We deliberately do **not** introduce a bare `Vec3d`; the typed records make the layer explicit (skill rule "never store a position without knowing its layer").

---

## 4. Terrain spine changes

### 4.1 Planet anchor → planet-space km, with a floating-origin reference

`PlanetTerrainSystem`:
- Stores the planet **radius in km** (`double radiusKm = planet.radius * 6371.0`) and the planet's **galaxy anchor** (`double[]`, aligned with `Scene.galaxyAnchor`) for SP3. The planet centre is the origin of PLANET_SPACE, so it is `(0,0,0)` km by definition.
- Stores `originPlanetKm` (`PlanetCoordsKM`): the planet-space km position of the **current LOCAL_SCENE floating origin**. This is the single reference used to place all terrain in the local frame.
- Updates `originPlanetKm` on `OriginRebasedEvent(dx,dy,dz)`: `originPlanetKm += delta_metres * M_TO_KM` (subscribe via `EventBus`, same pattern as `BulletPhysicsSystem`).
- Feeds the quadtree the **camera position in planet-space km** each frame: `cameraPlanetKm = localToPlanet(cameraLocalMetres, originPlanetKm)`.

Replace `getPlanetCenter():Vector3` and the `float planetRadius`. Every current reader of those converts at the boundary (see §6 breakage list).

### 4.2 Quadtree & chunk geometry → double km

`TerrainChunk`:
- `center` becomes `PlanetCoordsKM centerPlanetKm` = `CubeSphere.toSphere(face,u,v).scl(radiusKm)` computed in double.
- `arcLength` in km (double); used only for the screen-size LOD ratio.
- `shouldSplit/shouldMerge` take the **planet-km camera** (or a precomputed double distance) so the screen-size metric is exact at any radius: `dist = centerPlanetKm.dst(cameraPlanetKm)`.
- Add a per-chunk **placement transform** translation `LocalCoordsM` (recomputed when the chunk is (re)placed / on rebase).

`TerrainQuadtree`: carries `double radiusKm` and the `PlanetCoordsKM originPlanetKm`; `update(PlanetCoordsKM cameraPlanetKm)`; adds `onRebase(...)` that recomputes every live chunk's placement transform and updates its collision body transform.

### 4.3 Chunk-local meshes (the core fix)

`TerrainMeshBuilder.build(...)` emits vertices **relative to the chunk centre, in LOCAL metres**:
```
vertexLocalM = (surfacePointPlanetKm − chunkCenterPlanetKm) * KM_TO_M   // subtract in double, cast to float
```
Per-vertex magnitude ≈ chunk extent (metres→few km), full float precision regardless of planet radius. Grid stays 33×33; normals/biome-colour/stride unchanged. The builder gains the `PlanetCoordsKM chunkCenter` parameter.

**Placement.** A chunk's render/collision translation is `planetToLocal(chunkCenterPlanetKm, originPlanetKm)`:
- **Render:** draw the chunk mesh with that translation (per-chunk `Matrix4` / shader model uniform) instead of identity-on-baked-world-verts.
- **Collision:** build `btBvhTriangleMeshShape` once from the chunk-local vertices; set the rigid body's **world transform** to the placement translation. On rebase, just `body.setWorldTransform(...)` with the new translation — no mesh rebuild, O(1) per chunk. (Today collision bakes verts + adds centre; moving the offset into the body transform is what makes rebasing cheap and precision-safe and mirrors how `BulletPhysicsSystem` already shifts dynamic bodies.)

### 4.4 Precision-safe band

Chunk-local metre meshes are valid only while the chunk's placement stays within the float-safe band (~±5 km local, consistent with the 1000 m rebase + horizon distance ≈ √(2·R·h)). Near the surface only small, deep chunks satisfy this — and curvature puts everything beyond the horizon out of view anyway. Distant / low-LOD chunks (far hemisphere, whole-planet-from-orbit) exceed the band and remain on the **existing altitude-fade backdrop path** until SP3 unifies the frames. SP1 guarantees and tests correctness for the **near-field surface chunks** (exactly what SP2 will let the player stand on); it does not attempt to render the whole globe in the local metre frame.

### 4.5 Gravity & "up"

`RadialGravitySystem` takes the planet centre as PLANET_SPACE and computes, per body, `up = surfaceUpLocal(bodyPlanetKm)` (radial), applying force as a near-unit float at magnitude `planet.surfaceGravity`. Direction stays accurate despite the ~6371 km lever arm because the normalize happens in double. `GameWorld` wires the planet centre as a planet-space/galaxy anchor instead of the float `(0,-50000,0)`.

### 4.6 Lockstep: atmosphere shell

`ScatteringParams.planetRadius` (fed to the sky shader as `u_planetRadius`) must match the terrain's **local-frame metre** scale or the atmosphere detaches. SP1 updates the `ScatteringParams` radius derivation to `radiusKm * KM_TO_M` (i.e. `planet.radius * 6371000`) in lockstep with the terrain scale. Full sky/altitude transition behaviour remains SP3's concern.

---

## 5. Files changed / added

| File | Change |
|---|---|
| **`core/coords/PlanetCoordsKM.java`** *(new)* | Double km position record + vector ops. |
| **`core/coords/LocalCoordsM.java`** *(new)* | Float metre position record + `toVector3()`. |
| **`core/coords/CoordConvert.java`** *(new)* | `planetToLocal`, `localToPlanet`, `surfaceUpLocal`, `KM_TO_M`. |
| `planet/terrain/TerrainChunk.java` | `centerPlanetKm` (double), km `arcLength`, double-camera split/merge, placement `LocalCoordsM`. |
| `planet/terrain/TerrainMeshBuilder.java` | Emit chunk-local metre vertices (subtract in double); gains `chunkCenter` param. |
| `planet/terrain/TerrainQuadtree.java` | Double `radiusKm` + `originPlanetKm`; double-camera `update`; collision from chunk-local verts + body transform; `onRebase`. |
| `planet/terrain/PlanetTerrainSystem.java` | `radiusKm = planet.radius*6371`; planet-space/galaxy anchor; `originPlanetKm`; subscribe `OriginRebasedEvent`; feed planet-km camera; double getters. |
| `core/systems/RadialGravitySystem.java` | Planet centre as planet-space; `up` via `surfaceUpLocal`. |
| `planet/ScatteringParams.java` | Radius derivation in lockstep (`radiusKm * 1000`). |
| `core/GameWorld.java` | Wire planet centre as planet-space/galaxy anchor; drop 50 km override; pass `EventBus` to `PlanetTerrainSystem`. |

`data/TerrainGenerator.java` (flat patch), `GameScreen` spawn, and the altitude fade are **unchanged** in SP1.

---

## 6. Contracts touched (convert-at-boundary, don't break)

1. `PlanetTerrainSystem.getPlanetCenter():Vector3` / `float planetRadius` — removed/replaced; readers (`setCameraPositionWorld`, gamescreen renderer transform, gravity) convert at the boundary.
2. `loadPlanet(..., Vector3 gameWorldCenter, float gameWorldRadius)` overloads — collapse to a planet-space/galaxy-anchor + `Planet`-derived `radiusKm`. The 50 km toy override path is deleted.
3. `RadialGravitySystem` / `PlayerMovementSystem` currently assume `planetCenter==(0,0,0)`; that assumption is preserved (planet centre **is** the PLANET_SPACE origin), so on-foot gravity/movement behaviour is unchanged.
4. `ScatteringParams` radius (sky shader) — updated in lockstep (§4.6).
5. `PlanetSurfaceEnvironment` (thermal) derives lat/lon by dividing local position by radius — must read the new km radius consistently (verify, adjust if it assumed the toy radius).
6. `OrbitLineRenderer` / `OrbitalPositionSystem` — render at `1 AU = 1000 units` and rebase on `OriginRebasedEvent`; untouched by SP1, but the planet anchor must remain consistent with the same local frame after rebase (it does, since both shift on the same event).

---

## 7. Testing (headless, isolated — architectural rule 5)

1. **Scale (data-driven):** `loadPlanet` with `radius=1.0` ⇒ `radiusKm≈6371`; `radius=0.05` ⇒ `≈318`. No toy override remains.
2. **Chunk-local precision:** for a deep chunk on a 6371 km planet, every emitted vertex magnitude ≤ chunk extent (not ~6.37e6 m); reconstruct `chunkCenterKm + vertex*M_TO_KM` and match the analytic cube-sphere surface point within mm.
3. **Placement:** chunk render/collision translation equals `planetToLocal(chunkCenterKm, originPlanetKm)` for several faces/depths.
4. **Rebase invariance (key property):** after a simulated `OriginRebasedEvent(dx,dy,dz)`, a chunk's placement and a managed physics body shift by the **same** delta; the player's height above the chunk surface is unchanged.
5. **LOD determinism:** split/merge decisions identical before vs. after a rebase (proves the double-km LOD path is precision-safe).
6. **Gravity:** `surfaceUpLocal` at a surface point equals the analytic radial within 1e-5; applied magnitude matches `planet.surfaceGravity`.
7. **CoordConvert unit tests:** `planetToLocal`/`localToPlanet` round-trip within float epsilon; subtract-in-double verified (large-coordinate case where naive float subtraction would fail).

---

## 8. Risks / open items

- **Render path (only real unknown):** confirm exactly how chunks are drawn in the deferred terrain pass so the per-chunk transform slots in cleanly (model instance vs. raw mesh draw + uniform). Resolve during planning before writing tests.
- **Bullet far-from-origin:** with chunk-local shapes + body transform, collision bodies sit ≤1000 m from origin — inside Bullet's comfort zone. ✓
- **Coarse surface until SP2:** at Earth scale, `MAX_DEPTH=8` ⇒ ~1 km vertex spacing. Expected; SP1 is the spine, validated by tests, not by eyeballing the ground.
- **Two floating-origin notions:** SP1 keeps `CoordinateManager` (galaxy↔local) as the rebase authority and derives `originPlanetKm` from its events. Full galaxy↔planet anchor unification (LY/AU/km chain) is only needed for SP3; SP1 keeps the planet self-contained in planet-space km.
