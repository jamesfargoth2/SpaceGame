# Ship Systems

The `ship` package covers the full lifecycle of a ship entity: procedural generation of the hull and interior, six-degrees-of-freedom flight physics, combat weapons and turrets, structural integrity, thermal management, life support, fluid dynamics, and docking.

---

## Procedural Ship Generation

**`ShipFactory`**

Entry point for creating ship entities. Accepts a `ShipBlueprint` (seed, size class, spine length, cross-section count, wing/engine counts), orchestrates the generation pipeline, and returns an Ashley `Entity` with all required components attached.

Pipeline:
1. `ShipHullGenerator` → `HullGeometry` (mesh + convex collision shape)
2. `ShipInteriorGenerator` → `InteriorLayout` (rooms, corridors, airlock positions)
3. Attach `ShipDataComponent`, `ShipFlightComponent`, `EngineSpecComponent`, `FuelTankComponent`, etc.
4. Create the exterior Bullet rigid body from `HullGeometry`.
5. Create a separate `btDynamicsWorld` for the interior.

**`ShipHullGenerator`**

Procedurally builds the exterior hull mesh using a spine curve and parametric cross-sections. The resulting mesh is used for rendering and as the source for the convex-hull collision shape.

**`ShipInteriorGenerator`**

Voxelizes the interior volume of the hull, then runs a room-packing pass to place rooms by purpose (`RoomPurpose`): living quarters, engine room, cargo bay, bridge, medical bay, etc. Corridors are generated on a grid between rooms. Outputs floor/wall meshes and an `InteriorLayout` with room placement records, corridor grid, airlock position, and pilot seat position.

**`ShipBlueprint`**

Seed-reproducible parameters for a ship. Changing any parameter produces an entirely different ship; same parameters always produce the same ship.

---

## Flight Physics

**`ShipFlightSystem`** (priority 3)

Reads pilot input from `ShipFlightInputComponent` (throttle, pitch, yaw, roll, lateral/vertical strafe) and applies thrust and torque to the ship's Bullet rigid body via `EngineSpecComponent` (thrust profile, throttle response curve). Consumes fuel each frame proportional to throttle level via `FuelTankComponent`. Applies relativistic time dilation factor from `ShipFlightComponent` when relevant.

**`AtmosphericFlightSystem`**

Adds aerodynamic forces during planetary flight. Works in tandem with `AeroForceSystem` (in `ship/atmosphere/`).

**`PropulsionSystem`**

High-level propulsion integration that monitors fuel tank depletion and throttles engine thrust accordingly.

**`ShipCameraSystem`**

Follows the ship's orientation for the third-person camera when the player is piloting.

**`RelativisticDopplerSystem`** / **`VelocityTimeDilationSystem`**

At extreme velocities, shift visual colour (Doppler) and scale local time (twin-paradox dilation) for the piloted ship.

### Atmospheric Sub-systems (`ship/atmosphere/`)

| System | What it does |
|---|---|
| `AeroForceSystem` | Applies lift and drag from `AeroBodyComponent` (coefficients, reference area) |
| `EntryHeatSystem` | Generates heat from aerodynamic compression during re-entry; feeds into `ThermalSystem` |

---

## Ship Weapons (`ship/weapons/systems/`)

| System | What it does |
|---|---|
| `ShipWeaponSystem` | Fires turret weapons, tracks heat via `ShipWeaponHeatComponent`, manages cooldown |
| `ShipWeaponPilotSystem` | Maps pilot input to weapon firing and weapon-group selection |
| `TargetingSystem` | Locks a target and calculates lead point for projectile weapons |
| `TurretTrackingSystem` | Rotates automated turrets toward the targeting solution |
| `PointDefenseSystem` | Auto-fires on inbound projectiles within intercept range |
| `ShipProjectileSystem` | Physics for ship-scale kinetic projectiles |
| `GuidedProjectileSystem` | Proportional navigation for homing missiles (`GuidedProjectileComponent`) |
| `ShipHeatSystem` | Dissipates weapon heat over time; triggers heat warnings |

---

## Ship Interior Physics

**`ShipInteriorPhysicsSystem`**

Steps the interior `btDynamicsWorld` each frame independently of the exterior world. The interior world is attached to the ship entity and translates/rotates with it in galaxy-space, but objects inside move in ship-local coordinates — keeping FPS physics correct even as the ship manoeuvres.

---

## Structural Integrity (`ship/structure/`)

| Class | Role |
|---|---|
| `StructuralIntegritySystem` | Tracks per-zone hull integrity; triggers `DamageCascadeSystem` when a zone fails |
| `DamageCascadeSystem` | Propagates structural failures to adjacent zones (e.g. hull breach → pressure loss) |
| `GForceSystem` | Computes g-load on crew and equipment from rapid acceleration; damages both if tolerance exceeded |
| `AtmosphereVentSystem` | Vents atmosphere from breached compartments into vacuum |
| `StructuralIntegrityComponent` | Hull integrity value per structural zone |
| `GForceToleranceComponent` | Maximum safe g-load for a crew member or piece of equipment |

