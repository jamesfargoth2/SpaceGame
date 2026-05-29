# Creature Generation — Cycles B, C, D

**Date:** 2026-05-28
**Status:** Approved design, pending implementation plan
**Author:** Brainstormed with Claude
**Depends on:** [Cycle A spec](2026-05-28-creature-generation-core-design.md) (implemented)

## Context

Cycle A shipped a deterministic `CreatureSpec` pipeline: socket-graph assembly, 4 archetypes
(BIPEDAL/QUADRUPED/HEXAPOD/SERPENTINE), dual geometry providers, mass-derived stats, and F6
debug spawn. Creatures render as static neutral-pose meshes with flat tint colors. This spec
covers the remaining three cycles that bring creatures to life:

- **B — Procedural Skeleton & Animation**: runtime rig from socket graph, code-driven gaits
- **C — Procedural Skin Patterns**: hybrid vertex-color + shader pattern system
- **D — Behavior, Ecology & Biome Spawning**: full ecosystem simulation with chunk-persistent populations

All three cycles maintain Cycle A's zero-art-asset philosophy — everything is procedural and
data-driven.

---

## Cycle B — Procedural Skeleton & Animation

### Runtime Skeleton Construction

A `CreatureRigBuilder` walks the `AssembledNode` tree (produced by `CreatureAssembler`) and
generates a `CreatureRig` — a flat array of `Bone` objects mirroring the socket graph. Each
`AssembledNode`'s `Socket.jointHint` determines the bone's semantic role:

| jointHint | Bone role | Used by |
|-----------|-----------|---------|
| `"hip"` | Leg root / pelvis attachment | Leg IK chain origin |
| `"shoulder"` | Arm root | Arm IK chain origin |
| `"neck"` | Head attachment | Head look-at target |
| `"spine"` | Spine segment | Serpentine undulation, body flex |
| `"tail"` | Tail root | Follow-through dynamics |
| `null` | Structural (no animation) | Static attachment |

Bones store bind-pose transforms derived from the socket's `localTransform` and
`worldTransform` already computed by the assembler. The bone hierarchy exactly mirrors the
`AssembledNode` parent-child tree.

### Mesh Skinning

Mesh vertices are skinned at build time: each vertex is assigned to the bone of its owning
`AssembledNode` (single-bone-per-vertex, no blending — parts are discrete rigid segments).
This converts the current multi-`ModelInstance` representation into a single skinned `Model`
with a node hierarchy that libGDX's animation infrastructure can drive.

`CreatureMeshBuilder` is modified to output a single combined `Model` with bone nodes instead
of an `Array<ModelInstance>`. Each part's mesh becomes a `MeshPart` bound to its corresponding
bone node via `NodePart`.

### Procedural Gait System

`CreatureGaitSystem` (Ashley system, priority between physics and rendering) drives animation
each frame. It reads `gaitClass` from the archetype and leg count from the rig to select a
gait controller.

#### WalkGaitController (quadruped `"walk"`, biped `"walk"`)

- Sine-wave leg cycling with configurable phase offsets per leg pair.
- Stride length and frequency scale with `moveSpeed` and `sizeMultiplier`.
- Foot placement uses physics raycasts to snap feet to terrain surface.
- 2-bone IK solving from hip bone → foot target position.
- Quadruped: 4-beat walk cycle, diagonal pairs in anti-phase (LF+RR, RF+LR).
- Biped: 2-beat alternating cycle, arm swing in counter-phase to legs.
- Body bob: vertical sine oscillation at 2× leg frequency, amplitude proportional to size.
- Body roll: lateral tilt toward the planted foot, proportional to stride width.

#### SkitterGaitController (hexapod `"skitter"`)

- Alternating tripod gait: legs grouped into two sets of 3 (L1+R2+L3 vs R1+L2+R3).
- Each set moves in anti-phase — one set planted while the other swings.
- Higher frequency, lower amplitude than walk.
- Same IK foot placement via raycasts.
- Body stays level (minimal bob) — low center of gravity.

#### SlitherGaitController (serpentine `"slither"`)

