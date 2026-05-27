# Swimming & Water Mechanics — Design Spec

**Date:** 2026-05-27  
**Scope:** Player swimming, underwater exploration, dynamic weather/waves, ship-water interaction  
**Reference style:** Sea of Thieves  

## 1. Overview

This spec adds four interconnected systems to the existing water infrastructure:

1. **SwimmingSystem** — player water detection, swim states, buoyancy, breath/pressure
2. **UnderwaterSystem** — depth zones, visibility falloff, pressure damage, dive gear
3. **WeatherSystem** — storm state machine driving wave parameters and ocean currents
4. **DeckWashSystem** — wave overtopping, interior flooding via hatches, bailing/repair loop

These layer onto the existing `WaveSystem`, `BuoyancySystem`, `HydrodynamicDragSystem`, `FloodingSystem`, and `PlayerMovementSystem`. No existing system is replaced — the new systems compose with them via the event bus and shared ECS components.

## 2. Swimming System

### 2.1 State Machine

The player has six water states, tracked in `SwimmingStateComponent`:

| State | Trigger | Physics Model |
|-------|---------|---------------|
| **DRY** | Default (on land) | `PlayerMovementSystem` has full control. No water forces. |
| **WADING** | Water level between feet and chest | Walk speed reduced proportional to immersion depth. Drag force on lower body. Camera stays dry. Can still jump. |
| **SURFACE** | Water above chest, feet off ground | Camera Y tracks `WaveSystem.getHeight()` + eye offset. WASD = horizontal swim. Auto-buoyancy keeps head above water. Swim speed ~2 m/s. Stamina drains slowly. |
| **DIVING** | Player presses dive input while at SURFACE | Look direction drives movement direction (6DOF-style). Breath meter starts. Buoyancy pulls upward — player must actively swim down. Sprint = faster swim, more stamina drain. |
| **SUBMERGED** | Depth exceeds 5m | Same movement as DIVING. Depth zone effects activate (visibility, pressure). Without gear, auto-ascent when breath runs out. With gear, O₂ tank determines duration. |
| **DROWNING** | Breath/O₂ fully depleted | Health drains rapidly. Player floats upward automatically (unconscious). If they reach the surface, health drain stops and breath slowly refills. If health hits 0, death. |

### 2.2 State Transitions

- **DRY → WADING**: Water surface height at player position exceeds foot height (capsule bottom + small offset).
- **WADING → DRY**: Water drops below feet or player walks onto dry ground.
- **WADING → SURFACE**: Water exceeds chest height OR ground detection raycast finds no ground within capsule length below player.
- **SURFACE → WADING**: Ground detected and water below chest.
- **SURFACE → DIVING**: Player presses crouch/dive input.
- **DIVING → SURFACE**: Player stops providing dive input — auto-buoyancy gradually returns them to surface. Also triggered by pressing jump/ascend.
- **DIVING → SUBMERGED**: Depth exceeds 5m threshold.
- **SUBMERGED → DIVING**: Depth drops below 5m (ascending but not yet at surface). Uses 1m hysteresis band — transitions at 4m to avoid flickering at the boundary.
- **SUBMERGED → SURFACE**: Player ascends to surface (depth < 0.5m). Skips DIVING if ascent is continuous.
- **DIVING/SUBMERGED → DROWNING**: Breath = 0 (no gear) or O₂ = 0 (with gear).
- **DROWNING → SURFACE**: Unconscious float-up reaches surface. Health drain stops, breath starts refilling.

### 2.3 Water Detection

The `SwimmingSystem` determines water immersion each tick by:

1. Query all `WaterBodyComponent` entities to find one whose bounds contain the player (or the unbounded ocean).
2. Call `WaveSystem.getHeight(galaxyX, galaxyZ)` at the player's galaxy-space position to get the exact wave surface height.
3. Compare surface height against the player capsule's foot position, waist position, and head position to determine immersion level.
4. Publish `PlayerEnteredWaterEvent` / `PlayerExitedWaterEvent` on state transitions.

