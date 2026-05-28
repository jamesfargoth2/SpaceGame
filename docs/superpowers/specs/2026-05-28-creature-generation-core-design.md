# Creature Generation — Cycle A: Generation Core

**Date:** 2026-05-28
**Status:** Approved design, pending implementation plan
**Author:** Brainstormed with Claude

## Context

Creatures are currently the biggest gap in the procgen systems — they render as colored
boxes with no real generation behind them. The `npc` package handles *sapient* crew/NPCs
(species, dialog, recruitment) and is a distinct domain; there is no wildlife/fauna system.
The planet, climate, water, and ship-procgen systems are already mature, so creatures are
true greenfield.

The full creature ambition (modular assembly, rigging, procedural skin, behavior, ecosystem,
mass-scaled stats) is too large for one spec. It is decomposed into four sequential cycles,
each with its own spec → plan → implementation. **This document specs Cycle A only.**

### Cycle decomposition

| Cycle | Scope |
|-------|-------|
| **A — Generation Core** (this spec) | Body-plan archetypes, data-driven part library, socket-graph assembly, symmetry, size scaling, mass-derived stats. Output: a deterministic `CreatureSpec` + assembled neutral-pose geometry from a seed. |
| B — Rig & Animation | Runtime skeleton built from the socket graph; shared animation sets keyed per archetype (idle/walk/attack). |
| C — Appearance | Procedural skin patterns & palettes (stripes/spots/gradient/bioluminescence) via shader, biome-tinted. |
| D — Behavior & Ecosystem | Diet/temperament profiles, flee/fight thresholds, herd/pack/solitary, biome habitat rules, predator-prey spawn ratios, population density. |

## Goals (Cycle A)

- From a seed (+ biome, + optional archetype filter), deterministically produce a
  `CreatureSpec`: a GL-free blueprint of a fully assembled creature.
- Turn a `CreatureSpec` into a renderable libGDX `Model` in a static neutral pose and an
  Ashley entity.
- Support **two geometry sources behind one interface**: fully procedural geometry
  (default, ships content, no art assets) *and* authored `.g3db` part meshes. The assembly
  engine is agnostic to which backs any given part.
- Cover four maximally-different topologies: **Bipedal, Quadruped, Hexapod, Serpentine**
  (limb counts 2 / 4 / 6 / 0; upright-with-arms; bilateral symmetry; limbless bone-chain).
- Size scaling from mouse-sized to megafauna, with HP/speed/damage derived allometrically
  from mass.
- A debug spawn path so a generated creature can be seen in-world.

## Non-goals (deferred to later cycles)

- Skeleton binding and animation — Cycle A creatures appear in a **static neutral pose**.
  The socket graph is authored so it *becomes* the skeleton in Cycle B (sockets carry a
  `jointHint`), but no skinning/animation is built here.
- Procedural skin pattern shaders — Cycle A uses a **simple biome-tinted solid material**
  so creatures aren't all grey; pattern generation is Cycle C.
- Behavior, AI, ecological/biome-weighted spawning, population density — Cycle D. Cycle A
  ships only a manual **debug spawn**.
- Additional archetypes (avian, aquatic, amorphous, insectoid variants) — data-driven
  additions in later cycles once the engine is proven.

## Architecture

### Assembly model: socket-graph (chosen)

A creature is assembled by walking a **socket graph**:

