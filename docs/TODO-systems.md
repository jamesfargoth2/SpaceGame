# Remaining Systems — TODO

Last updated: 2026-05-28. Cross-reference implemented system docs in [systems/](systems/).

---

## Plans written — implementation pending

These have a spec + plan ready to execute; no code written yet.

| System | Plan |
|---|---|
| **ED Flight Model SP1** | `docs/superpowers/plans/2026-05-28-ed-flight-model.md` (13 TDD tasks). Set-point throttle, FA on/off, blue-zone, boost. |
| **Ship procgen Phase 1a** | `docs/superpowers/plans/2026-05-28-ship-procgen-1a-style-foundation.md`. Faction visual language foundation (enums, HullStyle registry, FactionData.styleId, ShipFactory dispatch). |

---

## High Impact — Core Gameplay Loop

| System | Gap |
|---|---|
| **Scene streaming B — LOD bands** | Only `FleetLODSystem`/`DebrisLODSystem` exist. Need geometry/physics/AI LOD bands with hysteresis for all entity types. |
| **Scene streaming C — Multiplayer zone alignment** | Map client `SceneType` transitions onto server `ZoneDefinition`/`ZoneHandoffManager`. |
| **Ship procgen Phase 1b–1c** | Faction signature features (1b: Vaun spinal lance, Fed shield rings, etc.) + FACETED hull generator for Null-System/zeeLee (1c). Needs spec. |
| **Ship procgen Phase 2–3** | Component visibility (external loadout geometry on hull sockets) + damage/wear vertex-colour modulation. Needs spec. |

---

## Planet Realism

Sub-projects 2–6 of the planet realism roadmap (sub-project 1, tectonics, is implemented):

| System | Gap |
|---|---|
| **Ocean simulation** | Reads tectonic continent shapes for coastlines, tidal flats, coral zones. |
| **Volcanic terrain** | Reads tectonic volcanic-arc/hotspot zones for lava flows, calderas, basalt columns. |
| **Clouds / weather** | Extends existing climate sim: cloud bands, storm cells, precipitation → biome moisture. |
| **Ice / glacial** | Ice sheets, glacial valleys, permafrost, polar caps. |
| **Realistic atmospherics** | Rayleigh/Mie sky colour derived from atmosphere composition + star spectral type. |

---

## Cities & Settlements

City layout core (A) is implemented. Building generation through runtime streaming (B–E) are still missing:

| System | Gap |
|---|---|
| **Building generation (City B)** | Lot → exterior shell per `BuildingFunction` tag: archetypes, floor stack, facade, roof. |
| **Building interior generation (City B)** | Room graph, stairwells, furniture, NPC spawns, loot, lighting. |
| **NPC population & schedules (City C)** | Citizens with role/home/workplace, daily schedule routes through the street grid. |
| **City market integration (City D)** | Per-city `MarketComponent` + `CityEconomyGenerator` wired to sector economy. |
| **City runtime & streaming (City E)** | Geometry/collision/physics realisation, district streaming, interior load-on-enter, sphere/galaxy projection via `GalaxyAnchor`. |

---

## Combat & Weapons

| System | Gap |
|---|---|
| **Capital ship subsystem targeting** | `ShipSubsystemsComponent` exists (boarding plan A) but no player-facing precision targeting UI or combat-damage routing to specific subsystem pools outside of boarding. |
| **Broadside batteries** | `HardpointType.BROADSIDE` enum value exists; no `BroadsideSystem` or coordinated salvo logic implemented. |
| **Fighter carrier operations** | No launch/recover mechanic for fighter wings from carrier bays. |
| **Power armour locomotion** | Late-game personal armour type with its own movement and stamina modifiers. |
| **Heavy weapons** | No distinct component/system for shoulder-fired heavy weapons (rocket launchers, miniguns). |

---

## Economy & World Simulation

Only the planetary economy tier is implemented (`PlanetaryEconomyManager`, `PlanetaryStockSystem`, `PricingSystem`):