For **ship interiors**: the same detection logic runs, but uses the compartment's `waterVolume / floorArea` to compute a flat water level (no waves inside a ship). The water body is the interior compartment, not the ocean.

### 2.4 Swimming Physics

The player's `btRigidBody` remains the same capsule used for FPS movement. When in water states, `SwimmingSystem` applies forces instead of `PlayerMovementSystem`:

**Buoyancy force (SURFACE state):**
```
F_buoy = waterDensity * g * submergedVolume * up_direction
```
Where `submergedVolume` is estimated from capsule geometry and immersion depth. A target-tracking spring keeps the player's head at `waveHeight + eyeOffset` — this creates the Sea of Thieves bobbing feel without oscillation.

**Swim propulsion (all swim states):**
```
F_swim = swimForce * input_direction
```
Where `input_direction` is:
- WADING/SURFACE: horizontal plane (WASD projected onto water surface tangent)
- DIVING/SUBMERGED: camera look direction (6DOF — look where you want to swim)

**Water drag:**
```
F_drag = -0.5 * waterDensity * Cd * crossSectionArea * |v_rel|² * normalize(v_rel)
```
Where `v_rel = playerVelocity - waterCurrentVelocity`. This naturally creates:
- Deceleration when player stops swimming (momentum + drift)
- Current pushing the player in storms
- Speed limit from drag equilibrium

**Swim speed parameters (data-driven from swim_config.json):**

| Parameter | Value | Notes |
|-----------|-------|-------|
| surfaceSwimSpeed | 2.0 m/s | Horizontal at surface |
| diveSwimSpeed | 2.5 m/s | Underwater, any direction |
| sprintSwimMultiplier | 1.6x | With sprint input |
| wadingSpeedFraction | depth-proportional | 1.0 at ankles → 0.4 at waist |
| playerDragCoefficient | 1.2 | Cd for a human body shape |
| playerCrossSectionArea | 0.7 m² | Approximate frontal area |
| buoyancySpringK | 50.0 | Surface-tracking spring stiffness |
| buoyancyDamping | 8.0 | Prevents oscillation at surface |

### 2.5 Camera Behavior

**SURFACE state:**
- Camera Y = `waveHeight + eyeOffset` (lerped, not snapped — `lerp(currentY, targetY, 5.0 * dt)`)
- Subtle camera roll from wave surface normal: `roll = atan2(normal.x, normal.y) * 0.3` (30% of actual tilt)
- Camera pitch clamped to prevent looking straight down (reserves that for dive input)