- A **Socket** is a named attachment point on a part: a local transform (position + rotation
  relative to the part's origin), an `acceptedPartType`, a `mirrorGroup` (for symmetry), and
  a `jointHint` (consumed by Cycle B's rig).
- A **part** (`CreaturePartDef`) is a mesh-bearing node that itself exposes sockets, enabling
  recursion (e.g. a torso socket holds a neck, the neck holds a head).
- A **body-plan archetype** (`BodyPlanArchetypeDef`) is a template graph: which root part,
  which sockets exist, which part-types fill them, count and symmetry rules (mirror across the
  sagittal plane, or radial), scale band, mass range, and gait class (metadata for Cycle B).
- **Assembly** walks the template: for each socket, seeded-select a matching part variant,
  place it at the socket transform, recurse into that part's sockets, mirror symmetric pairs,
  and apply per-part scale.

The socket graph doubles as the future skeleton, and because each part's mesh is produced by a
**geometry provider**, the assembler never needs to know whether geometry is procedural or
authored.

Alternatives considered and rejected for creatures:
- **L-system grammar** — better suited to flora; hard to hit recognizable archetypes or place
  discrete limbs/sockets predictably.
- **Metaball / implicit-surface blend** — costly, hard to rig, weak limb control. May return
  later as a *part-level* geometry provider for a future Amorphous archetype.

### Package & layout

New top-level package `com.galacticodyssey.fauna` (design-doc-checked: `docs/DESIGN.md` only
mentions "creature habitats" in passing, with no creature system; `npc` is sapient crew, a
separate domain).

```
core/src/main/java/com/galacticodyssey/fauna/
  archetype/    BodyPlan (enum), BodyPlanArchetypeDef, ArchetypeSelector
  part/         CreaturePartDef, Socket, PartType (enum)
  geometry/     PartGeometryProvider (iface), PartGeometrySpec,
                ProceduralPartProvider, AuthoredPartProvider
  assembly/     CreatureAssembler, AssembledNode, AssembledCreature
  stats/        MassStatModel
  CreatureSpec.java          (resolved, GL-free blueprint)
  components/   CreatureComponent
  CreatureMeshBuilder.java   (Spec → libGDX Model; GL side)
  CreatureFactory.java       (Spec → Ashley entity)

core/src/main/java/com/galacticodyssey/data/
  FaunaDataRegistry.java     (loads + validates part/archetype JSON)

core/src/main/resources/data/fauna/
  parts/*.json
  archetypes/*.json
```

### Data model

- **`PartType`** (enum): `TORSO, HEAD, NECK, LIMB_LEG, LIMB_ARM, TAIL`. Extensible; antennae /
  mandibles / wings / fins arrive in later cycles.
- **`Socket`**: `id`, `localTransform` (pos + rot), `acceptedPartType`, `mirrorGroup`
  (nullable; sockets sharing a group are mirrored as a pair), `jointHint` (joint metadata for
  Cycle B).
- **`CreaturePartDef`** (JSON): `id`, `partType`, `sockets[]`, `geometry` (a `PartGeometrySpec`
  that is either procedural params or an authored `modelRef`), `scaleRange`.
- **`BodyPlanArchetypeDef`** (JSON): `id`, `bodyPlan`, `rootPartType`, per-socket fill rules
  (`acceptedPartType` + `count` + `symmetry` ∈ {mirror, radial, none}), `scaleBand`,
  `massRange`, `gaitClass`.
- **`CreatureSpec`**: the deterministic output blueprint — chosen archetype, the chosen part
  variant per node, resolved transforms, per-part scales, total mass, derived stats, and a
  color seed. Pure serializable data, no GL references.

### Pipeline / data flow

```
seed (+ biome, + optional archetype filter)
  → ArchetypeSelector   seeded archetype pick (biome-weighting hook reserved for Cycle D)
  → CreatureAssembler   walk template: per socket → seeded part-variant pick →
                        place at socket transform → recurse → mirror symmetric pairs → scale
  → MassStatModel       mass = Σ(part bounding volume × archetype density); derive stats
  → CreatureSpec        ← fully testable headless, no GL
  ───────────── GL boundary ─────────────
  → CreatureMeshBuilder  per part, ask its geometry provider for a Mesh; compose under nodes
  → CreatureFactory      Ashley entity: CreatureComponent + Transform + Render + HealthComponent
```

### Geometry providers

```java
interface PartGeometryProvider {
    Mesh buildMesh(PartGeometrySpec spec);   // GL-side; Disposable meshes, pooled builders
}
```

- **`ProceduralPartProvider`** (default content): builds parts from primitives via libGDX
  `MeshBuilder` — capsule / spline-swept-radius limbs, lofted tapered torso, ellipsoid + snout
  head, cone tail. Pooled, disposes its resources.
- **`AuthoredPartProvider`**: loads the `.g3db` node referenced by `PartGeometrySpec.modelRef`
  and scales it to the socket.

The provider is selected per `CreaturePartDef`. All shipped Cycle-A content uses the
procedural provider, so the system runs with zero art assets.

### Size scaling & stats

- Each archetype defines a scale band (e.g. quadruped 0.3 m – 8 m); a seeded roll picks the
  size within the band.
- `mass = Σ(part bounding volume) × archetypeDensity`, adjusted by the size roll.
- Allometric stat derivation (constants in JSON, tunable, clamped to sane ranges):
  - `HP = k_hp · mass^(2/3)`
  - `moveSpeed = k_speed · mass^(-1/4)`
  - `meleeDamage = k_dmg · mass^(3/4)`
- Stats feed the existing `HealthComponent`.

## Error handling & validation

- `FaunaDataRegistry` validates JSON at load: every archetype's required socket part-types
  exist in the part library; every socket's `acceptedPartType` is satisfiable; `mirrorGroup`
  sockets come in resolvable pairs; scale/mass ranges are well-formed (min ≤ max). Invalid
  content fails fast at load with a descriptive error rather than producing broken creatures.
- Assembly is total: if a socket has no matching part variant after validation, that is a
  programming/data error and throws (it should be impossible post-validation).
- Geometry providers dispose GL resources; `CreatureMeshBuilder` / `CreatureFactory` own and
  clean up the `Model` lifecycle (`Disposable`).

## Determinism & testing (architectural rule #5)

- A single seeded RNG (following the `procgen-seed-reproducibility` pattern) drives archetype
  selection, part-variant selection, and size roll. Same `(seed, biome, archetype filter)`
  yields a byte-identical `CreatureSpec`, every field.
- **Headless JUnit5 tests** (no GL context required):
  - Determinism: two generations with identical inputs produce equal `CreatureSpec`s.
  - Per-archetype structural correctness: expected socket/part counts and symmetry (e.g.
    quadruped has 4 legs in 2 mirrored pairs; serpentine has a limbless bone-chain).
  - `MassStatModel`: stats are monotonic in mass and clamped.
  - Both geometry providers yield a valid spec (provider selection is data-resolved; mesh
    *building* is GL-gated and kept thin — all logic lives in the spec).
- Mesh building (`CreatureMeshBuilder`) is GL-dependent and therefore not exercised in
  headless CI; it is kept as a thin translation layer over the already-tested `CreatureSpec`.

## Integration

- Cycle A ships a **debug spawn** (dev console command or dev key) that calls
  `CreatureFactory` to build a creature near the player on the planet surface, anchored via the
  existing `SurfaceAnchorSystem`, in a static neutral pose — enough to visually confirm output.
- Biome-weighted ecological spawning, population density, and predator-prey ratios are Cycle D.

## Open questions / future hooks

- `ArchetypeSelector` reserves a biome-weighting hook for Cycle D.
- `Socket.jointHint` and `gaitClass` are authored now but consumed only in Cycle B.
- `color seed` on `CreatureSpec` is set now but only drives a flat biome tint until Cycle C.
