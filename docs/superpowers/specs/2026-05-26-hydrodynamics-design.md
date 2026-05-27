# Hydrodynamics System Integration

Integrate boat, ship, and submarine water physics into Galactic Odyssey. Curates the best implementations from existing eval-generated code, removes duplicates, consolidates events, and wires everything into GameWorld.

## Scope

- Surface vessel physics: Gerstner waves, multi-point buoyancy, hydrodynamic drag, wake trails
- Submarine physics: ballast tanks, PID depth control, hull integrity, crush depth
- Interior flooding: breach ingress (Torricelli), cross-compartment orifice flow, free surface stability, capsize
- Flooding HUD: Scene2D warning banners, compartment gauges, stability readout
- Ocean spawning: WaterBodyComponent created when player enters ocean biome on a planet
- Alien fluid support: density/viscosity swaps for methane seas, lava, ammonia, etc.

## Architecture

All water physics lives in `com.galacticodyssey.water/`. Ship-specific flooding integration (HUD, event-driven breach handling, repair mechanics) lives in `com.galacticodyssey.ship.flooding/`.

### Package Layout

```
water/
  WaterBodyComponent.java        — fluid properties, wave params, current
  WaveParams.java                — single Gerstner wave contribution
  BuoyancySamplePoint.java       — hull surface patch struct
  HullComponent.java             — hull geometry, drag coefficients, crush depth
  WakeComponent.java             — Kelvin wake data for rendering
  Compartment.java               — watertight compartment (merged: +sealed, +waterHead)
  FloodingComponent.java         — compartment flooding state (physics-only)
  BallastTank.java               — single ballast tank struct
  WaveQuery.java                 — stateless wave height/normal/velocity utility
  
  components/
    BallastTankComponent.java    — array of BallastTanks with utility methods
    DepthControlComponent.java   — PID gains, integral state, anti-windup
    SubmarineHullComponent.java  — crush depth, integrity, breach count
    SubmarineStateComponent.java — state machine (SURFACED/DIVING/SUBMERGED/...)
    BuoyancyComponent.java       — net buoyancy state, metacentric height
    BoatInputComponent.java      — throttle/steering [-1,1]
    BoatMotorComponent.java      — thrust, rudder torque, response rates
  
  events/
    VesselEnteredWaterEvent.java
    VesselExitedWaterEvent.java
    HullBreachEvent.java
    FloodingStartedEvent.java
    CompartmentFloodedEvent.java
    StabilityWarningEvent.java
    CapsizeEvent.java
    DepthWarningEvent.java
    BallastChangedEvent.java
    BreachSealedEvent.java       — moved from ship/flooding/
    SubmarineHullBreachEvent.java
  
  systems/
    WaveSystem.java              — priority 10, Gerstner surface evaluation
    BoatBuoyancySystem.java      — priority 11, per-point hull-normal buoyancy
    HydrodynamicDragSystem.java  — priority 12, skin friction + form + wave-making
    BallastSystem.java           — priority 13, PID depth control + setMassProps
    FloodingSystem.java          — priority 14, Torricelli + orifice + free surface
    HullIntegritySystem.java     — priority 15, crush depth monitoring
    BoatMotorSystem.java         — priority 16, thrust + rudder forces
    WakeTrailSystem.java         — priority 17, wake trail for rendering
  
  data/
    SubmarineData.java           — JSON-loadable submarine spec
    CompartmentDefinition.java   — JSON-loadable compartment layout
  
  FishingBoatFactory.java        — 16-point fishing boat entity
  SubmarineFactory.java          — data-driven submarine entity
  OceanSpawner.java              — NEW: spawns WaterBodyComponent for ocean planets

ship/flooding/
  components/
    ShipFloodingComponent.java   — RENAMED from FloodingComponent (HUD metadata)
    BreachRepairableComponent.java
    ShipStabilityComponent.java
  DoorwayConnection.java         — orifice model for internal passages
  systems/
    ShipFloodingSystem.java      — RENAMED, priority 18, event-driven flooding wrapper
    FloodingHudSystem.java       — priority 50, Scene2D warnings + gauges
  FloodableShipFactory.java      — 3-compartment cargo ship builder
```

### Files to Delete

These are duplicate or inferior implementations from the eval agents:

```
water/systems/VesselBuoyancySystem.java      — duplicate of BoatBuoyancySystem
water/systems/SubmarineBuoyancySystem.java   — less accurate height-fraction approach
water/systems/SubmarineWaveSystem.java       — redundant, WaveSystem handles all queries
water/systems/BallastControlSystem.java      — redundant, BallastSystem has PID built in
water/systems/DepthControlSystem.java        — merged into BallastSystem
water/systems/WaterDragSystem.java           — HydrodynamicDragSystem is superior
water/components/WaterDragComponent.java     — goes with WaterDragSystem
ship/flooding/Compartment.java               — merged into water/Compartment
ship/flooding/events/*.java                  — consolidated into water/events/
```