- Lateral sine wave propagated along the spine bone chain.
- Each spine bone receives a phase-offset lateral rotation: `angle = A * sin(wt - k * boneIndex)`.
- Amplitude `A` and wavelength `1/k` scale with total body length.
- Forward propulsion derived from the wave — the creature's `TransformComponent` position
  advances along the average heading vector.
- No leg IK (limbless).

#### Shared Secondary Motion

All gait controllers also drive:

- **Head look-at**: Head bone tracks toward a target (player, threat, wander point) via damped
  rotation (exponential decay toward target angle, ~5°/frame max slew).
- **Tail follow-through**: Tail bone(s) lag behind body rotation with spring dynamics
  (`k_spring = 8`, `damping = 0.7`). Creates natural trailing motion on turns.
- **Idle variation**: When stationary (no locomotion input), breathing (subtle torso scale
  oscillation at ~0.5 Hz) and weight-shifting (small lateral hip sway at ~0.15 Hz).
- **Speed blending**: Animation parameters (stride frequency, amplitude, bob intensity)
  interpolate smoothly between idle (speed=0) and max speed, avoiding pops on start/stop.

### IK Solver

A lightweight `TwoBoneIKSolver` utility handles leg placement:

1. Inputs: root bone position (hip), target position (terrain contact point), pole vector
   (forward knee direction), upper/lower bone lengths.
2. Solves the triangle analytically (law of cosines for elbow/knee angle).
3. Outputs: rotation for upper bone, rotation for lower bone.
4. Terrain contact point = raycast from hip straight down (or along gravity vector), offset by
   foot length.

Runs per leg, per frame. For a hexapod (6 legs) this is 6 IK solves per frame — trivial cost.

### New Types

```
fauna/rig/
  CreatureRigBuilder.java    — AssembledNode tree → CreatureRig
  CreatureRig.java           — bone array + hierarchy + role tags
  Bone.java                  — bind pose, current pose, parent index, role enum
  BoneRole.java              — enum: HIP, SHOULDER, NECK, SPINE, TAIL, STRUCTURAL
  TwoBoneIKSolver.java       — analytical 2-bone IK

fauna/animation/
  CreatureGaitSystem.java    — Ashley system, dispatches to gait controllers
  GaitController.java        — interface: update(rig, deltaTime, velocity, terrain)
  WalkGaitController.java
  SkitterGaitController.java
  SlitherGaitController.java

fauna/components/
  CreatureAnimationComponent.java — holds CreatureRig, active GaitController, bone transforms
```

### Data Changes

None — `jointHint` and `gaitClass` are already authored in the part/archetype JSON from Cycle A.

### Integration

- `CreatureFactory` attaches `CreatureAnimationComponent` alongside existing components.
- `CreatureGaitSystem` registered in `GameWorld` at priority after physics, before rendering.
- `CreatureMeshBuilder` modified to produce a single skinned `Model` with bone nodes instead
  of separate `ModelInstance`s per part.
- `CreatureRenderComponent.modelInstance` changes from `Object` (holding `Array<ModelInstance>`)
  to a single `ModelInstance` referencing the skinned model.

---

## Cycle C — Procedural Skin Patterns & Shaders

### Pattern Generation Pipeline

Pattern generation is two-layered, both driven deterministically from `CreatureSpec.colorSeed`.

#### Layer 1 — Vertex Color Baking (mesh build time)

`ProceduralPartMesher` is extended to emit a `COLOR_PACKED` vertex attribute. After mesh
geometry is generated, a `PatternStamper` writes per-vertex color data encoding body-context
channels:

| Channel | Encoding | Purpose |
|---------|----------|---------|
| R | Dorsal-ventral gradient | `dot(vertexNormal, creatureUp)` mapped 0→1. Drives natural dorsal/ventral color split. |
| G | Limb-axis gradient | Normalized distance along the part's primary axis (0 = proximal, 1 = distal). Drives extremity darkening, stripe alignment. |
| B | Voronoi cell ID | Object-space Voronoi noise (seeded from `colorSeed`) baked per vertex. Discrete zone IDs for patch/rosette boundaries. |
| A | Curvature estimate | Local convexity/concavity from neighbor vertex normals. Drives ridge highlighting, crease darkening. |

