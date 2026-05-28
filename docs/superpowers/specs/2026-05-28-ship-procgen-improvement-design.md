# Ship Procgen Improvement — Design Spec

**Date:** 2026-05-28
**Status:** Draft (awaiting review)
**Scope:** Three additive improvements to procedural ship generation:
1. **Faction visual language** — data-driven silhouette archetypes so a faction's ships share recognizable traits (angular / organic / insectoid / utilitarian / sleek).
2. **Component visibility** — external modules (turrets, missile pods, sensors, cargo pods) rendered on the hull based on the ship's loadout.
3. **Damage/wear states** — generation-time paint chipping, scorch, hull patches, and breaches for derelicts and aged ships.

**Approach:** CPU-side, deterministic, GL-free generation layered on the existing
spline-hull pipeline. Each layer is independently unit-testable (architectural
rule #5). Implemented in three phases.

---

## 1. Background — current pipeline

`ShipFactory.createShip(seed, sizeClass, x, y, z)` drives generation:

```
seed,sizeClass → ShipBlueprint → ShipHullGenerator → HullGeometry
                                                    → ShipInteriorGenerator
              → assemble Entity (ShipDataComponent, ShipMeshComponent,
                ShipInteriorComponent, ShipFlightComponent, PhysicsBodyComponent)
```

Key facts that shape this design:

- **Hull geometry** is a lofted triangle mesh: a cubic-Bezier `SpineCurve`, a set
  of superellipse `CrossSection`s (`|x/a|^n + |y/b|^n = 1`; `n≈2` round, `n>2`
  boxy, `n<2` diamond), interpolated rings oriented by a Frenet frame, plus swept
  wings and engine nacelles. `ShipHullGenerator.VERTEX_STRIDE = 11`:
  `pos[3] | normal[3] | color RGBA[4] | emissive[1]`.
- **Color is baked per-vertex** — there is no UV mapping or texture. Colors come
  from `ShipColorPalette` (hardcoded base/accent arrays today). The
  deferred-PBR pipeline (`2026-05-28-deferred-pbr-rendering-pipeline-design.md`)
  explicitly supports a **Vertex Color PBR** material variant for procgen ships:
  vertex color → albedo, the emissive attribute → bloom. **Wear that modulates
  vertex color therefore modulates albedo for free** under both the current
  forward renderer and the incoming deferred renderer.
- **Hardpoints** today: `ShipHullGenerator` emits a single placeholder hardpoint
  at `spine.evaluate(0.4f)`. The real loadout data model lives in the outfitter
  spec (`2026-05-28-ship-outfitter-loadout-design.md`): weapon `Hardpoint` /
  `HardpointTemplate`, and `ShipModuleSlot` / `ShipModuleData` /
  `ShipModuleCategory`. Those carry positions but are **not procedurally placed on
  the generated surface** yet.
- **Factions** (`FactionData`, `FactionEthos` ∈ {CORPORATE, MILITARIST,
  ISOLATIONIST, FEDERATION, PIRATE_SYNDICATE}) currently have **zero** influence
  on ship appearance.
- **Mesh upload is deferred** to GL context in `GameScreen`
  (`ShipMeshComponent.hullMesh` starts null); generation itself is GL-free.

---

## 2. Architecture & data flow

### 2.1 Generation config

Introduce `ShipGenerationConfig` to carry context the current signature lacks:

| Field | Type | Notes |
|---|---|---|
| `seed` | long | base seed |
| `sizeClass` | ShipSizeClass | as today |
| `faction` | FactionData (nullable) | null → neutral/independent |
| `role` | ShipRole | WARSHIP, MERCHANT, PIRATE, SCOUT, CIVILIAN (drives loadout fill) |
| `conditionFactor` | float 0–1 | 1 = pristine, 0 = derelict (drives wear) |
| `isFlagship` | boolean | fuller loadout |

`ShipFactory` gains `createShip(ShipGenerationConfig, x, y, z)`. The existing
`createShip(seed, sizeClass, x, y, z)` is kept as a thin overload that builds a
**default config** (neutral style, condition 1.0, no loadout) so current callers
and tests are unaffected.

### 2.2 Sub-seed derivation

Per the seed-reproducibility rule, replace the ad-hoc `seed` / `seed + 1` usage
with explicit per-layer sub-seeds derived from the base seed:

```
long hullSeed       = mix(seed, LAYER_HULL);
long componentSeed  = mix(seed, LAYER_COMPONENTS);
long wearSeed       = mix(seed, LAYER_WEAR);
```

`mix` is a small fixed integer hash (e.g. SplitMix64-style finalizer). Each layer
uses its own `java.util.Random`; no shared/global RNG. Same config → identical
ship, byte for byte.

### 2.3 Revised pipeline

```
ShipGenerationConfig
   │
   ├─ resolve HullStyle            ← faction.styleId (default from ethos),
   │                                 loaded from hull_styles.json
   │
   ├─ ShipBlueprint(style, hullSeed)        // style biases param ranges
   ├─ ShipHullGenerator(style)              // → HullGeometry + placed sockets
   │
   ├─ LoadoutGenerator(config)              // which sockets filled, with what
   ├─ ComponentGenerator(loadout, compSeed) // per filled socket → sub-mesh + xform
   │
   ├─ WearProcessor(condition, wearSeed)    // modulate vertex colors;
   │                                          append patch/scorch/breach geometry
   │                                          (hull + component sub-meshes)
   │
   └─ assemble Entity:
        ShipMeshComponent               (hull mesh, wear baked in)
        ShipExternalModulesComponent    (component sub-meshes + local xforms + refs)
        ShipDataComponent / interior / flight / physics  (as today)
```

New data files under `core/src/main/resources/data/ships/`:
`hull_styles.json` (archetypes) and `ethos_style_map.json` (ethos → default
styleId). Existing `hardpoint_templates.json` is reused for socket layouts.

### 2.4 Phases

- **Phase 1 — Faction visual language** (foundational; changes core generation).
- **Phase 2 — Component visibility** (consumes hull + loadout model).
- **Phase 3 — Damage/wear** (post-process over assembled geometry).

Each phase is independently testable and shippable.

---

## 3. Phase 1 — Faction visual language

### 3.1 `HullStyle` (data-driven archetype)

A data class loaded from `hull_styles.json`. It does **not** introduce new
geometry math — it constrains the random ranges the generator already samples,
plus supplies the palette.

| Lever | Existing code it controls | angular | organic | insectoid |
|---|---|---|---|---|
| `spineCurvature` | `generateSpine` ctrl-pt offsets (`xVar`,`yVar`) | low (straight) | high (curved) | medium, asymmetric |
| `sectionExponentRange` | `generateCrossSections` exp (now 2.2–4.0) | 3.5–4.5 boxy | 1.8–2.4 round | 1.4–1.9 diamond |
| `aspectBiasRange` | `aspectBias` (now 0.7–1.3) | wide/flat | balanced | narrow/segmented |
| `wingStyle` | `buildWingVerts` span/sweep/chord | hard swept | smooth/short | many, thin, high-sweep |
| `nacelleStyle` | `enginePodCount`, `podRadius` | blocky | faired/blended | multiple small |
| `panelStyle` | `PANEL_INSET`, inset frequency | deep/frequent | shallow/sparse | fine segmentation |
| `palette` | replaces `ShipColorPalette` constants | per-archetype base/accent sets |

Five archetypes ship initially: **angular, organic, insectoid, utilitarian,
sleek**. The JSON is the source of truth; adding a sixth archetype is a data edit.

> Note: `aspectBiasRange` and segmentation are within reach of the current
> generator (aspect bias and per-section variance already exist). True
> multi-segment "insectoid" bodies (gaps between body segments) are approximated
> via cross-section pinching and exponent, not new topology — keeps Phase 1 a
> parameterization change, not a rewrite. Deeper insectoid topology is future work.

### 3.2 Faction tie-in

- `FactionData` gains a `styleId` field (string). If blank, it resolves via
  `ethos_style_map.json` (default mapping: MILITARIST→angular, CORPORATE→sleek,
  ISOLATIONIST→utilitarian, FEDERATION→organic, PIRATE_SYNDICATE→insectoid).
- The faction's `mapColor` is blended into the style's base/accent palette with
  **±15% per-ship variance** (livery pattern from the ship-generation skill): ships
  read as "that faction" while staying individually varied. A null faction uses the
  style's own palette with neutral/weathered variance.

### 3.3 Code changes

- `ShipColorPalette` takes a `HullStyle` (palette source + faction color) instead
  of hardcoded arrays.
- `ShipBlueprint` and `ShipHullGenerator` take the resolved `HullStyle` and pull
  ranges from it rather than literals.
- New: `HullStyle`, `HullStyleRegistry` (loads + caches JSON), `ShipRole` enum
  (new — WARSHIP, MERCHANT, PIRATE, SCOUT, CIVILIAN).

### 3.4 Tests

- Same `(style, seed)` → identical `HullGeometry` (determinism).
- Different styles produce measurably different silhouettes: assert on mean
  cross-section exponent, a spine-curvature metric (max deviation of control
  points), and wing count, across styles.
- Faction `mapColor` is detectably present in the resulting palette.
- Every `FactionEthos` resolves to a valid, loadable style.
- `hull_styles.json` parses; all five archetypes present and complete.

---

## 4. Phase 2 — Component visibility

### 4.1 Socket placement on the generated hull

Replace the single placeholder hardpoint with proper placement. A `HullSocket`
records the data needed to position external geometry **and** to feed the
outfitter/loadout model:

| Field | Type | Notes |
|---|---|---|
| `id` | String | stable per ship (e.g. `dorsal_0`) |
| `position` | Vector3 | point on the generated hull surface (local space) |
| `normal` | Vector3 | outward surface normal at that point |
| `forward` | Vector3 | mount facing (default = ship forward; turrets aim from here) |
| `socketType` | SocketType | TURRET, POINT_DEFENCE, MISSILE, SENSOR, CARGO_POD |
| `size` | HardpointSize | reuses existing enum (SMALL…CAPITAL) |

Placement: sockets are distributed over recognizable regions (dorsal/ventral
spine line, wing tips, nose, flanks) by sampling the already-computed hull rings,
so positions are guaranteed to sit on the actual surface (not a static template
guess). Count and type mix scale with `ShipSizeClass` and are biased by
`HullStyle` and `role` (militarist → more weapon sockets; freighter/merchant →
more cargo-pod sockets). Where `hardpoint_templates.json` defines a layout for the
class, those relative positions are honored and snapped to the nearest surface
point; otherwise sockets are derived procedurally.

**Alignment with the outfitter spec:** `HullSocket` is the *3D placement source*
for the existing loadout model. Weapon sockets become weapon `Hardpoint`s; module
sockets back the externally-visible subset of `ShipModuleSlot`s. We do not
duplicate the outfitter's data model — Phase 2 supplies valid 3D mount points it
previously lacked, and renders geometry for occupied ones.

### 4.2 Loadout determination

`LoadoutGenerator(config)` decides which sockets are filled and with what, driven
by `role` / `faction` / techLevel / `isFlagship` (merchants leave offensive
sockets often empty; flagships fill more). Output: a list of
`(socket, componentType, size)`. Deterministic from `componentSeed`.

Mapping from loadout to visible geometry:

| Source | Visible component |
|---|---|
| Weapon Hardpoint (ballistic/energy) | turret |
| Weapon Hardpoint (missile) | missile pod |
| Weapon Hardpoint (point-defence) | PD dome |
| `ShipModuleCategory.SCANNER` | sensor dish/antenna |
| `ShipModuleCategory.CARGO_EXPANDER` | cargo pod |
| `MINING_LASER` / `TRACTOR_BEAM` | emitter boom |
| internal modules (REACTOR, SHIELD_GENERATOR, …) | none (internal) |

### 4.3 Component meshes

Procedural primitives, one small mesh per filled socket, in the hull palette
(stride-11 to match the hull, so wear can post-process them uniformly):

- **Turret** — cylindrical base + boxy barrel housing (barrel along `forward`);
  references the weapon `Hardpoint` so it can rotate to aim later.
- **Point-defence** — small dome + thin barrels.
- **Missile pod** — clustered box of tubes.
- **Sensor** — dish or antenna array.
- **Cargo pod** — rounded box clamped to the hull flank.
- **Emitter boom** — short strut + emitter tip.

### 4.4 Attachment & rendering

Each component becomes a `ShipComponentAttachment`:

| Field | Type |
|---|---|
| `mesh` | Mesh (uploaded deferred, like the hull) |
| `localTransform` | Matrix4 (from socket position + normal + forward) |
| `hardpointRef` | Hardpoint (nullable; set for turrets) |
| `componentType` | enum |

Collected on a new `ShipExternalModulesComponent` (implements `Disposable`;
disposes all sub-meshes). The render system draws each as
`shipWorldTransform × localTransform`. Separate sub-meshes (vs. merging) so
turrets can aim and modules can later be individually destroyed/swapped.

Mesh **upload** is deferred to GL context in `GameScreen` exactly as the hull mesh
is today, now extended to also upload component sub-meshes.

### 4.5 Migration

Grep for `HullGeometry.hardpoints` usage and migrate any consumer to the new
socket list. (Expected to be minimal — currently a single placeholder.)

### 4.6 Tests

- Loadout deterministic from `componentSeed`.
- Socket count scales monotonically with `ShipSizeClass`.
- Merchant role yields strictly fewer filled offensive sockets than warship for
  the same hull.
- Each generated component mesh is non-empty and stride-valid.
- Attachment transforms place each component on/just outside the hull surface
  (distance from hull center ≥ local surface radius along the socket normal; not
  buried inside the bounding volume).
- `#attachments == #filled sockets`.

---

## 5. Phase 3 — Damage/wear states

Generation-time only (no live battle-damage mesh churn). Driven by
`conditionFactor`.

### 5.1 `WearProfile`

Derived from `conditionFactor` + `wearSeed`; every field scales with severity
`s = 1 − conditionFactor`:

| Field | Meaning |
|---|---|
| `chipAmount` | per-vertex paint chip / discoloration intensity |
| `scorchAmount` | darkening around scorch centers |
| `discoloration` | global desaturation/fade |
| `patchCount` | number of welded replacement plates |
| `breachCount` | number of hull breaches (severe only) |

At `conditionFactor == 1.0` every field is **zero** → output arrays are identical
to input (verified no-op).

### 5.2 `WearProcessor`

Pure function over the assembled `float[]` vertices / `short[]` indices of the
hull **and** each component sub-mesh — no GL, fully unit-testable. Because vertex
color is albedo in the PBR variant, all of this is albedo modulation:

- **Chipping & discoloration** — sample 3D value-noise at each vertex's local
  position; where it crosses a `chipAmount`-scaled threshold, darken/desaturate
  the vertex color. Forward-facing vertices (derived from the existing normal
  attribute) wear more (leading-edge erosion).
- **Scorch** — choose N scorch centers, biased toward the emissive
  engine/weapon vertices (already flagged via the emissive attribute); darken
  surrounding vertices with radial falloff scaled by `scorchAmount`.
- **Hull patches** — append quad geometry: welded replacement plates in an
  **off-palette** mismatched grey/primer color, placed over random hull regions
  and offset just above the surface along the local normal. Patches carry the
  fine, readable "patched derelict" detail that coarse per-vertex scorch cannot.
- **Breaches** (only when `breachCount > 0`) — append darkened, inset jagged rim
  quads suggesting a hole. Deliberately simple.

The emissive attribute (stride index 10) is **preserved** so engine/window glow
survives wear; only color channels are modulated for non-emissive vertices.

### 5.3 Known tradeoffs (documented, not bugs)

- Per-vertex scorch resolution is bounded by mesh density
  (`RING_VERTEX_COUNT = 24`). Appended patch/breach geometry carries fine detail.
- Roughness is not varied per-vertex (no per-vertex roughness attribute today;
  the PBR variant uses uniform defaults). Burnt-surface roughness is future work
  if a roughness attribute is added.

### 5.4 Tests

- `conditionFactor == 1.0` → output arrays byte-identical to input (no-op).
- Lower condition → strictly more darkened vertices **and** more appended patch
  vertices.
- Deterministic from `(wearSeed, conditionFactor)`.
- Patches use an off-palette color (distinguishable from base/accent).
- Processor never corrupts vertex stride or produces out-of-range indices.

---

## 6. Testing, integration & isolation

Each layer is a standalone unit with a clear contract, testable without a GL
context (architectural rule #5):

| Unit | Input → Output | GL-free test |
|---|---|---|
| `HullStyleRegistry` | JSON → styles | parse, completeness, ethos resolution |
| `ShipHullGenerator(style)` | blueprint → geometry + sockets | determinism, style differentiation |
| `LoadoutGenerator` | config → loadout list | determinism, role bias |
| `ComponentGenerator` | loadout + sockets → attachments | mesh validity, placement |
| `WearProcessor` | arrays + profile → arrays | no-op at 1.0, monotonicity, integrity |

**Integration seam.** `ShipFactory` orchestrates the four layers and assembles the
entity. Only mesh *upload* stays deferred to GL context in `GameScreen` (hull +
component sub-meshes). `ShipExternalModulesComponent` and all new meshes implement
`Disposable`.

**Backward compatibility.** `createShip(seed, sizeClass, x, y, z)` and current
tests keep working via the default-config overload (neutral style, condition 1.0,
no components).

**Render integration.** The scene render path draws each
`ShipComponentAttachment` at `shipTransform × localTransform`, using the same
material/shader path as the hull (vertex-color PBR variant).

---

## 7. New/changed files (anticipated)

**New (Java, `core/src/main/java/com/galacticodyssey/ship/`):**
- `ShipGenerationConfig`, `ShipRole`
- `HullStyle`, `HullStyleRegistry`
- `HullSocket`, `SocketType`
- `LoadoutGenerator`, `ComponentGenerator`, `ShipComponentAttachment`
- `components/ShipExternalModulesComponent`
- `WearProfile`, `WearProcessor`

**Changed:**
- `ShipFactory` (config + orchestration), `ShipBlueprint`, `ShipHullGenerator`,
  `ShipColorPalette`, `HullGeometry` (sockets[] replaces hardpoints[]),
  `FactionData` (+`styleId`), `GameScreen` (upload component meshes).

**New data (`core/src/main/resources/data/ships/`):**
- `hull_styles.json`, `ethos_style_map.json`

**Tests (`core/src/test/java/com/galacticodyssey/ship/`):**
- `HullStyleRegistryTest`, `ShipHullGeneratorStyleTest`, `LoadoutGeneratorTest`,
  `ComponentGeneratorTest`, `WearProcessorTest`, plus additions to
  `ShipHullGeneratorTest` / `ShipFactoryTest`.

---

## 8. Out of scope

- Live battle-damage mesh updates (HP-driven scorch during combat).
- Shader-based procedural wear / per-vertex roughness.
- Turret aiming/animation gameplay (geometry references the Hardpoint so it is
  *ready* for it; the behavior itself is separate).
- True multi-segment insectoid topology (approximated via cross-section shaping).
- Texture/UV-mapped PBR materials for procgen hulls (outfitter cosmetics, future).
