# Ship Procgen Improvement — Design Spec

**Date:** 2026-05-28
**Status:** Draft (awaiting review)
**Scope:** Three additive improvements to procedural ship generation:
1. **Faction visual language** — data-driven, lore-faithful styles so each faction's
   ships share a recognizable silhouette, palette, and set of signature features.
2. **Component visibility** — external modules (turrets, missile pods, sensors,
   cargo pods/crates, etc.) rendered on the hull based on the ship's loadout.
3. **Damage/wear states** — generation-time paint chipping, scorch, hull patches,
   and breaches for derelicts and aged ships.

**Source material:** Faction identities and art direction are taken from
`factionOverview.txt` (lore bible) and `factionShipDesigns.txt` (ship design
language) in the project root.

**Approach:** CPU-side, deterministic, GL-free generation layered on the existing
pipeline. Two hull generation paths (lofted + a new faceted path for exotic
factions). Each layer is independently unit-testable (architectural rule #5).
Implemented in three phases.

---

## 1. Background — current pipeline & faction state

`ShipFactory.createShip(seed, sizeClass, x, y, z)` drives generation:

```
seed,sizeClass → ShipBlueprint → ShipHullGenerator → HullGeometry
                                                    → ShipInteriorGenerator
              → assemble Entity (ShipDataComponent, ShipMeshComponent, …)
```

Facts that shape this design:

- **Hull geometry** is a lofted triangle mesh: cubic-Bezier `SpineCurve`,
  superellipse `CrossSection`s (`|x/a|^n + |y/b|^n = 1`; `n≈2` round, `n>2` boxy,
  `n<2` diamond), Frenet-framed interpolated rings, swept wings, engine nacelles.
  `VERTEX_STRIDE = 11`: `pos[3] | normal[3] | color RGBA[4] | emissive[1]`.
- **Color is baked per-vertex** — no UV/textures. The deferred-PBR pipeline
  (`2026-05-28-deferred-pbr-rendering-pipeline-design.md`) supports a **Vertex
  Color PBR** variant for procgen ships (vertex color → albedo, emissive attr →
  bloom). Wear that modulates vertex color therefore modulates albedo for free.
- **Hardpoints** today: a single placeholder at `spine.evaluate(0.4f)`. The real
  loadout model lives in the outfitter spec
  (`2026-05-28-ship-outfitter-loadout-design.md`): weapon `Hardpoint`,
  `ShipModuleSlot`/`ShipModuleData`/`ShipModuleCategory`. None are procedurally
  placed on the generated surface yet.
- **Factions are fully procedural and anonymous.** `FactionGenerator` produces
  `FactionData` with `id = "faction-" + i`, a procedurally generated name, and a
  **random** `FactionEthos` ∈ {CORPORATE, MILITARIST, ISOLATIONIST, FEDERATION,
  PIRATE_SYNDICATE}. The named entries in `faction_seeds.json` (terran_federation,
  iron_collective, crystal_dominion…) are **stale placeholders not wired to
  generation**. The lore's five powers do **not** exist as data yet. Factions
  currently have **zero** influence on ship appearance.
- **Mesh upload is deferred** to GL context in `GameScreen`; generation is GL-free.

---

## 2. Architecture & data flow

### 2.1 Generation config

`ShipGenerationConfig`:

| Field | Type | Notes |
|---|---|---|
| `seed` | long | base seed |
| `sizeClass` | ShipSizeClass | as today |
| `faction` | FactionData (nullable) | null → independent |
| `role` | ShipRole | WARSHIP, MERCHANT, PIRATE, SCOUT, CIVILIAN |
| `conditionFactor` | float 0–1 | 1 = pristine, 0 = derelict |
| `isFlagship` | boolean | fuller loadout, may force signature/flagship hull |

`ShipFactory` gains `createShip(ShipGenerationConfig, x, y, z)`. The existing
4-arg signature stays as an overload building a **default config** (independent
style, condition 1.0, no loadout) so current callers/tests are unaffected.

### 2.2 Sub-seed derivation

Per the seed-reproducibility rule, replace ad-hoc `seed`/`seed+1` with explicit
per-layer sub-seeds (`mix(seed, LAYER_HULL | LAYER_COMPONENTS | LAYER_WEAR)`,
SplitMix64-style finalizer). Each layer uses its own `java.util.Random`; no global
RNG. Same config → identical ship.

### 2.3 Revised pipeline

```
ShipGenerationConfig
   │
   ├─ resolve HullStyle            ← faction styleId (explicit map, else ethos
   │                                 fallback), loaded from hull_styles.json
   │
   ├─ ShipBlueprint(style, hullSeed)
   ├─ dispatch on style.generatorType:
   │     LOFTED  → ShipHullGenerator(style)        // spline-loft + signature feats
   │     FACETED → FacetedHullGenerator(style)     // platonic / crystalline
   │   → HullGeometry (verts, indices, sockets, bbox)
   │
   ├─ LoadoutGenerator(config)              // which sockets filled, with what
   ├─ ComponentGenerator(loadout, compSeed) // per filled socket → sub-mesh + xform
   │
   ├─ WearProcessor(condition, wearSeed)    // modulate vertex colors;
   │                                          append patch/scorch/breach geometry
   │                                          (skipped when style.ageless)
   │
   └─ assemble Entity:
        ShipMeshComponent               (hull + merged signature features)
        ShipExternalModulesComponent    (loadout sub-meshes + xforms + refs)
        ShipDataComponent / interior / flight / physics
```

New data under `core/src/main/resources/data/ships/`: `hull_styles.json`
(style definitions), `ethos_style_map.json` (ethos → fallback styleId),
`faction_styles.json` (explicit factionId → styleId). Existing
`hardpoint_templates.json` reused for socket layouts.

### 2.4 Phases

- **Phase 1 — Faction visual language** (styles, two generators, signature
  features). Foundational.
- **Phase 2 — Component visibility** (loadout-driven external modules).
- **Phase 3 — Damage/wear** (post-process; no-op for ageless styles).

---

## 3. Phase 1 — Faction visual language

### 3.1 Two generation paths

`HullStyle.generatorType`:

- **`LOFTED`** — existing spline-hull pipeline (smooth Frenet-framed rings).
  Federation, Vaun, Zul-Kiri, and all generic/procedural factions.
- **`FACETED`** — new `FacetedHullGenerator` producing flat-shaded, hard-edged
  geometric / crystalline geometry. Null-System and zeeLee.

Both emit the same `HullGeometry`, so components, wear, and deferred upload flow
through unchanged. `ShipFactory` dispatches on `generatorType`.

### 3.2 `HullStyle` (data-driven)

Loaded from `hull_styles.json`. Fields:

- `id`, `generatorType`
- **Lofted params** (constrain existing random ranges): `spineCurvature`,
  `sectionExponentRange`, `aspectBiasRange`, `wingStyle`, `nacelleStyle`,
  `panelStyle`, `symmetry` (symmetric ↔ modular-asymmetric ↔ chaotic).
- **Faceted params**: `baseForm` (PLATONIC / CRYSTAL_SPIRE), `facetJitter`,
  `sectionCount` + `sectionGap` (detached floating pieces), `emissiveMode`
  (NONE / SEAM_LINES / FULL_GLOW).
- `palette`: base/accent/emissive color sets.
- `signatureFeatures`: list of feature specs (see 3.4).
- `ageless`: boolean (skip wear; Null & zeeLee true).

### 3.3 The five lore factions + fallbacks

**Lofted styles:**

**`federation` — Modular Co-op.** Smooth rounded white/light-gray composite
plating, glowing **blue** accents (shields + engines). **Modular-asymmetric**:
multi-segment hull of visibly heterogeneous pods (different species' modules).
Section exp 2.0–2.8; gentle spine; moderate smooth wings; faired nacelles with
blue glow. Signature: **shield-projector rings** encircling the midsection,
**rotating sensor dishes**, prominent habitation windows (emissive). Defensive,
EMP-oriented loadout bias.

**`vaun` — Brutalist War Machine.** Jagged sharp angles, thick layered dark
iron-gray/matte-black armor slabs, harsh **red/orange** engine + weapon lighting.
Section exp 3.5–4.5; straight wedge spine; short armored buttress "wings".
Signature: **full-length spinal weapon barrel**, **forward jagged ram/teeth**,
**exposed structural ribbing**, **cooling vents** (emissive). No shields, heavy
hull; weapon-heavy loadout bias; boarding pods.

**`zulkiri` — Franken-ship / Cargo Hulk.** Industrial **rust-brown + toxic-green**,
mismatched salvaged plates (per-panel color jitter). **Bulbous insect-abdomen**
central body; chaotic asymmetry. Section exp 1.4–1.9 (carapace) with high
variance; kinked spine. **Many small erratically-placed thrusters** (fly's legs)
instead of clean nacelles. Signature: **strapped-on external cargo crates** with
magnetic clamps, **exposed wiring/fuel pipes**. Weak loadout; chaff/drone bias.

**Faceted styles:**

**`null_sentinel` — Monolithic Geometry.** Flawless seamless obsidian-black or
reflective chrome. `baseForm = PLATONIC` (cube / icosphere / tetrahedron),
`facetJitter ≈ 0`, **multiple detached sections** (`sectionCount` 2–4,
`sectionGap > 0`) floating apart. `emissiveMode = SEAM_LINES` (single pulsing
green/white data-light along seams). **No windows, no engine glow, no external
weapons.** `ageless = true`. (Combat-mode face-opening to reveal railguns is
gameplay/animation — out of scope.)

**`zeelee` — Spacetime Anomaly.** Iridescent translucent "solid light".
`baseForm = CRYSTAL_SPIRE` (tall vertical cluster of fused crystal prisms /
bipyramids), moderate `facetJitter`, `emissiveMode = FULL_GLOW` with prismatic
color varied by facet orientation. **No thrusters, no weapons, no crew, no
sockets.** `ageless = true`. (Non-Euclidean view-dependent shape-shift and
projectile-bending are shader/gameplay effects — out of scope; approximated by
iridescent emissive + bloom.)

**Generic fallbacks (lofted):** `pirate_patchwork` (weathered, mismatched,
chaotic asymmetry), `independent_utilitarian` (plain functional), `mercenary`
(aggressive, mixed-source).

### 3.4 Signature features (new concept)

Geometry intrinsic to a style, present regardless of loadout — this is what makes
a silhouette recognizable. Each `SignatureFeature` is a small parametric generator
keyed by type, scaled by `ShipSizeClass`, merged into the **hull** mesh:

| Feature type | Used by | Geometry |
|---|---|---|
| `SPINAL_LANCE` | vaun | full-length centerline barrel, emissive muzzle |
| `RAM_PROW` | vaun | forward jagged wedge/teeth |
| `STRUCTURAL_RIBBING` | vaun | repeated exposed rib arches over the hull |
| `COOLING_VENTS` | vaun | recessed vent slats (emissive) |
| `SHIELD_RING` | federation | torus ring(s) encircling the midsection |
| `MODULAR_PODS` | federation, zulkiri | extra distinct pod segments (varied section/color) |
| `SENSOR_DISH` | federation | dish on a short mast (rotation-ready transform) |
| `INSECT_ABDOMEN` | zulkiri | bulbous tapered rear body section |
| `STRAPPED_CARGO` | zulkiri | boxy crates clamped to the hull flanks |
| `EXPOSED_PIPING` | zulkiri | thin greeble tubes along seams |
| `ERRATIC_THRUSTERS` | zulkiri | many small thruster nubs at varied angles |

`MODULAR_PODS` also drives Federation's "heterogeneous pods" asymmetry. Faceted
styles express identity through `baseForm` + `emissiveMode` instead and declare no
lofted signature features.

### 3.5 Faction → style binding

Two-tier, because factions are currently anonymous:

- **Explicit:** `faction_styles.json` maps factionId → styleId. The five lore
  powers get explicit styleIds here. *(Authoring the five as real `FactionData` is
  a companion concern — this spec consumes whatever styleId a faction carries.)*
- **Fallback:** ethos → default styleId for anonymous procedural factions:
  FEDERATION→`federation`, MILITARIST→`vaun`, CORPORATE→`zulkiri`,
  ISOLATIONIST→`null_sentinel`, PIRATE_SYNDICATE→`pirate_patchwork`. `zeelee` is
  reachable **only** via explicit binding (unique/rare; no ethos maps to it).
- Faction `mapColor` is blended into the style palette (±15% per-ship variance)
  for non-fixed styles; the five signature palettes are authoritative and take
  precedence over map-color blending.

### 3.6 Code changes

- New: `HullStyle`, `HullStyleRegistry`, `ShipRole`, `FacetedHullGenerator`,
  `SignatureFeature` (+ types), `GeneratorType` enum.
- Changed: `ShipColorPalette` (takes `HullStyle`), `ShipBlueprint` &
  `ShipHullGenerator` (take `HullStyle`, pull ranges from it, emit signature
  features), `ShipFactory` (config + generator dispatch), `FactionData`
  (+`styleId`), `HullGeometry` (sockets[] replaces hardpoints[]).

### 3.7 Tests

- `(style, seed)` → identical geometry (determinism), both generators.
- Lofted style differentiation: mean section exponent, spine-curvature metric,
  symmetry metric measurably differ across federation/vaun/zulkiri.
- Signature features present: vaun hull contains a centerline `SPINAL_LANCE` span;
  federation contains a `SHIELD_RING` torus; zulkiri contains `STRAPPED_CARGO`.
- Faceted: flat per-face normals (neighboring facets differ); `null_sentinel`
  yields multiple disjoint sections with a gap and SEAM_LINES emissive, zero
  weapon sockets, zero engine-glow verts; `zeelee` yields a spire, FULL_GLOW, zero
  sockets, zero nacelles, and `ageless`.
- Every ethos resolves to a valid fallback style; `zeelee` only via explicit map.
- `hull_styles.json` / `ethos_style_map.json` / `faction_styles.json` parse;
  all referenced styles exist and are complete.

---

## 4. Phase 2 — Component visibility

### 4.1 Socket placement on the generated hull

A `HullSocket` records: `id`, `position` (on surface, local), `normal`, `forward`,
`socketType`, `size`. Sockets are sampled from the already-computed hull rings (or
faceted faces) so positions sit on the real surface. Count/type mix scale with
`ShipSizeClass`, biased by `HullStyle` and `role`. Where `hardpoint_templates.json`
defines a layout for the class, those positions are honored and snapped to the
nearest surface point; otherwise derived procedurally. Faceted exotic styles
(Null, zeeLee) declare **no** weapon sockets.

`SocketType`: TURRET, POINT_DEFENCE, MISSILE, SENSOR, CARGO_POD, UTILITY (drone
bay / chaff / mining / tractor / EMP emitter).

**Alignment with the outfitter spec:** `HullSocket` is the 3D placement source for
the existing loadout model — weapon sockets back weapon `Hardpoint`s, module
sockets back externally-visible `ShipModuleSlot`s. No parallel data model.

### 4.2 Loadout determination

`LoadoutGenerator(config)` decides which sockets are filled and with what, driven
by `role` / `faction` / techLevel / `isFlagship`, deterministic from
`componentSeed`. Faction loadout bias reflects the lore: Federation favors
PD/EMP/sensors, Vaun favors heavy turrets + boarding pods, Zul-Kiri favors cargo +
drone/chaff utilities and leaves offensive sockets sparse.

| Loadout source | Visible component |
|---|---|
| Weapon Hardpoint (ballistic/energy) | turret |
| Weapon Hardpoint (missile) | missile pod |
| Weapon Hardpoint (point-defence) | PD dome |
| `SCANNER` module | sensor dish/antenna |
| `CARGO_EXPANDER` module | cargo pod / crate |
| utility (drone bay, chaff, mining, tractor, EMP) | emitter/bay geometry |
| internal modules (REACTOR, SHIELD_GENERATOR, …) | none |

> Faction *signature* geometry (Vaun spinal lance, Federation shield rings, etc.)
> is produced in Phase 1 as signature features, not here. Phase 2 adds the
> per-ship variable loadout on top.

### 4.3 Component meshes

Procedural primitives, one small stride-11 mesh per filled socket, in the hull
palette (so wear post-processes them uniformly): turret (base + barrel housing,
references the `Hardpoint` for future aiming), PD dome, missile pod, sensor dish,
cargo pod/crate, utility bay/emitter.

### 4.4 Attachment & rendering

`ShipComponentAttachment` = `mesh` + `localTransform` (from socket
position/normal/forward) + nullable `hardpointRef` + `componentType`, collected on
`ShipExternalModulesComponent` (`Disposable`). Render draws each as
`shipWorldTransform × localTransform`. Separate sub-meshes so turrets can aim and
modules can later be destroyed/swapped. Upload deferred to GL like the hull.

### 4.5 Migration

Grep `HullGeometry.hardpoints` usage and migrate consumers to the socket list
(expected minimal — currently one placeholder).

### 4.6 Tests

- Loadout deterministic from `componentSeed`; faction bias holds (merchant/Zul-Kiri
  fewer offensive than Vaun warship).
- Socket count scales monotonically with size class.
- Faceted exotic styles produce zero loadout components.
- Each component mesh non-empty and stride-valid; transforms place components
  on/just outside the surface (not buried); `#attachments == #filled sockets`.

---

## 5. Phase 3 — Damage/wear states

Generation-time only. Skipped entirely when `style.ageless` (Null, zeeLee).

### 5.1 `WearProfile`

From `conditionFactor` + `wearSeed`; fields scale with severity `s = 1−condition`:
`chipAmount`, `scorchAmount`, `discoloration`, `patchCount`, `breachCount`. At
`conditionFactor == 1.0` (or `ageless`) every field is zero → output identical to
input.

### 5.2 `WearProcessor`

Pure function over assembled `float[]`/`short[]` of hull (incl. signature features)
and component sub-meshes. Vertex color = albedo, so all of this is albedo
modulation:

- **Chipping & discoloration** — 3D value-noise per vertex; darken/desaturate past
  a `chipAmount` threshold. Forward-facing vertices (from normals) wear more.
- **Scorch** — N centers biased toward emissive engine/weapon verts; radial
  darkening scaled by `scorchAmount`.
- **Hull patches** — appended quad geometry: off-palette welded plates over random
  regions, offset along the local normal. Carries the "patched derelict" read.
- **Breaches** (severe only) — appended darkened inset jagged rim quads.

The emissive attribute is preserved (engine/window glow survives wear).

### 5.3 Known tradeoffs

- Per-vertex scorch resolution bounded by mesh density (`RING_VERTEX_COUNT = 24`);
  appended patch/breach geometry carries fine detail.
- No per-vertex roughness attribute → burnt-surface roughness is future work.

### 5.4 Tests

- `conditionFactor == 1.0` or `ageless` → output byte-identical to input (no-op).
- Lower condition → strictly more darkened verts and more appended patch verts.
- Deterministic from `(wearSeed, conditionFactor)`; patches off-palette; stride
  and index integrity preserved.

---

## 6. Testing, integration & isolation

| Unit | Input → Output | GL-free test |
|---|---|---|
| `HullStyleRegistry` | JSON → styles | parse, completeness, binding resolution |
| `ShipHullGenerator(style)` | blueprint → geometry + sockets + features | determinism, differentiation, signature features |
| `FacetedHullGenerator(style)` | blueprint → faceted geometry | flat normals, detached sections, emissive mode |
| `LoadoutGenerator` | config → loadout list | determinism, faction/role bias |
| `ComponentGenerator` | loadout + sockets → attachments | mesh validity, placement |
| `WearProcessor` | arrays + profile → arrays | no-op at 1.0/ageless, monotonicity, integrity |

`ShipFactory` orchestrates and assembles; only mesh upload stays deferred to GL.
All new components/meshes are `Disposable`. The 4-arg `createShip` overload keeps
existing callers/tests working. Render path draws each `ShipComponentAttachment`
with the vertex-color PBR variant, same as the hull.

---

## 7. New/changed files (anticipated)

**New (Java, `core/src/main/java/com/galacticodyssey/ship/`):**
- `ShipGenerationConfig`, `ShipRole`, `GeneratorType`
- `HullStyle`, `HullStyleRegistry`
- `FacetedHullGenerator`
- `SignatureFeature` (+ `SignatureFeatureType`)
- `HullSocket`, `SocketType`
- `LoadoutGenerator`, `ComponentGenerator`, `ShipComponentAttachment`
- `components/ShipExternalModulesComponent`
- `WearProfile`, `WearProcessor`

**Changed:**
- `ShipFactory`, `ShipBlueprint`, `ShipHullGenerator`, `ShipColorPalette`,
  `HullGeometry` (sockets[] replaces hardpoints[]), `FactionData` (+`styleId`),
  `GameScreen` (upload component meshes).

**New data (`core/src/main/resources/data/ships/`):**
- `hull_styles.json`, `ethos_style_map.json`, `faction_styles.json`

**Tests (`core/src/test/java/com/galacticodyssey/ship/`):**
- `HullStyleRegistryTest`, `ShipHullGeneratorStyleTest`,
  `FacetedHullGeneratorTest`, `LoadoutGeneratorTest`, `ComponentGeneratorTest`,
  `WearProcessorTest`, plus additions to `ShipHullGeneratorTest` /
  `ShipFactoryTest`.

---

## 8. Out of scope

- Authoring the five lore factions as real `FactionData` (companion concern; this
  spec consumes a styleId however assigned).
- Live battle-damage mesh updates; shader-based wear / per-vertex roughness.
- Turret aiming/animation and Null combat-mode face-opening (geometry is *ready* —
  transforms/Hardpoint refs exist — but behavior is separate).
- zeeLee non-Euclidean view-dependent morphing, projectile-bending, and true
  translucency/refraction; Vaun weapon-fire gas venting VFX (these are
  shader/VFX/gameplay, approximated here by emissive + bloom only).
- Texture/UV-mapped PBR materials for procgen hulls (outfitter cosmetics, future).
