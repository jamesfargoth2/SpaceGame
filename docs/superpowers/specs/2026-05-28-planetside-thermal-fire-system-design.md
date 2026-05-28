# Planetside Thermal & Fire System — Design

**Date:** 2026-05-28
**Status:** Approved (design); pending implementation plan

## 1. Goal & Scope

A **general per-object temperature simulation** for world/surface gameplay, paired
with a **wildfire propagation model**. Objects drift toward their environment's
ambient temperature and respond to active heat/cold sources (flamethrowers, fire,
lava, cryo weapons, cold biomes). When an object crosses its material's **ignition
point** it catches fire; when it drops below its **freeze point** it freezes.

Primary use cases:
- Forest fires spreading across planet vegetation, biased by wind, moisture, and O₂.
- Flamethrower / incendiary weapons igniting objects and terrain.
- Objects (crates, props, characters, dropped items) freezing in cold biomes or under
  cryo effects.

### Out of scope / explicitly untouched
- **Ship subsystem thermal** (`ship/thermal/`: `ThermalStateComponent`, `ThermalSystem`,
  `ThermalPenaltySystem`, `ThermalDamageSystem`) — engine/hull/weapon/reactor heat stays
  a **separate, independently managed** system. Not migrated, not refactored.
- **Ship compartment fire** (`ship/lifesupport/`: `FireComponent`, `FirePhysicsSystem`,
  `CompartmentAtmosphereComponent`) — interior life-support fire stays separate.
- The **full flamethrower weapon definition** (weapon stats, model, animation). Only the
  **thermal hook** it uses (a transient `HeatSourceComponent`) is in scope here.

## 2. Placement

New code lives under the existing `planet/` package (which already contains `climate`,
`terrain`, `atmosphere`) — no new top-level package, consistent with CLAUDE.md.

```
core/src/main/java/com/galacticodyssey/planet/
  thermal/     TemperatureComponent, HeatSourceComponent, BurningComponent,
               FrozenComponent, ThermalMaterial, ThermalMaterialRegistry,
               ThermalEnvironment (interface) + PlanetSurfaceEnvironment,
               HeatSourceSystem, ObjectTemperatureSystem,
               thermal/events/...
  fire/        FuelGridComponent, WildfireSystem, CombustionSystem,
               fire/events/...
core/src/main/resources/data/thermal/
  materials.json
```

The simulation is engine-neutral in principle (driven by the `ThermalEnvironment`
abstraction), so a future space/interior environment provider can reuse the same
components and systems without moving them.

## 3. Data-Driven Materials

`ThermalMaterialRegistry` loads `data/thermal/materials.json`, mirroring the existing
`WeaponDataRegistry` / `CombatDataRegistry` load pattern (libGDX `Json` +
`Map<String, ThermalMaterial>`).

`ThermalMaterial` fields:

| Field | Unit / type | Purpose |
|---|---|---|
| `id`, `name` | String | Identity |
| `specificHeat` | J/(kg·K) | Combined with mass → thermal mass |
| `emissivity` | 0–1 | Radiative cooling coefficient |
| `ignitionPoint` | K | At/above → ignites (if `flammable`) |
| `freezePoint` | K | At/below → freezes (if `freezable`) |
| `flammable` | bool | Can ignite at all |
| `freezable` | bool | Can freeze at all |
| `flammability` | 0–1 | Ease of ignition & spread probability weight |
| `combustionEnergy` | J/kg | Fuel energy released while burning |
| `burnHeatOutput` | W (at intensity 1) | Heat emitted while burning |
| `consumedWhenBurnt` | bool | Whether the object is destroyed when fuel exhausts |
| `charMaterialId` | String (nullable) | Material it becomes after burning (e.g. "ash") |
| `frozenSpeedMultiplier` | 0–1 | Movement penalty when frozen |
| `brittleWhenFrozen` | bool | Shatters on impact when frozen |

Nothing about thresholds or behavior is hardcoded; all comes from data.

## 4. Components

- **`TemperatureComponent`** (any entity participating in the sim):
  `float temperature` (K), `float thermalMass` (J/K), `float surfaceArea` (m²),
  `String materialId` (+ cached resolved `ThermalMaterial`),
  `enum State { NORMAL, BURNING, FROZEN }`,
  `float incomingHeat` (W — per-frame scratch accumulator, reset each tick).
- **`HeatSourceComponent`** (emitters: flamethrower cone, lava patch, cryo field):
  `float power` (W; negative = cooling), emitter shape (`radius` for sphere, or cone
  `direction`/`halfAngle`/`range`), falloff curve, optional `lifetime` for transient
  sources.
- **`BurningComponent`** (added on ignition, removed on extinguish/consume):
  `float intensity` (≥0), `float fuelRemaining` (J), `float heatOutput` (W).
- **`FrozenComponent`** (added on freeze, removed on thaw):
  `float frozenFraction` (0–1) for ramping penalties in/out.
- **`FuelGridComponent`** (one per active surface scene): parallel 2D arrays
  `float[] fuelLoad`, `float[] moisture`, `byte[] state`
  (`UNBURNT/IGNITING/BURNING/BURNT`), `float[] burnTimer`; grid origin + cell size for
  grid↔world mapping; an **active-cell index list** so the wildfire system only visits
  burning/igniting cells. Seeded at scene load from `BiomeMap` density + moisture.

## 5. Environment Provider

```java
interface ThermalEnvironment {
    float ambientTemp(Vector3 localPos);    // 3K space / star flux / biome temp / compartment
    float oxygenFraction(Vector3 localPos); // gates combustion
    Vector3 wind(Vector3 localPos);          // biases wildfire spread
}
```