These four channels provide the fragment shader with body-topology context that pure tri-planar
projection cannot achieve.

#### Layer 2 — Fragment Shader Detail (runtime)

A `creature_skin.frag` shader (registered with `GBufferBatchShaderProvider`) reads vertex
colors plus per-creature uniforms derived from `colorSeed`:

| Uniform | Type | Description |
|---------|------|-------------|
| `u_patternType` | int | Pattern function selector: 0=SOLID, 1=STRIPES, 2=SPOTS, 3=ROSETTES, 4=MOTTLED, 5=BIOLUMINESCENT |
| `u_palette` | vec3[3] | Primary, secondary, accent colors |
| `u_patternScale` | float | Noise frequency multiplier |
| `u_patternContrast` | float | Edge hardness (soft blend → sharp boundaries) |
| `u_bioGlow` | float | Bioluminescence emissive intensity (0 for most creatures) |
| `u_bodyPlan` | int | Archetype index — drives PBR property selection |

**Pattern compositing in the shader:**

1. Vertex-color R (dorsal/ventral) interpolates between `u_palette[0]` (dorsal) and a
   lightened/desaturated variant (ventral).
2. The selected pattern function modulates between `u_palette[0]` and `u_palette[1]`:
   - STRIPES: `sin(limbAxisGradient * u_patternScale * PI)` thresholded by contrast.
   - SPOTS: Voronoi cell distance < threshold → secondary color.
   - ROSETTES: Voronoi cell edge (distance to border) within band → secondary, interior → primary.
   - MOTTLED: Multi-octave Simplex noise in object space.
   - BIOLUMINESCENT: Mottled base + curvature-weighted emissive in `u_palette[2]`.
3. Vertex-color G (limb gradient) darkens extremities: `mix(color, color * 0.7, limbGradient)`.
4. Vertex-color A (curvature) adds subtle highlight on ridges, darkening in creases.
5. `u_palette[2]` (accent) is used for eye spots, crest tips, or bioluminescent patches.

#### Palette Generation

`PaletteGenerator` derives colors deterministically from `colorSeed` + biome:

1. `colorSeed` → base hue angle, constrained per biome:
   - Desert/Savanna: 20°–60° (warm ochre/sandy)
   - Forest/Jungle: 80°–160° (greens, olive)
   - Tundra/Ice: 0°–40°, low saturation (grey-white, pale blue)
   - Ocean/Coast: 180°–260° (blue-teal)
   - Volcanic: 0°–30° (dark red-black)
   - Cave: unconstrained hue, low saturation base + high-saturation bioluminescent accent
2. Primary = base hue, medium saturation (0.4–0.7), medium value (0.4–0.7).
3. Secondary = analogous hue shift (±30°) or complementary (±180°), slight value change.
4. Accent = high saturation (0.7–1.0), contrasting or same-family hue.
5. Ventral auto-derived: primary hue, saturation ×0.5, value ×1.3 (clamped).

Pattern type is also biome-weighted:
- Jungle/Forest: spots (0.3), rosettes (0.3), mottled (0.3), stripes (0.1)
- Grassland/Savanna: stripes (0.4), mottled (0.3), spots (0.2), solid (0.1)
- Desert: solid (0.4), mottled (0.3), stripes (0.2), spots (0.1)
- Cave: bioluminescent (0.5), solid (0.3), mottled (0.2)
- Ocean: stripes (0.3), spots (0.3), mottled (0.2), bioluminescent (0.2)

#### PBR Material Properties

The shader varies roughness and metallic by `u_bodyPlan`:

| Body Plan | Roughness | Metallic | Rationale |
|-----------|-----------|----------|-----------|
| QUADRUPED | 0.75 | 0.0 | Fur/hide-like |
| BIPEDAL | 0.70 | 0.0 | Skin/hide |
| HEXAPOD | 0.35 | 0.15 | Chitin/carapace — shinier |
| SERPENTINE | 0.45 | 0.05 | Scales — moderate sheen |