---

## Thermal Management (`ship/thermal/`)

| Class | Role |
|---|---|
| `ThermalSystem` | Accumulates heat from all sources (weapons, engines, re-entry) and dissipates via radiators |
| `ThermalDamageSystem` | Applies damage to components when heat exceeds their rated temperature |
| `ThermalPenaltySystem` | Degrades engine thrust and weapon accuracy as heat rises |
| `ThermalStateComponent` | `currentHeat`, `maxHeat`, `dissipationRate`, per-source heat map |

---

## Life Support (`ship/lifesupport/`)

| Class | Role |
|---|---|
| `LifeSupportSystem` | Models O₂ generation (electrolysis), CO₂ scrubbing, and trace gas management |
| `AtmosphereHealthSystem` | Tracks atmosphere quality per compartment; warns at thresholds |
| `CrewMetabolicSystem` | Deducts O₂ based on crew count and activity level each tick |
| `FirePhysicsSystem` | Simulates fire ignition, spread between compartments, and suppression |
| `CompartmentAtmosphereComponent` | Per-compartment: O₂/CO₂/pressure levels |
| `FireComponent` | Fire intensity, fuel remaining, suppression progress |

---

## Ship Flooding (`ship/flooding/`)

| Class | Role |
|---|---|
| `ShipFloodingSystem` | Tracks water ingress rate through breaches; updates ship stability |
| `FloodingHudSystem` | Renders the flooding status overlay |
| `ShipFloodingComponent` | Per-compartment flood level, ingress rate |
| `ShipStabilityComponent` | Trim (fore/aft) and list (port/starboard) angles derived from flood distribution |
| `BreachRepairableComponent` | Marks a breach as player-repairable with progress tracking |

---

## Fluid Dynamics in Zero-G (`ship/fluid/`)

| Class | Role |
|---|---|
| `SloshSystem` | Simulates fluid movement within tanks under acceleration |
| `SloshTorqueSystem` | Couples slosh momentum into the ship's angular dynamics |
| `PropellantSettlingSystem` | Forces propellant to tank outlets in zero-g using settling burns |
| `CryoTankSystem` | Manages cryogenic propellant: boil-off, pressure relief |
| `SloshTankComponent` | Tank geometry, current fluid fill, sloshing momentum |
| `CryoTankStateComponent` | Cryo state: temperature, boil-off rate, pressure |

---

## Docking (`ship/docking/`)

| Class | Role |
|---|---|
| `DockingApproachSystem` | Provides guidance cues while the player aligns for docking |
| `DockingCaptureSystem` | Engages docking clamps when alignment and velocity thresholds are met |
| `HardDockConstraintSystem` | Creates a fixed Bullet constraint between docked ships, synchronising their motion |
| `DockingStateComponent` | State machine: `FREE` → `APPROACHING` → `ALIGNED` → `DOCKED` |
| `DockingPortComponent` | Port position, axis, and compatible port type |

---

## Data & Registries

| Class | Content |
|---|---|
| `ShipClassRegistry` | Loads `data/ships/` JSON files; provides class stats by class ID |
| `ShipClassData` | Per-size-class parameters: base thrust, turn rate, max speed, hull HP, cargo volume |
| `ShipWeaponRegistry` | Ship-scale weapon definitions |
| `ShipWeaponData` | Weapon stats: damage, range, heat generation, reload time |

---

## Components Reference

| Component | Key fields |
|---|---|
| `ShipDataComponent` | Blueprint reference, mass, thrust, turn rate, hull HP |
| `ShipFlightComponent` | Drag coefficients, throttle state, time-dilation factor |
| `ShipFlightInputComponent` | Throttle, pitch, yaw, roll, strafe X/Y, boost flag |
| `EngineSpecComponent` | Thrust curve, throttle response lag, specific impulse |
| `FuelTankComponent` | Current/max fuel, consumption rate at full throttle |
| `ShipInteriorComponent` | Interior `btDynamicsWorld` reference, active flag |
| `PilotSeatComponent` | World position of pilot seat |
| `ShipEntryPointComponent` | Airlock world position for player boarding |
| `ShipAerodynamicsComponent` | Lift/drag coefficients, reference area, centre of pressure |
| `CockpitRenderComponent` | Cockpit interior mesh reference and animation state |
| `ShipMeshComponent` | Deferred hull mesh handle |
| `ShipHardpointComponent` | Weapon mount points: local position, facing, compatible weapon types |