### Files to Modify

**water/Compartment.java** — merge from ship/flooding/ version:
- Add `boolean sealed` field
- Add `float waterHead(float compartmentHeight)` method

**ship/flooding/FloodingComponent.java** → **ShipFloodingComponent.java**:
- Rename class
- Import `Compartment` from `water/` instead of local
- Import events from `water.events/`

**ship/flooding/FloodingSystem.java** → **ShipFloodingSystem.java**:
- Rename class
- Import events from `water.events/`

**ship/flooding/FloodingHudSystem.java**:
- Update imports for renamed ShipFloodingComponent
- Import events from `water.events/`

**ship/flooding/FloodableShipFactory.java**:
- Update imports for renamed component and consolidated events

**core/GameWorld.java** — register all water systems (see System Registration below)

### System Registration in GameWorld

```java
// --- Water/Hydrodynamics ---
WaveSystem waveSystem = new WaveSystem(10);
engine.addSystem(waveSystem);
engine.addSystem(new BoatBuoyancySystem(11, waveSystem, coordinateManager, eventBus));
engine.addSystem(new HydrodynamicDragSystem(12));
engine.addSystem(new BallastSystem(13, waveSystem, eventBus));
engine.addSystem(new FloodingSystem(14, eventBus));
engine.addSystem(new HullIntegritySystem(15, eventBus));
engine.addSystem(new BoatMotorSystem(16));
engine.addSystem(new WakeTrailSystem(17));
engine.addSystem(new ShipFloodingSystem(18, eventBus));
engine.addSystem(new FloodingHudSystem(50, stage, eventBus));
```

Systems that need external dependencies receive them via constructor:
- `waveSystem` — BuoyancySystem and BallastSystem query wave heights
- `coordinateManager` — BuoyancySystem converts to galaxy-space for wave phase
- `eventBus` — systems that publish/subscribe to water events
- `stage` — FloodingHudSystem needs the Scene2D stage for UI actors

### OceanSpawner (New)

Listens for planet entry events. When the player reaches a planet with `seaLevel > 0` and the terrain system reports ocean biome underfoot, spawns a `WaterBodyComponent` entity with properties from a lookup table:

| Planet Type | Fluid | Density | Viscosity | Wave Amplitudes |
|-------------|-------|---------|-----------|-----------------|
| Earthlike | Seawater | 1025 | 1.19e-6 | 0.5–2.0m |
| Titan-type | Methane | 450 | 2.2e-7 | 0.2–0.8m |
| Volcanic | Lava | 2700 | 1e3 | 0.1–0.3m |
| Ice world | Ammonia | 680 | 3.5e-7 | 0.3–1.2m |
| Hypersaline | Brine | 1200 | 1.5e-6 | 0.4–1.5m |

Wave params (count, directions, wavelengths) are procedurally generated per-planet from the planet seed for consistency across visits.

### Dependencies Between Systems

```
WaveSystem (10)
  ↓ queried by
BoatBuoyancySystem (11) ←→ HullComponent + PhysicsBodyComponent
  ↓ runs before
HydrodynamicDragSystem (12) ←→ HullComponent + PhysicsBodyComponent
  ↓
BallastSystem (13) ←→ BallastTankComponent + DepthControlComponent
  ↓
FloodingSystem (14) ←→ FloodingComponent
  ↓
HullIntegritySystem (15) ←→ SubmarineHullComponent
  ↓
BoatMotorSystem (16) ←→ BoatMotorComponent + BoatInputComponent
  ↓
WakeTrailSystem (17) ←→ WakeComponent
  ↓
ShipFloodingSystem (18) ←→ ShipFloodingComponent (event-driven)
  ↓
FloodingHudSystem (50) ←→ ShipFloodingComponent (reads for display)
```

### Floating-Origin Safety

All wave phase calculations use galaxy-space doubles via `CoordinateManager.toGalaxySpace()`. Local-space floats are used only for force application. This prevents wave discontinuities on origin rebase.

The `WaveSystem.getHeight(double gx, double gz)` signature enforces this — callers must convert before querying.

### Testing

Each system is testable without a GL context (per CLAUDE.md rule 5):
- WaveSystem: verify wave height at known galaxy coordinates
- BuoyancySystem: verify upward force proportional to submersion depth
- HydrodynamicDragSystem: verify quadratic drag, wave-making hump at Froude 0.45
- BallastSystem: verify PID convergence to target depth
- FloodingSystem: verify Torricelli flow rate, conservation of water volume across compartments
- HullIntegritySystem: verify breach events at crush depth