Bioluminescent patches override: roughness 0.1, emissive = `u_palette[2] * u_bioGlow`.

### New Types

```
fauna/skin/
  PatternStamper.java        — bakes vertex color channels onto ProceduralMeshData
  PaletteGenerator.java      — colorSeed + biome → palette + pattern type
  CreatureSkinSpec.java       — pattern type, palette, scale, contrast, glow (stored on CreatureSpec)
  PatternType.java            — enum: SOLID, STRIPES, SPOTS, ROSETTES, MOTTLED, BIOLUMINESCENT

resources/shaders/
  creature_skin.vert          — passes vertex colors + object-space position to fragment
  creature_skin.frag          — pattern compositing, PBR output to G-buffer
```

### Data Changes

- `CreatureSpec` gains a `CreatureSkinSpec skinSpec` field, populated by `CreatureAssembler`
  using `PaletteGenerator`.
- `ProceduralPartMesher` vertex format adds `COLOR_PACKED` attribute.
- `CreatureRenderComponent` replaces `tintR/tintG/tintB` with a reference to `CreatureSkinSpec`
  (uniforms set at render time from this spec).

---

## Cycle D — Behavior, Ecology & Biome-Weighted Spawning

### Species Definition

A **Species** is the data-driven link between a body archetype and its ecological role. Defined
in JSON at `data/fauna/species/*.json`:

```json
{
  "id": "plains_grazer",
  "archetypeId": "grazer_quad",
  "diet": "HERBIVORE",
  "temperament": "TIMID",
  "socialStructure": "HERD",
  "herdSize": [4, 12],
  "biomes": { "GRASSLAND": 1.0, "SAVANNA": 0.8, "TUNDRA": 0.3 },
  "trophicLevel": 1,
  "preySpecies": [],
  "predatorPressure": 0.6,
  "activityCycle": "DIURNAL",
  "detectionRadius": 25.0,
  "fleeRadius": 15.0,
  "fleeSpeedMultiplier": 1.5,
  "safeDistance": 40.0,
  "birthRate": 0.02,
  "carryingCapacityBase": 30,
  "massOverride": null
}
```

Fields:
- `diet`: HERBIVORE, CARNIVORE, OMNIVORE — determines foraging vs hunting behavior.
- `temperament`: TIMID (flees early), NEUTRAL (flees when close), TERRITORIAL (charges
  intruders), AGGRESSIVE (hunts player). Drives state transition thresholds.
- `socialStructure`: SOLITARY, HERD (shared flee), PACK (coordinated hunt).
- `herdSize`: Min/max individuals spawned together in a group.
- `biomes`: Biome affinity weights (0–1). Species only spawn in biomes with weight > 0.
- `trophicLevel`: 1 = herbivore, 2 = mesopredator, 3 = apex. Drives food chain math.
- `preySpecies`: List of species IDs this predator hunts (empty for herbivores).
- `activityCycle`: DIURNAL, NOCTURNAL, CREPUSCULAR — modulates behavior by time of day.
- Detection/flee/safe radii and speed multiplier control threat response geometry.
- `birthRate`, `carryingCapacityBase`: Population dynamics parameters.