| System | Gap |
|---|---|
| **SectorEconomySystem** | Aggregates planetary production/consumption per sector; updates inter-planet trade route baselines. |
| **GalacticEconomySystem** | Daily galaxy-wide tick that sets sector supply baselines from aggregate demand. |
| **NPC freighter simulation** | NPC ships travel trade routes and shift local station stock as they deliver/pick up cargo. |
| **Faction economic control** | Faction territory should affect tax rates, tariffs, and contraband classification at stations. |

---

## Rare Astronomical Phenomena

The anomaly procgen layer (`AnomalyPlacer`, `AnomalyType`) places wormholes and pulsar beams as data objects. The hazard/gameplay mechanics behind each are missing:

| Phenomenon | Status |
|---|---|
| Nebula volumetric rendering | `NebulaVolumeGenerator` + `NebulaDensityField` exist (hazard + procgen). No volumetric visual rendering. |
| Pulsars | `AnomalyType.PULSAR_BEAM` placed. No periodic radiation burst gameplay / nav hazard zone. |
| Binary star systems | `BinaryStarData` + companion generation in `StarSystemGenerator` exist. Multi-star orbital dynamics (figure-8, horseshoe orbits) not modelled. |
| Neutron stars | Not in `SpectralClass`; no FTL supercharge interaction or unique material spawning. |
| Wormholes | `AnomalyType.WORMHOLE` placed. No transit mechanic, entry/exit pair linkage, or collapse timer. |
| Supernova remnants | Not modelled — radiation field, artifact spawning, ruins POI. |
| Dyson structures | Not modelled — mega-structure POI, associated quest chain. |
| Rogue planets | Not in `StarSystemGenerator` — interstellar wandering body generation. |

---

## UI & UX

| System | Gap |
|---|---|
| **Power priority sliders** | No player-adjustable SYS/ENG/WEP power redistribution UI. `PowerSystem` exists but has no UI surface. |
| **Ship exterior customisation UI** | `ShipColorPalette` exists; no player-driven paint scheme, decal, or hull mod flow. |
| **Ship room customisation** | No interior room placement, resizing, or decoration UI. |
| **Debug / editor imgui overlay** | imgui-java overlay for in-editor spawning, stat inspection, and economy tuning — not started. |

---

## Authored Content (Data)

| Area | Gap |
|---|---|
| **Main story arc JSON** | `act1_the_signal.json` exists with real content (Act I). Acts II and III are missing. |
| **Faction quest chains** | No per-faction `SagaData` unlocking at reputation thresholds. |
| **Companion quests** | `HookType` defined on crew members; no authored crew backstory sagas. |
| **Encyclopedia entries** | `EncyclopediaScreen` exists; zero data entries for species, factions, locations, or equipment. |

---

## Already Implemented (for reference)

