# Water & Submarine Systems

The `water` package simulates aquatic environments at multiple scales: ocean surface dynamics, player swimming and diving, surface vessel operation, and fully modelled submarine operations including ballast, flooding, and pressure.

---

## Player Swimming & Diving

**`SwimmingSystem`**

Drives the player's underwater movement. On each frame it evaluates:

- **Immersion level** — depth relative to water surface. Partial immersion triggers wading physics; full immersion activates swimming forces.
- **Swim state machine** — transitions: `DRY` → `WADING` → `SWIMMING` → `DIVING` → `UNDERWATER`. Written to `SwimmingStateComponent.swimmingState`.
- **Breath hold** — while underwater without dive gear, depletes `SwimmingStateComponent.breathRemaining` each frame. When exhausted, health begins draining.
- **Stamina drain** — active swimming burns `SwimmingStateComponent.stamina`; exhaustion slows stroke speed.
- **Depth** — tracks current depth in metres; used by `DepthZoneComponent` lookup to determine pressure effects.

**`DiveGearComponent`** stores equipped oxygen tank capacity and weight. With tanks, breath is replaced by the tank supply. `DiveGearDefinition` loads the gear stats from data.

**`HydrodynamicDragSystem`**

Applies drag forces proportional to velocity squared while the entity is immersed. Drag coefficient is read from `BuoyancyComponent` (or player capsule defaults). This replaces the normal air-drag model for submerged entities.

**`BuoyancySystem`**

Computes buoyant force as `ρ·V·g` where V is the submerged volume at the current waterline. Applies an upward force to counteract gravity, allowing the player and objects to float.

---

## Ocean Surface & Waves

**`WaveSystem`**

Generates a wave height field using sum-of-sines superposition. Outputs heights at query positions; used by the buoyancy system for accurate waterline detection and by the renderer for wave mesh generation.

**`WakeTrailSystem`**

Spawns `WakeComponent` entities behind fast-moving surface vessels. Wake visualisation fades over time.

**`DeckWashSystem`**

Applies spray forces to entities on the deck of a surface vessel during rough weather.

---

## Surface Vessels

**`BoatBuoyancySystem`**

Extended buoyancy for multi-hull vessels. Samples waterline at multiple points along the hull, computes restoring moments, and applies trim and list forces to keep the vessel stable.

**`BoatMotorSystem`**

Reads `BoatInputComponent` (throttle, rudder angle) and applies thrust and yaw torque via the Bullet rigid body on `BoatMotorComponent`.

**`VesselFactory`** / **`FishingBoatFactory`**

Create surface vessel entities with appropriate components. `FishingBoatFactory` additionally spawns an NPC crew.

**`OceanSpawner`**

Populates the ocean with NPC vessels, fish, and environmental objects within streaming range.

---

## Weather

**`WeatherStateComponent`**

Stores the current weather phase (`WeatherPhase`: calm, squall, storm, hurricane) and intensity. Affects wave amplitude in `WaveSystem` and drag in `HydrodynamicDragSystem`.

**`StormCellComponent`**

A moving storm centre entity. The storm system advances storm cells over time; nearby vessels and players receive increased wave action and wind drag.

`StormConfigData` and `WeatherProfileData` loaded from `data/water/` define storm spawn rates and intensity curves.

---

## Submarines

### Depth & Pressure

**`DepthZoneComponent`**

Each ocean body is divided into depth zones defined by `DepthZone` (surface / twilight / abyssal / hadal). Each zone has:
- Pressure rating (atmospheres)
- Visibility distance
- NPC fauna set
- Ambient audio cue

Submarines must have a `SubmarineHullComponent` rated for the target zone or risk hull failure.

### Ballast Control

**`BallastSystem`**

Manages ballast tank fill to control buoyancy-driven depth changes:
- Filling tanks adds negative buoyancy (descend).
- Blowing tanks restores positive buoyancy (ascend).
- Emergency blow (CO₂-driven) provides rapid ascent at the cost of tank gas supply.

**`BallastTankComponent`**

Per-tank state: fill level, max capacity, vent rate, blow rate, gas remaining.

**`DepthControlComponent`**

High-level dive controller. Sets target depth; drives ballast commands to achieve it while maintaining trim.

### Flooding & Hull Integrity

**`FloodingSystem`**

Tracks water ingress through hull breaches. Ingress rate per breach depends on:
- Depth (pressure drives faster ingress).
- Breach area (`BreachRepairableComponent`).
- Whether the hatch is sealed (`HatchComponent.state`).

Flooded compartments gain mass, pulling the bow or stern down and affecting trim.

**`HatchFloodingSystem`**

Handles the specific case of open or improperly sealed hatches (`Hatch` enum: SEALED / UNSEALED / DAMAGED). An unsealed hatch at depth floods at a high rate.

**`HullIntegritySystem`**

Monitors cumulative hull stress from depth pressure and impact. When `SubmarineHullComponent.integrity` drops to zero, triggers catastrophic flooding.

### Submarine State Machine

`SubmarineStateComponent` transitions: `SURFACE` → `DIVING` → `SUBMERGED` → `ASCENDING` → `SURFACE`.

**`SubmarineData`** loaded from `data/water/submarines/` defines rated depth, ballast capacity, hull integrity, and module complement for each submarine class.

---

## Data Reference

| Class | Content |
|---|---|
| `WaterDataRegistry` | Central registry; loads all water-related JSON data files |
| `VesselRegistry` / `VesselData` | Surface vessel class definitions |
| `SubmarineData` | Submarine specifications |
| `SwimConfigData` | Swimming balance: stamina drain rate, breath capacity, dive speed |
| `DepthZonesConfig` / `DepthZoneData` | Depth zone pressure and hazard definitions |
| `StormConfigData` | Storm spawn, intensity, and movement parameters |
| `CompartmentDefinition` | Submarine interior compartment layout |

---

## Components Reference

| Component | Key fields |
|---|---|
| `SwimmingStateComponent` | `swimmingState`, `breathRemaining`, `stamina`, `immersionDepth` |
| `DiveGearComponent` | O₂ tank capacity, remaining O₂, weight |
| `BuoyancyComponent` | Submerged volume, reference density, buoyancy force |
| `BoatInputComponent` | Throttle, rudder angle, reverse flag |
| `BoatMotorComponent` | Max thrust, current RPM, rudder torque |
| `SubmarineStateComponent` | Current state enum, target depth, emergency blow flag |
| `SubmarineHullComponent` | Rated depth, current integrity, hull material |
| `BallastTankComponent` | Fill level, capacity, vent/blow rates |
| `DepthControlComponent` | Target depth, current depth, trim error |
| `FloodableCompartmentComponent` | Current flood level, max volume, ingress rate |
| `WaterBodyComponent` | Water body bounds, surface elevation, salinity |
| `WeatherStateComponent` | Phase enum, intensity, wind direction |

---

## Events Reference

| Event | When published |
|---|---|
| `HullBreachEvent` | Hull integrity reaches a breach threshold |
| `FloodingStartedEvent` | First water enters a compartment |
| `BreachSealedEvent` | Player successfully seals a breach |
| `CapsizeEvent` | Vessel heel exceeds capsize angle |
| `StabilityWarningEvent` | Stability margin drops below warning threshold |