`FaunaDataRegistry` is extended to load and cross-validate species definitions (referenced
archetypes must exist, prey species must exist, trophic levels must be consistent — predators
can't prey on species at equal or higher trophic level).

### Behavior System

`CreatureBehaviorSystem` (Ashley system) uses a gdx-ai `DefaultStateMachine` per creature
entity, with states keyed by a `CreatureState` enum.

#### States — All Creatures

| State | Entry Condition | Behavior | Exit |
|-------|----------------|----------|------|
| IDLE | Default / post-wander | Stand in place, breathing animation, head scans environment. Duration: 3–8s (seeded). | Timer expires → WANDER |
| WANDER | Post-idle | Random walk within home range. Herbivores bias toward noise-based "food density" hotspots. Speed = 0.4 × moveSpeed. | Reached target → IDLE; threat detected → ALERT |
| ALERT | Threat enters detection radius | Head tracks threat, body orients for escape vector. No movement. | Threat enters flee radius → FLEE; threat leaves detection radius → IDLE |
| FLEE | Threat enters flee radius, or herd alert received | Sprint directly away from threat. Speed = moveSpeed × fleeSpeedMultiplier. Energy drains at 2× rate. | Distance > safeDistance → ALERT; energy depleted → IDLE (exhausted) |

#### States — Predators (additional)

| State | Entry Condition | Behavior | Exit |
|-------|----------------|----------|------|
| HUNT | Hunger > 0.7 and prey detected within detection radius | Pursuit steering toward prey (predict intercept point). Speed = 0.8 × moveSpeed (energy-conserving approach). | Melee range reached → ATTACK; prey escapes detection range → WANDER; energy depleted → IDLE |
| ATTACK | Melee range to prey | Lunge animation, apply `meleeDamage` to prey's `HealthComponent`. 1-second cooldown between strikes. | Prey dies → FEED; prey escapes melee range → HUNT |
| FEED | Prey killed | Stationary at carcass position. Hunger decreases at 0.3/s. Detection radius halved (vulnerable). | Hunger < 0.2 → IDLE; threat detected at halved radius → FLEE |

#### Social Behavior Overlays

**Herd alert propagation (HERD social structure):**
- When a HERD creature transitions to FLEE, it publishes a `HerdAlertEvent` on the event bus
  with its spawn group ID and position.
- All creatures sharing the same spawn group within a configurable alert radius (default 50m)
  also transition to FLEE, even if they haven't independently detected the threat.
- Creates stampede behavior: one spooked animal triggers the herd.

**Pack coordination (PACK social structure):**
- Pack members share a hunt target. When one pack member enters HUNT, other pack members in
  range also enter HUNT targeting the same prey.
- Each pack member selects an approach offset: spread by `360° / packSize` around the prey,
  with angular noise. Creates flanking/encirclement without explicit communication.
- If the pack alpha (lowest entity ID in the group) gives up the hunt, the pack disengages.

### Creature Drives

`CreatureDrivesComponent` (Ashley component) carries float fields that tick over time:

| Drive | Range | Tick Rate | Effect |
|-------|-------|-----------|--------|
| hunger | 0–1 | +0.01/s (scales with mass^(-1/4) — small creatures hunger faster) | >0.7: predators enter HUNT, herbivores wander more aggressively. =1.0: health drains 1 HP/s (starvation). |
| energy | 0–1 | -0.02/s while moving, -0.06/s while sprinting (FLEE). +0.05/s while IDLE. | <0.2: moveSpeed ×0.5. =0.0: forced IDLE (exhaustion), no flee possible. |
| fear | 0–1 | Spikes to 1.0 on threat detection, decays -0.1/s. Stacks additively from herd alerts (+0.5). | >0.5: flee distance ×1.5. >0.8: ignore exhaustion, flee at full speed. |

`CreatureDriveSystem` (Ashley system) ticks all drives each frame. Separate from
`CreatureBehaviorSystem` to maintain single-responsibility.

### Ecosystem — Chunk Population Model

The planet surface is divided into chunks (reusing `SceneStreamingSystem`'s existing chunk
grid). Each chunk stores a `ChunkPopulationRecord`:

```java
public final class ChunkPopulationRecord {
    public int chunkX, chunkZ;
    public BiomeType biome;
    public final List<SpeciesPopulation> populations = new ArrayList<>();
    public double lastTickTime;  // game-time of last population update
}

public final class SpeciesPopulation {
    public String speciesId;
    public int count;
    public float birthAccumulator;  // fractional births pending
}
```

#### Population Tick

`PopulationTickSystem` runs at low frequency (every 30 seconds game-time) and processes all
tracked chunks within a configurable radius (e.g., 5-chunk radius around the player). This
includes **unloaded** chunks — population math runs without instantiated creatures.

For each chunk, for each species population:

1. **Carrying capacity K**: `species.carryingCapacityBase * biomeFertility(biome)`. Herbivore
   K is high in grassland (fertility 1.0), low in desert (0.1), zero in unsuitable biomes.
   Predator K is additionally proportional to prey population:
   `K_pred = basePredatorK * sum(preyCount) / sum(preyK)`.

2. **Logistic growth**: `dN = species.birthRate * N * (1 - N/K) * dt`. Added to
   `birthAccumulator`. When accumulator ≥ 1.0, `count` increments and accumulator decreases by
   1.0. Negative growth (N > K) causes deaths (count decremented).

3. **Predation (Lotka-Volterra simplified)**:
   `consumed = attackRate * predatorCount * preyCount * dt`.
   `attackRate` is derived from predator speed and prey detection radius.
   Prey count decreases by `consumed`. Predator hunger satisfaction modeled as:
   predator starvation deaths reduced proportional to consumption rate.

4. **Starvation**: If `count > K * 1.2`, excess die at rate `0.05 * (count - K) * dt`.

5. **Migration**: If `count > K * 0.8`, excess individuals migrate to a random adjacent chunk
   where the species' biome affinity > 0 and that chunk's population is below 50% K. Migration
   rate: `0.02 * (count - 0.8*K) * dt` individuals per tick. This spreads populations naturally
   and prevents permanent overcrowding.

#### Chunk Load / Unload

`CreatureSpawnSystem` subscribes to chunk load/unload events from `SceneStreamingSystem`:

**On chunk load:**
1. Read `ChunkPopulationRecord` for this chunk (or generate initial population if first visit).
2. For each species with count > 0, instantiate creatures:
   - Position: seeded within chunk bounds, clustered by herd group (Poisson disk for spacing,
     with herd members offset within a 10m radius of herd center).
   - Each creature gets full ECS components: `CreatureComponent`, `CreatureAnimationComponent`,
     `CreatureRenderComponent`, `CreatureBehaviorComponent`, `CreatureDrivesComponent`,
     `HealthComponent`, `TransformComponent`.
3. Herd members share a `spawnGroupId` for social behavior.

**On chunk unload:**
1. Count surviving creatures per species (accounting for player kills since load).
2. Write updated counts back to `ChunkPopulationRecord`.
3. Remove all creature entities from the ECS engine.

**Initial population (first visit to a chunk):**
1. Query chunk's `BiomeType`.
2. Filter species whose biome affinity for this type is > 0.
3. Seeded weighted selection → 2–5 species per chunk.
4. For each selected species, initial count = seeded roll in range `[0.5*K, 0.8*K]`.
5. Store as new `ChunkPopulationRecord`.

### Activity Cycle

The game's existing day/night cycle provides a normalized time-of-day value (0–1, where
0.25 = noon, 0.75 = midnight). This drives a per-creature `activityFactor`:

| Activity Cycle | Peak Activity | activityFactor calculation |
|---------------|---------------|---------------------------|
| DIURNAL | Day (0.1–0.4) | High during day, near-zero at night |
| NOCTURNAL | Night (0.6–0.9) | High at night, near-zero during day |
| CREPUSCULAR | Dawn/Dusk (0.0–0.1, 0.4–0.6) | Peaks at transitions, low at noon and midnight |

`activityFactor` modulates:
- Detection radius: `effective = base * activityFactor` (sleeping creatures don't notice threats).
- Wander frequency: low-activity creatures spend most time in IDLE.
- Hunger rate: reduced during low-activity periods (resting metabolism).

### Biome Spawn Table

`BiomeSpawnTable` is built from species data at load time. It maps each `BiomeType` to a
weighted list of eligible species. `CreatureSpawnSystem` queries this table when generating
initial populations for newly visited chunks.

### New Types

```
fauna/behavior/
  CreatureBehaviorSystem.java      — Ashley system, per-entity state machine
  CreatureState.java               — enum: IDLE, WANDER, ALERT, FLEE, HUNT, ATTACK, FEED
  CreatureBehaviorComponent.java   — holds StateMachine, home position, spawn group ID
  CreatureDrivesComponent.java     — hunger, energy, fear floats
  CreatureDriveSystem.java         — ticks drives each frame
  HerdAlertEvent.java              — event bus event for social flee propagation

fauna/ecosystem/
  SpeciesDef.java                  — data model for species JSON
  ChunkPopulationRecord.java       — per-chunk population state
  SpeciesPopulation.java           — per-species count within a chunk
  PopulationTickSystem.java        — Lotka-Volterra population math
  CreatureSpawnSystem.java         — chunk load → creature instantiation
  BiomeSpawnTable.java             — biome → weighted species list

data/fauna/species/
  default-species.json             — shipped species definitions
```

### Data Changes

- New JSON directory `data/fauna/species/`.
- `FaunaDataRegistry` extended with species loading and validation.
- `CreatureGenerator.generate()` gains a `SpeciesId` overload.
- `CreatureFactory` extended to attach behavior and drive components.

---

## Cross-Cutting Concerns

### Cycle Dependencies

```
A (done) → B (skeleton) → C (skin)
                        ↘
A (done) ──────────────→ D (behavior/ecosystem)
```

B must complete before C (skin shader needs the single-model skinned mesh from B, not the
multi-ModelInstance layout from A). D depends only on A (behavior doesn't need animation or
skin — it drives movement that the gait system animates). However, D benefits from B being
done first so creatures animate while behaving.

Recommended implementation order: **B → C → D**.

### Performance Budget

| System | Per-frame cost | Scaling |
|--------|---------------|---------|
| CreatureGaitSystem | ~0.1ms per creature (IK + bone updates) | Max ~50 active creatures in view |
| creature_skin.frag | ~0.05ms per creature (noise + pattern eval) | GPU-bound, trivial per-pixel cost |
| CreatureBehaviorSystem | ~0.02ms per creature (state machine eval) | Max ~100 active creatures in loaded chunks |
| CreatureDriveSystem | ~0.005ms per creature (3 float ticks) | Negligible |
| PopulationTickSystem | ~1ms total (runs every 30s) | Covers ~100 chunks, purely arithmetic |

Total creature budget target: < 5ms per frame with 50 visible, 100 loaded creatures.

### Testing Strategy

All three cycles follow Cycle A's pattern: logic is GL-free and testable headless.

**Cycle B tests:**
- `CreatureRigBuilder` produces correct bone count and hierarchy per archetype.
- `TwoBoneIKSolver` solves known geometric cases.
- Gait controllers produce deterministic bone transforms given identical inputs.
- Bone roles map correctly from `jointHint` strings.

**Cycle C tests:**
- `PaletteGenerator` is deterministic: same `(colorSeed, biome)` → identical palette.
- `PatternStamper` produces vertex colors with expected channel semantics (dorsal vertices have
  R ≈ 0, ventral have R ≈ 1).
- Pattern type selection is biome-weighted and seeded.

**Cycle D tests:**
- State machine transitions: IDLE→WANDER→ALERT→FLEE given appropriate threat distances.
- Herd alert propagation: one flee triggers group flee.
- Population tick: logistic growth converges to K, predation reduces prey.
- Migration: overpopulated chunk sheds to neighbors.
- `BiomeSpawnTable` filters species correctly by biome affinity.
- Species validation catches invalid predator-prey references and trophic inconsistencies.
- Initial population is seeded and deterministic.

### Seed Derivation

Following `procgen-seed-reproducibility`, all new seeded operations derive from the existing
`SeedDeriver.faunaDomain(seed)` chain:

- Rig construction: deterministic from `CreatureSpec` (no additional seed needed).
- Skin: `colorSeed` already on `CreatureSpec` (set in Cycle A).
- Species selection per chunk: `SeedDeriver.forChunk(faunaSeed, chunkX, chunkZ)`.
- Creature placement within chunk: `SeedDeriver.forId(chunkSeed, creatureIndex)`.
- Drive initial values: seeded from creature's individual seed to vary hunger/energy at spawn.