| System | PR / branch |
|---|---|
| Deferred PBR rendering pipeline (GBuffer, lighting, post-processing) | procgen |
| KryoNet networking, client prediction, entity interpolation, zone routing | procgen |
| Server module (gateway, zone server, PostgreSQL/Redis persistence) | procgen |
| Stealth (signature, NPC awareness FSM, LOS) | procgen |
| Hacking (puzzle grid, controller, overlay UI) | procgen |
| Faction reputation (ReputationManager, price modifiers, docking denial) | procgen |
| Fleet combat (formation, fleet AI, admiral BT, squadron coordination) | procgen |
| Crafting / refinery pipeline | procgen |
| Grenade system (cook, bounce, fuse types, VFX) | procgen |
| Mission / quest system (saga, job board, objective tracking, rewards) | procgen |
| Asset streaming (GalacticAssetManager, priority queue) | procgen |
| Dialogue system (DialogTree, DialogSystem, DialogHudSystem, JSON data) | PR #24 |
| Ship power management (PowerSystem, PowerPenaltySystem, ReactorSpec) | PR #24 |
| Swimming / water mechanics | procgen |
| Ship builder (hull sculpt, room layout, module fit) | procgen |
| Outfitter / loadout screen (OutfitterSystem) | procgen |
| Crew recruitment screen | procgen |
| Quest journal / job board screen | procgen |
| Inventory / equipment screen | procgen |
| Orbital mechanics (Keplerian orbits, SOI tracking, trajectory HUD) | coresystemFinish |
| Player levelling & perks (XP hooks, perk trees, effects, Character screen, persistence) | coresystemFinish |
| Scene orchestration core (SceneManager FSM, SceneTransitionController, 28 tests) | coresystemFinish |
| Vehicle bay & deployment (VehicleRegistry, VehicleBayPanel, deploy/retrieve loop, DRIVING mode) | coresystemFinish |
| Ship boarding pipeline (disable → attach → breach → FPS combat → resolution, PR #33) | coresystemFinish |
| NPC dogfight AI (ShipPilotAISystem, PD steering, PilotArchetype, 97 tests) | coresystemFinish |
| Black hole physics (EventHorizonSystem, TidalForceSystem, TimeDilationSystem) | coresystemFinish |
| Tether / cable physics (TetherSystem, VerletRopeSystem, WinchSystem) | coresystemFinish |
| Structural integrity (StructuralIntegritySystem, AtmosphereVentSystem, DamageCascadeSystem, GForceSystem) | coresystemFinish |
| Life support (LifeSupportSystem, CrewMetabolicSystem, FirePhysicsSystem, PressureCycleSystem) | coresystemFinish |
| Ship thermal physics (ThermalSystem, ThermalDamageSystem, ThermalPenaltySystem) | coresystemFinish |
| Ship flooding (ShipFloodingSystem) | coresystemFinish |
| Atmospheric flight / entry heating (AeroForceSystem, EntryHeatSystem) | coresystemFinish |
| Solar wind / radiation (CMESystem, RadiationPressureSystem, SolarWindSystem, PhotonSailSystem) | coresystemFinish |
| Docking rendezvous (DockingApproachSystem, DockingCaptureSystem, HardDockConstraintSystem) | coresystemFinish |
| Planetary rings (RingSystemGenerator, ShepherdMoonPlacer, ResonanceGapCalculator) | coresystemFinish |
| Cave systems (CaveSystemGenerator, BezierTunnel, BioluminescentPatch) | coresystemFinish |
| Galaxy generation pipeline (GalaxyGenerationPipeline, GalaxyChunkManager, density field) | coresystemFinish |
| Faction territory (FactionTerritoryGenerator, TradeLane, PatrolRouteGenerator) | coresystemFinish |
| Encounter table (EncounterRoller, EncounterTableBuilder) | coresystemFinish |
| Station generation (StationGenerator, StationModule, StationType) | coresystemFinish |
| Derelict generation (DerelictGenerator, DamageProfile, SalvageItem) | coresystemFinish |
| Asteroid generation (AsteroidGenerator, VeinDeposit, MineralType) | coresystemFinish |
| Space anomaly procgen (AnomalyPlacer, AnomalyType, IonisationZone) | coresystemFinish |
| Nebula procgen / hazards (NebulaVolumeGenerator, NebulaDensityField, NebulaHazard) | coresystemFinish |
| Mech locomotion (MechLocomotionSystem, JointLimitSystem, MechGroundContactSystem) | coresystemFinish |
| Guided missiles + point defence (GuidedProjectileSystem, PointDefenseSystem) | coresystemFinish |
| Turret tracking (TurretTrackingSystem) | coresystemFinish |
| Name generation (SpaceNameGenerator, LanguageStyle, phoneme chains) | coresystemFinish |
| Audio system (AmbientManager, MusicManager, AudioSystem, SoundBindings) | coresystemFinish |
| Binary star systems (BinaryStarData, companion generation in StarSystemGenerator) | coresystemFinish |
| Planet tectonics (TectonicModel, PlateGenerator, tectonic zones — sub-project 1 of 6) | coresystemFinish |
| City layout core (CityLayoutGenerator, districts, streets, lots, landmarks — city sub-project A) | coresystemFinish |
| Creature generation — all 4 cycles (socket-graph assembly, rig+animation, skin patterns, behavior+ecosystem) | coresystemFinish |
| Flora generation — all 3 cycles (space-colonization trees, GPU-instanced grass, alien plants) | coresystemFinish |
