# Remaining Systems — TODO

Last updated: 2026-05-28. Cross-reference implemented system docs in [systems/](systems/).

Systems marked ✅ are implemented on the `procgen` branch (landed in PR #23 or earlier,
or in PR #24). Systems below are what is **still missing**.

---

## High Impact — Core Gameplay Loop Blockers

| System | Gap |
|---|---|
| **Scene / Sector streaming** | No `SceneManager` that additively loads/unloads zones (deep space → orbit → planet surface → station interior). `StreamingSystem` distance-enqueues assets but no zone transition state machine exists. |
| **Player levelling & perks** | `RealTimeSkillSystem` and `PointSkillComponent` exist. No character level accumulation from aggregate XP, no skill point allocation UI, no perk system or perk selection overlay. |
| **Vehicle bay & deployment** | Mech locomotion works. No `VehicleBayComponent`, no bay slot UI, no ramp open/deploy/retrieve flow, no `VehicleRegistry`. |
| **Boarding flow** | Docking exists. The full pipeline — disable engines → approach → breach/dock → FPS interior combat → capture resolution (hijack / scrap / ransom / tow) — is not implemented. |

---

## Combat & Weapons

| System | Gap |
|---|---|
| **NPC pilot AI (ship-scale)** | No dogfighting AI. NPC ships don't manoeuvre, strafe, or engage in combat flight. |
| **Capital ship subsystem targeting** | No per-subsystem health pools (engines, shields, bridge, weapons) for precision disabling. |
| **Broadside batteries** | Fixed-arc weapon mounts for capital ships that fire coordinated salvoes are missing. |
| **Fighter carrier operations** | No launch/recover mechanic for fighter wings from carrier bays. |
| **Power armour locomotion** | Late-game personal armour type with its own movement and stamina modifiers. |
| **Heavy weapons** | No distinct component/system for shoulder-fired heavy weapons (rocket launchers, miniguns). |

---

## Economy & World Simulation

| System | Gap |
|---|---|
| **SectorEconomySystem** | Aggregates planetary production/consumption per sector; updates inter-planet trade route baselines. Only the planetary tier is implemented. |
| **GalacticEconomySystem** | Daily galaxy-wide tick that sets sector supply baselines from aggregate demand. |
| **NPC freighter simulation** | NPC ships travel trade routes and shift local station stock as they deliver/pick up cargo. |
| **Faction economic control** | Faction territory should affect tax rates, tariffs, and contraband classification at stations. |

---

## Cities & Settlements

| System | Gap |
|---|---|
| **City layout generator** | Landmark district placement (spaceport, commercial, slums) + procedural street and building fill. |
| **Building interior generation** | Key buildings (shops, cantinas, faction HQs) with functional rooms and interactables. |
| **NPC population seeding** | Spawn NPCs with appropriate roles and assign schedule routes through the city grid. |
| **City market integration** | Physical market (`MarketComponent`) scoped to each city with its own stock and prices. |

---

## Rare Astronomical Phenomena

| Phenomenon | Status |
|---|---|
| Nebula volumetric rendering | Region hazards and procgen exist; no volumetric visual rendering |
| Pulsars | Missing — periodic radiation burst events, navigation hazard zone |
| Binary / trinary star systems | Missing — multi-star orbital dynamics at system generation |
| Neutron stars | Missing — FTL supercharge interaction, unique material spawning |
| Wormholes | Missing — unstable transit point, entry/exit pair, collapse timer |
| Supernova remnants | Missing — radiation field, artifact spawning, ruins POI |
| Dyson structures | Missing — mega-structure POI, associated quest chain |
| Rogue planets | Missing — interstellar wandering body generation |

---

## UI & UX

| System | Gap |
|---|---|
| **Power priority sliders** | No player-adjustable power redistribution UI (weapons / shields / engines balance). `PowerSystem` exists but has no UI surface. |
| **Level-up overlay** | No UI for character level notification, skill point award, or perk selection. |
| **Ship exterior customisation UI** | `ShipColorPalette` exists; no player-driven paint scheme, decal, or hull mod flow. |
| **Ship room customisation** | No interior room placement, resizing, or decoration UI. |
| **Debug / editor imgui overlay** | imgui-java overlay for in-editor spawning, stat inspection, and economy tuning — planned but not started. |

---

## Authored Content (Data)

| Area | Gap |
|---|---|
| **Main story arc JSON** | `SagaRunner` and the graph structure exist. No authored Act I–III saga data beyond stubs. |
| **Faction quest chains** | No per-faction `SagaData` unlocking at reputation thresholds. |
| **Companion quests** | `HookType` defined on crew members; no authored crew backstory sagas. |
| **Encyclopedia entries** | `EncyclopediaScreen` exists; zero data entries for species, factions, locations, or equipment. |

---

## Already Implemented (for reference)

These were listed as missing in earlier drafts of this file but are now done:

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
| Outfitter / loadout screen | procgen |
| Crew recruitment screen | procgen |
| Quest journal / job board screen | procgen |
| Inventory / equipment screen | procgen |
| Orbital mechanics (Keplerian orbits, SOI tracking, trajectory HUD) | coresystemFinish |
