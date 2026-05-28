# Asset & Animation Pipeline Design

**Date:** 2026-05-27
**Status:** Approved for implementation

## Context

The project currently has no external 3D model support. Everything rendered today — ships, cockpit, terrain — is procedurally generated geometry built in-code via libGDX's `ModelBuilder`. Characters have an animation system scaffolded (`PlayerAnimationSystem`, `PlayerModelComponent` with `AnimationController`) but no model to animate. Referenced particle textures don't exist (`particles/smoke.png` is missing). `gdx-gltf` is mentioned in the design doc but is not in Gradle dependencies and has no integration code.

This spec designs the full asset and animation pipeline that fills this gap: streaming glTF model loading, a full blend tree animation system, LOD with three tiers, GPU-instanced foliage, prop placement for interiors, shader material variants for faction/damage/emissive, and portal-based occlusion culling for dense ship interiors.

**Decisions locked in:**
- **Hybrid sourcing:** Ships remain procedurally generated. Everything else (characters, props, foliage, buildings) uses imported `.glTF` models.
- **Art style:** Stylized/vibrant (No Man's Sky, Outer Wilds). Bold colour over photorealistic grime. Moderate shader complexity — no heavy PBR wear maps.
- **Animation:** Full parametric blend trees, not simple clip switching.

---

## 1. Streaming Asset Manager

### Overview
`GalacticAssetManager` wraps libGDX's `AssetManager` with category-typed handles, a priority-queue background loader, and distance-based prefetch/unload. No synchronous model loads ever happen on the GL thread.

### AssetCategory
```java
public enum AssetCategory {
    CHARACTER, PROP_SMALL, PROP_LARGE, INTERIOR_PROP,
    FOLIAGE, BUILDING, VFX_MESH, TEXTURE_ATLAS
}
```
Each category has a memory budget and eviction policy in `data/assets/asset_budgets.json`.

### AssetHandle\<T\>
Reference-counted typed handle. `retain()` / `release()` manage the count; count reaching zero queues the asset for eviction. Consumers never hold raw references to loaded objects — only handles.

### StreamingQueue
`PriorityQueue<StreamRequest>` ordered by `(1.0 / distanceToCamera) * categoryPriority`. Evaluated once per frame by `GalacticAssetManager.update()` dispatching work to libGDX's `AsyncExecutor`. GL-thread finalisation (texture upload, VAO creation) happens at end-of-frame in the main `update()` call — same pattern as libGDX's built-in `AssetManager`.

### StreamingSystem (Ashley)
Runs every frame. For each entity with `StreamableComponent` (asset ID + category):
- Distance < prefetch radius → `manager.enqueue(handle)` if not resident
- Distance > unload radius → `handle.release()`

Radii are per-category in `data/assets/streaming_config.json`.

### Content manifests
One JSON file per category under `core/src/main/resources/data/assets/`:

```
characters.json   props.json   foliage.json   buildings.json   vfx_meshes.json
```

Each entry: `id`, `path` (glTF), `lod_mid` (reduced-poly glTF), `lod_far` (billboard glTF or sprite region), `memory_tier`.

### New files
| File | Purpose |
|---|---|
| `core/.../data/GalacticAssetManager.java` | Central loader |
| `core/.../data/AssetCategory.java` | Category enum |
| `core/.../data/AssetHandle.java` | Ref-counted handle |
| `core/.../data/StreamingQueue.java` | Priority queue logic |
| `core/.../data/systems/StreamingSystem.java` | Ashley system |
| `core/.../data/components/StreamableComponent.java` | Entity component |
| `core/src/main/resources/data/assets/*.json` | Content manifests |

---

## 2. Blend Tree Animation System

### Overview
Full parametric blend trees that manually sample multiple animation clips per frame and blend their node transforms. Bypasses libGDX's `AnimationController` for blend logic (which supports only one active clip + crossfade) while still using libGDX's `Animation` clip sampling infrastructure.

### Node types
| Node | Description |
|---|---|
| `AnimationBlendTree` | Root container. Holds named float/bool parameters. Evaluates tree each frame, writes final pose to `ModelInstance` skeleton. |
| `StateMachineNode` | Routes to one sub-tree based on boolean params (grounded / airborne / swimming) |
| `Blend2DNode` | Bilinear blend across a 2D parameter space (e.g. `moveSpeed` × `strafeX` → idle / walk_fwd / walk_left / run_fwd) |
| `Blend1DNode` | Linear blend along one axis (e.g. `verticalVelocity` → jump_rise / jump_fall) |
| `ClipNode` | Leaf. Samples an `Animation` at its local time, returns a pose (map of bone name → Transform) |
| `AdditiveNode` | Applies additive delta layers on top of a base tree result |

### Additive layers (applied in order)
1. **AimOffset** — `aimPitch` uniform tilts the upper body spine bones
2. **ProceduralBreathing** — sine-wave on chest/spine bones when `moveSpeed < 0.1`
3. **FootIK** — per-frame downward raycast from each ankle, two-bone IK solve for ankle + knee
4. **HandIK** — weapon grip or surface contact point solve
5. **RecoilLayer** — impulse event → exponentially decaying additive delta on spine

### Float parameters (written by ECS each frame)
`moveSpeed`, `strafeX`, `verticalVelocity`, `aimPitch`, `swimSpeed`, `airborne` (bool), `crouching` (bool), `swimming` (bool)

### Evaluation loop (CharacterAnimationSystem)
1. Write parameters from `PhysicsBodyComponent`, `PlayerInputComponent`, camera system
2. `blendTree.evaluate(deltaTime)` — traverses nodes, computes weights, samples clips in parallel
3. Blend: lerp position, slerp rotation for each named bone
4. Apply additive deltas
5. Write final transforms to `ModelInstance.nodes`

### One-shot animations
Death, interact, and emote use libGDX `AnimationController.animate()` directly — no blend tree overhead for non-blended playback.

### New files
| File | Purpose |
|---|---|
| `core/.../player/animation/AnimationBlendTree.java` | Root + parameter store |
| `core/.../player/animation/BlendNode.java` + subclasses | Node implementations |
| `core/.../player/animation/AdditiveLayer.java` + subclasses | Additive overlay layers |
| `core/.../player/systems/CharacterAnimationSystem.java` | Ashley system (replaces/extends PlayerAnimationSystem) |
| `core/.../player/components/AnimatedModelComponent.java` | Component for any animated character entity (player or NPC) — holds ModelInstance + BlendTree ref |

---

## 3. LOD + Rendering Systems

### LOD tiers
| Tier | Detail |
|---|---|
| T0 — Near | Full mesh + full material + shadows + animation |
| T1 — Mid | ~50% poly + simplified material, no animation |
| T2 — Far | Camera-facing billboard quad, faction-tinted |
| Culled | Not submitted to render batch |

### Per-category distance thresholds (JSON-configurable, with hysteresis)
| Category | T0→T1 | T1→T2 | Cull |
|---|---|---|---|
| Characters | 15 m | 50 m | 120 m |
| Interior Props | 8 m | 25 m (→cull, no T2) | 25 m |
| Foliage | 20 m | 60 m | 150 m |
| Buildings | 100 m | 400 m | 1000 m |

Hysteresis: upgrade threshold is ~20% tighter than downgrade threshold (e.g. upgrade to T0 at 12 m, downgrade back at 18 m) to prevent popping when the player lingers at a boundary.

### LODSystem (Ashley)
Runs every 3 frames (distance rarely changes fast enough to matter). For each entity with `LODComponent`: compute camera distance, determine target tier, swap `ModelInstance` via `AssetHandle` if tier changed.

### Foliage — GPU Instancing
`FoliageRenderSystem`:
- World divided into tiles (32 × 32 m default)
- Each `FoliageTile` holds an instance buffer (`Matrix4[]`) per foliage type present in the tile
- One `glDrawArraysInstanced` call per foliage-type per visible tile
- Wind: vertex shader applies `sin(worldPos.x * freq + u_time) * amplitude * vertex.y` — tips move, trunks don't
- Tile AABB frustum-culled before submission; tiles outside render radius skipped entirely

### Props & Interior Objects — Pooled + Selective Instancing
- **`PropCatalog`** JSON registry: `id`, `meshPath`, `lodMidPath`, `lodFarSprite`, category tags (`SEATING`, `CONSOLE`, `DECORATION`, `STORAGE`, `TECH`)
- **`PropPlacementSystem`** populates rooms at interior-enter time using room archetype + tag filter from `PropCatalog`
- **`ModelInstance` pooling** via libGDX `Pool<ModelInstance>` — reused when props enter/leave visibility
- High-repetition props (chairs, crates): GPU-instanced batch. Hero props (unique consoles, captain's chair): individual `ModelInstance`

### Buildings — Modular Assembly + Static Bake
- Each panel (wall section, floor, roof, doorframe, window) is a glTF model with snap-point metadata (attachment position + orientation)
- **`BuildingAssembler`** reads a building definition JSON, instantiates panels connected via snap points, then merges all into one static `ModelInstance` per building (one draw call)
- Procedural variation: swap panel variants per seed — different wall styles, window counts, roof shapes

### New files
| File | Purpose |
|---|---|
| `core/.../rendering/LODSystem.java` | Ashley LOD tier updater |
| `core/.../rendering/components/LODComponent.java` | Tier state per entity |
| `core/.../rendering/FoliageRenderSystem.java` | GPU instanced foliage |
| `core/.../rendering/FoliageTile.java` | Per-tile instance buffer |
| `core/.../rendering/PropCatalog.java` | JSON prop registry |
| `core/.../rendering/PropPlacementSystem.java` | Interior prop placement |
| `core/.../rendering/BuildingAssembler.java` | Modular building mesh merger |
| `core/src/main/resources/data/assets/lod_config.json` | Distance thresholds |

---

## 4. Particle Effects Upgrade

### Current state
`ParticleRenderSystem` (DecalBatch billboard rendering) works. `ParticleEffectDefinition` JSON configs exist. All referenced textures are missing. This upgrade populates the textures and adds a second emitter type.

### Texture atlas
Single `TextureAtlas` at `particles/atlas.png` + `particles/atlas.atlas`. Regions: `smoke`, `flame`, `glow`, `flash`, `shockwave`, `dust`, `droplet`, `debris_soft`. Loaded as `TEXTURE_ATLAS` category via `GalacticAssetManager`. All billboard emitters reference regions by name in JSON.

### Two emitter types
**`BillboardEmitter`** — existing approach, upgraded with real atlas regions and GPU depth sorting.

**`MeshParticleEmitter`** — spawns small 3D mesh instances (`spark_line`, `shell_casing`, `debris_chunk`, `glass_shard`). Each particle is a `ModelInstance` with position, velocity, gravity, and bounce simulated each frame. Rendered via `ModelBatch` with depth write enabled (no sort needed).

### GPU depth sorting (billboard only)
Each frame: collect all billboard particles from all active `BillboardEmitter` instances into one array, sort back-to-front by camera distance (`Arrays.sort()` with distance comparator), flush in one `DecalBatch.flush()` call.

### JSON format upgrade (backwards compatible)
```json
{
  "type": "BILLBOARD",
  "sprite": "flame",
  "blendMode": "ADDITIVE",
  "maxParticles": 200,
  "emitRate": 60,
  "lifetime": { "min": 0.3, "max": 0.8 },
  "size": { "start": 0.4, "end": 0.0, "curve": "EASE_OUT" },
  "color": { "start": "#ff8800ff", "end": "#ff220000" }
}
```
```json
{
  "type": "MESH",
  "mesh": "spark_line",
  "maxParticles": 50,
  "emitOnce": true,
  "speed": { "min": 3.0, "max": 12.0 },
  "lifetime": { "min": 0.2, "max": 0.6 },
  "gravity": -9.8,
  "bounce": 0.3
}
```

### New/modified files
| File | Purpose |
|---|---|
| `core/src/main/resources/particles/atlas.png` + `atlas.atlas` | Sprite sheet (new art asset) |
| `core/.../vfx/MeshParticleEmitter.java` | New mesh emitter |
| `core/.../vfx/systems/ParticleRenderSystem.java` | Upgrade: depth sort + two emitter paths |
| `core/src/main/resources/data/vfx/*.json` | Add `"type"` and correct `"sprite"` fields |

---

## 5. Shader Material Variants

### Overview
`GalacticShaderProvider` extends libGDX `DefaultShaderProvider`. Selects and caches shader programs per renderable based on feature flags. Three variant axes are all driven by uniforms — no model reload needed when faction or damage state changes.

### MaterialVariantComponent (Ashley)
```java
public class MaterialVariantComponent implements Component {
    public Vector3 factionColor = new Vector3(1, 1, 1); // RGB
    public int damageState = 0;       // 0=pristine, 1=worn, 2=damaged, 3=critical
    public float emissiveScale = 1f;  // 0=off, 1=normal, 2=alert
    public int featureFlags;          // HAS_FACTION_TINT | HAS_DAMAGE | HAS_EMISSIVE
}
```
Updated via the event bus on change: `FactionSystem` sets `factionColor` on faction assignment; `HealthSystem` updates `damageState` on health threshold crossings; `PowerSystem` updates `emissiveScale` on power-state events. Not written every frame — only when values change.

### Shader families
| Shader | Used for | Variant flags |
|---|---|---|
| `GalacticCharacterShader` | Characters, NPC crew | Faction tint + damage + emissive |
| `GalacticPropShader` | Props, interior objects | Optional faction tint + damage |
| `GalacticFoliageShader` | Foliage | Wind + optional tint, no damage |
| `GalacticBuildingShader` | Buildings | Faction tint + damage + emissive windows |

Shader programs compiled once and pooled per feature-flag combination. A prop without emissive never pays for that shader path.

### Damage state (stylized — no extra texture variants)
The `u_damageState` uniform (0–3) drives:
- **0:** base albedo, full saturation
- **1:** desaturate 40%, add tiled scratch noise
- **2:** desaturate 75%, sample burn overlay mask, darken
- **3:** full desaturation, sample crack mask, crack emissive colour, animated sparking via `u_time`

All in the fragment shader — no separate texture sets per damage tier.

### New files
| File | Purpose |
|---|---|
| `core/.../rendering/GalacticShaderProvider.java` | Shader selector + cache |
| `core/.../rendering/shaders/GalacticCharacterShader.java` + `.vert`/`.frag` | Character shader |
| `core/.../rendering/shaders/GalacticPropShader.java` + `.vert`/`.frag` | Prop shader |
| `core/.../rendering/shaders/GalacticFoliageShader.java` + `.vert`/`.frag` | Foliage wind shader |
| `core/.../rendering/shaders/GalacticBuildingShader.java` + `.vert`/`.frag` | Building shader |
| `core/.../rendering/components/MaterialVariantComponent.java` | Variant data component |

---

## 6. Occlusion Culling — Portal + Room System

### Overview
Interior-only. Portal BFS culling prevents rendering props in rooms hidden behind closed walls. Activates when the player is inside a ship or station; deactivates in exterior scenes where `ModelBatch` frustum culling and streaming radius are sufficient.

### Data structures
- **`RoomGraph`** — stored in `ShipInteriorComponent`. Map of room ID → `Room`.
- **`Room`** — AABB, list of `Portal` references, set of entity IDs belonging to this room.
- **`Portal`** — rectangle (4 corner points) connecting Room A ↔ Room B. `boolean open` toggled by door events.

### PortalCullingSystem (Ashley)
Each frame:
1. Point-in-AABB test across rooms → camera's current room
2. BFS from camera room: for each open portal, test portal rect against camera frustum (progressively clip frustum planes to each portal as BFS deepens); enqueue adjacent room if visible
3. Collect all reached rooms into `Set<Integer> visibleRoomIds`
4. `PropRenderSystem` and `CharacterRenderSystem` skip any entity whose `RoomMemberComponent.roomId` is absent from the visible set

### Integration points
- **`ShipInteriorGenerator`** — modified to record doorframe positions as `Portal` objects and store the `RoomGraph` in `ShipInteriorComponent` at generation time
- **`PropPlacementSystem`** — assigns `RoomMemberComponent` to each placed prop at placement time
- **Door events** — toggle `portal.open`; a closed door blocks BFS, immediately culling the room behind it
- **Character movement** — crossing a portal rect triggers `RoomMemberComponent.roomId` update

### Exterior scenes
System deactivated. `ModelBatch` frustum culls per-renderable. `FoliageTile` AABB culling covers large outdoor areas. `StreamingSystem` load radius culls anything beyond render distance before it enters memory.

### New files
| File | Purpose |
|---|---|
| `core/.../rendering/PortalCullingSystem.java` | BFS portal traversal |
| `core/.../rendering/RoomGraph.java` | Room + portal data structure |
| `core/.../rendering/components/RoomMemberComponent.java` | Room membership per entity |
| `core/.../ship/systems/ShipInteriorGenerator.java` | Modified to emit Portal objects |

---

## Implementation Order

Build in this sequence — each phase unblocks the next:

| Phase | Systems | Why first |
|---|---|---|
| 1 | `GalacticAssetManager` + gdx-gltf Gradle dep | Foundation everything else loads through |
| 2 | Particle atlas + `MeshParticleEmitter` | Fixes missing textures; uses asset manager |
| 3 | `MaterialVariantComponent` + `GalacticShaderProvider` | Required by all subsequent rendered entities |
| 4 | `LODSystem` + `PropCatalog` + `PropPlacementSystem` | Gets props into interiors |
| 5 | `AnimationBlendTree` + `CharacterAnimationSystem` | Requires a loaded character glTF (Phase 1) |
| 6 | `FoliageRenderSystem` + `BuildingAssembler` | Planetary surfaces |
| 7 | `PortalCullingSystem` | Performance optimisation once props are in place |

---

## Verification

1. **Asset loading** — Load a glTF character in a test scene. Confirm async load (no frame hitch on spawn), correct position, LOD tier swap as camera moves away.
2. **Blend tree** — Walk the player. Confirm smooth directional locomotion blend, foot IK adjusting on uneven terrain, aim offset tracking camera pitch.
3. **Particles** — Trigger `engine_exhaust` and `impact_sparks`. Confirm atlas textures appear, additive blend on exhaust, sparks bounce with gravity.
4. **Shader variants** — Assign different `factionColor` values to one character model; confirm accent changes without reload. Advance `damageState` 0→3; confirm visual progression with crack emissive at state 3.
5. **LOD** — Place 20 characters at increasing distances. Confirm tier transitions with no popping at thresholds.
6. **Occlusion** — Enter a ship interior. Enable a debug outline of all submitted meshes. Confirm rooms behind closed walls contribute zero draw calls. Open a door — room appears immediately.