**DIVING/SUBMERGED:**
- Free look — pitch/yaw unrestricted (full 360° vertical)
- Slight camera sway (sinusoidal, ~0.5° amplitude, ~0.3 Hz) for underwater ambience
- No roll from waves (underwater, waves don't affect orientation)

**Surface crossing transition:**
- When camera crosses the water plane (entering/exiting): brief 0.2s blur + refraction post-process effect
- Underwater: blue-green color grading that deepens with depth

### 2.6 Stamina & Breath

**Stamina** (extends existing `MovementStateComponent.currentStamina`):
- Surface swimming drains stamina at 2.0/s (vs 0 on land idle)
- Sprint swimming drains at 5.0/s
- Wading drains at 0.5/s
- Exhaustion (stamina = 0): swim speed reduced to 40%, no sprint. Same exhaustion mechanic as on land.

**Breath** (new field in `SwimmingStateComponent`):
- `maxBreath`: 30s base (upgradeable via skills/gear to 60s)
- Starts counting when state enters DIVING
- Depletes at 1.0/s normally, 1.5/s while sprint-swimming
- Refills at 3.0/s when at SURFACE (head above water)
- At 0 breath → DROWNING state

**Oxygen (gear-based):**
- `DiveGearComponent.oxygenRemaining`: measured in seconds
- Replaces breath meter when equipped
- Basic rebreather: 300s (5 min), Advanced tank: 900s (15 min), Sealed suit: 1200s (20 min)
- Refillable at surface or at oxygen stations

## 3. Underwater Depth Zone System

### 3.1 Depth Zones

Five zones define the full ocean column. All thresholds and parameters are loaded from `depth_zones.json` — nothing hardcoded.

| Zone | Depth Range | Gear Required | Visibility | Pressure Behavior |
|------|-------------|---------------|------------|-------------------|
| **Sunlit** | 0–10m | None | 100%→60%, blue tint | None |
| **Twilight** | 10–50m | Basic rebreather | 60%→30%, deep blue | Mild (ear pop SFX only) |
| **Midnight** | 50–200m | Dive suit + lamp | 30%→5%, near black | Slow health drain without suit |
| **Abyssal** | 200–500m | Pressure suit | Near zero, bioluminescence only | Damage ticks without suit |
| **Hadal** | 500m+ | Exosuit / submarine | Total darkness | Crush damage even with suit, scaled |

### 3.2 Pressure Model

Pressure is continuous: `pressure_atm = 1.0 + (depth_m / 10.0)`.

Each piece of dive gear has a `maxPressure` rating (in atm). When ambient pressure exceeds the gear's rating, damage ticks begin:

```
damage_per_second = pressureDamageRate * (ambient_pressure - gear_maxPressure)
```

No gear: `maxPressure = 2.0` (grace buffer matching the Sunlit zone boundary — no pressure damage in the top 10m, damage starts at ~10m and scales linearly below).

**Ascent sickness**: Rising faster than 10 m/s from below 30m triggers a disorientation debuff:
- Duration: 5–10 seconds (proportional to ascent speed)
- Effects: camera wobble (sinusoidal pitch/yaw offset), blurred vision (gaussian post-process), swim speed halved
- Publish `AscentSicknessEvent` for audio/UI to react

### 3.3 Visibility & Rendering

**Fog distance** decreases with depth:
```
fogEnd = maxVisibilityDistance * (1.0 - depth / maxRenderDepth)²
fogStart = fogEnd * 0.3
```

**Color shift**: water absorbs red wavelengths first. The underwater color grading transitions:
- Sunlit: light blue-green
- Twilight: deep blue
- Midnight: dark blue-black
- Abyssal: pure black with bioluminescent point lights
- Hadal: black with faint thermal vent glow

**Light sources**: player-held lamps and bioluminescent creatures have a `pointLightRadius` that decreases with depth due to particulate scattering:
```
effectiveRadius = baseLightRadius * (1.0 - depth / maxLightDepth) * 0.7 + baseLightRadius * 0.3
```
Minimum 30% of base radius so lights always do something.

### 3.4 Depth Zone Component

```
DepthZoneComponent:
  currentZone: enum (SUNLIT, TWILIGHT, MIDNIGHT, ABYSSAL, HADAL)
  currentDepth: float (metres below surface)
  ambientPressure: float (atm)
  visibilityFraction: float (0..1)
  fogColor: Color
  requiresLight: boolean
```

Updated each tick by `UnderwaterSystem` for any entity with both `SwimmingStateComponent` and `DepthZoneComponent`.

### 3.5 Dive Gear

Defined in `dive_gear.json`. Each gear piece is an equipment item with:

```
DiveGearComponent:
  oxygenCapacity: float (seconds)
  oxygenRemaining: float (seconds)
  maxPressure: float (atm)
  providesLight: boolean
  lightRadius: float (metres)
  swimSpeedModifier: float (multiplier, e.g. 0.9 for heavy suit)
  depthRating: String (human-readable, e.g. "Abyssal-rated")
```

Gear tiers:
- **Basic rebreather**: O₂ 300s, maxPressure 6 atm (60m), no light, speed 1.0x
- **Dive suit + lamp**: O₂ 600s, maxPressure 20 atm (200m), light 15m radius, speed 0.9x
- **Pressure suit**: O₂ 1200s, maxPressure 50 atm (500m), light 20m radius, speed 0.8x
- **Exosuit**: O₂ 1800s, maxPressure 120 atm (1200m), light 25m radius, speed 0.7x, has thrusters

## 4. Weather & Dynamic Wave System

### 4.1 Weather State Machine

Weather follows a four-phase cycle: **CALM → BUILDING → STORM → SUBSIDING → CALM**.

Each phase has:
- A target wave profile (number of Gerstner components, amplitudes, steepness, directions)
- A duration range (randomized within bounds)
- A transition rate (how fast wave parameters lerp toward the target)

| Phase | Duration | Wave Amplitude | Wave Count | Steepness | Wind Speed |
|-------|----------|---------------|------------|-----------|------------|
| CALM | 60–300s | 0.3–0.8m | 2 | 0.05–0.15 | 0–5 m/s |
| BUILDING | 30–120s | 0.8→3.0m | 3–4 | 0.15→0.4 | 5→15 m/s |
| STORM | 60–180s | 3.0–6.0m | 5–6 | 0.4–0.8 | 15–30 m/s |
| SUBSIDING | 60–120s | 6.0→0.8m | 6→2 | 0.8→0.1 | 30→5 m/s |

The `WeatherSystem` does not create waves directly. It modifies the `WaterBodyComponent.waves` array by lerping each `WaveParams` entry (amplitude, steepness, direction) toward the current phase's target profile. New wave components are added/removed during transitions by fading amplitude to/from zero.

**Storm intensification**: During STORM phase, there is a small chance (configurable, default 10%) per storm cycle that the storm intensifies — amplitude increases by 30%, steepness increases by 0.1, and the storm phase duration extends. This creates rare "superstorms."

### 4.2 Storm Cells — Spatial Model

Storms are localized entities with `StormCellComponent`:

```
StormCellComponent:
  centerGalaxyX: double
  centerGalaxyZ: double
  radius: float (1000–5000m)
  currentPhase: enum (CALM, BUILDING, STORM, SUBSIDING)
  phaseTimer: float
  phaseDuration: float
  windDirection: float (degrees)
  windSpeed: float (m/s)
  driftVelocityX: float (m/s, 5–20)
  driftVelocityZ: float (m/s)
  intensity: float (0..1, multiplier on wave profile)
```

Wave parameter blending based on distance to nearest storm cell:
- **Inside inner radius (70% of radius)**: full storm wave profile at current intensity
- **Edge band (70–100% of radius)**: linear lerp between storm profile and ambient calm profile
- **Outside**: ambient calm profile only

Storm cells drift with their wind direction. They can be spawned by the `WeatherSystem` based on configurable spawn rates, or triggered by game events (boss encounters, story triggers).

### 4.3 Wind → Current

Wind drives ocean surface current: `WaterBodyComponent.currentVelocity = windDirection * windSpeed * windCurrentFactor`.

Default `windCurrentFactor`: 0.03 (3% of wind speed becomes current). This means a 20 m/s storm wind creates a 0.6 m/s current — enough to push a swimmer noticeably but not teleport them.

### 4.4 Weather Events

| Event | When | Payload |
|-------|------|---------|
| `StormApproachingEvent` | Storm cell edge within 500m of player | stormEntity, distance, bearing |
| `StormEnteredEvent` | Player enters storm cell radius | stormEntity, intensity |
| `StormExitedEvent` | Player leaves storm cell radius | stormEntity |
| `StormPhaseChangedEvent` | Storm transitions between phases | stormEntity, oldPhase, newPhase |
| `StormIntensifiedEvent` | Rare intensification triggers | stormEntity, newIntensity |

### 4.5 Impact on Swimming

| Weather | Swimming Effect |
|---------|----------------|
| Calm | Gentle bobbing, easy surface swimming, negligible current |
| Building | Stronger bobbing, current starts pushing (~0.3 m/s), stamina drain 1.2x |
| Storm | Waves can submerge swimmer (wave peak > head height), strong current (~0.6 m/s), stamina drain 2.0x, surface visibility reduced |
| Subsiding | Large swells but less chop, current fading, stamina drain 1.5x |

During storms, waves tall enough to exceed the player's head height at SURFACE state trigger a **wave wash** effect. The swim state remains SURFACE (not DIVING — no breath drain), but the `SwimCameraSystem` detects `waveHeight > headHeight` and applies the underwater post-process (blue tint, muffled audio) for the duration. The buoyancy spring continues pulling the player up, so they naturally resurface in 0.5–1.0s as the wave trough passes. This creates the dramatic "fighting the ocean" feel without triggering dive mechanics or breath drain.

## 5. Ship-Water Interaction

### 5.1 Deck Wash

A new `DeckWashSystem` checks wave overtopping at a ship's gunwale sample points. These are `BuoyancySamplePoint` entries on the `HullComponent` specifically marked as gunwale-level (via a `isGunwale` flag or by having their `localOffset.y` near deck height).

When `WaveSystem.getHeight()` at a gunwale point exceeds the point's world-space Y:
```
overtoppingDepth = waveHeight - gunwaleWorldY
flowRate = Cd * gunwaleSegmentLength * sqrt(2 * g * overtoppingDepth)
```

This water enters the topmost compartment of the ship's `FloodingComponent`. Default discharge coefficient `Cd = 0.6` (same as existing flooding orifice flow).

Ship heading relative to wave direction matters: a ship broadside to waves exposes more gunwale length, taking dramatically more water. Heading into waves exposes only the bow.

### 5.2 Open Hatch Ingress

Ship hatches/doors are tracked as entries in the `FloodingComponent` with:
```
Hatch:
  id: String
  isOpen: boolean
  area: float (m², opening size)
  localPosition: Vector3 (hull body frame)
  connectsCompartments: [String, String] (or "exterior" for deck hatches)
```

When a hatch is open and its world-space Y position is below the wave surface (exterior hatches) or below the water level in an adjacent flooded compartment (interior hatches), water flows through it using orifice flow: `Q = Cd * hatchArea * sqrt(2g * headDifference)`.

Closing a hatch is a player interaction (press interact key near hatch). Closed hatches block water flow between compartments — this is a critical survival mechanic during flooding.

### 5.3 Bailing & Repair

**Bilge pump** (automatic, per-compartment):
- Passive removal rate: 0.05 m³/s per compartment with a pump
- Only functions while the pump is above the water level in its compartment (floods can disable pumps)
- Configurable per ship in ship data files

**Bail bucket** (manual player interaction):
- Player approaches water in a flooded compartment and presses interact
- Each scoop removes 0.2 m³, animation takes ~1s
- Player must then throw water overboard (walk to deck edge, press interact again) or into a drain
- Occupies both hands — player can't hold weapons while bailing
- Publish `PlayerBailingEvent` for animation/audio

**Repair kit** (manual player interaction):
- Player approaches a hull breach marker (visible glowing crack/hole in interior wall)
- Press and hold interact for 3–5 seconds (channeled action)
- On completion: `Compartment.breachArea` set to 0 (sealed)
- Repair is temporary — `breachArea` slowly reopens over 300–600s (configurable) unless permanently repaired at a dock
- Publish `HullRepairEvent` for VFX/audio

**Close hatch** (manual player interaction):
- Press interact near an open hatch door to close it
- Instant action, toggles `Hatch.isOpen`
- Closed hatches prevent both water flow and player movement through the doorway

### 5.4 Sinking Progression

The existing `BuoyancySystem` and `FloodingSystem` produce natural sinking behavior. The new systems add event-based milestones:

1. **Bilge alarm** — `BilgeAlarmEvent` when any compartment exceeds 30% flooded. Audio: klaxon. UI: warning indicator.
2. **Listing warning** — `StabilityWarningEvent` (already exists) when free surface GZ loss exceeds threshold. Ship visibly tilting.
3. **Deck awash** — `DeckAwashEvent` when deck-level sample points detect sustained submersion. Point of no return approaching.
4. **Sinking** — `ShipSinkingEvent` when total buoyancy can no longer support total mass (all sample points submerged). Players must abandon ship.
5. **Capsize** — `CapsizeEvent` (already exists) if roll exceeds 60° without recovery.

### 5.5 Interior Water Experience

Inside the ship's interior `btDynamicsWorld`, flooded compartments present a rising water plane at height `waterVolume / compartmentFloorArea` above the compartment floor.

The player's `SwimmingSystem` state machine works identically in interior worlds:
- Water at feet → WADING (slowed movement in the flooded hold)
- Water at chest → SURFACE (swimming inside the ship)
- Fully submerged compartment → DIVING/SUBMERGED (compartment is full)

The "surface" for interior swimming is the compartment water level, not the ocean wave height. No wave bobbing inside — the water plane is flat (the existing `FloodingSystem` doesn't simulate interior waves, just volume levels).

## 6. New ECS Components

| Component | Attached To | Key Fields |
|-----------|-------------|------------|
| `SwimmingStateComponent` | Player entity | `swimState` (enum), `breath`, `maxBreath`, `currentDepth`, `immersionFraction`, `isInInteriorWater` |
| `DepthZoneComponent` | Player entity, submarines | `currentZone` (enum), `ambientPressure`, `visibilityFraction`, `fogColor`, `requiresLight` |
| `DiveGearComponent` | Player entity (when equipped) | `oxygenCapacity`, `oxygenRemaining`, `maxPressure`, `providesLight`, `lightRadius`, `swimSpeedModifier` |
| `WeatherStateComponent` | Global weather entity | `activeStormCells` (managed by WeatherSystem) |
| `StormCellComponent` | Per-storm entity | `centerGalaxyX/Z`, `radius`, `currentPhase`, `phaseTimer`, `windDirection`, `windSpeed`, `intensity`, `driftVelocity` |
| `DeckWashComponent` | Ship entities | `gunwaleSampleIndices` (which HullComponent sample points are gunwale-level), `deckHeight` |
| `HatchComponent` | Ship entities | `hatches` array (id, isOpen, area, localPosition, connections) |

## 7. New ECS Systems (Priority Order)

| System | Priority | Family | Reads | Writes |
|--------|----------|--------|-------|--------|
| `WeatherSystem` | 5 (after WaveSystem) | StormCellComponent | time, storm config | WaterBodyComponent.waves, currentVelocity |
| `SwimmingSystem` | 15 (after PlayerMovement) | SwimmingStateComponent + PhysicsBodyComponent | WaveSystem, WaterBodyComponent, PlayerInputComponent, MovementStateComponent | SwimmingStateComponent, applies forces to btRigidBody |
| `UnderwaterSystem` | 16 | SwimmingStateComponent + DepthZoneComponent | SwimmingStateComponent.currentDepth, DiveGearComponent | DepthZoneComponent, health (pressure damage) |
| `DeckWashSystem` | 12 (after BuoyancySystem) | DeckWashComponent + HullComponent + FloodingComponent | WaveSystem, HullComponent sample points | FloodingComponent compartment water volumes |
| `HatchFloodingSystem` | 13 | HatchComponent + FloodingComponent | WaveSystem, compartment water levels | FloodingComponent compartment water volumes |
| `SwimCameraSystem` | 90 (late, after all physics) | SwimmingStateComponent + FPSCameraComponent | SwimmingStateComponent, WaveSystem | FPSCameraComponent (Y tracking, roll, underwater effects) |

## 8. Events

| Event | Published By | Subscribers |
|-------|-------------|-------------|
| `PlayerEnteredWaterEvent` | SwimmingSystem | Audio (splash), VFX (ripples), UI (show swim HUD) |
| `PlayerExitedWaterEvent` | SwimmingSystem | Audio, VFX, UI (hide swim HUD) |
| `PlayerStartedDivingEvent` | SwimmingSystem | Audio (submerge), UI (show breath meter) |
| `PlayerSurfacedEvent` | SwimmingSystem | Audio (gasp), VFX (surface break) |
| `BreathDepletedEvent` | SwimmingSystem | UI (danger flash), Audio (choking) |
| `PlayerDrowningEvent` | SwimmingSystem | UI (screen darken), Audio (muffled) |
| `AscentSicknessEvent` | UnderwaterSystem | UI (blur effect), Audio (ringing), SwimmingSystem (speed debuff) |
| `DepthZoneChangedEvent` | UnderwaterSystem | Audio (ambient change), Rendering (fog/color update) |
| `PressureDamageEvent` | UnderwaterSystem | UI (hull crack overlay), Audio (creaking) |
| `StormApproachingEvent` | WeatherSystem | UI (weather warning), Audio (distant thunder) |
| `StormEnteredEvent` | WeatherSystem | Audio (wind/rain), VFX (rain particles, lightning) |
| `StormExitedEvent` | WeatherSystem | Audio (calm), VFX (clear) |
| `StormPhaseChangedEvent` | WeatherSystem | Audio (intensity shift) |
| `DeckWashEvent` | DeckWashSystem | Audio (wave crash), VFX (water spray on deck) |
| `BilgeAlarmEvent` | FloodingSystem (extended) | Audio (klaxon), UI (flooding indicator) |
| `DeckAwashEvent` | DeckWashSystem | Audio (alarm), UI (abandon ship warning) |
| `ShipSinkingEvent` | BuoyancySystem (extended) | All (critical) |
| `PlayerBailingEvent` | Player interaction | Audio (water scoop), Animation |
| `HullRepairEvent` | Player interaction | Audio (hammering), VFX (patch) |

## 9. Data Files

All gameplay parameters are data-driven. New files in `core/src/main/resources/data/water/`:

- **`swim_config.json`** — swim speeds, drag coefficients, stamina drain rates, breath parameters, buoyancy spring constants
- **`depth_zones.json`** — zone boundaries, visibility curves, pressure damage rates, fog colors, light attenuation
- **`dive_gear.json`** — gear tiers with O₂ capacity, pressure rating, light, speed modifier
- **`weather_profiles.json`** — wave profiles per weather phase (amplitude, steepness, count, direction spread), phase durations, transition rates
- **`storm_config.json`** — storm cell spawn rates, radius ranges, drift speeds, intensification chance

## 10. Integration With Existing Systems

### PlayerMovementSystem
`SwimmingSystem` and `PlayerMovementSystem` must not fight over the same rigid body. When `SwimmingStateComponent.swimState != DRY`, `PlayerMovementSystem` skips that entity (checks a guard condition). `SwimmingSystem` takes over force application. When the player exits water, `SwimmingSystem` publishes `PlayerExitedWaterEvent` and clears its forces; `PlayerMovementSystem` resumes.

### WaveSystem
Read-only dependency. `SwimmingSystem`, `DeckWashSystem`, and `SwimCameraSystem` all call `WaveSystem.getHeight()` and `WaveSystem.getNormal()`. The `WeatherSystem` writes to `WaterBodyComponent.waves` which `WaveSystem` reads — no direct coupling.

### BuoyancySystem / FloodingSystem
`DeckWashSystem` writes water into `FloodingComponent` compartments. The existing `FloodingSystem` handles cross-flow, CoM shift, and stability warnings. The existing `BuoyancySystem` handles the resulting mass increase and sinking. No modification to these systems needed — only new ingress sources.

### Ship Interior Physics
`SwimmingSystem` must detect whether the player is in an interior `btDynamicsWorld` or the main world. If in an interior, it uses the compartment water level instead of `WaveSystem.getHeight()`. The `ShipInteriorComponent` provides the reference to the interior world and compartment data.

### Floating Origin
All wave phase calculations already use galaxy-space doubles. Storm cell positions are stored in galaxy-space (`double`). The `WeatherSystem` subscribes to `OriginRebasedEvent` to update local-space cached positions. Storm cell drift updates galaxy-space positions directly.

## 11. Testing Strategy

Each system must be testable without a GL context:

- **SwimmingSystem**: mock `WaveSystem` to return fixed heights, verify state transitions for each trigger, verify force magnitudes match config
- **UnderwaterSystem**: verify pressure calculation at known depths, verify damage rates against gear ratings, verify visibility curve
- **WeatherSystem**: verify phase transitions respect duration bounds, verify wave parameter lerping, verify storm cell blending by distance
- **DeckWashSystem**: mock wave heights above/below gunwale, verify flow rate calculation matches orifice formula, verify water appears in correct compartment
- **HatchFloodingSystem**: verify open vs closed hatch behavior, verify flow direction follows head difference
- **Integration**: full scenario tests with real `WaveSystem` + `BuoyancySystem` verifying a ship takes water and sinks under storm conditions