`PlanetSurfaceEnvironment` wires to existing APIs:
- ambient temp ← `BiomeMap.getTemperature(lat, lon)` (delegates to `ClimateData`)
- moisture ← `BiomeMap.getMoisture(lat, lon)`
- wind ← `ClimateData.windU/windV` sampled at the location
- O₂ ← `planet.atmosphere.composition.getOrDefault(Gas.O2, 0f)`

This keeps the sim decoupled from world specifics (CLAUDE.md rules 3 & 5) and makes the
systems mockable in isolation tests.

## 6. Systems (Ashley `EntitySystem`, priority-ordered)

Existing reference priorities: `DamageSystem`=8, ship `ThermalSystem`=10,
`ThermalPenaltySystem`=11, ship `FirePhysicsSystem`=14. New systems slot into the
local-scene simulation band:

1. **`HeatSourceSystem`** (~11): iterate `HeatSourceComponent` emitters; for each, a
   brute-force radius/cone query over in-scene `TemperatureComponent` entities and
   `FuelGridComponent` cells (same O(n)-local pattern as `ExplosionSystem`), depositing
   watts (with falloff) into each target's `incomingHeat`. Decrements transient emitter
   lifetimes.
2. **`ObjectTemperatureSystem`** (~12): per `TemperatureComponent` entity — resolve
   ambient via `ThermalEnvironment`; compute radiative cooling (Stefan-Boltzmann
   `εσA(T⁴ − T_amb⁴)`) + conduction toward ambient + `incomingHeat`; integrate
   `temperature += (Q_in − Q_out)/thermalMass · dt`; reset `incomingHeat`. On threshold
   crossings, **publish events** (`IgnitionEvent`, `FreezeEvent`, `ThawEvent`) rather
   than mutating other systems directly. Equilibrium objects with no nearby source
   "sleep" (skip integration) for performance.
3. **`WildfireSystem`** (~13): process the **active-cell list** only. Each `BURNING`
   cell consumes fuel, emits heat, and probabilistically ignites neighbor cells weighted
   by `flammability`, `moisture`, **wind alignment**, and local O₂; cells with exhausted
   fuel → `BURNT`. Two-way coupling: burning cells add heat to nearby
   `TemperatureComponent` entities' `incomingHeat`; entities with `BurningComponent` over
   flammable cells raise those cells' ignition chance.
4. **`CombustionSystem`** (~14): `BurningComponent` lifecycle — burn `fuelRemaining`
   down by `combustionEnergy` rate; apply burning **damage-over-time** to
   `HealthComponent` through the existing damage model / `BURNING` status effect; when
   fuel exhausts, **consume** the entity (remove, or swap material to `charMaterialId`);
   extinguish if O₂ drops below threshold or the fire is doused. Also drives
   `FrozenComponent`: apply `frozenSpeedMultiplier` / disable penalties, brittle-shatter
   on impact when `brittleWhenFrozen`, and thaw (remove component) when temperature rises
   back above `freezePoint`.

### Event catalogue (`thermal/events`, `fire/events`)
`IgnitionEvent(entity)`, `ExtinguishedEvent(entity)`, `FreezeEvent(entity)`,
`ThawEvent(entity)`, `ObjectConsumedByFireEvent(entity, charMaterialId)`,
`WildfireCellIgnitedEvent(cellX, cellY, worldPos)` — consumed by VFX/audio/UI
independently (CLAUDE.md rule 3).

## 7. Effects Delivered

- **Damage over time** — `CombustionSystem` feeds the existing unified damage model /
  `BURNING` status; cold deals frostbite damage the same way.
- **Spread to neighbors** — wildfire grid propagation + entity↔cell + entity↔entity heat
  coupling.
- **State change / destruction** — burnt objects consumed → `charMaterialId` or removed;
  frozen objects become solid/brittle and can shatter.
- **Stat / behavior penalties** — `FrozenComponent` applies speed/disable penalties;
  burning hooks into the existing penalty/status systems.

## 8. Cross-Cutting Concerns

- **Floating origin (rule 1):** all thermal math runs on local-scene 32-bit floats; the
  fuel grid is mapped in local-scene / lat-lon coordinates. No galaxy-space floats.
- **Server-authoritative (rule 4):** the simulation is headless (no GL); clients consume
  ignition/freeze/extinguish/consume events for VFX and audio only.
- **Isolated testability (rule 5):** systems have no GL dependency; tests inject a stub
  `ThermalEnvironment` and synthetic fuel grids.
- **Performance:** only active-scene entities carry `TemperatureComponent`; equilibrium
  objects sleep; the wildfire system iterates only active cells, never the full grid.

## 9. Testing (JUnit 5 + Mockito)

- `ThermalMaterialRegistry` loads and indexes `materials.json`.
- `ObjectTemperatureSystem`: heats toward a hot ambient, cools toward a cold ambient,
  radiative cooling obeys T⁴, crosses ignition/freeze thresholds and emits the correct
  events (stubbed `ThermalEnvironment`).
- `HeatSourceSystem`: deposits falloff-weighted watts into in-range targets only;
  transient emitter lifetime expires.
- `WildfireSystem`: fuel depletion, wind-biased spread direction, O₂/moisture gating, and
  cell↔entity coupling on a synthetic grid.
- `CombustionSystem`: fuel burns down, DoT applied, consume→char transition, extinguish
  on low O₂; frozen penalties applied and removed on thaw.

## 10. Open Defaults (confirmed)

- **Placement:** `planet/thermal` + `planet/fire` (engine-neutral via `ThermalEnvironment`).
- **Flamethrower:** only the thermal hook (transient `HeatSourceComponent`) is in scope;
  full weapon definition is separate future work.
