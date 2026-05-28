# Planet Systems

The `planet` package handles everything that happens once the player reaches a planetary surface: terrain simulation, surface vehicle physics, environmental hazards, cave systems, and atmospheric effects.

---

## Surface & Terrain

**`HeightSampler`**

Provides the height value at any surface coordinate for a procedurally generated planet. Used by vehicle physics to query ground elevation and by the terrain renderer to tesselate the mesh. Height is derived from a seeded noise stack (multi-octave Simplex) modulated by the planet's `PlanetType` and `BiomeType`.

**`PlanetType`**

Enum classifying the overall planet category (terrestrial, ocean, desert, ice, volcanic, gas giant, barren rock). Determines which biomes and hazards can spawn.

**`BiomeType`**

Enum for surface biomes: desert, tundra, savanna, temperate forest, jungle, ocean, ice sheet, volcanic, etc. Each biome provides parameters for vegetation density, moisture, and surface material.

---

## Surface Vehicle Physics

**`SurfaceVehicleSystem`** (priority 5)

Simulates wheeled or tracked vehicle behaviour on planetary surfaces:

- **Traction:** computes slip ratio per wheel contact point from `GroundVehicleComponent.tractionCoefficient`. Publishes `WheelSlipEvent` when slip exceeds threshold.
- **Sinkage:** soft surfaces (`SurfaceProperties.density`) allow wheels to sink; sinkage depth is modelled as an additional rolling resistance.
- **Rolling resistance:** friction force opposing motion, scaled by surface hardness and vehicle mass.
- **Heat stress:** sustained high load or sun exposure accumulates heat; publishes `VehicleSurfaceHeatEvent` when thermal limits are approached.
- **Low-gravity liftoff:** on low-gravity bodies, high-speed traversal over terrain bumps can launch the vehicle; publishes `LowGravLiftoffRiskEvent` and applies damping.

**`GroundVehicleComponent`**

Per-vehicle state: traction coefficient, sinkage depth, surface contact points, heat accumulation.

**`SurfaceProperties`**

Material properties loaded from `SurfaceMaterial` definitions: friction coefficient, density, hardness. Queried by the vehicle system at each contact point.

**`SurfaceMaterial`**

Enum of surface material types: bedrock, loose soil, sand, gravel, ice, mud, metal plating.

---

## Surface Anchoring

**`SurfaceAnchorSystem`**

Manages anchoring equipment (pitons, mag-locks) that pin the player or a vehicle to the surface in zero-g or low-g environments. Creates a Bullet point constraint from the entity to a ground body. `AnchorConstraint` stores the attachment point, maximum pull force, and the constraint object.

---

## Environmental Hazards

**`DustSystem`**

Simulates dust storms as `DustCloud` entities with velocity and opacity. Reduces visibility, applies drag to moving vehicles and the player.

**`SeismicSystem`**

Propagates `SeismicEvent` waves outward from epicentres (impact craters, volcanic vents). Entities with `TransformComponent` within the wave radius receive a surface shake impulse.

**Atmospheric Hazards (`AtmoHazard`)**

Enum covering atmospheric dangers: radiation belts, acidic rain, toxic gas plumes, extreme temperature bands, electrical storms. Each hazard type is checked by the planet's atmosphere system and applies appropriate damage or status effects.

**`Gas`**

Atmospheric gas composition values (O₂, CO₂, N₂, noble gases, toxins). Used to determine whether the player needs a suit and what life support load is required.

---

## Cave Systems

Located in `planet/cave/`.

| Class | Purpose |
|---|---|
| `ChamberType` | Chamber classification: crystal cavern, lava tube, flooded cave, ice pocket |
| `CaveBiome` | Biome configuration for a cave region: bioluminescence, mineral deposits, hazards |
| `CaveRoomType` | Room type within a cave complex (similar to `DungeonRoomType` but cave-themed) |

---

## Dungeon Interiors

| Class | Purpose |
|---|---|
| `DungeonRoomType` | Room purpose: armoury, lab, dormitory, power core, vault |
| `DungeonTheme` | Visual theme: cyberpunk industrial, ancient alien, derelict colonial |

---

## Impact Craters

**`CraterMorphology`**

Enum classifying crater shapes by impact energy and target material: simple bowl, complex central-peak, multi-ring basin. Used by terrain generation to decide which noise profile to apply around a crater site.

**`AsteroidComposition`**

Material type of the impactor: iron-nickel, carbonaceous, silicate, icy. Affects the mineral deposits left in the crater.

---

## Planet Rings

Located in `planet/rings/`. Ring system simulation that generates a torus of orbiting particles around gas giants or post-collision worlds. Contributes to the visual renderer and to hazard physics (micrometeorite strikes, dust drag) when the player navigates through the ring plane.

---

## Swimming on Planetary Water

**`SwimCameraSystem`** (shared between `planet/terrain/` and `water/systems/`)

Underwater camera with refraction colour correction and gentle bob driven by depth. Activates when `SwimmingStateComponent.swimmingState` enters `UNDERWATER`.

---

## Components Reference

| Component | Key fields |
|---|---|
| `GroundVehicleComponent` | Traction coefficient, sinkage depth, contact point array, heat accumulation |
| `AnchorConstraint` | Attachment world position, max pull force, Bullet constraint reference |
| `DustCloud` | Velocity, opacity, radius, lifetime |
| `SeismicEvent` | Epicentre, wave radius, current propagation radius, intensity |
